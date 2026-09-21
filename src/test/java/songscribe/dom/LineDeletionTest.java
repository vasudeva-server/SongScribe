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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link Line#deleteRange} leaves of the lyrics around the range it removes, and the order
 * {@link Line#deleteRanges} takes several ranges in.
 *
 * <p>The deletion an edit performs, as against the {@link Line#removeRange} primitive beneath it.
 * The lyric cases state its contract: a syllable goes with its note, a word closes up around the
 * gap, a melisma follows its syllable, and an element holding no lyric is no neighbor to either.
 */
class LineDeletionTest extends UnitTest {

    /** The indices of a lyric fixture's third and fourth elements. */
    private static final int THIRD = 2;
    private static final int FOURTH = 3;

    /** How {@link #lyricsOf} writes an element that holds no lyric. */
    private static final String NO_LYRIC = "none";

    /** How {@link #lyricsOf} writes a melisma carrier, in front of its extend. */
    private static final String CARRIER = "_";

    /**
     * One deletion, and the lyrics the line is left with.
     *
     * @param description the case, as the test's display name
     * @param elements    the line's elements, in order
     * @param named       the range the caller names, before widening
     * @param expected    what {@link #lyricsOf} must read off the line afterwards
     */
    private record LyricCase(
        String description, List<StaffElement> elements, StaffElementRun.ElementRange named, List<String> expected
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static NoteElement syllable(String text, Lyric.Syllabic syllabic, Lyric.Extend extend) {
        return withLyric(crotchet(), new Lyric(Lyric.FIRST_VERSE, text, extend, syllabic, false));
    }

    private static NoteElement syllable(String text, Lyric.Syllabic syllabic) {
        return syllable(text, syllabic, Lyric.Extend.NONE);
    }

    private static NoteElement carrier(Lyric.Extend extend) {
        return withLyric(crotchet(), Lyric.carrier(Lyric.FIRST_VERSE, extend));
    }

    /** A grace note paired with the note after it, carrying the pair's syllable and its melisma. */
    private static NoteElement pairedGrace(String text, Lyric.Syllabic syllabic) {
        var grace = withLyric(
            graceQuaver(), new Lyric(Lyric.FIRST_VERSE, text, Lyric.Extend.START, syllabic, false));
        grace.setSlide(Slide.GLISSANDO);

        return grace;
    }

    private static NoteElement withLyric(NoteElement note, Lyric lyric) {
        note.putLyric(lyric);

        return note;
    }

    /** The first-verse lyric of every element on {@code line}, in order, one string each. */
    private static List<String> lyricsOf(Line line) {
        return elementsOf(line).stream()
            .map(element -> element.getLyricForVerse(Lyric.FIRST_VERSE))
            .map(lyric -> {
                if (lyric == null) {
                    return NO_LYRIC;
                }

                return lyric.isCarrier()
                    ? CARRIER + lyric.extend()
                    : lyric.text() + " " + lyric.syllabic() + " " + lyric.extend();
            })
            .toList();
    }

    static Stream<LyricCase> lyricCases() {
        return Stream.of(
            new LyricCase("a word that loses its last syllable ends on the one before",
                List.of(syllable("hel", Lyric.Syllabic.BEGIN), syllable("lo", Lyric.Syllabic.END)),
                StaffElementRun.ElementRange.single(1),
                List.of("hel SINGLE NONE")),
            new LyricCase("a word that loses its first syllable begins on the next",
                List.of(syllable("hel", Lyric.Syllabic.BEGIN), syllable("lo", Lyric.Syllabic.END)),
                StaffElementRun.ElementRange.single(0),
                List.of("lo SINGLE NONE")),
            new LyricCase("a word that loses a middle syllable stays one word",
                List.of(syllable("a", Lyric.Syllabic.BEGIN), syllable("b", Lyric.Syllabic.MIDDLE),
                    syllable("c", Lyric.Syllabic.END)),
                StaffElementRun.ElementRange.single(1),
                List.of("a BEGIN NONE", "c END NONE")),
            new LyricCase("a word that loses every syllable but its first is a single syllable",
                List.of(syllable("a", Lyric.Syllabic.BEGIN), syllable("b", Lyric.Syllabic.MIDDLE),
                    syllable("c", Lyric.Syllabic.MIDDLE), syllable("d", Lyric.Syllabic.END)),
                new StaffElementRun.ElementRange(1, FOURTH),
                List.of("a SINGLE NONE")),
            new LyricCase("two words that each lose a syllable do not join",
                List.of(syllable("a", Lyric.Syllabic.BEGIN), syllable("b", Lyric.Syllabic.END),
                    syllable("c", Lyric.Syllabic.BEGIN), syllable("d", Lyric.Syllabic.END)),
                new StaffElementRun.ElementRange(1, THIRD),
                List.of("a SINGLE NONE", "d SINGLE NONE")),
            new LyricCase("a rest deleted from inside a word changes no lyric",
                List.of(syllable("hel", Lyric.Syllabic.BEGIN), crotchetRest(),
                    syllable("lo", Lyric.Syllabic.END)),
                StaffElementRun.ElementRange.single(1),
                List.of("hel BEGIN NONE", "lo END NONE")),
            new LyricCase("a barline beside the range does not hide the syllable behind it",
                List.of(syllable("hel", Lyric.Syllabic.BEGIN), singleBarline(),
                    syllable("lo", Lyric.Syllabic.END)),
                StaffElementRun.ElementRange.single(THIRD),
                List.of("hel SINGLE NONE", NO_LYRIC)),
            new LyricCase("a pair that goes whole counts as the note of its grace note's syllable",
                List.of(syllable("a", Lyric.Syllabic.BEGIN), pairedGrace("b", Lyric.Syllabic.MIDDLE),
                    carrier(Lyric.Extend.STOP), syllable("c", Lyric.Syllabic.END)),
                StaffElementRun.ElementRange.single(THIRD),
                List.of("a BEGIN NONE", "c END NONE")),
            new LyricCase("a melisma that loses its syllable loses its extender and its carriers",
                List.of(syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START),
                    carrier(Lyric.Extend.CONTINUE), carrier(Lyric.Extend.STOP)),
                StaffElementRun.ElementRange.single(0),
                List.of(NO_LYRIC, NO_LYRIC)),
            new LyricCase("a melisma that loses its close closes on the note before",
                List.of(syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START),
                    carrier(Lyric.Extend.CONTINUE), carrier(Lyric.Extend.STOP)),
                StaffElementRun.ElementRange.single(THIRD),
                List.of("la SINGLE START", CARRIER + Lyric.Extend.STOP)),
            new LyricCase("a melisma that loses a note inside it keeps its close",
                List.of(syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START),
                    carrier(Lyric.Extend.CONTINUE), carrier(Lyric.Extend.STOP)),
                StaffElementRun.ElementRange.single(1),
                List.of("la SINGLE START", CARRIER + Lyric.Extend.STOP)),
            new LyricCase("a melisma that loses every note under it is an ordinary syllable",
                List.of(syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START),
                    carrier(Lyric.Extend.CONTINUE), carrier(Lyric.Extend.STOP)),
                new StaffElementRun.ElementRange(1, THIRD),
                List.of("la SINGLE NONE")),
            new LyricCase("a rest in front of a deleted close does not leave the melisma open",
                List.of(syllable("la", Lyric.Syllabic.SINGLE, Lyric.Extend.START), crotchetRest(),
                    carrier(Lyric.Extend.STOP)),
                StaffElementRun.ElementRange.single(THIRD),
                List.of("la SINGLE NONE", NO_LYRIC)),
            new LyricCase("a paired grace note deleted alone takes its syllable, and its host is left empty",
                List.of(pairedGrace("la", Lyric.Syllabic.SINGLE), carrier(Lyric.Extend.STOP)),
                StaffElementRun.ElementRange.single(0),
                List.of(NO_LYRIC)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lyricCases")
    void testDeleteRangeLeavesWholeWordsAndWholeMelismas(LyricCase testCase) {
        var line = lineHolding(testCase.elements());

        line.deleteRange(line.effectiveRange(testCase.named()));

        assertThat(lyricsOf(line)).isEqualTo(testCase.expected());
    }

    /** The key the fixture line runs in, and the one both its key changes restate. */
    private static final Key LINE_KEY = Key.NO_ACCIDENTALS;

    private static final int FIRST_BARLINE_INDEX = 1;
    private static final int FIRST_KEY_CHANGE_INDEX = 2;
    private static final int MIDDLE_NOTE_INDEX = 3;
    private static final int SECOND_BARLINE_INDEX = 4;
    private static final int SECOND_KEY_CHANGE_INDEX = 5;
    private static final int LAST_NOTE_INDEX = 6;

    /**
     * A line in {@link #LINE_KEY} holding {@code note, barline, key change, note, barline,
     * key change, note} — two complete pairs with a note either side of each.
     *
     * @return the elements in the order they stand on the line, so a case can name survivors by
     *     the index they were built at
     */
    private static List<StaffElement> fixtureElements() {
        return List.of(
            crotchet(), singleBarline(), keyChange(LINE_KEY),
            crotchet(), singleBarline(), keyChange(LINE_KEY),
            crotchet());
    }

    private static Line lineHolding(List<StaffElement> elements) {
        var line = detachedLine();

        line.setKey(LINE_KEY);

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    /** The elements standing on {@code line}, in order, for an identity comparison. */
    private static List<StaffElement> elementsOf(Line line) {
        var standing = new ArrayList<StaffElement>();

        for (var index = 0; index < line.effectiveElementCount(); index++) {
            standing.add(line.getElement(index));
        }

        return standing;
    }

    private static List<StaffElement> at(List<StaffElement> elements, List<Integer> indices) {
        return indices.stream().map(elements::get).toList();
    }

    @Test
    void testDeleteRangesRemovesTheElementsEveryRangeNamed() {
        var elements = fixtureElements();
        var line = lineHolding(elements);

        line.deleteRanges(List.of(
            line.effectiveRange(
                new StaffElementRun.ElementRange(FIRST_BARLINE_INDEX, FIRST_KEY_CHANGE_INDEX)),
            line.effectiveRange(
                new StaffElementRun.ElementRange(SECOND_BARLINE_INDEX, SECOND_KEY_CHANGE_INDEX))));

        assertThat(elementsOf(line))
            .as("taken in ascending order the first removal would shift the second range onto the "
                + "wrong elements, so both pairs gone and every note standing is the ordering's "
                + "whole claim")
            .containsExactlyElementsOf(at(elements, List.of(0, MIDDLE_NOTE_INDEX, LAST_NOTE_INDEX)));
    }
}
