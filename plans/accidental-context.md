# Accidental context
High-level plan. Tracked by #675 (resolver scope) and #676 (reconciliation). Once approved, `/make-plan` turns this into the detailed phased plan.
## Problem
A note stores a staff position plus an _optional explicit_ accidental. It does not store a pitch — pitch is derived at read time by `StaffElement.getPitch`, which resolves the effective accidental in three steps:

1. the note's own explicit accidental, else
  
2. the nearest earlier note at the **same staff position** with an explicit accidental, else
  
3. the line's key signature.
  

Two defects in that scan:

- **It spills past barlines.** Convention says any structural marker — barline or repeat — cancels prior accidentals. The scan runs to the start of the line.
  
- **It ignores ties across a barrier.** Convention carries an accidental through a tie that crosses a barline; the scan would stop at the barrier.
  

**Resetting at the end of a line is house convention, not a defect.** The repertoire is largely meterless, and metered music here closes its staves with a barline at the end of a row, so the row boundary and the measure boundary coincide in practice. 102 songs read differently under this convention than under strict measure scoping — accepted. See the structuring note below so revisiting it later is cheap.

Separately, and largely independent:

**Nothing reconciles the scan across an edit.** Any operation that changes which explicit accidentals sit on a line, or where, silently changes the sounding pitch of other notes the user never touched. `docs/clipboard.md` §6 does not list it as deferred — it is an unrecognized hole, not a known one.
### Corpus figures, and what each one sizes
| Figure | Measures | Sizes |
| --- | --- | --- |
| 102 songs | a genuine alteration, never reconfirmed or cancelled, whose bare pitch recurs on a **later line**, where the accidental's line doesn't end on a barline | the cost of keeping line-reset |
| 4 songs | a tie crossing a barline | migration risk for the tie escape |
| a few dozen songs | an accidental inherited across a barline **within** a line | the barline barrier's playback impact |
| 23,860 occurrences / 9,851 lines / 6,860 files | an explicit accidental followed, within the line, by a note at the same staff position without one | the reconciliation gap — **but over-counted** |

The last figure over-counts: it includes explicit accidentals that merely restate the key signature, which contribute nothing, since moving or removing them changes no pitch. Filtering to accidentals that actually deviate from the key would shrink it — but not the work. The reconciliation has to be correct whatever the number turns out to be, so it was not re-run.
## Why the barline barrier matters: export fidelity
SongScribe does not ingest external MusicXML, but other applications must be able to import SongScribe's accurately. Today they cannot, for the cross-barline case.

`MusicXmlNoteWriter.writeAccidental` emits `<accidental>` only when a note carries an explicit one, while `PitchSpelling.soundingAlterFor` derives `<alter>` from the unbarriered scan. A note inheriting an accidental across a barline therefore exports as `<pitch>` with `<alter>1</alter>` and **no** `<accidental>` element.

A standard consumer treats `<pitch>` as authoritative and — since standard reading cancels the accidental at the barline — draws a sharp to show it. **The same file renders with an accidental in MuseScore or Finale that SongScribe does not draw.**

**The migration is free.** The reader ignores `<alter>` and re-derives sound from the written accidentals (`NoteAccumulator:507-508`), so existing files pick up the corrected reading on next open — no rewrite, no version bump, no migration step.
## The resolver's target shape
Three separate pieces of work each touch `findEffectiveAccidental`. Settle its shape once and land the pieces against it:

```
effectiveAccidental(line, index, staffPosition):
  1. the note's own explicit accidental                          → return
  2. scan back through preceding elements:
       barrier (barline | repeat | key change) →
           if this note ends a tie anchored before the barrier,
              resolve at the anchor instead  (same pitch ⇒ same staff position)
           else stop
       same staffPosition && explicit accidental                 → return
  3. keyInEffectAt(line, index) → its accidental for this pitch class, else null
```

- `ElementType.isBarLine()` and `isRepeat()` already exist. Do **not** use `isNonDuration()`; it bundles in `isBreathMark()`, which cancels nothing.
  
- **Isolate the backward traversal.** "Scan back through preceding elements" is a single private helper yielding elements in reverse order — today, this line's elements from `index-1` down to `0`. If line-reset is ever revisited, that helper continues into the previous line and nothing else changes: the barrier test, the tie escape, the staff-position match and the key fallback are all untouched. That is the whole structuring cost of keeping the option open.
  
- **The tie escape is an export requirement, not an edge case.** Without it the second note of a cross-barline tie exports `<alter>` for the unaltered pitch, contradicting the tie a consumer also reads. Only 4 songs have one today, but that sizes migration risk, not the requirement.
  
- `keyInEffectAt(line, index)` **is introduced now** returning the line's key unchanged — a no-op that makes #53 (mid-line key changes) a one-method change instead of a resolver rewrite. A key change is itself a barrier, so #53 adds to the barrier list too.
  
- **Same-octave matching stays.** The scan matches on staff position, which is ordinary staff-notation convention and what export fidelity depends on. See ABC import below, where this differs from the source format.
  
## The invariant
> Every note keeps the pitch it had, unless the user changed that note.

Two populations: **pasted** notes keep the pitch they had in the source; **surviving** notes keep the pitch they had before the mutation.
## The rule
For a note whose effective accidental would change, materialize an explicit one:

```
if adjustment(A_before) != adjustment(A_after):
    note.accidental = (A_before != null) ? A_before : NATURAL
```

- Compare **adjustments**, not enum identity — `null` and `NATURAL` sound alike, as do `FLAT` and `NATURAL_FLAT`; no glyph for a difference nobody can hear.
  
- **null → NATURAL**, because you cannot write "nothing" and get a natural in a context that alters that pitch. This direction is the entire cross-key case and is easy to miss.
  

Two properties bound the work:

- Only a staff position carrying an explicit accidental **in the removed content or the inserted content** can change the context arriving at the boundary.
  
- For each such position, only the **first** following note lacking its own accidental needs fixing; later ones resolve from it. Stop at the first note that already has an explicit accidental.
  

Both bounds stay within the line, since the scan does. The key signature never appears in the algorithm — it is already branch 3, so resolving against source and destination compares the two keys implicitly. Cross-key paste falls out with no source key stored and no key comparison written.
## Architecture
**One shared reconciliation unit.** Given a line, a mutated region, and the pre-mutation effective accidentals, return the notes needing a materialized accidental. Pure, pre-mutation. Sits beside `InsertionSpacingCalculator`, not inside the paste path.

`Fragment` **carries a parallel** `List<@Nullable Accidental>` aligned with `elements`, size-checked in the compact constructor. `instantiate()` maps 1:1 in order, so alignment is preserved for free.

> Trap: resolve inside `Fragment.capture`'s loop against the **live original**. A clone's `line` still points at the source line but `getElementIndex` returns −1 for it (`StaffElement` overrides neither `equals` nor `hashCode`), so `clone.findLastAccidental()` silently skips the scan and returns the key alone.

**Ordering is mandatory.** Accidentals must be materialized _before_ the projected column chain is built. Then the fit gate and the committed layout are both automatically correct — `ElementColumnBuilder` derives extents including accidental width, and `LayoutEngine` treats accidental widths as a layout input. No per-position shift machinery is needed: nothing in `layout/` reads `getXOffsetPx()`, so displayed geometry comes entirely from the whole-line solve.

When the materialized accidentals no longer fit: **warn and reject** via the existing `LINE_FULL` path. Fit is already unpredictable to a user because of compress-to-fit, so this adds no new class of surprise, and the existing strings cover it unchanged.
## Manual offsets
`xOffset`'s intended meaning is a **nudge from the computed position** — MusicXML `relative-x`. The feature is not implemented yet; the decision belongs here because the `Fragment` reshape lands in step 3.

> A fragment carries semantic content, not layout corrections. What the notes **mean** travels; how they were **nudged** does not.

This is the mirror of the accidental rule, and deliberately goes the opposite way. An accidental's context-dependence is the problem to solve, because pitch is semantic. A nudge is _purely_ contextual — a correction to one spring solve, with specific neighbours, under a specific header width. Pasted elsewhere it is meaningless at best, and at worst recreates the collision it was made to fix.

- **Element fragment: zero the offsets on instantiate**, explicitly. Today `copyStateFrom` copies `xOffset` and `tryInsertFragment` happens to overwrite it; once offsets mean something that overwrite becomes load-bearing by accident.
  
- **Line fragment (#612): offsets travel**, alongside the key signature and `elementSpacingRatio` it already carries. It reproduces its whole context — same elements, same neighbours, same header width — so the nudges remain correct.
  
- No rule keyed on how _similar_ the destination context is. Predictability beats salvaging nudges on a near-miss paste.
  
## Call sites
| #   | Path | Via `InsertionSpacingCalculator` | Fit-gated today |
| --- | --- | --- | --- |
| 1   | Single insert (including a preview-carried accidental) | yes | yes |
| 2   | Single / range delete | yes | n/a |
| 3   | Paste (insert, or delete + insert) | yes | yes |
| 4   | Accidental toggle — adds **and removes** | no  | **no** |
| 5   | Pitch shift (drag + arrow keys) | no  | n/a |
| 6   | ABC import (#11, future) | n/a | n/a |

Paste is the n-element case of insertion, not a separate concern; that is what makes 1–3 one call-site shape.

**ABC import** needs materialization in two cases, both handled by the shared unit:

- **Octave scope.** ABC's default applies an accidental to the pitch class in _all octaves_ within the bar; SongScribe matches same-octave only. Materialize where the readings differ.
  
- **Bars spanning a line break.** ABC does not reset at a line break; SongScribe does, by the convention above. Where an imported bar straddles a SongScribe line boundary, materialize so the sound survives the break.
  

Both apply to default-directive files, so #11 is a materialization call site by default and the shared unit must exist before the importer is built.
## The fit-gate gap
`SelectionCoordinator.applyActionToSelection` is the single un-gated path for **every** in-place element modification. The routed actions that change horizontal extent are `AccidentalAction`, `AccidentalInParensAction`, `DotAction`, and `ElementReplaceable` duration swaps. Fermata and dynamics stack independently of the note column and do not.

An infeasible line is not refused at mutation time — it surfaces later as `LINE_TOO_FULL_ERROR` and a **null** `LayoutResult`, so the line does not render at all. `calculateInsertion` already carries a comment naming exactly this failure as the reason the insertion gate exists; the modification path never got one.

Fix: `calculateModification` beside `calculateInsertion` and `calculateFragmentInsertion` — the same projection with a column **replaced** rather than spliced — gating `applyActionToSelection`, refusing with the existing `error.line.full.element`.
## Pitch shift
Adopt MuseScore's behavior: **clear any explicit accidental as soon as the note's staff position changes during a drag.** Undo restores it through the existing mutation records. Document in `moveGroupAndPlayAnchor`'s javadoc as intended behavior.

That covers the moved note but not the position it vacated, where a later note that inherited the departing accidental still changes silently — structurally identical to the toggle-off case. So this path also needs the shared reconciliation, in a different shape: the drag mutates live on every mouse step, so it captures pre-drag state once at drag start and reconciles once at finalize.
## Sequence
1. **Resolver scope, alone and first — #675.** Barrier at barline/repeat, tie escape past a barrier, the traversal isolated behind one helper, and `keyInEffectAt` introduced as a no-op. Driven by export fidelity, migrates for free, and every later step resolves through it. Smallest diff, clearest correctness story. Own branch off `develop`.
  
2. **Paste-mode stuck state.** A staff-header click during paste mode selects the line, flips EDIT→SELECT, and leaves paste mode active with nothing pasted — `mousePressed` is not paste-mode-aware while `mouseClicked` is. **Line select is disabled during paste mode**: replacing a line means selecting it _before_ Cmd+V, which is #612's Replace. Independent and tiny; it's a bug in what just landed, so it stays on the current `paste-into-line` branch and needs no issue of its own.
  
3. **Shared reconciliation unit +** `Fragment` **reshape + call sites 1–3.** Paste ships correct at the end of this.
  
4. `calculateModification` **+ gate** `applyActionToSelection` **+ call site 4.**
  
5. **Call site 5** (pitch shift), including the accidental-clearing rule.
  

Steps 3–5 are **#676** and share one branch, since they share the reconciliation unit. Step 4's fit gate is separable — it is a defect for dots and duration swaps regardless of accidentals — and can be split out if it grows.

Downstream: **#612** needs the `Fragment` reshape from 3; **#11** needs the shared unit from 3 and both materialization cases above; **#53** needs only `keyInEffectAt` from 1.
## Considered and rejected: storing pitch on `StaffElement`
MuseScore stores pitch and derives the displayed accidental — the inverse of this model. Rejected actively, not deferred:

- **ABC import reverses the argument.** ABC is a written-notation format. Import is transcription of written accidentals, with materialization only where octave scope or a line break differs. Storing pitch would insert _compute pitch, then re-derive the written accidental_ between two written representations, and require that round-trip to reproduce exactly what the source stated across 23,000+ files.
  
- **No MusicXML ingest**, so import fidelity was never a justification.
  
- **No transposition** in code, strings, or any open issue.
  
- The derivation does not disappear, it **inverts** — same logic, moved to layout time. The gain is one place instead of six; the cost is a migration across persistence, undo, layout, rendering, MIDI and MusicXML, plus a user-override concept for courtesy accidentals that the current model gets free, pitch _and_ spelling to keep consistent, and a new drift class where sound can disagree with the page.
  

Revisit if any of these become true:

1. **Transposition** becomes a feature — trivial with stored pitch, painful without.
  
2. A **pitch-based source** becomes a primary import path (MIDI, or MusicXML as ingest rather than interchange).
  
3. The call-site set **stops being finite** — if mutations that shift accidental context keep appearing, layout-time derivation wins on maintenance.
  
## Out of scope
- **#612 (cut/copy/paste entire line)** — separate downstream plan, following step 3.
  
- **#53 (mid-line key changes)** — separate. Step 1 shapes the resolver to accept it; nothing here implements it. It also breaks the one-key-per-line assumption in `HorizontalSpacingCalculator.isWithinHeaderXSs`, the shared boundary landed in 85fa21e0.
  
- **#11 (ABC import)** — separate. Listed above only as a future call site.
  
- **Line-reset revisited.** Kept as house convention; the traversal helper is the seam if it changes.
  
- **The** `xOffset` **dual meaning.** The field is documented and exported as a nudge, but serves as an absolute position store for the insert/delete/paste arithmetic and `HorizontalAdjustment`. Both cannot hold once a real nudge exists, and pasted notes plausibly export a spurious `relative-x` today. A prerequisite for the manual-offset feature, not for this work — its own issue.
  
## Status

No open questions. Ready for `/make-plan`.
