# GB Mahjong Rule Source

MahjongPaper's GB Mahjong implementation is anchored to the upstream `GB-Mahjong` project:

- <https://github.com/zheng-fan/GB-Mahjong>

## Rule Positioning

The GB ruleset in this repository is intended to track the same rule direction described by `GB-Mahjong`:

- baseline rule positioning primarily follows the 1998 `中国麻将竞赛规则（试行）`
- fan counting aims to match the practical expectations encoded by the upstream implementation
- legality and fan composition are evaluated through the native bridge rather than a separate local scoring clone

This means the GB variant here is not treated as a loose “Chinese Mahjong” flavor mode.
Its intended behavior is to stay as close as practical to the upstream `GB-Mahjong` interpretation used by the vendored native backend.

## What The Upstream Provides

The upstream rule engine is used as the authoritative backend for:

- hand parsing
- fan counting
- ting calculation
- per-fan composition output

MahjongPaper then translates those results into its own:

- table reaction flow
- settlement UI
- score settlement records
- player-facing prompts and state summaries

## Practical Consequence For This Repository

When GB behavior is questioned, the default expectation should be:

1. inspect the request built by MahjongPaper
2. inspect how it is mapped into the native bridge
3. compare that mapped request against upstream `GB-Mahjong` expectations

The plugin should not casually drift into a parallel local interpretation unless there is a deliberate, documented reason.

## Current Implementation State

In the current `dev` branch:

- GB is selectable as a live table variant
- GB round flow supports chi, pon, kan, ron, tsumo, flowers, and supplement draws
- the JNI bridge is wired to vendored native source
- native fan, ting, and win entry points are implemented in source

That does not guarantee every tournament edge case is permanently solved, but it does lock the implementation direction: GB rule behavior is meant to flow through the upstream-native path, not through an unrelated fallback ruleset.
