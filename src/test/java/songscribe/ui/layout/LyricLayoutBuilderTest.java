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

package songscribe.ui.layout;

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
import songscribe.message.MessageCenter;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.Song;
import songscribe.music.StaffElement;
import songscribe.smufl.Engraving;

class LyricLayoutBuilderTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final double COLUMN_SPACING_SS = 4.0;
    private static final double LINE_WIDTH_SS = 100.0;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);

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
        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            0.0,
            Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }

    private static void setLyric(
        StaffElement element, Lyric.@Nullable Syllabic syllabic, boolean compound, String text, boolean extend) {
        element.properties.lyrics.add(new Lyric(1, text,
            extend ? Lyric.Extend.START : Lyric.Extend.NONE, syllabic, compound));
    }

    private static void setLyric(
        StaffElement element, Lyric.@Nullable Syllabic syllabic, boolean compound, String text, Lyric.Extend extend) {
        element.properties.lyrics.add(new Lyric(1, text, extend, syllabic, compound));
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
        var columns = List.of(columnAt(note(), 5), columnAt(note(), 10), columnAt(note(), 15));
        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).isEmpty();
        assertThat(result.connectors()).isEmpty();
        assertThat(result.verseCount()).isEqualTo(1);
    }

    // do-re-mi across three notes → three boxes, two HYPHEN spans
    @Test
    void testDoReMiProducesThreeBoxesAndTwoHyphens() {
        var n1 = note();
        setLyric(n1, Lyric.Syllabic.BEGIN, false, "do", false);
        var n2 = note();
        setLyric(n2, Lyric.Syllabic.MIDDLE, false, "re", false);
        var n3 = note();
        setLyric(n3, Lyric.Syllabic.END, false, "mi", false);
        addToLine(n1, n2, n3);

        var columns = List.of(
            columnAt(n1, 5),
            columnAt(n2, 5 + COLUMN_SPACING_SS),
            columnAt(n3, 5 + 2 * COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).hasSize(3);
        assertThat(boxesOf(result.boxes(), n1)).hasSize(1);
        assertThat(boxesOf(result.boxes(), n1).get(0).text()).isEqualTo("do");
        assertThat(boxesOf(result.boxes(), n2).get(0).text()).isEqualTo("re");
        assertThat(boxesOf(result.boxes(), n3).get(0).text()).isEqualTo("mi");

        var hyphens = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN);
        assertThat(hyphens).hasSize(2);
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER)).isEmpty();
        assertThat(result.hasTrailingContinuation()).isFalse();
        assertThat(result.verseCount()).isEqualTo(1);
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

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).hasSize(2);
        assertThat(boxesOf(result.boxes(), n1).get(0).text()).isEqualTo("heart");
        assertThat(boxesOf(result.boxes(), n4).get(0).text()).isEqualTo("garden");

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
        assertThat(connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.HYPHEN)).isEmpty();
        assertThat(result.hasTrailingContinuation()).isFalse();
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
        assertThat(extenders.get(0).endXSs())
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
        assertThat(trailingExtenders.get(0).endXSs())
            .as("trailing stub ends at last note's right edge")
            .isCloseTo(columnsA.get(0).getRightEdgeXSs(), within(TOLERANCE));

        var resultB = LyricLayoutBuilder.build(columnsB, LYRIC_METRICS, true, LINE_WIDTH_SS);

        var leadingExtenders = connectorsOfKind(resultB.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(leadingExtenders).hasSize(1);
        assertThat(leadingExtenders.get(0).startXSs())
            .as("leading stub starts at line left edge")
            .isCloseTo(0.0, within(TOLERANCE));
        assertThat(leadingExtenders.get(0).endXSs())
            .as("leading stub ends at garden's left edge")
            .isEqualTo(boxesOf(resultB.boxes(), n3).get(0).xSs(), within(TOLERANCE));
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
        assertThat(danglingExtenders.get(0).endXSs())
            .as("dangling extender ends at START note's right edge")
            .isCloseTo(columns.get(0).getRightEdgeXSs(), within(TOLERANCE));
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
        assertThat(danglingExtenders.get(0).endXSs())
            .as("dangling extender ends at last CONTINUE note's right edge (n3), not n4 (no extend marker)")
            .isCloseTo(columns.get(2).getRightEdgeXSs(), within(TOLERANCE));
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
        assertThat(extenders.get(0).endXSs())
            .as("extender ends 0.25 ss beyond stop carrier note's right edge")
            .isCloseTo(
                columns.get(1).getRightEdgeXSs() + LyricLayoutBuilder.STOP_MELISMA_OVERSHOOT_SS,
                within(TOLERANCE));
        assertThat(result.hasTrailingContinuation())
            .as("stop terminates melisma — no trailing continuation")
            .isFalse();
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

    // Multi-verse: verse 1 and verse 2 each emit their own boxes and spans keyed by verseIndex
    @Test
    void testMultiVerseProducesSeparateBoxesPerVerse() {
        var n1 = note();
        var n2 = note();
        n1.properties.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
        n1.properties.lyrics.add(new Lyric(2, "un", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
        n2.properties.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
        n2.properties.lyrics.add(new Lyric(2, "deux", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
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
