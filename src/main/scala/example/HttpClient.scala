package example

import example.Routes.pokeEndpoint
import example.tracer._
import sttp.client3.{Request, SttpBackend}
import sttp.model.{Header, StatusCode, Uri}
import sttp.tapir.DecodeResult
import sttp.tapir.client.sttp.SttpClientInterpreter
import zio._
import zio.stream.ZStream

import java.util.UUID

object HttpClient extends TracedApp {

  override def run: ZIO[Scope, Any, Any] =
    program
      .flatMap(run => Console.printLine("HTTP client started") *> run *> ZIO.never)
      .ensuring(Console.printLine("HTTP client terminated").orDie)
      .orDie

  private def program: ZIO[Scope, Throwable, ZIO[Any, Throwable, Unit]] = {
    httpBackend.map { backend =>
      ZIO.collectAllParDiscard(List.fill(10)(operation(backend).forever)).unit
    }
  }

  private def operation(backend: SttpBackend[Task, Any]): ZIO[Any, Throwable, Unit] = {
    val req = for {
      payload <- generatePayload
      _       <- sendRequest(backend, payload)
    } yield ()

    ZStream
      .repeat(req)
      .grouped(10)
      .schedule(Schedule.spaced(500.millis))
      .mapZIO(actions => ZIO.foreachParDiscard(actions)(identity))
      .runDrain
  }

  def generatePayload: ZIO[Any, Nothing, Payload] =
    ZIO.yieldNow *>
      ZIO.succeed(Payload(UUID.randomUUID().toString))

  private val makeRequest: ((List[Header], Payload)) => Request[DecodeResult[Either[Unit, StatusCode]], Any] =
    SttpClientInterpreter().toRequest(pokeEndpoint, Some(Uri("http", "localhost", 8080)))

  def sendRequest(backend: SttpBackend[Task, Any], payload: Payload): ZIO[Any, Throwable, Unit] = {
    rootSpan("send_request", "http_client_tag" -> payload.id) {
      addBaggage("http_client_tag", payload.id) *>
        backend
          .send(makeRequest(List.empty, payload))
          .flatMap {
            _.body match {
              case DecodeResult.Value(Right(StatusCode.Ok)) =>
                ZIO.unit
              case resp                                     =>
                ZIO.fail(new RuntimeException(s"Unexpected response: $resp"))
            }
          }
          .catchAll(err => Console.printLine(s"Failed to call the HTTP server: ${err.getMessage}"))
    }
  }

}
