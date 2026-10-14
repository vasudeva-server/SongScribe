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

package songscribe.layout;

import java.awt.Font;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Lyric;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * How far a melisma and a hyphen reach past the column they start on — the promise that a column
 * bearing no syllable is passed over rather than treated as the end of the chain — and which
 * columns get a missing-lyric placeholder and where it sits.
 */
class LyricLayoutBuilderTest extends UnitTest {

    /** One element standing between two syllables, with the name its row reports. */
    private record InterveningElement(String description, Supplier<StaffElement> element) {}

    /** Uniform column geometry: every fixture column is one staff space of ink, two apart. */
    private static final double COLUMN_WIDTH_SS = 1.0;

    private static final double COLUMN_PITCH_SS = 2.0;

    /** The gap a melisma keeps from the syllable that follows it. */
    private static final double SPACE_WIDTH_SS = 0.25;

    private static final double SYLLABLE_WIDTH_SS = 0.8;

    private static final double TOLERANCE_SS = 0.0001;

    private static final int LYRICS_FONT_SIZE = 12;

    private static final int SCALED_LYRICS_FONT_SIZE = 13;

    private static final int PLACEHOLDER_FONT_SIZE = 14;

    /** Narrower than the one staff space of notehead every fixture column has. */
    private static final double NARROW_PLACEHOLDER_WIDTH_SS = 0.5;

    /** Wider than a notehead, narrower than the three staff spaces a grace and its host span. */
    private static final double MID_PLACEHOLDER_WIDTH_SS = 2.0;

    /** Wider than the three staff spaces a grace and its host span. */
    private static final double WIDE_PLACEHOLDER_WIDTH_SS = 5.0;

    private static final String FIRST_SYLLABLE = "hal";

    private static final String LAST_SYLLABLE = "le";

    /** A melisma carrier draws no syllable of its own, so it carries no text. */
    private static final String CARRIER_TEXT = "";

    @ParameterizedTest(name = "{0}")
    @MethodSource("interveningElements")
    void testAMelismaRunsThroughAColumnThatBearsNoSyllable(InterveningElement testCase) {
        var start = syllableColumn(0, FIRST_SYLLABLE, Lyric.Syllabic.SINGLE, Lyric.Extend.START);
        var intervening = bareColumn(1, testCase.element().get());
        var next = syllableColumn(2, LAST_SYLLABLE, Lyric.Syllabic.SINGLE, Lyric.Extend.NONE);

        var result = build(List.of(start, intervening, next));
        var extender = onlyConnector(result, LyricConnectorLayout.Kind.EXTENDER);

        assertThat(extender.endXSs())
            .as("the melisma reaches the syllable it is sung into, not the column standing between")
            .isCloseTo(syllableBoxLeftXSs(next) - SPACE_WIDTH_SS, within(TOLERANCE_SS));
    }

    @Test
    void testAHyphenLeftOpenAtTheEndOfALineReachesTheNextSyllableSlot() {
        var opening = syllableColumn(0, FIRST_SYLLABLE, Lyric.Syllabic.BEGIN, Lyric.Extend.NONE);
        var barline = bareColumn(1, StaffElementFactory.singleBarline());
        var slot = bareColumn(2, StaffElementFactory.crotchet());

        var result = build(List.of(opening, barline, slot));
        var hyphen = onlyConnector(result, LyricConnectorLayout.Kind.DANGLING_HYPHEN);

        assertThat(hyphen.endXSs())
            .as("the hyphen is centered against the note that will carry the next syllable")
            .isCloseTo(slot.getLeftEdgeXSs(), within(TOLERANCE_SS));
    }

    /**
     * A note with a grace note paired in front of it carries the grace's syllable, never one of
     * its own, so it is not the slot the word continues on however note-like its type is.
     */
    @Test
    void testAHyphenOpenedOnAGraceNoteReachesPastTheHostThatCanBearNoSyllable() {
        var opening = column(0, pairedGraceNote(),
            new Lyric(Song.Script.LATIN, FIRST_SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
        var host = bareColumn(1, StaffElementFactory.crotchet());
        var slot = bareColumn(2, StaffElementFactory.crotchet());

        var result = build(List.of(opening, host, slot));
        var hyphen = onlyConnector(result, LyricConnectorLayout.Kind.DANGLING_HYPHEN);

        assertThat(hyphen.endXSs())
            .as("the host carries the grace's syllable, so the word continues on the note after it")
            .isCloseTo(slot.getLeftEdgeXSs(), within(TOLERANCE_SS));
    }

    /**
     * A melisma nothing closes before the line ends reaches its last carrier. A column bearing no
     * syllable is passed over on the way rather than ending the walk, or a melisma written across
     * a barline would be cut short at it.
     */
    @Test
    void testAMelismaLeftOpenAtTheEndOfALineReachesItsLastCarrierPastABareColumn() {
        var start = syllableColumn(0, FIRST_SYLLABLE, Lyric.Syllabic.SINGLE, Lyric.Extend.START);
        var barline = bareColumn(1, StaffElementFactory.singleBarline());
        var carrier = column(2, StaffElementFactory.crotchet(),
            new Lyric(Song.Script.LATIN, CARRIER_TEXT, Lyric.Extend.CONTINUE, null, false));

        var result = build(List.of(start, barline, carrier));
        var extender = onlyConnector(result, LyricConnectorLayout.Kind.DANGLING_EXTENDER);

        assertThat(extender.endXSs())
            .as("the melisma runs over the barline to the carrier beyond it")
            .isCloseTo(carrier.getRightEdgeXSs(), within(TOLERANCE_SS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placeholderCases")
    void testOnlyAnElementThatCanBearASyllableButHasNoLyricGetsAPlaceholder(PlaceholderCase testCase) {
        var result = build(testCase.columns());

        var expectedBearers = testCase.bearerIndexes().stream()
            .map(index -> testCase.columns().get(index).getElement())
            .toList();

        assertThat(result.placeholders().keySet()).containsExactlyInAnyOrderElementsOf(expectedBearers);
        assertThat(result.placeholders().values())
            .allSatisfy(box -> assertThat(box.text()).isEqualTo(LyricLayoutBuilder.PLACEHOLDER_TEXT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placementCases")
    void testAPlaceholderSitsWhereASyllableOfItsWidthWould(PlacementCase testCase) {
        var result = build(testCase.columns(), testCase.placeholderWidthSs());

        var placeholder = result.placeholders().get(testCase.columns().get(testCase.bearerIndex()).getElement());

        assertThat(placeholder).isNotNull();
        assertThat(placeholder.widthSs()).isEqualTo(testCase.placeholderWidthSs());
        assertThat(placeholder.xSs()).isCloseTo(testCase.expectedLeftXSs(), within(TOLERANCE_SS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("openingSyllables")
    void testAPlaceholderTakesNoSpaceAndTouchesNoConnector(OpeningSyllable testCase) {
        var opening = StaffElementFactory.crotchet();
        var closing = StaffElementFactory.crotchet();
        var openingLyric = new Lyric(
            Song.Script.LATIN, FIRST_SYLLABLE, testCase.extend(), testCase.syllabic(), false);
        var closingLyric = new Lyric(
            Song.Script.LATIN, LAST_SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        var withPlaceholder = build(List.of(
            column(0, opening, openingLyric),
            bareColumn(1, StaffElementFactory.crotchet()),
            column(2, closing, closingLyric)));
        var withoutPlaceholder = build(List.of(
            column(0, opening, openingLyric),
            bareColumn(1, StaffElementFactory.crotchetRest()),
            column(2, closing, closingLyric)));

        assertThat(withPlaceholder.placeholders()).hasSize(1);
        assertThat(withoutPlaceholder.placeholders()).isEmpty();
        assertThat(withPlaceholder.connectors()).isNotEmpty();
        assertThat(withPlaceholder.boxes()).isEqualTo(withoutPlaceholder.boxes());
        assertThat(withPlaceholder.connectors()).isEqualTo(withoutPlaceholder.connectors());
    }

    /**
     * One line, and which of its columns get a placeholder.
     *
     * @param description   the case, which doubles as the parameterized display name
     * @param columns       the line's columns
     * @param bearerIndexes the indexes of the columns that get a placeholder
     */
    private record PlaceholderCase(String description, List<ElementColumn> columns, List<Integer> bearerIndexes) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * One line, and where the placeholder of one of its columns goes.
     *
     * @param description         the case, which doubles as the parameterized display name
     * @param columns             the line's columns
     * @param bearerIndex         the index of the column whose placeholder is measured
     * @param placeholderWidthSs  the width of the placeholder
     * @param expectedLeftXSs     the left edge of the placeholder's box
     */
    private record PlacementCase(
        String description,
        List<ElementColumn> columns,
        int bearerIndex,
        double placeholderWidthSs,
        double expectedLeftXSs
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * The syllable that opens a chain, which a placeholder after it must leave alone.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param syllabic    whether the syllable opens a hyphen
     * @param extend      whether the syllable opens a melisma
     */
    private record OpeningSyllable(String description, Lyric.Syllabic syllabic, Lyric.Extend extend) {

        @Override
        public String toString() {
            return description;
        }
    }

    /** A grace note paired with the note after it, which hosts it. */
    private static NoteElement pairedGraceNote() {
        var grace = StaffElementFactory.graceQuaver();
        grace.setSlide(Slide.GLISSANDO);

        return grace;
    }

    private static Lyric lyricOf(String text) {
        return new Lyric(Song.Script.LATIN, text, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
    }

    static Stream<PlaceholderCase> placeholderCases() {
        var graceColumn = bareColumn(0, pairedGraceNote());
        var hostColumn = bareColumn(1, StaffElementFactory.crotchet());

        return Stream.of(
            new PlaceholderCase("a note with no lyric", List.of(bareColumn(0, StaffElementFactory.crotchet())),
                List.of(0)),
            new PlaceholderCase("a note with a lyric",
                List.of(column(0, StaffElementFactory.crotchet(), lyricOf(FIRST_SYLLABLE))), List.of()),
            new PlaceholderCase("a rest", List.of(bareColumn(0, StaffElementFactory.crotchetRest())), List.of()),
            new PlaceholderCase("a barline", List.of(bareColumn(0, StaffElementFactory.singleBarline())), List.of()),
            new PlaceholderCase("a breath mark", List.of(bareColumn(0, StaffElementFactory.breathMark())), List.of()),
            new PlaceholderCase("a key change",
                List.of(bareColumn(0, StaffElementFactory.keyChange(Key.NO_ACCIDENTALS))), List.of()),
            new PlaceholderCase("a grace note and its host, the placeholder going to the grace",
                List.of(graceColumn, hostColumn), List.of(0))
        );
    }

    static Stream<PlacementCase> placementCases() {
        return Stream.of(
            new PlacementCase("a note's placeholder is centered on its notehead",
                List.of(bareColumn(0, StaffElementFactory.crotchet())), 0, NARROW_PLACEHOLDER_WIDTH_SS,
                (COLUMN_WIDTH_SS - NARROW_PLACEHOLDER_WIDTH_SS) / 2.0),
            new PlacementCase("a grace note's placeholder narrower than its notehead is centered on it",
                pairedGraceAndHost(), 0, NARROW_PLACEHOLDER_WIDTH_SS,
                (COLUMN_WIDTH_SS - NARROW_PLACEHOLDER_WIDTH_SS) / 2.0),
            new PlacementCase("a grace note's placeholder wider than its notehead starts centered on it",
                pairedGraceAndHost(), 0, MID_PLACEHOLDER_WIDTH_SS,
                (COLUMN_WIDTH_SS - MID_PLACEHOLDER_WIDTH_SS) / 2.0),
            new PlacementCase("a grace note's placeholder wider than the pair is centered on the pair",
                pairedGraceAndHost(), 0, WIDE_PLACEHOLDER_WIDTH_SS,
                -(WIDE_PLACEHOLDER_WIDTH_SS - (COLUMN_PITCH_SS + COLUMN_WIDTH_SS)) / 2.0)
        );
    }

    static Stream<OpeningSyllable> openingSyllables() {
        return Stream.of(
            new OpeningSyllable("a melisma", Lyric.Syllabic.SINGLE, Lyric.Extend.START),
            new OpeningSyllable("a hyphen", Lyric.Syllabic.BEGIN, Lyric.Extend.NONE)
        );
    }

    /** A paired grace note and its host, the pair spanning {@code COLUMN_PITCH_SS + COLUMN_WIDTH_SS}. */
    private static List<ElementColumn> pairedGraceAndHost() {
        return List.of(
            bareColumn(0, pairedGraceNote()),
            bareColumn(1, StaffElementFactory.crotchet()));
    }

    static Stream<InterveningElement> interveningElements() {
        return Stream.of(
            new InterveningElement("a rest", StaffElementFactory::crotchetRest),
            new InterveningElement("a barline", StaffElementFactory::singleBarline),
            new InterveningElement("a breath mark", StaffElementFactory::breathMark),
            new InterveningElement("a key change",
                () -> StaffElementFactory.keyChange(Key.NO_ACCIDENTALS))
        );
    }

    private static LyricLayoutBuilder.Result build(List<ElementColumn> columns) {
        return build(columns, NARROW_PLACEHOLDER_WIDTH_SS);
    }

    private static LyricLayoutBuilder.Result build(List<ElementColumn> columns, double placeholderWidthSs) {
        return LyricLayoutBuilder.build(
            columns, Song.Script.LATIN, metrics(placeholderWidthSs), false);
    }

    /**
     * The one connector of {@code kind} the fixture produces. Asserting there is exactly one is
     * part of the promise: a chain that reached its target emits a single span.
     */
    private static LyricConnectorLayout onlyConnector(
        LyricLayoutBuilder.Result result,
        LyricConnectorLayout.Kind kind
    ) {
        var matching = result.connectors().stream().filter(connector -> connector.kind() == kind).toList();

        assertThat(matching).as("connectors of kind %s", kind).hasSize(1);

        return matching.getFirst();
    }

    private static ElementColumn syllableColumn(
        int index,
        String text,
        Lyric.Syllabic syllabic,
        Lyric.Extend extend
    ) {
        return column(index, StaffElementFactory.crotchet(),
            new Lyric(Song.Script.LATIN, text, extend, syllabic, false));
    }

    private static ElementColumn bareColumn(int index, StaffElement element) {
        return column(index, element, null);
    }

    private static ElementColumn column(int index, StaffElement element, @Nullable Lyric lyric) {
        var extents = new ElementColumn.Extents(0.0, COLUMN_WIDTH_SS, COLUMN_WIDTH_SS, 0.0, 0.0);
        // A carrier — a lyric with no text — is a melisma marker, not a drawn syllable.
        var measured = lyric == null
            ? null
            : new ElementColumn.MeasuredLyric(lyric, lyric.text().isEmpty() ? 0.0 : SYLLABLE_WIDTH_SS);
        var column = new ElementColumn(element, extents, measured, null);

        column.setXSs(index * COLUMN_PITCH_SS);

        return column;
    }

    /** Where {@code column}'s syllable box starts: its text centered on the notehead. */
    private static double syllableBoxLeftXSs(ElementColumn column) {
        return column.getNoteheadCenterXSs() - SYLLABLE_WIDTH_SS / 2.0;
    }

    /** Metrics whose three fonts differ in face and size, so no one is mistaken for another. */
    private static LyricRenderMetrics metrics(double placeholderWidthSs) {
        var lyricsFont = new Font(Font.SANS_SERIF, Font.PLAIN, LYRICS_FONT_SIZE);
        var scaledLyricsFont = new Font(Font.SERIF, Font.ITALIC, SCALED_LYRICS_FONT_SIZE);
        var placeholderFont = new Font(Font.MONOSPACED, Font.BOLD, PLACEHOLDER_FONT_SIZE);

        return new LyricRenderMetrics(
            lyricsFont,
            scaledLyricsFont,
            placeholderFont,
            SPACE_WIDTH_SS,
            SPACE_WIDTH_SS,
            placeholderWidthSs,
            0.0);
    }
}
