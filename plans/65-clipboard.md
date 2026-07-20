# Cut/Copy/Paste — Issue #65
**Type:** Master plan  
**Spec:** [../specs/65-clipboard.md](../specs/65-clipboard.md)  
**Created:** 2026-07-16  
**Status:** Complete  
**BlockedBy:** —

* * *
## Key code touchpoints
- `src/main/java/songscribe/dom/RangeElement.java` — extends `LineElement`; anchor/end `StaffElement` refs (:55, :58); `getAnchorElementIndex()` (:209) / `getEndElementIndex()` (:221) resolve via the **anchor's own** `getLine()`, not a passed-in line. Subclasses: `Tie`, `Beam` (both stateless), `Tuplet` (`grade`, `verticalPositionSs` :76-77), `Trill` (`yPositionSs` :44), `Hairpin` (`x1ShiftSs`, `x2ShiftSs`, `yShiftSs` :41-47; sealed → `Crescendo`/`Diminuendo`, which add no fields), `layout/Ending.java` (`yPositionSs` :128; `repeatSplitIndex` :129 / `bracketRanges` :130 are derived caches, written only by `computeBracketRanges` :210-324).

- `src/main/java/songscribe/dom/LineElement.java` — `userXOffsetSs` (:66), `userYOffsetSs` (:75), margins (:78-81), `positionSs` (:57), `parentLine` (:51). Every `RangeElement` inherits these.

- `src/main/java/songscribe/dom/Line.java` — spans in one `List<RangeElement> rangeElements` (:63); `song` back-reference (:65); `getRangeElements()` (:1591); `addElement(int, StaffElement)` (:202) **re-parents via `setLine`/`setParentLine` (:211-212)** and also removes a destination tuplet (:214-217) and endings invalidated by insertion (:218-221); `addRangeElement` (:1509) re-parents **only the span**, not its anchor/end; `removeRange` (:890); grace pairing `isPairedGraceNote` (:971) / `isHostOfPairedGraceNote` (:986); lyric helpers `adjustSyllablesForNeighborChange` (:321), `adjustSyllablesForSuccessorAfterInsertion` (:386), `adjustExtendsForInsertion` (:435).

- `src/main/java/songscribe/dom/StaffElement.java` — copy constructor (:159-183): copies `line` (:166) and `parentLine` (:172) **by raw reference**, deep-copies attachments (:174-177) and articulations, and copies the `lyrics` list (:182) — safe, since `Lyric` is an immutable record (`Lyric.java:55`). **Shares** the `slide` object (:168). `clone()` (:197-201) delegates to it. Nested `Slide`/`Glissando`/`Fall` (:832-855) hold **only** `transient` render/hit-test caches — no authored state.

- `src/main/java/songscribe/dom/Attachment.java` / `Articulation.java` — `ownerElement` back-refs (:49, :62). `AnnotationAttachment.copy` (:74-77) and `TempoChangeAttachment.copy` (:50-53) are **shallow w.r.t. their payloads**; `Annotation` and `Tempo` are mutable classes.

- `src/main/java/songscribe/ui/clipboard/ClipboardManager.java` — plain non-singleton; `addElement` (:100-106) normalizes `FINAL_DOUBLE_BARLINE`. Constructed once at `ScoreView.java:251`; `ScoreView` itself is constructed once (`MainFrame.java:496`) and only its `song` is swapped (`setSong` :839-840).

- `src/main/java/songscribe/ui/component/ScoreViewController.java` — `handlePasteboardOp` (:441, requires score focus :443-445), `handleCut` (:455), `handleCopy` (:460, `clear()` at :465), `handleDelete` (:474-585; element-range branch :501-569, `EndingConfirms` :506-509, `clearSelection` :516, breath-mark extension :526-533, glissando strip :536-542, `xOffsetPx` gap-fill :546-552, range bracket :554-568, tail :583-584), `deleteSelection` (:592), `deleteNote` (:634, returns a count that **excludes** cascade-deleted breath marks :682-688), `handlePaste` stub (:602).

- `src/main/java/songscribe/layout/InsertionSpacingCalculator.java` — `InsertionResult`/`fitsWithinLine` (:55-79), `fitsWithinMarginSs` (:77-79, private), `calculateInsertion` (:155-227, **single-element only**; seeds from the predecessor at :204, `insertIndex == 0` via `calculateFirstElementXSs` :185), `calculateNextElementXSs` (:136), clone-and-measure precedent `hasRoomForFall` (:294-329), `createLightweightColumn` (:361, private), `projectedWidthWithLastShiftSs` (:336, private). `ElementColumn.setXSs` (`ElementColumn.java:361`) is package-private.

- `src/main/java/songscribe/layout/HorizontalSpacingCalculator.java` — `calculateNextColumnXSs` (:215, public), `DEFAULT_COLUMN_GAP_SS` (:84, public).

- `src/main/java/songscribe/layout/LayoutResult.java` — `findInsertionIndex` (:777-843): over an element head returns that element's index (:788-792); every return path is bounded by `effectiveElementCount()`, max `elementCount` (:813, :816-818, :842) — **no clamping needed**.

- `src/main/java/songscribe/ui/component/score/PreviewElementManager.java` — singleton `INSTANCE` (:127); coordinate conversion (:540-556), insert path with lyric seams + `xOffsetPx` + trailing shift (:1120-1168), suppression precedents `modeDidChange` (:136-144) / `playbackStateDidChange` (:146-153), `restorePreviewElement` (:975). Its inserted element is always a fresh `type.newInstance()` (`EditModeManager.java:219`) — it never inserts a cross-line clone, so it does not exercise re-parenting.

- `src/main/java/songscribe/ui/edit/GraceModeManager.java` — the modal-manager pattern: `isInProgress()` (:163), constructed in `EditModeManager` (:99), quasi-singleton via a static `instance` (:152), static `isActive()`, `keyPressed` (:528-538), consume-first mouse hooks, single-funnel exit `finish(boolean)` (:720).

- `src/main/java/songscribe/ui/component/score/LineComponent.java` — consume-first grace slots in `mouseMoved` (:623-625), `mouseDragged` (:638-640), `mouseClicked` (:659-661), `mousePressed` (:689-691).

- `src/main/java/songscribe/ui/component/ScoreInputHandler.java` — `keyPressed` Escape routing, grace-mode-first then `Mode.SELECT` → `DeselectCommand` (:150-166); `mouseClicked` on `ScoreView` (:63-70); dead `VK_ENTER` binding (`KEY_CODES` :186, `KeyAction` :247-268 — no branch handles it; `handleSelectionArrow` :311-328 and `handlePreviewNudge` :436-456 both ignore it).

- `src/main/java/songscribe/ui/action/UIAction.java` — `updateEnabledState` chain (:379-406): `enableInAdjustmentMode` → `enableInSelectMode` → `enableFromTextEditingState` → `enableFromPlaybackState` → `enableFromMidiState` → `enableFromDialogVisibility` → `enableFromGraceModeState` → `enableInRestMode` → `enableFromSelectionSize` → `enableFromBarSelection` → `enableFromSelection` → `enableFromDurationSelection` → `enableFromSongState`. Grace precedent: `enableFromGraceModeState` (:507-510) reads the static `GraceModeManager.isActive()`. `PasteboardAction` (:57-79), `PasteAction` force-disable override (:50-55), constructor flags `DISABLE_WHEN_PLAYING` only (:40-47). `Flag.DISABLE_IN_GRACE_MODE` (:77), `Flag.DISABLE_WHEN_EDITING_TEXT` (:72).

- `src/main/java/songscribe/ui/component/ScoreView.java` — `getPasteboardSize()` delegate (:1304), `getPreviewElementColor()` (:132), `requireScrollPane()` (:632), `setSong` (:839-840).

- `src/main/java/songscribe/ui/component/MainFrame.java` — **there is no `WindowFocusListener`.** App-background hooks: Desktop API `AppForegroundListener.appMovedToBackground` → `ActivationGate.activate()` (:526-527) on macOS, with a `WindowAdapter.windowDeactivated` fallback when `!usingDesktopApi` (:535-546). Content assembly `initContent()` (:617-664), scroll pane added at :661.

- `src/main/java/songscribe/ui/component/ActivationGate.java` — `install()` creates the pane, sets it **invisible** (:52), adds a `mousePressed → deactivate()` listener (:53-58), and calls `frame.setGlassPane` **once** (:60). It caches its own `static @Nullable JPanel glassPane` (:39) and **never re-reads `frame.getGlassPane()`**. Visible only transiently while the app is backgrounded. **Its glass pane must not be swapped out.**

- `src/main/java/songscribe/ui/renderer/LineRenderer.java` — render pass order, `renderPreviewElement` last (:166); strip-transform recipe in `ui/renderer/LyricTextRenderer.java` (:79-98) via `LineInvariants.getViewPixelsPerStaffSpace()` (`LineInvariants.java:186`).

## Plan
### Status Dashboard
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1a  | [Span Copy and Clone Hygiene](#-phase-1a-span-copy-and-clone-hygiene) | ✅ Complete | Sonnet 5, medium |
| 1b  | [Fragment and Clipboard Storage](#-phase-1b-fragment-and-clipboard-storage) | ✅ Complete | Sonnet 5, low |
| 2   | [Range-Delete Helper Extraction](#-phase-2-range-delete-helper-extraction) | ✅ Complete | Sonnet 5, low |
| 3   | [Copy Fidelity and Cut Ordering](#-phase-3-copy-fidelity-and-cut-ordering) | ✅ Complete | Opus 4.8, medium |
| 4   | [Fragment Insertion, Fit Check, Paste-Replace](#-phase-4-fragment-insertion-fit-check-paste-replace) | ✅ Complete | Fable 5, high |
| 5   | [Paste-Mode State and Action Modality](#-phase-5-paste-mode-state-and-action-modality) | ✅ Complete | Sonnet 5, low |
| 6   | [PasteModeManager Input Routing](#-phase-6-pastemodemanager-input-routing) | ✅ Complete | Opus 4.8, high |
| 7   | [Insertion-Point Rendering and Overlay](#-phase-7-insertion-point-rendering-and-overlay) | ✅ Complete | Sonnet 5, medium |
| 8a  | [Copy and Clone Tests](#-phase-8a-copy-and-clone-tests) | ✅ Complete | Sonnet 5, low |
| 8b  | [Paste and Modality Tests](#-phase-8b-paste-and-modality-tests) | ✅ Complete | Sonnet 5, medium |
| 9   | [Architecture Doc](#-phase-9-architecture-doc) | ✅ Complete | Sonnet 5, low |

## ✅ Phase 1a: Span Copy and Clone Hygiene
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Sonnet 5, medium — mechanical per-subclass work across ~12 files plus two aliasing fixes; the slide fix changes existing clone semantics, so the unit suite gates it

### Tasks
1. Add a **template method** to `RangeElement` (`dom/RangeElement.java`):

   ```java
   public final RangeElement copy(StaffElement newAnchor, StaffElement newEnd) {
       var copy = createCopy(newAnchor, newEnd);
       // LineElement-level user state, carried in ONE place so a new subclass cannot forget it
       copy.setUserXOffsetSs(getUserXOffsetSs());
       copy.setUserYOffsetSs(getUserYOffsetSs());
       copy.setMargins(...);          // marginTop/Right/Bottom/LeftSs (LineElement.java:78-81)
       copy.setPositionSs(getPositionSs());
       return copy;
   }
   protected abstract RangeElement createCopy(StaffElement newAnchor, StaffElement newEnd);
   ```
   Do **not** set `parentLine` here — `Line.addRangeElement` (:1509) does it on insert. Do **not** copy derived caches.

2. Implement `createCopy` in the stateless subclasses: `Tie`, `Beam` — construct with the new anchor/end only.

3. Implement `createCopy` in the stateful subclasses: `Tuplet` (carry `grade`, `verticalPositionSs`), `Trill` (carry `yPositionSs`), `Crescendo`/`Diminuendo` (carry the `Hairpin` fields `x1ShiftSs`, `x2ShiftSs`, `yShiftSs`), `Ending` (carry `yPositionSs` only; `repeatSplitIndex` and `bracketRanges` are derived caches recomputed by `computeBracketRanges`).

4. Fix the payload aliasing in the `StaffElement` copy constructor's attachment path (`StaffElement.java:174-177`). These are **shallow with respect to their mutable payloads**:
   - `AnnotationAttachment.copy` (:74-77) shares its `Annotation`. This is a **live bug**: `VerticalAdjustment.adjustAnnotation` (:201-214) mutates the `Annotation` in place, so dragging a *pasted* annotation moves the *original*. Deep-copy the `Annotation`.
   - `TempoChangeAttachment.copy` (:50-53) shares its `Tempo`. No in-place mutator today (`TempoChangeDialog` :85 reassigns the field), so this is latent — deep-copy it anyway so `copy()` means one thing across the hierarchy.

5. Stop sharing `slide` (`StaffElement.java:168`). Add a copy method to the nested `Slide` hierarchy (`StaffElement.java:832-855`) and use it in the copy constructor. **Rationale is cache hygiene, not data sharing:** `Glissando`/`Fall` hold *only* `transient` render/hit-test caches, so a shared instance corrupts hit-testing once both the original and the pasted clone are on-screen and each render pass overwrites the other's cached geometry. A fresh instance with cleared caches is the correct clone.

6. Gate: `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh unit` → all green (task 5 changes existing clone semantics).

## ✅ Phase 1b: Fragment and Clipboard Storage
**Status:** Complete  
**BlockedBy:** 1a  
**Recommended model/effort:** Sonnet 5, low — one record and a field swap on `ClipboardManager`; compile gates it

### Tasks
1. Add the `Fragment` record in `songscribe.ui.clipboard`, embedding this lifecycle diagram in its header — it is the one place "clone + re-anchor" is defined, used by both capture and every paste:

   ```
     copy:   Line ──capture(line,begin,end)──> Fragment{elements[], spans[]}  ──> ClipboardManager.fragment
                       ├─ effectiveDeleteEnd() extends past trailing breath mark
                       ├─ drop orphan paired grace note at the tail
                       ├─ clone elements → IdentityHashMap<orig,clone>
                       ├─ FINAL_DOUBLE_BARLINE → DOUBLE_BARLINE
                       └─ span kept iff BOTH endpoints ∈ map keys
                             └─ span.copy(map[anchor], map[end])

     paste:  ClipboardManager.fragment ──instantiate()──> Fragment{fresh clones, fresh spans}
                                                               │
             (the stored Fragment is NEVER inserted, so paste N times ⇒ N independent results)
   ```

   ```java
   public record Fragment(List<StaffElement> elements, List<RangeElement> spans) {
       public static Fragment capture(Line line, int begin, int end)   // phase 3 fills this in
       public Fragment instantiate()   // clone elements + re-anchor spans onto the clones (uses RangeElement.copy)
   }
   ```
   Because the stored `Fragment` is never itself inserted, repeated pastes are independent by construction.

2. `ClipboardManager`: replace `pasteboard` with a single `@Nullable Fragment fragment` field. `clear()` sets it to null. Keep the `FINAL_DOUBLE_BARLINE` → `DOUBLE_BARLINE` normalization (currently `addElement` :100-106) by moving it into `Fragment.capture`. `getPasteboardSize()` (`ScoreView.java:1304`) becomes `fragment == null ? 0 : fragment.elements().size()`.

3. Do **not** clear the clipboard on document close, and do not otherwise change `ClipboardManager`'s lifetime. It is app-global (constructed once at `ScoreView.java:251`; `ScoreView` is never recreated, only its `song` swapped at :839-840), and clones retain `line` (`StaffElement.java:166`) → `Line.song` (`Line.java:65`), so a copy followed by a document close retains that one `Song` until the next copy replaces it. This is bounded, harmless, and pre-existing. Clearing on close would fix it at the cost of copy-in-A / paste-in-B, which works today; neither the retention nor cross-document paste is in scope for #65.

4. Gate: `./scripts/compile.sh` → SUCCESS.

## ✅ Phase 2: Range-Delete Helper Extraction
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Sonnet 5, low — a verbatim extraction plus one small pure query; existing unit suite gates semantics  
**Required reading:** `.agents/guides/mutations.md`

### Tasks
1. Extract the breath-mark extension rule (`handleDelete` :526-533) into a **pure query** on `ScoreViewController`: `static int effectiveDeleteEnd(Line line, int begin, int end)` — returns `end` extended past a trailing breath mark. Three call sites share it: `handleDelete`/the helper below, `handleCopy` (phase 3), and the fit check's delete range (phase 4). It must not mutate.

2. In `ScoreViewController`, extract the element-range branch of `handleDelete` (:501-569) into a **confirmation-free, `void`** mutation helper `deleteElementRange(Line line, int begin, int end)`. It returns nothing: paste-replace inserts at `begin`, and the fit check obtains the effective end from `effectiveDeleteEnd()` *before* mutating — so no caller needs an extent threaded back out. Do **not** add extent tracking to `deleteSelection` (:592) / `deleteNote` (:634); `deleteNote`'s count deliberately excludes cascade-deleted breath marks (:682-688) and must stay that way.

3. Confirmation stays at the call sites: `handleDelete` keeps its `hasEndingInvalidatedByDeletion` + `EndingConfirms.confirmInvalidation` check (:506-509) and `clearSelection` (:516) in their current order, then calls the helper.

4. Move the branch body verbatim, preserving operation order and the existing internal `withModification` bracket placement (grace-pair path :521-522, range path :554-568; callers may add their own outer bracket — brackets nest): grace-pair fallback (`isHostOfPairedGraceNote(begin)` → `deleteSelection` loop), breath-mark extension (now via `effectiveDeleteEnd`), glissando strip (:536-542), `xOffsetPx` gap-fill (:546-552), lyric adjustments + `removeRange`.

5. Gate: `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh unit` → all green (delete semantics must not change).

## ✅ Phase 3: Copy Fidelity and Cut Ordering
**Status:** Complete  
**BlockedBy:** 1b, 2  
**Recommended model/effort:** Opus 4.8, medium — boundary normalization and containment semantics interact; each rule is small but the composition has edge cases

### Tasks
1. Implement `Fragment.capture(line, begin, end)`. Compute the effective range first via `effectiveDeleteEnd(line, begin, end)` (phase 2 — do not restate the breath-mark rule); then, if the last included element is a paired grace note (`line.isPairedGraceNote`) whose host lies outside the range, drop it from the fragment.

2. Clone the effective range, building an original→clone `IdentityHashMap` as you go. Apply the `FINAL_DOUBLE_BARLINE` → `DOUBLE_BARLINE` normalization here (moved from `addElement` in phase 1b). Repeats copy verbatim with no balance validation.

3. Capture spans: for each `RangeElement` in `line.getRangeElements()`, include it iff **both** its `anchorElement` and `endElement` are keys of the clone map (this single rule implements "fully contained; partially-overlapping dropped" and automatically excludes spans touching the dropped orphan grace note); store `span.copy(map.get(anchor), map.get(end))`.

4. Rework `handleCopy` (:460-472) to `clipboardManager.setFragment(Fragment.capture(line, begin, end))` — one assignment, therefore **one** `ClipboardDidChangeNotification` per copy (phase 5), not one per element.

5. Reorder `handleCut` (:455-458): run the ending-invalidation confirm **first** (`line.hasEndingInvalidatedByDeletion` over the selection + `EndingConfirms.confirmInvalidation`); declining returns with clipboard and score untouched. Then `handleCopy()`, then one `song.withModification(() -> deleteElementRange(...))` with no further confirmation, preserving the selection-clearing and tail behavior (`clearSavedActionStates`/`deselect`, :583-584) the delete path performs today.

6. Gate: `./scripts/compile.sh` → SUCCESS.

## ✅ Phase 4: Fragment Insertion, Fit Check, Paste-Replace
**Status:** Complete  
**BlockedBy:** 3  
**Recommended model/effort:** Fable 5, high — the one phase that designs something novel: a fragment-aware spacing/fit algorithm composed from package-private layout internals with no direct precedent, plus delete-range seams, xOffsetPx bookkeeping, and single-bracket atomicity  
**Required reading:** `.agents/guides/mutations.md`, `.agents/guides/option-dialogs.md`

**Rebase note:** branch `330-element-spacing` (line-spacing rework) has not started — zero commits beyond its merge-base. It reworks the spacing internals this phase composes but does **not** introduce reflow, so "line full" stays a real state and this fit check is not throwaway work. Whichever of the two branches lands second rebases; phase 8b's behavioral fit tests are the net that catches a break, which is why they must be written in `Line`/`Ss`/`song.getLineWidthSs()` terms and never against the package-private column internals.

### Tasks
1. Add a fragment-aware fit/spacing method to `InsertionSpacingCalculator` (it must live in `songscribe.layout`: `createLightweightColumn` :361 and `ElementColumn.setXSs` are package-private). Embed this diagram in its header:

   ```
     pred        ┌──────── fragment clones ────────┐        succ ... tail
      │          │                                 │          │
      ●──gap─────●────gap────●────gap────●─────gap─●──────────●─────●
      │          c0          c1          c2        │          │
      └ seed: elementXSs(pred, layout)             └ trailing shift = ONE delta
        (or calculateFirstElementXSs when insertIndex == 0)     applied to succ..tail

      deleteRange present (paste-replace) ⇒ pred/succ are the elements ADJACENT to the range
      projected width = last clone right edge + shift  →  fitsWithinMarginSs(song.getLineWidthSs())
   ```
   Signature shape: `calculateFragmentInsertion(Line line, List<StaffElement> fragment, int insertIndex, @Nullable <deleted-range> deleteRange, @Nullable LayoutResult layout)` returning per-clone X positions (Ss), the single trailing shift, the projected line width, and a fits-within-line verdict (reuse `fitsWithinMarginSs` :77-79). Chain the fragment internally via `HorizontalSpacingCalculator.calculateNextColumnXSs` (:215) over lightweight columns, seeding from the predecessor exactly as `calculateInsertion` (:155-227) does. `calculateInsertion`'s own orchestration is single-element and does **not** generalize — reuse its primitives, don't adapt it in place. Follow the clone-and-measure precedent of `hasRoomForFall` (:294-329): **never mutate the line.**

2. Add `tryInsertFragment(Line line, int insertIndex, @Nullable <deleted-range> deleteRange)` to `ScoreViewController`, returning an outcome (`INSERTED` | `LINE_FULL` | `EMPTY`). It runs the fit check, and on `LINE_FULL` shows the "line full" error (per the OptionDialogs guide; reuse or extend the existing `ERROR_LINE_FULL_*` string family) and mutates nothing. **Callers decide recovery** — this is the single implementation shared by paste-replace (task 5) and paste-mode placement (phase 6). `PasteModeManager` receives an explicit `ScoreViewController` reference at construction to reach it.

3. Inside `tryInsertFragment`, on a fit: `var f = clipboardManager.getFragment().instantiate()` (fresh clones every paste), set each clone's `xOffsetPx` from the calculator result (`ScaleContext.ssToRoundedPx`), insert the clones with `line.addElement(insertIndex + k, clone)` — which re-parents each clone via `setLine`/`setParentLine` (`Line.java:211-212`) — then apply the single trailing shift to every element after the fragment (mirror `PreviewElementManager.java:1161-1166`), then add `f.spans()` via `line.addRangeElement`.

   **Hard ordering constraint — comment this at the call site:** every clone must be inserted *before* the first `addRangeElement`. `addRangeElement` (:1509) re-parents only the span, not its anchor/end, and `getAnchorElementIndex()` (:209) resolves through the anchor's **own** `getLine()`. A span added while its anchors still carry the source line's back-reference makes `addElement`'s `isInvalidatedByInsertion` sweep (:218-221) evaluate it against the wrong line, yielding a wrong index or `-1`.

   **Accepted loss — document in a comment:** `line.addElement` removes a destination tuplet the insert point falls inside (:214-217) and drops endings invalidated by the inserted element types (:218-221). Paste inherits this; a paste into a tuplet destroys that tuplet. It occurs inside the paste's own undo bracket, so a single undo restores it. This matches what any single-element insert already does and is deliberately not special-cased.

4. Repair lyric seams inside the same bracket, mirroring the single-note insert path (`PreviewElementManager.java:1145-1148`): before inserting — `line.adjustSyllablesForNeighborChange(index - 1, null)` and `line.adjustExtendsForInsertion(index)`; after inserting all N clones — `line.adjustSyllablesForSuccessorAfterInsertion(index + N - 1)`.

5. Implement the selection branch of `handlePaste` (:602): empty fragment → no-op. Otherwise run **one** `song.withModification { deleteElementRange(begin, end); tryInsertFragment(begin, deleteRange); }` (single undo step), passing the selection's effective delete range (`effectiveDeleteEnd`) to the fit check. On `LINE_FULL` the bracket must produce **no** mutation and the selection stays intact; on `INSERTED`, clear the selection.

6. Gate: `./scripts/compile.sh` → SUCCESS.

## ✅ Phase 5: Paste-Mode State and Action Modality
**Status:** Complete  
**BlockedBy:** 4  
**Recommended model/effort:** Sonnet 5, low — notification and enablement wiring with exact precedents to mirror (playback/grace twins)  
**Required reading:** `.agents/guides/messages.md`

### Tasks
1. Add `PasteModeDidChangeNotification` (carrying `boolean active`) in `songscribe.message.notification`, modeled on `GraceModeStateDidChangeNotification` (immutable, final field + getter, extends `Message`).

2. Add `ClipboardDidChangeNotification` (no payload) — it does not exist today — and post it from `ClipboardManager` whenever content changes (`clear`, `setFragment`). Without it, Paste's enabled state would not refresh after the first copy, since enablement currently only re-evaluates on selection changes. Post once per content change, never per element.

3. Create `PasteModeManager` in `ui/edit` as a skeleton owning the mode state, mirroring `GraceModeManager`: a private `active` flag, `isInProgress()`, and a static `isActive()` backed by a static `instance` field (`GraceModeManager.java:152` precedent). Constructed in `EditModeManager` alongside the grace manager (`:99`), holding the `ClipboardManager`, `ScoreView`, and `ScoreViewController` references it needs. The setter sets the field **before** `MessageCenter.post(new PasteModeDidChangeNotification(active))` — handlers must read the new state during synchronous dispatch.

   The flag lives here, **not** on `ClipboardManager`: paste mode is UI modality, not clipboard content, and this mirrors the grace twin exactly. No `ScoreView.isPasteModeActive()` delegate is needed.

4. `UIAction`: add `@Handler pasteModeDidChange(PasteModeDidChangeNotification)` calling `updateEnabledState()`, and a new `enableFromPasteMode()` predicate returning `!PasteModeManager.isActive()` — mirroring `enableFromGraceModeState` (:507-510), which reads the static directly. Call it **first** in the chain in `updateEnabledState()` (:379-406). Blanket rule — no new `Flag`, and no `saveActionStates`/`restoreActionStatesWithFlag`: those exist in grace mode to preserve *selected* (toggle) state, which paste mode never touches. Exiting simply re-runs `updateEnabledState()`.

5. In `PasteboardAction`, add `@Handler clipboardDidChange(...)` applying the same enablement refinement as its `musicSelectionDidChange` (:57-74).

6. Delete `PasteAction`'s force-disable `musicSelectionDidChange` override (`PasteAction.java:50-55`, stale issue-#410 comment included) so the base `PasteboardAction` enablement (fragment non-empty) applies.

7. **Add `Flag.DISABLE_IN_GRACE_MODE` and `Flag.DISABLE_WHEN_EDITING_TEXT` to `PasteAction`'s constructor flags** (`PasteAction.java:40-47`, currently `DISABLE_WHEN_PLAYING` only). The force-disable deleted in task 6 is what masks their absence today, so this must land in the same change.
   - `DISABLE_IN_GRACE_MODE` is **required for correctness**: grace mode runs with the score focused, so `handlePasteboardOp`'s `!score.isFocusOwner()` guard (:443-445) does not fire, and Cmd+V mid-grace-mode would enter paste mode on top of an in-progress grace mode — two live modal managers. This is the mirror of phase 6 task 6, which blocks the reverse.
   - `DISABLE_WHEN_EDITING_TEXT` is cosmetic but correct: the focus guard already prevents mutation while a `LyricEditor` holds focus, so without the flag Edit ▸ Paste merely appears enabled and silently no-ops.
   - `CutAction`/`CopyAction`/`DeleteAction` carry neither flag and must stay as they are — they are gated by music selection, which is empty in both states. `PasteAction` is the first pasteboard action enabled purely by clipboard contents, which is why it alone needs them.
   - `LyricEditorActionAuditTest` will **not** catch a regression here: it asserts over a hardcoded whitelist of *toolbar* actions, and the pasteboard actions are Edit-menu actions that were never in it. Do not expand that whitelist as part of this change.

8. Gate: `./scripts/compile.sh` → SUCCESS.

## ✅ Phase 6: PasteModeManager Input Routing
**Status:** Complete  
**BlockedBy:** 5  
**Recommended model/effort:** Opus 4.8, high — modal state machine with mouse/keyboard routing priorities and multiple cancel paths  
**Required reading:** `.agents/guides/option-dialogs.md`

### Tasks
1. Fill in `PasteModeManager`'s lifecycle (skeleton from phase 5), embedding this state-machine diagram in its header. Entering sets `active = true` and posts. Enter from `handlePaste`'s no-selection branch (score already has focus — `handlePasteboardOp` requires it, :443-445).

   ```
                       Cmd+V, no selection
           INACTIVE ─────────────────────────> ACTIVE ──────────> [overlay shown, all actions disabled,
               ^                                 │                 preview element suppressed]
               │                                 │
               │                          mouseMoved on a line
               │                                 │  └─> findInsertionIndex → track (lc, index), repaint old+new
               │                                 │
               │        ┌── click / Return ──────┤   (Return with no tracked point ⇒ no-op, stay ACTIVE)
               │        │      └─> tryInsertFragment(index, no deleteRange)
               │        │             ├─ LINE_FULL ─> error dialog ─> STAY ACTIVE
               │        │             └─ INSERTED  ─> exit  (clipboard retained)
               │        │
               └────────┴── Escape (before DeselectCommand; selection left intact)
                        ├── click outside any line
                        └── app backgrounded  (AppForegroundListener.appMovedToBackground :526-527,
                                               WindowAdapter.windowDeactivated fallback :535-546)

           ALL FIVE exits funnel through ONE exit():
               active=false → post notification → remove overlay → remove ComponentListener
   ```

   **All five exits must route through a single `exit()` funnel** — mirroring `GraceModeManager.finish(boolean)` (:720), which every grace-mode exit already funnels through. `exit()` performs the whole teardown in one place: reset `active` → post `PasteModeDidChangeNotification` → remove the `PasteOverlay` from the layered pane → remove the bounds `ComponentListener` (phase 7 task 5). Do not open-code teardown in the individual exit paths: five paths × three steps is fifteen chances to drift, and a missed listener removal is invisible — each enter/exit cycle would leave another live listener on the layered pane firing `setBounds` on every window resize, with nothing to surface it.

2. Insertion-point tracking: hook `LineComponent.mouseMoved` consume-first in the grace-manager slot (`:623-625`). Convert with the `trackMouse` recipe (`PreviewElementManager.java:540-556`: `ScaleContext.pxToSs(lc.getViewScale().toDocPx(new ViewPx(e.getX())).value())`), guard a null `lc.getLayoutResult()`, and take `layoutResult.findInsertionIndex(mouseXSs, line)` **directly** — over an element head it returns that element's index, which is exactly the spec's "never on an element, always before N", and every return path is bounded by `effectiveElementCount()` (`:777-843`), so no clamping is needed. Track `(lineComponent, index)`; on change repaint the old and new lines only.

3. Suppress the normal preview element for the duration: add `@Handler pasteModeDidChange(...)` to `PreviewElementManager.INSTANCE` doing the same `clearPreviewElement()` / `restorePreviewElement(currentMouseLine)` dance as `modeDidChange` (:136-144) and `playbackStateDidChange` (:146-153).

4. Placement: consume-first `mouseClicked` on a line (`LineComponent` :659-661) → `tryInsertFragment(index, null)` → on `LINE_FULL` **stay in paste mode** (the error is already shown by `tryInsertFragment`); on `INSERTED` exit paste mode (clipboard content is retained — another Cmd+V starts a new paste). Wrap in `line.withModification`. Return/Enter: route the currently dead `VK_ENTER` binding (`ScoreInputHandler` `KEY_CODES` :186, `KeyAction` :247-268) to the same placement; with no tracked insertion point (mouse never entered a line) it does nothing and the paste stays pending.

5. Cancel paths:
   - Escape in `ScoreInputHandler.keyPressed` (:150-166) checked **before** the `Mode.SELECT` → `DeselectCommand` branch, alongside the existing grace-mode check (first Escape cancels paste and leaves any selection intact; a second deselects).
   - Click outside any line = `ScoreInputHandler.mouseClicked` on the `ScoreView` itself (:63-70) → cancel.
   - App backgrounded → cancel. **There is no `WindowFocusListener` in this codebase.** Hook both paths where `ActivationGate.activate()` is already called in `MainFrame`: the Desktop API `AppForegroundListener.appMovedToBackground` (:526-527) and the non-macOS `WindowAdapter.windowDeactivated` fallback (:535-546).

6. Belt-and-braces: `handlePasteboardOp` (:441-453) ignores all operations while paste mode is active (the action layer is already disabled via phase 5; this covers any non-action dispatch path).

7. Gate: `./scripts/compile.sh` → SUCCESS.

## ✅ Phase 7: Insertion-Point Rendering and Overlay
**Status:** Complete  
**BlockedBy:** 6  
**Recommended model/effort:** Sonnet 5, medium — the zoom recipe and precedents are spelled out, but the overlay uses two mechanisms with no precedent in `src/main`; manual visual verification  
**Required reading:** `.agents/guides/strings.md`, `.agents/guides/flatlaf-props.md`, `.agents/guides/zoom.md`, `.agents/guides/unit-conversion.md`

### Tasks
1. Insertion-point pass in `LineRenderer.render`, appended after `renderPreviewElement` (:166, topmost, still inside the single Ss transform) and active only when paste mode targets this line. Use the strip-transform recipe from `LyricTextRenderer` (:79-98): inside a `GraphicsState` TRANSFORM save, `g2.scale(1.0 / viewPxPerSs, 1.0 / viewPxPerSs)` with `viewPxPerSs = invariants.getViewPixelsPerStaffSpace()` (`LineInvariants.java:186`), pixel coords = Ss value × `viewPxPerSs`. Draw a vertical line from 1 ss above the top staff line to 1 ss below the bottom one (staff lines sit at `middleLineYSs ± 2`; declare named constants for the staff half-height and the 1-ss margin), stroke width a named constant `3.0f` logical px (constant across zoom; position/height scale), color `ScoreView.getPreviewElementColor()`.

2. Insertion-line X in Ss from the `LayoutResult`: for `index < effectiveElementCount`, the element-`index` column X minus half of `HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS` (:84, public); for the append slot, the last effective element's right edge plus half that gap (division by 2 needs no constant).

3. Strings: add the overlay title and exits-hint line ("click or Return to place, Escape to cancel") to `strings.properties` per the Strings guide — dot-segmented lowercase keys (e.g. `paste.mode.*`), inserted in sorted position within their group, with the generated `Strings.*` constants referenced literally in source in the same change (the dead-key audit fails the build otherwise).

4. FlatLaf keys per the FlatLaf Properties guide: `SongScribe.pasteOverlay.background`, `.foreground`, `.arc` in `FlatLaf.properties` with `[dark]` overrides, read via `FlatLafProps.getColor`/`getInt` on the generated `FlatLafKeys` constants — referenced in the same change (the generator fails the build on unreferenced keys).

5. Overlay as a paint-only **`JLayeredPane` child** — **do not touch the glass pane.** `ActivationGate` calls `frame.setGlassPane` exactly once (:60), caches its own pane in a static field (:39), and never re-reads `frame.getGlassPane()`; swapping it out leaves the gate toggling a detached component, so a click that reactivates the backgrounded app would fall through and place the paste.
   - `PasteOverlay` is a non-opaque component with **no mouse listeners** — a listener-free component is not selected as a mouse-event target, so score clicks (including under the pill) pass through.
   - Add it to `frame.getLayeredPane()` above the content layer on entry. Removal happens **only** in `PasteModeManager.exit()` (phase 6 task 1) — never open-coded in an exit path.
   - **Bounds:** a `JLayeredPane` child gets no layout. On entry, add a `ComponentListener` to the layered pane setting `overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight())`. Keep a reference to that listener so `exit()` can remove it; a leaked listener is silent and accumulates per enter/exit cycle. Note: no `getLayeredPane().add(...)` and no `ComponentListener` exists anywhere in `src/main` today — both are firsts here, so verify by hand rather than by pattern-match.
   - **Centering:** `paintComponent` computes the scroll-pane rectangle via `SwingUtilities.convertRectangle` each paint and draws a rounded pill horizontally centered over it, a named-constant 10 px below the viewport top, with the title and hint strings. Centering is paint-time math — no layout manager is involved.
   - `E2ETest.createStatusOverlay` (:164-209) is the visual precedent only: it is a separate always-on-top `JWindow` positioned in screen coordinates, and is **not** reusable.

6. Gate: `./scripts/compile.sh` → SUCCESS, then manual verification with the user (`./scripts/run.sh` requires permission): insertion point correct on every line and across zoom levels (constant stroke width, scaling height), overlay legible in light and dark themes, anchored through window resizes, clicks pass through it, and — critically — background the app during paste mode, then click to reactivate: the paste must **not** be placed and `ActivationGate` must still swallow that click.

## ✅ Phase 8a: Copy and Clone Tests
**Status:** Complete  
**BlockedBy:** 3  
**Recommended model/effort:** Sonnet 5, low — model/clipboard-layer coverage, all pure; read the testing guides first  
**Required reading:** `.agents/guides/testing-common.md`, `.agents/guides/testing-unit.md`

Extend `songscribe.UnitTest`; build fixtures with `minimalSongMock()`/`detachedLine()`; AssertJ assertions; `mockStatic` for `EndingConfirms`/dialog confirms.

### Tasks
1. Span copy and re-anchor: a fully-contained tie, beam, tuplet, hairpin, trill, **and `Ending`** each survive a copy → paste round-trip and anchor to the pasted clones, not the originals (assert by identity).

2. Partially-overlapping spans are dropped (anchor inside/end outside and vice versa), including an `Ending`.

3. Boundary normalization: trailing breath mark included; orphan paired grace note dropped (and its spans with it); `FINAL_DOUBLE_BARLINE` normalized to `DOUBLE_BARLINE`.

4. `effectiveDeleteEnd(line, begin, end)`: extends past a trailing breath mark, leaves a non-breath-mark successor alone, handles `end` at the last element, and mutates nothing.

5. Cut confirmation ordering: a declined ending-invalidation confirm leaves both the clipboard and the score untouched.

6. Annotation aliasing regression: copy → paste an element carrying an `AnnotationAttachment`; mutate the pasted annotation the way `VerticalAdjustment.adjustAnnotation` (:201-214) does; assert the original's `userYOffsetSs` is unchanged. Same shape for `TempoChangeAttachment`/`Tempo`.

7. Gate: `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh unit` → all green.

## ✅ Phase 8b: Paste and Modality Tests
**Status:** Complete  
**BlockedBy:** 4, 5  
**Recommended model/effort:** Sonnet 5, medium — spacing algorithm and modality coverage; read the testing guides first  
**Required reading:** `.agents/guides/testing-common.md`, `.agents/guides/testing-unit.md`

Same fixtures and conventions as phase 8a.

### Tasks
1. Lyric seam repair: pasting into a hyphenated word (`BEGIN`/`MIDDLE`…) and into a melisma (extend `START`/`CONTINUE`…) leaves valid syllabic and extend chains at both seams.

2. Paste-replace atomicity: delete + insert produce exactly one modification bracket (one `SongDidChangeNotification`, one undo step); overflow-blocked paste produces none.

3. `calculateFragmentInsertion` — the one novel algorithm, and pure/non-UI. Express every assertion in terms of `Line`, `Ss`, and `song.getLineWidthSs()`, never the package-private column internals, so these survive the #330 rebase:
   - N-element chaining: per-clone X positions advance by the expected column gaps.
   - Seeding: `insertIndex == 0` vs. a mid-line predecessor.
   - `deleteRange` supplied: predecessor/successor are the elements adjacent to the range.
   - Boundary verdict: a fragment that just fits ⇒ FITS; one element more ⇒ LINE_FULL.
   - Non-mutation: the `Line` and its elements are unchanged after a call (both verdicts).

4. Repeated paste independence: paste the same fragment twice; assert the two results share no `StaffElement` or `RangeElement` instances (identity), and that the second paste's spans anchor to the second paste's clones.

5. Span-add ordering: after a paste, every pasted span's `getAnchorElementIndex()`/`getEndElementIndex()` resolve against the **destination** line and return the correct indices (never `-1`) — the regression test for the phase 4 task 3 ordering constraint.

6. `enableFromPasteMode`: returns false for every action while `PasteModeManager.isActive()`, true otherwise.

7. `PasteAction` flags: asserts it carries `DISABLE_IN_GRACE_MODE` and `DISABLE_WHEN_EDITING_TEXT` (phase 5 task 7), and that it is disabled while `GraceModeManager.isActive()`. Nothing else covers this — `LyricEditorActionAuditTest` whitelists toolbar actions only.

8. Empty fragment: `handlePaste` with a null/empty fragment is a no-op — no mutation, no paste mode entered.

9. Gate: `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh unit` → all green.

## ✅ Phase 9: Architecture Doc
**Status:** Complete  
**BlockedBy:** 7, 8b  
**Recommended model/effort:** Sonnet 5, low — write up what was built; no code changes

Written **last, from the merged implementation** — not from this plan — so it documents what actually shipped, not the intended design.

### Tasks
1. Create `docs/clipboard.md` documenting the cut/copy/paste architecture, matching the style of the existing `docs/*.md` architecture notes (e.g. `tie-rendering-placement.md`, `vertical-stacking-system.md`). Cover:
   - **Fragment model** — the `Fragment` record as the single "clone + re-anchor" definition, why the stored fragment is never inserted (repeated-paste independence), and span containment ("both endpoints in the copied set"). Include the Fragment-lifecycle diagram (now in `Fragment`'s header).
   - **Copy boundary normalization** — trailing breath mark, orphan grace-note drop, `FINAL_DOUBLE_BARLINE`.
   - **Mutation-bracket architecture** — `deleteElementRange` as a confirmation-free `void` helper with confirmation lifted to callers; `effectiveDeleteEnd` as a pure query; cut = confirm-first-then-one-bracket; paste-replace = one `withModification { delete; insert }` = single undo step; and that `addElement`'s re-parenting and its destination-tuplet/ending removal (the accepted loss) all occur inside that one bracket.
   - **Fragment spacing / fit check** — `calculateFragmentInsertion`, the "line full" refusal, and the #330 rebase boundary. Include the spacing diagram (now in the method header).
   - **Paste mode** — the modal state machine (include the diagram from `PasteModeManager`'s header), the single `exit()` funnel, the blanket action disable via `enableFromPasteMode`, the `PasteAction` flag requirements, and the layered-pane overlay / `ActivationGate` glass-pane boundary.
   - **Deliberately out of scope** — the spec's deferred boundaries, plus the bounded clipboard-retention and cross-document-paste notes.

2. Add a link to `docs/clipboard.md` from wherever the other architecture docs are indexed, if such an index exists.

## Verification (whole plan)
- Copy/paste round-trip preserves every fully-contained span type including `Ending`, re-anchored to clones; partial overlaps dropped (unit-tested).

- Boundary normalization holds: no orphan grace note or detached breath mark can reach the clipboard (unit-tested).

- Declined cut leaves clipboard and score untouched (unit-tested).

- Paste with a selection replaces in one undo step and clears the selection (unit-tested; manual undo check).

- Paste that would overflow the target line is blocked with a "line full" error and mutates nothing, in both paste paths (unit-tested for replace; manual for paste mode).

- Repeated pastes are fully independent; pasted spans resolve indices against the destination line (unit-tested).

- Dragging a pasted annotation does not move the original (unit-tested).

- Paste mode (manual): entered by Cmd+V with no selection; insertion point tracks the mouse on every line, never on an element, never past the terminal, constant 3 px stroke at all zoom levels; normal preview suppressed; every action disabled (menus, toolbar, Cmd+V itself); click or Return places once and exits; Escape, click outside a line, and app-backgrounding cancel; overlay themed, centered, click-transparent, anchored through resizes; **backgrounding during paste mode then clicking to reactivate does not place the paste, and `ActivationGate` still swallows that click**.

- Pasted lyrics carry text and both seams stay valid (unit-tested).

- Full suite green: `./scripts/compile.sh` and `./scripts/test.sh unit`.
