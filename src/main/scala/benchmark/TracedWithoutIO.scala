package benchmark

import io.opentelemetry.api.GlobalOpenTelemetry
import zio._
import zio.telemetry.opentelemetry.Tracing

object TracedWithoutIO extends ZIOAppDefault {

  override def run: ZIO[Any, Any, Any] = {
    for {
      _ <- ZIO.service[Tracing]
      _ <- Console.printLine("TracedWithoutIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withoutIO, Duration.fromMillis(60000))
    } yield ()
  }.provide(ZLayer.succeed(GlobalOpenTelemetry.getTracer("zio-telemetry")) >>> Tracing.live)

}
