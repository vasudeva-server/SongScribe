# Accidental Removal Reconciliation

Fixes a defect found during Phase 10 verification of
`plans/accidental-context-implementation.md`. Lands as two new phases (10a, 10b) before that
plan's Phase 11, plus additions to its Phases 11 and 12.

---

## Context

Reconciliation is structurally **add-only**. `AccidentalReconciliation.materialize()` skips every
note that already carries an explicit accidental:

```java
// AccidentalReconciliation.java:319
if (projected.userChanged || (projected.explicit != null)) {
    continue;
}
```

and `Materialization` (line 102) declares `accidental` non-nullable, filled only from a non-null
value at line 333. So the engine can give a note an accidental but has no way to take one back.

The reported repro, in the key of D♭ (five flats — F is unaltered):

| step | index 0 | index 2 | |
|---|---|---|---|
| start | F | F | both natural from the key |
| toggle ♭ on index 0 | F♭ | F♮ | index 2 would inherit the ♭, so a ♮ is materialized — correct |
| toggle ♭ off index 0 | F | F♮ | index 2 carries an explicit ♮, so line 319 skips it — **stranded** |

Two mechanisms close this. They partition the work and do not overlap.

---

## Mechanism 1 — De-materialization

Clear a note's explicit accidental when this edit **both** moved the context arriving at that note
**and** left the accidental sounding identical to the new context:

```
clear when:
    adj(contextBefore) != adj(contextAfter)    // this edit moved the context
    AND adj(own)        == adj(contextAfter)   // own is now redundant
```

The mirror of the existing materialize rule, in the same left-to-right pass, comparing sounding
adjustments and never enum identity.

**What the guard buys.** An accidental that was *already* redundant when placed can never be
removed: "already redundant" means `adj(own) == adj(contextBefore)`, which together with the second
condition forces `adj(contextBefore) == adj(contextAfter)`, contradicting the first. So a
deliberate restatement, or a courtesy accidental placed where the note already sounded that way, is
untouched by every edit that does not move its context. That is exactly the class Mechanism 2
exists to handle, so the two do not double-handle anything.

**Parenthesized accidentals are treated identically** — no exemption. Parentheses record that the
notator chose to write something they did not have to, which says nothing about whether a later
edit obviated it.

**Consequence for the refusal contract.** `StaffElement.setAccidental` (line 483) silently clears
`isAccidentalInParentheses` when the accidental goes null, and `AccidentalMaterializer.restore()`
(line 180) puts the accidental back but not the flag. `SavedAccidental` must therefore also carry
and restore the flag, restoring accidental first and parentheses second, because
`setAccidentalInParentheses` (line 493) guards on the accidental being non-null. Only the paste and
insert paths are affected — `SelectionCoordinator` measures via clones and applies only after its
gate has passed, so it has nothing to restore.

**Removal applies to surviving notes only.** A pasted or inserted note keeps the notation it
arrived with, matching the settled "a fragment carries semantic content" rule from Phase 5. Sound
is preserved either way; this is only about what is drawn.

---

## Mechanism 2 — Restatement propagation

A **restatement** is a later note at the same staff position carrying an explicit accidental with
the **same sounding adjustment** as one this edit removes. Mechanism 1 provably cannot reach these,
and no context arithmetic can identify them — on its own line a restatement may be doing real work.
Only the user knows it restates an earlier intent, so ask.

Worked example, key of D♭:

```
before                              after "Yes"
1:  F♭(0)  G(1)  F♭(2)              1:  F(0)  G(1)  F(2)
2:  F♭(0)  A(1)  F(2)   F♮(3)       2:  F(0)  A(1)  F(2)  F(3)
```

Toggling the ♭ off `1:0` scans forward through the song and finds `1:2` and `2:0` — both explicit
♭ at that staff position. `2:0` is *not* redundant on its own line (line-reset means its context is
the key, where F is natural), which is precisely why arithmetic cannot find it. `2:3` is not a
restatement — it is cleared by Mechanism 1, because its context moves from ♭ to nothing and its own
♮ becomes redundant.

**Scan.** Forward from the edit point through the rest of the song, at the removed accidental's
staff position, collecting explicit accidentals of the same adjustment. **Stop at an explicit
cancellation** — the first explicit accidental at that staff position with a different adjustment.
Past that point a matching accidental reads as a fresh decision, not a restatement.

**Prompt.** `Remove restatements of this accidental?` — Cancel / No / Yes. Shown only when the scan
finds at least one restatement. One prompt per edit, however many accidentals the edit removes.

- **Yes** — remove every restatement found, and suppress protection (below).
- **No** — remove nothing; Mechanism 1 still runs.
- **Cancel** — abort the whole edit; nothing is mutated and no undo step is created.

**Propagation suppression — the part that inverts the invariant.** Removing `2:0`'s ♭ changes the
pitch of `2:2`, a bare F that inherited it. Reconciliation's whole purpose is to prevent that, so
left alone it would materialize a ♭ onto `2:2` and hand back `F(0) A(1) F♭(2) F(3)`, defeating the
feature. Answering Yes must therefore **suspend materialization** at that staff position, from the
removal forward until the next explicit accidental at that position. De-materialization still runs
— that is what clears `2:3`.

Suppressing by staff position is safe rather than merely convenient: a bare note at that position
before any explicit accidental there necessarily resolved through the removed accidental or through
the key, and in the key case `before` and `after` already agree, so nothing was going to be
materialized.

**All removal paths prompt** — accidental toggle-off, accidental change (which removes the old
one), single and range delete, cut, paste-replace, and pitch shift (which clears the moved note's
accidental). Adding an accidental never prompts: restatements are the house norm here, so
newly-redundant later accidentals are wanted, not surplus.

**The dialog must not be open inside a modification bracket.** Phase 8 established this when it
moved the ending confirms out of `song.withModification`. The prompt runs in each site's decide
phase, before any bracket opens.

---

## Settled decisions

| | |
|---|---|
| Removal rule | Context moved **and** now redundant. Not provenance tracking (no home in MusicXML, lost on reload), not blanket redundancy removal (strips unrelated restatements). |
| Parenthesized accidentals | No exemption — treated exactly like bare ones. |
| Pasted/inserted notes | Never de-materialized; surviving notes only. |
| Restatement scan bound | Stops at an explicit cancellation. |
| Prompt scope | Every path that removes an explicit accidental. |

---

## Phase 10a — De-materialization in the shared engine

**Files:** `src/main/java/songscribe/layout/AccidentalReconciliation.java`,
`src/main/java/songscribe/layout/AccidentalMaterializer.java`, and the five call sites.

1. Rename the record `Materialization` → `AccidentalChange` with
   `StaffElement.@Nullable Accidental accidental`, null meaning "clear this note's explicit
   accidental". Use `jet_brains_rename` so all call sites update atomically. Rename
   `AccidentalMaterializer.materializeIfAccepted` → `applyIfAccepted`; keep both class names and
   update their javadoc to say they add *and* remove.
2. Give `ProjectedElement` two fields: `contextBefore` (the pre-mutation effective accidental
   ignoring the note's own, from `findEffectiveAccidental`) and `survivor`. The constructor is
   already five positional arguments — replace it with static factories `survivor(...)`,
   `inserted(...)` and `changed(...)` rather than growing it to seven with two booleans.
3. In `survivor(Line, int)` (line 284) compute `contextBefore` unconditionally, not only when the
   note has no accidental of its own. `before` stays `own != null ? own : contextBefore`.
4. Add the removal branch to `materialize()` (line 301), keeping the pitched-note guard ahead of it
   so barlines and repeats are never candidates:

   ```java
   if (projected.explicit != null) {
       if (!projected.survivor) { continue; }
       if (adjustmentOf(projected.contextBefore) == adjustmentOf(after)) { continue; }
       if (adjustmentOf(projected.explicit) != adjustmentOf(after)) { continue; }

       projected.explicit = null;                       // later notes now resolve past it
       changes.add(new AccidentalChange(projected.element, null));
       continue;
   }
   ```

   Setting `explicit` to null before continuing is what lets the rest of the pass see the removal —
   the same mechanism the materialize side already relies on.
5. Add a `Set<Integer> suppressedStaffPositions` parameter to `materialize()`, empty for every
   edit that has not been through Mechanism 2. A note at a suppressed position skips
   materialization only; removal still applies. Drop a position from the set on reaching an
   explicit accidental at that position, so suppression covers exactly the region between the
   removal and the next explicit accidental there.
6. Add `priorInParentheses` to `AccidentalMaterializer.SavedAccidental` (line 91) and restore it in
   `restore()` (line 180) after the accidental, per **Consequence for the refusal contract** above.
7. Confirm each call site tolerates a null accidental: the `line.modifyElement(index,
   EnumSet.of(ElementField.ACCIDENTAL), ...)` applications in `SelectionCoordinator` (~782-789),
   `PitchShifter`, `ScoreViewController.deleteElementRange` and `tryInsertFragment` need no change,
   but check whether an `ElementField` for the parentheses flag exists and should join the set.
8. `./scripts/compile.sh`, then `./scripts/test.sh unit`.

This alone fixes the reported repro.

---

## Phase 10b — Restatement scan and prompt

Read `.agents/guides/strings.md`, `.agents/guides/option-dialogs.md` and
`.agents/guides/mutations.md` first.

1. Add a pure static scan to `AccidentalReconciliation`:
   `findRestatements(Song, Line, int fromIndex, int staffPosition, Accidental removed)` returning
   the notes to offer. Forward through the song from the edit point, matching on staff position and
   equal `getPitchAdjustment`, stopping at the first explicit accidental at that position with a
   different adjustment. Pure and pre-mutation, like everything else in the class.
2. Add the UI-side helper — `songscribe.ui.edit.AccidentalRestatements` — that runs the scan for an
   edit's removed accidentals, shows the three-button confirm through `OptionDialogs` when the scan
   is non-empty, and returns Cancel / No / Yes plus the notes to remove. One prompt per edit even
   when the edit removes several accidentals at several staff positions.
3. Add the string per the strings guide. This supersedes the "no new user-facing string" line in
   the parent plan's Verification section, which was an assertion of that plan, not a requirement.
4. Wire the five removal paths. In each, the prompt runs in the decide phase, before any
   modification bracket opens:
   - `SelectionCoordinator.applyActionToSelection` — pass 1 (`decideChanges`, ~806-861), alongside
     the existing ending confirms that were already moved out of the bracket.
   - `ScoreViewController.deleteElementRange` (~655) — before `withModification`. Covers cut and
     delete.
   - `ScoreViewController.tryInsertFragment` (~756) — before the fit gate, reusing the existing
     `LINE_FULL` "nothing was mutated" path for Cancel.
   - `PitchShifter.shiftPitch` — before `moveGroupAndPlayAnchor`.
   - `NoteDragHandler.handleRelease` (~218) — after the drag, before `commitPitchShift`. **Cancel
     here means reverting the live-mutated positions** from each entry's `beforeClone` /
     `originalStaffPositionSp`, which is a new abort path; today a release always commits.
5. Apply an accepted removal set as `IntendedChange(index, null, staffPosition)` per removed note,
   reconciled **per line** — run `reconcileModification(thatLine, changes)` on each line the
   removals touch, with that staff position suppressed. No song-level entry point is needed on the
   engine; the existing single-line API covers it.
6. Confirm the whole edit — the original change, the restatement removals across lines, and the
   reconciliation on each — is **one undo step**. Verify the modification bracket spans lines
   correctly; nothing in the plan so far has recorded mutations on more than one line at a time.
7. `./scripts/compile.sh`, then `./scripts/test.sh unit`.

---

## Phase 10c — Re-run manual verification

Re-run the parent plan's Phase 10 scenarios, plus:

- The reported repro: key of D♭, bare `F G F`. Toggle ♭ on index 0 → index 2 gains a ♮. Toggle it
  off → index 2 loses the ♮, and both F sound natural.
- The worked example above, across two lines, answering Yes, then No, then Cancel.
- Undo after each: one Cmd+Z restores everything, across both lines.
- A parenthesized materialized accidental: parenthesize the ♮, then toggle the ♭ off — the (♮) goes.
- A restatement that is *not* redundant after the edit stays: key of D♭, `F♭ G F♭`, answer **No** —
  index 2 keeps its ♭ and still sounds F♭.

---

## Additions to the parent plan's Phases 11 and 12

**Phase 11 (tests).** Add to `AccidentalReconciliationTest`: the repro as a unit case; removal
requires both conditions (assert no removal when the context did not move, and none when the
accidental is not redundant after); the algebra result — an already-redundant accidental is never
removed; a pasted note carrying an accidental is never de-materialized; parenthesized accidentals
are removed like any other; suppression prevents materialization at a suppressed staff position but
not removal. Add `findRestatements` cases: same-adjustment matches collected across lines, the scan
stopping at an explicit cancellation, and an empty result when nothing matches. Extend the
`AccidentalMaterializer` contract test to cover a null-valued change and the parentheses flag
surviving a refused gate.

**Phase 12 (docs).** Document both mechanisms in `docs/clipboard.md` alongside the materialize
rule: the removal rule and its guard, the algebra result and why it makes the prompt necessary, the
scan and its stopping condition, and propagation suppression as a deliberate, consented inversion
of the invariant. Update the Verification section's user-facing-string line.

---

## Verification

- `./scripts/compile.sh` reports SUCCESS.
- `./scripts/test.sh unit` is green.
- The repro at the top of this file behaves as the "after" column describes.
- The two-line worked example produces the stated result for Yes, leaves everything for No, and
  mutates nothing for Cancel.
- One Cmd+Z undoes any of it in a single step, including across lines.

---

## Assumptions worth rejecting at review

1. **Cancel on a drag release reverts the drag.** The only sensible reading, but it is new
   behavior — a release currently always commits.
2. **One prompt per edit, not per accidental.** A range delete removing accidentals at several
   staff positions asks once and applies to all.
3. **`findRestatements` lives on `AccidentalReconciliation`** in `songscribe.layout`, following
   that class's own placement, even though a song-wide scan is not layout work.
4. **Class names stay** (`AccidentalReconciliation`, `AccidentalMaterializer`) while the record and
   the method are renamed. Keeps the diff small at the cost of "Materializer" also removing.
