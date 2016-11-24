package com.snapswap.layer.unmarshaller

import java.util.UUID
import scala.util.{Try, Success, Failure}
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import spray.json._
import com.snapswap.layer._

trait BaseLayerUnmarshaller extends DefaultJsonProtocol {

  protected def enumNameFormat(enum: Enumeration) = new RootJsonFormat[enum.Value] {
    def read(value: JsValue) = value match {
      case JsString(s) => enum.withName(s)
      case x => deserializationError("Expected Enum as JsString, but got " + x)
    }
    def write(v: enum.Value) = JsString(v.toString)
  }

  protected implicit val uuidFormat: JsonFormat[UUID] = new RootJsonFormat[UUID] {
    override def write(obj: UUID) = JsString(obj.toString)
    override def read(json: JsValue) = json match {
      case JsString(str) => UUID.fromString(str)
      case x => deserializationError("Expected UUID as JsString, but got " + x)
    }
  }

  protected def idFormat[T <: LayerId](maker: String => T) = new RootJsonFormat[T] {
    override def read(value: JsValue) = value match {
      case JsString(s) => maker(s)
      case obj: JsObject => obj.fields.get("id") match {
        case Some(JsString(s)) => maker(s)
        case _ => deserializationError(s"Expected 'id' field, but got $obj")
      }
      case x => deserializationError("Expected 'id' as JsString, but got " + x)
    }
    override def write(obj: T) = JsObject(Map("id" -> JsString(obj.id), "url" -> JsString(obj.url)))
  }

  protected def idMaker[T <: LayerId](idPrefix: String, maker: UUID => T): String => T = { str =>
    if (!str.startsWith(idPrefix)) {
      deserializationError(s"Expected 'id' with '$idPrefix' prefix, but got '$str'")
    } else Try(UUID.fromString(str.stripPrefix(idPrefix))) match {
      case Success(uuid: UUID) => maker(uuid)
      case Failure(ex) => deserializationError(s"Expected 'id' as UUID with '$idPrefix' prefix, but got '$str'", cause = ex)
    }
  }

  protected implicit val enumRecipientStatusFormat = enumNameFormat(EnumRecipientStatus)

  protected implicit val dateTimeFormat = new RootJsonFormat[DateTime] {
    private val dfPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"
    private val df = DateTimeFormat.forPattern(dfPattern).withZoneUTC()
    override def read(json: JsValue) = json match {
      case JsString(str) =>
        val s = str//.stripSuffix("+00:00")
        Try(df.parseDateTime(s)) match {
          case Success(dt) => dt
          case Failure(ex) => deserializationError(s"Expected DateTime as JsString in '$dfPattern' format, but got '$s'", cause = ex)
        }
      case x => deserializationError("Expected DateTime as JsString, but got " + x)
    }
    override def write(obj: DateTime) = JsString(df.print(obj))
  }

  protected case class ErrorData(property: Option[String]) {
    override def toString = property.map(p => s"property = $p: ").getOrElse("")
  }
  protected case class RawError(code: Int, id: String, message: String, data: Option[ErrorData])

  protected implicit val errorDataFormat = jsonFormat1(ErrorData)
  protected implicit val rawErrorFormat: RootJsonFormat[RawError] = jsonFormat4(RawError)

  private[layer] implicit val layerExceptionFormat = new RootJsonReader[LayerException] {
    override def read(json: JsValue) = Try(rawErrorFormat.read(json)) match {
      case Success(err) => LayerException(err.code, err.id, err.data.map(_.toString).getOrElse("") + err.message)
      case Failure(ex) => UnexpectedLayerException(json.compactPrint + s" -> ${ex.getMessage}", Some(ex))
    }
  }

  protected implicit val messageIdFormat = idFormat[MessageId](idMaker(MessageId.idPrefix, MessageId.apply))

  protected implicit val conversationIdFormat = idFormat[ConversationId](idMaker(ConversationId.idPrefix, ConversationId.apply))

  implicit val basicIdentityFormat: JsonFormat[BasicIdentity] = new RootJsonFormat[BasicIdentity] {
    override def read(json: JsValue) = json match {
      case obj: JsObject =>
        (obj.fields.get("user_id"), obj.fields.get("display_name")) match {
          case (Some(id: JsString), Some(name: JsString)) => BasicIdentity(id = Some(id.value), name = Some(name.value))
          case (Some(id: JsString), _) => BasicIdentity(id = Some(id.value), name = None)
          case (_, Some(name: JsString)) => BasicIdentity(id = None, name = Some(name.value))
          case _ => deserializationError("Expected Sender with 'user_id' or 'display_name' field, but got " + obj)
        }
      case x => deserializationError("Expected Sender as JsObject, but got " + x)
    }
    override def write(bi: BasicIdentity) =
      JsObject((bi._id.map(id => "id" -> JsString(id)) ::
        bi.url.map(url => "url" -> JsString(url)) ::
        Some("user_id" -> bi.id.map(id => JsString(id)).getOrElse(JsNull)) ::
        bi.name.map(name => "display_name" -> JsString(name)) :: Nil).flatten.toMap)
  }

  implicit val messagePartFormat: JsonFormat[MessagePart] = new RootJsonFormat[MessagePart] {
    override def read(json: JsValue) = json match {
      case obj: JsObject =>
        (obj.fields.get("mime_type"), obj.fields.get("body")) match {
          case (Some(mime: JsString), optBody) =>
            val mimeType = mime.value
            optBody match {
              case Some(body: JsString) =>
                if (mimeType == PlainText.mime) {
                  PlainText(body.value)
                } else {
                  CustomMessagePart(mimeType, body.value)
                }
              case _ =>
                ContentMessagePart(mimeType)
            }
          case _ => deserializationError("Expected MessagePart with mandatory 'mime_type' field, but got " + obj)
        }
      case x => deserializationError("Expected MessagePart as JsObject, but got " + x)
    }

    override def write(obj: MessagePart) = obj match {
      case text: PlainText =>
        JsObject(Map("mime_type" -> JsString(text.mime), "body" -> JsString(text.body)))
      case custom: CustomMessagePart =>
        JsObject(Map("mime_type" -> JsString(custom.mime), "body" -> JsString(custom.body)))
      case content: ContentMessagePart =>
        JsObject(Map("mime_type" -> JsString(content.mime)))
      case other =>
        throw new UnsupportedOperationException("Don't know how to format to json: " + other)
    }
  }

  implicit val messageReader = new RootJsonReader[Message] {
    override def read(json: JsValue) = json match {
      case obj: JsObject =>
        (obj.fields.get("id"), obj.fields.get("conversation"), obj.fields.get("parts"), obj.fields.get("sent_at"), obj.fields.get("sender"), obj.fields.get("recipient_status")) match {
          case (Some(id), Some(conversation), Some(parts: JsArray), Some(sentAt), Some(sender), Some(recipientStatus)) =>
            Message(
              id.convertTo[MessageId],
              sender.convertTo[BasicIdentity],
              parts.convertTo[Seq[MessagePart]],
              sentAt.convertTo[DateTime],
              conversation.convertTo[ConversationId],
              recipientStatus.convertTo[Map[String, EnumRecipientStatus.RecipientStatus]]
            )
          case _ =>
            deserializationError("Expected Message with mandatory 'id', 'conversation', 'parts', 'sent_at', 'sender', 'recipient_status' fields present, but got " + obj)
        }
      case x => deserializationError("Expected Message as JsObject, but got " + x)
    }
  }

  implicit val messagesReader = new RootJsonReader[Seq[Message]] {
    override def read(json: JsValue) = json match {
      case JsArray(elements) => Seq(elements.map(el => messageReader.read(el)) :_*)
      case x => deserializationError("Expected Collection as JsArray, but got " + x)
    }
  }

  implicit def conversationsReader[M <: ConversationMetadata](implicit metadataReader: JsonReader[M]) =
    new RootJsonReader[Seq[Conversation[M]]] {
      override def read(json: JsValue) = json match {
        case JsArray(elements) => Seq(elements.map(el => conversationReader.read(el)) :_*)
        case x => deserializationError("Expected Collection as JsArray, but got " + x)
      }
    }

  implicit def conversationReader[M <: ConversationMetadata](implicit metadataReader: JsonReader[M]) =
    new RootJsonReader[Conversation[M]] {
      override def read(json: JsValue) = json match {
        case obj: JsObject =>
          (obj.fields.get("id"), obj.fields.get("created_at"), obj.fields.get("participants"), obj.fields.get("distinct"), obj.fields.get("metadata")) match {
            case (Some(id), Some(createdAt), Some(participants: JsArray), Some(distinct), Some(metadata)) =>
              Conversation(
                id.convertTo[ConversationId],
                createdAt.convertTo[DateTime],
                participants.elements.map(_.convertTo[BasicIdentity]),
                distinct.convertTo[Boolean],
                metadataReader.read(metadata)
              )
            case _ =>
              deserializationError(s"Expected Conversation with mandatory 'id', 'created_at', 'participants', 'distinct', 'metadata' fields present, but got " + obj)
          }
        case x => deserializationError("Expected Conversation as JsObject, but got " + x)
      }
    }

}
