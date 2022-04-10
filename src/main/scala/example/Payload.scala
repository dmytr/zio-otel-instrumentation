package example

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class Payload(id: String)

object Payload {
  implicit val decoder: JsonDecoder[Payload] = DeriveJsonDecoder.gen
  implicit val encoder: JsonEncoder[Payload] = DeriveJsonEncoder.gen
}
