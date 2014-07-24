name := "traceplay"

version := "1.0"

scalaVersion := "2.10.4"

lazy val root = (project in file(".")).enablePlugins(PlayScala).settings(echoPlaySettings:_*)
