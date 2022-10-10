package example

import zio._

import java.util.concurrent.Executors

trait TracedApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setExecutor(Executor.fromJavaExecutor(Executors.newSingleThreadExecutor())) ++
      Runtime.setBlockingExecutor(Executor.fromJavaExecutor(Executors.newSingleThreadExecutor()))

}
