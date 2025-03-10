/**
 * Copyright (c) 2014-2022 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

// SBT
import sbt._
import Keys._

import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerExposedPorts, dockerUpdateLatest}
import sbtdynver.DynVerPlugin.autoImport._

object BuildSettings {

  lazy val compilerOptions = Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused-import",
    "-Xfuture",
    "-Xlint"
  )

  lazy val javaCompilerOptions = Seq(
    "-source", "11",
    "-target", "11"
  )

  lazy val dockerSettings =
    Seq(
      Docker / packageName := "elasticsearch-loader",
      dockerRepository := Some("snowplow"),
      dockerBaseImage := "eclipse-temurin:11-jre-focal",
      Docker / maintainer := "Snowplow Analytics Ltd. <support@snowplowanalytics.com>",
      Docker / daemonUser := "daemon",
      dockerCmd := Seq("--help"),
      dockerUpdateLatest := true,
      Docker / daemonUserUid := None,
      Docker / defaultLinuxInstallLocation := "/opt/snowplow"
    )

  // Makes our SBT app settings available from within the app
  lazy val scalifySettings = Seq(
    Compile / sourceGenerators += Def.task {
      val dir = (Compile / sourceManaged).value
      val file = dir / "settings.scala"
      IO.write(file, """package com.snowplowanalytics.stream.loader.generated
        |object Settings {
        |  val organization = "%s"
        |  val version = "%s"
        |  val name = "%s"
        |}
        |""".stripMargin.format(organization.value, version.value, moduleName.value))

      Seq(file)
    }.taskValue
  )

  // sbt-assembly settings for building an executable
  import sbtassembly.AssemblyPlugin.autoImport._
  lazy val assemblySettings = Seq(
    assembly / assemblyJarName := { s"${name.value}-${version.value}.jar" },
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case x if x.endsWith("module-info.class") => MergeStrategy.discard // not used by JDK8
      case "META-INF/io.netty.versions.properties" => MergeStrategy.first
      case PathList("org", "joda", "time", "base", "BaseDateTime.class") => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )

  lazy val dynVerSettings = Seq(
    ThisBuild / dynverVTagPrefix := false, // Otherwise git tags required to have v-prefix
    ThisBuild / dynverSeparator := "-" // to be compatible with docker
    )


  lazy val addExampleConfToTestCp = Seq(
    Test / unmanagedClasspath += {
      baseDirectory.value.getParentFile / "config"
    }
  )
}
