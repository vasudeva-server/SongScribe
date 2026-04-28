# Lyric Editor — Phase 1a

## Overview

An in-place editing UX for per-element lyric syllables (verse 1 only). Triggered by `AddLyricAction`, the editor is a `JTextArea` overlay positioned at the exact pixel position where the lyric will render. The user types a syllable; pressing Tab or Space commits the current text to the model and advances the editor onto the next eligible element. Pressing Enter commits without advancing. Pressing Escape cancels. Losing focus commits.

Phase 1a is the smallest end-to-end usable feature. Boundary characters (`-`, `=`, `_`), `_` scan-back, paste, IME composition tracking, and lyric selection / deletion are deferred to follow-on phases. See `lyric-editor-part2.md`.

## Scope

- Verse 1 only.
- Single-line syllables. The component is a `JTextArea` for visual sizing flexibility; multi-line input is suppressed.
- Tab / Space / Enter / Escape / focus-loss only. No `-` / `=` / `_` boundary characters in 1a.
- No paste support in 1a.
- No IME composition tracking in 1a.

## Goals

1. The editor opens at the exact pixel position where the syllable will render — centered on the owning element, on the verse-1 baseline, in the same font the renderer will use.
2. Tab / Space commit and advance to the next eligible element. Enter commits without advancing. Escape cancels. Focus loss commits.
3. Empty commit removes any existing lyric record on the element (the data model stays sparse — held / un-lyriced notes have no `Lyric` entry).
4. Same-text commits emit zero mutations.

## Non-goals (1a)

- Verses 2+.
- `-` / `=` / `_` boundary characters and connectors (deferred to 1b).
- `_` alone scan-back (deferred to 1b).
- IME composition tracking (deferred to 1b).
- Paste support (deferred to 1c).
- Lyric click-selection, visual highlight, deletion (deferred to phases 2 and 3).
- Double-click-to-edit (deferred to phase 4).
- Live preview-while-typing in the final layout slot. The model only updates on commit; the layout reflow runs once.
- Multi-line lyrics, lyric formatting, per-syllable color overrides.
- Editing connectors directly (connectors are layout output, not a data input).

## High-level architecture

```
                    ┌───────────────────────────────────────┐
                    │              Score                    │
                    │                                       │
                    │  SelectionCoordinator (existing)      │
                    │                                       │
                    │  LyricEditor (new, per-session)       │
                    │   - JTextArea overlay                 │
                    │   - parented directly to Score        │
                    │   - new instance per element advance  │
                    └───────────────────────────────────────┘
                                    │
                                    ▼ child-of (when active)
                            ┌──────────────┐
                            │     Score    │
                            └──────────────┘
```

The editor is parented directly to `Score`, never to `LineComponent`. This avoids retrofitting `LineComponent` as a child container (no precedent on the score side) and eliminates the reparent dance when advancing across lines (deferred to 1b/1c).

The editor is a per-session new instance, not a Score-owned singleton. On open: construct, configure (bounds, prefill text, attach listeners), `score.add(editor)`, set visible, request focus. On dismiss: `score.remove(editor)`, drop reference. JTextArea allocation is microseconds; pooling adds no value and adds reparent code surface.

The editor does not subscribe to `SongDidChangeNotification`. The UIAction audit (see "Action gating during editing") guarantees no external mutation can fire while the editor has focus. The editor's own commit notifications are followed immediately by `advance()` (which constructs a new editor with fresh layout coordinates) or by `dismiss()`. No reanchor handler is needed.

## Component design

### `StaffElement.setLyricForVerse` (new helper on existing class)

```java
public void setLyricForVerse(int verse, SyllableRelation relation, String text, Extend extend) {
    // Replaces or removes the verse-N entry in the internal lyrics list.
    // If text is null or blank, removes the entry.
    // Otherwise constructs new Lyric(verse, relation, text, extend) and replaces or appends.
}

public void setLyricForVerse(int verse, @Nullable Lyric lyric) {  // overload accepting prebuilt Lyric — only used internally if needed
    // Asserts lyric == null || lyric.verse() == verse.
}
```

Encapsulates list mutation. The `Lyric` is built internally from `(verse, relation, text, extend)` so callers cannot accidentally pass a `Lyric` whose verse field disagrees with the index they're updating.

### `LyricEditor` (new class)

```java
public final class LyricEditor extends JTextArea {
    private static final int MAX_LENGTH_CHARS = 32;

    // Session state
    private final Line line;
    private final StaffElement element;
    private boolean committing;  // re-entrancy guard
}
```

Lifecycle (embed this diagram in the class header comment):

```
 ┌───────────────────────────────────────────────────────────┐
 │ LyricEditor lifecycle (Phase 1a)                          │
 │                                                           │
 │   AddLyricAction.actionPerformed                          │
 │           │                                               │
 │           ▼                                               │
 │   new LyricEditor(line, element)                          │
 │   editor.setBounds(...)                                   │
 │   if existing lyric: setText, selectAll, caret end        │
 │   editor.attachListeners()                                │
 │   score.add(editor); setVisible(true); requestFocus       │
 │           │                                               │
 │           ▼                                               │
 │   ┌──── ACTIVE ─────────────────────────────────┐         │
 │   │                                             │         │
 │   │  user keystroke                             │         │
 │   │   - char insert/delete → recompute width    │         │
 │   │   - len > 32 → beep, reject                 │         │
 │   │   - newline → reject                        │         │
 │   │                                             │         │
 │   │  Tab/Space  → commit() → advance()          │         │
 │   │  Enter      → commit() → dismiss()          │         │
 │   │  Escape     → dismiss() (no commit)         │         │
 │   │  focus-lost → commit() → dismiss()          │         │
 │   └─────────────────────────────────────────────┘         │
 │           │                                               │
 │           ▼                                               │
 │   advance(): scan forward for eligible element            │
 │     eligible: !rest, OR rest with existing lyric          │
 │     found: dismiss this, new LyricEditor(line, next)      │
 │     none:  dismiss()                                      │
 │                                                           │
 │   dismiss(): score.remove(this); editor reference cleared │
 │                                                           │
 │ Invariant: while editor is active, no external code path  │
 │ may mutate the song. Enforced by DISABLE_WHEN_EDITING_TEXT │
 │ on every mutating UIAction (audit step in 1a).            │
 └───────────────────────────────────────────────────────────┘
```

Key dependencies:
- `ScaleContext` — staff-space ↔ pixel conversion.
- `LayoutResult.getLyricAnchor(StaffElement)` — new helper returning `(centerXSs, baselineYSs)` for the editor's center anchor.
- `LyricRenderMetrics.scaledLyricsFont()` — for the editor's font.
- `LyricRenderMetrics.lyricBoxWidthSs(String)` — new shared helper, called by both `LyricBoxLayout` and `LyricEditor` to guarantee identical width formulas.
- `Song.withModification` / `Line.modifyElement` — for emitting `ElementModification(LYRIC)` mutations.
- `StaffElement.setLyricForVerse` — for the actual list mutation.

### `LayoutResult.getLyricAnchor(StaffElement)` (new helper)

```java
public record LyricAnchor(double centerXSs, double baselineYSs) {}

public LyricAnchor getLyricAnchor(StaffElement element) {
    var boxes = getLyricBoxes(element);
    var verse = 1;  // verse-1 only for now
    var baselineYSs = songLayoutMetrics.verseYSsInLine(verse);

    if (!boxes.isEmpty()) {
        var box = boxes.getFirst();   // verse 1 by enumeration order
        var centerXSs = box.xSs() + box.widthSs() / 2.0;
        return new LyricAnchor(centerXSs, baselineYSs);
    }

    var column = getElementColumn(element);
    if (column == null) {
        throw new IllegalStateException(
            "getLyricAnchor: no lyric box and no column for element " + element);
    }
    var centerXSs = column.getXSs() + column.getRightExtentSs() / 2.0;
    return new LyricAnchor(centerXSs, baselineYSs);
}
```

Both `LyricTextRenderer` and `LyricEditor` call this helper. Neither recomputes the formula independently.

The null-column branch throws rather than falling back to an arbitrary value. A missing column for an element that doesn't have a lyric box yet indicates broken layout state; silent fallback would mask the bug and place the editor at a misleading location.

### `LyricRenderMetrics.lyricBoxWidthSs(String text)` (new shared helper)

Returns the full box width in staff spaces (text width + 2 × horizontal padding) using `scaledLyricsFont()`. The padding constant lives here, not in `LyricEditor`. Both `LyricBoxLayout` (during layout) and `LyricEditor` (during typing) call this method. A unit test asserts that the value `LyricBoxLayout` produces for a given text matches `lyricBoxWidthSs(text)` exactly.

### `LyricTextRenderer` (existing, modified)

Skip rendering the box for the element currently being edited. The renderer queries `score.getActiveLyricEditor()` (or equivalent — exact getter to be defined when wiring `Score`) and compares its `getActiveElement()` to the element being rendered. Connectors touching the edited element are *not* skipped (when 1b adds them); users keep visual context.

### `AddLyricAction` (existing skeleton, modified)

- Add `Flag.DISABLE_WHEN_EDITING_TEXT` to the FLAGS array.
- Implement `actionPerformed`: get the single selected element via `score.getSelectionCoordinator().getSingleSelectedElement()`, open the `LyricEditor` session on it.
- Existing `enableFromSelection` already disables when the element has a non-blank verse-1 lyric — keep.

### `Score` (existing, modified)

- Add an `@Nullable LyricEditor activeLyricEditor` field, with `getActiveLyricEditor()` and a package-private setter used by `LyricEditor` open/dismiss.
- The renderer queries this getter; the action queries it via the flag system.

## Center anchor and width

The editor's geometry follows the same formula `LyricBoxLayout` uses. After every keystroke (or document change in general), the editor:

1. Measures `widthSs = LyricRenderMetrics.lyricBoxWidthSs(text)` (text + 2 × padding, baked in).
2. Reads `(centerXSs, baselineYSs)` from `LayoutResult.getLyricAnchor(element)`.
3. Computes `xSs = centerXSs - widthSs / 2`, converts to pixels via `ScaleContext.toPixels`.
4. Sizes the editor to `widthSs × lyricsLineHeightSs` (in staff spaces, converted to pixels).
5. Positions the editor so its baseline aligns with `baselineYSs`. Since `JTextArea` sizes to its font's full ascent + descent, the top-Y is `baselineYSs - ascentSs` (where `ascentSs` is the lyrics font's `FontMetrics.getAscent()` converted to staff spaces).
6. Translates the resulting line-local bounds to Score-local coordinates by adding `lineComponent.getLocation()`, then `setBounds(scoreLocalBounds)`.

The recompute fires from a `DocumentListener.insertUpdate / removeUpdate`. Listeners are attached *after* initial bounds + text are set during construction, so programmatic prefill on construction does not trigger the listener.

## Behavior

### Entry

`AddLyricAction.actionPerformed`. Enabled when:
- exactly one element is selected,
- the element's verse-1 lyric is null or has blank text.

Opens an empty editor on the selected element.

### Eligibility for advance

Default: rests are skipped. Each candidate is eligible iff:
- it is a note (`!element.isRest()`), OR
- it is a rest that already has a verse-1 lyric.

If no eligible element exists in the rest of the line, the advance stops: the current segment is committed, the editor dismisses. (End-of-line rule.) This is *not* a wrap to the next line.

### Commit / dismiss matrix (1a)

| Trigger | Field state | Effect |
|---|---|---|
| Escape | any | Cancel: no commit, dismiss editor |
| Enter | any | Commit current text to current element; do not advance; dismiss editor |
| Tab | any | Commit current text; advance |
| Space | any | Same as Tab |
| Focus loss | any | Commit current text; dismiss editor; no advance |

"Commit current text" semantics:
- Text non-empty and different from existing: emit `ElementModification(LYRIC)` writing `Lyric(1, NONE, text, NONE)`.
- Text empty: remove any existing lyric record on the element (set the verse-1 entry to null). If the element already had no lyric, skip the `withModification` call entirely (zero mutations).
- Text equal to existing lyric text: skip the `withModification` call entirely.

All commit-and-dismiss-but-no-advance paths (Enter, Escape, focus loss, end-of-line) leave the (future) lyric-selection coordinator state untouched. Element selection is unchanged.

### Re-entrancy guard

Focus loss can fire during commit (e.g., a layout reflow steals focus). The editor sets a `committing` boolean before opening the modification bracket and clears it after dismiss completes. The focus-lost handler returns early when `committing` is true. The Escape handler also checks the flag.

### Text validation

- Hard cap: 32 characters. On any insert that would exceed the cap, beep and reject.
- Newlines are rejected from the document model unconditionally.
- No other character restrictions in 1a.

### Input behaviors (1a)

- **Enter** (`VK_ENTER`) — intercepted; commits same-note, dismisses; not inserted as newline.
- **Tab** (`VK_TAB`) — intercepted; commits and advances; not inserted as a tab character.
- **Space** (`KeyTyped` with `getKeyChar() == ' '`) — intercepted; commits and advances; not inserted into the document.
- **Escape** (`VK_ESCAPE`) — intercepted; cancels.
- All other characters insert normally up to the 32-char cap.

`-`, `=`, `_` are inserted as plain characters in 1a. They become boundary characters in 1b.

## Action gating during editing

`AddLyricAction` adds `Flag.DISABLE_WHEN_EDITING_TEXT`. The existing `UIUtils.isEditingText()` returns true while the `LyricEditor` has focus (it is a `JTextComponent`). `UIAction`'s `enableFromTextEditingState` machinery disables flagged actions automatically.

**Audit step (load-bearing in 1a):** walk every `UIAction` subclass and add `DISABLE_WHEN_EDITING_TEXT` to any action that mutates element/song state. Without the audit, a stray mutation during editing puts the editor's `element` reference in a state we deliberately do not handle — there is no defensive `@Handler` to recover. The audit is what makes the editor's invariants safe.

The audit is encoded as a unit test (T25 below) that iterates a curated whitelist and asserts each carries the flag. When a new mutating action is added in the future, the test fails until the flag is added. A header comment in `LyricEditor.java` documents this invariant for future maintainers.

Out of scope for the audit: actions that do not mutate (e.g. open/save document — though these still need to behave correctly in the presence of an active editor; see "Focus management" below).

## Focus management

- On editor open: `editor.requestFocusInWindow()` after `add` and `setVisible`.
- On editor dismiss: `score.requestFocusInWindow()` to return keyboard focus to the score component (so element-level shortcuts keep working).
- File-level operations (Open / New / Revert) shift focus to `Score` before performing their mutation. The editor's focus-lost handler commits and dismisses naturally, after which the file operation runs against a clean state. No `DISABLE_WHEN_EDITING_TEXT` flag needed on these paths.
- On `MainFrame` deactivation: focus loss → commit → dismiss, per the matrix.

## Mutations

The editor never constructs `Mutation` records directly. All commits go through `Line.modifyElement`:

```java
song.withModification(() -> {
    line.modifyElement(elementIndex, EnumSet.of(ElementField.LYRIC), () -> {
        element.setLyricForVerse(1, SyllableRelation.NONE, text, Extend.NONE);
        // text == null or blank → setLyricForVerse internally removes the verse-1 entry
    });
});
```

Skip the `withModification` call entirely when the new text is empty AND the element already has no verse-1 lyric, OR when the new text equals the existing verse-1 lyric text (a same-text commit produces zero mutations).

## Edge cases

- **Editor opens on an element with an existing lyric**: prefill text, select all, caret at end. `LyricTextRenderer` skips drawing this element's box during the session.
- **Empty commit on an element with a prior lyric**: the prior lyric is removed (verse-1 entry set to null). This is the "user opened the editor on a populated note via advance, deleted all the text, then committed" path.
- **Commit with the same text already present**: emit no mutation; skip `withModification` entirely.
- **Re-entrant commit / dismiss** (focus-lost during commit): `committing` flag returns early.
- **`getLyricAnchor` called for an element with neither boxes nor column**: throws `IllegalStateException`. This indicates broken layout state; surface the error rather than masking it with a fallback position.

## Strings

No new strings in 1a. `action.add.lyric` and `action.add.lyric.tooltip` already exist.

## Files / classes

**New:**
- `songscribe.ui.component.LyricEditor`
- `songscribe.ui.layout.LyricAnchor` (record, may live inside `LayoutResult`)

**Modified:**
- `songscribe.music.StaffElement` — add `setLyricForVerse(int, SyllableRelation, String, Extend)` helper.
- `songscribe.ui.action.AddLyricAction` — add `DISABLE_WHEN_EDITING_TEXT`; implement `actionPerformed`.
- `songscribe.ui.layout.LayoutResult` — add `getLyricAnchor(StaffElement)`.
- `songscribe.ui.layout.LyricRenderMetrics` — add `lyricBoxWidthSs(String)`. (Or whichever class owns `scaledLyricsFont` — confirm at code time.)
- `songscribe.ui.layout.LyricBoxLayout` — call `lyricBoxWidthSs` instead of computing inline.
- `songscribe.ui.renderer.LyricTextRenderer` — skip rendering for the editor's active element.
- `songscribe.ui.component.Score` — own the active editor reference; expose `getActiveLyricEditor()`.

**UIAction audit:** add `DISABLE_WHEN_EDITING_TEXT` to mutating actions currently missing it. Per exploration, candidates include `DeleteAction`, `CutAction`, `PasteAction`, `AddLyricAction` itself, and any `NoteOnlyAction` subclasses without the flag. Final list to be confirmed by the audit pass and locked into T25.

## Tests

| # | Test |
|---|---|
| T1 | `LayoutResult.getLyricAnchor` returns box-anchored geometry when verse-1 box exists |
| T2 | `LayoutResult.getLyricAnchor` returns column-anchored geometry when no boxes |
| T3 | `LayoutResult.getLyricAnchor` Y matches `verseYSsInLine(1)` exactly |
| T4 | `LayoutResult.getLyricAnchor` throws `IllegalStateException` when neither boxes nor column exist |
| T5 | `LyricRenderMetrics.lyricBoxWidthSs(text)` returns the same value as `LyricBoxLayout` produces for the same text |
| T6 | `LyricRenderMetrics.lyricBoxWidthSs("")` returns 2 × padding |
| T7 | `StaffElement.setLyricForVerse(1, ...)` replaces existing verse-1 entry |
| T8 | `StaffElement.setLyricForVerse(1, NONE, "", NONE)` (empty text) removes verse-1 entry |
| T9 | `StaffElement.setLyricForVerse(2, ...)` on element with verse-1 only adds verse-2 entry without disturbing verse-1 |
| T10 | `LyricEditor.commit` non-empty new text emits exactly one `ElementModification(LYRIC)` mutation |
| T11 | `LyricEditor.commit` empty text on element with prior lyric removes the lyric (one mutation) |
| T12 | `LyricEditor.commit` empty text on element with no prior lyric emits zero mutations |
| T13 | `LyricEditor.commit` same-text-as-existing emits zero mutations |
| T14 | `LyricEditor.advance` skips rests |
| T15 | `LyricEditor.advance` treats a rest with an existing lyric as eligible |
| T16 | `LyricEditor.advance` at end of line dismisses, doesn't wrap |
| T17 | `LyricEditor.advance` into populated element prefills text + selectAll + caret at end |
| T18 | Tab consumed (commit + advance, no tab character in document) |
| T19 | Space consumed (commit + advance, no space in document) |
| T20 | Enter consumed (commit + dismiss, no newline in document) |
| T21 | Escape cancels (no mutation, dismiss) |
| T22 | 33rd character beeps and isn't inserted |
| T23 | Newlines rejected from document model |
| T24 | Re-entrant commit during focus-lost is a no-op (committing flag works) |
| T25 | Audit meta-test: every UIAction in the curated mutating-actions whitelist carries `DISABLE_WHEN_EDITING_TEXT` |
| T26 | `AddLyricAction` carries `DISABLE_WHEN_EDITING_TEXT` and disabled state toggles via `enableFromTextEditingState` |
| T27 | `AddLyricAction.actionPerformed` opens the editor when one element with a blank lyric is selected |
| T28 | `LyricTextRenderer` skips rendering for the editor's active element; renders other elements normally |

## References

- `songscribe.music.Lyric` — data record (verse, relation, text, extend).
- `songscribe.music.StaffElement.SyllableRelation` — `NONE | SYLLABLE | COMPOUND_WORD`.
- `songscribe.music.Lyric.Extend` — `NONE | START | STOP | CONTINUE`.
- `songscribe.ui.layout.LyricBoxLayout` — per-element rendered box geometry the editor must match.
- `songscribe.ui.layout.SongLayoutMetrics.verseYSsInLine(int)` — verse baseline Y.
- `.agent/rules/messages.md` — message bus conventions.
- `.agent/rules/mutations.md` — modification brackets, `Line.modifyElement`, `ElementField.LYRIC`.
- `.agent/rules/strings.md` — externalized string conventions.
- `lyric-editor-part2.md` — phases 1b, 1c, 2, 3, 4 (boundary chars, paste, selection, delete, double-click).
