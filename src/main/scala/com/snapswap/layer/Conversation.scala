package com.snapswap.layer

import java.util.UUID
import org.joda.time.DateTime

case class ConversationId(uuid: UUID) extends LayerId {
  val id: String = "layer:///conversations/" + uuid
  val url: String = "https://api.layer.com/conversations/" + uuid
  override def toString = uuid.toString
}

object ConversationId {

  private[layer] val idPrefix = "layer:///conversations/"
  private[layer] val urlPrefix = "https://api.layer.com/conversations/"

  def apply(str: String): ConversationId = ConversationId(UUID.fromString(str.split('/').last))
}

trait ConversationMetadata {
}

case class Conversation[M <: ConversationMetadata](
                         id: ConversationId,
                         createdAt: DateTime,
                         participants: Set[String],
                         distinct: Boolean,
                         metadata: M
                       ) {
  val messagesUrl: String = id.url + "/messages"
}
