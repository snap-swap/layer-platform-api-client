package com.snapswap.layer

import java.util.UUID

import com.snapswap.layer.webhooks.EnumEventType._
import com.snapswap.layer.webhooks.{EnumWebhookStatus, Webhook, WebhookId}
import org.joda.time.{DateTime, DateTimeZone}
import spray.json.{JsonFormat, JsonReader}

import scala.concurrent.Future

class FakeLayerClient extends LayerClient {
  override def getConversation[M <: ConversationMetadata](id: ConversationId, participant: Option[String] = None)(implicit metadataReader: JsonReader[M]) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def getMessages(id: ConversationId, participant: Option[String] = None) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def getOrCreateConversation[M <: ConversationMetadata](participants: Set[String], metadata: M)(implicit metadataFormat: JsonFormat[M]) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def setConversationParticipants(id: ConversationId, participants: Set[String]) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def deleteConversation(id: ConversationId) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def setConversationProperty(id: ConversationId, property: String, value: String) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def createConversation[M <: ConversationMetadata](participants: Set[String], metadata: M)(implicit metadataFormat: JsonFormat[M]) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def deleteConversationProperty(id: ConversationId, property: String) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def sendAnnouncementTo(recipients: Set[String], senderName: String, parts: Seq[MessagePart], notification: Notification) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def removeConversationParticipant(id: ConversationId, participant: String) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def sendMessage(id: ConversationId, sender: BasicIdentity, parts: Seq[MessagePart], notification: Notification) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def addConversationParticipant(id: ConversationId, participant: String) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def listWebhooks() = Future.successful(Seq())

  override def getWebhook(id: WebhookId) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def createWebhook(targetUrl: String, eventTypes: Set[EventType], secret: String, targetConfig: Map[String, String]) = {
    val id: WebhookId = new WebhookId(appUuid = UUID.randomUUID(), uuid = UUID.randomUUID())

    Future.successful(
      Webhook(
        id = id,
        targetUrl = targetUrl,
        eventTypes = eventTypes.toSeq,
        status = EnumWebhookStatus.unverified,
        createdAt = new DateTime(DateTimeZone.UTC),
        config = Map()
      ))
  }

  override def deleteWebhook(id: WebhookId) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def activateWebhook(id: WebhookId) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def deactivateWebhook(id: WebhookId) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def blockCustomer(ownerUserId: String, userId: String) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def unBlockCustomer(ownerUserId: String, userId: String) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def listBlocked(ownerUserId: String) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def listConversations[M <: ConversationMetadata](participant: String)(implicit metadataReader: JsonReader[M]) = Future.failed(LayerServiceUnavailable("not implemented"))

  override def getIdentity(userId: String): Future[Identity] = Future.failed(LayerServiceUnavailable("not implemented"))

  override def updateDisplayName(userId: String, newDisplayName: String): Future[Unit] = Future.failed(LayerServiceUnavailable("not implemented"))
}
