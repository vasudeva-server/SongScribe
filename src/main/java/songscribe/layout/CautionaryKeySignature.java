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

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Key;
import songscribe.dom.KeySignatureExtent;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.StaffHeaderMetrics;

/**
 * The cautionary key signature at the end of a line — the warning to the performer that the next
 * line begins in a different key. This type answers the three questions the feature raises, so that
 * the layout that reserves room, the renderer that paints, and the hit test that finds the glyphs
 * all read one answer instead of each deriving their own. A second derivation would fail silently:
 * a hit target that drifts off the glyphs, or a reserved span the renderer does not fill.
 *
 * <p>A cautionary is a barline followed by a run of accidentals. Its parts sit like this, measured
 * from the ink of the line's last element to the right edge of the staff:
 *
 * <pre>
 *   … last element  [line rest]  |  [padding]  ♯♯  [padding] │ staff right edge
 * </pre>
 *
 * <p>The barline is the same one a mid-line key change stands behind
 * ({@code KeyChangeElement}'s position invariant), and it is drawn on the same rule: only when the
 * line does not already end in a barline or a repeat. A line that does ends in one barline, not
 * two, and the run of accidentals takes its padding from the barline the line already holds — which
 * is then the whole of the lead-in, since the line rest ahead of a drawn barline separates the
 * music from a barline that is not there.
 *
 * <p>The line rest is the song's own, which is what {@code HorizontalSpacingCalculator} gives a
 * mid-line barline, so music clears a barline by the same distance wherever the barline is. The
 * padding either side of the accidentals is {@link StaffHeaderMetrics#KEY_SIGNATURE_PADDING_SS} —
 * the same padding {@link HorizontalSpacingCalculator} puts between a mid-line key change and the
 * barline in front of it, so a key signature stands the same distance behind its barline wherever it
 * falls. The barline is {@link LineThickness#THIN_BARLINE_SS} wide — the same barline an element
 * draws.
 *
 * <p>See {@code docs/key-changes.md} for what a cautionary is and why it is stored nowhere.
 *
 * @param previousKey   the key the line leaves off in — the left-hand side of the change
 * @param nextKey       the key the following line begins in, never equal to {@code previousKey}
 * @param drawsBarLine  whether the cautionary draws a barline of its own, false when the line
 *                      already ends in a barline or a repeat
 * @param lineRestSs    the song's line rest, which separates the music from a barline the
 *                      cautionary draws for itself
 */
public record CautionaryKeySignature(
    Key previousKey,
    Key nextKey,
    boolean drawsBarLine,
    double lineRestSs
) {

    /**
     * Where a cautionary's parts are drawn, in the staff-space coordinates of the line.
     *
     * <p>A cautionary that stands behind the line's own trailing barline has no barline of its own
     * to place, and there is therefore no coordinate for one: {@link AccidentalsOnly} carries none.
     * A single record with a barline position that is meaningful only sometimes would hand a
     * caller a plausible coordinate landing inside the line's own barline, and a second barline
     * painted over the first is close enough to right to be missed.
     */
    public sealed interface Placement {

        /**
         * @return the pen position the first accidental is drawn from
         */
        double accidentalsXSs();

        /**
         * A cautionary that draws its own barline, because the line does not already end in one.
         *
         * @param barLineXSs     the left edge of that barline
         * @param accidentalsXSs the pen position the first accidental is drawn from
         */
        record WithBarLine(double barLineXSs, double accidentalsXSs) implements Placement {}

        /**
         * A cautionary standing behind the barline or repeat the line already ends in.
         *
         * @param accidentalsXSs the pen position the first accidental is drawn from
         */
        record AccidentalsOnly(double accidentalsXSs) implements Placement {}
    }

    /**
     * Returns the cautionary {@code line} ends in as the document stands, or {@code null} when it
     * ends in none.
     *
     * @param line the line whose end is being asked about
     * @return the cautionary, or {@code null} when {@code line} is the song's last line or the next
     *         line begins in the key {@code line} leaves off in
     */
    public static @Nullable CautionaryKeySignature of(Line line) {
        var elementCount = line.elementCount();
        var lastElement = elementCount == 0 ? null : line.getElement(elementCount - 1);

        return of(LineKeys.of(line), lastElement, line.getSong().getDefaultRestLengthSs());
    }

    /**
     * Returns the cautionary a line ending in {@code lastElement} draws under {@code keys}, or
     * {@code null} when it draws none.
     *
     * <p>This is the form a solve of projected keys takes — an edit measured before it is committed
     * ({@link KeyEditFitCalculator}), whose last column need not be the line's last element.
     *
     * @param keys        the keys to read the change off
     * @param lastElement the line's last element, or {@code null} when the line holds none, which
     *                    leaves the cautionary to draw its own barline
     * @param lineRestSs  the song's line rest
     * @return the cautionary, or {@code null} when {@code keys} names no next line or the next line
     *         begins in the key the line leaves off in
     */
    public static @Nullable CautionaryKeySignature of(
        LineKeys keys,
        @Nullable StaffElement lastElement,
        double lineRestSs
    ) {
        var nextRunningKey = keys.nextRunningKey();
        var keyAtEndOfLine = keys.keyAtEndOfLine();

        if (nextRunningKey == null || nextRunningKey == keyAtEndOfLine) {
            return null;
        }

        return new CautionaryKeySignature(
            keyAtEndOfLine,
            nextRunningKey,
            lastElement == null || !endsAtBarLine(lastElement),
            lineRestSs);
    }

    /** Whether {@code lastElement} is the barline a cautionary would otherwise draw for itself. */
    private static boolean endsAtBarLine(StaffElement lastElement) {
        var type = lastElement.getType();
        return type.isBarLine() || type.isRepeat();
    }

    /**
     * Returns the accidentals to draw, which is what a key signature between this pair of keys
     * draws anywhere rather than a second reading of the cancellation policy.
     *
     * @return the accidentals in drawing order, never empty — {@link #of} returns null rather than
     *     a cautionary between two equal keys, which is the only pair that draws nothing
     */
    public List<Key.DrawnAccidental> accidentals() {
        return extent().accidentals();
    }

    /**
     * Returns how wide the drawn run of accidentals is, excluding the padding either side of it.
     *
     * @return the width in staff spaces, always greater than zero
     */
    public double accidentalsWidthSs() {
        return extent().widthSs();
    }

    /** The change this cautionary warns of, which is what it draws and how wide it is. */
    private KeySignatureExtent extent() {
        return new KeySignatureExtent(previousKey, nextKey);
    }

    /**
     * Returns the span layout keeps clear past the right extent of the line's last column, so that
     * the whole cautionary — the lead-in, the barline it may draw, the accidentals and the trailing
     * padding — fits between that extent and the staff's right edge.
     *
     * <p>This is the whole of the line's trailing gap rather than a floor under it: the lead-in
     * already carries whatever separation the last element is owed, so taking the larger of this
     * and the ordinary line rest would push a narrow cautionary off its padding and leave the
     * signature further from its barline than {@link StaffHeaderMetrics#KEY_SIGNATURE_PADDING_SS}.
     *
     * @return the span in staff spaces
     */
    public double reservationSs() {
        return leadInSs() + accidentalsWidthSs() + StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS;
    }

    /**
     * Returns where this cautionary's parts are drawn on a line whose content solved to
     * {@code layoutResult}.
     *
     * <p>Placement depends on whether the line's content fits the staff:
     * <ul>
     *   <li>On a line that fits, the run is right-aligned to {@code lineWidthSs} less
     *       {@link StaffHeaderMetrics#KEY_SIGNATURE_PADDING_SS}, and the barline sits that same
     *       padding ahead of it. Layout reserved exactly that span, so both clear the music.</li>
     *   <li>On a line whose {@link LayoutResult#overflowsStaffWidth() content overflows}, the margin
     *       is already behind the last element and pinning to it would drop the run on top of the
     *       music. The cautionary instead starts one lead-in past the rightmost element edge and
     *       extends the overflow. {@code LayoutEngine.positionTerminalFlushRight} skips an
     *       overflowing line for the same reason.</li>
     * </ul>
     *
     * @param layoutResult the line's solved layout
     * @param lineWidthSs  the width of the staff line
     * @return the coordinates to draw from, and the ones a hit test on the glyphs reads;
     *     a {@link Placement.WithBarLine} exactly when {@link #drawsBarLine()} is true
     */
    public Placement placeIn(LayoutResult layoutResult, double lineWidthSs) {
        double accidentalsXSs;

        if (layoutResult.overflowsStaffWidth()) {
            accidentalsXSs = layoutResult.contentRightEdgeSs() + leadInSs();
        } else {
            accidentalsXSs =
                lineWidthSs - StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS - accidentalsWidthSs();
        }

        if (!drawsBarLine) {
            return new Placement.AccidentalsOnly(accidentalsXSs);
        }

        return new Placement.WithBarLine(
            accidentalsXSs - StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS - LineThickness.THIN_BARLINE_SS,
            accidentalsXSs);
    }

    /**
     * Returns the span from the ink of the line's last element to the first accidental. A cautionary
     * that draws its own barline separates it from the music by the song's line rest, then pads it
     * off the accidentals; one that stands behind the line's own trailing barline is that padding
     * alone, since the rest before that barline is already part of the line's own spacing.
     *
     * @return the span in staff spaces, always greater than zero
     */
    private double leadInSs() {
        if (!drawsBarLine) {
            return StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS;
        }

        return lineRestSs
            + LineThickness.THIN_BARLINE_SS
            + StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS;
    }
}
