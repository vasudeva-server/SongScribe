package songscribe.layout;

import java.awt.geom.Rectangle2D;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Direction;
import songscribe.dom.Dots;
import songscribe.dom.ElementType;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Accidental;
import songscribe.engraving.LedgerLine;
import songscribe.engraving.Staff;
import songscribe.engraving.StemMetrics;
import songscribe.error.RuntimeError;
import songscribe.smufl.BBox;
import songscribe.smufl.Anchor;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StemmedNotehead;

/**
 * Geometry constants and stateless calculations for notes, so layout algorithms need not import
 * renderer classes.
 */
public final class NoteGeometry {

    // ==========================================================================
    // Stem Constants
    // ==========================================================================

    /** LilyPond's {@code shortening_step}, halved to ss. */
    public static final double FORCED_SHORTEN_PER_STEP_SS = 1.0 / 6.0;

    /** LilyPond's {@code 2·stem-shorten[0]}, halved to ss. */
    public static final double MAX_FORCED_SHORTEN_SS = 1.0;

    /** A forced stem never falls below this; for defensive flooring at the renderer. */
    public static final double FORCED_STEM_FLOOR_SS = StemMetrics.STEM_LENGTH_SS - MAX_FORCED_SHORTEN_SS;

    // ==========================================================================
    // Font Constants
    // ==========================================================================

    public static final float MUSIC_FONT_SIZE_SS = 4.0f;

    // ==========================================================================
    // Glissando Constants
    // ==========================================================================

    public static final double GLISSANDO_DRAWN_GAP_SS = 0.4;

    /**
     * Mirrors LilyPond's {@code BendAfter} padding. Shared by {@code SlideRenderer} (the drawn gap)
     * and {@code ElementColumnBuilder} (the layout reservation for a fall).
     */
    public static final double FALL_GAP_SS = 0.5;

    private static final double GLISSANDO_MIN_LENGTH_SS = 1.0;

    public static final double MIN_GLISSANDO_RESERVATION_SS =
        GLISSANDO_MIN_LENGTH_SS + 2 * GLISSANDO_DRAWN_GAP_SS;

    public static final Anchor GRACE_STEM_UP_SE = new Anchor(
        SMuFLMetadata.stemAnchors(StemmedNotehead.BLACK).stemUpSE().xSs()
            * ElementType.GRACE_NOTE_SCALE,
        SMuFLMetadata.stemAnchors(StemmedNotehead.BLACK).stemUpSE().ySs()
            * ElementType.GRACE_NOTE_SCALE
    );

    // ==========================================================================
    // Augmentation Dot Geometry
    // ==========================================================================

    // Augmentation dot Y shift (in ss) when the note sits on a staff line —
    // moves the dot into the space above the line.
    public static final double DOT_ON_LINE_Y_SHIFT_SS = -0.5;

    // Center-to-center spacing between consecutive augmentation dots (in ss): one dot glyph
    // plus a one-dot-width gap, following LilyPond's stack-by-one-dot-width convention.
    public static final double DOT_SPACING_SS = SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT) * 2;

    /**
     * The dot sits one augmentation-dot width right of the note's right-most obstruction: the
     * notehead, unless an unbeamed up-stem flag extends further right.
     */
    public static double firstDotXSs(NoteElement note, Beaming beaming, Direction direction) {
        var obstructionRightSs = SMuFLMetadata.bboxSs(note.notehead().glyph()).rightSs();

        if (direction.isUp()) {
            var flagBBox = flagBBoxLocalSs(note, Direction.UP, beaming);

            if (flagBBox != null) {
                obstructionRightSs = Math.max(obstructionRightSs, flagBBox.getMaxX());
            }
        }

        return obstructionRightSs + SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT);
    }

    public static void forEachDotPosition(
        NoteElement note, Beaming beaming, Direction direction,
        BiConsumer<? super Double, ? super Double> consumer
    ) {
        if (note.dots() == Dots.NONE) {
            return;
        }

        // Dots shift up when note is on a line (even staff position)
        var yOffset = note.getStaffPosition().isOnLine() ? DOT_ON_LINE_Y_SHIFT_SS : 0.0;

        var dotX = firstDotXSs(note, beaming, direction);

        for (var i = 0; i < note.dots().count(); i++) {
            consumer.accept(dotX, yOffset);
            dotX += DOT_SPACING_SS;
        }
    }

    /** Derived from the dot positions {@link #forEachDotPosition} feeds the renderer, so reserved space matches the ink. */
    public static double dotsRightExtentSs(
        NoteElement note, Beaming beaming, Direction direction, double baseRightSs
    ) {
        if (note.dots() == Dots.NONE) {
            return baseRightSs;
        }

        var dotRightSs = SMuFLMetadata.bboxSs(SMuFLGlyph.AUGMENTATION_DOT).rightSs();
        var maxRightSs = new double[]{baseRightSs};

        forEachDotPosition(note, beaming, direction, (dotX, yOffset) ->
            maxRightSs[0] = Math.max(maxRightSs[0], dotX + dotRightSs));

        return maxRightSs[0];
    }

    // ==========================================================================
    // Stem Geometry
    // ==========================================================================

    /** The <em>drawn</em> stem direction: a grace note is always drawn stem up, whatever it answers. */
    public static Direction effectiveDirection(NoteElement note) {
        return note.getType().isGraceNote() ? Direction.UP : note.direction();
    }

    /**
     * A stem is forced when the direction it answers opposes its
     * {@link NoteElement#naturalDirection()}, whoever turned it. An unbeamed auto stem is never
     * shortened, and neither is a grace stem.
     */
    public static double forcedShorteningSs(NoteElement note) {
        if (note.getType().isGraceNote() || note.direction() == note.naturalDirection()) {
            return 0.0;
        }

        var shortenSs = FORCED_SHORTEN_PER_STEP_SS * (1 + Math.abs(note.getStaffPosition().value()));
        return Math.min(MAX_FORCED_SHORTEN_SS, shortenSs);
    }

    /**
     * The length shared by the drawn stem ({@link #computeBaseStemGeometry}) and the column's baked
     * vertical extent ({@link ElementColumnBuilder#calculateStemTopSs}).
     */
    public static double stemLengthSs(ElementType noteType) {
        return noteType.isGraceNote()
            ? StemMetrics.GRACE_NOTE_STEM_LENGTH_SS
            : StemMetrics.STEM_LENGTH_SS;
    }

    /**
     * @param note must satisfy {@link ElementType#isNoteWithStem()}
     */
    public static StemGeometry computeBaseStemGeometry(NoteElement note, Direction direction) {
        var noteType = note.getType();
        var isGrace = noteType.isGraceNote();
        var anchor = isGrace ? GRACE_STEM_UP_SE : stemSideAnchor(note.stemmedNotehead(), direction);
        var anchorXSs = anchor.xSs();

        // Stem left edge: the anchor marks the stem's RIGHT edge for up-stems (back off a full
        // stem width) and its LEFT edge for down-stems (use the anchor directly). Both leave the
        // stem's outer edge flush with the notehead's outer edge, overlapping inward by its width.
        var stemLeftXSs = direction.isUp() ? anchorXSs - StemMetrics.thicknessSs() : anchorXSs;

        return new StemGeometry(stemLeftXSs, anchor.ySs(), stemLengthSs(noteType));
    }

    /** For a non-grace note; shared with {@link songscribe.ui.renderer.RenderingUtils#stemCenterXOffsetSs}. */
    public static Anchor stemSideAnchor(StemmedNotehead notehead, Direction direction) {
        var anchors = SMuFLMetadata.stemAnchors(notehead);

        return direction.isUp() ? anchors.stemUpSE() : anchors.stemDownNW();
    }

    /**
     * @param stemLeftXSs relative to notehead origin
     * @param lengthSs    without beam lengthening
     */
    public record StemGeometry(double stemLeftXSs, double anchorYSs, double lengthSs) {

        public double stemTipYSs(Direction direction) {
            return direction.isUp() ? anchorYSs - lengthSs : anchorYSs + lengthSs;
        }
    }

    // ==========================================================================
    // Accidental Constants
    // ==========================================================================

    /**
     * The accidental-to-notehead gap, shared by the renderer and the layout's reserved left extent
     * ({@code ElementColumnBuilder.calculateLeftExtentSs}).
     */
    public static final float ACCIDENTAL_PADDING_SS = 0.3375f; // 2.7px / 8 px/ss

    public static final float GRACE_ACCIDENTAL_PADDING_SS =
        ElementType.GRACE_NOTE_SCALE * ACCIDENTAL_PADDING_SS;

    private static final class AccidentalTables {
        static final float[] WIDTHS_SS = widthsSs();
        static final BBox[] BOUNDS_SS = boundsSs();

        private static float[] widthsSs() {
            var accidentals = Accidental.values();
            var widthsSs = new float[accidentals.length];

            for (var i = 0; i < accidentals.length; i++) {
                widthsSs[i] = (float) SMuFLMetadata.advanceWidthSs(accidentals[i].glyph());
            }

            return widthsSs;
        }

        private static BBox[] boundsSs() {
            var accidentals = Accidental.values();
            var boundsSs = new BBox[accidentals.length];

            for (var i = 0; i < accidentals.length; i++) {
                boundsSs[i] = computeAccidentalBounds(accidentals[i].glyph(), WIDTHS_SS[i]);
            }

            return boundsSs;
        }
    }

    private NoteGeometry() {}

    // ==========================================================================
    // Accidental Geometry
    // ==========================================================================

    public static float getAccidentalWidthSs(NoteElement note) {
        var accidental = note.drawnAccidental();

        if (accidental == null) {
            return 0;
        }

        var widthSs = AccidentalTables.WIDTHS_SS[accidental.ordinal()];

        return note.getType().isGraceNote() ? ElementType.GRACE_NOTE_SCALE * widthSs : widthSs;
    }

    /** The renderer and {@code NoteColumnGeometry} must share this, or the drawn glyphs and the glissando clearance drift apart. */
    public static float getAccidentalStartXSs(NoteElement note) {
        var paddingSs = note.getType().isGraceNote()
            ? GRACE_ACCIDENTAL_PADDING_SS
            : ACCIDENTAL_PADDING_SS;

        return -paddingSs - getAccidentalWidthSs(note);
    }

    /**
     * Returns {@code null} when the note has no accidental, or when it is a grace note:
     * grace-note accidentals are deliberately not reserved into the structural stacking layer.
     */
    public static @Nullable BBox getAccidentalBoundsSs(NoteElement note) {
        var accidental = note.drawnAccidental();

        if (accidental == null) {
            return null;
        }

        // Grace-note accidentals do not reserve structural stacking space (by design).
        if (note.getType().isGraceNote()) {
            return null;
        }

        return AccidentalTables.BOUNDS_SS[accidental.ordinal()];
    }

    // ==========================================================================
    // Notehead / Ledger Line Geometry
    // ==========================================================================

    /**
     * Pass {@code middleLineYSs = 0} to get the midline-relative layout-space Y; pass the line's
     * actual middle-line Y to get component space.
     */
    public static double noteStaffPositionToCoordinateSs(int staffPosition, double middleLineYSs) {
        return middleLineYSs + staffPosition * Staff.HALF_SPACE_SS;
    }

    /**
     * The X span of one ledger line, note-relative and right-positive: it reaches past the note
     * head on each side.
     */
    public record LedgerExtentSs(double leftSs, double rightSs) {}

    /**
     * Per-note ledger-line geometry, identical for all of a note's ledger lines: compute it once
     * via {@link #getLedgerLineGeometry} and call {@link #extentAtSs} per line. Note-relative, X
     * right-positive and Y down-positive.
     *
     * @param closestAccidentalSs bounds of the note's nearest accidental close enough to shorten a
     *     ledger line for, or {@code null} when the note has none
     */
    public record LedgerLineGeometry(
        LedgerExtentSs baseExtentSs,
        @Nullable BBox closestAccidentalSs,
        double headLeftSs
    ) {
        /**
         * Where the nearest accidental spans this ledger's Y, the left edge is pulled in to the
         * midpoint between the accidental and the notehead (LilyPond's per-head accidental
         * shortening), but only where that extends the extent inward.
         */
        public LedgerExtentSs extentAtSs(double yOffsetSs) {
            // closestAccidentalSs and yOffsetSs share the note-center, Y-down origin, so the closed
            // vertical-range test needs no coordinate conversion. The max is the only guard (LilyPond
            // left_shorten = max(dist - ledgerLeft, 0)); there is no accRight > ledgerLeft precondition.
            if (closestAccidentalSs == null
                || yOffsetSs < closestAccidentalSs.topSs()
                || yOffsetSs > closestAccidentalSs.bottomSs()) {
                return baseExtentSs;
            }

            var accidentalRightSs = closestAccidentalSs.rightSs();
            var ledgerLeftSs = Math.max(baseExtentSs.leftSs(), (accidentalRightSs + headLeftSs) / 2);

            return new LedgerExtentSs(ledgerLeftSs, baseExtentSs.rightSs());
        }
    }

    public static boolean noteNeedsLedgerLines(NoteElement note) {
        return note.getStaffPosition().needsLedgerLines();
    }

    /** Compute this once per note and reuse it across the note's ledger lines. */
    public static LedgerLineGeometry getLedgerLineGeometry(NoteElement note) {
        var noteType = note.getType();
        var bbox = SMuFLMetadata.bboxSs(note.notehead().glyph());
        var direction = effectiveDirection(note);
        var offsetSs = getNoteheadXOffsetSs(noteType, direction);
        var headLeftSs = offsetSs + bbox.leftSs();
        var headRightSs = offsetSs + bbox.rightSs();
        var widthSs = headRightSs - headLeftSs;
        var extensionSs = LedgerLine.LENGTH_FRACTION * widthSs;
        var baseExtentSs = new LedgerExtentSs(headLeftSs - extensionSs, headRightSs + extensionSs);

        return new LedgerLineGeometry(baseExtentSs, getAccidentalBoundsSs(note), headLeftSs);
    }

    public static LedgerExtentSs getLedgerLineBaseExtentSs(NoteElement note) {
        return getLedgerLineGeometry(note).baseExtentSs();
    }

    /** Callers drawing all of a note's ledger lines should reuse one {@link LedgerLineGeometry}. */
    public static LedgerExtentSs getLedgerLineExtentSs(NoteElement note, double yOffsetSs) {
        return getLedgerLineGeometry(note).extentAtSs(yOffsetSs);
    }

    /** Always zero: noteheads are drawn at the column X regardless of stem direction. */
    @SuppressWarnings("SameReturnValue")
    public static float getNoteheadXOffsetSs(ElementType noteType, Direction direction) {
        return 0f;
    }

    /** Anything centered over the notehead measures from here, not from the column X. */
    public static double getNoteheadCenterXSs(StaffElement note) {
        return note.getType().getElementCenterXSs();
    }

    /** The one place the centering is expressed, so a dynamic and an articulation over a note never drift apart. */
    public static Footprint centeredOverNotehead(ElementAtColumn<?> host, double widthSs) {
        var xSs = host.columnXSs() + getNoteheadCenterXSs(host.element()) - widthSs / 2.0;

        return new Footprint(xSs, widthSs);
    }

    /** Flag rendering and glissando column-extent computation must agree on where the grace flag sits. */
    public static double getGraceFlagOriginXSs(double stemLeftXSs) {
        return stemLeftXSs + StemMetrics.thicknessSs() * (1 - ElementType.GRACE_NOTE_SCALE) / 2;
    }

    /**
     * The single source of truth for where the flag sits, so spacing reservation,
     * augmentation-dot clearance and column geometry never drift apart. In the note's local
     * coordinate space.
     *
     * @return {@code null} when the note is beamed, stemless, or its type has no flag glyph
     */
    @Nullable
    public static Rectangle2D flagBBoxLocalSs(NoteElement note, Direction direction, Beaming beaming) {
        var noteType = note.getType();

        if (beaming == Beaming.BEAMED || !noteType.isNoteWithStem()) {
            return null;
        }

        var flagGlyph = noteType.getFlagGlyph(direction);

        if (flagGlyph == null) {
            return null;
        }

        var stemGeom = computeBaseStemGeometry(note, direction);
        var stemLeftXSs = stemGeom.stemLeftXSs();
        var stemTipYSs = stemGeom.stemTipYSs(direction);
        var flagBBox = SMuFLMetadata.bboxSs(flagGlyph);

        if (noteType.isGraceNote()) {
            var scale = (double) ElementType.GRACE_NOTE_SCALE;
            var flagOriginX = getGraceFlagOriginXSs(stemLeftXSs);

            return new Rectangle2D.Double(
                flagOriginX + flagBBox.leftSs() * scale,
                stemTipYSs + flagBBox.topSs() * scale,
                flagBBox.widthSs() * scale,
                flagBBox.heightSs() * scale);
        }

        return new Rectangle2D.Double(
            stemLeftXSs + flagBBox.leftSs(),
            stemTipYSs + flagBBox.topSs(),
            flagBBox.widthSs(),
            flagBBox.heightSs());
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    private static BBox computeAccidentalBounds(SMuFLGlyph accidentalGlyph, float totalWidthSs) {
        var startX = -ACCIDENTAL_PADDING_SS - totalWidthSs;

        return SMuFLMetadata.bboxSs(accidentalGlyph).translateXSs(startX);
    }
}
