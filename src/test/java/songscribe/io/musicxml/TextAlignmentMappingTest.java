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

import java.awt.Component;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Verifies the bidirectional {@link TextAlignmentMapping} between a SongScribe
 * {@code xAlignment} and the MusicXML {@code left}/{@code center}/{@code right}
 * token: each of the three recognized values round-trips, and both directions
 * default to left for an unrecognised or absent input.
 */
class TextAlignmentMappingTest extends UnitTest {

    // A float that is none of the three recognized alignment values, exercising
    // alignToken's fall-through-to-left branch.
    private static final float UNRECOGNISED_ALIGNMENT = 0.25f;

    @Test
    void testAlignTokenMapsLeftCenterRight() {
        assertThat(TextAlignmentMapping.alignToken(Component.LEFT_ALIGNMENT))
            .isEqualTo(MusicXmlTags.HALIGN_LEFT);
        assertThat(TextAlignmentMapping.alignToken(Component.CENTER_ALIGNMENT))
            .isEqualTo(MusicXmlTags.HALIGN_CENTER);
        assertThat(TextAlignmentMapping.alignToken(Component.RIGHT_ALIGNMENT))
            .isEqualTo(MusicXmlTags.HALIGN_RIGHT);
    }

    @Test
    void testAlignTokenDefaultsToLeftForUnrecognisedValue() {
        assertThat(TextAlignmentMapping.alignToken(UNRECOGNISED_ALIGNMENT))
            .as("an unrecognised alignment must map to the left token (the model default)")
            .isEqualTo(MusicXmlTags.HALIGN_LEFT);
    }

    @Test
    void testXAlignmentMapsLeftCenterRight() {
        assertThat(TextAlignmentMapping.xAlignment(MusicXmlTags.HALIGN_LEFT))
            .isEqualTo(Component.LEFT_ALIGNMENT);
        assertThat(TextAlignmentMapping.xAlignment(MusicXmlTags.HALIGN_CENTER))
            .isEqualTo(Component.CENTER_ALIGNMENT);
        assertThat(TextAlignmentMapping.xAlignment(MusicXmlTags.HALIGN_RIGHT))
            .isEqualTo(Component.RIGHT_ALIGNMENT);
    }

    @Test
    void testXAlignmentDefaultsToLeftForAbsentToken() {
        assertThat(TextAlignmentMapping.xAlignment(null))
            .as("an absent token must map to LEFT_ALIGNMENT (the model default)")
            .isEqualTo(Component.LEFT_ALIGNMENT);
    }

    @Test
    void testXAlignmentDefaultsToLeftForUnrecognisedToken() {
        assertThat(TextAlignmentMapping.xAlignment("bogus"))
            .as("an unrecognised token must map to LEFT_ALIGNMENT (the model default)")
            .isEqualTo(Component.LEFT_ALIGNMENT);
    }

    @Test
    void testEachAlignmentRoundTripsThroughItsToken() {
        var alignments = new float[] {
            Component.LEFT_ALIGNMENT, Component.CENTER_ALIGNMENT, Component.RIGHT_ALIGNMENT
        };

        for (var alignment : alignments) {
            assertThat(TextAlignmentMapping.xAlignment(TextAlignmentMapping.alignToken(alignment)))
                .as("alignment %s must survive the token round-trip", alignment)
                .isEqualTo(alignment);
        }
    }
}
