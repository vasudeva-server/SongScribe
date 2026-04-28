# Lyric Editor — Phase 1a Implementation Plan

**Spec:** [`specs/lyric-editor.md`](../../../specs/lyric-editor.md)  <br>
**Deferred phases:** [`specs/lyric-editor-part2.md`](../../../specs/lyric-editor-part2.md) (1b, 1c, 2, 3, 4)  <br>
**Created:** 2026-04-25

---

## Overview

Phase 1a delivers the smallest end-to-end usable lyric editor: a `JTextArea` overlay parented to `Score`, opened by `AddLyricAction` on a single selected element. The user types a syllable; Tab / Space commits and advances, Enter commits without advancing, Escape cancels, focus loss commits.

Boundary characters (`-`, `=`, `_`), `_` scan-back, paste, IME composition, and lyric click-selection / deletion / double-click-to-edit are explicitly out of scope and addressed by follow-on phases (see part2 spec).

The implementation is broken into 8 phases ordered so each ends in a compilable, testable state with the editor incrementally functional. Tests for each phase ship with the phase that introduces the behavior they cover.

---

## Status Dashboard

| Phase | Description | Status | Model | Effort | Sub-plan |
|-------|-------------|--------|-------|--------|----------|
| 1 | [Shared geometry helpers](#-phase-1-shared-geometry-helpers) | ✅ Done | Sonnet 4.6 | Medium | — |
| 2 | [Model helper: setLyricForVerse](#-phase-2-model-helper-setlyricforverse) | ✅ Done | Haiku 4.5 | Low | — |
| 3 | [LyricEditor scaffold and lifecycle](#-phase-3-lyriceditor-scaffold-and-lifecycle) | ✅ Done | Opus 4.7 | Medium-High | — |
| 4 | [Score integration and renderer skip](#-phase-4-score-integration-and-renderer-skip) | ✅ Done | Sonnet 4.6 | Low | — |
| 5 | [Commit and advance logic](#-phase-5-commit-and-advance-logic) | ✅ Done | Opus 4.7 | High | — |
| 6 | [Input behavior and validation](#-phase-6-input-behavior-and-validation) | ⏳ Pending | Opus 4.7 | High | — |
| 7 | [AddLyricAction wiring](#-phase-7-addlyricaction-wiring) | ⏳ Pending | Haiku 4.5 | Low | — |
| 8 | [UIAction audit and meta-test](#-phase-8-uiaction-audit-and-meta-test) | ⏳ Pending | Sonnet 4.6 | Medium | — |

---

## Cross-cutting decisions (from spec)

These decisions apply across phases. Re-read before any phase that touches the relevant area.

- **Editor parent:** `Score`, never `LineComponent`. Avoids retrofitting line components as containers.
- **Lifecycle:** per-session new instance. No pooling.
- **No `SongDidChangeNotification` subscription on the editor.** The UIAction audit (Phase 8) is what guarantees no external mutation can fire while the editor has focus.
- **All mutations go through `Line.modifyElement`** with `EnumSet.of(ElementField.LYRIC)`. Editor never constructs `Mutation` records directly.
- **Same-text and empty-on-empty commits emit zero mutations** — skip the `withModification` call entirely.
- **`getLyricAnchor` throws `IllegalStateException`** when neither boxes nor column exist. No silent fallback.
- **`lyricBoxWidthSs` is the single source of truth for box width.** Both `LyricBoxLayout` and `LyricEditor` call it; identity is asserted by a unit test.
- **All user-facing strings externalized.** Phase 1a adds none — `action.add.lyric` and `action.add.lyric.tooltip` already exist.
- **Java/Kotlin code exploration uses Serena tools** (`jet_brains_get_symbols_overview`, `jet_brains_find_symbol`, `jet_brains_find_referencing_symbols`). See `.agent/rules/serena.md`.

---

## ✅ Phase 1: Shared geometry helpers

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Model:** Sonnet 4.6  <br>
**Effort:** Medium — pure refactor + helper extraction, geometry math, six tests. No tricky lifecycle or input handling; Sonnet handles this layer of the layout package well.

### Purpose

Introduce the geometry helpers that both the existing renderer and the new editor will share. Refactor `LyricBoxLayout` so width computation lives in exactly one place. This phase ships entirely without `LyricEditor` and is a pure refactor + addition.

### Scope

1. **`LyricRenderMetrics.lyricBoxWidthSs(String text)`** — new method returning `text width (via scaledLyricsFont) + 2 × horizontal padding` in staff spaces. The padding constant moves to this class if it currently lives elsewhere. Confirm the actual owning class via `jet_brains_get_symbols_overview` on `LyricRenderMetrics` and `LyricBoxLayout`; if `scaledLyricsFont` lives elsewhere, host the new method there and document the location.
2. **Refactor `LyricBoxLayout`** to call `lyricBoxWidthSs(text)` instead of computing width inline. Verify no behavioral change via existing layout tests.
3. **`LayoutResult.LyricAnchor` record** — `(double centerXSs, double baselineYSs)`. May live as a nested record on `LayoutResult` (preferred for cohesion) or in `songscribe.ui.layout.LyricAnchor`. Decide at code time based on existing patterns in `LayoutResult`.
4. **`LayoutResult.getLyricAnchor(StaffElement element)`** — implements the box-anchored / column-anchored branches per the spec. Throws `IllegalStateException` when neither branch is satisfiable (no defensive fallback).

### Files

**Modified:**
- `songscribe.ui.layout.LyricRenderMetrics` (or owner of `scaledLyricsFont`) — add `lyricBoxWidthSs`.
- `songscribe.ui.layout.LyricBoxLayout` — call `lyricBoxWidthSs`.
- `songscribe.ui.layout.LayoutResult` — add `getLyricAnchor`, possibly nest `LyricAnchor` record.

**New (if not nested):**
- `songscribe.ui.layout.LyricAnchor`

### Tests

- **T1** `getLyricAnchor` returns box-anchored geometry when verse-1 box exists.
- **T2** `getLyricAnchor` returns column-anchored geometry when no boxes.
- **T3** `getLyricAnchor` Y matches `verseYSsInLine(1)` exactly.
- **T4** `getLyricAnchor` throws `IllegalStateException` when neither boxes nor column exist.
- **T5** `lyricBoxWidthSs(text)` returns the same value as `LyricBoxLayout` produces for the same text.
- **T6** `lyricBoxWidthSs("")` returns 2 × padding.

### Exit criteria

- `./scripts/compile.sh` passes.
- All tests T1–T6 green.
- Existing layout tests unchanged (no regressions in lyric box geometry).

---

## ✅ Phase 2: Model helper: setLyricForVerse

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Model:** Haiku 4.5  <br>
**Effort:** Low — one method on `StaffElement`, three straightforward tests. Mechanical scope; Haiku is sufficient.

### Purpose

Centralize verse-keyed lyric mutation on `StaffElement` so callers cannot accidentally pass a `Lyric` whose verse field disagrees with the index they're updating. This is a precondition for `LyricEditor.commit`.

### Scope

1. **`StaffElement.setLyricForVerse(int verse, SyllableRelation relation, String text, Extend extend)`** — replaces or removes the verse-N entry. Null/blank `text` removes; otherwise constructs `new Lyric(verse, relation, text, extend)` and replaces or appends.
2. **Overload `StaffElement.setLyricForVerse(int verse, @Nullable Lyric lyric)`** (internal helper). Asserts `lyric == null || lyric.verse() == verse`. Add only if needed by the primary overload's implementation; otherwise omit.
3. Use `jet_brains_find_referencing_symbols` to confirm no existing code mutates the lyrics list directly in a way that would conflict.

### Files

**Modified:**
- `songscribe.music.StaffElement`

### Tests

- **T7** `setLyricForVerse(1, ...)` replaces existing verse-1 entry.
- **T8** `setLyricForVerse(1, NONE, "", NONE)` (empty text) removes verse-1 entry.
- **T9** `setLyricForVerse(2, ...)` on element with verse-1 only adds verse-2 entry without disturbing verse-1.

### Exit criteria

- Compile clean.
- Tests T7–T9 green.

---

## ✅ Phase 3: LyricEditor scaffold and lifecycle

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Model:** Opus 4.7  <br>
**Effort:** Medium-High — new Swing class with geometry pipeline, line-local→Score-local translation, listener-attach ordering (must follow prefill), and a manual smoke-test path. Subtle Swing wiring; Opus pays off for getting the lifecycle right the first time.

### Purpose

Stand up the `LyricEditor` class with construction, geometry, attach-to-Score, focus, and `dismiss()`. No commit logic, no advance logic, no input handling beyond what `JTextArea` provides by default. This phase produces a visible-but-inert overlay; verify positioning by manually wiring a temporary trigger from `AddLyricAction` (will be replaced in Phase 7).

### Scope

1. **New class `songscribe.ui.component.LyricEditor extends JTextArea`** with:
   - Fields: `final Line line`, `final StaffElement element`, `boolean committing` (re-entrancy guard, used in later phases).
   - Constants: `MAX_LENGTH_CHARS = 32`.
   - Header comment block embedding the lifecycle ASCII diagram from the spec.
   - Constructor: configure font (`scaledLyricsFont`), single-line behavior, transparent or appropriate background, set initial bounds via geometry pipeline, prefill text + selectAll + caret-at-end if existing lyric.
   - `attachListeners()` — empty stub for now; `DocumentListener` added for live width recompute (calls geometry recompute below). Listeners attached **after** construction-time prefill so programmatic prefill does not trigger.
   - `recomputeBounds()` — implements steps 1–6 of the "Center anchor and width" section: width from `lyricBoxWidthSs`, anchor from `getLyricAnchor`, top-Y from `baselineYSs - ascentSs`, translate line-local → Score-local via `lineComponent.getLocation()`, `setBounds`.
   - `dismiss()` — `score.remove(this)`, clear `score.activeLyricEditor` reference (Phase 4 wires the field).
2. **Manual smoke test:** temporarily wire `AddLyricAction.actionPerformed` to construct the editor on the selected element so the developer can run `./scripts/run.sh` and visually confirm position/font/sizing matches `LyricBoxLayout`'s output for that element. Remove this temporary wiring at end of phase or leave it for Phase 7 to formalize.

### Files

**New:**
- `songscribe.ui.component.LyricEditor`

**Modified (temporary):**
- `songscribe.ui.action.AddLyricAction` — temp `actionPerformed` for smoke test (will be replaced in Phase 7).

### Tests

No automated tests in this phase — geometry is exercised via the Phase 1 helpers (already tested) and visual confirmation. Behavior tests follow in Phases 5–6.

### Exit criteria

- Compile clean.
- Manual smoke: editor opens at the expected pixel position centered on the selected note's column / existing lyric box, font matches the rendered lyrics font, width grows with typed text, Escape (default JTextArea behavior — no-op currently) does not dismiss yet (deferred to Phase 6).

---

## ✅ Phase 4: Score integration and renderer skip

**Status:** Done  <br>
**BlockedBy:** 3  <br>
**Model:** Sonnet 4.6  <br>
**Effort:** Low — small surface area: one nullable field on `Score`, two accessor edits, one renderer skip-branch, one test. Sonnet is plenty.

### Purpose

Make `Score` aware of the active editor so the renderer can suppress the box for the element being edited. This phase enables the visual handoff: while editing, the box disappears from the rendered score and the editor takes its place.

### Scope

1. **`Score.activeLyricEditor` field** — `@Nullable LyricEditor`, with public `getActiveLyricEditor()` and package-private `setActiveLyricEditor(LyricEditor)` setter used by `LyricEditor`'s open/dismiss paths.
2. **Update `LyricEditor`** to call `score.setActiveLyricEditor(this)` after `score.add(this)` and `score.setActiveLyricEditor(null)` in `dismiss()`.
3. **`LyricTextRenderer`** — query `score.getActiveLyricEditor()`; if non-null and its `getActiveElement()` (new package-private accessor on `LyricEditor` returning `element`) equals the element being rendered, skip drawing the box for that element. Connectors are not skipped (no connectors in 1a anyway, but document the intent in a code comment).

### Files

**Modified:**
- `songscribe.ui.component.Score` — field, getter, package-private setter.
- `songscribe.ui.component.LyricEditor` — call setter on attach/dismiss; expose `getActiveElement()`.
- `songscribe.ui.renderer.LyricTextRenderer` — skip rendering when matching active editor.

### Tests

- **T28** `LyricTextRenderer` skips rendering for the editor's active element; renders other elements normally.

### Exit criteria

- Compile clean.
- T28 green.
- Manual: opening the editor on a populated note hides the existing rendered lyric for that note while keeping all others visible. Closing restores it.

---

## ✅ Phase 5: Commit and advance logic

**Status:** Done  <br>
**BlockedBy:** 4  <br>
**Model:** Opus 4.7  <br>
**Effort:** High — heart of the feature: zero-mutation skip cases, modification bracket usage, eligibility scan with rest-but-has-lyric carve-out, `openOn` helper that both action and `advance()` share, and the `committing` re-entrancy flag wiring. Eight tests cover semantics that are easy to get subtly wrong; use Opus.

### Purpose

Implement the data-flow heart of the editor: turning the editor's text into a model mutation, and walking forward to the next eligible element. No keystroke routing yet — these methods are exercised directly from tests in this phase.

### Scope

1. **`LyricEditor.commit()`** — implements the full commit semantics:
   - Reads `getText()`.
   - If text equals existing verse-1 lyric text → no-op (zero mutations).
   - If text empty AND element has no existing verse-1 lyric → no-op (zero mutations).
   - Otherwise opens `song.withModification` bracket and calls `line.modifyElement(elementIndex, EnumSet.of(ElementField.LYRIC), () -> element.setLyricForVerse(1, SyllableRelation.NONE, text, Extend.NONE))`.
   - Sets `committing = true` before opening the bracket; clears in a `finally` after dismiss/advance completes (the focus-lost re-entrancy concern is addressed in Phase 6 — flag is wired here so commit is safe to call from any path).
2. **`LyricEditor.advance()`** — implements the eligibility scan:
   - Iterates the current line's elements starting at `elementIndex + 1`.
   - Eligible iff `!candidate.isRest()` OR `candidate.hasLyricForVerse(1)` (with non-blank text) — verify the exact accessor name on `StaffElement` via `jet_brains_find_symbol`; the spec says "rest with existing lyric."
   - Found: `dismiss()`, then `new LyricEditor(line, next)` and re-attach (open helper).
   - None: `dismiss()` (no wrap to next line — explicit non-goal).
3. **Open/advance helper** — extract the open sequence (`new LyricEditor`, `setBounds`, prefill, attach listeners, `score.add`, `setVisible`, `requestFocusInWindow`, `score.setActiveLyricEditor`) into a static `LyricEditor.openOn(Score, Line, StaffElement)` so both the action and `advance()` use the same path. Returns the new editor reference.
4. **Prefill on advance**: when `openOn` is given an element with an existing verse-1 lyric, prefill text + `selectAll()` + caret at end (already implemented in Phase 3 constructor; verify `advance` exercises that path).

### Files

**Modified:**
- `songscribe.ui.component.LyricEditor`

### Tests

- **T10** `commit()` non-empty new text emits exactly one `ElementModification(LYRIC)` mutation.
- **T11** `commit()` empty text on element with prior lyric removes the lyric (one mutation).
- **T12** `commit()` empty text on element with no prior lyric emits zero mutations.
- **T13** `commit()` same-text-as-existing emits zero mutations.
- **T14** `advance()` skips rests.
- **T15** `advance()` treats a rest with an existing lyric as eligible.
- **T16** `advance()` at end of line dismisses, doesn't wrap.
- **T17** `advance()` into populated element prefills text + selectAll + caret at end.

### Exit criteria

- Compile clean.
- T10–T17 green.
- Manual: typing text and calling `commit()` from the debugger or via a temporary keybinding produces a visible lyric on the next reflow.

---

## ⏳ Phase 6: Input behavior and validation

**Status:** Pending  <br>
**BlockedBy:** 5  <br>
**Model:** Opus 4.7  <br>
**Effort:** High — Swing input handling is the most error-prone surface: `InputMap`/`ActionMap` for Tab/Enter/Escape, `setFocusTraversalKeys` to stop Tab from traversing, `KeyListener.keyTyped` for Space, `DocumentFilter` length+newline rejection, `DocumentListener` width recompute ordering, and a `focusLost` re-entrancy path that has to honor the `committing` flag set in Phase 5. Use Opus.

### Purpose

Wire keystrokes, focus loss, and input validation. After this phase the editor is fully usable from the keyboard given an external trigger; only the action wiring (Phase 7) and audit (Phase 8) remain.

### Scope

1. **KeyListener / KeyBindings** on the editor — intercept (consume so JTextArea does not handle):
   - `VK_TAB` → `commit()` then `advance()`.
   - `VK_ENTER` → `commit()` then `dismiss()` (no advance).
   - `VK_ESCAPE` → `dismiss()` only (no commit). Skip when `committing == true` per re-entrancy guard.
   - `KeyTyped` with `getKeyChar() == ' '` → `commit()` then `advance()`. Consume so the space character does not enter the document.
   - All other keystrokes pass through to default `JTextArea` handling.
   - Implementation note: prefer `InputMap` / `ActionMap` over raw `KeyListener` for Tab / Enter / Escape since those are part of Swing's focus/traversal handling and need explicit override. Space goes through `KeyListener.keyTyped` since it's a normal printable.
   - **Tab note:** `JComponent.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.emptySet())` is required so Tab reaches the key handler instead of moving focus.
2. **DocumentFilter** on the editor — installed in `attachListeners()`:
   - Reject any insert containing `\n` (silently).
   - Reject any insert that would push the document past `MAX_LENGTH_CHARS`; beep and reject (insert nothing).
3. **DocumentListener** — `insertUpdate` / `removeUpdate` call `recomputeBounds()`. Already stubbed in Phase 3; wire it now after the filter is in place.
4. **FocusListener** — `focusLost` → if `committing == true` return early; else `commit()` then `dismiss()`.
5. **Re-entrancy guard** — verify `commit()`'s `committing` flag (set in Phase 5) covers the focus-lost path: a layout reflow during commit can steal focus, which would re-enter via `focusLost`; the early return is what makes this safe. The Escape handler also checks the flag for symmetry.

### Files

**Modified:**
- `songscribe.ui.component.LyricEditor`

### Tests

- **T18** Tab consumed (commit + advance, no tab character in document).
- **T19** Space consumed (commit + advance, no space in document).
- **T20** Enter consumed (commit + dismiss, no newline in document).
- **T21** Escape cancels (no mutation, dismiss).
- **T22** 33rd character beeps and isn't inserted.
- **T23** Newlines rejected from document model.
- **T24** Re-entrant commit during focus-lost is a no-op (committing flag works).

### Exit criteria

- Compile clean.
- T18–T24 green.
- Manual: full type-edit-tab-edit cycle works keyboard-only on a multi-element line.

---

## ⏳ Phase 7: AddLyricAction wiring

**Status:** Pending  <br>
**BlockedBy:** 6  <br>
**Model:** Haiku 4.5  <br>
**Effort:** Low — replace the temp Phase-3 wiring with the real `openOn` call, add `DISABLE_WHEN_EDITING_TEXT` to `FLAGS`, two tests. Mechanical; Haiku is fine.

### Purpose

Replace the temporary action wiring from Phase 3 with the real implementation, including the `DISABLE_WHEN_EDITING_TEXT` flag so the action itself cannot fire while the editor is open.

### Scope

1. **`AddLyricAction.actionPerformed`** — calls `score.getSelectionCoordinator().getSingleSelectedElement()`, returns silently if null (the existing `enableFromSelection` guarantees a single element when enabled, but defend against race conditions). Then calls `LyricEditor.openOn(score, line, element)` (the static helper introduced in Phase 5).
2. **Add `Flag.DISABLE_WHEN_EDITING_TEXT`** to the `FLAGS` array on `AddLyricAction`.
3. **Verify `enableFromSelection`** already disables when the element has a non-blank verse-1 lyric per the spec (existing skeleton behavior). Adjust only if missing.
4. **Remove the temporary smoke-test wiring** from Phase 3 if not already cleaned up.

### Files

**Modified:**
- `songscribe.ui.action.AddLyricAction`

### Tests

- **T26** `AddLyricAction` carries `DISABLE_WHEN_EDITING_TEXT` and disabled state toggles via `enableFromTextEditingState`.
- **T27** `AddLyricAction.actionPerformed` opens the editor when one element with a blank lyric is selected.

### Exit criteria

- Compile clean.
- T26–T27 green.
- Manual: selecting a note and triggering the action's accelerator opens the editor; the action grays out while the editor is focused.

---

## ⏳ Phase 8: UIAction audit and meta-test

**Status:** Pending  <br>
**BlockedBy:** 7  <br>
**Model:** Sonnet 4.6  <br>
**Effort:** Medium — load-bearing safety phase but largely tedious: walk every `UIAction` subclass, classify mutating vs. non-mutating, add the flag where missing, lock the whitelist, and write the meta-test. Categorization needs care but no novel design. Sonnet is the right level; escalate to Opus only if the audit surfaces an action whose mutation status is genuinely ambiguous.

### Purpose

Lock the editor's "no external mutation while focused" invariant by auditing every mutating `UIAction` and adding `DISABLE_WHEN_EDITING_TEXT` where missing. Encode the audit as a meta-test (T25) so future mutating actions cannot regress this silently.

This is the load-bearing safety phase. The editor's design has no `@Handler` defenses against external mutation — the audit is what makes the design correct.

### Scope

1. **Walk every `UIAction` subclass** using `jet_brains_type_hierarchy(name_path="UIAction", hierarchy_type="sub")` plus `jet_brains_find_implementations` if needed.
2. **Categorize** each subclass as mutating or non-mutating:
   - Mutating: emits an `ElementModification`, `ElementInsertion`, `ElementDeletion`, range/interval addition/removal, `LineKeyChange`, `LineLayoutChange`, etc. — anything covered by the `Mutation` sealed interface.
   - Non-mutating: pure UI/view actions (zoom, pan, view toggle, file open/save which use a different focus path per the spec's Focus Management section).
3. **Add `DISABLE_WHEN_EDITING_TEXT`** to the `FLAGS` array on every mutating action that lacks it. Per spec exploration: candidates include `DeleteAction`, `CutAction`, `PasteAction`, `AddLyricAction` itself (already done in Phase 7), and `NoteOnlyAction` subclasses. Confirm via the audit walk.
4. **Curate the whitelist** as a `static final List<Class<? extends UIAction>>` constant in the test class (or a sibling resource), capturing the final inventory.
5. **T25 meta-test** — iterates the whitelist and asserts each class declares `DISABLE_WHEN_EDITING_TEXT` in its `FLAGS`. Failure message instructs the developer to either add the flag or remove the class from the whitelist with justification.
6. **Header comment in `LyricEditor.java`** documenting the invariant and pointing to T25 so future maintainers understand why the flag matters.

### Files

**Modified:**
- All mutating `UIAction` subclasses missing the flag (final list locked during the audit).
- `songscribe.ui.component.LyricEditor` — invariant doc comment.

**New (test):**
- `LyricEditorActionAuditTest` (or merged into an existing action test file).

### Tests

- **T25** Audit meta-test: every UIAction in the curated mutating-actions whitelist carries `DISABLE_WHEN_EDITING_TEXT`.

### Exit criteria

- Compile clean.
- Full unit-test suite green (`./scripts/test.sh unit`).
- T25 green and the whitelist locked.
- Manual end-to-end: with the editor focused, every mutating accelerator (Delete, Cut, Paste, note-entry shortcuts, etc.) is inert; pressing them does nothing visible until the editor dismisses.

---

## Files / classes summary

**New:**
- `songscribe.ui.component.LyricEditor`
- `songscribe.ui.layout.LyricAnchor` (record, may live nested in `LayoutResult`)
- `LyricEditorActionAuditTest` (or equivalent location)

**Modified:**
- `songscribe.music.StaffElement` — add `setLyricForVerse(int, SyllableRelation, String, Extend)`.
- `songscribe.ui.layout.LayoutResult` — add `getLyricAnchor`.
- `songscribe.ui.layout.LyricRenderMetrics` — add `lyricBoxWidthSs`. (Or owner of `scaledLyricsFont`.)
- `songscribe.ui.layout.LyricBoxLayout` — call `lyricBoxWidthSs` instead of inlining.
- `songscribe.ui.renderer.LyricTextRenderer` — skip rendering for the editor's active element.
- `songscribe.ui.component.Score` — own `activeLyricEditor` reference; expose getter.
- `songscribe.ui.action.AddLyricAction` — `DISABLE_WHEN_EDITING_TEXT`, `actionPerformed`.
- All mutating `UIAction` subclasses missing `DISABLE_WHEN_EDITING_TEXT` (Phase 8 audit).

---

## Test inventory cross-reference

Every test in the spec maps to exactly one phase:

| Test | Phase |
|------|-------|
| T1–T6 | 1 |
| T7–T9 | 2 |
| T10–T17 | 5 |
| T18–T24 | 6 |
| T25 | 8 |
| T26–T27 | 7 |
| T28 | 4 |

---

## References

- Spec: [`specs/lyric-editor.md`](../../../specs/lyric-editor.md)
- Deferred phases: [`specs/lyric-editor-part2.md`](../../../specs/lyric-editor-part2.md)
- [Mutation system](../../../.agents/rules/mutations.md) — `Line.modifyElement`, `ElementField.LYRIC`, `withModification` brackets.
- [Message system](../../../.agents/rules/messages.md) — handler conventions (no editor handler in 1a, but referenced for general patterns).
- [Strings](../../../.agents/rules/strings.md) — externalization conventions (no new strings in 1a).
- [Serena tool usage](../../../.agents/rules/serena.md) — semantic exploration for Java/Kotlin code.
- [Unit conversion](../../../.agents/rules/unit-conversion.md) — `ScaleContext` for staff-space ↔ pixel conversion.
