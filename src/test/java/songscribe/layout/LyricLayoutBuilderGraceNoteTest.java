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
 * Grace-note-lyric layout: a lyric narrower than the grace notehead centers on that
 * notehead (excluding the flag), a wider lyric anchors/overhangs against the grace→host
 * notehead union, and the host of the paired grace contributes no lyric box. A STOP carrier
 * on the host closes the automatic grace-host melisma at the host's notehead; without one the
 * host still passes hyphens and extenders through.
 */
class LyricLayoutBuilderGraceNoteTest extends UnitTest {

    private static final double POSITION_TOLERANCE_SS = 0.0001;
    // A real unbeamed grace quaver's right extent reaches past the small notehead to the flag tip;
    // double the notehead width stands in for that so a notehead-vs-flag centering bug is detectable.
    private static final double FLAG_INFLATED_GRACE_RIGHT_EXTENT_SS = ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS * 2;
    /** A multi-grapheme syllable, so the first grapheme is distinguishable from the whole box. */
    private static final String GRACE_SYLLABLE = "la";
    /**
     * A syllable wider than the whole grace-host union, so it reaches its host on its own and the
     * pair's melisma has nowhere left to be drawn.
     */
    private static final String UNION_SPANNING_SYLLABLE = "strength";
    /**
     * A syllable that fits the union on its own but not once its minimum-length melisma follows it,
     * so the two together are centered on the union.
     */
    private static final String MELISMA_SPILLING_SYLLABLE = "lala";
    private static final double GRACE_X_SS = 5.0;
    private static final double HOST_X_SS = 9.0;
    // The host at the ideal grace→host distance: one fixed grace→host rest past the grace's ink.
    // A test that reasons about the ideal union has to lay the pair out here, since that is the only
    // distance at which the ideal union and the union actually spaced coincide.
    private static final double PACKED_HOST_X_SS =
        GRACE_X_SS + ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS
            + HorizontalSpacingCalculator.GRACE_HOST_REST_SS;
    // How much wider than ideal a solved grace→host gap can be — the optical correction measured for
    // a stem-up grace against a stem-down host. The exact amount does not matter to the tests below,
    // only that the pair ends up at a distance the ideal-union formula does not predict.
    private static final double OPTICAL_WIDENING_SS = 0.38;
    private static final double NEXT_X_SS = 13.0;
    // Close enough behind the host that a melisma ending at the host notehead would run into the
    // following syllable's box, so the follow-syllable clamp has something to pull back.
    private static final double CROWDED_NEXT_X_SS = 10.0;
    // A run of columns past the first grace-host pair, for a melisma that spans several notes before
    // ending at a second pair's host. Spread far enough apart that each candidate anchor the melisma
    // could have stopped at is distinguishable from the one it must actually reach.
    private static final double SPAN_MIDDLE_X_SS = 13.0;
    private static final double SPAN_LATER_GRACE_X_SS = 17.0;
    private static final double SPAN_LATER_HOST_X_SS = 20.0;
    private static final double LINE_WIDTH_SS = 100.0;
    private static final int LYRICS_FONT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, LYRICS_FONT_SIZE);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);
    // Every glyph of a monospaced font is one advance wide, so even "I" measures wider than the
    // 0.885 ss grace notehead at LYRICS_FONT_SIZE. The center-on-notehead path needs a syllable that
    // actually fits the notehead, which only a smaller font gives.
    private static final int NARROW_LYRICS_FONT_SIZE = 8;
    private static final Font NARROW_LYRICS_FONT =
        new Font(Font.MONOSPACED, Font.PLAIN, NARROW_LYRICS_FONT_SIZE);
    private static final LyricRenderMetrics NARROW_LYRIC_METRICS =
        new LyricRenderMetrics(NARROW_LYRICS_FONT, NARROW_LYRICS_FONT, 0.0, 0.0, 0.0);

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
        return column(element, xSs, ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS, syllableWidthSs);
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

    // (a) A grace lyric narrower than the grace notehead centers on the notehead itself, NOT on the
    // flag-inflated right extent (getRightExtentExcludingAugmentationSs() folds in the flag, which
    // would push a narrow syllable such as "I" off the notehead to the right).
    // (b) The host of the paired grace produces no lyric box.
    @Test
    void testNarrowGraceLyricCentersOnNoteheadNotFlag() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, "I");
        var host = crotchet();
        addToLine(grace, host);

        // Precondition: "I" is narrower than the grace notehead, so it takes the center-on-notehead path.
        var widthSs = NARROW_LYRIC_METRICS.lyricBoxWidthSs("I");
        assertThat(widthSs)
            .as("the syllable must be narrower than the grace notehead to exercise the centering path")
            .isLessThan(ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS);

        // Grace column whose right extent reaches past the small notehead to the flag tip, but whose
        // notehead width is the small head (as ElementColumnBuilder sets it). If centering used the
        // right extent instead of the notehead width, the syllable would shift right.
        var graceCol = new ElementColumn(
            grace,
            Collections.emptyList(),
            0.0,
            FLAG_INFLATED_GRACE_RIGHT_EXTENT_SS,
            FLAG_INFLATED_GRACE_RIGHT_EXTENT_SS,
            0.0, 0.0, null, widthSs, false);
        graceCol.setNoteheadWidthSs(ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS);
        graceCol.setXSs(GRACE_X_SS);
        var hostCol = normalColumn(host, HOST_X_SS);
        var result =
            LyricLayoutBuilder.build(List.of(graceCol, hostCol), NARROW_LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(result.boxes())
            .as("host of paired grace must contribute no lyric box")
            .containsOnlyKeys(grace);

        var graceBoxes = boxesOf(result, grace);
        assertThat(graceBoxes).hasSize(1);

        var noteheadCenterXSs = GRACE_X_SS + ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS / 2.0;
        var graceBox = graceBoxes.getFirst();
        assertThat(graceBox.xSs())
            .as("narrow grace lyric centers on the notehead, ignoring the flag-inflated right extent")
            .isCloseTo(noteheadCenterXSs - widthSs / 2.0, within(POSITION_TOLERANCE_SS));
    }

    // A grace lyric too wide to fit under the small notehead centers its FIRST GRAPHEME on the
    // notehead, so the syllable reads as beginning at the grace note and flows rightward toward the
    // host — it is not left-anchored on the notehead's left edge.
    @Test
    void testWideGraceLyricCentersItsFirstGraphemeOnTheNotehead() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, GRACE_SYLLABLE);
        var host = crotchet();
        addToLine(grace, host);

        var widthSs = LYRIC_METRICS.lyricBoxWidthSs(GRACE_SYLLABLE);
        var firstGraphemeWidthSs = LYRIC_METRICS.firstGraphemeWidthSs(GRACE_SYLLABLE);
        assertThat(widthSs)
            .as("the syllable must exceed the grace notehead to exercise the first-grapheme path")
            .isGreaterThan(ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS);

        var graceCol = graceColumn(grace, GRACE_X_SS, widthSs);
        var hostCol = normalColumn(host, HOST_X_SS);
        var result =
            LyricLayoutBuilder.build(List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);

        var noteheadCenterXSs = GRACE_X_SS + ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS / 2.0;
        var graceBox = boxesOf(result, grace).getFirst();
        assertThat(graceBox.xSs())
            .as("grace lyric centers its first grapheme on the grace notehead")
            .isCloseTo(noteheadCenterXSs - firstGraphemeWidthSs / 2.0, within(POSITION_TOLERANCE_SS));
        assertThat(graceBox.xSs())
            .as("the syllable must not be left-anchored on the notehead")
            .isNotCloseTo(GRACE_X_SS, within(POSITION_TOLERANCE_SS));
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

    // (g) A grace syllable at least as wide as the grace-host union already reaches its host, so the
    // melisma — still there in the model — is not drawn at all, and the syllable stays centered on
    // the union.
    @Test
    void testGraceMelismaIsNotDrawnWhenTheSyllableAlreadySpansTheUnion() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, UNION_SPANNING_SYLLABLE, Lyric.Extend.START);
        var host = crotchet();
        setCarrier(host, Lyric.Extend.STOP);
        addToLine(grace, host);

        var widthSs = LYRIC_METRICS.lyricBoxWidthSs(UNION_SPANNING_SYLLABLE);
        var graceCol = graceColumn(grace, GRACE_X_SS, widthSs);
        var hostCol = normalColumn(host, PACKED_HOST_X_SS);
        var unionWidthSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(graceCol, hostCol);

        // Precondition: the syllable alone spans the union, which is what suppresses the melisma.
        assertThat(widthSs)
            .as("the syllable must span the union to exercise the suppression path")
            .isGreaterThanOrEqualTo(unionWidthSs);

        var result = LyricLayoutBuilder.build(
            List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER))
            .as("a syllable that already reaches the host leaves nothing for an extender to show")
            .isEmpty();
        assertThat(result.hasTrailingContinuation())
            .as("dropping the drawn extender must not leak the melisma onto the next line")
            .isFalse();

        var graceBox = boxesOf(result, grace).getFirst();
        assertThat(graceBox.xSs())
            .as("the syllable stays centered on the union, overhanging it equally on both sides")
            .isCloseTo(GRACE_X_SS - (widthSs - unionWidthSs) / 2, within(POSITION_TOLERANCE_SS));
    }

    // (h) A grace syllable that fits the union but whose minimum-length melisma spills past it is
    // laid out with that melisma: the two are centered on the union together.
    @Test
    void testGraceSyllableAndItsMelismaAreCenteredOnTheUnionWhenTheySpillPastIt() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, MELISMA_SPILLING_SYLLABLE, Lyric.Extend.START);
        var host = crotchet();
        setCarrier(host, Lyric.Extend.STOP);
        addToLine(grace, host);

        var widthSs = LYRIC_METRICS.lyricBoxWidthSs(MELISMA_SPILLING_SYLLABLE);
        var graceCol = graceColumn(grace, GRACE_X_SS, widthSs);
        var hostCol = normalColumn(host, PACKED_HOST_X_SS);
        var unionWidthSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(graceCol, hostCol);
        var contentWidthSs = widthSs + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS;

        // Precondition: the syllable fits the union alone, and overflows it once the melisma follows.
        assertThat(widthSs).isLessThan(unionWidthSs);
        assertThat(contentWidthSs).isGreaterThan(unionWidthSs);

        var result = LyricLayoutBuilder.build(
            List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);

        var expectedBoxXSs = GRACE_X_SS - (contentWidthSs - unionWidthSs) / 2;
        var graceBox = boxesOf(result, grace).getFirst();
        assertThat(graceBox.xSs())
            .as("syllable and melisma are centered on the union as one")
            .isCloseTo(expectedBoxXSs, within(POSITION_TOLERANCE_SS));

        var extenders = connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders).hasSize(1);

        var extender = extenders.getFirst();
        assertThat(extender.startXSs())
            .as("the melisma starts where the syllable box ends")
            .isCloseTo(graceBox.xSs() + graceBox.widthSs(), within(POSITION_TOLERANCE_SS));
        assertThat(extender.endXSs())
            .as("the melisma keeps its minimum length, ending past the host's notehead")
            .isCloseTo(expectedBoxXSs + contentWidthSs, within(POSITION_TOLERANCE_SS));
        assertThat(extender.endXSs())
            .isGreaterThan(PACKED_HOST_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS);
    }

    // (i) The grace→host gap the solver produces is not the ideal one — OpticalSpacing corrects it
    // for the two notes' stem geometry, and a strut can clamp it — so the syllable and its melisma
    // have to be centered on the union as spaced, not on the union the ideal formula predicts.
    @Test
    void testGraceSyllableAndItsMelismaAreCenteredOnTheUnionAsSpaced() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, MELISMA_SPILLING_SYLLABLE, Lyric.Extend.START);
        var host = crotchet();
        setCarrier(host, Lyric.Extend.STOP);
        addToLine(grace, host);

        var widthSs = LYRIC_METRICS.lyricBoxWidthSs(MELISMA_SPILLING_SYLLABLE);
        var graceCol = graceColumn(grace, GRACE_X_SS, widthSs);
        var hostCol = normalColumn(host, PACKED_HOST_X_SS + OPTICAL_WIDENING_SS);
        var idealUnionWidthSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(graceCol, hostCol);
        var spacedUnionWidthSs = hostCol.getNoteheadRightEdgeXSs() - GRACE_X_SS;
        var contentWidthSs = widthSs + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS;

        // Precondition: the pair was spaced wider than ideal, and the content still spills past it.
        assertThat(spacedUnionWidthSs).isGreaterThan(idealUnionWidthSs);
        assertThat(contentWidthSs).isGreaterThan(spacedUnionWidthSs);

        var result = LyricLayoutBuilder.build(
            List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);

        var graceBox = boxesOf(result, grace).getFirst();
        var extender = connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER).getFirst();

        assertThat((graceBox.xSs() + extender.endXSs()) / 2)
            .as("syllable and melisma straddle the center of the union as spaced")
            .isCloseTo(GRACE_X_SS + spacedUnionWidthSs / 2, within(POSITION_TOLERANCE_SS));
        assertThat(graceBox.xSs())
            .as("centering on the ideal union would have left the content half the error too far left")
            .isNotCloseTo(
                GRACE_X_SS - (contentWidthSs - idealUnionWidthSs) / 2, within(POSITION_TOLERANCE_SS));
    }

    // (j) The melisma reaching a host need not be the one its own grace started: Line.syncGraceHostMelisma
    // leaves a host that already carries an extender onward without a STOP of its own, so a melisma can
    // start at one grace, run through its host and past several notes, and stop at a second pair's host.
    // Dropping a self-spanning syllable's melisma belongs only to the pair that started it — applied here
    // it would erase a line spanning the whole run.
    @Test
    void testMelismaEndingAtALaterPairSurvivesAStartingSyllableThatSpansItsOwnUnion() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, UNION_SPANNING_SYLLABLE, Lyric.Extend.START);
        var host = crotchet();
        setCarrier(host, Lyric.Extend.CONTINUE);
        var middle = crotchet();
        var laterGrace = graceQuaver();
        setCarrier(laterGrace, Lyric.Extend.CONTINUE);
        var laterHost = crotchet();
        setCarrier(laterHost, Lyric.Extend.STOP);
        addToLine(grace, host, middle, laterGrace, laterHost);

        var widthSs = LYRIC_METRICS.lyricBoxWidthSs(UNION_SPANNING_SYLLABLE);
        var graceCol = graceColumn(grace, GRACE_X_SS, widthSs);
        var hostCol = normalColumn(host, PACKED_HOST_X_SS);
        var columns = List.of(
            graceCol,
            hostCol,
            normalColumn(middle, SPAN_MIDDLE_X_SS),
            graceColumn(laterGrace, SPAN_LATER_GRACE_X_SS),
            normalColumn(laterHost, SPAN_LATER_HOST_X_SS));

        // Precondition: the starting syllable spans its own pair, which is what suppresses a melisma
        // belonging to that pair — the melisma here belongs to a later one.
        assertThat(widthSs)
            .as("the starting syllable must span its own union to arm the suppression")
            .isGreaterThanOrEqualTo(hostCol.getNoteheadRightEdgeXSs() - GRACE_X_SS);

        var result = LyricLayoutBuilder.build(columns, LYRIC_METRICS, false, LINE_WIDTH_SS);

        var extenders = connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER);
        assertThat(extenders)
            .as("a melisma that outlives its starting pair must not be dropped with that pair's own")
            .hasSize(1);
        assertThat(extenders.getFirst().endXSs())
            .as("the melisma ends at the notehead of the host that actually carries the STOP")
            .isCloseTo(
                SPAN_LATER_HOST_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS,
                within(POSITION_TOLERANCE_SS));
        assertThat(result.hasTrailingContinuation())
            .as("the melisma is closed on this line, so nothing continues onto the next")
            .isFalse();
    }

    // (k) A lyric-bearing grace ending the line has no host to measure against: the union falls back to
    // the ideal hostless one, and with no host lyric the pair can carry no melisma of its own, so a
    // melisma the grace starts dangles onto the next line instead of closing at a host.
    @Test
    void testGraceEndingTheLineIsLaidOutAgainstTheHostlessUnion() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, UNION_SPANNING_SYLLABLE, Lyric.Extend.START);
        addToLine(grace);

        var widthSs = LYRIC_METRICS.lyricBoxWidthSs(UNION_SPANNING_SYLLABLE);
        var graceCol = graceColumn(grace, GRACE_X_SS, widthSs);
        var hostlessUnionWidthSs =
            HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(graceCol, null);

        // Precondition: the syllable spans the hostless union, so it takes the center-on-union branch.
        assertThat(widthSs)
            .as("the syllable must span the hostless union to exercise the centering path")
            .isGreaterThanOrEqualTo(hostlessUnionWidthSs);

        var result = LyricLayoutBuilder.build(List.of(graceCol), LYRIC_METRICS, false, LINE_WIDTH_SS);

        var graceBox = boxesOf(result, grace).getFirst();
        assertThat(graceBox.xSs())
            .as("with no host, the syllable centers on the grace's own extent plus the grace-host rest")
            .isCloseTo(
                GRACE_X_SS - (widthSs - hostlessUnionWidthSs) / 2, within(POSITION_TOLERANCE_SS));

        assertThat(connectorsOfKind(result, LyricConnectorLayout.Kind.DANGLING_EXTENDER))
            .as("a melisma with no host on this line continues onto the next")
            .hasSize(1);
        assertThat(result.hasTrailingContinuation()).isTrue();
    }

    // (l) The exact boundary of the spans-the-union rule: a syllable precisely as wide as the union
    // already ends at the host's notehead, which is where its melisma would have ended. Were the
    // comparison exclusive, the melisma would be drawn with zero length — a stray tick at the notehead.
    @Test
    void testGraceMelismaIsNotDrawnWhenTheSyllableExactlyMeetsTheHost() {
        var grace = graceQuaver();
        setLyric(grace, Lyric.Syllabic.SINGLE, GRACE_SYLLABLE, Lyric.Extend.START);
        var host = crotchet();
        setCarrier(host, Lyric.Extend.STOP);
        addToLine(grace, host);

        var hostCol = normalColumn(host, HOST_X_SS);
        // Verse 1 lays out the width measured onto the column rather than re-measuring the text, so
        // handing it the union itself puts the syllable exactly on the boundary — bit for bit, since
        // this is the same expression the builder measures the spaced union with.
        var unionWidthSs = hostCol.getNoteheadRightEdgeXSs() - GRACE_X_SS;
        var graceCol = graceColumn(grace, GRACE_X_SS, unionWidthSs);

        var result = LyricLayoutBuilder.build(
            List.of(graceCol, hostCol), LYRIC_METRICS, false, LINE_WIDTH_SS);

        assertThat(connectorsOfKind(result, LyricConnectorLayout.Kind.EXTENDER))
            .as("a syllable that exactly meets the host leaves the melisma no length to be drawn in")
            .isEmpty();

        var graceBox = boxesOf(result, grace).getFirst();
        assertThat(graceBox.xSs())
            .as("the syllable sits on the union exactly, overhanging neither side")
            .isCloseTo(GRACE_X_SS, within(POSITION_TOLERANCE_SS));
    }
}
