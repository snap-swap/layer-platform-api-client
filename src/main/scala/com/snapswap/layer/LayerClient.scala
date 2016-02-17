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

trait LayerClient {
  def getConversation[M <: ConversationMetadata](id: ConversationId)(implicit metadataReader: JsonReader[M]): Future[Conversation[M]]

  def getOrCreateConversation[M <: ConversationMetadata](participants: Set[String], metadata: M)(implicit metadataFormat: JsonFormat[M]): Future[Conversation[M]]

  def createConversation[M <: ConversationMetadata](participants: Set[String], metadata: M)(implicit metadataFormat: JsonFormat[M]): Future[Conversation[M]]

  def setConversationProperty(id: ConversationId, property: String, value: String): Future[Unit]

  def deleteConversationProperty(id: ConversationId, property: String): Future[Unit]

  def addConversationParticipant(id: ConversationId, participant: String): Future[Unit]

  def removeConversationParticipant(id: ConversationId, participant: String): Future[Unit]

  def setConversationParticipants(id: ConversationId, participants: Set[String]): Future[Unit]

  def deleteConversation(id: ConversationId): Future[Unit]

  def sendMessage(id: ConversationId, sender: Sender, parts: Seq[MessagePart], notification: Notification): Future[Message]

  def sendAnnouncementTo(recipients: Set[String], senderName: String, parts: Seq[MessagePart], notification: Notification): Future[Announcement]

  def sendAnnouncementToEveryone(senderName: String, parts: Seq[MessagePart], notification: Notification): Future[Announcement] =
    sendAnnouncementTo(Set("everyone"), senderName, parts, notification)

  def createWebhook(targetUrl: String, eventTypes: Set[EnumEventType.EventType], secret: String, targetConfig: Map[String, String] = Map()): Future[Webhook]

  def listWebhooks(): Future[Seq[Webhook]]

  def getWebhook(id: WebhookId): Future[Webhook]

  def activateWebhook(id: WebhookId): Future[Webhook]

  def deactivateWebhook(id: WebhookId): Future[Webhook]

  def deleteWebhook(id: WebhookId): Future[Unit]
}

class AkkaHttpLayerClient(application: String, token: String)(implicit system: ActorSystem, materializer: Materializer) extends LayerClient {

  import system.dispatcher

  private val log = Logging(system, this.getClass)
  private val baseURL = s"/apps/$application"

  private val `application/vnd.layer+json` = MediaType.customWithFixedCharset(
    "application", "vnd.layer+json", HttpCharsets.`UTF-8`, params = Map("version" -> "1.0"))
  private val `application/vnd.layer-patch+json` = MediaType.customWithFixedCharset(
    "application", "vnd.layer-patch+json", HttpCharsets.`UTF-8`)

  private lazy val layerConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnectionHttps("api.layer.com", 443).log("layer")

  private def http(request: HttpRequest): Future[HttpResponse] =
    Source.single(
      request.addHeader(Authorization(OAuth2BearerToken(token)))
        .addHeader(Accept(`application/vnd.layer+json`))
    ).via(layerConnectionFlow).runWith(Sink.head)

  private def send[T](request: HttpRequest)(handler: String => T): Future[T] = {
    http(request).flatMap { response =>
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


  override def getConversation[M <: ConversationMetadata](id: ConversationId)
                                                         (implicit metadataReader: JsonReader[M]): Future[Conversation[M]] = {
    send(get(s"/conversations/${id.uuid}")) { response =>
      response.parseJson.convertTo[Conversation[M]](unmarshaller.platform.conversationReader)
    }
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

  override def sendMessage(id: ConversationId, sender: Sender, parts: Seq[MessagePart], notification: Notification): Future[Message] = {
    import unmarshaller.platform.{sendMessageWriter, messageReader}
    val json = (sender, parts, notification).toJson
    send(post(s"/conversations/${id.uuid}/messages", json)) { response =>
      response.parseJson.convertTo[Message]
    }
  }

  override def sendAnnouncementTo(recipients: Set[String], senderName: String, parts: Seq[MessagePart], notification: Notification): Future[Announcement] = ???

  override def createWebhook(targetUrl: String, eventTypes: Set[EnumEventType.EventType], secret: String, targetConfig: Map[String, String] = Map()): Future[Webhook] = {
    require(eventTypes.nonEmpty)
    import unmarshaller.webhooks.{createWebhookWriter, webhookFormat}
    val json = (targetUrl, eventTypes, secret, targetConfig).toJson
    send(post(s"/webhooks", json)) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def listWebhooks(): Future[Seq[Webhook]] = {
    import unmarshaller.webhooks.webhookFormat
    send(get(s"/webhooks")) { response =>
      response.parseJson.asInstanceOf[JsArray].elements.map(el => webhookFormat.read(el)).toSeq
    }
  }

  override def getWebhook(id: WebhookId): Future[Webhook] = {
    import unmarshaller.webhooks.webhookFormat
    send(get(s"/webhooks/${id.uuid}")) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def activateWebhook(id: WebhookId): Future[Webhook] = {
    import unmarshaller.webhooks.webhookFormat
    send(post(s"/webhooks/${id.uuid}/activate", JsString(""))) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def deactivateWebhook(id: WebhookId): Future[Webhook] = {
    import unmarshaller.webhooks.webhookFormat
    send(post(s"/webhooks/${id.uuid}/deactivate", JsString(""))) { response =>
      response.parseJson.convertTo[Webhook]
    }
  }

  override def deleteWebhook(id: WebhookId): Future[Unit] = {
    send(delete(s"/webhooks/${id.uuid}")) { _ =>
    }
  }
}
