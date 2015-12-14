package com.snapswap.layer.platform

import spray.json._
import org.scalatest._
import com.snapswap.layer.{ConversationMetadata, Conversation}
import com.snapswap.layer.unmarshaller

class UnmarshallerSpec extends FlatSpec with Matchers {

  case class MyMetadata(title: String, color: String) extends ConversationMetadata
  object MetadataFormat extends DefaultJsonProtocol {
    implicit val myMetadataFormat = jsonFormat2(MyMetadata)
    implicit val myConversationReader: JsonReader[Conversation[MyMetadata]] = unmarshaller.platform.conversationReader(myMetadataFormat)
  }
  import MetadataFormat._

  "Unmarshaller" should "parse conversation with custom metadata" in {
    val result = conversation.parseJson.convertTo[Conversation[MyMetadata]]
    result.id.toString shouldBe "f3cc7b32-3c92-11e4-baad-164230d1df67"
    result.distinct shouldBe true
    result.metadata shouldBe MyMetadata("Who likes this conversation?", "#3c3c3c")
  }

  private val conversation = """{
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
}
