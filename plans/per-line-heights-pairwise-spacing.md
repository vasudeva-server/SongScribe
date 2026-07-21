# Per-Line Heights with Pairwise Midline Spacing (#591)
**Created:** 2026-07-20  
**Status:** Pending  
**BlockedBy:** —

* * *
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Per-Line Geometry API](#-phase-1-per-line-geometry-api) | ✅ Complete | —   |
| 2   | [StaffLinesLayout Manager](#-phase-2-stafflineslayout-manager) | ✅ Complete | —   |
| 3   | [Renderer + Consumer Migration](#-phase-3-renderer--consumer-migration) | ✅ Complete | —   |
| 4   | [LineComponent + Navigator Cleanup](#-phase-4-linecomponent--navigator-cleanup) | ✅ Complete | —   |
| 5   | [Always Reserve the Lyric Band](#-phase-5-always-reserve-the-lyric-band) | ✅ Complete | —   |
| 6   | [Manual UI Verification](#-phase-6-manual-ui-verification) | ✅ Complete | [preview-element-overlay.md](./preview-element-overlay.md) |
| 7   | [Tests](#-phase-7-tests) | ✅ Complete | —   |

* * *
## The Target Model
Every phase below assumes this model. It replaces the current song-wide uniform-line-height model.

Each `LineComponent` sizes itself to **its own** content. A custom `LayoutManager2` on `StaffPanel` positions the lines so that **midline-to-midline distance is uniform**, using the worst adjacent pair:

```
aboveMidline[N] = STAFF_HALF_SS + contentAboveStaffSs[N]
belowMidline[N] = STAFF_HALF_SS + contentBelowStaffSs[N] + lyricsBandHeight[N]

S = max over N in [0, count-2] of
        ( belowMidline[N] + MIN_INTER_LINE_GAP_SS + aboveMidline[N+1] )

midlineY[0] = aboveMidline[0]
midlineY[N] = midlineY[0] + N * S

component[N].y      = midlineY[N] - aboveMidline[N]
component[N].height = aboveMidline[N] + belowMidline[N]
```

Consequences that must hold, and that later phases depend on:

- `aboveMidline[0]` appears only in `midlineY[0]`, never in any pair. So content above the **first** line's staff translates the whole block downward and does **not** widen the uniform spacing. Content **below** the first line's staff does enter pair `(0,1)` and does widen it.
  
- Components never overlap: `S >= belowMidline[N] + gap + aboveMidline[N+1]` guarantees at least `MIN_INTER_LINE_GAP_SS` between the bottom of component N and the top of component N+1. Swing's default child clipping is therefore not a problem and must not be worked around.
  
- Lyrics **hug each line individually**: a line's verse baselines are measured from that line's own `contentBelowStaffSs`, never from a song-wide maximum.
  

* * *
## ✅ Phase 1: Per-Line Geometry API
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Opus 4.8, high effort — this defines the vocabulary every later phase consumes; getting the unit semantics wrong silently corrupts all downstream geometry.

Read `.agents/guides/unit-conversion.md` before starting. All values in this phase are staff spaces (`Ss` suffix mandatory). Never introduce a raw numeric literal — use named constants.
### Tasks
1. **Revert the uncommitted #591 changes.** The working tree currently contains changes that moved lyric anchoring and staff position from per-line values to song-wide values. They were built on the uniform-height model this plan removes, so they are a dead end. Run `git checkout -- src/main/java/songscribe/layout/LayoutResult.java src/main/java/songscribe/ui/component/score/LineComponent.java src/test/java/songscribe/layout/LayoutResultTest.java src/test/java/songscribe/ui/component/score/LineComponentTest.java` to restore all four files to `HEAD` (commit `257905f3`). Verify with `git status` that the working tree is clean before continuing.
  
2. **Create** `src/main/java/songscribe/layout/LineSpacing.java` — a `final` class with a private constructor holding the spacing constants, replacing the two competing margin constants that currently double up (`SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS = 1.25` baked into each line's height, plus `StaffPanel.LINE_MARGIN_BOTTOM_SS = 2.0` added as a strut, giving 26px gaps where 16px was intended):
  
  - `public static final double MIN_INTER_LINE_GAP_SS = 2.0;` — the single minimum gap between the lowest content of one line and the highest content of the next (2 staff spaces = 16px).
    
  - `public static final double LYRICS_ROW_MARGIN_SS = 1.0;` — moved verbatim from `SongLayoutMetricsBuilder.LYRICS_ROW_MARGIN_SS`; distance from a line's below-staff content to the top of its first verse row.
    
  - `public static final double MIN_LINE_HEIGHT_SS` — replaces `SongLayoutMetricsBuilder.MIN_LINE_HEIGHT_SS`, but **without** the old inter-line margin term, since the gap is now owned by the layout manager: `Staff.STAFF_HEIGHT_SS + Staff.MIN_ABOVE_STAFF_SS + Staff.MIN_BELOW_STAFF_SS`.
    
3. **Add** `staffToLyricsGapSs` **to** `LyricRenderMetrics` (`src/main/java/songscribe/layout/LyricRenderMetrics.java`). This record is already song-wide, font-derived, and content-independent, so it is the correct home for the lyric-band geometry that is being removed from `SongLayoutMetrics`. Add it as a fourth record component with Javadoc stating it equals `LineSpacing.LYRICS_ROW_MARGIN_SS + fontAscentSs(lyricsFont)` — the visual gap plus the font ascent, so a baseline placed at this distance puts the _text top_ one visual gap below the content. Update the single producer, `ScoreView.rebuildLyricRenderMetrics()` (`src/main/java/songscribe/ui/component/ScoreView.java:1354-1370`), to compute and pass it using `ScaleContext.fontAscentSs(lyricsFont).value()`. Use the existing `lyricBoxHeightSs()` accessor on this record as the **single** source of verse row height everywhere. The old `SongLayoutMetricsBuilder.LYRICS_HEIGHT_SS = 2.5` constant is a competing, hardcoded row height and must be deleted, not carried forward.
  
4. **Stop flooring the content extents, and stop baking the margin into line height**, in `src/main/java/songscribe/layout/stacking/VerticalStackingCalculator.java:159-211`. Today `aboveStaffSs` is floored at `Staff.MIN_ABOVE_STAFF_SS` (3.0) and the reservation is floored at `Staff.MIN_BELOW_STAFF_SS` (4.0); those floors plus `INTER_LINE_MARGIN_SS` are what make the current gaps too large. Change the calculator to set two **unfloored** true-content values:
  
  - `contentAboveStaffSs = max(0.0, -topExtentSs - Staff.STAFF_HALF_SS)`, still grown by the attribution adjustment already at lines 172-184.
    
  - `contentBelowStaffSs = max(0.0, context.getBotContentExtentSs() - Staff.STAFF_HALF_SS)` — this is the existing `belowContentSs` computation at lines 200-202, unchanged in formula.
    
  - `lineHeightSs` must no longer add `INTER_LINE_MARGIN_SS`. Delete the now-unused `INTER_LINE_MARGIN_SS` import and reference.
    
5. **Rename and add the per-line geometry accessors on**`src/main/java/songscribe/layout/LayoutResult.java`. Rename the stored field `aboveStaffSs` to `contentAboveStaffSs` and `belowContentSs` to `contentBelowStaffSs` (use `jet_brains_rename` so builder setters and all call sites update atomically). Then add these methods, all returning staff spaces in the line component's local coordinate frame:
  
  - `public double aboveMidlineSs()` → `Staff.STAFF_HALF_SS + contentAboveStaffSs`
    
  - `public double belowMidlineSs(LyricRenderMetrics m)` → `Staff.STAFF_HALF_SS + contentBelowStaffSs + lyricsBandHeightSs(m)`
    
  - `public double lyricsBandHeightSs(LyricRenderMetrics m)` → `0.0` when `verseCount == 0`, otherwise `m.staffToLyricsGapSs() + verseCount * m.lyricBoxHeightSs()`
    
  - `public double lineHeightSs(LyricRenderMetrics m)` → `aboveMidlineSs() + belowMidlineSs(m)`
    
  - `public double staffTopYSsInLine()` → `contentAboveStaffSs`
    
  - `public double staffBottomYSsInLine()` → `contentAboveStaffSs + Staff.STAFF_HEIGHT_SS`
    
  - `public double verseYSsInLine(int verse, LyricRenderMetrics m)` → `staffBottomYSsInLine() + contentBelowStaffSs + m.staffToLyricsGapSs() + (verse - 1) * m.lyricBoxHeightSs()`
    
  
  Delete the stored `lineHeightSs` field and the derived `getBelowStaffReservationSs()` — height is now computed from the midline extents above, and the reservation concept (a floored below-staff allowance that existed only to make uniform heights work) no longer has a role.
  
6. **Empty-line path**: update `src/main/java/songscribe/layout/LayoutEngine.java:273-283` so the `columns.isEmpty()` short-circuit sets `contentAboveStaffSs` and `contentBelowStaffSs` such that `lineHeightSs(m)` is at least `LineSpacing.MIN_LINE_HEIGHT_SS`. Split the shortfall evenly above and below the staff so an empty line's staff is vertically centred in its component. Remove the `SongLayoutMetricsBuilder` import.
  
7. Run `./scripts/compile.sh`. It will still fail on `SongLayoutMetrics` consumers — that is expected and is Phases 2-4's job. Confirm that the **only** remaining errors name `SongLayoutMetrics`, `SongLayoutMetricsBuilder`, `getAboveStaffSs`, `getBelowContentSs`, or `getBelowStaffReservationSs`, and record that error list in your final report so the next phase knows the exact surface to fix.
  
### Outcome
Done. Remaining `compile.sh` error surface is a **single** error, exactly as anticipated:

```
SongLayoutMetricsBuilder.java:69: cannot find symbol
    maxBelowStaffSs = Math.max(maxBelowStaffSs, layout.getBelowStaffReservationSs());
    symbol: method getBelowStaffReservationSs()
```

Phase 2 deletes this file, which resolves it.

#### Note on `contentBelowStaffSs`
Implemented exactly as the plan specifies — `max(0.0, context.getBotContentExtentSs() - Staff.STAFF_HALF_SS)`.

I first widened this to max over the `structural`/`system` tier bot extents, on the theory that a below-staff hairpin or text dynamic would otherwise be clipped now that this value sizes the component. **That was wrong and has been reverted:** only the note-attached layer ever places content below the staff — hairpins, dynamics, endings, tempo and annotations always stack above it. So `getBotContentExtentSs()` is complete. It is fed exclusively from `NoteAttachedStacker` and covers everything that stacks below a note: notehead+stem bottoms, downward ties, and the outermost below-staff articulation (accent/staccato, via `updateBelowStaffContentExtent`). It also tracks true ink rather than reserved footprints, which is the correct basis for lyric clearance.

#### Deviations from the plan as written
1. **`SongLayoutMetricsBuilder` was left otherwise intact** rather than having `LYRICS_HEIGHT_SS` / `LYRICS_ROW_MARGIN_SS` / `MIN_LINE_HEIGHT_SS` deleted out of it. Phase 2 deletes the whole file, so deleting members early would only have widened Phase 1's error surface past what task 7 predicted. `LayoutResult.lyricAreaBaseYSs()` was repointed at `LineSpacing.LYRICS_ROW_MARGIN_SS` so nothing in the surviving code depends on the doomed class's constants.
2. **The uncommitted #591 work was stashed, not `git checkout --`'d** — recoverable as stash entry `pre-phase-1 revert of #591 uniform-height work`.

#### Free wins
`jet_brains_rename` propagated the field renames into test sources as well, so several rename-only edits Phase 7 was scoped to make are already done (`ArticulationStackingTest`, `LineHeightTest`, `SongLayoutMetricsTest`, both `VerticalStackingCalculatorTest`s, `LayoutResultTest`, `LineComponentTest`).
  

* * *
## ✅ Phase 2: StaffLinesLayout Manager
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Opus 4.8, high effort — a hand-written Swing `LayoutManager2` with a non-obvious positioning formula and zoom interaction; no existing example in this codebase to mirror.

This project has **no existing custom** `LayoutManager`, so there is no in-repo convention to copy. Read `.agents/guides/zoom.md` before starting: this class sits at a view boundary and must apply the view zoom factor, and it is the one place in this phase where `Ss` becomes `Px`.
### Tasks
1. **Create** `src/main/java/songscribe/ui/component/score/StaffLinesLayout.java` implementing `java.awt.LayoutManager2`. It lays out the `LinePanel` children of `StaffPanel`. It needs access to each child's `LayoutResult` and to the song-wide `LyricRenderMetrics`; obtain both from the owning `StaffPanel`/`ScoreView` (pass the `StaffPanel` into the constructor). Implement the formula given in **The Target Model** section at the top of this plan — reproduce it exactly, including the rule that `aboveMidline[0]` never participates in the pairwise maximum.
  
2. **Unit handling.** Compute everything in staff spaces, then convert once per child at the boundary. Positions round to nearest, sizes round up: `y = (int) Math.round(ScaleContext.ssToPx(ySs) * viewScale.factor())` and `height = (int) Math.ceil(ScaleContext.ssToPx(heightSs) * viewScale.factor())`. Child width is the full container width. A `LineComponent`'s own preferred width already comes from `song.getLineWidthSs()` (full document width, per issue #578) — do not shrink children to their content extent.
  
3. **Implement the required** `LayoutManager2` **surface**: `layoutContainer`, `preferredLayoutSize` (height = `midlineY[last] + belowMidline[last]`, width = max child preferred width), `minimumLayoutSize` (delegate to preferred), `maximumLayoutSize` (delegate to preferred, matching the existing `StaffPanel.getMaximumSize()` behaviour), `addLayoutComponent(Component, Object)`, `addLayoutComponent(String, Component)`, `removeLayoutComponent`, `getLayoutAlignmentX` (0.0), `getLayoutAlignmentY` (0.0), and `invalidateLayout` (no cached state, so a no-op is correct — but state that explicitly in a comment rather than leaving it bare).
  
4. **Handle the degenerate cases explicitly**: zero children (return `new Dimension(0, 0)`); exactly one child (no pairs exist, so no uniform spacing is computed — position it at `y = 0`, i.e. its own `aboveMidline` above its midline); and any child whose `LayoutResult` is `null` because that line failed to fit (the issue-#449 `lineDoesNotFit` state on `LineComponent`). For a null `LayoutResult`, fall back to `Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HALF_SS` for `aboveMidline` and `Staff.MIN_BELOW_STAFF_SS + Staff.STAFF_HALF_SS` for `belowMidline` rather than throwing.
  
5. **Rewire** `src/main/java/songscribe/ui/component/score/StaffPanel.java`**:**
  
  - Constructor line 68: replace `setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))` with `setLayout(new StaffLinesLayout(this))`.
    
  - `rebuildLayout()` lines 114-142: delete the `Box.createVerticalStrut(lineMarginPx())` insertion (lines 135-137). Children are now positioned solely by the layout manager.
    
  - Delete `LINE_MARGIN_BOTTOM_SS` (line 52) and `lineMarginPx()` (lines 82-85). Note that `LINE_MARGIN_BOTTOM_SS` is referenced from `src/main/java/songscribe/ui/component/ComponentHierarchyNavigator.java:168`; Phase 4 fixes that call site, so leave it broken here and say so in your report.
    
  - Delete `getPreferredSize()` (lines 184-214) and `getMaximumSize()` — the layout manager now supplies both. Its `updateSongMetrics()` call must not simply disappear: see the next task.
    
  - Delete `updateSongMetrics()` (lines 224-240). Replace it with a package-private `ensureAllLineLayouts()` that keeps the two behaviours the old method had and that the layout manager still depends on: (a) call `scoreView.rebuildLyricRenderMetrics()` **first**, because `LineComponent.performLayout` reads `hyphenWidthSs`/`spaceWidthSs` from it while measuring columns; then (b) call the existing `getLayoutResults()` (lines 245-263) unchanged, which forces `ensureLayout()` on every line in order while threading `hasLeadingLyricContinuation` across line boundaries so a melisma running off one line reappears as a leading stub on the next. Drop only the `SongLayoutMetricsBuilder.build(...)` / `scoreView.setSongLayoutMetrics(...)` tail.
    
  - `StaffLinesLayout.preferredLayoutSize` and `layoutContainer` must both call `ensureAllLineLayouts()` before reading any `LayoutResult`. The old code re-ran this on every `getPreferredSize()` call deliberately (comment at `StaffPanel.java:193-196`: live drag moves elements without firing a mutation, so no dirty flag fires) — preserve that unconditional behaviour. It is cheap because each line's `ensureLayout()` is a no-op when that line is clean.
    
6. **Delete** `src/main/java/songscribe/layout/SongLayoutMetrics.java` **and** `src/main/java/songscribe/layout/SongLayoutMetricsBuilder.java`**.** Remove the `songLayoutMetrics` field (`ScoreView.java:214`), `getSongLayoutMetrics()` (lines 1323-1330) and `setSongLayoutMetrics()` (lines 1332-1334) from `src/main/java/songscribe/ui/component/ScoreView.java`. Leave the remaining compile errors in renderers and `LyricEditor` for Phase 3.
  
7. Run `./scripts/compile.sh` and record the remaining error list in your final report. Do not attempt to fix errors in the files Phase 3 and Phase 4 own.
  
### Outcome
Done as specified. `StaffLinesLayout` implements the Target Model formula verbatim; geometry is computed in `Ss` in a private `Geometry` record and converted once per child via `ViewScale.toViewPx(Ss)` — `roundedPx()` for `y`, `ceilPx()` for `height`, matching the zoom guide's position/size rule rather than hand-rolling `ssToPx * factor()`.

`compile.sh` FAILURE with 14 errors, all owned by later phases:

- **Phase 3** — `LineInvariants.java:40,76,97,212,485,522`; `LayoutResult.java:704` (`getLyricAnchor`); `LyricTextRenderer.java:32`; `LyricConnectorRenderer.java:35`; `LyricEditor.java:661`; `LineRenderer.java:138`.
- **Phase 4** — `LineComponent.java:43,559`; `ComponentHierarchyNavigator.java:168` (the expected `LINE_MARGIN_BOTTOM_SS` break).

#### Deviations / notes
1. **`updateSongMetrics()` → `ensureAllLineLayouts()`** as specified, plus a new package-private `StaffPanel.lyricRenderMetrics()` so the layout manager can read the song-wide metrics; it resolves through the first line component's `ScoreView` back-reference, which is set before the panel's own. `viewScale()` was widened from private to package-private for the same reason.
2. **`StaffPanel.getMaximumSize()` was deleted, not reimplemented.** `Container.getMinimumSize`/`getMaximumSize` route to a `LayoutManager2`, so `minimumLayoutSize`/`maximumLayoutSize` delegating to `preferredLayoutSize` preserves the old behaviour exactly.
3. **Insets are honoured** in both `layoutContainer` and `preferredLayoutSize`. `StaffPanel` has no border so they are zero today; including them costs nothing and avoids a latent bug if one is ever added.
4. `StaffLinesLayout` produced **no** compile errors of its own, but since the build aborted on the errors above it has not been fully verified by a clean compile. Phase 3 should confirm.

* * *
## ✅ Phase 3: Renderer + Consumer Migration
**Status:** Complete  
**BlockedBy:** 1, 2  
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical substitution of a per-line call for a song-wide one at a known, enumerated set of call sites.

Every call site below currently reads verse geometry from a song-wide `SongLayoutMetrics` object that no longer exists. Each must read it from the **current line's** `LayoutResult` instead, using the methods added in Phase 1: `verseYSsInLine(int verse, LyricRenderMetrics m)`, `staffTopYSsInLine()`, `staffBottomYSsInLine()`. The `LyricRenderMetrics` argument is available wherever `LineInvariants` is (`invariants.getLyricRenderMetrics()`) and on `ScoreView` (`getLyricRenderMetrics()`).
### Tasks
1. `src/main/java/songscribe/ui/renderer/LineInvariants.java`: delete the `songLayoutMetrics` field (line 76), its constructor parameter (line 97) and assignment (line 106), the `getSongLayoutMetrics()` getter (lines 206-214), the builder field (line 485), the `setSongLayoutMetrics` builder setter (lines 522-525), and the null-check in `build()` that throws when it is absent (lines 576-587). The `layoutResult` and `lyricRenderMetrics` fields already on this class supply everything the renderers now need. Update `src/main/java/songscribe/ui/component/score/LineRenderer.java:138` to drop the `.setSongLayoutMetrics(score.getSongLayoutMetrics())` call.
  
2. `src/main/java/songscribe/ui/renderer/LyricTextRenderer.java`: delete the `var metrics = invariants.getSongLayoutMetrics();` at line 73 and change line 95 to `var baselineYSs = invariants.getLayoutResult().verseYSsInLine(box.verseIndex(), lyricRenderMetrics);` (`lyricRenderMetrics` is already bound at line 74). Do not touch the transform-stripping block at lines 84-95 or its explanatory comment — that logic is about font rasterization matching `JTextField`, and is unrelated to this change. Update the class Javadoc at lines 39-41 to say the baseline comes from the line's own `LayoutResult`, not a song-wide object.
  
3. `src/main/java/songscribe/ui/renderer/LyricConnectorRenderer.java`: same substitution — delete line 99 and change line 108 to read the verse Y from `invariants.getLayoutResult().verseYSsInLine(connector.verseIndex(), invariants.getLyricRenderMetrics())`. Update the class Javadoc at lines 43-45.
  
4. `src/main/java/songscribe/layout/LayoutResult.java` — the four lyric hit-test/anchor methods lose their `SongLayoutMetrics` parameter and become self-referential, since a `LayoutResult` is already per-line:
  
  - `lyricAreaBaseYSs(SongLayoutMetrics)` (lines 649-653) → `lyricAreaBaseYSs(LyricRenderMetrics m)` returning `staffBottomYSsInLine() + contentBelowStaffSs + m.staffToLyricsGapSs()`. Delete the `#591` comment at lines 646-648 explaining the song-wide anchoring — it no longer applies.
    
  - `hitTestLyric(...)` (line 603) and `isYInLyricBounds(...)` (line 633): drop the `SongLayoutMetrics` parameter; they already receive `LyricRenderMetrics`.
    
  - `getLyricAnchor(StaffElement, SongLayoutMetrics)` (lines 668-676): change the second parameter to `LyricRenderMetrics` and compute the baseline as `verseYSsInLine(1, m)` at line 670. Update the Javadoc-only references to `SongLayoutMetrics#verseYSsInLine(int)` in `src/main/java/songscribe/layout/LyricBoxLayout.java:28` and `src/main/java/songscribe/layout/LyricConnectorLayout.java:34` to point at `LayoutResult#verseYSsInLine(int, LyricRenderMetrics)`.
    
5. `src/main/java/songscribe/ui/component/LyricEditor.java:661-665`: replace `var songLayoutMetrics = score.getSongLayoutMetrics();` with the line's `LyricRenderMetrics` (`score.getLyricRenderMetrics()`) and pass that to `layoutResult.getLyricAnchor(element, ...)`.
  
6. Run `./scripts/compile.sh`. The only remaining errors should be in `LineComponent.java` and `ComponentHierarchyNavigator.java`, which Phase 4 owns. Report them.
  

### Outcome
Done as specified. `LayoutResult.java`'s lyric hit-test/anchor methods (`lyricAreaBaseYSs`, `hitTestLyric`, `isYInLyricBounds`) had already lost their `SongLayoutMetrics` parameter in Phase 1 — only `getLyricAnchor(StaffElement, SongLayoutMetrics)` still took one, so that was the sole method changed, now `getLyricAnchor(StaffElement, LyricRenderMetrics)` computing the baseline via `verseYSsInLine(1, m)`.

`compile.sh` FAILURE with exactly 3 errors, all owned by Phase 4:
- `LineComponent.java:43` (`import songscribe.layout.SongLayoutMetrics`), `LineComponent.java:559` (`getSongLayoutMetrics()`)
- `ComponentHierarchyNavigator.java:168` (`StaffPanel.LINE_MARGIN_BOTTOM_SS`)

No deviations from the plan as written.

* * *
## ✅ Phase 4: LineComponent + Navigator Cleanup
**Status:** Complete  
**BlockedBy:** 2, 3  
**Recommended model/effort:** Sonnet 4.6, medium effort — four small, well-specified edits, but one is a latent caching bug that needs care.
### Tasks
1. `LineComponent.getPreferredSize()` (`src/main/java/songscribe/ui/component/score/LineComponent.java:542-568`): replace the height term. It currently reads `getScoreView().getSongLayoutMetrics().totalLineHeightSs()` (a song-wide uniform height). It must now use this line's own height: `layoutResult.lineHeightSs(getScoreView().getLyricRenderMetrics())`. Leave the width term alone — `song.getLineWidthSs()` is deliberate (issue #578: an element-less line must still draw its staff out to the margin).
  
2. `LineComponent.calculateMiddleLineYSs()` (lines 570-593): replace the body's return with `result.staffTopYSsInLine() + Staff.STAFF_HALF_SS`, i.e. this line's own above-staff extent. Delete the `#591` comment block at lines 587-592 that justifies using the song-wide value — the parent layout manager now guarantees cross-line consistency, so the per-line value is correct. Keep the existing `lineDoesNotFit` fallback at lines 574-577 unchanged.
  
3. **Fix the stale-cache bug in** `LineComponent.getMiddleLineYSs()` (lines 244-254). It currently recomputes only when the cached `middleLineYSs` field is exactly `0.0`, so any other stale value is returned indefinitely; it happens to work today only because `render()` (line 486) recomputes unconditionally on every paint. Under per-line sizing this value varies per line and is read before first paint, so replace the `== 0.0` sentinel with a proper invalidation: add a `middleLineYSsValid` boolean field, clear it everywhere `layoutDirty` is set (`setLine()` line 198, `invalidateLayout()` line 342, `setHasLeadingLyricContinuation()` line 400), and recompute when it is false.
  
4. `ComponentHierarchyNavigator.getActualLineMiddleYPx` **path** (`src/main/java/songscribe/ui/component/ComponentHierarchyNavigator.java:157-171`): the single-line fallback at lines 166-168 adds the deleted `StaffPanel.LINE_MARGIN_BOTTOM_SS`. With one line there is no inter-line spacing at all, so the row height is just `linePanel.getLineComponent().getHeight()`. Delete the margin term and the now-unused `StaffPanel` import if nothing else in the file uses it. The two-or-more-lines branch at line 161 (`getActualLineMiddleYPx(1) - getActualLineMiddleYPx(0)`) already measures real component positions and stays correct under the new layout — leave it alone.
  
5. Run `./scripts/compile.sh` and confirm **SUCCESS**. Then run `./scripts/test.sh unit` and record the failures. Expect failures in `SongLayoutMetricsTest`, `LineHeightTest`, `VerticalStackingCalculatorTest`, `LayoutResultTest`, `LineComponentTest`, `LyricTextRendererTest`, `LyricConnectorRendererTest`, `LineInvariantsTest`, `LineRendererTest`, `RenderContextTestHelper`, `ComponentHierarchyNavigatorTest`, and `TranslationTextPanelStaffPanelTest`. **Do not fix them** — Phase 7 owns tests, and it is gated behind manual verification. Report the full failure list.
  

### Outcome
Done as specified. `./scripts/compile.sh` reports **SUCCESS**. `./scripts/test.sh unit` fails at **test compilation**, not at runtime — expected, since the deleted `SongLayoutMetrics`/`SongLayoutMetricsBuilder` classes and the Phase 1 `LyricRenderMetrics` constructor-arity change (new `staffToLyricsGapSs` component) are still referenced from test sources that only Phase 7 is scoped to fix.

Files with compile errors (superset of the plan's predicted list — the extra files are cascading failures from shared test helpers/constructors touched by Phases 1-3, not new breakage from this phase):
`SongLayoutMetricsTest`, `LineHeightTest`, `VerticalStackingCalculatorTest`, `LayoutResultTest`, `LineComponentTest`, `LyricTextRendererTest`, `LyricConnectorRendererTest`, `LineInvariantsTest`, `LineRendererTest`, `RenderContextTestHelper`, `ComponentHierarchyNavigatorTest`, `TranslationTextPanelStaffPanelTest`, plus `InsertionSpacingCalculatorTest`, `LayoutEngineTest`, `LyricEditFitCalculatorTest`, `LyricLayoutBuilderGraceNoteTest`, `LyricLayoutBuilderTest`, `LyricRenderMetricsTest`, `TiedScriptStackingTest`, `LyricEditorTest`, `LyricEditorTestSupport` (these last nine fail only because they share a helper/constructor with one of the files already on the plan's list).

#### Deviations / notes
1. **Task 1 (`getPreferredSize`)**: the plan's replacement expression, `layoutResult.lineHeightSs(...)`, is unsafe when `layoutResult` is `null` and `lineDoesNotFit` is `true` (issue #449, first-ever layout attempt fails) — `layoutMissing()` returns `false` in that state, so execution reaches the replaced line with a null `layoutResult`. Added a null check with the same fallback extents `StaffLinesLayout` uses for a null child `LayoutResult` (`Staff.MIN_ABOVE_STAFF_SS + Staff.MIN_BELOW_STAFF_SS + Staff.STAFF_HEIGHT_SS`), rather than reproducing the plan's line verbatim and risking an NPE regression.
2. **Task 2 (`calculateMiddleLineYSs`)**: no `#591` comment block was present at the cited lines — Phase 1's rename pass had already left this method clean. Only the return expression was updated, from `result.getContentAboveStaffSs()` to `result.staffTopYSsInLine()`.
3. **Task 3 (stale-cache fix)**: added `middleLineYSsValid`, cleared in `setLine()`, `invalidateLayout()`, and `setHasLeadingLyricContinuation()` (only on actual change, matching the existing `layoutDirty` guard there), and set `true` in both `getMiddleLineYSs()` and `render()`'s unconditional recompute — the latter wasn't explicitly named in the plan but needed the same flag flip to stay consistent with the new invalidation contract.
4. **Task 4**: removed the now-unused `songscribe.ui.component.score.StaffPanel` import from `ComponentHierarchyNavigator.java`; the class is still referenced elsewhere in the file only via `mainPanel.getStaffPanel()`, an instance method, so the import had no other user.
  

* * *
## ✅ Phase 5: Always Reserve the Lyric Band
**Status:** Complete  
**BlockedBy:** 4  
**Recommended model/effort:** Sonnet 4.6, medium effort — a one-method behaviour change, but
it must be traced to every other reader of `verseCount` before it is safe.

Every line in a finished song ultimately carries lyrics, so a line with no verses yet must
still reserve one verse row. Otherwise the whole block re-spaces the moment the first lyric
on a line is typed, which reads as the score jumping under the cursor.

### Tasks
1. **Reserve at least one verse row** in `LayoutResult.lyricsBandHeightSs(LyricRenderMetrics)`
  (`src/main/java/songscribe/layout/LayoutResult.java:585-591`). It currently short-circuits
  to `0.0` when `verseCount == 0`. Delete that short-circuit and compute the row count as
  `Math.max(LineSpacing.MIN_RESERVED_VERSE_ROWS, verseCount)`. Add
  `public static final int MIN_RESERVED_VERSE_ROWS = 1;` to
  `src/main/java/songscribe/layout/LineSpacing.java` with Javadoc stating that a line reserves
  space for its first verse before that verse exists, so entering the first lyric on a line
  does not re-space the song. Update the method's Javadoc, which currently ends "Zero when the
  line has no lyrics" — that sentence becomes false.

2. **`verseCount` is per-line, not song-wide** — `LayoutEngine.java:363` sets it from
  `lyricResult.verseCount()`, documented at `LayoutResult.java:1166` as "the highest verse
  index present on **this line**". This task deliberately reserves only the *first* row, not
  the song's maximum verse count, so adding a second verse to a line still re-spaces. Do not
  widen it to a song-wide maximum — that would reintroduce the song-wide uniformity this plan
  removed.

3. **Audit the other readers of `verseCount`.** Run
  `jet_brains_find_referencing_symbols` on the `verseCount` field and on
  `lyricsBandHeightSs` in `src/main/java/songscribe/layout/LayoutResult.java`. Anything that
  branches on "this line has no lyrics" to decide whether to *draw* or *hit-test* lyrics must
  keep using `verseCount == 0` and must **not** be switched to the reserved row count — the
  reservation changes geometry only, never whether lyric content exists. Report every call
  site you inspected and which category you put it in.

4. **Confirm the reservation does not disturb baselines.** `verseYSsInLine(int, LyricRenderMetrics)`
  and `lyricAreaBaseYSs(LyricRenderMetrics)` derive from `staffBottomYSsInLine()`,
  `contentBelowStaffSs` and `staffToLyricsGapSs` — none of which read `verseCount`. Verify by
  reading them that a line's existing verse baselines are unchanged by this task, and say so
  in your report. If any baseline does shift, stop and report rather than compensating.

5. Run `./scripts/compile.sh` and confirm **SUCCESS**. Do not run the unit tests — they are
  still red from Phase 4 and Phase 7 owns fixing them.

### Outcome
Done as specified. `./scripts/compile.sh` reports **SUCCESS**. Unit tests were not run, per the
task instruction — they remain red from Phase 4 and are Phase 7's responsibility.

- Added `LineSpacing.MIN_RESERVED_VERSE_ROWS = 1` with Javadoc as specified.
- `LayoutResult.lyricsBandHeightSs(LyricRenderMetrics)` now computes
  `reservedVerseRows = Math.max(LineSpacing.MIN_RESERVED_VERSE_ROWS, verseCount)` and always
  returns `staffToLyricsGapSs() + reservedVerseRows * lyricBoxHeightSs()` — the `verseCount == 0`
  short-circuit is gone, and the Javadoc's "Zero when the line has no lyrics" sentence was
  replaced with a description of the reservation behaviour.

**Audit of other `verseCount` readers** (`jet_brains_find_referencing_symbols` on the field, the
`verseCount()` getter, and `lyricsBandHeightSs`, cross-checked with `rg -n "verseCount"
src/main`):

- `LayoutResult.isYInLyricBounds(LyricRenderMetrics, double)` (line 674-676) — branches on
  `verseCount == 0` to decide whether a point is in lyric bounds at all, and uses `verseCount` in
  its upper-bound math. This is a hit-test over *actual* lyric content, so it correctly keeps
  reading raw `verseCount`, unchanged.
- `LayoutResult.hitTestLyric(...)` — iterates `getLyricBoxes(element)`, which is populated only
  from real lyric boxes, not the reservation. No `verseCount` read at all; unaffected.
- `LayoutResult.lyricsBandHeightSs(LyricRenderMetrics)` — the geometry method itself, now
  reservation-aware as above.
- `LayoutResult.belowMidlineSs(LyricRenderMetrics)` — the sole caller of `lyricsBandHeightSs`;
  it's pure geometry (line component sizing), so inheriting the reservation is exactly the
  intended effect.
- `LayoutEngine.java:363` (`builder.setVerseCount(lyricResult.verseCount())`) — sets the raw
  per-line count from `LyricLayoutBuilder.Result`; unaffected, still the true content count.
- `verseCount()` getter (`LayoutResult.java:737`) — no production callers outside this file; only
  referenced from test files (`SongLayoutMetricsTest`, `LyricLayoutBuilderTest`), which are
  Phase 7's territory.

**Baseline confirmation (task 4):** `verseYSsInLine(int, LyricRenderMetrics)` and
`lyricAreaBaseYSs(LyricRenderMetrics)` (verified by reading both) derive only from
`staffBottomYSsInLine()`/`contentAboveStaffSs`/`contentBelowStaffSs` and
`LineSpacing.LYRICS_ROW_MARGIN_SS`/`m.staffToLyricsGapSs()` — neither reads `verseCount` or
`lyricsBandHeightSs()`. Existing verse baselines are unchanged by this task; only the reserved
below-midline footprint (and thus inter-line spacing) changes when a line has zero verses.

No deviations from the plan as written.

* * *
## ✅ Phase 6: Manual UI Verification
**Status:** Complete  
**BlockedBy:** 5, [preview-element-overlay.md](./preview-element-overlay.md)  
**Recommended model/effort:** No model — the user performs this.

The layout behaviour must be confirmed visually by the user before any test is written against it, because the expected numbers are a design judgement, not a derivation.

Run this in the **same session** as Phase 3 of [preview-element-overlay.md](./preview-element-overlay.md) — both are user-driven passes over the same score, and that plan fixes task 4 below.
### Tasks
1. Ask the user for permission, then run `./scripts/run.sh`. Never run it without asking.
  
2. Have the user open a multi-line song with lyrics and confirm the two symptoms from issue #591 are gone: (a) the space between lines is no longer excessive — it should be one `LineSpacing.MIN_INTER_LINE_GAP_SS` (16px) between the lowest content of a line and the highest content of the next, not the 26px that the old doubled margins produced; (b) on lines after the first, lyrics render where hit-testing responds, with no downward drift proportional to line index.
  
3. Have the user confirm the pairwise spacing rule visually: add a note with several ledger lines **above** the staff on the **first** line only. The whole block should shift down, and the gaps between lines should **not** grow. Then add the same note above the staff on the **second** line; now the gaps **should** grow uniformly. Then add a low note **below** the staff on the first line; the gaps **should** grow.
  
4. **Already failed — see [preview-element-overlay.md](./preview-element-overlay.md), which must land before this phase runs.** Removing the floors from the content extents (Phase 1 task 4) did break editing headroom: the preview element is clipped a few ledger lines above the staff, and the extreme staff positions are unreachable on a line with no content, because the floors were derived from exactly the staff-position range the preview accepts. The fix is the overlay in that plan — do **not** reintroduce headroom into the content extents, which would restore the excessive spacing this plan exists to remove.
  
5. Have the user confirm behaviour at non-100% zoom (both a zoom-in and a zoom-out step) and on a single-line song.
  
6. Record the user's verdict and any tuning they ask for in this phase's section before Phase 7 starts. If tuning is needed, apply it and re-verify before proceeding.
  
### Outcome
**Verified visually by the user (2026-07-21).** Both issue #591 symptoms are gone.

Two changes were made during verification rather than as planned tasks:

1. **The inter-line gap became a setting.** `MIN_INTER_LINE_GAP_SS` was lowered to 2.0 and demoted to a pure floor; `DEFAULT_INTER_LINE_GAP_SS = 4.0` is the gap actually used, clamped through the new `LineSpacing.interLineGapSs(double)`. This supersedes the plan's original single-constant design — see the revised Verification item 5.
2. **Lyric baselines were anchored on the painted staff position** (commit `4efee872`). `staffTopYSsInLine` / `staffBottomYSsInLine` / `lyricAreaBaseYSs` answered from the measured `contentAboveStaffSs` while `LineComponent` draws the staff at the floored `paintAboveMidlineSs`. On a line short enough to hit the floor the two frames diverged, drawing every lyric up to a floor's worth above its own staff — visible on the second line of `paste-test.musicxml` as lyrics rendered inside the staff. Caught during this phase, not by any test, which is why `testStaffYHelpersReportFlooredPositionOnShortLine` now exists.

* * *
## ✅ Phase 7: Tests
**Status:** Complete  
**BlockedBy:** 6  
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical test migration against behaviour the user has already signed off on.

This phase is what makes `./scripts/test.sh unit` compile again. Phase 4 of [preview-element-overlay.md](./preview-element-overlay.md) is blocked on it and must run after.

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first. No raw numeric literals in tests either — every expected value must be built from the named constants.
### Tasks
1. **Delete** `src/test/java/songscribe/layout/SongLayoutMetricsTest.java` in full — the class it tests no longer exists.
  
2. **Update the test files that construct the deleted record.** Each of these calls `new SongLayoutMetrics(...)` or `setSongLayoutMetrics(...)` and must drop it, since `LineInvariants` no longer carries that object: `src/test/java/songscribe/ui/renderer/LineInvariantsTest.java:63,196,221` (and delete the `testBuildThrowsWhenSongLayoutMetricsNull` test at `:206`), `src/test/java/songscribe/ui/renderer/RenderContextTestHelper.java:64`, `src/test/java/songscribe/ui/renderer/LineRendererTest.java:100,657`, `src/test/java/songscribe/ui/renderer/LyricTextRendererTest.java:57-59` and its eight `.setSongLayoutMetrics(...)` call sites, `src/test/java/songscribe/ui/renderer/LyricConnectorRendererTest.java:57-58,72,82`, `src/test/java/songscribe/layout/LayoutResultTest.java:977-981` (the `testSongMetrics()` helper), `src/test/java/songscribe/ui/component/score/LineComponentTest.java:197,262`, and `src/test/java/songscribe/ui/component/score/TranslationTextPanelStaffPanelTest.java:862,885` (the ordering test there must now assert `rebuildLyricRenderMetrics()` is called before `getLayoutResults()`, since `setSongLayoutMetrics` is gone but the ordering guarantee it was checking still matters).
  
3. **Update the constant references** in `src/test/java/songscribe/layout/LineHeightTest.java:89,100,105,145,187` and `src/test/java/songscribe/layout/VerticalStackingCalculatorTest.java:199` from `SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS` / `MIN_LINE_HEIGHT_SS` to the new `LineSpacing.MIN_LINE_HEIGHT_SS`, and drop the inter-line-margin term from the expected values — line height no longer includes it. Update `src/test/java/songscribe/ui/component/ComponentHierarchyNavigatorTest.java:272-274` to expect a row height with no `LINE_MARGIN_BOTTOM_SS` term.
  
4. **Write a new** `src/test/java/songscribe/ui/component/score/StaffLinesLayoutTest.java` covering the positioning formula directly, with hand-built `LayoutResult`s so no real song is needed:
  
  - Uniform spacing across three lines with equal extents.
    
  - Content above the **first** line translates the block down but leaves the uniform spacing unchanged (the asymmetry that defines this algorithm).
    
  - Content below the first line **does** widen the uniform spacing.
    
  - Content above a middle line widens the uniform spacing, and all gaps stay equal.
    
  - The spacing is driven by the worst adjacent pair, not by the global maximum: build a case where the deepest-below line and the tallest-above line are **not** adjacent, and assert the spacing is smaller than `maxBelow + gap + maxAbove`.
    
  - Adjacent components never overlap, and the gap between them is never less than `LineSpacing.MIN_INTER_LINE_GAP_SS`.
    
  - Degenerate cases: zero children, one child, and a child with a null `LayoutResult`.
    
5. **Add per-line lyric anchoring tests** to `LayoutResultTest`: assert that `verseYSsInLine` and `lyricAreaBaseYSs` are driven by that result's own `contentBelowStaffSs`, and that two `LayoutResult`s with different `contentBelowStaffSs` yield different verse baselines — this is the "lyrics hug each line individually" decision, and it is the regression guard for the second half of issue #591.
  
6. Run `./scripts/compile.sh`, then `./scripts/test.sh unit`. Both must report SUCCESS / green before this phase is done.
  
### Outcome
Done. `./scripts/compile.sh` SUCCESS, `./scripts/test.sh unit` green at 5467 passed / 1 skipped.

Tasks 1-3 landed earlier, in commit `e53f0009` ("test: port the test tree to the per-line height model"). Tasks 4-5 were completed on 2026-07-21:

- **Task 4** — `src/test/java/songscribe/ui/component/score/StaffLinesLayoutTest.java`, 9 tests over the positioning formula with hand-built `LayoutResult`s. Covers all seven listed cases. Verified the two defining tests can actually fail by temporarily replacing the pairwise rule with a global maximum: `testContentAboveFirstLineTranslatesBlockWithoutWideningSpacing` and `testSpacingFollowsWorstAdjacentPairNotGlobalMaximum` both failed under that mutation and pass against the real implementation.
- **Task 5** — `testVerseBaselinesFollowEachLinesOwnBelowStaffContent` added to `LayoutResultTest`; `testLyricAreaBaseYSsFollowsAboveStaffAndBelowContent` already covered the `lyricAreaBaseYSs` half.

#### Deviations / notes
1. **Gap assertions carry a one-pixel tolerance** (`ROUNDING_TOLERANCE_PX`). `StaffLinesLayout` rounds each child's Y independently, so a gap measured between two of them lands within a pixel of the exact staff-space spacing. Uniformity is exact in `Ss` and approximate in px; asserting exact pixel equality would assert a rounding accident.
2. **`LineComponent.setLineDoesNotFit(boolean)` was added** (package-private) so the null-`LayoutResult` case is reachable from a test. `layoutMissing()` treats a null result as "lay this out again" unless the line is in the issue-#449 state, so without the setter `ensureAllLineLayouts()` simply recomputed the result the test had just cleared.
3. **`StaffLinesLayout` already had partial coverage** in `TranslationTextPanelStaffPanelTest` (`testThreeLinesSpanTwoUniformMidlineGaps`, `testSingleLineAddsNoInterLineGap`, `testLinesAreSizedIndividuallyAndSpacedUniformly`). Those were left alone; the new file covers the cases that fixture's monotonically-growing extents cannot express.

* * *
## Verification (whole plan)
1. `./scripts/compile.sh` reports SUCCESS.
  
2. `./scripts/test.sh unit` is green.
  
3. Issue #591's two reported symptoms are gone, confirmed by the user in Phase 6: inter-line space is no longer excessive, and lyrics on lines after the first render where hit-testing responds.
  
4. `rg -n "SongLayoutMetrics" src/` returns no hits.
  
5. ~~There is exactly one inter-line spacing constant in the codebase (`LineSpacing.MIN_INTER_LINE_GAP_SS`), not the two that previously stacked to 26px.~~
  **Revised during Phase 6.** The gap became a setting, so there are now two constants — but they no longer *stack*, which was the actual defect. `LineSpacing.DEFAULT_INTER_LINE_GAP_SS` is the gap applied when the song specifies none; `MIN_INTER_LINE_GAP_SS` is only a floor, enforced by `LineSpacing.interLineGapSs(double)`. The check is therefore: exactly one value reaches `StaffLinesLayout`'s pair computation, and no line's height carries an inter-line term of its own.
