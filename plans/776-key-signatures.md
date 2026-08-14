# Mid-line and Per-line Key Signatures (#776)
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Key Value Type](#-phase-1-key-value-type) | ✅ Done | —   |
| 2   | [Key Change Policy, KeySignatureElement and ElementType](#-phase-2-key-change-policy-keysignatureelement-and-elementtype) | ✅ Done | —   |
| 3   | [Line Key Storage and Index Resolution](#-phase-3-line-key-storage-and-index-resolution) | ✅ Done | —   |
| 4   | [Remove the Song-wide Key](#-phase-4-remove-the-song-wide-key) | ✅ Done | —   |
| 5   | [Cautionary Rendering and Overflow Position](#-phase-5-cautionary-rendering-and-overflow-position) | ✅ Done | —   |
| 6   | [Layout Reservation and Header Spacing](#-phase-6-layout-reservation-and-header-spacing) | ✅ Done | —   |
| 7   | [MusicXML Key Export/Import](#-phase-7-musicxml-key-exportimport) | ✅ Done | —   |
| 7a  | [MIDI Key Signature Export](#-phase-7a-midi-key-signature-export) | ✅ Done | —   |
| 8   | [Legacy .mssw Read](#-phase-8-legacy-mssw-read) | ✅ Done | —   |
| 9   | [Barline Pairing and Selection Expansion](#-phase-9-barline-pairing-and-selection-expansion) | ✅ Done | —   |
| 10a | [Restatement Scan Hoist](#-phase-10a-restatement-scan-hoist) | ✅ Done | —   |
| 10b | [Accidental Propagation Across a Key Change](#-phase-10b-accidental-propagation-across-a-key-change) | ✅ Done | —   |
| 11  | [Operation-Independent Insertion Point](#-phase-11-operation-independent-insertion-point) | ✅ Done | —   |
| 12a | [Key Signature Dialog](#-phase-12a-key-signature-dialog) | ✅ Done | —   |
| 12b | [Key Edit Hit Targets](#-phase-12b-key-edit-hit-targets) | ✅ Done | —   |
| 12c | [Key Signature Insertion Flow](#-phase-12c-key-signature-insertion-flow) | ✅ Done | —   |
| 12d | [Fit Rejection and Commit Routing](#-phase-12d-fit-rejection-and-commit-routing) | ✅ Done | —   |
| 13  | [Design Document](#-phase-13-design-document) | ✅ Done | —   |
| 14a | [MIDI Mid-line Key Test](#-phase-14a-midi-mid-line-key-test) | ✅ Done | —   |
| 14b | [Cross-cutting Tests](#-phase-14b-cross-cutting-tests) | ✅ Done | —   |
| 15  | [Manual UI Verification](#-phase-15-manual-ui-verification) | ⏳ Pending (manual) | —   |
| 16  | [Shared Dialog Foundations](#-phase-16-shared-dialog-foundations) | ✅ Done | —   |
| 17  | [Remove the Deprecated Key Accessors](#-phase-17-remove-the-deprecated-key-accessors) | ✅ Done | —   |

The **Status** column lists only blockers that have not shipped; each phase's own **BlockedBy** line keeps the full list, done phases included, so the reasoning behind an edge survives.

Phases are **not** ordered by number — **BlockedBy** is the execution order. Phases 8, 10a, 11, 12a, 12b, 13 and 14a carry no unmet dependency and can run first.

**Phase 15 is manual and interactive by design** — its task 1 requires the user's permission before `./scripts/run.sh`, and every check needs a human at the screen. It is never dispatched as part of an automated run; it is done by hand once Phase 14b is green.

Some `BlockedBy` edges exist to give a shared file one writer at a time rather than because of a semantic dependency: **11 → 9 → 10b → 12d** and **9 → 12c → 12d** serialize `ScoreViewController.java` (Phases 9, 10b, 11, 12c, 12d), **11 → 9 → 10b** also serializes `docs/clipboard.md` (Phases 10b, 11), **5 → 9** serializes `LineRenderer.java`, **10a → 10b** serializes `AccidentalRestatements.java`, **12a → 9** serializes `ElementType.java` and **12a → 12d** serializes `KeySignatureChangeDialog.java`. Do not remove them to gain parallelism.

**The lettered splits (10a/10b, 12a–12d, 14a/14b) are model splits.** Each `a` half is work whose outcome is already decided — a stated algorithm, a stated dialog, a stated assertion — and runs on Sonnet; each later half holds the judgment and runs on Opus. They are serialized by shared files, so splitting buys model cost, not wall-clock, except where the earlier half has no unmet dependency and can start now.

**Phase 12 splits four ways, and 12b is the exception to that last clause.** 12a is the dialog, 12b the hit targets, 12c the insertion flow, 12d the fit rejections and commit routing. 12b writes `LayoutHitTester.java` and `docs/selection.md` and touches neither `ScoreViewController.java` nor the dialog, so it shares no file with any other pending phase and buys real wall-clock: it can run immediately, alongside 8, 10a, 11, 12a, 13 and 14a.

* * *
## Conventions
**No issue numbers in code.** Never write `#53`, `#776` or any issue reference in source, Javadoc or comments — the codebase is self-contained. Issue references belong in commit messages only. Where existing code carries one, the phase that touches that code removes it.

**Tests are data-driven by default.** Every case list in this plan is a parameterized table unless the case is genuinely singular. Where the domain is enumerable (`Key.allSignatures()`, `ElementType.values()`, `KeyType.values()`), drive the table from the domain rather than a hand-written list, so a domain change reaches the test.

**Never write the domain's size as a literal.** Not in an assertion, not in a test's Javadoc, not in a comment. `hasSize(15)` and "all 225 ordered pairs" are the domain's cardinality hand-copied, and they go on passing while the claim rots. Derive them — `1 + (2 * Key.MAX_ACCIDENTAL_COUNT)`, `signatures.size() * signatures.size()` — so growth reaches the assertion. This is the No Magic Numbers rule, which applies to tests exactly as it does to production code.

**No temporary duplicates.** A phase never writes a second copy of logic another phase will replace, however clearly the comment names the replacement. If two phases need the same function, the earlier one writes it in its final home.

**The phase that invalidates a doc section updates it.** A `docs/` section describing code this plan changes is fixed in the same phase as the change, by whoever holds the context for it — never swept up later by someone reconstructing what happened.

**No compile-gate is allowed to be unpassable.** Every phase's `./scripts/compile.sh` must be able to report SUCCESS at the moment that phase ends. Phase 3 keeps the old key accessors alive as deprecated delegates for exactly this reason, and Phase 17 removes them once the last consumer is gone.

* * *
## ✅ Phase 1: Key Value Type
**Status:** Done  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/dom/Key.java, src/test/java/songscribe/dom/KeyTest.java  
**Recommended model/effort:** Sonnet, medium — a small self-contained value type whose promise is fully specified below; the only judgment is test-table shape.

> **Two amendments to what shipped here land in Phase 2, tasks 2–3** — `allSignatures()` returning a shared list rather than a fresh one, and `KeyTest`'s hand-written `15`. Do not reopen this phase; Phase 2 owns both.

### Tasks
1. Read `.claude/guides/contracts.md` and `.agents/guides/testing-unit.md` before writing anything.
  
2. Write the contract for a new `public record Key(KeyType keyType, int accidentalCount)` in `src/main/java/songscribe/dom/Key.java`, **before** implementing it. The promise, which is settled and must not be re-derived:
  

- The domain is exactly 15 values: `(NONE, 0)`, `(SHARPS, 1..7)`, `(FLATS, 1..7)`.
  
- The invariant is biconditional: `keyType == NONE` **if and only if** `accidentalCount == 0`. `(NONE, 3)` and `(SHARPS, 0)` are both invalid.
  
- `keyType` is never null. `NONE` with count 0 is C major — a real key, not an "unset" marker.
  
- **There is no mode.** Every SongScribe key is major. MusicXML's `<key>` carries an optional `<mode>` child, and the writer emits `major` for it, but nothing in the model stores one: this program reads only MusicXML it wrote (`docs/musicxml-object-model.md`, _Only SongScribe documents are read_), so no minor or modal key can enter. State this on the record so a later reader does not add a `Mode` field for a case that cannot occur.
  
- The compact constructor throws `IllegalArgumentException` when the invariant is violated or `accidentalCount` is outside `0..MAX_ACCIDENTAL_COUNT`. Document the exact condition in `@throws`.
  
- Move `MAX_ACCIDENTAL_COUNT = 7` here from `songscribe.dom.KeySignature`; the contract names it via `{@value #MAX_ACCIDENTAL_COUNT}`, so it is public.
  

3. Add `public static final Key DEFAULT = new Key(KeyType.FLATS, 5)`, contracted as the key a new song's line 0 starts in. It replaces `Song.DEFAULT_KEY_TYPE` (`KeyType.FLATS`) and `Song.DEFAULT_KEY_ACCIDENTAL_COUNT` (`5`), which Phase 4 deletes; `5` is the one magic number here and it is the constant's own definition, not a literal in logic.
  

- A method named `default()` is impossible — `default` is a Java keyword — so this is a constant, which also matches how the two values it replaces read at their call sites.
  
- Do **not** add a `C_MAJOR` constant. No production code would use it, and a test that wants C major writes `new Key(KeyType.NONE, 0)`.
  

4. Add `public static List<Key> allSignatures()` returning the 15 distinct keys in a stable order (NONE first, then FLATS 1..7, then SHARPS 1..7). Contract it as "every valid key signature, exactly once." This is what makes downstream enumeration claims checkable rather than hand-written — several later phases drive `@MethodSource` from it.
  
5. Move the key-membership logic out of `songscribe.dom.Line` into `Key`. `Line` currently holds:
  

```java
private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
    new int[]{0, 3, 6, 2, 5, 1, 4},   // FLATS order:  B E A D G C F
    new int[]{4, 1, 5, 2, 6, 3, 0},   // SHARPS order: F C G D A E B
};
```

and `Line.keyExists(int pitchType)` which indexes it as `FLAT_SHARP_ORDINAL[keyType.ordinal() - 1][i]` for `i` in `0..keys`. Reproduce that behavior as `public boolean altersPitchClass(int pitchIndex)` on `Key`, contracted as: returns whether this key places an accidental on the given pitch class, where `pitchIndex` is 0 for B, 1 for C, 2 for D, … 6 for A. A key of `(NONE, 0)` alters nothing. Do **not** delete anything from `Line.java` — Phase 3 owns that file and will remove the old members.

- The `keyType.ordinal() - 1` indexing is only valid because `NONE` is ordinal 0; state that dependency in a comment on the array so a reordering of `KeyType` does not silently corrupt it.
  

6. Write `KeyTest` deriving every case from the contract, not from the implementation. **Every case below is a parameterized table**; the domain here is finite, small and enumerable, so there is no case that earns a single-example method:
  

- Valid domain: `allSignatures()` as a `@MethodSource`, asserting construction succeeds and the invariant holds for each of the 15.
  
- `allSignatures()` returns exactly 15 keys, in the documented order, with no duplicates.
  
- Invariant violations: `(NONE, n)` for `n` in `1..7`, and `(SHARPS, 0)` and `(FLATS, 0)`, all throw `IllegalArgumentException` — parameterized, not one example.
  
- Boundaries: `accidentalCount` of `-1` and `MAX_ACCIDENTAL_COUNT + 1` throw; `1` and `MAX_ACCIDENTAL_COUNT` succeed.
  
- `Key.DEFAULT` is 5 flats and satisfies the invariant.
  
- `altersPitchClass`: for each key in `allSignatures()`, the count of pitch classes `0..6` it alters equals `accidentalCount()`. That is the invariant, and it holds for all 15 without a hand-written expected table.
  
- `altersPitchClass` order: assert the first flat is B (pitch index 0) and the first sharp is F (pitch index 4), and that each key's altered set contains the previous key's altered set for the same type — the nesting property that the fifths order guarantees.
  
- Before writing each test method, check whether it will sit beside a same-shape sibling; if so, both are rows in one parameterized table, not two methods.
  

7. Run `./scripts/compile.sh` and `./scripts/test.sh KeyTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 2: Key Change Policy, KeySignatureElement and ElementType
**Status:** Done  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/dom/Key.java, src/main/java/songscribe/dom/KeyChange.java, src/main/java/songscribe/dom/ElementType.java, src/main/java/songscribe/dom/KeySignatureElement.java, src/main/java/songscribe/ui/renderer/KeySignatureRenderer.java, src/test/java/songscribe/dom/KeyTest.java, src/test/java/songscribe/dom/KeyChangeTest.java, src/test/java/songscribe/dom/ElementTypeTest.java  
**Recommended model/effort:** Opus, high — the cancellation policy is the semantic heart of the rendering, spacing and MusicXML phases, and it lands here in its final home so no phase downstream writes a second copy.
### Tasks
1. Read `.claude/guides/contracts.md` before writing contracts.
  
2. **Amend `Key.allSignatures()` (Phase 1) to return the same immutable list on every call.** It currently builds a fresh `ArrayList` and wraps it in `List.copyOf` per invocation. Its callers include a `ListCellRenderer`'s entry list (Phase 16) and a combo model (Phase 12), and it replaces a `static final` field there — a per-call build would be a regression against what it replaces. Make it a `private static final List<Key> ALL_SIGNATURES` built once, and **contract the sharing**: the returned list is immutable and is the same instance on every call. Add one case asserting the returned list rejects mutation.
  
3. **Amend `KeyTest`'s hand-written domain size.** `KeyTest.java:84`'s `.hasSize(15)` and the two `15`s in its class Javadoc (lines 40 and 44) are the domain's cardinality copied by hand — see **Conventions**. Derive from `1 + (2 * Key.MAX_ACCIDENTAL_COUNT)` and reword the Javadoc so it names the derivation rather than a number.
  
4. **Write the key change policy as `public final class songscribe.dom.KeyChange`, in `dom`.** This is where it belongs and where it stays: it is a pure function of two `Key`s, `songscribe.dom` imports `songscribe.ui.*` in zero places today and `songscribe.layout` imports `songscribe.ui.renderer` in zero places, and its three consumers are `KeySignatureElement` (`dom`, task 8), `HorizontalSpacingCalculator` (`layout`, Phase 6), the cautionary renderer (`ui.renderer`, Phase 5) and the MusicXML writer (`io.musicxml`, Phase 7). Placing it in the renderer would invert both layers at once. `songscribe.dom.KeySignature` already computes key-signature width and glyphs in `dom`, so the metrics are reachable from here.
  
5. **The cancellation policy is settled and confirmed by the domain owner. Do not re-derive it.** Given a previous key and a new key:
  

- **Same type, more accidentals** — no naturals; draw the new signature only.
  
- **Same type, fewer accidentals** — no naturals; draw the new signature only. The new signature is understood to supersede the old one (Gould, _Behind Bars_, and current software defaults).
  
- **Different type (sharps ↔ flats), including to or from** `NONE` — cancellation naturals for the **entire** previous signature, then the new signature.
  

This **overturns** the current same-type-fewer behavior, which draws naturals for the dropped accidentals. State in the commit message that this contract was changed deliberately as a domain correction — see _A failing test means one of three things_ in `~/.claude/rules/development.md`.

6. Give `KeyChange` this surface, each contracted **before** implementing:
  

```java
public static List<DrawnAccidental> accidentals(Key previous, Key next)
public static double widthSs(Key previous, Key next)
public record DrawnAccidental(SMuFLGlyph glyph, int staffPositionSp, double leadingGapSs) {}
public static final double RIGHT_MARGIN_SS = …
```

- `DrawnAccidental` moves here from `KeySignatureRenderer`, where it is a private nested record with exactly these components, and becomes public.
  
- `KEY_CHANGE_RIGHT_MARGIN_SS` moves here as `RIGHT_MARGIN_SS`. Phase 6's trailing reservation needs it from `layout`, and leaving it on the renderer would inject the same inversion by a smaller door.
  
- `widthSs` is the laid-out width including inter-glyph gaps and the cancellation/new-key separation. `KeySignatureRenderer.totalWidthSs(List<DrawnAccidental>)` and its `advanceSs` helper are exactly that arithmetic and move here as its body.
  
- **Contract `widthSs` as returning 0 when `previous.equals(next)`**, and `accidentals` as returning an empty list in the same case.
  
- Because naturals now **always** come first, three things in the renderer's current implementation become unreachable and are deleted rather than moved: the `startingOffsets` array (always 0), the `isNaturals` array (always `{true, false}`), and the reverse-gap handling in `advanceSs` / `collectAccidentals` that exists only for naturals-second.
  
- The staff-position tables (`FLAT_STAFF_POSITIONS`, `SHARP_STAFF_POSITIONS`, `KEY_STAFF_POSITIONS`) and `getGlyphForKeyType` move or are shared as the implementation requires.
  
- **`KeySignatureRenderer.renderKeyChange` is reduced to drawing in this phase**, not in Phase 5: it collects `KeyChange.accidentals(previous, next)` and paints them. Its current three-branch computation, `collectAccidentals`, `advanceSs` and `totalWidthSs` all leave the class. It still takes two `Line`s here — Phase 5 changes it to compare running keys and Phase 5 owns its position under overflow.
  

7. Write `KeyChangeTest` from those contracts:
  

- The policy as a parameterized table over **every ordered pair** from `Key.allSignatures()`, asserting which of the three branches each pair lands in. Driving from `allSignatures()` rather than a hand-written list is what keeps the claim true if the domain grows; derive the pair count from `allSignatures().size()` rather than writing it (see **Conventions**).
  
- `widthSs(k, k)` is 0 and `accidentals(k, k)` is empty, for every `k` in `allSignatures()` — the equal-keys clause, which the branch table does not assert.
  
- Naturals always precede the new signature's accidentals in the returned list, over the same table — the ordering invariant the deleted `isNaturals` array used to make variable.
  

8. Add a `KEY_SIGNATURE` constant to `songscribe.dom.ElementType`, **immediately after** `FINAL_DOUBLE_BARLINE` and before the deprecated alias block (`SEMIBREVEREST` onward). That position is settled and carries no hazard: the file's three ordinal-range predicates — `isNote()` (`SEMIBREVE`…`DEMI_SEMIQUAVER`), `isRest()` (`SEMIBREVE_REST`…`DEMI_SEMIQUAVER_REST`) and `isBarLine()` —
  

```java
ordinal() >= SINGLE_BARLINE.ordinal() && ordinal() <= FINAL_DOUBLE_BARLINE.ordinal()
```

— all end before it. Nothing persists an `ElementType` by ordinal (`StaffElementIO` round-trips the constant name through `ElementType.valueOf`), nothing indexes `values()` positionally, and the alias constants resolve through their `aliasOf` reference rather than ordinal arithmetic.

- `Key` (from Phase 1, `songscribe.dom.Key`) supplies `MAX_ACCIDENTAL_COUNT`; `Line.FLAT_SHARP_ORDINAL` has been reproduced there as `Key.altersPitchClass`.
  

9. Change `ElementType.cancelsAccidentals()` to include `KEY_SIGNATURE`. Its existing Javadoc already anticipates this — it reads "#53 (mid-line key changes) adds key changes here, and only here." Replace that forward reference with the accomplished fact, and keep the existing narrowness note (the same `isBarLine() || isRepeat()` test appears elsewhere for unrelated reasons and must not be generalized).
  
10. Give `KEY_SIGNATURE` its display width and bounds. Follow how the barline types get theirs in `computeBarlineBoundsSs()`; a key signature's width depends on its `Key`, which the enum constant does not know, so the enum-level bounds must be the zero/degenerate case and the real width comes from the element instance. State this explicitly in a comment, because every other type in the enum computes bounds statically and a reader will assume this one does too.
  
11. Write the contract for a new `public class KeySignatureElement extends StructuralElement` in `src/main/java/songscribe/dom/KeySignatureElement.java`, **before** implementing it:
  

- It holds one `Key` (from Phase 1), non-null, and reports `ElementType.KEY_SIGNATURE` from `getType()`.
  
- Class-level invariant, stated on the class: a `KeySignatureElement` is never the element at index 0 of a line, and is always immediately preceded by an element whose `ElementType.isBarLine()` or `isRepeat()` is true. It is enforced at all three entry points — the editing UI (Phase 12), the deletion pairing (Phase 9), and the MusicXML reader (Phase 7 task 8a) — and the class states the invariant that those rely on. The insert flow does not _restrict_ the user to positions after a barline — it **inserts a** `SINGLE_BARLINE` **when the chosen position lacks one**, which is how the invariant is kept (Phase 12).
  
- `getContentWidthSs()` returns the drawn width of the key's accidentals: `KeyChange.widthSs(previousKey, thisKey)` from task 6. There is no interim implementation and no second copy of the arithmetic — that is why the policy lands in this phase rather than Phase 5.
  
- `StructuralElement` already overrides `getStaffPosition`, `getDotCount` and `getAccidental` for non-pitched elements; inherit those rather than restating them.
  

12. Extend `ElementTypeTest` for the two promises this phase changes: `cancelsAccidentals()` is true for `KEY_SIGNATURE`, and `isBarLine()` is false for it. Drive the `cancelsAccidentals()` assertions from `ElementType.values()` against an explicit expected set, so adding a future constant fails the test rather than passing silently.
  
13. Run `./scripts/compile.sh`, then `./scripts/test.sh KeyTest`, `./scripts/test.sh KeyChangeTest` and `./scripts/test.sh ElementTypeTest`. All must report SUCCESS / green. `KeySignatureRendererTest` will fail here — Phase 5 rewrites it against the new policy; do not adapt it in this phase.
  

* * *
## ✅ Phase 3: Line Key Storage and Index Resolution
**Status:** Done  
**BlockedBy:** 1, 2 (both done)  
**Files:** src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/StaffElement.java, src/main/java/songscribe/dom/KeySignatureElement.java, src/main/java/songscribe/layout/AccidentalReconciliation.java, src/main/java/songscribe/message/mutation/LineKeyChange.java, src/main/java/songscribe/message/mutation/KeyField.java, src/test/java/songscribe/dom/LineMutationTest.java, src/test/java/songscribe/dom/KeySignatureElementTest.java  
**Recommended model/effort:** Opus, high — this is the semantic core; the "derived vs authored" distinction and the backward-walk resolution decide correctness for every downstream phase.

> **What this phase actually touched, and why the `Files:` list above is short.** Task 3's claim
> that the two writers' "only callers are this phase's own tests" was wrong: `setKeyType` /
> `setKeyAccidentalCount` had six production call sites (`Song` ×3, `LineIO`, `MutationReplayer`,
> `MeasureMapper`) and eighteen test files. They could not survive as deprecated delegates either,
> because every call site writes the pair in two statements and the intermediate state —
> `Key(SHARPS, 0)` — is what Phase 1's constructor throws on. Every call site was therefore
> rewritten to `setKey(new Key(type, count))` here, which is what kept the compile gate passable.
> Also pulled forward, because no coherent intermediate existed: the key half of
> `Song.applyLineDefaults` and its two `SongDefaultsTest` cases (Phase 4 task 5), a
> `KeySignatureMapping.toKey(int)` so fifths 0 decodes to a valid `Key` rather than
> `(FLATS, 0)` (Phase 7 task 10), and `LineIO`'s two half-key tests, which exercised a state the
> model no longer has. `Song.getDocumentKey()` was added as a `@Deprecated` normalizer over the
> two legacy fields — a count of 0 with a named accidental type is representable there and is not
> a key — and Phase 4 deletes it with them.

### Tasks
1. Read `.claude/guides/contracts.md`, `docs/mutations.md`, and `.agents/guides/null-handling.md` first.
  
2. Replace `Line`'s two key fields — `private @Nullable KeyType keyType` and `private int keys` — with a single `private @Nullable Key key` (`songscribe.dom.Key`, from Phase 1). Write the field's contract **before** the change. The settled meaning:
  

- **Non-null means a key change takes effect at the start of this line.** Null means the line inherits the key in effect at the end of the previous line.
  
- Line 0 of a song is always non-null; there is nothing for it to inherit from. State this as a class-level invariant on `Line`, not on the field alone.
  
- There is no song-wide key. A song's key is line 0's key. (Phase 4 removes `Song.defaultKeyType` / `defaultKeyAccidentalCount`; this phase must not depend on them.)
  

3. Add `@Nullable Key getKey()` and `setKey(@Nullable Key)`. Contract `getKey()`'s `@Nullable` return by naming what null _means_ (inherits from the previous line), not by restating the annotation.
  

- **Keep `getKeyType()` and `getKeyAccidentalCount()` as `@Deprecated` delegates over `getRunningKey()`, and delete `setKeyType()` / `setKeyAccidentalCount()` outright.** The readers have consumers in Phases 4, 5, 6, 7, 8 and 12 plus six test classes; deleting them here would leave the tree uncompilable until every one of those phases lands, so no phase between this one and Phase 12 could pass its own compile gate and the parallelism the dashboard advertises would be fiction. ~~The writers have no such problem — their only callers are this phase's own tests.~~ **Wrong; see the note at the top of this phase.** Javadoc each delegate with one line: *replaced by `getKey()` / `getRunningKey()`; removed in the phase that removes the last consumer.* Phase 17 deletes them.
  

- **`setKey` normalizes a no-op to null.** When the argument equals the key this line would inherit, the line's own key is set to **null** instead. A line holds a key only where one actually changes. Without this, a user who picks the key a line already inherits pins that line — visually identical (every header draws its key either way, Phase 12 task 3), but it now stops inheritance propagation, so a later change upstream silently fails to reach it or anything past it. State the rule in `setKey`'s contract; Phase 8's `.mssw` normalization becomes an instance of it rather than its own clause, and Phase 12 needs no guard of its own.
  
4. Add `public Key getRunningKey()` — the key actually in effect at the **start** of this line: this line's own `Key` when it has one, otherwise the key in effect at the end of the previous line, which accounts for any `KeySignatureElement` that changed it mid-line. Contract it as: never null; equals `getKey()` when `getKey()` is non-null. The contract states the resolved key the caller is entitled to and says nothing about how it is stored.
  

- It is called per note during accidental resolution, so a walk to line 0 on every call is not acceptable. **Maintain a** `private @Nullable Key inheritedKey` **on** `Line`, propagated forward, so the resolution is a field read and never a walk. This is state, not a cache — no invalidate-and-recompute path exists, and every mutation that could change it updates it.
  
  - Meaning of the field: the key in effect **at the end of the previous line**. It is null on line 0 and non-null on every other line. `getRunningKey()` is then `key != null ? key : requireInheritedKey()`.
    
  - Propagation runs when the key at the end of some line changes: setting or clearing a line's own `Key`, inserting/removing/editing a `KeySignatureElement`, and inserting or removing a line. Walk forward from the affected line assigning `inheritedKey`, and **stop after the first line that has its own non-null key** — that line's running key is unchanged, so nothing past it can move. A song with a key on every line therefore costs one step.
    
  - **There is exactly one hook, and it is `Song.applyChange(Mutation, Runnable)`.** Do not scatter propagation calls across the mutators. `Line` has seven element mutators that can carry a `KeySignatureElement` — `addElement` (both overloads), `setElement`, `modifyElement` (both overloads), `removeElement`, `removeRange` — and every one of them routes through `Line.applyChange` → `Song.applyChange`, as do `Song.addLine`, `Song.removeLine` and `setKey`. Hooking the mutators individually is ten sites, each independently forgettable, and each omission is silently wrong pitches with no error and no visual cue. Hooking `Song.applyChange` is one site, and it covers **undo and redo for free**, because replay goes through the same records.
    
  - After the mutator runs, switch on the mutation type and propagate for: `LineKeyChange`; any element insertion, deletion, replacement or range deletion whose affected elements include a `KEY_SIGNATURE`; `LineInsertion`; `LineDeletion`. Enumerate those cases explicitly rather than propagating unconditionally — an ordinary note edit must not walk the line list.
    
  - **Repair the line-0 invariant in the same hook.** Line 0 must have a non-null key and there is nothing for it to inherit, but `Song.addLine(0, line)` (`Song.java:946`) and `Song.removeLine(0)` (`Song.java:1025`) both promote a possibly-null-keyed line to index 0 — and Phase 4 deletes the key half of `applyLineDefaults`, so a new line's key is null. When a mutation leaves line 0 with a null key, **materialize the key it was inheriting**, inside the same modification bracket. That is the only outcome that leaves what the user sees unchanged. Without the repair, `getRunningKey()` on line 0 dereferences a null `inheritedKey`.
    
  - File readers suspend mutation tracking, so `Line.applyChange` runs the mutator directly and never reaches `Song.applyChange` — the hook is correctly skipped on load. Add a one-pass rebuild called at the end of a load, following the precedent of `Song.installTerminalAfterParsing()` (`Song.java:1103`) — same shape, same "must be called while tracking is suspended" note.
    
  - State the invariant on `Song`, which is what maintains it: _for every line other than line 0,_ `inheritedKey` _equals the previous line's key at its last element, and line 0's own key is non-null._ Test it as an invariant over a set of edits rather than pinning expected values — that assertion is what would catch a missed propagation case. **Write it once as a shared test helper** (`assertKeyPropagationInvariant(song)`) and call it after every edit in every key-touching test table, not only the edits task 12 lists. A helper called from one table is a helper that only guards one table.
    

4a. **Add `KeySignatureElement.getContentWidthSs()`, which Phase 2 could not.** It is `KeyChange.widthSs(line.keyAt(index - 1), getKey())`, where `line` is `getParentLine()` and `index` is `line.getElementIndex(this)` — and `keyAt` is task 5, below, which is why the override waits for this phase. Phase 2 wrote the element, its `Key`, its class invariant and the policy; only the previous-key lookup was missing, and the alternatives were a backward scan inside the element that this task would delete (a temporary duplicate) or pulling `keyAt` into Phase 2 ahead of the `inheritedKey` machinery it rests on. Contract the width as the drawn width of the change this element makes, and state that it is zero when the element re-states the key already in effect. Test it in `KeySignatureElementTest` against the class invariant: a key signature at index 0 has no previous element, which the invariant forbids, so no case needs to define a width there.

5. Add `public Key keyAt(int elementIndex)` — the key in effect at the given element index within this line: `getRunningKey()`, overridden by the last `KeySignatureElement` at or before `elementIndex`. Contract the boundary explicitly: a `KeySignatureElement` **at** `elementIndex` is in effect at that index (inclusive), and `keyAt(0)` always equals `getRunningKey()` because index 0 can never hold a `KeySignatureElement`.
  
6. Delete `Line.keyExists(int pitchType)` and `Line.FLAT_SHARP_ORDINAL`. Phase 1 moved that logic to `Key.altersPitchClass(int pitchIndex)`. Every caller resolves a `Key` first, then asks it.
  
7. Rewrite `StaffElement.keyAccidentalFor` to take a `Key` rather than a `Line`:
  

```java
public static @Nullable Accidental keyAccidentalFor(Key key, int staffPosition)
```

It returns `Accidental.FLAT` when the key is `FLATS` and alters that staff position's pitch class, `Accidental.SHARP` when `SHARPS` and likewise, and null otherwise. Keep the existing note explaining _why_ it is static and staff-position-taking (the projected-layout resolver in `AccidentalReconciliation` walks positions rather than live notes and shares this definition).

8. Rewrite `StaffElement.keyInEffectAt(Line targetLine, int index)` to actually honour `index`: `return keyAccidentalFor(targetLine.keyAt(index), staffPosition)`. Its current Javadoc says "Today a line carries exactly one key signature, so `index` is unused" and points forward to an issue number — replace all of it with the real contract, and write no issue reference in its place (see **Conventions**).
  

- **Take the key from the barrier when the barrier is the answer.** `findEffectiveAccidental` has two fallback sites. At the second (the scan ran out of elements) `keyAt(index)` is needed. At the first — the scan stopped at a `cancelsAccidentals()` element at `scanIndex` — **when that element is a `KeySignatureElement`, its key *is* the key in effect at `index`**: no later key signature can sit between `scanIndex` and `index`, or the backward scan would have reached that one first. Use it directly. Passing `index` back to `keyAt` there throws away the position the scan just found and walks the same elements a second time, per note, on a path that runs per layout pass, per `getPitch()` (MIDI export, playback, the status-bar readout) and per projected element in `AccidentalReconciliation`. The barline-barrier case still needs `keyAt`.
  
- State that reasoning in `findEffectiveAccidental`'s contract — the "no later key signature can intervene" step is what makes the shortcut correct, and a reader without it will see an inconsistency between the two fallback sites.
  
- Change neither call site's control flow beyond this.
  
9. Update `AccidentalReconciliation` (`src/main/java/songscribe/layout/AccidentalReconciliation.java`) so its projected-layout resolution asks for the key at the projected index rather than the line's single key. Its `resolveOverProjection` and the `ProjectedElement` walk are the sites. The two resolvers must agree — a preview and its committed result disagreeing about a pitch is the failure this guards.
  
10. Update `LineKeyChange` and `KeyField` in `src/main/java/songscribe/message/mutation/` so undo records a `Key` change rather than separate type and count fields. `KeyField`'s two constants (`ACCIDENTAL_COUNT`, and the type field) collapse to one mutation carrying old and new `Key`. Read `docs/mutations.md` for the record's obligations before changing it.
  
11. Update `LineMutationTest`'s key tests (`SetKeyType`, `SetKeyAccidentalCount` nested classes) to the new API. Write the test class's testing-approach Javadoc and triage the existing key tests against the new contract **as one task**: stating what the class is responsible for requires accounting for each existing test, which is the triage.
  
12. Add tests for the three new promises, derived from the contracts written in tasks 4 and 5:
  

- `getRunningKey()` equals `getKey()` when the line has its own key.
  
- A line with a null key returns the previous line's running key; a chain of several null-keyed lines all return the same one.
  
- `keyAt(0)` equals `getRunningKey()` on a line holding a mid-line `KeySignatureElement`.
  
- `keyAt(i)` for `i` at, just before, and just after a `KeySignatureElement`'s index — the inclusive boundary.
  
- A `KeySignatureElement` on line N changes what line N+1's `getRunningKey()` returns.
  
- `setKey` with the key the line already inherits leaves `getKey()` null — the no-op normalization from task 3.
  
- The propagation invariant holds after each of: setting a line's key, clearing it, inserting a line, removing a line, and adding a mid-line `KeySignatureElement`. Assert `assertKeyPropagationInvariant(song)` across every line of the song after each edit — one property, applied to a table of edits.
  

13. **Test undo and redo of a key change.** Task 10 rewrites the mutation record this phase depends on, and the propagation hook of task 4 lives in `Song.applyChange`, which replay routes through — so undo is the single case that proves the propagation survives replay. Nothing else in the plan covers it, and the failure mode is wrong pitches on every downstream line with no error.
  

- Undo of a line-key change restores the old key **and** satisfies `assertKeyPropagationInvariant`; redo re-applies both.
  
- Undo of a mid-line `KeySignatureElement` insertion satisfies the invariant.
  

14. **Test the line-0 boundary**, which task 4's repair creates:
  

- `addLine(0, line)` with a null-keyed line leaves line 0 holding the key it was inheriting, and `getRunningKey()` on it does not throw.
  
- `removeLine(0)` leaves the promoted line holding what it was inheriting.
  

15. Run `./scripts/compile.sh`, then `./scripts/test.sh LineMutationTest` and `./scripts/test.sh StaffElementTest` (if present). All must report SUCCESS / green. **The compile gate is passable here** because task 3 keeps the two reader accessors alive as deprecated delegates — if it is not passing, the delegates are missing or a writer is still referenced. Do not edit files outside this phase's `Files:` list; report anything that still will not compile.
  

* * *
## ✅ Phase 4: Remove the Song-wide Key
**Status:** Done  
**BlockedBy:** 3, 16  
**Files:** src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/message/SongData.java, src/main/java/songscribe/message/mutation/MetadataField.java, src/main/java/songscribe/undo/MutationReplayer.java, src/main/java/songscribe/message/notification/KeySignatureDidChangeNotification.java, src/main/java/songscribe/ui/dialog/SongSettingsDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsMusicTab.java, src/test/java/songscribe/dom/SongDefaultsTest.java, src/test/java/songscribe/dom/SongNotificationHandlerTest.java, src/test/java/songscribe/ui/dialog/SongSettingsDialogTest.java  
**Recommended model/effort:** Opus, high — deletes a propagation heuristic with undo and notification consequences; deciding what each removed promise is replaced by is design work, not mechanical.

> **What this phase actually touched, and why the `Files:` list above is short.** Deleting the two
> fields breaks every reader and writer that consumed them, and six of those files belong to later
> phases. The compile gate is not optional (see **Conventions**), so each was converted here, to its
> final home rather than to an interim shape:
>
> - **`Song.getStartingKey()` replaces `getDocumentKey()`** — the key the song starts in, which is
>   line 0's running key. Written once here rather than `getLine(0).getRunningKey()` at each of the
>   four call sites.
> - **`SongIO`** (Phase 8) stops writing the song-level `<keys>`/`<keytype>` (Phase 8 task 5) and
>   keeps reading them: its `DocumentReader` now holds the pair as a `documentKey()` and applies it
>   to line 0 when the file gave line 0 none, before the lines reach the `Song`. The normalization
>   `getDocumentKey()` carried — a named type with a count of 0, or `NONE` with a count, both mean C
>   major — moved there with it. **`LineIO.writeLine`** now writes a line's key exactly where the
>   line establishes one, instead of comparing against the song default.
> - **`MeasureBuilder`/`MeasureMapper`** (Phase 7): `effectiveKeyFifths` became a function of
>   `line.getRunningKey()` (Phase 7 task 3) and its dead null branch went with the song default;
>   `newScoreState` and `buildAttributes` share one `startingKeyFifths`, so the seed and measure 1's
>   `<key>` cannot disagree. `applyFifths`'s measure-1 branch now sets the first line's key like any
>   other, because there is no song default left to seed. Phase 7's mid-line emission and its
>   `<cancel>` work are untouched.
> - **`KeySignatureChangeDialog`** (Phase 12) commits through `line.setKey` inside a modification
>   bracket instead of posting the deleted notification. Phase 12 still rewrites the dialog whole.
> - **`UndoController.metadataOpNameKey`** lost its two key cases with the enum constants.
> - Test files beyond the three listed: `SongIsEmptyTest`, `SongLoadingTest`, `SongSetterMutationTest`,
>   `SongIOTest`, `LineIOTest`, `MutationRecordsTest`, `MutationLabelTest`,
>   `MutationReplayerRoundTripTest`, `MusicXmlBarlineRoundTripTest`, `MusicXmlKeyRoundTripTest`,
>   `MusicXmlDocumentRoundTripTest`, `KeySignatureChangeDialogTest`.
>
> `KeySignatureRendererTest`'s two cancellation-policy cases still fail, as Phase 2 task 13 states.
> Phase 5 owns them.

### Tasks
1. Read `docs/mutations.md`, `docs/messages.md`, and `.claude/guides/contracts.md` first.
  
2. Delete `Song.defaultKeyAccidentalCount`, `Song.defaultKeyType`, their getters (`getDefaultKeyAccidentalCount`, `getDefaultKeyType` at `Song.java:625` and `:629`), and their setters (`setDefaultKeyAccidentalCount`, `setDefaultKeyType` at `Song.java:818` and `:825`). A song's key is line 0's key (`Song.getLine(0).getKey()`), which Phase 3 guarantees is non-null.
  
3. Delete `Song.DEFAULT_KEY_TYPE` and `Song.DEFAULT_KEY_ACCIDENTAL_COUNT`. Phase 1 replaced them with `Key.DEFAULT`, which holds the same value — 5 flats, major — as one thing instead of a loose pair the caller has to keep consistent.
  

Replace their uses in `Song`'s initialization (`Song.java:275`, `:282-283`) with `Key.DEFAULT` assigned to line 0, and update every other reference `jet_brains_find_referencing_symbols` turns up before deleting them.

4. **Delete `KeySignatureDidChangeNotification` and `Song.keySignatureDidChange` entirely** (`Song.java:1477-1499`), along with the propagation heuristic inside the handler. Do not update the record — it has no posters left.
  

- The heuristic rewrites every line whose key equals the old default — a guess that silently overwrites lines the user set deliberately to that key. Inheritance replaces it: changing line 0's key reaches every line that inherits from it via the `inheritedKey` propagation in `Song.applyChange` (Phase 3 task 4), and a line with its own key stops it.
  
- `jet_brains_find_referencing_symbols` gives exactly two posters: `SongSettingsDialog.applyMusicTabChanges:481`, which **task 8 deletes**, and `KeySignatureChangeDialog.setData:91`, which **Phase 12d task 3 rewrites** to commit through `ScoreViewController` (Phase 10 task 6) rather than through the message bus. After both, the record has no poster and the `@Handler` is unreachable — keeping it, or spending work making its `lineIndex` non-nullable, is work for code with no callers.
  
- Deleting it also removes the four `SongNotificationHandlerTest` cases and three `SongSettingsDialogTest` cases that drive the handler directly. Those go under task 9's triage, not as a separate step.
  
5. ~~Delete the key half of `Song.applyLineDefaults`.~~ **Done in Phase 3.** It was the whole method, so `applyLineDefaults` and both its call sites are gone; its two `SongDefaultsTest` cases were replaced by one asserting that a new line establishes no key of its own. Nothing is left here.
  
6. Remove `defaultKeyAccidentalCount` and `defaultKeyType` from the `SongData` record (`src/main/java/songscribe/message/SongData.java:66-67`) and every producer and consumer of those components.
  
7. Remove `MetadataField.DEFAULT_KEY_ACCIDENTAL_COUNT` and `DEFAULT_KEY_TYPE`, and their two cases in `MutationReplayer` (`MutationReplayer.java:236-237`).
  
8. Remove the key-signature section from the song settings dialog: `SongSettingsMusicTab.createKeySignatureSection()` (`SongSettingsMusicTab.java:117`) and its call at line 101, `getKeyTypeAndCountFromCombo()` at line 193, the key branch of `SongSettingsDialog`'s commit path (`SongSettingsDialog.java:441`, `:465`, `:482`), and the strings that go with it.
  

- **Phase 16 owns the cell renderer and `KeySelection`.** It renames `SongSettingsKeyCellRenderer` to `KeyCellRenderer`, retypes it to `Key`, and deletes `SongSettingsDialog.KeySelection` — a shared-dialog refactor that has nothing to do with removing a song-wide key, and is a prerequisite for Phase 12's combo. This phase deletes only the settings dialog's *use* of them; do not rename or move anything here.
  
- Read `.agents/guides/strings.md` before removing any user-facing string, and remove the orphaned string keys.
  

9. Update `SongDefaultsTest`, `SongNotificationHandlerTest` and `SongSettingsDialogTest`. Write each test class's testing-approach Javadoc and triage its existing key tests against the new contract **as one task** — the tests asserting the propagation heuristic (`testSongLevelChangePropagatesToMatchingLinesOnly`) assert a promise that no longer exists and are deleted, not adapted, as are the cases driving the deleted notification (task 4). State in the commit message that this contract was removed deliberately, because a diff that deletes tests otherwise reads as making a change pass.
  
10. Run `./scripts/compile.sh`, then `./scripts/test.sh SongDefaultsTest`, `./scripts/test.sh SongNotificationHandlerTest` and `./scripts/test.sh SongSettingsDialogTest`. All must report SUCCESS / green.
  

* * *
## ✅ Phase 5: Cautionary Rendering and Overflow Position
**Status:** Done  
**BlockedBy:** 2, 3 (both done)  
**Files:** src/main/java/songscribe/ui/renderer/KeySignatureRenderer.java, src/main/java/songscribe/ui/component/score/LineRenderer.java, src/main/java/songscribe/dom/KeySignature.java, src/main/java/songscribe/engraving/StaffHeaderMetrics.java, src/test/java/songscribe/ui/renderer/KeySignatureRendererTest.java, src/test/java/songscribe/ui/component/score/LineRendererTest.java, src/test/java/songscribe/engraving/StaffHeaderMetricsTest.java  
**Recommended model/effort:** Opus, high — the overflow-relative position has no precedent in this renderer and the test triage runs against a contract Phase 2 already overturned.

> **What this phase actually touched, and why the `Files:` list above is short.** Task 5b's
> `KeySignature.widthSs(Key)` has no callers that can still pass a `(KeyType, int)` pair, so
> **Phase 6 tasks 5 and 6 landed here** rather than as an interim shape:
>
> - **`HorizontalSpacingCalculator`**: `calculateFirstElementXSs` and `calculateHeaderRightEdgeSs`
>   collapsed their `(@Nullable KeyType, int)` overloads to a single `Key` parameter, and their
>   `(Line)` forms now pass `line.getRunningKey()`. `calculateHeaderRightEdgeSs(null)` — the "no
>   line, so no key signature" case — resolves to `(NONE, 0)`.
> - **`LayoutEngine.createHeaderElements`**: the `rawKeyType != null ? rawKeyType : KeyType.NONE`
>   coalescing is gone; the header is built from `line.getRunningKey()`, and the method's contract
>   now states that a line inheriting a key still shows it.
> - Tests beyond the three listed: `KeySignatureTest` (rewritten against `Key`),
>   `HorizontalSpacingCalculatorTest`, `LayoutEngineTest`, `LayoutResultTest`, `PasteModeManagerTest`.
>
> **`KeySignature.hasAccidentals()` was deleted** with the two-field pair it existed to reconcile:
> under `Key`'s biconditional invariant it only restates `keyType() != NONE`, and both callers ask
> the key.
>
> **Task 4's gap is one line rest** (`Song.getDefaultRestLengthSs()`), confirmed by the domain owner
> — the same trailing gap layout reserves after a last element that is not the flush-right terminal.
> The run is measured off the *rightmost* column edge rather than the last column's, so a wide
> trailing extent anywhere in the chain still pushes the cautionary clear of it.
>
> **`renderKeyChange` now takes two `Key`s rather than two `Line`s** (Phase 2 task 6 anticipated
> this). Resolving the running keys belongs to `LineRenderer.renderKeyChanges`, which is the
> caller that knows the song and the line index; the renderer paints. Its `keyOf(Line)` helper —
> which existed only to collapse the two spellings of "no key" — is gone.

### Tasks
1. Read `.claude/guides/contracts.md` first.
  
2. **The cancellation policy and its width function are Phase 2's, in `songscribe.dom.KeyChange`.** Do not restate the policy here, do not reimplement it, and do not re-derive it. This phase decides *when* a cautionary is drawn and *where* it is drawn; `KeyChange.accidentals(previous, next)` decides *what*.
  
3. Change the cautionary trigger in `LineRenderer.renderKeyChanges` to compare **running keys**: draw when `song.getLine(lineIndex + 1).getRunningKey()` differs from this line's key at its last element (`line.keyAtEndOfLine()`), not the raw `Line` fields it compares today. The last-line guard (`lineIndex + 1 >= song.lineCount()`) stays.
  
4. Fix the cautionary's position under overflow. `renderKeyChange` currently right-aligns to `song.getLineWidthSs()` unconditionally, so on an overflowing line the notes run past the margin while the cautionary stays at it, colliding. Follow the precedent at `LayoutEngine.java:405`, where `positionTerminalFlushRight` is deliberately skipped on an overflowing line because nothing should pin to a margin the content has passed: when the line's `LayoutResult.overflowsStaffWidth()` is true, position the cautionary off the solved chain's end instead of the margin. Contract this on `renderKeyChange`, because Phase 12b's hit target follows the glyphs and depends on it.
  
5. Delete `songscribe.dom.KeySignature`'s mutable state if nothing outside layout still needs it: `setKeyType`, `setAccidentalCount`, and the constructor's `Math.clamp(accidentalCount, 0, MAX_ACCIDENTAL_COUNT)`. Clamping silently accepted corrupt input; `Key` (Phase 1) now rejects it at construction. `KeySignature` stays as the header's transient layout box, holding a `Key`. `MAX_ACCIDENTAL_COUNT` moved to `Key` in Phase 1 — update the references here.
  
5a. **Delete `StaffHeaderMetrics.KEY_TO_CANCELLATION_GAP_SS`** and the two `StaffHeaderMetricsTest` cases that are now its only readers (`testKeyToCancellationGapMatchesItsLilyPondSpaceAlistEntry` and `testTheTwoCancellationGapsAreNotInterchangeable`). Phase 2's policy puts naturals first in every case, so no production code reads the reverse gap any more and it is test-only surface. Decided by the domain owner. Reword `StaffHeaderMetrics`'s class Javadoc, which currently says the tables are directional because "each pair of parts has its own entry for each order they can appear in" — only one order can now appear.

5b. **Collapse the duplicate header-width arithmetic.** `KeySignature.widthSs(KeyType, int)` computes `count × StaffHeaderMetrics.accidentalInkBboxSs(glyph)`, which is what `KeyChange.totalWidthSs(KeyChange.signatureAccidentals(key))` computes by the other route. Once this phase retypes `KeySignature` to hold a `Key`, the method takes a `Key` and delegates, so the header and the cautionary cannot drift apart. The clamping this task already removes is what makes the delegation safe — `Key` rejects an out-of-range count rather than measuring it.

6. Rewrite `KeySignatureRendererTest`. Write the test class's testing-approach Javadoc and triage its existing tests against the new contract **as one task**. The policy itself is tested in `KeyChangeTest` (Phase 2 task 7) and is not retested here; what remains for this class is drawing, triggering and position.
  

- `testRenderKeyChangeSameTypeRemovingAccidentalsDrawsNewKeyAndNaturals` asserts the overturned promise — rewrite it to assert no naturals are drawn.
  
- `testRenderKeyChangeUsesTheReverseGapWhenTheNaturalsComeSecond` exercises a branch that no longer exists — delete it.
  
- `testRenderKeyChangeKernsNaturalsApart` — keep only if naturals-first kerning is still a promise; re-derive it from `KeyChange`'s contract rather than adapting the old test.
  
- Add the two sides of the overflow position: on a line that fits, the cautionary right-aligns to `song.getLineWidthSs()`; on a line where `overflowsStaffWidth()` is true, it sits off the solved chain's end. That is the boundary task 4 creates, and nothing else asserts it.
  

7. **`docs/line-layout.md` is not this phase's** — Phase 6 owns it. Nothing in `docs/` describes cautionary positioning today; if this phase's overflow rule proves hard to state in the method contract alone, add it to `docs/key-signatures.md`'s outline for Phase 13 rather than inlining a diagram in the renderer.
  
8. Run `./scripts/compile.sh`, then `./scripts/test.sh KeySignatureRendererTest` and `./scripts/test.sh LineRendererTest`. All must report SUCCESS / green.
  

* * *
## ✅ Phase 6: Layout Reservation and Header Spacing
**Status:** Done  
**BlockedBy:** 2, 3, 5 (all done)  
**Files:** src/main/java/songscribe/layout/HorizontalSpacingCalculator.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/layout/KeyEditFitCalculator.java, docs/line-layout.md, src/test/java/songscribe/layout/HorizontalSpacingCalculatorTest.java, src/test/java/songscribe/layout/KeyEditFitCalculatorTest.java  
**Recommended model/effort:** Opus, high — the spring-solver reservation feeds both full layout and the insertion pre-check; getting the shared-verdict property wrong desynchronizes preview from result.

> **What this phase actually touched, and why the `Files:` list above is short.**
>
> - **`Line.nextLineRunningKey()` is new**, and both the reservation (task 2) and
>   `LineRenderer.renderKeyChanges` (Phase 5) go through it. Without it the "is there a next line,
>   and what key does it begin in" step would have been written twice, in layout and in the
>   renderer, and the two would decide independently whether a cautionary exists. `LayoutEngine`
>   itself needed no change: task 4's shared-verdict property was verified rather than assumed —
>   every fit path (`solveLine`, all three `InsertionSpacingCalculator` projections,
>   `LyricEditFitCalculator`) reaches `trailingReservationSs`, so the widening reaches them all.
> - **A mid-line key signature was being measured at its type's floor, not its drawn width.**
>   `ElementColumnBuilder.calculateRightExtentInternal`'s non-note branch returned
>   `type.getElementWidthSs()`, which for `KEY_SIGNATURE` is the deliberate one-accidental floor
>   Phase 2 set. Task 7's minimum column spacing is unstateable against a floor — a following note
>   would be placed over the accidentals — so the branch now asks the element. This fixes the
>   *committed* layout, not only the pre-check.
> - **`KeySignatureElement.getContentWidthSs()` is now total, and gained
>   `KeySignatureElement.forMeasurement(key, previousKey)`.** It previously threw when the element
>   was on no line, which made every projection path (the fit pre-checks, an insertion preview, a
>   clipboard fragment) a latent crash, and left the pre-check no way to measure a signature before
>   it is committed. The key changed from now resolves in three steps — a measurement snapshot, the
>   parent line, then C major — each contracted. Deliberate contract change; the C-major clause is
>   documented as under-reserving for a cancelling change, so an edit is never gated on it.
> - **`trailingReservationSs` and `solveLine` each gained a next-running-key form.** The
>   two-argument `trailingReservationSs` and three-argument `solveLine` are unchanged for callers
>   and resolve the real next line; the pre-check passes a hypothetical key instead. That is what
>   lets `cautionaryFits` run the identical solve rather than a parallel one.
> - **`cautionaryFitsSs` / `keySignatureFitsSs` are named `cautionaryFits` / `keySignatureFits`.**
>   The `Ss` suffix marks a spatial value (`docs/unit-conversion.md`); these return booleans. Both
>   also derive the staff margin from `line.getSong().getLineWidthSs()` rather than taking it as a
>   parameter, following `InsertionSpacingCalculator.hasRoomForGraceNote`, which keeps
>   `keySignatureFits` inside the four-parameter limit.
> - **The reservation tests went to `HorizontalSpacingCalculatorSpringTest`, not
>   `HorizontalSpacingCalculatorTest`.** Every existing `trailingReservationSs` and `solveLine` case
>   already lives there; `HorizontalSpacingCalculatorTest` states in its own Javadoc that it holds
>   header geometry only. Splitting one method's cases across two files to match the `Files:` list
>   would have been the worse choice.
> - Tests beyond the two listed: `HorizontalSpacingCalculatorSpringTest` (the cautionary
>   reservation table and the mid-line key signature's width and both gaps),
>   `KeySignatureElementTest` (the width contract's three sources, consolidated into one table),
>   `LineMutationTest` (`nextLineRunningKey`, both null cases included).
> - `docs/line-layout.md` Example 1 was rewritten and Examples 1a (mid-line key signature), 1b
>   (cautionary) and 1c (what a key change costs, and where it is checked) added; the stale
>   `FIRST_NOTE_OFFSET_SS` reference in the old Example 1 named a constant that no longer exists.
>
> **Three findings raised after the phase's first pass, and fixed on the domain owner's call.**
>
> - **A key change costs four things, and task 3 named two of them.** Changing a line's key also
>   widens or narrows that line's own header, and — through inheritance — the header of every line
>   that inherits from it, along with the cautionary at the end of each. A check covering only the
>   previous line's cautionary accepts an edit that then overflows somewhere else. `cautionaryFits`
>   was therefore folded into **`lineKeyChangeFits(Line, Key, LyricRenderMetrics)`**, which walks
>   the inheritance chain — the same forward walk `Song`'s propagation makes, stopping at the first
>   line with a key of its own — and measures every line it re-keys. `keySignatureFits` walks it
>   too, because a mid-line change moves the key its line leaves off in. Neither half can now be
>   called without the other; the class exposes no partial query.
> - **The keys a solve reads off a line became `songscribe.layout.LineKeys`** — header key, key at
>   end of line, next line's running key. They travel together because an edit moves all three at
>   once, and a pre-check that projected one while reading the other two off the unedited document
>   would measure a line that will never exist. `HorizontalSpacingCalculator.solveLine` and
>   `calculateAnchorXSs` gained forms that take them; `LineKeys.of(line)` is what the committed
>   layout passes. `Line.lastKeySignatureKey()` is new, and is what lets a projected end-of-line key
>   be computed: a line with a mid-line change ends in that change's key whatever it started in.
> - **`InsertionSpacingCalculator`'s three fit gates now call `solveLine` instead of reassembling
>   its steps.** They built `buildSprings` → `applyLyricLift` → `calculateAnchorXSs` →
>   `trailingReservationSs` → `solveChain` by hand and so **skipped `OpticalSpacing.applyCorrections`,
>   which `solveLine` applies** — a real disagreement with the committed layout, in three classes
>   whose contracts promise the two can never disagree. The three result records now carry
>   `(Line line, List<ElementColumn> projectedColumns)` in place of the springs/anchor/reservation
>   triple, which is both smaller and the only shape that cannot drift.
> - **`ElementType.KEY_SIGNATURE.getInstance()` returned a plain `StructuralElement`**, so an element
>   reached through the type registry had no `Key` to ask for and a `newInstance()` clone could not
>   be added to a line. `createDefaultInstance` now builds a `KeySignatureElement` in C major — the
>   key that draws nothing — and `ElementTypeTest` drives the assertion from `values()`.
> - Tests changed for the above: `KeyEditFitCalculatorTest` (rewritten around the four costs, each
>   isolated in a fixture where it is the only one and each pinned by bisection),
>   `InsertionSpacingCalculatorTest`, `LineComponentTest`, `ElementTypeTest`, `LineMutationTest`.

### Tasks
1. Read `docs/line-layout.md`, `docs/unit-conversion.md`, and `.claude/guides/contracts.md` first.
  
2. Widen `HorizontalSpacingCalculator.trailingReservationSs(ElementColumn lastColumn, Line line)` to reserve space for the cautionary key signature drawn at the end of the line. It currently returns `lastColumn.getRightExtentSs() + (isAutoMaintainedTerminal ? 0 : song.getDefaultRestLengthSs())`. It becomes that, with the trailing gap raised to `max(lineRest, cautionaryWidth + KeyChange.RIGHT_MARGIN_SS)` when the next line's running key differs.
  

- `cautionaryWidth` is `KeyChange.widthSs(previous, next)` — `songscribe.dom.KeyChange` from Phase 2, **not** the renderer — where `previous` is `line.keyAtEndOfLine()` and `next` is the following line's `getRunningKey()`. `RIGHT_MARGIN_SS` lives there too, for the same reason: `songscribe.layout` imports `songscribe.ui.renderer` in zero places today and this phase must not be the first.
  
- There is no next line for the last line, so the reservation is unchanged there.
  
- Update the method's contract to state the new clause. Its existing note about why a look-alike terminal on a non-last line still owes the trailing rest stays.
  

3. **Add a fit pre-check for interactive key edits.** #53 settles this: before a key change is accepted, the cautionary it creates on the previous line must be shown to fit, and an added mid-line key signature must be shown to fit on its own line; when either does not, the user is alerted and the modification is rejected. Build the query here, in layout, and let Phase 12 call it — the dialog decides what to say, layout decides what fits.
  

- Follow `songscribe.layout.LyricEditFitCalculator` exactly: it is the existing precedent for "pre-check an edit, reject it if the line cannot hold it," and its `lineFits` / `lyricEditFits` pair is the shape. Add `KeyEditFitCalculator` beside it rather than growing a second idiom.
  
- Two queries, both contracted **before** implementing:
  
  - `cautionaryFitsSs(Line previousLine, Key next)` — whether `previousLine` still solves feasibly once its trailing reservation is widened for a cautionary to `next`. This is the check for changing a line's key when that line previously inherited.
    
  - `keySignatureFitsSs(Line line, int insertionIndex, Key key)` — whether `line` still solves feasibly with a `KeySignatureElement` for `key` spliced in at `insertionIndex`. **When the element at** `insertionIndex - 1` **is not a barline or repeat, the width of the** `SINGLE_BARLINE` **that Phase 12c inserts alongside it is part of this measurement**, because that barline is part of the edit; a check that omitted it would accept an edit that then overflows.
    
- Both must go through the same `solveChain` / `InsertionSpacingCalculator` splice the committed layout uses, so a pre-check verdict and the resulting layout can never disagree. `HorizontalSpacingCalculator.java:422` already states that property for the existing pre-check; these two inherit it and their contracts say so.
  
- Overflow stays for every other cause. Rejection covers the two interactive edits #53 names; a line that overflows on load, on a font change, or from any non-key edit still goes through `SpringSpacer.solve` → `infeasible()` → `LayoutEngine.java:299` `setOverflowsStaffWidth` → `LineRenderer.java:216` overflow color → `LineComponent.warnLineOverflows`. A file whose key change no longer fits after a page-size change is rendered overflowing, not refused — refusing would make a document unopenable.
  
- **Deliberate divergence from `LyricEditFitCalculator`, decided by the domain owner: an already-overflowing line rejects key edits too.** `LyricEditFitCalculator`'s class doc states the opposite for lyrics — *"an already-overflowing line is deliberately not blocked, so the user can still shorten a syllable to recover"* — and this phase's instruction to follow it "exactly" stops here. State the divergence and the decision in `KeyEditFitCalculator`'s class doc, because a reader who checks the precedent will otherwise read it as an oversight and "fix" it. The consequence, which the doc must name: on an overflowing line the user cannot simplify the key signature to recover, even though a narrower signature would help.
  

4. Because `solveLine` is shared by full layout and the insertion pre-check — its contract says both "get the same anchor, span and solve, so their verdicts agree" — the widened reservation reaches the pre-check automatically. Verify that property still holds rather than assuming it; if the pre-check splices columns through a path that bypasses `trailingReservationSs`, that is a defect to report, not to work around.
  
5. ~~Update `calculateFirstElementXSs(Line)` and `calculateHeaderRightEdgeSs(Line)` to take the line's running key (`line.getRunningKey()`) rather than its raw `getKeyType()`/`getKeyAccidentalCount()`. Both have `(KeyType, int)` overloads — collapse those to take a `Key`, which removes the loose pair from the signature.~~ **Done in Phase 5** — task 5b's `KeySignature.widthSs(Key)` left these two with no `(KeyType, int)` caller. See the note at the top of Phase 5.
  
6. ~~Update `LayoutEngine.createHeaderElements` (around `LayoutEngine.java:1429-1433`). It currently does `var rawKeyType = line.getKeyType(); var keyType = rawKeyType != null ? rawKeyType : KeyType.NONE;` — that null-coalescing is exactly the two-unset-states defect this work removes. It becomes `line.getRunningKey()`, which is never null.~~ **Done in Phase 5**, for the same reason — the `KeySignature` constructor now takes a `Key`.
  
7. **Contract the minimum column spacing on both sides of a mid-line `KeySignatureElement`, then test it.** They are ordinary elements in the line's element list, so the spring chain places them like any other column — but "verify the gap is sane" states no promise and yields no test, so state the promise: given the invariant that a key signature always follows a barline or repeat and (except at the end of a line) precedes a note, `calculateMinimumColumnSpacingSs` returns a named minimum for the barline→key-signature pair and for the key-signature→note pair. Give each a value **and** say what the caller is entitled to at it. Add the two cases to `HorizontalSpacingCalculatorTest` under task 8.
  
8. **Update `docs/line-layout.md`.** Its "First note on a line, with or without key signature" rule at line 64 and the `[Clef][KeySig] ---> 3.5 ss ---> [First Note]` figure at line 69 now resolve through the line's *running* key rather than a per-line field, and a key signature can now appear mid-line as well as in the header. Fix both, in this phase — the phase that invalidates a doc section updates it (see **Conventions**).
  
9. Extend `HorizontalSpacingCalculatorTest` from the changed contracts:
  

- `trailingReservationSs` returns the plain line rest when the next line's running key matches.
  
- It returns the cautionary width plus margin when that exceeds the line rest.
  
- It returns the line rest when the cautionary is narrower than it — the `max` boundary, tested at both sides.
  
- It is unchanged on the last line.
  
- The minimum column spacing on each side of a mid-line `KeySignatureElement` (task 7) — one case per side.
  
- Before writing each test method, check whether it will sit beside a same-shape sibling; if so, both are rows in one parameterized table.
  

10. Write `KeyEditFitCalculatorTest` from the two contracts in task 3:
  

- A near-empty line accepts a cautionary; a line filled to the margin rejects one — the two sides of the verdict.
  
- `keySignatureFitsSs` at a position **not** preceded by a barline rejects an edit that the same position would accept if the barline width were omitted. This is the case that proves the auto-inserted barline is in the measurement, and it is the one a naive implementation gets wrong.
  
- The pre-check's verdict matches what the committed layout does: for a set of lines spanning accept and reject, a line the check accepts lays out without `overflowsStaffWidth`. Assert it as a property over the set, which is the agreement guarantee itself, rather than pinning widths.
  
- An already-overflowing line rejects a key edit that would make it *narrower* — the deliberate divergence from `LyricEditFitCalculator` recorded in task 3. Pin it, so the decision cannot be quietly reversed by someone matching the precedent.
  

11. Run `./scripts/compile.sh`, then `./scripts/test.sh HorizontalSpacingCalculatorTest` and `./scripts/test.sh KeyEditFitCalculatorTest`. All must report SUCCESS / green.
  

* * *
## ✅ Phase 7: MusicXML Key Export/Import
**Status:** Done  
**BlockedBy:** 2, 3 (both done)  
**Files:** src/main/java/songscribe/io/musicxml/MeasureBuilder.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/KeySignatureMapping.java, src/test/java/songscribe/io/musicxml/MusicXmlKeyRoundTripTest.java, src/test/java/songscribe/io/musicxml/MusicXmlDocumentRoundTripTest.java  
**Recommended model/effort:** Opus, high — the write placement is asymmetric (a line-boundary change emits into the _next_ line's measure) and the read is its inverse, which is the fiddly direction.

> **What this phase actually touched, and why the `Files:` list above is short.**
>
> - **Task 4's write-side assertion was not written, on the domain owner's call.** A mid-line key
>   signature not preceded by a barline is impossible: the editing UI maintains the invariant, and
>   `KeySignatureElement`'s class doc already states that MusicXML writing *reads* it rather than
>   re-checking it. `DocumentValidation.corrupt` returns a checked `SAXException` and the whole
>   write path declares none — `SongFileWriter.write` (×4), `MusicXmlWriter.writeSong` (×2),
>   `ScorePartwiseBuilder.build`, `MeasureBuilder.buildLine` — so the task confused the two
>   directions. **The read side is where the check belongs and is where it landed** (task 7a).
> - **`ScoreState` carries a `Key`, not an `int` of fifths.** The line-boundary comparison, the
>   mid-line advance and the `<cancel>` all need the key rather than its encoding, and decoding the
>   running fifths back into a key at each of them would have been the mapping run backwards three
>   times. `effectiveKeyFifths` and `startingKeyFifths` are gone with it: `line.getRunningKey()`
>   and `song.getStartingKey()` say the same thing without a helper.
> - **The reader no longer pins each line with the carried-forward key**, which is what
>   `startNewLine` did. Under Phase 3's model a line with no `<key>` of its own *inherits*, and
>   pinning it round-trips identically while silently stopping a later edit to an earlier line's
>   key from propagating past it. `runningFifths` and `keySeen` are gone; the walk's only new state
>   is `lineStartMeasureIndex`, which is what distinguishes a line's own key from a mid-line change.
> - **`DocumentValidation.corrupt` is now public**, with a contract. `songscribe.io.musicxml`
>   reports the same class of failure and supplies its own logger, exactly as `MusicXmlUnits`
>   already does for `parseIntOrThrow`. It replaces this package's ad-hoc
>   `throw new SAXException("Corrupt document: …")`, which logged nothing — the remaining instance
>   of that idiom is `MeasureMapper.requireNoOpenTuplet`, left alone as outside this phase's
>   subject but worth folding in.
> - **`MusicXmlTags` gained `CANCEL`, `MODE` and `MODE_MAJOR`.** `<cancel>`'s value is set through
>   `Cancel.setValue` (it is the element's `@XmlValue`, not a `<fifths>` child), and `<key>`'s child
>   order comes from the generated class's `propOrder`, so unlike `<lyric>` the builder cannot get
>   it wrong.
> - **`MeasureBuilder` imports `songscribe.dom.Key` and fully-qualifies the ProxyMusic one** in
>   `buildKey`'s return type, the file's only remaining mention of it. The reverse would have put
>   the qualified name in four signatures instead of one.
> - Files beyond the list: `MusicXmlTags.java`, `songscribe/io/DocumentValidation.java`,
>   `docs/musicxml-object-model.md` (the mapper's state fields, and a new *Where a key signature
>   lands, in both directions* section).

### Tasks
1. Read `docs/musicxml-object-model.md` and `.claude/guides/contracts.md` first.
  
2. **The MusicXML mapping is settled. Do not re-derive it from the spec.**
  

- A `<key>` lives in the `<attributes>` of the measure where the key **takes effect**.
  
- A cautionary key signature at the end of a system is **rendering only** and is never written. Multiple sources confirm it should not be represented in MusicXML.
  
- Therefore: a `Line` with a non-null `getKey()` emits `<key>` into that line's **first** measure. A mid-line `KeySignatureElement` emits `<key>` into the measure it opens — the measure that the preceding barline started.
  

3. ~~Rewrite `MeasureBuilder.effectiveKeyFifths(Song, Line)`.~~ **Deleted rather than rewritten.** Its song-default fallback went with the song-wide key in Phase 4, which left it a one-line wrapper over `line.getRunningKey()`; `ScoreState` now holds a `Key`, so the line-boundary emission compares `line.getRunningKey()` against `state.runningKey` directly and nothing encodes fifths until the `<key>` is built. `startingKeyFifths` went the same way, in favour of `song.getStartingKey()`.
  
4. Add mid-line emission to `MeasureBuilder.buildLine`'s element loop. `KEY_SIGNATURE` is neither a barline nor a note, so it currently falls into the `appendElementContent` branch. Give it its own branch that emits `<key>` into the current measure's `<attributes>` and advances the running key. The invariant from Phase 2 — a `KeySignatureElement` is always immediately preceded by a barline — means the current measure was just opened by that barline, so the `<attributes>` lands at the head of the measure where it belongs. ~~Assert that invariant rather than assuming it, and fail the save with `DocumentValidation.corrupt` if it does not hold.~~ **Not done, and not to be done — see the note at the top of this phase.** The write side reads the invariant; task 7a's read-side check is the one that enforces it.
  
5. Emit `<cancel>` by **asking the policy**, never by restating it. `docs/musicxml-4.0-schema/attributes.mod:81-93` defines it: the cancel value is the signed-fifths value of the **cancelled** key, and it carries an optional location attribute.
  

- The condition is `KeyChange.accidentals(previous, next)` containing naturals — `songscribe.dom.KeyChange`, Phase 2. Do **not** write "when the key type differs" here. That is the policy hand-copied into a second subsystem, and when the policy changes one of the two copies keeps the old answer while both subsystems' tests go on passing in isolation.
  
- The cancelled key is `previous`, and its signed fifths come from `KeySignatureMapping.toFifths` (task 10), not from a second computation.
  
6. **Ignore incoming** `<cancel>` **on read.** This is a settled decision: cancellation is derived from the key change itself, so a file from another application normalizes to this application's rendering policy rather than importing someone else's. Each application is free to implement its own cancellation policy. State this in `MeasureMapper`'s contract so a later reader does not "fix" it.
  
7. Rewrite `MeasureMapper.applyFifths` (currently at the `songDefaultKeySet` branch). The measure-1 special case and the `songDefaultKeySet` flag both disappear: every `<key>` is uniform. The rule becomes —
  

- `<key>` in a measure that **starts a line** (the measure carrying `<print new-system="yes"/>`, which `MeasureBuilder` writes on every line-starting measure) sets that line's `Key` via `Line.setKey`.
  
- `<key>` in any other measure appends a `KeySignatureElement` at the current position in the current line.
  
- Delete `applyFifthsToLine` or reduce it to `line.setKey(...)`.
  

7a. **Assert `KeySignatureElement`'s class invariant on read, not only on write.** Task 4 fails the *save* with `DocumentValidation.corrupt` when a mid-line key signature is not immediately preceded by a barline or repeat. The read path currently has no equivalent, and it is the one that takes input from a file: a `<key>` in a measure the reader did not open with a barline element produces a `KeySignatureElement` that Phase 2's class invariant, Phase 9's deletion pairing and Phase 12c's index predicate all assume cannot exist. **There is no test, no error handling, and no visible symptom** — the model is simply invariant-violating until a later delete behaves wrongly. Before appending, assert the preceding element satisfies `isBarLine() || isRepeat()` and call `DocumentValidation.corrupt` when it does not. Test both sides: a well-formed mid-measure `<key>` loads, and one whose measure lacks its opening barline fails as corrupt.

8. **Fix the live corruption bug this replaces.** `applyFifths` currently calls `applyFifthsToLine(currentLine, fifths)` for _any_ non-first `<key>`, which sets the whole line's key and retroactively re-spells every note earlier in that line. Any MusicXML file with a mid-system key change loads wrong today, silently. Add a round-trip test that would have caught it: a four-measure line with a key change in measure 3, asserting the notes in measures 1–2 keep their original spelling.
  
9. Bound `KeySignatureMapping.accidentalCount(int fifths)`. It is `Math.abs(fifths)` with no range check, so `<fifths>12</fifths>` produced `keys = 12` and then an `ArrayIndexOutOfBoundsException` in the old `Line.keyExists`, thrown per note during pitch resolution. **Fail with a corrupt-document error** rather than clamping: call `DocumentValidation.corrupt` (`src/main/java/songscribe/io/DocumentValidation.java`) when `|fifths| > Key.MAX_ACCIDENTAL_COUNT`. Contract the valid range in `@param` and the failure in `@throws`.
  
10. Have `KeySignatureMapping` map to and from `Key` rather than `(KeyType, int)`: `toFifths(Key)` and `toKey(int fifths)`, replacing `toFifths(KeyType, int)` / `keyType(int)` / `accidentalCount(int)`.

- **`toKey(int)` already exists** — Phase 3 added it because `keyType(0)` returns `FLATS`, which with a count of 0 is exactly what `Key`'s constructor rejects, so the reader could not build a key at all without it. `keyType` and `accidentalCount` survive only for `applyFifths`'s song-default branch, which task 7 removes; this task deletes them with it and adds `toFifths(Key)`.
  
11. **Write** `<mode>major</mode>`**, and ignore it on read.** `<key>`'s child order is `cancel?, fifths, mode?` (`docs/musicxml-4.0-schema/musicxml.xsd:6351-6355`), so `<mode>` is optional and follows `<fifths>`.
  

- Write it on every `<key>`, always the literal `major`. It costs nothing and it is what a foreign consumer of our output reads — and output is for everyone (`docs/musicxml-object-model.md`).
  
- The reader does not read it. Only SongScribe-authored files get past the provenance gate, so `<mode>` in an input is always the `major` this writer emitted; parsing it would be code for a case that cannot occur. State that in `MeasureMapper`'s contract, with the reason, so a later reader does not add the parse.
  

12. Extend the round-trip tests from the contracts above:
  

- A mid-line key change survives a write/read round trip at the same element index.
  
- A line-boundary key change survives, and writes into the following line's first measure — assert the measure placement, not just the resulting model.
  
- No `<key>` is written for the cautionary at the end of the previous line.
  
- `<cancel>` is present exactly when `KeyChange.accidentals` yields naturals — drive the case table from `Key.allSignatures()` pairs rather than hand-picking a type change and a same-type change, so the writer and the policy cannot drift apart silently.
  
- A mid-measure `<key>` whose measure is not opened by a barline fails as corrupt; a well-formed one loads (task 7a).
  
- An incoming `<cancel>` that contradicts the policy is ignored and the rendering follows the policy.
  
- `<fifths>` of `8` and `-8` fail as corrupt; `7` and `-7` succeed — the boundary on both sides.
  
- Drive the fifths round-trip from `Key.allSignatures()` so all 15 keys are covered rather than sampled.
  
- Every written `<key>` carries `<mode>major</mode>`, in that position — assert it on the marshalled document, since nothing on the read side would notice its absence.
  

13. Run `./scripts/compile.sh`, then `./scripts/test.sh MusicXmlKeyRoundTripTest` and `./scripts/test.sh MusicXmlDocumentRoundTripTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 7a: MIDI Key Signature Export
**Status:** Done  
**BlockedBy:** 7 (done)  
**Files:** src/main/java/songscribe/midi/MidiEventFactory.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/io/musicxml/KeySignatureMapping.java, src/test/java/songscribe/midi/MidiEventFactoryTest.java  
**Recommended model/effort:** Sonnet, medium — a self-contained meta-event emission against a fifths mapping Phase 7 already built; the one subtlety is the repeat-replay case in task 2.

> **What this phase actually touched, and why the `Files:` list above is longer than planned.**
> `KeySignatureMapping.toFifths` (task 1's reuse target) and its class were package-private,
> visible only inside `songscribe.io.musicxml`. Reusing it from `songscribe.midi` — rather than
> recomputing the sign convention a second time, which task 1 explicitly rules out — required
> widening both to `public`. `toKey` stays package-private; only the MusicXML reader decodes
> fifths.
>
> **The emission logic lives entirely in `LineTrackBuilder`'s per-element loop**, not in
> `MidiSequenceBuilder`. At `i == 0`, `line.getKey()` (the line's own key, non-null exactly where
> a key change or line 0 establishes one) is emitted; at any element whose type is
> `KEY_SIGNATURE`, its `Key` is emitted. Because that loop is the same one both the linear and
> the repeat path funnel through — `MidiSequenceBuilder.buildSequenceWithRepeats` calls
> `addToTrack` once per element, including on replay — a mid-line key change inside a repeated
> span emits its event on every replay for free, with no separate repeat-aware branch.

### Tasks
1. **Emit a MIDI key signature meta-event.** Exported MIDI currently carries no key signature at all: `MidiMetaMessageTypes.KEY_SIGNATURE = 0x59` is declared in `src/main/java/songscribe/ui/playback/MidiMetaMessageTypes.java` and referenced by nothing, so a DAW opening a SongScribe MIDI file reads C major and notates everything with explicit accidentals. This is a pre-existing defect, fixed here because Phase 7 already computes the fifths for every key change.
  

- Add `addKeySignatureEvent(Track track, int ticks, Key key)` to `songscribe.midi.MidiEventFactory`, alongside the existing `addTempoEvent`. Write its contract before implementing it. `FF 59` carries two data bytes: `sf`, the signed fifths in `-7..7`, and `mi`, `0` for major and `1` for minor.
  
- `mi` is the constant `0`. Every SongScribe key is major and no minor key can enter the model, so the byte is named by a constant with that reason on it, not derived from a field the model does not have.
  
- Reuse `KeySignatureMapping.toFifths(Key)` (Phase 7 task 10) rather than recomputing the fifths — that mapping is already the one MusicXML export uses, and two copies would drift.
  
- Emit one event at tick 0 for the first line's running key, and one at the tick of every key change thereafter. `songscribe.midi.LineTrackBuilder` walks each line's elements with a running tick and is where a mid-line `KeySignatureElement`'s tick is known; the line-boundary case emits at the first tick of the line whose `getKey()` is non-null.
  
- `MidiSequenceBuilder.buildSequenceWithRepeats` replays lines out of order for repeats. A key signature event must be emitted wherever its line is replayed, not once per line in document order, or a repeated passage plays under the wrong key event. Verify which track the events belong on — tempo events establish the existing convention.
  

2. Test the MIDI emission in `MidiEventFactoryTest`, derived from the contract in task 1:
  

- `addKeySignatureEvent` writes a `FF 59` meta-event with the expected `sf` byte, driven from `Key.allSignatures()` so all 15 signatures are covered rather than sampled.
  
- `mi` is 0 on every emitted event, over the same table of signatures.
  
- The `sf` byte is negative for flats and positive for sharps — the sign convention, which is the thing a transcription error would invert.
  
- A song with a mid-line key change emits an event at that change's tick, not only at tick 0.
  
- **A repeated passage carrying a key change emits the event on each replay.** Task 1 names this failure precisely — `MidiSequenceBuilder.buildSequenceWithRepeats` replays lines out of order, and emitting once per line in document order makes the repeat play under the wrong key event. It is silent in SongScribe and audible only in a DAW, so nothing else will catch it. Build a song with a repeat spanning a key change and assert the event count and ticks across the replayed span.
  

3. Run `./scripts/compile.sh`, then `./scripts/test.sh MidiEventFactoryTest`. Must report SUCCESS / green.

* * *
## ✅ Phase 8: Legacy .mssw Read
**Status:** Done  
**BlockedBy:** 3 (done)  
**Files:** src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/SongIO.java, src/test/java/songscribe/io/LineIOTest.java  
**Recommended model/effort:** Sonnet, medium — a mechanical translation into an already-decided model, with one validation boundary to add.
### Tasks
1. `.mssw` is **legacy read-only**, for migrating old files. Never add new persisted fields to it. This phase changes how existing tags are interpreted and may delete write-side code, but adds nothing to the format.
  
2. The `.mssw` format stores a song-level default key plus per-line `keys` / `keytype` tags, written only when a line differs from that default (`LineIO.writeLine`, `LineIO.java:74-86`). Translate on read:
  

- Line 0 takes the file's song-level default key.
  
- A line with `keys` / `keytype` tags gets that `Key`.
  
- A line with neither inherits — a null `Key`.
  
- Normalization — any line whose resulting key equals the key it would inherit becomes null — is **`Line.setKey`'s job**, not this reader's (Phase 3 task 3). Do not repeat the rule here; call `setKey` and let it hold. Note in `LineIO`'s contract that the reader relies on it, so a reader of this file knows where the collapse happens.
  

3. `.mssw` has no representation for a mid-line key change, so none is produced on read. State that in `LineIO`'s contract so a reader does not go looking for one.
  
4. Validate the `keys` value. `LineIO.java:566` parses it with `DocumentValidation.parseIntOrThrow`, which catches non-numeric text but not an out-of-range count. A corrupt value of 12 previously reached the model unclamped. **Fail with a corrupt-document error**: reject anything outside `0..Key.MAX_ACCIDENTAL_COUNT` via `DocumentValidation.corrupt`. Likewise reject a `keys`/`keytype` combination that violates `Key`'s biconditional invariant (a non-zero count with `NONE`, or a zero count with `SHARPS`/`FLATS`).
  
5. If `SongIO` writes the song-level default key, remove that write path — `Song` no longer has one after Phase 4. Keep the read path, since old files still carry the tag.
  
6. Extend `LineIOTest`. Write the test class's testing-approach Javadoc and triage its existing key tests (`EndElement11Keys`, `EndElement11Keytype`) against the new contract **as one task**. Add:
  

- A line whose stored key matches what it would inherit reads back as a null key.
  
- A line whose stored key differs reads back as that key.
  
- A line with no key tags inherits.
  
- `keys` of `8` and `-1` fail as corrupt; `0` and `7` succeed — the boundary on both sides.
  
- **Both directions of the biconditional**, since task 4 rejects both: a `keytype` of `NONE` with a non-zero `keys` fails as corrupt, **and** a `keytype` of `SHARPS` or `FLATS` with a `keys` of `0` fails as corrupt. Testing one direction of an "if and only if" leaves half the clause unchecked.
  

7. Run `./scripts/compile.sh` and `./scripts/test.sh LineIOTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 9: Barline Pairing and Selection Expansion
**Status:** Done  
**BlockedBy:** 3 (done), 5 (done), 11, 12a  
**Files:** src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/ElementType.java, src/main/java/songscribe/ui/selection/Selection.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/component/score/LineRenderer.java, src/test/java/songscribe/dom/LineQueryTest.java, src/test/java/songscribe/dom/ElementTypeTest.java  
**Recommended model/effort:** Opus, medium — every decision this phase needs is now written down, including task 6a's, so what is left is the selection-expansion routing: `Selection.Range.contains` is on a sealed interface and is asked per element index during painting, and widening it is the one step whose shape is not dictated below. **Do not split this phase** — the confirm of task 6 would be Sonnet work, but it writes `ScoreViewController.java`, which the dashboard already serializes across 9, 10b, 11, 12c and 12d, so a split buys nothing.
### Tasks
1. A `KeySignatureElement` (`songscribe.dom.KeySignatureElement`, `ElementType.KEY_SIGNATURE`) is always immediately preceded by a barline or repeat element, and can never sit at index 0 of a line. The invariant holds because Phase 12c's insert flow adds a `SINGLE_BARLINE` when the chosen position lacks one; nothing here has to police it, only preserve it under deletion. **Deleting the barline must delete the key signature with it**, and selecting either must show both as selected so the user sees what will go.
  
2. This also makes index 0 unreachable by deletion: reaching index 0 requires deleting the barline in front, which now takes the key signature too. No separate index-0 check is needed. State that reasoning in the pairing method's contract so a later reader does not add a redundant guard.
  
3. Extend `Line`'s pairing methods. Both are currently one-directional and the key-signature pair is the first bidirectional one:
  

- `effectiveDeleteEnd(int end)` today extends forward over a trailing breath mark. Add: extend forward over a `KEY_SIGNATURE` element immediately after `end` when the element at `end` is a barline or repeat.
  
- `effectiveDeleteBegin(int begin)` today extends backward over a paired grace note. Add: extend backward over a barline or repeat immediately before `begin` when the element at `begin` is a `KEY_SIGNATURE`.
  
- The backward direction is a **settled decision, not a symmetry the model forces.** A key signature cannot outlive its barline, but a barline can outlive its key: deleting only the key would leave a valid line. The pair is deleted whole anyway so that a barline the insert flow added to host a key does not linger after the key is gone. The cost is the other case — a barline the user placed themselves is removed along with the key that happened to follow it, merging two measures — and the confirm in task 6 is what makes that visible before it happens. State this reasoning in the contract; without it a later reader will read the backward case as redundant and delete it.
  
- Update both contracts. Each currently names exactly one pairing; they now name two, and the class Javadoc should state the general rule (some elements cannot outlive their partner, and a deletion range widens to keep pairs whole) rather than each method restating it.
  

4. **Rename the three methods to `effectiveRange`, `effectiveBegin` and `effectiveEnd`, and the `EffectiveRange` record to `EffectiveRange`.** Once selection rendering uses them (task 5) they are no longer delete-specific — `effectiveEnd`'s own doc already says "deletion or copy range", and `Fragment.capture` calls it on the copy path. The name is settled rather than left to the implementer: `effective` is already this class's prefix for "what this actually resolves to" (`Line.effectiveElementCount()`), so the three methods join a naming pattern instead of inventing one. Apply it with `jet_brains_rename` so call sites update mechanically. Changing an existing contract is a visible decision: state the rename and its reason in the commit message.
  
5. Make selection rendering resolve through that range **in both directions**. The forward half is already routed — `Selection.Range.contains` (`Selection.java:138`) calls `effectiveDeleteEnd(end)`, so a trailing breath mark does read as selected. **The backward half is not**: nothing asks `effectiveBegin`, so today a preceding paired grace note is deleted without ever having been highlighted, and the barline this phase pairs backward from a key signature would go the same way. Widen `Selection.Range.contains` to test the full range rather than only its end.
  

- The two `ScoreViewController` callers (`deleteElementRange`, around line 1139, and `confirmDeletionRestatements`, around line 1096) already ask for the whole range and need no change.
  
- `Selection.Range.contains`'s Javadoc explains the forward case and carries a `refs #698`. Rewrite it to state the general rule and remove the issue reference (see **Conventions**).
  

- The selection model is `songscribe.ui.selection.Selection`, a sealed interface with `Range` and `Target` variants. Expand at the point where a `Selection.Range` becomes highlighted columns, not by mutating the stored selection — the user's selection is what they chose; the highlight shows what an operation would reach.
  

6. **Confirm before deleting either half of the pair.** The alternatives were the breath-mark precedent (silent, `effectiveEnd`) and the Ending precedent (`Line.hasEndingInvalidatedByDeletion`, which exists so the user is warned before a delete destroys an Ending). A key signature is closer to the Ending in weight — losing one silently re-spells every note after it — and the backward pairing can take a barline the user placed, which changes the measure structure. Follow `hasEndingInvalidatedByDeletion`'s shape.
  

- The prompt must name **both** elements, not just the one the user selected, and must distinguish the two directions: deleting a barline also removes the key signature after it; deleting a key signature also removes the barline before it. A confirm that says only "this will delete the key signature" leaves the second case as the surprise it was meant to prevent.
  
- Read `.claude/guides/option-dialogs.md` before adding the prompt and `.agents/guides/strings.md` before adding the message strings.
  

6a. **`ElementType.isNonContentElement()` returns `true` for `KEY_SIGNATURE`.** Phase 2 left it at the default of `false`, and `false` is wrong: it makes an ending immediately after a mid-line key signature fail validation for no reason the user can see. Change the method to `isGraceNote() || this == BREATH_MARK || this == KEY_SIGNATURE` and state the reasoning below on it. This is settled — implement it, do not re-derive it.
  

The four readers in `MusicEditOperations`, and what each does with a key signature:

- **`checkPrecedingElement` via `indexOfPrecedingContentElement` (`:1000`) is the one that decides it.** With `false`, a `KEY_SIGNATURE` immediately before the selection becomes `precedingType`, matches neither `isDuration()` nor the barline/left-repeat branch, and falls through to the catch-all that returns `EndingValidationResult.invalid()` — so a first-second ending starting right after a mid-line key change is silently refused. With `true`, the walk skips it and reaches the barline that Phase 2's class invariant guarantees is behind it, which yields `EXTEND_SPAN` onto that barline: the bracket anchors exactly where it would have if the key signature were not there. **A key signature must not move where an ending anchors**, and transparency is what delivers that.
  
- **`validateEndingStructure` (`:850`) counts it toward `MIN_CONTENT_ELEMENTS`** when it is content. A key signature is not musical content and must not help a selection clear the minimum; skipping it is correct.
  
- **`validateEndingRegionContent` (`:899`) is unaffected either way** — a `KEY_SIGNATURE` is neither a barline, a repeat, nor a duration, so it already falls through the loop body without effect. Note this in the commit message so a reviewer who checks all four sites is not left wondering why one shows no behavior change.
  
- **`hasEnclosingRepeat` never asks**, skipping every type it does not name.
  

The catch-all comment at `MusicEditOperations.java:1037` enumerates "the only element types left" as the right repeat and the two double barlines, and says the branch is unreachable today. That stays true with `KEY_SIGNATURE` non-content and would become false with it content. Leave the comment alone, and do not add a `KEY_SIGNATURE` branch to `checkPrecedingElement` — the whole point is that the walk never arrives there with one.

Add `KEY_SIGNATURE` to `ElementTypeTest`'s `isNonContentElement` membership table, and note that `testIsDurationAndIsNonContentElementAreNeverBothTrue` already covers it once it is in the table.

6b. **Ending validation reads the user's raw selection, not the widened range, and this phase must keep it that way.** Task 5 expands the highlight where a `Selection.Range` becomes highlighted columns rather than by mutating the stored selection, so `MusicEditOperations` still sees what the user chose. That matters here: `validateEndingRegionContent` rejects any barline inside an ending region, so feeding it a range widened backward onto a barline (task 3) would refuse endings that are legal today. If the expansion is implemented anywhere that `MusicEditOperations.getRange()` can observe, that is the defect.

7. Test the pairing from the contracts in task 3:
  

- Deleting a range ending at a barline followed by a key signature widens to include the key signature.
  
- Deleting a range beginning at a key signature widens to include the preceding barline.
  
- Deleting a range covering only the key signature widens backward — the bidirectional case neither existing pairing exercises.
  
- A barline **not** followed by a key signature does not widen.
  
- A key signature at the end of a line (nothing after it) widens backward only.
  
- No deletion can leave a `KEY_SIGNATURE` at index 0 — assert the invariant over a set of ranges rather than pinning one expected output.
  
- The confirm names both elements in each direction — assert the message key chosen for a barline-initiated delete differs from the one for a key-initiated delete, since a single shared message is exactly the regression task 6 guards against.
  
- An ending anchored immediately after a mid-line key signature validates, and anchors on the barline in front of it rather than on the key signature — the case task 6a exists for, and the one that fails today. It belongs in the ending-validation tests, not in `LineQueryTest`; find them from `MusicEditOperations.validateEndingStructure` with `jet_brains_find_referencing_symbols`.
  

**Where these cases go:** the existing range cases are `LineQueryTest`'s nested `EffectiveDeleteEnd` class — there is no `LineDeleteRangeTest`. Add the forward cases there, rename the nested class with the methods (task 4), and add a sibling nested class for the backward direction, which has no cases today.

8. Run `./scripts/compile.sh`, then `./scripts/test.sh LineQueryTest` and `./scripts/test.sh ElementTypeTest`, plus the ending-validation suite found in task 7. All must report SUCCESS / green.
  

* * *
## ✅ Phase 10a: Restatement Scan Hoist
**Status:** Done  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/layout/AccidentalReconciliation.java, src/main/java/songscribe/ui/edit/AccidentalRestatements.java, src/test/java/songscribe/ui/edit/AccidentalRestatementsTest.java  
**Recommended model/effort:** Sonnet, medium — one signature change and one loop inversion, both stated below; the algorithm and the resulting complexity are decided, not open. It changes no behavior and carries no key-signature dependency, so it can run now, before Phase 10b needs it.

> Split out of Phase 10 so the Opus context there is spent on the range widening rather than on a
> refactor whose shape is settled. Phase 10b is blocked on this one because both write
> `AccidentalRestatements.java`.

### Tasks
1. Read `.claude/guides/contracts.md` first.

2. **Hoist the restatement scan out of its per-removal loop.** `AccidentalRestatements.confirm` loops over every removed accidental calling `AccidentalReconciliation.findRestatements(song, line, …)`, and **each of those scans forward through the whole song**. That is affordable today for the reason its own contract gives — *"a removal is always something one selection or one click does"* — the premise Phase 10b deletes. A key change on line 0 of the common song (only line 0 keyed) produces removals across every line, making the loop O(removals × song length), run synchronously on the EDT before the confirm dialog appears.
  

- The scan is keyed by **staff position**, and every removal's staff position is known before the loop starts. Change `findRestatements` to take the set of `(staffPosition, accidental)` pairs and make **one** forward pass resolving all of them; `confirm`'s loop collapses to collecting the set. O(removals × song) becomes O(song), with the same result.
  
- Update `findRestatements`' contract for the set-taking signature, and keep the existing `excluded` semantics unchanged — the edited elements are still excluded whether or not they lose anything.
  

3. **This changes no behavior**, so the existing suites must pass unedited. Run the existing `AccidentalReconciliation`, `AccidentalRestatements`, clipboard, paste and delete tests before and after and confirm the same set passes. If one needs editing to stay green, the hoist changed behavior and the change is the defect, not the test. Add no new cases for the same result reached by a faster route; the one case worth adding is that a removal set spanning several staff positions still resolves each of them, which the collapsed loop is what could break.
  
4. Run `./scripts/compile.sh`, then the suites named in task 3. All must report SUCCESS / green.
  

* * *
## ✅ Phase 10b: Accidental Propagation Across a Key Change
**Status:** Done  
**BlockedBy:** 3, 9, 10a  
**Files:** src/main/java/songscribe/layout/AccidentalReconciliation.java, src/main/java/songscribe/ui/edit/AccidentalRestatements.java, src/main/java/songscribe/ui/component/ScoreViewController.java, docs/clipboard.md, src/test/java/songscribe/layout/AccidentalReconciliationTest.java, src/test/java/songscribe/ui/edit/AccidentalRestatementsTest.java  
**Recommended model/effort:** Opus, high — widens two contracts that both state a single-line assumption in prose; the range is the first edit in the program that spans lines.
### Tasks
1. Read `docs/mutations.md`, `.claude/guides/contracts.md`, and `.claude/guides/option-dialogs.md` first.
  
2. A key change moves pitches, so it owes the same accidental propagation an added barline owes. Two edits trigger it:
  

- Setting, changing or clearing a line's own `Key` (the header and cautionary edits).
  
- Inserting, editing or removing a mid-line `KeySignatureElement`.
  

Both preserve the pitch the user did not change — the same promise `AccidentalReconciliation.reconcile(InsertionRegion)` already keeps when a barline is inserted. Notes whose sounding pitch would move get an explicit accidental; notation the change makes redundant is cleared.

3. **The scope is the whole inheritance chain, and this is the settled decision.** Reconciliation runs from the change point forward through every line that inherits the changed key, stopping at the first line with its own non-null `Key` — the same stopping rule as Phase 3's `inheritedKey` propagation, because it is the same reach. A key change on line 3 moves pitches on lines 4 and 5 when they inherit, and stopping at line 3 would let those notes change pitch with no prompt, which is the exact failure this machinery exists to prevent.
  
4. Widen the two per-line contracts. **Both currently state the single-line assumption in prose, so both statements are now false and must be rewritten, not merely extended:**
  

- `AccidentalRestatements.confirm(Component, Line, List<EditedNote>)` says _"an edit whose removals span two lines does not exist, because a removal is always something one selection or one click does."_ A key change is that edit. It takes a line range, and the restatement prompt it raises is **one dialog covering the whole range**, not one per line — a prompt per line for a single click is the worse failure of the two.
  
- `AccidentalReconciliation.reconcileModification(Line, List<IntendedChange>)` and its `RestatementRemoval` overload are per-line. Give them a range form. The `RestatementRemoval` overload's existing note — that it is _"also the entry point for a line the edit does not otherwise touch"_ — is the seam the range form generalizes; read it before changing anything, because it already anticipates this shape.
  
- Prefer widening the existing methods over adding parallel range-taking siblings. Two entry points for one operation drift, and the per-line call becomes a range of one.
  

5. Do **not** give the key change its own copy of the reconciliation walk. `resolveOverProjection` already asks for the key at a projected index after Phase 3 task 9, so the resolver needs no key-specific branch; what this phase adds is the range and the trigger, not a second algorithm.
  
5a. ~~Hoist the restatement scan out of its per-removal loop.~~ **Moved to Phase 10a**, which is why this phase is blocked on it. The premise this phase deletes — *"a removal is always something one selection or one click does"* — is what made the per-removal full-song scan affordable, so the hoist has to be in place before the range widening lands. Do not re-derive it here; if `findRestatements` still takes a single `(staffPosition, accidental)` pair when this phase starts, Phase 10a did not run and this phase is not ready.

5b. **Update `docs/clipboard.md` §6** (`docs/clipboard.md:437`). It restates `AccidentalReconciliation`'s contract, including that the key signature never appears in the algorithm directly and that both bounds stay within the line. The second is now false: the reach is the inheritance chain. Fix it in this phase — the phase that invalidates a doc section updates it (see **Conventions**).
  
6. Wire the trigger in `ScoreViewController` alongside the existing `confirmDeletionRestatements` call, so a key edit reaches confirmation by the same route every other pitch-moving edit does. Phase 12d task 3 redirects the dialog's commit through this path rather than letting it mutate the model directly.
  
7. Order the confirm against Phase 12d's fit rejection: **fit is checked first, restatements second.** A change that is going to be refused for not fitting must not first ask the user about accidentals it will never apply.
  
8. Test the range promise, which is the whole of what this phase adds:
  

- A key change on a line with two inheriting lines after it produces accidental changes on all three; the same change with line N+1 holding its own key produces them on N only. That pair is the stopping rule.
  
- A note two lines downstream keeps its sounding pitch across a key change — the invariant, asserted on pitch rather than on a table of expected accidentals.
  
- One confirm dialog is raised for a multi-line key change, not one per line.
  
- A mid-line `KeySignatureElement` insert reconciles from its index forward, not from the start of its line.
  
- A key change that alters no pitch class any note uses produces no changes and raises no dialog — the common case must not be made to pay for this.
  

9. **The existing suites must pass unedited.** This phase widens the two most-called contracts in the program — every insert, delete, paste and in-place modification goes through them (`docs/clipboard.md` §6) — and task 8's cases all test the *new* range promise. Nothing there would notice an off-by-one in "a per-line call becomes a range of one." Apply Phase 11 task 6's discipline verbatim: run the existing `AccidentalReconciliation` tests, the clipboard and paste tests, and the delete tests before and after, and confirm the same set passes. **If one of them needs editing to stay green, the widening changed behavior and the change is the defect, not the test.**
  
10. Run `./scripts/compile.sh`, then `./scripts/test.sh AccidentalReconciliationTest` and `./scripts/test.sh AccidentalRestatementsTest` — both exist, at `src/test/java/songscribe/layout/` and `src/test/java/songscribe/ui/edit/` — plus the suites named in task 9. All must report SUCCESS / green.
  

* * *
## ✅ Phase 11: Operation-Independent Insertion Point
**Status:** Done  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/edit/InsertionPointMode.java, src/main/java/songscribe/ui/edit/PasteModeManager.java, src/main/java/songscribe/ui/component/score/InsertionMarkerOverlay.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/component/ScoreInputHandler.java, src/main/java/songscribe/ui/component/ScoreViewController.java, docs/clipboard.md, src/test/java/songscribe/ui/edit/InsertionPointModeTest.java, src/test/java/songscribe/ui/edit/PasteModeManagerTest.java  
**Recommended model/effort:** Opus, high — extracting a mode from the one operation that has always owned it, without changing paste's behavior by a pixel.
### Tasks
1. #53 requires that adding a mid-line key signature use "the same mechanism that is used to select a paste insertion point," and says to "refactor the paste insertion point mechanism to be independent of pasting, if it is not already." That refactor is this phase, kept separate because it changes no key-signature code and can proceed alongside Phases 1–9.
  
2. Read `docs/messages.md` and `.claude/guides/contracts.md` first.
  
3. `songscribe.ui.edit.PasteModeManager` is today both things at once: the interaction that lets the user pick an index on a line (mouse tracking, the Return/Enter path, the marker overlay, dropping the point when the mouse leaves the line) and the paste that consumes it. Separate them. The interaction becomes a reusable mode; paste becomes one client of it.
  

- **The reusable half is `songscribe.ui.edit.InsertionPointMode`.** Naming it here is not cosmetic: Phase 12c task 2 is written against it from a separate context, and an unnamed class is one each phase invents differently.
  
- It owns: the tracked `(Line, index)` pair, the mouse and keyboard handling that moves it, `InsertionMarkerOverlay`, cancellation, and a **per-client predicate for which indices may be tracked**. It reports the chosen index to its client and exits.
  
- `PasteModeManager` remains, as the paste client. It owns: what a valid paste index is, the clipboard fragment, and the insertion itself.
  
- **The static `isActive()` moves to `InsertionPointMode`.** `UIAction.enableFromPasteMode()` returns `!PasteModeManager.isActive()` and is called first in `updateEnabledState()`'s predicate chain, so it is what disables every action while a placement is pending — and that must hold for a key-signature placement exactly as it does for a paste. Leaving it on `PasteModeManager` would leave every action live while the user is picking a key-signature position. Rename the `UIAction` hook to match what it now gates.
  
- Write `InsertionPointMode`'s contract before moving any code. State what a client is entitled to: it will be called back with an index its own predicate accepted, or told the user cancelled, **exactly once** — never both, never twice, never neither.
  

4. The index predicate is the seam that makes this phase worth doing. Paste's current rule stays exactly as it is; the key-signature client (Phase 12c) supplies a different one. Do not fold either rule into the mode.
  
5. `LineComponent` (`:678`, `:741`) and `ScoreInputHandler` (`:411`) dispatch to paste mode by name. Route them through the mode instead, so a second client needs no further edits to either file. `jet_brains_find_referencing_symbols` on `PasteModeManager`'s members gives the full set — work from that rather than from the three line numbers above.
  
6. Paste behavior must be unchanged. There is no new promise here and no old one withdrawn; this is a seam being cut. Run the existing paste tests before and after and confirm the same set passes — if a paste test needs editing to stay green, the refactor changed behavior and the change is the defect, not the test.
  

- One exception is expected and is not a behavior change: `PasteModeManagerTest.lineStub` stubs `line.getKeyAccidentalCount()`, which Phase 3 turns into a deprecated delegate. Retarget the stub; do not delete the test.
  

7. **Write `InsertionPointModeTest` for the mode's own contract**, which the paste tests do not reach — they exercise the client, not the promise task 3 states. The callback fires **exactly once**: once with an accepted index on placement, once with a cancellation on Escape / click-outside / backgrounding, and never both. Cover at least the placement path and one cancellation path, and assert the count, not merely that it fired.
  
8. **Update `docs/clipboard.md` §5 and §7.** §5 (`docs/clipboard.md:339`) carries the paste-mode state diagram and the `exit()`-funnel and mouse-tracking prose, all of which attribute to `PasteModeManager` what this phase splits in two — redraw the diagram against the two classes and say which owns each transition. §7's "#53 (mid-line key changes)" entry (`docs/clipboard.md:618`) says *"nothing in this architecture implements it"* and names the one-key-per-line assumption in `HorizontalSpacingCalculator.isWithinHeaderXSs` as an obstacle; both are false once this work lands. Rewrite the entry as a pointer to `docs/key-signatures.md` (Phase 13) and drop the issue number (see **Conventions**).
  
9. Run `./scripts/compile.sh`, then `./scripts/test.sh InsertionPointModeTest` and the existing paste and score-input tests (find them with `jet_brains_find_referencing_symbols` on `PasteModeManager`). All must report SUCCESS / green.
  

* * *
## ✅ Phase 12a: Key Signature Dialog
**Status:** Done  
**BlockedBy:** 4, 16 (both done)  
**Files:** src/main/java/songscribe/ui/dialog/KeySignatureChangeDialog.java, src/main/java/songscribe/dom/ElementType.java, src/test/java/songscribe/ui/dialog/KeySignatureChangeDialogTest.java  
**Recommended model/effort:** Sonnet, high — the dialog's shape is fully specified below and every part it is built from already exists: `KeyCellRenderer` and `DialogOp` from Phase 16, the button row from `AttachmentDialog.modifyButtonPanel`, the entries from `Key.allSignatures()`. Nothing here is a decision; the judgment that produced this specification is already spent.

> Split out of Phase 12 so that the interaction work gets the Opus context on its own. That
> interaction work then split again, into three: **12b** (hit targets), **12c** (the index predicate
> and the implicit barline) and **12d** (the fit rejections and the commit route). This half has no
> unmet dependency and can run now, in parallel with Phases 9, 10a, 11 and 12b. Phase 12d is blocked
> on it because both write `KeySignatureChangeDialog.java`; Phase 9 is blocked on it because both
> write `ElementType.java`.

### Tasks
1. Read `.claude/guides/dialogs.md`, `.claude/guides/option-dialogs.md`, and `.agents/guides/strings.md` first.
  
2. **Give `KEY_SIGNATURE` a case in `ElementType.categoryName()`.** It currently falls through to the `IllegalStateException` at the end of the method, so Phase 12b's fit rejection — whose message is "There isn't enough room on this line for this {0}." — would throw instead of alerting, on the exact path that exists to tell the user something is too wide. Add the case and its string (read `.agents/guides/strings.md` first).
  
3. Give the dialog an input record — `record KeySignatureInput(Line line, int insertionIndex, DialogOp op)`. It carries **no incoming key**: see task 4.
  

- Do not describe `Key` as "a copy the dialog cannot edit in place." `Key` is an immutable record, so no implementation could violate that promise — it describes the code rather than promising anything, and a test derived from it can never fail. There is no such clause and no such test.
  
- `BaseDialog.DialogOp { ADD, EDIT, REMOVE }` and `KeyCellRenderer` come from **Phase 16**. Use them; do not create, rename or restyle either.
  
4. Rewrite `KeySignatureChangeDialog`. It currently reads `line.getKeyType()` and `line.getKeyAccidentalCount()` into a combo and a spinner (`KeySignatureChangeDialog.java:75-76`). It becomes a single combo over `Key.allSignatures()` plus an explicit "inherit" choice for lines other than line 0 — line 0 cannot inherit and must not offer it. **The combo uses** `KeyCellRenderer` from Phase 16, so a key is picked here exactly as it was picked in song settings: glyph plus display name, not a type combo and a count spinner. Do not write a second renderer, and do not restyle this one.
  

- **The dialog opens with no selection, and OK is disabled until the user picks something.** It does **not** pre-select the key already in effect. Pre-selecting it makes the default commit a no-op key change, and a no-op mid-line `KeySignatureElement` is invisible — nothing drawn changes — while still being a `cancelsAccidentals()` barrier that re-spells every note after it, and still dragging Phase 12b's auto-inserted barline into the score. A blank combo makes every commit a deliberate choice.
  
- The line's current key is not otherwise hidden from the user: it is drawn in the header, and Phase 3's `setKey` collapses a chosen key that equals the inherited one back to null, so picking "the same key" cannot pin a line by accident.
  
- A key type combo plus a separate count spinner can express the invalid states `Key` rejects (`NONE` with a count, `SHARPS` with zero). One combo over the 15 valid signatures cannot. That is why the control changes shape.
  
- **No mode control**, because the model has no mode. Every SongScribe key is major; see Phase 1.
  
- Buttons follow the attachment dialogs' algorithm — `AttachmentDialog.modifyButtonPanel` and `opLabel` are where that lives. Reuse it; do not hand-build a button row.
  

5. **Leave the commit path as Phase 4 left it** — `line.setKey` inside a modification bracket — and do not invent a second one. Phase 12b task 6 redirects that single call through `ScoreViewController` so the edit reaches accidental reconciliation (Phase 10b task 6) by the same route every other pitch-moving edit does. Redirecting one call is not a temporary duplicate; writing a reconciliation path here that Phase 12b would replace would be.
  
6. Fix the crash noted in #53: the dialog currently crashes when invoked. Verify the rewritten dialog opens from a fresh song, from a song with existing key changes, and in both `ADD` and `EDIT` ops.
  
7. Write the dialog's tests from the contracts above:
  

- Line 0's dialog offers no "inherit" choice; a later line's does.
  
- Choosing "inherit" on a line with a key sets its key to null.
  
- Choosing a key on an inheriting line sets it.
  
- The combo's entries are exactly `Key.allSignatures()` — assert against the list rather than a hand-written expectation, so a domain change reaches the test.
  
- The dialog opens with **nothing selected** and OK disabled, in both `ADD` and `EDIT`; picking any entry enables OK. That is the whole of task 4's opening promise, and both sides of it need a case.
  
- `ElementType.categoryName()` returns a name for `KEY_SIGNATURE` rather than throwing — the one case that proves task 2, and the failure it prevents is an exception on the alert path.
  

8. Run `./scripts/compile.sh` and `./scripts/test.sh KeySignatureChangeDialogTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 12b: Key Edit Hit Targets
**Status:** Done  
**BlockedBy:** 5 (done), 6 (done)  
**Files:** src/main/java/songscribe/layout/LayoutHitTester.java, docs/selection.md, src/test/java/songscribe/layout/LayoutHitTesterTest.java  
**Recommended model/effort:** Sonnet, medium — three rects whose geometry already exists: the header region from `HorizontalSpacingCalculator`, the cautionary from `KeySignatureRenderer` (Phase 5, including its overflow position), and a mid-line element's own bounds. The interaction model below is settled, so what is left is wiring rects to an existing hit tester and writing the doc section.

> **This is the one Phase 12 half that buys wall-clock.** It shares no file with any other pending
> phase — not `ScoreViewController.java`, not `KeySignatureChangeDialog.java` — so it runs
> immediately, in parallel with 8, 10a, 11, 12a, 13 and 14a. It makes the targets *hittable*;
> Phases 12c and 12d decide what happens when one is hit.

### Tasks
1. Read `.claude/guides/contracts.md` and `docs/selection.md` first.
  
2. **The interaction model is settled by #53. Do not re-derive it.** It is stated here because this phase creates the targets; Phases 12c and 12d cite it rather than restating it. There is no song-wide key; each line has or inherits a key. Three double-click targets edit a key:
  

- **A line's header key signature** edits **that line's** key. If the line was inheriting, setting a key gives it that key and a cautionary is then drawn automatically at the end of the previous line. Removing an existing key makes the line inherit again and the cautionary disappears.
  
- **The cautionary at the end of a line** edits the **next** line's key. It renders a change that lives on the following line, and clicking it edits that change where the user sees it.
  
- **The rect bounded by a mid-line key's rendered accidentals** edits that `KeySignatureElement`.
  

3. Every line's header is selectable, including line 0 and lines that merely restate an inherited key. Uniform selectability is deliberate: headers are visually identical whether or not they represent a change, so making only some clickable would vary selectability invisibly.
  
4. Update `LayoutHitTester` so all three rects in task 2 are hit targets. `HorizontalSpacingCalculator.isWithinHeaderXSs(double, Line)` already bounds the header region and now resolves through the line's running key. The cautionary's rect is the one `KeySignatureRenderer` draws — including the overflow-relative position Phase 5 gives it, so the target follows the glyphs rather than the margin.
  

- Contract each target by **what it resolves to**, not by where it sits: the header resolves to its own line, the cautionary to the **next** line, a mid-line rect to its `KeySignatureElement`. That is the promise Phases 12c and 12d are written against, and a hit test that returns a rect without saying what it identifies leaves them to re-derive it.
  

5. **Update `docs/selection.md`.** Line 117 describes the header as "the clef and key signature, and a press there selects the …" — this phase makes that header a double-click edit target, and adds two more (the cautionary, and a mid-line key signature's accidental rect). State all three and how they relate to the press-to-select behavior the section already documents. The phase that invalidates a doc section updates it (see **Conventions**), and it is this phase that creates all three targets.
  
6. Extend `LayoutHitTesterTest` for the three targets, derived from task 4's contract:
  

- A point inside a line's header resolves to that line; a point just outside it does not.
  
- A point inside the cautionary resolves to the **next** line, not the line it is drawn on — the one case where the target and its subject differ, and the one a reader would get backwards.
  
- On a line where `overflowsStaffWidth()` is true, the cautionary's target sits off the solved chain's end rather than at the margin. Phase 5 moved the glyphs; this asserts the target moved with them.
  
- A point inside a mid-line key signature's accidental rect resolves to that `KeySignatureElement`.
  

7. Run `./scripts/compile.sh` and `./scripts/test.sh LayoutHitTesterTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 12c: Key Signature Insertion Flow
**Status:** Done  
**BlockedBy:** 9, 11, 12a  
**Files:** src/main/java/songscribe/ui/action/KeySignatureChangeAction.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/test/java/songscribe/ui/action/KeySignatureChangeActionTest.java  
**Recommended model/effort:** Opus, medium — the predicate's three rejected indices are stated below, but the implicit barline and the key signature must enter as one modification so undo takes them back together, and that bracket interacts with Phase 9's pairing and Phase 11's freshly extracted mode. The rules are decided; how they compose through the mutation bracket is not.

> Blocked on **11** for the insertion-point mode it extracts, on **9** for the barline-plus-key-signature
> pair the predicate has to recognize, and on **12a** for the dialog this flow opens. It writes
> `ScoreViewController.java`, which is why **12d** follows it.

### Tasks
1. Read `.claude/guides/dialogs.md` and `docs/mutations.md` first. **The dialog itself is Phase 12a's** — do not restyle it, re-shape its combo or re-decide its opening state; this phase decides where the user may put a key signature. **The hit targets are Phase 12b's** — see its task 2 for the settled interaction model.
  
2. **Adding a mid-line key signature** uses Phase 11's insertion-point mode with its own index predicate. The predicate rejects:
  

- index 0,
  
- index `line.effectiveElementCount() - 1`,
  
- any index touching an existing mid-line key signature **or the barline or repeat that precedes it**. The two are one unit (Phase 9 deletes them together), so all three surrounding indices are rejected: immediately before the barline, between the barline and the key signature, and immediately after the key signature. Rejecting only the two that touch the key signature would still let the user insert a second barline-plus-key directly in front of the first.
  

Once the user picks an index, show the dialog. Contract the predicate as its own method so the three rules are testable without driving the UI.

3. **When the chosen index is not immediately after a barline or repeat, insert a** `SINGLE_BARLINE` **before the key signature.** This is what keeps the Phase 2 class invariant true without restricting where the user may click. Both elements go in as one modification so undo takes them back together — read `docs/mutations.md` for the bracket's obligations.
  
3a. ~~Give `KEY_SIGNATURE` a case in `ElementType.categoryName()`.~~ **Done in Phase 12a**, because Phase 12d's alert message goes through it and would throw instead of alerting. If `categoryName()` still throws for `KEY_SIGNATURE` when this phase starts, Phase 12a did not run and this phase is not ready.

4. Update `KeySignatureChangeAction`'s flags. #53 records that `Flag.DISABLE_WHEN_BAR_SELECTED` can be removed. Keep `DISABLE_WHEN_PLAYING`, `DISABLE_WHEN_EDITING_TEXT`, `DISABLE_IN_GRACE_MODE` and `OPENS_DIALOG`. The action no longer needs a position guard — invoking it enters the insertion-point mode, and the predicate of task 2 is what excludes the illegal indices.
  
5. Write tests for what this phase adds. **The dialog's own cases are Phase 12a's** and **the hit targets are Phase 12b's** — do not restate either.
  

- The index predicate rejects 0, `effectiveElementCount() - 1`, and all three indices around an existing barline-plus-key-signature pair, and accepts an ordinary interior index. Parameterize over a line whose indices span every case, including the index before the pair's barline — the one a predicate that only looks at the key signature would wrongly accept.
  
- Adding at an index not preceded by a barline produces two elements, barline then key signature, and one undo restores both.
  
- Adding at an index already preceded by a barline produces one element, not two — the other side of task 3, and the case that catches an unconditional barline insert.
  

6. Run `./scripts/compile.sh` and `./scripts/test.sh KeySignatureChangeActionTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 12d: Fit Rejection and Commit Routing
**Status:** Done  
**BlockedBy:** 6 (done), 10b, 12a, 12c  
**Files:** src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/dialog/KeySignatureChangeDialog.java, src/test/java/songscribe/ui/dialog/KeySignatureChangeDialogTest.java  
**Recommended model/effort:** Opus, high — two rejections and two user prompts whose ordering is a correctness question, on the one path where a refused edit could still reach the model. This is where the judgment in Phase 12 concentrates.

> Blocked on **10b** for the reconciliation route task 3 redirects into, on **12a** for the dialog it
> edits, and on **12c** for `ScoreViewController.java`.

### Tasks
1. Read `.claude/guides/option-dialogs.md` and `.agents/guides/strings.md` first. **The dialog's shape is Phase 12a's** — do not restyle it, re-shape its combo or re-decide its opening state; this phase decides when an edit is refused and by what route an accepted one commits.
  
2. **Two fit rejections, both from Phase 6's** `KeyEditFitCalculator`:
  

- Changing a line's key when that line did **not** previously have one creates a cautionary on the previous line, and — through inheritance — re-keys the header of every line that inherits from it. Call `lineKeyChangeFits(Line, Key, LyricRenderMetrics)` before accepting; if it fails, alert and reject the modification.
  
- Adding a mid-line key signature must fit on its line, and moves the key its line leaves off in. Call `keySignatureFits` before accepting; if it fails, alert and reject. The barline Phase 12c auto-inserts is already inside that measurement.
  
- **Neither name is `…Ss`-suffixed and neither is a partial query.** Phase 6 folded the cautionary check into `lineKeyChangeFits`, which walks the whole inheritance chain, after finding that a check covering only the previous line's cautionary accepts an edit that then overflows elsewhere. Read Phase 6's closing notes before calling either; the class exposes no half of the check on its own.
  

Both alerts are `JOptionPane`-based — read `.claude/guides/option-dialogs.md` and `.agents/guides/strings.md` before writing either.

3. **Redirect the dialog's commit through `ScoreViewController`.** Phase 12a leaves it committing via `line.setKey` inside a modification bracket; route that single call through the path Phase 10b task 6 wires, so a key edit reaches accidental reconciliation and its restatement confirm by the same route every other pitch-moving edit does. Change nothing else in the dialog.
  
4. Order the two user prompts: **fit is checked first, restatements second** (Phase 10b task 7). A change that is going to be refused for not fitting must not first ask the user about accidentals it will never apply.
  
5. Write tests for what this phase adds. **The dialog's own cases are Phase 12a's** — its combo entries, its inherit choice and its opening state are already covered there; do not restate them.
  

- A change whose cautionary does not fit is rejected and leaves the model untouched — assert the model, not the alert, since an alert that fires while the edit still lands is the failure worth catching.
  
- A mid-line key signature that does not fit its line is rejected and leaves the model untouched — the second rejection, which the first case does not reach.
  
- A rejected-for-fit change never asks about restatements. That is task 4's ordering, and it is the only assertion that distinguishes the two orders.
  

6. Run `./scripts/compile.sh` and `./scripts/test.sh KeySignatureChangeDialogTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 13: Design Document
**Status:** Done  
**BlockedBy:** —  
**Files:** docs/key-signatures.md, CLAUDE.md  
**Recommended model/effort:** Sonnet, medium — the design is settled and stated below; this is writing it down, not deciding it.
### Tasks
1. Write `docs/key-signatures.md` as a tier-3 design document — architectural and domain rules that span subsystems, which no single class's Javadoc can state. Read `.claude/guides/contracts.md` §"When it belongs in `docs/` instead" for what qualifies, and `docs/mutations.md` for the house shape.
  
2. **Include exactly one diagram: the inheritance chain and its stopping rule.** Draw a short run of lines — some with their own key, some inheriting, one carrying a mid-line change — and mark where a change to one line's key stops propagating. That rule ("forward to the first line with its own key") governs four subsystems independently: `inheritedKey` propagation (Phase 3), reconciliation reach (Phase 10b), cautionary derivation (Phase 5) and MusicXML emission (Phase 7). The list below states it four separate times; the picture is the one copy they can all point at.
  

- No other diagram. The rest of this document is contracts stated once, and a diagram that redraws them is maintenance cost with no reader benefit.
  
- `docs/clipboard.md` §5's paste-mode state machine is Phase 11's to redraw, not this phase's.
  

3. The document states these settled rules. Method Javadoc **links** to this document rather than paraphrasing it; a paraphrase is a second copy and the second copy goes stale.
  

- **There is no song-wide key.** Each line has a key or inherits the one in effect at the end of the previous line. Line 0 always has one.
  
- **Two representations, one query.** A key change at a line boundary is the line's own `Key`; a mid-line change is a `KeySignatureElement` in the element list. Both resolve through `Line.keyAt(int)`, which is what every consumer asks.
  
- **A mid-line key change is always preceded by a barline** and never sits at index 0. The user is not restricted to positions that already have one: adding a key signature elsewhere inserts a `SINGLE_BARLINE` before it, as part of the same modification. The barline and the key signature are deleted together.
  
- **A key change cancels accidentals** the way a barline does, via `ElementType.cancelsAccidentals()`.
  
- **A key change propagates accidentals forward, across lines.** Like an added barline, it moves pitches, so the reconciliation that preserves every pitch the user did not change runs over it. Its reach is the inheritance chain — from the change point to the first line with its own key — which makes it the only edit in the program that spans lines, and the reason `AccidentalReconciliation` and `AccidentalRestatements` take a line range rather than a line. The user sees one restatement prompt for the whole range.
  
- **The cautionary key signature is rendering only.** It is derived by comparing adjacent lines' running keys, stored nowhere, and never written to MusicXML. It is still editable: double-clicking it edits the **next** line's key, the change it depicts.
  
- **Where the cautionary sits depends on whether its line fits.** On a line that fits, it is right-aligned to the line width less `KeyChange.RIGHT_MARGIN_SS`, a span the trailing reservation keeps clear for it. On an overflowing line the margin is already behind the last element, so it starts one line rest past the rightmost element edge instead — extending the overflow rather than colliding with the music. `LayoutEngine.positionTerminalFlushRight` skips an overflowing line for the same reason. Phase 12's hit target follows the glyphs, so it follows this placement rather than the margin.
  
- **The cancellation policy**: same type (more or fewer accidentals) draws the new signature alone; a type change draws naturals cancelling the entire previous signature, then the new one.
  
- **An interactive key edit that will not fit is refused; everything else overflows.** The cautionary widens the previous line's trailing reservation, and a mid-line key signature (plus any barline inserted with it) widens its own line. Before either edit is accepted, `KeyEditFitCalculator` asks the layout solver whether the line still fits; when it does not, the user is alerted and the modification is rejected. Every other path keeps the existing best-effort behavior — a document whose key change stops fitting after a page-size or font change renders overflowing and flagged, because refusing there would make it unopenable.
  
- **MusicXML**: `<key>` goes in the measure where the key takes effect, so a line-boundary change writes into the _following_ line's first measure. `<cancel>` is written on type changes and ignored on read, because each application implements its own cancellation policy.
  
- **MIDI**: a `FF 59` key signature meta-event is emitted at tick 0 and at every key change, with `sf` from the signed fifths and `mi` the constant 0.
  
- **Every key is major, and mode is not modelled.** `Key` is a signature — a type and a count — with no mode component. MusicXML's `<key>` takes a `<mode>` child, so the writer emits `major` for the benefit of whoever reads our output, but the reader ignores it: only SongScribe-authored files pass the provenance gate, so no minor or modal key can enter (`docs/musicxml-object-model.md`, _Only SongScribe documents are read_). Nothing in rendering, spacing, accidental resolution or the editing UI has a mode dimension to account for.
  

4. Two rules the list above does not yet state, both settled in review:
  

- **A line holds a key only where one changes.** `Line.setKey` collapses a key equal to the inherited one to null, so "this line restates the key it already had" is not representable — deliberately, because every header draws its key either way, so a pinned line would be invisible while silently blocking propagation.
  
- **An interactive key edit is refused on a line that already overflows**, unlike an interactive lyric edit, which is deliberately allowed so the user can shorten a syllable to recover. Name the divergence and that it was a decision, not an oversight.
  

5. Add a "Key signatures" row to the **Required Reading by Task** list in `CLAUDE.md`, pointing at `docs/key-signatures.md`, covering: line keys, mid-line key changes, cautionary rendering, and the cancellation policy.
  
6. No compile or test gate — this phase writes no code.
  

* * *
## ✅ Phase 14a: MIDI Mid-line Key Test
**Status:** Done  
**BlockedBy:** 2, 3 (both done)  
**Files:** src/test/java/songscribe/midi/LineTrackBuilderTest.java  
**Recommended model/effort:** Sonnet, medium — one assertion against a chain that already exists, with no subsystem disagreement to reason about. It needs only the element and the index-taking resolution, both of which shipped in Phases 2 and 3, so it can run now rather than waiting behind every other phase.

### Tasks
1. Read `.agents/guides/testing-unit.md` first.
  
2. Add a MIDI test to `LineTrackBuilderTest`: notes after a mid-line key change get the pitches the new key implies. MIDI pitch flows through `StaffElement.getPitch()` → `findLastAccidental()` → `findEffectiveAccidental(Line, int)` → `keyInEffectAt(Line, int)`, so this requires no MIDI-side change — the test asserts that the chain actually carries the index through, which is the thing that could silently break.
  
3. Run `./scripts/compile.sh`, then `./scripts/test.sh LineTrackBuilderTest`. Both must report SUCCESS / green.
  

* * *
## ✅ Phase 14b: Cross-cutting Tests
**Status:** Done  
**BlockedBy:** 5, 6, 7, 8, 9, 10b, 12b, 12c, 12d, 17  
**Files:** src/test/java/songscribe/dom/KeyResolutionTest.java  
**Recommended model/effort:** Opus, high — these are the properties that span phases, and each one is where two subsystems could disagree without any single phase's tests noticing. The MIDI case, which is a single chain rather than a disagreement, went to Phase 14a.
### Tasks
1. Read `.agents/guides/testing-unit.md` and `.claude/guides/contracts.md` first. Every case below is derived from a stated contract; do not add cases by reading implementations.
  
2. Write `KeyResolutionTest` for the properties no single phase owns:
  

- **Accidental resolution respects a mid-line key change.** A note after a mid-line `KeySignatureElement` resolves against the new key; an identical note before it resolves against the old one. This is the promise `StaffElement.keyInEffectAt(Line, int)` makes and the reason it takes an index.
  
- **A key change cancels accidentals like a barline.** An explicit accidental before a key change does not carry across it to a later note at the same staff position.
  
- **A tie carries an accidental across a key change**, matching the barrier-escape behavior `findEffectiveAccidental` documents for barlines.
  
- **The two resolvers agree.** For a set of representative edits, `AccidentalReconciliation`'s projected resolution and `StaffElement.findEffectiveAccidental`'s live resolution return the same accidental for the same note. A preview disagreeing with its committed result is the failure this guards; assert it as a property over many inputs, not one pinned pair.
  
- **Inheritance chains.** With lines keyed `[C, null, null, D, null]`, every line's `getRunningKey()` is correct, and a mid-line change on line 1 changes what lines 2 and 3 inherit.
  

3. ~~Add a MIDI test to `LineTrackBuilderTest`.~~ **Done in Phase 14a.**
  
4. Run `./scripts/compile.sh`, then the full unit suite with `./scripts/test.sh`. Report the result. If any pre-existing test fails, fix it — do not assume it was already failing and do not stash to check.
  

* * *
## ⏳ Phase 15: Manual UI Verification
**Status:** Pending  
**BlockedBy:** 14  
**Files:** —  
**Recommended model/effort:** Sonnet, low — drives the app and reports; no design decisions.
### Tasks
1. **Ask the user for permission before running the app.** `./scripts/run.sh` must never be executed without it.
  
2. Run `./scripts/run.sh` and have the user confirm each behavior:
  

- Double-clicking a line's header key signature opens the dialog; changing the key updates that line and every line inheriting from it.
  
- Double-clicking the cautionary at the end of a line opens the dialog for the **next** line's key.
  
- Double-clicking a mid-line key signature's accidentals opens the dialog for that element.
  
- A cautionary key signature appears at the end of the preceding line, with naturals only on a sharps↔flats change.
  
- Setting a line back to "inherit" removes its key and the cautionary.
  
- Changing a line's key with two inheriting lines after it keeps every note's sounding pitch on all three, adding explicit accidentals where needed, and asks about restatements **once** rather than once per line.
  
- Invoking "Add Key Signature" shows the insertion-point marker, and the marker does not appear at index 0, at the last position, or anywhere touching an existing key signature or the barline before it.
  
- Inserting a mid-line key change **after a barline** draws it in place, and notes after it are spelled against the new key.
  
- Inserting a mid-line key change **between two notes** adds a barline before it in the same step, and one undo removes both.
  
- A change whose cautionary will not fit on the previous line alerts and leaves the score unchanged.
  
- Adding a key signature that will not fit on its line alerts and leaves the score unchanged.
  
- Deleting the barline before a mid-line key change prompts, and removes both.
  
- Deleting the mid-line key change itself prompts with a message that names the barline, and removes both.
  
- Selecting either the barline or the key signature highlights both.
  
- Opening a song, then shrinking the page until a key change no longer fits, renders the line in the overflow color rather than alerting — rejection is for interactive edits only.
  
- Opening one of the existing songs that has a key change at the start of a line renders correctly.
  

3. Report exactly which items passed and which failed, with the observed behavior for any failure. Do not report completion unless every item was checked.

* * *
## ✅ Phase 16: Shared Dialog Foundations
**Status:** Done  
**BlockedBy:** 1  
**Files:** src/main/java/songscribe/ui/dialog/BaseDialog.java, src/main/java/songscribe/ui/dialog/AttachmentDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsKeyCellRenderer.java, src/main/java/songscribe/ui/dialog/SongSettingsDialog.java, src/main/java/songscribe/ui/KeySignatureDisplay.java  
**Recommended model/effort:** Sonnet, medium — two mechanical consolidations against decided outcomes, both done with refactoring tools.

Two shared-dialog refactors that Phase 12 depends on and that have nothing to do with key signatures beyond needing `Key`. They were originally scheduled inside Phases 4 and 12 — a rename and a package move riding in a deletion phase, and an attachment-dialog refactor riding in the plan's largest phase. Neither shares any code with the work it was riding in.

### Tasks
1. Read `.claude/guides/dialogs.md` and `.claude/guides/contracts.md` first.
  
2. **Add** `DialogOp { ADD, EDIT, REMOVE }` **to** `BaseDialog` and reuse it across the attachment dialogs. `AttachmentDialog` already has a nested `AttachmentOp` with these same three values (`ADD`, `CHANGE`, `REMOVE`); replace it rather than letting two enums for one concept coexist, renaming `CHANGE` to `EDIT` in the move. `REMOVE` is kept because `AttachmentDialog` and its three subclasses (`TempoChangeDialog`, `AnnotationDialog`, `BeatChangeDialog`) rely on it; Phase 12's key-change dialog uses only `ADD`/`EDIT` from the shared enum. Renaming and moving it is `jet_brains_rename` plus `jet_brains_move`. Changing a shared dialog contract is a visible decision — state it in the commit message.
  
3. **Make the key cell renderer the shared one.** `SongSettingsKeyCellRenderer` is the glyph-plus-name key picker, and Phase 12's dialog must present keys exactly the same way rather than growing a second look.
  

- Rename it to `KeyCellRenderer` (it is no longer song-settings-specific), move it out of `songscribe.ui.dialog` if a shared UI package is the better home, and retype it from `KeySelection` to `Key`.
  
- Drive its list from `Key.allSignatures()` and delete its hand-built `SELECTIONS` — the same entries in the same order, built twice today. Keep only the glyph map, which is the part `Key` cannot supply. `allSignatures()` returns a shared immutable list after Phase 2 task 2, so this is not a per-call build.
  
- Its no-accidentals entry is `new KeySelection(KeyType.FLATS, 0)`, a state `Key`'s invariant rejects. It becomes `(NONE, 0)`, and the glyph map keys off that.
  
- `KeySignatureDisplay.getDisplayName(KeyType, int)` takes the same loose pair — collapse it to `getDisplayName(Key)`.
  

4. Delete `SongSettingsDialog.KeySelection` (`record KeySelection(KeyType keyType, int count)`, line 57). It is `Key` under another name and without the invariant. Keep the settings dialog compiling against `Key` here; Phase 4 removes its key section entirely and Phase 12 builds the new combo.
  
5. Run `./scripts/compile.sh`, then the existing attachment-dialog and song-settings tests. All must report SUCCESS / green — this phase changes no behavior, so a test needing an edit to stay green is a defect in the refactor.
  

* * *
## ✅ Phase 17: Remove the Deprecated Key Accessors
**Status:** Done  
**BlockedBy:** 4, 5, 6, 7, 8, 12a, 12b, 12c, 12d  
**Files:** src/main/java/songscribe/dom/Line.java  
**Recommended model/effort:** Sonnet, low — a deletion whose safety the compiler proves.

Phase 3 kept `Line.getKeyType()` and `Line.getKeyAccidentalCount()` alive as `@Deprecated` delegates so that every phase between it and the last of the Phase 12 halves could pass its own compile gate. Every consumer is now converted.

### Tasks
1. Confirm with `jet_brains_find_referencing_symbols` on both members that no reference remains in `src/main` or `src/test`. If any does, that phase left work undone — report it rather than converting the call site here.
  
2. Delete both methods.
  
3. Run `./scripts/compile.sh` and the full unit suite with `./scripts/test.sh`. Both must report SUCCESS / green.
