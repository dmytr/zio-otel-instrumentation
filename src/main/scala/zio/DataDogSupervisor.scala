package zio

import io.opentracing.util.GlobalTracer
import io.opentracing.{Span, Tracer}

import java.util.concurrent.ConcurrentHashMap
import scala.annotation.nowarn

@nowarn
final class DataDogSupervisor private (tracer: Tracer) extends Supervisor[Unit] {

  private val storage = new ConcurrentHashMap[Int, Span]()

  override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

  override def onStart[R, E, A_](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A_],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A_]
  )(implicit unsafe: Unsafe): Unit = {
    if (parent.isDefined) {
      val span = storage.get(parent.get.id.id)
      if (span != null) tracer.scopeManager().activate(span)
    }
  }

  override def onEnd[R, E, A_](value: Exit[E, A_], fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = ()

  override def onSuspend[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val span  = tracer.activeSpan()
    val scope = tracer.scopeManager().active()
    if (span != null) storage.put(fiber.id.id, span)
    if (scope != null) scope.close()
  }

  override def onResume[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val span = storage.get(fiber.id.id)
    if (span != null) tracer.activateSpan(span)
  }

}

object DataDogSupervisor {

  def make: DataDogSupervisor =
    new DataDogSupervisor(GlobalTracer.get())

}
