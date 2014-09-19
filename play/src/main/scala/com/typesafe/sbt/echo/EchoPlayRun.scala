/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt
package echo

import sbt._
import sbt.Keys._
import play.Play.ClassLoaderCreator
import org.aspectj.weaver.loadtime.WeavingURLClassLoader

object EchoPlayRun {
  import EchoRun._
  import SbtEcho.Echo
  import SbtEcho.EchoKeys._

  val Play21Version = "2.1.5"
  val Play22Version = "2.2.3"
  val Play23Version = "2.3.3"

  def echoPlayRunSettings(): Seq[Setting[_]] = Seq(
    weavingClassLoader in Echo <<= (sigar in Echo) map createWeavingClassLoader) ++ EchoPlaySpecific.echoPlaySpecificSettings

  def tracePlayDependencies(dependencies: Seq[ModuleID], tracePlayVersion: Option[String], echoVersion: String): Seq[ModuleID] = {
    if (containsTracePlay(dependencies)) Seq.empty[ModuleID]
    else tracePlayVersion match {
      case Some(playVersion) => Seq(tracePlayDependency(playVersion, echoVersion))
      case None => Seq.empty[ModuleID]
    }
  }

  def tracePlayDependency(playVersion: String, echoVersion: String): ModuleID =
    if (playVersion startsWith "2.3.") "com.typesafe.trace" % ("trace-play-" + playVersion) % echoVersion % EchoTraceCompile.name cross CrossVersion.binary
    else "com.typesafe.trace" % ("trace-play-" + playVersion) % echoVersion % EchoTraceCompile.name cross CrossVersion.Disabled

  def supportedPlayVersion(playVersion: String): Option[String] = {
    if (playVersion startsWith "2.1.") Some(Play21Version)
    else if (playVersion startsWith "2.2.") Some(Play22Version)
    else if (playVersion startsWith "2.3.") Some(Play23Version)
    else None
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
