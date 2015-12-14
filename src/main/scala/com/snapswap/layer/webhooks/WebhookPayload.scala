package com.snapswap.layer.webhooks

import java.util.UUID
import org.joda.time.DateTime
import com.snapswap.layer.{Message, Conversation, ConversationMetadata}

trait WebhookPayload {
  def eventTimestamp: DateTime
  def eventType: EnumEventType.EventType
  def eventId: UUID
  def targetConfig: Map[String, String]
}

case class WebhookMessagePayload(
                                  eventTimestamp: DateTime,
                                  eventType: EnumEventType.EventType,
                                  eventId: UUID,
                                  targetConfig: Map[String, String],
                                  message: Message
                                ) extends WebhookPayload

case class WebhookConversationPayload[M <: ConversationMetadata](
                                       eventTimestamp: DateTime,
                                       eventType: EnumEventType.EventType,
                                       eventId: UUID,
                                       targetConfig: Map[String, String],
                                       conversation: Conversation[M]
                                     ) extends WebhookPayload
