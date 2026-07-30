# Issue 696 — Strategy: a line that will not fit

## Decision

A line that cannot fit is **always laid out and always drawn**. Layout stops
having a failure mode.

1. When the solver reports the line infeasible, lay the columns out on their
   strut floors — the tightest legal spacing — instead of abandoning the line.
2. The content past the right edge of the staff is clipped. No overflow is
   painted outside the staff.
3. The five staff lines of an affected line are drawn in **red**.
4. Once per document, warn:
   > One or more lines could not fit in the current margins and part of their
   > content will be clipped. Those lines are drawn in red.

Unconditional — no prompt, no per-line choice, no document repair. The score
itself carries the indication; the dialog only says what the red means.

## Why this and not the alternatives

Rejected during discussion:

- **Reflow onto following lines** (move what does not fit to the next line,
  cascading, adding lines as needed). Too large to attempt, and the reason is not
  only the range-splitting problem: accidental context is strictly per-line —
  `findEffectiveAccidental(Line targetLine, int index)` scans backward within one
  line only (`StaffElement.java:762`), and `AccidentalRestatements` says it
  outright, "accidental context resets at the line boundary"
  (`AccidentalRestatements.java:47`). Moving a note across a line boundary
  therefore **changes the pitch it sounds** whenever it depended on an accidental
  left behind, and inserting notes at the head of the receiving line changes the
  context for that line's existing notes too. `AccidentalReconciliation` can fix
  this up, but its own design routes the ambiguous cases to a user prompt
  (`AccidentalReconciliation.java:85-88`), so one reflow could raise a cascade of
  them. Reflow is a semantic operation dressed as a layout one.
- **Shrinking** — per-line lyric-font shrink, or rendering the whole line at a
  reduced scale to fit. Both hide the problem rather than showing it, and the
  uniform-shrink variant needs a per-line scale factor threaded through every
  vertical-extent consumer.
- **Refusing to open the file.** Strands the user with a file they can neither
  view nor repair, when the likeliest cause is that our own spacing rules changed
  under a previously valid document.
- **Gating the edits that cause it.** Out of scope here: the lyric font size has
  its own issue, and line width is to be replaced by pure margins, which will be
  gated then.

## What happens today

1. `SpringSpacer.compress` returns `SpringSolveResult.infeasible()` in exactly one
   case: `availableSpanSs < Σ strutSs` — every gap already on its collision floor
   and the chain still overflows (`SpringSpacer.java:154`).
2. `LayoutEngine.placeColumnsHorizontally` records `LINE_TOO_FULL_ERROR` and
   `layout()` returns **null** (`LayoutEngine.java:365`, `:285`). That is
   `layout()`'s only null return — the other `return null` in the file is an
   unrelated private helper.
3. `LineComponent.performLayout` sets `lineDoesNotFit = true`, leaves
   `layoutResult` unchanged, and defers a modal warning
   (`LineComponent.java:479`, `:502`).
4. `LineComponent.render` skips `lineRenderer.render` entirely when
   `layoutResult == null` (`LineComponent.java:557`).

On the load path every affected line is fresh with no prior layout, so step 4
always applies: the line collapses to `LineSpacing.MIN_LINE_HEIGHT_SS`
(`LineComponent.java:623`) and draws nothing. One modal fires for the whole file —
the static `lineDoesNotFitWarningScheduled` guard collapses every affected line
into a single dialog — and it names no line and offers nothing.

Away from load, when a line *had* a fitting layout, the stale layout keeps
painting instead, so the edit silently appears to do nothing. The same change
removes that too.

## Two things that make this cheap

- **The clip is already exactly right.** `LineComponent.getPreferredSize` returns
  `song.getLineWidthSs()` in view px (`LineComponent.java:627`), so Swing already
  clips a line's painting at the end of the staff. "Draw as much as you can, clip
  at the staff end" needs no clipping code at all.
- **The struts are already in hand.** `LineSolution` carries the solved spring
  chain (`HorizontalSpacingCalculator.java:419`), so on an infeasible verdict
  `LayoutEngine` can read each floor straight off `springs.get(i).strutSs()`.

## The trap: do not change the solver

`SpringSpacer.compress` must keep returning `infeasible()`. Five call sites use
that verdict to **refuse an edit**, and they would all silently stop refusing
anything:

- `InsertionSpacingCalculator.java:105`, `:180`, `:226`, `:743` — insertion,
  fragment paste, grace/host room, modification
- `LyricEditFitCalculator.java:63` — typing in the lyric editor

The fallback therefore belongs in `LayoutEngine.placeColumnsHorizontally`, which
*responds* to the infeasible verdict. The solver's honest answer is unchanged, the
gates keep working, and the pre-check/layout agreement documented at
`HorizontalSpacingCalculator.java:408-413` still holds: a line the gate accepts is
one layout can place normally.

## Status: implemented

Decisions taken during implementation: title "Lines Do Not Fit"; warn once, never re-armed
within a document; selection color wins over red; print/export is not implemented yet, and
when it is, it prints in black and warns again that the output is clipped.

Four deviations from the change list below, all noted where they occur:

1. **A new title key, not a repurposed one.** `alert.title.line.too.full` ("Line Too Full")
   survives because `LyricEditor` still uses it for its own single-line lyric-fit error
   (`LyricEditor.java:1043`). The aggregated alert got new keys —
   `alert.lines.do.not.fit` and `alert.title.lines.do.not.fit`. Only the old *message*
   key was retired.
2. **`unexpectedNullLayout()` kept**, along with `LineRenderer.buildInvariants`' null
   guard. A null layout no longer means "would not fit" — it now means the pass never ran,
   which happens only without a score view. That is a real invariant worth asserting, so
   the throws stay and their Javadoc was corrected instead.
3. **`LayoutEngine.getLastError()` and `lastError` removed.** They existed only to carry
   `LINE_TOO_FULL_ERROR`; with it gone the field could never be non-null.
4. **`positionTerminalFlushRight` is skipped on an overflowing line.** It was first left
   untouched, on the reasoning that its clamp to the pair's strut (`LayoutEngine.java:412`)
   already resolves to the position the strut chain gives. Review found that reasoning is not
   universally true: the chain's struts come from `buildSprings`, which threads each pair's
   outer neighbours, while the clamp recomputes the strut through the three-argument
   `buildSpring`, which passes `null` for them. The two differ when the column before the
   terminal hosts a grace note carrying a syllable, and the clamp is then the lower of the
   two — so the terminal would be pulled left of where the chain placed it, past the syllable
   it is meant to clear. Skipping the snap outright makes the code say what the comment
   claimed, and removes the discrepancy rather than documenting around it. Covered by
   `LayoutEngineTest.OverflowingLines.testOverflowingLastLineLeavesItsTerminalPastTheStaff`.

Verification: `./scripts/compile.sh` SUCCESS; `./scripts/test.sh unit` 6435 passed, 1
skipped. Each new assertion was checked against a deliberately broken implementation, so
none passes vacuously: returning natural lengths instead of struts fails the strut
assertion; swapping the colour precedence fails the selection-wins assertion; setting the
once-per-document guard after the dialog instead of before it turns one alert into three;
dropping `LineComponent.resetOverflowWarning()` from `ScoreView.setSong` fails the
re-arming test; and reinstating the `MIN_LINE_HEIGHT_SS` fallback for an overflowing line
fails the sizing test.

The fixture `overflowing-lines.musicxml` cycles `CROTCHET`, `QUAVER`, `SEMIBREVE` rather
than using one note type throughout. Identical notes give every gap the same collision
floor, which would let a placement bug that reused a single floor for all of them pass the
per-gap assertion; three widths give three floors, and the test asserts it is seeing more
than one.

## Change list

**Layout**

- `LayoutEngine.placeColumnsHorizontally` — on `solution.isInfeasible()`, lay the
  columns out from `solution.firstXSs()` using `springs.get(i).strutSs()` as each
  gap, and record that the line overflowed. Return true. Delete
  `LINE_TOO_FULL_ERROR` and its `lastError` write.
- `LayoutResult` — carry an `overflows` flag, set by the builder. Putting it here
  rather than on `LineComponent` means the renderer needs no new plumbing:
  `LineInvariants` already holds the `LayoutResult`.
- `LayoutEngine.layout` — drop `@Nullable` from the four overloads. It can no
  longer return null.

**Rendering**

- `LineRenderer.drawStaffLines` — pick red when the layout overflows. The color is
  chosen at one line already (`LineRenderer.java:202`) inside a
  `GraphicsState.save`, so it is an `if`/`else if`/`else` there. Needs the
  FlatLaf-properties guide, since `RenderingUtils.STAFF_LINE_COLOR` is a bare
  `Color.BLACK` (`RenderingUtils.java:110`) and a new UI color should not be.

**Line component — deletions**

With `layout()` non-null, all of this goes:

- the `lineDoesNotFit` field and `setLineDoesNotFit`
- `unexpectedNullLayout()` and its two call sites
- the `layoutResult != null` guards in `render`
- the `MIN_LINE_HEIGHT_SS` fallback in `getPreferredSize`
- the `Staff.MIN_ABOVE_STAFF_SS` fallback in `calculateMiddleLineYSs`
- `LineRenderer.buildInvariants`' null-layout `RuntimeError.exit`
  (`LineRenderer.java:154`)

The static `lineDoesNotFitWarningScheduled` guard and `warnLineDoesNotFit` stay in
shape but key off `result.overflows()` instead of a null result.

**Strings** (needs the strings guide)

- `alert.line.too.full` — replace with the wording above.
- `alert.title.line.too.full` — "Line Too Full" no longer fits a message about
  several lines and about clipping. Open question below.

**Tests**

- Three prose comments describe the old outcome and need updating:
  `E2ETest.java:135`, `SongDefaultsTest.java:340`,
  `MusicXmlWriterOutputTest.java:1202`.
- `SpringSpacerTest` and `HorizontalSpacingCalculatorSpringTest` should keep
  passing untouched — that is the check that the solver was not changed.
- New: an infeasible line yields a non-null layout whose gaps equal the struts and
  whose `overflows` flag is set; `drawStaffLines` picks the overflow color.

## Open questions

1. **Dialog title.** The body now covers several lines and mentions clipping, so
   "Line Too Full" reads wrong. Suggestion: "Lines Do Not Fit".
2. **How often to warn.** Today's guard resets when the dialog is dismissed, so
   any later layout of an overflowing line warns again. Once per document open is
   probably what "once per document" should mean — worth pinning down.
3. **Selected *and* overflowing.** `drawStaffLines` currently gives the selection
   color priority over everything. Does selection still win on an overflowing
   line, or does red?
4. **Print and export.** Whether they share this layout path, and so whether an
   overflowing line prints red. Not yet traced.
