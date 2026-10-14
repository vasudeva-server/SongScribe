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

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Articulation;
import songscribe.dom.Attachment;
import songscribe.dom.Attribution;
import songscribe.dom.Beam;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.NoteElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.engraving.BeamMetrics;
import songscribe.engraving.TupletMetrics;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;

/**
 * Turns a finished {@link LayoutResult} into the line's {@link HitRegistry}. How a region resolves
 * against the ones it overlaps is declared on {@link HitTarget}; nothing here chooses it.
 *
 * <p><b>Registration happens at layout, never at render</b>, so a hit test before the first paint
 * still answers.
 *
 * <p>Coordinates are layout space: X in line-local staff spaces, Y in staff spaces relative to the
 * staff midline, positive downward. The lyric boxes are converted into it once, here.
 */
public final class HitRegionBuilder {

    private static final double STAFF_HIT_RADIUS_SS = 2.0;

    /** Deliberately wider than the drawn line so a thin diagonal stays easy to click. */
    private static final double SLIDE_HIT_THICKNESS_PX = 8.0;

    /** How far a rectangular hit region's right and bottom edges reach past the extent they name. */
    static final double INCLUSIVE_EDGE_MARGIN_PX = 1.0;

    private HitRegionBuilder() {
    }

    /**
     * @param layoutResult that line's finished layout — every map this reads must already
     *                     be populated
     * @return {@link HitRegistry#EMPTY} if the line has nothing clickable
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
        addTuplets(layoutResult, builder);
        addTies(layoutResult, builder);
        addBeams(line, layoutResult, builder);
        addAttribution(layoutResult, builder);
        addStaffLine(line, builder);

        return builder.build();
    }

    /** Includes the song's auto-maintained terminal element, so an annotation can be attached to it. */
    private static void addElements(
        Line line,
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        for (var element : line.getElements()) {
            addRegion(builder, layoutResult.elementHitRectSs(element), new HitTarget.Element(element));
        }
    }

    private static void addLyrics(
        Line line,
        LayoutResult layoutResult,
        LyricRenderMetrics lyricRenderMetrics,
        HitRegistry.Builder builder) {

        for (var element : line.getElements()) {
            for (var box : layoutResult.getLyricBoxes(element)) {
                var rectSs = layoutResult.lyricBoxRectSs(box, lyricRenderMetrics);
                addRegion(builder, rectSs, new HitTarget.Lyric(element, box.script()));
            }
        }
    }

    private static void addHairpins(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.decorationLayouts().<Hairpin>getDecorationLayoutsByType(Hairpin.class)) {
            addRegion(builder, decorationHitRectSs(entry.getValue()), new HitTarget.Hairpin(entry.getKey()));
        }
    }

    private static void addEndings(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.decorationLayouts().endings().entrySet()) {
            addRegion(builder, decorationHitRectSs(entry.getValue()), new HitTarget.Ending(entry.getKey()));
        }
    }

    /** A grace note's slide is registered as {@link HitTarget.GraceGlissando}: not selectable, but answered. */
    private static void addSlides(
        Line line,
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        // The auto-maintained terminal barline can own no slide, so the effective count is enough.
        for (var elementIndex = 0; elementIndex < line.effectiveElementCount(); elementIndex++) {
            if (!(line.getElement(elementIndex) instanceof NoteElement note)) {
                continue;
            }

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

    private static void addArticulations(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.decorationLayouts().<Articulation>getDecorationLayoutsByType(Articulation.class)) {
            addRegion(builder, decorationHitRectSs(entry.getValue()), new HitTarget.Articulation(entry.getKey()));
        }
    }

    /**
     * Attachments take the bare box, with no margin: widening the largest targets in the score
     * would steal clicks from the notes under them. See {@link #decorationHitRectSs}.
     * <p>
     * Each concrete subtype is registered explicitly, because
     * {@code getDecorationLayoutsByType(Attachment.class)} would miss a subtype added later.
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

        for (var entry : layoutResult.decorationLayouts().<T>getDecorationLayoutsByType(type)) {
            addRegion(builder, entry.getValue().inkRectSs(), new HitTarget.Attachment(entry.getKey()));
        }
    }

    /** The layout's bounds are the drawn ink ({@link TrillGeometry#inkExtentSs}). */
    private static void addTrills(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.decorationLayouts().<Trill>getDecorationLayoutsByType(Trill.class)) {
            addRegion(builder, decorationHitRectSs(entry.getValue()), new HitTarget.Trill(entry.getKey()));
        }
    }

    /**
     * The X extent comes from the same {@link TupletGeometry} edge helpers the renderer and the
     * stacker use, not from the anchor-to-end run the reservation records: a bare number is far
     * narrower than that run.
     */
    private static void addTuplets(
        LayoutResult layoutResult,
        HitRegistry.Builder builder) {

        for (var entry : layoutResult.decorationLayouts().tuplets().entrySet()) {
            var tuplet = entry.getKey();
            addRegion(builder, tupletHitShapeSs(tuplet, layoutResult, entry.getValue()),
                new HitTarget.Tuplet(tuplet));
        }
    }

    /** The box is the hull of the outer curve's four points; the inner curve lies inside it. */
    private static void addTies(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var tieLayout : layoutResult.tieLayouts()) {
            addRegion(builder, tieBoundsSs(tieLayout), new HitTarget.Tie(tieLayout.placement().tie()));
        }
    }

    /** One region per beam group: the bounding box of every beam line in the group. */
    private static void addBeams(Line line, LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var beam : line.spanRegistry().findSpans(Beam.class)) {
            var beamLayout = layoutResult.getBeamLayout(beam);

            if (beamLayout == null) {
                continue;
            }

            addRegion(builder, beamGroupRectSs(beam, beamLayout, layoutResult), new HitTarget.Beam(beam));
        }
    }

    /**
     * The rect is the drawn box with nothing added: the blank air above the staff would take
     * clicks aimed at the music.
     */
    private static void addAttribution(LayoutResult layoutResult, HitRegistry.Builder builder) {
        for (var entry : layoutResult.decorationLayouts().<Attribution>getDecorationLayoutsByType(Attribution.class)) {
            addRegion(builder, entry.getValue().inkRectSs(), new HitTarget.Attribution());
        }
    }

    /**
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

    /**
     * The margin is part of the clickable area for thin markings only. Attachments take the bare
     * box; see {@link #addAttachments}.
     */
    private static Rectangle2D.Double decorationHitRectSs(LayoutResult.DecorationLayout layout) {
        var rectSs = layout.inkRectSs();
        rectSs.height += layout.marginSs();

        return rectSs;
    }

    /**
     * A number-only tuplet is its number's ink box. A bracketed one adds the quad through the four
     * arm ends, so a tilted bracket takes in no empty corners. The two pieces are subpaths of one
     * shape, so a click resolves against a single area and a drag reports the tuplet once.
     */
    private static Shape tupletHitShapeSs(
        Tuplet tuplet,
        LayoutResult layoutResult,
        LayoutResult.DecorationLayout.Sloped layout) {

        // A bracket reaches past the notes it spans at both ends, so the edges come from the frame
        // the renderer draws from, not from the anchor-to-end run.
        var frame = TupletGeometry.BracketFrame.of(tuplet, layout, layoutResult);
        var leftEdgeXSs = frame.leftEdgeXSs();
        var rightEdgeXSs = frame.rightEdgeXSs();
        var numberRectSs = tupletNumberRectSs(tuplet, frame);

        if (tuplet.isNumberOnly()) {
            return numberRectSs;
        }

        var leftArmTopYSs = frame.bracketLineYAtSs(leftEdgeXSs);
        var rightArmTopYSs = frame.bracketLineYAtSs(rightEdgeXSs);

        // Wound left to right along the top and back along the bottom — the same direction the
        // appended number rect is wound in. The two subpaths overlap where the number hangs below
        // the bracket line, and opposite windings would cancel each other out there under the
        // non-zero rule, punching a hole through the middle of the tuplet.
        var shapeSs = new Path2D.Double();
        shapeSs.moveTo(leftEdgeXSs, leftArmTopYSs);
        shapeSs.lineTo(rightEdgeXSs, rightArmTopYSs);
        shapeSs.lineTo(rightEdgeXSs, rightArmTopYSs + TupletMetrics.BRACKET_ARM_HEIGHT_SS);
        shapeSs.lineTo(leftEdgeXSs, leftArmTopYSs + TupletMetrics.BRACKET_ARM_HEIGHT_SS);
        shapeSs.closePath();
        shapeSs.append(numberRectSs, false);

        return shapeSs;
    }

    private static Rectangle2D.Double tupletNumberRectSs(Tuplet tuplet, TupletGeometry.BracketFrame frame) {
        var centerXSs = frame.centerXSs();
        var inkWidthSs = TupletGeometry.numberInkWidthSs(tuplet.getGrade());

        return new Rectangle2D.Double(
            centerXSs - inkWidthSs / 2,
            frame.boxTopYAtSs(centerXSs),
            inkWidthSs,
            TupletGeometry.TUPLET_NUMBER_INK_HEIGHT_SS);
    }

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
        // so the strip covers exactly the span 0 ≤ localX ≤ length in the glissando's local frame.
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

    private static Rectangle2D.Double tieBoundsSs(LayoutResult.TieLayout tieLayout) {
        return boundingBoxSs(
            tieLayout.startXSs(), tieLayout.startYSs(),
            tieLayout.cp1XSs(), tieLayout.cp1YSs(),
            tieLayout.cp2XSs(), tieLayout.cp2YSs(),
            tieLayout.endXSs(), tieLayout.endYSs());
    }

    /**
     * {@code Rectangle2D.contains} is half-open, so a rect covers one column and one row short of
     * the glyph it was measured from; the far edges are extended by {@link #INCLUSIVE_EDGE_MARGIN_PX}.
     * Every registration goes through here. Non-rectangular shapes pass through untouched.
     */
    private static void addRegion(HitRegistry.Builder builder, Shape shapeSs, HitTarget target) {
        builder.add(inclusiveOf(shapeSs), target);
    }

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
     * The depth is what {@link BeamMetrics#beamStackHeightSs(int, double)} measures, so this region
     * and the stack {@code BeamGroupRenderer.drawBeam} draws are read off one definition. It
     * extends toward the noteheads: downward when {@code stemsUp}, upward otherwise.
     */
    private static Rectangle2D.Double beamGroupRectSs(
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

        var line = beamLayout.line();
        var outerStartYSs = line.yAtSs(minXSs);
        var outerEndYSs = line.yAtSs(maxXSs);

        var totalDepthSs =
            BeamMetrics.beamStackHeightSs(beam.beamStackCount(), beamLayout.thickeningSs());
        var innerOffsetSs = beamLayout.direction().isUp() ? totalDepthSs : -totalDepthSs;

        return boundingBoxSs(
            minXSs, outerStartYSs,
            maxXSs, outerEndYSs,
            minXSs, outerStartYSs + innerOffsetSs,
            maxXSs, outerEndYSs + innerOffsetSs);
    }

    private static HitTarget slideTarget(NoteElement note) {
        if (note.getType().isGraceNote()) {
            return new HitTarget.GraceGlissando(note);
        }

        return new HitTarget.Slide(note);
    }
}
