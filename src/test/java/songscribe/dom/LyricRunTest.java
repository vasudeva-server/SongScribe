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
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.singleBarline;

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
        assertThat(line.getElement(INSERTION_INDEX + 1).getLyricForScript(Song.Script.LATIN))
            .as("and the carrier that closed it, having no text of its own, is gone entirely")
            .isNull();
    }

    /**
     * What one element's first-script lyric must read as after an insertion.
     *
     * @param index    the element's index once the note is in
     * @param syllabic its syllabic, or {@code null} when it holds no syllable
     * @param extend   its extend, or {@code null} when it holds no lyric at all
     */
    private record Probe(int index, Lyric.@Nullable Syllabic syllabic, Lyric.@Nullable Extend extend) {}

    /**
     * One blank note inserted where a chain's neighbor stands past an element holding no lyric.
     *
     * @param description    the case, as the test's display name
     * @param elements       the line's elements, in order
     * @param insertionIndex where the note lands
     * @param probes         what the chain's members must then read as
     */
    private record DistantNeighborCase(
        String description, List<StaffElement> elements, int insertionIndex, List<Probe> probes
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static final int AFTER_SECOND_ELEMENT = 2;
    private static final int LAST_OF_FOUR = 3;
    private static final int LAST_OF_FIVE = 4;

    static Stream<DistantNeighborCase> distantNeighborCases() {
        return Stream.of(
            new DistantNeighborCase("the word ends on a syllable standing past a barline before the insertion",
                List.of(openingSyllable(Lyric.Extend.NONE, Lyric.Syllabic.BEGIN), singleBarline(),
                    closingSyllable()),
                AFTER_SECOND_ELEMENT,
                List.of(new Probe(0, Lyric.Syllabic.SINGLE, Lyric.Extend.NONE))),
            new DistantNeighborCase("a new word starts on a syllable standing past a rest after the insertion",
                List.of(openingSyllable(Lyric.Extend.NONE, Lyric.Syllabic.BEGIN), crotchetRest(),
                    closingSyllable()),
                INSERTION_INDEX,
                List.of(new Probe(LAST_OF_FOUR, Lyric.Syllabic.SINGLE, Lyric.Extend.NONE))),
            new DistantNeighborCase("the melisma breaks past a rest on either side of the insertion",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), crotchetRest(),
                    crotchetRest(), closingCarrier()),
                AFTER_SECOND_ELEMENT,
                List.of(
                    new Probe(0, Lyric.Syllabic.SINGLE, Lyric.Extend.NONE),
                    new Probe(LAST_OF_FIVE, null, null)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("distantNeighborCases")
    void testAnInterruptingInsertionFindsAChainsNeighborPastAnElementHoldingNoLyric(DistantNeighborCase testCase) {
        var line = detachedLine();
        testCase.elements().forEach(line::addElement);

        line.insertRun(testCase.insertionIndex(), List.of(crotchet()));

        for (var probe : testCase.probes()) {
            assertThat(syllabicAt(line, probe.index())).isEqualTo(probe.syllabic());
            assertThat(extendAt(line, probe.index())).isEqualTo(probe.extend());
        }
    }

    /** Where every {@link SpanEndCase} starts the walk: right after the element that opens the span. */
    private static final int SPAN_START = 1;

    /**
     * One {@link LyricRun#lyricSpanEnd} walk and the end it must find.
     *
     * @param description the case, as the test's display name
     * @param elements    the run's elements, the first of which opens the span
     * @param start       where the walk starts
     * @param expected    where the span ends
     */
    private record SpanEndCase(
        String description, List<StaffElement> elements, int start, LyricRun.LyricSpanEnd expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("spanEndCases")
    void testASpanEndsAtTheFirstOfItsFourEnds(SpanEndCase testCase) {
        var line = detachedLine();
        testCase.elements().forEach(line::addElement);

        assertThat(line.lyricSpanEnd(testCase.start(), Song.Script.LATIN)).isEqualTo(testCase.expected());
    }

    static Stream<SpanEndCase> spanEndCases() {
        return Stream.of(
            new SpanEndCase("a STOP carrier closes the melisma and is covered",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), crotchet(),
                    carrying(Lyric.Extend.CONTINUE), closingCarrier()),
                SPAN_START, new LyricRun.LyricSpanEnd(LAST_OF_FOUR, false)),
            new SpanEndCase("a text-bearing lyric starts a span of its own and is not covered",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), crotchet(), closingSyllable()),
                SPAN_START, new LyricRun.LyricSpanEnd(SPAN_START, false)),
            new SpanEndCase("a repeat is not covered, and nothing runs through it",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), crotchet(), repeatLeft(),
                    crotchet()),
                SPAN_START, new LyricRun.LyricSpanEnd(SPAN_START, false)),
            new SpanEndCase("the end of the run leaves the span open, over a rest and a barline",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), crotchetRest(), singleBarline(),
                    crotchet()),
                SPAN_START, new LyricRun.LyricSpanEnd(LAST_OF_FOUR, true)),
            new SpanEndCase("a lyric in another script does not end the span",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), bengaliSyllable(), crotchet()),
                SPAN_START, new LyricRun.LyricSpanEnd(AFTER_SECOND_ELEMENT, true)),
            new SpanEndCase("a text-bearing lyric right after the opener covers nothing",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), closingSyllable()),
                SPAN_START, new LyricRun.LyricSpanEnd(SPAN_START - 1, false)),
            new SpanEndCase("a repeat right after the opener covers nothing",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE), repeatLeft()),
                SPAN_START, new LyricRun.LyricSpanEnd(SPAN_START - 1, false)),
            new SpanEndCase("a span opened by the run's last element covers nothing and stays open",
                List.of(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE)),
                SPAN_START, new LyricRun.LyricSpanEnd(SPAN_START - 1, true))
        );
    }

    /** A note carrying a carrier in the Latin script. */
    private static NoteElement carrying(Lyric.Extend extend) {
        var note = crotchet();
        note.putLyric(Lyric.carrier(Song.Script.LATIN, extend));

        return note;
    }

    /** A note carrying a syllable in the Bengali script only. */
    private static NoteElement bengaliSyllable() {
        var note = crotchet();
        note.putLyric(new Lyric(Song.Script.BENGALI, LAST_SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

        return note;
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
        line.addElement(openingSyllable(Lyric.Extend.NONE, Lyric.Syllabic.BEGIN));
        line.addElement(closingSyllable());

        return line;
    }

    /** A note carrying {@link #FIRST_SYLLABLE}. */
    private static NoteElement openingSyllable(Lyric.Extend extend, Lyric.Syllabic syllabic) {
        var note = crotchet();
        note.putLyric(new Lyric(Song.Script.LATIN, FIRST_SYLLABLE, extend, syllabic, false));

        return note;
    }

    /** A note carrying {@link #LAST_SYLLABLE} as the end of a word. */
    private static NoteElement closingSyllable() {
        var note = crotchet();
        note.putLyric(
            new Lyric(Song.Script.LATIN, LAST_SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.END, false));

        return note;
    }

    /** A note carrying the carrier that closes a melisma. */
    private static NoteElement closingCarrier() {
        var note = crotchet();
        note.putLyric(Lyric.carrier(Song.Script.LATIN, Lyric.Extend.STOP));

        return note;
    }

    /** A syllable and the carrier closing its melisma, with the insertion point between them. */
    private Line lineWithMelisma() {
        var line = detachedLine();
        line.addElement(openingSyllable(Lyric.Extend.START, Lyric.Syllabic.SINGLE));
        line.addElement(closingCarrier());

        return line;
    }

    private static void insert(Line line, InsertedElement testCase) {
        line.insertRun(INSERTION_INDEX, List.of(testCase.element().get()));
    }

    private static Lyric.@Nullable Syllabic syllabicAt(Line line, int index) {
        var lyric = line.getElement(index).getLyricForScript(Song.Script.LATIN);
        return lyric != null ? lyric.syllabic() : null;
    }

    private static Lyric.@Nullable Extend extendAt(Line line, int index) {
        var lyric = line.getElement(index).getLyricForScript(Song.Script.LATIN);
        return lyric != null ? lyric.extend() : null;
    }
}
