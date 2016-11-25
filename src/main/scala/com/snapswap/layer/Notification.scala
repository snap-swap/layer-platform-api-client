package com.snapswap.layer

case class NotificationData(title: Option[String], text: Option[String], sound: Option[String])

case class Notification(title: Option[String], text: Option[String], sound: Option[String], recipients: Map[String, NotificationData] = Map.empty)

object TextNotification {
  def apply(title: String, text: String): Notification =
    Notification(title = Some(title), text = Some(text), sound = None)
}

object NoNotification {
  def apply(): Notification =
    Notification(None, None, None)
}

object CustomizedTextNotification {
  def apply(title: String, text: String, textPerRecipient: Map[String, String]): Notification = {
    val recipients: Map[String, NotificationData] =
      textPerRecipient.map { case (recipient, t) => recipient -> NotificationData(None, Some(t), None)}
    Notification(title = Some(title), text = Some(text), sound = None, recipients)
  }
}
