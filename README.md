# ZIO instrumentation with OpenTelemetry and Datadog

A simple application which can be used to debug automatic instrumentation of ZIO code provided by OpenTelemetry and
Datadog JVM agents.

This example involves:

* HTTP Client
* HTTP Server
* Postgres
* Kafka publisher and consumer
* Redis

Data which is sent between components is just a random UUID. To make sure that traces are collected correctly, each
manual span (i.e. which is not added by OpenTelemetry instrumentation agent) has a tag with the value of that UUID.

### Components diagram

![Diagram](docs/diagram.png)

### Running the example with OpenTelemetry

First you need to start all the dependencies using Docker Compose:

```shell
$ docker-compose up -d
```

Download OpenTelemetry JVM agent JAR (e.g. using [Coursier](https://get-coursier.io)):

```shell
$ OTEL_AGENT_PATH=$(cs fetch --classpath "io.opentelemetry.javaagent:opentelemetry-javaagent:latest.release")
```

Then start each component:

```shell
$ sbt -J-Dotel.service.name=example-consumer \
      -J-Dotel.traces.sampler=always_on \
      -J-javaagent:$OTEL_AGENT_PATH \
      -J-Dexample.instrumentation=otel \
      "runMain example.KafkaConsumer"
$ sbt -J-Dotel.service.name=example-server \
      -J-Dotel.traces.sampler=always_on \
      -J-javaagent:$OTEL_AGENT_PATH \
      -J-Dexample.instrumentation=otel \
      "runMain example.HttpServer"
$ sbt -J-Dotel.service.name=example-client \
      -J-Dotel.traces.sampler=always_on \
      -J-javaagent:$OTEL_AGENT_PATH \
      -J-Dexample.instrumentation=otel \
      "runMain example.HttpClient"
```

After a short moment you should see your traces in Jaeger UI ([http://localhost:16686](http://localhost:16686)).

![Trace](docs/trace.png)

### Running the example with Datadog

First you need to start all the dependencies using Docker Compose:

```shell
$ docker-compose up -d
```

Start Datadog agent (you'll need Datadog API key for this):

```shell
$ docker run -d --rm --name dd-agent \
    -e DD_API_KEY=$DD_API_KEY \
    -e DD_SITE="datadoghq.eu" \
    -e DD_APM_ENABLED=true \
    -e DD_HOSTNAME=example \
    -p 8126:8126 \
    datadog/agent:latest
```

Download Datadog JVM agent JAR (e.g. using [Coursier](https://get-coursier.io)):

```shell
$ DD_AGENT_PATH=$(cs fetch --classpath "com.datadoghq:dd-java-agent:latest.release")
```

Then start each component:

```shell
$ sbt -J-Ddd.service=example-consumer \
      -J-Ddd.version=1.0 \
      -J-Ddd.env=dev \
      -J-Ddd.trace.sample.rate=1 \
      -J-javaagent:$DD_AGENT_PATH \
      -J-Dexample.instrumentation=dd \
      "runMain example.KafkaConsumer"
$ sbt -J-Ddd.service=example-server \
      -J-Ddd.version=1.0 \
      -J-Ddd.env=dev \
      -J-Ddd.trace.sample.rate=1 \
      -J-javaagent:$DD_AGENT_PATH \
      -J-Dexample.instrumentation=dd \
      "runMain example.HttpServer"
$ sbt -J-Ddd.service=example-client \
      -J-Ddd.version=1.0 \
      -J-Ddd.env=dev \
      -J-Ddd.trace.sample.rate=1 \
      -J-javaagent:$DD_AGENT_PATH \
      -J-Dexample.instrumentation=dd \
      "runMain example.HttpClient"
```

After a short moment you should see your traces in Datadog UI.
