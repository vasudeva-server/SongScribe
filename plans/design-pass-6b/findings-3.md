# Findings — step 3, pass 6b, _Accidentals_

Paths are under `src/main/java/songscribe/dom/` unless stated.

## The yield

**Guards this step retires: 0. Types it introduces: 0.**

The step 2 gate took the type work this step would have proposed, and it is built: two `@Nullable` parameters retired (`Accidental.semitoneAdjustmentOf`, `DiatonicPitch.midi`), one prose invariant made structural (`ChainEdit`'s `0 <= from <= to`, now `Cursor start` and a non-negative `length`), one open constructor closed (`ChainEdit`), one mode boolean made an enum (`ChainEdit.Reach`), and one public record hidden (`Projected`).

What remains is where the new `Cursor` type is not yet used, and one constructible-before-usable field. Every item below is a signature change over types that already exist.

## What it found

| Where | Fact |
|---|---|
| `ChainEdit.inserted` `:159`, `removed` `:174`, `replaced` `:191`, `modified` `:212`, `rekeyingFrom` `:247` | Each takes `(int lineIndex, int index, …)`: two adjacent `int`s a call site can transpose. Each builds `new Cursor(lineIndex, index)` on its first line. |
| `AccidentalScanView.elementAt(int lineIndex, int index)` `:73` | Two adjacent `int`s. Six call sites in `AccidentalChain`: `:227` and `:412` split a `Cursor` the walk holds; `:389` and `:399` pass a cursor's line and a loop counter; `:179` and `:357` pass two loop counters. |
| `AccidentalChain.Walk.key` `:201`, `:212`, `:222` | Set to `Key.DEFAULT` in the constructor, replaced in `run()` after the first-element check. `previous` `:203`, `:223` follows the same pattern with null. `design.md`'s *Constructible before usable*. The check exists because `keyBefore` reads `view.line(rangeStart.lineIndex())`, and `ChainEdit.lineRemoved` for the last line puts `rangeStart` one past the last line. |
| `ChainEdit.of` `@Nullable` return `:309` | Honestly partial: a mutation the walk does not read. Stays. |
| `AccidentalScanView.ownKey` `@Nullable` `:82` | Honestly partial: a line inherits or has its own key. Stays. |
| `Walk.previous`, `spanEnd`, `existingAtOrAfter`, `firstUnskippedAtOrAfter`, `lastKeyChangeBefore` | Partial lookups inside the walk. Stay. |
| `Walk.run` `cursor == null` `:218` | Reachable from `lineRemoved` on the last line. Stays. |
| `Walk.inForceIsKey` `:202` | Records that the walk passed a key change, so a position first met afterwards seeds from the key, not from a scan back past the edit. A fact, not a mode. Stays. |

## What needs deciding

### 1. <span id="rd-c1">`ChainEdit`'s factories take a `Cursor`</span>

`inserted(Cursor start, List)`, `removed(Cursor start, List)`, `replaced(Cursor start, List removed, List inserted)`, `modified(Cursor at, StaffElement before, StaffElement after)`, `rekeyingFrom(Cursor at)`. `lineRekeyed(int lineIndex)`, `lineInserted(Line)` and `lineRemoved(int lineIndex, Line)` take one index and stay.

Callers that change: `ChainEdit.of`'s four arms (`:311–319`), which build the cursor from the mutation's line and index; `GraceModeManager.enterGraceNote` (`ui/edit/GraceModeManager.java:564`) and `abort` (`:773`); `ProjectedEdit.walk` (`:146`); `AccidentalChain.walkSong` (`:180`); `AccidentalChainTest`'s eight edit tests.

- Pro: a transposed line index and element index no longer compile.
- Con: none real. Every caller has both values in hand.

**Option B. Keep the ints.**

- Con: `of`'s four arms each pass `x.line().index(), x.index()` in an order the compiler does not check.

### 2. `AccidentalScanView.elementAt` takes a `Cursor`

`elementAt(Cursor at)`. `elementCount(int lineIndex)`, `line(int lineIndex)` and `ownKey(int lineIndex)` take one index and stay.

- Pro: the two sites that hold a `Cursor` pass it whole, and the two that pass a cursor's line with a counter cannot swap the halves.
- Con: `walkSong` (`:179`) and `inForceBeforeStart` (`:357`) iterate with two `int` counters and build a `Cursor` per element.

<span id="rd-c2">**Option B. Keep the ints**</span>**.**

- Con: `Walk` splits a `Cursor` into two arguments at `:227` and `:412`.

### 3. <span id="rd-c3">`Walk` is constructed only when there is something to walk</span>

`AccidentalChain.walk(view, edit)` finds the first existing place at or after the edit's start and returns no restamps when there is none; otherwise it constructs `Walk` with that cursor, and the constructor seeds `key` from `keyBefore` and `previous` from `previousBefore`. `key` becomes `final`; the `Key.DEFAULT` placeholder and the `run()` reseed go.

- Pro: no `Walk` exists whose `key` is a placeholder, and the reason for the early return — the edit's start can be one past the song — is stated once, in `walk`, where the empty answer is decided.
- Con: none real.

**Option B. Keep the placeholder.**

- Con: `key` is written twice, and a reader of the constructor sees a value that is never read.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: ok
    by: user
    at: "2026-09-22T00:51:32.656Z"
  rd-c2:
    body: this
    by: user
    at: "2026-09-22T00:53:03.180Z"
  rd-c3:
    body: ok
    by: user
    at: "2026-09-22T00:53:25.758Z"
