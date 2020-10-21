import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport.{releaseCrossBuild, _}

// Dependencies
lazy val scalatestVersion = "3.2.0"
lazy val scalacheckVersion = "1.14.3"
lazy val commonIOVersion = "2.5"
lazy val logbackVersion = "1.1.7"
lazy val scoptVersion = "3.7.1"
lazy val akkaVersion = "2.5.31"
lazy val spark3Version = "3.0.1"
lazy val spark2Version = "2.4.7"

// Releases versions
lazy val scala213 = "2.13.3"
lazy val scala212 = "2.12.12"
lazy val scalaVersions = List(scala213, scala212)

lazy val commonSettings = Seq(
  crossScalaVersions := scalaVersions,
  organization := "com.acervera.osm4scala",
  organizationHomepage := Some(url("http://www.acervera.com")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage in ThisBuild := Some(
    url(s"https://github.com/simplexspatial/osm4scala")
  ),
  scmInfo in ThisBuild := Some(
    ScmInfo(
      url("https://github.com/simplexspatial/osm4scala"),
      "scm:git:git://github.com/simplexspatial/osm4scala.git",
      "scm:git:ssh://github.com:simplexspatial/osm4scala.git"
    )
  ),
  developers in ThisBuild := List(
    Developer(
      "angelcervera",
      "Angel Cervera Claudio",
      "angelcervera@silyan.com",
      url("https://www.acervera.com")
    )
  ),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % scalatestVersion % Test,
    "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test,
    "commons-io" % "commons-io" % commonIOVersion % Test
  ),
  test in assembly := {},
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-encoding", "utf8",
    "-deprecation",
    "-unchecked",
    "-Xlint"
  ),
  javacOptions ++= Seq(
    "-Xlint:all",
    "-source",
    "1.8",
    "-target",
    "1.8",
    "-parameters"
  )
)

lazy val disablingPublishingSettings =
  Seq(skip in publish := true, publishArtifact := false)

lazy val enablingPublishingSettings = Seq(
  publishArtifact := true, // Enable publish
  publishMavenStyle := true,
  publishArtifact in Test := false,
  // Bintray
  bintrayPackageLabels := Seq("scala", "osm", "openstreetmap"),
  bintrayRepository := "maven",
  bintrayVcsUrl := Some("https://github.com/simplexspatial/osm4scala.git")
)

lazy val disablingCoverage = Seq(coverageEnabled := false)

lazy val coverageConfig =
  Seq(coverageMinimum := 80, coverageFailOnMinimum := true)

lazy val exampleSettings = disablingPublishingSettings ++ disablingCoverage

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
lazy val root = (project in file("."))
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    core,
    spark2,
    spark2FatShaded,
    spark3,
    spark3FatShaded,
    commonUtilities,
    examplesCounter,
    examplesCounterParallel,
    examplesCounterAkka,
    examplesTagsExtraction,
    examplesPrimitivesExtraction,
    exampleSparkUtilities
  )
  .settings(
    name := "osm4scala-root",
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil,
    publish / skip := true,
    // don't use sbt-release's cross facility
    releaseCrossBuild := false,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publish"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

lazy val core = Project(id = "core", base = file("core"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    enablingPublishingSettings,
    coverageConfig,
    coverageExcludedPackages := "org.openstreetmap.osmosis.osmbinary.*",
    name := "osm4scala-core",
    description := "Scala OpenStreetMap Pbf 2 parser. Core",
    bintrayPackage := "osm4scala-core",
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = false) -> (sourceManaged in Compile).value
    )
  )

lazy val spark2 = Project(id = "spark2", base = file("spark2"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    crossScalaVersions := Seq(scala212),
    enablingPublishingSettings,
    coverageConfig,
    name := "osm4scala-spark3",
    description := "Spark 2 connector for OpenStreetMap Pbf parser.",
    bintrayPackage := "osm4scala-spark2",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % spark2Version % Provided
    ),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeScala = false,
      cacheUnzip = false,
      cacheOutput = false
    ),
    assemblyShadeRules in assembly := Seq(
      ShadeRule
        .rename("com.google.protobuf.**" -> "shadeproto.@1")
        .inAll
    )
  )
  .dependsOn(core)

lazy val spark2FatShaded = Project(id = "osm4scala-spark2-shaded", base = file("osm4scala-spark2-shaded"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    crossScalaVersions := Seq(scala212),
    enablingPublishingSettings,
    disablingCoverage,
    name := "osm4scala-spark2-shaded",
    description := "Spark 2 connector for OpenStreetMap Pbf parser as shaded fat jar.",
    bintrayPackage := "osm4scala-spark2-shaded",
    packageBin in Compile := (assembly in (spark2, Compile)).value
  )

lazy val spark3 = Project(id = "spark3", base = file("spark3"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    crossScalaVersions := Seq(scala212),
    enablingPublishingSettings,
    coverageConfig,
    name := "osm4scala-spark3",
    description := "Spark 3 connector for OpenStreetMap Pbf parser.",
    bintrayPackage := "osm4scala-spark3",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % spark3Version % Provided
    ),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeScala = false,
      cacheUnzip = false,
      cacheOutput = false
    ),
    assemblyShadeRules in assembly := Seq(
      ShadeRule
        .rename("com.google.protobuf.**" -> "shadeproto.@1")
        .inAll
    )
  )
  .dependsOn(core)

lazy val spark3FatShaded = Project(id = "osm4scala-spark3-shaded", base = file("osm4scala-spark3-shaded"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    crossScalaVersions := Seq(scala212),
    enablingPublishingSettings,
    disablingCoverage,
    name := "osm4scala-spark3-shaded",
    description := "Spark 3 connector for OpenStreetMap Pbf parser as shaded fat jar.",
    bintrayPackage := "osm4scala-spark3-shaded",
    packageBin in Compile := (assembly in (spark3, Compile)).value
  )

// Examples

lazy val commonUtilities = Project(id = "examples-common-utilities", base = file("examples/common-utilities"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    exampleSettings,
    skip in publish := true,
    name := "osm4scala-examples-common-utilities",
    description := "Utilities shared by all examples",
    libraryDependencies ++= Seq("com.github.scopt" %% "scopt" % scoptVersion)
  )
  .disablePlugins(AssemblyPlugin)

lazy val examplesCounter =
  Project(id = "examples-counter", base = file("examples/counter"))
    .disablePlugins(AssemblyPlugin)
    .settings(
      commonSettings,
      exampleSettings,
      name := "osm4scala-examples-counter",
      description := "Counter of primitives (Way, Node, Relation or All) using osm4scala"
    )
    .dependsOn(core, commonUtilities)

lazy val examplesCounterParallel = Project(id = "examples-counter-parallel", base = file("examples/counter-parallel"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    exampleSettings,
    name := "osm4scala-examples-counter-parallel",
    description := "Counter of primitives (Way, Node, Relation or All) using osm4scala in parallel threads"
  )
  .dependsOn(core, commonUtilities)

lazy val examplesCounterAkka = Project(id = "examples-counter-akka", base = file("examples/counter-akka"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    exampleSettings,
    name := "osm4scala-examples-counter-akka",
    description := "Counter of primitives (Way, Node, Relation or All) using osm4scala in parallel with AKKA",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion
    )
  )
  .dependsOn(core, commonUtilities)

lazy val examplesTagsExtraction = Project(id = "examples-tag-extraction", base = file("examples/tagsextraction"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    exampleSettings,
    name := "osm4scala-examples-tags-extraction",
    description := "Extract all unique tags from the selected primitive type (Way, Node, Relation or All) using osm4scala"
  )
  .dependsOn(core, commonUtilities)

lazy val examplesBlocksExtraction = Project(id = "examples-blocks-extraction", base = file("examples/blocksextraction"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    exampleSettings,
    name := "osm4scala-examples-blocks-extraction",
    description := "Extract all blocks from the pbf into a folder using osm4scala."
  )
  .dependsOn(core, commonUtilities)

lazy val examplesPrimitivesExtraction =
  Project(id = "examples-primitives-extraction", base = file("examples/primitivesextraction"))
    .disablePlugins(AssemblyPlugin)
    .settings(
      commonSettings,
      exampleSettings,
      name := "osm4scala-examples-primitives-extraction",
      description := "Extract all primitives from the pbf into a folder using osm4scala."
    )
    .dependsOn(core, commonUtilities)

lazy val exampleSparkUtilities = Project(id = "examples-spark-utilities", base = file("examples/spark-utilities"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    commonSettings,
    exampleSettings,
    crossScalaVersions := Seq(scala212),
    name := "osm4scala-examples-spark-utilities",
    description := "Example of different utilities using osm4scala and Spark.",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % spark3Version % Provided
    )
  )
  .dependsOn(spark3, commonUtilities)
