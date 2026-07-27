# Propagate Accidental Removal to Restatements

Tracked by #681. Follow-up feature — **not part of #676**, see *Why this is not reconciliation*
below. Depends on Phase 9a of `plans/accidental-context-implementation.md`, and does not block
#676 from merging.

---

## Context

Phase 9a gives reconciliation a removal direction: an explicit accidental is cleared when the edit
both moved the context arriving at that note and left the accidental redundant. That rule has a
provable limit — an accidental that was *already* redundant when placed can never be removed,
because "already redundant" means `adj(own) == adj(contextBefore)`, which with the redundancy
condition forces `adj(contextBefore) == adj(contextAfter)` and contradicts the movement condition.

That limit is deliberate: it is what protects a deliberate restatement, and restatements are the
norm in this repertoire. But it leaves one real case uncovered.

A **restatement** is a later note at the same staff position carrying an explicit accidental with
the **same sounding adjustment** as one the edit removes. Remove the original and the restatements
are left asserting something the notator no longer means — and no context arithmetic can find them,
because on its own line a restatement may be doing real work. So ask.

Worked example, key of D♭ (five flats — F is unaltered):

```
before                              after "Yes"
1:  F♭(0)  G(1)  F♭(2)              1:  F(0)  G(1)  F(2)
2:  F♭(0)  A(1)  F(2)   F♮(3)       2:  F(0)  A(1)  F(2)  F(3)
```

Toggling the ♭ off `1:0` scans forward through the song and finds `1:2` and `2:0`, both explicit ♭
at that staff position. `2:0` is *not* redundant on its own line — line-reset means its context is
the key, where F is natural — which is precisely why arithmetic cannot identify it and the user
must. `2:3` is not a restatement at all; Phase 9a clears it, because its context moves from ♭ to
nothing and its own ♮ becomes redundant.

### Why this is not reconciliation

#676's invariant is *every note keeps the pitch it had, unless the user changed that note.* This
feature does the opposite: removing `2:0`'s ♭ changes the pitch of `2:2`, a bare F that inherited
it. That is the point of the feature, and the dialog is the consent that licenses it. Folding it
into #676 would make that issue contradict itself, which is why it carries its own.

---

## Mechanism

**Scan.** Forward from the edit point through the rest of the song, at the removed accidental's
staff position, collecting explicit accidentals of the same sounding adjustment. **Stop at an
explicit cancellation** — the first explicit accidental at that staff position with a different
adjustment. Past that point a matching accidental reads as a fresh decision, not a restatement.

**Prompt.** `Remove restatements of this accidental?` — Cancel / No / Yes. Shown only when the scan
finds at least one restatement. One prompt per edit, however many accidentals the edit removes.

| | |
|---|---|
| **Yes** | Remove every restatement found, and suppress protection (below). |
| **No** | Remove nothing. Phase 9a's rule still runs. |
| **Cancel** | Abort the whole edit. Nothing is mutated and no undo step is created. |

**Propagation suppression — the part that inverts the invariant.** Left alone, reconciliation would
see `2:2`'s pitch about to change and materialize a ♭ onto it, handing back
`F(0) A(1) F♭(2) F(3)` and defeating the feature. Answering Yes must therefore **suspend
materialization** at that staff position, from the removal forward until the next explicit
accidental at that position. Removal still runs — that is what clears `2:3`.

Suppressing by staff position is safe rather than merely convenient: a bare note at that position
before any explicit accidental there necessarily resolved through the removed accidental or through
the key, and in the key case `before` and `after` already agree, so nothing was going to be
materialized.

**Every removal path prompts** — accidental toggle-off, accidental change (which removes the old
one), single and range delete, cut, paste-replace, and pitch shift (which clears the moved note's
accidental). The point is to warn, since otherwise these accidentals are easy to overlook.

**Adding an accidental never prompts.** Restatements are the house norm here, so later accidentals
made redundant by an addition are wanted, not surplus.

**The dialog must not be open inside a modification bracket.** Phase 8 established this when it
moved the ending confirms out of `song.withModification`. The prompt runs in each site's decide
phase, before any bracket opens.

---

## Settled decisions

| | |
|---|---|
| What counts as a restatement | Same staff position, same sounding adjustment, later in the song. |
| Scan bound | Stops at an explicit cancellation. |
| Prompt scope | Every path that removes an explicit accidental; never on addition. |
| Buttons | Cancel / No / Yes, with Cancel aborting the whole edit. |
| Granularity | One prompt per edit, not per accidental. |

---

## Phase 1 — Scan and prompt

Read `.agents/guides/strings.md`, `.agents/guides/option-dialogs.md` and
`.agents/guides/mutations.md` first.

1. Add a pure static scan to `AccidentalReconciliation`:
   `findRestatements(Song, Line, int fromIndex, int staffPosition, Accidental removed)`, returning
   the notes to offer. Forward through the song from the edit point, matching on staff position and
   equal `getPitchAdjustment`, stopping at the first explicit accidental at that position with a
   different adjustment. Pure and pre-mutation, like everything else in that class.
2. Add the UI-side helper — `songscribe.ui.edit.AccidentalRestatements` — that runs the scan for an
   edit's removed accidentals, shows the three-button confirm through `OptionDialogs` when the scan
   is non-empty, and returns Cancel / No / Yes plus the notes to remove.
3. Add the string per the strings guide. Update the parent plan's Verification section, which
   asserts no new user-facing string was added — true for #676, not for this.
4. Add the `suppressedStaffPositions` parameter to `AccidentalReconciliation.materialize()`, empty
   for every edit that has not been through this feature. A note at a suppressed position skips
   materialization only; removal still applies. Drop a position from the set on reaching an explicit
   accidental at that position, so suppression covers exactly the region between the removal and the
   next explicit accidental there.
5. Run `./scripts/compile.sh` and `./scripts/test.sh unit`.

## Phase 2 — Wire the removal paths

In each, the prompt runs in the decide phase, before any modification bracket opens:

- `SelectionCoordinator.applyActionToSelection` — pass 1 (`decideChanges`), alongside the existing
  ending confirms already moved out of the bracket.
- `ScoreViewController.deleteElementRange` — before `withModification`. Covers cut and delete.
- `ScoreViewController.tryInsertFragment` — before the fit gate, reusing the existing `LINE_FULL`
  "nothing was mutated" path for Cancel.
- `PitchShifter.shiftPitch` — before `moveGroupAndPlayAnchor`.
- `NoteDragHandler.handleRelease` — after the drag, before `commitPitchShift`. **Cancel here means
  reverting the live-mutated positions** from each entry's `beforeClone` /
  `originalStaffPositionSp`. This is a new abort path; today a release always commits.

Apply an accepted removal set as `IntendedChange(index, null, staffPosition)` per removed note,
reconciled **per line** — run `reconcileModification(thatLine, changes)` on each line the removals
touch, with that staff position suppressed. No song-level entry point is needed; the existing
single-line API covers it.

Confirm the whole edit — the original change, the removals across lines, and the reconciliation on
each — is **one undo step**, and verify the modification bracket spans lines correctly. Nothing in
#676 recorded mutations on more than one line at a time.

## Phase 3 — Tests

`findRestatements`: same-adjustment matches collected across lines; the scan stopping at an explicit
cancellation; an empty result when nothing matches. Suppression: materialization skipped at a
suppressed staff position, removal still applied, suppression dropped at the next explicit
accidental there. The worked example above as an end-to-end case for each of Yes, No and Cancel.

## Phase 4 — Manual verification

- The worked example across two lines, answering Yes, then No, then Cancel.
- One Cmd+Z after each, restoring everything across both lines in a single step.
- The prompt appearing on delete, cut, paste-replace and both pitch-shift paths.
- No prompt when an edit only adds an accidental.

---

## Verification

- `./scripts/compile.sh` reports SUCCESS and `./scripts/test.sh unit` is green.
- The worked example produces the stated result for Yes, leaves everything for No, and mutates
  nothing for Cancel.
- One Cmd+Z undoes any of it in a single step, including across lines.

---

## Assumptions worth rejecting at review

1. **Cancel on a drag release reverts the drag.** The only sensible reading, but it is new
   behaviour — a release currently always commits.
2. **One prompt per edit, not per accidental.** A range delete removing accidentals at several
   staff positions asks once and applies to all.
3. **`findRestatements` lives on `AccidentalReconciliation`** in `songscribe.layout`, following that
   class's own placement, even though a song-wide scan is not layout work.
