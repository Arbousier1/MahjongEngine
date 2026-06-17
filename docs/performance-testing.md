# Performance Testing

MahjongPaper includes benchmark-style tests under the `perf` JUnit tag. They are excluded from normal `test` runs and are executed through the dedicated Gradle `perfTest` task.

## How It Works

Benchmark helpers live in:

- `src/test/kotlin/top/ellan/mahjong/perf/PerformanceBenchmarkSupport.kt`

Current benchmark entry points live in:

- `src/test/kotlin/top/ellan/mahjong/perf/CorePerformanceBenchmarksTest.kt`
- `src/test/kotlin/top/ellan/mahjong/perf/GbBotSuggestionBenchmarkTest.kt`

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
- `gb.native_gateway.ting_cache.hit`

In addition to the `perf` suite, JNI startup now logs a one-time first-call benchmark
(`GbNativeWarmupService`) for `fan/ting/win` first-call vs warm-call latency.
Use those startup numbers as the baseline before considering JNI "call pool" designs.

## Reading Results

The generated markdown report includes:

- average ns/op
- median ns/op
- p90 ns/op
- min ns/op
- max ns/op
- total measured milliseconds

Use the same warmup, measurement, and batch parameters before and after an optimization so the results remain comparable.

## Entity-Heavy Render Changes

`perfTest` measures CPU-side hot paths such as snapshot creation, layout precompute, and region fingerprinting. It does **not** model Paper entity tracking cost or client-side rendering cost.

For changes that increase table entities, per-viewer overlays, or display churn, also validate on a live server scene:

- server `mspt`
- client `fps`
- `table.render.region.managed_entities`
- `table.render.region.viewer_overlay_regions`
- `table.render.region.viewer_overlay_entities`

Treat those entity gauges as leading indicators. Even if benchmark numbers stay flat, a higher managed entity count can still hurt server tick time and client frame rate once real viewers are present.

## Recommended Workflow

For a performance-sensitive change:

1. Run `perfTest` on the current baseline
2. Save `build/reports/performance/results.md`
3. Apply the optimization
4. Run the same `perfTest` command again
5. Compare benchmark names one by one instead of relying on a single global impression

These benchmarks are intentionally lightweight and developer-friendly. They help catch regressions and compare hot-path changes, but they are not a substitute for full production profiling on a live Paper/Folia server.
