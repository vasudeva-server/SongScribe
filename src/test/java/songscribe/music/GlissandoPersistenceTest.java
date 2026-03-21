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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests verifying that glissando data persists correctly through
 * save/load round-trips, using the {@code connections} fixture.
 */
class GlissandoPersistenceTest extends UnitTest {

    // PAIR_B_SRC (index 7) has a CONNECTED glissando in the fixture
    private static final int PAIR_B_SRC = 7;

    private static Composition composition;

    @BeforeAll
    static void loadFixtureData() throws Exception {
        composition = loadFixture("connections");
    }

    @Test
    void testGlissandoPersistsThroughSaveLoad() throws Exception {
        var originalNote = composition.getLine(0).getElement(PAIR_B_SRC);
        var originalGlissando = originalNote.getGlissando();
        assertThat(originalGlissando).as("fixture has glissando").isNotNull();
        var originalType = Objects.requireNonNull(originalGlissando).type;

        var reloaded = roundTrip(composition);
        var reloadedNote = reloaded.getLine(0).getElement(PAIR_B_SRC);
        var reloadedGlissando = reloadedNote.getGlissando();

        assertAll(
            () -> assertThat(reloadedGlissando)
                .as("save/load: glissando preserved").isNotNull(),
            () -> assertThat(Objects.requireNonNull(reloadedGlissando).type)
                .as("save/load: glissando type preserved").isEqualTo(originalType)
        );
    }

    private static Composition roundTrip(Composition original) throws Exception {
        var sw = new java.io.StringWriter();
        var pw = new java.io.PrintWriter(sw);
        songscribe.io.CompositionIO.writeComposition(original, pw);
        pw.flush();
        var xml = sw.toString();

        var factory = javax.xml.parsers.SAXParserFactory.newInstance();
        var parser = factory.newSAXParser();
        var reader = new songscribe.io.CompositionIO.DocumentReader();
        parser.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)), reader);

        return reader.getComposition();
    }
}
