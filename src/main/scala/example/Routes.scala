package example

import sttp.model.{Header, StatusCode}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._

object Routes {

  val pokeEndpoint: Endpoint[Unit, (List[Header], Payload), Unit, StatusCode, Any] =
    endpoint.post
      .in("poke")
      .in(headers)
      .in(jsonBody[Payload])
      .out(statusCode)

}
