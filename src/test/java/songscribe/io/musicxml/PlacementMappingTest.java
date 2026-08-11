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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Annotation;

/**
 * Verifies the bidirectional {@link PlacementMapping} between an
 * {@link Annotation.Placement} and the MusicXML {@code above}/{@code below} token.
 *
 * <p>Which token means which side is asserted here rather than left to the annotation
 * round-trip, because the two directions are separate structures — a {@code switch} out,
 * a {@code Map} back — and a round-trip agrees with itself as long as they are wrong the
 * same way. Only naming the token can say that {@code ABOVE} writes {@code "above"}.
 *
 * <p>The read direction is partial on purpose: {@code placement} is an optional attribute
 * and a foreign file may carry a token this reader does not model. Both come back
 * {@code null}, which is how {@link MeasureMapper} tells a {@code <direction>} that is not
 * one of ours from one that is.
 */
class PlacementMappingTest extends UnitTest {

    /** A placement token no MusicXML document declares, standing for anything unmodelled. */
    private static final String UNRECOGNISED_TOKEN = "middle";

    @Test
    void testPlacementTokenNamesTheSide() {
        assertThat(PlacementMapping.placementToken(Annotation.Placement.ABOVE))
            .isEqualTo(MusicXmlTags.PLACEMENT_ABOVE);
        assertThat(PlacementMapping.placementToken(Annotation.Placement.BELOW))
            .isEqualTo(MusicXmlTags.PLACEMENT_BELOW);
    }

    @Test
    void testPlacementForNamesTheSide() {
        assertThat(PlacementMapping.placementFor(MusicXmlTags.PLACEMENT_ABOVE))
            .isEqualTo(Annotation.Placement.ABOVE);
        assertThat(PlacementMapping.placementFor(MusicXmlTags.PLACEMENT_BELOW))
            .isEqualTo(Annotation.Placement.BELOW);
    }

    @Test
    void testPlacementForAbsentTokenIsNull() {
        assertThat(PlacementMapping.placementFor(null))
            .as("no placement attribute means the direction is not an annotation of ours")
            .isNull();
    }

    @Test
    void testPlacementForUnrecognisedTokenIsNull() {
        assertThat(PlacementMapping.placementFor(UNRECOGNISED_TOKEN))
            .as("a token this reader does not model is treated as no placement at all")
            .isNull();
    }

    @Test
    void testEachPlacementRoundTripsThroughItsToken() {
        for (var placement : Annotation.Placement.values()) {
            assertThat(PlacementMapping.placementFor(PlacementMapping.placementToken(placement)))
                .as("placement %s must survive the token round-trip", placement)
                .isEqualTo(placement);
        }
    }
}
