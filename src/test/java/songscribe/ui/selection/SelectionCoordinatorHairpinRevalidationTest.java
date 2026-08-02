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
import songscribe.hit.HitTarget;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Covers the hairpin clause of the {@code songDidChange} guard in
 * {@link SelectionCoordinator}.
 * <p>
 * {@link SelectionCoordinator#revalidateDecorationSelection()} is tested on its own in
 * {@link SelectionCoordinatorTargetSelectionTest}, but that cannot show whether the coordinator calls
 * it for a hairpin. Without the {@code hasDecorationSelection()} clause in the guard, an
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

        var state = coordinator.getActiveLine();

        assertThat(state).as("the test coordinator has no active line state").isNotNull();

        var line = state;
        var hairpin = new Crescendo(line.getElement(0), line.getElement(1));
        line.addSpan(hairpin);
        coordinator.select(new HitTarget.Hairpin(hairpin));

        return new Fixture(coordinator, line, hairpin);
    }

    @Test
    void testSongDidChangeClearsHairpinSelectionWhenTheHairpinIsGone() {
        var fixture = selectedHairpinOnActiveLine();
        assertThat(fixture.coordinator().getSelectedTarget())
            .isEqualTo(new HitTarget.Hairpin(fixture.hairpin()));

        // An undo that removed the hairpin, reported on the selected line.
        fixture.line().removeSpan(fixture.hairpin());

        fixture.coordinator().songDidChangeReflectSelection(new SongDidChangeNotification(
            List.of(new CrescendoRemoval(fixture.line(), fixture.hairpin())),
            fixture.line().getSong()
        ));

        assertThat(fixture.coordinator().getSelectedTarget())
            .as("the dead hairpin selection was revalidated away")
            .isNull();
    }

    /**
     * The opposite direction, end to end: a change that leaves the hairpin on the line
     * must not cost the user their selection.
     * <p>
     * This cannot isolate the guard's hairpin clause the way the test above does. The
     * guard only acts when revalidation reports a stale selection, and revalidation
     * reports nothing when the hairpin is alive, so removing the clause would leave this
     * outcome unchanged. What the clause does in this case is covered directly by
     * {@code SelectionCoordinatorTargetSelectionTest.testRevalidateDecorationSelectionKeepsHairpinWhenStillOnLine}.
     */
    @Test
    void testSongDidChangeKeepsHairpinSelectionWhenTheHairpinSurvives() {
        var fixture = selectedHairpinOnActiveLine();

        // A redo that re-added the selected hairpin: it is on the line, so the mutation
        // and the document agree, unlike a removal reported for a hairpin still present.
        fixture.coordinator().songDidChangeReflectSelection(new SongDidChangeNotification(
            List.of(new CrescendoAddition(fixture.line(), fixture.hairpin())),
            fixture.line().getSong()
        ));

        assertThat(fixture.coordinator().getSelectedTarget())
            .as("a live hairpin selection survives revalidation")
            .isEqualTo(new HitTarget.Hairpin(fixture.hairpin()));
    }
}
