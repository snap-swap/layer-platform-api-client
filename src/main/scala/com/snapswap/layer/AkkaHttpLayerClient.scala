package com.snapswap.layer

import scala.concurrent.Future
import akka.event.Logging
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.http.scaladsl._
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.{Accept, OAuth2BearerToken, Authorization}
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json._
import com.snapswap.layer.webhooks.{EnumEventType, Webhook, WebhookId}

class AkkaHttpLayerClient(application: String, token: String)(implicit system: ActorSystem, materializer: Materializer) extends LayerClient {

  import system.dispatcher

  private val log = Logging(system, this.getClass)
  private val baseURL = s"/apps/$application"

  private val `application/vnd.layer+json` = MediaType.customWithFixedCharset(
    "application", "vnd.layer+json", HttpCharsets.`UTF-8`, params = Map("version" -> "2.0"))
  private val `application/vnd.layer+json;version=1.1` = MediaType.customWithFixedCharset(
    "application", "vnd.layer+json", HttpCharsets.`UTF-8`, params = Map("version" -> "1.1"))
  private val `application/vnd.layer-patch+json` = MediaType.customWithFixedCharset(
    "application", "vnd.layer-patch+json", HttpCharsets.`UTF-8`)
  private val `application/vnd.layer.webhooks+json` = MediaType.customWithFixedCharset(
    "application", "vnd.layer.webhooks+json", HttpCharsets.`UTF-8`, params = Map("version" -> "1.1"))

  private lazy val layerConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnectionHttps("api.layer.com", 443).log("layer")

  private def http(request: HttpRequest, accept: MediaType): Future[HttpResponse] =
    Source.single(
      request.addHeader(Authorization(OAuth2BearerToken(token)))
        .addHeader(Accept(accept))
    ).via(layerConnectionFlow).runWith(Sink.head)

  private def send[T](request: HttpRequest, accept: MediaType = `application/vnd.layer+json`)(handler: String => T): Future[T] = {
    http(request, accept).flatMap { response =>
      Unmarshal(response.entity).to[String].map { asString =>
        if (response.status.isSuccess()) {
          log.debug(s"SUCCESS ${request.method} ${request.uri} -> ${response.status} '$asString'")
          asString
        } else {
          log.warning(s"FAILURE ${request.method} ${request.uri} -> ${response.status} '$asString'")
          throw asString.parseJson.convertTo[LayerException](unmarshaller.platform.layerExceptionFormat)
        }
      }
    }.map(handler)
  }

  private def post(path: String, json: JsValue): HttpRequest = {
    val endpoint = baseURL + path
    val content = json.compactPrint
    log.info(s"Prepare request: POST $endpoint with $content")
    Post(endpoint)
      .withEntity(HttpEntity(`application/json`, content))
  }

  private def patch(path: String, json: JsValue): HttpRequest = {
    val endpoint = baseURL + path
    val content = json.compactPrint
    log.info(s"Prepare request: PATCH $endpoint with $content")
    Patch(endpoint)
      .withEntity(HttpEntity(`application/vnd.layer-patch+json`, content))
  }

  private def get(path: String): HttpRequest = Get(baseURL + path)

  private def delete(path: String): HttpRequest = Delete(baseURL + path)

  private def put(path: String, json: JsValue): HttpRequest = {
    val endpoint = baseURL + path
    val content = json.compactPrint
    log.info(s"Prepare request: PUT $endpoint with $content")
    Put(endpoint)
      .withEntity(HttpEntity(`application/json`, content))
  }

  override def listConversations[M <: ConversationMetadata](participant: String)(implicit metadataReader: JsonReader[M]): Future[Seq[Conversation[M]]] = {
    send(get(s"/users/${participant.trim}/conversations")) { response =>
      response.parseJson.convertTo[Seq[Conversation[M]]](unmarshaller.platform.conversationsReader)
    }
  }

  override def getConversation[M <: ConversationMetadata](id: ConversationId, participant: Option[String])
                                                         (implicit metadataReader: JsonReader[M]): Future[Conversation[M]] = {
    send(get(conversationPath(id, participant))) { response =>
      response.parseJson.convertTo[Conversation[M]](unmarshaller.platform.conversationReader)
    }
  }

  override def getMessages(id: ConversationId, participant: Option[String]): Future[Seq[Message]] = {
    import unmarshaller.platform.messagesReader
    send(get(conversationPath(id, participant) + "/messages")) { response =>
      response.parseJson.convertTo[Seq[Message]]
    }
  }

  private def conversationPath(id: ConversationId, participant: Option[String]): String =
    participant match {
      case Some(user) => s"/users/${user.trim}/conversations/${id.uuid}"
      case None => s"/conversations/${id.uuid}"
    }

  override def getOrCreateConversation[M <: ConversationMetadata](participants: Set[String], metadata: M)
                                                                 (implicit metadataFormat: JsonFormat[M]): Future[Conversation[M]] = {
    require(participants.nonEmpty)
    val json = (participants, metadata, true).toJson(unmarshaller.platform.createConversationWriter)
    send(post("/conversations", json)) { response =>
      response.parseJson.convertTo[Conversation[M]](unmarshaller.platform.conversationReader)
    }
  }

  override def createConversation[M <: ConversationMetadata](participants: Set[String], metadata: M)
                                                            (implicit metadataFormat: JsonFormat[M]): Future[Conversation[M]] = {
    require(participants.nonEmpty)
    val json = (participants, metadata, false).toJson(unmarshaller.platform.createConversationWriter)
    send(post("/conversations", json)) { response =>
      response.parseJson.convertTo[Conversation[M]](unmarshaller.platform.conversationReader)
    }
  }

  override def setConversationProperty(id: ConversationId, property: String, value: String): Future[Unit] = {
    patchConversation(id, unmarshaller.platform.SetValue(s"metadata.$property", value))
  }

  override def deleteConversationProperty(id: ConversationId, property: String): Future[Unit] =
    patchConversation(id, unmarshaller.platform.DeleteValue(s"metadata.$property"))

  override def addConversationParticipant(id: ConversationId, participant: String): Future[Unit] =
    patchConversation(id, unmarshaller.platform.AddValue("participants", participant))

  override def removeConversationParticipant(id: ConversationId, participant: String): Future[Unit] =
    patchConversation(id, unmarshaller.platform.RemoveValue("participants", participant))

  override def setConversationParticipants(id: ConversationId, participants: Set[String]): Future[Unit] =
    patchConversation(id, unmarshaller.platform.SetValues("participants", participants.toArray))

  private def patchConversation(id: ConversationId, changes: Seq[unmarshaller.platform.PatchMetadata]): Future[Unit] = {
    import unmarshaller.platform.{patchFormat, seqFormat}
    val json = changes.toJson
    send(patch(s"/conversations/${id.uuid}", json)) { response =>
    }
  }

  override def deleteConversation(id: ConversationId): Future[Unit] = {
    send(delete(s"/conversations/${id.uuid}")) { _ =>
    }
  }

  override def sendMessage(id: ConversationId, sender: BasicIdentity, parts: Seq[MessagePart], notification: Notification): Future[Message] = {
    import unmarshaller.platform.{sendMessageWriter, messageReader}
    val json = (sender, parts, notification).toJson
    send(post(s"/conversations/${id.uuid}/messages", json)) { response =>
      response.parseJson.convertTo[Message]
    }
  }

  // TODO: current Announcement implementation utilizes older Layer API - v1.1 - due to the fact that newer version requires to specify 'user_id' of a sender
  override def sendAnnouncementTo(recipients: Set[String], senderName: String, parts: Seq[MessagePart], notification: Notification): Future[Announcement] = {
    require(recipients.nonEmpty, "Recipients collection is expected to be not empty")
    require(recipients.forall(_.trim.nonEmpty), s"Expected only non-empty user IDs in recipients collection: ${recipients.mkString("'", "', '", "'")}")
    require(parts.nonEmpty, "Message parts collection is expected to be not empty")
    require(senderName.trim.nonEmpty, "Expected non-empty sender name")
    import unmarshaller.platform.{sendAnnouncementWriter, announcementReader}
    val sender = SystemIdentity(senderName.trim)
    val json = (recipients, sender, parts, notification).toJson
    send(post(s"/announcements", json), accept = `application/vnd.layer+json;version=1.1`) { response =>
      response.parseJson.convertTo[Announcement]
    }
  }

  override def createWebhook(targetUrl: String, eventTypes: Set[EnumEventType.EventType], secret: String, targetConfig: Map[String, String] = Map()): Future[Webhook] = {
    require(eventTypes.nonEmpty)
    import unmarshaller.webhooks.{createWebhookWriter, webhookReader}
    val json = (targetUrl, eventTypes, secret, targetConfig).toJson
    send(post(s"/webhooks", json)) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def listWebhooks(): Future[Seq[Webhook]] = {
    import unmarshaller.webhooks.webhooksReader
    send(get(s"/webhooks"), accept = `application/vnd.layer.webhooks+json`) { response =>
      response.parseJson.convertTo[Seq[Webhook]]
    }
  }

  override def getWebhook(id: WebhookId): Future[Webhook] = {
    import unmarshaller.webhooks.webhookReader
    send(get(s"/webhooks/${id.uuid}"), accept = `application/vnd.layer.webhooks+json`) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def activateWebhook(id: WebhookId): Future[Webhook] = {
    import unmarshaller.webhooks.webhookReader
    send(post(s"/webhooks/${id.uuid}/activate", JsString("")), accept = `application/vnd.layer.webhooks+json`) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def deactivateWebhook(id: WebhookId): Future[Webhook] = {
    import unmarshaller.webhooks.webhookReader
    send(post(s"/webhooks/${id.uuid}/deactivate", JsString("")), accept = `application/vnd.layer.webhooks+json`) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def deleteWebhook(id: WebhookId): Future[Unit] = {
    send(delete(s"/webhooks/${id.uuid}"), accept = `application/vnd.layer.webhooks+json`) { _ =>
    }
  }

  override def blockCustomer(ownerUserId: String, userId: String): Future[Unit] = {
    import unmarshaller.platform.userFormat
    send(post(s"users/$ownerUserId/blocks", UserId(userId).toJson)) { _ =>
    }
  }

  override def unBlockCustomer(ownerUserId: String, userId: String): Future[Unit] = {
    send(delete(s"users/$ownerUserId/blocks/$userId")) { _ =>
    }
  }

  override def listBlocked(ownerUserId: String): Future[Seq[String]] = {
    import unmarshaller.platform.usersReader

    send(get(s"/users/$ownerUserId/blocks")) { response =>
      response.parseJson.convertTo[Seq[UserId]].map(_.value)
    }
  }

  override def getIdentity(userId: String): Future[Identity] = {
    import unmarshaller.platform.identityFormat
    send(get(s"/users/$userId/identity")) { response =>
      response.parseJson.convertTo[Identity]
    }
  }

  override def updateDisplayName(userId: String, newDisplayName: String): Future[Unit] = {
    import unmarshaller.platform.{patchFormat, seqFormat}
    val changes = unmarshaller.platform.SetValue("display_name", newDisplayName).toJson
    send(patch(s"/users/$userId/identity", changes)) { response =>
    }
  }
}
