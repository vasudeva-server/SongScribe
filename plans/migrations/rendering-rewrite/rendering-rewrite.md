# Rendering and Layout System Rewrite

## Status Dashboard

| Milestone | Description | Status | Sub-plan |
|-----------|-------------|--------|----------|
| 1 | [Coordinate System + Staff + Notes](#milestone-1-coordinate-system--staff--notes) | ✅ Done | [milestone-1-coordinate-system.md](milestone-1-coordinate-system.md) |
| 2 | [Beams + Stems](#milestone-2-beams--stems) | ⏳ Pending | [milestone-2-beams-stems.md](milestone-2-beams-stems.md) |
| 3 | [Ties + Glissandos](#milestone-3-ties--glissandos) | ⏳ Pending | — |
| 4 | [Vertical Stacking + All Decorations](#milestone-4-vertical-stacking--all-decorations) | ⏳ Pending | — |
| 5 | [Lyrics + Line Height + Tuplets](#milestone-5-lyrics--line-height--tuplets) | ⏳ Pending | — |
| 6 | [Cleanup + Polish](#milestone-6-cleanup--polish) | ⏳ Pending | — |

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

## Milestone 1: Coordinate System + Staff + Notes

Establish the staff-space coordinate system and get all basic note rendering working. This is the foundation everything else builds on.

**Verification:** Open an existing composition. Notes and staff should render correctly. Nothing above the staff renders yet. Lyrics don't render yet.

See [milestone-1-coordinate-system.md](milestone-1-coordinate-system.md) for detailed implementation plan.

---

## Milestone 2: Beams + Stems

Port beam algorithm from abc2svg `calculate_beam()` and `draw_beams()`. Hyperbolic slope dampening, flat beam snapping, beam thickening. Automatic stem direction from pitch. Automatic partial beam stub direction. Multi-level beams.

**Verification:** Beamed passages render with correct slopes. Compare against abc2svg output.

See [milestone-2-beams-stems.md](milestone-2-beams-stems.md) for detailed implementation plan.

---

## Milestone 3: Ties + Glissandos

Port tie algorithm from abc2svg `slur_out()`. Cubic Bezier replacing quadratic. Dynamic height scaling. Interior note collision avoidance. Filled lens shape rendering. Glissando rendering between notes.

**Verification:** Ties scale height with span distance. Short ties similar to before; long ties noticeably better.

---

## Milestone 4: Vertical Stacking + All Decorations

Y-extent array collision detection (YSTEP=128). Three-layer model. Tier 1: articulations. Tier 2: fermata, trill. Tier 3: dynamics hairpins, text dynamics (new), volta brackets. Tier 4: tempo, beat changes, annotations. Self-rendering note-attached elements. All manual offset adjustments working post-layout.

**Verification:** Stacked decorations don't overlap. Text dynamics render for the first time.

---

## Milestone 5: Lyrics + Line Height + Tuplets

Lyric collision avoidance via syllable width overflow walk (ported from abc2svg `ly_width()`). Lyric rendering below staff. Total line height calculation. Tuplet bracket rendering. Line justification (compress/stretch).

**Verification:** Full compositions render completely. Lyrics don't overlap.

---

## Milestone 6: Cleanup + Polish

Remove RendererRegistry and BaseElementRenderer hierarchy. Remove Note.Properties mutable state. Remove all pixel-based constants from layout code. Verify all manual adjustment fields work in staff-space units. Performance profiling. Full regression testing.

**Deferred from Milestone 1:** Opening v2.0 format files causes a freeze on load. Investigate and fix the FormatMigrator v2.0 → v2.1 conversion path.

**Verification:** No visual regressions. Clean codebase with no legacy rendering code paths. v2.0 files load and migrate correctly.
