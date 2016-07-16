package com.snapswap.layer.platform

import spray.json._
import org.scalatest._
import com.snapswap.layer._

class UnmarshallerSpec extends WordSpecLike with Matchers {

  case class MyMetadata(title: String, color: String) extends ConversationMetadata

  object MetadataFormat extends DefaultJsonProtocol {
    implicit val myMetadataFormat = jsonFormat2(MyMetadata)
    implicit val myConversationReader: JsonReader[Conversation[MyMetadata]] = unmarshaller.platform.conversationReader(myMetadataFormat)
    implicit val myAnnouncementsReader: JsonReader[Announcement] = unmarshaller.platform.announcementReader
  }

  import MetadataFormat._

  "Unmarshaller" should {
    "parse conversation with custom metadata" in {
      val result = conversation.parseJson.convertTo[Conversation[MyMetadata]]
      result.id.toString shouldBe "f3cc7b32-3c92-11e4-baad-164230d1df67"
      result.distinct shouldBe true
      result.metadata shouldBe MyMetadata("Who likes this conversation?", "#3c3c3c")
    }
    "parse annoncement" in {

      val result = announcement.parseJson.convertTo[Announcement]
      result.id.toString shouldBe "f3cc7b32-3c92-11e4-baad-164230d1df67"
      result.parts shouldBe Seq(PlainText(body = "Hello, World!"))
      result.recipients shouldBe Set("1234", "5678")
      result.sender shouldBe Sender(id = None, name = Some("The System"))
    }
  }

  private val conversation =
    """{
      |  "id": "layer:///conversations/f3cc7b32-3c92-11e4-baad-164230d1df67",
      |  "url": "https://api.layer.com/conversations/f3cc7b32-3c92-11e4-baad-164230d1df67",
      |  "messages_url": "https://api.layer.com/conversations/f3cc7b32-3c92-11e4-baad-164230d1df67/messages",
      |  "created_at": "2015-12-14T05:23:56.455Z",
      |  "last_message": null,
      |  "participants": [
      |    "1234",
      |    "777",
      |    "999",
      |    "111"
      |  ],
      |  "distinct": true,
      |  "unread_message_count": 3,
      |  "metadata": {
      |    "title": "Who likes this conversation?",
      |    "color": "#3c3c3c"
      |  }
      |}""".stripMargin

  private val announcement =
    """{
      |    "id": "layer:///announcements/f3cc7b32-3c92-11e4-baad-164230d1df67",
      |    "url": "https://api.layer.com/apps/24f43c32-4d95-11e4-b3a2-0fd00000020d/announcements/f3cc7b32-3c92-11e4-baad-164230d1df67",
      |    "sent_at": "2014-09-15T04:44:47.455Z",
      |    "recipients": [ "1234", "5678" ],
      |    "sender": {
      |        "name": "The System"
      |    },
      |    "parts": [
      |        {
      |            "body": "Hello, World!",
      |            "mime_type": "text/plain"
      |        }
      |    ],
      |    "notification": {
      |        "text": "This is the alert text to include with the Push Notification.",
      |        "sound": "chime.aiff"
      |    }
      |}""".stripMargin
}
