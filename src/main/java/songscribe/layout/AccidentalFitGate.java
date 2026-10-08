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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import songscribe.dom.AccidentalChain;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;

/**
 * The fit gate for an edit whose chain walk restamps notes: answers whether the edit still fits
 * with every accidental the walk decided drawn, and guarantees that a refusal leaves every note
 * exactly as it was.
 *
 * <h2>Why a gate is needed at all</h2>
 * An edit measured for fit before it mutates — a preview insertion, a preview replacement, a
 * paste, a selection action that widens a column — is refused when a line it would widen no
 * longer fits, and the refusal must mutate nothing: the caller's modification bracket stays
 * closed and no notification is posted. But the fit check has to <em>measure</em> the accidentals
 * the walk decided: {@code ElementColumnBuilder} derives a note's extents from what it draws and
 * {@link LayoutEngine} takes accidental widths as a layout input, so the decided states must
 * already be on the notes when the projected column chain is built. Stamping before measuring and
 * measuring before deciding therefore pull against each other, and this class is the one place
 * that resolves them.
 *
 * <p>A restamp is written plainly with {@link AccidentalChain.Restamp#stamp}, which records
 * nothing — mutation recording happens only through {@link Line#modifyElement} — so the gate
 * changes what the notes draw without making anything undoable. The song runs the chain walk
 * after every recorded change, so once the accepted edit commits, the walk stamps every live note
 * the gate had restamped and records those stamps in the edit's own undo step. The gate is called
 * before that bracket opens; the live notes it stamped are put back before it returns, so the
 * walk finds them as they stood.
 *
 * <p>A chain crosses line breaks, so a restamp may land on a line other than the one the edit
 * touches. Such a line is not projected — it is measured as it stands, with the restamp in place
 * — and only when the restamp changes what its note draws: a state change that draws the same
 * accidental, or none either way, leaves the line's columns as they were and needs no measuring.
 */
public final class AccidentalFitGate {

    /**
     * A note the gate stamped, with what it held before so the gate can put it back.
     *
     * @param line           The live line the note is on or is about to land on
     * @param prior          The note, with the state it held on entry
     * @param drawnChanged   Whether stamping changed what the note draws, and so the width of its
     *                       column
     */
    private record Stamped(Line line, NoteElement.SavedAccidental prior, boolean drawnChanged) {
    }

    /**
     * The gate's answer. A refusal names the line that has no room, because the user is told
     * which: the line being edited, or another line whose accidentals the edit changes.
     */
    public enum Verdict {
        /** The edited line and every other line the walk widens still fit. */
        FITS,
        /** The edited line, as the edit will leave it, has no room. */
        EDITED_LINE_FULL,
        /** The edited line fits, but another line a restamp widens has no room. */
        OTHER_LINE_FULL
    }

    private AccidentalFitGate() {
        // Prevent instantiation - utility class with static methods only
    }

    /**
     * Judges whether the edit whose chain walk produced {@code restamps} fits: {@code editedLine}
     * as the edit will leave it, and every other line a restamp widens as it stands, each with the
     * walk's decisions drawn.
     *
     * <p>Every note named by a restamp is stamped plainly, through
     * {@link AccidentalChain.Restamp#stamp}, before anything is measured, so both measurements
     * see the accidentals the edit will draw. {@code editedLineFits} is the edit's
     * own fit check over its projection of {@code editedLine}, evaluated with the stamps in place;
     * every other line holding a restamp that changes what its note draws is measured with
     * {@link InsertionSpacingCalculator#fitsInPlace} over its current effective elements, and
     * only when {@code editedLineFits} answers true.
     *
     * <p>Call before the edit's modification bracket opens. The edit's own walk, run by the song
     * once the bracket records the edit, is what stamps the live notes for good and records the
     * stamps into the same undo step.
     *
     * @param restamps           The walk's decisions for the edit, naming each note at most once;
     *                           {@link AccidentalChain.Restamp#line} is the live line each note is
     *                           on or is about to land on
     * @param detached           Elements not on any line — a preview note, a selection action's
     *                           stand-in, a replacement, a pasted clone — that the edit's projection
     *                           put on {@code editedLine} and the edit will place there
     * @param lyricRenderMetrics Lyric metrics for measuring the other lines' syllables; null spaces
     *                           them as if they had no lyrics
     * @param editedLineFits     The edit's fit check over {@code editedLine} as the edit will leave
     *                           it, evaluated exactly once, with every restamp in place. It shows
     *                           nothing; the caller tells the user from the verdict
     * @return {@link Verdict#EDITED_LINE_FULL} when {@code editedLineFits} answers false;
     *         otherwise {@link Verdict#OTHER_LINE_FULL} when another line a restamp widens no
     *         longer fits its song's line width; otherwise {@link Verdict#FITS}
     * @effects nothing is recorded. A refusal leaves every note, live and detached, in the state
     *          it held on entry. {@link Verdict#FITS} leaves every live note as it was and leaves
     *          each note in {@code detached} stamped as the projection decided, so a replacement
     *          or a pasted clone lands with a sounding alteration
     * @invariant a live note — one not in {@code detached} — holds on return the state it held on
     *            entry, whatever the verdict
     */
    public static Verdict judge(
        Line editedLine,
        List<AccidentalChain.Restamp> restamps,
        List<? extends StaffElement> detached,
        @Nullable LyricRenderMetrics lyricRenderMetrics,
        BooleanSupplier editedLineFits) {

        var stamped = stamp(restamps);

        Verdict verdict;

        if (!editedLineFits.getAsBoolean()) {
            verdict = Verdict.EDITED_LINE_FULL;
        } else if (!otherLinesFit(editedLine, stamped, lyricRenderMetrics)) {
            verdict = Verdict.OTHER_LINE_FULL;
        } else {
            verdict = Verdict.FITS;
        }

        restore(stamped, verdict == Verdict.FITS ? identities(detached) : Collections.emptySet());

        return verdict;
    }

    /**
     * Stamps every restamp's note plainly and returns what each held before, in {@code restamps}
     * order.
     */
    private static List<Stamped> stamp(List<AccidentalChain.Restamp> restamps) {
        var stamped = new ArrayList<Stamped>(restamps.size());

        for (var restamp : restamps) {
            var note = restamp.note();
            var prior = note.saveAccidentalState();
            var drawnBefore = note.drawnAccidental();
            restamp.stamp();
            var drawnChanged = !Objects.equals(drawnBefore, note.drawnAccidental());
            stamped.add(new Stamped(restamp.line(), prior, drawnChanged));
        }

        return stamped;
    }

    /**
     * Measures, as it stands, every line other than {@code editedLine} on which a stamp changed
     * what a note draws. Each such line is measured once, however many of its notes were stamped.
     */
    private static boolean otherLinesFit(
        Line editedLine, List<Stamped> stamped, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        Set<Line> measured = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var stamp : stamped) {
            var line = stamp.line();

            if (line == editedLine || !stamp.drawnChanged() || !measured.add(line)) {
                continue;
            }

            var currentElements = line.effectiveElements();

            if (!InsertionSpacingCalculator.fitsInPlace(line, currentElements, lyricRenderMetrics)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Puts each stamped note's prior state back plainly, except the notes in
     * {@code keepStamped}, which keep the state the gate gave them.
     */
    private static void restore(List<Stamped> stamped, Set<StaffElement> keepStamped) {
        for (var stamp : stamped) {
            var prior = stamp.prior();

            if (!keepStamped.contains(prior.note())) {
                prior.restore();
            }
        }
    }

    /**
     * The elements of {@code elements} as an identity set: a restamp names the note object the
     * projection holds, so which notes are detached is a question of identity, not equality.
     */
    private static Set<StaffElement> identities(List<? extends StaffElement> elements) {
        Set<StaffElement> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(elements);
        return identities;
    }
}
