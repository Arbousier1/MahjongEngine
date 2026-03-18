# Performance Testing

MahjongPaper includes benchmark-style tests under the `perf` JUnit tag. They are excluded from normal `test` runs and are executed through the dedicated Gradle `perfTest` task.

## How It Works

Benchmark helpers live in:

- `src/test/kotlin/doublemoon/mahjongcraft/paper/perf/PerformanceBenchmarkSupport.kt`

Current benchmark entry points live in:

- `src/test/kotlin/doublemoon/mahjongcraft/paper/perf/CorePerformanceBenchmarksTest.kt`
- `src/test/kotlin/doublemoon/mahjongcraft/paper/perf/GbBotSuggestionBenchmarkTest.kt`

The helper writes aggregated reports after each benchmark run to:

- `build/reports/performance/results.md`
- `build/reports/performance/results.json`

## Run

```powershell
$env:GRADLE_USER_HOME='E:\project\majiang\.gradle-home'
.\gradlew.bat perfTest --console plain
```

## Tune Iterations

`perfTest` reads these Gradle properties:

- `perfWarmups`
- `perfIterations`
- `perfBatchSize`

Example:

```powershell
.\gradlew.bat perfTest -PperfWarmups=8 -PperfIterations=20 -PperfBatchSize=500 --console plain
```

These values are forwarded to the benchmark helper as:

- `mahjong.perf.warmupIterations`
- `mahjong.perf.measurementIterations`
- `mahjong.perf.batchSize`

## Current Benchmarks

As of the current `dev` branch, the benchmark suite covers:

- `render.snapshot.create.started_session`
- `render.layout.precompute.started_snapshot`
- `render.region_fingerprints.precompute.started_snapshot`
- `riichi.round_engine.start_round`
- `gb.round_controller.start_round`
- `gb.bot.suggest_discard.duplicate_hand`

## Reading Results

The generated markdown report includes:

- average ns/op
- median ns/op
- p90 ns/op
- min ns/op
- max ns/op
- total measured milliseconds

Use the same warmup, measurement, and batch parameters before and after an optimization so the results remain comparable.

## Recommended Workflow

For a performance-sensitive change:

1. Run `perfTest` on the current baseline
2. Save `build/reports/performance/results.md`
3. Apply the optimization
4. Run the same `perfTest` command again
5. Compare benchmark names one by one instead of relying on a single global impression

These benchmarks are intentionally lightweight and developer-friendly. They help catch regressions and compare hot-path changes, but they are not a substitute for full production profiling on a live Paper/Folia server.
