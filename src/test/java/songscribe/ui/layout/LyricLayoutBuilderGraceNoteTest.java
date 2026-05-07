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
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.Song;
import songscribe.music.StaffElement;
import songscribe.smufl.Engraving;

/**
 * Phase 4 of the grace-note-lyric feature: a lyric on a grace note centres its
 * first glyph on the grace's notehead centre (deliberately different from the
 * normal-note rule), and the host of the paired grace contributes no column to
 * the lyric verse output.
 */
class LyricLayoutBuilderGraceNoteTest extends UnitTest {

    private static final double POSITION_TOLERANCE_SS = 0.0001;
    private static final double GRACE_X_SS = 5.0;
    private static final double HOST_X_SS = 9.0;
    private static final double NEXT_X_SS = 13.0;
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

    private static StaffElement graceQuaver() {
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        return grace;
    }

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    private static void setLyric(
        StaffElement element, Lyric.Syllabic syllabic, String text) {
        element.properties.lyrics.add(new Lyric(1, text, Lyric.Extend.NONE, syllabic, false));
    }

    private static List<LyricBoxLayout> boxesOf(LyricLayoutBuilder.Result result, StaffElement element) {
        var list = result.boxes().get(element);

        if (list == null) {
            throw new AssertionError("expected boxes for element but found none");
        }

        return list;
    }

    /** Column for an ordinary note (full-size notehead width). */
    private static ElementColumn normalColumn(StaffElement element, double xSs) {
        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            0.0,
            Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }

    /** Column for a grace note (small-notehead width, no flag for simplicity). */
    private static ElementColumn graceColumn(StaffElement element, double xSs) {
        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            0.0,
            ElementColumnBuilder.NOTE_HEAD_SMALL_WIDTH_SS,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }

    // (a) The first glyph of a grace note's lyric is centred on the grace's notehead
    // centre — NOT the whole syllable, by design. Do not refactor this to use the
    // normal-note centring helper.
    // (b) The host of the paired grace produces no lyric box.
    @Test
    void testGraceLyricFirstGlyphCentredOnGraceNoteheadAndHostHasNoBox() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, "la");
        var host = crotchet();
        addToLine(grace, host);

        var columns = List.of(graceColumn(grace, GRACE_X_SS), normalColumn(host, HOST_X_SS));
        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes())
            .as("host of paired grace must contribute no lyric box")
            .containsOnlyKeys(grace);

        var graceBoxes = boxesOf(result, grace);
        assertThat(graceBoxes).hasSize(1);

        var graceBox = graceBoxes.getFirst();
        var firstGlyph = "l";
        var firstGlyphWidthSs = LYRIC_METRICS.lyricBoxWidthSs(firstGlyph);
        var noteheadCenterXSs = GRACE_X_SS + ElementType.GRACE_QUAVER.getElementCenterXSs();
        var firstGlyphCenterXSs = graceBox.xSs() + firstGlyphWidthSs / 2.0;

        assertThat(firstGlyphCenterXSs)
            .as("grace lyric's first-glyph centre must sit on the grace notehead centre")
            .isCloseTo(noteheadCenterXSs, within(POSITION_TOLERANCE_SS));
    }

    // (c) A hyphen opened on the grace lyric reaches the next lyric-bearing element
    // after the host, not the host itself.
    @Test
    void testHyphenFromGraceLyricSkipsHostAndConnectsToNextLyricBearingNote() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.BEGIN, "la");
        var host = crotchet();
        var next = crotchet();
        setLyric(next, Lyric.Syllabic.END, "ter");
        addToLine(grace, host, next);

        var columns = List.of(
            graceColumn(grace, GRACE_X_SS),
            normalColumn(host, HOST_X_SS),
            normalColumn(next, NEXT_X_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var hyphens = result.connectors().stream()
            .filter(c -> c.kind() == LyricConnectorLayout.Kind.HYPHEN)
            .toList();
        assertThat(hyphens).hasSize(1);

        var nextBox = boxesOf(result, next).getFirst();
        assertThat(hyphens.getFirst().endXSs())
            .as("hyphen must reach the next lyric-bearing element's box, skipping the host")
            .isCloseTo(nextBox.xSs(), within(POSITION_TOLERANCE_SS));
    }
}
