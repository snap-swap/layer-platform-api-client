package com.snapswap.layer.webhooks

import spray.json._
import org.scalatest._
import com.snapswap.layer.ConversationMetadata
import com.snapswap.layer.unmarshaller

class UnmarshallerSpec extends FlatSpec with Matchers {

  case class MyMetadata(title: String, color: String) extends ConversationMetadata
  object MetadataFormat extends DefaultJsonProtocol {
    implicit val myMetadataFormat = jsonFormat2(MyMetadata)
    implicit val myWebhookPayloadReader: JsonReader[WebhookPayload] = unmarshaller.webhooks.webhookPayloadReader(myMetadataFormat)
  }
  import MetadataFormat._

  "Unmarshaller" should "parse conversation data from webhook payload" in {
    val result = conversationMetadataUpdated.parseJson.convertTo[WebhookPayload]
    result shouldBe a [WebhookConversationPayload[_]]
    result.eventId.toString shouldBe "c12f340d-3b62-4cf1-9b93-ef4d754cfe69"
    result.eventType shouldBe EnumEventType.conversation_metadata_updated
    result.asInstanceOf[WebhookConversationPayload[MyMetadata]].conversation.metadata shouldBe MyMetadata("TEST", "#3c3c3c")
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
}
