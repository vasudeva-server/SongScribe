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
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.hit.HitTarget;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for the score's single-target selection, which
 * {@link SelectionCoordinator} owns: {@code select}, {@code getSelectedTarget},
 * {@code isSelected}, {@code hasDecorationSelection}, {@code isLineSelected} and
 * {@code revalidateDecorationSelection}.
 *
 * <p>The target and the per-line index range are mutually exclusive, and each of these tests
 * that says so exercises the two halves of that invariant: {@link SelectionCoordinator#select}
 * clears the range, and the range-change callback wired at registration clears the target.
 */
class SelectionCoordinatorTargetSelectionTest extends UnitTest {

    /** The line every single-line test registers, so every query names it. */
    private static final int LINE_0 = 0;

    /** The second line, registered only by the tests about a score-wide target. */
    private static final int LINE_1 = 1;

    /** Verse number used by the lyric fixtures, chosen to not be verse 1. */
    private static final int LYRIC_VERSE = 2;

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
    private Line twoNoteLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        return line;
    }

    /**
     * Returns a coordinator with {@code line} registered and activated at index
     * {@link #LINE_0}, holding no selection yet.
     */
    private static SelectionCoordinator coordinatorFor(Line line) {
        return ReflectionTestHelper.createCoordinatorForLine(line);
    }

    /**
     * Returns the active line, which every test in this class has because
     * {@link #coordinatorFor} registers and activates one.
     */
    private static Line activeLine(SelectionCoordinator coordinator) {
        var line = coordinator.getActiveLine();
        assertThat(line).as("the coordinator's active line").isNotNull();
        return line;
    }

    /** Three notes in the time of two, undotted — the shape of the fixture's tuplet. */
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int NO_DOTS = 0;

    /**
     * Every decoration kind, as a factory over a line holding at least two elements, so
     * that a test covering all of them adds no branch of its own.
     * <p>
     * "Decoration" here means whatever {@link SelectionCoordinator#hasDecorationSelection}
     * counts as one — every target except the staff line and a lyric, both of which are
     * answered elsewhere. The list must stay complete: the tests below derive their cases from
     * it, including the pairwise replacement test, so a kind missing from here is a kind
     * nothing checks.
     */
    private static final List<Named<Function<Line, HitTarget>>> DECORATION_KINDS = List.of(
        Named.of("slide", line -> new HitTarget.Slide(line.getElement(0))),
        Named.of("ending", line -> new HitTarget.Ending(makeEnding(line))),
        Named.of("hairpin", line -> new HitTarget.Hairpin(makeHairpin(line))),
        Named.of("articulation",
            line -> new HitTarget.Articulation(new Articulation(ArticulationType.STACCATO))),
        Named.of("attachment", line -> new HitTarget.Attachment(new FermataAttachment())),
        Named.of("accidental", line -> new HitTarget.Accidental(line.getElement(0))),
        Named.of("tie", line -> new HitTarget.Tie(new Tie(line.getElement(0), line.getElement(1)))),
        Named.of("beam",
            line -> new HitTarget.Beam(new Beam(line.getElement(0), line.getElement(1)))),
        Named.of("trill",
            line -> new HitTarget.Trill(new Trill(line.getElement(0), line.getElement(1)))),
        Named.of("tuplet", line -> new HitTarget.Tuplet(new Tuplet(
            line.getElement(0), line.getElement(1),
            TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS))),
        Named.of("grace glissando", line -> new HitTarget.GraceGlissando(line.getElement(0)))
    );

    private static Stream<Named<Function<Line, HitTarget>>> decorationKinds() {
        return DECORATION_KINDS.stream();
    }

    // -- the line selection as a target --

    @Test
    void testSelectingTheStaffLineReplacesTheDecoration() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Slide(line.getElement(0)));

        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.isLineSelected()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isEqualTo(new HitTarget.StaffLine());
        assertThat(coordinator.hasDecorationSelection())
            .as("a line selection is not a decoration selection")
            .isFalse();
    }

    /**
     * Replaces the old {@code setLineSelected(false)} case, which had no production caller
     * and no equivalent now that the line selection is held as a target: the only way to
     * take a line selection away without putting something else in its place is to clear.
     */
    @Test
    void testClearSelectionDropsTheLineSelection() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.StaffLine());

        coordinator.clearSelection();

        assertThat(coordinator.isLineSelected()).isFalse();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    /**
     * The point of folding the line selection into {@code selected}: neither direction of the
     * mutual exclusion is written down anywhere, because one field holds only one value.
     */
    @Test
    void testTheLineSelectionAndATargetSelectionDisplaceEachOther() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        var slide = new HitTarget.Slide(line.getElement(0));

        coordinator.select(new HitTarget.StaffLine());
        coordinator.select(slide);

        assertThat(coordinator.isLineSelected()).isFalse();
        assertThat(coordinator.getSelectedTarget()).isEqualTo(slide);

        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.getSelectedTarget()).isEqualTo(new HitTarget.StaffLine());
        assertThat(coordinator.isSelected(slide, LINE_0)).isFalse();
    }

    // -- select --

    /**
     * Each decoration kind, selected over a live element and line selection. The three
     * take the same path through {@link SelectionCoordinator#select}, so what is
     * checked per kind is that the decoration itself is what comes back out.
     */
    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testSelectClearsElementAndLineSelection(
        Function<? super Line, ? extends HitTarget> makeDecoration
    ) {
        var line = twoNoteLine();
        var decoration = makeDecoration.apply(line);
        var coordinator = coordinatorFor(line);
        activeLine(coordinator);
        coordinator.selectRange(0, 1);
        coordinator.select(new HitTarget.StaffLine());

        coordinator.select(decoration);

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.isLineSelected()).isFalse();
        assertThat(coordinator.getSelectedTarget()).isEqualTo(decoration);
        assertThat(coordinator.hasDecorationSelection()).isTrue();
    }

    /**
     * One field holds the selected decoration, so selecting one replaces whatever was
     * there. Walking every ordered pair of kinds from {@link #DECORATION_KINDS} keeps
     * this exhaustive as decoration kinds are added: a new entry in that list extends
     * the coverage here without touching this test.
     */
    @Test
    void testSelectReplacesAnyPreviouslySelectedKind() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);

        for (var first : DECORATION_KINDS) {
            for (var second : DECORATION_KINDS) {
                coordinator.select(first.getPayload().apply(line));

                var replacement = second.getPayload().apply(line);
                coordinator.select(replacement);

                assertThat(coordinator.getSelectedTarget())
                    .as("selecting a %s over a %s leaves only the %s selected",
                        second.getName(), first.getName(), second.getName())
                    .isEqualTo(replacement);
            }
        }
    }

    // -- getSelectedTarget --

    @Test
    void testGetSelectedTargetReturnsNullUntilATargetIsSelected() {
        var line = twoNoteLine();
        var ending = makeEnding(line);
        var coordinator = coordinatorFor(line);

        assertThat(coordinator.getSelectedTarget()).isNull();
        assertThat(coordinator.hasDecorationSelection()).isFalse();

        coordinator.select(new HitTarget.Ending(ending));

        assertThat(coordinator.getSelectedTarget())
            .isEqualTo(new HitTarget.Ending(ending));
        assertThat(coordinator.hasDecorationSelection()).isTrue();
    }

    /**
     * A lyric shares the field with the decorations but is not one of them: Delete reaches it
     * through its own branch, and the action-state snapshot must go on treating it the way it
     * did when it lived in a store of its own.
     */
    @Test
    void testALyricSelectionIsNotADecorationSelection() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);

        coordinator.selectLyric(line.getElement(0), 1);

        assertThat(coordinator.getSelectedTarget())
            .isEqualTo(new HitTarget.Lyric(line.getElement(0), 1));
        assertThat(coordinator.hasDecorationSelection()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testSetSelectionFromClickClearsTheDecoration(
        Function<? super Line, ? extends HitTarget> makeDecoration
    ) {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(makeDecoration.apply(line));

        coordinator.selectSingleElement(LINE_0, 1);

        assertThat(coordinator.getSelectedTarget()).isNull();
        assertThat(coordinator.isElementSelected(1, LINE_0)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testSelectingTheStaffLineReplacesEachDecorationKind(
        Function<? super Line, ? extends HitTarget> makeDecoration
    ) {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(makeDecoration.apply(line));

        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.getSelectedTarget()).isEqualTo(new HitTarget.StaffLine());
        assertThat(coordinator.hasDecorationSelection()).isFalse();
        assertThat(coordinator.isLineSelected()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("decorationKinds")
    void testClearSelectionClearsTheDecoration(
        Function<? super Line, ? extends HitTarget> makeDecoration
    ) {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(makeDecoration.apply(line));

        coordinator.clearSelection();

        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    // -- isSelected: the kinds not held as the single selected target --

    /**
     * A note is selected through the index range, not through {@code selected}, so
     * {@code isSelected} has to read the range for it — otherwise a clicked note is addressable
     * by a click and invisible to every renderer.
     */
    @Test
    void testIsSelectedReportsAnElementInsideTheSelectionRange() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.selectSingleElement(LINE_0, 1);

        assertThat(coordinator.isSelected(new HitTarget.Element(line.getElement(1)), LINE_0)).isTrue();
        assertThat(coordinator.isSelected(new HitTarget.Element(line.getElement(0)), LINE_0)).isFalse();
    }

    @Test
    void testIsSelectedReportsNoElementWhenTheRangeIsEmpty() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);

        assertThat(coordinator.isSelected(new HitTarget.Element(line.getElement(0)), LINE_0)).isFalse();
    }

    /** The staff line is answered from {@code selected} like every other target. */
    @Test
    void testIsSelectedAnswersForTheStaffLine() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);

        assertThat(coordinator.isSelected(new HitTarget.StaffLine(), LINE_0)).isFalse();

        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.isSelected(new HitTarget.StaffLine(), LINE_0)).isTrue();
    }

    /** Everything else is compared against the single selected target. */
    @Test
    void testIsSelectedComparesOtherKindsAgainstTheSelectedTarget() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Slide(line.getElement(1)));

        assertThat(coordinator.isSelected(new HitTarget.Slide(line.getElement(1)), LINE_0)).isTrue();
        assertThat(coordinator.isSelected(new HitTarget.Slide(line.getElement(0)), LINE_0)).isFalse();
    }

    // -- getSelection --

    @Test
    void testGetSelectionReturnsFullLineSpanWhenLineSelected() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.getSelection())
            .isNotNull()
            .satisfies(selection -> {
                assertThat(selection.begin()).isEqualTo(0);
                assertThat(selection.end()).isEqualTo(1);
                assertThat(selection.line()).isSameAs(line);
            });
    }

    @Test
    void testGetSelectionReturnsNullForALineSelectionOnAnEmptyLine() {
        var song = new Song();
        var line = song.getLine(0);

        // Default song seeds the first (and only) line with just the final barline, which
        // effectiveElementCount() leaves out, so there is nothing for the span to cover.
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.getSelection()).isNull();
    }

    // -- selectAll swaps a line selection for its elements --

    @Test
    void testSelectAllClearsLineSelection() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.StaffLine());

        coordinator.selectAll();

        assertThat(coordinator.isLineSelected()).isFalse();
        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(1);
            });
    }

    @Test
    void testSelectAllOnEmptyLineLeavesLineSelectionIntact() {
        var song = new Song();
        var line = song.getLine(0);

        // Default song seeds the first (and only) line with just the final barline,
        // so there is no element selection to swap the line selection for.
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.StaffLine());

        coordinator.selectAll();

        assertThat(coordinator.isLineSelected()).isTrue();
        assertThat(coordinator.getRange()).isNull();
    }

    // -- what the hoist is for: one target for the whole score --

    /**
     * The reason the selection lives on the coordinator rather than on a line: a
     * {@code LineComponent} re-registers its line on every rebuild, and nothing held per line
     * would survive that.
     */
    @Test
    void testATargetSelectionSurvivesARebuildOfAnUnrelatedLine() {
        var song = minimalSongMock();
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        var lineA = new Line(song);
        lineA.addElement(ElementType.CROTCHET.newInstance());
        lineA.addElement(ElementType.CROTCHET.newInstance());
        coordinator.registerLine(LINE_0, lineA);

        var lineB = new Line(song);
        lineB.addElement(ElementType.CROTCHET.newInstance());
        coordinator.registerLine(LINE_1, lineB);

        coordinator.activateLine(LINE_0);

        var ending = makeEnding(lineA);
        lineA.addSpan(ending);
        coordinator.select(new HitTarget.Ending(ending));

        // What LineComponent.setLine does when line B is rebuilt: re-register at the same index.
        coordinator.registerLine(LINE_1, lineB);

        assertThat(coordinator.getSelectedTarget())
            .as("the ending on line 0 after line 1 was rebuilt")
            .isEqualTo(new HitTarget.Ending(ending));
        assertThat(coordinator.isSelected(new HitTarget.Ending(ending), LINE_0)).isTrue();
    }

    /**
     * One target for the whole score, so selecting on another line displaces it — with no
     * callback and no cross-line bookkeeping, because there is only one field to assign.
     */
    @Test
    void testSelectingALyricOnAnotherLineClearsATargetOnTheFirst() {
        var song = minimalSongMock();
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        var lineA = new Line(song);
        lineA.addElement(ElementType.CROTCHET.newInstance());
        lineA.addElement(ElementType.CROTCHET.newInstance());
        coordinator.registerLine(LINE_0, lineA);

        var lineB = new Line(song);
        var lyricNote = ElementType.CROTCHET.newInstance();
        lineB.addElement(lyricNote);
        coordinator.registerLine(LINE_1, lineB);

        coordinator.activateLine(LINE_0);

        var ending = makeEnding(lineA);
        lineA.addSpan(ending);
        coordinator.select(new HitTarget.Ending(ending));

        coordinator.selectLyric(lyricNote, LYRIC_VERSE);

        assertThat(coordinator.getActiveLineIndex()).isEqualTo(LINE_1);
        assertThat(coordinator.getSelectedTarget())
            .isEqualTo(new HitTarget.Lyric(lyricNote, LYRIC_VERSE));
        assertThat(coordinator.isSelected(new HitTarget.Ending(ending), LINE_0))
            .as("the ending on line 0 is no longer selected")
            .isFalse();
    }

    // -- revalidateDecorationSelection --

    @Test
    void testRevalidateDecorationSelectionNoOpWhenNoDecorationSelected() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.selectSingleElement(LINE_0, 0);

        assertThat(coordinator.revalidateDecorationSelection()).isFalse();
        assertThat(coordinator.isElementSelected(0, LINE_0)).isTrue();
    }

    @Test
    void testRevalidateDecorationSelectionKeepsSlideWhenElementStillHasSlide() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Slide(line.getElement(0)));

        assertThat(coordinator.revalidateDecorationSelection()).isFalse();
        assertThat(coordinator.getSelectedTarget())
            .isEqualTo(new HitTarget.Slide(line.getElement(0)));
    }

    /**
     * Liveness is a question about the owning element, not about the slide: revalidation
     * asks whether what the selection names is still on the line, and the note is. Stripping
     * the slide leaves nothing drawn to highlight, which the renderer and the delete path
     * each answer for themselves.
     */
    @Test
    void testRevalidateDecorationSelectionKeepsSlideWhenOnlyTheSlideWasRemoved() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var note = line.getElement(0);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Slide(note));

        note.removeSlide();

        assertThat(coordinator.revalidateDecorationSelection()).isFalse();
        assertThat(coordinator.getSelectedTarget()).isEqualTo(new HitTarget.Slide(note));
    }

    @Test
    void testRevalidateDecorationSelectionClearsSlideWhenOwningElementLeavesTheLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setGlissando();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Slide(line.getElement(0)));

        // Simulates an undo/redo that removed the element the slide hangs off.
        line.removeElement(0);

        assertThat(coordinator.revalidateDecorationSelection()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    @Test
    void testRevalidateDecorationSelectionKeepsEndingWhenStillOnLine() {
        var line = twoNoteLine();
        var ending = makeEnding(line);
        line.addSpan(ending);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Ending(ending));

        assertThat(coordinator.revalidateDecorationSelection()).isFalse();
        assertThat(coordinator.getSelectedTarget())
            .isEqualTo(new HitTarget.Ending(ending));
    }

    @Test
    void testRevalidateDecorationSelectionClearsEndingWhenNoLongerOnLine() {
        var line = twoNoteLine();
        var ending = makeEnding(line);
        line.addSpan(ending);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Ending(ending));

        // Simulates an undo/redo that removed the ending without clearing the selection.
        line.removeSpan(ending);

        assertThat(coordinator.revalidateDecorationSelection()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    @Test
    void testRevalidateDecorationSelectionKeepsHairpinWhenStillOnLine() {
        var line = twoNoteLine();
        var hairpin = makeHairpin(line);
        line.addSpan(hairpin);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Hairpin(hairpin));

        assertThat(coordinator.revalidateDecorationSelection()).isFalse();
        assertThat(coordinator.getSelectedTarget())
            .isEqualTo(new HitTarget.Hairpin(hairpin));
    }

    @Test
    void testRevalidateDecorationSelectionClearsHairpinWhenNoLongerOnLine() {
        var line = twoNoteLine();
        var hairpin = makeHairpin(line);
        line.addSpan(hairpin);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Hairpin(hairpin));

        // Simulates an undo/redo that removed the hairpin without clearing the selection.
        line.removeSpan(hairpin);

        assertThat(coordinator.revalidateDecorationSelection()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    /**
     * One derived rule replaced three hand-written ones, so each family of target is pinned
     * here: a span owned by the line, something owned by an element, an element itself, the
     * staff line, and a target that must survive an unrelated edit. A wrong answer for any
     * of them is silent — the selection either sticks to something that is gone or vanishes
     * while the user is looking at it.
     */
    @Test
    void testRevalidateDecorationSelectionClearsArticulationWhenRemovedFromItsNote() {
        var line = twoNoteLine();
        var note = line.getElement(0);
        var articulation = new Articulation(note, ArticulationType.STACCATO);
        note.addArticulation(articulation);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Articulation(articulation));

        note.removeArticulation(articulation);

        assertThat(coordinator.revalidateDecorationSelection()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    /**
     * An articulation is a child of its note and carries no line of its own, so its liveness
     * is the note's liveness. Deleting the note must take the selection with it.
     */
    @Test
    void testRevalidateDecorationSelectionClearsArticulationWhenItsNoteLeavesTheLine() {
        var line = twoNoteLine();
        var note = line.getElement(0);
        var articulation = new Articulation(note, ArticulationType.STACCATO);
        note.addArticulation(articulation);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Articulation(articulation));

        line.removeElement(0);

        assertThat(coordinator.revalidateDecorationSelection()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    @Test
    void testRevalidateDecorationSelectionClearsAttachmentWhenItsNoteLeavesTheLine() {
        var line = twoNoteLine();
        var note = line.getElement(0);
        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Attachment(fermata));

        line.removeElement(0);

        assertThat(coordinator.revalidateDecorationSelection()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    @Test
    void testRevalidateDecorationSelectionClearsElementTargetWhenTheElementLeavesTheLine() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Element(line.getElement(0)));

        line.removeElement(0);

        assertThat(coordinator.revalidateDecorationSelection()).isTrue();
        assertThat(coordinator.getSelectedTarget()).isNull();
    }

    /**
     * The staff line is not a thing on the line, so nothing can remove it. It has no owner
     * for the liveness rule to ask about and must never be revalidated away.
     */
    @Test
    void testRevalidateDecorationSelectionNeverClearsTheStaffLine() {
        var line = twoNoteLine();
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.StaffLine());

        line.removeElement(0);

        assertThat(coordinator.revalidateDecorationSelection()).isFalse();
        assertThat(coordinator.getSelectedTarget()).isEqualTo(new HitTarget.StaffLine());
    }

    /**
     * Removing a different element on the same line is exactly the traffic revalidation
     * runs on, so a live target surviving it is what keeps the rule from being useless.
     */
    @Test
    void testRevalidateDecorationSelectionKeepsALiveTargetThroughAnUnrelatedMutation() {
        var line = twoNoteLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        var note = line.getElement(0);
        var articulation = new Articulation(note, ArticulationType.STACCATO);
        note.addArticulation(articulation);
        var coordinator = coordinatorFor(line);
        coordinator.select(new HitTarget.Articulation(articulation));

        line.removeElement(2);

        assertThat(coordinator.revalidateDecorationSelection()).isFalse();
        assertThat(coordinator.getSelectedTarget())
            .isEqualTo(new HitTarget.Articulation(articulation));
    }
}
