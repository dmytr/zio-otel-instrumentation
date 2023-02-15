package zio

import ddtrot.dd.trace.bootstrap.instrumentation.api.{AgentSpan, AgentTracer, ScopeSource}

import java.util.concurrent.ConcurrentHashMap

final class DataDogSupervisor private (tracer: AgentTracer.TracerAPI) extends Supervisor[Unit] {

  private val storage = new ConcurrentHashMap[Int, AgentSpan]()

  override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

  override def onStart[R, E, A_](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A_],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A_]
  )(implicit unsafe: Unsafe): Unit = {
    if (parent.isDefined) {
      val span = storage.get(parent.get.id.id)
      if (span != null) tracer.activateSpan(span, ScopeSource.INSTRUMENTATION, true)
    }
  }

  override def onEnd[R, E, A_](value: Exit[E, A_], fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = ()

  override def onSuspend[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val span  = tracer.activeSpan()
    val scope = tracer.activeScope()
    if (span != null) storage.put(fiber.id.id, span)
    if (scope != null) scope.close()
  }

  override def onResume[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val span = storage.get(fiber.id.id)
    if (span != null) tracer.activateSpan(span, ScopeSource.INSTRUMENTATION, true)
  }

}

object DataDogSupervisor {

  def make: DataDogSupervisor =
    new DataDogSupervisor(AgentTracer.get())

}
