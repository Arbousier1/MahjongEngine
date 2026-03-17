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

At the moment, this branch only contains:

- the GB branch scaffold
- JNI loading/bootstrap code
- a native C++ stub project

It does **not** yet mean the in-game GB rules are fully implemented.
The confirmation made here is about the implementation target and rule source, not about feature completeness today.
