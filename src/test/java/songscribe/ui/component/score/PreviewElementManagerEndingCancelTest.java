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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Tuplet;
import songscribe.ui.OptionDialogs;

/**
 * Tests where {@code modifyExistingElement} strips the spans a replacement invalidates, relative
 * to the first-second ending confirmation that can still call the replacement off.
 *
 * <p>The strip has to sit below the confirm. Above it, a user who answers "no" keeps the note they
 * meant to keep but loses the beam and the tuplet that hung off it — removals recorded in the
 * modification bracket with no replacement left to justify them.
 *
 * <p>Layout — replacing the ending's end note with a right repeat asks the user to compensate by
 * turning the split into a left-right repeat, and that note carries both a beam and a triplet, so
 * one declined confirm exercises both sweeps:
 * <pre>
 *  idx:  0           1      2            3      4      5      6
 *        REPEAT_LEFT QUAVER REPEAT_RIGHT QUAVER QUAVER QUAVER SINGLE_BARLINE
 *        (anchor)           (split)      ···beam + triplet··· (keeps the end off the
 *                                                     (end)    terminal position)
 * </pre>
 */
class PreviewElementManagerEndingCancelTest extends PreviewElementManagerTestBase {

    /** The ending's anchor. */
    private static final int ANCHOR_INDEX = 0;

    /** The repeat that splits the ending's two sub-spans. */
    private static final int SPLIT_INDEX = 2;

    /** First note under the beam and the triplet. */
    private static final int SPAN_BEGIN_INDEX = 3;

    /** The ending's end note, and the last note under the beam and the triplet. */
    private static final int ENDING_END_INDEX = 5;

    /** Number of notes under the triplet. */
    private static final int TRIPLET_SIZE = 3;

    /** The button index {@code EndingConfirms} reads as "yes"; anything else declines. */
    private static final int YES_BUTTON_INDEX = 0;

    private Ending ending;
    private Beam beam;
    private Tuplet triplet;

    @BeforeEach
    void buildLine() {
        song.withoutMutationTracking(() -> {
            song.setLineWidthSs(WIDE_LINE_SS);
            line.addElement(repeatLeft());
            line.addElement(quaver());
            line.addElement(repeatRight());
            line.addElement(quaver());
            line.addElement(quaver());
            line.addElement(quaver());
            line.addElement(singleBarline());
        });

        ending = new Ending(line.getElement(ANCHOR_INDEX), line.getElement(ENDING_END_INDEX));
        beam = new Beam(line.getElement(SPAN_BEGIN_INDEX), line.getElement(ENDING_END_INDEX));
        triplet = Tuplet.withUnresolvedRatio(
            line.getElement(SPAN_BEGIN_INDEX), line.getElement(ENDING_END_INDEX), TRIPLET_SIZE);

        song.withoutMutationTracking(() -> {
            line.addSpan(ending);
            line.addBeaming(beam);
            line.addTuplet(triplet);
        });
    }

    /**
     * Drives a click that replaces the ending's end note with a right repeat, answering every
     * confirm with {@code confirmResult}.
     */
    private void clickRightRepeatOnEndNote(int confirmResult) {
        PreviewElementManager.setXPosSsMatchesElement(true);
        PreviewElementManager.setCurrentXIndex(ENDING_END_INDEX);
        setPreviewElement(repeatRight());

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            dialogs.when(() -> OptionDialogs.showOptionDialog(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any()
            )).thenReturn(confirmResult);

            PreviewElementManager.handleClick(lc);
        }
    }

    @Test
    void testDecliningTheConfirmLeavesEveryLineSpanInPlace() {
        clickRightRepeatOnEndNote(YES_BUTTON_INDEX + 1);

        assertAll(
            () -> assertThat(line.getElement(ENDING_END_INDEX).getType())
                .as("the declined replacement must not happen")
                .isEqualTo(ElementType.QUAVER),
            () -> assertThat(line.findSpans(Beam.class))
                .as("a beam must not be stripped for a replacement the user refused")
                .containsExactly(beam),
            () -> assertThat(line.findSpans(Tuplet.class))
                .as("a tuplet must not be stripped for a replacement the user refused")
                .containsExactly(triplet),
            () -> assertThat(line.findSpans(Ending.class))
                .as("the ending the confirm was about must survive")
                .containsExactly(ending),
            () -> assertThat(line.getElement(SPLIT_INDEX).getType())
                .as("the compensation the user refused must not be applied either")
                .isEqualTo(ElementType.REPEAT_RIGHT)
        );
    }

    @Test
    void testAcceptingTheConfirmStripsTheInvalidatedSpans() {
        clickRightRepeatOnEndNote(YES_BUTTON_INDEX);

        assertAll(
            () -> assertThat(line.getElement(ENDING_END_INDEX).getType())
                .as("the accepted replacement must happen")
                .isEqualTo(ElementType.REPEAT_RIGHT),
            () -> assertThat(line.findSpans(Beam.class))
                .as("a repeat cannot be beamed")
                .isEmpty(),
            () -> assertThat(line.findSpans(Tuplet.class))
                .as("a repeat carries no duration the triplet can be built on")
                .isEmpty(),
            () -> assertThat(line.findSpans(Ending.class))
                .as("the compensation is what keeps the ending")
                .containsExactly(ending),
            () -> assertThat(line.getElement(SPLIT_INDEX).getType())
                .as("the accepted compensation must turn the split into a left-right repeat")
                .isEqualTo(ElementType.REPEAT_LEFT_RIGHT)
        );
    }
}
