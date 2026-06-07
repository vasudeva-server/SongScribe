### 9C — Glyph / element painters

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| `NoteRenderer` | `getNoteHeadGlyph(ElementType)` — map lookup, returns glyph or null | unit | — | missing | Add: all 7 note types return correct glyph; non-note type returns null | ✅ |
| `NoteRenderer` | `getNoteHeadChar(ElementType)` — derives String from glyph or null | unit | — | missing | Add: null when type has no glyph, non-null for standard types | ✅ |
| `NoteRenderer` | `computeBaseStemGeometry(ElementType, boolean)` — derives stemLeftX, anchorY, length by type/direction | unit | — | missing | Add: minim vs. black head, up vs. down, grace note uses separate anchor | ✅ |
| `NoteRenderer.StemGeometry` | `stemTipYSs(boolean)` — tip = anchorY ∓ length | unit | — | missing | Add: up tip = anchorY - length; down tip = anchorY + length | ✅ |
| `NoteRenderer` | `forEachDotPosition(note, beamed, upper, consumer)` — xAdjust branching by note type, yOffset by staff position parity | unit | — | missing | Add: semibreve/minim offsets, beamable+unbeamed+upper offsets, on-line vs. space yOffset, dotCount loop | ✅ |
| `NoteRenderer` | `getLedgerLineCenterXSs(note)` — rightEdge / 2 | unit | — | missing | Add: simple arithmetic verified against known notehead width | ✅ |
| `NoteRenderer` | `getLedgerLineWidthSs(note, extensionSs)` — rightEdge + 2×extension | unit | — | missing | Add: verify additive formula | ✅ |
| `NoteRenderer` | Accidental bounds (via `NoteGeometry.getAccidentalBoundsSs`) — null for grace/no accidental; sensible extents per type; widens when parenthesized | unit | `NoteRendererTest.*` (6 tests) | inadequate | Tests are correct and can fail, but they test `NoteGeometry` not `NoteRenderer` — name mismatch (class should be `NoteGeometryTest` or tests should be moved); also assertions are directional-only (`isNegative`/`isPositive`/`isGreater`) with no expected values from independent calculation | ✅ |
| `NoteRenderer` | Pure painting (render, renderNoteHead, renderStem, renderFlags, renderDots, renderLedgerLines, renderAccidental, renderBreathMark) | none | — | none | — | — |
| `RestRenderer` | `getRestGlyph(ElementType)` — map lookup | unit | — | missing | Add: each rest type maps to correct glyph; non-rest returns null | ✅ |
| `RestRenderer` | `calculateRestYSs(note, middleLineYSs)` — branching by SEMIBREVE_REST / MINIM_REST / other | unit | — | missing | Add: all three branches with exact expected Y offsets | ✅ |
| `RestRenderer` | Pure painting (render, renderDots) | none | — | none | — | — |
| `ClefRenderer` | `render` — `baseline = middleLineYSs + 1.0`, no branching | none | — | none | Trivial single-expression positioning; geometry is a named constant offset | — |
| `KeySignatureRenderer` | `render` no-op when `hasAccidentals()` is false | unit | `KeySignatureRendererTest.testRenderIsNoOpForCMajor` | adequate | — | — |
| `KeySignatureRenderer` | `render` draw loop — correct staff positions for flats (BEADGCF order) and sharps (FCGDAEB order), accidentalCount iterations | unit | — | missing | Add: verify FLAT/SHARP_STAFF_POSITIONS arrays encode correct staff positions for 1–7 accidentals | ✅ |
| `KeySignatureRenderer` | `renderKeyChange` — 4 branches: same type adding, same type removing (naturals for removed), different type (naturals then new key), identical keys (no-op) | unit | — | missing | Add: each branch; verify correct keyType arrays, accidentalCounts, startingOffsets, isNaturals flags | ✅ |
| `KeySignatureRenderer` | `getGlyphForKeyType` — switch on FLATS/SHARPS/default throws | unit | — | missing | Add: FLATS → FLAT glyph, SHARPS → SHARP glyph, NONE throws | ✅ |
| `KeySignatureRenderer` | Pure painting (drawString calls in render/renderKeySignatureChange) | none | — | none | — | — |
| `BarRenderer` | `renderBarLineOrRepeat` — switch on 6 barline/repeat types selects correct drawing primitives | unit | — | missing | Add: verify each case (SINGLE, DOUBLE, FINAL_DOUBLE, REPEAT_LEFT, REPEAT_RIGHT, REPEAT_LEFT_RIGHT) calls the right draw helpers | ✅ |
| `BarRenderer` | `drawRightRepeat` — returns x after thick bar; accumulates dots-advance + sep + thin + sep + thick | unit | — | missing | Add: verify returned x is correct | ✅ |
| `BarRenderer` | Pure painting (drawBar, drawRepeatDots, resolveBarXSs) | none | — | none | — | — |
| `ArticulationRenderer` | `render` — combo detection (hasStaccato && hasAccent → ACCENT_STACCATO glyph); solo staccato → STACCATO; solo accent → ACCENT | unit | — | missing | Add: three combinations; verify correct glyph selected via layout-position path | ✅ |
| `ArticulationRenderer` | Pure painting (drawBravuraGlyph calls) | none | — | none | — | — |
| `FermataRenderer` | `render` — guard (no FermataAttachment → no-op); layout lookup; delegates to drawBravuraGlyph | none | — | none | The only logic is a null guard; placement is entirely delegated to `NoteAttachedStacker` and `RenderingUtils` — no computable geometry owned here | — |
| `TrillRenderer` | `drawWavyLine` — segment count = `max(1, round(length / WIGGLE_SEGMENT_WIDTH_SS))`; scale = length/segWidth/segments | unit | — | missing | Add: zero/negative length no-op; normal length computes correct segment count; rounding edge case | ✅ |
| `TrillRenderer` | `renderTrillAtPosition` — branches on endNote != null && endNote != anchor | unit | — | missing | Add: single-note trill (NaN endX); multi-note trill (endX = endNote X + noteheadWidth) | ✅ |
| `TrillRenderer` | Pure painting (renderTrill, drawString) | none | — | none | — | — |
| `BeatChangeRenderer` | `render` — null guard on attachment; delegates to `drawDurationEquals` + `drawDurationGlyph` | none | — | none | All branching logic lives in `MetronomeRenderer` base methods | — |
| `MetronomeRenderer` | `requireMetronomeGlyph(ElementType)` — 6-way mapping + throws on unmapped type | unit | — | missing | Add: each note type maps to correct SMuFL glyph; unmapped type throws RuntimeError | ✅ |
| `MetronomeRenderer` | `drawDurationEquals` — advances xSs by glyph advance + dotAdvance (×2 if dotted) + equals string width | unit | — | missing | Add: dotted and non-dotted duration; verify returned xSs accounts for all advances | ✅ |
| `MetronomeRenderer` | `drawDurationGlyph` — draws glyph + optional dot | none | — | none | Pure painting delegating to already-tested geometry | — |
| `TempoChangeRenderer` | `renderTempoChange` — `shouldShowTempo` branch: with tempo shows "visibleTempo + space + description + glyph"; without shows description only | unit | — | missing | Add: verify StringBuilder contents for showTempo=true vs false | ⬜ |
| `TempoChangeRenderer` | Pure painting (drawString) | none | — | none | — | — |
| `DynamicMarkingRenderer` | `render` — null guard on attachment; `glyph = dynamicType.getGlyph()` (null → return) | none | — | none | Glyph selection is an enum property on `DynamicAttachment.DynamicType`, already verified there; renderer itself has no logic | — |
| `DynamicMarkingRenderer` | Pure painting | none | — | none | — | — |
| `DynamicsRenderer` | `renderSingleHairpin` — type branch: crescendo → two lines from left-middle to right-top/bottom; diminuendo → two lines from left-top/bottom to right-middle | unit | — | missing | Add: verify line endpoints differ between crescendo and diminuendo (could test via a recording Graphics2D or by extracting coordinate logic to a pure method) | ⬜ |
| `DynamicsRenderer` | Pure painting (g2.draw calls) | none | — | none | — | — |

**Notes.**

The audit covers 13 production classes. Across the 37 rows above (23 testable, 14 `none`), the tally is:

- **adequate**: 1  
- **inadequate**: 1 (`NoteRendererTest` — name mismatch: class is `NoteRendererTest` but all tests call `NoteGeometry` methods, not `NoteRenderer`; assertions use directional-only comparisons)  
- **missing**: 21  
- **none**: 14  

**Dead code:** No dead code identified. Every method has either a rendering call path, a utility call from collaborator classes (`computeBaseStemGeometry` is called by `GlissandoRenderer`), or a public API used from `LineComponent`/`FughettaRenderer` equivalents. `FermataRenderer.renderFermata` is a thin forwarding wrapper — its usages should be confirmed if removal is considered.

**Production observations for the Session 9 GitHub issue:**

1. **`NoteRendererTest` is misclassified** — all six tests in `NoteRendererTest` exercise `NoteGeometry.getAccidentalBoundsSs`, not `NoteRenderer` itself. The class has no import for `NoteRenderer`. This file should be renamed `NoteGeometryTest` and moved to `src/test/…/layout/` alongside the other `NoteGeometry` tests (or absorbed into a future `NoteGeometryTest`). The tests themselves are sound.

2. **`KeySignatureRenderer.renderKeyChange` "adding accidentals" branch is ambiguous** — when `nextLine.count > line.count`, the code sets `accidentalCounts[0] = nextLine.getKeyAccidentalCount()` (the full new count) but the comment says "just show the new ones." It is unclear whether this draws the full new key signature starting at the beginning (rendering all accidentals) or only the incremental ones. A unit test of this branch is the only way to confirm the intent is correctly implemented; the absence of one leaves this ambiguous.

3. **`DynamicsRenderer.renderSingleHairpin` lacks a pure-function extraction** — the hairpin line-endpoint logic (two `Line2D.Double` constructions that differ only in which corners go to the apex) is directly inside a method that also sets stroke and color. If the line construction ever regresses, the only way to catch it is to mock `Graphics2D.draw()` and inspect the shapes passed — awkward. Extracting endpoint computation to a package-private method would make it trivially unit-testable.

(37 rows: 1 adequate / 1 inadequate / 21 missing / 14 none)

