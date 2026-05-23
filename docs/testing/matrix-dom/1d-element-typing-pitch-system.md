### 1D. element typing & pitch system — `ElementType`, `RangeElement`, `KeySignature`, `ScaleContext`, `StructuralElement`, `Clef`, `Duration`, `KeyType`, `ElementLocation`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ElementType | all types have non-zero bounds after static init | unit | `ElementTypeTest.testAllVisualTypesHaveNonZeroBounds` | adequate | keep |
| ElementType | `getFullElementCenterXSs()` = width/2 | unit | `ElementTypeTest.testCenterXIsHalfWidth` | adequate | keep |
| ElementType | `getFlagGlyph(upper)` per flagged type × direction | unit | `ElementTypeTest.testGetFlagGlyphReturnsCorrectGlyphForFlaggedTypes` | adequate | keep |
| ElementType | `getFlagGlyph(GRACE_QUAVER)` always FLAG_8TH_UP | unit | `ElementTypeTest.testGetFlagGlyphReturnsEighthFlagForGraceQuaver` | adequate | add explanatory comment (asymmetry intentional; assertion can fail — keep) |
| ElementType | `getFlagGlyph` null for non-flagged types | unit | `ElementTypeTest.testGetFlagGlyphReturnsNullForNonFlaggedTypes` | adequate | keep |
| ElementType | `isDuration()` — notes/rests true; grace/barline/breath/glissando false | unit | `ElementTypeTest.testIsDuration*` (3) | adequate | keep |
| ElementType | `toNote()`/`toRest()` — 6-pair bidirectional + identity | unit | `ElementTypeTest.testToNote*`/`testToRest*` (6) | adequate | keep |
| ElementType | stemmed `getElementHeightSs(true)` ≠ `(false)` | unit | `ElementTypeTest.testStemmedNoteHeightIsDirectionDependent` | inadequate | asserts only `>0`; add directional `!=` assertion for CROTCHET |
| ElementType | barline/repeat heights == STAFF_HEIGHT_SS | unit | `ElementTypeTest.ElementHeightTests` (2) | adequate | keep |
| ElementType | SEMIBREVE height same both directions | unit | `ElementTypeTest.ElementHeightTests.testSemibreveHeightIsSameBothDirections` | adequate | keep |
| ElementType | barline width arithmetic (single/double/final/repeat) | unit | `ElementTypeTest.ElementWidthTests` (4) | adequate | keep |
| ElementType | grace `fullWidthSs` < regular QUAVER | unit | `ElementTypeTest.ElementWidthTests.testGraceNoteWidthIsScaled` | adequate | keep |
| ElementType | stemmed flagged width > unflagged; notehead width consistent | unit | `ElementTypeTest.ElementWidthTests` (2) | inadequate | `testStemmedNoteWidthIncludesFlagExtent` uses `>=` (allows equality); should be `>` |
| ElementType | SEMIBREVE width == full width (no flag) | unit | `ElementTypeTest.ElementWidthTests.testSemibreveWidthFromBBox` | adequate | keep |
| ElementType | `isBeamable()` | unit | — | missing | write membership test |
| ElementType | `isRepeat()`/`isBarLine()` | unit | — | missing | write membership tests |
| ElementType | `isNonDuration()` (excludes GLISSANDO) | unit | — | missing | write membership test |
| ElementType | `isContentElement()`/`isNonContentElement()` | unit | — | missing | write membership tests |
| ElementType | `isTerminal()`/`isValidTerminal()`/`isReplaceableByTerminal()` (REPEAT_LEFT exclusion) | unit | — | missing | write membership tests (non-obvious exclusion) |
| ElementType | `snapToEnd()` membership | unit | `HorizontalAdjustmentTest` (integration only) | wrong-level | add dedicated membership unit test |
| ElementType | `drawStaveLongitude()` — false only BREATH_MARK | unit | — | missing | write test |
| ElementType | `endingAnchorXOffsetSs()` — 3-branch formula | unit | — | missing | write test per branch |
| ElementType | `terminalFlushRightXSs(lineWidth, type)` = lineWidth − baseWidth | unit | `LayoutEngineTest` (self-referential) | inadequate | write direct arithmetic test (LayoutEngineTest uses it as its own oracle) |
| ElementType | `getSMuFLGlyph()` mapping (barlines null) | unit | — | missing | write map-contents test |
| ElementType | `isPitchedNote()`/`isNote()`/`isNoteWithStem()`/`isGraceNote()` | unit | (used as loop predicate, not asserted) | missing | write predicate test across full type set |
| ElementType | alias types share bounds/instance with canonical | unit | `testAllVisualTypesHaveNonZeroBounds` (partial) | adequate (partial) | optional: add alias==canonical width equality |
| RangeElement | `isInvalidatedBy(deleted)` — anchor/end/both/middle/external | unit | `RangeElementInvalidationTest` (5 params × 6 subtypes, in layout/) | adequate | keep |
| RangeElement | `getElementCount()` — `end−start+1`; 0 when null/not-in-line | unit | — | missing | write test |
| RangeElement | `getContentWidthSs()` — `|endX−anchorX|+endWidth`; 0 when null | unit | — | missing | write test |
| RangeElement | base `isInvalidatedBy{Insertion,Deletion,Replacement}` return false (hooks) | none | — | none | trivial defaults; subclass overrides tested in `EndingInvalidationTest` |
| RangeElement | base `isAbove()` returns true | none | — | none | trivial default |
| KeySignature | default ctor → NONE/0 | unit | `KeySignatureTest.EmptySignature` (in layout/) | adequate | keep |
| KeySignature | ctor clamps accidentalCount to 0–7 | unit | — | missing | write test (−1→0, 8→7) |
| KeySignature | `hasAccidentals()` — count=0 false; NONE false; else true | unit | `KeySignatureTest` (indirect via dimensions) | inadequate | add direct assertions (3 conditions) |
| KeySignature | `getContentWidthSs()` — count × glyph bbox width; 0 when none | unit | `KeySignatureTest.Sharps`/`Flats`/`EmptySignature` | adequate | keep |
| KeySignature | `getContentHeightSs()` — glyph bbox height; 0 when none | unit | `KeySignatureTest.Sharps`/`Flats` | adequate | keep |
| KeySignature | px methods delegate to ssToPx | unit | `KeySignatureTest.testPxDerivesFromSs` | adequate | keep |
| KeySignature | `setAccidentalCount` clamps (same guard) | unit | — | missing | write test (−1→0, 8→7) |
| ScaleContext | `ssToPx(ss)` = pps × ss | unit | (used as collaborator only) | inadequate | write direct test w/ known pps |
| ScaleContext | `ssToRoundedPx(ss)` rounds to nearest int | unit | — | missing | write test (round down/up) |
| ScaleContext | `pxToSs(px)` = px / pps | unit | — | missing | write direct test |
| ScaleContext | `setPixelsPerStaffSpace` throws IAE for ≤0 | unit | — | missing | write test (0 and negative) |
| ScaleContext | `getScaleTransform()` correct scale factor | unit | — | missing | write test |
| ScaleContext | `scaleFont(font)` — size in ss units | unit | (test setup only) | inadequate | write direct test |
| ScaleContext | `textWidthSs`/`textHeightSs`/`fontAscentSs`/`fontDescentSs`/`fontMaxAscentSs` wrap pxToSs(metric) | unit | (helpers only, never tested directly) | missing | write a test each vs `pxToSs` of the pixel metric |
| StructuralElement | `getStaffPosition()` always type default (ignores stored pitch) | unit | `StaffElementCopyConstructorTest` (indirect) | inadequate | write direct test (CROTCHET_REST) |
| StructuralElement | `getDotCount()` — rests delegate to super; non-rests always 0 | unit | — | missing | write test (barline→0 even after setDotCount; rest preserves) |
| StructuralElement | `getAccidental()` always null | unit | `StaffElementCopyConstructorTest` | adequate | keep |
| StructuralElement | `clone()` returns `StructuralElement` w/ state copied | unit | `StaffElementCopyConstructorTest` (via copy ctor, not clone) | missing | write clone test asserting type + dot count |
| Clef | `getContentWidthPx`/`HeightPx` from G_CLEF bbox via ssToPx | unit | — | missing | write test vs bbox |
| Duration | `getNote()` returns a clone (not shared instance) | unit | — | missing | write identity-≠ test |
| Duration | dotted variants → dotCount=1, staffPosition=1 | unit | — | missing | write test (3 dotted constants) |
| Duration | non-dotted variants → dotCount=0 | unit | — | missing | write test |
| Duration | each constant's note has expected ElementType | unit | — | missing | write test (all 7) |
| KeyType | pure enum, no methods | none | — | none | no test warranted |
| ElementLocation | ctor rejects negative line/element index | unit | — | missing | write test (IAE both) |
| ElementLocation | `matches(l,e)` iff both equal | unit | — | missing | write test (3 cases) |
| ElementLocation | zero indices valid (boundary) | unit | — | missing | write test |

**1D notes (quality concerns):** **`ScaleContext` is the highest-leverage gap** — every pixel dimension in the app flows through `ssToPx`/`ssToRoundedPx`/`pxToSs`, yet it is used everywhere as a collaborator and unit-tested nowhere; a bug shifts all geometry silently. Three concrete test defects: `testStemmedNoteHeightIsDirectionDependent` asserts only `>0` (can't catch up/down swap); `testStemmedNoteWidthIncludesFlagExtent` uses `>=` where the contract is strictly `>`; `LayoutEngineTest` uses `terminalFlushRightXSs` as both expected value and code-under-test (self-referential). `ElementType`'s many predicate-membership methods (`isBeamable`, `isTerminal` family, `isPitchedNote` family) are used in production but never directly asserted. `ElementLocation` has no tests anywhere. (Note: `RangeElementInvalidationTest`, `KeySignatureTest` correctly test `dom` classes but live under `layout/`.)

