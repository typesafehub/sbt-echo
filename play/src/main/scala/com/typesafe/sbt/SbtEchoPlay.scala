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
    // we SHOULD require that the akka version is also supported, but commented out
    // for now because the check for akka does not chase transitive deps, so if
    // someone only explicitly depends on play, the akka code thinks we don't support akka.
    echoTraceSupported := { /* echoTraceSupported.value && */ tracePlayVersion.value.isDefined },
    echoPlayVersionReport := { playVersionReport(Some(playVersion.value)) },
    traceDependencies <<= (libraryDependencies, tracePlayVersion, echoVersion) map tracePlayDependencies)
}
