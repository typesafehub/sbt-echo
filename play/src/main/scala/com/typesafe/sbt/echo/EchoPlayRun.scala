/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt
package echo

import sbt._
import sbt.Keys._
import play.Project.ClassLoaderCreator
import org.aspectj.weaver.loadtime.WeavingURLClassLoader

object EchoPlayRun {
  import EchoRun._
  import SbtEcho.Echo
  import SbtEcho.EchoKeys._

  val Play21Version = "2.1.4"
  val Play22Version = "2.2.0"

  def echoPlayRunSettings(): Seq[Setting[_]] = Seq(
    weavingClassLoader in Echo <<= (sigar in Echo) map createWeavingClassLoader
  ) ++ EchoPlaySpecific.echoPlaySpecificSettings

  def tracePlayDependencies(dependencies: Seq[ModuleID], playVersion: String, echoVersion: String): Seq[ModuleID] = {
    if (containsTracePlay(dependencies)) Seq.empty[ModuleID]
    else Seq("com.typesafe.trace" % ("trace-play-" + playVersion) % echoVersion % EchoTraceCompile.name cross CrossVersion.Disabled)
  }

  def supportedPlayVersion(playVersion: String): String = {
    if      (playVersion startsWith "2.1.") Play21Version
    else if (playVersion startsWith "2.2.") Play22Version
    else    sys.error("Play version is not supported by Activator tracing: " + playVersion)
  }

  def containsTracePlay(dependencies: Seq[ModuleID]): Boolean = dependencies exists { module =>
    module.organization == "com.typesafe.trace" && module.name.startsWith("trace-play")
  }

  def createWeavingClassLoader(sigar: Sigar): ClassLoaderCreator = (name, urls, parent) => new WeavingURLClassLoader(urls, parent) {
    val sigarLoader = SigarClassLoader(sigar)
    override def loadClass(name: String, resolve: Boolean): Class[_] = {
      if (name startsWith "org.hyperic.sigar") sigarLoader.loadClass(name)
      else super.loadClass(name, resolve)
    }
    override def toString = "Weaving" + name + "{" + getURLs.map(_.toString).mkString(", ") + "}"
  }

  def createRunHook = (sigarLibs in Echo) map { (sigar) => new RunHook(sigar) }

  class RunHook(sigarLibs: Option[File]) extends play.PlayRunHook {
    override def beforeStarted(): Unit = {
      System.setProperty("org.aspectj.tracing.factory", "default")
      sys.props.getOrElseUpdate("config.resource", "application.conf")
      sigarLibs foreach { s => System.setProperty("org.hyperic.sigar.path", s.getAbsolutePath) }
    }
    override def afterStopped(): Unit = {}
  }
}
