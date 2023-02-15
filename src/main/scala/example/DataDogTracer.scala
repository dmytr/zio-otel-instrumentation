package example

import ddtrot.dd.trace.bootstrap.instrumentation.api._
import zio._

trait DataDogTracer extends Tracer {

  private lazy val tracer = AgentTracer.get()

  def span[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.succeed(tracer.activeSpan()).flatMap { parent =>
      childSpan(if (parent != null) parent.context() else null, false, name, tags: _*)(zio)
    }

  def rootSpan[R, E, A](name: String, tags: (String, String)*)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    childSpan(null, true, name, tags: _*)(zio)

  def continueSpan[R, E, A](name: String, headers: Map[String, String], tags: (String, String)*)(
      zio: ZIO[R, E, A]
  ): ZIO[R, E, A] = {
    ZIO.succeed(tracer.extract(headers, TextMapGetter)).flatMap { parent =>
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

  private def childSpan[R, E, A](parent: AgentSpan.Context, isRoot: Boolean, name: String, tags: (String, String)*)(
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
        scope         <- ZIO.succeed(tracer.activateSpan(spanWithAttrs, ScopeSource.MANUAL))
      } yield spanWithAttrs -> scope

    def closeSpan(spanAndScope: (AgentSpan, AgentScope)) =
      ZIO.succeed {
        spanAndScope._2.close()
        spanAndScope._1.finish()
      }

    ZIO.acquireReleaseWith(startSpan)(closeSpan)(_ => zio)
  }

  private object TextMapGetter extends AgentPropagation.ContextVisitor[Map[String, String]] {
    override def forEachKey(carrier: Map[String, String], classifier: AgentPropagation.KeyClassifier): Unit = {
      carrier.foreach { case (key, value) => classifier.accept(key, value) }
    }
  }

}
