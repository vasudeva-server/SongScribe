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
import songscribe.ui.action.TupletAction;

class LineSelectionStateTest extends UnitTest {

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
        state.selectGlissando(0);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.clearSelection();

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(state.getSelectionAnchor()).isEqualTo(-1);
        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.hasGlissandoSelection()).isFalse();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- setLineSelected --

    @Test
    void testSetLineSelectedTrueClearsGlissandoIndexAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.selectGlissando(0);
        assertThat(state.hasGlissandoSelection()).isTrue();

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setLineSelected(true);

        assertThat(state.isLineSelected()).isTrue();
        assertThat(state.hasGlissandoSelection()).isFalse();
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

    // -- selectGlissando --

    @Test
    void testSelectGlissandoClearsSelectionAndSetsIndexAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
        state.setLineSelected(true);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.selectGlissando(1);

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(state.getSelectionAnchor()).isEqualTo(-1);
        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.isGlissandoSelected(1)).isTrue();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- isGlissandoSelected --

    @Test
    void testIsGlissandoSelectedReturnsTrueOnlyForMatchingIndex() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.selectGlissando(1);

        assertThat(state.isGlissandoSelected(1)).isTrue();
        assertThat(state.isGlissandoSelected(0)).isFalse();
    }

    // -- getSelectedGlissandoElementIndex --

    @Test
    void testGetSelectedGlissandoElementIndexReturnsMinusOneInitiallyAndIndexAfterSelectGlissando() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        assertThat(state.getSelectedGlissandoElementIndex()).isEqualTo(-1);

        state.selectGlissando(1);

        assertThat(state.getSelectedGlissandoElementIndex()).isEqualTo(1);
    }

    // -- hasGlissandoSelection --

    @Test
    void testHasGlissandoSelectionReturnsFalseInitiallyAndTrueAfterSelectGlissando() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        assertThat(state.hasGlissandoSelection()).isFalse();

        state.selectGlissando(0);

        assertThat(state.hasGlissandoSelection()).isTrue();
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
    void testSetSelectionFromClickSetsAllFieldsAndClearsGlissandoAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.selectGlissando(0);
        assertThat(state.hasGlissandoSelection()).isTrue();

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setSelectionFromClick(1);

        assertThat(state.getSelectionBegin()).isEqualTo(1);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
        assertThat(state.getSelectionAnchor()).isEqualTo(1);
        assertThat(state.hasGlissandoSelection()).isFalse();
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
        // also clears selectedGlissandoElementIndex so glissando cannot be simultaneously active.
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

        state.selectGlissando(0);
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
    void testCanToggleTieWithElementsInDifferentTiesSetsCanTieFalse() {
        // tie1 spans notes 0-1, tie2 spans notes 2-3.
        // Selecting notes 1 and 2 crosses two different ties.
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

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
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

    // -- canFlipStemDirection --

    @Test
    void testCanFlipStemDirectionReturnsFalseWhenNothingSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        // selectionBegin defaults to -1 — no selection.

        assertThat(state.canFlipStemDirection()).isFalse();
    }

    @Test
    void testCanFlipStemDirectionReturnsTrueWhenAtLeastOneNonRestInRange() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canFlipStemDirection()).isTrue();
    }

    @Test
    void testCanFlipStemDirectionReturnsFalseWhenSelectionIsAllRests() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canFlipStemDirection()).isFalse();
    }
}
