/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt

import sbt._
import sbt.Keys._
import play.Project.playVersion

object SbtEchoPlay extends Plugin {
  import SbtEcho._
  import SbtEcho.EchoKeys._
  import echo.EchoPlayRun._
  import echo.EchoRun.EchoTraceCompile

  lazy val echoPlaySettings: Seq[Setting[_]] = echoCompileSettings ++ inConfig(Echo)(tracePlaySettings) ++ echoPlayRunSettings

  def tracePlaySettings(): Seq[Setting[_]] = Seq(
    tracePlayVersion <<= playVersion map supportedPlayVersion,
    traceDependencies <<= (libraryDependencies, tracePlayVersion, echoVersion) map tracePlayDependencies
  )
}
