package zio

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context

import java.util.concurrent.ConcurrentHashMap

final class OpenTelemetrySupervisor extends Supervisor[Unit] {

  private val storage = new ConcurrentHashMap[Int, Span]()

  override def value(implicit trace: ZTraceElement): UIO[Unit] = UIO.unit

  override private[zio] def unsafeOnStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
  ): Unit = {
    val span = Span.current()
    if (span != null) storage.put(fiber.id.id, span)
    else storage.put(fiber.id.id, Span.fromContext(Context.root()))
  }

  override private[zio] def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
    storage.remove(fiber.id.id)
    Context.root().makeCurrent()
  }

  override private[zio] def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = ()

  override private[zio] def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
    val span = Span.current()
    if (span != null) storage.put(fiber.id.id, span)
    else storage.put(fiber.id.id, Span.fromContext(Context.root()))
    Context.root().makeCurrent()
  }

  override private[zio] def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
    val span = storage.get(fiber.id.id)
    if (span != null) span.makeCurrent()
  }

}
