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

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.action.Actions;

/**
 * E2E coverage for the one half of accidental reconciliation that no unit test can reach: that
 * inserting a barrier through the real insertion path reconciles at all.
 *
 * <p>The engine — {@code AccidentalReconciliation.reconcile} with a barline as the whole inserted
 * list — is unit-tested in {@code songscribe.layout.AccidentalReconciliationTest}. What that
 * cannot show is whether the caller ever asks: the defect this guards against was
 * {@code PreviewElementManager} skipping reconciliation for an element carrying no accidental of
 * its own, on the reasoning that "no accidental" meant "nothing to reconcile". A barline carries
 * none and cancels every accidental before it, so the skip silently changed pitches. That method
 * is private and needs a live {@code LineComponent} and layout result, which is what puts this
 * test here rather than beside the engine's.
 *
 * <p>Fixture, in a song with no key signature — Fixture B of #676, flattened rather than sharpened
 * for no particular reason; the direction of the alteration is immaterial to what is being tested:
 *
 * <pre>
 * F♭  G  A  F      ← the last F carries nothing and inherits the flat
 * </pre>
 */
class AccidentalContextTest extends E2ETest {

    // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
    private static final int F_STAFF_POSITION = 3;

    private static final int LINE_INDEX = 0;

    private static final int FLATTENED_F_INDEX = 0;
    private static final int INHERITING_F_INDEX = 3;
    private static final int FIXTURE_ELEMENT_COUNT = 4;

    /** Where the barline lands, and where the F that follows it ends up. */
    private static final int BARLINE_INDEX = 3;
    private static final int INHERITING_F_INDEX_AFTER_BARLINE = 4;
    private static final int ELEMENT_COUNT_AFTER_BARLINE = 5;

    /** {@code Actions.BARLINE_ACTIONS} is {double, single}. */
    private static final int SINGLE_BARLINE_ACTION_INDEX = 1;

    /**
     * What the note at {@code index} sounds like: its own accidental when it has one, otherwise
     * the context it inherits. {@link StaffElement#findEffectiveAccidental} answers only the
     * second half — its contract is that callers check the note's own accidental first — and it is
     * exactly the note's own accidental that this feature adds, so the two halves have to be put
     * back together here or the assertion reads the context and misses the point.
     */
    private static StaffElement.@Nullable Accidental soundingAccidental(Line line, int index) {
        var element = line.getElement(index);
        var own = element.getAccidental();

        if (own != null) {
            return own;
        }

        return element.findEffectiveAccidental(line, index);
    }

    /**
     * Loads the fixture fresh for each test, since the test under it mutates the line.
     *
     * <p>The line comes from disk rather than from four real clicks because inserting the first
     * note into an empty song raises the automatic tempo prompt, which is modal and swallows every
     * click after it. The fixture's first note carries the tempo the prompt would have asked for,
     * so nothing blocks. Only the barline insertion — the thing under test — goes through the real
     * click pipeline.
     */
    @BeforeEach
    void loadFlattenedThenInheritingLine() {
        resetSong();
        loadFixture("accidental-context");
        deselectSelection();
        performLayout(LINE_INDEX);
    }

    @Test
    void testInsertingABarlineWritesTheAccidentalItCancelsOntoTheNoteThatInheritedIt() {
        var line = song().getLine(LINE_INDEX);

        // Preconditions, so a fixture that failed to build cannot pass this test by accident: the
        // last F carries nothing of its own and sounds flat only by inheritance.
        assertAll(
            () -> assertThat(line.effectiveElementCount())
                .as("fixture built").isEqualTo(FIXTURE_ELEMENT_COUNT),
            () -> assertThat(line.getElement(FLATTENED_F_INDEX).getAccidental())
                .as("first F is flattened").isEqualTo(StaffElement.Accidental.FLAT),
            () -> assertThat(line.getElement(INHERITING_F_INDEX).getAccidental())
                .as("last F carries nothing of its own").isNull(),
            () -> assertThat(soundingAccidental(line, INHERITING_F_INDEX))
                .as("last F sounds flat by inheritance").isEqualTo(StaffElement.Accidental.FLAT)
        );

        selectDuration(Actions.BARLINE_ACTIONS[SINGLE_BARLINE_ACTION_INDEX]);
        clickAt(insertionPointBefore(LINE_INDEX, INHERITING_F_INDEX, F_STAFF_POSITION));
        performLayout(LINE_INDEX);

        var modified = song().getLine(LINE_INDEX);

        assertAll(
            () -> assertThat(modified.effectiveElementCount())
                .as("barline added one element").isEqualTo(ELEMENT_COUNT_AFTER_BARLINE),
            () -> assertThat(modified.getElement(BARLINE_INDEX).getType())
                .as("barline landed between the A and the last F")
                .isEqualTo(ElementType.SINGLE_BARLINE),
            // The point of the whole feature: the barline cancels what the F was inheriting, so
            // the flat has to be written onto the F itself or the note silently turns natural.
            () -> assertThat(modified.getElement(INHERITING_F_INDEX_AFTER_BARLINE).getAccidental())
                .as("the cancelled flat is now drawn on the F")
                .isEqualTo(StaffElement.Accidental.FLAT),
            () -> assertThat(soundingAccidental(modified, INHERITING_F_INDEX_AFTER_BARLINE))
                .as("the F sounds exactly as it did before the barline")
                .isEqualTo(StaffElement.Accidental.FLAT),
            // The other half of the same claim: the barline really did cancel the context, so the
            // accidental now on the note is doing the work, not riding on a context that survived.
            () -> assertThat(modified.getElement(INHERITING_F_INDEX_AFTER_BARLINE)
                .findEffectiveAccidental(modified, INHERITING_F_INDEX_AFTER_BARLINE))
                .as("the barline cancelled the context the F had been inheriting")
                .isNull()
        );
    }
}
