# GB Mahjong JNI Bridge

This branch now uses a real JNI bridge for the GB Mahjong variant rather than a placeholder native stub.

Rule target:

- `https://github.com/zheng-fan/GB-Mahjong`
- see `docs/gb-mahjong-rules.md`

## JVM Side

Bridge entry points:

- `src/main/java/doublemoon/mahjongcraft/paper/gb/jni/GbMahjongNativeLibrary.java`
- `src/main/java/doublemoon/mahjongcraft/paper/gb/jni/GbMahjongNativeBridge.java`
- `src/main/java/doublemoon/mahjongcraft/paper/gb/runtime/GbNativeRulesGateway.java`

Responsibilities:

- load `mahjongpaper_gb` from `java.library.path`
- or load an explicit library path from `-Dmahjongpaper.gb.native.path=/absolute/path/to/library`
- expose handshake calls:
  - `libraryVersion()`
  - `ping()`
- expose JSON JNI service entry points:
  - `evaluateFan(GbFanRequest)`
  - `evaluateTing(GbTingRequest)`
  - `evaluateWin(GbWinRequest)`

The request/response models live in:

- `src/main/kotlin/doublemoon/mahjongcraft/paper/gb/jni/GbMahjongNativeModels.kt`

## Native Side

Native project:

- `native/gbmahjong/CMakeLists.txt`
- `native/gbmahjong/src/gbmahjong_jni.cpp`

Bundled reference source:

- `native/gbmahjong/vendor/GB-Mahjong/mahjong/`
- upstream license copied to `native/gbmahjong/vendor/GB-Mahjong/LICENSE`

The JNI layer now:

- parses the JSON request payloads
- converts them into `GB-Mahjong` hand-string expressions
- calls the vendored `mahjong::Fan` / `mahjong::Handtiles` APIs
- returns structured JSON for:
  - fan counting
  - ting waits
  - win settlement fan breakdown

### Request Mapping

The plugin keeps its own structured request types and converts them to the hand-string grammar that `GB-Mahjong` expects.

Examples:

- `W1/T1/B1/F1/J1` are mapped to the upstream tile grammar (`m/s/p`, `ESWN`, `PFC`)
- flower tiles use the upstream `a`-`h` ids (`plum`, `orchid`, `bamboo`, `chrysanthemum`, `spring`, `summer`, `autumn`, `winter`)
- melds are mapped into upstream bracket notation such as `[123m,1]`, `[EEE,2]`, `[1111p,7]`
- JNI flags are mapped into the upstream status suffix:
  - `LAST_OF_KIND` -> juezhang bit
  - `LAST_TILE` -> haidi bit
  - `AFTER_KONG` / `ROBBING_KONG` -> gang bit

## Settlement Scope

`GB-Mahjong` itself provides hand parsing, ting calculation, and fan counting.
It does not provide a full Paper-plugin settlement model, so the plugin still translates native fan output into:

- `RoundResolution`
- `ScoreSettlement`
- `YakuSettlement`

Current settlement transfer policy in the plugin:

- `RON`: discarder pays the winner `totalFan`
- `TSUMO`: each non-winner pays the winner `totalFan`

This keeps the in-game table flow consistent while still using the upstream GB fan engine as the source of truth for legality and fan composition.

## Local Build

Gradle tasks:

- `configureGbMahjongNative`
- `buildGbMahjongNative`

Example:

```powershell
.\gradlew.bat buildGbMahjongNative
```

Artifacts are built under:

```text
build/native/gbmahjong
```

## Verification Status

Java/plugin-side tests are part of the normal Gradle test suite.

On this development machine, the Java side was verified with:

```powershell
.\gradlew.bat test --tests doublemoon.mahjongcraft.paper.table.core.round.GbTableRoundControllerTest --tests doublemoon.mahjongcraft.paper.table.MahjongTableSessionTest --tests doublemoon.mahjongcraft.paper.gb.jni.GbMahjongNativeJsonTest --no-daemon
```

The native library could not be compiled locally here because this machine currently has no `cmake` or C++ toolchain installed.
The native source and CMake project are still checked in ready for compilation in CI or on a machine with the required toolchain.
