package com.snapswap.layer


import java.util.UUID
import org.joda.time.DateTime

case class AnnouncementId(uuid: UUID) extends LayerId {
  val id: String = AnnouncementId.idPrefix + uuid
  val url: String = AnnouncementId.urlPrefix + uuid

  override def toString = uuid.toString
}

object AnnouncementId {
  private[layer] val idPrefix = "layer:///announcements/"
  private[layer] val urlPrefix = "https://api.layer.com/announcements/"

  def apply(str: String): AnnouncementId = AnnouncementId(UUID.fromString(str.split('/').last))
}

case class Announcement(
                         id: AnnouncementId,
                         sender: BasicIdentity,
                         parts: Seq[MessagePart],
                         sentAt: DateTime,
                         recipients: Set[String]
                       )
