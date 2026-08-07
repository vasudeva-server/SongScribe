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

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
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

    /** A single element selected at a line boundary — the cross-line tie candidate (#493). */
    private static final int TIE_SELECTION_SIZE_BOUNDARY = 1;

    /** Two adjacent notes, with nothing between them. */
    private static final int TIE_SELECTION_SIZE_WITHOUT_SEPARATOR = 2;

    /** Two notes with a single non-duration element between them (refs #527). */
    private static final int TIE_SELECTION_SIZE_WITH_SEPARATOR = 3;

    /** A glissando's source and its target, both selected (#717). */
    private static final int GLISSANDO_SELECTION_SIZE_WITH_TARGET = 2;

    /** A single selected element, read as the glissando's target — its source precedes it (#717). */
    private static final int GLISSANDO_SELECTION_SIZE_SOURCE_ONLY = 1;

    private RangeQueries() {}

    /**
     * A tie candidate spanning a line boundary, with {@code anchor} the endpoint earlier in
     * document order and {@code end} the endpoint later (#493).
     */
    public record BoundaryTie(StaffElement anchor, StaffElement end) {}

    /**
     * Returns the cross-line tie candidate for the element at {@code index} in {@code line},
     * or {@code null} if the element does not sit at an eligible line boundary (#493).
     *
     * <p>All of these must hold: {@code index} is the element a tie would leave or enter
     * {@code line} through — see {@link #tieExitIndex}/{@link #tieEntryIndex} — an adjacent
     * line exists on that side and offers such an element of its own, both elements are
     * pitched notes, and the two share a pitch. A line with only one of them checks the
     * boundary before the line first, so it never also offers the boundary after it.
     */
    public static @Nullable BoundaryTie boundaryTieAt(Line line, int index) {
        var song = line.getSong();
        var lineIndex = song.indexOfLine(line);
        var element = line.getElement(index);
        BoundaryTie candidate = null;

        if (index == tieEntryIndex(line) && lineIndex > 0) {
            var previousLine = song.getLine(lineIndex - 1);
            var anchorIndex = tieExitIndex(previousLine);

            if (anchorIndex >= 0) {
                candidate = new BoundaryTie(previousLine.getElement(anchorIndex), element);
            }
        } else if (index == tieExitIndex(line)
            && lineIndex >= 0 && lineIndex < song.lineCount() - 1) {
            var nextLine = song.getLine(lineIndex + 1);
            var endIndex = tieEntryIndex(nextLine);

            if (endIndex >= 0) {
                candidate = new BoundaryTie(element, nextLine.getElement(endIndex));
            }
        }

        if (candidate == null) {
            return null;
        }

        return canBeTied(candidate.anchor(), candidate.end()) ? candidate : null;
    }

    /**
     * Returns whether two notes are eligible to be joined by a tie at all: both are pitched
     * notes, and they share a pitch.
     * <p>
     * The only statement of that rule. Both paths that offer a tie ask — {@link #canToggleTie}
     * for a pair selected within one line, {@link #boundaryTieAt} for a pair straddling a line
     * break — and a rule written twice is a rule that changes in one place only.
     */
    private static boolean canBeTied(StaffElement anchor, StaffElement end) {
        return anchor.getType().isPitchedNote()
            && end.getType().isPitchedNote()
            && anchor.getPitch() == end.getPitch();
    }

    /** The element a tie entering {@code line} from the previous line ends on. */
    private static int tieEntryIndex(Line line) {
        return tieAttachmentIndex(line, 0, 1);
    }

    /** The element a tie leaving {@code line} for the next line anchors to. */
    private static int tieExitIndex(Line line) {
        return tieAttachmentIndex(line, line.elementCount() - 1, -1);
    }

    /**
     * The index of the element at one edge of {@code line} that a cross-line tie attaches to,
     * found by walking inward from {@code edgeIndex} in direction {@code step} past every
     * element a tie may straddle. Returns -1 when the walk runs off the line without reaching
     * one.
     *
     * <p>Not simply the first or last element. {@link Tie#isLegalSeparator} lets a barline,
     * repeat or breath mark sit between a tie's two notes, and a line that ends with a barline
     * is the ordinary case rather than a corner one — pinning the candidate to the last element
     * would refuse the tie for most of the lines a user actually writes. The same walk answers
     * both halves of the question, because they are one question put to two lines: which
     * element the user must have selected for a tie to leave this line, and which element of
     * the adjacent line it lands on.
     *
     * <p>A grace note is not a legal separator, so one sitting before the end note or after the
     * anchor stops the walk instead of being passed over — matching what
     * {@link Tie#isInvalidatedByInsertion} does to a tie the moment a grace note appears there.
     * A final double barline stops it for the same reason: nothing may sound across the end of
     * the piece.
     */
    private static int tieAttachmentIndex(Line line, int edgeIndex, int step) {
        for (var i = edgeIndex; i >= 0 && i < line.elementCount(); i += step) {
            if (!Tie.isLegalSeparator(line.getElement(i).getType())) {
                return i;
            }
        }

        return -1;
    }

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
     * Returns whether the range can toggle a tie.
     *
     * <p>A tie joins two notes of the same pitch, which may be adjacent or separated by a
     * single non-duration element such as a barline or repeat (refs #527).
     *
     * <p>A <b>single</b> selected element is also accepted, but only when it sits at a line
     * edge and the adjacent line offers a same-pitch note at its own edge — the cross-line
     * tie (#493). That case shares none of the same-line checks below: there is no second
     * selected element to pair with, and the partner is found by
     * {@link #boundaryTieAt} rather than by looking inside {@code range}.
     */
    public static boolean canToggleTie(Selection.Range range) {
        var selectionSize = range.size();

        // A single boundary element ties to the adjacent line's matching edge note, not to
        // anything else in range.line() — none of the same-line checks below apply (#493).
        if (selectionSize == TIE_SELECTION_SIZE_BOUNDARY) {
            return boundaryTieAt(range.line(), range.begin()) != null;
        }

        if (selectionSize != TIE_SELECTION_SIZE_WITHOUT_SEPARATOR
            && selectionSize != TIE_SELECTION_SIZE_WITH_SEPARATOR) {
            return false;
        }

        var line = range.line();
        var begin = range.begin();
        var end = range.end();
        if (!canBeTied(line.getElement(begin), line.getElement(end))) {
            return false;
        }

        if (selectionSize == TIE_SELECTION_SIZE_WITH_SEPARATOR
            && !Tie.isLegalSeparator(line.getElement(begin + 1).getType())) {
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
     * Returns the index of the element a glissando toggle would act on, or -1 when the range
     * is ineligible.
     *
     * <p>A glissando lives on its <b>source</b> note and connects forward to the element that
     * immediately follows it, so the source is not always inside the range: a single selected
     * element is read as the glissando's <em>target</em>, whose source is the element before it,
     * while a selected pair is read as source and target in order. Any other size has no
     * unambiguous source.
     *
     * <p>Removing an existing glissando is never blocked. The {@code hasGlissando()} test
     * therefore comes first, ahead of the bounds, type and pitch gates, which apply to the add
     * branch only: a glissando that reached the model through a MusicXML import may sit on a
     * note whose follower is a rest or a grace note, and gating removal on those rules would
     * leave it impossible to toggle off.
     *
     * <p>Adding one requires two adjacent {@link songscribe.dom.ElementType#isPitchedNote()
     * pitched notes} — which excludes rests, barlines, repeats, breath marks and grace notes —
     * of different pitch, as {@link Line#isSamePitchAsFollower} defines that condition.
     *
     * <pre>{@code
     * GLISSANDO SOURCE RESOLUTION       A glissando lives on its SOURCE and connects forward.
     *
     *   size 1:  [ N ]                  source = begin() - 1
     *          ↑ begin                  ┌───┐   ┌───┐
     *                                   │ p │~~~│ N │      p = begin()-1
     *                                   └───┘   └───┘
     *   size 2:  [ N   N ]              source = begin()
     *          ↑ begin ↑ end            ┌───┐   ┌───┐
     *                                   │ N │~~~│ N │
     *                                   └───┘   └───┘
     *   any other size  →  -1
     *
     *   source resolved
     *         │
     *         ▼
     *   source.hasGlissando()? ──yes──► return source     (removal: no further gate)
     *         │ no
     *         ▼
     *   source >= 0  AND  source+1 < elementCount()
     *     AND  both source and source+1 isPitchedNote()
     *     AND  !isSamePitchAsFollower(source)  ──no──► -1
     *         │ yes
     *         ▼
     *   return source                                     (addition)
     * }</pre>
     */
    public static int glissandoSourceIndex(Selection.Range range) {
        var line = range.line();
        int sourceIndex;

        if (range.size() == GLISSANDO_SELECTION_SIZE_SOURCE_ONLY) {
            sourceIndex = range.begin() - 1;
        } else if (range.size() == GLISSANDO_SELECTION_SIZE_WITH_TARGET) {
            sourceIndex = range.begin();
        } else {
            return -1;
        }

        if (sourceIndex < 0) {
            return -1;
        }

        var sourceElement = line.getElement(sourceIndex);

        // Removal answers before any add-only gate runs.
        if (sourceElement.hasGlissando()) {
            return sourceIndex;
        }

        if (sourceIndex + 1 >= line.elementCount()) {
            return -1;
        }

        if (!sourceElement.getType().isPitchedNote()
            || !line.getElement(sourceIndex + 1).getType().isPitchedNote()) {
            return -1;
        }

        return line.isSamePitchAsFollower(sourceIndex) ? -1 : sourceIndex;
    }

    /**
     * Returns whether the range can toggle a glissando.
     *
     * @see #glissandoSourceIndex
     */
    public static boolean canToggleGlissando(Selection.Range range) {
        return glissandoSourceIndex(range) >= 0;
    }

    /**
     * Returns every index in the range whose element may carry a fall — that is, every
     * {@link songscribe.dom.ElementType#isPitchedNote() pitched note}, which excludes rests,
     * barlines, repeats, breath marks and grace notes, plus any element that already carries a
     * fall regardless of type: a fall that reached the model through a MusicXML import may sit
     * on a non-pitched element the add rules would never have allowed one on, and gating removal
     * on those rules would leave it impossible to toggle off (mirrors
     * {@link #glissandoSourceIndex}'s removal bypass).
     *
     * <p>The one statement of which notes a fall toggle applies to, so the predicate that
     * offers the toggle and the kernel that performs it cannot disagree.
     */
    public static int[] fallIndices(Selection.Range range) {
        var line = range.line();

        return IntStream.rangeClosed(range.begin(), range.end())
            .filter(i -> {
                var element = line.getElement(i);

                return element.hasFall() || element.getType().isPitchedNote();
            })
            .toArray();
    }

    /**
     * Returns whether the range can toggle a fall — that is, whether it holds any note a fall
     * could hang off.
     *
     * @see #fallIndices
     */
    public static boolean canToggleFall(Selection.Range range) {
        return fallIndices(range).length > 0;
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
                line.anchorIndexOf(firstTuplet), line.endIndexOf(firstTuplet));

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
