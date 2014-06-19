name := """purchase-transformer"""

version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0")

organization := "com.blinkbox.books.platform.services"

scalaVersion := "2.10.4"

libraryDependencies ++= {
  val akkaV = "2.3.2"
  val sprayV = "1.3.1"
  Seq(
  "com.blinkbox.books" %% "common-config"        % "0.2.1",
  "com.blinkbox.books" %% "common-messaging"     % "0.0.0",
  "com.blinkboxbooks.hermes" %% "rabbitmq-ha"    % "2.0.0",
  "com.typesafe"       %% "scalalogging-slf4j"   % "1.0.1",
  "ch.qos.logback"      % "logback-classic"      % "1.1.2",
  "io.spray"            %  "spray-client"        % sprayV,
  "io.spray"            %  "spray-http"          % sprayV,
  "io.spray"            %  "spray-httpx"         % sprayV,
  "io.spray"           %% "spray-json"           % "1.2.5",
  "org.json4s"         %% "json4s-jackson"       % "3.2.9",
  "org.scalatest"      %% "scalatest"            % "2.2.0" % "test",
  "junit"               % "junit"                % "4.11" % "test",
  "com.novocode"        % "junit-interface"      % "0.10" % "test",
  "com.h2database"      % "h2"                   % "1.3.173" % "test",
  "org.mockito"         % "mockito-core"         % "1.9.5" % "test",
  "com.typesafe.akka"  %% "akka-actor"           % akkaV,
  "com.typesafe.akka"  %% "akka-testkit"         % akkaV % "test",
  "joda-time"           % "joda-time"            % "2.3",
  "net.sf.saxon"        % "Saxon-HE"             % "9.5.1-5"
  )
}

// Add current working directory to classpath for JAR file.
packageOptions in (Compile, packageBin) +=
    Package.ManifestAttributes( java.util.jar.Attributes.Name.CLASS_PATH -> "." )
