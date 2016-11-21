package com.snapswap.layer

case class UserId(value: String) extends AnyVal

case class BasicIdentity(id: Option[String], name: Option[String]) {
  require(id.isDefined || name.isDefined, "Either user_id or display_name must be specified")
  val _id: Option[String] = id.map(i => s"layer:///identities/$i")
  val url: Option[String] = id.map(i => s"https://api.layer.com/identities/$i")
  override def toString = id.orElse(name).getOrElse("None")
}

object UserIdentity {
  def apply(id: String): BasicIdentity = BasicIdentity(Some(id), None)
}

object SystemIdentity {
  def apply(name: String): BasicIdentity = BasicIdentity(None, Some(name))
}

case class Identity(id: String, displayName: Option[String],
                    avatarUrl: Option[String], firstName: Option[String], lastName: Option[String],
                    phoneNumber: Option[String], emailAddress: Option[String],
                    publicKey: Option[String]) { // TODO: support identityType and metadata
  override def toString = s"user=$id" + displayName.map(n => s", name='$n'").getOrElse("")
}
