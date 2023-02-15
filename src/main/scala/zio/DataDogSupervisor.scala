package zio

import datadog.trace.api.GlobalTracer
import zio.DataDogSupervisor.Helper

import java.util.concurrent.ConcurrentHashMap

final class DataDogSupervisor private (private val helper: Helper) extends Supervisor[Unit] {

  private val storage = new ConcurrentHashMap[Int, (Any, Any)]()

  override def value(implicit trace: ZTraceElement): UIO[Unit] = UIO.unit

  override private[zio] def unsafeOnStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
  ): Unit = {
    storage.put(fiber.id.id, helper.makeSnapshot())
  }

  override private[zio] def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
    storage.remove(fiber.id.id)
    helper.reset()
  }

  override private[zio] def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = ()

  override private[zio] def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
    storage.put(fiber.id.id, helper.makeSnapshot())
    helper.reset()
  }

  override private[zio] def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
    val snapshot = storage.get(fiber.id.id)
    if (snapshot != null) helper.restoreSnapshot(snapshot)
  }

}

object DataDogSupervisor {

  private trait Helper {

    def makeSnapshot(): (Any, Any)

    def restoreSnapshot(snapshot: (Any, Any)): Unit

    def reset(): Unit

  }

  def make: DataDogSupervisor = {
    val tracer = GlobalTracer.get()

    val scopeManagerField = tracer.getClass.getDeclaredField("scopeManager")
    scopeManagerField.setAccessible(true)
    val scopeManager      = scopeManagerField.get(tracer)

    val tlsScopeStackField = scopeManager.getClass.getDeclaredField("tlsScopeStack")
    tlsScopeStackField.setAccessible(true)
    val tlsScopeStack      = tlsScopeStackField.get(scopeManager).asInstanceOf[ThreadLocal[Any]]

    val rootIterationScopesField = scopeManager.getClass.getDeclaredField("rootIterationScopes")
    rootIterationScopesField.setAccessible(true)

    val scopeStackClass            = tlsScopeStack.get().getClass
    val scopeStackClassConstructor = scopeStackClass.getDeclaredConstructor()
    scopeStackClassConstructor.setAccessible(true)

    println(tracer)
    println(tracer.getClass)
    println(scopeManagerField)
    println(scopeManager)
    println(tlsScopeStack)
    println(tlsScopeStack.get())
    println(rootIterationScopesField.get(scopeManager))
    println(tlsScopeStack.get().getClass)
    println(scopeStackClassConstructor.newInstance())

    new DataDogSupervisor(
      new Helper {
        override def makeSnapshot(): (Any, Any) = (
          tlsScopeStack.get(),
          rootIterationScopesField.get(scopeManager)
        )

        override def restoreSnapshot(snapshot: (Any, Any)): Unit = {
          tlsScopeStack.set(snapshot._1)
          rootIterationScopesField.set(scopeManager, snapshot._2)
        }

        override def reset(): Unit = restoreSnapshot(
          (
            scopeStackClassConstructor.newInstance(),
            null
          )
        )
      }
    )
  }

}
