// Comment to get more information during initialization
logLevel := Level.Warn

// sbt-jmh plugin - pulls in JMH dependencies too
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.16")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.2")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC3")

