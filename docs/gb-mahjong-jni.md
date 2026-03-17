# GB Mahjong JNI Bootstrap

This branch uses JNI as the intended bridge between the Paper plugin and a future native Chinese Official Mahjong backend.

Rule target:

- the native backend is expected to follow `https://github.com/zheng-fan/GB-Mahjong`
- see `docs/gb-mahjong-rules.md`

## JVM Side

The initial bridge lives in:

- `src/main/java/doublemoon/mahjongcraft/paper/gb/jni/GbMahjongNativeLibrary.java`
- `src/main/java/doublemoon/mahjongcraft/paper/gb/jni/GbMahjongNativeBridge.java`

Current responsibilities:

- load `mahjongpaper_gb` from `java.library.path`
- or load an explicit library path from `-Dmahjongpaper.gb.native.path=/absolute/path/to/library`
- expose a minimal JNI handshake:
  - `libraryVersion()`
  - `ping()`

## Native Side

The native project lives in:

- `native/gbmahjong/CMakeLists.txt`
- `native/gbmahjong/src/gbmahjong_jni.cpp`

Right now it is a stub JNI library that only proves the bridge can load and answer a handshake call.

## Local Build

Gradle tasks:

- `configureGbMahjongNative`
- `buildGbMahjongNative`

Example:

```powershell
.\gradlew.bat buildGbMahjongNative
```

That builds the shared library with CMake under:

```text
build/native/gbmahjong
```

## Next Step

Once the native library boundary is stable, the next stage is to define a compact JNI API for:

- hand parsing / serialization
- ting calculation
- fan calculation
- settlement output
- rule profile selection
