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

package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.MusicEditOperations.HairpinActionState;
import songscribe.ui.MusicEditOperations.HairpinResolution;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.hit.HitTarget;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Tests for {@link MusicEditOperations#resolveHairpinAction(Hairpin.Kind)} — the
 * per-menu-item decision that drives that item's hairpin label and the span its
 * add/extend mutation uses.
 *
 * <p>Grouped by the steps of that decision: the input guard, structural eligibility of
 * the selection, and the relation analysis against nearby hairpins.
 */
class HairpinActionStateTest extends UnitTest {

    // Element positions within a fixture's line
    private static final int IDX_0 = 0;
    private static final int IDX_1 = 1;
    private static final int IDX_2 = 2;
    private static final int IDX_3 = 3;
    private static final int IDX_4 = 4;
    private static final int IDX_5 = 5;
    private static final int IDX_6 = 6;
    private static final int IDX_8 = 8;
    private static final int IDX_9 = 9;
    private static final int IDX_10 = 10;
    private static final int IDX_14 = 14;
    private static final int IDX_15 = 15;
    private static final int IDX_16 = 16;
    private static final int IDX_18 = 18;
    private static final int IDX_19 = 19;

    // Line lengths the fixtures are built at
    private static final int NOTE_COUNT_3 = 3;
    private static final int NOTE_COUNT_4 = 4;
    private static final int NOTE_COUNT_5 = 5;
    private static final int NOTE_COUNT_6 = 6;
    private static final int NOTE_COUNT_7 = 7;
    private static final int NOTE_COUNT_8 = 8;
    private static final int NOTE_COUNT_9 = 9;
    private static final int NOTE_COUNT_10 = 10;
    private static final int NOTE_COUNT_20 = 20;

    /** A line, its coordinator and the operations under test, all sharing one song mock. */
    private record Fixture(Line line, SelectionCoordinator coordinator, MusicEditOperations ops) {

        /** Selects the inclusive element range [begin, end]. */
        void select(int begin, int end) {
            ReflectionTestHelper.selectRange(coordinator, begin, end);
        }

        /** Adds a crescendo spanning [anchor, end] without going through the merge logic. */
        Crescendo addCrescendo(int anchor, int end) {
            var hairpin = new Crescendo(line.getElement(anchor), line.getElement(end));
            line.addSpan(hairpin);
            return hairpin;
        }

        /** Adds a diminuendo spanning [anchor, end] without going through the merge logic. */
        @SuppressWarnings("UnusedReturnValue")
        Diminuendo addDiminuendo(int anchor, int end) {
            var hairpin = new Diminuendo(line.getElement(anchor), line.getElement(end));
            line.addSpan(hairpin);
            return hairpin;
        }

        HairpinResolution resolve(Hairpin.Kind kind) {
            return ops.resolveHairpinAction(kind);
        }

        /** Asserts the resolution for {@code kind} is {@code state} over [spanBegin, spanEnd]. */
        void assertResolves(
            Hairpin.Kind kind,
            HairpinActionState state,
            int spanBegin,
            int spanEnd,
            String because
        ) {
            var resolution = resolve(kind);
            assertThat(resolution.state()).as(because).isEqualTo(state);
            assertThat(resolution.spanBegin()).as(because).isEqualTo(spanBegin);
            assertThat(resolution.spanEnd()).as(because).isEqualTo(spanEnd);
        }
    }

    /** Builds a fixture whose line holds exactly the given elements, in order. */
    private static Fixture fixtureWith(StaffElement... elements) {
        var song = minimalSongMock();
        var line = new Line(song);

        for (var element : elements) {
            line.addElement(element);
        }

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        return new Fixture(line, coordinator, new MusicEditOperations(song, coordinator));
    }

    /** Builds a fixture whose line holds {@code count} crotchets. */
    private static Fixture fixtureWithNotes(int count) {
        return fixtureWith(crotchets(count));
    }

    /** Builds a fixture of {@code count} crotchets whose element at {@code restIndex} is a rest. */
    private static Fixture fixtureWithRestAt(int count, int restIndex) {
        var elements = crotchets(count);
        elements[restIndex] = crotchetRest();
        return fixtureWith(elements);
    }

    /** An array of {@code count} crotchets, for a caller that then substitutes one element. */
    private static StaffElement[] crotchets(int count) {
        var elements = new StaffElement[count];

        for (var i = 0; i < count; i++) {
            elements[i] = crotchet();
        }

        return elements;
    }

    // -----------------------------------------------------------------------
    // Step 0 — input guard
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InputGuard {

        @Test
        void testNoActiveSelectionStateIsIneligible() {
            // A coordinator with no registered line returns null from getRange().
            var ops = new MusicEditOperations(minimalSongMock(), new SelectionCoordinator(mock(ScoreView.class)));

            assertThat(ops.resolveHairpinAction(Hairpin.Kind.CRESCENDO).state())
                .as("a null selection state must resolve to INELIGIBLE")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testDecorationSelectionIsIneligibleWithoutThrowing() {
            // A hairpin selection leaves the element indices at -1, and Line.getElement
            // does not bounds check — reaching an element index here would throw.
            var fixture = fixtureWithNotes(NOTE_COUNT_4);
            var hairpin = fixture.addCrescendo(IDX_0, IDX_2);
            fixture.coordinator().select(new HitTarget.Hairpin(hairpin));

            assertThatCode(() -> fixture.resolve(Hairpin.Kind.CRESCENDO))
                .as("a -1/-1 decoration selection must not reach an element index")
                .doesNotThrowAnyException();

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a decoration selection must resolve to INELIGIBLE")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testIneligibleResolutionCarriesNoSpan() {
            // A lone note with no hairpin nearby still fails the two-column gate.
            var fixture = fixtureWithNotes(NOTE_COUNT_3);
            fixture.select(IDX_1, IDX_1);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.INELIGIBLE);
            assertThat(resolution.spanBegin())
                .as("an INELIGIBLE resolution must not hand a usable span to the execution path")
                .isEqualTo(-1);
            assertThat(resolution.spanEnd()).isEqualTo(-1);
        }
    }

    // -----------------------------------------------------------------------
    // Step 1 — structural eligibility of the selection
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StructuralEligibility {

        @Test
        void testPitchedNotesOnlySelectionCanAdd() {
            var fixture = fixtureWithNotes(NOTE_COUNT_3);
            fixture.select(IDX_0, IDX_2);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.CAN_ADD);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_0);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_2);
        }

        @Test
        void testGraceNoteAtEndIsIneligible() {
            var fixture = fixtureWith(crotchet(), crotchet(), graceQuaver());
            fixture.select(IDX_0, IDX_2);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a grace note cannot be the end of a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testRestAtBeginIsIneligible() {
            var fixture = fixtureWith(crotchetRest(), crotchet(), crotchet());
            fixture.select(IDX_0, IDX_2);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a rest is neither a pitched note nor a grace note, so it cannot begin a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testStructuralBoundaryInSelectionIsIneligible() {
            var fixture = fixtureWith(crotchet(), doubleBarline(), crotchet());
            fixture.select(IDX_0, IDX_2);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a selection crossing a double barline cannot host a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testSingleBarlineInSelectionCanAdd() {
            var fixture = fixtureWith(crotchet(), singleBarline(), crotchet());
            fixture.select(IDX_0, IDX_2);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a single barline is not a structural boundary")
                .isEqualTo(HairpinActionState.CAN_ADD);
        }

        @Test
        void testGraceNoteAtBeginWithPitchedHostCanAdd() {
            // The grace note belongs to the crotchet that follows it, so the hairpin
            // anchors on the grace note itself and covers what the user selected.
            var fixture = fixtureWith(graceQuaver(), crotchet(), crotchet());
            fixture.select(IDX_0, IDX_2);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.CAN_ADD);
            assertThat(resolution.spanBegin())
                .as("the span must anchor on the grace note, not on its host")
                .isEqualTo(IDX_0);
        }

        @Test
        void testGraceNoteAtBeginWithUnpitchedHostIsIneligible() {
            var fixture = fixtureWith(graceQuaver(), crotchetRest(), crotchet());
            fixture.select(IDX_0, IDX_2);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a grace note whose host is not a pitched note cannot begin a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testLoneGraceNoteIsIneligible() {
            // The end test rejects this before the host lookup can run off the end
            // of the line.
            var fixture = fixtureWith(graceQuaver());
            fixture.select(IDX_0, IDX_0);

            assertThatCode(() -> fixture.resolve(Hairpin.Kind.CRESCENDO))
                .as("a lone grace note must be rejected without an out-of-bounds host lookup")
                .doesNotThrowAnyException();

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state()).isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testGraceNoteAnchorsAnExtensionReachingItsHost() {
            var elements = crotchets(NOTE_COUNT_7);
            elements[IDX_2] = graceQuaver();
            var fixture = fixtureWith(elements);
            fixture.addCrescendo(IDX_3, IDX_6);
            fixture.select(IDX_2, IDX_2);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.EXTEND,
                IDX_2,
                IDX_6,
                "the host lookahead is bounded by the resolved span end, not by the selection end, "
                    + "so the grace note's host at 3 is within reach and the grace note may anchor "
                    + "the extension");
        }

        @Test
        void testRestBetweenPitchedNotesCanAdd() {
            var fixture = fixtureWith(crotchet(), crotchetRest(), crotchet());
            fixture.select(IDX_0, IDX_2);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("only the endpoints are constrained — a rest inside the span is fine")
                .isEqualTo(HairpinActionState.CAN_ADD);
        }
    }

    // -----------------------------------------------------------------------
    // Step 2 — relation analysis with no hairpin nearby
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NoCandidates {

        @Test
        void testSingleNoteWithNoHairpinIsIneligible() {
            var fixture = fixtureWithNotes(NOTE_COUNT_3);
            fixture.select(IDX_1, IDX_1);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a new hairpin needs something to slope across")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testGraceNoteAndItsHostAloneIsIneligible() {
            // Two elements, but one pitched note — the pair is a single note musically.
            var fixture = fixtureWith(graceQuaver(), crotchet());
            fixture.select(IDX_0, IDX_1);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a grace note does not add to the pitched-note count")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }
    }

    // -----------------------------------------------------------------------
    // Step 2 — extension against same-type candidates
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Extension {

        @Test
        void testSingleNoteAfterCrescendoExtendsIt() {
            // Adjacency counts: Line.addHairpin would absorb this crescendo anyway.
            var fixture = fixtureWithNotes(NOTE_COUNT_5);
            fixture.addCrescendo(IDX_0, IDX_2);
            fixture.select(IDX_3, IDX_3);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state())
                .as("a single note touching a crescendo extends it, with no count requirement")
                .isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_0);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_3);
        }

        @Test
        void testSingleNoteBeforeCrescendoExtendsIt() {
            var fixture = fixtureWithNotes(NOTE_COUNT_5);
            fixture.addCrescendo(IDX_2, IDX_4);
            fixture.select(IDX_1, IDX_1);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_1);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_4);
        }

        @Test
        void testCrescendoExtendsRight() {
            var fixture = fixtureWithNotes(NOTE_COUNT_5);
            fixture.addCrescendo(IDX_0, IDX_2);
            fixture.select(IDX_2, IDX_4);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_0);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_4);
        }

        @Test
        void testCrescendoExtendsLeft() {
            var fixture = fixtureWithNotes(NOTE_COUNT_5);
            fixture.addCrescendo(IDX_2, IDX_4);
            fixture.select(IDX_0, IDX_2);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_0);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_4);
        }

        @Test
        void testCrescendoExtendsBothDirections() {
            var fixture = fixtureWithNotes(NOTE_COUNT_6);
            fixture.addCrescendo(IDX_2, IDX_3);
            fixture.select(IDX_1, IDX_4);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_1);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_4);
        }

        @Test
        void testDiminuendoExtendsRight() {
            var fixture = fixtureWithNotes(NOTE_COUNT_5);
            fixture.addDiminuendo(IDX_0, IDX_2);
            fixture.select(IDX_2, IDX_4);

            var resolution = fixture.resolve(Hairpin.Kind.DIMINUENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_0);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_4);
        }

        @Test
        void testDiminuendoExtendsLeft() {
            var fixture = fixtureWithNotes(NOTE_COUNT_5);
            fixture.addDiminuendo(IDX_2, IDX_4);
            fixture.select(IDX_0, IDX_2);

            var resolution = fixture.resolve(Hairpin.Kind.DIMINUENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_0);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_4);
        }

        @Test
        void testDiminuendoExtendsBothDirections() {
            var fixture = fixtureWithNotes(NOTE_COUNT_6);
            fixture.addDiminuendo(IDX_2, IDX_3);
            fixture.select(IDX_1, IDX_4);

            var resolution = fixture.resolve(Hairpin.Kind.DIMINUENDO);

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin()).isEqualTo(IDX_1);
            assertThat(resolution.spanEnd()).isEqualTo(IDX_4);
        }

        @Test
        void testTwoFlankingCrescendosMergeIntoOneSpan() {
            var fixture = fixtureWithNotes(NOTE_COUNT_9);
            fixture.addCrescendo(IDX_0, IDX_2);
            fixture.addCrescendo(IDX_6, IDX_8);
            fixture.select(IDX_3, IDX_5);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.state())
                .as("same-type neighbors are absorbed, not blocking")
                .isEqualTo(HairpinActionState.EXTEND);
            assertThat(resolution.spanBegin())
                .as("the span reaches back over the left-hand crescendo it absorbs")
                .isEqualTo(IDX_0);
            assertThat(resolution.spanEnd())
                .as("and forward over the right-hand one")
                .isEqualTo(IDX_8);
        }
    }

    // -----------------------------------------------------------------------
    // Step 2 — blocking
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Blocking {

        @Test
        void testOppositeTypeClearOfTheUnionDoesNotBlock() {
            var fixture = fixtureWithNotes(NOTE_COUNT_9);
            fixture.addCrescendo(IDX_0, IDX_2);
            fixture.addDiminuendo(IDX_6, IDX_8);
            fixture.select(IDX_3, IDX_5);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.EXTEND,
                IDX_0,
                IDX_5,
                "the union stops at the selection's end, short of the diminuendo, so nothing collides");
        }

        @Test
        void testOppositeTypeOverlappingTheUnionBlocks() {
            var fixture = fixtureWithNotes(NOTE_COUNT_9);
            fixture.addCrescendo(IDX_0, IDX_2);
            fixture.addDiminuendo(IDX_4, IDX_8);
            fixture.select(IDX_3, IDX_5);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("the union shares more than one element with the diminuendo, which is a collision")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testBothTypesOverlappingSelectionBlocks() {
            var fixture = fixtureWithNotes(NOTE_COUNT_8);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.addDiminuendo(IDX_2, IDX_6);
            fixture.select(IDX_3, IDX_5);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a crescendo and a diminuendo cannot occupy one span")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testSelectionInsideOneHairpinBlocks() {
            var fixture = fixtureWithNotes(NOTE_COUNT_6);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.select(IDX_1, IDX_3);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a hairpin already covering the whole selection has nothing to extend")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testSelectionExactlyMatchingHairpinBlocks() {
            var fixture = fixtureWithNotes(NOTE_COUNT_6);
            fixture.addCrescendo(IDX_1, IDX_3);
            fixture.select(IDX_1, IDX_3);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("coverage is inclusive at both ends, so an exact match extends nothing")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testBlockedResolutionCarriesNoSpan() {
            var fixture = fixtureWithNotes(NOTE_COUNT_6);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.select(IDX_1, IDX_3);

            var resolution = fixture.resolve(Hairpin.Kind.CRESCENDO);

            assertThat(resolution.spanBegin())
                .as("a BLOCKED resolution must not hand a usable span to the execution path")
                .isEqualTo(-1);
            assertThat(resolution.spanEnd()).isEqualTo(-1);
        }

        @Test
        void testUnionSpanCrossingBoundaryBlocks() {
            // A song saved before the restriction existed can hold a hairpin that already
            // crosses a double barline. The selection itself crosses nothing, but absorbing
            // that hairpin would carry the span over the barline — refuse rather than widen.
            var fixture = fixtureWith(
                crotchet(), crotchet(), crotchet(), doubleBarline(), crotchet(), crotchet());
            fixture.addCrescendo(IDX_0, IDX_3);
            fixture.select(IDX_4, IDX_5);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("the union span is re-checked against the structural boundary")
                .isEqualTo(HairpinActionState.BLOCKED);
        }
    }

    // -----------------------------------------------------------------------
    // Step 2 — back-to-back wedges
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BackToBack {

        // The next two tests are the point of the whole feature and belong together:
        // one selection, two menu items, two different honest answers.

        @Test
        void testDiminuendoAfterCrescendoCanAdd() {
            var fixture = fixtureWithNotes(NOTE_COUNT_9);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.select(IDX_4, IDX_8);

            fixture.assertResolves(
                Hairpin.Kind.DIMINUENDO,
                HairpinActionState.CAN_ADD,
                IDX_4,
                IDX_8,
                "the crescendo shares only the element the diminuendo begins on, which is where "
                    + "one wedge is allowed to hand off to the next");
        }

        @Test
        void testCrescendoOnTheSameSelectionExtendsInstead() {
            var fixture = fixtureWithNotes(NOTE_COUNT_9);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.select(IDX_4, IDX_8);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.EXTEND,
                IDX_0,
                IDX_8,
                "the same selection that adds a back-to-back diminuendo extends the crescendo — "
                    + "each menu item resolves for its own type");
        }

        @Test
        void testCrescendoBeforeDiminuendoCanAdd() {
            var fixture = fixtureWithNotes(NOTE_COUNT_9);
            fixture.addDiminuendo(IDX_4, IDX_8);
            fixture.select(IDX_0, IDX_4);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.CAN_ADD,
                IDX_0,
                IDX_4,
                "the hand-off element may be the new wedge's end just as well as its anchor");
        }

        @Test
        void testOppositeTypeOverlappingByMoreThanOneElementBlocks() {
            var fixture = fixtureWithNotes(NOTE_COUNT_9);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.select(IDX_2, IDX_8);

            assertThat(fixture.resolve(Hairpin.Kind.DIMINUENDO).state())
                .as("the selection shares elements 2 through 4 with the crescendo, which is an "
                    + "overlap rather than a hand-off")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testOppositeTypeOneElementGapCanAdd() {
            var fixture = fixtureWithNotes(NOTE_COUNT_10);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.select(IDX_6, IDX_9);

            fixture.assertResolves(
                Hairpin.Kind.DIMINUENDO,
                HairpinActionState.CAN_ADD,
                IDX_6,
                IDX_9,
                "a gap of exactly one element used to be blocked while a gap of two or more was "
                    + "allowed — nothing is shared with the crescendo, so there is nothing to block");
        }

        @Test
        void testSelectionBetweenOppositeTypesExtendsTheSameType() {
            var fixture = fixtureWithNotes(NOTE_COUNT_10);
            fixture.addDiminuendo(IDX_0, IDX_3);
            fixture.addCrescendo(IDX_6, IDX_9);
            fixture.select(IDX_3, IDX_6);

            fixture.assertResolves(
                Hairpin.Kind.DIMINUENDO,
                HairpinActionState.EXTEND,
                IDX_0,
                IDX_6,
                "the diminuendo absorbs the selection and hands off to the crescendo on their one "
                    + "shared element");
        }

        @Test
        void testSelectionMeetingAnOppositeTypeTooEarlyBlocks() {
            var fixture = fixtureWithNotes(NOTE_COUNT_10);
            fixture.addDiminuendo(IDX_0, IDX_3);
            fixture.addCrescendo(IDX_5, IDX_9);
            fixture.select(IDX_3, IDX_6);

            assertThat(fixture.resolve(Hairpin.Kind.DIMINUENDO).state())
                .as("the extended diminuendo would share elements 5 and 6 with the crescendo")
                .isEqualTo(HairpinActionState.BLOCKED);
        }
    }

    // -----------------------------------------------------------------------
    // Step 1 — the union has to converge before it is checked
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class UnionConvergence {

        @Test
        void testAbsorbedHairpinBringsAFurtherOneIntoReach() {
            var fixture = fixtureWithNotes(NOTE_COUNT_20);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.addCrescendo(IDX_10, IDX_14);
            fixture.addCrescendo(IDX_15, IDX_18);
            fixture.select(IDX_5, IDX_9);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.EXTEND,
                IDX_0,
                IDX_18,
                "one pass reaches only [0, 14], and the crescendo at [15, 18] is one element past "
                    + "that, so a single-pass union would promise a narrower hairpin than "
                    + "Line.addHairpin actually builds");
        }

        @Test
        void testOppositeTypeCollidingOnlyWithTheConvergedUnionBlocks() {
            var fixture = fixtureWithNotes(NOTE_COUNT_20);
            fixture.addCrescendo(IDX_0, IDX_4);
            fixture.addCrescendo(IDX_10, IDX_14);
            fixture.addCrescendo(IDX_15, IDX_18);
            fixture.addDiminuendo(IDX_16, IDX_19);
            fixture.select(IDX_5, IDX_9);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("the diminuendo collides only with the converged union, so a single-pass "
                    + "resolution would let the user create an overlapping pair")
                .isEqualTo(HairpinActionState.BLOCKED);
        }
    }

    // -----------------------------------------------------------------------
    // Rests as endpoints — LilyPond's rule is right-side only
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RestEndpoints {

        @Test
        void testRestAtEndCanAdd() {
            var fixture = fixtureWith(crotchet(), crotchet(), crotchetRest());
            fixture.select(IDX_0, IDX_2);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.CAN_ADD,
                IDX_0,
                IDX_2,
                "a rest bounds a wedge at its left edge, so it may end a hairpin");
        }

        @Test
        void testOneNoteAndTheRestAfterItCanAdd() {
            var fixture = fixtureWith(crotchet(), crotchetRest());
            fixture.select(IDX_0, IDX_1);

            fixture.assertResolves(
                Hairpin.Kind.DIMINUENDO,
                HairpinActionState.CAN_ADD,
                IDX_0,
                IDX_1,
                "the 18000/17323.abc corpus shape — a diminuendo over a single note ending on the "
                    + "next rest — where the trailing rest is the second column");
        }

        @Test
        void testSecondTrailingRestIsIneligible() {
            var fixture = fixtureWith(crotchet(), crotchetRest(), crotchetRest());
            fixture.select(IDX_0, IDX_2);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("a hairpin ends on at most one rest — a wedge running on across a second has "
                    + "nothing left to slope over")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testFirstOfTwoTrailingRestsCanAdd() {
            var fixture = fixtureWith(crotchet(), crotchetRest(), crotchetRest());
            fixture.select(IDX_0, IDX_1);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.CAN_ADD,
                IDX_0,
                IDX_1,
                "the rest closing the run of notes may end the hairpin; only the one after it may "
                    + "not");
        }

        @Test
        void testLoneRestIsIneligible() {
            var fixture = fixtureWith(crotchetRest());
            fixture.select(IDX_0, IDX_0);

            assertThat(fixture.resolve(Hairpin.Kind.DIMINUENDO).state())
                .as("a rest cannot anchor a hairpin, so a lone rest carries nothing")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testRestAfterACrescendoExtendsIt() {
            var fixture = fixtureWithRestAt(NOTE_COUNT_5, IDX_4);
            fixture.addCrescendo(IDX_0, IDX_3);
            fixture.select(IDX_4, IDX_4);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.EXTEND,
                IDX_0,
                IDX_4,
                "the selected rest supplies only the end of the span, which a rest may be");
        }

        @Test
        void testRestBeforeACrescendoIsIneligible() {
            var fixture = fixtureWithRestAt(NOTE_COUNT_9, IDX_4);
            fixture.addCrescendo(IDX_5, IDX_8);
            fixture.select(IDX_4, IDX_4);

            assertThat(fixture.resolve(Hairpin.Kind.CRESCENDO).state())
                .as("the selected rest would anchor the extended span, and LilyPond's rest rule is "
                    + "right-side only")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testRestInheritedAsAnAnchorIsNotRechecked() {
            var fixture = fixtureWithRestAt(NOTE_COUNT_9, IDX_3);
            fixture.addCrescendo(IDX_0, IDX_5);
            fixture.select(IDX_3, IDX_8);

            fixture.assertResolves(
                Hairpin.Kind.CRESCENDO,
                HairpinActionState.EXTEND,
                IDX_0,
                IDX_8,
                "the anchor comes from the absorbed crescendo rather than from the selected rest, "
                    + "so the rest never has to anchor anything");
        }
    }
}
