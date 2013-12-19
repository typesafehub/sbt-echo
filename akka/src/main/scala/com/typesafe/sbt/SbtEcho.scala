/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt

import sbt._
import sbt.Keys._
import java.net.URI

object SbtEcho extends Plugin {
  import echo.EchoRun._

  val EchoVersion = "0.1.0"
  val AspectjVersion = "1.7.3"

  val Echo = config("echo").extend(Compile)
  val EchoTest = config("echo-test").extend(Echo, Test)

  object EchoKeys {
    val echoVersion = SettingKey[String]("echo-version")
    val aspectjVersion = SettingKey[String]("echo-aspectj-version")

    val echoDirectory = SettingKey[File]("echo-directory")
    val echoConfigDirectory = SettingKey[File]("echo-config-directory")

    val tracePort = TaskKey[Int]("echo-trace-port")

    val node = SettingKey[String]("echo-node")
    val traceable = SettingKey[Seq[(String, Boolean)]]("echo-traceable")
    val traceableConfigString = SettingKey[String]("echo-traceable-config-string")
    val sampling = SettingKey[Seq[(String, Int)]]("echo-sampling")
    val samplingConfigString = SettingKey[String]("echo-sampling-config-string")
    val traceConfigString = TaskKey[String]("echo-trace-config-string")
    val includeConfig = TaskKey[Seq[String]]("echo-include-config")
    val traceConfigIncludes = TaskKey[String]("echo-trace-config-includes")
    val traceConfig = TaskKey[File]("echo-trace-config")
    val traceConfigClasspath = TaskKey[Classpath]("echo-trace-config-classpath")
    val aspectjWeaver = TaskKey[Option[File]]("echo-aspectj-weaver")
    val sigarDependency = TaskKey[Option[File]]("echo-sigar-dependency")
    val sigarLibs = TaskKey[Option[File]]("echo-sigar-libs")
    val sigar = TaskKey[Sigar]("echo-sigar")
    val traceOptions = TaskKey[Seq[String]]("echo-trace-options")
    val traceAkkaVersion = TaskKey[Option[String]]("echo-trace-akka-version")
    val traceDependencies = TaskKey[Seq[ModuleID]]("echo-trace-dependencies")

    // play keys
    val tracePlayVersion = TaskKey[Option[String]]("echo-trace-play-version")
    val weavingClassLoader = TaskKey[(String, Array[URL], ClassLoader) => ClassLoader]("echo-weaving-class-loader")
  }

  import EchoKeys._

  lazy val echoSettings: Seq[Setting[_]] = echoCompileSettings

  def echoCompileSettings: Seq[Setting[_]] =
    inConfig(Echo)(echoDefaultSettings(Runtime, EchoTraceCompile)) ++
    inConfig(Echo)(echoRunSettings(Compile)) ++
    echoUnscopedSettings

  def echoTestSettings: Seq[Setting[_]] =
    inConfig(EchoTest)(echoDefaultSettings(Test, EchoTraceTest)) ++
    inConfig(EchoTest)(echoRunSettings(Test))

  def echoDefaultSettings(extendConfig: Configuration, classpathConfig: Configuration): Seq[Setting[_]] = Seq(
    echoVersion := EchoVersion,
    aspectjVersion := AspectjVersion,

    echoDirectory <<= target / targetName(extendConfig),
    echoConfigDirectory <<= echoDirectory / "conf",

    tracePort := 28667,

    node <<= name,
    traceable := Seq("*" -> true),
    traceableConfigString <<= traceable apply { s => seqToConfig(s, indent = 6, quote = true) },
    sampling := Seq("*" -> 1),
    samplingConfigString <<= sampling apply { s => seqToConfig(s, indent = 6, quote = true) },
    traceConfigString <<= (node, traceableConfigString, samplingConfigString, tracePort) map defaultTraceConfig,
    includeConfig := Seq.empty,
    traceConfigIncludes <<= includeConfig map includeEchoConfig,
    traceConfig <<= writeTraceConfig("trace", traceConfigString, traceConfigIncludes),
    traceConfigClasspath <<= traceConfig map createClasspath,
    aspectjWeaver <<= findAspectjWeaver,
    sigarDependency <<= findSigar,
    sigarLibs <<= unpackSigar,
    sigar <<= (sigarDependency, sigarLibs) map Sigar,
    traceOptions <<= (aspectjWeaver, sigarLibs) map traceJavaOptions,

    traceAkkaVersion <<= libraryDependencies map selectAkkaVersion,
    traceDependencies <<= (libraryDependencies, traceAkkaVersion, echoVersion, scalaVersion) map selectTraceDependencies,

    unmanagedClasspath <<= unmanagedClasspath in extendConfig,
    managedClasspath <<= collectTracedClasspath(classpathConfig),
    managedClasspath <<= Classpaths.concat(managedClasspath, traceConfigClasspath),
    internalDependencyClasspath <<= internalDependencyClasspath in extendConfig,
    externalDependencyClasspath <<= Classpaths.concat(unmanagedClasspath, managedClasspath),
    dependencyClasspath <<= Classpaths.concat(internalDependencyClasspath, externalDependencyClasspath),
    exportedProducts <<= exportedProducts in extendConfig,
    fullClasspath <<= Classpaths.concatDistinct(exportedProducts, dependencyClasspath)
  )

  def echoRunSettings(extendConfig: Configuration): Seq[Setting[_]] = Seq(
    mainClass in run <<= mainClass in run in extendConfig,
    inTask(run)(Seq(runner <<= echoRunner)).head,
    run <<= Defaults.runTask(fullClasspath, mainClass in run, runner in run),
    runMain <<= Defaults.runMainTask(fullClasspath, runner in run)
  )

  def echoUnscopedSettings: Seq[Setting[_]] = Seq(
    ivyConfigurations ++= Seq(EchoTraceCompile, EchoTraceTest, EchoWeave, EchoSigar),

    resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",

    libraryDependencies <++= (aspectjVersion in Echo)(weaveDependencies),
    libraryDependencies <++= (echoVersion in Echo)(sigarDependencies),

    allDependencies <++= traceDependencies in Echo
  )

  def traceAkka(akkaVersion: String) = {
    traceAkkaVersion in Echo := supportedAkkaVersion(akkaVersion)
  }
}
