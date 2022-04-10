package benchmark

import zio._

object NotTracedWithoutIO extends ZIOAppDefault {

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    for {
      _ <- Console.printLine("NotTracedWithoutIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withoutIO, Duration.fromMillis(60000))
    } yield ()

}
