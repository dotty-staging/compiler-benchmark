import java.io.BufferedInputStream
import java.io.StringReader

name := "compiler-benchmark"

version := "1.0-SNAPSHOT"

val dottyVersion = settingKey[String]("Dotty version to be benchmarked.")

dottyVersion in ThisBuild := sys.env.getOrElse("DOTTY_VERSION",
                                               DottyVersion.latestNightly.get)

scalaVersion in ThisBuild := latestScalacVersion

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

lazy val latestScalacVersion = sys.props.getOrElse(
  "scalacVersion", {
    val view = scala.io.Source.fromURL(
      "https://scala-ci.typesafe.com/job/scala-2.12.x-integrate-bootstrap/lastSuccessfulBuild/artifact/jenkins.properties/*view*/")
    val props = new java.util.Properties()
    props.load(new StringReader(view.mkString))
    val version = props.getProperty("version")
    println("Latest Scala nightly version: " + Some(version))
    version
  }
)

lazy val infrastructure = project
  .enablePlugins(JmhPlugin)
  .settings(
    description := "Infrastrucuture to persist benchmark results annotated with metadata from Git",
    crossPaths := false,
    libraryDependencies ++= Seq(
      "org.influxdb" % "influxdb-java" % "2.5", // TODO update to 2.6 when released for fix for https://github.com/influxdata/influxdb-java/issues/269
      "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r",
      "com.google.guava" % "guava" % "21.0",
      "org.apache.commons" % "commons-lang3" % "3.5",
      "com.typesafe" % "config" % "1.3.1",
      "org.slf4j" % "slf4j-api" % "1.7.24",
      "org.slf4j" % "log4j-over-slf4j" % "1.7.24",
      "ch.qos.logback" % "logback-classic" % "1.2.1"
    )
  )

lazy val dotcRuntime = project
  .in(file("dotc-runtime"))
  .settings(
    scalaVersion := dottyVersion.value,
    libraryDependencies += "ch.epfl.lamp" % "dotty-compiler_0.1" % dottyVersion.value,
    libraryDependencies += "ch.epfl.lamp" % "dotty-library_0.1" % dottyVersion.value
  )

lazy val scalacRuntime = project
  .in(file("scalac-runtime"))
  .settings(
    resolvers += "scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/",
    libraryDependencies += ScalaArtifacts.Organization % ScalaArtifacts.CompilerID % latestScalacVersion,
    libraryDependencies += ScalaArtifacts.Organization % ScalaArtifacts.LibraryID % latestScalacVersion
  )

lazy val compilation = project
  .enablePlugins(JmhPlugin)
  .settings(
    scalaVersion := "2.11.11",
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    description := "Black box benchmark of the compilers",
    fork in run := true,
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
    testOptions in Test += Tests.Argument(TestFrameworks.JUnit),
    buildInfoKeys := Seq[BuildInfoKey](
      dottyVersion,
      BuildInfoKey.map(fullClasspath.in(dotcRuntime, Compile)) {
        case (_, cp) =>
          val dottyClasspath = cp.map(_.data.getAbsolutePath)
          "dotcClasspath" -> dottyClasspath.mkString(
            java.io.File.pathSeparator)
      },
      BuildInfoKey.map(fullClasspath.in(scalacRuntime, Compile)) {
        case (_, cp) =>
          val scalacClasspath = cp.map(_.data.getAbsolutePath)
          "scalacClasspath" -> scalacClasspath.mkString(
            java.io.File.pathSeparator)
      }
    )
  )
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(infrastructure)

lazy val micro = project
  .enablePlugins(JmhPlugin)
  .settings(
    description := "Finer grained benchmarks of compiler internals",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
  )

lazy val jvm = project
  .enablePlugins(JmhPlugin)
  .settings(
    description := "Pure Java benchmarks for demonstrating performance anomalies independent from the Scala language/library",
    autoScalaLibrary := false,
    crossPaths := false
  )

val ui = project.settings(
  scalaVersion := "2.11.8",
  libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.3",
  libraryDependencies += "com.h2database" % "h2" % "1.4.192"
)

lazy val DottyRef = ".*-([^-]+)-NIGHTLY".r
lazy val ScalaRef = ".*-([^-]+)".r
lazy val timestamp =
  new java.text.SimpleDateFormat("yyyyMMdd_kkmmss")
    .format(new java.util.Date())

lazy val batchTasks = taskKey[List[String]]("")
batchTasks := tasks
lazy val tasks = {
  val dottyLatestVersion @ DottyRef(dottyRef) = dottyLatestNightlyBuild.get
  val ScalaRef(scalaRef) = latestScalacVersion
  for {
    (compiler, sourceDirectory, version, ref) <- List(
      ("Dotc", "dotty", dottyLatestVersion, dottyRef),
      ("Scalac", "scala", latestScalacVersion, scalaRef)
    )
    scalaVersion = s" -DscalaVersion=$version"
    baseSourceDir = sys.props
      .getOrElse("gitrepos", "/home/benchs")
      .stripSuffix("/")
    benchmarkTimestamp = s" -DbenchmarkTimestamp=$timestamp"
    scalaRef = s" -DscalaRef=$ref"
    localdir = s" -Dgit.localdir=$baseSourceDir/$sourceDirectory"
    sysProps = s"$scalaVersion $scalaRef $localdir $benchmarkTimestamp"
    runUpload = s"compilation/jmh:runMain $sysProps scala.bench.UploadingRunner "
    inputProject <- List("vector", "squants", "paiges")
    source = s"-p source=$inputProject"
    bench = s"(Cold|Warm|Hot)${compiler}Benchmark"
  } yield s"""; clean ; $runUpload $bench $source """.stripMargin
}

commands += Command.command("runBatch") { s =>
  tasks.foldLeft(s) {
    case (state, task) =>
      println(s"Running command: $task")
      task :: state
  }
}

addCommandAlias("hot-scalac", "compilation/jmh:run HotScalacBenchmark")
addCommandAlias("cold-scalac", "compilation/jmh:run ColdScalacBenchmark")
addCommandAlias("warm-scalac", "compilation/jmh:run WarmScalacBenchmark")
addCommandAlias("hot-dotc", "compilation/jmh:run HotDotcBenchmark")
addCommandAlias("cold-dotc", "compilation/jmh:run ColdDotcBenchmark")
addCommandAlias("warm-dotc", "compilation/jmh:run WarmDotcBenchmark")
commands += runBoth("cold")
commands += runBoth("warm")
commands += runBoth("hot")
def runBoth(cmd: String) = Command.args(cmd, cmd) {
  case (state, args) =>
    s"$cmd-dotc " + args.mkString(" ") ::
      s"$cmd-scalac " + args.mkString(" ") ::
      state
}
