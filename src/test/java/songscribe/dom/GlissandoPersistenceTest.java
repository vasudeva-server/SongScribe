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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests verifying that glissando data persists correctly through
 * save/load round-trips, using the {@code connections} fixture.
 */
@SuppressWarnings({ "OverlyBroadThrowsClause", "StaticVariableMayNotBeInitialized" })
class GlissandoPersistenceTest extends UnitTest {

    // PAIR_B_SRC (index 7) has a CONNECTED glissando in the fixture
    private static final int PAIR_B_SRC = 7;

    private static Song song;

    @BeforeAll
    static void loadFixtureData() throws Exception {
        song = loadFixture("connections");
    }

    @Test
    void testGlissandoPersistsThroughSaveLoad() throws Exception {
        var originalNote = song.getLine(0).getElement(PAIR_B_SRC);
        assertThat(originalNote.hasGlissando()).as("fixture has glissando").isTrue();

        var reloaded = legacyRoundTrip(song);
        var reloadedNote = reloaded.getLine(0).getElement(PAIR_B_SRC);

        assertThat(reloadedNote.hasGlissando())
            .as("save/load: glissando preserved").isTrue();
    }

}
