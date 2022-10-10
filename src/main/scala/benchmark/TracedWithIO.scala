package benchmark

import io.opentelemetry.api.GlobalOpenTelemetry
import zio._
import zio.telemetry.opentelemetry.Tracing

object TracedWithIO extends ZIOAppDefault {

  override def run: ZIO[Any, Any, Any] = {
    for {
      _ <- ZIO.service[Tracing]
      _ <- Console.printLine("TracedWithIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withIO, Duration.fromMillis(60000))
    } yield ()
  }.provide(ZLayer.succeed(GlobalOpenTelemetry.getTracer("zio-telemetry")) >>> Tracing.live)

}
