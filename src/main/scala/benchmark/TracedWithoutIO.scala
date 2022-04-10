package benchmark

import zio.RuntimeConfigAspect.addSupervisor
import zio._

object TracedWithoutIO extends ZIOAppDefault {

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    for {
      _ <- Console.printLine("TracedWithoutIO")
      _ <- Console.printLine("==================")
      _ <- Benchmark.run(Benchmark.withoutIO, Duration.fromMillis(60000))
    } yield ()

  override def hook: RuntimeConfigAspect = addSupervisor(new OpenTelemetrySupervisor)

}
