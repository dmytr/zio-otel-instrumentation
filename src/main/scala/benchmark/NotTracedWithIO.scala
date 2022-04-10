package benchmark

import zio._

object NotTracedWithIO extends ZIOAppDefault {

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    for {
      _ <- Console.printLine("NotTracedWithIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withIO, Duration.fromMillis(60000))
    } yield ()

}
