# Mutation Hierarchy & Notification Refactor (Issue #280)

**Type:** Master  <br>
**Created:** 2026-04-11  <br>
**Status:** In Progress  <br>
**BlockedBy:** --

---

## Context

Issue #280 was originally framed as adding a `CompositionWillChangeNotification` that fires before composition mutations. The two stated motivations were:

1. **Range element validation:** Range elements (Endings, ties, beams, tuplets, hairpins) need to react to element deletions that invalidate their anchor or end references.
2. **Undo support (issue #14):** Capturing pre-mutation state for undo snapshots.

During plan review, the design was refined:

- **Range cleanup does not need a `WillChange` notification.** Range elements store their anchor/end as `StaffElement` references, not indices. After an element deletion, the broken reference is detectable from the deleted element's identity. Range cleanup runs **inside** the deletion mutator via a template method on `RangeElement`, atomic with the user operation, with no message bus involvement.
- **Undo's pre-mutation snapshot is captured in the mutation records themselves.** Deletion mutations carry the deleted element/line references; property mutations carry old + new values; `ElementModification` carries a clone of the element before the mutation. Issue #14 (undo) can build on these without revisiting the mutation hierarchy.

The refactor still does substantial work:

- Replaces the `ChangeType` enum with a sealed `Mutation` hierarchy.
- Replaces `mutateAndPost(ChangeType, Runnable)` with a strict `applyChange(Mutation, Runnable)` that requires an open modification bracket.
- Adds a `withModification(Runnable)` helper as the primary bracket API.
- Adds `Line.removeRange(from, to)` with a single `ElementRangeDeletion` mutation, replacing the existing per-element delete loop in `handleDelete`.
- Separates document loading from user mutations via a new `DocumentDidLoadNotification`.
- Migrates all callers and subscribers off `ChangeType`.
- Introduces `RangeElement.isInvalidatedBy(List<StaffElement>)` template method, called from `Line.removeElement` and `Line.removeRange`.

`CompositionWillChangeNotification` itself is **not** added in this issue. The remaining sections of this plan describe the work that **is** in scope.

## Status Dashboard

| Phase | Description | Model | Status |
|-------|-------------|-------|--------|
| 1 | [Mutation Hierarchy](#-phase-1-mutation-hierarchy) | Sonnet | ✅ Done |
| 2 | [Notification Refactor](#-phase-2-notification-refactor) | Sonnet | ✅ Done |
| 3a | [Composition Mutation Audit](#-phase-3a-composition-mutation-audit) | Opus | ✅ Done |
| 3b | [Composition Bracket & applyChange](#-phase-3b-composition-bracket--applychange) | Sonnet | ✅ Done |
| 4a | [Migrate Simple Callers](#-phase-4a-migrate-simple-callers) | Sonnet | ✅ Done |
| 4b | [Migrate Complex Callers](#-phase-4b-migrate-complex-callers) | Opus | ✅ Done |
| 5 | [Migrate Subscribers](#-phase-5-migrate-subscribers) | Sonnet | ✅ Done |
| 6 | [Range Element Template Method](#-phase-6-range-element-template-method) | Sonnet | ✅ Done |
| 7a | [Tests: Mutation Hierarchy & Notifications](#-phase-7a-tests-mutation-hierarchy--notifications) | Sonnet | ✅ Done |
| 7b | [Tests: Composition Bracket & Setters](#-phase-7b-tests-composition-bracket--setters) | Sonnet | ✅ Done |
| 7c | [Tests: Line Mutations & Range Cleanup](#-phase-7c-tests-line-mutations--range-cleanup) | Sonnet | ✅ Done |
| 7d | [Tests: Coordinator, Dialog, Drag](#-phase-7d-tests-coordinator-dialog-drag) | Sonnet | ✅ Done |

---

## Design Decisions

### Mutation Flow

The lifecycle for every composition mutation:

```
withModification(() -> {                           ┐
  composition.applyChange(mutation₁, mutator₁);   │ caller's
  composition.applyChange(mutation₂, mutator₂);   │ bracket
  ...                                              │
})                                                 ┘
  │
  ├─ applyChange(mutation, mutator)
  │     ├─ throws IllegalStateException if depth == 0
  │     ├─ mutator.run()
  │     └─ accumulatedMutations.add(mutation)
  │
  └─ at bracket close (depth → 0):
       ├─ push undo entry (future, issue #14)
       └─ post CompositionDidChangeNotification(accumulatedMutations)
```

- `applyChange()` replaces `mutateAndPost(ChangeType, Runnable)`.
- `applyChange()` **throws `IllegalStateException`** if called outside a modification bracket. Test coverage is the safety net; missed sites surface as test failures, not silent regressions.
- `withModification(Runnable)` is the primary bracket API. It handles `try/finally` so a forgotten `endModification()` cannot strand the depth counter.
- `beginModification()` / `endModification()` remain available for the rare case where a `Runnable` is not ergonomic, but `withModification` is preferred.
- `CompositionDidChangeNotification` fires **once** when the outermost bracket closes, carrying all accumulated `Mutation` objects.

### Why no `CompositionWillChangeNotification`?

- **Range cleanup runs inside the mutator,** not via a subscriber. `Line.removeElement` and `Line.removeRange` capture the deleted element references, then call `rangeElements.removeIf(r -> r.isInvalidatedBy(deletedElements))` after mutating the elements list, all inside the same `applyChange()` invocation. Cleanup is atomic with the deletion, contained in one undo entry, with no notification overhead.
- **Reactive decoupling per range type is preserved** via the template method: each `RangeElement` subclass overrides `isInvalidatedBy` to express its own invalidation logic. Adding a new range type does not modify `Line`.
- **Undo (issue #14)** is a separate concern. When undo lands, it can either subscribe to the existing `CompositionDidChangeNotification` and use mutation before-state fields, or introduce `WillChange` at that point as a focused, undo-driven addition. Either path is open; deferring the choice keeps this issue smaller.
- Not adding `WillChange` removes the need for an `isDispatchingWillChange` reentrancy flag and eliminates a class of subtle bugs.

### Mutation Hierarchy

A **flat sealed hierarchy** under `Mutation` in package `songscribe.message.mutation`. No intermediate category interfaces — subscribers use `instanceof` / pattern matching to filter.

One non-sealed interface is defined for scope: `LineScopedMutation` provides `Line getLine()`. Element mutations implement it. Composition-scoped mutations (property, font, layout, structural) do not.

Only types that have callers or subscribers in this issue are created. Range mutations (`{Tie,Beam,Tuplet,Crescendo,Diminuendo}{Addition,Removal,Modification}`) are intentionally **deferred** until a subscriber needs them — see the deferred work list at the end of this plan.

```
sealed interface Mutation
    permits ElementInsertion, ElementDeletion, ElementRangeDeletion,
            ElementModification,
            LineInsertion, LineDeletion,
            MetadataChange, FontChange, LayoutChange, LyricsChange

interface LineScopedMutation {
    Line getLine();
}
```

**Element mutations** (implement `LineScopedMutation`):
- `ElementInsertion(Line line, int index, StaffElement element)` — element about to be / just inserted at index. The element reference is required because, during construction, the element does not yet live on the line.
- `ElementDeletion(Line line, int index, StaffElement deletedElement)` — element at index that was deleted. The reference is required because, after deletion, the element is no longer accessible via the line.
- `ElementRangeDeletion(Line line, int from, int to, List<StaffElement> deletedElements)` — contiguous range of elements that were deleted. The list captures all removed elements in order.
- `ElementModification(Line line, int index, EnumSet<ElementField> fields, StaffElement beforeElement)` — fields changed on the element at index. `beforeElement` is a clone of the element captured before the mutation runs; it is the source of truth for undo to revert. The post-mutation state is accessible via `line.getElement(index)`.

**Structural mutations** (composition-scoped):
- `LineInsertion(int lineIndex, Line line)` — line inserted at lineIndex. The reference is required (during construction the line may not yet live in the composition).
- `LineDeletion(int lineIndex, Line deletedLine)` — line at lineIndex that was deleted. The reference is required for the same reason as `ElementDeletion`.

**Property mutations** (composition-scoped):
- `MetadataChange(MetadataField field, @Nullable Object oldValue, @Nullable Object newValue)` — most metadata is `String` (title, place, attribution, etc.) but some fields are `int` (year, month, day, tempo, keyAccidentalCount) or other types (e.g. `KeyType` enum). The expected runtime type is documented on each `MetadataField` enum value's javadoc. `Object` is used instead of generics to keep the sealed hierarchy simple.
- `FontChange(FontField field, Font oldFont, Font newFont)` — all values are `Font`.
- `LayoutChange(LayoutField field, @Nullable Object oldValue, @Nullable Object newValue)` — values are mostly `double` (staff-space measurements) or `int`. Same `Object` rationale as `MetadataChange`.
- `LyricsChange(LyricsField field, String oldText, String newText)` — all values are `String`.

`MetadataField`, `FontField`, `LayoutField`, `LyricsField` are enums whose values mirror today's setter coverage (e.g. `MetadataField.TITLE`, `FontField.LYRICS`, `LayoutField.LINE_WIDTH`, `LyricsField.MAIN`/`UNDER`/`BANGLA`/`TRANSLATED`).

`LyricsChange` is a dedicated mutation (not folded into `MetadataChange`) because it mirrors the existing `ChangeType.LYRICS` distinction: lyrics text content has its own subscriber (`LyricsPanel`), separate from the metadata subscribers.

### ElementField Enum

Created in Phase 1 as empty. Phase 4b populates it with whatever fields `NoteDragHandler` changes during a drag (typically `PITCH`, possibly `NOTE_TYPE` if drag can change a note to a rest). Add more values incrementally as additional `ElementModification` emitters appear. The `EnumSet<ElementField>` on the mutation lets subscribers (currently none, eventually undo) filter on which fields changed without inspecting `beforeElement` field-by-field.

### Asymmetry of Element References

Insertion and deletion mutations both carry an element reference, but for different reasons:

- **Insertion:** the element does not yet live on the line at construction time, so the only way to expose it is via the mutation.
- **Deletion:** the element was on the line, but after the mutation runs, it is gone — `line.getElement(index)` would return a different element or be out of bounds. Subscribers (current and future) can only identify the deleted element via the mutation.

`ElementModification` does not carry an element reference because the element exists on the line both before and after, and the index remains valid; subscribers call `line.getElement(index)`.

### ChangeType Elimination

`ChangeType` is fully replaced by the `Mutation` hierarchy. Subscribers that currently filter on `ChangeType` use `instanceof` checks on `Mutation` subclasses instead.

### DocumentDidLoadNotification

A new notification replacing `ChangeType.FULL`. Carries the loaded `Composition`. Posted by `Score.setComposition()` when a document is loaded (File > Open, File > New). Subscribers that currently check `hasChangeType(ChangeType.FULL)` migrate to a separate `@Handler` for this notification.

### Notifications Carry `List<Mutation>`

`CompositionDidChangeNotification` carries `List<Mutation>` and `Composition`.

Convenience methods:

- `@Nullable Line getLine()` — derives a single line from the mutation list. Result is **lazily cached** so repeated calls by multiple subscribers are O(1) after the first call. Semantics:

  ```
  Examples:
  
  [ElementDeletion(line5, 3), ElementInsertion(line5, 7, e)]   → line5
    ↑ both LineScopedMutation, both target line5
  
  [ElementDeletion(line5, 3), MetadataChange(TITLE)]           → line5
    ↑ MetadataChange is composition-scoped, ignored
      only LineScopedMutation entry targets line5
  
  [ElementDeletion(line5, 3), ElementDeletion(line7, 2, e)]    → null
    ↑ both line-scoped, different lines
  
  [MetadataChange(TITLE), FontChange(LYRICS)]                  → null
    ↑ no LineScopedMutation entries
  
  []                                                           → null
    ↑ empty list
  ```
  Algorithm: filter to entries that implement `LineScopedMutation`. If filtered list is empty, return `null`. If all filtered entries return the same `Line`, return it. Otherwise return `null`. Composition-scoped mutations are ignored, not blockers.

- `boolean hasMutationOf(Class<? extends Mutation>)` — true if the list contains any instance of the given subclass.

### Range Cleanup via Template Method

`RangeElement` gains:

```java
public boolean isInvalidatedBy(List<StaffElement> deletedElements) {
    return deletedElements.contains(getAnchorElement())
        || deletedElements.contains(getEndElement());
}
```

The default implementation handles every current `RangeElement` subclass (Tie, Trill, Tuplet, Hairpin, Crescendo, Diminuendo, Ending). Subclasses override only if they need special logic.

`Line.removeElement(int)` and `Line.removeRange(int, int)` both call `rangeElements.removeIf(r -> r.isInvalidatedBy(deletedList))` after mutating `elements`, inside the same `applyChange()` invocation. The cleanup is atomic with the deletion, in the same bracket, in the same eventual undo entry.

(There is no existing imperative range-adjustment logic to migrate. The current code only shifts legacy `IntervalSets` via `Line.shiftIntervals()`; `RangeElement` instances have no adjustment code at all today. This phase adds missing behavior.)

### `withModification` Helper

```java
public void withModification(Runnable body) {
    beginModification();
    try {
        body.run();
    } finally {
        endModification();
    }
}
```

Phase 4 uses `withModification` for all multi-mutation operations. `beginModification` / `endModification` remain available as the lower-level API for cases where a `Runnable` is not ergonomic. The helper eliminates the silent-failure mode where a forgotten `endModification` strands the depth counter.

### Line Delegation

`Line` gets `applyChange(Mutation, Runnable)` and `withModification(Runnable)` methods that delegate to its parent `Composition`. This is convenient for code that has a `Line` reference but not the `Composition`.

### Strict Bracket Enforcement

`applyChange()` throws `IllegalStateException` if `modificationDepth == 0`. Rationale:

- A self-bracketing fallback would mask bugs where a caller forgot to bracket a multi-mutation operation, silently splitting it into N single-mutation events.
- Test coverage is the safety net: every mutation site is exercised by unit tests in Phase 7. A missed bracket surfaces as a test failure, not a production crash.
- Production code that *did* miss a bracket (despite testing) would fail loudly via the existing `MessageCenter.handlePublicationError` path — same as any other unhandled mutation site bug.

---

## ✅ Phase 1: Mutation Hierarchy

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** --

### Tasks

1. Create package `songscribe.message.mutation` with `package-info.java` (`@NullMarked`).
2. Create `Mutation` sealed interface (pure marker, no methods).
3. Create `LineScopedMutation` interface with `Line getLine()`.
4. Create `ElementField` enum. Leave it empty for now; Phase 4b populates it with whatever fields `NoteDragHandler` actually changes (e.g., `PITCH`, `NOTE_TYPE`).
5. Create element mutation records:
   - `ElementInsertion(Line line, int index, StaffElement element)`
   - `ElementDeletion(Line line, int index, StaffElement deletedElement)`
   - `ElementRangeDeletion(Line line, int from, int to, List<StaffElement> deletedElements)`
   - `ElementModification(Line line, int index, EnumSet<ElementField> fields, StaffElement beforeElement)`
6. Create structural mutation records:
   - `LineInsertion(int lineIndex, Line line)`
   - `LineDeletion(int lineIndex, Line deletedLine)`
7. Create property field enums: `MetadataField`, `FontField`, `LayoutField`, `LyricsField`. Populate with values mirroring today's setter coverage.
   - `LyricsField` values: `MAIN`, `UNDER`, `BANGLA`, `TRANSLATED` (mirrors `setLyrics` / `setUnderLyrics` / `setBanglaLyrics` / `setTranslatedLyrics`).
   - For `MetadataField` and `LayoutField`, document the expected runtime type of `oldValue`/`newValue` in each enum constant's javadoc (e.g. `TITLE` → `String`, `YEAR` → `Integer`, `LINE_WIDTH_SS` → `Double`).
8. Create property mutation records:
   - `MetadataChange(MetadataField field, @Nullable Object oldValue, @Nullable Object newValue)`
   - `FontChange(FontField field, Font oldFont, Font newFont)`
   - `LayoutChange(LayoutField field, @Nullable Object oldValue, @Nullable Object newValue)`
   - `LyricsChange(LyricsField field, String oldText, String newText)`

### Key files

- `src/main/java/songscribe/message/mutation/` (new package, all files)

---

## ⏳ Phase 2: Notification Refactor

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phase 1

### Tasks

1. Refactor `CompositionDidChangeNotification`:
   - Replace `EnumSet<ChangeType>` and `@Nullable Line` with `List<Mutation>` and `Composition`.
   - Add lazily-cached `@Nullable Line getLine()` per the semantics in Design Decisions (private `cachedLine` field + `cached` boolean).
   - Add `boolean hasMutationOf(Class<? extends Mutation>)`.
2. Create `DocumentDidLoadNotification` extending `Message`, carrying `Composition`.
3. Delete `ChangeType` enum from `CompositionDidChangeNotification`.

### Key files

- `src/main/java/songscribe/message/notification/CompositionDidChangeNotification.java` (refactor)
- `src/main/java/songscribe/message/notification/DocumentDidLoadNotification.java` (new)

---

## ✅ Phase 3a: Composition Mutation Audit

**Model:** Opus  <br>
**Status:** Done  <br>
**BlockedBy:** Phases 1, 2

### Purpose

Investigation-only phase. Produces an explicit list of every mutation site in `Composition.java` that Phase 3b must migrate. The goal is to ensure Phase 3b can run as a mechanical pass without missing outliers.

### Tasks

1. Find every call to `postChanged(...)` in `Composition.java`. For each, record:
   - Method name
   - Line number
   - The `ChangeType` being posted
   - Which Mutation type it should become (e.g., `addLine` → `LineInsertion`, `removeLine` → `LineDeletion`)
2. Find every call to `setModified(true)` in `Composition.java` that is **not** inside `mutateAndPost(...)`. For each, record method name + line number + intended Mutation type.
3. Find every call to `mutateAndPost(...)` in `Composition.java` and record the same info. (These are the well-known sites; Phase 3b migrates them mechanically.)
4. Cross-check: search for any other paths that mutate composition state without going through `mutateAndPost` or `setModified`. Possible candidates: direct field assignments in `loadFrom`, internal helpers.
5. Audit `Line.java` similarly: every call to `compositionWasModified()`, every direct mutation method (`addElement`, `removeElement`, `addRangeElement`, `removeRangeElement`, etc.).
6. Produce an audit report appended to this plan as a sub-section "Audit Results" before Phase 3b runs. The report is a table:

   | File | Method | Line | Current pattern | Target Mutation |
   |------|--------|------|-----------------|----------------|
   | Composition.java | addLine | 716 | setModified + postChanged(STRUCTURE) | LineInsertion |
   | Composition.java | removeLine | 751 | setModified + postChanged(STRUCTURE) | LineDeletion |
   | … | … | … | … | … |

### Deliverable

The "Audit Results" sub-section in this plan, complete enough that Phase 3b can be executed without further investigation.

### Key files

- `src/main/java/songscribe/music/Composition.java` (read only)
- `src/main/java/songscribe/music/Line.java` (read only)

### Audit Results

Line numbers below reflect the working tree at the time of the audit
(`Composition.java` = 1162 lines, `Line.java` = 1179 lines). Phase 3b should
re-locate sites by symbol if line numbers drift.

#### Composition.java — `mutateAndPost` setters (well-known)

| Method | Line | Current `ChangeType` | Target Mutation | Field constant | Notes |
|--------|------|----------------------|-----------------|----------------|-------|
| `setTempo(Tempo)` | 565 | CONTENT | `MetadataChange` | `MetadataField.TEMPO` | No early-return today; add one comparing `Objects.equals(tempo, this.tempo)` so the bracket stays empty when unchanged. Old value is the previous `Tempo` reference. |
| `setTitle(String)` | 569 | METADATA | `MetadataChange` | `MetadataField.TITLE` | Has early return. `applyTitle` runs `processText`, which can call `setModified(true)` (line 1113) — see "loadFrom + processText" finding below. |
| `setPlace(String)` | 577 | METADATA | `MetadataChange` | `MetadataField.PLACE` | No early return; add one. |
| `setYear(String)` | 581 | METADATA | `MetadataChange` | `MetadataField.YEAR` | No early return; add one. |
| `setMonth(int)` | 585 | METADATA | `MetadataChange` | `MetadataField.MONTH` | No early return; add one. Old value is `int`, box to `Integer`. |
| `setDay(int)` | 589 | METADATA | `MetadataChange` | `MetadataField.DAY` | Same as `setMonth`. |
| `setLyrics(String)` | 593 | LYRICS | `LyricsChange` | `LyricsField.MAIN` | No early return today (asymmetric vs. the other lyrics setters). |
| `setUnderLyrics(String)` | 597 | LYRICS | `LyricsChange` | `LyricsField.UNDER` | Has early return. |
| `setBanglaLyrics(String)` | 607 | LYRICS | `LyricsChange` | `LyricsField.BANGLA` | Has early return. |
| `setTranslatedLyrics(String)` | 617 | LYRICS | `LyricsChange` | `LyricsField.TRANSLATED` | Has early return. |
| `setFootnotes(String)` | 627 | METADATA | `MetadataChange` | **MISSING** `MetadataField.FOOTNOTES` | Has early return. **Phase 1 gap** — `MetadataField` has no `FOOTNOTES` constant. Phase 3b must add it (and document `String` as the expected runtime type). |
| `setUnofficialTranslation(boolean)` | 637 | METADATA | `MetadataChange` | **MISSING** `MetadataField.UNOFFICIAL_TRANSLATION` | No early return; add one. **Phase 1 gap** — Phase 3b must add this `MetadataField` constant (`Boolean` runtime type). |
| `setAttribution(String)` | 641 | METADATA | `MetadataChange` | `MetadataField.ATTRIBUTION` | Has early return. |
| `setNumber(String)` | 651 | METADATA | `MetadataChange` | `MetadataField.NUMBER` | No early return; add one. |
| `setDefaultKeyAccidentalCount(int)` | 655 | CONTENT | `MetadataChange` | `MetadataField.DEFAULT_KEY_ACCIDENTAL_COUNT` | No early return; add one. Note: today this is `CONTENT`, not `METADATA`; per Phase 1's `MetadataField` enum it lives in `MetadataChange`. |
| `setDefaultKeyType(KeyType)` | 659 | CONTENT | `MetadataChange` | `MetadataField.DEFAULT_KEY_TYPE` | Same as above. |
| `setTitleFont(Font)` | 665 | FONT | `FontChange` | `FontField.TITLE` | No early return; add one. `applyTitleFont` also recomputes `titleFontMetrics` — keep that side effect inside the mutator. |
| `setLyricsFont(Font)` | 669 | FONT | `FontChange` | `FontField.LYRICS` | Same. |
| `setAttributionFont(Font)` | 673 | FONT | `FontChange` | `FontField.ATTRIBUTION` | Same. |
| `setAnnotationFont(Font)` | 677 | FONT | `FontChange` | `FontField.ANNOTATION` | Same. |
| `setBanglaFont(Font)` | 681 | FONT | `FontChange` | `FontField.BANGLA` | Same. |
| `setFootnoteFont(Font)` | 685 | FONT | `FontChange` | `FontField.FOOTNOTE` | Same. |
| `setTopPaddingSs(double, boolean)` | 691 | LAYOUT | `LayoutChange` | `LayoutField.TOP_PADDING_SS` | No early return. The `setByUser` parameter is OR'd into `userSetTopPadding` inside `applyTopPaddingSs` — that side effect must remain inside the mutator. |
| `setAttributionStartYSs(double)` | 695 | LAYOUT | `LayoutChange` | `LayoutField.ATTRIBUTION_START_Y_SS` | No early return; add one. |
| `setRowHeightAdjustmentSs(double)` | 699 | LAYOUT | `LayoutChange` | `LayoutField.ROW_HEIGHT_ADJUSTMENT_SS` | No early return; add one. |
| `setLineWidthSs(double)` | 707 | LAYOUT | `LayoutChange` | `LayoutField.LINE_WIDTH_SS` | Has early return (`==` on `double`). |

#### Composition.java — Raw `setModified` + `postChanged` sites (not via `mutateAndPost`)

| Method | Lines | Current pattern | Target Mutation | Notes |
|--------|-------|-----------------|-----------------|-------|
| `addLine(int, Line)` | 717–749 (mutation at 746/748) | `setModified(true)` + `postChanged(STRUCTURE)` | `LineInsertion(lineIndex, line)` | The whole body must run inside `applyChange`. The `lineIndex` resolution from `InsertLineAction.ADD` and the `setKeyAccidentalCount`/`setKeyType`/`setTempoChangeYPosPx` follow-ups stay inside the mutator. |
| `removeLine(int)` | 751–756 (753, 755) | `setModified(true)` + `postChanged(STRUCTURE)` | `LineDeletion(index, deletedLine)` | Capture `var deletedLine = lines.get(index);` **before** removing so the mutation carries the reference. |

#### Composition.java — `@Handler` methods (use `mutateAndPost`)

These already wrap their work in a single `mutateAndPost` call. Phase 3b
converts them to `withModification` brackets containing one `applyChange` per
field set on the inbound update record (so a multi-field metadata update
yields a `DidChange` carrying multiple `MetadataChange` mutations).

| Method | Line | Current `ChangeType` | Target Mutations | Notes |
|--------|------|----------------------|------------------|-------|
| `lyricsDidChange(LyricsDidChangeNotification)` | 826 | LYRICS | One `LyricsChange` per non-null field on the update | After the bracket, `LyricsProcessor.spellLyrics(this)` continues to run **outside** the bracket (it currently sits after `mutateAndPost`). |
| `metadataDidChange(MetadataDidChangeNotification)` | 849 | METADATA | One `MetadataChange` per non-null field | Same shape — emit one per `if (update.getX() != null)` branch. |
| `fontDidChange(FontDidChangeNotification)` | 886 | FONT | One `FontChange` per non-null field | Same shape. |
| `tempoDidChange(TempoDidChangeNotification)` | 907 | CONTENT | `MetadataChange(MetadataField.TEMPO, oldTempo, newTempo)` | The handler currently mutates the existing `Tempo` instance in place via setters. Phase 3b should either (a) clone the old tempo for `oldValue` and emit one mutation after the in-place mutation, or (b) refactor to construct a fresh `Tempo` and assign. Option (a) is the smaller change. |
| `keySignatureDidChange(KeySignatureDidChangeNotification)` | 928 | CONTENT | When `lineIndex == null`: a `MetadataChange(DEFAULT_KEY_TYPE, ...)` and `MetadataChange(DEFAULT_KEY_ACCIDENTAL_COUNT, ...)`, plus one `LineKeyChange` (**MISSING**, see Gaps) per propagated line. When `lineIndex != null`: just the per-line mutation. | Largest handler to migrate. **Phase 1 gap** — there is no Mutation type for line key signature changes today. |
| `layoutDidChange(LayoutDidChangeNotification)` | 958 | LAYOUT | One `LayoutChange` per non-null field | Same shape as `metadataDidChange`. |

#### Composition.java — Direct field assignments outside the mutation system

| Site | Lines | Pattern | Disposition |
|------|-------|---------|-------------|
| Constructor `Composition()` | 198–221 | Direct field assignment (`tempo`, `defaultKeyAccidentalCount`, etc.) and direct `lines.add(initialLine)` | **Out of scope** — the composition is not yet observable. Comment at lines 209–210 already explains why the initial line is added directly rather than via `addLine`. Leave untouched. |
| Loading constructor `Composition(CompositionData)` | 228–232 | Calls `loadFrom(data)` | See `loadFrom`. |
| `loadFrom(CompositionData)` | 278–353 | Direct `apply*` calls; `this.tempo = data.tempo()`; `this.hasBeenDynamicallyLaidOut = ...`; `this.formatVersion = ...`; `modified = false` | **Out of scope** — load is not a user mutation. The notification is `DocumentDidLoadNotification`, posted by `Score.setComposition()` (Phase 4a). `loadFrom` must NOT open a modification bracket — Phase 3b's stricter `applyChange` would otherwise misclassify load as mutation. The existing comment at lines 350–352 already records the intent. |
| `documentWasSaved` handler | 264 | `setModified(false)` | **Out of scope** — clearing the dirty flag is not a mutation. Keep as-is. Phase 3b's `setModified(false)` path must remain unguarded by the bracket. |
| `processText(String)` | 1100–1118; `setModified` at 1113 | When STRIP_SHORT_A pref is on and the input contains `ă`/`Ă`, calls `setModified(true)` and returns the replaced text | **Pre-existing oddity.** `processText` is invoked from inside `apply*` helpers (`applyTitle`, `applyLyrics`, `applyUnderLyrics`), which run inside `mutateAndPost`. The redundant `setModified(true)` is harmless during user edits (the surrounding `mutateAndPost` already sets it) and is masked under `loadFrom` (which unconditionally clears `modified` at line 348). **Phase 3b action:** delete the `setModified(true)` call at line 1113. With the new strict bracket, `processText` running outside a bracket from anywhere unexpected would otherwise leave `modified` true with no mutation accumulated — a silent state leak. The surrounding setter is the right place to mark dirty. |
| `setHasBeenDynamicallyLaidOut(boolean)` | 809 | Direct field assignment | **Out of scope** — internal/IO state, no observers. |
| `setFormatVersion(int)` | 820 | Direct field assignment | **Out of scope** — set by FormatMigrator after load; not a user mutation. |
| `setComposition` (called by `Line.setComposition`) | n/a | Wiring | Wiring, not a mutation. |

#### Line.java — `compositionWasModified()` call sites

`compositionWasModified()` (lines 249–256) currently maps every line-side
mutation to `ChangeType.CONTENT` with `this` as the line. Phase 3b/4a must
replace each call with an `applyChange` carrying a more specific Mutation.

| Method | Line | Current pattern | Target Mutation | Notes |
|--------|------|-----------------|-----------------|-------|
| `setKeyAccidentalCount(int)` | 162–165 | `compositionWasModified()` then assign | **MISSING** `LineKeyChange` (or extend `MetadataChange` to be line-scoped) | **Phase 1 gap.** See Gaps section. Today this is bypassed by `keySignatureDidChange` which calls it inside its own `mutateAndPost`, so the redundant inner `compositionWasModified` is absorbed by the bracket. |
| `setKeyType(@Nullable KeyType)` | 171–174 | Same | **MISSING** `LineKeyChange` | Same. |
| `addElement(StaffElement)` (no-index) | 176–181 | `compositionWasModified()` after `elements.add(element)` | `ElementInsertion(this, elements.size() - 1, element)` | Phase 4a Task 1 only mentions `addElement(int, StaffElement)`. This no-index overload also needs migration; capture `var index = elements.size();` before `elements.add(element)`. |
| `addElement(int, StaffElement)` | 183–189 | `compositionWasModified()` at end | `ElementInsertion(this, index, element)` | Already covered in Phase 4a Task 1. The `shiftIntervals` call at 186 stays inside the mutator. |
| `setElement(int, StaffElement)` | 191–196 | `compositionWasModified()` at start | `ElementModification(this, index, fields, beforeClone)` (replace-whole-element semantics) | **Phase 1 gap** — there is no `ElementField` constant that represents "the entire element was replaced." Options: (a) add `ElementField.WHOLE_ELEMENT`; (b) emit `ElementDeletion` + `ElementInsertion` at the same index. Phase 3b must decide and document. The `attachInitialTempoIfNeeded` side effect stays in the mutator. |
| `replaceElementQuietly(int, StaffElement)` | 202–205 | No notification at all (intentional) | None — caller-bracketed | The "quietly" contract means callers must already be inside an `applyChange` for the surrounding batch operation. Under strict bracket enforcement (Phase 3b), this method's quietness is fine because it does not call `applyChange`. **However:** all current callers of `replaceElementQuietly` should be audited (during Phase 4a or 4b) to confirm they sit inside a bracket. Grep target: `replaceElementQuietly`. |
| `removeElement(int)` | 243–247 | `compositionWasModified()` then `elements.remove(index)` | `ElementDeletion(this, index, deleted)` | Already covered in Phase 4a Task 1. Capture `var deleted = elements.get(index);` **before** the remove call. |
| `setTempoChangeYPosPx(int)` | 343–346 | `compositionWasModified()` after assign | **MISSING** `LineYPosChange` (or `LayoutChange` extended to line scope) | Deprecated — only legacy documents touch it. **Defer:** wrap as a no-op `applyChange` is not feasible without a Mutation type. Two options: (1) introduce `LineLegacyYPosChange` covering all four deprecated Y-pos setters and one `lyricsYPosSs` setter; (2) make these setters bypass `applyChange` and call a new `Composition.markLegacyMutation()` helper that just sets dirty + posts an empty `DidChange` (worse). **Recommendation:** add a single `LineLayoutChange(Line line, LineLayoutField field, Object oldValue, Object newValue)` Mutation in Phase 3b alongside a `LineLayoutField` enum covering: `TEMPO_CHANGE_Y_POS_PX` (deprecated), `BEAT_CHANGE_Y_POS_PX` (deprecated), `FIRST_SECOND_ENDING_Y_POS_PX` (deprecated), `TRILL_Y_POS_PX` (deprecated), `LYRICS_Y_POS_SS`, `ELEMENT_DIST_CHANGE_RATIO`. This is a Phase 1 gap; flagging here. |
| `setBeatChangeYPosPx(int)` | 360–363 | Same | **MISSING** — see above | Same. Deprecated. |
| `setLyricsYPosSs(double)` | 369–372 | Same | **MISSING** `LineLayoutChange(LYRICS_Y_POS_SS, ...)` | Active code path. |
| `setFirstSecondEndingYPosPx(int)` | 386–389 | Same | **MISSING** — see above | Deprecated. |
| `setTrillYPosPx(int)` | 403–406 | Same | **MISSING** — see above | Deprecated. |
| `mulElementDistChange(float)` | 408–411 | Same | **MISSING** `LineLayoutChange(ELEMENT_DIST_CHANGE_RATIO, ...)` | Active. The "old value" is `elementDistChangeRatio / ratio`. |
| `addRangeElement(RangeElement)` | 522–526 | `compositionWasModified()` after add | **MISSING** `RangeElementAddition(Line, RangeElement)` (or accept generic `LineScopedMutation` placeholder) | **Phase 1 gap** — the design doc explicitly defers range mutation types ("Range mutations are intentionally deferred until a subscriber needs them"). For Phase 3b to migrate this site, it either needs to (a) create the mutation type now, or (b) leave `addRangeElement` calling a stub Mutation. **Recommendation:** create `RangeElementAddition(Line, RangeElement)` and `RangeElementRemoval(Line, RangeElement)` as `LineScopedMutation` records during Phase 3b. The "until a subscriber needs them" rationale is overridden because Phase 3b will not compile if `addRangeElement` cannot route through `applyChange`. |
| `removeRangeElement(RangeElement)` | 534–543 | Sets parent to null then `compositionWasModified()` if removed | **MISSING** `RangeElementRemoval(Line, RangeElement)` | Same. |

#### Line.java — Other observations

- `removeInterval(int, int)` (line 445), `pasteIntervals` (line 460), and the
  legacy `IntervalSet` accessors (`getBeamings`, `getTies`, etc.) **return
  mutable references** that callers can modify directly without notifying the
  composition. This is a pre-existing escape hatch for the legacy intervals
  pipeline. Phase 3a explicitly does **not** scope migration of legacy
  IntervalSet plumbing — the design doc states "There is no existing
  imperative range-adjustment logic to migrate." Leave these untouched in
  Phase 3b/4. They will eventually disappear with the IntervalSet → RangeElement
  migration.
- `setComposition(Composition)` (line 154) is wiring, not a mutation. Leave
  untouched.

#### Phase 1 Gaps Surfaced by the Audit

Phase 3b cannot run as a purely mechanical pass because the following
Mutation types and field constants are missing from Phase 1's package:

1. **`MetadataField.FOOTNOTES`** (`String`) — required by `setFootnotes`.
2. **`MetadataField.UNOFFICIAL_TRANSLATION`** (`Boolean`) — required by
   `setUnofficialTranslation`.
3. **`LineKeyChange(Line, KeyField, Object oldValue, Object newValue)`**
   record + `KeyField { ACCIDENTAL_COUNT, KEY_TYPE }` enum — required by
   `Line.setKeyAccidentalCount` and `Line.setKeyType`. Implements
   `LineScopedMutation`.
4. **`LineLayoutChange(Line, LineLayoutField, Object oldValue, Object newValue)`**
   record + `LineLayoutField { TEMPO_CHANGE_Y_POS_PX (deprecated),
   BEAT_CHANGE_Y_POS_PX (deprecated), FIRST_SECOND_ENDING_Y_POS_PX
   (deprecated), TRILL_Y_POS_PX (deprecated), LYRICS_Y_POS_SS,
   ELEMENT_DIST_CHANGE_RATIO }` enum — required by the six legacy/active
   line-property setters. Implements `LineScopedMutation`.
5. **`RangeElementAddition(Line, RangeElement)`** and
   **`RangeElementRemoval(Line, RangeElement)`** records — required by
   `Line.addRangeElement` and `Line.removeRangeElement`. Both implement
   `LineScopedMutation`. The design doc's "deferred until a subscriber needs
   them" guidance is overridden because Phase 3b's strict bracket enforcement
   leaves no mechanical path to migrate these sites otherwise.
6. **`ElementField.WHOLE_ELEMENT`** *(or)* a documented decision that
   `Line.setElement(int, StaffElement)` emits an
   `ElementDeletion`+`ElementInsertion` pair instead of `ElementModification`
   — required by `Line.setElement`. Phase 3b must pick one before touching
   `setElement`.

All six items must be added to the `songscribe.message.mutation` package
**before** the bulk Phase 3b migration begins, as a small pre-step inside
Phase 3b. They are listed here so Phase 3b's executor does not stop
mid-migration to design these types from scratch.

#### Sites that Phase 3b/4 must explicitly leave alone

These touch composition state but are deliberately exempt from
`applyChange` bracketing:

| Site | Reason |
|------|--------|
| `Composition()` constructor (lines 198–221) | Composition not yet installed; no observers. |
| `Composition(CompositionData)` constructor (lines 228–232) | Same. |
| `loadFrom(CompositionData)` (lines 278–353) | Document load. `Score.setComposition` posts `DocumentDidLoadNotification` after install (Phase 4a). |
| `documentWasSaved` handler (line 264) | `setModified(false)` is a clear, not a mutation. |
| `setModified(false)` from save/load paths | Same. |
| `setHasBeenDynamicallyLaidOut(boolean)` (line 809) | Internal IO state. |
| `setFormatVersion(int)` (line 820) | Set by FormatMigrator post-load. |
| `Line.setComposition(Composition)` (line 154) | Wiring. |
| `Line.replaceElementQuietly` (lines 202–205) | Intentionally quiet — caller is responsible for the surrounding bracket. Phase 4 must audit existing callers for compliance. |
| `Line.removeInterval` / `pasteIntervals` / mutable IntervalSet accessors | Legacy intervals plumbing; out of scope for this issue. |

---

## ✅ Phase 3b: Composition Bracket & applyChange

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** Phase 3a

### Tasks

0. **Close the Phase 1 gaps surfaced by the Phase 3a audit before touching any mutation site.** The audit's "Phase 1 Gaps Surfaced by the Audit" sub-section enumerates exactly what to add to `songscribe.message.mutation`:

   1. Add `MetadataField.FOOTNOTES` (`String`) and `MetadataField.UNOFFICIAL_TRANSLATION` (`Boolean`) to the existing enum, with javadoc on each constant documenting the runtime type.
   2. Create `KeyField { ACCIDENTAL_COUNT, KEY_TYPE }` enum and `LineKeyChange(Line line, KeyField field, @Nullable Object oldValue, @Nullable Object newValue)` record implementing `LineScopedMutation`.
   3. Create `LineLayoutField { TEMPO_CHANGE_Y_POS_PX, BEAT_CHANGE_Y_POS_PX, FIRST_SECOND_ENDING_Y_POS_PX, TRILL_Y_POS_PX, LYRICS_Y_POS_SS, ELEMENT_DIST_CHANGE_RATIO }` enum (mark the four `_PX` constants `@Deprecated` to mirror the line setters) and `LineLayoutChange(Line line, LineLayoutField field, @Nullable Object oldValue, @Nullable Object newValue)` record implementing `LineScopedMutation`.
   4. Create `RangeElementAddition(Line line, RangeElement element)` and `RangeElementRemoval(Line line, RangeElement element)` records implementing `LineScopedMutation`. (This overrides the design doc's "deferred until a subscriber needs them" guidance — strict bracketing leaves no other migration path.)
   5. **Decision for `Line.setElement(int, StaffElement)`:** emit `ElementDeletion(line, index, oldElement)` followed by `ElementInsertion(line, index, newElement)` inside a single `withModification` bracket. Do **not** add `ElementField.WHOLE_ELEMENT`. Rationale: the deletion+insertion pair already expresses the semantics precisely with existing types, and `ElementModification` is reserved for in-place field changes (the only current emitter is `NoteDragHandler`'s pitch drag in Phase 4b).

   All five sub-tasks must compile cleanly before Task 1 begins. Add the new types to the existing `package-info.java`'s null-marked package; no new package needed.

1. Replace `mutateAndPost(ChangeType, Runnable)` with `applyChange(Mutation, Runnable)` on `Composition`:
   - Throws `IllegalStateException` if `modificationDepth == 0`.
   - Runs the `Runnable`, then accumulates the `Mutation`.
2. Replace `EnumSet<ChangeType> accumulatedChangeTypes` with `List<Mutation> accumulatedMutations`. Remove `accumulatedLine` (no longer needed; `getLine()` derives from the list).
3. Update `endModification()` to post `CompositionDidChangeNotification` with the accumulated `List<Mutation>`.
4. Add `withModification(Runnable)` helper that wraps `beginModification` / `endModification` in `try/finally`.
5. Remove `postChanged(ChangeType)` and `postChanged(ChangeType, Line)` (their callers move to `applyChange`).
6. Add `applyChange(Mutation, Runnable)` and `withModification(Runnable)` on `Line` that delegate to the parent `Composition`.
7. Migrate every site identified in the Phase 3a audit results to use `applyChange()` with the listed Mutation. Work the audit tables top-to-bottom: `mutateAndPost` setters → raw `setModified+postChanged` sites → `@Handler` methods → `Line.java` `compositionWasModified` sites. **Do not touch the sites listed under "Sites that Phase 3b/4 must explicitly leave alone" in the Phase 3a audit results** — those (constructors, `loadFrom`, `documentWasSaved`, `setHasBeenDynamicallyLaidOut`, `setFormatVersion`, `Line.setComposition`, `Line.replaceElementQuietly`, legacy IntervalSet plumbing) must remain bracket-free. Also: delete the redundant `setModified(true)` call inside `processText` (line 1113 of `Composition.java` at audit time) per the audit's recommendation; the surrounding setter is the right place to mark dirty.
8. Update internal Composition setters (`setTitle`, `setLyrics`, `setTempo`, etc.) to use `withModification` + `applyChange` with the appropriate property mutation. **Each setter must capture the old value before mutating** so the mutation record carries it. Standard pattern:

   ```java
   public void setTitle(String newTitle) {
       if (Objects.equals(this.title, newTitle)) return;
       var oldTitle = this.title;
       withModification(() -> applyChange(
           new MetadataChange(MetadataField.TITLE, oldTitle, newTitle),
           () -> this.title = newTitle
       ));
   }
   ```

   The early return on unchanged value is preserved (no mutation, no notification).
9. Add an ASCII javadoc diagram on `Composition.applyChange()` showing the bracket lifecycle:

   ```
   //   withModification(() -> {                              ┐
   //     │                                                    │
   //     ├─ applyChange(mutation₁, mutator₁)                 │ caller's
   //     │     ├─ throws if depth == 0                       │ bracket
   //     │     ├─ mutator₁.run()                             │
   //     │     └─ accumulatedMutations.add(mutation₁)        │
   //     │                                                    │
   //     ├─ applyChange(mutation₂, mutator₂)                 │
   //     │     └─ ...                                         │
   //     │                                                    │
   //   })  // bracket closes                                  │
   //     ├─ depth → 0                                         │
   //     ├─ push undo entry (future, #14)                     │
   //     └─ post CompositionDidChangeNotification(accumulated)┘
   ```

### Key files

- `src/main/java/songscribe/music/Composition.java`
- `src/main/java/songscribe/music/Line.java`

---

## ✅ Phase 4a: Migrate Simple Callers

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** Phase 3b

### Scope

Mechanical migrations: callers whose mutation paths are localized and where the change is "wrap in `applyChange()` / `withModification()`." Excludes `NoteDragHandler` and `GraceModeManager` (their state machines are handled in Phase 4b).

### Tasks

1. **Migrate `Line` mutation methods to wrap in `applyChange`:**
   - `Line.addElement(int index, StaffElement element)` → wraps body in `composition.applyChange(new ElementInsertion(this, index, element), () -> { ... })`.
   - `Line.removeElement(int index)` → captures `var deleted = elements.get(index);`, then wraps body in `composition.applyChange(new ElementDeletion(this, index, deleted), () -> { ... })`. Move the `compositionWasModified()` logic into the mutator. Remove `compositionWasModified()` helper.
   - **Add `Line.removeRange(int from, int to)`** → wraps body in `composition.applyChange(new ElementRangeDeletion(this, from, to, deletedElementsList), () -> { elements.subList(from, to+1).clear(); shiftIntervals(intervalSets, from, -(to-from+1)); })`. Capture `deletedElementsList` from `elements.subList(from, to+1)` before clearing.
   - Range cleanup hook (Phase 6) lives **inside** these mutators, after the elements list is updated.
2. **Add bracketing to multi-mutation operations:**
   - `ScoreMessageCoordinator.handleDelete()` — replace the per-element loop with a single `Line.removeRange(selectionBegin, selectionEnd)` call. The host-note + paired-grace-note case (`deleteNote`) keeps using `removeElement(int)` because it removes non-contiguous indices.
   - `ScoreMessageCoordinator.handleCut()` — wrap in `withModification` (calls handleCopy then handleDelete; only handleDelete mutates).
   - Each dialog's `setData()` method — wrap in `composition.withModification(() -> { ... })` so multi-property changes coalesce into a single `DidChange`. Audit `BaseDialog` subclasses; representative examples: `CompositionSettingsDialog.TextTab/MusicTab/FontTab.setData()`.
3. Migrate `PreviewElementManager` — replace direct posting with `composition.applyChange()`. The mutation site is one place (around line 501 of the file).
4. Migrate `Score.setComposition()` — replace `ChangeType.FULL` posting with `MessageCenter.post(new DocumentDidLoadNotification(composition))`.
5. Migrate `CompositionData` and any other code referencing `ChangeType.FULL` to `DocumentDidLoadNotification`.

### Key files

- `src/main/java/songscribe/music/Line.java`
- `src/main/java/songscribe/ui/component/ScoreMessageCoordinator.java` (handleDelete / handleCut only)
- `src/main/java/songscribe/ui/component/score/PreviewElementManager.java`
- `src/main/java/songscribe/ui/component/Score.java`
- `src/main/java/songscribe/message/CompositionData.java`
- `src/main/java/songscribe/ui/dialog/CompositionSettingsDialog.java` (and other `BaseDialog` subclasses)

---

## ✅ Phase 4b: Migrate Complex Callers

**Model:** Opus  <br>
**Status:** Done  <br>
**BlockedBy:** Phase 4a

### Scope

`NoteDragHandler` and `GraceModeManager` both have multi-step state machines with conditional mutation paths. The migration requires understanding the existing state flow before mechanically wrapping mutations — easy to introduce subtle bugs if the model doesn't fully grasp the drag finalize / grace mode entry-exit semantics.

### Tasks

1. **`NoteDragHandler` migration:**
   - Read the entire drag state machine (`handlePress`, `handleDrag`, `handleRelease`, helpers).
   - Identify every code path that mutates the composition: pitch changes, host-note + paired grace note removal cases (around lines 320–340), drag finalize at line 342.
   - **Pitch / element field changes fire `ElementModification`.** Before mutating the dragged element, capture `var beforeClone = element.clone();` and pass it to the mutation: `composition.applyChange(new ElementModification(line, index, fields, beforeClone), () -> { /* mutate the element */ });`. Use the appropriate `ElementField` enum values for `fields` (Phase 1 leaves the enum empty; populate `PITCH` or whatever values the drag actually changes during this migration).
   - **Element removal paths fire `ElementDeletion`** via `line.removeElement(index)` (which Phase 4a already migrated).
   - Wrap the entire `handleRelease` finalize block in `composition.withModification(() -> { ... })` so multi-mutation drags coalesce into a single `DidChange`.
   - Remove direct `setModified` / `MessageCenter.post(new CompositionDidChangeNotification(...))` calls.
   - **Note:** This is the first emitter of `ElementModification`. Without this migration step, `ElementModification` would be a type with no caller.
2. **`GraceModeManager` migration:**
   - Read the grace mode entry/exit logic (`enterGraceMode`, `exitGraceMode`, `cancelGraceMode`, `finalizeGraceMode`).
   - Identify mutation paths: grace note add (line ~470), grace note finalize (line ~557), cancel paths that remove orphan host notes (lines 522–525) or grace notes (lines 550–554).
   - Wrap each mutation in `applyChange()` with the appropriate Mutation type. Wrap multi-step finalize/cancel sequences in `composition.withModification(() -> { ... })`.
   - Preserve the existing `GraceModeStateDidChangeNotification` posts — those are unrelated to composition mutations.
3. After both migrations, run `./scripts/test.sh unit` and a manual `./scripts/crun.sh` smoke test (drag a note, enter/exit grace mode, finalize a grace note) to catch regressions.

### Key files

- `src/main/java/songscribe/ui/component/score/NoteDragHandler.java`
- `src/main/java/songscribe/ui/edit/GraceModeManager.java`

---

## ✅ Phase 5: Migrate Subscribers

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** Phase 4b

### Tasks

1. Migrate `ScoreMessageCoordinator.compositionDidChange()` — replace `hasChangeType()` checks with `instanceof` / `hasMutationOf()` checks on Mutation subclasses. Use pattern matching where it improves clarity.
2. Migrate `LyricsPanel.compositionDidChange()`:
   - Replace `hasChangeType(LYRICS)` with `n.hasMutationOf(LyricsChange.class)` (the `LyricsChange` mutation type was added to Phase 1).
   - Replace `hasChangeType(FULL)` handler logic with a separate `@Handler` for `DocumentDidLoadNotification`.
3. Migrate `Actions.ResetHandler.compositionDidChange()` — replace `hasChangeType(FULL)` with a `@Handler` for `DocumentDidLoadNotification`. Rename method to `documentDidLoad`.
4. Migrate `UIAction.compositionDidChange()` and `getRelevantChangeTypes()` — replace `EnumSet<ChangeType>` filtering with Mutation-based filtering. Subclasses that override `getRelevantChangeTypes()` migrate accordingly. Add `@Handler` for `DocumentDidLoadNotification` if the action needs to react to document loads (most do, since `FULL` was treated as a catch-all).
5. Migrate `ToggleNotationAction.compositionDidChange()` — same treatment as `UIAction`.
6. `MainFrame.compositionDidChange()` — currently has no filter and just calls `updateTitle()`. Keep as-is (the method body doesn't depend on change type), but the parameter type and signature change to match the refactored notification.
7. Search for any other `ChangeType` references and migrate them.

### Key files

- `src/main/java/songscribe/ui/component/ScoreMessageCoordinator.java`
- `src/main/java/songscribe/ui/component/LyricsPanel.java`
- `src/main/java/songscribe/ui/action/Actions.java`
- `src/main/java/songscribe/ui/action/UIAction.java`
- `src/main/java/songscribe/ui/action/ToggleNotationAction.java`
- `src/main/java/songscribe/ui/component/MainFrame.java`

---

## ✅ Phase 6: Range Element Template Method

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** Phase 4a

### Note on existing logic

The original plan included a task to "move existing imperative range adjustment logic for Endings" into the new template method. **Code exploration during plan review found there is no such existing logic** — the current code only adjusts `IntervalSets` (legacy ties, beams, tuplets, crescendos, diminuendos) via `Line.shiftIntervals()`. `RangeElement` instances (Endings and the new range types) store element references and have no current adjustment code at all. This phase therefore *adds* missing behavior; nothing needs to be migrated from elsewhere.

### Tasks

1. Add `boolean isInvalidatedBy(List<StaffElement> deletedElements)` to `RangeElement` with the default implementation:

   ```java
   public boolean isInvalidatedBy(List<StaffElement> deletedElements) {
       return deletedElements.contains(getAnchorElement())
           || deletedElements.contains(getEndElement());
   }
   ```

2. Hook the cleanup into `Line.removeElement(int)` and `Line.removeRange(int, int)`. After mutating the `elements` list, call:

   ```java
   rangeElements.removeIf(r -> r.isInvalidatedBy(deletedList));
   ```

   (where `deletedList` is `List.of(deletedElement)` for single deletes or the captured sublist for range deletes).

3. Read each `RangeElement` subclass (`Tie`, `Trill`, `Tuplet`, `Hairpin` and its subclasses `Crescendo` / `Diminuendo`, `Ending`) and confirm the default implementation is sufficient. The default ("anchor or end is in the deleted set") is the universal correctness condition for any range that spans two elements; in practice no override should be needed. If any subclass requires special behavior, override with a comment explaining why.

4. Add an ASCII javadoc diagram on `Line.removeRange()` showing the cleanup hook:

   ```
   //  removeRange(from, to)
   //    └─ composition.applyChange(ElementRangeDeletion, () -> {
   //         ├─ var deletedElements = List.copyOf(elements.subList(from, to+1));
   //         ├─ elements.subList(from, to+1).clear();
   //         ├─ shiftIntervals(from, -(to-from+1));
   //         └─ rangeElements.removeIf(r -> r.isInvalidatedBy(deletedElements));
   //       });
   ```

### Key files

- `src/main/java/songscribe/ui/layout/RangeElement.java`
- `src/main/java/songscribe/ui/layout/Ending.java` (only if it needs an override)
- `src/main/java/songscribe/music/Line.java` (cleanup hook)

---

## ✅ Phase 7a: Tests — Mutation Hierarchy & Notifications

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** Phases 1, 2

Self-contained tests for the mutation records and notification classes. No
`Composition` instance, no `MessageCenter` mocking — just construct records
and notifications directly and assert on their public surface.

### Tasks

1. **Mutation hierarchy** — unit tests for `Mutation` subclass construction and field access
   (one test per record). Element mutations, line-level mutations, structural
   mutations, property mutations, range-element mutations.
2. **`LineScopedMutation` interface membership** — verify which mutation kinds
   implement `LineScopedMutation` and which do not.
3. **`CompositionDidChangeNotification` convenience methods:**
   - `getLine()` edge cases:
     - Empty mutation list → null
     - All composition-scoped mutations → null
     - Single line-scoped mutation → that line
     - Multiple line-scoped mutations on the same line → that line
     - Multiple line-scoped mutations on different lines → null
     - Mix of line-scoped (one line) + composition-scoped → that line
   - `getLine()` repeated calls return the cached result (same instance).
   - `hasMutationOf(Class)` — true positives, true negatives.
   - `getMutations()` returns an immutable copy (mutating the source list
     does not affect the notification).
4. **`DocumentDidLoadNotification`** — verify it carries the supplied
   composition. Full `Score.setComposition()` integration is deferred (Score
   has too many UI dependencies for a unit test).

### Key files

- `src/test/java/songscribe/message/mutation/MutationRecordsTest.java` (new)
- `src/test/java/songscribe/message/notification/CompositionDidChangeNotificationTest.java` (new)
- `src/test/java/songscribe/message/notification/DocumentDidLoadNotificationTest.java` (new)

---

## ✅ Phase 7b: Tests — Composition Bracket & Setters

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** Phase 7a

Tests covering the modification-bracket lifecycle and the migrated property
setters on `Composition`. Both groups need `MessageCenter` mocked via
`MockedStatic` so that posted notifications can be captured without a real
mbassador dispatch.

**Pattern for these tests:** construct the `Composition` *before* opening the
`MockedStatic<MessageCenter>` so the constructor's bus interactions go to the
real (unobserved) bus. Inside each test, capture all `MessageCenter.post(...)`
calls and filter to `CompositionDidChangeNotification`.

### Tasks

1. **`applyChange()` lifecycle:**
   - Inside a bracket: mutator runs, mutation is accumulated, single `DidChange`
     fires at bracket close.
   - Outside a bracket: throws `IllegalStateException`, mutator does not run,
     no notification posted.
   - Nested brackets via `withModification`: only the outermost close fires
     `DidChange` and it carries all accumulated mutations in order.
   - Nested brackets via `beginModification()` / `endModification()` pairs:
     same as above.
2. **`withModification` helper:**
   - Runs the body once.
   - `endModification` runs even if the body throws (verified by confirming
     that a follow-up `applyChange` outside the bracket throws
     `IllegalStateException`, proving the depth counter was unwound).
   - Empty body posts no notification.
   - Notification carries the same `Composition` instance.
3. **Composition setters** — for each property setter (`setTitle`, `setPlace`,
   `setYear`, `setMonth`, `setDay`, `setAttribution`, `setNumber`, `setTempo`,
   `setLyrics`, `setUnderLyrics`, `setBanglaLyrics`, `setTranslatedLyrics`,
   `setFootnotes`, `setUnofficialTranslation`, `setDefaultKeyAccidentalCount`,
   `setDefaultKeyType`, font setters, layout setters):
   - Sets the field to a known starting value, then to a new value.
   - Verifies the resulting `MetadataChange` / `LyricsChange` / `FontChange` /
     `LayoutChange` mutation carries the correct `oldValue` and `newValue`.
   - Verifies a single `CompositionDidChangeNotification` is posted.
   - Verifies the early-return path: calling the setter with the same value
     posts no notification.
   - Group related setters (e.g. all string metadata setters, all font setters)
     into `@Nested` classes for readability.

### Key files

- `src/test/java/songscribe/music/CompositionBracketTest.java` (new)
- `src/test/java/songscribe/music/CompositionSetterMutationTest.java` (new)

---

## ⏳ Phase 7c: Tests — Line Mutations & Range Cleanup

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phase 7b

Tests for line-level structural mutations, the new `Line.removeRange` operation,
and the `RangeElement.isInvalidatedBy` template method (including end-to-end
cleanup behavior).

### Tasks

1. **`addLine` / `removeLine`** — fire `LineInsertion` / `LineDeletion` via
   `applyChange`. `LineDeletion.deletedLine` matches the removed line;
   `LineInsertion.line` matches the added line; both carry the correct index.
2. **`Line.removeElement` end-to-end** — verify it fires a single
   `ElementDeletion` with the correct index and `deletedElement` reference,
   and that `rangeElements.removeIf` runs after the mutation.
3. **`Line.removeRange` end-to-end:**
   - Fires a single `ElementRangeDeletion` with the correct `from`, `to`,
     and `deletedElements` list.
   - The element list is shrunk by exactly `to - from + 1` entries.
   - Interval sets are shifted by the correct amount (verifiable indirectly
     via observable `IntervalSet` content after the call).
   - `rangeElements.removeIf` runs after the mutation.
4. **`RangeElement.isInvalidatedBy` parameterized:**
   - Anchor in deleted set → invalidated.
   - End in deleted set → invalidated.
   - Both anchor and end in deleted set → invalidated.
   - Anchor and end outside the deleted set, deleted set falls between
     them → not invalidated (default impl is identity-based).
   - Deleted set entirely outside the range → not invalidated.
   - Cover the default impl on `Tie`, `Trill`, `Tuplet`, `Crescendo`,
     `Diminuendo`, and `Ending`. No subclass currently overrides.
5. **End-to-end range cleanup:** create a `Line` with elements `[e0..e9]`,
   add an `Ending(e3, e7)`, call `line.removeRange(2, 5)`, verify the
   `Ending` is removed from `rangeElements` because its anchor (`e3`) was
   among the deleted elements. Add a complementary case where the deleted
   range falls entirely outside the ending and the ending survives.

### Key files

- `src/test/java/songscribe/music/LineMutationTest.java` (new)
- `src/test/java/songscribe/ui/layout/RangeElementInvalidationTest.java` (new)

---

## ⏳ Phase 7d: Tests — Coordinator, Dialog, Drag

**Model:** Sonnet  <br>
**Status:** Done  <br>
**BlockedBy:** Phase 7c

End-to-end and integration tests for the production code paths that drive
mutations from user actions: the delete coordinator, the metadata dialog
flow, and the note-pitch-drag handler. These are higher-coupling tests
because they exercise real subscribers / handlers.

### Tasks

1. **`ScoreMessageCoordinator.handleDelete` bracketing** — for the contiguous
   range case, verify that the production path (`composition.withModification(
   () -> line.removeRange(begin, end))`) fires one `DidChange` carrying one
   `ElementRangeDeletion`. Test the production helper directly rather than
   instantiating `ScoreMessageCoordinator` with all its UI dependencies.
2. **Dialog `setData` bracketing** — `CompositionSettingsDialog.TextTab` posts
   a `MetadataDidChangeNotification` inside `composition.withModification(...)`.
   Test the contract by calling `composition.metadataDidChange(notification)`
   inside `composition.withModification(...)` with multiple metadata fields
   set, and verify a single `CompositionDidChangeNotification` is posted
   carrying multiple `MetadataChange` mutations.
3. **`ElementModification` from `NoteDragHandler`** — extend
   `NoteDragHandlerTest`. Drag a note to a new pitch, verify the emitted
   `ElementModification` mutation carries a `beforeElement` clone whose pitch
   matches the original (pre-drag) pitch and whose other fields are intact.
   Verify `line.getElement(index)` after the drag has the new pitch.

### Key files

- `src/test/java/songscribe/music/LineMutationTest.java` (extend with the
  coordinator-equivalent test)
- `src/test/java/songscribe/music/CompositionMetadataDialogFlowTest.java` (new)
- `src/test/java/songscribe/ui/component/score/NoteDragHandlerTest.java` (extend)

---

## Verification

1. **Compile:** `./scripts/compile.sh` passes with no errors.
2. **Unit tests:** `./scripts/test.sh unit` — all tests pass, including new tests from Phases 7a–7d.
3. **Manual test:** `./scripts/crun.sh` — open a song, make edits (add/delete notes, delete a multi-note selection across an Ending, toggle ties/beams, change title via dialog), verify no regressions in rendering or behavior. Confirm that deleting the anchor or end of an Ending removes the Ending. Confirm that loading a document still triggers the expected UI updates.
4. **Grep verification:** `rg ChangeType src/main/java` returns no matches after migration.

---

## Deferred Work (out of scope; revisit when needed)

1. **`CompositionWillChangeNotification`** — defer to undo (#14). When undo is implemented, decide whether to add WillChange or extend mutations to carry full before-state.
2. **Range mutation types** — `{Tie,Beam,Tuplet,Crescendo,Diminuendo}{Addition,Removal,Modification}`. Add when a subscriber needs them (likely undo, or a future range-element-driven validator).
3. **`addRange` / `ElementRangeInsertion`** — add when `handlePaste` is implemented (`PreviewElementManager.java` TODO).
4. **`BaseDialog.getMutations()` abstract method** — not needed because Composition's existing notification handlers already route through `applyChange()`. Revisit only if dialog-level mutation tracking becomes useful (e.g., per-dialog undo).
5. **`ElementField` enum population beyond drag's needs** — Phase 4b populates the values that `NoteDragHandler` actually changes. Add more values when other emitters or subscribers need them.
