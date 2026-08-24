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
import java.util.EnumMap;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.hit.HitTarget;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

/**
 * Renders notes (head, stem, flags, dots, accidentals, ledger lines).
 * <p>
 * Extracts rendering logic from FughettaRenderer for the new ElementRenderer system.
 * This renderer handles:
 * <ul>
 *   <li>Note heads (whole, half, filled)</li>
 *   <li>Stems (up or down based on note.getDirection())</li>
 *   <li>Flags (for non-beamed 8th, 16th, 32nd notes)</li>
 *   <li>Dots (for dotted notes)</li>
 *   <li>Accidentals (sharps, flats, naturals)</li>
 *   <li>Ledger lines (for notes above/below staff)</li>
 * </ul>
 */
public final class StaffElementRenderer implements ElementRenderer<StaffElement> {
    // ==========================================================================
    // Constants
    // ==========================================================================

    // Note heads by type
    private static final EnumMap<ElementType, SMuFLGlyph> NOTE_HEAD = new EnumMap<>(ElementType.class);

    static {
        NOTE_HEAD.put(ElementType.SEMIBREVE, SMuFLGlyph.NOTEHEAD_WHOLE);
        NOTE_HEAD.put(ElementType.MINIM, SMuFLGlyph.NOTEHEAD_HALF);
        NOTE_HEAD.put(ElementType.CROTCHET, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.QUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.DEMI_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.GRACE_QUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
    }


    // Half the beam thickness in ss, used to tuck beamed stems inside the beam
    // so they don't peek past the outer edge when the beam is angled.
    private static final double HALF_BEAM_THICKNESS_SS = LineThickness.BEAM_THICKNESS_SS / 2.0;

    // Stem end-cap arc diameter as a fraction of stem width (from LilyPond code analysis)
    private static final double STEM_ARC_RATIO = 0.615;

    // Singleton instance
    private static final StaffElementRenderer INSTANCE = new StaffElementRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private StaffElementRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static StaffElementRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the SMuFL glyph for a note type's head.
     */
    @Nullable
    public static SMuFLGlyph getNoteHeadGlyph(ElementType noteType) {
        return NOTE_HEAD.get(noteType);
    }

    /**
     * Returns the note head character string for a note type (Bravura codepoint).
     */
    @Nullable
    public static String getNoteHeadChar(ElementType noteType) {
        var glyph = NOTE_HEAD.get(noteType);
        return glyph != null ? glyph.asString() : null;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var elementType = element.getType();

        if (elementType.isRest()) {
            RestRenderer.getInstance().render(invariants, frame, element, g2);
            return;
        }

        if (elementType.isBarLine() || elementType.isRepeat()) {
            BarRenderer.getInstance().render(invariants, frame, element, g2);
            return;
        }

        if (elementType.isBreathMark()) {
            renderBreathMark(element, g2, invariants, frame);
            return;
        }

        if (element instanceof KeyChangeElement keyChange) {
            KeySignatureRenderer.getInstance().renderMidLine(invariants, frame, keyChange, g2);
            return;
        }

        renderNote(element, g2, invariants, frame);
    }

    // ==========================================================================
    // Note Rendering
    // ==========================================================================

    /** Draws a note or a grace note: its head, its ledger lines and its accidental. */
    private void renderNote(
        StaffElement element,
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        // Note: Don't set color here - respect the color set by the caller
        // (e.g., blue for insertion notes, black for song notes)
        try (var _ = GraphicsState.save(g2, TRANSFORM, FONT)) {
            var noteX = frame.resolveElementXSs(element, invariants);
            var noteY = RenderingUtils.noteStaffPositionToCoordinateSs(element.getStaffPosition(), invariants.getMiddleLineYSs());

            g2.translate(noteX, noteY);
            g2.setFont(RenderingUtils.MUSIC_FONT);

            var isBeamed = isNoteBeamed(element, invariants);
            renderNoteHead(g2, element, isBeamed, invariants);
            renderLedgerLines(g2, element, invariants);
            renderAccidental(g2, element, invariants, frame);
        }
    }

    // ==========================================================================
    // Breath Mark Rendering
    // ==========================================================================

    private void renderBreathMark(
        StaffElement element,
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var noteX = frame.resolveElementXSs(element, invariants);

        // Derived from the element's staff position — half a staff space above the top staff
        // line — rather than written out again here. The hit rect is built from that same staff
        // position, so deriving both from it is what keeps the clickable area on the glyph; the
        // two were once separate numbers and drifted a full staff space apart.
        var breathY = NoteGeometry.noteStaffPositionToCoordinateSs(
            element.getStaffPosition(), invariants.getMiddleLineYSs());

        RenderingUtils.drawBravuraGlyph(
            g2,
            SMuFLGlyph.BREATH_MARK_COMMA,
            noteX,
            breathY,
            true
        );
    }

    // ==========================================================================
    // Note Head Rendering
    // ==========================================================================

    private void renderNoteHead(
        Graphics2D g2,
        StaffElement note,
        boolean beamed,
        LineInvariants invariants
    ) {
        var noteType = note.getType();
        var glyph = NOTE_HEAD.get(noteType);

        if (glyph == null) {
            return;
        }

        var direction = NoteGeometry.effectiveDirection(note);

        // Note: Don't set color here - respect the color set by the caller

        // Adjust x position for lower stem notes
        var noteHeadXPosSs = NoteGeometry.getNoteheadXOffsetSs(noteType, direction);

        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(noteType.isGraceNote() ? RenderingUtils.GRACE_NOTE_FONT : RenderingUtils.MUSIC_FONT);
            g2.drawString(glyph.asString(), noteHeadXPosSs, 0f);
        }

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        var stemTip = renderStem(g2, note, direction, beamed, noteType, invariants);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note, direction, noteType, stemTip);
        }

        // Draw dots (grace notes don't have dots)
        if (!noteType.isGraceNote()) {
            renderDots(g2, note, beamed, direction);
        }
    }

    // ==========================================================================
    // Stem Rendering
    // ==========================================================================

    // Returns the flag attachment point (x = stem left edge, y = stem tip), or null if no stem.
    private Point2D.@Nullable Double renderStem(
        Graphics2D g2,
        StaffElement note,
        StaffElement.Direction direction,
        boolean beamed,
        ElementType noteType,
        LineInvariants invariants
    ) {
        if (!noteType.isNoteWithStem()) {
            return null;
        }

        var upper = direction.isUp();
        var geom = NoteGeometry.computeBaseStemGeometry(noteType, direction);
        var stemWidthSs = LineThickness.STEM_SS;

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

        if (beamed) {
            var line = invariants.requireCurrentLine();
            var beam = line.findBeamAt(line.getElementIndex(note));

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
                + frenchShorteningLevels * LineThickness.beamTranslationSs(beamThickeningSs);
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

    // ==========================================================================
    // Flag Rendering
    // ==========================================================================

    private void renderFlags(
        Graphics2D g2,
        StaffElement note,
        StaffElement.Direction direction,
        ElementType noteType,
        Point2D.@Nullable Double stemTip
    ) {
        if (stemTip == null) {
            return;
        }

        var flagGlyph = noteType.getFlagGlyph(direction);

        if (flagGlyph == null) {
            return;
        }

        // Position flag at the stem tip. SMuFL flag glyphs have their origin
        // at the left edge of the stem, so stemTip.x is already the left edge.
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

    // ==========================================================================
    // Dot Rendering
    // ==========================================================================

    private void renderDots(
        Graphics2D g2,
        StaffElement note,
        boolean beamed,
        StaffElement.Direction direction
    ) {
        if (note.getDotCount() == 0) {
            return;
        }

        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            var dotStr = SMuFLGlyph.AUGMENTATION_DOT.asString();
            NoteGeometry.forEachDotPosition(
                note, beamed, direction, (dotX, yOffset) ->
                g2.drawString(dotStr, dotX.floatValue(), yOffset.floatValue()));
        }
    }

    // ==========================================================================
    // Ledger Line Rendering
    // ==========================================================================

    private void renderLedgerLines(Graphics2D g2, StaffElement note, LineInvariants invariants) {
        if (!NoteGeometry.noteNeedsLedgerLines(note)) {
            return;
        }

        // The geometry is identical for all of a note's ledger lines; compute it once and apply only
        // the per-line accidental clamp inside the loop.
        var geometry = NoteGeometry.getLedgerLineGeometry(note);

        RenderingUtils.forEachLedgerLineYSs(note.getStaffPosition(), yOffsetSs -> {
            var extent = geometry.extentAtSs(yOffsetSs);
            RenderingUtils.drawLedgerLine(g2, extent.leftSs(), extent.rightSs(), yOffsetSs, invariants);
        });
    }

    // ==========================================================================
    // Accidental Rendering
    // ==========================================================================

    /**
     * Draws {@code note}'s accidental, if it has one.
     * <p>
     * An accidental is selectable in its own right, so it is the one part of a note that can
     * carry a color the rest of the note does not. The {@link GraphicsState} block keeps that
     * scoped: the color set here is restored before the caller draws anything else.
     * <p>
     * The ambient color the caller set is left alone unless the accidental itself resolves to
     * something other than plain black. That ambient color is what carries the insertion
     * preview, the grace-cancel red and the slide-preview highlight — a preview note is not on
     * the line and can never be selected, so overwriting its color unconditionally would draw
     * its accidental black while the rest of it is blue.
     */
    private void renderAccidental(
        Graphics2D g2,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return;
        }

        var accidentalGlyph = accidental.glyph();

        try (var _ = GraphicsState.save(g2, COLOR, FONT)) {
            var accidentalColor = invariants.colorFor(
                new HitTarget.Accidental(note), frame.currentElementIndex());

            if (!LineInvariants.isDefaultColor(accidentalColor)) {
                g2.setColor(accidentalColor);
            }

            // Grace-note accidentals are drawn at grace scale, matching the clearance reserved
            // by NoteColumnGeometry via getAccidentalStartXSs (the two must agree or the glissando
            // gap is computed against the wrong glyph size). The same scale feeds walkAccidentalGlyphs so
            // the pen advances track the scaled-down glyphs and the right edge sits flush against
            // the notehead padding.
            g2.setFont(RenderingUtils.getGlyphFont(note));
            var scale = NoteGeometry.getGlyphScale(note);

            var startX = NoteGeometry.getAccidentalStartXSs(note);
            NoteGeometry.walkAccidentalGlyphs(
                accidentalGlyph,
                note.isAccidentalInParentheses(),
                startX,
                scale,
                (glyph, x) -> g2.drawString(glyph.asString(), x, 0f));
        }
    }

    // ==========================================================================
    // Utility Methods
    // ==========================================================================

    private boolean isNoteBeamed(StaffElement note, LineInvariants invariants) {
        var line = invariants.requireCurrentLine();
        var noteIndex = line.getElementIndex(note);
        return line.findBeamAt(noteIndex) != null &&
            note.getType() != ElementType.GRACE_QUAVER;
    }
}
