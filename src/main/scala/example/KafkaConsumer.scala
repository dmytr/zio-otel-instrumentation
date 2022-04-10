package example

import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.json.JsonDecoder
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde

import java.nio.charset.StandardCharsets

object KafkaConsumer extends TracedApp {

  private val settings     = ConsumerSettings(List("localhost:9092")).withGroupId("group")
  private val subscription = Subscription.topics("example")

  private type RedisClient = RedisCommands[Task, String, String]

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    program
      .use(run => Console.printLine("Kafka consumer started") *> run *> ZIO.never)
      .ensuring(Console.printLine("Kafka consumer terminated").orDie)
      .orDie

  private def program: ZManaged[ZEnv, Throwable, ZIO[ZEnv, Throwable, Unit]] =
    for {
      redisClient <- makeRedisClient
      consumer    <- makeConsumer
    } yield consume(redisClient, consumer)

  private def consume(redisClient: RedisClient, consumer: Consumer): ZIO[ZEnv, Throwable, Unit] =
    consumer.subscribeAnd(subscription).plainStream(Serde.string, Serde.string).runForeach { record =>
      continueSpan("consume_message", extractHeaders(record)) {
        for {
          payload <- ZIO.fromEither(JsonDecoder[Payload].decodeJson(record.value)).orDieWith(new RuntimeException(_))
          _       <- processMessage(redisClient, record.key, payload).orDie
        } yield ()
      }
    }

  private def extractHeaders(record: CommittableRecord[String, String]): Map[String, String] =
    record.record.headers().toArray.map(h => h.key() -> new String(h.value(), StandardCharsets.UTF_8)).toMap

  private def processMessage(redisClient: RedisClient, key: String, payload: Payload): ZIO[Any, Throwable, Unit] =
    span("process_message", "kafka_consumer_tag" -> payload.id) {
      redisClient.set(key, payload.id)
    }

  private def makeConsumer: ZManaged[ZEnv, Throwable, Consumer] =
    Consumer.make(settings)

  private def makeRedisClient: ZManaged[ZEnv, Throwable, RedisClient] = {
    import Log.NoOp.instance
    RedisClient[Task]
      .from("redis://localhost:6379")
      .flatMap(Redis[Task].fromClient(_, RedisCodec.Utf8))
      .toManagedZIO
  }

}
