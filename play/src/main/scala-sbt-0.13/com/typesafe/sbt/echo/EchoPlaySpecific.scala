/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt
package echo

import sbt._
import sbt.Keys._
import sbt.Project.Initialize
import play.PlayImport._
import PlayKeys.playRunHooks
import play.Play.{ playRunTask, playReloaderClasspath, playReloaderClassLoader, playAssetsClassLoader }

object EchoPlaySpecific {
  import SbtEcho.Echo
  import SbtEcho.EchoKeys._

  def echoPlaySpecificSettings(): Seq[Setting[_]] = Seq(
    playRunHooks in Echo <<= playRunHooks,
    playRunHooks in Echo <+= EchoPlayRun.createRunHook,
    run in Echo <<= echoPlayRunTask)

  def echoPlayRunTask: Initialize[InputTask[Unit]] = Def.inputTask {
    if ((echoTraceSupported in Echo).value) {
      streams.value.log.info(s"Running Play application with Inspect tracing enabled.")
      playRunTask(playRunHooks in Echo,
        externalDependencyClasspath in Echo,
        weavingClassLoader in Echo,
        playReloaderClasspath,
        playReloaderClassLoader,
        playAssetsClassLoader).evaluated
    } else {
      val message = s"Inspect tracing does not work with this project. ${(echoAkkaVersionReport in Echo).value} ${(echoPlayVersionReport in Echo).value}"
      streams.value.log.error(message)
      sys.error(message)
    }
  }
}
