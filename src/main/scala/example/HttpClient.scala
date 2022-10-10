package example

import example.Routes.pokeEndpoint
import io.opentelemetry.api.{GlobalOpenTelemetry, trace}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.{Request, SttpBackend}
import sttp.model.{StatusCode, Uri}
import sttp.tapir.DecodeResult
import sttp.tapir.client.sttp.SttpClientInterpreter
import zio._
import zio.telemetry.opentelemetry.Tracing

import java.util.UUID

object HttpClient extends ZIOAppDefault {

  override def run: ZIO[Scope, Any, Any] = {
    for {
      backend <- HttpClientZioBackend.scoped()
      run      = ZIO.collectAllParDiscard(List.fill(100)(makeRequest(backend).forever)) *> ZIO.never
      _       <- Console.printLine("HTTP client started")
      _       <- run.ensuring(Console.printLine("HTTP client terminated").orDie)
    } yield ()
  }.provideSome[Scope](ZLayer.succeed(GlobalOpenTelemetry.getTracer("zio-telemetry")) >>> Tracing.propagating)

  private def makeRequest(backend: SttpBackend[Task, Any]): ZIO[Tracing, Throwable, Unit] =
    for {
      payload <- generatePayload
      _       <- sendRequest(backend, payload)
    } yield ()

  def generatePayload: ZIO[Any, Nothing, Payload] =
    ZIO.sleep(100.millis) *>
      ZIO.succeed(Payload(UUID.randomUUID().toString))

  private val makeRequest: Payload => Request[DecodeResult[Either[Unit, StatusCode]], Any] =
    SttpClientInterpreter().toRequest(pokeEndpoint, Some(Uri("http", "localhost", 8080)))

  def sendRequest(backend: SttpBackend[Task, Any], payload: Payload): ZIO[Tracing, Throwable, Unit] =
    Tracing.root[Tracing, Throwable, Unit]("send_request", trace.SpanKind.CLIENT, _ => trace.StatusCode.ERROR) {
      Tracing.setAttribute("http_client_tag", payload.id) *>
        backend
          .send(makeRequest(payload))
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
