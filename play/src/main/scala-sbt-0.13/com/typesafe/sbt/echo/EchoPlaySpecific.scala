/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt
package echo

import sbt._
import sbt.Keys._
import play.Keys.playRunHooks
import play.Project.{ playRunTask, playReloaderClasspath, playReloaderClassLoader }

object EchoPlaySpecific {
  import SbtEcho.Echo
  import SbtEcho.EchoKeys._

  def echoPlaySpecificSettings(): Seq[Setting[_]] = Seq(
    playRunHooks in Echo <<= playRunHooks,
    playRunHooks in Echo <+= EchoPlayRun.createRunHook,
    run in Echo <<= playRunTask(playRunHooks in Echo, externalDependencyClasspath in Echo, weavingClassLoader in Echo, playReloaderClasspath, playReloaderClassLoader)
  )
}
