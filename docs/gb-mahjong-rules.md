# GB Mahjong Rule Source

Authoritative implementation target for the `feature/gb-mahjong` branch:

- `https://github.com/zheng-fan/GB-Mahjong`

## Rule Positioning

The GB Mahjong variant in this repository is intended to follow the same rule positioning described by `GB-Mahjong`:

- base rules mainly follow the 1998 `中国麻将竞赛规则（试行）`
- fan counting behavior follows the consensus used by major GB Mahjong tournaments
- the project treats `《国标麻将规则释义（网络对局版）》` as an important reference

This means the GB variant here is **not** being designed as a loose “Chinese-style Mahjong” mode.
Its intended behavior is to match the scoring and hand-judging expectations of `zheng-fan/GB-Mahjong` as closely as possible.

## Functional Scope Expected From The Native Backend

The referenced `GB-Mahjong` project explicitly provides:

- hand-string parsing
- fan counting
- ting calculation
- detailed per-fan composition output

The JNI backend in this branch should be designed around those same responsibilities.

## Current Status

This branch now contains more than just scaffolding:

- GB tables can be selected as a live runtime variant
- the plugin-side round flow supports GB actions such as chi / pon / kan / ron / tsumo
- the JNI bridge is wired to a vendored copy of `GB-Mahjong`
- native `fan`, `ting`, and `win` entry points are implemented at source level

That still does **not** mean every possible tournament edge case is permanently complete.
The important guarantee here is that the GB implementation direction is locked to `zheng-fan/GB-Mahjong`, and the current code actively routes legality/fan evaluation through that native rule source rather than treating GB as a loose placeholder mode.
