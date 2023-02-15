package example

import doobie.Transactor
import doobie.implicits._
import example.tracer._
import sttp.model.{Header, StatusCode}
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.RichZEndpoint
import zio._
import zio.http.Server
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.json.JsonEncoder
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde

object HttpServer extends TracedApp {

  private val settings: ProducerSettings = ProducerSettings(List("localhost:9092"))

  override def run: ZIO[Scope, Any, Any] =
    program
      .flatMap(run => Console.printLine("HTTP server started") *> run *> ZIO.never)
      .ensuring(Console.printLine("HTTP server terminated").orDie)
      .orDie

  private def program: ZIO[Scope, Throwable, ZIO[Any, Throwable, Unit]] = {
    for {
      _        <- initializeDatabase
      producer <- makeProducer
      http      = makeHttp(producer)
    } yield Server.serve(http).unit.provide(Server.default)
  }

  def handleRequest(producer: Producer)(headers: List[Header], payload: Payload): ZIO[Any, Nothing, StatusCode] =
    span("handle_request", "http_server_tag" -> payload.id) {
      for {
        _ <- addBaggage("http_server_tag", payload.id)
        // _ <- ZIO.succeed(println(headers))
        _ <- publishPayload(producer, payload)
        _ <- persistPayload(payload)
      } yield StatusCode.Ok
    }.catchAll(_ => ZIO.succeed(StatusCode.InternalServerError))

  def publishPayload(producer: Producer, payload: Payload): ZIO[Any, Throwable, Unit] =
    span("publish_payload", "http_server_tag" -> payload.id) {
      producer
        .produce(
          "example",
          payload.id,
          JsonEncoder[Payload].encodeJson(payload, None).toString,
          Serde.string,
          Serde.string
        )
        .unit
    }

  def persistPayload(payload: Payload): ZIO[Any, Nothing, Unit] =
    span("persist_payload", "http_server_tag" -> payload.id) {
      sql"insert into payloads (id) values (${payload.id})".update.run.transact(transactor).unit.orDie
    }

  private def initializeDatabase: ZIO[Scope, Throwable, Unit] =
    sql"create table if not exists payloads (id text)".update.run.transact(transactor).unit

  private def makeProducer: ZIO[Scope, Throwable, Producer] =
    Producer.make(settings)

  private def makeHttp(producer: Producer): http.App[Any] =
    ZioHttpInterpreter().toApp(Routes.pokeEndpoint.zServerLogic { case (headers, payload) =>
      handleRequest(producer)(headers, payload)
    })

  private lazy val transactor: Transactor[Task] =
    Transactor.fromDriverManager(
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5432/dev",
      "dev",
      "dev"
    )

}
