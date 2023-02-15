package zio

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context

import java.util.concurrent.ConcurrentHashMap

final class OpenTelemetrySupervisor extends Supervisor[Unit] {

  private val storage = new ConcurrentHashMap[Int, Span]()

  override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

  override def onStart[R, E, A_](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A_],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A_]
  )(implicit unsafe: Unsafe): Unit = {
    val span = Span.current()
    if (span != null) storage.put(fiber.id.id, span)
    else storage.put(fiber.id.id, Span.fromContext(Context.root()))
  }

  override def onEnd[R, E, A_](value: Exit[E, A_], fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    storage.remove(fiber.id.id)
    Context.root().makeCurrent()
  }

  override def onSuspend[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val span = Span.current()
    if (span != null) storage.put(fiber.id.id, span)
    else storage.put(fiber.id.id, Span.fromContext(Context.root()))
    Context.root().makeCurrent()
  }

  override def onResume[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val span = storage.get(fiber.id.id)
    if (span != null) span.makeCurrent()
  }

}
