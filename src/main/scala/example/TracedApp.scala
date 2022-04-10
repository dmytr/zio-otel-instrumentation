package example

import zio.RuntimeConfigAspect.addSupervisor
import zio.{OpenTelemetrySupervisor, RuntimeConfigAspect, ZIOAppDefault}

trait TracedApp extends ZIOAppDefault {

  override def hook: RuntimeConfigAspect = addSupervisor(new OpenTelemetrySupervisor)

}
