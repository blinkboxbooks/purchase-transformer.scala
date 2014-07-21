import AssemblyKeys._

val buildSettings = Seq(
  organization := "com.blinkbox.books.hermes",
  name := "purchase-transformer",
  version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0"),
  scalaVersion  := "2.10.4",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7"),
  javacOptions := Seq("-source", "1.7", "-target", "1.7", "-Xlint:unchecked")
)

val dependencySettings = Seq(
  libraryDependencies ++= {
    val akkaV = "2.3.3"
    val sprayV = "1.3.1"
    Seq(
    "com.blinkbox.books" %% "common-config"        % "0.7.0",
    "com.blinkbox.books" %% "common-messaging"     % "0.4.0",
    "com.blinkbox.books.hermes" %% "rabbitmq-ha"   % "4.0.0",
    "ch.qos.logback"      % "logback-classic"      % "1.1.2",
    "io.spray"            % "spray-client"         % sprayV,
    "io.spray"            % "spray-http"           % sprayV,
    "io.spray"            % "spray-httpx"          % sprayV,
    "io.spray"           %% "spray-json"           % "1.2.6",
    "org.json4s"         %% "json4s-jackson"       % "3.2.10",
    "org.scalatest"      %% "scalatest"            % "2.2.0" % "test",
    "junit"               % "junit"                % "4.11" % "test",
    "com.novocode"        % "junit-interface"      % "0.10" % "test",
    "com.h2database"      % "h2"                   % "1.3.173" % "test",
    "org.mockito"         % "mockito-core"         % "1.9.5" % "test",
    "xmlunit"             % "xmlunit"              % "1.5" % "test",
    "com.typesafe.akka"  %% "akka-actor"           % akkaV,
    "com.typesafe.akka"  %% "akka-slf4j"           % akkaV,
    "com.typesafe.akka"  %% "akka-testkit"         % akkaV % "test",
    "joda-time"           % "joda-time"            % "2.3",
    "org.joda"            % "joda-convert"         % "1.6",
    "net.sf.saxon"        % "Saxon-HE"             % "9.5.1-5"
    )
  }
)


val publishSettings = Seq(
  mergeStrategy in assembly <<= (mergeStrategy in assembly) { old =>
    {
      case "application.conf" => MergeStrategy.discard
      case x => old(x)
    }
  },
  artifact in (Compile, assembly) ~= { art => art.copy(`classifier` = Some("assembly")) },
  publishArtifact in (Compile, packageBin) := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false 
) ++ addArtifact(artifact in (Compile, assembly), assembly).settings

val root = (project in file(".")).
  settings(rpmPrepSettings: _*).
  settings(publishSettings: _*).
  settings(buildSettings: _*).
  settings(dependencySettings: _*)
