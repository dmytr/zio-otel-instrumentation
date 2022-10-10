package example

import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import io.opentelemetry.api.{GlobalOpenTelemetry, trace}
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.json.JsonDecoder
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.telemetry.opentelemetry.{TextMapAdapter, Tracing}

import java.nio.charset.StandardCharsets
import scala.collection.mutable

object KafkaConsumer extends ZIOAppDefault {

  private val settings     = ConsumerSettings(List("localhost:9092")).withGroupId("group")
  private val subscription = Subscription.topics("example")

  private type RedisClient = RedisCommands[Task, String, String]

  override def run: ZIO[Scope, Any, Any] = {
    for {
      redisClient <- makeRedisClient
      consumer    <- makeConsumer
      run          = consume(redisClient, consumer) *> ZIO.never
      _           <- Console.printLine("Kafka consumer started")
      _           <- run.ensuring(Console.printLine("Kafka consumer terminated").orDie)
    } yield ()
  }.provideSome[Scope](ZLayer.succeed(GlobalOpenTelemetry.getTracer("zio-telemetry")) >>> Tracing.propagating)

  private def consume(redisClient: RedisClient, consumer: Consumer): ZIO[Tracing, Throwable, Unit] =
    consumer.subscribeAnd(subscription).plainStream(Serde.string, Serde.string).runForeach { record =>
      Tracing.spanFrom[mutable.Map[String, String], Tracing, Throwable, Unit](
        GlobalOpenTelemetry.getPropagators.getTextMapPropagator,
        extractHeaders(record),
        TextMapAdapter,
        "consume_message",
        trace.SpanKind.CONSUMER,
        _ => trace.StatusCode.ERROR
      ) {
        for {
          payload <- ZIO.fromEither(JsonDecoder[Payload].decodeJson(record.value)).orDieWith(new RuntimeException(_))
          _       <- processMessage(redisClient, record.key, payload).orDie
        } yield ()
      }
    }

  private def extractHeaders(record: CommittableRecord[String, String]): mutable.Map[String, String] =
    mutable.Map.from(record.record.headers().toArray.map(h => h.key() -> new String(h.value(), StandardCharsets.UTF_8)))

  private def processMessage(redisClient: RedisClient, key: String, payload: Payload): ZIO[Tracing, Throwable, Unit] =
    Tracing.span[Tracing, Throwable, Unit]("process_message", trace.SpanKind.INTERNAL, _ => trace.StatusCode.ERROR) {
      Tracing.setAttribute("kafka_consumer_tag", payload.id) *>
        redisClient.set(key, payload.id)
    }

  private def makeConsumer: ZIO[Scope, Throwable, Consumer] =
    Consumer.make(settings)

  private def makeRedisClient: ZIO[Scope, Throwable, RedisClient] = {
    import Log.NoOp.instance
    RedisClient[Task]
      .from("redis://localhost:6379")
      .flatMap(Redis[Task].fromClient(_, RedisCodec.Utf8))
      .toScopedZIO
  }

}
