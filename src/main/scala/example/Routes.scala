package example

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._

object Routes {

  val pokeEndpoint: Endpoint[Unit, Payload, Unit, StatusCode, Any] =
    endpoint.post
      .in("poke")
      .in(jsonBody[Payload])
      .out(statusCode)

}
