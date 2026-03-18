# Performance Testing

MahjongPaper now includes a dedicated benchmark task that stays out of normal `test` runs.

## Run

```powershell
$env:GRADLE_USER_HOME='E:\project\majiang\.gradle-home'
.\gradlew.bat perfTest --console plain
```

## Tune iterations

```powershell
.\gradlew.bat perfTest -PperfWarmups=8 -PperfIterations=20 -PperfBatchSize=500 --console plain
```

## Reports

After the task finishes, benchmark reports are written to:

- `build/reports/performance/results.md`
- `build/reports/performance/results.json`

## Current benchmarks

- `render.layout.precompute.started_snapshot`
- `render.region_fingerprints.precompute.started_snapshot`
- `render.snapshot.create.started_session`
- `riichi.round_engine.start_round`
- `gb.round_controller.start_round`
- `gb.bot.suggest_discard.duplicate_hand`

Use the same command before and after an optimization so the numbers stay comparable.
