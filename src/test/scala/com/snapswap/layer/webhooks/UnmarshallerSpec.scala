package com.snapswap.layer.webhooks

import spray.json._
import org.scalatest.{FlatSpec, Matchers}
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
                                              |  "event": {
                                              |    "created_at": "2015-09-17T20:46:47.561Z",
                                              |    "type": "conversation.updated.metadata",
                                              |    "id": "c12f340d-3b62-4cf1-9b93-ef4d754cfe69"
                                              |  },
                                              |  "conversation": {
                                              |      "id": "layer:///conversations/f3cc7b32-3c92-11e4-baad-164230d1df67",
                                              |      "url": "https://api.layer.com/apps/082d4684-0992-11e5-a6c0-1697f925ec7b/conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f",
                                              |      "created_at": "2014-09-15T04:44:47.561Z",
                                              |      "messages_url": "https://api.layer.com/conversations/c12fd916-1390-464b-850f-1380a051f7c8/messages",
                                              |      "distinct": false,
                                              |      "participants": [{
                                              |            "id": "layer:///identities/1234",
                                              |            "url": "https://api.layer.com/identities/1234",
                                              |            "user_id": "1234",
                                              |            "display_name": "One Two Three Four",
                                              |            "avatar_url": "https://mycompany.co/images/1234.png"
                                              |      }, {
                                              |            "id": "layer:///identities/5678",
                                              |            "url": "https://api.layer.com/identities/5678",
                                              |            "user_id": "5678",
                                              |            "display_name": "Number Sevens",
                                              |            "avatar_url": "https://mycompany.co/images/5678.png"
                                              |      }],
                                              |      "metadata": {
                                              |            "title": "TEST",
                                              |            "color": "#3c3c3c"
                                              |      }
                                              |  },
                                              |  "config": {
                                              |    "key1": "value1",
                                              |    "key2": "value2"
                                              |  }
                                              |}""".stripMargin

  private val messageSent = """{
                              |  "event": {
                              |    "created_at": "2015-09-17T20:46:47.561Z",
                              |    "type": "message.sent",
                              |    "id": "c12f340d-3b62-4cf1-9b93-ef4d754cfe69",
                              |    "actor": {
                              |      "user_id": "14251111111"
                              |    }
                              |  },
                              |  "message": {
                              |  "id": "layer:///messages/940de862-3c96-11e4-baad-164230d1df67",
                              |  "url": "https://api.layer.com/apps/YOUR_APP_ID/messages/940de862-3c96-11e4-baad-164230d1df67",
                              |  "conversation": {
                              |    "id": "layer:///conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f",
                              |    "url": "https://api.layer.com/apps/YOUR_APP_ID/conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f"
                              |  },
                              |  "parts": [
                              |    {
                              |      "id": "layer:///messages/940de862-3c96-11e4-baad-164230d1df67/parts/0",
                              |      "mime_type": "text/plain",
                              |      "body": "This is the message."
                              |    },
                              |    {
                              |      "id": "layer:///messages/940de862-3c96-11e4-baad-164230d1df67/parts/1",
                              |      "mime_type": "application/vnd.snapswap+json",
                              |      "body": "..."
                              |    }
                              |  ],
                              |  "sent_at": "2014-09-09T04:44:47.000Z",
                              |  "sender": {
                              |    "id": "layer:///identities/14251111111",
                              |    "url": "https://api.layer.com/identities/14251111111",
                              |    "user_id": "14251111111",
                              |    "display_name": "One Two Three Four"
                              |  },
                              |  "recipient_status": {
                              |    "layer:///identities/14251111111": "sent",
                              |    "layer:///identities/31611111111": "read"
                              |  }
                              |},
                              |  "config": {
                              |    "key1": "value1",
                              |    "key2": "value2"
                              |  }
                              |}""".stripMargin
}
