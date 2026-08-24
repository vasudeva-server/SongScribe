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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KeySignatureExtent}'s promise that what a key signature draws, and the room it takes, are
 * a function of <em>both</em> keys the change runs between — never of the new key alone.
 *
 * <p>The failure this guards is the one a caller who has only the new key to hand cannot see: a
 * change measured as though nothing preceded it omits the cancelling naturals, so the column
 * reserved for it is too narrow for the run actually drawn.
 */
class KeySignatureExtentTest extends UnitTest {

    /**
     * A change out of a key whose accidentals the new key does not keep, so the policy calls for
     * cancelling naturals — sharps giving way to flats.
     *
     * @param description the case, as the test's display name
     * @param previousKey the key in effect before the change
     * @param newKey      the key taking effect
     */
    private record CancellingCase(String description, Key previousKey, Key newKey) {}

    static Stream<CancellingCase> cancellingCases() {
        return Stream.of(
            new CancellingCase("sharps giving way to flats", Key.TWO_SHARPS, Key.ONE_FLAT),
            new CancellingCase("flats giving way to sharps", Key.THREE_FLATS, Key.ONE_SHARP),
            new CancellingCase("sharps giving way to none", Key.TWO_SHARPS, Key.NO_ACCIDENTALS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cancellingCases")
    void testAChangeThatCancelsDrawsWiderThanTheSameKeyOutOfNoAccidentals(CancellingCase testCase) {
        var cancelling = new KeySignatureExtent(testCase.previousKey(), testCase.newKey());
        var fromNothing = new KeySignatureExtent(Key.NO_ACCIDENTALS, testCase.newKey());

        assertThat(cancelling.accidentals())
            .as("the cancelling naturals are drawn on top of the new key's own accidentals")
            .hasSizeGreaterThan(fromNothing.accidentals().size());

        assertThat(cancelling.widthSs())
            .as("the column reserved is as wide as the run drawn")
            .isGreaterThan(fromNothing.widthSs());
    }

    @Test
    void testRestatingTheKeyAlreadyInEffectDrawsNothing() {
        var extent = new KeySignatureExtent(Key.TWO_SHARPS, Key.TWO_SHARPS);

        assertThat(extent.accidentals()).isEmpty();
        assertThat(extent.widthSs()).isZero();
    }
}
