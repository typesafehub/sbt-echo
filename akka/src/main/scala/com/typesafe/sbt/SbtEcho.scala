/**
 *  Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
package com.typesafe.sbt

import sbt._
import sbt.Keys._
import java.net.URI

object SbtEcho extends Plugin {
  import echo.EchoController
  import echo.EchoRun._
  import echo.DevNullLogger

  val AtmosVersion = "1.3.1"

  val Echo = config("echo").extend(Compile)
  val EchoTest = config("echo-test").extend(Echo, Test)

  object EchoKeys {
    val atmosVersion = SettingKey[String]("atmos-version")
    val atmosUseProGuardedVersion = SettingKey[Boolean]("atmos-use-proguarded-version")
    val aspectjVersion = SettingKey[String]("aspectj-version")

    val echoDirectory = SettingKey[File]("echo-directory")
    val echoConfigDirectory = SettingKey[File]("echo-config-directory")
    val echoLogDirectory = SettingKey[File]("echo-log-directory")

    val traceOnly = TaskKey[Boolean]("trace-only")

    val defaultAtmosPort = SettingKey[Int]("default-atmos-port")
    val defaultConsolePort = SettingKey[Int]("default-console-port")
    val defaultTracePort = SettingKey[Int]("default-trace-port")

    val atmosPort = TaskKey[Int]("atmos-port")
    val consolePort = TaskKey[Int]("console-port")
    val tracePort = TaskKey[Int]("trace-port")

    val atmosJvmOptions = TaskKey[Seq[String]]("atmos-jvm-options")
    val atmosConfigString = TaskKey[String]("atmos-config-string")
    val atmosLogbackString = SettingKey[String]("atmos-logback-string")
    val atmosConfig = TaskKey[File]("atmos-config")
    val atmosConfigClasspath = TaskKey[Classpath]("atmos-config-classpath")
    val atmosManagedClasspath = TaskKey[Classpath]("atmos-managed-classpath")
    val atmosClasspath = TaskKey[Classpath]("atmos-classpath")

    val consoleJvmOptions = TaskKey[Seq[String]]("console-jvm-options")
    val consoleConfigString = TaskKey[String]("console-config-string")
    val consoleLogbackString = SettingKey[String]("console-logback-string")
    val consoleConfig = TaskKey[File]("console-config")
    val consoleConfigClasspath = TaskKey[Classpath]("console-config-classpath")
    val consoleManagedClasspath = TaskKey[Classpath]("console-managed-classpath")
    val consoleClasspath = TaskKey[Classpath]("console-classpath")

    val node = SettingKey[String]("node")
    val traceable = SettingKey[Seq[(String, Boolean)]]("traceable")
    val traceableConfigString = SettingKey[String]("traceable-config-string")
    val sampling = SettingKey[Seq[(String, Int)]]("sampling")
    val samplingConfigString = SettingKey[String]("sampling-config-string")
    val traceConfigString = TaskKey[String]("trace-config-string")
    val includeConfig = TaskKey[Seq[String]]("include-config")
    val traceConfigIncludes = TaskKey[String]("trace-config-includes")
    val traceConfig = TaskKey[File]("trace-config")
    val traceConfigClasspath = TaskKey[Classpath]("trace-config-classpath")
    val aspectjWeaver = TaskKey[Option[File]]("aspectj-weaver")
    val sigarDependency = TaskKey[Option[File]]("sigar-dependency")
    val sigarLibs = TaskKey[Option[File]]("sigar-libs")
    val sigar = TaskKey[Sigar]("sigar")
    val traceOptions = TaskKey[Seq[String]]("trace-options")
    val traceAkkaVersion = TaskKey[Option[String]]("trace-akka-version")
    val traceDependencies = TaskKey[Seq[ModuleID]]("trace-dependencies")

    val atmosOptions = TaskKey[EchoOptions]("atmos-options")
    val consoleOptions = TaskKey[EchoOptions]("console-options")
    val echoRunListeners = TaskKey[Seq[URI => Unit]]("echo-run-listeners")
    val echoInputs = TaskKey[EchoInputs]("echo-inputs")

    val start = TaskKey[Unit]("start")
    val stop = TaskKey[Unit]("stop")

    type NodeNamer = (String, String, Seq[String]) => String
    val launchNode = TaskKey[NodeNamer]("launch-node")

    val launch = InputKey[Unit]("launch")
    val launchMain = InputKey[Unit]("launch-main")

    // play keys
    val tracePlayVersion = TaskKey[String]("trace-play-version")
    val weavingClassLoader = TaskKey[(String, Array[URL], ClassLoader) => ClassLoader]("weaving-class-loader")
  }

  import EchoKeys._

  lazy val echoSettings: Seq[Setting[_]] = echoCompileSettings

  def echoCompileSettings: Seq[Setting[_]] =
    inConfig(Echo)(echoDefaultSettings(Runtime, EchoTraceCompile)) ++
    inConfig(Echo)(echoRunSettings(Compile)) ++
    inConfig(Echo)(echoLaunchSettings) ++
    echoUnscopedSettings

  def echoTestSettings: Seq[Setting[_]] =
    inConfig(EchoTest)(echoDefaultSettings(Test, EchoTraceTest)) ++
    inConfig(EchoTest)(echoRunSettings(Test)) ++
    inConfig(EchoTest)(echoLaunchSettings)

  def echoDefaultSettings(extendConfig: Configuration, classpathConfig: Configuration): Seq[Setting[_]] = Seq(
    atmosVersion := AtmosVersion,
    atmosUseProGuardedVersion := true,
    aspectjVersion := "1.7.3",

    echoDirectory <<= target / targetName(extendConfig),
    echoConfigDirectory <<= echoDirectory / "conf",
    echoLogDirectory <<= echoDirectory / "log",

    traceOnly := false,

    defaultAtmosPort := 8660,
    defaultConsolePort := 9900,
    defaultTracePort := 28660,

    atmosPort <<= defaultAtmosPort map EchoController.selectPort,
    consolePort <<= defaultConsolePort map EchoController.selectPort,
    tracePort <<= (traceOnly, defaultTracePort) map { (traceOnly, defaultPort) =>
      if (traceOnly) defaultPort else EchoController.selectTracePort(defaultPort)
    },

    atmosJvmOptions := Seq("-Xms512m", "-Xmx1024m", "-XX:+UseParallelGC"),
    atmosConfigString <<= tracePort map defaultEchoConfig,
    atmosLogbackString <<= defaultLogbackConfig("atmos"),
    atmosConfig <<= writeConfig("atmos", atmosConfigString, atmosLogbackString),
    atmosConfigClasspath <<= atmosConfig map createClasspath,
    atmosManagedClasspath <<= collectManagedClasspath(EchoAtmos),
    atmosClasspath <<= Classpaths.concat(atmosConfigClasspath, atmosManagedClasspath),

    consoleJvmOptions := Seq("-Xms256m", "-Xmx512m"),
    consoleConfigString <<= (name, atmosPort) map defaultConsoleConfig,
    consoleLogbackString <<= defaultLogbackConfig("console"),
    consoleConfig <<= writeConfig("console", consoleConfigString, consoleLogbackString),
    consoleConfigClasspath <<= consoleConfig map createClasspath,
    consoleManagedClasspath <<= collectManagedClasspath(EchoConsole),
    consoleClasspath <<= Classpaths.concat(consoleConfigClasspath, consoleManagedClasspath),

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
    traceDependencies <<= (libraryDependencies, traceAkkaVersion, atmosVersion, scalaVersion) map selectTraceDependencies,

    unmanagedClasspath <<= unmanagedClasspath in extendConfig,
    managedClasspath <<= collectTracedClasspath(classpathConfig),
    managedClasspath <<= Classpaths.concat(managedClasspath, traceConfigClasspath),
    internalDependencyClasspath <<= internalDependencyClasspath in extendConfig,
    externalDependencyClasspath <<= Classpaths.concat(unmanagedClasspath, managedClasspath),
    dependencyClasspath <<= Classpaths.concat(internalDependencyClasspath, externalDependencyClasspath),
    exportedProducts <<= exportedProducts in extendConfig,
    fullClasspath <<= Classpaths.concatDistinct(exportedProducts, dependencyClasspath),

    atmosOptions <<= (atmosPort, atmosJvmOptions, atmosClasspath) map EchoOptions,
    consoleOptions <<= (consolePort, consoleJvmOptions, consoleClasspath) map EchoOptions,
    echoRunListeners := Seq.empty,
    echoRunListeners <+= state map { s => logConsoleUri(s.log)(_) },
    echoInputs <<= (traceOnly, tracePort, javaHome, atmosOptions, consoleOptions, echoRunListeners) map EchoInputs,

    start <<= (echoInputs, streams) map { (inputs, s) => EchoController.start(inputs, s.log, explicit = true) },
    stop <<= streams map { s => EchoController.stop(s.log, explicit = true) }
  )

  def echoRunSettings(extendConfig: Configuration): Seq[Setting[_]] = Seq(
    mainClass in run <<= mainClass in run in extendConfig,
    inTask(run)(Seq(runner <<= echoRunner)).head,
    run <<= Defaults.runTask(fullClasspath, mainClass in run, runner in run),
    runMain <<= Defaults.runMainTask(fullClasspath, runner in run)
  )

  def echoLaunchSettings(): Seq[Setting[_]] = Seq(
    mainClass in launch <<= mainClass in run,
    outputStrategy in launch := Some(LoggedOutput(DevNullLogger)),
    launchNode := { (name, mainClass, args) => name },
    inTask(launch)(Seq(runner <<= echoLauncher)).head,
    launch <<= Defaults.runTask(fullClasspath, mainClass in launch, runner in launch),
    launchMain <<= Defaults.runMainTask(fullClasspath, runner in launch)
  )

  def echoUnscopedSettings: Seq[Setting[_]] = Seq(
    ivyConfigurations ++= Seq(EchoTraceCompile, EchoTraceTest, EchoAtmos, EchoConsole, EchoWeave, EchoSigar),

    resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",

    libraryDependencies <++= (atmosVersion in Echo, atmosUseProGuardedVersion in Echo)(atmosDependencies),
    libraryDependencies <++= (atmosVersion in Echo, atmosUseProGuardedVersion in Echo)(consoleDependencies),
    libraryDependencies <++= (aspectjVersion in Echo)(weaveDependencies),
    libraryDependencies <++= (atmosVersion in Echo)(sigarDependencies),

    allDependencies <++= traceDependencies in Echo
  )

  def traceAkka(akkaVersion: String) = {
    traceAkkaVersion in Echo := Option(akkaVersion) map supportedAkkaVersion
  }
}
