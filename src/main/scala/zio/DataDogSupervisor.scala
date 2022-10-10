package zio

import datadog.trace.bootstrap.instrumentation.api.AgentScope
import datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope
import zio.DataDogSupervisor.Helper

import java.util.concurrent.ConcurrentHashMap

final class DataDogSupervisor private (private val helper: Helper) extends Supervisor[Unit] {

  private val states = new ConcurrentHashMap[Int, AnyRef]()

  override def value(implicit trace: ZTraceElement): UIO[Unit] = UIO.unit

  override private[zio] def unsafeOnStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
  ): Unit = {
    val state = helper.enter(currentState = null)
    if (state != null) states.put(fiber.id.id, state)
  }

  override private[zio] def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
    helper.exit(states.remove(fiber.id.id))
  }

  override private[zio] def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = ()

  override private[zio] def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
    helper.exit(states.get(fiber.id.id))
  }

  override private[zio] def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
    helper.enter(states.get(fiber.id.id))
  }

}

object DataDogSupervisor {

  private trait Helper {

    def enter(currentState: AnyRef): AnyRef

    def exit(state: AnyRef): Unit

  }

  def make: DataDogSupervisor = {
    // Based on https://github.com/DataDog/dd-trace-java/pull/3252
    val stateClass                = Class.forName("datadog.trace.bootstrap.instrumentation.java.concurrent.State")
    val captureAndSetContinuation = stateClass.getDeclaredMethod("captureAndSetContinuation", classOf[AgentScope])
    val startThreadMigration      = stateClass.getDeclaredMethod("startThreadMigration")
    val closeContinuation         = stateClass.getDeclaredMethod("closeContinuation")

    val stateFactoryField = stateClass.getDeclaredField("FACTORY")
    val stateFactory      = stateFactoryField.get(null)
    val stateFactoryClass = Class.forName("datadog.trace.bootstrap.ContextStore$Factory")
    val createState       = stateFactoryClass.getMethod("create")

    new DataDogSupervisor(
      new Helper {

        override def enter(currentState: AnyRef): AnyRef = {
          val scope = activeScope()
          if (scope != null && scope.isAsyncPropagating) {
            val state = if (currentState == null) createState.invoke(stateFactory) else currentState
            captureAndSetContinuation.invoke(state, scope)
            startThreadMigration.invoke(state)
            state
          } else null
        }

        override def exit(state: AnyRef): Unit = {
          if (state != null) closeContinuation.invoke(state)
        }

      }
    )
  }

}
