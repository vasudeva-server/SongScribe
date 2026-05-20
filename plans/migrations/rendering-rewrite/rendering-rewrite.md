# Rendering and Layout System Rewrite

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Coordinate System + Staff + Notes](#-phase-1-coordinate-system--staff--notes) | ✅ Done | [milestone-1-coordinate-system.md](milestone-1-coordinate-system.md) |
| 2 | [Beams + Stems](#-phase-2-beams--stems) | ✅ Done | [milestone-2-beams-stems.md](milestone-2-beams-stems.md) |
| 3 | [Ties + Glissandos](#-phase-3-ties--glissandos) | ✅ Done | [milestone-3-ties-glissandos.md](milestone-3-ties-glissandos.md) |
| 4 | [Vertical Stacking + All Decorations](#-phase-4-vertical-stacking--all-decorations) | ✅ Done | [milestone-4-vertical-stacking.md](milestone-4-vertical-stacking.md) |
| 5 | [Lyrics + Line Height + Tuplets](#-phase-5-lyrics--line-height--tuplets) | ✅ Done | — |
| 6 | [Legacy Decoration Flag Migration](#-phase-6-legacy-decoration-flag-migration) | ✅ Done | [milestone-6-legacy-flag-migration.md](milestone-6-legacy-flag-migration.md) |
| 7 | [Package Restructure (DOM / layout / ui)](#-phase-7-package-restructure-dom--layout--ui) | ⏳ Pending | — |
| 8 | [Split `ElementRenderContext` by Lifetime](#-phase-8-split-elementrendercontext-by-lifetime) | ✅ Done | [milestone-8-context-split.md](milestone-8-context-split.md) |
| 9 | [Flatten `BaseElementRenderer` Hierarchy](#-phase-9-flatten-baseelementrenderer-hierarchy) | ⏳ Pending | — |
| 10 | [Remove `StaffElement.Properties` Mutable State](#-phase-10-remove-staffelementproperties-mutable-state) | ⏳ Pending | — |
| 11 | [Performance Profiling + Regression Testing](#-phase-11-performance-profiling--regression-testing) | ⏳ Pending | — |

## Context

SongScribe's rendering and layout pipeline uses a mix of legacy pixel-based code and Gould/Ross rules. Three independent pixel constants all equal to 8 are scattered across StaffSpaces, LayoutStylesheet, and LayoutConstants. Layout and rendering code mixes pixel values throughout. This rewrite normalizes the coordinate system to staff-space units, ports algorithms from abc2svg, replaces the collision detection model, and restructures the renderer architecture.

**Spec:** [docs/specs/rendering-rewrite.md](../../../docs/specs/rendering-rewrite.md)
**Source:** abc2svg by Jean-Francois Moine (LGPL v3, compatible with GPL v3)

## Architectural Decisions

- **Coordinate unit**: Staff spaces throughout all layout and rendering code. Pixel conversion only at the render boundary via Graphics2D scale transform.
- **Scale factor**: Single mutable `pixelsPerStaffSpace` in `ScaleContext` (default 8.0). Changing this value scales everything (zoom integration point).
- **Render boundary**: Graphics2D transform set once in `LineComponent.render()`. All drawing downstream uses staff-space coordinates.
- **File format**: Version 2.1 stores all positions in staff-space units. FormatMigrator converts v2.0 pixel values on load.
- **Document tree as DOM**: The document tree (`Song` → `Line` → `StaffElement` → attachments/range elements) is modeled as a DOM. Elements carry both document data and box geometry (X/Y, content width/height), analogous to browser DOM nodes exposing `getBoundingClientRect()`. `layout/` computes geometry into the DOM and produces span/decoration layout records; `ui.renderer/` paints both. Earlier framings of "self-rendering elements" and "pure model objects" are superseded.
- **Collision detection**: Y-extent arrays (y_get/y_set) with three-layer model replacing Java2D Area intersection.
- **Renderer architecture**: Stateless `ElementRenderer<T>` strategies invoked by `LineRenderer`, reading pre-computed geometry from the DOM and `LayoutResult`. `RendererRegistry` eliminated.
- **Rename**: `Note.yPos` → `Note.staffPosition` (semantic, unit-agnostic pitch position).

---

## ✅ Phase 1: Coordinate System + Staff + Notes

Establish the staff-space coordinate system and get all basic note rendering working. This is the foundation everything else builds on.

**Verification:** Open an existing song. Notes and staff should render correctly. Nothing above the staff renders yet. Lyrics don't render yet.

See [milestone-1-coordinate-system.md](milestone-1-coordinate-system.md) for detailed implementation plan.

---

## ✅ Phase 2: Beams + Stems

Port beam algorithm from abc2svg `calculate_beam()` and `draw_beams()`. Hyperbolic slope dampening, flat beam snapping, beam thickening. Automatic stem direction from pitch. Automatic partial beam stub direction. Multi-level beams.

**Verification:** Beamed passages render with correct slopes. Compare against abc2svg output.

See [milestone-2-beams-stems.md](milestone-2-beams-stems.md) for detailed implementation plan.

---

## ✅ Phase 3: Ties + Glissandos

Port tie algorithm from abc2svg `slur_out()`. Cubic Bezier replacing quadratic. Dynamic height scaling. Interior note collision avoidance. Filled lens shape rendering. Glissando rendering between notes.

**Verification:** Ties scale height with span distance. Short ties similar to before; long ties noticeably better.

See [milestone-3-ties-glissandos.md](milestone-3-ties-glissandos.md) for detailed implementation plan.

✓ References [milestone-3-ties-glissandos.md](milestone-3-ties-glissandos.md)

---

## ✅ Phase 4: Vertical Stacking + All Decorations

Y-extent array collision detection (YSTEP=128). Three-layer model. Tier 1: articulations. Tier 2: fermata, trill. Tier 3: dynamics hairpins, text dynamics (new), volta brackets. Tier 4: tempo, beat changes, annotations. Self-rendering note-attached elements. All manual offset adjustments working post-layout.

**Verification:** Stacked decorations don't overlap. Text dynamics render for the first time.

---

## ✅ Phase 5: Lyrics + Line Height + Tuplets

Lyric collision avoidance via syllable width overflow walk (ported from abc2svg `ly_width()`). Lyric rendering below staff. Total line height calculation. Tuplet bracket rendering.

**Verification:** Full songs render completely. Lyrics don't overlap.

---

## ✅ Phase 6: Legacy Decoration Flag Migration

Migrate all decoration data from legacy boolean/object flags on `StaffElement` to the new `Attachment`/`RangeElement` types, and replace the legacy `SpanSet` containers on `Line` with `RangeElement` entries. Currently both systems coexist: user actions, file I/O, export, and copy/paste use the legacy flags and `SpanSet`s, while layout/rendering uses the new types with bridging code. This phase eliminates the dual representation so the new types become the single source of truth. When done, `SpanSet` (and `Span`, `BeamSpan`, `TieSpan`, `TupletSpan`, `DynamicsSpan`) no longer exist.

See [milestone-6-legacy-flag-migration.md](milestone-6-legacy-flag-migration.md) for the detailed implementation plan.

**Flags to migrate:**

| Legacy flag | New type | Key callers |
|-------------|----------|-------------|
| `isFermata()` / `setFermata()` | `FermataAttachment` | FermataAction, EditModeManager, FermataMenuItem, StaffElementIO, ExportABCAction |
| `isTrill()` / `setTrill()` | `Trill` (RangeElement) | MusicEditOperations, StaffElementIO, ExportABCAction, Line utilities |
| `getTempoChange()` / `setTempoChange()` | `TempoAttachment` | TempoChangeDialog, StaffElementIO, Song, ExportABCAction, MIDI |
| `getBeatChange()` / `setBeatChange()` | `BeatChangeAttachment` | BeatChangeDialog, StaffElementIO, ExportABCAction |
| `getAnnotation()` / `setAnnotation()` | `AnnotationAttachment` | AnnotationDialog, StaffElementIO, AnnotationIO, ExportABCAction |

**Per-flag migration pattern:**
1. User actions: toggle/create/remove the new type instead of setting the flag
2. File I/O: serialize/deserialize the new type (StaffElementIO, LineIO)
3. Copy constructor: copy attachments/range elements instead of boolean fields
4. Export: read from new types (ExportABCAction, MIDI)
5. UI: dialogs and menus operate on new types
6. Remove the legacy field, getter, and setter from `StaffElement`
7. Remove bridging code from `VerticalStackingCalculator` and renderers
8. Update tests

**Verification:** All user actions, file I/O, export, and copy/paste work through the new types. No legacy decoration flags remain on `StaffElement`. Bridging code in layout/rendering removed.

---

## ✅ Phase 7: Package Restructure (DOM / layout / ui)

Today `ui.layout/` mixes three responsibilities: domain elements (`LineElement`, `Attachment*`, `RangeElement*`, `Beam`, `Tie`, `Tuplet`, `Trill`, `Hairpin`, `Ending`, `Articulation`, `Crescendo`, `Diminuendo`, `Clef`, `KeySignature`, `Attribution`), layout machinery (`LayoutEngine`, `*Calculator`, `*Builder`, `LayoutResult` + `*Layout` records, `StaffExtents`, `CollisionDetector`), and units (`ScaleContext`, `InsetsSs`, `Margin`, `Size`, `ElementBoundsSs`, `LineThickness`, `SongLayoutMetrics`). `model/` and `ui.layout/` form an import cycle: `Line.java` imports 14 classes from `ui.layout`, `StaffElement` imports 5, `Song` imports 3.

Restructure into three packages with strict one-way dependencies:

```
songscribe.dom      ← document tree + box geometry + ScaleContext
                      depends on: —
songscribe.layout   ← layout algorithms, LayoutResult, *Layout records,
                      StaffExtents, CollisionDetector, units
                      depends on: dom
songscribe.ui.*     ← renderers, components, dialogs
                      depends on: dom, layout
```

### Tasks

- [ ] Unwind `ui.layout/` → `ui.renderer/` imports (`LyricLayoutBuilder` reaching into `NoteRenderer`/`GlissandoRenderer`/`LineThickness`, etc.). Move `LineThickness` into the `layout/` units.
- [ ] `jet_brains_rename` directory `model/` → `dom/`.
- [ ] `jet_brains_move` the score-element classes from `ui.layout/` into `dom/`: `LineElement`, `Attachment` + subclasses, `RangeElement` + subclasses, then `ScaleContext`.
- [ ] `jet_brains_rename` `ui.layout/` → `layout/`.
- [ ] Add a checked test that scans imports: `dom/` must not import `songscribe.layout` or `songscribe.ui`; `layout/` must not import `songscribe.ui`.

**Verification:** Project compiles. Import-scan test passes. Visual output unchanged from Phase 6 baseline.

---

## ✅ Phase 8: Split `ElementRenderContext` by Lifetime

See [milestone-8-context-split.md](milestone-8-context-split.md) for the detailed implementation plan.

Resolve [#369](https://github.com/vasudeva-server/SongScribe/issues/369). `ElementRenderContext` is a 19-field grab bag set through ~12 ordered setter calls in `LineRenderer.render()` — an undocumented protocol that's a source of stale-state bugs. Split by lifetime:

- **Per-line invariants** become construction-time immutable (`LineInvariants`): `song`, `line`, `lineIndex`, `middleLineYSs`, `layoutResult`, `metrics`, edit state, playing indices.
- **Per-element state** (currentElementIndex, overrideElementX, previewShift) becomes a small record (`ElementFrame`) built fresh per loop iteration.

After this, renderers receive `(LineInvariants, ElementFrame, T element, Graphics2D)`. Forgetting a setter is impossible by construction. Load-bearing for Phase 9.

**Verification:** `ElementRenderContext` deleted. `LineRenderer.render()` has no setter calls during the render loop. Renderer unit tests construct a valid context without reproducing a setup sequence.

---

## ⏳ Phase 9: Flatten `BaseElementRenderer` Hierarchy

With the Phase 8 context split, most of what `BaseElementRenderer` provides is either trivial accessors that forward to `LineInvariants` / `ElementFrame`, or drawing helpers that belong as static utilities. Keep the `ElementRenderer<T>` interface; delete the abstract base; renderers become effectively pure functions of `(LineInvariants, ElementFrame, T element, Graphics2D)`.

### Tasks

- [ ] Audit `BaseElementRenderer`'s protected helpers. Categorize each as (a) trivial accessor → inline, (b) drawing utility → extract to a static `RenderingUtils` (or similar), (c) genuinely needed default behavior → fold into the `ElementRenderer<T>` interface as a `default` method.
- [ ] Migrate the ~17 concrete renderers off `BaseElementRenderer` one at a time.
- [ ] Delete `BaseElementRenderer`.

**Verification:** `BaseElementRenderer` deleted. All concrete renderers implement `ElementRenderer<T>` directly. Visual output unchanged.

---

## ⏳ Phase 10: Remove `StaffElement.Properties` Mutable State

`StaffElement.Properties` holds `public final List<Lyric> lyrics` and `public final Line2D.Double stem`. The `stem` field is computed layout geometry hanging off the DOM; the `lyrics` list is genuine document state. The class is a residual grab bag of mutable state that should be split and deleted.

### Tasks

- [ ] Move `lyrics` onto `StaffElement` directly as a first-class field.
- [ ] Move `stem` into `LayoutResult` (or delete if unused after the rendering rewrite).
- [ ] Update `StaffElementIO`, `LegacyLyricsImporter`, `Line`, and `StaffElement` callers.
- [ ] Delete `StaffElement.Properties`.

**Verification:** `StaffElement.Properties` deleted. File I/O round-trips lyrics correctly. Visual output unchanged.

---

## ⏳ Phase 11: Performance Profiling + Regression Testing

Confirm no regression from Phases 7–10.

### Tasks

- [ ] Profile layout + render time on representative songs; compare against the Phase 6 baseline.
- [ ] Open a representative set of existing songs; compare renders against pre-rewrite output for visual regressions.
- [ ] Triage and fix any regressions found.

**Verification:** No visual regressions. Layout + render time within tolerance of the pre-Phase-7 baseline.
