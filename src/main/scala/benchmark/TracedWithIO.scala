package benchmark

import example.TracedApp
import zio._

object TracedWithIO extends TracedApp {

  override def run: ZIO[Scope with ZIOAppArgs, Any, Any] =
    for {
      _ <- Console.printLine("TracedWithIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withIO, Duration.fromMillis(60000))
    } yield ()

}
