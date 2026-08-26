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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Song;

/**
 * Answers "will this key change still fit?" for the key-editing UI, so an edit that cannot be
 * drawn is refused <em>before</em> it is written to the model.
 * <p>
 * There is one query per edit, and each answers for the whole edit rather than for one line of it.
 * A key change claims horizontal space in four places, and a check that covered only some of them
 * would accept an edit that then overflows somewhere else:
 * <ul>
 *   <li>the <b>cautionary</b> at the end of the line before it, warning what the next line begins
 *       in, which widens that line's trailing reservation
 *       ({@link HorizontalSpacingCalculator#trailingReservationSs});</li>
 *   <li>the <b>header</b> of every line the change re-keys, which moves that line's first element
 *       right or left ({@link HorizontalSpacingCalculator#calculateFirstElementXSs});</li>
 *   <li>the <b>cautionary at the end of each of those lines</b>, since the key they leave off in
 *       moves with them;</li>
 *   <li>for a mid-line change, the <b>key signature column</b> itself — plus, where the chosen
 *       position has no barline before it, the barline the editor inserts alongside it.</li>
 * </ul>
 * <p>
 * "Every line the change re-keys" is the inheritance chain: a line with no key of its own inherits
 * from the line before it, so a change propagates forward and stops at the first line that
 * establishes its own key — the same rule {@code Song}'s inherited-key propagation follows. Each
 * query walks exactly that far.
 * <p>
 * Every line is measured by the identical solve the committed layout runs
 * ({@link ElementColumnBuilder} + {@link HorizontalSpacingCalculator#solveLine}), over the same
 * columns, so a change these queries accept is one the layout can always place. The committed
 * layout only discovers an overflow at paint time, and by then it can only place the line on its
 * collision floors and draw the tail clipped, in red (refs #696).
 *
 * <p><b>Deliberate divergence from {@link LyricEditFitCalculator}, decided by the domain owner.</b>
 * That class's callers first ask {@link LyricEditFitCalculator#lineFits} and let the edit through
 * when the line was <em>already</em> overflowing, so a user can still shorten a syllable to
 * recover. Key edits get no such escape, and this class deliberately exposes no query that would
 * grant one. The consequence, which is the cost of the decision: on an overflowing line the user
 * cannot simplify the key signature to recover either, even though a narrower signature would
 * help. A reader who checks the precedent and reads this as an oversight should not "fix" it.
 *
 * <p>Rejection covers these interactive edits only. A line that overflows on load, on a font
 * change, or from any non-key edit is still rendered overflowing rather than refused — refusing
 * would make a document unopenable.
 *
 * <p>Everything here is pure measurement: no line, element or column is ever mutated, and no
 * mutation is recorded.
 */
public final class KeyEditFitCalculator {

    private KeyEditFitCalculator() {
    }

    /**
     * Returns whether every line a key change touches still fits once {@code line} establishes
     * {@code key}.
     * <p>
     * The lines measured are the one before {@code line} (for the cautionary the change creates at
     * its end), {@code line} itself, and every following line that inherits from it. A line that
     * holds a mid-line key change still leaves off in that key change's key, so the chain stops
     * being re-keyed where the document says it stops.
     *
     * @param line               the line that would establish {@code key}
     * @param key                the key it would establish
     * @param lyricRenderMetrics metrics for measuring the affected lines' syllables
     * @return {@code true} when every affected line still solves feasibly against its song's line
     *         width
     */
    public static boolean lineKeyChangeFits(Line line, Key key, LyricRenderMetrics lyricRenderMetrics) {
        var columnBuilder = new ElementColumnBuilder(lyricRenderMetrics);

        return chainFits(line, columnBuilder.buildColumns(line), key, line.keyAtEndOfLineUnder(key), columnBuilder);
    }

    /**
     * Returns whether every line a mid-line key change touches still fits once a key change for
     * {@code key} is inserted into {@code line} at {@code insertionIndex}.
     * <p>
     * The barline the editor inserts when the chosen position has none is part of the measurement,
     * because it is part of the edit: {@link KeyChangeElement}'s position invariant puts a key
     * change immediately after a barline or repeat, and the editor keeps it there by inserting a
     * {@link ElementType#SINGLE_BARLINE} rather than by refusing the position. A check that left
     * that barline out would accept an edit that then overflows by exactly its width.
     * <p>
     * The spliced key change is measured against the key in effect at {@code insertionIndex - 1} on
     * the line as it stands, which is the key it will cancel: an insertion does not move the
     * elements before it, so that key is settled before the edit is applied. The line's header is
     * untouched — a mid-line change starts part-way along — but the key the line leaves off in
     * moves when the insertion is the last key change on it, and the following lines are then
     * re-keyed exactly as {@link #lineKeyChangeFits} describes.
     *
     * @param line               the line the key change would be inserted into
     * @param insertionIndex     the index the key change would land at; at least 1 and at most
     *                           {@link Line#effectiveElementCount()}, since a key change is
     *                           never the first element on a line
     * @param key                the key the inserted key change would establish
     * @param lyricRenderMetrics metrics for measuring the affected lines' syllables
     * @return {@code true} when every affected line still solves feasibly against its song's line
     *         width
     * @throws IndexOutOfBoundsException if {@code insertionIndex} is below 1 or above
     *                                   {@link Line#effectiveElementCount()}
     */
    public static boolean midLineKeyChangeInsertionFits(
        Line line, int insertionIndex, Key key, LyricRenderMetrics lyricRenderMetrics) {

        if (insertionIndex < 1 || insertionIndex > line.effectiveElementCount()) {
            throw new IndexOutOfBoundsException(
                "key change insertion index " + insertionIndex + " out of bounds [1, "
                    + line.effectiveElementCount() + ']');
        }

        var columnBuilder = new ElementColumnBuilder(lyricRenderMetrics);

        return chainFits(
            line,
            columnsWithKeySignature(line, insertionIndex, key, columnBuilder),
            line.getRunningKey(),
            line.keyAtEndOfLineUnder(insertionIndex, key),
            columnBuilder);
    }

    /**
     * Returns whether every line a mid-line key change touches still fits once the key change
     * already standing at {@code keyChangeIndex} on {@code line} establishes {@code key} instead of
     * the key it establishes now.
     * <p>
     * A swap, not an insertion, which is why {@link #midLineKeyChangeInsertionFits} is the wrong
     * measurement for it: that one measures the line with a column <em>added</em>, and would refuse
     * a swap for want of room the line already has. The key change's own column is measured against
     * the new key, so it may come out wider or narrower than the one it replaces; no barline is
     * ever part of this edit, because {@link KeyChangeElement}'s position invariant guarantees the
     * one in front of it is already there.
     * <p>
     * The line's header is untouched — a mid-line change starts part-way along — but the key the
     * line leaves off in moves when no later key change overrides it, and the following lines
     * are then re-keyed exactly as {@link #lineKeyChangeFits} describes.
     *
     * @param line               the line the key change stands on
     * @param keyChangeIndex     its index on {@code line}
     * @param key                the key it would establish instead
     * @param lyricRenderMetrics metrics for measuring the affected lines' syllables
     * @return {@code true} when every affected line still solves feasibly against its song's line
     *         width
     */
    public static boolean midLineKeyChangeSwapFits(
        Line line, int keyChangeIndex, Key key, LyricRenderMetrics lyricRenderMetrics) {

        var columnBuilder = new ElementColumnBuilder(lyricRenderMetrics);

        return chainFits(
            line,
            columnsWithSwappedKeySignature(line, keyChangeIndex, key, columnBuilder),
            line.getRunningKey(),
            line.keyAtEndOfLineUnder(keyChangeIndex + 1, key),
            columnBuilder);
    }

    /**
     * Runs the fit check over every line an edit touches: the line before the edited one, for the
     * cautionary the edit creates at its end; the edited line, with the columns and keys the edit
     * would leave it; and then forward along the inheritance chain, each line re-keyed by what the
     * one before it now leaves off in, stopping at the first line that establishes its own key.
     * <p>
     * A line the song does not hold has neither a line before it nor one after it, so the walk
     * measures it alone.
     *
     * @param editedLine    the line the edit lands on
     * @param editedColumns its columns as the edit would leave them
     * @param headerKey     the key its header would draw
     * @param endKey        the key it would leave off in
     * @param columnBuilder the builder every other affected line's columns come from
     */
    private static boolean chainFits(
        Line editedLine,
        List<ElementColumn> editedColumns,
        Key headerKey,
        Key endKey,
        ElementColumnBuilder columnBuilder) {

        var song = editedLine.getSong();
        var staffRightMarginSs = song.getLineWidthSs();
        var lineIndex = song.indexOfLine(editedLine);

        if (lineIndex > 0 && !cautionaryFits(
                song.getLine(lineIndex - 1), headerKey, columnBuilder, staffRightMarginSs)) {
            return false;
        }

        var currentLine = editedLine;
        var currentIndex = lineIndex;
        var currentColumns = editedColumns;
        var currentHeaderKey = headerKey;
        var currentEndKey = endKey;

        while (true) {
            var nextLine = lineAfter(song, currentIndex);
            var nextOwnKey = nextLine == null ? null : nextLine.getKey();
            Key nextRunningKey = null;

            if (nextLine != null) {
                // A line establishing its own key begins in it whatever precedes; one that inherits
                // begins in whatever this line now leaves off in.
                nextRunningKey = nextOwnKey != null ? nextOwnKey : currentEndKey;
            }

            if (!fits(
                    currentColumns,
                    currentLine,
                    new LineKeys(currentHeaderKey, currentEndKey, nextRunningKey),
                    staffRightMarginSs)) {
                return false;
            }

            // Propagation stops at the first line with a key of its own: its header does not move,
            // and nothing past it can either.
            if (nextLine == null || nextOwnKey != null) {
                return true;
            }

            currentIndex++;
            currentLine = nextLine;
            currentColumns = columnBuilder.buildColumns(nextLine);
            currentHeaderKey = currentEndKey;
            currentEndKey = nextLine.keyAtEndOfLineUnder(currentHeaderKey);
        }
    }

    /**
     * Returns whether {@code previousLine} still fits once its trailing reservation is widened for
     * a cautionary key signature warning that the line after it begins in {@code nextRunningKey}.
     * Its own header and the key it leaves off in are untouched by an edit on the line after it.
     */
    private static boolean cautionaryFits(
        Line previousLine, Key nextRunningKey, ElementColumnBuilder columnBuilder, double staffRightMarginSs) {

        var keys = LineKeys.of(previousLine);

        return fits(
            columnBuilder.buildColumns(previousLine),
            previousLine,
            new LineKeys(keys.headerKey(), keys.keyAtEndOfLine(), nextRunningKey),
            staffRightMarginSs);
    }

    /** A line with no columns has nothing a key signature can push against, so it always fits. */
    private static boolean fits(
        List<ElementColumn> columns, Line line, LineKeys keys, double staffRightMarginSs) {

        return columns.isEmpty()
            || !HorizontalSpacingCalculator.solveLine(columns, line, staffRightMarginSs, keys).isInfeasible();
    }

    /** The line after index {@code lineIndex}, or null at the last line or off the song entirely. */
    private static @Nullable Line lineAfter(Song song, int lineIndex) {
        if (lineIndex < 0 || lineIndex + 1 >= song.lineCount()) {
            return null;
        }

        return song.getLine(lineIndex + 1);
    }

    /**
     * Builds {@code line}'s columns as the editor would leave them with a key signature for
     * {@code key} spliced in at {@code insertionIndex} — the same columns
     * {@link ElementColumnBuilder} gives the committed layout, with the inserted ones (and the
     * barline the position may need) in place.
     */
    private static List<ElementColumn> columnsWithKeySignature(
        Line line, int insertionIndex, Key key, ElementColumnBuilder columnBuilder) {

        var elementCount = line.elementCount();
        var columns = new ArrayList<ElementColumn>(elementCount + 2);

        for (var index = 0; index < elementCount; index++) {
            if (index == insertionIndex) {
                appendInsertedColumns(columns, line, insertionIndex, key, columnBuilder);
            }

            columns.add(columnBuilder.buildColumn(line.getElement(index), line, index));
        }

        if (insertionIndex == elementCount) {
            appendInsertedColumns(columns, line, insertionIndex, key, columnBuilder);
        }

        return columns;
    }

    /** Appends the columns the editor's insertion adds: the key signature, behind any barline it needs. */
    private static void appendInsertedColumns(
        List<? super ElementColumn> columns,
        Line line,
        int insertionIndex,
        Key key,
        ElementColumnBuilder columnBuilder) {

        if (KeyChangeElement.needsBarlineBefore(line, insertionIndex)) {
            columns.add(columnBuilder.buildDetachedColumn(
                ElementType.SINGLE_BARLINE.newInstance(), line.getSong().getActiveVerse()));
        }

        columns.add(keySignatureColumn(line, insertionIndex, key, columnBuilder));
    }

    /**
     * Builds {@code line}'s columns as the editor would leave them with the key change standing
     * at {@code keyChangeIndex} establishing {@code key} instead — every other column exactly as
     * the committed layout builds it.
     *
     * @param line           the line as it stands, unmodified
     * @param keyChangeIndex the index of the key change whose key is being swapped
     * @param key            the key it would establish instead
     * @param columnBuilder  the builder the committed layout would use, so the projected columns
     *                       are measured on the same terms
     * @return the projected columns, in element order
     */
    private static List<ElementColumn> columnsWithSwappedKeySignature(
        Line line, int keyChangeIndex, Key key, ElementColumnBuilder columnBuilder) {

        var elementCount = line.elementCount();
        var columns = new ArrayList<ElementColumn>(elementCount);

        for (var index = 0; index < elementCount; index++) {
            columns.add(index == keyChangeIndex
                ? keySignatureColumn(line, keyChangeIndex, key, columnBuilder)
                : columnBuilder.buildColumn(line.getElement(index), line, index));
        }

        return columns;
    }

    /**
     * The column a key signature for {@code key} occupies at {@code index} on {@code line},
     * measured against the key in effect immediately before that index — the key it cancels.
     * <p>
     * The one measurement both edits need: an insertion does not move the elements before it and a
     * swap replaces one column in place, so in either case the cancelled key is settled on the
     * line as it stands.
     *
     * @param line          the line as it stands, unmodified
     * @param index         where the key signature would stand
     * @param key           the key it would establish
     * @param columnBuilder the builder to measure with
     * @return the column that key signature would occupy
     */
    private static ElementColumn keySignatureColumn(
        Line line, int index, Key key, ElementColumnBuilder columnBuilder) {

        return columnBuilder.buildDetachedColumn(
            KeyChangeElement.forMeasurement(key, line.keyAt(index - 1)),
            line.getSong().getActiveVerse());
    }
}
