package com.snapswap.layer

import com.snapswap.layer.webhooks.{EnumEventType, Webhook, WebhookId}
import spray.json._

import scala.concurrent.Future

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

  def blockCustomer(ownerUserId: String, userId: String): Future[Unit]

  def unBlockCustomer(ownerUserId: String, userId: String): Future[Unit]

  def listBlocked(ownerUserId: String): Future[Seq[String]]
}