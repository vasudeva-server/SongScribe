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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;
import songscribe.dom.Lyric;

/**
 * Verifies {@link SyllabicMapping} round-trips every {@link Lyric.Syllabic}
 * value and every tokened {@link Lyric.Extend} value, and pins down the two
 * fallback behaviors the reader (Phase 3) depends on: an absent/unrecognized
 * {@code <extend type>} defaults to {@link Lyric.Extend#START}, and an
 * unrecognized {@code <syllabic>} token defaults to
 * {@link Lyric.Syllabic#SINGLE}.
 */
class SyllabicMappingTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Syllabic: forward/reverse round-trip over all four values.
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Lyric.Syllabic.class)
    void testEverySyllabicRoundTrips(Lyric.Syllabic syllabic) {
        var token = SyllabicMapping.forSyllabic(syllabic);

        assertThat(token)
            .as("%s must have a forward-map token", syllabic)
            .isNotNull();

        assertThat(SyllabicMapping.forSyllabicToken(token))
            .as("%s must map back to %s", token, syllabic)
            .isEqualTo(syllabic);
    }

    @Test
    void testForSyllabicDefaultsNullToSingle() {
        // Mirrors the legacy writer's `case null -> "single"` default.
        assertThat(SyllabicMapping.forSyllabic(null))
            .isEqualTo(SyllabicMapping.SYLLABIC_SINGLE);
    }

    @Test
    void testForSyllabicTokenDefaultsUnrecognizedTokenToSingle() {
        // Chosen unknown-token behavior (task 1): an unrecognized <syllabic>
        // token defaults to SINGLE, matching the writer's null-syllabic default,
        // rather than throwing.
        assertThat(SyllabicMapping.forSyllabicToken("bogus"))
            .isEqualTo(Lyric.Syllabic.SINGLE);
    }

    @Test
    void testForSyllabicTokenDefaultsNullToSingle() {
        assertThat(SyllabicMapping.forSyllabicToken(null))
            .isEqualTo(Lyric.Syllabic.SINGLE);
    }

    // -------------------------------------------------------------------------
    // Extend: forward/reverse round-trip over the three tokened values.
    // NONE is excluded here -- it has no token (see the NONE-specific tests).
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = Lyric.Extend.class, names = {"START", "STOP", "CONTINUE"})
    void testEveryTokenedExtendRoundTrips(Lyric.Extend extend) {
        var token = SyllabicMapping.forExtend(extend);

        assertThat(token)
            .as("%s must have a forward-map token", extend)
            .isNotNull();

        assertThat(SyllabicMapping.forExtendToken(token))
            .as("%s must map back to %s", token, extend)
            .isEqualTo(extend);
    }

    @Test
    void testForExtendThrowsForNoneBecauseItHasNoToken() {
        // NONE represents the absence of an <extend> element entirely, not a
        // token value -- mirrors StaffElementIO.extendTypeAttr, which throws
        // on NONE.
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SyllabicMapping.forExtend(Lyric.Extend.NONE))
            .withMessageContaining("NONE");
    }

    @Test
    void testForExtendTokenDefaultsAbsentTokenToStart() {
        // An absent type attribute (e.g. a legacy self-closed <extend/>) means
        // "melisma starts here" -- mirrors StaffElementIO.parseExtendType.
        assertThat(SyllabicMapping.forExtendToken(null))
            .isEqualTo(Lyric.Extend.START);
    }

    @Test
    void testForExtendTokenDefaultsUnrecognizedTokenToStart() {
        assertThat(SyllabicMapping.forExtendToken("bogus"))
            .isEqualTo(Lyric.Extend.START);
    }
}
