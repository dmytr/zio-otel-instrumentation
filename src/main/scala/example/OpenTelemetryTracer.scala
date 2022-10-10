package example

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.{Context, Scope}
import zio._

import scala.jdk.CollectionConverters._

object OpenTelemetryTracer extends Tracer {

  private lazy val tracer     = GlobalOpenTelemetry.get().getTracer("example")
  private lazy val propagator = GlobalOpenTelemetry.get().getPropagators.getTextMapPropagator

  override def span[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.succeed(Context.current()).flatMap { parent =>
      childSpan(parent, name, tags: _*)(zio)
    }

  override def rootSpan[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    childSpan(Context.root(), name, tags: _*)(zio)

  override def continueSpan[R, E, A](name: String, headers: Map[String, String], tags: (String, String)*)(
      zio: ZIO[R, E, A]
  ): ZIO[R, E, A] = {
    ZIO.succeed(propagator.extract(Context.current(), headers, ScalaMapTextMapGetter)).flatMap { parent =>
      childSpan(parent, name, tags: _*)(zio)
    }
  }

  override def addBaggage(key: String, value: String): ZIO[Any, Nothing, Unit] =
    ZIO.succeed {
      Baggage.current().toBuilder.put(key, value).build().storeInContext(Context.current()).makeCurrent()
    }

  private def childSpan[R, E, A](parent: Context, name: String, tags: (String, String)*)(
      zio: ZIO[R, E, A]
  ): ZIO[R, E, A] = {
    val startSpan =
      for {
        span          <- ZIO.succeed(tracer.spanBuilder(name).setParent(parent).startSpan())
        spanWithAttrs <- ZIO.foldLeft(tags)(span) { (span, tag) => ZIO.succeed(span.setAttribute(tag._1, tag._2)) }
        scope         <- ZIO.succeed(span.makeCurrent())
      } yield spanWithAttrs -> scope

    def closeSpan(spanAndScope: (Span, Scope)) =
      ZIO.succeed {
        spanAndScope._2.close()
        spanAndScope._1.end()
      }

    ZIO.acquireReleaseWith(startSpan)(closeSpan)(_ => zio)
  }

  private object ScalaMapTextMapGetter extends TextMapGetter[Map[String, String]] {
    override def keys(carrier: Map[String, String]): java.lang.Iterable[String] = carrier.keys.asJava

    override def get(carrier: Map[String, String], key: String): String = carrier.getOrElse(key, null)
  }

}
