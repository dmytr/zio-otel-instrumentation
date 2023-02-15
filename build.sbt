ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = project
  .in(file("."))
  .settings(
    name                := "inject-context",
    libraryDependencies := Seq(
      "com.datadoghq"                  % "dd-trace-ot"                   % "1.8.0",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.8.11",
      "com.softwaremill.sttp.client3" %% "zio"                           % "3.8.11",
      "com.softwaremill.sttp.client3" %% "zio-json"                      % "3.8.11",
      "com.softwaremill.sttp.tapir"   %% "tapir-json-zio"                % "1.2.8",
      "com.softwaremill.sttp.tapir"   %% "tapir-sttp-client"             % "1.2.8",
      "com.softwaremill.sttp.tapir"   %% "tapir-zio-http-server"         % "1.2.8",
      "dev.profunktor"                %% "redis4cats-effects"            % "1.4.0",
      "dev.zio"                       %% "zio-interop-cats"              % "23.0.0.0",
      "dev.zio"                       %% "zio-kafka"                     % "2.0.7",
      "dev.zio"                       %% "zio-nio"                       % "2.0.1",
      "io.opentelemetry"               % "opentelemetry-api"             % "1.23.0",
      "org.tpolecat"                  %% "doobie-postgres"               % "1.0.0-RC2"
    )
  )
