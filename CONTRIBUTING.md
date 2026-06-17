# Contributing

MahjongPaper currently mixes Java and Kotlin deliberately. Keep the split visible when adding production code.

## Java/Kotlin Boundary

Use Java by default for Paper/Bukkit integration, plugin bootstrap, commands, table/session orchestration, rendering, UI, database, runtime scheduling, CraftEngine compatibility, configuration, i18n, and metrics.

Use Kotlin only where it keeps rule or data-heavy code clearer:

- pure Riichi rule logic, scoring helpers, and state models under `src/main/kotlin/top/ellan/mahjong/riichi`
- GB native request/response DTOs under `src/main/kotlin/top/ellan/mahjong/gb/jni`
- future pure algorithm or serialization model packages, after updating `ArchitectureBoundaryTest` and this document in the same change

Production Kotlin should not import Bukkit, Paper, Kyori UI/presentation APIs, CraftEngine compatibility classes, plugin bootstrap classes, database services, runtime schedulers, or table/session coordinator internals. Tests may use Kotlin freely.

## Package Boundaries

Keep these dependency directions in mind:

- `bootstrap` wires services and entry points; avoid reaching into command/render/ui internals.
- `command` talks to the public table and service surface through `MahjongCommandContext`.
- `table` owns session orchestration and may call render/UI services through narrow interfaces.
- `render` owns display entity specs/layout and must stay free of `table` and `bootstrap`.
- `riichi` and `gb` should stay rules-focused and free of platform/presentation dependencies.
- `compat` hides CraftEngine and Paper reflection details behind service facades.

If a new feature needs to cross one of these boundaries, prefer introducing a small DTO or interface near the caller instead of importing a concrete internal class from another layer.

## Build Logic

Keep root `build.gradle.kts` focused on task wiring. Resource generation, native helper logic, and bundle generation belong in `buildSrc`.
