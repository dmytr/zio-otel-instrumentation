package example

import zio.RuntimeConfigAspect.addSupervisor
import zio.{DataDogSupervisor, RuntimeConfigAspect, ZIOAppDefault}

trait TracedApp extends ZIOAppDefault {

  override def hook: RuntimeConfigAspect =
    // addSupervisor(new OpenTelemetrySupervisor)
    addSupervisor(DataDogSupervisor.make)

}
