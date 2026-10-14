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
import songscribe.engraving.BarStroke;
import songscribe.engraving.EngravingConstants;

/**
 * The cautionary key signature at the end of a line — the warning that the next line begins in a
 * different key. The layout that reserves room, the renderer and the hit test all read one answer
 * from here; a second derivation would fail silently.
 *
 * <p>A cautionary is a barline followed by a run of accidentals, the barline drawn only when the
 * line does not already end in a barline or a repeat. Padding either side of the accidentals is
 * {@link EngravingConstants#KEY_SIGNATURE_PADDING_SS}.
 *
 * <p>See {@code docs/key-changes.md}.
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
     * {@link AccidentalsOnly} carries no barline coordinate, so a second barline cannot be painted
     * over the line's own.
     */
    public sealed interface Placement {

        double accidentalsXSs();

        /**
         * @param barLineXSs     the left edge of that barline
         * @param accidentalsXSs the pen position the first accidental is drawn from
         */
        record WithBarLine(double barLineXSs, double accidentalsXSs) implements Placement {}

        /**
         * @param accidentalsXSs the pen position the first accidental is drawn from
         */
        record AccidentalsOnly(double accidentalsXSs) implements Placement {}
    }

    /**
     * @return {@code null} when {@code line} is the song's last line or the next line begins in
     *         the key {@code line} leaves off in
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

    private static boolean endsAtBarLine(StaffElement lastElement) {
        var type = lastElement.getType();
        return type.isBar();
    }

    /**
     * Never empty.
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
     * signature further from its barline than {@link EngravingConstants#KEY_SIGNATURE_PADDING_SS}.
     *
     * @return the span in staff spaces
     */
    public double reservationSs() {
        return leadInSs() + accidentalsWidthSs() + EngravingConstants.KEY_SIGNATURE_PADDING_SS;
    }

    /**
     * Returns where this cautionary's parts are drawn on a line whose content solved to
     * {@code layoutResult}.
     *
     * <p>Placement depends on whether the line's content fits the staff:
     * <ul>
     *   <li>On a line that fits, the run is right-aligned to {@code lineWidthSs} less
     *       {@link EngravingConstants#KEY_SIGNATURE_PADDING_SS}, and the barline sits that same
     *       padding ahead of it. Layout reserved exactly that span, so both clear the music.</li>
     *   <li>On a line whose {@link LayoutResult#overflowsStaffWidth() content overflows}, the margin
     *       is already behind the last element and pinning to it would drop the run on top of the
     *       music. The cautionary instead starts one lead-in past the rightmost element edge and
     *       extends the overflow. {@code LayoutEngine.positionTerminalFlushRight} skips an
     *       overflowing line for the same reason.</li>
     * </ul>
     *
     * @param overflowsStaffWidth whether the line's content
     *                            {@link LayoutResult#overflowsStaffWidth() overflows the staff}
     * @param contentRightEdgeSs  the line's {@link LayoutResult#contentRightEdgeSs() rightmost
     *                            solved column edge}
     * @param lineWidthSs         the width of the staff line
     * @return the coordinates to draw from, and the ones a hit test on the glyphs reads;
     *     a {@link Placement.WithBarLine} exactly when {@link #drawsBarLine()} is true
     */
    public Placement placeIn(boolean overflowsStaffWidth, double contentRightEdgeSs, double lineWidthSs) {
        double accidentalsXSs;

        if (overflowsStaffWidth) {
            accidentalsXSs = contentRightEdgeSs + leadInSs();
        } else {
            accidentalsXSs =
                lineWidthSs - EngravingConstants.KEY_SIGNATURE_PADDING_SS - accidentalsWidthSs();
        }

        if (!drawsBarLine) {
            return new Placement.AccidentalsOnly(accidentalsXSs);
        }

        return new Placement.WithBarLine(
            accidentalsXSs - EngravingConstants.KEY_SIGNATURE_PADDING_SS - BarStroke.THIN.widthSs(),
            accidentalsXSs);
    }

    private double leadInSs() {
        if (!drawsBarLine) {
            return EngravingConstants.KEY_SIGNATURE_PADDING_SS;
        }

        return lineRestSs
            + BarStroke.THIN.widthSs()
            + EngravingConstants.KEY_SIGNATURE_PADDING_SS;
    }
}
