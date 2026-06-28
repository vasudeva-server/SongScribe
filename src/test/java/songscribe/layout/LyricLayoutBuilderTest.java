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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mockStatic;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.jspecify.annotations.Nullable;

import songscribe.UnitTest;
import songscribe.layout.ElementColumn;
import songscribe.layout.LyricBoxLayout;
import songscribe.layout.LyricConnectorLayout;
import songscribe.layout.LyricLayoutBuilder;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.MessageCenter;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.smufl.Engraving;

class LyricLayoutBuilderTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final double COLUMN_SPACING_SS = 4.0;
    private static final double LINE_WIDTH_SS = 100.0;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);
    // Non-zero space width so melisma trailing-gap assertions verify a real gap rather than 0.
    private static final double EXTENDER_SPACE_WIDTH_SS = 0.5;
    private static final LyricRenderMetrics LYRIC_METRICS_WITH_SPACE =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, EXTENDER_SPACE_WIDTH_SS);
    // Distance from the STOP carrier to the following syllable that leaves the raw extender
    // overshoot well within the gap, so the clamp must leave the extender untouched.
    private static final double FAR_FOLLOWING_SYLLABLE_GAP_SS = 20.0;
    // Arbitrary dot width used in tests that need rightExtentSs > rightExtentExcludingDotsSs.
    private static final double FAKE_DOT_EXTENT_SS = 2.0;

    private Song song;
    private Line line;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
        song = new Song();
        line = song.getLine(0);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    private void addToLine(StaffElement... elements) {
        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });
    }

    private static StaffElement note() {
        return ElementType.CROTCHET.newInstance();
    }

    private static StaffElement rest() {
        return ElementType.CROTCHET_REST.newInstance();
    }

    /** Places {@code element} in a column at the given X with notehead-width right extent. */
    private static ElementColumn columnAt(StaffElement element, double xSs) {
        return columnAt(element, xSs, 0.0);
    }

    /**
     * Places {@code element} in a column at the given X with notehead-width right extent and
     * a pre-measured {@code syllableWidthSs} (used by the verse-1 code path in the builder).
     */
    private static ElementColumn columnAt(StaffElement element, double xSs, double syllableWidthSs) {
        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            0.0,
            Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, syllableWidthSs, false);
        column.setXSs(xSs);
        return column;
    }

    private static void setLyric(
        StaffElement element, Lyric.@Nullable Syllabic syllabic, boolean compound, String text, boolean extend) {
        element.lyrics.add(new Lyric(1, text,
            extend ? Lyric.Extend.START : Lyric.Extend.NONE, syllabic, compound));
    }

    private static void setLyric(
        StaffElement element, Lyric.@Nullable Syllabic syllabic, boolean compound, String text, Lyric.Extend extend) {
        element.lyrics.add(new Lyric(1, text, extend, syllabic, compound));
    }

    private static List<LyricBoxLayout> boxesOf(
        Map<StaffElement, ? extends List<LyricBoxLayout>> boxes,
        StaffElement element) {
        var list = boxes.get(element);

        if (list == null) {
            throw new AssertionError("expected boxes for element but found none");
        }

        return list;
    }

    private static List<LyricConnectorLayout> connectorsOfKind(
        List<LyricConnectorLayout> connectors,
        LyricConnectorLayout.Kind kind) {
        var result = new ArrayList<LyricConnectorLayout>();

        for (var c : connectors) {
            if (c.kind() == kind) {
                result.add(c);
            }
        }

        return result;
    }

    @Test
    void testEmptyLineProducesEmptyResult() {
        var result = LyricLayoutBuilder.build(List.of(), LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).isEmpty();
        assertThat(result.connectors()).isEmpty();
        assertThat(result.verseCount()).isEqualTo(1);
        assertThat(result.hasTrailingContinuation()).isFalse();
    }

    @Test
    void testLineWithoutLyricsProducesEmptyResult() {
        var n1 = note();
        var n2 = note();
        var n3 = note();
        addToLine(n1, n2, n3);

        var columns = List.of(columnAt(n1, 5), columnAt(n2, 10), columnAt(n3, 15));
        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).isEmpty();
        assertThat(result.connectors()).isEmpty();
        assertThat(result.verseCount()).isEqualTo(1);
    }

    // do-re-mi across three notes → three boxes, two HYPHEN spans with correct coordinates
    @Test
    void testDoReMiProducesThreeBoxesAndTwoHyphens() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, false, "do", false);
        var n2 = note();
        setLyric(n2, Lyric.Syllabic.MIDDLE, false, "re", false);
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.END, false, "mi", false);
        addToLine(n1, n2, n3);

        // syllableWidthSs=0 in these test columns, so boxXSs = columnXSs + NOTE_HEAD_WIDTH_SS/2
        var col0XSs = 5.0;
        var col1XSs = 5.0 + COLUMN_SPACING_SS;
        var col2XSs = 5.0 + 2 * COLUMN_SPACING_SS;
        var columns = List.of(
            columnAt(n1, col0XSs),
            columnAt(n2, col1XSs),
            columnAt(n3, col2XSs));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).hasSize(3);
        assertThat(boxesOf(result.boxes(), n1)).hasSize(1);
        assertThat(boxesOf(result.boxes(), n1).getFirst().text()).isEqualTo("do");
        assertThat(boxesOf(result.boxes(), n2).getFirst().text()).isEqualTo("re");
        assertThat(boxesOf(result.boxes(), n3).getFirst().text()).isEqualTo("mi");

        var hyphens = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN);
        assertThat(hyphens).hasSize(2);
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER)).isEmpty();
        assertThat(result.hasTrailingContinuation()).isFalse();
        assertThat(result.verseCount()).isEqualTo(1);

        // With syllableWidthSs=0, boxXSs == noteCenter (columnXSs + rightExtent/2) for each column.
        // A hyphen opens at syllableEnd (= boxXSs + 0) and closes at the next box's xSs.
        var halfHeadWidthSs = Engraving.NOTE_HEAD_WIDTH_SS / 2.0;
        var box0XSs = col0XSs + halfHeadWidthSs;
        var box1XSs = col1XSs + halfHeadWidthSs;
        var box2XSs = col2XSs + halfHeadWidthSs;

        // First hyphen: from "do" syllable end → "re" box left, opened by column index 0
        assertThat(hyphens.get(0).startXSs())
            .as("first hyphen startXSs: end of 'do' box")
            .isCloseTo(box0XSs, within(TOLERANCE));
        assertThat(hyphens.get(0).endXSs())
            .as("first hyphen endXSs: start of 're' box")
            .isCloseTo(box1XSs, within(TOLERANCE));
        assertThat(hyphens.get(0).sourceElementIndex())
            .as("first hyphen sourceElementIndex: column index of 'do' note")
            .isEqualTo(0);

        // Second hyphen: from "re" syllable end → "mi" box left, opened by column index 1
        assertThat(hyphens.get(1).startXSs())
            .as("second hyphen startXSs: end of 're' box")
            .isCloseTo(box1XSs, within(TOLERANCE));
        assertThat(hyphens.get(1).endXSs())
            .as("second hyphen endXSs: start of 'mi' box")
            .isCloseTo(box2XSs, within(TOLERANCE));
        assertThat(hyphens.get(1).sourceElementIndex())
            .as("second hyphen sourceElementIndex: column index of 're' note")
            .isEqualTo(1);
    }

    // computeLyricBoxLeftXSs: normal note box is centered on the note column centre
    // (columnXSs + rightExtentExcludingDotsSs/2), minus half the syllable width.
    // Uses a verse-2 lyric so the builder measures width via lyricBoxWidthSs(text)
    // rather than the cached syllableWidthSs; the oracle calls lyricBoxWidthSs independently.
    @Test
    void testNormalNoteLyricBoxIsCenteredOnNoteCenter() {
        var n1 = note();
        // Use verse 2 so the builder calls lyricRenderMetrics.lyricBoxWidthSs(text) rather
        // than the pre-measured syllableWidthSs (which would be 0 in the test column).
        var syllableText = "heart";
        n1.lyrics.add(new Lyric(2, syllableText, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        addToLine(n1);

        var noteXSs = 10.0;
        var col = columnAt(n1, noteXSs);
        var result = LyricLayoutBuilder.build(List.of(col), LYRIC_METRICS, false, LINE_WIDTH_SS);

        var boxes = boxesOf(result.boxes(), n1);
        assertThat(boxes).as("expected exactly one box for n1 (verse 2)").hasSize(1);

        var box = boxes.getFirst();

        // Independent oracle: box left = note centre (excluding dots) − half syllable width
        var syllableWidthSs = LYRIC_METRICS.lyricBoxWidthSs(syllableText);
        var noteCenterXSs = noteXSs + col.getRightExtentExcludingDotsSs() / 2.0;
        var expectedBoxLeftXSs = noteCenterXSs - syllableWidthSs / 2.0;

        assertThat(box.xSs())
            .as("lyric box left edge = note centre (excluding dots) − halfWidth")
            .isCloseTo(expectedBoxLeftXSs, within(TOLERANCE));
    }

    // Dotted note: the lyric box must be centred on the notehead extent (rightExtentExcludingDotsSs),
    // not the full extent that includes the dot. Dots must not shift the lyric to the right (#451).
    @Test
    void testDottedNoteLyricBoxIgnoresDots() {
        var n1 = note();
        var syllableText = "heart";
        // Use verse 2 so the builder measures width on the fly via lyricBoxWidthSs.
        n1.lyrics.add(new Lyric(2, syllableText, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        addToLine(n1);

        var noteXSs = 10.0;
        var rightExtentExcludingDotsSs = Engraving.NOTE_HEAD_WIDTH_SS;
        var rightExtentWithDotsSs = rightExtentExcludingDotsSs + FAKE_DOT_EXTENT_SS;

        // Full public constructor: rightExtentSs includes the dot, rightExtentExcludingDotsSs does not.
        var col = new ElementColumn(
            n1,
            Collections.emptyList(),
            0.0,
            rightExtentWithDotsSs,
            rightExtentExcludingDotsSs,
            0.0, 0.0, null, 0.0, false);
        col.setXSs(noteXSs);

        var result = LyricLayoutBuilder.build(List.of(col), LYRIC_METRICS, false, LINE_WIDTH_SS);

        var boxes = boxesOf(result.boxes(), n1);
        assertThat(boxes).as("expected exactly one box for dotted note").hasSize(1);

        var syllableWidthSs = LYRIC_METRICS.lyricBoxWidthSs(syllableText);
        var noteCenterXSs = noteXSs + rightExtentExcludingDotsSs / 2.0;
        var expectedBoxLeftXSs = noteCenterXSs - syllableWidthSs / 2.0;

        assertThat(boxes.getFirst().xSs())
            .as("dotted note: lyric box must be centred on notehead centre, not shifted right by the dot")
            .isCloseTo(expectedBoxLeftXSs, within(TOLERANCE));
    }

    // heart(extend=true) [..continuation..] garden → two boxes, one EXTENDER
    @Test
    void testExtenderSpansContinuationNotes() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "heart", true);
        var n2 = note();
        var n3 = note();
        var n4 = note();
        setLyric(n4, Lyric.Syllabic.SINGLE, false, "garden", false);
        addToLine(n1, n2, n3, n4);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(n3, 5 + 2 * COLUMN_SPACING_SS),
            columnAt(n4, 5 + 3 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS_WITH_SPACE, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).hasSize(2);
        assertThat(boxesOf(result.boxes(), n1).getFirst().text()).isEqualTo("heart");
        assertThat(boxesOf(result.boxes(), n4).getFirst().text()).isEqualTo("garden");

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN)).isEmpty();
        assertThat(result.hasTrailingContinuation()).isFalse();

        // The extender stops one word-space short of "garden", preserving the same gap kept
        // between adjacent words rather than running flush into the next syllable (#430).
        var gardenBoxXSs = boxesOf(result.boxes(), n4).getFirst().xSs();
        assertThat(extenders.getFirst().endXSs())
            .as("extender ends one word-space before 'garden'")
            .isCloseTo(gardenBoxXSs - EXTENDER_SPACE_WIDTH_SS, within(TOLERANCE));
    }

    // heart(extend=true) [continuation] (rest) [continuation] garden → two EXTENDER spans (rest breaks)
    @Test
    void testRestWithoutLyricBreaksExtender() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "heart", true);
        var n2 = note();
        var r = rest();
        var n3 = note();
        var n4 = note();
        setLyric(n4, Lyric.Syllabic.SINGLE, false, "garden", false);
        addToLine(n1, n2, r, n3, n4);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(r, 5 + 2 * COLUMN_SPACING_SS),
            columnAt(n3, 5 + 3 * COLUMN_SPACING_SS),
            columnAt(n4, 5 + 4 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
        assertThat(extenders.getFirst().endXSs())
            .as("extender ends at rest's left edge")
            .isCloseTo(columns.get(2).getLeftEdgeXSs(), within(TOLERANCE));
    }

    // heart(extend=true) [continuation] (rest with Lyric(extend=true)) [continuation] garden → one EXTENDER
    @Test
    void testRestWithExtendingLyricContinuesExtender() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "heart", true);
        var n2 = note();
        var r = rest();
        setLyric(r, Lyric.Syllabic.SINGLE, false, "", true);
        var n3 = note();
        var n4 = note();
        setLyric(n4, Lyric.Syllabic.SINGLE, false, "garden", false);
        addToLine(n1, n2, r, n3, n4);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(r, 5 + 2 * COLUMN_SPACING_SS),
            columnAt(n3, 5 + 3 * COLUMN_SPACING_SS),
            columnAt(n4, 5 + 4 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        // No box emitted for the rest (it is only a continuation marker).
        assertThat(result.boxes()).containsOnlyKeys(n1, n4);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
    }

    // heart(extend=true) [continuation] (rest with Lyric(extend=CONTINUE)) [continuation] garden →
    // CONTINUE is a distinct enum value from START; both must keep the extender open through a rest.
    @Test
    void testRestWithContinueLyricContinuesExtender() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "heart", Lyric.Extend.START);
        var n2 = note();
        var r = rest();
        setLyric(r, null, false, "", Lyric.Extend.CONTINUE);
        var n3 = note();
        var n4 = note();
        setLyric(n4, Lyric.Syllabic.SINGLE, false, "garden", Lyric.Extend.NONE);
        addToLine(n1, n2, r, n3, n4);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(r, 5 + 2 * COLUMN_SPACING_SS),
            columnAt(n3, 5 + 3 * COLUMN_SPACING_SS),
            columnAt(n4, 5 + 4 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        // No box emitted for the rest (it is only a CONTINUE marker).
        assertThat(result.boxes()).containsOnlyKeys(n1, n4);

        // The CONTINUE on the rest must not break the extender; only one EXTENDER spanning n1 → n4.
        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders)
            .as("REST + CONTINUE must keep extender open — exactly one EXTENDER emitted")
            .hasSize(1);
        assertThat(result.hasTrailingContinuation()).isFalse();
    }

    // Cross-line continuation: line A ends with extend=true; line A's build reports hasTrailingContinuation.
    // Line B is built with hasLeadingContinuation = true and produces a leading EXTENDER from x = 0.
    @Test
    void testTrailingContinuationAndLeadingStub() {
        // Line A: single note with extend=true and no closing syllable
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "ah", true);
        var columnsA = List.of(columnAt(n1, 5));

        // Line B: no lyric on its first note, second note has 'garden'
        var n2 = note();
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.SINGLE, false, "garden", false);
        var columnsB = List.of(columnAt(n2, 5), columnAt(n3, 5 + COLUMN_SPACING_SS));

        // All elements share one Line for getSyllabicAt — syllabic values are still correct since all
        // syllabics are SINGLE, which yields SINGLE regardless of neighbors.
        addToLine(n1, n2, n3);

        var resultA = LyricLayoutBuilder.build(columnsA, LYRIC_METRICS, false, LINE_WIDTH_SS);
        assertThat(resultA.hasTrailingContinuation()).isTrue();
        var trailingExtenders = connectorsOfKind(resultA.connectors(), LyricConnectorLayout.Kind.DANGLING_EXTENDER);
        assertThat(trailingExtenders).hasSize(1);
        assertThat(trailingExtenders.getFirst().endXSs())
            .as("trailing stub ends at last note's right edge")
            .isCloseTo(columnsA.getFirst().getRightEdgeXSs(), within(TOLERANCE));

        var resultB = LyricLayoutBuilder.build(columnsB, LYRIC_METRICS_WITH_SPACE, true, LINE_WIDTH_SS);

        var leadingExtenders = connectorsOfKind(resultB.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(leadingExtenders).hasSize(1);
        assertThat(leadingExtenders.getFirst().startXSs())
            .as("leading stub starts at line left edge")
            .isCloseTo(0.0, within(TOLERANCE));
        assertThat(leadingExtenders.getFirst().endXSs())
            .as("leading stub ends one word-space before garden's left edge")
            .isCloseTo(
                boxesOf(resultB.boxes(), n3).getFirst().xSs() - EXTENDER_SPACE_WIDTH_SS,
                within(TOLERANCE));
    }

    // Dangling extender: note with extend=START followed only by bare notes with no lyrics →
    // DANGLING_EXTENDER ending at the START note's right edge (no CONTINUE/STOP markers follow,
    // so the extender does not advance past the START column).
    @Test
    void testDanglingExtenderEndsAtStartNoteWhenNoContinueFollows() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "ah", true);
        var n2 = note();
        var n3 = note();
        addToLine(n1, n2, n3);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(n3, 5 + 2 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.hasTrailingContinuation()).isTrue();

        var danglingExtenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.DANGLING_EXTENDER);
        assertThat(danglingExtenders).hasSize(1);
        assertThat(danglingExtenders.getFirst().endXSs())
            .as("dangling extender ends at START note's right edge")
            .isCloseTo(columns.getFirst().getRightEdgeXSs(), within(TOLERANCE));
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER)).isEmpty();
    }

    // Dangling extender with explicit CONTINUE markers on following notes →
    // extends through them and ends at the last CONTINUE note's right edge.
    @Test
    void testDanglingExtenderExtendsThroughContinueMarkers() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, null, false, "", Lyric.Extend.CONTINUE);
        var n3 = note();
        setLyric(n3, null, false, "", Lyric.Extend.CONTINUE);
        var n4 = note();
        addToLine(n1, n2, n3, n4);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(n3, 5 + 2 * COLUMN_SPACING_SS),
            columnAt(n4, 5 + 3 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.hasTrailingContinuation()).isTrue();

        var danglingExtenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.DANGLING_EXTENDER);
        assertThat(danglingExtenders).hasSize(1);
        assertThat(danglingExtenders.getFirst().endXSs())
            .as("dangling extender ends at last CONTINUE note's right edge (n3), not n4 (no extend marker)")
            .isCloseTo(columns.get(2).getRightEdgeXSs(), within(TOLERANCE));
    }

    // verse-1 (getSyllableWidthSs) vs verse-≥2 (lyricBoxWidthSs) must agree for the same text.
    // The builder takes two distinct code paths to compute box width depending on verse index:
    //   verse 1  → column.getSyllableWidthSs()  (pre-measured by ElementColumnBuilder)
    //   verse ≥2 → lyricRenderMetrics.lyricBoxWidthSs(text)  (measured on the fly)
    // Populate the verse-1 column with the real measured width so both paths use the same
    // measurement source; then assert the resulting box widths are equal within TOLERANCE.
    @Test
    void testVerse1AndVerse2ProduceEqualWidthForSameText() {
        var syllableText = "heart";
        var measuredWidthSs = LYRIC_METRICS.lyricBoxWidthSs(syllableText);

        var n1 = note();
        // Verse 1 lyric: builder reads syllableWidthSs from the column.
        n1.lyrics.add(new Lyric(1, syllableText, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        // Verse 2 lyric: builder calls lyricRenderMetrics.lyricBoxWidthSs(text) on the fly.
        n1.lyrics.add(new Lyric(2, syllableText, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        addToLine(n1);

        // Populate syllableWidthSs with the measured width so the verse-1 path uses real data.
        var col = columnAt(n1, 5.0, measuredWidthSs);
        var result = LyricLayoutBuilder.build(List.of(col), LYRIC_METRICS, false, LINE_WIDTH_SS);

        var boxes = boxesOf(result.boxes(), n1);
        assertThat(boxes).as("expected two boxes (one per verse) for n1").hasSize(2);

        var verse1Box = boxes.stream()
            .filter(b -> b.verseIndex() == 1)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no verse-1 box found"));
        var verse2Box = boxes.stream()
            .filter(b -> b.verseIndex() == 2)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no verse-2 box found"));

        assertThat(verse1Box.widthSs())
            .as("verse-1 box width (from cached getSyllableWidthSs) must equal verse-2 box width (from lyricBoxWidthSs)")
            .isCloseTo(verse2Box.widthSs(), within(TOLERANCE));
    }

    // Compound-word boundary: heart(BEGIN+compound) garden(END) → HYPHEN span (visually identical to SYLLABLE)
    @Test
    void testCompoundWordBoundaryProducesHyphen() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, true, "heart", false);
        var n2 = note();
        setLyric(n2, Lyric.Syllabic.END, false, "garden", false);
        addToLine(n1, n2);

        var columns = List.of(columnAt(n1, 5), columnAt(n2, 5 + COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var hyphens = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN);
        assertThat(hyphens).hasSize(1);
    }

    // Melisma terminated by an explicit STOP carrier on the last note —
    // the extender ends at that note's right edge (not at end of line).
    @Test
    void testStopCarrierEndsExtenderAtNoteRightEdge() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "den", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, null, false, "", Lyric.Extend.STOP);
        addToLine(n1, n2);

        var columns = List.of(columnAt(n1, 5), columnAt(n2, 5 + COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).containsOnlyKeys(n1);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
        assertThat(extenders.getFirst().endXSs())
            .as("extender ends 0.5 ss beyond stop carrier note's right edge")
            .isCloseTo(
                columns.get(1).getRightEdgeXSs() + LyricLayoutBuilder.STOP_MELISMA_OVERSHOOT_SS,
                within(TOLERANCE));
        assertThat(result.hasTrailingContinuation())
            .as("stop terminates melisma — no trailing continuation")
            .isFalse();
    }

    // STOP-terminated melisma followed by a nearby syllable: the extender's overshoot past the
    // STOP carrier is pulled back so it keeps the lyric space-width gap from the next syllable (#430).
    @Test
    void testStopCarrierExtenderClampedToFollowingSyllableGap() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "two", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, null, false, "", Lyric.Extend.STOP);
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.SINGLE, false, "three", Lyric.Extend.NONE);
        addToLine(n1, n2, n3);

        // n3 sits close behind the STOP carrier so the raw overshoot would crowd "three".
        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 9),
            columnAt(n3, 10));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS_WITH_SPACE, false, LINE_WIDTH_SS);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);

        var threeBoxXSs = boxesOf(result.boxes(), n3).getFirst().xSs();

        assertThat(extenders.getFirst().endXSs())
            .as("STOP-melisma extender is clamped to one space-width before 'three'")
            .isCloseTo(threeBoxXSs - EXTENDER_SPACE_WIDTH_SS, within(TOLERANCE));
    }

    // STOP-melisma extender whose overshoot already clears the following syllable by more than the
    // space-width gap: the clamp only pulls extenders back, so it must leave this one at its raw
    // overshoot (guards the "endXSs > maxEndXSs" branch in clampExtendersToFollowingSyllable, #430).
    @Test
    void testStopCarrierExtenderNotClampedWhenFollowingSyllableIsFar() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "two", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, null, false, "", Lyric.Extend.STOP);
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.SINGLE, false, "three", Lyric.Extend.NONE);
        addToLine(n1, n2, n3);

        // n3 sits far past the STOP carrier, so the raw overshoot already keeps more than a
        // space-width gap and the clamp must not fire.
        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 9),
            columnAt(n3, 9 + FAR_FOLLOWING_SYLLABLE_GAP_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS_WITH_SPACE, false, LINE_WIDTH_SS);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);

        var unclampedStopEndXSs = columns.get(1).getRightEdgeXSs() + LyricLayoutBuilder.STOP_MELISMA_OVERSHOOT_SS;

        assertThat(extenders.getFirst().endXSs())
            .as("extender already clears 'three' by more than a space width, so it keeps its raw overshoot")
            .isCloseTo(unclampedStopEndXSs, within(TOLERANCE));
    }

    // The clamp pass only pulls back EXTENDER connectors. A HYPHEN run already ends exactly at the
    // following syllable's left edge and must keep that endpoint — it must not be shortened by the
    // space-width gap (guards the kind check in clampExtendersToFollowingSyllable, #430).
    @Test
    void testHyphenRunNotClampedToFollowingSyllableGap() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, false, "con", false);
        var n2 = note();
        setLyric(n2, Lyric.Syllabic.END, false, "tinue", false);
        addToLine(n1, n2);

        var columns = List.of(columnAt(n1, 5), columnAt(n2, 5 + COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS_WITH_SPACE, false, LINE_WIDTH_SS);

        var hyphens = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN);
        assertThat(hyphens).hasSize(1);

        var tinueBoxXSs = boxesOf(result.boxes(), n2).getFirst().xSs();

        assertThat(hyphens.getFirst().endXSs())
            .as("hyphen ends at the following syllable's left edge, unaffected by the extender clamp")
            .isCloseTo(tinueBoxXSs, within(TOLERANCE));
    }

    // CONTINUE carrier on a mid-line note — extender passes through silently, same
    // shape as a note with no lyric.
    @Test
    void testContinueCarrierPassesThrough() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, null, false, "", Lyric.Extend.CONTINUE);
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.SINGLE, false, "men", Lyric.Extend.NONE);
        addToLine(n1, n2, n3);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(n3, 5 + 2 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).containsOnlyKeys(n1, n3);
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER)).hasSize(1);
    }

    // Non-final syllable with melisma — hyphen run is the sole connector; the extender
    // that would otherwise span the CONTINUE carrier is suppressed.
    @Test
    void testNonFinalSyllableWithMelismaEmitsHyphenOnly() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, false, "con", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, null, false, "", Lyric.Extend.CONTINUE);
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.END, false, "tinue", Lyric.Extend.NONE);
        addToLine(n1, n2, n3);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(n3, 5 + 2 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN)).hasSize(1);
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER)).isEmpty();
    }

    // Combined case from the terminology example: heart---=gar---den across 8 notes.
    // Both non-final syllables also arm extend=START, but step 1 suppresses those
    // extenders in favour of the hyphen runs.
    @Test
    void testHeartCompoundGarDenComposite() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, true, "heart", true);
        var n2 = note();
        var n3 = note();
        var n4 = note();
        var n5 = note();
        setLyric(n5, Lyric.Syllabic.MIDDLE, false, "gar", true);
        var n6 = note();
        var n7 = note();
        var n8 = note();
        setLyric(n8, Lyric.Syllabic.END, false, "den", false);

        var elements = List.of(n1, n2, n3, n4, n5, n6, n7, n8);
        addToLine(elements.toArray(new StaffElement[0]));

        var columns = new ArrayList<ElementColumn>();

        for (var i = 0; i < elements.size(); i++) {
            columns.add(columnAt(elements.get(i), 5 + i * COLUMN_SPACING_SS));
        }

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).containsOnlyKeys(n1, n5, n8);
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER)).isEmpty();
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN)).hasSize(2);
    }

    // firstGraphemeClusterEndIndex: correctly identifies multi-codepoint grapheme clusters.
    // Tested via the grace-note code path, which centres the first grapheme cluster (not
    // the first Java char) of the lyric text on the grace notehead.
    // (a) combining mark: "é" (base + combining acute = "é") is one cluster (2 Java chars).
    // (b) surrogate pair: "😀" (U+1F600) is one cluster (2 Java chars, code point > 0xFFFF).
    // A naive charAt(0) implementation would produce firstGlyph="e" or firstGlyph="\uD83D",
    // resulting in a different (wrong) box position.
    @Test
    void testFirstGraphemeClusterEndIndexHandlesMultiCodepointClusters() {
        // Use a grace note element so that computeLyricBoxLeftXSs follows the grace path
        // and calls firstGraphemeClusterEndIndex on the lyric text.
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando();
        var host = note();
        addToLine(grace, host);

        var graceXSs = 5.0;
        // Use NOTE_HEAD_WIDTH_SS as the right extent (approximation sufficient for this test).
        var graceCol = columnAt(grace, graceXSs);
        var hostCol = columnAt(host, graceXSs + COLUMN_SPACING_SS);
        var noteheadCenterXSs = graceXSs + grace.getType().getElementCenterXSs();

        // (a) Combining mark: "é" + "la" — first grapheme cluster = "é" (2 Java chars)
        var combiningText = "éla";
        grace.lyrics.clear();
        grace.lyrics.add(new Lyric(1, combiningText, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        var resultCombining = LyricLayoutBuilder.build(List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);
        var combiningBox = boxesOf(resultCombining.boxes(), grace).getFirst();
        var firstClusterCombining = "é";
        var expectedCombiningXSs = noteheadCenterXSs - LYRIC_METRICS.lyricBoxWidthSs(firstClusterCombining) / 2.0;
        assertThat(combiningBox.xSs())
            .as("combining mark: box must be centred on first grapheme cluster 'e\\u0301', not just 'e'")
            .isCloseTo(expectedCombiningXSs, within(TOLERANCE));

        // (b) Surrogate pair: "😀" (U+1F600, 2 Java chars) + "la" — first grapheme cluster = "😀"
        var surrogateText = "😀la";
        grace.lyrics.clear();
        grace.lyrics.add(new Lyric(1, surrogateText, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        var resultSurrogate = LyricLayoutBuilder.build(List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);
        var surrogateBox = boxesOf(resultSurrogate.boxes(), grace).getFirst();
        var firstClusterSurrogate = "😀";
        var expectedSurrogateXSs = noteheadCenterXSs - LYRIC_METRICS.lyricBoxWidthSs(firstClusterSurrogate) / 2.0;
        assertThat(surrogateBox.xSs())
            .as("surrogate pair: box must be centred on emoji grapheme cluster, not the lone surrogate")
            .isCloseTo(expectedSurrogateXSs, within(TOLERANCE));
    }

    // REST + STOP: extender ends STOP_MELISMA_OVERSHOOT_SS past the rest's right edge —
    // a distinct code path from note+STOP (tested by testStopCarrierEndsExtenderAtNoteRightEdge).
    @Test
    void testRestWithStopLyricClosesExtenderPastRestRightEdge() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        var r = rest();
        setLyric(r, null, false, "", Lyric.Extend.STOP);
        addToLine(n1, r);

        var col0XSs = 5.0;
        var col1XSs = 5.0 + COLUMN_SPACING_SS;
        var columns = List.of(columnAt(n1, col0XSs), columnAt(r, col1XSs));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        // Only n1 gets a box; the rest carries only a STOP marker.
        assertThat(result.boxes()).containsOnlyKeys(n1);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders)
            .as("REST+STOP must close exactly one EXTENDER")
            .hasSize(1);
        assertThat(extenders.getFirst().endXSs())
            .as("extender ends STOP_MELISMA_OVERSHOOT_SS past rest's right edge")
            .isCloseTo(
                columns.get(1).getRightEdgeXSs() + LyricLayoutBuilder.STOP_MELISMA_OVERSHOOT_SS,
                within(TOLERANCE));
        assertThat(result.hasTrailingContinuation())
            .as("STOP on rest terminates the melisma — no trailing continuation")
            .isFalse();
    }

    // emitDanglingHyphen — no eligible follower: BEGIN syllable is the last element on the line;
    // no following element can receive the hyphen, so no DANGLING_HYPHEN (or HYPHEN) is emitted.
    @Test
    void testDanglingHyphenWithNoEligibleFollowerEmitsNoConnector() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, false, "do", false);
        addToLine(n1);

        // n1 is the only column; emitDanglingHyphen finds no eligible successor.
        var columns = List.of(columnAt(n1, 5.0));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.DANGLING_HYPHEN))
            .as("no eligible follower → no DANGLING_HYPHEN emitted")
            .isEmpty();
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN))
            .as("no eligible follower → no HYPHEN emitted")
            .isEmpty();
    }

    // emitDanglingHyphen — happy path: BEGIN syllable followed by an ineligible rest then an
    // eligible note; the DANGLING_HYPHEN ends at the eligible note's left edge.
    @Test
    void testDanglingHyphenEndsAtNextEligibleElementLeftEdge() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, false, "do", false);
        // Rest with no lyric is not eligible for lyric — skipped by emitDanglingHyphen.
        var r = rest();
        // Note with no lyric is always eligible — DANGLING_HYPHEN ends here.
        var n2 = note();
        addToLine(n1, r, n2);

        var col0XSs = 5.0;
        var col1XSs = 5.0 + COLUMN_SPACING_SS;
        var col2XSs = 5.0 + 2 * COLUMN_SPACING_SS;
        var columns = List.of(
            columnAt(n1, col0XSs),
            columnAt(r, col1XSs),
            columnAt(n2, col2XSs));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var danglingHyphens = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.DANGLING_HYPHEN);
        assertThat(danglingHyphens)
            .as("exactly one DANGLING_HYPHEN to the eligible note")
            .hasSize(1);
        assertThat(danglingHyphens.getFirst().endXSs())
            .as("DANGLING_HYPHEN ends at eligible note's left edge")
            .isCloseTo(columns.get(2).getLeftEdgeXSs(), within(TOLERANCE));
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN))
            .as("no closed HYPHEN — syllable never resolved to a text box")
            .isEmpty();
    }

    // sourceElementIndex on EXTENDER, DANGLING_HYPHEN, and DANGLING_EXTENDER connectors.
    // (HYPHEN sourceElementIndex is already verified in testDoReMiProducesThreeBoxesAndTwoHyphens.)
    @Test
    void testSourceElementIndexOnExtenderAndDanglingKinds() {
        // --- EXTENDER: opened by n1 at column index 0, closed by n3 ---
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        var n2 = note();
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.SINGLE, false, "men", Lyric.Extend.NONE);
        addToLine(n1, n2, n3);

        var columns = List.of(
            columnAt(n1, 5.0),
            columnAt(n2, 5.0 + COLUMN_SPACING_SS),
            columnAt(n3, 5.0 + 2 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
        assertThat(extenders.getFirst().sourceElementIndex())
            .as("EXTENDER sourceElementIndex = column index of START syllable (n1 = 0)")
            .isEqualTo(0);

        // --- DANGLING_EXTENDER: opened by n1 (SINGLE+START), no closer on line ---
        var nStart = note();
        setLyric(nStart, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        var nBare = note();

        var songD = new Song();
        var lineD = songD.getLine(0);
        songD.withoutMutationTracking(() -> {
            lineD.addElement(nStart);
            lineD.addElement(nBare);
        });

        var colsDangling = List.of(
            columnAt(nStart, 5.0),
            columnAt(nBare, 5.0 + COLUMN_SPACING_SS));

        var resultD = LyricLayoutBuilder.build(colsDangling, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var danglingExtenders = connectorsOfKind(resultD.connectors(), LyricConnectorLayout.Kind.DANGLING_EXTENDER);
        assertThat(danglingExtenders).hasSize(1);
        assertThat(danglingExtenders.getFirst().sourceElementIndex())
            .as("DANGLING_EXTENDER sourceElementIndex = column index of START syllable (nStart = 0)")
            .isEqualTo(0);

        // --- DANGLING_HYPHEN: opened by BEGIN at column index 0, eligible follower at column index 1 ---
        var nBegin = note();
        setLyric(nBegin, Lyric.Syllabic.BEGIN, false, "do", false);
        var nEligible = note();

        var songH = new Song();
        var lineH = songH.getLine(0);
        songH.withoutMutationTracking(() -> {
            lineH.addElement(nBegin);
            lineH.addElement(nEligible);
        });

        var colsHyphen = List.of(
            columnAt(nBegin, 5.0),
            columnAt(nEligible, 5.0 + COLUMN_SPACING_SS));

        var resultH = LyricLayoutBuilder.build(colsHyphen, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var danglingHyphens = connectorsOfKind(resultH.connectors(), LyricConnectorLayout.Kind.DANGLING_HYPHEN);
        assertThat(danglingHyphens).hasSize(1);
        assertThat(danglingHyphens.getFirst().sourceElementIndex())
            .as("DANGLING_HYPHEN sourceElementIndex = column index of BEGIN syllable (nBegin = 0)")
            .isEqualTo(0);
    }

    // Multi-verse: verse 1 and verse 2 each emit their own boxes and spans keyed by verseIndex
    @Test
    void testMultiVerseProducesSeparateBoxesPerVerse() {
        var n1 = note();
        var n2 = note();
        n1.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
        n1.lyrics.add(new Lyric(2, "un", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
        n2.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
        n2.lyrics.add(new Lyric(2, "deux", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
        addToLine(n1, n2);

        var columns = List.of(columnAt(n1, 5), columnAt(n2, 5 + COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.verseCount()).isEqualTo(2);
        assertThat(result.boxes().get(n1)).hasSize(2);
        assertThat(result.boxes().get(n2)).hasSize(2);
        var verses = result.connectors().stream().map(LyricConnectorLayout::verseIndex).distinct().toList();
        assertThat(verses).containsExactlyInAnyOrder(1, 2);
    }
}
