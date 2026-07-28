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
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.MusicEditOperations.HairpinActionState;
import songscribe.ui.MusicEditOperations.HairpinResolution;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Tests for {@link MusicEditOperations#resolveHairpinAction()} — the single decision
 * that drives both the hairpin menu labels and the span the add/extend mutation uses.
 *
 * <p>Grouped by the steps of that decision: the input guard, structural eligibility of
 * the selection, and the relation analysis against nearby hairpins.
 */
class HairpinActionStateTest extends UnitTest {

    /** A line, its coordinator and the operations under test, all sharing one song mock. */
    private record Fixture(Line line, SelectionCoordinator coordinator, MusicEditOperations ops) {

        /** Selects the inclusive element range [begin, end]. */
        void select(int begin, int end) {
            ReflectionTestHelper.selectRange(coordinator, begin, end);
        }

        /** Adds a crescendo spanning [anchor, end] without going through the merge logic. */
        Crescendo addCrescendo(int anchor, int end) {
            var hairpin = new Crescendo(line.getElement(anchor), line.getElement(end));
            line.addRangeElement(hairpin);
            return hairpin;
        }

        /** Adds a diminuendo spanning [anchor, end] without going through the merge logic. */
        Diminuendo addDiminuendo(int anchor, int end) {
            var hairpin = new Diminuendo(line.getElement(anchor), line.getElement(end));
            line.addRangeElement(hairpin);
            return hairpin;
        }

        HairpinResolution resolve() {
            return ops.resolveHairpinAction();
        }

        LineSelectionState state() {
            var state = coordinator.getActiveSelection();

            if (state == null) {
                throw new AssertionError("the fixture's line must have an active selection state");
            }

            return state;
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
        var elements = new StaffElement[count];

        for (var i = 0; i < count; i++) {
            elements[i] = crotchet();
        }

        return fixtureWith(elements);
    }

    // -----------------------------------------------------------------------
    // Step 0 — input guard
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InputGuard {

        @Test
        void testNoActiveSelectionStateIsIneligible() {
            // A coordinator with no registered line returns null from getActiveSelection().
            var ops = new MusicEditOperations(minimalSongMock(), new SelectionCoordinator());

            assertThat(ops.resolveHairpinAction().state())
                .as("a null selection state must resolve to INELIGIBLE")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testDecorationSelectionIsIneligibleWithoutThrowing() {
            // A hairpin selection leaves the element indices at -1, and Line.getElement
            // does not bounds check — reaching an element index here would throw.
            var fixture = fixtureWithNotes(4);
            var hairpin = fixture.addCrescendo(0, 2);
            fixture.state().selectHairpin(hairpin);

            assertThatCode(fixture::resolve)
                .as("a -1/-1 decoration selection must not reach an element index")
                .doesNotThrowAnyException();

            assertThat(fixture.resolve().state())
                .as("a decoration selection must resolve to INELIGIBLE")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testIneligibleResolutionCarriesNoSpan() {
            var fixture = fixtureWith(crotchet(), crotchetRest());
            fixture.select(0, 1);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.INELIGIBLE);
            assertThat(resolution.spanBegin())
                .as("an INELIGIBLE resolution must not hand a usable span to the execution path")
                .isEqualTo(-1);
            assertThat(resolution.spanEnd()).isEqualTo(-1);
            assertThat(resolution.absorbedHairpins()).isEmpty();
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
            var fixture = fixtureWithNotes(3);
            fixture.select(0, 2);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.CAN_ADD);
            assertThat(resolution.spanBegin()).isEqualTo(0);
            assertThat(resolution.spanEnd()).isEqualTo(2);
        }

        @Test
        void testRestAtEndIsIneligible() {
            var fixture = fixtureWith(crotchet(), crotchet(), crotchetRest());
            fixture.select(0, 2);

            assertThat(fixture.resolve().state())
                .as("a hairpin must end on a pitched note")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testGraceNoteAtEndIsIneligible() {
            var fixture = fixtureWith(crotchet(), crotchet(), graceQuaver());
            fixture.select(0, 2);

            assertThat(fixture.resolve().state())
                .as("a grace note cannot be the end of a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testRestAtBeginIsIneligible() {
            var fixture = fixtureWith(crotchetRest(), crotchet(), crotchet());
            fixture.select(0, 2);

            assertThat(fixture.resolve().state())
                .as("a rest is neither a pitched note nor a grace note, so it cannot begin a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testStructuralBoundaryInSelectionIsIneligible() {
            var fixture = fixtureWith(crotchet(), doubleBarline(), crotchet());
            fixture.select(0, 2);

            assertThat(fixture.resolve().state())
                .as("a selection crossing a double barline cannot host a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testSingleBarlineInSelectionCanAdd() {
            var fixture = fixtureWith(crotchet(), singleBarline(), crotchet());
            fixture.select(0, 2);

            assertThat(fixture.resolve().state())
                .as("a single barline is not a structural boundary")
                .isEqualTo(HairpinActionState.CAN_ADD);
        }

        @Test
        void testGraceNoteAtBeginWithPitchedHostCanAdd() {
            // The grace note belongs to the crotchet that follows it, so the hairpin
            // anchors on the grace note itself and covers what the user selected.
            var fixture = fixtureWith(graceQuaver(), crotchet(), crotchet());
            fixture.select(0, 2);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.CAN_ADD);
            assertThat(resolution.spanBegin())
                .as("the span must anchor on the grace note, not on its host")
                .isEqualTo(0);
        }

        @Test
        void testGraceNoteAtBeginWithUnpitchedHostIsIneligible() {
            var fixture = fixtureWith(graceQuaver(), crotchetRest(), crotchet());
            fixture.select(0, 2);

            assertThat(fixture.resolve().state())
                .as("a grace note whose host is not a pitched note cannot begin a hairpin")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testLoneGraceNoteIsIneligible() {
            // The end test rejects this before the host lookup can run off the end
            // of the line.
            var fixture = fixtureWith(graceQuaver());
            fixture.select(0, 0);

            assertThatCode(fixture::resolve)
                .as("a lone grace note must be rejected without an out-of-bounds host lookup")
                .doesNotThrowAnyException();

            assertThat(fixture.resolve().state()).isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testRestBetweenPitchedNotesCanAdd() {
            var fixture = fixtureWith(crotchet(), crotchetRest(), crotchet());
            fixture.select(0, 2);

            assertThat(fixture.resolve().state())
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
            var fixture = fixtureWithNotes(3);
            fixture.select(1, 1);

            assertThat(fixture.resolve().state())
                .as("a new hairpin needs something to slope across")
                .isEqualTo(HairpinActionState.INELIGIBLE);
        }

        @Test
        void testGraceNoteAndItsHostAloneIsIneligible() {
            // Two elements, but one pitched note — the pair is a single note musically.
            var fixture = fixtureWith(graceQuaver(), crotchet());
            fixture.select(0, 1);

            assertThat(fixture.resolve().state())
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
            var fixture = fixtureWithNotes(5);
            fixture.addCrescendo(0, 2);
            fixture.select(3, 3);

            var resolution = fixture.resolve();

            assertThat(resolution.state())
                .as("a single note touching a crescendo extends it, with no count requirement")
                .isEqualTo(HairpinActionState.EXTEND_CRESCENDO);
            assertThat(resolution.spanBegin()).isEqualTo(0);
            assertThat(resolution.spanEnd()).isEqualTo(3);
        }

        @Test
        void testSingleNoteBeforeCrescendoExtendsIt() {
            var fixture = fixtureWithNotes(5);
            fixture.addCrescendo(2, 4);
            fixture.select(1, 1);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND_CRESCENDO);
            assertThat(resolution.spanBegin()).isEqualTo(1);
            assertThat(resolution.spanEnd()).isEqualTo(4);
        }

        @Test
        void testCrescendoExtendsRight() {
            var fixture = fixtureWithNotes(5);
            fixture.addCrescendo(0, 2);
            fixture.select(2, 4);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND_CRESCENDO);
            assertThat(resolution.spanBegin()).isEqualTo(0);
            assertThat(resolution.spanEnd()).isEqualTo(4);
        }

        @Test
        void testCrescendoExtendsLeft() {
            var fixture = fixtureWithNotes(5);
            fixture.addCrescendo(2, 4);
            fixture.select(0, 2);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND_CRESCENDO);
            assertThat(resolution.spanBegin()).isEqualTo(0);
            assertThat(resolution.spanEnd()).isEqualTo(4);
        }

        @Test
        void testCrescendoExtendsBothDirections() {
            var fixture = fixtureWithNotes(6);
            fixture.addCrescendo(2, 3);
            fixture.select(1, 4);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND_CRESCENDO);
            assertThat(resolution.spanBegin()).isEqualTo(1);
            assertThat(resolution.spanEnd()).isEqualTo(4);
        }

        @Test
        void testDiminuendoExtendsRight() {
            var fixture = fixtureWithNotes(5);
            fixture.addDiminuendo(0, 2);
            fixture.select(2, 4);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND_DIMINUENDO);
            assertThat(resolution.spanBegin()).isEqualTo(0);
            assertThat(resolution.spanEnd()).isEqualTo(4);
        }

        @Test
        void testDiminuendoExtendsLeft() {
            var fixture = fixtureWithNotes(5);
            fixture.addDiminuendo(2, 4);
            fixture.select(0, 2);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND_DIMINUENDO);
            assertThat(resolution.spanBegin()).isEqualTo(0);
            assertThat(resolution.spanEnd()).isEqualTo(4);
        }

        @Test
        void testDiminuendoExtendsBothDirections() {
            var fixture = fixtureWithNotes(6);
            fixture.addDiminuendo(2, 3);
            fixture.select(1, 4);

            var resolution = fixture.resolve();

            assertThat(resolution.state()).isEqualTo(HairpinActionState.EXTEND_DIMINUENDO);
            assertThat(resolution.spanBegin()).isEqualTo(1);
            assertThat(resolution.spanEnd()).isEqualTo(4);
        }

        @Test
        void testTwoFlankingCrescendosMergeIntoOneSpan() {
            var fixture = fixtureWithNotes(9);
            var left = fixture.addCrescendo(0, 2);
            var right = fixture.addCrescendo(6, 8);
            fixture.select(3, 5);

            var resolution = fixture.resolve();

            assertThat(resolution.state())
                .as("same-type neighbours are absorbed, not blocking")
                .isEqualTo(HairpinActionState.EXTEND_CRESCENDO);
            assertThat(resolution.spanBegin()).isEqualTo(0);
            assertThat(resolution.spanEnd()).isEqualTo(8);
            assertThat(resolution.absorbedHairpins())
                .as("both flanking hairpins are absorbed by the extension")
                .containsExactlyInAnyOrder(left, right);
        }
    }

    // -----------------------------------------------------------------------
    // Step 2 — blocking
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Blocking {

        @Test
        void testOppositeTypeNeighbourBlocksExtension() {
            // The crescendo alone would extend; the diminuendo on the other side means
            // no single hairpin can cover the union, so neither action may proceed.
            var fixture = fixtureWithNotes(9);
            fixture.addCrescendo(0, 2);
            fixture.addDiminuendo(6, 8);
            fixture.select(3, 5);

            assertThat(fixture.resolve().state())
                .as("an opposite-type candidate blocks the extension")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testBothTypesOverlappingSelectionBlocks() {
            var fixture = fixtureWithNotes(8);
            fixture.addCrescendo(0, 4);
            fixture.addDiminuendo(2, 6);
            fixture.select(3, 5);

            assertThat(fixture.resolve().state())
                .as("a crescendo and a diminuendo cannot occupy one span")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testSelectionInsideOneHairpinBlocks() {
            var fixture = fixtureWithNotes(6);
            fixture.addCrescendo(0, 4);
            fixture.select(1, 3);

            assertThat(fixture.resolve().state())
                .as("a hairpin already covering the whole selection has nothing to extend")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testSelectionExactlyMatchingHairpinBlocks() {
            var fixture = fixtureWithNotes(6);
            fixture.addCrescendo(1, 3);
            fixture.select(1, 3);

            assertThat(fixture.resolve().state())
                .as("coverage is inclusive at both ends, so an exact match extends nothing")
                .isEqualTo(HairpinActionState.BLOCKED);
        }

        @Test
        void testBlockedResolutionCarriesNoSpan() {
            var fixture = fixtureWithNotes(6);
            fixture.addCrescendo(0, 4);
            fixture.select(1, 3);

            var resolution = fixture.resolve();

            assertThat(resolution.spanBegin())
                .as("a BLOCKED resolution must not hand a usable span to the execution path")
                .isEqualTo(-1);
            assertThat(resolution.spanEnd()).isEqualTo(-1);
            assertThat(resolution.absorbedHairpins()).isEmpty();
        }

        @Test
        void testUnionSpanCrossingBoundaryBlocks() {
            // A song saved before the restriction existed can hold a hairpin that already
            // crosses a double barline. The selection itself crosses nothing, but absorbing
            // that hairpin would carry the span over the barline — refuse rather than widen.
            var fixture = fixtureWith(
                crotchet(), crotchet(), crotchet(), doubleBarline(), crotchet(), crotchet());
            fixture.addCrescendo(0, 3);
            fixture.select(4, 5);

            assertThat(fixture.resolve().state())
                .as("the union span is re-checked against the structural boundary")
                .isEqualTo(HairpinActionState.BLOCKED);
        }
    }
}
