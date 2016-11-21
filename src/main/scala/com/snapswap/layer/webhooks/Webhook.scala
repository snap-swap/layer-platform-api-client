package com.snapswap.layer.webhooks

import java.util.UUID
import org.joda.time.DateTime
import com.snapswap.layer.LayerId

object EnumEventType extends Enumeration {
  type EventType = Value
  val message_sent = Value("message.sent")
  val message_delivered = Value("message.delivered")
  val message_read = Value("message.read")
  val message_deleted = Value("message.deleted")
  val conversation_created = Value("conversation.created")
  val conversation_participants_updated = Value("conversation.updated.participants")
  val conversation_metadata_updated = Value("conversation.updated.metadata")
  val conversation_deleted = Value("conversation.deleted")
}

object EnumWebhookStatus extends Enumeration {
  type WebhookStatus = Value
  val unverified, inactive, active = Value
}

case class WebhookId(appUuid: UUID, uuid: UUID) extends LayerId {
  val id: String = WebhookId.idPrefix + appUuid + "/webhooks/" + uuid
  val url: String = WebhookId.urlPrefix + appUuid + "/webhooks/" + uuid
  override def toString = uuid.toString
}

private[layer] object WebhookId {
  private[layer] val idPrefix = "layer:///apps/"
  private[layer] val urlPrefix = "https://api.layer.com/apps/"
}

case class Webhook(
                    id: WebhookId,
                    targetUrl: String,
                    eventTypes: Seq[EnumEventType.EventType],
                    status: EnumWebhookStatus.WebhookStatus,
                    createdAt: DateTime,
                    config: Map[String, String]
                  )
