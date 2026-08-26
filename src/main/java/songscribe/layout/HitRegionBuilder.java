/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.layout;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.function.DoubleUnaryOperator;

import org.jspecify.annotations.Nullable;

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Articulation;
import songscribe.dom.Attachment;
import songscribe.dom.Attribution;
import songscribe.dom.Beam;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Ending;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.engraving.LineThickness;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;

/**
 * Turns a finished {@link LayoutResult} into the line's {@link HitRegistry}.
 * <p>
 * The whole content of this class is the mapping below — which layout map feeds which target
 * kind — so it is written out rather than left to be inferred from a column of similar-looking
 * {@code add} calls:
 *
 * <pre>
 *   LAYOUT SOURCE                                 TARGET
 *
 *   Line elements + element columns          ──▶  Element
 *     (via ElementHitGeometry, expanded)
 *   LayoutResult.getLyricBoxes               ──▶  Lyric
 *     (+ lyric row top, midline-relative)
 *   decorationLayouts ▸ Hairpin              ──▶  Hairpin
 *   decorationLayouts ▸ Ending               ──▶  Ending
 *   LayoutResult.getSlideLayout ▸ glissando  ──▶  Slide / GraceGlissando
 *     (four-point strip)
 *   LayoutResult.getSlideLayout ▸ fall       ──▶  Slide / GraceGlissando
 *     (glyph bounding box)
 *   decorationLayouts ▸ Articulation         ──▶  Articulation
 *   decorationLayouts ▸ five Attachment      ──▶  Attachment
 *     subtypes, each registered explicitly
 *   decorationLayouts ▸ Trill                ──▶  Trill
 *     (X narrowed to the drawn ink:
 *      tr glyph ∪ wavy line)
 *   decorationLayouts ▸ Tuplet               ──▶  Tuplet
 *     (number ink box, plus the sloped
 *      bracket quad when bracketed)
 *   NoteGeometry.getAccidentalBoundsSs       ──▶  Accidental
 *     (+ note center, midline-relative)
 *   LayoutResult.TieLayout control points    ──▶  Tie
 *     (control-point bounding box)
 *   LayoutResult.BeamLayout ▸ stems          ──▶  Beam
 *     (whole-group bounding box)
 *   decorationLayouts ▸ Attribution          ──▶  Attribution
 *     (first line only; drawn box)
 *   staff header extent — no layout map      ──▶  StaffLine
 * </pre>
 *
 * <p>How each region resolves against the ones it overlaps, and whether the mouse-move query
 * scans it, are properties of the target kind and are declared on {@link HitTarget}. Nothing
 * here chooses either.
 *
 * <p><b>Registration happens at layout, never at render.</b> Everything here is built from data
 * the layout pipeline has already produced, so a line's hit geometry exists from the moment its
 * layout does. Building it during a paint pass — which is what the slide geometry cache used to
 * do — means a hit test before the first paint answers nothing, and the registry has to be
 * invalidated on every repaint, including repaints caused by selection itself.
 *
 * <p>Coordinates are layout space throughout: X in line-local staff spaces, Y in staff spaces
 * relative to the staff midline, positive downward. Decoration and slide geometry already arrive
 * in that space; the lyric boxes do not, and are converted once, here.
 */
public final class HitRegionBuilder {

    /**
     * Vertical reach of the staff-line hit region on each side of the middle line, in staff
     * spaces.
     */
    private static final double STAFF_HIT_RADIUS_SS = 2.0;

    /**
     * Full width of a glissando's hit strip, in pixels — deliberately wider than the drawn line
     * so a thin diagonal stays easy to click.
     * <p>
     * A pixel constant is safe to bake into layout geometry because {@link DocumentScale} is a
     * fixed document scale that does not vary with on-screen zoom.
     */
    private static final double SLIDE_HIT_THICKNESS_PX = 8.0;

    /**
     * How far a rectangular hit region's right and bottom edges reach past the extent they name,
     * in pixels. See {@link #addRegion} for why they have to.
     */
    static final double INCLUSIVE_EDGE_MARGIN_PX = 1.0;

    private HitRegionBuilder() {
    }

    /**
     * Builds the hit registry for one line.
     *
     * @param line               the line being laid out
     * @param layoutResult       that line's finished layout — every map this reads must already
     *                           be populated
     * @param lyricRenderMetrics song-wide lyric metrics, for the lyric row's height
     * @return the registry, or {@link HitRegistry#EMPTY} if the line has nothing clickable
     */
    public static HitRegistry build(
        Line line,
        LayoutResult layoutResult,
        LyricRenderMetrics lyricRenderMetrics) {

        var builder = HitRegistry.builder();

        addElements(line, layoutResult, builder);
        addLyrics(line, layoutResult, lyricRenderMetrics, builder);
        addHairpins(layoutResult, builder);
        addEndings(layoutResult, builder);
        addSlides(line, layoutResult, builder);
        addArticulations(layoutResult, builder);
        addAttachments(layoutResult, builder);
        addTrills(layoutResult, builder);
        addTuplets(line, layoutResult, builder);
        addAccidentals(line, layoutResult, builder);
        addTies(line, layoutResult, builder);
        addBeams(line, layoutResult, builder);
        addAttribution(layoutResult, builder);
        addStaffLine(line, builder);

        return builder.build();
    }

    // ==========================================================================
    // Registration
    // ==========================================================================

    /**
     * Registers each note head's expanded hit rect, including the song's auto-maintained
     * terminal element, so an annotation can be attached to it (issue #713).
     */
    private static void addElements(
        Line line,
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);
            var rectSs = new Rectangle2D.Double();
            ElementHitGeometry.elementHitRectSs(
                layoutResult.getElementXSs(element), element, rectSs, true);
            addRegion(builder, rectSs, new HitTarget.Element(element));
        }
    }

    /**
     * Registers each laid-out lyric syllable box.
     */
    private static void addLyrics(
        Line line,
        LayoutResult layoutResult,
        LyricRenderMetrics lyricRenderMetrics,
        HitRegistry.Builder builder) {

        var rowHeightSs = lyricRenderMetrics.lyricBoxHeightSs();

        // The lyric boxes are the one geometry source in line-top origin. paintAboveMidlineSs()
        // is the distance from the top of the line's component to the staff midline, so
        // subtracting it puts the row in the midline-relative space every other region uses.
        var rowTopYSs = layoutResult.lyricAreaBaseYSs() - layoutResult.paintAboveMidlineSs();

        for (var element : line.getElements()) {
            for (var box : layoutResult.getLyricBoxes(element)) {
                var rectSs = new Rectangle2D.Double(box.xSs(), rowTopYSs, box.widthSs(), rowHeightSs);
                addRegion(builder, rectSs, new HitTarget.Lyric(element, box.verseIndex()));
            }
        }
    }

    private static void addHairpins(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.<Hairpin>getDecorationLayoutsByType(Hairpin.class)) {
            addRegion(builder, decorationHitRectSs(entry.getValue()), new HitTarget.Hairpin(entry.getKey()));
        }
    }

    private static void addEndings(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.<Ending>getDecorationLayoutsByType(Ending.class)) {
            addRegion(builder, decorationHitRectSs(entry.getValue()), new HitTarget.Ending(entry.getKey()));
        }
    }

    /**
     * Registers every note carrying drawable slide geometry: a glissando as a strip along the
     * drawn line, a fall as its glyph's bounding box.
     * <p>
     * A grace note's slide is registered too, as {@link HitTarget.GraceGlissando}. It is not
     * selectable, but the click has to be answered with an explanation rather than swallowed.
     */
    private static void addSlides(
        Line line,
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        // The auto-maintained terminal barline can own no slide, so the effective count is enough.
        for (var elementIndex = 0; elementIndex < line.effectiveElementCount(); elementIndex++) {
            var note = line.getElement(elementIndex);
            var slideLayout = layoutResult.getSlideLayout(note);

            if (slideLayout == null) {
                continue;
            }

            var target = slideTarget(note);
            var fallBoundsSs = slideLayout.fallBounds();

            // A fall is one glyph that cannot overlap anything else, so its drawn box is exact.
            if (fallBoundsSs != null) {
                addRegion(builder, fallBoundsSs, target);
                continue;
            }

            var glissando = slideLayout.glissando();

            if (glissando == null) {
                continue;
            }

            addRegion(builder, glissandoStripSs(glissando), target);
        }
    }

    /** Registers each articulation's expanded hit rect. */
    private static void addArticulations(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.<Articulation>getDecorationLayoutsByType(Articulation.class)) {
            addRegion(builder, decorationHitRectSs(entry.getValue()), new HitTarget.Articulation(entry.getKey()));
        }
    }

    /**
     * Registers each attached decoration's drawn box, with no margin added.
     * <p>
     * Attachments are the largest hit targets in the score, so widening them costs far more than
     * it buys: every staff space added below one is a staff space in which the note it is attached
     * to, or a marking stacked under it, cannot be clicked. The margin belongs to the thin
     * decorations that would otherwise be fiddly — see {@link #decorationHitRectSs}.
     * <p>
     * {@code Attachment} has five concrete subtypes; each is registered explicitly rather than
     * by its abstract base, because {@code getDecorationLayoutsByType(Attachment.class)} would
     * silently include only whichever subtypes happen to have layouts today and miss a new one
     * added later. {@code MetronomeAttachment} itself is abstract and never attached directly, so
     * only its two concrete subtypes, {@link TempoChangeAttachment} and {@link BeatChangeAttachment},
     * are registered.
     */
    private static void addAttachments(LayoutResult layoutResult, HitRegistry.Builder builder) {
        addAttachmentsOfType(FermataAttachment.class, layoutResult, builder);
        addAttachmentsOfType(DynamicAttachment.class, layoutResult, builder);
        addAttachmentsOfType(AnnotationAttachment.class, layoutResult, builder);
        addAttachmentsOfType(TempoChangeAttachment.class, layoutResult, builder);
        addAttachmentsOfType(BeatChangeAttachment.class, layoutResult, builder);
    }

    private static <T extends Attachment> void addAttachmentsOfType(
        Class<T> type,
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        for (var entry : layoutResult.<T>getDecorationLayoutsByType(type)) {
            addRegion(builder, decorationInkRectSs(entry.getValue()), new HitTarget.Attachment(entry.getKey()));
        }
    }

    /**
     * Registers each trill: the {@code tr} glyph, plus the wavy line out to the end note when the
     * trill spans a range.
     * <p>
     * The X extent is taken from the drawn ink rather than from the stacker's reservation, which
     * starts at the note column and is padded by a glyph width at the right. The vertical extent
     * is the reserved band: the wavy line is drawn along the glyph's own baseline, so it adds
     * nothing below or above it.
     */
    private static void addTrills(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.<Trill>getDecorationLayoutsByType(Trill.class)) {
            var trill = entry.getKey();
            var anchor = trill.getAnchorElement();

            if (anchor == null) {
                continue;
            }

            var layout = entry.getValue();
            var glyphLeftXSs = Trill.glyphLeftXSs(
                layoutResult.getElementXSs(anchor), NoteGeometry.getNoteheadCenterXSs(anchor));
            var rightXSs = trillRightEdgeXSs(trill, anchor, glyphLeftXSs, layoutResult);
            var rectSs = new Rectangle2D.Double(
                glyphLeftXSs,
                layout.ySs(),
                rightXSs - glyphLeftXSs,
                layout.heightSs() + layout.marginSs());
            addRegion(builder, rectSs, new HitTarget.Trill(trill));
        }
    }

    /**
     * The right edge of a trill's ink: the end note's own right edge for a multi-note trill —
     * where {@code TrillRenderer} stops the wavy line — or the {@code tr} glyph's right edge for
     * a single-note trill, which draws no wavy line at all.
     */
    private static double trillRightEdgeXSs(
        Trill trill,
        StaffElement anchor,
        double glyphLeftXSs,
        LayoutResult layoutResult) {

        var endNote = trill.getEndElement();

        if (endNote == null || endNote == anchor) {
            return glyphLeftXSs + trill.getContentWidthSs();
        }

        return layoutResult.getElementXSs(endNote) + endNote.getType().getElementWidthSs();
    }

    /**
     * Registers each tuplet: the bracket together with its number, or — when the group is beamed
     * and no bracket is drawn — the number alone.
     * <p>
     * A bracket reaches past the notes it spans at both ends, so its X extent comes from the same
     * {@link Tuplet} edge helpers the renderer and the stacker use, not from the anchor-to-end run
     * the reservation records. A bare number is far narrower than that run, and registering the
     * run for it would make empty space above a beam select the tuplet.
     */
    private static void addTuplets(
        Line line,
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        for (var entry : layoutResult.<Tuplet>getDecorationLayoutsByType(Tuplet.class)) {
            var tuplet = entry.getKey();
            var shapeSs = tupletHitShapeSs(tuplet, line, layoutResult, entry.getValue());

            if (shapeSs == null) {
                continue;
            }

            addRegion(builder, shapeSs, new HitTarget.Tuplet(tuplet));
        }
    }

    /**
     * Registers each note's accidental as a sub-element hit rect, skipping notes with no
     * accidental and grace notes (neither has bounds — see {@link NoteGeometry#getAccidentalBoundsSs}).
     * <p>
     * {@code AccidentalBounds} is relative to the notehead glyph origin (X) and the note center
     * (Y), so both are added back in to land the rect in this builder's layout space.
     */
    private static void addAccidentals(
        Line line,
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);
            var boundsSs = NoteGeometry.getAccidentalBoundsSs(element);

            if (boundsSs == null) {
                continue;
            }

            var elementXSs = layoutResult.getElementXSs(element);
            var noteCenterYSs = NoteGeometry.noteStaffPositionToCoordinateSs(element.getStaffPosition(), 0);
            var rectSs = new Rectangle2D.Double(
                elementXSs + boundsSs.leftSs(),
                noteCenterYSs + boundsSs.topSs(),
                boundsSs.widthSs(),
                boundsSs.botSs() - boundsSs.topSs());
            addRegion(builder, rectSs, new HitTarget.Accidental(element));
        }
    }

    /**
     * Registers each tie's control-point bounding box.
     * <p>
     * The box is computed directly from the four curve-defining points — start, the two outer
     * control points, and end — rather than by building a {@link java.awt.geom.CubicCurve2D} and
     * calling {@code getBounds2D()}, which would return exactly this same control-point hull at
     * the cost of an allocation on every layout pass. The inner curve's control points are not
     * part of the computation: the inner curve lies inside the outer curve's hull, so it can
     * never widen the box.
     */
    private static void addTies(Line line, LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var tie : line.findTies()) {
            var tieLayout = layoutResult.getTieLayout(tie);

            if (tieLayout == null) {
                continue;
            }

            addRegion(builder, tieBoundsSs(tieLayout), new HitTarget.Tie(tie));
        }
    }

    /**
     * Registers one region per beam group: the bounding box of every beam line in the group, not
     * a separate shape per beam.
     */
    private static void addBeams(Line line, LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var beam : line.findSpans(Beam.class)) {
            var beamLayout = layoutResult.getBeamLayout(beam);

            if (beamLayout == null) {
                continue;
            }

            addRegion(builder, beamGroupRectSs(line, beam, beamLayout, layoutResult), new HitTarget.Beam(beam));
        }
    }

    /**
     * Registers the attribution block, which appears in exactly one line's decoration layouts —
     * the first, the only line {@code VerticalStackingCalculator.stackAttribution} places it on —
     * so the loop needs no line-index test.
     * <p>
     * The rect is the drawn box with nothing added: {@value Attribution#ATTRIBUTION_MARGIN_BOTTOM_SS}
     * staff spaces of blank air sit directly above the staff, and folding that into the clickable
     * area would take clicks aimed at the music.
     */
    private static void addAttribution(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.<Attribution>getDecorationLayoutsByType(Attribution.class)) {
            addRegion(builder, decorationInkRectSs(entry.getValue()), new HitTarget.Attribution());
        }
    }

    /**
     * Registers the staff line itself, which selects the whole line.
     * <p>
     * Deliberately reachable only from the header at the left, never by clicking a staff line
     * under the music: a click among the notes belongs to the notes.
     */
    private static void addStaffLine(Line line, HitRegistry.Builder builder) {
        var headerRightEdgeSs = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line);
        var rectSs = new Rectangle2D.Double(
            0,
            -STAFF_HIT_RADIUS_SS,
            headerRightEdgeSs,
            2 * STAFF_HIT_RADIUS_SS);
        addRegion(builder, rectSs, new HitTarget.StaffLine());
    }

    // ==========================================================================
    // Shapes
    // ==========================================================================

    /**
     * The hit rect of a thin decoration: its drawn box plus its bottom margin.
     * <p>
     * The margin is part of the clickable area for these kinds only — a hairpin, an ending or an
     * articulation is a thin marking, and the space reserved below it is the difference between
     * comfortable and fiddly. Attachments are large enough already and take the bare box; see
     * {@link #addAttachments}.
     */
    private static Rectangle2D.Double decorationHitRectSs(LayoutResult.DecorationLayout layout) {
        var rectSs = decorationInkRectSs(layout);
        rectSs.height += layout.marginSs();

        return rectSs;
    }

    /**
     * The drawn box of a decoration, with nothing added.
     * <p>
     * Every decoration reaching this point is flat, so {@code ySs} is the box top outright. The
     * tuplet bracket is the one decoration whose layout carries a slope, and it does not come
     * through here: an axis-aligned rect is the wrong shape for it — see {@link #tupletHitShapeSs}.
     */
    private static Rectangle2D.Double decorationInkRectSs(LayoutResult.DecorationLayout layout) {
        return new Rectangle2D.Double(
            layout.xSs(),
            layout.ySs(),
            layout.widthSs(),
            layout.heightSs());
    }

    /**
     * The hit shape of one tuplet, or {@code null} when its anchor or end note has no column — the
     * same incomplete-tuplet case {@code TupletRenderer} skips.
     * <p>
     * A number-only tuplet is its number's ink box and nothing else. A bracketed one is that box
     * plus the bracket, drawn as the quad through the four arm ends — top and bottom of the left
     * arm, top and bottom of the right arm. The quad is what a rectangle cannot be: the bracket
     * tilts, and a rect around a tilted bracket takes in the empty corner above the low end and
     * the empty corner below the high end, both of which are nowhere near the tuplet's ink.
     * <p>
     * The two pieces are subpaths of one shape rather than two registered regions, so that a click
     * resolves against a single area and a drag rectangle reports the tuplet once.
     */
    private static @Nullable Shape tupletHitShapeSs(
        Tuplet tuplet,
        Line line,
        LayoutResult layoutResult,
        LayoutResult.DecorationLayout layout) {

        var anchor = tuplet.getAnchorElement();
        var endNote = tuplet.getEndElement();

        if (anchor == null || endNote == null) {
            return null;
        }

        var anchorColumn = layoutResult.getElementColumn(anchor);
        var endColumn = layoutResult.getElementColumn(endNote);

        if (anchorColumn == null || endColumn == null) {
            return null;
        }

        // A bracket reaches past the notes it spans at both ends, so the edges come from the same
        // Tuplet helpers the renderer and the stacker use, not from the anchor-to-end run.
        var anchorXSs = layout.xSs();
        var leftEdgeXSs = Tuplet.bracketLeftEdgeXSs(anchorXSs, anchorColumn.hasStem(),
            anchor.getDirection().isUp(), LineThickness.STEM_SS, anchorColumn.getNoteheadWidthSs());
        var rightEdgeXSs = Tuplet.bracketRightEdgeXSs(
            anchorXSs + layout.widthSs(), endColumn.getNoteheadWidthSs());

        // The reserved box top is sloped, so it has to be evaluated at an X rather than read off
        // the layout: dySs is its rise over the anchor-to-end run, measured from the anchor X.
        // widthSs is always positive (Tuplet.getSpanWidthSs floors it at one staff space).
        var slope = layout.dySs() / layout.widthSs();
        DoubleUnaryOperator boxTopYAtSs = xSs -> layout.ySs() + slope * (xSs - anchorXSs);

        var numberRectSs = tupletNumberRectSs(tuplet, leftEdgeXSs, rightEdgeXSs, boxTopYAtSs);

        if (tuplet.isNumberOnly(line)) {
            return numberRectSs;
        }

        var bracketLineOffsetSs = Tuplet.bracketLineOffsetSs();
        var leftArmTopYSs = boxTopYAtSs.applyAsDouble(leftEdgeXSs) + bracketLineOffsetSs;
        var rightArmTopYSs = boxTopYAtSs.applyAsDouble(rightEdgeXSs) + bracketLineOffsetSs;

        // Wound left to right along the top and back along the bottom — the same direction the
        // appended number rect is wound in. The two subpaths overlap where the number hangs below
        // the bracket line, and opposite windings would cancel each other out there under the
        // non-zero rule, punching a hole through the middle of the tuplet.
        var shapeSs = new Path2D.Double();
        shapeSs.moveTo(leftEdgeXSs, leftArmTopYSs);
        shapeSs.lineTo(rightEdgeXSs, rightArmTopYSs);
        shapeSs.lineTo(rightEdgeXSs, rightArmTopYSs + Tuplet.BRACKET_ARM_HEIGHT_SS);
        shapeSs.lineTo(leftEdgeXSs, leftArmTopYSs + Tuplet.BRACKET_ARM_HEIGHT_SS);
        shapeSs.closePath();
        shapeSs.append(numberRectSs, false);

        return shapeSs;
    }

    /**
     * The ink box of a tuplet's number, bracketed or not.
     * <p>
     * The number is centered between the bracket edges either way, so the two edges locate it here
     * exactly as they locate it in the renderer. Its ink top is the reserved box top at that center
     * X: the number straddles the bracket line, which sits {@link Tuplet#bracketLineOffsetSs} — half
     * an ink height — below the box top.
     *
     * @param boxTopYAtSs the reserved box top as a function of X, following the bracket's slope
     */
    private static Rectangle2D.Double tupletNumberRectSs(
        Tuplet tuplet,
        double leftEdgeXSs,
        double rightEdgeXSs,
        DoubleUnaryOperator boxTopYAtSs) {

        var centerXSs = (leftEdgeXSs + rightEdgeXSs) / 2;
        var inkWidthSs = Tuplet.numberInkWidthSs(tuplet.getGrade());

        return new Rectangle2D.Double(
            centerXSs - inkWidthSs / 2,
            boxTopYAtSs.applyAsDouble(centerXSs),
            inkWidthSs,
            Tuplet.TUPLET_NUMBER_INK_HEIGHT_SS);
    }

    /**
     * The clickable strip along a glissando: the drawn segment widened by half the hit thickness
     * on each side, as a closed four-point path.
     * <p>
     * This is the only shape in the registry that is not an axis-aligned rectangle. A rotated
     * strip is what the old hit test computed the hard way, by rotating the click into the
     * segment's local frame and comparing against its length and half-thickness; expressing it
     * as a path lets the registry resolve it with the same {@code contains} call as everything
     * else.
     */
    private static Path2D.Double glissandoStripSs(SlideGeometry.Endpoints endpoints) {
        var halfHitSs = DocumentScale.pxToSs(SLIDE_HIT_THICKNESS_PX) / 2;
        var cos = Math.cos(endpoints.angle());
        var sin = Math.sin(endpoints.angle());

        // Perpendicular to the segment direction (cos, sin), scaled to half the strip width.
        var offsetXSs = -sin * halfHitSs;
        var offsetYSs = cos * halfHitSs;

        var startXSs = endpoints.startXSs();
        var startYSs = endpoints.startYSs();

        // Walked out from the start along the angle rather than read from the record's end point,
        // so the strip covers exactly the span the old local-frame test accepted: 0 ≤ localX ≤ length.
        var endXSs = startXSs + cos * endpoints.length();
        var endYSs = startYSs + sin * endpoints.length();

        var strip = new Path2D.Double();
        strip.moveTo(startXSs + offsetXSs, startYSs + offsetYSs);
        strip.lineTo(endXSs + offsetXSs, endYSs + offsetYSs);
        strip.lineTo(endXSs - offsetXSs, endYSs - offsetYSs);
        strip.lineTo(startXSs - offsetXSs, startYSs - offsetYSs);
        strip.closePath();

        return strip;
    }

    /**
     * The control-point bounding box of a tie's curve — start, the two outer control points, and
     * end. See {@link #addTies} for why the inner curve's control points are excluded.
     */
    private static Rectangle2D.Double tieBoundsSs(LayoutResult.TieLayout tieLayout) {
        return boundingBoxSs(
            tieLayout.startXSs(), tieLayout.startYSs(),
            tieLayout.cp1XSs(), tieLayout.cp1YSs(),
            tieLayout.cp2XSs(), tieLayout.cp2YSs(),
            tieLayout.endXSs(), tieLayout.endYSs());
    }

    /**
     * Registers {@code shapeSs}, extending a rectangle's right and bottom edges so the extent it
     * names is fully clickable.
     * <p>
     * {@code Rectangle2D.contains} is half-open: the left and top edges count as inside, the
     * right and bottom as outside. Coordinates fall between pixels, so a rect of width {@code w}
     * starting at {@code x} covers the pixels from {@code x} up to but not including
     * {@code x + w} — one column short of the glyph it was measured from, and one row short at
     * the bottom. Extending the far edges by a pixel restores that column and row.
     * <p>
     * Every registration goes through here so no rect can miss it. Non-rectangular shapes pass
     * through untouched: a closed path has no single pair of far edges to extend, and the two
     * that exist — a glissando strip and a sloped tuplet bracket — are generous shapes already.
     */
    private static void addRegion(HitRegistry.Builder builder, Shape shapeSs, HitTarget target) {
        builder.add(inclusiveOf(shapeSs), target);
    }

    /** @see #addRegion */
    private static Shape inclusiveOf(Shape shapeSs) {
        if (!(shapeSs instanceof Rectangle2D rectSs)) {
            return shapeSs;
        }

        var marginSs = DocumentScale.pxToSs(INCLUSIVE_EDGE_MARGIN_PX);

        return new Rectangle2D.Double(
            rectSs.getX(),
            rectSs.getY(),
            rectSs.getWidth() + marginSs,
            rectSs.getHeight() + marginSs);
    }

    /**
     * The smallest rectangle containing every given point.
     *
     * @param coordsSs the points as alternating x, y values in staff spaces; at least one point,
     *                 so an even count of at least two values
     */
    private static Rectangle2D.Double boundingBoxSs(double... coordsSs) {
        var boundsSs = new Rectangle2D.Double(coordsSs[0], coordsSs[1], 0, 0);

        for (var i = 2; i < coordsSs.length; i += 2) {
            boundsSs.add(coordsSs[i], coordsSs[i + 1]);
        }

        return boundsSs;
    }

    /**
     * The bounding box of a beam group's drawn quads, derived without walking
     * {@link songscribe.ui.renderer.BeamGroupRenderer}'s recursive per-sub-beam geometry.
     * <p>
     * The group's X extent runs from the leftmost to the rightmost stem's element X. Because the
     * beam's slope is constant across the whole group, the outer (tip-side) edge at any X between
     * those two is a straight line between {@code startYSs} at the left end and
     * {@code startYSs + slope * width} at the right end — so its extremes occur at the two ends,
     * and it is enough to evaluate there. The same is true of the innermost edge of the deepest
     * sub-beam, offset from the outer edge by a constant depth, so the two ends again bound
     * everything nested between them.
     * <p>
     * That depth — from the outermost beam's outer edge to the deepest sub-beam's inner edge — is
     * {@link LineThickness#BEAM_THICKNESS_SS} plus this group's {@code thickeningSs}, plus one
     * {@link LineThickness#beamTranslationSs} step per sub-beam level beyond the first, mirroring
     * how {@code BeamGroupRenderer.drawBeam} accumulates {@code innerBeamOffsetSs} per recursion
     * level. The depth extends toward the noteheads: downward when {@code stemsUp}, upward
     * otherwise.
     */
    private static Rectangle2D.Double beamGroupRectSs(
        Line line,
        Beam beam,
        LayoutResult.BeamLayout beamLayout,
        LayoutResult layoutResult) {

        var minXSs = Double.POSITIVE_INFINITY;
        var maxXSs = Double.NEGATIVE_INFINITY;

        for (var element : beamLayout.stems().keySet()) {
            var elementXSs = layoutResult.getElementXSs(element);
            minXSs = Math.min(minXSs, elementXSs);
            maxXSs = Math.max(maxXSs, elementXSs);
        }

        var widthSs = maxXSs - minXSs;
        var outerStartYSs = beamLayout.startYSs();
        var outerEndYSs = outerStartYSs + beamLayout.slope() * widthSs;

        var levelCount = BeamMath.beamLevel(line, beam.getAnchorElementIndex(), beam.getEndElementIndex()) + 1;
        var totalDepthSs = LineThickness.BEAM_THICKNESS_SS + beamLayout.thickeningSs()
            + (levelCount - 1) * LineThickness.beamTranslationSs(beamLayout.thickeningSs());
        var innerOffsetSs = beamLayout.stemsUp() ? totalDepthSs : -totalDepthSs;

        // The four corners: each end of the outer edge, and each end of the deepest inner edge.
        return boundingBoxSs(
            minXSs, outerStartYSs,
            maxXSs, outerEndYSs,
            minXSs, outerStartYSs + innerOffsetSs,
            maxXSs, outerEndYSs + innerOffsetSs);
    }

    private static HitTarget slideTarget(StaffElement note) {
        if (note.getType().isGraceNote()) {
            return new HitTarget.GraceGlissando(note);
        }

        return new HitTarget.Slide(note);
    }
}
