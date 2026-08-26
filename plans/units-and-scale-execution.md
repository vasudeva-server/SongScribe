# Units and Scale — Execution

**Type:** Master plan  <br>
**Created:** 2026-08-26  <br>
**Status:** In Progress

Executes design-pass register row 1, steps 2–9. The approved decisions this plan
implements are recorded in `plans/design-pass/units-and-scale.md`; read that file
for what was decided and why. Tick a phase in the Status Dashboard the moment it
is committed — that dashboard is what lets a cleared context resume mid-step.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Collapse the document scale](#-phase-1-collapse-the-document-scale) | ⏳ Pending | — |
| 2 | [PixelDistance](#-phase-2-pixeldistance) | ⏸️ Blocked by 1 | — |
| 3 | [TextMeasurement](#-phase-3-textmeasurement) | ⏸️ Blocked by 1, 2 | — |
| 4 | [Dissolve MyFontUtils](#-phase-4-dissolve-myfontutils) | ⏸️ Blocked by 1, 3 | — |
| 5 | [Rename to DocumentScale](#-phase-5-rename-to-documentscale) | ⏸️ Blocked by 1, 2, 3, 4 | — |
| 6 | [Contracts spanning classes](#-phase-6-contracts-spanning-classes) | ⏸️ Blocked by 1, 2, 3, 4, 5 | — |
| 7 | [FontDescription tests](#-phase-7-fontdescription-tests) | ⏸️ Blocked by 4, 5 | — |
| 8 | [Gate](#-phase-8-gate) | ⏸️ Blocked by 1, 2, 3, 4, 5, 6, 7 | — |

**Every phase here ends with `./scripts/compile.sh --test`.** Each re-points its
own callers, so each leaves a final, compilable state — no phase leans on a later
one to close a half-migrated tree, and none needs an adapter, an overload, or a
field beside the old one to hold an intermediate state together. What
`~/.claude/rules/development.md` forbids is inventing that scaffolding so a
step can compile; nothing here does. Compile, because a phase whose end state is
final is information you can have for free.

Phase 8 is a separate gate: the full unit suite, coverage, and the diagrams.

---

## ⏳ Phase 1: Collapse the document scale

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/ScaleContext.java, src/main/java/songscribe/util/MyFontUtils.java, src/main/java/songscribe/ui/renderer/LineInvariants.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/layout/MetronomeContent.java, docs/zoom.md  <br>
**Recommended model/effort:** Sonnet, medium — deletions with every call site named; no design decisions remain open.

### Tasks

1. In `src/main/java/songscribe/dom/ScaleContext.java`, delete these members. Each
   has zero callers, or its callers are re-pointed in tasks 6–7 of this phase:
   `INSTANCE` (line 43), the `private volatile double pixelsPerStaffSpace` field
   (48), `getPixelsPerStaffSpace()` (52–54), `setPixelsPerStaffSpace(double)`
   (56–64) including its `pxPerSs <= 0` guard, `getScaleTransform()` (141–143),
   and `fontMaxAscentSs(Font)` (121–129). Keep the private constructor — the
   class remains a static utility.
2. Rename the constant `DEFAULT_PIXELS_PER_STAFF_SPACE` (line 46) to
   `PIXELS_PER_STAFF_SPACE` using `jet_brains_rename` with
   `rename_in_comments: false`. Its reference sites are
   `src/main/java/songscribe/dom/DocPx.java` (Javadoc),
   `src/main/java/songscribe/ui/ViewScale.java` (Javadoc, `toViewPx(Ss)` at 76,
   `toSs(ViewPx)` at 81), `src/main/java/songscribe/io/FormatMigrator.java:104`,
   `src/main/java/songscribe/io/MigrationPipeline.java:56` and `:63`, and
   `src/main/java/songscribe/io/AnnotationIO.java:101`.
3. Rewrite the three methods that read the deleted field so they read the
   constant instead: `ssToPx` (67–69), `ssToRoundedPx` (72–74), `pxToSs`
   (77–79).
4. Replace the class Javadoc (30–40). It must state that the document scale is a
   compile-time constant, and that per-view zoom is `songscribe.ui.ViewScale`'s
   concern, citing `docs/zoom.md` rather than restating it. Delete the sentence
   "Currently a singleton; this will evolve to per-view instances when zoom
   support is added." Write the Javadoc as a description of the code as it now
   is — no sentence may narrate what changed, per
   `~/.claude/rules/development.md` §"No history in comments".
5. Add a paragraph to `inchesToSs`'s Javadoc (line 81–90) stating that the method
   has no callers today and is retained for page setup, which is where the user's
   `LengthUnit` choice is going. Mirror the phrasing in
   `src/main/java/songscribe/util/LengthUnit.java:38–41`. Do not delete the
   method.
6. In `src/main/java/songscribe/ui/renderer/LineInvariants.java:227` and
   `src/main/java/songscribe/ui/component/score/LineComponent.java:298`, replace
   `ScaleContext.getPixelsPerStaffSpace()` with
   `ScaleContext.PIXELS_PER_STAFF_SPACE`.
7. In `src/main/java/songscribe/layout/MetronomeContent.java:53–55`, delete the
   clause "{@code ScaleContext.setPixelsPerStaffSpace} is never called in
   production, and zoom is applied by {@code ViewScale} and the paint transform."
   Keep the zoom-invariance claim in the preceding sentence, restated so it
   follows from the document scale being a constant. Do not leave a marker that
   anything was removed.
8. In `src/main/java/songscribe/util/MyFontUtils.java`, delete
   `getFontMetrics(Font)` (446–448) and the `METRICS_GRAPHICS` field with its
   preceding comment (440–444). Their only caller was
   `ScaleContext.fontMaxAscentSs`, deleted in task 1. This retires a second
   font-metric ruler built without the rendering hints that
   `GraphicUtils.MEASURING_GRAPHICS` carries. Leave the rest of the file alone —
   it is phase 4's. Do not hunt for imports the deletion strands; the project
   leaves those to the IDE.
9. In `docs/zoom.md`, under "Three regimes", state that the document scale is a
   compile-time constant, so staff spaces and document pixels cannot diverge —
   replacing the current wording that presents this as a consequence rather than
   a construction. State it in the present tense; do not narrate the change.
10. Run `./scripts/compile.sh --test` and fix every error before committing. This
    phase leaves a final state: every deleted member's callers are re-pointed
    above, so a failure here is a call site the task list missed, not a
    half-migrated tree. Never `./gradlew`, `gradle`, `javac`, or `java -cp`.

---

## ⏸️ Phase 2: PixelDistance

**Status:** Blocked  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/PixelDistance.java, src/main/java/songscribe/dom/DocPx.java, src/main/java/songscribe/dom/ViewPx.java, src/main/java/songscribe/dom/ScaleContext.java  <br>
**Recommended model/effort:** Sonnet, medium — one new interface and two record declarations; the contract is dictated by an existing guide.

`src/main/java/songscribe/dom/DocPx.java:15–29` and
`src/main/java/songscribe/dom/ViewPx.java:13–27` currently hold byte-identical
`roundedPx()` and `ceilPx()` bodies with byte-identical Javadoc, and
`ScaleContext.ssToRoundedPx` is a third statement of the same nearest-rounding
rule. This phase states the rule once.

### Tasks

1. Write the contract before the code. Create
   `src/main/java/songscribe/dom/PixelDistance.java` and author its class Javadoc
   first: the interface is the rounding rule the two pixel regimes share — sizes
   round **up** so content is never clipped, positions round to **nearest** so
   placement stays centered. Cite `.claude/guides/spatial-units.md`
   §"Rounding at the pixel boundary" and `docs/zoom.md` rather than restating
   them. State that `Ss` deliberately does not implement this interface, because
   staff spaces have no integer form — the omission is the point, and an absence
   nobody wrote down reads as an oversight.
2. Declare `public sealed interface PixelDistance permits DocPx, ViewPx` with
   `double value()`, `default int roundedPx()` returning `(int) Math.round(value())`,
   and `default int ceilPx()` returning `(int) Math.ceil(value())`. Move the
   per-method Javadoc that currently sits on `DocPx.roundedPx` and `DocPx.ceilPx`
   onto these defaults verbatim.
3. Reduce `src/main/java/songscribe/dom/DocPx.java` to
   `public record DocPx(double value) implements PixelDistance {}`, deleting both
   method bodies and their Javadoc. Keep the existing record-level Javadoc.
4. Reduce `src/main/java/songscribe/dom/ViewPx.java` the same way, keeping its
   record-level Javadoc.
5. Change `ScaleContext.ssToRoundedPx`'s body to
   `new DocPx(ssToPx(ss)).roundedPx()` so the nearest-rounding rule has exactly
   one statement in the codebase. Update its Javadoc to say it returns document
   pixels, and that the value rounds to nearest because it is a position.
   `ScaleContext` is in the same package, so no import is needed.
6. Do not rename `roundedPx`/`ceilPx`. Renaming them to `positionPx`/`sizePx` was
   considered and rejected.
7. Run `./scripts/compile.sh --test` and fix every error before committing. No
   call site changes in this phase, so a failure means the sealed hierarchy is
   wrong — a permitted type that does not implement, or a record that still
   declares a member the interface now provides. Never `./gradlew`, `gradle`,
   `javac`, or `java -cp`.

---

## ⏸️ Phase 3: TextMeasurement

**Status:** Blocked  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/main/java/songscribe/font/TextMeasurement.java, src/main/java/songscribe/dom/ScaleContext.java, src/main/java/songscribe/util/GraphicUtils.java, src/main/java/songscribe/dom/AnnotationAttachment.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/layout/LyricRenderMetrics.java, src/main/java/songscribe/layout/MetronomeContent.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/stacking/SystemStacker.java, src/main/java/songscribe/ui/renderer/AnnotationRenderer.java, src/main/java/songscribe/ui/renderer/RecordingGraphics2D.java, src/main/java/songscribe/ui/renderer/LyricConnectorRenderer.java, src/main/java/songscribe/ui/component/score/BaseTitleComponent.java, src/main/java/songscribe/ui/KeyCellRenderer.java, src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java  <br>
**Recommended model/effort:** Opus, high — deciding how the queries group and what each promises is the design work of this plan.

Text measurement currently lives in three places. This phase collects it into one
class with one measuring instrument.

### Tasks

1. Write the class Javadoc for
   `src/main/java/songscribe/font/TextMeasurement.java` before moving any code.
   It must state the axis that the scattered members never named: every query
   here answers one of three questions, and choosing the wrong one is a visible
   defect.
   - **advance** — how far the pen moves.
   - **ink** — where the marks actually land. Ink overshoots the advance for
     glyphs like an italic descender, and starts before the drawing origin for a
     "W"'s negative left bearing. Sizing a box from the advance when ink is meant
     clips the glyph.
   - **the font's own vertical design** — ascent, descent, leading.

   It must also state that the class holds the application's single measuring
   instrument, and why a second one cannot exist: metrics taken without the
   fractional-metrics hint wrap a paragraph at a different word than the paint
   pass does, and a component sized from those metrics clips its own text. The
   argument is already written at
   `src/main/java/songscribe/util/GraphicUtils.java:560–570`; carry it here.
2. Move `SCREEN_FRC` and `MEASURING_GRAPHICS` out of
   `src/main/java/songscribe/util/GraphicUtils.java` (declarations near lines 74
   and 143, initialised in the static block around 166–167) into
   `TextMeasurement`. `MEASURING_GRAPHICS` stays private; `SCREEN_FRC` stays
   public. Keep `setRenderingHints` in `GraphicUtils` and call it from
   `TextMeasurement`'s static initialiser.
3. Move these members from `GraphicUtils` into `TextMeasurement`, grouping them
   under the three questions from task 1: `fontMetrics(Font)`,
   `getTextBlockWidth(String, Graphics2D)`, `getTextBlockHeight(FontMetrics, int)`,
   `visualBounds(String, Font)`, `inkHeight(Rectangle2D)`,
   `extraInkAbove(...)`, `extraInkBelow(...)`. Preserve their existing Javadoc —
   `visualBounds` and `fontMetrics` in particular carry the reasoning the class
   Javadoc now cites. Drop the `get` prefix from `getTextBlockWidth` and
   `getTextBlockHeight` to match the unprefixed neighbours they now sit beside;
   use `jet_brains_rename`.
4. Move these members from `src/main/java/songscribe/dom/ScaleContext.java` into
   `TextMeasurement`: `scaleFont(Font)` (101–103), `textWidthSs(Font, String)`
   (106–108), `textHeightSs(Font)` (111–114), `fontAscentSs(Font)` (117–119),
   `fontDescentSs(Font)` (132–134). Use `jet_brains_move` so call sites re-point
   mechanically. After the move, `ScaleContext` must no longer import
   `java.awt.Font`, `java.awt.font.TextLayout`, `java.awt.geom.AffineTransform`,
   or `songscribe.util.MyFontUtils`.
5. `textHeightSs(Font)` and `textBlockHeight(FontMetrics, int)` now sit in one
   class and agree: for a single line both are ascent + descent, with leading
   inserted only between lines. State that relationship in `textHeightSs`'s
   Javadoc so the next reader does not add a third height query. Note in the same
   place that `textHeightSs` reads `LineMetrics` (float precision) while
   `textBlockHeight` reads `FontMetrics` (integer), which is why the two are not
   collapsed into one.
6. Collapse the six sites that hand-inline
   `font.createGlyphVector(SCREEN_FRC, text).getVisualBounds()` into calls to
   `TextMeasurement.visualBounds(text, font)`:
   `src/main/java/songscribe/layout/LyricRenderMetrics.java:211`,
   `src/main/java/songscribe/layout/EndingBracketGeometry.java:58` and `:62`,
   `src/main/java/songscribe/dom/Tuplet.java:66` and `:84`, and
   `src/main/java/songscribe/ui/KeyCellRenderer.java:150`. `visualBounds` returns
   `@Nullable Rectangle2D` — it is null for empty text. Every one of these six
   sites passes a non-empty literal or a value that cannot be empty, so handle
   the null per `.claude/guides/null-handling.md` rather than adding a runtime
   guard, and do not widen any caller's signature to nullable.
7. Re-point the remaining callers of the moved members. `scaleFont`:
   `layout/LyricRenderMetrics.java:77`, `layout/MetronomeContent.java:215`,
   `ui/renderer/AnnotationRenderer.java:74`. `textWidthSs`:
   `dom/AnnotationAttachment.java:78`, `layout/MetronomeContent.java:261`,
   `layout/LyricRenderMetrics.java:78`, `:79`, `:96`. `textHeightSs`:
   `dom/AnnotationAttachment.java:88`, `layout/stacking/SystemStacker.java:179`.
   `fontAscentSs`: `layout/LyricRenderMetrics.java:178`,
   `layout/MetronomeContent.java:221`, `ui/renderer/AnnotationRenderer.java:80`.
   `fontDescentSs`: `layout/LyricRenderMetrics.java:178`,
   `layout/MetronomeContent.java:222`. `fontMetrics`:
   `ui/component/score/BaseTitleComponent.java:347`. `SCREEN_FRC` references that
   are not covered by task 6: `ui/renderer/RecordingGraphics2D.java:123`,
   `ui/renderer/LyricConnectorRenderer.java:171`,
   `layout/LyricRenderMetrics.java:148`,
   `ui/dialog/SongSettingsAttributionTab.java:402`, and the Javadoc references in
   `ui/component/score/BaseTitleComponent.java:261` and `dom/Tuplet.java:79`.
8. Leave `src/main/java/songscribe/layout/LyricRenderMetrics.java` in `layout`.
   It is a cached per-font layout record built *from* measurements, not a
   measurement facility; it becomes a caller of `TextMeasurement` and nothing
   more.
9. Leave `GraphicUtils`'s images, SVG icons, DPI, stroke drawing and screen
   clamping where they are.
10. Run `./scripts/compile.sh --test` and fix every error before committing. Task
    7 lists the call sites this phase must re-point; a failure here names one it
    missed. Never `./gradlew`, `gradle`, `javac`, or `java -cp`.

---

## ⏸️ Phase 4: Dissolve MyFontUtils

**Status:** Blocked  <br>
**BlockedBy:** 1, 3  <br>
**Files:** src/main/java/songscribe/font/InstalledFonts.java, src/main/java/songscribe/font/FontDescription.java, src/main/java/songscribe/font/LocalFonts.java, src/main/java/songscribe/font/MusescoreIconFont.java, src/main/java/songscribe/util/MyFontUtils.java, src/main/java/songscribe/util/UIUtils.java, src/main/java/songscribe/font/DocumentFonts.java, src/main/java/songscribe/font/SourceSans3Font.java, src/main/java/songscribe/smufl/BravuraFont.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/ui/component/StatusBar.java, src/main/java/songscribe/ui/KeyCellRenderer.java, src/main/java/songscribe/ui/KeyDisplay.java, src/main/java/songscribe/ui/dialog/PreferencesDialog.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java, src/main/java/songscribe/ui/dialog/SongSettingsFontTab.java, src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java, src/main/java/songscribe/ui/dialog/fontchooser/FontFamiliesFactory.java, src/main/java/songscribe/ui/dialog/fontchooser/panes/StyleEntry.java  <br>
**Recommended model/effort:** Opus, high — four new class boundaries and a lifecycle change to the icon font.

`src/main/java/songscribe/util/MyFontUtils.java` holds five unrelated concepts.
This phase gives each a home and deletes the file.

### Tasks

1. Create `src/main/java/songscribe/font/InstalledFonts.java` for resolving a
   stored name to an installed face. Move `getAllFonts()` (90–118),
   `createFont(String, int)` (140–156), `findFamilyFont(String)` (166–172),
   `findClosestSourceSans3Font(String)` (182–187),
   `resolveSourceSans3Suffix(String)` (195–223), and the `familyNames`,
   `psFonts` and `allFonts` caches (78–80). Write `createFont`'s contract before
   moving it — two callers rely on it and its promise is a four-step fallback
   chain (PostScript name, then family name, then the closest Source Sans 3
   weight, then the label font) that a caller cannot see from the signature. The
   existing block comment at 132–139 states the chain; convert it to Javadoc with
   `@param` and `@return` rather than restating it. Callers to re-point:
   `src/main/java/songscribe/font/DocumentFonts.java:84`,
   `src/main/java/songscribe/ui/dialog/fontchooser/FontFamiliesFactory.java:30`.
2. Create `src/main/java/songscribe/font/FontDescription.java` for turning a face
   into text a person reads. Move `getFullFontDescription(Font)` (343–347),
   `getStyleDescription(Font)` (349–416), `parsePSName(String)` (307–341),
   `parseStyle(String)` (418–438), the `ParsedFontName` record (86), and the four
   regex constants plus the `ABBREVIATIONS` table (52–76). Name the two public
   entry points `full(Font)` and `style(Font)` so the call site reads
   `FontDescription.full(font)`; use `jet_brains_rename` after the move. Write
   contracts for both — seven call sites across four files rely on them, and
   `style` in particular promises a normalisation (abbreviations expanded, camel
   case split, a family's trailing weight word not repeated in the style) that
   the signature does not show. Keep `parsePSName` and `parseStyle`
   package-private; they are this class's internals and phase 7 tests them
   through the public entry points. Callers to re-point:
   `src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java:184` and
   `:186`, `src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java:206` and
   `:208`, `src/main/java/songscribe/ui/dialog/SongSettingsFontTab.java:102` and
   `:104`, `src/main/java/songscribe/ui/dialog/fontchooser/panes/StyleEntry.java:33`.
3. Create `src/main/java/songscribe/font/LocalFonts.java` for fonts shipped with
   the application. Move `getLocalFont(String, float)` (266–287) and both
   `installLocalFont` overloads (289–300). Name them `load(String, float)` and
   `install(String)` / `install(String, float)`. Callers to re-point:
   `src/main/java/songscribe/smufl/BravuraFont.java:80`,
   `src/main/java/songscribe/layout/EndingBracketGeometry.java:53`,
   `src/main/java/songscribe/dom/Tuplet.java:48`,
   `src/main/java/songscribe/ui/component/StatusBar.java:117`,
   `src/main/java/songscribe/font/SourceSans3Font.java:115` and `:126–132`
   (eight sites), `src/main/java/songscribe/util/UIUtils.java:663`.
4. Create `src/main/java/songscribe/font/MusescoreIconFont.java` to replace
   `getIconFont()` (242–248). `MyFontUtils.iconFont` (83–84) is a `@Nullable`
   lazily-initialised static — *constructible before usable* in
   `~/.claude/guides/design.md`'s `@Nullable` triage. Build it the way
   `src/main/java/songscribe/smufl/BravuraFont.java:79–81` does, with a private
   static holder class whose field is `static final` and therefore never null.
   Do not carry the `@Nullable` across. It loads `MusescoreIcon.otf` at size 20.
   Callers to re-point: `src/main/java/songscribe/util/UIUtils.java:696`,
   `src/main/java/songscribe/ui/KeyCellRenderer.java:49`,
   `src/main/java/songscribe/ui/dialog/PreferencesDialog.java:564`.
5. Move `getUIFont(String)` (231–239) to
   `src/main/java/songscribe/util/UIUtils.java`. It is a `UIManager` lookup with
   a `JLabel` fallback — Swing look-and-feel, not fonts as a domain. Its one
   external caller is `src/main/java/songscribe/ui/KeyDisplay.java:73`;
   `InstalledFonts.createFont` also calls it as its last-resort fallback.
6. Move `deriveBaselineShiftedFont(Font, int)` (250–261) to
   `src/main/java/songscribe/util/UIUtils.java`, beside its only caller
   `UIUtils.getTaggedString:707`.
7. Move `deriveKernedFont(Font)` (225–229) into
   `src/main/java/songscribe/font/InstalledFonts.java` and make it **private**.
   Confirm with `jet_brains_find_referencing_symbols` that it has no callers
   outside the members moved in tasks 1 and 3 before narrowing its visibility; if
   `LocalFonts` needs it, keep one copy in `InstalledFonts` as package-private
   rather than duplicating the body.
8. Delete `src/main/java/songscribe/util/MyFontUtils.java` with
   `jet_brains_safe_delete`. If it reports remaining references, they are members
   this phase failed to move — move them rather than reinstating the file.
9. Do not hunt for imports left stranded anywhere in this phase; the project
   leaves those to the IDE.
10. Run `./scripts/compile.sh --test` and fix every error before committing.
    `src/main/java/songscribe/util/MyFontUtils.java` no longer exists at this
    point, so any unresolved reference to it is a member task 8's safe-delete
    should have caught — move it to the home tasks 1–7 give its concept rather
    than reinstating the file. Never `./gradlew`, `gradle`, `javac`, or
    `java -cp`.

---

## ⏸️ Phase 5: Rename to DocumentScale

**Status:** Blocked  <br>
**BlockedBy:** 1, 2, 3, 4  <br>
**Files:** src/main/java/songscribe/  <br>
**Recommended model/effort:** Sonnet, low — a single IDE rename across roughly 25 files.

Runs last so each importing file is touched once, after `ScaleContext`'s content
is final.

### Tasks

1. Rename the file and class `src/main/java/songscribe/dom/ScaleContext.java` to
   `DocumentScale` with `jet_brains_rename`, passing
   `rename_in_comments: false`. The `{@link}` and `{@code}` references rename on
   their own; a repo-wide textual sweep damages unrelated prose.
2. Confirm no `ScaleContext` references survive by running
   `jet_brains_find_symbol` for the old name. A reference in a Markdown file
   under `docs/` or `.claude/` is not renamed by the IDE — fix those by hand, and
   check `docs/zoom.md` and `.claude/guides/spatial-units.md` in particular.
3. Update the class Javadoc's opening sentence so it names the class as the fixed
   authoring scale that `songscribe.ui.ViewScale` folds zoom on top of. State it
   in the present tense; do not record that the class was formerly called
   something else.
4. Run `./scripts/compile.sh --test` and fix every error before committing. A
   rename that compiles is a rename that took; a failure means the IDE missed a
   reference, most likely in a string or a file the rename did not treat as
   source. Never `./gradlew`, `gradle`, `javac`, or `java -cp`.

---

## ⏸️ Phase 6: Contracts spanning classes

**Status:** Blocked  <br>
**BlockedBy:** 1, 2, 3, 4, 5  <br>
**Files:** src/main/java/songscribe/font/package-info.java, src/main/java/songscribe/dom/package-info.java, docs/zoom.md, .claude/guides/spatial-units.md  <br>
**Recommended model/effort:** Opus, high — deciding what a package promises, and what belongs in a guide rather than a Javadoc.

Per-method and per-class contracts are written in the phases that create the
members. This phase covers only what spans classes.

### Tasks

1. Rewrite `src/main/java/songscribe/font/package-info.java`. It currently
   carries only `@NullMarked`. The package now holds four concepts that must not
   be confused: resolving a stored name to an installed face
   (`InstalledFonts`), describing a face to a person (`FontDescription`), loading
   a font shipped with the application (`LocalFonts`, `MusescoreIconFont`,
   `SourceSans3Font`), and measuring text (`TextMeasurement`). State what each
   owns in one sentence, and keep `@NullMarked`.
2. In `docs/zoom.md`, state that text measurement crosses from toolkit pixels
   into staff spaces in exactly one place, and name the concept rather than the
   class. Do not restate the three-question axis from `TextMeasurement`'s class
   Javadoc — cite it.
3. In `.claude/guides/spatial-units.md` §"Author in staff spaces", the sentence
   "Bring them into staff spaces through the measurement helpers rather than
   converting by hand, so text measurement crosses the boundary in one place"
   now describes reality rather than an aspiration. Name the single place.
4. In `.claude/guides/spatial-units.md` §"Rounding at the pixel boundary", state
   that the rule is carried by a sealed type both pixel regimes implement, and
   that staff spaces are outside it by construction.
5. Leave `src/main/java/songscribe/dom/package-info.java`'s `@NullMarked` alone
   unless the units types now share an invariant that spans them; `dom` holds
   roughly ten systems and this pass owns one of them, so do not write a package
   contract covering the others.
6. Run `./scripts/compile.sh --test` and fix every error before committing.
   `package-info.java` is compiled, so a malformed Javadoc tag or a `{@link}` to
   a class that no longer exists fails here. Never `./gradlew`, `gradle`,
   `javac`, or `java -cp`.

---

## ⏸️ Phase 7: FontDescription tests

**Status:** Blocked  <br>
**BlockedBy:** 4, 5  <br>
**Files:** src/test/java/songscribe/font/FontDescriptionTest.java, src/test/java/songscribe/font/package-info.java  <br>
**Recommended model/effort:** Opus, high — case selection against the testing floor, not case-per-branch.

`FontDescription`'s PostScript-name and style parsing is the only real algorithm
this pass produces: four regex tables, camel-case splitting, seven abbreviation
expansions, and a weight precedence in `resolveSourceSans3Suffix` where
`semibold` must be excluded from the `bold` test because the string contains it.
That is the testing floor's first kind — a real algorithm with logic worth
checking. Nothing else in this pass earns a test: the conversions multiply by a
constant, and `PixelDistance` is enforced by the type system.

Read `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md`
before writing anything.

### Tasks

1. Before writing each `@Test` method, check whether it will sit beside a
   same-shape sibling. If it will, both are rows in one parameterized `record`
   case table from the first case — not two methods. A varying lambda does not
   disqualify a case; only a varying assertion does.
2. Cover `FontDescription.style(Font)` over the PostScript-name shapes the
   implementation's own comments name as the hard ones: `Family-Style`,
   `Family_Style`, a style containing hyphens, a family name that itself contains
   a hyphen, and the bare-family forms `Damascus`, `DamascusBold`,
   `DamascusLight`, `DamascusMedium`, `DamascusSemiBold`. Drive these from one
   case table.
3. Cover the normalisation `style` promises: `OsF` expanding to "Oldstyle
   Figures", `SemiBold` staying one word rather than splitting, camel case
   splitting elsewhere, and each of the seven abbreviations in the
   `ABBREVIATIONS` table. Assert that the table of abbreviation cases has exactly
   as many rows as the production `ABBREVIATIONS` list, so adding an abbreviation
   without a case fails.
4. Cover the family-word-repeated case at
   `MyFontUtils.getStyleDescription:390–413` as moved: a family of "Source Sans 3
   SemiBold" with style "SemiBold" yields "Regular", and with style "SemiBold
   Italic" yields "Italic".
5. Cover `resolveSourceSans3Suffix`'s weight precedence through
   `InstalledFonts.createFont`'s fallback rather than by reaching into a private
   method: semibold beats medium beats bold beats italic-only beats regular, and
   italic composes with a weight. Do not add a production accessor to make this
   reachable — if it is not reachable through the public entry point, that is a
   finding against the API, not a reason to widen it.
6. Do not write a test for any guard, and do not derive cases by walking
   branches. A case that maps to no promise a caller can rely on does not go in.
7. Run `./scripts/test.sh FontDescriptionTest` and get it green before
   committing. It builds both trees itself, so do not compile first. Read
   failures for location; never rerun with extra flags, and never assume a
   failure is pre-existing. A failure is one of three things — the code, the
   test, or the contract — and if it is the contract, say so explicitly rather
   than quietly weakening it. Never `./gradlew test`.

---

## ⏸️ Phase 8: Gate

**Status:** Blocked  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7  <br>
**Files:** —  <br>
**Recommended model/effort:** Opus, high — a failure here is information about the design, and diagnosing it is the work.

Phases 1–7 each compile their own final state, so this phase is not where the
code first builds. It is the whole-tree gate: the full suite, coverage, and the
diagrams.

### Tasks

1. Run `./scripts/compile.sh --test` over the finished tree and report SUCCESS or
   FAILURE. Every phase compiled in isolation; this confirms they compose. Never
   `./gradlew`, `gradle`, `javac`, or `java -cp`.
2. Fix every compile error before proceeding. A failure here is one of three
   things — the code, a caller this plan did not account for, or a contract that
   was wrong about the domain. Never weaken a contract to reach green without
   saying so explicitly.
3. Stop and ask the user to run the full unit suite, then wait for the result
   before continuing. `.claude/hooks/no-full-test-suite.sh` denies a run naming
   no class or more than four; the suite is the user's to start. State what this
   plan changed and which packages it can reach — `dom`, `font`, `layout`,
   `util`, `ui`, `io`, `smufl` — so the decision is informed. Never attempt the
   suite in any form, including naming classes four at a time.
4. Run `./scripts/coverage.sh unit FontDescriptionTest` once. For each unexecuted
   region ask exactly one question: does it correspond to a missing contract
   case, or to implementation the contract promises nothing about? The first
   amends the contract and its tests; the second is left alone **and the reason
   written down**. A region nothing can reach is a dead-code finding against
   production. Never write a test to turn a region green.
5. Judge any diagram in `docs/zoom.md`. Keep only what shows what prose cannot —
   a topology, a state machine, a sequence with genuine concurrency. A diagram
   walking through a sequence the contracts already state is the contract drawn
   twice, and the second copy goes stale.
6. Ask the user to launch the application and confirm no visible change in text
   rendering: score title and subtitle centring, lyric baselines against the
   inline lyric editor, annotation placement, tuplet numbers, volta bracket
   labels, and the font names shown in Song Settings and the font chooser. This
   plan moves the measuring instrument and the ink-bounds call sites, so a
   regression would show as text shifted by a fraction of a pixel or clipped at
   an edge. Do not run the application without the user's permission.
