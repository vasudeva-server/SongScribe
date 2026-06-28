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

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

import module java.desktop;

import java.util.EnumMap;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.smufl.GlyphAnchors;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLMetadata;
import songscribe.layout.NoteGeometry;
import songscribe.layout.NoteGeometry.StemGeometry;
import songscribe.util.GraphicsState;

/**
 * Renders notes (head, stem, flags, dots, accidentals, ledger lines).
 * <p>
 * Extracts rendering logic from FughettaRenderer for the new ElementRenderer system.
 * This renderer handles:
 * <ul>
 *   <li>Note heads (whole, half, filled)</li>
 *   <li>Stems (up or down based on note.isUpper())</li>
 *   <li>Flags (for non-beamed 8th, 16th, 32nd notes)</li>
 *   <li>Dots (for dotted notes)</li>
 *   <li>Accidentals (sharps, flats, naturals)</li>
 *   <li>Ledger lines (for notes above/below staff)</li>
 * </ul>
 */
public final class NoteRenderer implements ElementRenderer<StaffElement> {
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
    private static final double HALF_BEAM_THICKNESS_SS = Engraving.BEAM_THICKNESS_SS / 2.0;

    // Stem end-cap arc diameter as a fraction of stem width (from LilyPond print analysis)
    private static final double STEM_ARC_RATIO = 0.57;

    // Singleton instance
    private static final NoteRenderer INSTANCE = new NoteRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private NoteRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static NoteRenderer getInstance() {
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

    /**
     * Resolves the device-pixel-snapped X coordinate for a note, using the first
     * available source in priority order:
     * <ol>
     *   <li>Override X from context (insertion note preview)</li>
     *   <li>Layout result position (laid-out song notes)</li>
     *   <li>Note's own {@code xPos} (fallback)</li>
     * </ol>
     */
    private static double resolveNoteXSs(
        Graphics2D g2,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        double noteX;

        if (frame.hasOverrideElementX()) {
            noteX = frame.overrideElementXSs();
        } else {
            noteX = invariants.getLayoutResult().getElementXSs(note);
        }

        return noteX;
    }

    /**
     * Computes the base stem geometry for a note type and direction.
     * This is the shared anchor selection and positioning logic used by both
     * {@code NoteRenderer} (for drawing) and {@code SlideRenderer} (for area building).
     *
     * @param noteType The note type (determines anchor and stem length)
     * @param upper    true for stem-up, false for stem-down
     * @return The base stem geometry
     */
    public static StemGeometry computeBaseStemGeometry(ElementType noteType, boolean upper) {
        var isMinim = noteType == ElementType.MINIM;
        var isGrace = noteType.isGraceNote();

        GlyphAnchors.Anchor anchor;

        if (isGrace) {
            anchor = NoteGeometry.STEM_UP_SE_BLACK_SMALL;
        } else if (upper) {
            anchor = isMinim ? Engraving.NOTEHEAD_HALF_STEM_UP_SE : Engraving.NOTEHEAD_BLACK_STEM_UP_SE;
        } else {
            anchor = isMinim ? Engraving.NOTEHEAD_HALF_STEM_DOWN_NW : Engraving.NOTEHEAD_BLACK_STEM_DOWN_NW;
        }

        var anchorX = anchor.x();

        // Stem left edge: for up-stems, the anchor marks the RIGHT edge of the stem;
        // for down-stems, the anchor marks the LEFT edge but the notehead is shifted
        // left by STEM_WIDTH_SS/2, so we compensate.
        var stemLeftX = anchorX - (upper ? NoteGeometry.STEM_WIDTH_SS : NoteGeometry.STEM_WIDTH_SS / 2);
        var stemLength = isGrace ? Engraving.GRACE_NOTE_STEM_LENGTH_SS : Engraving.STEM_LENGTH_SS;

        return new StemGeometry(stemLeftX, anchor.y(), stemLength);
    }


    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var noteType = element.getType();

        // Delegate to specialized renderers for non-note types
        if (noteType.isRest()) {
            RestRenderer.getInstance().render(invariants, frame, element, g2);
            return;
        }

        if (noteType.isBarLine() || noteType.isRepeat()) {
            BarRenderer.getInstance().render(invariants, frame, element, g2);
            return;
        }

        if (noteType == ElementType.BREATH_MARK) {
            renderBreathMark(element, g2, invariants, frame);
            return;
        }

        // Standard note rendering (including grace notes)
        // Note: Don't set color here - respect the color set by the caller
        // (e.g., blue for insertion notes, black for song notes)
        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            var noteX = resolveNoteXSs(g2, element, invariants, frame);
            var noteY = RenderingUtils.noteStaffPositionToCoordinateSs(element.getStaffPosition(), invariants.getMiddleLineYSs());

            g2.translate(noteX, noteY);
            g2.setFont(RenderingUtils.MUSIC_FONT);

            var isBeamed = isNoteBeamed(element, invariants);
            renderNoteHead(g2, element, isBeamed, invariants);
            renderLedgerLines(g2, element, invariants);
            renderAccidental(g2, element);
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
        var noteX = resolveNoteXSs(g2, element, invariants, frame);

        // Place half a staff space above the top staff line
        var breathY = invariants.getMiddleLineYSs() - 2.5;

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

        // Grace notes always have stem up
        var upper = noteType.isGraceNote() || note.isUpper();

        // Note: Don't set color here - respect the color set by the caller

        // Adjust x position for lower stem notes
        var noteHeadXPosSs = NoteGeometry.getNoteheadXOffsetSs(noteType, upper);

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(noteType.isGraceNote() ? RenderingUtils.GRACE_NOTE_FONT : RenderingUtils.MUSIC_FONT);
            g2.drawString(glyph.asString(), noteHeadXPosSs, 0f);
        }

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        var stemTip = renderStem(g2, note, upper, beamed, noteType, invariants);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note, upper, noteType, stemTip);
        }

        // Draw dots (grace notes don't have dots)
        if (!noteType.isGraceNote()) {
            renderDots(g2, note, beamed, upper);
        }
    }

    // ==========================================================================
    // Stem Rendering
    // ==========================================================================

    // Returns the flag attachment point (x = stem left edge, y = stem tip), or null if no stem.
    private Point2D.@Nullable Double renderStem(
        Graphics2D g2,
        StaffElement note,
        boolean upper,
        boolean beamed,
        ElementType noteType,
        LineInvariants invariants
    ) {
        if (!noteType.isNoteWithStem()) {
            return null;
        }

        var geom = NoteGeometry.computeBaseStemGeometry(noteType, upper);
        var stemWidthSs = invariants.getLineThickness().stemSs();

        // A down-stem sits flush with the notehead's LEFT edge, which is the glyph
        // origin — and the font snaps that origin to an integer device pixel. The
        // anti-aliased stem fill would otherwise land between pixels, leaving the
        // down-stem one pixel left of the notehead. Snap its left edge to the same
        // device pixel. Snapping must happen in absolute (device) coordinates
        // because the graphics context has been translated to the note's position —
        // rounding in local coordinates won't align to actual screen pixels.
        //
        // An up-stem sits at the notehead's RIGHT edge, which is origin + advance —
        // a fractional device position, not an integer pixel. Snapping it would pull
        // the stem off the notehead and, since beams derive their endpoints from the
        // same un-snapped stem centre, drag the beam ends out of alignment too. So
        // up-stems keep their exact (sub-pixel) position and follow the notehead.
        double stemLeftXSs;

        if (upper) {
            stemLeftXSs = geom.stemLeftXSs();
        } else {
            stemLeftXSs = snapXToDevicePixel(g2, geom.stemLeftXSs());
        }

        var layoutResult = invariants.getLayoutResult();
        var stemLayout = layoutResult.getStemLayout(note);
        var lengtheningSs = (stemLayout != null) ? stemLayout.lengtheningSs() : 0.0;

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
        var stemLength = geom.lengthSs() + lengtheningSs;
        var anchorY = geom.anchorYSs();

        // For beamed notes, shorten the rendered stem by half the (thickened) beam
        // so it tucks inside the beam rather than peeking past the outer edge
        // when the beam is angled. The logical stem tip retains the full length
        // for beam positioning.
        var beamInsetSs = beamed
            ? HALF_BEAM_THICKNESS_SS + beamThickeningSs / 2.0
            : 0.0;

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
     * Snaps a local X coordinate to the nearest device-pixel boundary. Transforms
     * to absolute device space using the current graphics transform, rounds, and
     * inverse-transforms back to local (staff-space) coordinates. This keeps vertical
     * stems crisp and aligned with the font-snapped notehead regardless of the current
     * translation or Retina scaling.
     */
    private static double snapXToDevicePixel(Graphics2D g2, double localXSs) {
        var transform = g2.getTransform();
        var devicePt = new Point2D.Double();
        transform.transform(new Point2D.Double(localXSs, 0), devicePt);
        devicePt.x = Math.round(devicePt.x);

        try {
            transform.inverseTransform(devicePt, devicePt);
        } catch (NoninvertibleTransformException e) {
            return localXSs;
        }

        return devicePt.x;
    }

    // ==========================================================================
    // Flag Rendering
    // ==========================================================================

    private void renderFlags(
        Graphics2D g2,
        StaffElement note,
        boolean upper,
        ElementType noteType,
        Point2D.@Nullable Double stemTip
    ) {
        if (stemTip == null) {
            return;
        }

        var flagGlyph = noteType.getFlagGlyph(upper);

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

        try (var ignored = GraphicsState.save(g2, FONT)) {
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
        boolean upper
    ) {
        if (note.getDotCount() == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            var dotStr = SMuFLGlyph.AUGMENTATION_DOT.asString();
            NoteGeometry.forEachDotPosition(note, beamed, upper, (dotX, yOffset) ->
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

    private void renderAccidental(
        Graphics2D g2,
        StaffElement note
    ) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return;
        }

        var components = NoteGeometry.getAccidentalComponents(accidental);

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            // Grace-note accidentals are drawn at grace scale, matching the clearance reserved
            // by NoteColumnGeometry via getAccidentalStartXSs (the two must agree or the glissando
            // gap is computed against the wrong glyph size). The same scale feeds walkAccidentalGlyphs so
            // the pen advances track the scaled-down glyphs and the right edge sits flush against
            // the notehead padding.
            g2.setFont(RenderingUtils.getGlyphFont(note));
            var scale = NoteGeometry.getGlyphScale(note);

            var startX = NoteGeometry.getAccidentalStartXSs(note);
            NoteGeometry.walkAccidentalGlyphs(
                components,
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
