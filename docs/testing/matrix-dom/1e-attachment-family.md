### 1E. attachment family — `Attachment`, `Annotation`, `AnnotationAttachment`, `DynamicAttachment`, `FermataAttachment`, `MetronomeAttachment`, `BeatChangeAttachment`, `TempoChangeAttachment`, `BeatChange`, `Tempo`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| Attachment | `Alignment` enum + owner/alignment accessors | none | — | none | trivial | — |
| Attachment | `copy(StaffElement)` contract — same-typed instance re-owned by newOwner | unit | `StaffElementCopyConstructorTest` (presence only) | inadequate | write per-subclass tests: correct type, fields preserved, owner==newOwner | ⬜ |
| Annotation | `ABOVE`/`BELOW` constants derived from `ssToPx(−2)`/`ssToPx(4)` | unit | — | missing | write relative-position test (ABOVE<0, BELOW>0, BELOW>|ABOVE|) | ⬜ |
| Annotation | `userYOffsetSs` default 0 + round-trip | none | — | none | trivial accessors | — |
| Annotation | `yPosPx` default == ABOVE | unit | — | missing | write test | ⬜ |
| AnnotationAttachment | `computeContentWidthSs(font)` via `textWidthSs` | unit | `AnnotationAttachmentTest` (height only) | inadequate | add width test w/ known font/string | ⬜ |
| AnnotationAttachment | `computeContentHeightSs(font)` via `textHeightSs` | unit | `AnnotationAttachmentTest.testUsesProvidedFont` | adequate | keep (but see defect note) | — |
| AnnotationAttachment | `getContentWidth/HeightSs/Px()` throw UnsupportedOperationException | unit | — | missing | write test (all 4 throw — contract guard) | ⬜ |
| AnnotationAttachment | `setText`/`getText` via inner Annotation | none | — | none | trivial delegation | — |
| DynamicAttachment | `DynamicType` — symbol/glyph/velocityFraction for all 8 | unit | `DynamicAttachmentTest.DynamicTypeFields` | adequate | keep (but see defect note) | — |
| DynamicAttachment | `getContentWidth/HeightSs()` — bbox path + fallback path | unit | `DynamicAttachmentTest.Dimensions` (4) | adequate | keep | — |
| DynamicAttachment | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write px=ss×scale tests | ⬜ |
| DynamicAttachment | serialization round-trip preserves DynamicType | unit | `DynamicAttachmentPersistenceTest`, `StaffElementIOTest.DynamicSerialization` | adequate | keep | — |
| DynamicAttachment | `copy()` — new instance, same type + newOwner | unit | `StaffElementCopyConstructorTest` (presence only) | inadequate | add type + owner assertions | ⬜ |
| FermataAttachment | `getContentWidth/HeightSs()` SMuFL constants | unit | `FermataTrillStackingTest.testFermataHasPositiveDimensions` (`>0`) | inadequate | write direct test vs exact bbox values | ⬜ |
| FermataAttachment | `getContentWidth/HeightPx()` via ssToPx | unit | — | missing | write px-matches-ssToPx test | ⬜ |
| FermataAttachment | `copy()` — new instance, owner preserved | unit | `StaffElementCopyConstructorTest` (presence only) | inadequate | add owner assertion | ⬜ |
| MetronomeAttachment | `metronomeGlyphFor(ElementType)` — 6 notes + default null | unit | — | missing | write parametrized test | ⬜ |
| MetronomeAttachment | `dotAdvanceWidthSs()` — bbox advance × NOTE_SCALE | unit | — | missing | write test | ⬜ |
| MetronomeAttachment | `noteWidthSs(el)` — 0 for unmapped; +dot width for dotted | unit | `BeatChangeAttachmentTest` (indirect) | inadequate | write direct tests (undotted, dotted, barline→0) | ⬜ |
| MetronomeAttachment | `getContentHeightSs()` — QUARTER_NOTE_HEIGHT_SS | unit | `BeatChangeAttachmentTest` | adequate | keep | — |
| BeatChangeAttachment | `computeContentMetrics(font)` — 3-region geometry, total width, descent | unit | `BeatChangeAttachmentTest` (7) | adequate | keep | — |
| BeatChangeAttachment | `copy()` — same beatChange + newOwner | unit | — | missing | write test | ⬜ |
| BeatChange | `fromLegacyName` — canonical names → duration pairs | unit | (only error path covered) | inadequate | write parametrized test for all 5 names + aliases | ⬜ |
| BeatChange | `fromLegacyName` unknown → IAE w/ message | unit | `StaffElementIOTest.testUnknownBeatChangeThrowsMeaningfulError` (wrapped) | adequate (indirect) | keep; optionally add direct test | — |
| TempoChangeAttachment | `computeContentMetrics` showTempo=true → glyph+text regions | unit | `SystemTierStackingTest` (`>0` only) | inadequate | write direct region/width test | ⬜ |
| TempoChangeAttachment | showTempo=false + description → text-only, glyph width 0 | unit | — | missing | write test | ⬜ |
| TempoChangeAttachment | showTempo=false + empty description → zero width, no regions | unit | — | missing | write test | ⬜ |
| TempoChangeAttachment | `copy()` — same tempo + newOwner | unit | — | missing | write test | ⬜ |
| Tempo | `getRealTempo()` — `(visibleTempo × noteDuration)/PPQ` | unit | — | missing | write test (CROTCHET + MINIM to catch divisor regression) | ⬜ |
| Tempo | default ctor → 120/CROTCHET/"Moderate"/show=true | unit | `SongDefaultsTest.testDefaultTempo` | adequate | keep | — |
| Tempo | `shouldShowTempo()` fallback when song tempo null | unit | `SongDefaultsTest.testEffectiveTempoFallbackWhenTempoIsNull` | adequate | keep | — |

**1E notes (quality concerns):** **`BeatChange.fromLegacyName` happy paths are completely untested** — only the error path is covered; no test maps a valid legacy name to its expected record (most significant gap here). Multiple `copy()` verdicts are `inadequate` because `StaffElementCopyConstructorTest` checks attachment *presence* but never that the copy is a new object with updated owner. Tautological tests: `DynamicAttachmentTest.testUiTypesHaveNonNullGlyphs` (`isNotNull()` on hard-coded enum fields — replace with bbox-resolves check); `AnnotationAttachmentTest.testUsesProvidedFont` (`isCloseTo(textHeightSs(font))` calls the production method as its own oracle). `Fermata`/`Tempo`/`TempoChange` dimension/metric contracts are asserted only at `>0` through stacking tests (wrong level / weak). Cross-package: `VelocityMapTest` (midi/) gives good `DynamicType.getVelocityFraction()` coverage end-to-end.

