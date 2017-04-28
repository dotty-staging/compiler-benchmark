name := "compiler-benchmark"

version := "1.0-SNAPSHOT"

scalaVersion in ThisBuild := "2.11.8"

// Convenient access to builds from PR validation
resolvers ++= (
  if (scalaVersion.value.endsWith("-SNAPSHOT"))
    List(
      "pr-scala snapshots old" at "http://private-repo.typesafe.com/typesafe/scala-pr-validation-snapshots/",
      "pr-scala snapshots" at "https://scala-ci.typesafe.com/artifactory/scala-pr-validation-snapshots/",
      Resolver.mavenLocal,
      Resolver.sonatypeRepo("snapshots")
    )
  else
    Nil
)

lazy val infrastructure = addJmh(project).settings(
  description := "Infrastrucuture to persist benchmark results annotated with metadata from Git",
  autoScalaLibrary := false,
  crossPaths := false,
  libraryDependencies ++= Seq(
    "org.influxdb" % "influxdb-java" % "2.5", // TODO update to 2.6 when released for fix for https://github.com/influxdata/influxdb-java/issues/269
    "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r",
    "com.google.guava" % "guava" % "21.0",
    "org.apache.commons" % "commons-lang3" % "3.5",
    "com.typesafe" % "config" % "1.3.1",
    "org.slf4j" % "slf4j-api" % "1.7.24",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.24",  // for any java classes looking for this
    "ch.qos.logback" % "logback-classic" % "1.2.1"
  )
)

val dottyVersion = settingKey[String]("Dotty version to be benchmarked.")

dottyVersion in ThisBuild := dottyLatestNightlyBuild.get

// Compilation project
// Needs to depend on both scalac and dotc and compile as a simple scala 2.11.8 maybe
lazy val compilation = project
  .enablePlugins(JmhPlugin)
  .settings(
    description := "Black box benchmark of the compilers",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    libraryDependencies += "ch.epfl.lamp" % "dotty-compiler_2.11" % dottyVersion.value,
    libraryDependencies += "ch.epfl.lamp" % "dotty-library_2.11" % dottyVersion.value,
    libraryDependencies += ScalaArtifacts.Organization % ScalaArtifacts.LibraryID % "2.11.5",
    resolvers ++= (
      if (dottyVersion.value.endsWith("-SNAPSHOT"))
        List(
          Resolver.mavenLocal,
          Resolver.sonatypeRepo("snapshots")
        )
      else List(Resolver.mavenLocal))
  ).settings(addJavaOptions).dependsOn(infrastructure)

lazy val micro = addJmh(project).settings(
  description := "Finer grained benchmarks of compiler internals",
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
).settings(addJavaOptions)

lazy val jvm = addJmh(project).settings(
  description := "Pure Java benchmarks for demonstrating performance anomalies independent from the Scala language/library",
  autoScalaLibrary := false,
  crossPaths := false
).settings(addJavaOptions).dependsOn(infrastructure)

lazy val addJavaOptions = javaOptions ++= {
  def refOf(version: String) = {
    val HasSha = """.*(?:bin|pre)-([0-9a-f]{7,})(?:-.*)?""".r
    version match {
      case HasSha(sha) => sha
      case _ => "v" + version
    }
  }
  List(
    "-DscalaVersion=" + scalaVersion.value,
    "-DscalaRef=" + refOf(scalaVersion.value)
  )
}

addCommandAlias("hot", "compilation/jmh:run HotScalacBenchmark")

addCommandAlias("cold", "compilation/jmh:run ColdScalacBenchmark")

def addJmh(project: Project): Project = {
  // IntelliJ SBT project import doesn't like sbt-jmh's default setup, which results the prod and test
  // output paths overlapping. This is because sbt-jmh declares the `jmh` config as extending `test`, but
  // configures `classDirectory in Jmh := classDirectory in Compile`.
  project.enablePlugins(JmhPlugin).overrideConfigs(config("jmh").extend(Compile))
}
