import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Scope, Task, ZIO}

package object example {

  val tracer: Tracer = {
    sys.props.get("example.instrumentation") match {
      case Some("dd")   => DataDogTracer
      case Some("otel") => OpenTelemetryTracer
      case Some("noop") => NoopTracer
      case _            => OpenTelemetryTracer
    }
  }

  val httpBackend: ZIO[Scope, Throwable, SttpBackend[Task, Any]] = {
    sys.props.get("example.instrumentation") match {
      case Some("dd")   => AsyncHttpClientZioBackend.scoped()
      case Some("otel") => HttpClientZioBackend.scoped()
      case _            => HttpClientZioBackend.scoped()
    }
  }

}
