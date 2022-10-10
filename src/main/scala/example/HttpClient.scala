package example

import example.Routes.pokeEndpoint
import example.tracer._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.model.Header
import zio.stream.ZStream
// import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.{Request, SttpBackend}
import sttp.model.{StatusCode, Uri}
import sttp.tapir.DecodeResult
import sttp.tapir.client.sttp.SttpClientInterpreter
import zio._

import java.util.UUID

object HttpClient extends TracedApp {

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    program
      .use(run => Console.printLine("HTTP client started") *> run *> ZIO.never)
      .ensuring(Console.printLine("HTTP client terminated").orDie)
      .orDie

  private def program: ZManaged[Any, Throwable, ZIO[ZEnv, Throwable, Unit]] = {
    AsyncHttpClientZioBackend.managed().map { backend =>
      ZIO.collectAllParDiscard(List.fill(4)(operation(backend).forever)).unit
    }
  }

  private def operation(backend: SttpBackend[Task, Any]): ZIO[ZEnv, Throwable, Unit] = {
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

  def generatePayload: ZIO[ZEnv, Nothing, Payload] =
    //ZIO.sleep(100.millis) *>
      ZIO.succeed(Payload(UUID.randomUUID().toString))

  private val makeRequest: ((List[Header], Payload)) => Request[DecodeResult[Either[Unit, StatusCode]], Any] =
    SttpClientInterpreter().toRequest(pokeEndpoint, Some(Uri("http", "localhost", 8080)))

  def sendRequest(backend: SttpBackend[Task, Any], payload: Payload): ZIO[ZEnv, Throwable, Unit] = {
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
