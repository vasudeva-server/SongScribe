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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Dots#augment}'s promise that every {@link NoteValue} augments to an exact integer at
 * {@link songscribe.midi.MidiSequenceBuilder#PPQ} ticks per quarter, the tick base in the model.
 */
class DotsTest extends UnitTest {

    private record AugmentCase(String description, NoteValue value, Dots dots, int expectedTicks) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("augmentCases")
    void testAugmentReturnsExactTicksForEveryNoteValueAndDots(AugmentCase testCase) {
        assertThat(testCase.dots().augment(testCase.value().ticks()))
            .isEqualTo(testCase.expectedTicks());
    }

    @Test
    void testCasesCoverEveryNoteValueAndDotsCombination() {
        var expected = new ArrayList<List<Object>>();

        for (var value : NoteValue.values()) {
            for (var dots : Dots.values()) {
                expected.add(List.of(value, dots));
            }
        }

        var actual = augmentCases()
            .map(testCase -> List.<Object>of(testCase.value(), testCase.dots()))
            .toList();

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    static Stream<AugmentCase> augmentCases() {
        return Stream.of(
            new AugmentCase("whole, no dots", NoteValue.WHOLE, Dots.NONE, 384),
            new AugmentCase("whole, one dot", NoteValue.WHOLE, Dots.ONE, 576),
            new AugmentCase("whole, two dots", NoteValue.WHOLE, Dots.TWO, 672),
            new AugmentCase("half, no dots", NoteValue.HALF, Dots.NONE, 192),
            new AugmentCase("half, one dot", NoteValue.HALF, Dots.ONE, 288),
            new AugmentCase("half, two dots", NoteValue.HALF, Dots.TWO, 336),
            new AugmentCase("quarter, no dots", NoteValue.QUARTER, Dots.NONE, 96),
            new AugmentCase("quarter, one dot", NoteValue.QUARTER, Dots.ONE, 144),
            new AugmentCase("quarter, two dots", NoteValue.QUARTER, Dots.TWO, 168),
            new AugmentCase("eighth, no dots", NoteValue.EIGHTH, Dots.NONE, 48),
            new AugmentCase("eighth, one dot", NoteValue.EIGHTH, Dots.ONE, 72),
            new AugmentCase("eighth, two dots", NoteValue.EIGHTH, Dots.TWO, 84),
            new AugmentCase("sixteenth, no dots", NoteValue.SIXTEENTH, Dots.NONE, 24),
            new AugmentCase("sixteenth, one dot", NoteValue.SIXTEENTH, Dots.ONE, 36),
            new AugmentCase("sixteenth, two dots", NoteValue.SIXTEENTH, Dots.TWO, 42),
            new AugmentCase("thirty-second, no dots", NoteValue.THIRTY_SECOND, Dots.NONE, 12),
            new AugmentCase("thirty-second, one dot", NoteValue.THIRTY_SECOND, Dots.ONE, 18),
            new AugmentCase("thirty-second, two dots", NoteValue.THIRTY_SECOND, Dots.TWO, 21)
        );
    }
}
