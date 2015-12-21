package com.snapswap.layer.webhooks

import spray.json._
import org.scalatest._
import com.snapswap.layer.{CustomMessagePart, PlainText, ConversationMetadata, unmarshaller}

class UnmarshallerSpec extends FlatSpec with Matchers {

  case class MyMetadata(title: String, color: String) extends ConversationMetadata
  object MetadataFormat extends DefaultJsonProtocol {
    implicit val myMetadataFormat = jsonFormat2(MyMetadata)
    implicit val myWebhookPayloadReader: JsonReader[WebhookPayload] = unmarshaller.webhooks.webhookPayloadReader(myMetadataFormat)
  }
  import MetadataFormat._

  "Unmarshaller" should "be able to parse conversation data from webhook payload" in {
    val result = conversationMetadataUpdated.parseJson.convertTo[WebhookPayload]
    result shouldBe a [WebhookConversationPayload[_]]
    result.eventId.toString shouldBe "c12f340d-3b62-4cf1-9b93-ef4d754cfe69"
    result.eventType shouldBe EnumEventType.conversation_metadata_updated
    result.asInstanceOf[WebhookConversationPayload[MyMetadata]].conversation.metadata shouldBe MyMetadata("TEST", "#3c3c3c")
  }

  it should "be able to parse message data from webhook payload" in {
    val result = messageSent.parseJson.convertTo[WebhookPayload]
    result shouldBe a [WebhookMessagePayload]
    result.eventId.toString shouldBe "c12f340d-3b62-4cf1-9b93-ef4d754cfe69"
    result.eventType shouldBe EnumEventType.message_sent
    val parts = result.asInstanceOf[WebhookMessagePayload].message.parts
    parts should have length 2
    parts.find(_.mime == "text/plain") shouldBe Some(PlainText("This is the message."))
    parts.find(_.mime == "application/vnd.snapswap+json") shouldBe Some(CustomMessagePart("application/vnd.snapswap+json", "..."))
  }

  private val conversationMetadataUpdated = """{
                               |    "event_timestamp":"2015-09-17T20:46:47.561Z",
                               |    "event_type":"conversation.metadata_updated",
                               |    "event_id":"c12f340d-3b62-4cf1-9b93-ef4d754cfe69",
                               |    "conversation": {
                               |        "id": "layer:///conversations/f3cc7b32-3c92-11e4-baad-164230d1df67",
                               |         "url": "https://api.layer.com/apps/082d4684-0992-11e5-a6c0-1697f925ec7b/conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f",
                               |        "created_at": "2014-09-15T04:44:47.561Z",
                               |         "messages_url": "https://api.layer.com/conversations/c12fd916-1390-464b-850f-1380a051f7c8/messages",
                               |        "distinct": false,
                               |        "participants": [
                               |            "1234",
                               |            "5678"
                               |        ],
                               |        "metadata": {
                               |            "title": "TEST",
                               |            "color": "#3c3c3c"
                               |        }
                               |    },
                               |    "target_config" : {
                               |        "key1" : "value1",
                               |        "key2" : "value2"
                               |    }
                               |}""".stripMargin

  private val messageSent = """{
                              |    "event_timestamp": "2015-09-17T20:46:47.561Z",
                              |    "event_type": "message.sent",
                              |    "event_id": "c12f340d-3b62-4cf1-9b93-ef4d754cfe69",
                              |    "message": {
                              |        "id": "layer:///messages/940de862-3c96-11e4-baad-164230d1df67",
                              |        "conversation": {
                              |            "id": "layer:///conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f",
                              |            "url": "https://api.layer.com/apps/082d4684-0992-11e5-a6c0-1697f925ec7b/conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f"
                              |        },
                              |        "parts": [
                              |            {
                              |                "id": "layer:///messages/940de862-3c96-11e4-baad-164230d1df67/parts/0",
                              |                "mime_type": "text/plain",
                              |                "body": "This is the message."
                              |            },
                              |            {
                              |                "id": "layer:///messages/940de862-3c96-11e4-baad-164230d1df67/parts/1",
                              |                "mime_type": "application/vnd.snapswap+json",
                              |                "body": "..."
                              |            }
                              |        ],
                              |        "sent_at": "2014-09-09T04:44:47.000Z",
                              |        "sender": {
                              |            "user_id": "14251111111"
                              |        },
                              |        "recipient_status": {
                              |            "14251111111": "read",
                              |            "31615152117": "sent"
                              |        }
                              |    },
                              |    "target_config" : {
                              |    }
                              |}""".stripMargin
}
