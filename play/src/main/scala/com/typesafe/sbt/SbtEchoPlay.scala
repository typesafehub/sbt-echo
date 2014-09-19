/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt

import sbt._
import sbt.Keys._
import play.PlayImport._
import PlayKeys.playVersion

object SbtEchoPlay extends AutoPlugin {
  import SbtEcho._
  import SbtEcho.EchoKeys._
  import echo.EchoPlayRun._
  import echo.EchoRun.EchoTraceCompile

  override def trigger = AllRequirements
  override def requires = play.Play && SbtEcho

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Echo)(tracePlaySettings) ++ echoPlayRunSettings

  def tracePlaySettings(): Seq[Setting[_]] = Seq(
    tracePlayVersion <<= playVersion map supportedPlayVersion,
    traceDependencies <<= (libraryDependencies, tracePlayVersion, echoVersion) map tracePlayDependencies)
}
