package example

import io.opentracing.propagation.{Format, TextMapExtract}
import io.opentracing.util.GlobalTracer
import io.opentracing.{Scope, Span, SpanContext}
import zio.{Scope => _, _}

import java.util
import scala.jdk.CollectionConverters._

trait DataDogTracer extends example.Tracer {

  private lazy val tracer = GlobalTracer.get()

  def span[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.succeed(tracer.activeSpan()).flatMap { parent =>
      childSpan(if (parent != null) parent.context() else null, false, name, tags: _*)(zio)
    }

  def rootSpan[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    childSpan(null, true, name, tags: _*)(zio)

  def continueSpan[R, E, A](name: String, headers: Map[String, String], tags: (String, String)*)(
      zio: ZIO[R, E, A]
  ): ZIO[R, E, A] = {
    ZIO.succeed(tracer.extract(Format.Builtin.TEXT_MAP_EXTRACT, new TextMapGetter(headers))).flatMap { parent =>
      childSpan(parent, parent == null, name, tags: _*)(zio)
    }
  }

  def addBaggage(key: String, value: String): ZIO[Any, Nothing, Unit] =
    ZIO.succeed {
      val span = tracer.activeSpan()
      if (span != null) span.setBaggageItem(key, value)
    }

  def getBaggage(key: String): ZIO[Any, Nothing, Option[String]] =
    ZIO.succeed {
      val span = tracer.activeSpan()
      if (span != null) Option(span.getBaggageItem(key))
      else None
    }

  private def childSpan[R, E, A](parent: SpanContext, isRoot: Boolean, name: String, tags: (String, String)*)(
      zio: ZIO[R, E, A]
  ): ZIO[R, E, A] = {
    val startSpan =
      for {
        spanBuilder   <- ZIO.succeed(
                           if (parent != null) tracer.buildSpan(name).asChildOf(parent)
                           else tracer.buildSpan(name)
                         )
        span          <- ZIO.succeed { if (isRoot) spanBuilder.ignoreActiveSpan().start() else spanBuilder.start() }
        spanWithAttrs <- ZIO.foldLeft(tags)(span) { (span, tag) => ZIO.succeed(span.setBaggageItem(tag._1, tag._2)) }
        scope         <- ZIO.succeed(tracer.activateSpan(spanWithAttrs))
      } yield spanWithAttrs -> scope

    def closeSpan(spanAndScope: (Span, Scope)) =
      ZIO.succeed {
        spanAndScope._2.close()
        spanAndScope._1.finish()
      }

    ZIO.acquireReleaseWith(startSpan)(closeSpan)(_ => zio)
  }

  private class TextMapGetter(map: Map[String, String]) extends TextMapExtract {
    override def iterator(): util.Iterator[java.util.Map.Entry[String, String]] =
      map.asJava.entrySet().iterator()
  }

}
