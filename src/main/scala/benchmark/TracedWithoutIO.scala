package benchmark

import example.TracedApp
import zio._

object TracedWithoutIO extends TracedApp {

  override def run: ZIO[Scope, Any, Any] =
    for {
      _ <- Console.printLine("TracedWithoutIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withoutIO, Duration.fromMillis(60000))
    } yield ()

}
