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

package songscribe.io.musicxml;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;
import songscribe.dom.BeatUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BeatUnitMapping}'s promise that the two directions of the table agree: the token and dot
 * count written for a unit read back as that unit.
 *
 * <p>The failure this guards is two units sharing a token and dot count. The reverse map is built
 * by insertion, so the second would silently displace the first and a file written with one would
 * read back as the other.
 */
class BeatUnitMappingTest extends UnitTest {

    @ParameterizedTest
    @EnumSource(BeatUnit.class)
    void testEveryUnitReadsBackFromWhatIsWrittenForIt(BeatUnit unit) {
        var entry = BeatUnitMapping.entryFor(unit);

        assertThat(BeatUnitMapping.beatUnitFor(entry.token(), entry.dotCount()))
            .as("%s reads back from %s with %d dots", unit, entry.token(), entry.dotCount())
            .isSameAs(unit);
    }
}
