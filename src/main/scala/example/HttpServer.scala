package example

import doobie.Transactor
import doobie.implicits._
import io.opentelemetry.api.{GlobalOpenTelemetry, trace}
import sttp.model.StatusCode
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.RichZEndpoint
import zhttp.http.{Http, Request, Response}
import zhttp.service.Server
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.json.JsonEncoder
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.telemetry.opentelemetry.Tracing

object HttpServer extends ZIOAppDefault {

  private val settings: ProducerSettings = ProducerSettings(List("localhost:9092"))

  override def run: ZIO[Scope, Any, Any] = {
    for {
      _        <- initializeDatabase
      producer <- makeProducer
      http      = makeHttp(producer)
      run       = Server.start(8080, http).orDie
      _        <- Console.printLine("HTTP server started")
      _        <- run.ensuring(Console.printLine("HTTP server terminated").orDie)
    } yield ()
  }.provideSome[Scope](ZLayer.succeed(GlobalOpenTelemetry.getTracer("zio-telemetry")) >>> Tracing.propagating)

  def handleRequest(producer: Producer)(payload: Payload): ZIO[Tracing, Nothing, StatusCode] =
    Tracing
      .span[Tracing, Throwable, StatusCode]("handle_request", trace.SpanKind.INTERNAL, _ => trace.StatusCode.ERROR) {
        Tracing.setAttribute("http_server_tag", payload.id) *> {
          for {
            _ <- publishPayload(producer, payload)
            _ <- persistPayload(payload)
          } yield StatusCode.Ok
        }
      }
      .catchAll(_ => ZIO.succeed(StatusCode.InternalServerError))

  def publishPayload(producer: Producer, payload: Payload): ZIO[Tracing, Throwable, Unit] =
    Tracing.span[Tracing, Throwable, Unit]("publish_payload", trace.SpanKind.INTERNAL, _ => trace.StatusCode.ERROR) {
      Tracing.setAttribute("http_server_tag", payload.id) *>
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

  def persistPayload(payload: Payload): ZIO[Tracing, Nothing, Unit] =
    Tracing.span[Tracing, Nothing, Unit]("persist_payload", trace.SpanKind.INTERNAL, _ => trace.StatusCode.ERROR) {
      Tracing.setAttribute("http_server_tag", payload.id) *>
        sql"insert into payloads (id) values (${payload.id})".update.run.transact(transactor).unit.orDie
    }

  private def initializeDatabase: ZIO[Any, Throwable, Unit] =
    sql"create table if not exists payloads (id text)".update.run.transact(transactor).unit

  private def makeProducer: ZIO[Scope, Throwable, Producer] =
    Producer.make(settings)

  private def makeHttp(producer: Producer): Http[Tracing, Throwable, Request, Response] =
    ZioHttpInterpreter().toHttp(Routes.pokeEndpoint.zServerLogic(handleRequest(producer)))

  private lazy val transactor: Transactor[Task] =
    Transactor.fromDriverManager(
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5432/dev",
      "dev",
      "dev"
    )

}
