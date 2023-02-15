package example

import zio._

trait TracedApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.addSupervisor(DataDogSupervisor.make)

}
