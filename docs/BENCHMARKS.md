# Benchmarks

There are 4 variants:

1. Without tracing, with IO (NotTracedWithIO)
2. With tracing, with IO (TracedWithIO)
3. Without tracing, without IO (NotTracedWithoutIO)
4. With tracing, with IO (TracedWithoutIO)

Results after running each variant for 1 minute:

| Variant            | Total ops | Ops / ms | ns / op, p50 | ns / op, p90 | ns / op, p95 | ns / op, p99 |
|--------------------|-----------|----------|--------------|--------------|--------------|--------------|
| NotTracedWithIO    | 1352975   | 22.549   | 31910        | 39244        | 51958        | 86392        |
| TracedWithIO       | 1338186   | 22.303   | 31620        | 39985        | 62097        | 87494        |
| NotTracedWithoutIO | 14534635  | 242.243  | 1022         | 2314         | 3757         | 4849         |
| TracedWithoutIO    | 10713031  | 178.550  | 2014         | 3416         | 3987         | 6011         |
