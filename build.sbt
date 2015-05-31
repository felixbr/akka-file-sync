name := "akka-file-sync"

version := "0.1"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0-M5",
  "com.typesafe.akka" % "akka-http-experimental_2.11" % "1.0-M5",
  "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "1.0-M5",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
  "com.typesafe.akka" %% "akka-remote" % "2.3.9",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.9",

  "com.google.guava" % "guava" % "18.0",

  "org.scalatest" % "scalatest_2.11" % "2.1.6" % "test"
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8", // yes, this is 2 args
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)