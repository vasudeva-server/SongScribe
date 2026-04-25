# Rendering and Layout System Rewrite

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Coordinate System + Staff + Notes](#-phase-1-coordinate-system--staff--notes) | ✅ Done | [milestone-1-coordinate-system.md](milestone-1-coordinate-system.md) |
| 2 | [Beams + Stems](#-phase-2-beams--stems) | ✅ Done | [milestone-2-beams-stems.md](milestone-2-beams-stems.md) |
| 3 | [Ties + Glissandos](#-phase-3-ties--glissandos) | ✅ Done | [milestone-3-ties-glissandos.md](milestone-3-ties-glissandos.md) |
| 4 | [Vertical Stacking + All Decorations](#-phase-4-vertical-stacking--all-decorations) | ✅ Done | [milestone-4-vertical-stacking.md](milestone-4-vertical-stacking.md) |
| 5 | [Lyrics + Line Height + Tuplets](#-phase-5-lyrics--line-height--tuplets) | ⏳ Pending | — |
| 6 | [Legacy Decoration Flag Migration](#-phase-6-legacy-decoration-flag-migration) | ⏳ Pending | — |
| 7 | [Cleanup + Polish](#-phase-7-cleanup--polish) | ⏳ Pending | — |

## Context

SongScribe's rendering and layout pipeline uses a mix of legacy pixel-based code and Gould/Ross rules. Three independent pixel constants all equal to 8 are scattered across StaffSpaces, LayoutStylesheet, and LayoutConstants. Layout and rendering code mixes pixel values throughout. This rewrite normalizes the coordinate system to staff-space units, ports algorithms from abc2svg, replaces the collision detection model, and restructures the renderer architecture.

**Spec:** [docs/specs/rendering-rewrite.md](../../../docs/specs/rendering-rewrite.md)
**Source:** abc2svg by Jean-Francois Moine (LGPL v3, compatible with GPL v3)

## Architectural Decisions

- **Coordinate unit**: Staff spaces throughout all layout and rendering code. Pixel conversion only at the render boundary via Graphics2D scale transform.
- **Scale factor**: Single mutable `pixelsPerStaffSpace` in `ScaleContext` (default 8.0). Changing this value scales everything (zoom integration point).
- **Render boundary**: Graphics2D transform set once in `LineComponent.render()`. All drawing downstream uses staff-space coordinates.
- **File format**: Version 2.1 stores all positions in staff-space units. FormatMigrator converts v2.0 pixel values on load.
- **Model purity**: Model objects (Note, Interval) remain pure data. No mutable rendering state. All computed geometry in LayoutResult.
- **Collision detection**: Y-extent arrays (y_get/y_set) with three-layer model replacing Java2D Area intersection.
- **Renderer architecture**: Self-rendering note-attached elements + LineRenderer methods for spans. RendererRegistry eliminated.
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

## ⏳ Phase 5: Lyrics + Line Height + Tuplets

Lyric collision avoidance via syllable width overflow walk (ported from abc2svg `ly_width()`). Lyric rendering below staff. Total line height calculation. Tuplet bracket rendering. Line justification (compress/stretch).

**Verification:** Full songs render completely. Lyrics don't overlap.

---

## ⏳ Phase 6: Legacy Decoration Flag Migration

Migrate all decoration data from legacy boolean/object flags on `StaffElement` to the new `Attachment`/`RangeElement` types. Currently both systems coexist: user actions, file I/O, export, and copy/paste use the legacy flags, while layout/rendering uses the new types with bridging code. This phase eliminates the dual representation so the new types become the single source of truth.

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

## ⏳ Phase 7: Cleanup + Polish

Remove RendererRegistry and BaseElementRenderer hierarchy. Remove Note.Properties mutable state. Remove all pixel-based constants from layout code. Verify all manual adjustment fields work in staff-space units. Performance profiling. Full regression testing.

- Consolidate `LayoutConstants` into `LayoutStylesheet` — both are final-constant bags in the same package serving the same purpose, with overlapping and conflicting values (e.g. `FERMATA_MARGIN_SS` 0.25 vs 0.5, `STAFF_HEIGHT_SS` in both). Merge into a single source of truth and update all importers.

**Deferred from Milestone 1:** Opening v2.0 format files causes a freeze on load. Investigate and fix the FormatMigrator v2.0 → v2.1 conversion path.

**Verification:** No visual regressions. Clean codebase with no legacy rendering code paths. v2.0 files load and migrate correctly.
