# Lyric Editor — Part 2 (Phases 1b, 2, 3, 4, 5)

Follow-on phases to `./lyric-editor.md` (Phase 1a). Each phase is independently shippable and depends on the editor scaffolding from 1a.

Index:
- [Phase 1b — Boundary characters, scan-back](#phase-1b--boundary-characters-scan-back-ime)
- [Phase 2 — Lyric click-selection](#phase-2--lyric-click-selection)
- [Phase 3 — DeleteAction dispatch for lyrics](#phase-3--deleteaction-dispatch-for-lyrics)
- [Phase 4 — Double-click to edit](#phase-4--double-click-to-edit)
- [Phase 5 — Paste tokenization](#phase-5--paste-tokenization)
- [Cross-phase TODOs](#cross-phase-todos)

---

## Phase 1b — Boundary characters, scan-back, IME

### Goal

Enable hyphen / compound-word / melisma-extender semantics by intercepting `-`, `=`, `_` keystrokes. Add `_` alone (scan-back) handling.

### Behavior matrix (additions to 1a)

| Trigger | Field state | Effect |
|---|---|---|
| `-` (keyTyped) | non-empty | Commit current text with `relation = SYLLABLE`; advance |
| `-` (keyTyped) | empty | Advance with no model change |
| `=` (keyTyped) | non-empty | Commit current text with `relation = COMPOUND_WORD`; advance |
| `=` (keyTyped) | empty | Beep; do not advance; do not modify model |
| `_` (keyTyped) | non-empty | Commit current text with `extend = START`; advance |
| `_` (keyTyped) | empty | Scan-back semantics (below); advance |

In 1b, `commit()` becomes parameterized by `(SyllableRelation, Extend)` rather than always `(NONE, NONE)`. The 1a path becomes a special case.

### `_` alone (scan-back) semantics

When the user presses `_` with an empty editor:

1. Walk backwards through the elements of the current line, starting from the element just before the editor's current element.
2. For each candidate, check whether its current verse lyric has non-blank text and `extend` is not `STOP` or `CONTINUE`. If yes, that's the previous syllable.
3. **If a previous syllable is found:**
   - In a single `song.withModification` block:
     - Set `extend = START` on the previous syllable's lyric (via `Line.modifyElement` and `setLyricForVerse`).
     - Remove any lyric record on the current element.
   - Advance.
4. **If no previous syllable is found:**
   - Remove any lyric record on the current element (single mutation).
   - Advance.

The layout's natural rules then handle the trailing extender: it runs from the prior syllable's right edge to the next syllable's left edge / next rest / end-of-line, automatically traversing any blank notes in between (including the current note, which is now blank by construction).

### Files / classes (1b)

**Modified:**
- `songscribe.ui.component.LyricEditor` — extend keystroke dispatch; add scan-back.

**New:**
- None expected.

### Tests (1b)

- `-` non-empty → commit with `SYLLABLE` relation, advance.
- `-` empty → advance, no mutation.
- `=` non-empty → commit with `COMPOUND_WORD` relation, advance.
- `=` empty → beep, no advance, no mutation.
- `_` non-empty → commit with `extend = START`, advance.
- `_` empty with prior syllable found → previous syllable gets `extend = START` AND current element's lyric removed, in one bracket; advance.
- `_` empty with no prior syllable → current element's lyric removed (single mutation); advance.
### Update lifecycle diagram in `LyricEditor.java`

When 1b lands, append the keystroke dispatch table to the lifecycle diagram in the class header comment. Per project convention, diagrams are maintained alongside the code they describe.

---

## Phase 2 — Lyric click-selection

### Goal

Single-click on a rendered lyric selects it. Selection is mutually exclusive with element selection. Selected lyric renders in a distinct color (the existing Score selected-element color).

### Design

Lyric selection is folded into the existing `SelectionCoordinator` rather than a separate coordinator. This keeps mutual-exclusion invariants in one place.

**`SelectionCoordinator` additions:**

```java
public record LyricSelection(StaffElement element, int verse) {}

public void selectLyric(StaffElement element, int verse);  // clears element selection first
public void clearLyricSelection();
public @Nullable LyricSelection getLyricSelection();
public boolean hasLyricSelection();
```

`activateLine` / element selection setters clear lyric selection internally. `selectLyric` clears element selection internally.

`MusicSelectionDidChangeNotification` is extended (or a sibling notification added — TBD at code time based on subscriber needs) to convey lyric-selection changes.

**`LayoutResult.hitTestLyric(Line line, Point2D pointPx)`** — new helper returning a `LyricHit(StaffElement element, int verse)` or null. Iterates `lyricBoxes` for the line and checks each box's pixel bounds (lyrics font ascent/descent for Y-extent). Tight glyph bounds — no padding.

**`LineComponent.mousePressed`** — adds a hit-test pass before the element hit-tester:

```java
var lyricHit = score.getLayoutResult().hitTestLyric(line, screenToLineLocal(p));
if (lyricHit != null) {
    score.getSelectionCoordinator().selectLyric(lyricHit.element(), lyricHit.verse());
    return;
}
// fall through to element hit-test
```

**`LyricTextRenderer`** — paints the selected lyric in the standard Score selected-element color. No fill, no outline — only the text glyphs change color.

### Files / classes (Phase 2)

**Modified:**
- `songscribe.ui.selection.SelectionCoordinator`
- `songscribe.ui.layout.LayoutResult`
- `songscribe.ui.component.score.LineComponent`
- `songscribe.ui.renderer.LyricTextRenderer`
- `songscribe.message.notification.MusicSelectionDidChangeNotification` (or new notification type if subscriber needs warrant)

**New:**
- `songscribe.ui.selection.LyricSelection` record (may live nested inside `SelectionCoordinator`).

### Tests (Phase 2)

- `SelectionCoordinator.selectLyric` clears element selection.
- Element selection clears lyric selection.
- `LayoutResult.hitTestLyric`: hits inside box bounds, misses outside.
- `LyricTextRenderer`: selected lyric paints in selection color.

---

## Phase 3 — DeleteAction dispatch for lyrics

### Goal

Delete or Backspace removes a selected lyric.

### Design

Per the locked decision (option C1 from the review), there is **no** new `DeleteLyricAction`. The existing `DeleteAction` dispatches internally based on which selection is non-empty:

```java
@Override
public void perform(...) {
    var selection = score.getSelectionCoordinator();
    if (selection.hasLyricSelection()) {
        deleteLyric(selection.getLyricSelection());
    } else if (selection.hasActiveSelection()) {
        deleteElement(...);  // existing path
    }
}
```

The action's `updateEnabledState` returns true when either selection is non-empty.

This avoids the accelerator collision that would arise from registering two actions on `VK_DELETE` / `VK_BACK_SPACE` in the same input map.

### Effects (lyric branch, single mutation bracket)

1. Remove the lyric record from the selected element (`setLyricForVerse(verse, NONE, "", NONE)` — empty text removes the entry).
2. Layout reflow handles the rest:
   - Connectors (hyphen / extender) involving the removed syllable disappear naturally.
   - If the removed syllable was the only lyric in the song, the lyrics band collapses on the next reflow.
3. Clear lyric selection. Element selection remains empty.

### Files / classes (Phase 3)

**Modified:**
- `songscribe.ui.action.DeleteAction` — branch on selection type.

**New:**
- None.

### Tests (Phase 3)

- `DeleteAction` enabled when lyric selection is non-empty (and element selection is empty).
- Delete sets the element's lyric to null; layout reflow drops the connector.
- Accelerator collision: `VK_DELETE` correctly fires the lyric branch when only lyric is selected, the element branch when only element is selected, neither when both are empty.
- After lyric delete, lyric selection is cleared.

---

## Phase 4 — Double-click to edit

### Goal

Double-clicking a rendered lyric opens the editor on that element with prefill semantics.

### Design

`LineComponent.mouseClicked` detects a double-click on a lyric hit (reusing `hitTestLyric` from Phase 2) and opens the editor on that element with the existing lyric's text loaded, all selected, caret at end. Single-click on a blank lyric row (no rendered text) does nothing.

### Files / classes (Phase 4)

**Modified:**
- `songscribe.ui.component.score.LineComponent` — `mouseClicked` adds the double-click branch.

### Tests (Phase 4)

- Double-click on rendered lyric → editor opens, text prefilled, all selected, caret at end.
- Double-click on a blank lyric row → no-op.
- Single-click on a rendered lyric → selects (Phase 2 behavior), does not open editor.

---

## Phase 5 — Paste tokenization

### Goal

Pasting clipboard text into an active editor distributes syllables across notes following Finale / Sibelius / MuseScore lyric-paste conventions: tokens separated by space, `-`, `=`, `_` advance through eligible elements; relation / extend follow from the boundary character.

### Behavior

Override the `JTextArea`'s `TransferHandler`. On paste:

1. Strip the clipboard text: replace each newline with a single space.
2. Tokenize on the boundary characters `space`, `-`, `=`, `_`. Each token + its trailing boundary character forms a "segment." A trailing token without a boundary forms a final segment with `boundary = null`.
3. Inside one `song.withModification` block:
   - For each segment with a non-null boundary character: treat it as if the user had typed the segment text and then pressed the boundary key. Apply the matching commit + advance from the 1b matrix. If advance has no eligible target (end of line), commit the current segment and stop processing remaining segments — they are silently dropped.
   - The final segment with `boundary = null`: replace the editor's field text with that segment (no commit, no advance). The user can continue typing or press a commit key.
4. The 32-char cap applies per-segment, not per-paste. A segment longer than 32 chars triggers a beep and that segment is truncated to 32 chars before commit.
5. The whole paste produces a single undo entry (one `withModification` bracket emits N mutations; undo reverts all of them at once).

### Special leading-character behavior

- Leading `-` (paste begins with `-`): `-` alone semantics (advance, no model change).
- Leading `_`: `_` alone scan-back semantics; advance.
- Leading `=`: silently consumed (no beep during paste); continue with next segment.

If the paste produces no advances at all (e.g. paste is just `"foo"` with no boundary), the editor's field text becomes `"foo"` and the session continues normally on the same element.

### Concern carried over from review (Issue 9)

`ScaleContext.textWidthSs` will be called at minimum once per segment width recompute during paste processing. Before paste lands, **verify that `ScaleContext.textWidthSs` is reasonably cheap** (caches `FontMetrics`, doesn't allocate per call). If it allocates per call, add a local cache in `LyricEditor` keyed on `(font, text)` for paste duration. A 50-syllable paste should not produce a measurable hitch.

### Files / classes (5)

**New:**
- `songscribe.ui.component.LyricPasteHandler` (or inner class on `LyricEditor`) extending `TransferHandler`.

**Modified:**
- `songscribe.ui.component.LyricEditor` — install the paste handler at construction.

### Tests (5)

- Paste `"hel-lo world"` over three eligible elements: writes `hel(SYLLABLE)`, `lo(NONE)`, `world(NONE)`; final element commits with no boundary so the editor stays open with `"world"`.
- Single-undo: undoing the paste reverts all three mutations atomically.
- Paste exceeding remaining elements: silently drops trailing segments after end-of-line.
- Per-segment 32-char cap: a 50-char segment beeps and commits truncated to 32.
- Leading `-` advances without model change.
- Leading `_` runs scan-back.
- Leading `=` silently consumed.
- Paste without any boundaries replaces field text only.

---

## Cross-phase TODOs

### TODO: Verify `ScaleContext.textWidthSs` cost before Phase 5

**What:** Read the implementation of `ScaleContext.textWidthSs(Font, String)` and confirm it does not allocate a fresh `FontMetrics` per call.

**Why:** Phase 5 paste calls the width recompute many times in rapid succession (one per segment × 1+ per width recompute). If the width call allocates per invocation, a long paste produces a measurable UI hitch. Phase 1a only calls it at human keystroke pace, so the cost is undetectable there — but 5 will surface any latent inefficiency.

**Context:** Per the review, expected to be cheap (most Java FontMetrics implementations cache internally). Two-minute read, no design change unless slow. If slow, options are (a) optimize `textWidthSs` itself, or (b) add a local memoization in `LyricEditor` for paste duration.

**Depends on / blocked by:** Nothing for the verification itself. The verification result feeds into Phase 5's design.

### TODO: Lock the UIAction audit whitelist in T25

**What:** During Phase 1a implementation, walk every `UIAction` subclass and curate the list of mutating actions for `T25` (the audit meta-test). The list is the authoritative inventory of "actions that must carry `DISABLE_WHEN_EDITING_TEXT`."

**Why:** The editor's invariants (no external mutation during edit) are enforced by this flag. Without an audit test, a new mutating action added in 6 months without the flag will silently break the editor. T25 catches regressions; the curated list catches the audit miss.

**Context:** Per exploration, candidates currently missing the flag: `DeleteAction`, `CutAction`, `PasteAction`, `AddLyricAction`, plus several `NoteOnlyAction` subclasses. Final list to be locked when 1a wires the audit.

**Depends on / blocked by:** Phase 1a in progress.

### TODO: Verify `Score.getSelectionStrokeColor()` (or equivalent) renders sensibly

**What:** Before Phase 2 ships, eyeball the actual color produced by Score's existing selection-color getter when applied to lyric text glyphs.

**Why:** Phase 2 reuses Score's existing selection color for selected lyric text. If the color is appropriate for selected element rectangles but visually wrong on text glyphs (contrast, legibility), the visual will need tuning.

**Context:** The actual getter name was reported by exploration as `getSelectionStrokeColor()`. Confirm during Phase 2 implementation; rename or add a sibling getter if the existing one is too specifically scoped to "stroke."

**Depends on / blocked by:** Phase 2 implementation start.
