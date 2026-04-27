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

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.Lyric;
import songscribe.music.StaffElement;
import songscribe.music.StaffElement.SyllableRelation;
import songscribe.smufl.Engraving;

class LyricLayoutBuilderTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final double COLUMN_SPACING_SS = 4.0;
    private static final double LINE_WIDTH_SS = 100.0;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);

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

    private static void setLyric(StaffElement element, SyllableRelation relation, String text, boolean extend) {
        element.properties.lyrics.add(new Lyric(1, relation, text,
            extend ? Lyric.Extend.START : Lyric.Extend.NONE));
    }

    private static void setLyric(StaffElement element, SyllableRelation relation, String text, Lyric.Extend extend) {
        element.properties.lyrics.add(new Lyric(1, relation, text, extend));
    }

    private static List<LyricBoxLayout> boxesOf(
        Map<StaffElement, List<LyricBoxLayout>> boxes,
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
        assertThat(result.verseCount()).isZero();
        assertThat(result.hasTrailingContinuation()).isFalse();
    }

    @Test
    void testLineWithoutLyricsProducesEmptyResult() {
        var columns = List.of(columnAt(note(), 5), columnAt(note(), 10), columnAt(note(), 15));
        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).isEmpty();
        assertThat(result.connectors()).isEmpty();
        assertThat(result.verseCount()).isZero();
    }

    // do-re-mi across three notes → three boxes, two HYPHEN spans
    @Test
    void testDoReMiProducesThreeBoxesAndTwoHyphens() {
        var n1 = note();
        setLyric(n1, SyllableRelation.SYLLABLE, "do", false);
        var n2 = note();
        setLyric(n2, SyllableRelation.SYLLABLE, "re", false);
        var n3 = note();
        setLyric(n3, SyllableRelation.NONE, "mi", false);

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
        setLyric(n1, SyllableRelation.NONE, "heart", true);
        var n2 = note();
        var n3 = note();
        var n4 = note();
        setLyric(n4, SyllableRelation.NONE, "garden", false);

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
        setLyric(n1, SyllableRelation.NONE, "heart", true);
        var n2 = note();
        var r = rest();
        var n3 = note();
        var n4 = note();
        setLyric(n4, SyllableRelation.NONE, "garden", false);

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
        setLyric(n1, SyllableRelation.NONE, "heart", true);
        var n2 = note();
        var r = rest();
        setLyric(r, SyllableRelation.NONE, "", true);
        var n3 = note();
        var n4 = note();
        setLyric(n4, SyllableRelation.NONE, "garden", false);

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
        setLyric(n1, SyllableRelation.NONE, "ah", true);
        var columnsA = List.of(columnAt(n1, 5));

        var resultA = LyricLayoutBuilder.build(columnsA, LYRIC_METRICS, false, LINE_WIDTH_SS);
        assertThat(resultA.hasTrailingContinuation()).isTrue();
        var trailingExtenders = connectorsOfKind(resultA.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(trailingExtenders).hasSize(1);
        assertThat(trailingExtenders.get(0).endXSs())
            .as("trailing stub ends at line width")
            .isCloseTo(LINE_WIDTH_SS, within(TOLERANCE));

        // Line B: no lyric on its first note, second note has 'garden'
        var n2 = note();
        var n3 = note();
        setLyric(n3, SyllableRelation.NONE, "garden", false);
        var columnsB = List.of(columnAt(n2, 5), columnAt(n3, 5 + COLUMN_SPACING_SS));

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

    // Compound-word boundary: heart(COMPOUND_WORD) garden(NONE) → HYPHEN span (visually identical to SYLLABLE)
    @Test
    void testCompoundWordBoundaryProducesHyphen() {
        var n1 = note();
        setLyric(n1, SyllableRelation.COMPOUND_WORD, "heart", false);
        var n2 = note();
        setLyric(n2, SyllableRelation.NONE, "garden", false);

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
        setLyric(n1, SyllableRelation.NONE, "den", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, SyllableRelation.NONE, "", Lyric.Extend.STOP);

        var columns = List.of(columnAt(n1, 5), columnAt(n2, 5 + COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes()).containsOnlyKeys(n1);

        var extenders = connectorsOfKind(result.connectors(), LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
        assertThat(extenders.get(0).endXSs())
            .as("extender ends at stop carrier note's right edge")
            .isCloseTo(columns.get(1).getRightEdgeXSs(), within(TOLERANCE));
        assertThat(result.hasTrailingContinuation())
            .as("stop terminates melisma — no trailing continuation")
            .isFalse();
    }

    // CONTINUE carrier on a mid-line note — extender passes through silently, same
    // shape as a note with no lyric.
    @Test
    void testContinueCarrierPassesThrough() {
        var n1 = note();
        setLyric(n1, SyllableRelation.NONE, "ah", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, SyllableRelation.NONE, "", Lyric.Extend.CONTINUE);
        var n3 = note();
        setLyric(n3, SyllableRelation.NONE, "men", Lyric.Extend.NONE);

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
        setLyric(n1, SyllableRelation.SYLLABLE, "con", Lyric.Extend.START);
        var n2 = note();
        setLyric(n2, SyllableRelation.NONE, "", Lyric.Extend.CONTINUE);
        var n3 = note();
        setLyric(n3, SyllableRelation.NONE, "tinue", Lyric.Extend.NONE);

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
        setLyric(n1, SyllableRelation.COMPOUND_WORD, "heart", true);
        var n2 = note();
        var n3 = note();
        var n4 = note();
        var n5 = note();
        setLyric(n5, SyllableRelation.SYLLABLE, "gar", true);
        var n6 = note();
        var n7 = note();
        var n8 = note();
        setLyric(n8, SyllableRelation.NONE, "den", false);

        var columns = new ArrayList<ElementColumn>();
        var elements = List.of(n1, n2, n3, n4, n5, n6, n7, n8);

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
        n1.properties.lyrics.add(new Lyric(1, SyllableRelation.SYLLABLE, "do", Lyric.Extend.NONE));
        n1.properties.lyrics.add(new Lyric(2, SyllableRelation.SYLLABLE, "un", Lyric.Extend.NONE));
        n2.properties.lyrics.add(new Lyric(1, SyllableRelation.NONE, "re", Lyric.Extend.NONE));
        n2.properties.lyrics.add(new Lyric(2, SyllableRelation.NONE, "deux", Lyric.Extend.NONE));

        var columns = List.of(columnAt(n1, 5), columnAt(n2, 5 + COLUMN_SPACING_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.verseCount()).isEqualTo(2);
        assertThat(result.boxes().get(n1)).hasSize(2);
        assertThat(result.boxes().get(n2)).hasSize(2);
        var verses = result.connectors().stream().map(LyricConnectorLayout::verseIndex).distinct().toList();
        assertThat(verses).containsExactlyInAnyOrder(1, 2);
    }
}
