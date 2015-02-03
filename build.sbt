val buildSettings = Seq(
  organization := "com.blinkbox.books",
  name := "purchase-transformer",
  version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0"),
  scalaVersion  := "2.10.4",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7"),
  javacOptions := Seq("-source", "1.7", "-target", "1.7", "-Xlint:unchecked")
)

val dependencySettings = Seq(
  libraryDependencies ++= {
    val akkaV = "2.3.6"
    Seq(
    "com.blinkbox.books" %% "common-config"        % "1.4.1",
    "com.blinkbox.books" %% "common-messaging"     % "1.1.5",
    "com.blinkbox.books.hermes" %% "rabbitmq-ha"   % "7.1.0",
    "com.blinkbox.books" %% "common-spray"         % "0.17.3",
    "com.blinkbox.books" %% "common-scala-test"    % "0.3.0" % Test,
    "org.json4s"         %% "json4s-jackson"       % "3.2.10",
    "xmlunit"             % "xmlunit"              % "1.5" % Test,
    "com.typesafe.akka"  %% "akka-actor"           % akkaV,
    "com.typesafe.akka"  %% "akka-slf4j"           % akkaV,
    "com.typesafe.akka"  %% "akka-testkit"         % akkaV % Test,
    "joda-time"           % "joda-time"            % "2.3",
    "org.joda"            % "joda-convert"         % "1.6",
    "net.sf.saxon"        % "Saxon-HE"             % "9.5.1-5"
    )
  }
)

parallelExecution in Test := false

val root = (project in file(".")).
  settings(buildSettings: _*).
  settings(dependencySettings: _*)
