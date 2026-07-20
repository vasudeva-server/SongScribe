# Cut/Copy/Paste Architecture (#65)

This document captures the clipboard architecture **as implemented**: the `Fragment`
model, the mutation-bracket discipline that keeps every clipboard operation a single
undo step, the fragment-aware spacing/fit check, and the paste-mode state machine and
overlay. It documents what shipped, not the original design — see `specs/65-clipboard.md`
and `plans/65-clipboard.md` for the process that got here.

---

## 1. The Fragment model

`songscribe.ui.clipboard.Fragment` is the single definition of "clone a run of
elements and re-anchor the spans that touch them." Both copy (`capture`) and every
paste (`instantiate`) go through it:

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

`Fragment` is an immutable record: `elements()` (the cloned `StaffElement`s, in
order) and `spans()` (the `RangeElement`s fully contained within them, already
re-anchored to the clones).

### Why the stored fragment is never inserted

`ClipboardManager` holds exactly one `Fragment`. Every paste calls
`fragment.instantiate()`, which clones the elements *again* (building a fresh
`IdentityHashMap`) and re-anchors the spans onto those fresh clones, leaving the
stored fragment untouched. The alternative — inserting the stored fragment's own
elements — would work for the first paste and then corrupt every subsequent one,
because a `StaffElement` can only belong to one `Line` at a time (`setLine`/
`setParentLine`, re-parented by `Line.addElement`). Pasting the same fragment twice
must produce two paste results that share no instances by identity; `instantiate()`
is what makes that true by construction rather than by care at each call site.

### Span containment

A `RangeElement` (`Tie`, `Beam`, `Tuplet`, `Trill`, `Crescendo`/`Diminuendo`, and
`layout.Ending`) is captured **iff both its anchor and end element are in the
copied set** — i.e. both are keys of the clone map built while copying elements.
This one rule implements "fully contained spans survive, partially-overlapping
spans are dropped" and, as a side effect, automatically drops any span touching an
element that boundary normalization excluded (the orphan grace note below): if
that element isn't a clone-map key, no span anchored to it can pass the
containment check either. A surviving span is stored as `span.copy(clonedAnchor,
clonedEnd)` — `RangeElement.copy` is the template method that carries
subclass-specific state (e.g. `Tuplet.grade`, `Trill.yPositionSs`) plus the
shared `LineElement` user-offset/margin state, without copying derived caches
like `Ending.bracketRanges`.

---

## 2. Copy boundary normalization

`Fragment.capture` normalizes the raw selection before cloning anything:

- **Trailing breath mark.** A breath mark is positionally attached to the element
  before it, so a copy or cut ending at `end` must also carry a breath mark sitting
  at `end + 1`. This is computed once by `ScoreViewController.effectiveDeleteEnd`
  (a pure query, no line mutation) and shared by copy, cut, and the paste-replace
  fit check — one rule, one implementation, three call sites.
- **Orphan paired grace note.** If the last element the effective range would
  include is a paired grace note whose host lies *outside* the range (`
  line.isPairedGraceNote(effectiveEnd)`), it's dropped from the capture. A grace
  note without its host is meaningless on its own, and per the span-containment
  rule above, dropping it also drops any span anchored to it.
- **`FINAL_DOUBLE_BARLINE` → `DOUBLE_BARLINE`.** The final double barline is a
  song-owned invariant (there is exactly one, and it's always the last element of
  the last line). A captured clone of it is renormalized to a plain
  `DOUBLE_BARLINE` so a paste can never introduce a second "final" barline
  elsewhere in the song.

Repeats are captured verbatim, with no repeat-balance validation — an unbalanced
`|:`/`:|` pair pasted into the middle of a line is accepted the same way typing one
in directly would be.

---

## 3. Mutation-bracket architecture

### `deleteElementRange` — confirmation-free, `void`

`ScoreViewController.deleteElementRange(Line, int, int)` is the extracted body of
the element-range delete path: grace-pair fallback, the breath-mark extension (via
`effectiveDeleteEnd`), glissando strip on the preceding element, `xOffsetPx`
gap-fill for the elements after the range, lyric-seam adjustment, and
`Line.removeRange`. It performs **no confirmation** and returns nothing — every
caller has already run whatever confirmation it needs and already knows the range
it asked to delete, so there is no extent to thread back out.

Confirmation lives entirely at the call sites: `handleDelete` and `handleCut` both
check `line.hasEndingInvalidatedByDeletion(...)` and run
`EndingConfirms.confirmInvalidation` **before** touching anything, and only clear
the selection and open a modification bracket once the user has agreed (or there
was nothing to confirm).

### `effectiveDeleteEnd` — a pure query

`ScoreViewController.effectiveDeleteEnd(Line, int begin, int end)` extends `end`
past a trailing breath mark and mutates nothing. It is shared, unchanged, by
`deleteElementRange`, `Fragment.capture`, and `tryInsertFragment`'s paste-replace
delete range — the breath-mark rule is defined once.

### Cut = confirm-first, then one bracket

`handleCut` runs the ending-invalidation confirm first; declining returns with
**both** the clipboard and the score untouched (copy hasn't happened yet). On
confirmation it calls `handleCopy()` (one `ClipboardDidChangeNotification`), clears
the selection, and then opens exactly one `song.withModification { deleteElementRange(...) }`
bracket — one undo step for the deletion, with no further confirmation inside it.

### Paste-replace = one bracket, one undo step

`handlePaste`, when a selection exists, wraps the whole replace in one
`song.withModification { tryInsertFragment(...) }` bracket:
`tryInsertFragment` deletes the effective selection range and inserts the fragment
in the same call. Delete-then-insert is therefore a single undo step, matching
what a user experiences as one action. On `LINE_FULL` `tryInsertFragment` mutates
nothing before returning, so the bracket closes empty, posts no
`SongDidChangeNotification`, and the selection is left intact.

Inside that one bracket, `Line.addElement`'s existing side effects also apply to
every pasted clone: it re-parents the clone (`setLine`/`setParentLine`) and, if the
insertion point falls inside a tuplet or invalidates an ending, removes that tuplet
or ending. This is the **accepted loss** for pasting into occupied structure — it
is exactly what any single-element insert already does, deliberately not
special-cased for paste, and because it happens inside the paste's own bracket a
single undo restores the destroyed tuplet/ending along with everything else.

### Hard ordering constraint inside `tryInsertFragment`

Every fragment clone must be inserted (`line.addElement`) **before** the first
`line.addRangeElement` call for that paste. `addRangeElement` re-parents only the
span itself, not its anchor/end elements, and a span's `getAnchorElementIndex()`
resolves through the anchor's *own* `getLine()`. A span added while its anchors
still carry the source line's back-reference gets evaluated against the wrong
line by `addElement`'s invalidation sweep, producing a wrong index or `-1`. The
implementation inserts every clone in a loop first, then adds every span in a
second loop.

---

## 4. Fragment spacing / fit check

`InsertionSpacingCalculator.calculateFragmentInsertion` is the one genuinely new
spacing algorithm: chaining N cloned elements through the existing single-element
spacing primitives, then computing one trailing shift for everything after the
fragment.

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

It is pure measurement: every position is computed on lightweight, throwaway
`ElementColumn`s (the same clone-and-measure precedent `hasRoomForFall` already
uses) — the real `Line` and its elements are never touched. It returns a
`FragmentInsertionResult` carrying the per-clone X positions, the single trailing
shift (which can be *negative* for a paste-replace whose fragment is narrower than
the deleted range — the tail pulls left, unlike a pure insertion which only ever
pushes right), and the projected line width.

### The "line full" refusal

`ScoreViewController.tryInsertFragment` runs the fit check against the *pre-delete*
line, and if `result.fitsWithinLine(song.getLineWidthSs())` is false, shows the
"line full" error (`Strings.ERROR_LINE_FULL_PASTE`) and returns `LINE_FULL`
**without mutating anything** — not even when a `deleteRange` was supplied. This
is what makes it safe for `handlePaste` to call `tryInsertFragment` from inside an
already-open modification bracket: a `LINE_FULL` outcome leaves that bracket with
nothing to commit.

### The #330 rebase boundary

Branch `330-element-spacing` reworks the spacing internals this method composes
(`HorizontalSpacingCalculator`, `ElementColumn`) but does not introduce reflow, so
"line full" remains a real, user-visible state rather than dead code. Whichever
branch lands second rebases onto the other. The tests for this method
(`InsertionSpacingCalculatorTest`) are deliberately written in terms of `Line`,
`Ss`, and `song.getLineWidthSs()` — never against the package-private column
internals — so they survive that rebase as a behavioral net rather than breaking
on internal refactoring.

---

## 5. Paste mode

When `handlePaste` runs with no active selection, it doesn't insert anything
directly — it hands off to `PasteModeManager.enter()`, which puts the score into a
modal "click to place" state.

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
                     └── app backgrounded

        ALL exits funnel through ONE exit():
            active=false → post notification → remove overlay → remove ComponentListener
```

### State and the single `exit()` funnel

`PasteModeManager` mirrors `GraceModeManager`'s shape exactly: a private `active`
flag, `isInProgress()`, and a static `instance` backing a static `isActive()` for
callers (`UIAction`, `ScoreViewController.handlePasteboardOp`) that don't hold a
reference. There are five distinct ways out of paste mode — successful placement,
"line full" leaves you in place so that's *not* an exit, Escape, a click outside
any line, and the app being backgrounded — and all of them route through one
private `exit()`: reset the `active` flag and post
`PasteModeDidChangeNotification(false)`, drop the tracked insertion point and
repaint the line that had been showing it, remove the layered-pane bounds
`ComponentListener`, and remove the `PasteOverlay` from the layered pane. Nothing
open-codes any of those four steps outside `exit()` — a missed listener removal
would otherwise be invisible, silently leaking one more live listener on the
layered pane every enter/exit cycle.

Mouse tracking (`updateTarget`, called from `LineComponent.mouseMoved` /
`mouseClicked`) converts the event's view-pixel X to staff spaces with the same
recipe `PreviewElementManager.trackMouse` uses, then calls
`LayoutResult.findInsertionIndex` directly — over an element head it returns that
element's index, so the tracked insertion point is never on top of an element,
and every return path from `findInsertionIndex` is already bounded by
`effectiveElementCount()`, so no clamping is needed at the call site.

### Blanket action disable

`UIAction.enableFromPasteMode()` returns `!PasteModeManager.isActive()` and is
called first in `updateEnabledState()`'s predicate chain, so **every** action —
menu, toolbar, and the paste shortcut itself — is disabled the instant paste mode
is active, with no per-action opt-in required. There's no saved/restored toggle
state here (unlike grace mode's `saveActionStates`/`restoreActionStatesWithFlag`,
which exists to preserve *selected* toggle state): paste mode doesn't touch
toggle state, so exiting simply re-runs `updateEnabledState()` and everything
re-evaluates from the current context.

### `PasteAction`'s flag requirements

`PasteAction` carries `DISABLE_WHEN_PLAYING`, `DISABLE_IN_GRACE_MODE`, and
`DISABLE_WHEN_EDITING_TEXT`. `DISABLE_IN_GRACE_MODE` is load-bearing: grace mode
runs with the score focused, so `handlePasteboardOp`'s focus guard doesn't block
it, and without the flag Cmd+V mid-grace-mode would start paste mode on top of an
already-in-progress grace mode — two live modal managers at once.
`DISABLE_WHEN_EDITING_TEXT` is cosmetic (the focus guard already prevents mutation
while a `LyricEditor` holds focus) but keeps Edit ▸ Paste from looking enabled
when it would silently no-op. `CutAction`/`CopyAction`/`DeleteAction` don't need
either flag — they're gated by music selection, which is already empty in both
states.

### Overlay: layered pane, not the glass pane

The `PasteOverlay` pill (naming the mode and its exits) is added directly to
`MainFrame`'s `JLayeredPane` (`PALETTE_LAYER`) on `enter()`, and removed only
inside `exit()`. It is deliberately **not** installed as the glass pane:
`ActivationGate` calls `frame.setGlassPane` exactly once at startup, caches that
pane in its own static field, and never re-reads `frame.getGlassPane()` —
swapping the glass pane out from under it would leave the gate toggling a
detached component, so a click meant to reactivate a backgrounded app would fall
through to the score and place the pending paste. Because a `JLayeredPane` child
gets no layout, `PasteModeManager` tracks the pane's size itself with a
`ComponentListener` added on `enter()` and removed in `exit()`, keeping the
overlay full-bleed through window resizes. `PasteOverlay` itself has no mouse
listeners — AWT never selects a listener-free component as a mouse-event target,
so every click (including one landing on the pill) passes through to the score
underneath, which is exactly what placement-by-click requires.

---

## 6. Deliberately out of scope

Carried over from `specs/65-clipboard.md`:

- **Beams at the seams.** Pasting mid-line can change the beat context of
  following notes, whose beams may now be wrong. Paste only affects what it
  inserts; surrounding beams are left for the user to fix with `toggleBeaming`.
  `SelectionCoordinator.repairBeamings` isn't wired into the delete path either,
  so this is a pre-existing pattern, not a new gap.
- **Selection restoration on undo.** Undoing a cut or paste-replace does not
  restore the selection that was active beforehand.
- **System clipboard interoperability.** The clipboard is in-process only; there
  is no interop with the OS pasteboard or other applications.

And two notes about `ClipboardManager`'s lifetime, not from the spec but decided
during implementation:

- **Bounded clipboard retention.** `ClipboardManager` is app-global — constructed
  once alongside `ScoreView`, which is itself never recreated (only its `song` is
  swapped on document open/close). A `Fragment`'s cloned elements retain their
  `line` back-reference, which keeps that one source `Song` reachable until the
  next copy replaces the fragment. This is bounded (one `Song`, replaced on the
  next copy) and harmless, and clearing it on document close was rejected because
  it would break today's working copy-in-A / paste-in-B flow for no benefit.
- **Cross-document paste is therefore supported, not merely tolerated.** Because
  the clipboard isn't tied to any one document, copying in one song and pasting in
  another works today as a side effect of the above — it was never a design goal
  of #65, but nothing in this architecture prevents it either.
