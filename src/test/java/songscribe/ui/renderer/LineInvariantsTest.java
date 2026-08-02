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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementLocation;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;

class LineInvariantsTest extends UnitTest {

    private static final int FONT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);
    private static final String BUILD_VALIDATION_MESSAGE =
        "LineInvariants requires layoutResult and lyricRenderMetrics to be set";

    /** Three notes in the time of two, undotted — the tuplet in the variant table. */
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int NO_DOTS = 0;

    /**
     * A builder seeded with the required layout fields (set to minimal values that the
     * color/playing logic never reads) so each test only configures the state it cares about.
     */
    private static LineInvariants.Builder seededBuilder() {
        return LineInvariants.builder(new Song(), DocumentFonts.defaultFonts())
            .setLayoutResult(LayoutResult.builder().build())
            .setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0, 0, 0));
    }

    private static Line tiedLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addRangeElement(new Tie(line.getElement(0), line.getElement(2)));
        return line;
    }

    // Not playing, not selected → Color.BLACK
    @Test
    void testDefaultReturnsBlack() {
        var invariants = seededBuilder().build();

        assertThat(invariants.getElementColor(0)).isEqualTo(Color.BLACK);
    }

    // Element is in a tie with the playing note → playing color
    @Test
    void testElementInPlayingTieReturnsPlayingColor() {
        var invariants = seededBuilder()
            .setCurrentLine(tiedLine())
            .setPlayingNoteIndex(0)
            .build();

        // Index 2 is in the [0, 2] tie but is not the playing note itself
        assertThat(invariants.getElementColor(2)).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // A playing grace note marks its element as playing
    @Test
    void testGraceNoteCountsAsPlaying() {
        var invariants = seededBuilder().setPlayingGraceNoteIndex(0).build();

        assertThat(invariants.isElementPlaying(0)).isTrue();
        assertThat(invariants.getElementColor(0)).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // Without a playing note, no element is in a playing tie
    @Test
    void testIsElementInPlayingTieFalseWithoutPlayingNote() {
        var invariants = seededBuilder().setCurrentLine(tiedLine()).build();

        assertThat(invariants.isElementInPlayingTie(2)).isFalse();
    }

    // An index that is neither the playing note nor grace note is not playing
    @Test
    void testIsElementPlayingFalseForUnrelatedIndex() {
        var invariants = seededBuilder().setPlayingNoteIndex(0).build();

        assertThat(invariants.isElementPlaying(0)).isTrue();
        assertThat(invariants.isElementPlaying(1)).isFalse();
    }

    // Element is playing → playing color
    @Test
    void testPlayingElementReturnsPlayingColor() {
        var invariants = seededBuilder().setPlayingNoteIndex(0).build();

        assertThat(invariants.getElementColor(0)).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // Element is selected → selectionColor
    @Test
    void testSelectedElementReturnsSelectionColor() {
        var line = lineWith(ElementType.CROTCHET);
        var builder = seededBuilder().setSelectionColor(Color.RED);
        RenderContextTestHelper.enableSelection(builder, line, 0);

        assertThat(builder.build().getElementColor(0)).isEqualTo(Color.RED);
    }

    // requireCurrentLine — returns line when set
    @Test
    void testRequireCurrentLineReturnsLineWhenSet() {
        var line = detachedLine();
        var invariants = seededBuilder().setCurrentLine(line).build();

        assertThat(invariants.requireCurrentLine()).isSameAs(line);
    }

    // requireCurrentLine — exits fatally when currentLine is null
    @Test
    void testRequireCurrentLineExitsWhenNull() {
        // setCurrentLine(null) leaves currentLine unset (it defaults to null)
        var invariants = seededBuilder().build();

        assertThatThrownBy(invariants::requireCurrentLine)
            .isInstanceOf(AssertionError.class);
    }

    // Element is hovered (preview-replacement target) → semi-transparent red
    @Test
    void testHoveredElementReturnsReplacedElementColor() {
        var hoveredLocation = new ElementLocation(0, 0);

        try (var previewMock = mockStatic(PreviewElementManager.class)) {
            previewMock.when(PreviewElementManager::getHoveredElementLocation)
                .thenReturn(hoveredLocation);

            var invariants = seededBuilder()
                .setLineIndex(0)
                .build();

            assertThat(invariants.getElementColor(0))
                .isEqualTo(LineInvariants.REPLACED_ELEMENT_COLOR);
        }
    }

    // ======================================================================
    // colorFor(HitTarget, int) — the target-keyed cascade
    // ======================================================================

    /**
     * One instance of every {@link HitTarget} variant, all hanging off the same two-note line.
     * The list is exhaustive by construction: adding a variant to the sealed interface without
     * adding it here leaves the new kind untested, which is the failure this table guards.
     */
    private static List<HitTarget> everyTargetVariant() {
        var line = detachedLine();
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.QUAVER.newInstance());
        var first = line.getElement(0);
        var second = line.getElement(1);

        var articulation = new Articulation(ArticulationType.STACCATO);
        first.addArticulation(articulation);
        var fermata = new FermataAttachment(first);
        first.addAttachment(fermata);

        return List.of(
            new HitTarget.Element(first),
            new HitTarget.Lyric(first, 0),
            new HitTarget.Slide(first),
            new HitTarget.GraceGlissando(first),
            new HitTarget.Hairpin(new Crescendo(first, second)),
            new HitTarget.Ending(new Ending(first, second)),
            new HitTarget.StaffLine(),
            new HitTarget.Articulation(articulation),
            new HitTarget.Attachment(fermata),
            new HitTarget.Accidental(first),
            new HitTarget.Tie(new Tie(first, second)),
            new HitTarget.Beam(new Beam(first, second)),
            new HitTarget.Trill(new Trill(first, second)),
            new HitTarget.Tuplet(new Tuplet(first, second, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES,
                ElementType.CROTCHET, NO_DOTS))
        );
    }

    @Test
    void testColorForReturnsSelectionColorForEverySelectedVariant() {
        for (var target : everyTargetVariant()) {
            var selectionProvider = mock(LineComponent.SelectionProvider.class);
            when(selectionProvider.isSelected(target, 0)).thenReturn(true);

            var invariants = seededBuilder()
                .setSelectionProvider(selectionProvider)
                .build();

            assertThat(invariants.colorFor(target, LineInvariants.NO_ELEMENT_INDEX))
                .as("selected %s", target.getClass().getSimpleName())
                .isEqualTo(ScoreView.getSelectionColor());
        }
    }

    @Test
    void testColorForReturnsBlackForEveryUnselectedVariant() {
        var invariants = seededBuilder()
            .setSelectionProvider(mock(LineComponent.SelectionProvider.class))
            .build();

        for (var target : everyTargetVariant()) {
            assertThat(invariants.colorFor(target, LineInvariants.NO_ELEMENT_INDEX))
                .as("unselected %s", target.getClass().getSimpleName())
                .isEqualTo(Color.BLACK);
        }
    }

    @Test
    void testColorForWithoutSelectionProviderReturnsBlackForEveryVariant() {
        var invariants = seededBuilder().build();

        for (var target : everyTargetVariant()) {
            assertThat(invariants.colorFor(target, LineInvariants.NO_ELEMENT_INDEX))
                .as("%s with no selection provider", target.getClass().getSimpleName())
                .isEqualTo(Color.BLACK);
        }
    }

    // Precedence, spelled out for Element: playing beats selected.
    @Test
    void testColorForElementPlayingBeatsSelected() {
        var line = tiedLine();
        var target = new HitTarget.Element(line.getElement(0));
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(target, 0)).thenReturn(true);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setSelectionProvider(selectionProvider)
            .setPlayingNoteIndex(0)
            .build();

        assertThat(invariants.colorFor(target, 0)).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // Precedence, spelled out for Element: selected beats hovered.
    @Test
    void testColorForElementSelectedBeatsHovered() {
        var line = tiedLine();
        var target = new HitTarget.Element(line.getElement(0));
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(target, 0)).thenReturn(true);

        try (var previewMock = mockStatic(PreviewElementManager.class)) {
            previewMock.when(PreviewElementManager::getHoveredElementLocation)
                .thenReturn(new ElementLocation(0, 0));

            var invariants = seededBuilder()
                .setCurrentLine(line)
                .setSelectionProvider(selectionProvider)
                .build();

            assertThat(invariants.colorFor(target, 0)).isEqualTo(ScoreView.getSelectionColor());
        }
    }

    // The hovered highlight reaches Element and stops there.
    @Test
    void testColorForHoverAppliesToElementButNotToItsDecorations() {
        var line = tiedLine();
        var note = line.getElement(0);
        var articulation = new Articulation(ArticulationType.STACCATO);
        note.addArticulation(articulation);

        try (var previewMock = mockStatic(PreviewElementManager.class)) {
            previewMock.when(PreviewElementManager::getHoveredElementLocation)
                .thenReturn(new ElementLocation(0, 0));

            var invariants = seededBuilder()
                .setCurrentLine(line)
                .build();

            assertThat(invariants.colorFor(new HitTarget.Element(note), 0))
                .isEqualTo(LineInvariants.REPLACED_ELEMENT_COLOR);
            assertThat(invariants.colorFor(new HitTarget.Articulation(articulation), 0))
                .as("hover is a property of the note, not of what hangs off it")
                .isEqualTo(Color.BLACK);
        }
    }

    // Playback reaches Element and Accidental and stops there.
    @Test
    void testColorForPlaybackAppliesToTheNoteAndItsAccidentalOnly() {
        var line = tiedLine();
        var note = line.getElement(0);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(0)
            .build();

        assertThat(invariants.colorFor(new HitTarget.Element(note), 0))
            .isEqualTo(ScoreView.getPlayingNoteColor());
        assertThat(invariants.colorFor(new HitTarget.Accidental(note), 0))
            .isEqualTo(ScoreView.getPlayingNoteColor());
        assertThat(invariants.colorFor(new HitTarget.Slide(note), 0))
            .as("a slide does not take the playing note's color from colorFor")
            .isEqualTo(Color.BLACK);
    }

    // Builder.build() — throws IllegalStateException when layoutResult is null
    @Test
    void testBuildThrowsWhenLayoutResultNull() {
        var builder = LineInvariants.builder(new Song(), DocumentFonts.defaultFonts())
            .setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0, 0, 0));

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(BUILD_VALIDATION_MESSAGE);
    }

    // Builder.build() — throws IllegalStateException when lyricRenderMetrics is null
    @Test
    void testBuildThrowsWhenLyricRenderMetricsNull() {
        var builder = LineInvariants.builder(new Song(), DocumentFonts.defaultFonts())
            .setLayoutResult(LayoutResult.builder().build());

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(BUILD_VALIDATION_MESSAGE);
    }

    // getLyricConnectorColor() — sourceElementIndex < 0 → Color.BLACK
    @Test
    void testGetLyricConnectorColorNegativeSourceIndexReturnsBlack() {
        var invariants = seededBuilder().build();

        assertThat(invariants.getLyricConnectorColor(-1, 1)).isEqualTo(Color.BLACK);
    }

    // getLyricConnectorColor() — no current line → delegates to getElementColor()
    @Test
    void testGetLyricConnectorColorNullLineReturnsElementColor() {
        // No line set; playing note → playing color should propagate
        var invariants = seededBuilder()
            .setPlayingNoteIndex(0)
            .build();

        // currentLine is null, so falls through to getElementColor(0) which returns playing color
        assertThat(invariants.getLyricConnectorColor(0, 1))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // getLyricConnectorColor() — line present + anchor playing → playing color
    @Test
    void testGetLyricConnectorColorAnchorPlayingReturnsPlayingColor() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(0)
            .build();

        assertThat(invariants.getLyricConnectorColor(0, 1))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // isLyricSpanPlaying() — anchor note is playing → true
    @Test
    void testIsLyricSpanPlayingWhenAnchorIsPlaying() {
        var line = lyricLine();
        var element = line.getElement(0);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(0)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // isLyricSpanPlaying() — melisma START anchor with carrier playing after it → true
    @Test
    void testIsLyricSpanPlayingForMelismaExtender() {
        var line = melismaLine();
        var element = line.getElement(0);

        // Index 1 is the STOP carrier; index 0 is the START anchor
        // Playing index 1 should keep index 0's lyric highlighted
        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(1)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // isLyricSpanPlaying() — melisma spanning CONTINUE carriers stays highlighted on every
    // carrier, not just the first (regression test for issue #364)
    @Test
    void testIsLyricSpanPlayingForMultiCarrierMelisma() {
        var line = multiCarrierMelismaLine();
        var anchor = line.getElement(0);

        // Anchor at 0 (START), carriers CONTINUE at 1 and 2, STOP at 3, new word at 4.
        // Every carrier index must keep the anchor's lyric highlighted.
        for (var playingIndex = 1; playingIndex <= 3; playingIndex++) {
            var invariants = seededBuilder()
                .setCurrentLine(line)
                .setPlayingNoteIndex(playingIndex)
                .build();

            assertThat(invariants.getLyricColor(0, anchor, 1))
                .as("carrier index %d should keep the melisma anchor highlighted", playingIndex)
                .isEqualTo(ScoreView.getPlayingNoteColor());
        }

        // The span ends at the STOP carrier (index 3): playing the new word at index 4
        // must leave the anchor unhighlighted.
        var pastSpan = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(4)
            .build();

        assertThat(pastSpan.getLyricColor(0, anchor, 1))
            .as("note past the STOP carrier must not highlight the anchor")
            .isEqualTo(Color.BLACK);

        // A CONTINUE carrier is not itself a forward-extending anchor: querying the
        // carrier at index 1 while a later carrier plays must not highlight it.
        var carrier = line.getElement(1);
        var playingLaterCarrier = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(3)
            .build();

        assertThat(playingLaterCarrier.getLyricColor(1, carrier, 1))
            .as("a CONTINUE carrier must not be treated as its own anchor span")
            .isEqualTo(Color.BLACK);
    }

    // isLyricSpanPlaying() — BEGIN/MIDDLE syllable with unlyriced note playing → true
    @Test
    void testIsLyricSpanPlayingForBeginMiddleContiguous() {
        var line = beginMiddleLine();
        var element = line.getElement(0);

        // Index 1 has no lyric; playing note at 1 is inside the BEGIN anchor's span
        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(1)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // isLyricSpanPlaying() — playing note is past span boundary → false
    @Test
    void testIsLyricSpanPlayingFalseWhenPlayingPastSpanEnd() {
        var line = beginMiddleLine();
        // Add a third element with a new syllable, terminating the span at index 1
        line.addElement(ElementType.CROTCHET.newInstance());
        var thirdElement = line.getElement(2);
        thirdElement.setLyricForVerse(1, Lyric.Syllabic.END, false, "do", Lyric.Extend.NONE);

        var element = line.getElement(0);

        // Now the BEGIN anchor's span ends at index 1 (the text-bearing END syllable is at 2,
        // so spanEnd = 2 - 1 = 1). Playing at index 2 is outside.
        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(2)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1))
            .isEqualTo(Color.BLACK);
    }

    // isLyricSpanPlaying() — no lyric on element → false
    @Test
    void testIsLyricSpanPlayingFalseWhenNoLyricOnAnchor() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());

        var element = line.getElement(0);
        // element has no lyric at verse 1

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(1)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1))
            .isEqualTo(Color.BLACK);
    }

    // getLyricColor() — lyric is selected → selectionColor
    @Test
    void testGetLyricColorSelectedLyricReturnsSelectionColor() {
        var line = lyricLine();
        var element = line.getElement(0);

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Lyric(element, 1), 0)).thenReturn(true);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setSelectionProvider(selectionProvider)
            .setSelectionColor(Color.BLUE)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1)).isEqualTo(Color.BLUE);
    }

    // isLyricSpanPlaying() — CONTINUE carrier mid-melisma → span includes it
    @Test
    void testIsLyricSpanPlayingForMelismaContinueCarrier() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setLyricForVerse(
            1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        line.getElement(1).setLyricForVerse(
            1, null, false, null, Lyric.Extend.CONTINUE);
        // index 2 has no lyric — span runs to end of line

        var element = line.getElement(0);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(1)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // isElementPlaying() — negative index is never playing
    @Test
    void testIsElementPlayingFalseForNegativeIndex() {
        var invariants = seededBuilder().setPlayingNoteIndex(0).build();

        assertThat(invariants.isElementPlaying(-1)).isFalse();
    }

    // isLyricSpanPlaying() — MIDDLE syllable with unlyriced note playing → true
    @Test
    void testIsLyricSpanPlayingForMiddleSyllable() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setLyricForVerse(
            1, Lyric.Syllabic.MIDDLE, false, "lo", Lyric.Extend.NONE);
        // index 1 intentionally left without a lyric

        var element = line.getElement(0);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(1)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // isLyricSpanPlaying() — SINGLE syllable, playing note is after it → not part of span
    @Test
    void testIsLyricSpanPlayingFalseForSingleSyllableWithPlayingAfter() {
        var line = lyricLine(); // SINGLE syllable at index 0
        line.addElement(ElementType.CROTCHET.newInstance());

        var element = line.getElement(0);

        // Playing note at index 1 is past the anchor, but SINGLE does not extend forward
        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(1)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1)).isEqualTo(Color.BLACK);
    }

    // isLyricSpanPlaying() — playing note is at or before anchor index → false
    @Test
    void testIsLyricSpanPlayingFalseWhenPlayingAtOrBeforeAnchor() {
        // Place the BEGIN anchor at index 1; playing note is at 0 (before the anchor).
        // isLyricSpanPlaying guards playingNoteIndex > anchorIndex, so returns false.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(1).setLyricForVerse(
            1, Lyric.Syllabic.BEGIN, false, "hi", Lyric.Extend.NONE);

        var element = line.getElement(1);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setPlayingNoteIndex(0)
            .build();

        assertThat(invariants.getLyricColor(1, element, 1)).isEqualTo(Color.BLACK);
    }

    // getLyricColor() — selectionProvider present but lyric not selected → Color.BLACK
    @Test
    void testGetLyricColorSelectionProviderNotSelectedReturnsBlack() {
        var line = lyricLine();
        var element = line.getElement(0);

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Lyric(element, 1), 0)).thenReturn(false);

        var invariants = seededBuilder()
            .setCurrentLine(line)
            .setSelectionProvider(selectionProvider)
            .setSelectionColor(Color.BLUE)
            .build();

        assertThat(invariants.getLyricColor(0, element, 1)).isEqualTo(Color.BLACK);
    }

    /**
     * A line with two notes: index 0 has a melisma-START lyric, index 1 has a STOP carrier.
     */
    private static Line melismaLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setLyricForVerse(
            1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        line.getElement(1).setLyricForVerse(
            1, null, false, null, Lyric.Extend.STOP);
        return line;
    }

    /**
     * A line with five notes spanning a melisma: index 0 has a melisma-START lyric,
     * indices 1 and 2 are CONTINUE carriers, index 3 is the STOP carrier, and index 4
     * begins a new text-bearing syllable just past the melisma boundary.
     */
    private static Line multiCarrierMelismaLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setLyricForVerse(
            1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
        line.getElement(1).setLyricForVerse(
            1, null, false, null, Lyric.Extend.CONTINUE);
        line.getElement(2).setLyricForVerse(
            1, null, false, null, Lyric.Extend.CONTINUE);
        line.getElement(3).setLyricForVerse(
            1, null, false, null, Lyric.Extend.STOP);
        line.getElement(4).setLyricForVerse(
            1, Lyric.Syllabic.SINGLE, false, "next", Lyric.Extend.NONE);
        return line;
    }

    /**
     * A line with two notes: index 0 has a BEGIN lyric, index 1 has no lyric
     * (simulates the gap between a BEGIN/MIDDLE anchor and its next text syllable).
     */
    private static Line beginMiddleLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setLyricForVerse(
            1, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);
        // index 1 intentionally left without a lyric
        return line;
    }

    /**
     * A line with one note that has a single-syllable lyric (no span extension).
     */
    private static Line lyricLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getElement(0).setLyricForVerse(
            1, Lyric.Syllabic.SINGLE, false, "la", Lyric.Extend.NONE);
        return line;
    }
}
