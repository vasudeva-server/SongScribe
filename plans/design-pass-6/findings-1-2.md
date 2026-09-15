# Findings — steps 1 and 2: inventory and class design

Target: `dom/Line.java` (2055 lines), `dom/StaffElementRun.java` (433), `dom/ElementChange.java` (209), `dom/ProjectedElements.java` (69). Tests: `LineTest`, `LineDeletionTest`, `StrandedKeyChangeTest`.

Fan-in below counts **production files outside the declaring file** holding a non-Javadoc reference. Tests and `{@link}`s are not counted.

## What it found

### The four types

`Line` — `public class Line implements LyricRun, SpanLookup`. Holds five fields of state: `elements` (+ unmodifiable view + lazy identity index map), `spans` (+ view), `song`, `key`, `lyricsYPosSs`. Its members fall into these groups, by line range:

| Lines | Group | Members |
|---|---|---|
| 178–210 | Position in song | ctor, `getSong`, `index`, `isFirst`, `isLast` |
| 212–293 | Bracket delegation to `Song` | `applyChange`, `withModification` ×2, `withModificationResult`, `withOptionallyNamedModification` |
| 295–460 | Key (pass 0) | `getKey`, `setKey`, `getRunningKey`, `keyAt`, `keyAtEndOfLine`, `keyAtEndOfLineAfterRemoving`, `nextLineRunningKey` |
| 462–578 | Parentage plumbing | `attach`, `detach`, `otherLineOf`, `appendChild`, `removeChild` (all private) |
| 580–870 | Element primitives | `addElement` ×2, `insertRun`, `setElement`, `modifyElement` ×2, `getElement`, `getElements` ×2, `removeElement`, `removeRange`; terminal-guard helpers `isTerminalGuardBypassed`, `guardsTerminalAt` |
| 871–1041 | Edit-level deletion | `deleteRange`, `deleteRanges`, `deleteRangeElementwise`, `deleteElement` |
| 1043–1278 | Element queries | `elementCount`, `effectiveElementCount`, `isEmpty`, `lastNoteIndex`, `getElementIndex`, `isInsideGraceHostPair`, `precedingGraceNoteIndex`, `isSamePitchAsFollower`, `isKeyChangeAt`, `isKeyChangeBarlineAt`, `canReplaceElementAt`, `canInsertElementAt`, `canBearSyllableAt` |
| 141–176, 1281–1291 | Layout field and Y defaults | `lyricsYPosSs` + accessors; `BEAT_CHANGE_DEFAULT_Y_SS`, `LYRICS_DEFAULT_Y_SS`, `ENDING_DEFAULT_Y_SS`, `TRILL_DEFAULT_Y_SS`, `SPAN_ADJACENCY_REACH` |
| 1293–1565, 1732–1895 | Span registry | `addTie`/`removeTie`, `addBeaming`/`removeBeaming`, `addTuplet`/`removeTuplet`, `addCrescendo`/`removeCrescendo`, `addDiminuendo`/`removeDiminuendo`, `addHairpin`/`removeHairpin` (private), `addTrill`, `removeTrillsOverlapping`, `addSpan`, `removeSpan`, `removeInvalidatedSpan`, `addPastedSpan`, `removeOverlappingTuplets`, `getSpans`, `mergeOverlappingSpans`, `bothResolved`, `ResolvedIndexPredicate`, `nearestNonGraceIndex`, `canAnchorHairpin`, `canEndHairpin`, `spansStructuralBoundary` |
| 1567–1730 | Span sweep on element change | `applySpanOutcomes`, `collectReshape`, `mergeAdjacentSpans`, `applySpanRun`, `HairpinSpan` |
| 1897–1962 | `SpanLookup` endpoints | `anchorIndexOf`, `endIndexOf`, `boundOf` |
| 1964–2049 | Ending-confirmation queries | `hasEndingInvalidatedByDeletion`, `removesConfirmableSpan`, `findEndingReplacementEffect`, `hasEndingInvalidatedByInsertion` ×2 |
| 1765–1784 | **Dead** | `isAnnotation`, `getFirstTrill`, `getFirstBeatChange` — zero callers in either tree |
| 1786–1788, 2051–2053 | Banner comments | "Range Element Management (Phase 4+)", "Beam Group Management (Phase 4+)" — the second heads an empty section |

`StaffElementRun` — `public interface`. Three abstract members (`getElement`, `elementCount`, `effectiveElementCount`), sixteen `default` methods in two groups (pairing: `hasIndex`, `noteAt`, `isPairedGraceNote`, `isHostOfPairedGraceNote`, `isKeyChangeBehindBarline`, `beginIncludingKeyChangeBarline`, `effectiveBegin`, `effectiveEnd`, `effectiveRange`; keys: `strandedKeyChangeIndices`, `redundantKeyChangeRanges` ×2, `lastKeyChangeKeyFrom`, `lastKeyChangeKey`, `keyAtEndUnder` ×2), the constant `FIRST_LEGAL_KEY_CHANGE_INDEX`, and the nested `record EffectiveRange(int begin, int end)` with `shiftedBy`. Nothing implements it directly: `LyricRun extends StaffElementRun`, and `Line` and `DetachedLyricRun` implement `LyricRun`. `ui/clipboard/Fragment` is not a run; it wraps its list in a `DetachedLyricRun` (`Fragment.run()`).

`ElementChange` — `public sealed interface` with `record Insertion`, `record Replacement`, `final class Deletion`, two abstract queries (`projectedElements`, `projectedIndexOf`) and three static factories. The factories are called only from `Line` (L643, L716, L820, L859, L1973, L2031). The queries are read only by `Hairpin` (L161, L187, L190) and `Span` (L268, `Deletion.deletedElements`). Everything about it is package-internal to `dom`.

`ProjectedElements` — package-private `final class extends AbstractList`, constructed only by the three `ElementChange` shapes.

### Invariants the types do not carry

| # | Where | What | Fact |
|---|---|---|---|
| I1 | `StaffElementRun.EffectiveRange` L419 | Two adjacent `int`s, no `begin <= end` check, public canonical constructor | Constructed raw at `LineDeletionTest` L154–155 with un-widened bounds, so the name promises a property ("effective" = already widened by `effectiveRange`) the type cannot keep. Also a component of `KeyChangeStranding.Stranded` and a parameter of `ProjectedEdit.replacing`. |
| I2 | `Line` | Eight public methods take a raw inclusive `(int, int)` range: `getElements(start, end)` L787, `removeRange(from, to)` L845, `keyAtEndOfLineAfterRemoving(begin, end)` L431, `spansStructuralBoundary(begin, end)` L1738, `removeOverlappingTuplets(begin, end)` L1759, `removeTrillsOverlapping(beginIndex, endIndex)` L1817, `hasEndingInvalidatedByDeletion(from, to)` L1972, `effectiveRange(begin, end)` (`StaffElementRun` L255); plus `ElementChange.forDeletion(line, from, to)` L206 and `Deletion(line, from, to)` L145 | Every one is a pair a call site can transpose with no compiler help, while `deleteRange` L898 already takes the range as a type. The concept "inclusive element range" exists once as `EffectiveRange` and nine times as two ints. |
| I3 | `Line.canAnchorHairpin(int index, int lastIndex)` L1652 | Two adjacent ints of different meaning | One caller (`MusicEditOperations` L745). |
| I4 | `Line.nearestNonGraceIndex(int fromIndex, int step)` L1331 | `step` is a direction selected by a bare `int` (`-1`/`1`) | Two callers: `Beam` L436 passes a computed `step`; `MusicEditOperations` L176 passes the literal `-1`. |
| I5 | `Line.mergeOverlappingSpans(…, boolean absorbAdjacent)` L1371 | Mode-selecting boolean | Private; two callers pass literals (`false` for beams L1354, `true` for hairpins L1542). The value is a property of the span *kind* — beams never absorb an adjacent group, hairpins always do. |
| I6 | `Line.withOptionallyNamedModification(@Nullable String label, …)` L287 | Nullable parameter selecting between two overloads | Two callers (`ScoreViewController` L1247, `SelectionActionApplier` L174). Its Javadoc L281–285 argues against collapsing the overloads because of "every test that stubs one of them"; no such test exists. |
| I7 | `-1` sentinels | `getElementIndex` L1099, `lastNoteIndex` L1082, `precedingGraceNoteIndex` L1140, `nearestNonGraceIndex` L1331, `ElementChange.projectedIndexOf` L50, and the dead `getFirstTrill`/`getFirstBeatChange` | Each returns a legitimate-looking `int` that is also "absent". `getElementIndex` has 29 caller files; its callers compare against `-1` or `< 0` by convention. |
| I8 | `Line` constructor L178 | `new Line(song)` yields a line with no key and no place in its song | Pass 0 settled the key half: `getRunningKey` is total over it. `index()` is partial over the other half (see G1). |
| I9 | Terminal policy | `isTerminalGuardBypassed` L608, `guardsTerminalAt` L619, and the `FINAL_DOUBLE_BARLINE` checks at L624–630 and L694–700 | `Line` encodes when the song's auto-maintained terminal may be touched, reading three `Song` flags. The policy belongs to `Song`/`TerminalMaintainer` (row 12); the list it protects belongs here. |

### Every `@Nullable`

| Where | Which of the three answers |
|---|---|
| `Line.key` field L105, `getKey()` L302 | **Always derivable** — the pattern `design.md` cites by name; `getRunningKey`/`keyAt`/`keyAtEndOfLine` are the total queries. `getKey` still has 7 production caller files (`ChainEdit`, `Song`, `KeyChangeSite`, `AccidentalScanView`, `LineTrackBuilder`, `KeyChangeDialogController`, `SongIO`), each of which needs the "does this line establish its own key" distinction rather than the running key. Pass 0's. |
| `Line.elementIndexMap` L139 | Lazy cache; null means "rebuild". Its Javadoc L123–128 explains at length a concurrency guarantee it says it does not provide. |
| `Line.getElementIndex(@Nullable StaffElement)` L1099 | Accepts null "matching `ArrayList.indexOf`, which this replaced" — change narration. Whether any of its 29 caller files passes a null is not established here. |
| `Line.nextLineRunningKey()` L454 | **Honestly partial** and documented as the answer ("nothing to warn about"). One caller (`LineKeys.of`). |
| `Line.withOptionallyNamedModification(@Nullable String)` L287 | A mode, not an absence — I6. |
| `Line.otherLineOf(Span)` L526 | Private, honestly partial. |
| `Line.boundOf(@Nullable StaffElement)` L1932 | Null comes from `Span.getAnchorElement()`/`getEndElement()` — row 7's nullability, resolved here to `SpanBound.ABSENT`. |
| `StaffElementRun.lastKeyChangeKeyFrom` L361, `lastKeyChangeKey` L383 | **Honestly partial** — "no key change at or after" is a real answer, and `keyAtEndUnder` is the total form built on it. `lastKeyChangeKey()` has **no production caller**: only `MusicXmlStrandedKeyChangeTest` (L78, L114, L117). |
| `Song.maintainKeyInvariant(…, @Nullable Key)` (outside target) | Exists only so `Line.applyChange`'s suspended branch can pass null — see G2. |

### Every guard, with who can reach it

| # | Where | Guard | Reachable by |
|---|---|---|---|
| G1 | `Line.index()` L192 | throws `IllegalStateException` when the line is not in its song | Yes: detached lines are built for measurement (`KeyEditFitCalculator`, `UnitTest.detachedLine`), and `nextLineRunningKey` L455 and `boundOf` L1950 both test `song.contains(this)` first to avoid it. |
| G2 | `Line.applyChange` L228 | suspended-tracking branch (run mutator, `song.maintainKeyInvariant(mutation, null)`, return), then throws if `!song.isModifying()` | **Duplicates `Song.applyChange` exactly.** `Song.applyChange` (Song.java) computes `startingKeyBefore` only for `LineInsertion`/`LineDeletion` (`promotesLineZero`), then `modifications.applyChange` — which itself runs the mutator and returns under suspension, and throws `IllegalStateException` at depth 0 — then `maintainKeyInvariant`, then returns before the chain walk when suspended. For every mutation a line can post, that is behavior-identical to what `Line.applyChange` does before delegating, so the branch and the throw at L229–245 are a second copy with a different message. `ModificationSession.applyChange`'s comment says it "mirrors the guard in `Line.applyChange`". |
| G3 | `Line.keyAt` L382 | explicit bounds check admitting `elementCount()` | Reachable by any caller passing `index - 1` at 0; `ScoreViewController` L1213/L1340 clamp with `Math.max(…, 0)` first, `KeyChangeElement.previousKey` L214 relies on the position invariant. The check exists because the loop below would silently return `getRunningKey()` for a negative index. |
| G4 | `Line.addElement(int,…)` L624 | `FINAL_DOUBLE_BARLINE` only appended to the last line, unless bypassed | Which production caller can insert a `FINAL_DOUBLE_BARLINE` at a non-terminal position is not established; `TerminalMaintainer` is bypassed by `isInAutoMaintenance`. |
| G5 | `Line.setElement` L694, L702 | same as G4 for replacement; and the terminal may only be replaced by a valid terminal type | As G4. |
| G6 | `Line.removeElement` L807, `removeRange` L846 | the terminal may not be removed | `ScoreViewController.handleDelete` deletes a selection; whether a selection can include the terminal is a selection-package fact (row 20). |
| G7 | `Line.detach` L510 | early return when `element.getParentLine() != this` | **Documented dead** at L507–509: "No caller reaches this return today … kept because … untestable". |
| G8 | `removeTie` L1308, `removeBeaming` L1448, `removeTuplet` L1485, `removeHairpin` L1557, `removeSpan` L1833 | `spans.indexOf < 0` → silent return | One caller relies on it: `ScoreViewController.tryInsertFragment` L1460 via `removeInvalidatedSpan`, whose Javadoc L1852–1858 states the idempotency as a promise. Every other caller passes a span it just found on the line. |
| G9 | `Line.collectReshape` L1633 | throws when a non-`Hairpin` answers `Reshape` | Nothing today: only `Hairpin.outcomeFor` constructs a `Reshape`. The type does not prevent another `Span` subclass from doing so. |
| G10 | `Line.applySpanRun` L1703 | `run.isEmpty()` → return | Dead: `mergeAdjacentSpans` L1680–1692 only calls it with a non-empty run (a run is flushed only after at least one span was added, and the final flush follows a loop over a non-empty list — `applySpanOutcomes` L1617 only builds a per-kind list on a reshape). |
| G11 | `StaffElementRun.noteAt` L113 | throws when the element is not a note | Caller bug only; 14 caller files each assert the kind beforehand ("a beam member, the owner of a slide"). |
| G12 | `Line.isSamePitchAsFollower` L1167, `isKeyChangeAt` L1188, `canReplaceElementAt` L1223, `nearestNonGraceIndex` L1332, `isKeyChangeBehindBarline` L159, `isPairedGraceNote` L136, `isHostOfPairedGraceNote` L141 | out-of-range → a documented total answer | These are contracts, not guards: each states "out of range yields X". |
| G13 | `Line.spansStructuralBoundary` L1738 | none — "callers own their indices" | One caller. |
| G14 | `ProjectedElements.get` L65 | `Objects.checkIndex` | `List` contract, not a guard; the comment explains why the underlying line's own bounds would not do. |

### Fan-in per public member

Production files outside the declaring file. `high` = 10 or more.

| Member | Fan-in | Member | Fan-in |
|---|---|---|---|
| `getElement` | 60 files, 232 refs | `elementCount` | high (36 files) |
| `getElementIndex` | high (29 files) | `effectiveElementCount` | high (26 files) |
| `addElement(StaffElement)` | high (~22 files) | `index()` | high (16 files) |
| `getElements()` | 11 | `getRunningKey` | 11 |
| `modifyElement(int, ElementField, Runnable)` | 12 | `keyAt` | 9 |
| `getKey` | 7 | `setKey` | 6 |
| `setElement` | 7 | `withModification(String, Runnable)` | 7 |
| `getElements(int, int)` | 6 | `withModification(Runnable)` | 5 |
| `keyAtEndOfLine` | 3 (`Song`, `KeyChangeStranding`, `LineKeys`) | `isFirst` | 5 |
| `isSamePitchAsFollower` | 4 | `getSpans` | 4 |
| `addSpan` | 4 (`MeasureMapper`, `LineIO`, `MusicEditOperations`, `MutationReplayer`) | `removeSpan` | 2 |
| `isLast` | 3 | `modifyElement(int, EnumSet, Runnable)` | 3 |
| `addTie` | 3 | `removeTie` | 2 |
| `addBeaming` | 3 | `removeBeaming` | 4 |
| `addTuplet` | 3 | `removeTuplet` | 6 |
| `addCrescendo` | 4 | `removeCrescendo` | 2 |
| `addDiminuendo` | 4 | `removeDiminuendo` | 2 |
| `canReplaceElementAt` | 2 | `canInsertElementAt` | 2 |
| `canBearSyllableAt` | 1 (`LyricTargetResolver`, 3 sites) | `insertRun` | 2 |
| `addElement(int, StaffElement)` | 2 (`MutationReplayer`, `PreviewElementInserter`) | `removeElement` | 5 |
| `removeRange` | 1 (`MutationReplayer`) | `deleteRange` | 1 (`ScoreViewController`) |
| `deleteRanges` | 2 (`Song`, `KeyChangeStranding`) | `applyChange` | 2 (`GraceModeManager`, `MutationReplayer`) |
| `withModificationResult` | 2 | `withOptionallyNamedModification` | 2 |
| `keyAtEndOfLineAfterRemoving` | 1 | `nextLineRunningKey` | 1 |
| `lastNoteIndex` | 1 (`KeyChangeAction`) | `isKeyChangeAt` | 1 (`KeyChangeAction`) |
| `isInsideGraceHostPair` | 1 | `precedingGraceNoteIndex` | 2 |
| `nearestNonGraceIndex` | 2 | `isEmpty` | 1 (`Song`, 2 sites) |
| `canAnchorHairpin`, `canEndHairpin`, `spansStructuralBoundary` | 1 each (`MusicEditOperations.resolveHairpinAction`) | `removeOverlappingTuplets` | 1 (`SelectionActionApplier`) |
| `removeInvalidatedSpan` | 2 (`Song`, `ScoreViewController`) | `addPastedSpan` | 1 |
| `addTrill`, `removeTrillsOverlapping` | 1 each (`TrillAction`) | `hasEndingInvalidatedByDeletion` | 1 (3 sites) |
| `findEndingReplacementEffect` | 2 | `hasEndingInvalidatedByInsertion` ×2 | 1 each |
| `getLyricsYPosSs` | 1 (`FormatMigrator`) | `setLyricsYPosSs` | 3 (`FormatMigrator`, `MutationReplayer`, `LineIO`) |
| `getSong` | high (24 files) | `isAnnotation`, `getFirstTrill`, `getFirstBeatChange` | **0** |
| `BEAT_CHANGE_DEFAULT_Y_SS`, `ENDING_DEFAULT_Y_SS`, `TRILL_DEFAULT_Y_SS` | 1 each, all `io/LegacyLineOffsets.DEFAULTS` | `LYRICS_DEFAULT_Y_SS` | 0 outside `Line` |
| `SPAN_ADJACENCY_REACH` | 1 (`MusicEditOperations`) | | |

`StaffElementRun`:

| Member | Fan-in |
|---|---|
| `hasIndex` | 12 |
| `noteAt` | 6 |
| `isPairedGraceNote` | 7 |
| `isHostOfPairedGraceNote` | 2 (`PreviewElementInserter`, `LyricTargetResolver`) |
| `isKeyChangeBehindBarline` | 0 outside the interface and `Line` |
| `beginIncludingKeyChangeBarline`, `effectiveEnd` | 1 each (`Fragment.capture`) |
| `effectiveBegin` | 1 (`ScoreViewController.pasteDisplacementIndex`) |
| `effectiveRange` | 3 (`KeyChangeDialogController`, `ScoreViewController`, `Selection`) |
| `strandedKeyChangeIndices` | 0 outside the interface (tests only, via `DetachedLyricRun`) |
| `redundantKeyChangeRanges(int, Key)` | 2 (`ScoreViewController`, `KeyChangeDialogController`) |
| `redundantKeyChangeRanges(Key)` | 3 (`Song`, `KeyChangeStranding`, `Fragment`) |
| `lastKeyChangeKeyFrom` | 0 outside the interface and `Line` |
| `lastKeyChangeKey` | **0 production** (tests only) |
| `keyAtEndUnder(Key)` | 4 (`KeyChangeStranding`, `Song`, `Fragment`, `KeyEditFitCalculator`) |
| `keyAtEndUnder(int, Key)` | 3 |
| `EffectiveRange` | 6 files; `shiftedBy` 1 (`KeyChangeStranding`) |
| `FIRST_LEGAL_KEY_CHANGE_INDEX` | 1 (`KeyChangeDialogController`) |

`ElementChange`: every member has fan-in 0 outside `dom` — factories from `Line` only, queries from `Hairpin` and `Span` only.

### Documents describing this subsystem

- `docs/span-invalidation.md` — the whole of `ElementChange`, `ProjectedElements` and `Line.applySpanOutcomes`: one funnel, projection not description, three answers, decisions before the change lands, and what stays with the line.
- `docs/mutations.md` — brackets, order within a batch, suspension vs replay; the contract `Line.applyChange`/`withModification` cite.
- `docs/key-changes.md` — a line holds a key only where one changes; the barline pair; stranding. What `StaffElementRun`'s key group implements.
- `docs/lyrics.md` — the run performs repairs; the contract behind `insertRun` and `deleteRange`'s lyric steps (`LyricRun`, row 9).
- `docs/hairpin-editing.md` — the merge rules `addHairpin` hosts.
- `docs/line-layout.md` — layout only; does not describe the model.

### Tests

| Class | Lines | `@Test` / parameterized | Passing | Appears to cover |
|---|---|---|---|---|
| `LineTest` | 230 | 3 / 3 (3 + 6 + 6 rows) | 47 with `LineDeletionTest` | `insertRun`'s two-sided repair; `canInsertElementAt`, `canBearSyllableAt` over a line holding both closed pairs |
| `LineDeletionTest` | 162 | 1 / 1 (3 rows) | (above) | `deleteRange` covers the widened range; `deleteRanges` works last-to-first |
| `StrandedKeyChangeTest` | 359 | 3 / 4 | 21 | `strandedKeyChangeIndices`, `lastKeyChangeKeyFrom`, `keyAtEndUnder` ×2, `redundantKeyChangeRanges` ×2, asked of both `Line` and `DetachedLyricRun` |

Baseline: 68 passing across the three. Other `dom` tests reach `Line` members as fixtures (`InheritedKeyPropagationTest`, `KeyChangeSiteTest`, `AccidentalChainTest`, `SongAccidentalWalkTest`) but assert other rows' promises.

### Other facts

- `Line.lyricsYPosSs` Javadoc L150–155: "This field is still in active use … Per-instance lyrics offsets are not yet implemented." — a plan, not a description. `LYRICS_DEFAULT_Y_SS`'s comment carries its pixel value (`// 50px`), as do the other three Y constants.
- `Line.elementIndexMap` Javadoc L130–133 is a universal claim ("every write to `elements` … goes through `attach` or `detach`"); checked and true: L650, L726, L827, L866 are the four writes and each sits beside one.
- `ElementChange.Deletion` Javadoc L136–137 states why it is not a record — present-tense and about the type as it is; not narration.
- `Line` L84 is not `final` and nothing extends it.

## Class design

`Line` is an element run that belongs to a song and posts mutations. Everything on it that is not that moves as below. Where the behaviour is a function of the run alone it pushes down into the run interfaces, so no call site changes. Where it needs an owner, `Line` composes one.

### What `Line` keeps

| Group | Members |
|---|---|
| State | `song`, `key`, `elements` (+ view + identity index cache), `lyricsYPosSs`, and a `LineSpans` field |
| Position in song | ctor, `getSong`, `index`, `isFirst`, `isLast` |
| Key (pass 0) | `getKey`, `setKey`, `getRunningKey`, `keyAt`, `keyAtEndOfLine`, `keyAtEndOfLineAfterRemoving`, `nextLineRunningKey` |
| Parentage | `attach`, `detach` |
| Recorded primitives | `addElement` ×2, `setElement`, `modifyElement` ×2, `removeElement`, `removeRange`, `getElement`, `getElements` ×2 |
| Run queries that need `Line` | `elementCount`, `effectiveElementCount` (reads `song.isAutoMaintainedTerminal`), `getElementIndex` (the cache) |
| Layout | `lyricsYPosSs` accessors, `LYRICS_DEFAULT_Y_SS` |

Roughly 800 of the 2055 lines. `Line` stops implementing `SpanLookup` and gains `spans()` returning its `LineSpans`. It is declared `final`.

### 1. Run queries → `StaffElementRun` defaults

`isEmpty`, `lastNoteIndex`, `isInsideGraceHostPair`, `precedingGraceNoteIndex`, `isSamePitchAsFollower`, `isKeyChangeAt`, `isKeyChangeBarlineAt`, `canReplaceElementAt`, `canInsertElementAt`, `canBearSyllableAt`, `nearestNonGraceIndex`, `spansStructuralBoundary`. Each reads only `elements`, `getElement`, `hasIndex` and sibling defaults (checked L1043–1278, L1331, L1738); rewrite the direct `elements.get`/`elements.size()` reads as `getElement`/`elementCount`. No call site changes; `DetachedLyricRun` and `Fragment` gain them.

### 2. Edit-level operations → `EditableRun extends LyricRun`

Abstract: `addElement(int, StaffElement)`, `removeElement(int)`, `removeRange(ElementRange)`. Defaults: `insertRun`, `deleteRange`, `deleteRanges`, `deleteRangeElementwise`, `deleteElement`. `deleteRange` and `deleteElement` call only `LyricRun`/`StaffElementRun` members plus those three primitives, so the bodies move unchanged. `Line` and `DetachedLyricRun` implement it. The "must be inside a bracket, records into it" clause is `Line`'s primitives' contract, not the interface's; a detached run records nothing. Callers (`ScoreViewController`, `KeyChangeDialogController`, `Song`, `KeyChangeStranding`) are unchanged.

### 3. The span half → `LineSpans`, one per line, `implements SpanLookup`

Owned by `Line`, holding a back-reference to it for mutation records and `applyChange`. Contents:

- `spans` list and view, `getSpans`, generic `add`/`remove` (today `addSpan`/`removeSpan`), `appendChild`/`removeChild`/`otherLineOf` (the cross-line tie write reaches `otherLine.spans()`)
- the sweep: `applySpanOutcomes`, `collectReshape`, `mergeAdjacentSpans`, `applySpanRun`, `HairpinSpan`, `mergeOverlappingSpans`, `bothResolved`, `ResolvedIndexPredicate`
- the anchor re-pointing loop from `setElement` L730–740, as `repoint(oldElement, newElement)`
- `SpanLookup` endpoints `anchorIndexOf`, `endIndexOf`, `boundOf`
- `removeOverlappingTuplets`, `removeTrillsOverlapping`, `SPAN_ADJACENCY_REACH`

`Line`'s four primitives call `spans.sweep(change)` where they call `applySpanOutcomes` today, and `setElement` calls `spans.repoint`. Every `SpanLookup` call through a `Line` becomes `line.spans().…`: 39 production files. `Span` and `Hairpin` read only `getElement`, `getElements`, `getElementIndex` off the line, so nothing cycles back.

### 4. Ending-confirm predictions → `ElementChange`

`removesConfirmableSpan` becomes an instance method of `ElementChange`; `hasEndingInvalidatedByDeletion`, `hasEndingInvalidatedByInsertion` ×2 and `findEndingReplacementEffect` are deleted, and their three callers (`ScoreViewController`, `PreviewElementInserter`, `SelectionActionApplier`) build the change and ask it. The factories become public; the `insertedType.newInstance()` stand-in becomes a factory overload `forInsertion(line, index, ElementType)`.

### 5. Per-kind add and remove → the span types

`Span` declares `addTo(LineSpans)` and `removeFrom(LineSpans)`. Each kind emits its own mutation record and applies its own merge policy, so I5's boolean dissolves: `Beam` never absorbs an adjacent group, `Hairpin` always does. `LineSpans` keeps only the generic `add`/`remove` the kinds call. Gone from `Line`: `addTie`/`removeTie`, `addBeaming`/`removeBeaming`, `addTuplet`/`removeTuplet`, `addCrescendo`/`removeCrescendo`, `addDiminuendo`/`removeDiminuendo`, `addHairpin`/`removeHairpin`, `addTrill`, and the two dispatch switches `removeInvalidatedSpan` and `addPastedSpan`, which become `span.removeFrom(spans)` / `span.addTo(spans)`. G8's idempotent removal is stated once, on `removeFrom`. `canAnchorHairpin`/`canEndHairpin` move to `Hairpin`, where their Javadoc says the rule lives. `MutationReplayer`'s per-kind switch stays: the records are per-kind. This edits every `Span` subclass, which row 7 owns; record that in the register.

### 6. Song façade and terminal policy → `Song`

- `applyChange` becomes one call, `song.applyChange(mutation, mutator)`; `Song.maintainKeyInvariant` goes back to `private` with a non-null parameter. G2's second copy is gone.
- The four `withModification*` overloads are deleted; their 16 caller files call `getSong().withModification(…)`. I6 goes with them.
- `isTerminalGuardBypassed`, `guardsTerminalAt` and the four `FINAL_DOUBLE_BARLINE` checks become one `Song` query, "may this element at this position on this line be written over now" (name open), which the primitives ask once. G4–G6 land there; row 12 owns `TerminalMaintainer`, so record the move in the register.

### 7. Deletions and relocations

- Delete `isAnnotation`, `getFirstTrill`, `getFirstBeatChange`, the two banner comments, G7's dead branch in `detach`, G10's dead guard in `applySpanRun`.
- `BEAT_CHANGE_DEFAULT_Y_SS`, `ENDING_DEFAULT_Y_SS`, `TRILL_DEFAULT_Y_SS` move to `io/LegacyLineOffsets`, their only reader. Drop the pixel-value comments on all four Y constants.
- `lyricsYPosSs` Javadoc L150–155 describes a plan; replace it with what the field is.
- `lastKeyChangeKey()` has no production caller: step 7 decides.

### What varies

The run substrate (`Line` vs `DetachedLyricRun`) is abstracted by `StaffElementRun`/`LyricRun`/`EditableRun`. The change shape is sealed over three. The span kind is handled by the kinds themselves (5). `Line`'s dependence on one concrete `Song` stays concrete.

### 8. `EffectiveRange` → `ElementRange` (I1, I2)

The record is renamed `ElementRange`, stays nested in `StaffElementRun`, and validates `begin <= end` in its canonical constructor. It is the one type for an inclusive element range: every `(int, int)` pair in I2 takes it — `getElements`, `removeRange`, `keyAtEndOfLineAfterRemoving`, `spansStructuralBoundary`, `removeOverlappingTuplets`, `removeTrillsOverlapping`, `ElementChange.forDeletion` and `Deletion`, and `effectiveRange` itself, which becomes `effectiveRange(ElementRange)` returning the widened `ElementRange`. `deleteRange`'s contract states that it must be handed the widened range, since the type no longer says so.
