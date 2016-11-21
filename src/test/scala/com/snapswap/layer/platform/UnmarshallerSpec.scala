package com.snapswap.layer.platform

import spray.json._
import org.scalatest.{FlatSpec, Matchers}
import com.snapswap.layer._

class UnmarshallerSpec extends FlatSpec with Matchers {

  case class MyMetadata(title: String, color: String) extends ConversationMetadata

  object MetadataFormat extends DefaultJsonProtocol {
    implicit val myMetadataFormat = jsonFormat2(MyMetadata)
    implicit val myConversationReader: JsonReader[Conversation[MyMetadata]] = unmarshaller.platform.conversationReader(myMetadataFormat)
    implicit val myIdentityFormat: JsonFormat[Identity] = unmarshaller.platform.identityFormat
  }

  import MetadataFormat._

  "Unmarshaller" should "parse conversation with custom metadata" in {
    val result = conversation.parseJson.convertTo[Conversation[MyMetadata]]
    result.id.toString shouldBe "f3cc7b32-3c92-11e4-baad-164230d1df67"
    result.distinct shouldBe true
    result.metadata shouldBe MyMetadata("Who likes this conversation?", "#3c3c3c")
  }

  it should "parse identity with display_name" in {
    val result = identity.parseJson.convertTo[Identity]
    result.id shouldBe "31611111111"
    result.displayName shouldBe Some("MyCoolNickname")
  }

  /*it should "parse announcement" in {
    val result = announcement.parseJson.convertTo[Announcement]
    result.id.toString shouldBe "f3cc7b32-3c92-11e4-baad-164230d1df67"
    result.parts shouldBe Seq(PlainText(body = "Hello, World!"))
    result.recipients shouldBe Set("1234", "5678")
    result.sender shouldBe Sender(id = None, name = Some("The System"))
  } */

  private val conversation =
    """{
      |  "id": "layer:///conversations/f3cc7b32-3c92-11e4-baad-164230d1df67",
      |  "url": "https://api.layer.com/apps/APP_ID/conversations/f3cc7b32-3c92-11e4-baad-164230d1df67",
      |  "messages_url": "https://api.layer.com/apps/APP_ID/conversations/f3cc7b32-3c92-11e4-baad-164230d1df67/messages",
      |  "created_at": "2014-09-15T04:44:47.000Z",
      |  "participants": [
      |     {
      |      "id": "layer:///identities/1234",
      |      "url": "https://api.layer.com/identities/1234",
      |      "user_id": "1234",
      |      "display_name": "One Two Three Four",
      |      "avatar_url": "https://mycompany.co/images/1234.png"
      |    },
      |    {
      |      "id": "layer:///identities/777",
      |      "url": "https://api.layer.com/identities/777",
      |      "user_id": "777",
      |      "display_name": "Number Sevens",
      |      "avatar_url": "https://mycompany.co/images/777.png"
      |    },
      |    {
      |      "id": "layer:///identities/999",
      |      "url": "https://api.layer.com/identities/999",
      |      "user_id": "999",
      |      "display_name": "Nein!",
      |      "avatar_url": "https://mycompany.co/images/999.png"
      |    },
      |    {
      |      "id": "layer:///identities/111",
      |      "url": "https://api.layer.com/identities/111",
      |      "user_id": "111",
      |      "display_name": "I One!",
      |      "avatar_url": "https://mycompany.co/images/111.png"
      |    }
      |  ],
      |  "distinct": true,
      |  "metadata": {
      |    "title": "Who likes this conversation?",
      |    "color": "#3c3c3c"
      |  }
      |}""".stripMargin

  private val identity = """{
                           |      "first_name": null,
                           |      "phone_number": null,
                           |      "email_address": null,
                           |      "url": "https://api.layer.com/apps/1155bb66-7f52-11e5-89d9-8a4a6d0e19e4/users/31611111111/identity",
                           |      "display_name": "MyCoolNickname",
                           |      "identity_type": "user",
                           |      "public_key": null,
                           |      "user_id": "31611111111",
                           |      "id": "layer:///identities/31611111111",
                           |      "last_name": null,
                           |      "metadata": null,
                           |      "avatar_url": null
                           |}""".stripMargin

  private val announcement =
    """{
      |  "id": "layer:///announcements/940de862-3c96-11e4-baad-164230d1df67",
      |  "url": "https://api.layer.com/announcements/940de862-3c96-11e4-baad-164230d1df67",
      |  "parts": [
      |    {
      |      "id": "layer:///announcements/940de862-3c96-11e4-baad-164230d1df67/parts/0",
      |      "mime_type": "text/plain",
      |      "body": "This is the announcement."
      |    }
      |  ],
      |  "sent_at": "2014-09-09T04:44:47.000Z",
      |  "sender": {
      |    "user_id": null,
      |    "name": "Admin"
      |  },
      |  "recipients": ["layer:///identities/999", "layer:///identities/777"]
      |}""".stripMargin
}
