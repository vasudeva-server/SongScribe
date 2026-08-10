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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.singleBarline;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.AccidentalAction;

/**
 * The coordinator answers questions about the selection out of caches keyed by
 * {@link ElementSelection} — a line plus an index range. An index names a different
 * element after a mutation shifts the line's contents, so every one of those caches has
 * to be dropped when the song changes, or the new occupant of an index inherits the
 * answers computed for the old one (issue #688).
 * <p>
 * Each test walks the reported sequence: select the barline at index 0, delete it so the
 * note slides into index 0, then select that note. Every assertion is about the note, and
 * every one of them would report the barline's answer without the invalidation.
 */
class SelectionCoordinatorStaleCacheTest extends MainFrameMockTest {

    private record Fixture(SelectionCoordinator coordinator, AccidentalAction action) {}

    /**
     * Selects the barline at index 0, priming all three caches with its answers, deletes
     * it, reports the deletion, then selects the note that took its index.
     */
    private Fixture noteSelectedWhereTheDeletedBarlineWas() {
        var note = crotchet();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(singleBarline(), note),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        // Prime every cache with the barline's answers.
        assertThat(coordinator.isApplicableToSelection(action))
            .as("precondition: the sharp action does not apply to a barline")
            .isFalse();
        assertThat(coordinator.selectionHasDurations())
            .as("precondition: a barline carries no duration")
            .isFalse();
        coordinator.getActionReflector().triggerReflection();
        assertThat(action.isSelected())
            .as("precondition: a barline cannot carry a sharp")
            .isFalse();

        var line = coordinator.getActiveLine();

        assertThat(line).as("the test coordinator has no active line").isNotNull();

        var barline = line.getElement(0);
        line.removeElement(0);

        coordinator.songDidChangeInvalidateCaches(new SongDidChangeNotification(
            List.of(new ElementDeletion(line, 0, barline)),
            line.getSong()
        ));

        // The note now occupies the index the barline was answered under.
        ReflectionTestHelper.selectNote(coordinator, 0);

        return new Fixture(coordinator, action);
    }

    @Test
    void testApplicabilityIsRecomputedAfterADeleteShiftsTheSelectedIndex() {
        var fixture = noteSelectedWhereTheDeletedBarlineWas();

        assertThat(fixture.coordinator().isApplicableToSelection(fixture.action()))
            .as("the sharp action applies to the selected note")
            .isTrue();
    }

    @Test
    void testSelectionContentIsRecomputedAfterADeleteShiftsTheSelectedIndex() {
        var fixture = noteSelectedWhereTheDeletedBarlineWas();

        assertThat(fixture.coordinator().selectionHasDurations())
            .as("the selected note carries a duration")
            .isTrue();
    }

    @Test
    void testReflectionRerunsAfterADeleteShiftsTheSelectedIndex() {
        var fixture = noteSelectedWhereTheDeletedBarlineWas();

        fixture.coordinator().getActionReflector().triggerReflection();

        assertThat(fixture.action().isSelected())
            .as("the selected note carries a sharp")
            .isTrue();
    }
}
