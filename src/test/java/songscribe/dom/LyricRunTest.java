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

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;

/**
 * Which insertions break a word or a melisma and which are transparent to one — the promise
 * {@link ElementType#interruptsLyricChain()} makes and {@link LyricRun}'s insertion repairs keep.
 * See {@code docs/lyrics.md}.
 */
class LyricRunTest extends UnitTest {

    /** One element to insert, with the name its row reports. */
    private record InsertedElement(String description, Supplier<StaffElement> element) {}

    /** The index every insertion here lands at: between the two elements carrying the chain. */
    private static final int INSERTION_INDEX = 1;

    private static final double INSERTION_POSITION_SS = 0.0;

    private static final int NO_TAIL_SHIFT_PX = 0;

    private static final String FIRST_SYLLABLE = "hal";

    private static final String LAST_SYLLABLE = "le";

    @ParameterizedTest(name = "{0}")
    @MethodSource("transparentElements")
    void testAnElementThatCanTakeNoSyllableLeavesAWordWhole(InsertedElement testCase) {
        var line = lineWithHyphenatedWord();

        insert(line, testCase);

        assertThat(syllabicAt(line, 0))
            .as("the word runs on across an element that could never have been sung as a syllable")
            .isEqualTo(Lyric.Syllabic.BEGIN);
        assertThat(syllabicAt(line, INSERTION_INDEX + 1))
            .as("and its last syllable still closes the word it opened")
            .isEqualTo(Lyric.Syllabic.END);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transparentElements")
    void testAnElementThatCanTakeNoSyllableLeavesAMelismaRunning(InsertedElement testCase) {
        var line = lineWithMelisma();

        insert(line, testCase);

        assertThat(extendAt(line, 0))
            .as("the melisma runs on across an element that could never have been sung as a syllable")
            .isEqualTo(Lyric.Extend.START);
        assertThat(extendAt(line, INSERTION_INDEX + 1))
            .as("and its carrier still closes it")
            .isEqualTo(Lyric.Extend.STOP);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("interruptingElements")
    void testAnInterruptingElementBreaksTheWord(InsertedElement testCase) {
        var line = lineWithHyphenatedWord();

        insert(line, testCase);

        assertThat(syllabicAt(line, 0))
            .as("the syllable that opened the word has nothing left to run into")
            .isEqualTo(Lyric.Syllabic.SINGLE);
        assertThat(syllabicAt(line, INSERTION_INDEX + 1))
            .as("and the syllable that closed it now stands alone")
            .isEqualTo(Lyric.Syllabic.SINGLE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("interruptingElements")
    void testAnInterruptingElementBreaksTheMelisma(InsertedElement testCase) {
        var line = lineWithMelisma();

        insert(line, testCase);

        assertThat(extendAt(line, 0))
            .as("the syllable the melisma was sung on no longer holds")
            .isEqualTo(Lyric.Extend.NONE);
        assertThat(line.getElement(INSERTION_INDEX + 1).getLyricForVerse(Lyric.FIRST_VERSE))
            .as("and the carrier that closed it, having no text of its own, is gone entirely")
            .isNull();
    }

    static Stream<InsertedElement> transparentElements() {
        return Stream.of(
            new InsertedElement("a rest bears a carrier but never a syllable", StaffElementFactory::crotchetRest),
            new InsertedElement("a barline", StaffElementFactory::singleBarline),
            new InsertedElement("a breath mark", StaffElementFactory::breathMark),
            new InsertedElement("a key change", () -> keyChange(Key.NO_ACCIDENTALS))
        );
    }

    static Stream<InsertedElement> interruptingElements() {
        return Stream.of(
            new InsertedElement("a note is a syllable slot arriving empty", StaffElementFactory::crotchet),
            new InsertedElement("a grace note is one too", StaffElementFactory::graceQuaver),
            new InsertedElement("a repeat ends a section, so nothing runs through it",
                StaffElementFactory::repeatLeft)
        );
    }

    /** Two syllables of one hyphenated word, with the insertion point between them. */
    private Line lineWithHyphenatedWord() {
        var line = detachedLine();
        var opening = crotchet();
        opening.setLyricForVerse(
            Lyric.FIRST_VERSE, Lyric.Syllabic.BEGIN, false, FIRST_SYLLABLE, Lyric.Extend.NONE);
        line.addElement(opening);

        var closing = crotchet();
        closing.setLyricForVerse(
            Lyric.FIRST_VERSE, Lyric.Syllabic.END, false, LAST_SYLLABLE, Lyric.Extend.NONE);
        line.addElement(closing);

        return line;
    }

    /** A syllable and the carrier closing its melisma, with the insertion point between them. */
    private Line lineWithMelisma() {
        var line = detachedLine();
        var syllable = crotchet();
        syllable.setLyricForVerse(
            Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, FIRST_SYLLABLE, Lyric.Extend.START);
        line.addElement(syllable);

        var carrier = crotchet();
        carrier.setLyricForVerse(Lyric.FIRST_VERSE, null, false, null, Lyric.Extend.STOP);
        line.addElement(carrier);

        return line;
    }

    private static void insert(Line line, InsertedElement testCase) {
        line.insertRun(
            INSERTION_INDEX,
            List.of(new Line.PlacedElement(testCase.element().get(), INSERTION_POSITION_SS)),
            NO_TAIL_SHIFT_PX);
    }

    private static Lyric.@Nullable Syllabic syllabicAt(Line line, int index) {
        var lyric = line.getElement(index).getLyricForVerse(Lyric.FIRST_VERSE);
        return lyric != null ? lyric.syllabic() : null;
    }

    private static Lyric.@Nullable Extend extendAt(Line line, int index) {
        var lyric = line.getElement(index).getLyricForVerse(Lyric.FIRST_VERSE);
        return lyric != null ? lyric.extend() : null;
    }
}
