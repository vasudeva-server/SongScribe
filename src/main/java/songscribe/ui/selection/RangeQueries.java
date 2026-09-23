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
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;

/**
 * What an {@link ElementSelection} may become: whether it can be beamed, tied, tupleted,
 * trilled, or have its stem direction changed.
 * <p>
 * Every answer here is a pure function of {@code (line, begin, end)} and nothing else, which
 * is why they are static, why they no longer live beside the selection field, and why they take
 * {@link ElementSelection} rather than {@link Selection.Range}: the anchor a {@code Range}
 * additionally carries records where a click landed, and no answer here depends on it. Asking
 * one neither reads nor writes what is currently selected — pass any range and get that range's
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
    public record BoundaryTie(NoteElement anchor, NoteElement end) {}

    /**
     * Returns the cross-line tie candidate for the element at {@code index} in {@code line},
     * or {@code null} if the element does not sit at an eligible line boundary (#493).
     *
     * <p>All of these must hold: {@code index} is the element a tie would leave or enter
     * {@code line} through — the first non-separator of the line
     * ({@link Tie#firstNonSeparatorIndexFrom}) for the entry side, the last
     * ({@link Tie#lastNonSeparatorIndexBefore}) for the exit side — an adjacent line exists on
     * that side and offers such an element of its own, both elements are pitched notes, and the
     * two share a pitch. A line with only one of them checks the boundary before the line
     * first, so it never also offers the boundary after it.
     */
    public static @Nullable BoundaryTie boundaryTieAt(Line line, int index) {
        var song = line.getSong();

        if (!(line.getElement(index) instanceof NoteElement note)) {
            return null;
        }

        BoundaryTie candidate = null;

        var entryIndex = Tie.firstNonSeparatorIndexFrom(line.getElements(), 0);
        var exitIndex = Tie.lastNonSeparatorIndexBefore(line.getElements(), line.elementCount());

        if (entryIndex != null && index == entryIndex && !line.isFirst()) {
            var previousLine = song.getLine(line.index() - 1);
            var previousElements = previousLine.getElements();
            var anchorIndex = Tie.lastNonSeparatorIndexBefore(previousElements, previousElements.size());

            if (anchorIndex != null && previousLine.getElement(anchorIndex) instanceof NoteElement anchor) {
                candidate = new BoundaryTie(anchor, note);
            }
        } else if (exitIndex != null && index == exitIndex && !line.isLast()) {
            var nextLine = song.getLine(line.index() + 1);
            var endIndex = Tie.firstNonSeparatorIndexFrom(nextLine.getElements(), 0);

            if (endIndex != null && nextLine.getElement(endIndex) instanceof NoteElement end) {
                candidate = new BoundaryTie(note, end);
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
    private static boolean canBeTied(NoteElement anchor, NoteElement end) {
        return anchor.getType().isPitchedNote()
            && end.getType().isPitchedNote()
            && anchor.getPitch() == end.getPitch();
    }

    /**
     * Returns whether the range can be beamed/unbeamed.
     */
    public static boolean canToggleBeaming(ElementSelection range) {
        var line = range.line();
        var grouped = line.nonGraceRange(range.range());

        // Fewer than two non-grace elements — there is nothing to join.
        if (grouped == null || grouped.size() < 2) {
            return false;
        }

        //noinspection SimplifiableIfStatement
        if (!IntStream.rangeClosed(grouped.begin(), grouped.end()).allMatch(i -> {
            var type = line.getElement(i).getType();
            return type.isGraceNote() || type.isBeamable();
        })) {
            return false;
        }

        // Conflict: beaming would connect what a tie already connects.
        return !(shouldConnectBeamSelection(line, grouped)
            && !shouldConnectTieSelection(line, grouped));
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
    public static boolean canToggleTie(ElementSelection range) {
        var bounds = range.range();
        var selectionSize = bounds.size();

        // A single boundary element ties to the adjacent line's matching edge note, not to
        // anything else in range.line() — none of the same-line checks below apply (#493).
        if (selectionSize == TIE_SELECTION_SIZE_BOUNDARY) {
            return boundaryTieAt(range.line(), bounds.begin()) != null;
        }

        if (selectionSize != TIE_SELECTION_SIZE_WITHOUT_SEPARATOR
            && selectionSize != TIE_SELECTION_SIZE_WITH_SEPARATOR) {
            return false;
        }

        var line = range.line();
        var begin = bounds.begin();
        var end = bounds.end();

        if (!(line.getElement(begin) instanceof NoteElement anchorNote)
            || !(line.getElement(end) instanceof NoteElement endNote)
            || !canBeTied(anchorNote, endNote)) {
            return false;
        }

        if (selectionSize == TIE_SELECTION_SIZE_WITH_SEPARATOR
            && !Tie.isLegalSeparator(line.getElement(begin + 1).getType())) {
            return false;
        }

        var shouldConnect = !anchorNote.isTiedTo(endNote);

        // Conflict: tying would connect what a beam already connects. The raw range bounds
        // are correct here, unlike beaming/tupleting: the only interior a tie allows holds a
        // single separator, which can never be a grace note, and the isPitchedNote() check
        // above already rejects a grace note at either endpoint (refs #592).
        return !shouldConnect || shouldConnectBeamSelection(line, bounds);
    }

    /**
     * Returns the range a tie toggle on the element at {@code index} would act on, or
     * {@code null} when no pair qualifies (#706).
     *
     * <p>This proposes shapes and lets {@link #canToggleTie} judge them; it restates no tie
     * rule of its own. The shapes, in order: the adjacent pair ending at {@code index}, the
     * same pair with one element between them (a barline, repeat or breath mark — refs #527),
     * and finally {@code index} alone, read as a cross-line tie (#493).
     *
     * <p>Order is what keeps the separator rule unstated here. A barline at {@code index - 1}
     * is not a pitched note, so the adjacent pair fails inside {@code canToggleTie} and the
     * separator shape is reached only when the adjacent one is genuinely ineligible; the
     * separator shape's own size-3 branch then decides whether what sits between is legal. A
     * wrong-pitch <em>note</em> at {@code index - 1} fails both, because a note is not a legal
     * separator — a refusal, which is what the user should get.
     *
     * <p>There is no widen-or-break branch, unlike the beam side: ties never coalesce, since a
     * tie always joins a note to the next one ({@link NoteElement#tie()}), so the toggle is a
     * plain add or remove of the tie over the pair.
     *
     * <p>Grace notes are not transparent here, though they are for beams. A grace note is not a
     * legal separator, so the walk that derives a tie's end stops at one, and a grace note is
     * refused at either endpoint, so nothing walks past one (refs #592).
     *
     * <p>The single-element shape is accepted only when the boundary tie it would create runs
     * <em>backward</em> into {@code index} — {@link #boundaryTieAt} returns the forward
     * candidate when {@code index} is the line's tie-exit element, and tying forward would join
     * the note to one on the next line that the user has not reached yet.
     */
    public static @Nullable ElementSelection tieCandidateWithPredecessor(Line line, int index) {
        if (!line.hasIndex(index)) {
            return null;
        }

        var adjacent = tieCandidateEndingAt(line, index, TIE_SELECTION_SIZE_WITHOUT_SEPARATOR);

        if (adjacent != null) {
            return adjacent;
        }

        var separated = tieCandidateEndingAt(line, index, TIE_SELECTION_SIZE_WITH_SEPARATOR);

        if (separated != null) {
            return separated;
        }

        var boundaryTie = boundaryTieAt(line, index);

        //noinspection ObjectEquality
        if (boundaryTie != null && boundaryTie.end() == line.getElement(index)) {
            return ElementSelection.single(line, index);
        }

        return null;
    }

    /**
     * The {@code size}-element range ending at {@code index}, or {@code null} when it runs off
     * the front of the line or {@link #canToggleTie} refuses it.
     *
     * <p>A negative begin index names no element of the line, so a shape running off the front
     * is skipped before it is built: {@link #canToggleTie} would fetch that element and fail.
     */
    private static @Nullable ElementSelection tieCandidateEndingAt(Line line, int index, int size) {
        var begin = index - (size - 1);

        if (begin < 0) {
            return null;
        }

        var candidate = new ElementSelection(line, new ElementRange(begin, index));

        return canToggleTie(candidate) ? candidate : null;
    }

    /**
     * Returns the index of the element a glissando toggle would act on, or {@code null} when the
     * range is ineligible.
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
     * <p>A glissando lives on its <em>source</em> and connects forward, so the source is resolved
     * from the selection's size first: a single-element selection takes {@code begin() - 1} (the
     * element before it, which the glissando would run from), a two-element selection takes
     * {@code begin()} itself, and any other size yields null.
     *
     * <p>With the source resolved, a source that already {@code hasGlissando()} is returned
     * immediately — removal needs no further gate. Otherwise the addition gate applies: the source
     * must be in range with a successor, both it and its successor must be pitched notes, and
     * {@code isSamePitchAsFollower(source)} must be false. Failing any of those yields null.
     */
    public static @Nullable Integer glissandoSourceIndex(ElementSelection range) {
        var line = range.line();
        var bounds = range.range();
        int sourceIndex;

        if (bounds.size() == GLISSANDO_SELECTION_SIZE_SOURCE_ONLY) {
            sourceIndex = bounds.begin() - 1;
        } else if (bounds.size() == GLISSANDO_SELECTION_SIZE_WITH_TARGET) {
            sourceIndex = bounds.begin();
        } else {
            return null;
        }

        if (sourceIndex < 0) {
            return null;
        }

        var sourceElement = line.getElement(sourceIndex);

        // Removal answers before any add-only gate runs.
        if (sourceElement instanceof NoteElement sourceNote && sourceNote.hasGlissando()) {
            return sourceIndex;
        }

        if (sourceIndex + 1 >= line.elementCount()) {
            return null;
        }

        if (!sourceElement.getType().isPitchedNote()
            || !line.getElement(sourceIndex + 1).getType().isPitchedNote()) {
            return null;
        }

        return line.isSamePitchAsFollower(sourceIndex) ? null : sourceIndex;
    }

    /**
     * Returns whether the range can toggle a glissando.
     *
     * @see #glissandoSourceIndex
     */
    public static boolean canToggleGlissando(ElementSelection range) {
        return glissandoSourceIndex(range) != null;
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
    public static int[] fallIndices(ElementSelection range) {
        var line = range.line();
        var bounds = range.range();

        return IntStream.rangeClosed(bounds.begin(), bounds.end())
            .filter(i -> line.getElement(i) instanceof NoteElement note
                && (note.hasFall() || note.getType().isPitchedNote()))
            .toArray();
    }

    /**
     * Returns whether the range can toggle a fall — that is, whether it holds any note a fall
     * could hang off.
     *
     * @see #fallIndices
     */
    public static boolean canToggleFall(ElementSelection range) {
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
    public static TupletToggleInfo canToggleTuplet(ElementSelection range) {
        var line = range.line();
        var grouped = line.nonGraceRange(range.range());

        // Fewer than two non-grace elements — there is nothing to group.
        if (grouped == null || grouped.size() < 2) {
            return new TupletToggleInfo(false, null, false);
        }

        Tuplet firstTuplet = null;

        for (int i = grouped.begin(); i <= grouped.end(); i++) {
            // A grace note rides along inside the span without joining the tuplet.
            if (line.getElement(i).getType().isGraceNote()) {
                continue;
            }

            var currentTuplet = line.spanRegistry().findTupletAt(i);

            if (i == grouped.begin()) {
                firstTuplet = currentTuplet;
            } else if (currentTuplet != firstTuplet) {
                return new TupletToggleInfo(false, null, false);
            }
        }

        var coversExisting = (firstTuplet != null)
            && Span.exactly(grouped).test(firstTuplet.getAnchorElementIndex(), firstTuplet.getEndElementIndex());

        // A strict sub-range of a tuplet has no creation decision to offer: making a tuplet
        // of it would silently destroy the tuplet it sits inside. The tuplet is still
        // reported so removal stays available.
        if ((firstTuplet != null) && !coversExisting) {
            return new TupletToggleInfo(false, firstTuplet, false);
        }

        return new TupletToggleInfo(
            true, validGradesFor(line, grouped), firstTuplet, coversExisting);
    }

    /**
     * Returns the tuplet numbers this span could be notated as.
     * <p>
     * The span is measured once and the six candidate grades are then tested against that
     * measurement: resolving the beat walks back through the song, and this runs on every
     * document edit, not only when the selection changes.
     */
    private static Set<Integer> validGradesFor(Line line, ElementRange span) {
        var context = TupletValidator.describeSpan(line, span);
        var grades = new HashSet<Integer>();

        // The range comes from the model, not from the menu's list of actions: what a
        // tuplet number may be is a fact about tuplets, and a selection query must not
        // change because someone reorders a menu.
        for (var grade = TupletValidator.MIN_GRADE; grade <= TupletValidator.MAX_GRADE; grade++) {
            if (TupletValidator.validate(context, grade, TupletValidator.Strictness.STRICT)
                    instanceof TupletValidator.Result.Valid) {
                grades.add(grade);
            }
        }

        return grades;
    }

    /**
     * Returns whether the range can toggle trill.
     */
    public static boolean canToggleTrill(ElementSelection range) {
        return selectedElements(range).stream()
            .anyMatch(element -> element.getType().isPitchedNote());
    }

    private static List<StaffElement> selectedElements(ElementSelection range) {
        return range.line().getElements(range.range());
    }

    /**
     * Returns whether the stem direction can be modified, either by flipping it
     * or by restoring it to automatic. Only notes that actually carry a stem
     * qualify — rests and whole notes have none.
     */
    public static boolean canModifyStemDirection(ElementSelection range) {
        return selectedElements(range).stream()
            .anyMatch(element -> element.getType().isNoteWithStem());
    }

    /**
     * Returns whether a new beam should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing beam (remove mode).
     */
    private static boolean shouldConnectBeamSelection(Line line, ElementRange range) {
        return !line.spanRegistry().sameBeamAt(range.begin(), range.end());
    }

    /**
     * Returns whether a new tie should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing tie (remove mode).
     */
    private static boolean shouldConnectTieSelection(Line line, ElementRange range) {
        return !(line.getElement(range.begin()) instanceof NoteElement first
            && line.getElement(range.end()) instanceof NoteElement second
            && first.isTiedTo(second));
    }
}
