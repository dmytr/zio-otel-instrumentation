package example

import zio.ZIO

trait Tracer {

  def span[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A]

  def rootSpan[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A]

  def continueSpan[R, E, A](name: String, headers: Map[String, String], tags: (String, String)*)(
      zio: ZIO[R, E, A]
  ): ZIO[R, E, A]

  def addBaggage(key: String, value: String): ZIO[Any, Nothing, Unit]

  def getBaggage(key: String): ZIO[Any, Nothing, Option[String]]

}
