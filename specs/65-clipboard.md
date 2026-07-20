# Cut/Copy/Paste
Complete the clipboard feature. Cut and copy exist but silently drop every span (tie, beam, tuplet, hairpin, trill) and can emit malformed fragments at range boundaries. Paste is an unimplemented stub and its action is force-disabled. This spec fixes copy fidelity and implements paste as a modal, click-to-place operation with an on-screen insertion point.

**Issue:** vasudeva-server/SongScribe#65

* * *
## Goals
1. **Span-faithful copy** — fully-contained ties, beams, tuplets, trills, and hairpins survive a copy/paste round-trip
  
2. **Well-formed clipboard fragments** — boundary normalization so no orphaned grace note or detached breath mark can reach the clipboard
  
3. **Paste** — implement `handlePaste()`: immediate replace when there is a selection, otherwise a modal click-to-place insertion point
  
4. **Paste mode** — a fully modal state with a themed "Paste mode" overlay anchored to the window, with every action disabled while it is active
  
5. **Lyric-safe insertion** — pasted lyrics carry their text, and syllabic/extend chains are repaired at both seams
  

* * *
## Current State
### What exists
- `ClipboardManager` (`ui/clipboard/ClipboardManager.java`) — a plain `final class`, **not** a singleton. Holds `ArrayList<StaffElement> pasteboard` and nothing else: no message-bus coupling, no span storage. `ScoreView` constructs it (`ScoreView.java:251`) and injects it into `ScoreViewController` and `EditModeManager`. `ScoreView.getPasteboardSize()` (`ScoreView.java:1304`) delegates to it.
  
- `ScoreViewController.handleCopy()` (line 460) — clones each selected element via `StaffElement.clone()` into the pasteboard. `handleCut()` (line 455) is `handleCopy(); song.withModification(this::handleDelete);`.
  
- `ScoreViewController.handleDelete()` (line 474) — the reference range-delete implementation: swallows a trailing breath mark, falls back to a per-element loop for paired grace notes, shifts trailing `xOffsetPx`, repairs lyric neighbors, and confirms ending invalidation via `EndingConfirms.confirmInvalidation`.
  
- `ScoreViewController.handlePaste()` (line 602) — **empty stub**, `// TODO: Implement paste with proper insertion-point visual feedback`.
  
- `PasteAction.musicSelectionDidChange` (`PasteAction.java:50-55`) — unconditionally `setEnabled(false)`, commented `// paste not yet implemented — see issue #410`.
  
- `PreviewElementManager` (`ui/component/score/PreviewElementManager.java`) — owns mouse tracking and insertion-index resolution. `trackMouse` (line 531) converts `ViewPx` → `DocPx` → `Ss` and calls `LayoutResult.findInsertionIndex` (`LayoutResult.java:777`) and `findElementAtXSs` (line 742). `handleClick` (line 709) picks append / replace / insert-between from `xPosSsMatchesElement`.
  
- `GraceModeManager` — the existing modal-sub-state precedent: `isInProgress()`, Escape-cancellable via `ScoreInputHandler.keyPressed` (line 150).
  
- `UIAction.updateEnabledState()` (line 379) — resolves `var scoreView = getScoreView()`, then ANDs a chain of `enableFromXxx(...)` predicates. `enableFromPlaybackState()` + `Flag.DISABLE_WHEN_PLAYING` + `PlaybackStateDidChangeNotification` is the model for a mode-driven global disable.
  
### Gaps this spec closes
- **Spans are dropped.** `RangeElement` and every subclass (`Tie`, `Beam`, `Tuplet`, `Trill`, `Hairpin`/`Crescendo`/`Diminuendo`, `Ending`) have **no** `copy()`/`clone()`. The clipboard cannot represent them.
  
- **Beams do not regenerate.** `PreviewElementManager.applyAutomaticBeaming` (line 1181) auto-beams by beat grouping, but fires **only** from the single-note interactive insert path. There is no bulk re-beam pass. `Beam` is persisted model state (`LineIO`, `XML_BEAMINGS`) and user-overridable via `MusicEditOperations.toggleBeaming`. Pasting without copying beams renders the notes unbeamed.
  
- **Orphan grace notes.** Grace-note pairing is adjacency-based (`Line.isPairedGraceNote` = grace note with a `Glissando`; host is whatever follows). `handleCopy` has no pair awareness, so copying a range ending on a paired grace note yields an orphan that silently re-pairs at the destination.
  
- **Identity-based anchoring.** The model has no IDs. `RangeElement` holds `StaffElement` references and derives indices via `Line.getElementIndex`. Copied spans must be re-anchored to the _pasted clones_, not the originals.
  

* * *
## Design
### Scope boundaries
- **In-process clipboard only.** No `Transferable`/`DataFlavor`, no system pasteboard, no cross-restart persistence. `ClipboardManager` stays app-internal.
  
- **Element ranges only.** Cut/Copy apply to an `ElementSelection` (a contiguous range on one `Line`). Lyric-cell and slide selections are excluded.
  
- **Undo restores the model only.** Selection state is not recorded or restored. `Mutation` and the bracket system are untouched.
  
### Copy
Copy captures a fragment: cloned `StaffElement`s **plus** cloned spans.

**Boundary normalization** — applied on copy, so the clipboard only ever holds well-formed fragments and paste needs no boundary logic:

1. **Trailing breath mark** — mirror `handleDelete`: if the element after the range is a breath mark, include it. Cut and copy behave identically here, so cut→paste round-trips exactly what was removed.
  
2. **Orphan grace note** — if the range's last element is a paired grace note whose host lies outside the range, **drop it** from the fragment.
  
3. **Terminal** — existing `ClipboardManager.addElement` behavior stands: `FINAL_DOUBLE_BARLINE` normalizes to `DOUBLE_BARLINE`.
  
4. **Repeats** — `REPEAT_LEFT`/`REPEAT_RIGHT` copy verbatim. No balance validation; an unbalanced repeat is reachable by hand anyway.
  

**Spans** — add `copy()` to `RangeElement` and each subclass, taking the new anchor/end `StaffElement`s so the clone re-anchors to the pasted instances.

- Copy a span **only if both its anchor and end are inside the range**. Partially-overlapping spans are dropped, not trimmed.
  
- `Beam` is copied like any other span. This preserves both automatic and manual beaming verbatim, and is required for correctness given there is no bulk re-beam pass.
  
- `Ending` is a `RangeElement` and is copied when fully contained, per the same rule.
  

**Cut** — reorder to confirm before copying: run the ending-invalidation confirmation first, and only then touch the clipboard. Declining leaves both the clipboard and the score untouched (today a declined cut silently degrades into a copy).
### Paste with a selection — immediate replace
`Cmd+V` with an active selection does delete + insert in a single modification bracket. No insertion-point UI, no paste mode.

Extract the element-range branch of `handleDelete` into a shared helper taking `(line, begin, end, boolean confirm)` and returning the **effective deleted range** (after breath-mark extension), which paste needs as its insert index. Both `handleDelete` and paste-replace call it. Paste passes `confirm = false` — delete-related confirmations are skipped during paste.

Selection is cleared after paste.
### Paste without a selection — paste mode
`Cmd+V` with no selection enters **paste mode**, a modal state modeled on `GraceModeManager`.

**Insertion point**

- Reuses `PreviewElementManager`'s positioning, with one deviation: the insertion point is **never on an element**. Where the preview logic resolves "on element N" (`xPosSsMatchesElement`), the insertion point goes **before N**. Otherwise it uses the same in-between position the preview element already uses.
  
- **Rendering** — a line spanning 1 ss above the staff to 1 ss below, in the preview element color (`ScoreView.getPreviewElementColor()` → `FlatLafKey.SCORE_PREVIEW_ELEMENT_COLOR`). Width is **3 screen pixels** — logical, not device, pixels: 6 device pixels on a Retina display, and constant regardless of zoom. Height is in `Ss` and therefore scales with zoom.
  
- The normal preview element is **suppressed** while paste mode is active.
  
- Every line is a valid target. The terminal is the only positional restriction; nothing inserts after it, handled by clamping the index.
  

**Resolution**

- **Click** on a line, or **Return/Enter** → insert at the insertion point.
  
- **Escape** → cancel. Escape takes priority over the existing `Mode.SELECT` → `DeselectCommand` path, exactly as grace mode does. With a selection present, the first Escape cancels the paste and leaves the selection intact; a second Escape deselects.
  
- **Click outside any line** → cancel.
  
- Return with no insertion point (mouse outside the score, or never moved) does nothing; paste stays pending. There is no keyboard-only fallback position.
  
- Paste mode exits after one placement. The clipboard content remains, so another `Cmd+V` starts a new paste.
  

**Modality** — every action is disabled while paste mode is active, including Cut/Copy/Paste/Delete themselves and app-level actions. `Cmd+V` during paste mode is inert. Escape, click, and Return are handled at `ScoreInputHandler` level, below the action layer, so they remain reachable.

Mechanism, following the `DISABLE_WHEN_PLAYING` precedent:

- `ClipboardManager` gains a `pasteModeActive` flag; its setter posts a new `PasteModeDidChangeNotification` (carrying a boolean `active`). This gives `ClipboardManager` a message-bus dependency it does not have today.
  
- `UIAction` gains `@Handler pasteModeDidChange(PasteModeDidChangeNotification)` calling `updateEnabledState()`.
  
- `UIAction` gains `enableFromPasteMode(scoreView)`, called **first** in the predicate chain in `updateEnabledState()`, before `enableInAdjustmentMode(scoreView)`. It reads paste mode from the `ClipboardManager` reached via the `ScoreView` that `updateEnabledState()` already resolves, and returns `false` when paste mode is active. No new flag is needed — the rule is blanket.
  
- `ScoreView` gains an `isPasteModeActive()` delegate, mirroring the existing `getPasteboardSize()` delegate.
  

**Overlay** — a "Paste mode" indicator, shown on entering paste mode and removed when paste completes or is cancelled.

- Positioned 10px below the top of the viewport, horizontally centered, **outside the scroll pane** so it stays anchored while the score scrolls.
  
- Implemented on the root pane's `JLayeredPane`. The e2e overlay (`E2ETest.createStatusOverlay`, `src/test/java/songscribe/e2e/E2ETest.java:164`) is the visual precedent but is **not reusable**: it is test-only, bottom-anchored, uses an always-on-top `JWindow`, and hardcodes its font and colors. The layered-pane approach avoids a separate native window that would need to track MainFrame moves and resizes.
  
- Styling comes from `FlatLafProps`/`FlatLafKey` with new `FlatLaf.properties` keys (background, text color, corner radius), per the FlatLaf Properties guide — not hardcoded as in the e2e overlay.
  
- Text: "Paste mode" plus a hint line naming the exits (click or Return to place, Escape to cancel), since this is a novel modal state whose exits are not discoverable. Both are user-facing strings and belong in the resource bundle per the Strings guide.
  
### Lyrics
Pasted elements carry their lyrics — `StaffElement.clone()` already copies the `lyrics` list.

Paste must call the same adjustment helpers the single-note insert path already calls (`PreviewElementManager.java:1145-1148`) at **both seams** — before the insert index and after the pasted range:

- `Line.adjustSyllablesForNeighborChange`
  
- `Line.adjustSyllablesForSuccessorAfterInsertion`
  
- `Line.adjustExtendsForInsertion`
  

Without this, splicing a fragment between a `BEGIN`/`MIDDLE` syllabic element and its continuation, or between a melisma `START` and its carriers, leaves dangling syllabic and extend state.

**Verses are a non-issue today.** Verse count is derived by scanning lyrics present (`LyricLayoutBuilder.collectVerses`), and `LyricEditor` only ever writes verse 1 (`CURRENT_VERSE = 1`). Nothing can author verse 2+, so no fragment can carry them. Noted as a hazard for when multi-verse authoring lands: pasting higher-verse lyrics would grow the song-wide lyrics band, since band height is the max verse count across all lines.
### Deliberately out of scope
- **Beams at the seams.** Pasting mid-line changes the beat context of following notes, whose beams may now be wrong. Paste affects only what it inserts; surrounding beams are left alone and users can fix them with `toggleBeaming`. Note that `SelectionCoordinator.repairBeamings` is already not wired into the delete path, so this is a pre-existing pattern rather than a new gap.
  
- **Selection restoration on undo.**
  
- **System clipboard interoperability.**
  

* * *
## Open Questions
- **Line overflow.** There is no reflow, and line spacing is being reworked in another branch. Pasting more elements than a line can hold has no defined behavior yet. **To be fully dealt with when this spec is converted into an implementation plan**, against whatever the line-spacing rework lands.
  

* * *
## Testing
Unit tests for the model and clipboard layers only. No e2e tests; the paste-mode state machine and insertion-point rendering are verified by hand.

Coverage:

- Span copy and re-anchor: a fully-contained tie/beam/tuplet/hairpin/trill survives a round-trip and anchors to the pasted clones, not the originals
  
- Partially-overlapping spans are dropped
  
- Boundary normalization: trailing breath mark included; orphan grace note dropped; `FINAL_DOUBLE_BARLINE` normalized
  
- Cut confirmation ordering: a declined ending-invalidation confirm leaves the clipboard untouched
  
- Lyric seam repair: syllabic and extend chains stay valid when pasting into a hyphenated word and into a melisma
  
- Paste-replace is a single modification bracket (one undo step)
