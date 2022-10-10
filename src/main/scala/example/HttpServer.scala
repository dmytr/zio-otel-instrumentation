package example

import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.{ExecutionContexts, Transactor}
import example.tracer._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.ByteArraySerializer
import sttp.model.{Header, StatusCode}
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.RichZEndpoint
import zio._
import zio.http.{Server, ServerConfig}
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.json.JsonEncoder
import zio.kafka.producer.{ByteRecord, Producer, ProducerSettings}
import zio.kafka.serde.{Serde, Serializer}

import scala.jdk.CollectionConverters._

object HttpServer extends TracedApp {

  private val settings: ProducerSettings = ProducerSettings(List("localhost:9092"))

  override def run: ZIO[Scope, Any, Any] =
    program
      .flatMap(run => Console.printLine("HTTP server started") *> run *> ZIO.never)
      .ensuring(Console.printLine("HTTP server terminated").orDie)
      .orDie

  private def program: ZIO[Scope, Throwable, ZIO[Any, Throwable, Unit]] = {
    for {
      tx       <- transactor
      _        <- initializeDatabase(tx)
      producer <- makeProducer
      http      = makeHttp(producer, tx)
    } yield Server.serve(http).unit.provide(ZLayer.succeed(ServerConfig.default.copy(nThreads = 1)) >>> Server.live)
  }

  def handleRequest(
      tx: Transactor[Task],
      producer: SyncProducer
  )(headers: List[Header], payload: Payload): ZIO[Any, Nothing, StatusCode] =
    span("handle_request", "http_server_tag" -> payload.id) {
      for {
        _ <- addBaggage("http_server_tag", payload.id)
        _ <- publishPayload(producer, payload)
        _ <- ZIO.sleep((50 + scala.util.Random.nextInt(100)).millis)
        _ <- persistPayload(tx, payload)
      } yield StatusCode.Ok
    }.catchAll(_ => ZIO.succeed(StatusCode.InternalServerError))

  def publishPayload(producer: SyncProducer, payload: Payload): ZIO[Any, Throwable, Unit] =
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

  def persistPayload(tx: Transactor[Task], payload: Payload): ZIO[Any, Nothing, Unit] =
    span("persist_payload", "http_server_tag" -> payload.id) {
      sql"insert into payloads (id) values (${payload.id})".update.run.transact(tx).unit.orDie
    }

  private def initializeDatabase(tx: Transactor[Task]): ZIO[Scope, Throwable, Unit] =
    sql"create table if not exists payloads (id text)".update.run.transact(tx).unit

  private def makeProducer: ZIO[Scope, Throwable, SyncProducer] =
    for {
      props       <- ZIO.attempt(settings.driverSettings)
      rawProducer <- ZIO.attempt(
                       new KafkaProducer[Array[Byte], Array[Byte]](
                         props.asJava,
                         new ByteArraySerializer(),
                         new ByteArraySerializer()
                       )
                     )
      producer    <- ZIO.acquireRelease(ZIO.succeed(new SyncProducer(rawProducer, settings)))(_.close)
    } yield producer

  Producer.make(settings)

  private def makeHttp(producer: SyncProducer, tx: Transactor[Task]): http.App[Any] =
    ZioHttpInterpreter()
      .toHttp(Routes.pokeEndpoint.zServerLogic[Any] { case (headers, payload) =>
        handleRequest(tx, producer)(headers, payload)
      })
      .withDefaultErrorResponse

  private lazy val transactor: ZIO[Scope, Throwable, Transactor[Task]] = {
    for {
      ce <- ExecutionContexts.fixedThreadPool[Task](1)
      xa <- HikariTransactor.newHikariTransactor[Task](
              "org.postgresql.Driver",
              "jdbc:postgresql://localhost:5432/dev",
              "dev",
              "dev",
              ce
            )
    } yield xa
  }.toScopedZIO

  // Kafka Producer from zio-kafka puts messages into a queue and background process sends them out.
  // This breaks traces, so we need a real sync producer without a queue.
  private final class SyncProducer(p: KafkaProducer[Array[Byte], Array[Byte]], settings: ProducerSettings) {

    def produce[R, K, V](
        topic: String,
        key: K,
        value: V,
        keySerializer: Serializer[R, K],
        valueSerializer: Serializer[R, V]
    ): RIO[R, RecordMetadata] =
      for {
        done             <- Promise.make[Throwable, RecordMetadata]
        record            = new ProducerRecord(topic, key, value)
        serializedRecord <- serialize(record, keySerializer, valueSerializer)
        _                <- ZIO.attemptBlocking {
                              p.send(
                                serializedRecord,
                                (metadata: RecordMetadata, err: Exception) =>
                                  Unsafe.unsafe { implicit u =>
                                    val _ =
                                      if (err != null) runtime.unsafe.run(done.fail(err)).getOrThrowFiberFailure()
                                      else runtime.unsafe.run(done.succeed(metadata)).getOrThrowFiberFailure()
                                    ()
                                  }
                              )
                            }
        res              <- done.await
      } yield res

    private def serialize[R, K, V](
        r: ProducerRecord[K, V],
        keySerializer: Serializer[R, K],
        valueSerializer: Serializer[R, V]
    ): RIO[R, ByteRecord] =
      for {
        key   <- keySerializer.serialize(r.topic, r.headers, r.key())
        value <- valueSerializer.serialize(r.topic, r.headers, r.value())
      } yield new ProducerRecord(r.topic, r.partition(), r.timestamp(), key, value, r.headers)

    def close: ZIO[Any, Nothing, Unit] =
      ZIO.attemptBlocking(p.close(settings.closeTimeout)).orDie

  }

}
