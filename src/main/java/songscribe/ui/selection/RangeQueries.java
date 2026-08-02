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

package songscribe.ui.selection;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;

/**
 * What a {@link Selection.Range} may become: whether it can be beamed, tied, tupleted,
 * trilled, or have its stem direction changed.
 * <p>
 * Every answer here is a pure function of {@code (line, begin, end)} and nothing else, which
 * is why they are static and why they no longer live beside the selection field. Asking one
 * neither reads nor writes what is currently selected — pass any range and get that range's
 * answer.
 */
public final class RangeQueries {

    /** Two adjacent notes, with nothing between them. */
    private static final int TIE_SELECTION_SIZE_WITHOUT_SEPARATOR = 2;

    /** Two notes with a single non-duration element between them (refs #527). */
    private static final int TIE_SELECTION_SIZE_WITH_SEPARATOR = 3;

    private RangeQueries() {}

    /**
     * Returns the index of the first element in the range that is not a grace note,
     * or -1 if the range holds nothing but grace notes.
     *
     * <p>Grace notes are transparent to beams and tuplets: the group spans the range's
     * non-grace endpoints and any grace note in between stays outside the group, so a
     * grace/host pair may sit inside a beamed or tupleted range (refs #592).
     */
    public static int nonGraceBegin(Selection.Range range) {
        var line = range.line();

        for (var i = range.begin(); i <= range.end(); i++) {
            if (!line.getElement(i).getType().isGraceNote()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns the index of the last element in the range that is not a grace note,
     * or -1 if the range holds nothing but grace notes.
     *
     * @see #nonGraceBegin
     */
    public static int nonGraceEnd(Selection.Range range) {
        var line = range.line();

        for (var i = range.end(); i >= range.begin(); i--) {
            if (!line.getElement(i).getType().isGraceNote()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns whether the range can be beamed/unbeamed.
     */
    public static boolean canToggleBeaming(Selection.Range range) {
        var line = range.line();
        var beginIndex = nonGraceBegin(range);
        var endIndex = nonGraceEnd(range);

        // Fewer than two non-grace elements — there is nothing to join.
        if (beginIndex < 0 || beginIndex == endIndex) {
            return false;
        }

        //noinspection SimplifiableIfStatement
        if (!IntStream.rangeClosed(beginIndex, endIndex).allMatch(i -> {
            var type = line.getElement(i).getType();
            return type.isGraceNote() || type.isBeamable();
        })) {
            return false;
        }

        // Conflict: beaming would connect what a tie already connects.
        return !(shouldConnectBeamSelection(line, beginIndex, endIndex)
            && !shouldConnectTieSelection(line, beginIndex, endIndex));
    }

    /**
     * Returns whether {@code type} may sit between two tied notes.
     *
     * <p>Non-duration elements take no time, so the notes on either side stay adjacent in
     * the music even though an element separates them on the staff. A final double barline
     * is the exception: it ends the piece, so nothing may sound across it (refs #527).
     */
    private static boolean isTieSeparator(ElementType type) {
        return type.isNonDuration() && type != ElementType.FINAL_DOUBLE_BARLINE;
    }

    /**
     * Returns whether the range can toggle a tie.
     *
     * <p>A tie joins two notes of the same pitch, which may be adjacent or separated by a
     * single non-duration element such as a barline or repeat (refs #527).
     */
    public static boolean canToggleTie(Selection.Range range) {
        var selectionSize = range.size();

        if (selectionSize != TIE_SELECTION_SIZE_WITHOUT_SEPARATOR
            && selectionSize != TIE_SELECTION_SIZE_WITH_SEPARATOR) {
            return false;
        }

        var line = range.line();
        var begin = range.begin();
        var end = range.end();
        var beginNote = line.getElement(begin);
        var endNote = line.getElement(end);

        if (!beginNote.getType().isPitchedNote() || !endNote.getType().isPitchedNote()) {
            return false;
        }

        if (selectionSize == TIE_SELECTION_SIZE_WITH_SEPARATOR
            && !isTieSeparator(line.getElement(begin + 1).getType())) {
            return false;
        }

        if (beginNote.getPitch() != endNote.getPitch()) {
            return false;
        }

        var shouldConnect = line.findExactTie(begin, end) == null;

        // Conflict: tying would connect what a beam already connects. The raw range bounds
        // are correct here, unlike beaming/tupleting: the only interior a tie allows holds a
        // single separator, which can never be a grace note, and the isPitchedNote() check
        // above already rejects a grace note at either endpoint (refs #592).
        return !shouldConnect || shouldConnectBeamSelection(line, begin, end);
    }

    /**
     * Returns a {@link TupletToggleInfo} describing whether the range can be
     * tupleted/untupleted, which tuplet numbers it could actually become, which tuplet
     * currently covers the range start, and whether the range covers that tuplet's full span.
     * <p>
     * Rests are welcome inside a new tuplet — they contribute their written duration
     * exactly as notes do — so only grace notes are skipped. Anything else the span may
     * contain (a barline, a breath mark, a fermata) is left for the validator to reject,
     * which it does by leaving the grade out of {@code validGrades}.
     */
    @SuppressWarnings("ObjectEquality")
    public static TupletToggleInfo canToggleTuplet(Selection.Range range) {
        var line = range.line();
        var beginIndex = nonGraceBegin(range);
        var endIndex = nonGraceEnd(range);

        // Fewer than two non-grace elements — there is nothing to group.
        if (beginIndex < 0 || beginIndex == endIndex) {
            return new TupletToggleInfo(false, null, false);
        }

        Tuplet firstTuplet = null;

        for (var i = beginIndex; i <= endIndex; i++) {
            // A grace note rides along inside the span without joining the tuplet.
            if (line.getElement(i).getType().isGraceNote()) {
                continue;
            }

            var currentTuplet = line.findTupletAt(i);

            if (i == beginIndex) {
                firstTuplet = currentTuplet;
            } else if (currentTuplet != firstTuplet) {
                return new TupletToggleInfo(false, null, false);
            }
        }

        var coversExisting = (firstTuplet != null)
            && Span.exactly(beginIndex, endIndex).test(
                firstTuplet.getAnchorElementIndex(), firstTuplet.getEndElementIndex());

        // A strict sub-range of a tuplet has no creation decision to offer: making a tuplet
        // of it would silently destroy the tuplet it sits inside. The tuplet is still
        // reported so removal stays available.
        if ((firstTuplet != null) && !coversExisting) {
            return new TupletToggleInfo(false, firstTuplet, false);
        }

        return new TupletToggleInfo(
            true, validGradesFor(line, beginIndex, endIndex), firstTuplet, coversExisting);
    }

    /**
     * Returns the tuplet numbers this span could be notated as.
     * <p>
     * The span is measured once and the six candidate grades are then tested against that
     * measurement: resolving the beat walks back through the song, and this runs on every
     * document edit, not only when the selection changes.
     */
    private static Set<Integer> validGradesFor(Line line, int beginIndex, int endIndex) {
        var song = line.getSong();
        var context = TupletValidator.describeSpan(
            song, line, song.indexOfLine(line), beginIndex, endIndex);
        var grades = new HashSet<Integer>();

        // The range comes from the model, not from the menu's list of actions: what a
        // tuplet number may be is a fact about tuplets, and a selection query must not
        // change because someone reorders a menu.
        for (var grade = TupletValidator.MIN_GRADE; grade <= TupletValidator.MAX_GRADE; grade++) {
            if (TupletValidator.validate(context, grade, TupletValidator.Strictness.STRICT).valid()) {
                grades.add(grade);
            }
        }

        return grades;
    }

    /**
     * Returns whether the range can toggle trill.
     */
    public static boolean canToggleTrill(Selection.Range range) {
        return range.line()
            .getElements(range.begin(), range.end())
            .stream()
            .anyMatch(element -> element.getType().isPitchedNote());
    }

    /**
     * Returns whether the stem direction can be modified, either by flipping it
     * or by restoring it to automatic. Only notes that actually carry a stem
     * qualify — rests and whole notes have none.
     */
    public static boolean canModifyStemDirection(Selection.Range range) {
        return range.line()
            .getElements(range.begin(), range.end())
            .stream()
            .anyMatch(element -> element.getType().isNoteWithStem());
    }

    /**
     * Returns whether a new beam should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing beam (remove mode).
     */
    private static boolean shouldConnectBeamSelection(Line line, int beginIndex, int endIndex) {
        return !line.sameBeamAt(beginIndex, endIndex);
    }

    /**
     * Returns whether a new tie should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing tie (remove mode).
     */
    private static boolean shouldConnectTieSelection(Line line, int beginIndex, int endIndex) {
        return !line.sameTieAt(beginIndex, endIndex);
    }
}
