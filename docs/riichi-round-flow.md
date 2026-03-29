# Riichi Round Flow Rules

This document describes the formal round-flow rules used by the Riichi mode in this repository.

## 1. Base Turn Flow

- Seat winds stay fixed as East, South, West, North.
- Turn progression follows East -> South -> West -> North.
- Seat labeling remains East, South, West, North.
- A normal turn is "draw one tile, discard one tile".
- In normal flow, each player keeps 13 concealed tiles after finishing their discard.

## 2. Draw Rules

- Normal draw: when your turn begins and no call interrupts flow, draw from the live wall front.
- Wall consumption is modeled as a single forward sequence from the live wall front.
- Dead wall reserve: the last 14 tiles are reserved and not used for normal draws.
- Kan supplement draw: after kan (ankan, minkan, kakan), draw one rinshan tile from the dead wall side and then discard.
- Exhaustive draw: when the live wall is exhausted (only dead wall reserve remains), the hand ends in an exhaustive draw if nobody has won.

## 3. Discard Rules

- Every discard is public and tracked as part of each player's discard history.
- A tile claimed by another player (chii/pon/minkan) is still treated as a tile that the discarder has discarded for furiten logic.
- Open calls interrupt normal draw order and transfer turn control to the caller.

## 4. Interrupting Actions

- Chii: only the next player in turn order after the discarder may chii.
- Pon: any non-discarding player may pon if legal.
- Kan: any non-discarding player may minkan if legal; caller then performs a rinshan draw.
- Ron: a legal ron claim immediately ends the hand.
- Priority when multiple claims compete on one discard: ron > pon/kan > chii.
- After chii/pon, the caller does not take a normal draw and must discard directly.

## 5. Furiten

- If your winning waits include a tile you have already discarded, you are furiten.
- While furiten, you cannot win by ron.
- While furiten, you may still win by tsumo if otherwise legal.

## 6. Test Coverage Mapping

Current automated tests explicitly protect:

- turn progression order after a normal discard
- chii eligibility limited to the next player
- no normal draw before the caller's discard after chii
- pon priority over chii on the same discard
- furiten checks over discard history
