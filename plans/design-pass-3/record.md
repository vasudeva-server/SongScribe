# Design Pass — `engraving`

Run by `/design-pass 3` — register row `3`, *Staff geometry*.

**Status:** ⏳ not started · 🔄 in progress · ✅ complete

| Step                     | Status | Gate | Plan | Notes |
|--------------------------|---|---|---|---|
| 1 Inventory              | ✅ | with 2 | — | No tests, no `@Nullable`, no guards. Read inline; fan-in delegated. |
| 2 Class design           | ✅ | ✅ | — | `findings-1-2.md` states the settled class design. |
| 3 Unrepresentable states | ✅ | ✅ | [restructure.md](restructure.md) | Steps 3–5 execute from one plan; all 13 phases complete. |
| 4 Extraction             | ✅ | ✅ | [restructure.md](restructure.md) | |
| 5 Contracts              | ✅ | ✅ | [restructure.md](restructure.md) | |
| 6 Test triage            | ✅ | ✅ | — | 16 keep, 3 discard, 1 rewrite, 1 moved package, 0 added. F1–F4 fixed. |
| 7 Test-only surface      | ✅ | ✅ | — | `Span.getSpanWidthSs` and `getEndElementWidthSs` deleted with four uncalled implementations. |
| 8a Suite gate            | ✅ | n/a | — | 577 passing, run by the user. |
| 8b Visual gate           | ✅ | n/a | [visual-gate.md](visual-gate.md) | 14 checks derived; all passed. |
| 9 Diagrams               | ✅ | ✅ | — | No diagram in the target. Both `docs/layout-geometry.md` diagrams kept; its false clamp clause corrected. |
| 10 Coverage              | ✅ | n/a | — | No unexecuted region: 150/150 instructions, 17/17 branches. Nothing amended, no test written. |
| 11 Mutation              | ✅ | n/a | — | Not run — no specific case worth investigating. |
| 12 Adversarial review    | ⏳ | n/a | — |  |
| 13 Close out             | ⏳ | ⏳ | — | |

*Gate* is the state of the step's findings document: ⏳ not written · 📖 open for
review · ✅ resolved. A step whose *Gate* is not ✅ has made no code change, so a
resume that finds one 📖 reads the marked-up document rather than the tree.

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

## Baseline

| | Before |
|---|---|
| Commit | `762d5bf6` |
| Production | `src/main/java/songscribe/engraving/` — 6 files, 451 lines |
| Tests | none — `src/test/java/songscribe/engraving/` does not exist |
| Passing tests | 0 |

## Findings claimed

Taken from the register's *Carry-forward findings* at step 0 and deleted there.

1. **From pass 2.** `StaffHeaderMetrics.accidentalInkBboxSs` is named for a
   bounding box and returns only its width. The Javadoc says why the ink extent
   rather than the advance width is the right measure; the name is what is left
   wrong.
2. **From pass 2.** `BarStroke` is new and owns the width of a thin bar, a thick
   bar and repeat dots; `SMuFLConstants` lost its four notehead stem-anchor
   constants and its static block that computed them; `LineThickness` lost
   `REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS` and `REPEAT_RIGHT_AFTER_THICK_X_SS`.
   Open: whether `BarStroke` belongs in `engraving` beside the `LineThickness`
   that defines the widths it carries, or in `dom` beside the `BarAppearance`
   that sequences it.

## Coverage

`./scripts/coverage.sh unit LedgerLineTest StaffGeometryRegressionTest
BarAppearanceTest` — 109 passing, and `songscribe.engraving` has no unexecuted
region: 150/150 instructions, 17/17 branches, 15/15 methods.

`BarAppearanceTest` belongs to the run because `BarStroke.widthSs` is reached
only through it — step 6 established that and the run confirms it. Without it,
`widthSs` and its three branches are the package's only unexecuted region.

`EngravingConstants`, `StemMetrics` and `StaffHeaderMetrics` carry no counters at
all. Each holds only compile-time constants, which javac folds into their call
sites, so there is no bytecode to execute; JaCoCo filters their private empty
constructors. This is not a gap, and no test would close it.

## Findings raised

Anything surfaced that was not this target's to fix, and anything a step turned
up that belongs to a later step's axis.

The `layout` re-aliases and the natural-kerning placement moved into this pass;
both are stated in `findings-1-2.md`.

Step 6 raised four production findings, stated in full in `findings-6.md`. All four
were fixed in this pass; none carries to the register:

- **F1.** `BeamMetrics.beamStackHeightSs`'s Javadoc names the outer edge of the
  last beam; the body measures to its inner edge.
- **F2.** `HitRegionBuilder.beamGroupRectSs:748-749` re-derives the beam stack
  extent `BeamMetrics.beamStackHeightSs` owns, generalised to a thickened stack.
- **F3.** The ledger-line threshold is stated twice — a bare `5` in
  `RenderingUtils.forEachLedgerLineYSs` and `NoteGeometry
  .OUTERMOST_STAFF_LINE_POSITION`, the latter misnamed — and the ledger-line
  sequence sits in a renderer rather than on `LedgerLine`.
- **F4.** `StaffPosition.POSITIONS_PAST_OUTERMOST_LEDGER_LINE`,
  `STAFF_LINES_ABOVE` and `STAFF_LINES_BELOW` are misnamed and their Javadoc is
  false; the values are correct.
- **F5.** `BarStroke.widthSs`'s Javadoc claimed computing on call keeps a font
  load out of `ElementType`'s class initializer. `ElementType`'s own static block
  reaches `SMuFLMetadata` through `BarAppearance.widthSs`, so it does not, and
  what loads is a classpath JSON resource rather than a font. Replaced with the
  method's contract; the on-call form stays, costing a static read and an array
  index.
