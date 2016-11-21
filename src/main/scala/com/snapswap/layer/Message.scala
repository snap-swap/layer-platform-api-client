package com.snapswap.layer

import java.util.UUID
import org.joda.time.DateTime

case class MessageId(uuid: UUID) extends LayerId {
  val id: String = MessageId.idPrefix + uuid
  val url: String = MessageId.urlPrefix + uuid
  override def toString = uuid.toString
}

object MessageId {

  private[layer] val idPrefix = "layer:///messages/"
  private[layer] val urlPrefix = "https://api.layer.com/messages/"

  def apply(str: String): MessageId = MessageId(UUID.fromString(str.split('/').last))
}

trait MessagePart {
  def mime: String
  // TODO: support base64 encoding
}

object PlainText {
  val mime = "text/plain"
}

case class PlainText(body: String) extends MessagePart {
  val mime = PlainText.mime
  override def toString = body
}

case class CustomMessagePart(mime: String, body: String) extends MessagePart {
  override def toString = body
}

case class ContentMessagePart(mime: String) extends MessagePart {
  // TODO support rich content
}

object EnumRecipientStatus extends Enumeration {
  type RecipientStatus = Value
  val read, delivered, sent = Value
}

case class Message(
                    id: MessageId,
                    sender: BasicIdentity,
                    parts: Seq[MessagePart],
                    sentAt: DateTime,
                    conversation: ConversationId,
                    recipientStatus: Map[String, EnumRecipientStatus.RecipientStatus]
                  )

