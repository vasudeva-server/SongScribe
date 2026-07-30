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
  at `end + 1`. This is computed once by `Line.effectiveDeleteEnd`
  (a pure query, no line mutation) and shared by copy, cut, the paste-replace
  fit check, and the selection highlight — one rule, one implementation.
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

`Line.effectiveDeleteEnd(int end)` extends `end` past a trailing breath mark and
mutates nothing. It is shared, unchanged, by `deleteElementRange`,
`Fragment.capture`, `tryInsertFragment`'s paste-replace delete range, and
`LineSelectionState.isElementSelected` — the breath-mark rule is defined once, so
what paints as selected is exactly what a delete or a copy carries away.

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

`handlePaste` also runs the ending-invalidation confirm before opening that bracket,
on the same terms as `handleDelete` and `handleCut`: a paste-replace deletes before
it inserts, so it can discard an ending the same way they can. Declining leaves the
score, the selection, and the clipboard untouched.

Inside the bracket, `Line.addElement`'s existing side effects also apply to every
pasted clone: it re-parents the clone (`setLine`/`setParentLine`) and removes any
ending invalidated by the inserted element's type. Everything else that pasting
into occupied structure would break is reconciled deliberately — see §3.1.

### 3.1 Span reconciliation — pasting inside an existing span (#614)

`Fragment.capture` keeps the source side consistent (a span survives only if both
endpoints are inside the copied range) and `Line.removeRange`'s sweep drops any
destination span whose anchor or end a paste-replace deletes. Neither covers the
span that **straddles** the paste: anchor before the paste region, end after it,
both endpoints surviving, so the span silently stretches over material the user
never put under it. `PasteSpanReconciliation.reconcile` is the one place that case
is decided. It runs on the **pre-mutation** line, before the delete, so every index
it reads is still live.

A span straddles iff `anchorIndex < insertIndex && endIndex >= firstIndexAfterRegion`,
where `firstIndexAfterRegion` is `deleteRange.end() + 1` for a paste-replace and
collapses onto `insertIndex` for a pure insertion. That single predicate excludes
both the fully-replaced span (endpoints deleted, the sweep's job) and the span
clear of the paste entirely.

| Kind | Destination | Fragment |
| --- | --- | --- |
| `Tuplet` | removed | tuplets dropped |
| `Beam` | removed | beams dropped |
| `Tie` | removed | kept |
| `Trill` | removed | kept |
| `Hairpin` | **kept**, unless contradicted | same-type hairpins dropped |
| `Ending` | **kept** | endings dropped |

Tuplets and beams are rhythmic groupings: one that no longer covers the notes it
was written for is wrong, and a pasted group dropped into the wreckage of a broken
one is equally wrong, so both sides go. The fragment-side drop is **per kind** — a
straddled beam does not kill a pasted tuplet. Ties and trills bind specific notes,
so a straddled one is dropped, but the fragment's own tie/trill still binds the
fragment's own notes and is kept.

**Hairpins reconcile by type**, because a crescendo and a diminuendo say opposite
things. A hairpin is a continuous dynamic that reads correctly over any span of
notes, so a straddled destination hairpin is kept — silently widened by the
insertion — and the fragment's, necessarily a shorter hairpin of the same type
inside it, is dropped as redundant. But if the fragment carries a hairpin of a
*different* type, the destination's is removed and the fragment's own hairpins win:
a diminuendo nested inside a crescendo is a contradiction no widening can fix. When
the whole destination hairpin *is* selected it dies with the deletion and the
fragment's hairpin replaces it, with no special case needed.

**A paste that merely abuts a hairpin is not the reconciler's problem.** Two
same-type hairpins left nose to tail are one hairpin — but that is the same rule
that applies when the user *draws* a hairpin flush against an existing one, so it
lives where drawing already handles it. `Line.addCrescendo`/`addDiminuendo` absorb an
overlapping **or adjacent** same-type hairpin (`mergeOverlappingSpans` takes an
`absorbAdjacent` reach: `true` for hairpins, `false` for beams, since two beam groups
written back to back are two deliberate groupings). `tryInsertFragment` adds pasted
spans through `Line.addPastedRangeElement`, which routes hairpins to those two adders
and everything else to the raw `addRangeElement`, so a pasted hairpin landing flush
against a same-type one continues it instead of restarting it. A different type merges
with nothing and the two stand side by side, again exactly as when drawn.

**Reading merges too.** Two same-type hairpins back to back say nothing a single
wider one does not, so that state is not one the model holds — however it arises.
`WedgeResolver` and legacy `LineIO` therefore add hairpins through
`addCrescendo`/`addDiminuendo` rather than the raw `addRangeElement` every other span
kind in both readers uses, so a file another program wrote (or an old `.mssw`)
normalizes on the way in. The upshot is a single invariant with no exceptions: a
hairpin never abuts or overlaps a same-type hairpin, whether the user drew it, pasted
it, or opened a file containing it.

**Only a straddle counts.** #614 words the tuplet/beam rule as "if the paste does not
completely replace a group, remove it from both sides", which literally also covers a
paste-replace that clips a group at its edge — deleting one endpoint and orphaning
notes on the other side. That case is deliberately excluded: the destination group
dies anyway (the deletion sweep sees the lost endpoint), and the pasted group lands
contiguous and self-consistent at the boundary rather than interleaved with the
orphaned remains, which is the mess the rule exists to prevent. Dropping it there
would strip beaming from a paste merely for landing next to a beamed note. The
orphaned notes' own beaming is left to the user, per "beams at the seams" in §6.

An ending follows the hairpin rule for the same reason in reverse: an ending bracket
covering a few extra notes is still valid notation, but an ending *nested inside*
another one never is — and nothing else rejects a nested ending, since
`makeFirstSecondEnding` validates repeat context rather than existing-ending overlap.
So the destination ending is kept and the fragment's is dropped. Whether the
destination ending survives the pasted *content* is a separate, more precise question
`Line.addElement` already answers per clone via `Ending.isInvalidatedByInsertion`, the
ending's own barline/repeat-aware rule; the reconciler does not second-guess it.

### Why the per-kind rules add up to an invariant

Taken together the rules guarantee that **a paste never leaves two overlapping spans
of the same kind**, which is the property #614 is really after. The argument: a
surviving destination span `D = [a, b]` (endpoints not deleted) can overlap the
pasted block at all only if `a < insertIndex && b >= firstIndexAfterRegion` — the
straddle predicate exactly. Any other `D` lies wholly before the region, wholly after
it, or wholly inside the deleted range (endpoints deleted, so it doesn't survive), and
is therefore index-disjoint from the block. So every possible same-kind overlap passes
through the straddle branch, where one side or the other is always dropped.

`NoSameKindOverlapSurvives` in `PasteSpanReconciliationTest` asserts this per kind
independently of which side each rule favours, so a future kind added to the switch
with the wrong policy — or added to `RangeElement` and forgotten here — fails a test
rather than shipping a malformed score. Note that the invariant is what makes it safe
for `addPastedRangeElement` to add every non-hairpin pasted span with the raw
`line.addRangeElement`, bypassing the overlap-merge and displacement logic in
`addTuplet`/`addBeaming`/`addTrill`: there is by then no overlap left for those to
resolve. Hairpins are the exception, and only because they must handle *abutment*,
which the invariant says nothing about — abutting spans do not overlap.

Removals go through `Line.removeInvalidatedRangeElement`, the typed dispatcher that
emits each span's proper tracked mutation, so a single undo restores every span this
step discarded. Every branch of it is a no-op on a span already gone from the line,
which is what makes `addElement`'s now-redundant tuplet removal harmless rather than
a double-remove.

`Fragment.instantiate()` therefore moves **above** the delete in `tryInsertFragment`:
the reconciliation decides which of the instantiated spans survive, and it needs
pre-mutation indices. The clones carry no line back-reference until `addElement`, so
building them early touches nothing.

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

        While ACTIVE, presses on a line are inert — no line select, no lyric select, no
        pitch drag — so the click that follows is always a placement or a cancel.
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

## 6. Accidental context reconciliation (#676)

Every insert, delete, paste, and in-place modification (an accidental toggle, a
pitch shift, a duration swap) that changes which explicit accidentals sit on a
line — or where — must reconcile the accidental context those changes disturb.
Without it, an edit can silently change the sounding pitch of a note the user
never touched, or leave a now-meaningless accidental stranded on the page.

### The invariant

Every note keeps the pitch it had, unless the user changed that note. Two
populations are protected: **pasted or inserted** notes keep the pitch they had
in their source context, and **surviving** notes keep the pitch they had before
the mutation. A note the user themselves changed is never protected — it is
supposed to change.

### Materialization

`songscribe.layout.AccidentalReconciliation.reconcile` (for an insert, delete, or
paste-replace) and `.reconcileModification` (for an in-place change) walk the
line's projected element sequence and compare, for each protected note, its
sounding accidental **before** the edit against what it would sound **after**.
Comparison is always by sounding adjustment
(`StaffElement.getPitchAdjustment`, with `null` treated as no adjustment, i.e.
`NATURAL`), never by enum identity — `null` and `NATURAL` sound alike. When
the two differ, the note is given an explicit
accidental: its own prior accidental if it had one, otherwise `NATURAL`. That
`null → NATURAL` direction is what a cross-key paste needs — a note that
inherited no accidental in its source context must still read as an explicit
natural if the destination context would otherwise sharpen or flatten it.

The key signature never appears in the algorithm directly. It's the last branch
of `StaffElement.findEffectiveAccidental`, so resolving the note's context
before the edit against the source line and after against the destination line
compares the two keys implicitly.

A note that ends a tie is never a candidate for materialization or removal: a
tie asserts that two notes are one sounding pitch, so the tied note has no pitch
of its own to protect — when the anchor moves, the tied note is supposed to move
with it.

### Removal — the mirror rule

An explicit accidental is cleared from a **surviving** note when the edit both:

1. moved the context arriving at that note (its before/after sounding context
   differ), and
2. left the note's own accidental sounding identical to the new context, so
   drawing it says nothing.

Both conditions are required. Together they make an accidental that was
*already* redundant when it was written unremovable: "already redundant" means
the note's own sound equalled its context before the edit, which combined with
condition 2 forces the context to be unchanged by the edit — contradicting
condition 1. That's what lets a deliberate restatement, or a courtesy
accidental placed where the note already sounded that way, survive every edit
that doesn't move its context.

Parenthesized accidentals get no exemption — parentheses record that the
notator chose to write something they didn't have to, which says nothing about
whether a later edit obviated it. `AccidentalMaterializer`'s private
`SavedAccidental` record therefore carries the note's parentheses flag
separately from its accidental: `StaffElement.setAccidental` clears the flag
whenever the accidental goes `null`, so a refused edit that restores the prior
accidental would otherwise restore it without its parentheses.

Removal applies to **surviving** notes only — a pasted or inserted note keeps
the notation it arrived with, matching the "a fragment carries semantic
content" rule below.

**The limit.** A restatement of the accidental *being removed* is invisible to
this arithmetic — on its own line it may be doing real work, since the backward
scan resets at the line boundary. Removing those needs the notator's judgement,
not arithmetic, and is deliberately left to a separate, follow-up feature
(#681) rather than left as an unrecognized hole.

### What can move a note's inherited context

Two kinds of edit can change the accidental a note inherits: an explicit
accidental added or removed, and a barline or repeat added or removed, because
either cancels every accidental before it. Both matter equally — assuming only
the first is what produced the paste/barrier-insertion defect this reconciliation
closes.

Two rules keep that from recurring:

- No call site decides for itself whether reconciliation is needed. It always
  runs, for every edit that can move accidental context.
- The list of element types that cancel accidentals (barlines, repeats) stays
  in exactly two places — `StaffElement.findEffectiveAccidental` and
  `AccidentalReconciliation.resolveOverProjection`, which mirrors it for the
  projected (not-yet-committed) sequence — and is never copied anywhere else.

### The two bounds, and why the pass is a single left-to-right walk

1. Only a staff position carrying an explicit accidental in the removed or
   inserted content can change the context arriving at a boundary.
2. For each such position, only the *first* following note lacking its own
   accidental needs fixing; later notes at that position resolve from it.

Both bounds are satisfied structurally by a single left-to-right pass over the
projected sequence, not by an early exit: each emitted change is written back
onto the projected position before the walk continues, so later positions
resolve against the already-reconciled state.

### Ordering — materialize before layout is projected

`AccidentalReconciliation` reads the live line and mutates nothing; it returns
a list of `AccidentalChange`s for the caller to apply.
`songscribe.layout.AccidentalMaterializer.applyIfAccepted` is where they're
applied — gated on the edit's own fit check being accepted, and guaranteed to
leave the line untouched on refusal. The accidentals **must** be materialized
before the projected column chain used for that fit check is built:
`ElementColumnBuilder` derives element extents including accidental width, and
`LayoutEngine` treats accidental width as a layout input. With that ordering,
both the fit gate and the committed layout are correct with no per-position
shift machinery.

### The `Fragment` reshape

`Fragment` (§1) carries a `priorAccidentals` list parallel to `elements()`:
each entry is the effective accidental that element had on its **source**
line, or `null` when the element is unpitched or already carries an explicit
accidental of its own. `Fragment.capture` resolves each element's context
against the **original**, never the clone — a clone's `line` field still
points at the source line, but `line.getElementIndex(clone)` returns −1
because `StaffElement` overrides neither `equals` nor `hashCode`, so resolving
against the clone would silently skip the whole backward scan and return the
key signature alone. `instantiate()` carries `priorAccidentals` through
unchanged onto the fresh clones it produces for each paste.

`instantiate()` also zeroes each clone's `xOffsetPx`: a fragment carries
semantic content, not layout corrections — `xOffset` is a nudge computed under
one specific spring solve, with specific neighbours, under a specific header
width, and pasted elsewhere it's meaningless at best and at worst recreates the
collision it was made to fix.

### The modification fit gate

An in-place modification — an accidental toggle, a pitch shift, a duration
swap — can widen a note's column (a new accidental needs room) without
changing the element count. `InsertionSpacingCalculator.calculateModification`
is the replace-a-column analogue of the insertion fit checks in §4: the same
projected-column-and-spring machinery, but columns are *replaced* in place
rather than spliced in, so indices need no repositioning.
`SelectionCoordinator.fitsAfterModification` calls it from pass 2 of the
selection-modification flow, projecting both the user's own changes and the
`AccidentalChange`s they force, all on clones — the live elements stay
untouched until the gate accepts.

Not every action is gated: `SelectionCoordinator.changesExtent` gates only
actions that can change a column's horizontal extent — an accidental toggle
(`AccidentalAction`, `AccidentalInParensAction`), a dot (`DotAction`), and
element replacement (`UIAction.ElementReplaceable`). Fermata and dynamics are
deliberately not gated: they stack vertically, independent of the note column,
and cannot make a line wider.

Without this gate, an infeasible modification isn't refused at mutation time —
it surfaces later as `LayoutEngine`'s `LINE_TOO_FULL_ERROR` with a `null`
`LayoutResult`, so the line doesn't render at all.

---

## 7. Deliberately out of scope

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
- **#612 (cut/copy/paste entire line).** A separate downstream plan. It needs
  the `Fragment` reshape from §6, and its line fragment carries offsets — the
  opposite of the element fragment's "semantic content, not layout corrections"
  rule.
- **#53 (mid-line key changes).** Separate. The resolver
  (`StaffElement.findEffectiveAccidental`) now accepts it via `keyInEffectAt`;
  nothing in this architecture implements it. It also breaks the
  one-key-per-line assumption in `HorizontalSpacingCalculator.isWithinHeaderXSs`.
- **#11 (ABC import).** Separate. Listed only as a future call site of
  `AccidentalReconciliation`, needed for two cases: ABC's default applies an
  accidental to the pitch class in *all* octaves within the bar, while
  SongScribe matches same-octave only; and ABC does not reset at a line break,
  while SongScribe does. Both apply to default-directive files, so #11 is a
  materialization call site by default.
- **Line-reset revisited.** Kept as house convention. The private backward-scan
  traversal seam in `StaffElement` (added for #675, the resolver's scope) is
  where it would change if this is ever revisited.
- **The `xOffset` dual meaning.** The field is documented and exported as a
  nudge but serves as an absolute-position store for the insert/delete/paste
  arithmetic and `HorizontalAdjustment`. Both cannot hold once a real nudge
  exists, and pasted notes plausibly export a spurious `relative-x` today. A
  prerequisite for a future manual-offset feature, not for this work — its own
  issue.
- **Storing pitch on `StaffElement`.** Rejected actively, not deferred. Revisit
  only if transposition becomes a feature, a pitch-based source becomes a
  primary import path, or the call-site set for accidental reconciliation stops
  being finite.

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
