import com.typesafe.sbt.SbtGit
import sbt._
import sbt.Keys._
import net.virtualvoid.sbt.cross.CrossPlugin
import com.typesafe.sbt.SbtGit
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object SbtEchoBuild extends Build {
  def baseVersions: Seq[Setting[_]] = SbtGit.versionWithGit

  lazy val sbtEcho = Project(
    id = "sbt-echo",
    base = file("."),
    settings = defaultSettings ++ noPublishSettings,
    aggregate = Seq(sbtEchoAkka, sbtEchoPlay)
  )

  lazy val sbtEchoAkka = Project(
    id = "sbt-echo-akka",
    base = file("akka"),
    settings = defaultSettings ++ Seq(
      name := "sbt-echo",
      libraryDependencies ++= Seq(Dependency.aspectjTools, Dependency.sbtBackgroundRun)
    )
  )

  lazy val sbtEchoPlay = Project(
    id = "sbt-echo-play",
    base = file("play"),
    dependencies = Seq(sbtEchoAkka),
    settings = defaultSettings ++ Seq(
      name := "sbt-echo-play"
    ) ++ Dependency.playPlugin
  )

  def formatPrefs = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(IndentSpaces, 2)
  }

  lazy val defaultSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ crossBuildSettings ++ baseVersions ++ SbtScalariform.scalariformSettings ++ Seq(
    sbtPlugin := true,
    organization := "com.typesafe.sbt",
    version <<= version in ThisBuild,
    publishMavenStyle := false,
    publishTo <<= isSnapshot { snapshot =>
      if (snapshot) Some(Classpaths.sbtPluginSnapshots) else Some(Classpaths.sbtPluginReleases)
    },
    sbt.CrossBuilding.latestCompatibleVersionMapper ~= {
      original => {
        case "0.13" => "0.13.6"
        case x => original(x)
      }
    },
    ScalariformKeys.preferences in Compile := formatPrefs,
    ScalariformKeys.preferences in Test    := formatPrefs
  )

  lazy val crossBuildSettings: Seq[Setting[_]] = CrossPlugin.crossBuildingSettings ++ CrossBuilding.scriptedSettings ++ Seq(
    CrossBuilding.crossSbtVersions := Seq("0.12", "0.13")
  )

  lazy val noPublishSettings: Seq[Setting[_]] = Seq(
    publish := {},
    publishLocal := {}
  )

  object Dependency {
    val aspectjTools = "org.aspectj" % "aspectjtools" % "1.7.3"
    val sbtBackgroundRun = Defaults.sbtPluginExtra("com.typesafe.sbtrc" % "ui-interface-0-13" % "1.0-d5ba9ed9c1d31e3431aeca5e429d290b56cb0b14", "0.13", "2.10")

    def playPlugin: Seq[Setting[_]] = Seq(
      resolvers += Classpaths.typesafeSnapshots,
      resolvers += "Typesafe Maven Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
      resolvers += "Typesafe Maven Releases" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies <+= (sbtVersion in sbtPlugin, scalaBinaryVersion in update) { (sbtV, scalaV) =>
        val dependency = sbtV match {
          case "0.12" => "play" % "sbt-plugin" % "2.1.5" exclude("com.github.scala-incubator.io", "scala-io-core_2.9.1") exclude("com.github.scala-incubator.io", "scala-io-file_2.9.1")
          case "0.13" => "com.typesafe.play" % "sbt-plugin" % "2.3.3"
          case _ => sys.error("Unsupported sbt version: " + sbtV)
        }
        Defaults.sbtPluginExtra(dependency, sbtV, scalaV)
      }
    )
  }
}
