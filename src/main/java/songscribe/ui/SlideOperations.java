/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.error.RuntimeError;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.mutation.ElementField;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.RangeQueries;
import songscribe.ui.selection.Selection;
import songscribe.undo.OpNames;

/**
 * Toggles glissandos and falls on notes. The single implementation behind both ways a slide can
 * be placed: the select-mode toggle actions, which act on the current selection, and the
 * edit-mode keys, which act on the last insertion.
 *
 * <p>The select-mode entry points take a {@link Selection.Range} directly. The edit-mode entry
 * points wrap the last-inserted element in {@link Selection.Range#single} and hand that to the
 * same {@link RangeQueries} eligibility predicate, so the two modes can never disagree about
 * what is eligible.
 *
 * <p>{@link SlideZone} is the kind parameter: {@code matches()} is the "already carries this
 * slide" test and {@code applyTo()} is the mutation, so one kernel serves both slide kinds and
 * the test and the mutation cannot drift apart.
 *
 * <pre>
 * SLIDE TOGGLE DECISION   (indices = one glissando source, or every pitched index in a range)
 *
 *   indices empty ────────────────────────────────────────► REFUSED
 *          │
 *          ▼
 *   every index zone.matches(element)? ──yes──► REMOVE all
 *          │ no                                 label = deleteSlideLabel(getSlide())
 *          ▼
 *   ADD to every index that does not match yet
 *   label = addSlideLabel(zone == SlideZone.FALL)
 *     ── a mixed selection ADDS, it never clears ──
 *          │
 *          ▼
 *   NOT hasRoomForSlides(line, addedIndices, zone)? ──────► modify nothing,
 *          │                                                 line-full error dialog,
 *          │ room is fine                                     REPORTED
 *          ▼
 *   ONE line.withModification(label, …) bracket  ⇒  exactly one undo step, MODIFIED
 * </pre>
 */
public final class SlideOperations {

    /** Passed as the arm index by the select-mode entry points, which never arm an insertion. */
    private static final int NO_ARM = -1;

    private SlideOperations() {
    }

    /**
     * What a toggle did, which is what tells its caller whether the user still owes an
     * explanation. The distinction matters because a refusal for want of horizontal room already
     * puts an error dialog on screen — a caller that beeps on every falsy outcome would sound the
     * beep the moment the user dismisses that dialog.
     */
    public enum Result {

        /** The line was modified, in exactly one undo step. */
        MODIFIED,

        /**
         * Nothing was modified and nothing was said: the selection or the last insertion is not
         * eligible. Silent, so the caller owes the user feedback — a beep, on the key paths.
         */
        REFUSED,

        /**
         * Nothing was modified and the user has already been told why with an error dialog. The
         * caller must add no further feedback.
         */
        REPORTED
    }

    /**
     * Toggles the glissando the range resolves to.
     *
     * @see RangeQueries#glissandoSourceIndex
     */
    public static Result toggleGlissando(
        Selection.Range range, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var sourceIndex = RangeQueries.glissandoSourceIndex(range);

        if (sourceIndex < 0) {
            return Result.REFUSED;
        }

        return applySlides(
            range.line(), new int[] { sourceIndex }, SlideZone.GLISSANDO, lyricRenderMetrics, NO_ARM);
    }

    /**
     * Toggles a glissando between the element at {@code elementIndex} and its predecessor.
     *
     * <p>Static and taking the line explicitly because the operation does not go through the
     * selection coordinator — the caller looks up the state, exactly as
     * {@link MusicEditOperations#toggleBeamWithPredecessor} does.
     *
     * <p>The armed slot is {@code elementIndex}, the note just inserted, not the glissando's
     * source — which for a single-element range is the note before it. Arming the inserted note
     * is what lets a second press toggle the same pair back off. Arming happens only on the
     * branch that actually opens a modification bracket; see
     * {@link EditModeManager#armInsertion} for why an arm on a refusing path is a bug.
     */
    public static Result toggleGlissandoWithPredecessor(
        Line line, int elementIndex, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        if (elementIndex < 0 || elementIndex >= line.elementCount()) {
            return Result.REFUSED;
        }

        var sourceIndex = RangeQueries.glissandoSourceIndex(Selection.Range.single(line, elementIndex));

        if (sourceIndex < 0) {
            return Result.REFUSED;
        }

        return applySlides(
            line, new int[] { sourceIndex }, SlideZone.GLISSANDO, lyricRenderMetrics, elementIndex);
    }

    /**
     * Toggles falls on every note in the range.
     *
     * @see RangeQueries#fallIndices
     */
    public static Result toggleFall(Selection.Range range, @Nullable LyricRenderMetrics lyricRenderMetrics) {
        return applySlides(
            range.line(), RangeQueries.fallIndices(range), SlideZone.FALL, lyricRenderMetrics, NO_ARM);
    }

    /**
     * Toggles a fall on the element at {@code elementIndex}.
     *
     * <p>The edit-mode counterpart of {@link #toggleFall}, arming {@code elementIndex} on the
     * modifying branch so a second press takes the fall back off. As with
     * {@link #toggleGlissandoWithPredecessor}, the refusing branches — an ineligible element and
     * a line with no room for the falls — arm nothing.
     */
    public static Result toggleFallOnLastInsertion(
        Line line, int elementIndex, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        if (elementIndex < 0 || elementIndex >= line.elementCount()) {
            return Result.REFUSED;
        }

        var range = Selection.Range.single(line, elementIndex);

        return applySlides(
            line, RangeQueries.fallIndices(range), SlideZone.FALL, lyricRenderMetrics, elementIndex);
    }

    /**
     * Adds or removes {@code zone}'s slide across {@code indices} in one modification bracket.
     * Removes only when every index already carries the slide; a mixed set adds to the ones that
     * do not, so a toggle never clears a selection it has not filled first.
     *
     * <p>The one bracket is what makes the whole batch a single undo step.
     *
     * <p>The add branch first asks whether the line can hold the slides it would add, and refuses
     * the whole batch rather than applying part of it. Both slide kinds claim horizontal space — a
     * fall widens its own column, a glissando puts a floor under the gap to the note it reaches —
     * so both are asked. The check is one batched call rather than one per index, because those
     * claims accumulate: asking about each candidate alone against the unchanged line would accept
     * a batch that jointly overruns the line.
     *
     * @param armElementIndex The index to arm as the pending insertion, or {@link #NO_ARM} for
     *                        the select-mode callers. Arming lives here so it can never happen
     *                        on a path that opens no bracket.
     */
    private static Result applySlides(
        Line line,
        int[] indices,
        SlideZone zone,
        @Nullable LyricRenderMetrics lyricRenderMetrics,
        int armElementIndex) {

        if (indices.length == 0) {
            return Result.REFUSED;
        }

        // No index left to add to means every index already carries the slide, so this is the
        // remove branch and nothing claims further space. Anything less adds, mixed or not.
        var addedIndices = Arrays.stream(indices)
            .filter(index -> !zone.matches(line.getElement(index)))
            .toArray();
        var removing = addedIndices.length == 0;
        String label;

        if (removing) {
            // Non-null wherever the zone matches: matching is precisely carrying that slide.
            var slide = line.getElement(indices[0]).getSlide();

            if (slide == null) {
                throw RuntimeError.exit("slide missing on an element its zone matched");
            }

            label = OpNames.deleteSlideLabel(slide);
        } else {
            if (!InsertionSpacingCalculator.hasRoomForSlides(line, addedIndices, zone, lyricRenderMetrics)) {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_INSERT_ERROR, lineFullMessage(zone));

                return Result.REPORTED;
            }

            label = OpNames.addSlideLabel(zone == SlideZone.FALL);
        }

        if (armElementIndex >= 0) {
            EditModeManager.armInsertion(line, armElementIndex);
        }

        line.withModification(label, () -> {
            for (var index : indices) {
                var element = line.getElement(index);

                if (removing) {
                    // Capture before the removal: stripping the glissando un-pairs the grace
                    // note, so by the time the sync runs there is no pairing left to read.
                    // The add branch never needs this — indices there are required to be
                    // pitched notes, and a grace note never is, so isPairedGraceNote cannot
                    // hold there.
                    var wasPairedGraceNote = line.isPairedGraceNote(index);

                    line.modifyElement(index, ElementField.SLIDE, element::removeSlide);

                    // Un-pairing dissolves the automatic melisma. Both elements survive, so
                    // the syllable simply stays on the now-ordinary former grace note.
                    if (wasPairedGraceNote) {
                        line.syncGraceHostMelisma(index);
                    }
                } else if (!zone.matches(element)) {
                    line.modifyElement(index, ElementField.SLIDE, () -> zone.applyTo(element));
                }
            }
        });

        return Result.MODIFIED;
    }

    /** Names the "no room on this line" message for the kind of slide that would not fit. */
    private static String lineFullMessage(SlideZone zone) {
        return zone == SlideZone.FALL ? Strings.ERROR_LINE_FULL_FALL : Strings.ERROR_LINE_FULL_GLISSANDO;
    }
}
