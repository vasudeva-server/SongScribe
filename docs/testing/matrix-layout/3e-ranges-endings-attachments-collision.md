### 3E. ranges, endings, attachments, collision — `AttachmentLayout`, `CollisionDetector`, `Ending`, `LineEndingSupport`, `RangeLayout`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| AttachmentLayout | `getVerticalOrder()` switch maps Type→VerticalOrder | unit | — | missing | **dead code (zero refs)** — resolve by deletion, not test (see observations) | ✅ |
| AttachmentLayout | `isAboveStaff`/`containsPoint` delegations | none | — | none | trivial delegation | — |
| AttachmentLayout | `getDataAs` null-safe cast | unit | — | missing | dead code; delete | ✅ |
| CollisionDetector | `calculateNoteExtent` accumulates min/max Y over notes/attachments/articulations/ranges | unit | — | missing | **dead code (zero refs)** — resolve by deletion | ✅ |
| CollisionDetector | `COLLISION_PADDING_SS` constant | none | — | none | numeric constant | — |
| Ending | `getLabel()` "1."/"2." | unit | — | missing | two-case test | ✅ |
| Ending | `getContentHeightSs()` = `VOLTA_TICK_HEIGHT_SS` | none | `StructuralTierStackingTest` pins value indirectly | none | constant return | — |
| Ending | `getSpanWidthSs()` = `max(NOTE_HEAD_WIDTH_SS, endX-anchorX+NOTE_HEAD_WIDTH_SS)` | unit | — | missing | zero-span + positive span | ✅ |
| Ending | `findRepeatSplitElement()` scans for REPEAT_RIGHT/REPEAT_LEFT_RIGHT | unit | indirect via invalidation tests | missing | direct: no-split / each split type / invalid indices | ✅ |
| Ending | `computeBracketRanges()` start-adjust, split detection, two-bracket geometry | unit | — | missing | **high-risk**: no-split, split→two brackets, start-adjust from barline, end-extend, closing-stroke per end type | ✅ |
| Ending | `computeCollisionRegions()` bar/tick(s)/label decomposition | unit | — | missing | region count (3 vs 4 by `hasClosingStroke`), x-offsets, label inset | ✅ |
| Ending | `labelBoundsSs(int)` cached glyph bounds | none | — | none | static lookup | — |
| Ending | `isInvalidatedByDeletion()` split + all-content cases | unit | `EndingInvalidationTest.IsInvalidatedByDeletion` (6) | adequate | keep | — |
| Ending | `isInvalidatedByReplacement()` / `checkReplacement()` all outcomes | unit | `EndingInvalidationTest.IsInvalidatedByReplacement` (15), `CheckReplacement` | adequate | keep | — |
| Ending | `isInvalidatedByInsertion()` guards + interior/split logic | unit | `EndingInvalidationTest.IsInvalidatedByInsertion` (5) | inadequate | missing split-boundary exemption (`insertedIndex==splitIndex`→false) and `splitEl==null` interior branch | ✅ |
| Ending | stacking above staff/hairpins | unit | `StructuralTierStackingTest.EndingStacking` (4) | adequate | keep (directional `isLessThan(0)` correct for the claim) | — |
| Ending | `setYPositionSs`/`getYPositionSs` applied in stacking | unit | `ManualOffsetStackingTest.EndingOffsets.testEndingYPositionApplied` | adequate | keep | — |
| Ending | base `isInvalidatedBy` anchor/end deleted | unit | `RangeElementInvalidationTest` (parametrized incl. Ending) | adequate | keep | — |
| Ending | Line-mutation wiring removes invalidated Ending | unit | `LineMutationTest.EndingInvalidationConditions` (10+) | adequate | keep | — |
| Ending | confirmation UI wiring (abort/proceed/dual change) | unit (integration) | `EndingConfirmsTest` (9, mocked dialogs) | adequate | keep | — |
| LineEndingSupport | `findEndings()` extracts Ending range elements | unit | indirect only | missing | 0/1/2 endings, verify content | ⬜ |
| LineEndingSupport | `findEndingAt(List,int)` span inclusion [start,end] | unit | — | missing | before/at-start/inside/at-end/after/empty | ⬜ |
| LineEndingSupport | `findEndingAt(Line,int)` overload | none | — | none | trivial delegation | — |
| LineEndingSupport | `isInsideAnyEnding` null-safe | unit | — | missing | positive + negative | ⬜ |
| LineEndingSupport | `isStartOfAnyEnding` anchor equality | unit | — | missing | start / inside-not-start / empty | ⬜ |
| LineEndingSupport | `isEndOfAnyEnding` end equality | unit | — | missing | end / inside-not-end / empty | ⬜ |
| LineEndingSupport | `findEndingReplacementEffect()` first non-None effect | unit | `EndingConfirmsTest` via `SelectionCoordinator.applyActionToSelection` | inadequate | indirect only (reclassified from wrong-level); add direct 0/1/2-affected test | ⬜ |
| RangeLayout | `getVerticalOrder()` ENDINGS / RANGE_ABOVE / RANGE_BELOW | unit | — | missing | **dead code (zero refs)** — resolve by deletion | ✅ |
| RangeLayout | `getElementCount()` = end-start+1 | unit | — | missing | dead code | ✅ |
| RangeLayout | `containsElement(int)` range-inclusive | unit | — | missing | dead code | ✅ |
| RangeLayout | `containsPoint`/`getDataAs` | none/unit | — | none/missing | dead code | ✅ |

**3E notes (quality concerns):** The most significant in-scope gap is **`Ending.computeBracketRanges()`** — the most complex method here (start-leftward-adjust, no-split single bracket, split→two brackets, per-end-type closing-stroke) — with zero direct coverage; bugs produce wrong visual geometry, not crashes. Its companion `computeCollisionRegions()` (3 vs 4 sub-regions) is also untested. `isInvalidatedByInsertion` has two survivable-mutant spots: the split-boundary exemption and the `splitEl==null` interior branch. **`LineEndingSupport`** is used by 8 production subsystems (MIDI, ABC export, IO, rendering, selection, vertical adjustment) but has no unit tests; its `findEndingAt` boundary comparators (`>=`/`<=`) are exactly where off-by-one hides. Out-of-scope production observation (**verified**): `AttachmentLayout`, `CollisionDetector`, `RangeLayout` have **zero references anywhere in `src/main` or `src/test`** (confirmed by grep + Serena) — dead scaffolding superseded by `LayoutResult.DecorationLayout`; resolve by deletion in remediation rather than writing the "missing" tests. Redundant: `StructuralTierStackingTest.EndingStacking.testEndingRangeElementProducesDecorationLayout` duplicates `testEndingPositionedAboveStaff`.

