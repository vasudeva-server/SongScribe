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
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.SMuFLConstants;

/**
 * Grace-note-lyric layout: a lyric narrower than the grace notehead centres on that
 * notehead (excluding the flag), a wider lyric anchors/overhangs against the grace→host
 * notehead union, and the host of the paired grace contributes no lyric box. A STOP carrier
 * on the host closes the automatic grace-host melisma at the host's notehead; without one the
 * host still passes hyphens and extenders through.
 */
class LyricLayoutBuilderGraceNoteTest extends UnitTest {

    private static final double POSITION_TOLERANCE_SS = 0.0001;
    // A real unbeamed grace quaver's right extent reaches past the small notehead to the flag tip;
    // double the notehead width stands in for that so a notehead-vs-flag centring bug is detectable.
    private static final double FLAG_INFLATED_GRACE_RIGHT_EXTENT_SS = ElementColumnBuilder.NOTE_HEAD_SMALL_WIDTH_SS * 2;
    private static final double GRACE_X_SS = 5.0;
    private static final double HOST_X_SS = 9.0;
    private static final double NEXT_X_SS = 13.0;
    // Close enough behind the host that a melisma ending at the host notehead would run into the
    // following syllable's box, so the follow-syllable clamp has something to pull back.
    private static final double CROWDED_NEXT_X_SS = 10.0;
    private static final double LINE_WIDTH_SS = 100.0;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

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
        grace.setGlissando();
        return grace;
    }

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    private static void setLyric(
        StaffElement element, Lyric.Syllabic syllabic, String text) {
        setLyric(element, syllabic, text, Lyric.Extend.NONE);
    }

    private static void setLyric(
        StaffElement element, Lyric.Syllabic syllabic, String text, Lyric.Extend extend) {
        element.lyrics.add(new Lyric(1, text, extend, syllabic, false));
    }

    /** Text-less melisma carrier, as {@code Line.syncGraceHostMelisma} writes onto a grace's host. */
    private static void setCarrier(StaffElement element, Lyric.Extend extend) {
        element.lyrics.add(new Lyric(1, "", extend, null, false));
    }

    private static List<LyricConnectorLayout> connectorsOfKind(
        LyricLayoutBuilder.Result result, LyricConnectorLayout.Kind kind) {

        return result.connectors().stream().filter(c -> c.kind() == kind).toList();
    }

    private static List<LyricBoxLayout> boxesOf(LyricLayoutBuilder.Result result, StaffElement element) {
        var list = result.boxes().get(element);

        if (list == null) {
            throw new AssertionError("expected boxes for element but found none");
        }

        return list;
    }

    /** Column for an ordinary note (full-size notehead width) with no measured syllable. */
    private static ElementColumn normalColumn(StaffElement element, double xSs) {
        return normalColumn(element, xSs, 0.0);
    }

    /**
     * Column for an ordinary note carrying a syllable of {@code syllableWidthSs}. Verse 1 reads its
     * box width off the column rather than re-measuring, so a lyric-bearing column has to supply it.
     */
    private static ElementColumn normalColumn(StaffElement element, double xSs, double syllableWidthSs) {
        return column(element, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, syllableWidthSs);
    }

    /** Column for a grace note (small-notehead width, no flag for simplicity). */
    private static ElementColumn graceColumn(StaffElement element, double xSs) {
        return graceColumn(element, xSs, 0.0);
    }

    /** Grace-note column carrying a syllable of {@code syllableWidthSs} (see {@link #normalColumn}). */
    private static ElementColumn graceColumn(StaffElement element, double xSs, double syllableWidthSs) {
        return column(element, xSs, ElementColumnBuilder.NOTE_HEAD_SMALL_WIDTH_SS, syllableWidthSs);
    }

    private static ElementColumn column(
        StaffElement element, double xSs, double noteheadWidthSs, double syllableWidthSs) {

        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            0.0,
            noteheadWidthSs,
            0.0, 0.0, null, syllableWidthSs, false);
        column.setXSs(xSs);
        return column;
    }

    // (a) A grace lyric narrower than the grace notehead centres on the notehead itself, NOT on the
    // flag-inflated right extent (getRightExtentExcludingAugmentationSs() folds in the flag, which
    // would push a narrow syllable such as "I" off the notehead to the right).
    // (b) The host of the paired grace produces no lyric box.
    @Test
    void testNarrowGraceLyricCentresOnNoteheadNotFlag() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, "I");
        var host = crotchet();
        addToLine(grace, host);

        // Precondition: "I" is narrower than the grace notehead, so it takes the centre-on-notehead path.
        var widthSs = LYRIC_METRICS.lyricBoxWidthSs("I");
        assertThat(widthSs)
            .as("the syllable must be narrower than the grace notehead to exercise the centring path")
            .isLessThan(ElementColumnBuilder.NOTE_HEAD_SMALL_WIDTH_SS);

        // Grace column whose right extent reaches past the small notehead to the flag tip, but whose
        // notehead width is the small head (as ElementColumnBuilder sets it). If centring used the
        // right extent instead of the notehead width, the syllable would shift right.
        var graceCol = new ElementColumn(
            grace,
            Collections.emptyList(),
            0.0,
            FLAG_INFLATED_GRACE_RIGHT_EXTENT_SS,
            FLAG_INFLATED_GRACE_RIGHT_EXTENT_SS,
            0.0, 0.0, null, widthSs, false);
        graceCol.setNoteheadWidthSs(ElementColumnBuilder.NOTE_HEAD_SMALL_WIDTH_SS);
        graceCol.setXSs(GRACE_X_SS);
        var hostCol = normalColumn(host, HOST_X_SS);
        var result = LyricLayoutBuilder.build(List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes())
            .as("host of paired grace must contribute no lyric box")
            .containsOnlyKeys(grace);

        var graceBoxes = boxesOf(result, grace);
        assertThat(graceBoxes).hasSize(1);

        var noteheadCenterXSs = GRACE_X_SS + ElementColumnBuilder.NOTE_HEAD_SMALL_WIDTH_SS / 2.0;
        var graceBox = graceBoxes.getFirst();
        assertThat(graceBox.xSs())
            .as("narrow grace lyric centres on the notehead, ignoring the flag-inflated right extent")
            .isCloseTo(noteheadCenterXSs - widthSs / 2.0, within(POSITION_TOLERANCE_SS));
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

        var hyphens = connectorsOfKind(result, LyricConnectorLayout.Kind.HYPHEN);
        assertThat(hyphens).hasSize(1);

        var nextBox = boxesOf(result, next).getFirst();
        assertThat(hyphens.getFirst().endXSs())
            .as("hyphen must reach the next lyric-bearing element's box, skipping the host")
            .isCloseTo(nextBox.xSs(), within(POSITION_TOLERANCE_SS));
    }

    // (d) The automatic grace-host melisma: a STOP carrier on the host closes the extender the
    // grace started, anchored past the host's notehead — the host is no longer passed through.
    @Test
    void testStopCarrierOnHostClosesTheGraceMelismaAtItsNotehead() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, "la", Lyric.Extend.START);
        var host = crotchet();
        setCarrier(host, Lyric.Extend.STOP);
        addToLine(grace, host);

        var result = LyricLayoutBuilder.build(
            List.of(graceColumn(grace, GRACE_X_SS, LYRIC_METRICS.lyricBoxWidthSs("la")),
                normalColumn(host, HOST_X_SS)),
            LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes())
            .as("the host's STOP carrier is text-less, so it still emits no lyric box")
            .containsOnlyKeys(grace);
        assertThat(result.hasTrailingContinuation())
            .as("the melisma is closed on this line, so nothing continues onto the next")
            .isFalse();

        var extenders = connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);

        var extender = extenders.getFirst();
        var graceBox = boxesOf(result, grace).getFirst();
        var syllableEndXSs = graceBox.xSs() + graceBox.widthSs();
        var hostNoteheadRightEdgeXSs = HOST_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS;

        assertThat(extender.startXSs())
            .as("the extender starts at the end of the grace's syllable")
            .isCloseTo(syllableEndXSs, within(POSITION_TOLERANCE_SS));

        // Precondition: the host's notehead is far enough away that it, not the minimum-length
        // rule, decides where the extender ends — otherwise the anchor assertion below is vacuous.
        assertThat(hostNoteheadRightEdgeXSs)
            .as("the host notehead must dominate the minimum melisma length in this layout")
            .isGreaterThan(syllableEndXSs + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS);
        assertThat(extender.endXSs())
            .as("the extender ends at the host's notehead right edge, excluding stem and flag")
            .isCloseTo(hostNoteheadRightEdgeXSs, within(POSITION_TOLERANCE_SS));
    }

    // (e) A host with no STOP still passes the grace's extender through: it is only a real STOP
    // that terminates at the host, so a melisma spanning past the pair is unaffected.
    @Test
    void testExtenderFromGracePassesThroughAHostWithNoStop() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, "la", Lyric.Extend.START);
        var host = crotchet();
        var next = crotchet();
        setCarrier(next, Lyric.Extend.STOP);
        addToLine(grace, host, next);

        var columns = List.of(
            graceColumn(grace, GRACE_X_SS, LYRIC_METRICS.lyricBoxWidthSs("la")),
            normalColumn(host, HOST_X_SS),
            normalColumn(next, NEXT_X_SS));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var extenders = connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);
        assertThat(extenders.getFirst().endXSs())
            .as("the extender runs past the host to the element that actually carries the STOP")
            .isCloseTo(NEXT_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS, within(POSITION_TOLERANCE_SS));
    }

    // (f) A grace-host melisma is clamped back off a following syllable by the lyric space width,
    // the same as any other melisma — the host anchor does not escape the clamp.
    @Test
    void testGraceMelismaIsClampedOffAFollowingSyllable() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, "la", Lyric.Extend.START);
        var host = crotchet();
        setCarrier(host, Lyric.Extend.STOP);
        var next = crotchet();
        setLyric(next, Lyric.Syllabic.SINGLE, "ter");
        addToLine(grace, host, next);

        var columns = List.of(
            graceColumn(grace, GRACE_X_SS, LYRIC_METRICS.lyricBoxWidthSs("la")),
            normalColumn(host, HOST_X_SS),
            normalColumn(next, CROWDED_NEXT_X_SS, LYRIC_METRICS.lyricBoxWidthSs("ter")));

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var extenders = connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);

        var maxEndXSs = boxesOf(result, next).getFirst().xSs() - LYRIC_METRICS.spaceWidthSs();

        // Precondition: unclamped, the extender would end at the host notehead, past the limit.
        assertThat(HOST_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS)
            .as("the following syllable must crowd the host anchor for the clamp to be exercised")
            .isGreaterThan(maxEndXSs);
        assertThat(extenders.getFirst().endXSs())
            .as("the extender is pulled back to one lyric space before the following syllable")
            .isCloseTo(maxEndXSs, within(POSITION_TOLERANCE_SS));
    }
}
