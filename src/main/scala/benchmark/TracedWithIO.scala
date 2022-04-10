package benchmark

import zio.RuntimeConfigAspect.addSupervisor
import zio._

object TracedWithIO extends ZIOAppDefault {

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    for {
      _ <- Console.printLine("TracedWithIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withIO, Duration.fromMillis(60000))
    } yield ()

  override def hook: RuntimeConfigAspect = addSupervisor(new OpenTelemetrySupervisor)

}
