name := "layer-platform-api-client"

organization  := "com.snapswap"

version       := "0.0.1"

scalaVersion  := "2.11.7"

scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-language:existentials", "-language:higherKinds", "-language:implicitConversions", "-Xfatal-warnings", "-Xlint", "-Yno-adapted-args", "-Ywarn-dead-code", "-Ywarn-numeric-widen", "-Ywarn-value-discard", "-Xfuture", "-Ywarn-unused-import", "-encoding", "UTF-8")

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

libraryDependencies ++= {
  val akkaStreamV = "2.0-M2"
  val akkaV = "2.4.1"
  Seq(
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV exclude("com.typesafe.akka", "akka-actor_2.11"),
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "joda-time" % "joda-time" % "2.9.1",
    "org.joda" % "joda-convert" % "1.8.1",
    "org.scalatest" %% "scalatest" % "2.2.5" % "test"
  )
}
