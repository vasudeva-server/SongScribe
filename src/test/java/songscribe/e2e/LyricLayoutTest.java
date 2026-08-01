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
package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.dom.Lyric;
import songscribe.layout.LyricBoxLayout;
import songscribe.ui.action.Actions;

/**
 * Rendering smoke test for the lyric layout pass: a note carrying a syllable must produce a
 * lyric box once the real {@link songscribe.layout.LayoutEngine} runs.
 * <p>
 * The geometry itself is covered by unit tests against {@code LyricLayoutBuilder} with mocked
 * metrics; what only the e2e pipeline exercises is the end-to-end path — a note inserted by a
 * real click, a real {@code LyricRenderMetrics} built from the document's installed lyrics
 * font, and the layout result the renderer consumes.
 */
class LyricLayoutTest extends E2ETest {

    private static final String SYLLABLE = "la";

    @BeforeEach
    void resetState() {
        resetSong();
    }

    @Test
    void testLyricBoxAppearsAfterInsertion() {
        // Insert one note through the real pipeline.
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        // effectiveElementCount excludes the song-owned terminal barline that every line
        // carries, so this is "exactly one note was inserted".
        assertThat(song().getLine(0).effectiveElementCount()).isEqualTo(1);

        // Attach a single, self-contained syllable to that note.
        GuiActionRunner.execute(() -> song().getLine(0).getElement(0).lyrics.add(
            new Lyric(1, SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false)));
        performLayout(0);

        // The real layout pass emits exactly one box for the syllable.
        var boxes = GuiActionRunner.execute(() -> {
            var lineComponent = scoreView().getLineComponent(0);
            if (lineComponent == null) {
                return List.<LyricBoxLayout>of();
            }

            var layoutResult = lineComponent.getLayoutResult();
            if (layoutResult == null) {
                return List.<LyricBoxLayout>of();
            }

            return layoutResult.getLyricBoxes(song().getLine(0).getElement(0));
        });

        assertThat(boxes).isNotNull();
        if (boxes == null) {
            return; // unreachable — satisfies NullAway after the assertThat above
        }

        assertThat(boxes).hasSize(1);
        var box = boxes.getFirst();
        assertThat(box.text()).isEqualTo(SYLLABLE);
        assertThat(box.verseIndex()).isEqualTo(1);
        assertThat(box.widthSs()).isGreaterThan(0.0);
    }
}
