ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

lazy val root = project
  .in(file("."))
  .settings(
    name                := "inject-context",
    libraryDependencies := Seq(
      "io.opentelemetry"               % "opentelemetry-api"     % "1.18.0",
      "com.softwaremill.sttp.client3" %% "zio"                   % "3.8.2",
      "com.softwaremill.sttp.client3" %% "zio-json"              % "3.8.2",
      "com.softwaremill.sttp.tapir"   %% "tapir-json-zio"        % "1.1.2",
      "com.softwaremill.sttp.tapir"   %% "tapir-sttp-client"     % "1.1.2",
      "com.softwaremill.sttp.tapir"   %% "tapir-zio-http-server" % "1.1.2",
      "dev.profunktor"                %% "redis4cats-effects"    % "1.2.0",
      "dev.zio"                       %% "zio"                   % "2.0.2",
      "dev.zio"                       %% "zio-interop-cats"      % "3.3.0",
      "dev.zio"                       %% "zio-kafka"             % "2.0.1",
      "dev.zio"                       %% "zio-nio"               % "2.0.0",
      "dev.zio"                       %% "zio-opentelemetry"     % "2.0.2+8-fcbd3091-SNAPSHOT",
      "org.tpolecat"                  %% "doobie-postgres"       % "1.0.0-RC2"
    )
  )
