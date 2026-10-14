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

package songscribe.ui.renderer;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.BarElement;
import songscribe.dom.BreathMarkElement;
import songscribe.dom.Direction;
import songscribe.dom.Dots;
import songscribe.dom.ElementType;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.NoteElement;
import songscribe.dom.RestElement;
import songscribe.dom.StaffElement;
import songscribe.engraving.BeamMetrics;
import songscribe.engraving.LedgerLine;
import songscribe.engraving.StemMetrics;
import songscribe.hit.HitTarget;
import songscribe.layout.Beaming;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

public final class StaffElementRenderer implements ElementRenderer<StaffElement> {
    // Half the beam thickness in ss, used to tuck beamed stems inside the beam
    // so they don't peek past the outer edge when the beam is angled.
    private static final double HALF_BEAM_THICKNESS_SS = BeamMetrics.BEAM_THICKNESS_SS / 2.0;

    // Stem end-cap arc diameter as a fraction of stem width (from LilyPond code analysis)
    private static final double STEM_ARC_RATIO = 0.615;

    private static final StaffElementRenderer INSTANCE = new StaffElementRenderer();

    private StaffElementRenderer() {
    }

    public static StaffElementRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        switch (element) {
            case RestElement rest ->
                RestRenderer.getInstance().render(invariants, frame, rest, g2);
            case BreathMarkElement breathMark -> renderBreathMark(breathMark, g2, invariants, frame);
            case BarElement bar -> BarRenderer.getInstance().render(invariants, frame, bar, g2);
            case KeyChangeElement keyChange ->
                KeySignatureRenderer.getInstance().renderMidLine(invariants, frame, keyChange, g2);
            case NoteElement note -> renderNote(note, g2, invariants, frame);
        }
    }

    private void renderNote(
        NoteElement element,
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        // Note: Don't set color here - respect the color set by the caller
        // (e.g., blue for insertion notes, black for song notes)
        try (var _ = GraphicsState.save(g2, TRANSFORM, FONT)) {
            var noteX = frame.resolveElementXSs(element, invariants);
            var noteY = RenderingUtils.noteStaffPositionToCoordinateSs(element.getStaffPosition().value(), invariants.getMiddleLineYSs());

            g2.translate(noteX, noteY);
            g2.setFont(RenderingUtils.MUSIC_FONT);

            renderNoteHead(g2, element, noteBeaming(element, invariants), invariants);
            renderLedgerLines(g2, element, invariants);
            renderAccidental(g2, element, invariants, frame);
        }
    }

    private void renderBreathMark(
        BreathMarkElement element,
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var noteX = frame.resolveElementXSs(element, invariants);

        // The glyph and its seat both come from the element's type, and the hit rect is built
        // from that same staff position, so the drawn glyph and its clickable area cannot
        // disagree.
        var breathY = NoteGeometry.noteStaffPositionToCoordinateSs(
            element.seatSp().value(), invariants.getMiddleLineYSs());

        RenderingUtils.drawBravuraGlyph(
            g2,
            element.glyph(),
            noteX,
            breathY,
            true
        );
    }

    private void renderNoteHead(
        Graphics2D g2,
        NoteElement note,
        Beaming beaming,
        LineInvariants invariants
    ) {
        var noteType = note.getType();
        var glyph = note.notehead().glyph();
        var direction = NoteGeometry.effectiveDirection(note);

        // Note: Don't set color here - respect the color set by the caller

        var noteHeadXPosSs = NoteGeometry.getNoteheadXOffsetSs(noteType, direction);

        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(noteType.isGraceNote() ? RenderingUtils.GRACE_NOTE_FONT : RenderingUtils.MUSIC_FONT);
            g2.drawString(glyph.asString(), noteHeadXPosSs, 0f);
        }

        // Beamed notes need their stems to connect to the beams.
        var stemTip = renderStem(g2, note, beaming, invariants);

        if (beaming == Beaming.UNBEAMED) {
            renderFlags(g2, note, stemTip);
        }

        if (!noteType.isGraceNote()) {
            renderDots(g2, note, beaming, direction);
        }
    }

    /**
     * @param g2 the graphics, translated to the note's origin
     * @return the flag attachment point (x = stem left edge, y = stem tip), or {@code null} when
     *     the note's type has no stem
     */
    private Point2D.@Nullable Double renderStem(
        Graphics2D g2,
        NoteElement note,
        Beaming beaming,
        LineInvariants invariants
    ) {
        var noteType = note.getType();

        if (!noteType.isNoteWithStem()) {
            return null;
        }

        var direction = NoteGeometry.effectiveDirection(note);

        var upper = direction.isUp();
        var geom = NoteGeometry.computeBaseStemGeometry(note, direction);
        var stemWidthSs = StemMetrics.thicknessSs();

        // Both stem directions keep their exact (sub-pixel) position. The score draws glyphs with
        // FRACTIONALMETRICS_ON (GraphicUtils.setRenderingHints), so the notehead's origin is NOT
        // snapped to an integer device pixel; rounding the stem to one would shift it off the
        // notehead edge it is meant to sit flush with, and drag beam ends — derived from the same
        // stem center — out of alignment with it.
        var stemLeftXSs = geom.stemLeftXSs();

        var layoutResult = invariants.getLayoutResult();
        var stemLayout = layoutResult.getStemLayout(note);
        var lengtheningSs = (stemLayout != null) ? stemLayout.lengtheningSs() : 0.0;
        var forcedShorteningSs = (stemLayout != null) ? stemLayout.forcedShorteningSs() : 0.0;
        var frenchShorteningLevels = (stemLayout != null) ? stemLayout.frenchShorteningLevels() : 0;

        var beamThickeningSs = 0.0;
        var beamed = beaming == Beaming.BEAMED;

        if (beamed) {
            var line = invariants.requireCurrentLine();
            var beam = line.spanRegistry().findBeamAt(line.getElementIndex(note));

            if (beam != null) {
                var beamLayout = layoutResult.getBeamLayout(beam);

                if (beamLayout != null) {
                    beamThickeningSs = beamLayout.thickeningSs();
                }
            }
        }

        // Stem length is measured from notehead center (y=0), not from the anchor.
        // The anchor only determines where the stem visually attaches to the notehead.
        // For a beamed note the quanted beam geometry is authoritative — LilyPond's
        // quanting legitimately produces stems shorter than the forced-stem floor, and
        // lengthening them here would push the stem past the beam edge. For an unbeamed
        // note the forced-shortening formula already caps at the floor; the guard below
        // is defensive.
        var naturalStemLength = geom.lengthSs() + lengtheningSs - forcedShorteningSs;
        var stemLength = beamed
            ? naturalStemLength
            : Math.max(NoteGeometry.FORCED_STEM_FLOOR_SS, naturalStemLength);
        var anchorY = geom.anchorYSs();

        // For beamed notes, shorten the rendered stem by half the (thickened) beam
        // so it tucks inside the beam rather than peeking past the outer edge
        // when the beam is angled. The logical stem tip retains the full length
        // for beam positioning.
        //
        // French beaming pulls an inner stem back by one further beam translation
        // per beam that passes straight through it, so it stops at the center of
        // the innermost beam it carries instead of running out to the outer one.
        // The translation comes from the same helper BeamGroupRenderer steps its
        // inner beams by, so the stem lands on the beam at any thickening.
        var beamInsetSs = 0.0;

        if (beamed) {
            beamInsetSs = HALF_BEAM_THICKNESS_SS + beamThickeningSs / 2.0
                + frenchShorteningLevels * BeamMetrics.beamTranslationSs(beamThickeningSs);
        }

        double stemTipY;
        double drawTop;
        double drawBottom;
        var arcDiameter = stemWidthSs * STEM_ARC_RATIO;

        if (upper) {
            stemTipY = -stemLength;
            drawTop = -(stemLength - beamInsetSs);
            drawBottom = anchorY;
        } else {
            stemTipY = stemLength;
            drawTop = anchorY;
            drawBottom = stemLength - beamInsetSs;
        }

        g2.fill(new RoundRectangle2D.Double(
            stemLeftXSs, drawTop, stemWidthSs, drawBottom - drawTop,
            arcDiameter, arcDiameter));
        return new Point2D.Double(stemLeftXSs, stemTipY);
    }

    /**
     * Called only for an unbeamed note: a beamed one carries its beams instead.
     *
     * @param g2      the graphics, translated to the note's origin
     * @param stemTip the stem's flag attachment point as {@link #renderStem} answered it (x = stem
     *                left edge, y = stem tip), or {@code null} when the note has no stem
     */
    private void renderFlags(Graphics2D g2, NoteElement note, Point2D.@Nullable Double stemTip) {
        if (stemTip == null) {
            return;
        }

        var noteType = note.getType();
        var flagGlyph = noteType.getFlagGlyph(NoteGeometry.effectiveDirection(note));

        if (flagGlyph == null) {
            return;
        }

        // SMuFL flag glyphs have their origin at the left edge of the stem, so stemTip.x is
        // already the left edge.
        var flagX = (float) stemTip.x;
        var flagY = (float) stemTip.y;

        Font flagFont;

        if (noteType.isGraceNote()) {
            flagFont = RenderingUtils.GRACE_NOTE_FONT;
            flagX = (float) NoteGeometry.getGraceFlagOriginXSs(stemTip.x);
        } else {
            flagFont = RenderingUtils.MUSIC_FONT;
        }

        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(flagFont);
            g2.drawString(flagGlyph.asString(), flagX, flagY);
        }
    }

    private void renderDots(
        Graphics2D g2,
        NoteElement note,
        Beaming beaming,
        Direction direction
    ) {
        if (note.dots() == Dots.NONE) {
            return;
        }

        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            var dotStr = SMuFLGlyph.AUGMENTATION_DOT.asString();
            NoteGeometry.forEachDotPosition(
                note, beaming, direction, (dotX, yOffset) ->
                g2.drawString(dotStr, dotX.floatValue(), yOffset.floatValue()));
        }
    }

    private void renderLedgerLines(Graphics2D g2, NoteElement note, LineInvariants invariants) {
        if (!NoteGeometry.noteNeedsLedgerLines(note)) {
            return;
        }

        // The geometry is identical for all of a note's ledger lines; compute it once and apply only
        // the per-line accidental clamp inside the loop.
        var geometry = NoteGeometry.getLedgerLineGeometry(note);

        LedgerLine.forEachOffsetSs(note.getStaffPosition(), yOffsetSs -> {
            var extent = geometry.extentAtSs(yOffsetSs);
            RenderingUtils.drawLedgerLine(g2, extent.leftSs(), extent.rightSs(), yOffsetSs, invariants);
        });
    }

    /**
     * The ambient color the caller set is left alone unless the note's own color resolves to
     * something other than plain black: that color carries the insertion preview, the
     * grace-cancel red and the slide-preview highlight.
     */
    private void renderAccidental(
        Graphics2D g2,
        NoteElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var accidental = note.drawnAccidental();

        if (accidental == null) {
            return;
        }

        try (var _ = GraphicsState.save(g2, COLOR, FONT)) {
            var accidentalColor =
                invariants.colorFor(new HitTarget.Element(note), frame.currentElementIndex());

            if (!LineInvariants.isDefaultColor(accidentalColor)) {
                g2.setColor(accidentalColor);
            }

            // Grace-note accidentals are drawn at grace scale, matching the clearance reserved
            // by NoteColumnGeometry via getAccidentalStartXSs (the two must agree or the glissando
            // gap is computed against the wrong glyph size).
            g2.setFont(RenderingUtils.getGlyphFont(note));

            var startX = NoteGeometry.getAccidentalStartXSs(note);
            g2.drawString(accidental.glyph().asString(), startX, 0f);
        }
    }

    /**
     * The insertion preview is not an element of the line, so it belongs to no beam.
     */
    private Beaming noteBeaming(StaffElement note, LineInvariants invariants) {
        var line = invariants.requireCurrentLine();

        if (!line.contains(note)) {
            return Beaming.UNBEAMED;
        }

        return Beaming.of(line.spanRegistry().findBeamJoining(line.getElementIndex(note)));
    }
}
