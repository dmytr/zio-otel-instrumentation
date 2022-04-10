ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = project
  .in(file("."))
  .settings(
    name                := "inject-context",
    libraryDependencies := Seq(
      "io.opentelemetry"               % "opentelemetry-api"      % "1.12.0",
      "com.softwaremill.sttp.tapir"   %% "tapir-json-zio"         % "1.0.0-M6",
      "com.softwaremill.sttp.tapir"   %% "tapir-zio-http-server"  % "1.0.0-M6",
      "com.softwaremill.sttp.tapir"   %% "tapir-sttp-client"      % "1.0.0-M6",
      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.5.1",
      "com.softwaremill.sttp.client3" %% "zio-json"               % "3.5.1",
      "dev.zio"                       %% "zio-kafka"              % "2.0.0-M1",
      "dev.zio"                       %% "zio-interop-cats"       % "3.3.0-RC2",
      "dev.profunktor"                %% "redis4cats-effects"     % "1.1.1",
      "org.tpolecat"                  %% "doobie-postgres"        % "1.0.0-RC2"
    )
  )
