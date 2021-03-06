package benchmark

import zio._
import zio.nio.file.Files

import java.util.concurrent.TimeUnit

object Benchmark {

  def withIO: ZIO[ZEnv, Nothing, Unit] =
    for {
      v <- Random.nextLong
      f <- Files.createTempFile(prefix = None, fileAttributes = List.empty).orDie
      _ <- Files.writeLines(f, List(s"$v")).orDie
      _ <- Files.delete(f).orDie
    } yield ()

  def withoutIO: ZIO[ZEnv, Nothing, Unit] =
    for {
      _ <- Random.nextLong
      _ <- Random.nextLong
      _ <- Random.nextLong
      _ <- Random.nextLong
    } yield ()

  def run(zio: ZIO[ZEnv, Nothing, Unit], duration: Duration): ZIO[ZEnv with Clock, Nothing, Unit] =
    Ref.make(List.empty[Long]).flatMap { stats =>
      iteration(zio, stats).forever
        .timeout(duration)
        .unit
        .ensuring(printReport(stats, duration))
    }

  private def iteration(zio: ZIO[ZEnv, Nothing, Unit], stats: Ref[List[Long]]): ZIO[ZEnv with Clock, Nothing, Unit] =
    for {
      begin   <- Clock.currentTime(TimeUnit.NANOSECONDS)
      _       <- zio
      end     <- Clock.currentTime(TimeUnit.NANOSECONDS)
      duration = end - begin
      _       <- stats.update(duration :: _)
    } yield ()

  private def printReport(stats: Ref[List[Long]], duration: Duration): ZIO[ZEnv, Nothing, Unit] =
    for {
      durations  <- stats.get.map(_.sorted)
      total       = durations.size
      perMilli    = total.toDouble / duration.toMillis
      avgDuration = durations.sum / durations.size
      minDuration = durations.headOption.getOrElse(0)
      maxDuration = durations.drop(durations.size - 1).headOption.getOrElse(0)
      p50Duration = durations.drop(durations.size / 2).headOption.getOrElse(0)
      p90Duration = durations.drop((durations.size * 0.9).toInt).headOption.getOrElse(0)
      p95Duration = durations.drop((durations.size * 0.95).toInt).headOption.getOrElse(0)
      p99Duration = durations.drop((durations.size * 0.99).toInt).headOption.getOrElse(0)
      _          <- Console.printLine(s"Total ops:          $total").orDie
      _          <- Console.printLine(s"Ops per ms:         $perMilli").orDie
      _          <- Console.printLine(s"Duration (ns, avg): $avgDuration").orDie
      _          <- Console.printLine(s"Duration (ns, min): $minDuration").orDie
      _          <- Console.printLine(s"Duration (ns, max): $maxDuration").orDie
      _          <- Console.printLine(s"Duration (ns, p50): $p50Duration").orDie
      _          <- Console.printLine(s"Duration (ns, p90): $p90Duration").orDie
      _          <- Console.printLine(s"Duration (ns, p95): $p95Duration").orDie
      _          <- Console.printLine(s"Duration (ns, p99): $p99Duration").orDie
    } yield ()

}
