## 9. `ui/renderer` (audited 2026-05-22)

Audited via three production-first sub-audits run in one wave: **9A** renderer
infrastructure + note-area geometry; **9B** span / connector renderers; **9C**
glyph / element painters. Read-only; e2e assessed from source only; coverage
checked across unit (mirrored + cross-package) and e2e. Scope: 29 production
classes (+ 1 `package-info`). Tallies below are parsed directly from the verdict
column of each table (the sub-audits' own prose self-counts drifted and were
corrected to match).

- [9A — Renderer infrastructure + note-area geometry](9a-renderer-infrastructure-note-area-geometry.md)
- [9B — Span / connector renderers](9b-span-connector-renderers.md)
- [9C — Glyph / element painters](9c-glyph-element-painters.md)

### §9 summary

**115 behavior rows: 87 testable / 28 `none`; of the 87 testable, 34 adequate ·
48 missing · 4 inadequate · 1 redundant · 0 wrong-level (~60% dark).** Zero
genuine e2e escalations in the entire package — every testable behavior is
`unit`, consistent with the rubric (renderers either paint or compute; the
integration risk lives upstream in `layout`/`ui/component`).

**The rubric's "pure painting → `none`" prediction held but was narrower than
expected.** The 28 `none` rows concentrate in 9C glyph painters (14/37), yet the
audit's defining finding is that substantial *computed* logic hides inside
classes named like painters and is almost entirely untested: glyph-selection
maps, staff-position arithmetic, barline-type switches, and duration-advance
math that the rubric does **not** excuse as paint.

**Darkest zone — 9C glyph painters (only 1 of 23 testable rows adequate).**
Untested computation spans `NoteRenderer` (stem/dot/ledger geometry helpers,
`computeBaseStemGeometry`, `forEachDotPosition`), `KeySignatureRenderer`
(flat/sharp staff-position arrays + the 4-branch `renderKeyChange`),
`BarRenderer` (6-way barline/repeat-type switch + `drawRightRepeat` advance),
`MetronomeRenderer`/`TempoChangeRenderer` (glyph mapping, dotted-duration
advance arithmetic, tempo-string assembly), and `RestRenderer`/
`ArticulationRenderer`/`TrillRenderer`/`DynamicsRenderer` (rest-Y branch,
combo-articulation glyph selection, wavy-line segment count, hairpin endpoints).

**9B span / connector renderers — every cross-element geometry helper dark:**
`BeamGroupRenderer` (`getBeamLevel`, `stemTipYSsOffset`, `getBeamHighlightColor`),
`TupletRenderer` bracket-X arithmetic + `numberOnly` branch, `EndingRenderer`
`getEffectiveEndingYSs`, `TieRenderer.determineTieColor`, `AnnotationRenderer`
baseline-Y. Bright spots: the two lyric renderers (`LyricConnectorRenderer`,
`LyricTextRenderer`) are well-covered with falsifiable assertions, and
`GlissandoRenderer`'s geometry primitives (`computeFarBoundsT`,
`findNoteAreaEntryPoint`, `hitTestGlissando`) are adequate.

**9A infrastructure — strongest existing coverage in the package**
(`LineInvariants.getElementColor` color-resolution matrix, `NoteAreaBuilder`
cache hit/invalidation matrix, `RenderingUtils.getDecorationColor`). The single
riskiest dark path in §9 is `LineInvariants.isLyricSpanPlaying()` — five exit
points, feeding two other untested color methods (`getLyricColor`,
`getLyricConnectorColor`). `GraphicsState.save/close` restore contract is also
untested.

**inadequate (4):** (1) `GlissandoRenderer` unison-suppression tests assert on
model `getPitch()` and never invoke the renderer's early-return branch (9B);
(2,3) two `NoteAreaBuilder.buildNoteArea` tests assert only `isEmpty()==false`
with no geometry (9A); (4) `NoteRendererTest` is a **name mismatch** — all six
tests exercise `NoteGeometry`, not `NoteRenderer`, with directional-only
(`isNegative`/`isPositive`) assertions and no independently-computed expected
values (9C). **redundant (1):** the `NoteAreaBuilder` `getLedgerLineCount` trio
tests `StaffElement.getLedgerLineCount()` (9A).

**Cross-session attribution (for remediation, not new rows here):**
`NoteRendererTest` belongs in `layout` (`NoteGeometry`, Session 3) when rewritten;
the `getLedgerLineCount` trio belongs in `StaffElementTest` (`dom`, Session 1).

**No dead classes** (all 29 actively used by `LineRenderer`). One unused symbol:
`BeamGroupRenderer`'s `LOG` field is declared but never invoked.

### §9 production observations (filed as GitHub issue #414)

1. **`GraphicsState.close()` asymmetric null guard.** `CLIP` is restored
   unconditionally while `COLOR`/`STROKE`/`FONT`/`TRANSFORM`/hints guard on
   `!= null`. Harmless with real `Graphics2D` (those getters never return null)
   but a custom/stub `Graphics2D` could silently skip restoration. Normalize or
   comment.
2. **`NoteAreaBuilder.addAccidentalToArea()` uniform accidental height.** Uses
   the sharp bbox height for all accidentals; a double-flat is taller, so the
   composite note area can understate the visual footprint, potentially letting
   a glissando endpoint land too close. Documented as an approximation but no
   follow-up exists.
3. **`EndingRenderer.getEffectiveEndingYSs()` hard-fails on missing layout.**
   Throws `IllegalStateException` when no `DecorationLayout` is found, diverging
   from every peer span renderer (which silently skip null layouts) — an
   uncaught-exception risk if layout invalidation races rendering.
4. **`GlissandoRenderer.computeEndpoints()` structural smell.** Two
   `//noinspection ConstantValue` suppressions around redundant `tgt` null guards
   (the compiler can't prove non-null inside the `!isSlideOut` branch). Splitting
   into distinct slide-out vs. connected branches would eliminate them.
5. **`KeySignatureRenderer.renderKeyChange` "adding accidentals" comment
   contradicts the code.** When `nextLine.count > line.count` the comment says
   "just show the new ones" but `accidentalCounts[0]` is set to the full new
   count. Intent (full redraw vs. delta) is ambiguous and untested.
6. **`DynamicsRenderer.renderSingleHairpin` endpoint logic is not extractable.**
   The crescendo/diminuendo `Line2D.Double` corner selection lives inside a
   method that also sets stroke/color, so the branch can only be observed by
   mocking `Graphics2D.draw()`. Extract endpoint computation to a package-private
   method.
7. **`BeamGroupRenderer` unused `LOG` field** (declared, never invoked) —
   candidate for removal.
