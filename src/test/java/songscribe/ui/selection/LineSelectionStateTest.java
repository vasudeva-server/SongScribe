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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;
import songscribe.ui.action.TupletAction;

class LineSelectionStateTest extends UnitTest {

    /**
     * Builds an {@link Ending} spanning the line's first two elements. The line must
     * already contain at least two elements.
     */
    private static Ending makeEnding(Line line) {
        return new Ending(line.getElement(0), line.getElement(1));
    }

    // -- clearSelection --

    @Test
    void testClearSelectionResetsAllFiveFieldsAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
        state.setLineSelected(true);
        state.selectSlide(0);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.clearSelection();

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(state.getSelectionAnchor()).isEqualTo(-1);
        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.hasSlideSelection()).isFalse();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- setLineSelected --

    @Test
    void testSetLineSelectedTrueClearsSlideIndexAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.selectSlide(0);
        assertThat(state.hasSlideSelection()).isTrue();

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setLineSelected(true);

        assertThat(state.isLineSelected()).isTrue();
        assertThat(state.hasSlideSelection()).isFalse();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    @Test
    void testSetLineSelectedFalseSetsLineSelectedFalseAndFiresCallback() {
        var line = detachedLine();
        var state = new LineSelectionState(line);
        state.setLineSelected(true);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setLineSelected(false);

        assertThat(state.isLineSelected()).isFalse();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- selectSlide --

    @Test
    void testSelectSlideClearsSelectionAndSetsIndexAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
        state.setLineSelected(true);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.selectSlide(1);

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(state.getSelectionAnchor()).isEqualTo(-1);
        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.isSlideSelected(1)).isTrue();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- isSlideSelected --

    @Test
    void testIsSlideSelectedReturnsTrueOnlyForMatchingIndex() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.selectSlide(1);

        assertThat(state.isSlideSelected(1)).isTrue();
        assertThat(state.isSlideSelected(0)).isFalse();
    }

    // -- getSelectedSlideElementIndex --

    @Test
    void testGetSelectedSlideElementIndexReturnsMinusOneInitiallyAndIndexAfterSelectSlide() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        assertThat(state.getSelectedSlideElementIndex()).isEqualTo(-1);

        state.selectSlide(1);

        assertThat(state.getSelectedSlideElementIndex()).isEqualTo(1);
    }

    // -- hasSlideSelection --

    @Test
    void testHasSlideSelectionReturnsFalseInitiallyAndTrueAfterSelectSlide() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        assertThat(state.hasSlideSelection()).isFalse();

        state.selectSlide(0);

        assertThat(state.hasSlideSelection()).isTrue();
    }

    // -- selectEnding --

    @Test
    void testSelectEndingClearsSelectionAndSetsEndingAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
        state.setLineSelected(true);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.selectEnding(ending);

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(state.getSelectionAnchor()).isEqualTo(-1);
        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.isDecorationSelected(ending)).isTrue();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    @Test
    void testSelectEndingClearsSlideSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);
        state.selectSlide(0);
        assertThat(state.hasSlideSelection()).isTrue();

        state.selectEnding(ending);

        assertThat(state.hasSlideSelection()).isFalse();
        assertThat(state.isDecorationSelected(ending)).isTrue();
    }

    @Test
    void testSelectSlideClearsEndingSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);
        state.selectEnding(ending);
        assertThat(state.hasEndingSelection()).isTrue();

        state.selectSlide(0);

        assertThat(state.hasEndingSelection()).isFalse();
        assertThat(state.isSlideSelected(0)).isTrue();
    }

    // -- revalidateDecorationSelection --

    @Test
    void testRevalidateDecorationSelectionNoOpWhenNoDecorationSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);

        assertThat(state.revalidateDecorationSelection()).isFalse();
        assertThat(state.getSelectionBegin()).isEqualTo(0);
    }

    @Test
    void testRevalidateDecorationSelectionKeepsSlideWhenElementStillHasSlide() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var state = new LineSelectionState(line);
        state.selectSlide(0);

        assertThat(state.revalidateDecorationSelection()).isFalse();
        assertThat(state.hasSlideSelection()).isTrue();
    }

    @Test
    void testRevalidateDecorationSelectionClearsSlideWhenElementNoLongerHasSlide() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var state = new LineSelectionState(line);
        state.selectSlide(0);

        // Simulates an undo/redo that removed the slide without clearing the selection.
        line.getElement(0).removeSlide();

        assertThat(state.revalidateDecorationSelection()).isTrue();
        assertThat(state.hasSlideSelection()).isFalse();
    }

    @Test
    void testRevalidateDecorationSelectionClearsSlideWhenIndexShiftedOutOfBounds() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var state = new LineSelectionState(line);
        state.selectSlide(0);

        // Simulates an undo/redo that shrank the line so the slide's index is stale.
        line.removeElement(0);

        assertThat(state.revalidateDecorationSelection()).isTrue();
        assertThat(state.hasSlideSelection()).isFalse();
    }

    @Test
    void testRevalidateDecorationSelectionKeepsEndingWhenStillOnLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        line.addRangeElement(ending);
        var state = new LineSelectionState(line);
        state.selectEnding(ending);

        assertThat(state.revalidateDecorationSelection()).isFalse();
        assertThat(state.hasEndingSelection()).isTrue();
    }

    @Test
    void testRevalidateDecorationSelectionClearsEndingWhenNoLongerOnLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        line.addRangeElement(ending);
        var state = new LineSelectionState(line);
        state.selectEnding(ending);

        // Simulates an undo/redo that removed the ending without clearing the selection.
        line.removeRangeElement(ending);

        assertThat(state.revalidateDecorationSelection()).isTrue();
        assertThat(state.hasEndingSelection()).isFalse();
    }

    // -- isDecorationSelected --

    @Test
    void testIsEndingSelectedReturnsTrueOnlyForMatchingEnding() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var firstEnding = makeEnding(line);
        var secondEnding = new Ending(line.getElement(2), line.getElement(3));
        var state = new LineSelectionState(line);
        state.selectEnding(firstEnding);

        assertThat(state.isDecorationSelected(firstEnding)).isTrue();
        assertThat(state.isDecorationSelected(secondEnding)).isFalse();
    }

    /**
     * The parameter is any line element, not just an ending, so a note can be passed in.
     * Even with the decoration slot occupied by an ending, a note must never be reported as
     * the selected decoration — notes are selected separately, by index. Pinning this down
     * now means the next decoration type added to this check cannot quietly start answering
     * for notes too.
     */
    @Test
    void testIsDecorationSelectedReturnsFalseForANoteWhileAnEndingIsSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);
        state.selectEnding(ending);

        assertThat(state.isDecorationSelected(ending))
            .as("the ending occupies the decoration slot")
            .isTrue();
        assertThat(state.isDecorationSelected(line.getElement(0)))
            .as("a note is not a selected decoration")
            .isFalse();
    }

    // -- getSelectedEnding --

    @Test
    void testGetSelectedEndingReturnsNullInitiallyAndEndingAfterSelectEnding() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);

        assertThat(state.getSelectedEnding()).isNull();

        state.selectEnding(ending);

        assertThat(state.getSelectedEnding()).isSameAs(ending);
    }

    // -- hasEndingSelection --

    @Test
    void testHasEndingSelectionReturnsFalseInitiallyAndTrueAfterSelectEnding() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);

        assertThat(state.hasEndingSelection()).isFalse();

        state.selectEnding(ending);

        assertThat(state.hasEndingSelection()).isTrue();
    }

    @Test
    void testSetSelectionFromClickClearsEndingSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);
        state.selectEnding(ending);
        assertThat(state.hasEndingSelection()).isTrue();

        state.setSelectionFromClick(1);

        assertThat(state.hasEndingSelection()).isFalse();
    }

    @Test
    void testSetLineSelectedTrueClearsEndingSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);
        state.selectEnding(ending);
        assertThat(state.hasEndingSelection()).isTrue();

        state.setLineSelected(true);

        assertThat(state.hasEndingSelection()).isFalse();
    }

    // -- canToggleBeaming / canToggleTuplet with grace notes (refs #592) --

    /**
     * Builds a detached line from {@code types} and returns a state with all of it selected.
     */
    private static LineSelectionState selectAllOf(ElementType... types) {
        var line = detachedLine();

        for (var type : types) {
            line.addElement(type.newInstance());
        }

        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(types.length - 1);
        return state;
    }

    @Test
    void testGraceHostPairInsideSelectionCanToggleBeaming() {
        var state = selectAllOf(ElementType.QUAVER, ElementType.GRACE_QUAVER, ElementType.QUAVER);

        assertThat(state.canToggleBeaming()).isTrue();
        assertThat(state.getNonGraceSelectionBegin()).isEqualTo(0);
        assertThat(state.getNonGraceSelectionEnd()).isEqualTo(2);
    }

    @Test
    void testLeadingGraceNoteIsExcludedFromTheBeamSpan() {
        var state = selectAllOf(ElementType.GRACE_QUAVER, ElementType.QUAVER, ElementType.QUAVER);

        assertThat(state.canToggleBeaming()).isTrue();
        assertThat(state.getNonGraceSelectionBegin()).isEqualTo(1);
        assertThat(state.getNonGraceSelectionEnd()).isEqualTo(2);
    }

    @Test
    void testSingleNoteWithGraceNoteCannotToggleBeaming() {
        var state = selectAllOf(ElementType.GRACE_QUAVER, ElementType.QUAVER);

        assertThat(state.canToggleBeaming()).isFalse();
    }

    // The leading QUAVER makes the grace-note exemption in the range check actually run; a
    // fixture whose first element is already unbeamable would short-circuit before reaching it.
    @Test
    void testGraceNoteDoesNotMakeUnbeamableSelectionBeamable() {
        var state = selectAllOf(ElementType.QUAVER, ElementType.GRACE_QUAVER, ElementType.CROTCHET);

        assertThat(state.canToggleBeaming())
            .as("the unbeamable crotchet still blocks beaming")
            .isFalse();
    }

    @Test
    void testOnlyGraceNotesSelectedCannotToggleBeaming() {
        var state = selectAllOf(ElementType.GRACE_QUAVER, ElementType.GRACE_QUAVER);

        assertThat(state.canToggleBeaming()).isFalse();
        assertThat(state.getNonGraceSelectionBegin()).isEqualTo(-1);
        assertThat(state.getNonGraceSelectionEnd()).isEqualTo(-1);
    }

    @Test
    void testGraceHostPairInsideSelectionCanToggleTuplet() {
        var state = selectAllOf(ElementType.CROTCHET, ElementType.GRACE_QUAVER, ElementType.CROTCHET);

        assertThat(state.canToggleTuplet().canToggle()).isTrue();
    }

    @Test
    void testSingleNoteWithGraceNoteCannotToggleTuplet() {
        var state = selectAllOf(ElementType.GRACE_QUAVER, ElementType.CROTCHET);

        assertThat(state.canToggleTuplet().canToggle()).isFalse();
    }

    @Test
    void testTupletCoverageIgnoresTrailingGraceNote() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addTuplet(new Tuplet(line.getElement(0), line.getElement(1), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.coversExisting())
            .as("the trailing grace note does not stop the selection from covering the tuplet")
            .isTrue();
    }

    // -- canToggleTuplet --

    @Test
    void testEmptySelectionCannotToggleTuplet() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testTwoPitchedNotesNoTupletCanToggle() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testFullCoverageOfTripletReportsCoversExisting() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(new Tuplet(line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing())
            .isNotNull()
            .extracting(tuplet -> tuplet != null ? tuplet.getGrade() : 0)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isTrue();
    }

    @Test
    void testPartialCoverageOfTripletDoesNotCoverExisting() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(new Tuplet(line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing())
            .isNotNull()
            .extracting(tuplet -> tuplet != null ? tuplet.getGrade() : 0)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testSelectionSpanningTwoDifferentTupletsCannotToggle() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(new Tuplet(line.getElement(0), line.getElement(1), TupletAction.Tuplet.DUPLET.getSize()));
        line.addTuplet(new Tuplet(line.getElement(2), line.getElement(3), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(3);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    // -- selectAll excludes the auto-maintained final barline --

    @Test
    void testSelectAllExcludesAutoMaintainedFinalBarlineOnLastLine() {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.addElement(0, ElementType.CROTCHET.newInstance());
            line.addElement(1, ElementType.CROTCHET.newInstance());
        });

        // Line now holds: [quarter, quarter, FINAL_DOUBLE_BARLINE]
        assertThat(line.elementCount()).isEqualTo(3);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

        var state = new LineSelectionState(line);
        state.selectAll();

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
    }

    @Test
    void testSelectAllExcludesAutoMaintainedRightRepeatTerminalOnLastLine() {
        var song = new Song();
        var line = song.getLine(0);
        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        song.withoutMutationTracking(() -> {
            line.addElement(0, ElementType.CROTCHET.newInstance());
            line.addElement(1, ElementType.CROTCHET.newInstance());
        });

        assertThat(line.elementCount()).isEqualTo(3);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.REPEAT_RIGHT);

        var state = new LineSelectionState(line);
        state.selectAll();

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
    }

    @Test
    void testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing() {
        var song = new Song();
        var line = song.getLine(0);

        // Default song seeds the first (and only) line with just the final barline.
        assertThat(line.elementCount()).isEqualTo(1);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

        var state = new LineSelectionState(line);
        state.selectAll();

        assertThat(state.hasElementSelection()).isFalse();
    }

    @Test
    void testSelectAllOnNonLastLineIncludesAllElements() {
        var song = new Song();
        var firstLine = song.getLine(0);
        song.addLine(1, new Line(song));

        // firstLine is no longer the last line — its final barline has been transferred away.
        song.withoutMutationTracking(() -> {
            firstLine.addElement(0, ElementType.CROTCHET.newInstance());
            firstLine.addElement(1, ElementType.CROTCHET.newInstance());
        });

        var state = new LineSelectionState(firstLine);
        state.selectAll();

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(firstLine.elementCount() - 1);
    }

    // -- isElementSelected --

    @Test
    void testIsElementSelectedReturnsTrueWithinInclusiveRange() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.isElementSelected(0)).isTrue();
        assertThat(state.isElementSelected(1)).isTrue();
        assertThat(state.isElementSelected(2)).isFalse();
        assertThat(state.isElementSelected(-1)).isFalse();
    }

    // -- getSelectionSize --

    @Test
    void testGetSelectionSizeReturnsZeroWhenNoSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        assertThat(state.getSelectionSize()).isEqualTo(0);
    }

    @Test
    void testGetSelectionSizeReturnsCorrectCountAfterSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        assertThat(state.getSelectionSize()).isEqualTo(3);
    }

    // -- getSelection --

    @Test
    void testGetSelectionReturnsNullWhenNothingSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        assertThat(state.getSelection()).isNull();
    }

    @Test
    void testGetSelectionReturnsFullLineSpanWhenLineSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setLineSelected(true);

        assertThat(state.getSelection())
            .isNotNull()
            .satisfies(sel -> {
                assertThat(sel.begin()).isEqualTo(0);
                assertThat(sel.end()).isEqualTo(1);
                assertThat(sel.line()).isSameAs(line);
            });
    }

    @Test
    void testGetSelectionReturnsElementRangeWhenElementSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(1);
        state.extendSelectionTo(2);

        assertThat(state.getSelection())
            .isNotNull()
            .satisfies(sel -> {
                assertThat(sel.begin()).isEqualTo(1);
                assertThat(sel.end()).isEqualTo(2);
                assertThat(sel.line()).isSameAs(line);
            });
    }

    // -- setSelectionFromClick --

    @Test
    void testSetSelectionFromClickSetsAllFieldsAndClearsSlideAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.selectSlide(0);
        assertThat(state.hasSlideSelection()).isTrue();

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setSelectionFromClick(1);

        assertThat(state.getSelectionBegin()).isEqualTo(1);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
        assertThat(state.getSelectionAnchor()).isEqualTo(1);
        assertThat(state.hasSlideSelection()).isFalse();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- extendSelectionTo --

    @Test
    void testExtendSelectionToWithAnchorBeforeIndexSetsBeginToAnchorEndToIndex() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.extendSelectionTo(2);

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(2);
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    @Test
    void testExtendSelectionToWithAnchorAfterIndexSetsBeginToIndexEndToAnchor() {
        // Reversed drag: anchor at index 2, extending back to index 0.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(2);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.extendSelectionTo(0);

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(2);
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    @Test
    void testExtendSelectionToIsNoOpWhenAnchorIsMinusOne() {
        // No anchor set: extendSelectionTo should not modify begin/end.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        // Default selectionAnchor is -1; set begin/end so we can detect any change.
        state.setSelectionFromClick(0);
        // Clear the anchor so we are testing the no-anchor path.
        state.clearSelection();

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.extendSelectionTo(1);

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(callbackCount[0]).isEqualTo(0);
    }

    // -- extendSelection --

    @Test
    void testExtendSelectionStartsNewSelectionWhenBeginIsMinusOne() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        // selectionBegin defaults to -1: first call should start a new selection.

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.extendSelection(1);

        assertThat(state.getSelectionBegin()).isEqualTo(1);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    @Test
    void testExtendSelectionExtendsEndWhenSelectionAlreadyExists() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.extendSelection(0);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.extendSelection(2);

        // begin must stay at 0; only end extends.
        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(2);
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- resetElementSelection --

    @Test
    void testResetElementSelectionSetsBeginAndEndToMinusOneAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
        // Set lineSelected=true after all element-selection setup; note that setLineSelected(true)
        // also clears selectedSlideElementIndex so a slide cannot be simultaneously active.
        state.setLineSelected(true);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.resetElementSelection();

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        // resetElementSelection must not touch lineSelected.
        assertThat(state.isLineSelected()).isTrue();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- setSelectionAnchor / getSelectionAnchor --

    @Test
    void testSetSelectionAnchorRoundTrip() {
        var line = detachedLine();
        var state = new LineSelectionState(line);

        assertThat(state.getSelectionAnchor()).isEqualTo(-1);

        state.setSelectionAnchor(3);

        assertThat(state.getSelectionAnchor()).isEqualTo(3);
    }

    // -- selectionChangeCallback fires on every state-mutating call --

    @Test
    void testSelectionChangeCallbackFiresOnEveryStateMutatingCall() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setSelectionFromClick(0);
        assertThat(callbackCount[0]).isEqualTo(1);

        state.extendSelectionTo(2);
        assertThat(callbackCount[0]).isEqualTo(2);

        state.extendSelection(1);
        assertThat(callbackCount[0]).isEqualTo(3);

        state.setLineSelected(true);
        assertThat(callbackCount[0]).isEqualTo(4);

        state.selectSlide(0);
        assertThat(callbackCount[0]).isEqualTo(5);

        state.resetElementSelection();
        assertThat(callbackCount[0]).isEqualTo(6);

        state.clearSelection();
        assertThat(callbackCount[0]).isEqualTo(7);
    }

    @Test
    void testSelectionContainingNonPitchedElementCannotToggle() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    // -- canToggleTie failure paths --

    @Test
    void testCanToggleTieWithSizeLessThanTwoSetsCanTieFalse() {
        // Size 1: only one element selected.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithSizeGreaterThanTwoSetsCanTieFalse() {
        // Size 3: three elements selected.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithNonPitchedElementSetsCanTieFalse() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithPitchMismatchSetsCanTieFalse() {
        // Two notes at different staff positions → different pitches.
        var line = detachedLine();
        var note0 = ElementType.CROTCHET.newInstance();
        note0.setStaffPosition(0);
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(1);
        line.addElement(note0);
        line.addElement(note1);
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithElementsInDifferentTiesAllowsChainedTie() {
        // tie1 spans notes 0-1, tie2 spans notes 2-3.
        // Selecting notes 1 and 2 chains a new tie between the two existing ties.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTie(new Tie(line.getElement(0), line.getElement(1)));
        line.addTie(new Tie(line.getElement(2), line.getElement(3)));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(1);
        state.extendSelectionTo(2);

        var result = state.canToggleTie();

        assertThat(result).isTrue();
        assertThat(state.getCanTie()).isEqualTo(true);
        assertThat(state.getExistingTie()).isNull();
    }

    // -- resetTieState --

    @Test
    void testResetTieStateClearsCanTieAndExistingTie() {
        // Arrange: two same-pitch notes → canToggleTie() sets canTie=true, existingTie=null (add mode).
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
        // Populate canTie and existingTie by calling canToggleTie().
        assertThat(state.canToggleTie()).isTrue();
        assertThat(state.getCanTie()).isEqualTo(true);

        state.resetTieState();

        assertThat(state.getCanTie()).isNull();
        assertThat(state.getExistingTie()).isNull();
    }

    // -- canToggleTrill --

    @Test
    void testCanToggleTrillReturnsFalseWhenNoSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        // selectionBegin defaults to -1 — no selection.

        assertThat(state.canToggleTrill()).isFalse();
    }

    @Test
    void testCanToggleTrillReturnsTrueWhenAtLeastOnePitchedNoteInRange() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canToggleTrill()).isTrue();
    }

    @Test
    void testCanToggleTrillReturnsFalseWhenSelectionContainsOnlyRests() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canToggleTrill()).isFalse();
    }

    // -- canModifyStemDirection --

    @Test
    void testCanModifyStemDirectionReturnsFalseWhenNothingSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        // selectionBegin defaults to -1 — no selection.

        assertThat(state.canModifyStemDirection()).isFalse();
    }

    @Test
    void testCanModifyStemDirectionReturnsTrueWhenAtLeastOneNonRestInRange() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canModifyStemDirection()).isTrue();
    }

    @Test
    void testCanModifyStemDirectionReturnsFalseWhenSelectionIsAllRests() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canModifyStemDirection()).isFalse();
    }
}
