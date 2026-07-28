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

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Covers the hairpin clause of the {@code songDidChange} guard in
 * {@link SelectionCoordinator}.
 * <p>
 * {@link LineSelectionState#revalidateDecorationSelection()} is tested on its own in
 * {@link LineSelectionStateTest}, but that cannot show whether the coordinator ever calls
 * it for a hairpin. Without the {@code hasHairpinSelection()} clause in the guard, an
 * undo that removes the selected hairpin leaves the coordinator holding a dead
 * {@link songscribe.dom.Hairpin} with the toolbar frozen in its selected state.
 */
class SelectionCoordinatorHairpinRevalidationTest extends UnitTest {

    private record Fixture(SelectionCoordinator coordinator, Line line, Crescendo hairpin) {}

    /**
     * Builds a coordinator whose active line carries a crescendo spanning its two notes,
     * with that crescendo selected.
     */
    private static Fixture selectedHairpinOnActiveLine() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance()),
            List.of()
        );

        var state = coordinator.getActiveSelection();

        if (state == null) {
            throw new AssertionError("the test coordinator has no active line state");
        }

        var line = state.getLine();
        var hairpin = new Crescendo(line.getElement(0), line.getElement(1));
        line.addRangeElement(hairpin);
        state.selectHairpin(hairpin);

        return new Fixture(coordinator, line, hairpin);
    }

    @Test
    void testSongDidChangeClearsHairpinSelectionWhenTheHairpinIsGone() {
        var fixture = selectedHairpinOnActiveLine();
        assertThat(fixture.coordinator().hasHairpinSelection()).isTrue();

        // An undo that removed the hairpin, reported on the selected line.
        fixture.line().removeRangeElement(fixture.hairpin());

        fixture.coordinator().songDidChange(new SongDidChangeNotification(
            List.of(new CrescendoRemoval(fixture.line(), fixture.hairpin())),
            fixture.line().getSong()
        ));

        assertThat(fixture.coordinator().hasHairpinSelection())
            .as("the dead hairpin selection was revalidated away")
            .isFalse();
    }

    @Test
    void testSongDidChangeKeepsHairpinSelectionWhenTheHairpinSurvives() {
        var fixture = selectedHairpinOnActiveLine();

        // A mutation on the same line that leaves the hairpin in place — e.g. a redo.
        fixture.coordinator().songDidChange(new SongDidChangeNotification(
            List.of(new CrescendoRemoval(fixture.line(), fixture.hairpin())),
            fixture.line().getSong()
        ));

        assertThat(fixture.coordinator().hasHairpinSelection())
            .as("a live hairpin selection survives revalidation")
            .isTrue();
    }
}
