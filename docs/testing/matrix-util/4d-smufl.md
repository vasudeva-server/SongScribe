### 4D. `smufl`

Audited by reading each production class body symbol-by-symbol with serena `jet_brains_find_symbol` (include_body=true), enumerating all testable behaviors, then searching `src/test/java/songscribe/smufl/` and cross-package test files via grep for any existing coverage of each behavior.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `BBox` | `width()` = right − left | unit | none found | missing | Add `BBoxTest.testWidthIsRightMinusLeft` |
| `BBox` | `height()` = bottom − top | unit | none found | missing | Add `BBoxTest.testHeightIsBottomMinusTop` |
| `BBox` | `translateX(dx)` shifts left and right by dx, leaves top/bottom unchanged | unit | none found | missing | Add `BBoxTest.testTranslateXShiftsHorizontallyOnly` |
| `BBox` | `union` returns smallest enclosing box (min left/top, max right/bottom) | unit | none found | missing | Add `BBoxTest.testUnionReturnsSmallestEnclosingBox` |
| `BBox` | `fromSMuFL` flips Y-up to Y-down (top=−neY, bottom=−swY) | unit | none found | missing | Add `BBoxTest.testFromSmuflFlipsYConvention` |
| `BBox` | record component accessors (left, top, right, bottom) | none | — | adequate (none warranted) | — |
| `GlyphAnchors` | `requireStemUpSE` returns anchor when present | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemUpSEReturnsAnchorWhenPresent` |
| `GlyphAnchors` | `requireStemUpSE` throws when stemUpSE is null | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemUpSEThrowsWhenNull` |
| `GlyphAnchors` | `requireStemDownNW` returns anchor when present | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemDownNWReturnsAnchorWhenPresent` |
| `GlyphAnchors` | `requireStemDownNW` throws when stemDownNW is null | unit | none found | missing | Add `GlyphAnchorsTest.testRequireStemDownNWThrowsWhenNull` |
| `GlyphAnchors/Anchor` | `fromSMuFL` flips Y (y becomes −y) | unit | none found | missing | Add test for `Anchor.fromSMuFL` Y-flip |
| `GlyphAnchors` | record component accessors (stemUpSE, stemDownNW, cutOutNW, cutOutSE — all `@Nullable`) | none | — | adequate (none warranted) | — |
| `SMuFLData` | pure data record, no logic | none | — | adequate (none warranted) | — |
| `SMuFLGlyph` | `smuflName()` returns canonical SMuFL name string | unit | none found | missing | Add `SMuFLGlyphTest.testSmuflNameMatchesSpec` (spot-check a few constants) |
| `SMuFLGlyph` | `codepoint()` returns correct Unicode codepoint | unit | none found | missing | Add `SMuFLGlyphTest.testCodepointMatchesSpec` (spot-check a few constants) |
| `SMuFLGlyph` | `asString()` returns single-character string of codepoint | unit | none found | missing | Add `SMuFLGlyphTest.testAsStringIsSingleCharOfCodepoint` |
| `SMuFLMetadata` | `getBBox` returns populated BBox for a known glyph | unit | indirect via `KeySignatureTest`, `DynamicAttachmentTest`, `ArticulationStackingTest` | inadequate (self-referential: tests use `requireBBox` as their own oracle) | Add direct assertion with concrete numeric value |
| `SMuFLMetadata` | `getBBox` returns null for a glyph absent from the metadata | unit | none found | missing | Add `SMuFLMetadataTest.testGetBBoxReturnsNullForUnknownGlyph` |
| `SMuFLMetadata` | `requireBBox` throws when glyph absent from metadata | unit | none found | missing | Add `SMuFLMetadataTest.testRequireBBoxThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `noteHeadWidthSs` returns correct notehead width in staff spaces | unit | none found | missing | Add `SMuFLMetadataTest.testNoteHeadWidthSsIsPositiveAndPlausible` with concrete bounds |
| `SMuFLMetadata` | `noteHeadHeightSs` returns correct notehead height in staff spaces | unit | none found | missing | Add `SMuFLMetadataTest.testNoteHeadHeightSsIsPositiveAndPlausible` with concrete bounds |
| `SMuFLMetadata` | `getAnchors` returns populated `GlyphAnchors` for a known glyph | unit | none found | missing | Add `SMuFLMetadataTest.testGetAnchorsReturnsAnchorsForKnownGlyph` |
| `SMuFLMetadata` | `getAnchors` returns null for a glyph absent from anchors data | unit | none found | missing | Add `SMuFLMetadataTest.testGetAnchorsReturnsNullForGlyphWithNoAnchors` |
| `SMuFLMetadata` | `requireAnchors` throws when glyph absent from anchors | unit | none found | missing | Add `SMuFLMetadataTest.testRequireAnchorsThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `getAdvanceWidth` returns width for a known glyph | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthReturnsValueForKnownGlyph` |
| `SMuFLMetadata` | `getAdvanceWidth` returns null for a glyph absent from advance widths | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthReturnsNullForAbsentGlyph` |
| `SMuFLMetadata` | `getAdvanceWidthOrZero` returns 0.0 when glyph absent | unit | none found | missing | Add `SMuFLMetadataTest.testGetAdvanceWidthOrZeroReturnsFallbackForAbsentGlyph` |
| `SMuFLMetadata` | `requireAdvanceWidth` throws when glyph absent | unit | none found | missing | Add `SMuFLMetadataTest.testRequireAdvanceWidthThrowsForAbsentGlyph` |
| `SMuFLMetadata` | `getEngravingDefaults` returns SMuFLData with plausible non-zero values | unit | none found | missing | Add `SMuFLMetadataTest.testEngravingDefaultsAreNonZero` |
| `SMuFLMetadata` | `Holder.load()` loads from classpath resource without exception (singleton initializes) | unit | implied by every test that touches `SMuFLMetadata.*` | adequate (singleton load tested implicitly) | — |
| `Engraving` | `G_CLEF_WIDTH_SS` is derived from SMuFL advance width, not hardcoded | unit | `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth` | inadequate (self-referential: expected value is `SMuFLMetadata.requireAdvanceWidth(G_CLEF)` — same call as the production code, so the test cannot detect a wrong value) | Rewrite with a concrete numeric bound or cross-check against a known Bravura value |
| `Engraving` | `BEAM_THICKNESS_SS` / `BEAM_SPACING_SS` / `LEDGER_LINE_THICKNESS_SS` etc. are positive non-zero plausible values | unit | none found | missing | Add `EngravingTest` assertions with concrete plausible bounds |
| `Engraving` | `NOTEHEAD_BLACK_STEM_UP_SE` / `NOTEHEAD_BLACK_STEM_DOWN_NW` anchors are loaded correctly | unit | none found | missing | Add `EngravingTest` assertions checking x/y are non-zero with expected sign |
| `Engraving` | private constructor prevents instantiation | none | — | adequate (none warranted) | — |

**4D notes (quality concerns):**

The most critical gap is that `BBox` — the geometry primitive used in every bounding-box computation across the codebase — has zero direct tests. `translateX` and `union` carry real arithmetic that can silently regress (e.g., a wrong coordinate axis or off-by-one in `union`'s `min`/`max` calls), and the Y-flip in `fromSMuFL` is a sign-convention conversion that is invisible in integration tests. All five `BBox` behaviors are pure functions with no dependencies and are trivial to test.

The sole existing `smufl` package test, `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth`, is self-referential: both the production constant and the test's expected value are computed from the same `SMuFLMetadata.requireAdvanceWidth(G_CLEF)` call, so the test passes even if the constant were set to any value from that same lookup. It cannot catch a wrong glyph mapping, a unit-conversion error, or a metadata parse regression.

The cross-package tests in `DynamicAttachmentTest` and `KeySignatureTest` that use `SMuFLMetadata.requireBBox(...)` as the oracle for their expected values exhibit the same self-referential defect: they verify structural wiring but cannot detect an incorrectly parsed bbox coordinate.

`GlyphAnchors.requireStemUpSE` and `requireStemDownNW` both have null-guard branches that throw via `RuntimeError.exit`. Neither branch has any test. The same pattern applies to `SMuFLMetadata.requireBBox`, `requireAnchors`, and `requireAdvanceWidth` — the "absent glyph throws" path is completely untested in all four methods. These are all plausible regression sites if the metadata JSON is changed or a new glyph mapping is added.

`SMuFLGlyph` enum accessors (`smuflName()`, `codepoint()`, `asString()`) are spot-checked nowhere. A transposed codepoint or misspelled SMuFL name would silently corrupt rendered glyphs and font metric lookups without any test failing.

