package example

import zio.ZIO

object NoopTracer extends Tracer {

  override def span[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] = zio

  override def rootSpan[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] = zio

  override def continueSpan[R, E, A](name: String, headers: Map[String, String], tags: (String, String)*)(
      zio: ZIO[R, E, A]
  ): ZIO[R, E, A] = zio

  override def addBaggage(key: String, value: String): ZIO[Any, Nothing, Unit] = ZIO.unit

}
