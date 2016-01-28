package com.snapswap.layer.webhooks

import java.util.UUID
import scala.util.{Try, Success, Failure}
import spray.json._
import org.joda.time.DateTime
import com.snapswap.layer.{ConversationMetadata, Message, Conversation}
import com.snapswap.layer.unmarshaller.BaseLayerUnmarshaller

trait Unmarshaller extends BaseLayerUnmarshaller {

  private[layer] implicit val enumEventTypeFormat = enumNameFormat(EnumEventType)
  private[layer] implicit val enumWebhookStatusFormat = enumNameFormat(EnumWebhookStatus)

  private[layer] implicit val webhookIdFormat = idFormat[WebhookId] { str =>
    val tokens = str.stripPrefix(WebhookId.idPrefix).split('/')
    if (tokens.length < 3) {
      deserializationError(s"Expected 'id' as '${WebhookId.idPrefix}/{app_uuid}/webhooks/{webhook_uuid}', but got '$str'")
    } else {
      val appUuid = tokens(0)
      val webhookUuid = tokens(2)
      (Try(UUID.fromString(appUuid)), Try(UUID.fromString(webhookUuid))) match {
        case (Success(app: UUID), Success(webhook: UUID)) => WebhookId(app, webhook)
        case (Failure(ex), _) => deserializationError(s"Expected 'id' as '${WebhookId.idPrefix}/{app_uuid}/webhooks/{webhook_uuid}', but got '$str'", cause = ex)
        case (_, Failure(ex)) => deserializationError(s"Expected 'id' as '${WebhookId.idPrefix}/{app_uuid}/webhooks/{webhook_uuid}', but got '$str'", cause = ex)
      }
    }
  }

  private[layer] implicit val webhookFormat = new RootJsonReader[Webhook] {
    override def read(json: JsValue) = json match {
      case obj: JsObject =>
        (obj.fields.get("id"), obj.fields.get("target_url"), obj.fields.get("event_types"), obj.fields.get("status"), obj.fields.get("created_at"), obj.fields.get("target_config")) match {
          case (Some(id), Some(targetUrl: JsString), Some(eventTypes: JsArray), Some(status), Some(createdAt), Some(targetConfig)) =>
            Webhook(
              id.convertTo[WebhookId],
              targetUrl.value,
              eventTypes.convertTo[Seq[EnumEventType.EventType]],
              status.convertTo[EnumWebhookStatus.WebhookStatus],
              createdAt.convertTo[DateTime],
              targetConfig.convertTo[Map[String, String]]
            )
          case _ =>
            deserializationError("Expected Webhook with mandatory 'id', 'target_url', 'event_types', 'status', 'created_at', 'target_config' fields present, but got " + obj)
        }
      case x => deserializationError("Expected Webhook as JsObject, but got " + x)
    }
  }

  private[layer] implicit val createWebhookWriter = new RootJsonWriter[(String, Set[EnumEventType.EventType], String, Map[String, String])] {
    override def write(tup: (String, Set[EnumEventType.EventType], String, Map[String, String])) = {
      val targetUrl: String = tup._1
      val eventTypes: Set[EnumEventType.EventType] = tup._2
      val secret: String = tup._3
      val targetConfig: Map[String, String] = tup._4
      JsObject(Map(
        "target_url" -> targetUrl.toJson,
        "event_types" -> eventTypes.toJson,
        "secret" -> secret.toJson,
        "target_config" -> targetConfig.toJson
      ))
    }
  }

  private def readPayload(json: JsValue): (DateTime, EnumEventType.EventType, UUID, Map[String, String]) = json match {
    case obj: JsObject =>
      (obj.fields.get("event_timestamp"), obj.fields.get("event_type"), obj.fields.get("event_id"), obj.fields.get("target_config")) match {
        case (Some(timestamp), Some(eventType), Some(eventId), Some(targetConfig)) =>
          (timestamp.convertTo[DateTime],
            eventType.convertTo[EnumEventType.EventType],
            eventId.convertTo[UUID],
            targetConfig.convertTo[Map[String, String]])
        case _ =>
          deserializationError("Expected WebhookPayload with mandatory 'event_timestamp', 'event_type', 'event_id', 'target_config' fields, but got " + obj)
      }
    case x => deserializationError("Expected WebhookPayload as JsObject, but got " + x)
  }

  private implicit val webhookMessagePayloadReader = new RootJsonReader[WebhookMessagePayload] {
    override def read(json: JsValue) = {
      val tup = readPayload(json)
      json.asJsObject.fields.get("message") match {
        case Some(message) => WebhookMessagePayload(
          tup._1, tup._2, tup._3, tup._4, message.convertTo[Message]
        )
        case _ => deserializationError("Expected WebhookMessagePayload with 'message' object, but got " + json)
      }
    }
  }

  private implicit def webhookConversationPayloadReader[M <: ConversationMetadata](implicit metadataReader: JsonReader[M]) =
    new RootJsonReader[WebhookConversationPayload[M]] {
      override def read(json: JsValue) = {
        val tup = readPayload(json)
        json.asJsObject.fields.get("conversation") match {
          case Some(conversation) => WebhookConversationPayload[M](
            tup._1, tup._2, tup._3, tup._4, conversation.convertTo[Conversation[M]]
          )
          case _ => deserializationError("Expected WebhookConversationPayload with 'conversation' object, but got " + json)
        }
      }
    }

  implicit def webhookPayloadReader[M <: ConversationMetadata](implicit metadataReader: JsonReader[M]) =
    new RootJsonReader[WebhookPayload] {
      override def read(json: JsValue) = json match {
        case obj: JsObject => (obj.fields.get("message"), obj.fields.get("conversation")) match {
          case (Some(message), None) => webhookMessagePayloadReader.read(json)
          case (None, Some(conversation)) => webhookConversationPayloadReader.read(json)
          case _ => deserializationError("Expected WebhookPayload with either 'message' or 'conversation' object, but got " + obj)
        }
        case x => deserializationError("Expected WebhookPayload as JsObject, but got " + x)
      }
    }
}
