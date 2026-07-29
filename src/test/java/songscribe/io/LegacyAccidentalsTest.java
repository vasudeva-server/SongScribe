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

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.StaffElement.Accidental;

/**
 * Retired-accidental lookups. The token lookup returns {@code null} rather than
 * throwing on unknown input, because callers use "not null" as the signal that
 * a conversion happened — a lookup that silently answered for a live token, or
 * threw on an ordinary one, would corrupt that signal.
 */
class LegacyAccidentalsTest extends UnitTest {

    @Test
    void testLegacyTokensMapToTheirReplacements() {
        assertThat(LegacyAccidentals.forLegacyToken(LegacyAccidentals.ACCIDENTAL_NATURAL_FLAT))
            .as("natural-flat sounds a flat")
            .isEqualTo(Accidental.FLAT);
        assertThat(LegacyAccidentals.forLegacyToken(LegacyAccidentals.ACCIDENTAL_NATURAL_SHARP))
            .as("natural-sharp sounds a sharp")
            .isEqualTo(Accidental.SHARP);
    }

    @Test
    void testUnknownTokenReturnsNull() {
        // A live token must not be claimed as legacy: doing so would flag every
        // ordinary file as converted and mark it modified on open.
        assertThat(LegacyAccidentals.forLegacyToken("flat"))
            .as("a live MusicXML token is not a retired one")
            .isNull();
        assertThat(LegacyAccidentals.forLegacyToken("bogus"))
            .as("an unrecognised token is not a retired one")
            .isNull();
    }

    /**
     * {@code StaffElementIO} seeds its {@code .mssw} accidental lookup from this map and
     * synthesizes the underscore-less aliases from these keys, so the map is the single
     * statement of which names are retired and what each one becomes. A wrong entry here
     * would silently rewrite notes in every old file that names that accidental — hence
     * asserting the exact contents rather than just the keys. Only the canonical names
     * belong here; the alias rule lives in {@code StaffElementIO} and is tested there.
     */
    @Test
    void testLegacyNamesMapsEveryRetiredNameToItsReplacement() {
        assertThat(LegacyAccidentals.legacyNames())
            .containsOnly(
                entry("NATURAL_FLAT", Accidental.FLAT),
                entry("NATURAL_SHARP", Accidental.SHARP),
                entry("DOUBLE_NATURAL", Accidental.NATURAL));
    }
}
