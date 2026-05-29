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
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.font.DocumentFonts;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.SongLayoutMetrics;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class LineInvariantsTest extends UnitTest {

    private static final int FONT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);

    /**
     * A builder seeded with the required layout fields (set to minimal values that the
     * color/playing logic never reads) so each test only configures the state it cares about.
     */
    private static LineInvariants.Builder seededBuilder() {
        return LineInvariants.builder(new Song(), DocumentFonts.defaultsFromPrefs())
            .setLayoutResult(LayoutResult.builder().build())
            .setSongLayoutMetrics(new SongLayoutMetrics(0, 0, 0, 0, 0, 0, 0, 0))
            .setLyricRenderMetrics(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0, 0));
    }

    private static Line tiedLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addRangeElement(new Tie(line.getElement(0), line.getElement(2)));
        return line;
    }

    // Edit mode, not playing, not selected → Color.BLACK
    @Test
    void testDefaultReturnsBlack() {
        var invariants = seededBuilder().setEditMode(true).build();

        assertThat(invariants.getElementColor(0)).isEqualTo(Color.BLACK);
    }

    // Edit mode + element is in a tie with the playing note → playing color
    @Test
    void testElementInPlayingTieReturnsPlayingColor() {
        var invariants = seededBuilder()
            .setEditMode(true)
            .setCurrentLine(tiedLine())
            .setPlayingNoteIndex(0)
            .build();

        // Index 2 is in the [0, 2] tie but is not the playing note itself
        assertThat(invariants.getElementColor(2)).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // A playing grace note marks its element as playing
    @Test
    void testGraceNoteCountsAsPlaying() {
        var invariants = seededBuilder().setEditMode(true).setPlayingGraceNoteIndex(0).build();

        assertThat(invariants.isElementPlaying(0)).isTrue();
        assertThat(invariants.getElementColor(0)).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // Without a playing note, no element is in a playing tie
    @Test
    void testIsElementInPlayingTieFalseWithoutPlayingNote() {
        var invariants = seededBuilder().setEditMode(true).setCurrentLine(tiedLine()).build();

        assertThat(invariants.isElementInPlayingTie(2)).isFalse();
    }

    // An index that is neither the playing note nor grace note is not playing
    @Test
    void testIsElementPlayingFalseForUnrelatedIndex() {
        var invariants = seededBuilder().setEditMode(true).setPlayingNoteIndex(0).build();

        assertThat(invariants.isElementPlaying(0)).isTrue();
        assertThat(invariants.isElementPlaying(1)).isFalse();
    }

    // Not in edit mode → Color.BLACK regardless of playing/selection state
    @Test
    void testNotEditModeReturnsBlack() {
        var invariants = seededBuilder().setPlayingNoteIndex(0).build();

        assertThat(invariants.getElementColor(0)).isEqualTo(Color.BLACK);
    }

    // Edit mode + element is playing → playing color
    @Test
    void testPlayingElementReturnsPlayingColor() {
        var invariants = seededBuilder().setEditMode(true).setPlayingNoteIndex(0).build();

        assertThat(invariants.getElementColor(0)).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // Edit mode + element is selected → selectionColor
    @Test
    void testSelectedElementReturnsSelectionColor() {
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isElementSelected(0, 0)).thenReturn(true);

        var invariants = seededBuilder()
            .setEditMode(true)
            .setSelectionProvider(selectionProvider)
            .setSelectionColor(Color.RED)
            .build();

        assertThat(invariants.getElementColor(0)).isEqualTo(Color.RED);
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
}
