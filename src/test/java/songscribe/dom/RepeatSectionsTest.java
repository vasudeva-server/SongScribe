/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.dom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * {@link RepeatSections}: where the section each right repeat closes begins, which of a song's
 * repeat bars are left over, and where the section open before an element began.
 *
 * <p>A case writes the song as a notation and names the elements it asserts about, so no case
 * counts element indexes. The notation is whitespace-separated tokens: {@code |:}, {@code :|},
 * {@code :|:}, {@code ||} and {@code fin} are the bars, {@code N} is a note, and {@code /} ends
 * the line. A token may end in {@code #name}, which names the element for the case's expected
 * values.
 */
class RepeatSectionsTest extends UnitTest {

    private static final String LINE_BREAK = "/";
    private static final String TAG_SEPARATOR = "#";
    private static final String WHITESPACE = "\\s+";

    /** A song written in notation, and the element locations its tokens named. */
    private record Notated(Song song, Map<String, ElementLocation> tags) {

        ElementLocation at(String tag) {
            var location = tags.get(tag);

            assertThat(location).as("the notation names an element %s", tag).isNotNull();

            return location;
        }
    }

    private static Notated notated(String notation) {
        var lineNotations = notation.split(LINE_BREAK, -1);
        var builders = new SongFactory.LineBuilder[lineNotations.length];
        var tags = new HashMap<String, ElementLocation>();

        for (var lineIndex = 0; lineIndex < lineNotations.length; lineIndex++) {
            var lineNotation = lineNotations[lineIndex].trim();
            var currentLineIndex = lineIndex;

            builders[lineIndex] = line -> {
                if (currentLineIndex == 0) {
                    line.setKey(Key.NO_ACCIDENTALS);
                }

                for (var token : lineNotation.split(WHITESPACE)) {
                    if (token.isEmpty()) {
                        continue;
                    }

                    var parts = token.split(TAG_SEPARATOR, 2);

                    if (parts.length > 1) {
                        tags.put(parts[1], new ElementLocation(currentLineIndex, line.getElements().size()));
                    }

                    line.addElement(elementOf(parts[0]));
                }
            };
        }

        return new Notated(SongFactory.buildSong(builders), tags);
    }

    private static StaffElement elementOf(String token) {
        return switch (token) {
            case "|:" -> repeatLeft();
            case ":|" -> repeatRight();
            case ":|:" -> repeatLeftRight();
            case "||" -> doubleBarline();
            case "fin" -> finalDoubleBarline();
            case "N" -> crotchet();
            default -> throw new IllegalArgumentException("Unrecognized notation token: " + token);
        };
    }

    /**
     * One right repeat that closes a section, and the first element of that section.
     *
     * @param closer the right repeat's name
     * @param start  the name of the section's first element
     */
    private record ClosedSection(String closer, String start) {}

    private static ClosedSection closed(String closer, String start) {
        return new ClosedSection(closer, start);
    }

    /**
     * A song and the model {@link RepeatSections#of} must build from it.
     *
     * @param description     the case, as the test's display name
     * @param notation        the song
     * @param closedSections  every right repeat that closes a section
     * @param unopenedClosers the right repeats that find no open section
     * @param unclosedOpeners the openers a closing bar or the song end ends first
     * @param nestedOpeners   the openers that come while a section is open
     */
    private record SectionsCase(
        String description,
        String notation,
        List<ClosedSection> closedSections,
        List<String> unopenedClosers,
        List<String> unclosedOpeners,
        List<String> nestedOpeners
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("sectionsCases")
    void testTheModelPairsEveryRepeatBarOrLeavesItUnmatched(SectionsCase testCase) {
        var notated = notated(testCase.notation());
        var model = RepeatSections.of(notated.song());

        for (var closedSection : testCase.closedSections()) {
            assertThat(model.sectionStart(notated.at(closedSection.closer())))
                .as("the start of the section %s closes", closedSection.closer())
                .isEqualTo(notated.at(closedSection.start()));
        }

        var unopenedClosers = locationsOf(notated, testCase.unopenedClosers());

        for (var closer : unopenedClosers) {
            assertThat(model.sectionStart(closer)).as("the start before unopened closer %s", closer).isNull();
        }

        assertThat(model.unopenedClosers()).containsExactlyElementsOf(unopenedClosers);
        assertThat(model.unclosedOpeners()).containsExactlyElementsOf(locationsOf(notated, testCase.unclosedOpeners()));
        assertThat(model.nestedOpeners()).containsExactlyElementsOf(locationsOf(notated, testCase.nestedOpeners()));
    }

    private static List<ElementLocation> locationsOf(Notated notated, List<String> tags) {
        return tags.stream().map(notated::at).toList();
    }

    static Stream<SectionsCase> sectionsCases() {
        var none = List.<String>of();

        return Stream.of(
            new SectionsCase("|: N :| is one section",
                "|: N#start :|#close",
                List.of(closed("close", "start")), none, none, none),

            new SectionsCase("|: |: :| :| nests the second opener; the last :| closes the outer section",
                "|: |:#inner :|#innerClose :|#outerClose",
                List.of(closed("innerClose", "innerClose"), closed("outerClose", "inner")),
                none, none, List.of("inner")),

            new SectionsCase("|: A || B leaves the opener unclosed",
                "|:#open N || N",
                List.of(), none, List.of("open"), none),

            new SectionsCase("A :|: B :| is two sections, the first opened by the song start",
                "N#first :|:#both N#second :|#close",
                List.of(closed("both", "first"), closed("close", "second")),
                none, none, none),

            new SectionsCase("|: A :|: B leaves the :|: opener unclosed",
                "|: N#start :|:#both N",
                List.of(closed("both", "start")), none, List.of("both"), none),

            new SectionsCase("a section across a line break",
                "|: N#start / N :|#close",
                List.of(closed("close", "start")), none, none, none),

            new SectionsCase("A :| B :| closes the song-start section, then finds the second :| unopened",
                "N#start :|#first N :|#second",
                List.of(closed("first", "start")), List.of("second"), none, none),

            new SectionsCase("A || B :| leaves the :| unopened, the || having closed every section",
                "N || N :|#close",
                List.of(), List.of("close"), none, none),

            new SectionsCase("a final barline closes the open section like a ||",
                "|:#open N fin N :|#close",
                List.of(), List.of("close"), List.of("open"), none),

            new SectionsCase("an opener nested in an unclosed outer opener is reported only as nested",
                "|:#outer |:#inner N",
                List.of(), none, List.of("outer"), List.of("inner")),

            new SectionsCase("a leading :| closes the song-start section, which holds no element",
                ":|#close N",
                List.of(closed("close", "close")), none, none, none),

            new SectionsCase("a leading :|: closes the song-start section, then opens a new one",
                ":|:#both N#start :|#close",
                List.of(closed("both", "both"), closed("close", "start")), none, none, none)
        );
    }

    /**
     * A song, an element in it, and the first element of the section open just before it.
     *
     * @param description the case, as the test's display name
     * @param notation    the song
     * @param query       the name of the element asked about
     * @param expected    the name of the element the section started at, or {@code null} when
     *                    no section is open just before the query
     */
    private record SectionStartCase(String description, String notation, String query, @Nullable String expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("sectionStartCases")
    void testSectionStartIsTheFirstElementOfTheSectionOpenBeforeTheQuery(SectionStartCase testCase) {
        var notated = notated(testCase.notation());
        var expected = testCase.expected();

        assertThat(RepeatSections.of(notated.song()).sectionStart(notated.at(testCase.query())))
            .isEqualTo(expected == null ? null : notated.at(expected));
    }

    static Stream<SectionStartCase> sectionStartCases() {
        return Stream.of(
            new SectionStartCase("a |: on the same line",
                "|: N#start N#query", "query", "start"),

            new SectionStartCase("a :|: on the same line",
                ":|: N#start N#query", "query", "start"),

            new SectionStartCase("a |: on an earlier line",
                "|: N#start / N N#query", "query", "start"),

            new SectionStartCase("a |: across an empty middle line",
                "|: N#start / / N N#query", "query", "start"),

            new SectionStartCase("a |: that is the last element of its line starts the next line's first element",
                "N |: / N#start N N#query", "query", "start"),

            new SectionStartCase("no |: answers the song's first element",
                "N#start N N#query", "query", "start"),

            new SectionStartCase("the query is the first element, which is the song start",
                "N#both N", "both", "both"),

            new SectionStartCase("a leading empty line is skipped",
                "/ N#start N#query", "query", "start"),

            new SectionStartCase("the query directly after a |: is its own answer",
                "|: N#both N", "both", "both"),

            new SectionStartCase("a query on a |: does not count that opener",
                "N#start |:#query N", "query", "start"),

            new SectionStartCase("a query on a :| asks about the section that :| ends",
                "|: N#start :|#query", "query", "start"),

            new SectionStartCase("a closer with no open section has no start, after a ||",
                "N || N :|#query", "query", null),

            new SectionStartCase("a closer with no open section has no start, after a closed section",
                "N :| N :|#query", "query", null),

            new SectionStartCase("an element after a :| closes the section has no start",
                "|: N :| N#query", "query", null),

            new SectionStartCase("an element after a || has no start",
                "|: N || N#query", "query", null),

            new SectionStartCase("an element after a final barline has no start",
                "|: N fin N#query", "query", null),

            new SectionStartCase("a nested section starts after its own opener",
                "|: N |: N#start N#query", "query", "start"),

            new SectionStartCase("a leading :| is itself the start of the section it closes",
                ":|#both N", "both", "both")
        );
    }
}
