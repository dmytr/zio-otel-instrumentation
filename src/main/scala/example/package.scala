package object example {

  // object tracer extends OpenTelemetryTracer
  object tracer extends DataDogTracer
  //object tracer extends NoopTracer

}
