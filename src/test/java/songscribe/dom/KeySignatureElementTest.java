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

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

/**
 * Tests {@link KeySignatureElement}: the key it establishes and the width that key change draws.
 *
 * <h2>What this class is responsible for</h2>
 * The element's own promises. The cancellation policy that decides <em>which</em> accidentals a
 * change draws is {@code KeyChangeTest}'s, and which key is in effect where along a line is
 * {@code LineMutationTest}'s; what is left here is that the element reports its type and key, and
 * that its content width is the width of the change from the key in front of it.
 *
 * <p><b>Where the key in front comes from</b> is the width contract's whole domain, and all three
 * of its sources are rows in one table: the snapshot a {@code forMeasurement} element carries, the
 * line an element in a document sits on, and C major for an element that has neither. The zero
 * case — an element re-stating the key already in effect — is a row of the same table.
 *
 * <p><b>No case places a key signature at index 0.</b> The class's position invariant forbids it,
 * so a width there is not a boundary this class owes an answer for — it is a state the document
 * cannot reach.
 */
class KeySignatureElementTest extends UnitTest {

    private static final Key TWO_SHARPS = new Key(KeyType.SHARPS, 2);
    private static final Key THREE_FLATS = new Key(KeyType.FLATS, 3);

    /** The key an element with nothing to state what precedes it measures from. */
    private static final Key C_MAJOR = new Key(KeyType.NONE, 0);

    /** The index the fixture puts the key signature at, immediately behind a barline. */
    private static final int KEY_SIGNATURE_INDEX = 2;

    private Line line;

    @BeforeEach
    void setUp() {
        line = detachedLine();
        line.setKey(THREE_FLATS);
        line.addElement(new StaffElement(ElementType.CROTCHET));
        line.addElement(new StaffElement(ElementType.SINGLE_BARLINE));
    }

    @Test
    void testGetTypeIsKeySignature() {
        assertThat(new KeySignatureElement(TWO_SHARPS).getType()).isEqualTo(ElementType.KEY_SIGNATURE);
    }

    @Test
    void testGetKeyReturnsTheKeyItEstablishes() {
        assertThat(new KeySignatureElement(TWO_SHARPS).getKey()).isEqualTo(TWO_SHARPS);
    }

    @Test
    void testSetKeyReplacesTheKeyItEstablishes() {
        var element = new KeySignatureElement(TWO_SHARPS);
        element.setKey(THREE_FLATS);

        assertThat(element.getKey()).isEqualTo(THREE_FLATS);
    }

    @Test
    void testCloneCarriesTheKey() {
        assertThat(new KeySignatureElement(TWO_SHARPS).clone().getKey()).isEqualTo(TWO_SHARPS);
    }

    /**
     * @param arrange           builds the element under test, given the fixture line
     * @param expectedPreviousKey the key the element must report the change as being <em>from</em>
     * @param expectedKey       the key the element establishes
     */
    record ContentWidthCase(
        String description,
        Function<Line, KeySignatureElement> arrange,
        Key expectedPreviousKey,
        Key expectedKey) {}

    static Stream<ContentWidthCase> contentWidthCases() {
        return Stream.of(
            new ContentWidthCase(
                "on a line, the key the line is in at the element before it",
                line -> addAt(line, KEY_SIGNATURE_INDEX, new KeySignatureElement(TWO_SHARPS)),
                THREE_FLATS,
                TWO_SHARPS),
            new ContentWidthCase(
                "on a line, re-stating the key already in effect draws nothing",
                line -> addAt(line, KEY_SIGNATURE_INDEX, new KeySignatureElement(THREE_FLATS)),
                THREE_FLATS,
                THREE_FLATS),
            new ContentWidthCase(
                "on a line, a second key signature measures from the first",
                KeySignatureElementTest::addSecondKeySignature,
                TWO_SHARPS,
                THREE_FLATS),
            new ContentWidthCase(
                "built for measurement, the key stated when it was built",
                line -> KeySignatureElement.forMeasurement(TWO_SHARPS, THREE_FLATS),
                THREE_FLATS,
                TWO_SHARPS),
            new ContentWidthCase(
                "on no line and not built for measurement, C major",
                line -> new KeySignatureElement(TWO_SHARPS),
                C_MAJOR,
                TWO_SHARPS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentWidthCases")
    void testContentWidthIsTheWidthOfTheChangeFromTheKeyInFront(ContentWidthCase testCase) {
        var element = testCase.arrange().apply(line);

        assertThat(element.getContentWidthSs())
            .isEqualTo(KeyChange.widthSs(testCase.expectedPreviousKey(), testCase.expectedKey()));
    }

    private static KeySignatureElement addAt(Line line, int index, KeySignatureElement element) {
        line.addElement(index, element);
        return element;
    }

    /** Extends the fixture with a second measure, and returns the key signature that opens it. */
    private static KeySignatureElement addSecondKeySignature(Line line) {
        addAt(line, KEY_SIGNATURE_INDEX, new KeySignatureElement(TWO_SHARPS));
        line.addElement(new StaffElement(ElementType.CROTCHET));
        line.addElement(new StaffElement(ElementType.SINGLE_BARLINE));

        var second = new KeySignatureElement(THREE_FLATS);
        line.addElement(second);

        return second;
    }
}
