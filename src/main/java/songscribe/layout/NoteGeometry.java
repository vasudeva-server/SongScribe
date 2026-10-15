package songscribe.layout;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Direction;
import songscribe.dom.ElementType;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.engraving.LedgerLine;
import songscribe.engraving.Staff;
import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLMetadata;

/**
 * Stateless calculations for a note's staff position, notehead and ledger lines, so layout
 * algorithms need not import renderer classes.
 */
public final class NoteGeometry {

    private NoteGeometry() {}

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
        var direction = StemGeometry.effectiveDirection(note);
        var offsetSs = getNoteheadXOffsetSs(noteType, direction);
        var headLeftSs = offsetSs + bbox.leftSs();
        var headRightSs = offsetSs + bbox.rightSs();
        var widthSs = headRightSs - headLeftSs;
        var extensionSs = LedgerLine.LENGTH_FRACTION * widthSs;
        var baseExtentSs = new LedgerExtentSs(headLeftSs - extensionSs, headRightSs + extensionSs);

        return new LedgerLineGeometry(baseExtentSs, AccidentalGeometry.getAccidentalBoundsSs(note), headLeftSs);
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

}
