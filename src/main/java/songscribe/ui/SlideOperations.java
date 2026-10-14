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
import songscribe.dom.Slide;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.mutation.ElementField;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.RangeQueries;
import songscribe.undo.OpNames;

/**
 * Toggles glissandos and falls on notes, for both the select-mode toggle actions and the
 * edit-mode keys. Both modes use the same {@link RangeQueries} eligibility predicate, so they
 * never disagree about what is eligible.
 *
 * <p>A mixed selection always adds; it never clears.
 */
public final class SlideOperations {

    private SlideOperations() {
    }

    public static EditResult toggleGlissando(
        ElementSelection range, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        var sourceIndex = RangeQueries.glissandoSourceIndex(range);

        if (sourceIndex == null) {
            return EditResult.REFUSED;
        }

        return applySlides(range.line(), new int[] { sourceIndex }, Slide.GLISSANDO, lyricRenderMetrics);
    }

    /**
     * {@code elementIndex} is the note just inserted, not the glissando's source (the note
     * before it). The caller keeps pointing the key at the inserted note, so a second press
     * toggles the same pair back off.
     */
    public static EditResult toggleGlissandoWithPredecessor(
        Line line, int elementIndex, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        if (!line.hasIndex(elementIndex)) {
            return EditResult.REFUSED;
        }

        var sourceIndex = RangeQueries.glissandoSourceIndex(ElementSelection.single(line, elementIndex));

        if (sourceIndex == null) {
            return EditResult.REFUSED;
        }

        return applySlides(line, new int[] { sourceIndex }, Slide.GLISSANDO, lyricRenderMetrics);
    }

    public static EditResult toggleFall(ElementSelection range, @Nullable LyricRenderMetrics lyricRenderMetrics) {
        return applySlides(range.line(), RangeQueries.fallIndices(range), Slide.FALL, lyricRenderMetrics);
    }

    public static EditResult toggleFallOnLastInsertion(
        Line line, int elementIndex, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        if (!line.hasIndex(elementIndex)) {
            return EditResult.REFUSED;
        }

        var range = ElementSelection.single(line, elementIndex);

        return applySlides(line, RangeQueries.fallIndices(range), Slide.FALL, lyricRenderMetrics);
    }

    /**
     * The one bracket makes the whole batch a single undo step. The fit check is one batched
     * call, because the claims accumulate: asking about each candidate alone would accept a batch
     * that jointly overruns the line.
     */
    private static EditResult applySlides(
        Line line, int[] indices, Slide slide, @Nullable LyricRenderMetrics lyricRenderMetrics) {

        if (indices.length == 0) {
            return EditResult.REFUSED;
        }

        // No index left to add to means every index already carries the slide, so this is the
        // remove branch and nothing claims further space. Anything less adds, mixed or not.
        var addedIndices = Arrays.stream(indices)
            .filter(index -> line.noteAt(index).getSlide() != slide)
            .toArray();
        var removing = addedIndices.length == 0;
        String label;

        if (removing) {
            label = OpNames.deleteSlideLabel(slide);
        } else {
            if (!InsertionSpacingCalculator.hasRoomForSlides(line, addedIndices, slide, lyricRenderMetrics)) {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_INSERT_ERROR, lineFullMessage(slide));

                return EditResult.REPORTED;
            }

            label = OpNames.addSlideLabel(slide);
        }

        line.getSong().withModification(label, () -> {
            for (var index : indices) {
                var element = line.noteAt(index);

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
                } else if (element.getSlide() != slide) {
                    line.modifyElement(index, ElementField.SLIDE, () -> element.setSlide(slide));
                }
            }
        });

        return EditResult.MODIFIED;
    }

    private static String lineFullMessage(Slide slide) {
        return switch (slide) {
            case FALL -> Strings.ERROR_LINE_FULL_FALL;
            case GLISSANDO -> Strings.ERROR_LINE_FULL_GLISSANDO;
        };
    }
}
