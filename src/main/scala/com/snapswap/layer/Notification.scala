package com.snapswap.layer

case class NotificationData(text: String, sound: Option[String] = None)

case class Notification(text: String, sound: Option[String], recipients: Option[Map[String, NotificationData]])

object TextNotification {
  def apply(text: String): Notification = Notification(text, sound = None, recipients = None)
}

object CustomizedTextNotification {
  def apply(text: String, textPerRecipient: Map[String, String]): Notification = {
    val recipients: Option[Map[String, NotificationData]] = Some(
      textPerRecipient.map { case (recipient, t) => recipient -> NotificationData(t)}
    )
    Notification(text, sound = None, recipients)
  }
}
