package benchmark

import zio._

object NotTracedWithIO extends ZIOAppDefault {

  override def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- Console.printLine("NotTracedWithIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withIO, Duration.fromMillis(60000))
    } yield ()

}
