## 3. `layout` (audited 2026-05-21)

Audited all 37 production classes (excl. 2 `package-info`) via six parallel production-first sub-audits: **orchestration & accumulation**; **horizontal spacing & columns**; **geometry primitives & metrics**; **lyric layout**; **ranges/endings/collision**; **stacking subsystem**. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e. One verdict reclassified from a sub-audit's `wrong-level` (`LineEndingSupport.findEndingReplacementEffect`): the vocabulary reserves `wrong-level` for unit↔e2e mismatches; a unit behavior covered only indirectly is `inadequate`.

- [3A. orchestration & accumulation — `LayoutEngine`, `LayoutAccumulator`, `LayoutResult`, `LayoutLayer`, `SectionLayout`, `PageModel`](3a-orchestration-accumulation.md)
- [3B. horizontal spacing & columns — `ElementColumn`, `ElementColumnBuilder`, `HorizontalSpacingCalculator`, `InsertionSpacingCalculator`, `LineJustificationCalculator`](3b-horizontal-spacing-columns.md)
- [3C. geometry primitives & metrics — `ElementBoundsSs`, `InsetsSs`, `Size`, `Margin`, `MarginReference`, `LineThickness`, `NoteGeometry`, `StaffExtents`, `VerticalOrder`, `SongLayoutMetrics`, `SongLayoutMetricsBuilder`](3c-geometry-primitives-metrics.md)
- [3D. lyric layout — `LyricBoxLayout`, `LyricConnectorLayout`, `LyricLayoutBuilder`, `LyricRenderMetrics`](3d-lyric-layout.md)
- [3E. ranges, endings, attachments, collision — `AttachmentLayout`, `CollisionDetector`, `Ending`, `LineEndingSupport`, `RangeLayout`](3e-ranges-endings-attachments-collision.md)
- [3F. stacking subsystem — `NoteAttachedStacker`, `StackingContext`, `StackingUtils`, `StructuralStacker`, `SystemStacker`, `VerticalStackingCalculator`](3f-stacking-subsystem.md)

### layout — production observations (out of test-audit scope)

Filed as a single tracked GitHub issue ([#408](https://github.com/vasudeva-server/SongScribe/issues/408)) — these are real code observations, not test gaps, so the disposable matrix isn't their only home.

1. **⚠️ Dead code — `AttachmentLayout`, `CollisionDetector`, `RangeLayout`.** Verified zero references anywhere in `src/main` or `src/test` (grep for the bareword + Serena reference search). Appears to be scaffolding from an earlier layout architecture superseded by `LayoutResult.DecorationLayout`. Resolve by deletion in remediation rather than writing the ~12 "missing" tests their behaviors would otherwise warrant.
2. **`ElementBoundsSs.formatCssSpacing` — wrong unit suffixes (confirmed).** The multi-value branches emit `t + "px " + r + "ss"` (and 3-/4-value analogues), so all but the last token are labelled `px` even though the values are staff-spaces and the method's own javadoc shows all-`ss` output (`"4ss 8ss"`). Cosmetic (these CSS strings are inspection/debug output) but incorrect.
3. **`LyricLayoutBuilder` — stale comment.** Line 68: `// Extends 0.25 ss past the column right edge` while `STOP_MELISMA_OVERSHOOT_SS = 0.5`. The `{@value}` javadoc at lines 44/52 is correct; only the inline comment (and the echoing test comment) is stale.
4. **`HorizontalSpacingCalculator.needsAccidentalPush` — unused parameters / misleading contract.** Ignores `prevColumn` and `currXSs` and returns true whenever the current element has any accidental; the real clearance check lives in the caller. The signature implies a pre-check it doesn't perform. Likely intentional but a code smell — review.

### layout — summary

Audited all 37 production classes (excl. 2 `package-info`). Dominant patterns to drive remediation:

1. **Pure geometry/conversion/stacking math is the biggest blind spot — and it is the riskiest math in the app.** `LayoutEngine`'s beam/stem/tie engines, `ElementBoundsSs`' box model, `NoteGeometry`'s accidental widths, `StaffExtents.spToSs`/`ssToSp`, `StackingUtils.anchorCeilingSs`, `NoteAttachedStacker.evaluateBezierYSs`, `LineJustificationCalculator`'s compression math, and `LayoutResult`'s hit-testing family are exercised only as collaborators (or not at all) and asserted directly almost nowhere. These are cheap, high-value unit tests.
2. **"Weak-but-green" assertions are pervasive and systemic** — far more than in `dom`/`io`. The entire stacking-test family asserts `ySs<0`/`>0`; `LineHeightTest` and several builder/metrics tests assert `>=`/`>`/`isNotEqualTo`/`isLessThan` where exact values are statically computable; `isNotNull`/fixture-only tests stand in for behavioral assertions. A position/sign/constant mutation survives most of them.
3. **Self-referential oracles** — `HorizontalSpacingCalculatorTest` (entirely tautological), `PageModelTest` (contentArea + defaultLineWidthSs), `SongLayoutMetricsTest.verseBaselineY`, `LyricRenderMetricsTest.lyricBoxWidth`. Each compares production output to the same formula and cannot fail.
4. **Untested complex logic / branch & error paths** — `LineJustificationCalculator` (zero tests), `Ending.computeBracketRanges`/`computeCollisionRegions`, `LyricLayoutBuilder` dangling-hyphen + REST-extend (STOP/CONTINUE) branches, `isInvalidatedByInsertion` split-boundary, `LineEndingSupport` (8 production callers, no unit tests), `LayoutResult` insertion/lookup family.
5. **Dead code surfaced** — `AttachmentLayout`, `CollisionDetector`, `RangeLayout` (delete, don't test). Plus three minor code observations (CSS suffixes, stale comment, unused params).
6. **Misfiled-but-relevant:** many `dom`-class tests live under `layout/` (`TieTest`, `TupletTest`, `KeySignatureTest`, `RangeElementInvalidationTest`, `AnnotationAttachmentTest`, etc., already audited in Session 1) — relocate during the rewrite, not re-test.
