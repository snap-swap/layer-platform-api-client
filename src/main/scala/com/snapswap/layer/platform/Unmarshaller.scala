package com.snapswap.layer.platform

import org.joda.time.DateTime
import spray.json._
import com.snapswap.layer._
import com.snapswap.layer.unmarshaller.BaseLayerUnmarshaller

trait Unmarshaller extends BaseLayerUnmarshaller {

  implicit val notificationDataFormat: JsonFormat[NotificationData] = jsonFormat2(NotificationData)
  implicit val notificationFormat: JsonFormat[Notification] = jsonFormat3(Notification)

  private[layer] implicit def createConversationWriter[M <: ConversationMetadata](implicit metadataWriter: JsonWriter[M])
    = new RootJsonWriter[(Set[String], M, Boolean)] {
    override def write(tup: (Set[String], M, Boolean)) = {
      val participants: Set[String] = tup._1
      val metadata: M = tup._2
      val distinct: Boolean = tup._3
      JsObject(Map(
        "participants" -> participants.toJson,
        "distinct" -> distinct.toJson,
        "metadata" -> metadata.toJson
      ))
    }
  }

  private[layer] implicit val sendMessageWriter = new RootJsonWriter[(BasicIdentity, Seq[MessagePart], Notification)] {
    override def write(tup: (BasicIdentity, Seq[MessagePart], Notification)) = {
      val sender: BasicIdentity = tup._1
      require(sender._id.isDefined, s"Message sender must contain a user ID: $sender")
      val senderId: String = sender._id.get
      val parts: Seq[MessagePart] = tup._2
      val notification: Notification = tup._3
      JsObject(Map(
        "sender_id" -> JsString(senderId),
        "notification" -> notification.toJson,
        "parts" -> parts.toJson
      ))
    }
  }

  private[layer] implicit val sendAnnouncementWriter = new RootJsonWriter[(Set[String], BasicIdentity, Seq[MessagePart], Notification)] {
    override def write(tup: (Set[String], BasicIdentity, Seq[MessagePart], Notification)) = {
      val recipients: Set[String] = tup._1
      val sender: BasicIdentity = tup._2
      require(sender.name.isDefined, s"Announcement sender must contain a name: $sender")
      val senderName: String = sender.name.get
      val parts: Seq[MessagePart] = tup._3
      val notification: Notification = tup._4
      JsObject(Map(
        "recipients" -> recipients.toJson,
        "sender" -> JsObject(Map("name" -> JsString(senderName))),
        "notification" -> notification.toJson,
        "parts" -> parts.toJson
      ))
    }
  }

  private[layer] case class PatchMetadata(operation: String, property: String, value: Option[String], values: Option[Array[String]]) {
    require(value.isDefined || values.isDefined || operation == DeleteValue.operation, s"For '$operation' operation either 'value' or 'values' must be specified")
  }
  private[layer] object AddValue {
    val operation = "add"
    def apply(property: String, value: String): Seq[PatchMetadata] = Seq(PatchMetadata(operation, property, Some(value), None))
  }
  private[layer] object RemoveValue {
    val operation = "remove"
    def apply(property: String, value: String): Seq[PatchMetadata] = Seq(PatchMetadata(operation, property, Some(value), None))
  }
  private[layer] object SetValue {
    val operation = "set"
    def apply(property: String, value: String): Seq[PatchMetadata] = Seq(PatchMetadata(operation, property, Some(value), None))
  }
  private[layer] object DeleteValue {
    val operation = "delete"
    def apply(property: String): Seq[PatchMetadata] = Seq(PatchMetadata(operation, property, None, None))
  }
  private[layer] object SetValues {
    def apply(property: String, value: Array[String]): Seq[PatchMetadata] = Seq(PatchMetadata(SetValue.operation, property, None, Some(value)))
  }

  private case class RawPatchNoValue(operation: String, property: String)
  private case class RawPatchValue(operation: String, property: String, value: String)
  private case class RawPatchValues(operation: String, property: String, value: Array[String])

  private implicit val rawPatchNoValueFormat: JsonFormat[RawPatchNoValue] = jsonFormat2(RawPatchNoValue)
  private implicit val rawPatchValueFormat: JsonFormat[RawPatchValue] = jsonFormat3(RawPatchValue)
  private implicit val rawPatchValuesFormat: JsonFormat[RawPatchValues] = jsonFormat3(RawPatchValues)
  private[layer] implicit val patchFormat = new RootJsonFormat[PatchMetadata] {
    override def write(obj: PatchMetadata) = (obj.value, obj.values) match {
      case (None, None) => RawPatchNoValue(obj.operation, obj.property).toJson
      case (Some(v), _) => RawPatchValue(obj.operation, obj.property, v).toJson
      case (_, Some(v)) => RawPatchValues(obj.operation, obj.property, v).toJson
    }
    override def read(json: JsValue) = {
      def patchNoValue: PatchMetadata = {
        val raw = rawPatchNoValueFormat.read(json)
        PatchMetadata(raw.operation, raw.property, None, None)
      }
      json match {
        case obj: JsObject => obj.fields.get("value") match {
          case None =>
            patchNoValue
          case Some(v) => v match {
            case _: JsArray =>
              val raw = rawPatchValuesFormat.read(json)
              PatchMetadata(raw.operation, raw.property, None, Some(raw.value))
            case _: JsString =>
              val raw = rawPatchValueFormat.read(json)
              PatchMetadata(raw.operation, raw.property, Some(raw.value), None)
            case _ =>
              patchNoValue
          }
        }
        case x =>
          deserializationError("Expected PatchValue as JsObject, but got " + x)
      }
    }
  }

  implicit val userFormat = jsonFormat(UserId, "user_id")
  implicit val usersReader = new RootJsonReader[Seq[UserId]] {
    override def read(json: JsValue) = json match {
      case JsArray(elements) => Seq(elements.map(el => userFormat.read(el)) :_*)
      case x => deserializationError("Expected Collection as JsArray, but got " + x)
    }
  }

  implicit val identityFormat = jsonFormat(Identity, "user_id", "display_name",
    "avatar_url", "first_name", "last_name", "phone_number", "email_address", "public_key")


  implicit val announcementIdFormat = idFormat[AnnouncementId](idMaker(AnnouncementId.idPrefix, AnnouncementId.apply))
  implicit val announcementReader = new RootJsonReader[Announcement] {
    override def read(json: JsValue) = json match {
      case obj: JsObject =>
        (obj.fields.get("id"), obj.fields.get("sent_at"), obj.fields.get("recipients"), obj.fields.get("sender"), obj.fields.get("parts")) match {
          case (Some(_id: JsString), Some(_sentAt), Some(_recipients), Some(_sender: JsObject), Some(_parts)) =>
            val id = AnnouncementId(_id.value)
            val sentAt = _sentAt.convertTo[DateTime]
            val recipients = _recipients.convertTo[Seq[String]].toSet
            val parts = _parts.convertTo[Seq[MessagePart]]
            _sender.fields.get("name") match {
              case Some(senderName: JsString) =>
                val sender = SystemIdentity(senderName.value)
                Announcement(id, sender, parts, sentAt, recipients)
              case _ => deserializationError("Expected Announcement with mandatory 'sender.name' field present, but got " + obj)
            }
          case _ =>
            deserializationError("Expected Announcement with mandatory 'id', 'sent_at', 'recipients', 'sender', 'parts' fields present, but got " + obj)
        }
      case x => deserializationError("Expected Announcement as JsObject, but got " + x)
    }
  }
}
