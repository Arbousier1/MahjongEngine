# Changelog

## 0.3.1 - 2026-03-17

Stability and interaction fix release.

- Fixed outdated test imports/packages so `./gradlew test jacocoTestReport` passes again in CI.
- Removed the green felt layer from both the built-in table render path and the CraftEngine `table_visual` model.
- Narrowed the CraftEngine hand-tile hitbox so adjacent hand tiles no longer overlap and mis-select under the pointer.

## 0.3.0 - 2026-03-17

Critical fix release.

- Fixed a severe bug in previous versions where the chair hitbox was positioned too high.
- Lowered the chair collision box by 0.5 blocks so seat interaction and collision align correctly.
- Users running any previous 0.2.x build should upgrade to 0.3.0 immediately.

## Release note

MahjongPaper 0.3.0 is a critical stability release.

Previous versions contained a severe bug in the chair collision/hitbox configuration, which could cause incorrect interaction and placement behavior around seats.

If you are using a 0.2.x version, upgrade to 0.3.0 as soon as possible.
