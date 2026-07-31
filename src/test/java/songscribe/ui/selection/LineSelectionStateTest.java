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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;
import songscribe.ui.action.TupletAction;

class LineSelectionStateTest extends UnitTest {

    /** Index of the terminal barline appended after the two notes of {@link #twoNoteLine()}. */
    private static final int TERMINAL_ELEMENT_INDEX = 2;

    /** Five notes — the span a quintuplet covers. */
    private static final int QUINTUPLET_NOTE_COUNT = 5;

    /**
     * Builds an {@link Ending} spanning the line's first two elements. The line must
     * already contain at least two elements.
     */
    private static Ending makeEnding(Line line) {
        return new Ending(line.getElement(0), line.getElement(1));
    }

    /**
     * Builds a {@link Crescendo} spanning the line's first two elements. The line must
     * already contain at least two elements.
     */
    private static Hairpin makeHairpin(Line line) {
        return new Crescendo(line.getElement(0), line.getElement(1));
    }

    /**
     * Builds a detached line holding two crotchets — enough for an ending or a hairpin
     * to span.
     */
    /**
     * Stubs the mock song behind a detached fixture so tuplet validation can resolve a beat.
     * An unstubbed mock resolves to no beat at all, which no real song ever does.
     */
    private static Line withQuarterBeat(Line line) {
        when(line.getSong().resolveBeatAt(anyInt(), anyInt()))
            .thenReturn(new Song.BeatAt(Duration.CROTCHET, 0, 0));
        return line;
    }

    private Line twoNoteLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        return line;
    }

    /**
     * Every decoration kind, as a factory over a line holding at least two elements, so
     * that a test covering all of them adds no branch of its own.
     */
    private static final List<Named<Function<Line, SelectedDecoration>>> DECORATION_KINDS = List.of(
        Named.of("slide", _ -> new SelectedDecoration.SlideSelection(0)),
        Named.of("ending", line -> new SelectedDecoration.EndingSelection(makeEnding(line))),
        Named.of("hairpin", line -> new SelectedDecoration.HairpinSelection(makeHairpin(line)))
    );

    private static Stream<Named<Function<Line, SelectedDecoration>>> decorationKinds() {
        return DECORATION_KINDS.stream();
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
        state.selectDecoration(new SelectedDecoration.SlideSelection(0));

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.clearSelection();

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(state.getSelectionAnchor()).isEqualTo(-1);
        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.getSelectedDecoration()).isNull();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    // -- setLineSelected --

    @Test
    void testSetLineSelectedTrueClearsTheDecorationAndFiresCallback() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.SlideSelection(0));

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setLineSelected(true);

        assertThat(state.isLineSelected()).isTrue();
        assertThat(state.getSelectedDecoration()).isNull();
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

    // -- selectDecoration --

    /**
     * Each decoration kind, selected over a live element and line selection. The three
     * take the same path through {@link LineSelectionState#selectDecoration}, so what is
     * checked per kind is that the decoration itself is what comes back out.
     */
    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testSelectDecorationClearsElementAndLineSelectionAndFiresCallback(
        Function<? super Line, ? extends SelectedDecoration> makeDecoration
    ) {
        var line = twoNoteLine();
        var decoration = makeDecoration.apply(line);
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
        state.setLineSelected(true);

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.selectDecoration(decoration);

        assertThat(state.getSelectionBegin()).isEqualTo(-1);
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
        assertThat(state.getSelectionAnchor()).isEqualTo(-1);
        assertThat(state.hasElementSelection()).isFalse();
        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.getSelectedDecoration()).isEqualTo(decoration);
        assertThat(state.hasDecorationSelection()).isTrue();
        assertThat(callbackCount[0]).isEqualTo(1);
    }

    /**
     * One field holds the selected decoration, so selecting one replaces whatever was
     * there. Walking every ordered pair of kinds from {@link #DECORATION_KINDS} keeps
     * this exhaustive as decoration kinds are added: a new entry in that list extends
     * the coverage here without touching this test.
     */
    @Test
    void testSelectDecorationReplacesAnyPreviouslySelectedKind() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);

        for (var first : DECORATION_KINDS) {
            for (var second : DECORATION_KINDS) {
                state.selectDecoration(first.getPayload().apply(line));

                var replacement = second.getPayload().apply(line);
                state.selectDecoration(replacement);

                assertThat(state.getSelectedDecoration())
                    .as("selecting a %s over a %s leaves only the %s selected",
                        second.getName(), first.getName(), second.getName())
                    .isEqualTo(replacement);
            }
        }
    }

    // -- getSelectedDecoration --

    @Test
    void testGetSelectedDecorationReturnsNullUntilADecorationIsSelected() {
        var line = twoNoteLine();
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);

        assertThat(state.getSelectedDecoration()).isNull();
        assertThat(state.hasDecorationSelection()).isFalse();

        state.selectDecoration(new SelectedDecoration.EndingSelection(ending));

        assertThat(state.getSelectedDecoration())
            .isEqualTo(new SelectedDecoration.EndingSelection(ending));
        assertThat(state.hasDecorationSelection()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testSetSelectionFromClickClearsTheDecoration(Function<? super Line, ? extends SelectedDecoration> makeDecoration) {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.selectDecoration(makeDecoration.apply(line));

        state.setSelectionFromClick(1);

        assertThat(state.getSelectedDecoration()).isNull();
        assertThat(state.isElementSelected(1)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testSetLineSelectedTrueClearsTheDecoration(Function<? super Line, ? extends SelectedDecoration> makeDecoration) {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.selectDecoration(makeDecoration.apply(line));

        state.setLineSelected(true);

        assertThat(state.getSelectedDecoration()).isNull();
        assertThat(state.isLineSelected()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testClearSelectionClearsTheDecoration(Function<? super Line, ? extends SelectedDecoration> makeDecoration) {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.selectDecoration(makeDecoration.apply(line));

        state.clearSelection();

        assertThat(state.getSelectedDecoration()).isNull();
    }

    // -- isSlideSelected --

    @Test
    void testIsSlideSelectedReturnsTrueOnlyForMatchingIndex() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.SlideSelection(1));

        assertThat(state.isSlideSelected(1)).isTrue();
        assertThat(state.isSlideSelected(0)).isFalse();
    }

    /**
     * A selected ending covers element 0, but nothing there owns a selected slide.
     */
    @Test
    void testIsSlideSelectedReturnsFalseWhileAnEndingIsSelected() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.EndingSelection(makeEnding(line)));

        assertThat(state.isSlideSelected(0)).isFalse();
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
        state.selectDecoration(new SelectedDecoration.EndingSelection(firstEnding));

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
        var line = twoNoteLine();
        var ending = makeEnding(line);
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.EndingSelection(ending));

        assertThat(state.isDecorationSelected(ending))
            .as("the ending occupies the decoration slot")
            .isTrue();
        assertThat(state.isDecorationSelected(line.getElement(0)))
            .as("a note is not a selected decoration")
            .isFalse();
    }

    /**
     * The parameter is any line element, so a note can be passed in. With a hairpin in
     * the decoration slot, a note must still never be reported as the selected decoration.
     */
    @Test
    void testIsDecorationSelectedDistinguishesHairpinsFromNotesAndOtherHairpins() {
        var line = twoNoteLine();
        var selectedHairpin = makeHairpin(line);
        var otherHairpin = makeHairpin(line);
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.HairpinSelection(selectedHairpin));

        assertThat(state.isDecorationSelected(selectedHairpin)).isTrue();
        assertThat(state.isDecorationSelected(otherHairpin)).isFalse();
        assertThat(state.isDecorationSelected(line.getElement(0))).isFalse();
    }

    /**
     * A slide belongs to an element rather than being one, so the element carrying the
     * selected slide is not itself a selected decoration.
     */
    @Test
    void testIsDecorationSelectedReturnsFalseWhileASlideIsSelected() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.SlideSelection(0));

        assertThat(state.isDecorationSelected(line.getElement(0))).isFalse();
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
        state.selectDecoration(new SelectedDecoration.SlideSelection(0));

        assertThat(state.revalidateDecorationSelection()).isFalse();
        assertThat(state.getSelectedDecoration())
            .isEqualTo(new SelectedDecoration.SlideSelection(0));
    }

    @Test
    void testRevalidateDecorationSelectionClearsSlideWhenElementNoLongerHasSlide() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.SlideSelection(0));

        // Simulates an undo/redo that removed the slide without clearing the selection.
        line.getElement(0).removeSlide();

        assertThat(state.revalidateDecorationSelection()).isTrue();
        assertThat(state.getSelectedDecoration()).isNull();
    }

    @Test
    void testRevalidateDecorationSelectionClearsSlideWhenIndexShiftedOutOfBounds() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.SlideSelection(0));

        // Simulates an undo/redo that shrank the line so the slide's index is stale.
        line.removeElement(0);

        assertThat(state.revalidateDecorationSelection()).isTrue();
        assertThat(state.getSelectedDecoration()).isNull();
    }

    @Test
    void testRevalidateDecorationSelectionKeepsEndingWhenStillOnLine() {
        var line = twoNoteLine();
        var ending = makeEnding(line);
        line.addRangeElement(ending);
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.EndingSelection(ending));

        assertThat(state.revalidateDecorationSelection()).isFalse();
        assertThat(state.getSelectedDecoration())
            .isEqualTo(new SelectedDecoration.EndingSelection(ending));
    }

    @Test
    void testRevalidateDecorationSelectionClearsEndingWhenNoLongerOnLine() {
        var line = twoNoteLine();
        var ending = makeEnding(line);
        line.addRangeElement(ending);
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.EndingSelection(ending));

        // Simulates an undo/redo that removed the ending without clearing the selection.
        line.removeRangeElement(ending);

        assertThat(state.revalidateDecorationSelection()).isTrue();
        assertThat(state.getSelectedDecoration()).isNull();
    }

    @Test
    void testRevalidateDecorationSelectionKeepsHairpinWhenStillOnLine() {
        var line = twoNoteLine();
        var hairpin = makeHairpin(line);
        line.addRangeElement(hairpin);
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.HairpinSelection(hairpin));

        assertThat(state.revalidateDecorationSelection()).isFalse();
        assertThat(state.getSelectedDecoration())
            .isEqualTo(new SelectedDecoration.HairpinSelection(hairpin));
    }

    @Test
    void testRevalidateDecorationSelectionClearsHairpinWhenNoLongerOnLine() {
        var line = twoNoteLine();
        var hairpin = makeHairpin(line);
        line.addRangeElement(hairpin);
        var state = new LineSelectionState(line);
        state.selectDecoration(new SelectedDecoration.HairpinSelection(hairpin));

        // Simulates an undo/redo that removed the hairpin without clearing the selection.
        line.removeRangeElement(hairpin);

        assertThat(state.revalidateDecorationSelection()).isTrue();
        assertThat(state.getSelectedDecoration()).isNull();
    }

    // -- revalidateElementSelection --

    @Test
    void testRevalidateElementSelectionNoOpWhenNothingSelected() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);

        assertThat(state.revalidateElementSelection()).isFalse();
    }

    @Test
    void testRevalidateElementSelectionKeepsSelectionStillWithinTheLine() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.revalidateElementSelection()).isFalse();
        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
    }

    @Test
    void testRevalidateElementSelectionClearsSelectionRunningPastTheEndOfTheLine() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        // Simulates undoing an insertion: the element the selection reached is gone.
        line.removeElement(1);

        assertThat(state.revalidateElementSelection()).isTrue();
        assertThat(state.hasElementSelection()).isFalse();
        assertThat(state.getSelectionEnd()).isEqualTo(-1);
    }

    /**
     * Pins down the choice of {@link Line#elementCount()} over
     * {@link Line#effectiveElementCount()}. The two differ only on a line that ends with a
     * barline the song maintains itself — which every line in a real song does. The terminal
     * is a real, indexable element, so a selection reaching it is usable and must survive.
     * Bounding the check by the terminal-excluding count would clear such a selection on
     * every song change, and the user would watch their selection vanish after an unrelated
     * edit.
     */
    @Test
    void testRevalidateElementSelectionKeepsSelectionReachingTheSongOwnedTerminal() {
        var line = twoNoteLine();
        var terminal = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
        line.addElement(terminal);
        when(line.getSong().isAutoMaintainedTerminal(terminal, line)).thenReturn(true);

        assertThat(line.effectiveElementCount())
            .as("the fixture must make the two counts differ, or this test proves nothing")
            .isLessThan(line.elementCount());

        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(TERMINAL_ELEMENT_INDEX);

        assertThat(state.revalidateElementSelection()).isFalse();
        assertThat(state.getSelectionEnd()).isEqualTo(TERMINAL_ELEMENT_INDEX);
    }

    /**
     * The crash this guards against: every selection query walks the selected range, so a
     * range left pointing past the end of a shrunk line throws before anything else can
     * notice it is stale.
     */
    @Test
    void testRevalidatedSelectionMakesTupletQuerySafeOnAShrunkLine() {
        var line = twoNoteLine();
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        line.removeElement(1);
        state.revalidateElementSelection();

        assertThat(state.canToggleTuplet().canToggle()).isFalse();
    }

    // -- canToggleBeaming / canToggleTuplet with grace notes (refs #592) --

    /**
     * Builds a detached line from {@code types} and returns a state with all of it selected.
     */
    private static LineSelectionState selectAllOf(ElementType... types) {
        var line = withQuarterBeat(detachedLine());

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
        var line = withQuarterBeat(detachedLine());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(1), TupletAction.Tuplet.TRIPLET.getSize()));
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
        var line = withQuarterBeat(detachedLine());
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
        var line = withQuarterBeat(detachedLine());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));
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
    void testPartialCoverageOfTripletCannotToggleButStillReportsTheTuplet() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle())
            .as("a strict sub-range offers no creation decision")
            .isFalse();
        assertThat(info.validGrades()).isEmpty();
        assertThat(info.existing())
            .isNotNull()
            .extracting(tuplet -> tuplet != null ? tuplet.getGrade() : 0)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isFalse();
    }

    /**
     * The set must name the grades the span could really become, not simply every grade.
     * Three quarter notes at a quarter beat total three quarters, so only a 3 and a 6 say
     * anything, and the other four are each refused for a different reason: a 2 leaves a
     * dotted quarter with no conventional span, a 4 leaves a dotted eighth with none, a 5
     * and a 7 do not divide the span evenly at all.
     */
    @Test
    void testValidGradesNamesOnlyTheGradesTheSpanCanBecome() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        var info = state.canToggleTuplet();

        assertThat(info.validGrades())
            .as("three quarters at a quarter beat are notatable as 3:2 or 6:4 and nothing else")
            .containsExactlyInAnyOrder(
                TupletAction.Tuplet.TRIPLET.getSize(), TupletAction.Tuplet.SEXTUPLET.getSize());
    }

    /**
     * The counterpart to the case above: a span that only one grade fits. Five eighths
     * divide evenly by five and by nothing else in range, so every other grade leaves a
     * written value that is not a note.
     */
    @Test
    void testValidGradesCanNameASingleGrade() {
        var line = withQuarterBeat(detachedLine());

        for (var i = 0; i < QUINTUPLET_NOTE_COUNT; i++) {
            line.addElement(ElementType.QUAVER.newInstance());
        }

        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(QUINTUPLET_NOTE_COUNT - 1);

        var info = state.canToggleTuplet();

        assertThat(info.validGrades())
            .as("five eighths at a quarter beat are a quintuplet or nothing")
            .containsExactly(TupletAction.Tuplet.QUINTUPLET.getSize());
    }

    /**
     * A rest carries written duration exactly as a note does, so a span containing one is
     * still a candidate. Before this rule a rest anywhere in the selection disabled the
     * tuplet control outright.
     */
    @Test
    void testValidGradesCountsARestAsPartOfTheSpan() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        var info = state.canToggleTuplet();

        assertThat(info.validGrades())
            .as("a rest contributes its duration, so the span reads the same as three notes")
            .containsExactlyInAnyOrder(
                TupletAction.Tuplet.TRIPLET.getSize(), TupletAction.Tuplet.SEXTUPLET.getSize());
    }

    @Test
    void testSelectionSpanningTwoDifferentTupletsCannotToggle() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(1), TupletAction.Tuplet.DUPLET.getSize()));
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(2), line.getElement(3), TupletAction.Tuplet.TRIPLET.getSize()));
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
    void testSelectAllClearsLineSelection() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());

        var state = new LineSelectionState(line);
        state.setLineSelected(true);

        state.selectAll();

        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
    }

    @Test
    void testSelectAllOnLineWithExactlyOneElementSelectsThatElement() {
        // The boundary between "nothing to select" and "something to select": one element
        // collapses the range to a single index rather than tripping the empty-line guard.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());

        var state = new LineSelectionState(line);
        state.setLineSelected(true);

        state.selectAll();

        assertThat(state.isLineSelected()).isFalse();
        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(0);
    }

    @Test
    void testSelectAllOnEmptyLineLeavesLineSelectionIntact() {
        var song = new Song();
        var line = song.getLine(0);

        // Default song seeds the first (and only) line with just the final barline,
        // so there is no element selection to swap the line selection for.
        var state = new LineSelectionState(line);
        state.setLineSelected(true);

        state.selectAll();

        assertThat(state.isLineSelected()).isTrue();
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

    @Test
    void testIsElementSelectedIncludesBreathMarkAfterSelectionEnd() {
        // [CROTCHET(0), BREATH_MARK(1), CROTCHET(2)] — the breath mark belongs to the
        // crotchet before it and is deleted with it, so selecting only that crotchet must
        // report the breath mark as selected too (refs #698).
        var line = lineWith(ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET);
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);

        assertThat(state.isElementSelected(1)).as("breath mark after the selection").isTrue();
        assertThat(state.isElementSelected(2)).as("crotchet past the breath mark").isFalse();
    }

    @Test
    void testIsElementSelectedIncludesBreathMarkAfterAMultiElementSelection() {
        // [CROTCHET(0), CROTCHET(1), BREATH_MARK(2), CROTCHET(3)] — dragging across the two
        // crotchets must carry the breath mark trailing the range *end*. Selecting a single
        // element leaves begin and end equal, so only a range this wide can tell the two
        // apart: extending from begin instead would reach index 1, not the breath mark.
        var line = lineWith(
            ElementType.CROTCHET, ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET
        );
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.isElementSelected(2)).as("breath mark after the range end").isTrue();
        assertThat(state.isElementSelected(3)).as("crotchet past the breath mark").isFalse();
    }

    @Test
    void testIsElementSelectedExcludesABreathMarkBeforeTheSelection() {
        // [CROTCHET(0), BREATH_MARK(1), CROTCHET(2), CROTCHET(3)] — the breath mark belongs
        // to crotchet 0, which is outside the selection, so selecting crotchet 2 must leave
        // it unselected. Only a lookup below the selection start reaches this branch, and
        // making the breath-mark rule symmetric would silently break it (refs #698).
        var line = lineWith(
            ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET, ElementType.CROTCHET
        );
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(2);

        assertThat(state.isElementSelected(1)).as("breath mark before the selection").isFalse();
        assertThat(state.isElementSelected(0)).as("crotchet owning that breath mark").isFalse();
    }

    @Test
    void testIsElementSelectedHandlesASelectionEndingAtTheLastElement() {
        // [CROTCHET(0), CROTCHET(1)] — with the selection on the last element there is no
        // successor to inspect for a breath mark, so the range must come back unextended
        // rather than reaching past the end of the line.
        var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(1);

        assertThat(state.isElementSelected(1)).as("the selected last element").isTrue();
        assertThat(state.isElementSelected(2)).as("one index past the end of the line").isFalse();
    }

    @Test
    void testIsElementSelectedCountsTrailingBreathMarkWithoutWideningTheRange() {
        // The trailing breath mark reads as selected without joining the range: the range is
        // what the toolbar actions reflect and what a tie or a beam is built from, and a
        // single-note selection must stay single (refs #698).
        var line = lineWith(ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET);
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);

        assertThat(state.isElementSelected(1))
            .as("breath mark reads as selected")
            .isTrue();
        assertThat(state.getSelectionEnd())
            .as("breath mark does not move the selection end")
            .isEqualTo(0);
        assertThat(state.getSelectionSize())
            .as("breath mark does not grow the selection")
            .isEqualTo(1);
        assertThat(state.getSingleSelectedElement())
            .as("the selection is still the single clicked crotchet")
            .isSameAs(line.getElement(0));
    }

    @Test
    void testIsElementSelectedReturnsFalseForLeadingBreathMarkWithNoSelection() {
        // With nothing selected, nothing is selected — including index 0. The fixture leads
        // with a breath mark because that is the input that exposes a missing guard: without
        // the early return, the empty range (-1) would be extended onto the breath mark and
        // report it as selected. Insertion forbids a breath mark at index 0, so this line is
        // not reachable through the UI; it is here to keep the query correct on its own terms
        // rather than by way of an invariant enforced in another subsystem.
        var line = lineWith(ElementType.BREATH_MARK, ElementType.CROTCHET);
        var state = new LineSelectionState(line);

        assertThat(state.isElementSelected(0))
            .as("leading breath mark with no selection")
            .isFalse();
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
        state.selectDecoration(new SelectedDecoration.SlideSelection(0));

        var callbackCount = new int[]{0};
        state.setSelectionChangeCallback(() -> callbackCount[0]++);

        state.setSelectionFromClick(1);

        assertThat(state.getSelectionBegin()).isEqualTo(1);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
        assertThat(state.getSelectionAnchor()).isEqualTo(1);
        assertThat(state.getSelectedDecoration()).isNull();
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

        state.selectDecoration(new SelectedDecoration.SlideSelection(0));
        assertThat(callbackCount[0]).isEqualTo(5);

        state.resetElementSelection();
        assertThat(callbackCount[0]).isEqualTo(6);

        state.clearSelection();
        assertThat(callbackCount[0]).isEqualTo(7);
    }

    @Test
    void testSelectionContainingRestCanToggle() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle())
            .as("a rest carries written duration, so it belongs inside a new tuplet")
            .isTrue();
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
    void testCanToggleTieWithTwoSeparatorsSetsCanTieFalse() {
        // [CROTCHET(0), SINGLE_BARLINE(1), SINGLE_BARLINE(2), CROTCHET(3)] — a tie may
        // span at most one separator, so two in a row must be rejected (refs #527).
        var state = selectAllOf(
            ElementType.CROTCHET,
            ElementType.SINGLE_BARLINE,
            ElementType.SINGLE_BARLINE,
            ElementType.CROTCHET
        );

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
    void testCanToggleTieAcrossBarlineSetsCanTieTrue() {
        // [CROTCHET(0), SINGLE_BARLINE(1), CROTCHET(2)] — a barline between two
        // same-pitch notes must not block the tie (refs #527).
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.SINGLE_BARLINE, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isTrue();
        assertThat(state.getCanTie()).isEqualTo(true);
        assertThat(state.getExistingTie()).isNull();
    }

    @Test
    void testCanToggleTieAcrossRepeatSetsCanTieTrue() {
        // [CROTCHET(0), REPEAT_RIGHT(1), CROTCHET(2)] — a repeat between two
        // same-pitch notes must not block the tie (refs #527).
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.REPEAT_RIGHT, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isTrue();
        assertThat(state.getCanTie()).isEqualTo(true);
        assertThat(state.getExistingTie()).isNull();
    }

    @Test
    void testCanToggleTieAcrossBreathMarkSetsCanTieTrue() {
        // [CROTCHET(0), BREATH_MARK(1), CROTCHET(2)] — a breath mark is the third kind of
        // separator a tie may span, alongside barlines and repeats (refs #527).
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isTrue();
        assertThat(state.getCanTie()).isEqualTo(true);
        assertThat(state.getExistingTie()).isNull();
    }

    @Test
    void testCanToggleTieAcrossDoubleBarlineSetsCanTieTrue() {
        // [CROTCHET(0), DOUBLE_BARLINE(1), CROTCHET(2)] — an ordinary double barline is a
        // section break, not the end of the piece, so a tie may span it (refs #527).
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.DOUBLE_BARLINE, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isTrue();
        assertThat(state.getCanTie()).isEqualTo(true);
        assertThat(state.getExistingTie()).isNull();
    }

    @Test
    void testCanToggleTieAcrossFinalBarlineSetsCanTieFalse() {
        // [CROTCHET(0), FINAL_DOUBLE_BARLINE(1), CROTCHET(2)] — a final barline ends the
        // piece, so no note may sound across it (refs #527).
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithNoteBetweenTwoNotesSetsCanTieFalse() {
        // [CROTCHET(0), CROTCHET(1), CROTCHET(2)] — a real note between the two
        // endpoints is not transparent to a tie, unlike a barline or repeat.
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithRestBetweenTwoNotesSetsCanTieFalse() {
        // [CROTCHET(0), CROTCHET_REST(1), CROTCHET(2)] — a rest takes time, so the notes
        // on either side are not adjacent and cannot be tied (refs #527).
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.CROTCHET_REST, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithGraceNoteBetweenTwoNotesSetsCanTieFalse() {
        // [CROTCHET(0), GRACE_QUAVER(1), CROTCHET(2)] — a grace note is transparent to a
        // beam or tuplet, but not to a tie: only a separator may sit between tied notes.
        var state = selectAllOf(
            ElementType.CROTCHET, ElementType.GRACE_QUAVER, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieWithLeadingRestSetsCanTieFalse() {
        // [CROTCHET_REST(0), CROTCHET(1), CROTCHET(2)] — the selection's endpoints are the
        // tied notes themselves. Padding at the edge is not skipped, so a leading rest
        // makes this an invalid tie selection rather than a tie of notes 1 and 2.
        var state = selectAllOf(
            ElementType.CROTCHET_REST, ElementType.CROTCHET, ElementType.CROTCHET
        );

        var result = state.canToggleTie();

        assertThat(result).isFalse();
        assertThat(state.getCanTie()).isEqualTo(false);
    }

    @Test
    void testCanToggleTieAcrossBarlineWithPitchMismatchSetsCanTieFalse() {
        // A separator does not excuse the pitch rule: tied notes must share a pitch.
        var line = detachedLine();
        var note0 = ElementType.CROTCHET.newInstance();
        note0.setStaffPosition(0);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(1);
        line.addElement(note0);
        line.addElement(ElementType.SINGLE_BARLINE.newInstance());
        line.addElement(note2);
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

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
    void testCanModifyStemDirectionReturnsTrueWhenAtLeastOneStemmedNoteInRange() {
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

    @Test
    void testCanModifyStemDirectionReturnsFalseWhenSelectionIsAllWholeNotes() {
        var line = detachedLine();
        line.addElement(ElementType.SEMIBREVE.newInstance());
        line.addElement(ElementType.SEMIBREVE.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canModifyStemDirection()).isFalse();
    }

    @Test
    void testCanModifyStemDirectionReturnsTrueWhenWholeNoteSelectedWithStemmedNote() {
        var line = detachedLine();
        line.addElement(ElementType.SEMIBREVE.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        assertThat(state.canModifyStemDirection()).isTrue();
    }
}
