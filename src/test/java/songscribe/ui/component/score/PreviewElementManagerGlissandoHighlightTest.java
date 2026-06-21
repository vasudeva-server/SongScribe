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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;

/**
 * Tests for {@link PreviewElementManager#isGlissandoPreviewNote}, which marks the notes
 * a previewed glissando would connect to so they render in the preview color. A
 * connecting glissando highlights both the source note and the target note to its right;
 * a slide-out highlights only the source note.
 */
class PreviewElementManagerGlissandoHighlightTest extends PreviewElementManagerTestBase {

    private static final StaffElement.Glissando.Type CONNECTED = StaffElement.Glissando.Type.CONNECTED;
    private static final StaffElement.Glissando.Type SLIDE_OUT = StaffElement.Glissando.Type.SLIDE_OUT;

    private static final int LINE_INDEX = 0;
    private static final int OTHER_LINE_INDEX = 1;

    /** Insertion index sitting in the gap between the two notes added by {@link #addTwoNotes}. */
    private static final int X_INDEX_BETWEEN = 1;

    private void addTwoNotes() {
        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
        });
    }

    private void primeGlissandoPreview(StaffElement.Glissando.@Nullable Type type) {
        when(lc.getLineIndex()).thenReturn(LINE_INDEX);
        PreviewElementManager.setCurrentXIndex(X_INDEX_BETWEEN);
        PreviewElementManager.setCurrentGlissandoZone(type);
    }

    @Test
    void testConnectedHighlightsSourceAndTarget() {
        addTwoNotes();
        primeGlissandoPreview(CONNECTED);

        assertThat(PreviewElementManager.isGlissandoPreviewNote(LINE_INDEX, 0))
            .as("source note highlighted").isTrue();
        assertThat(PreviewElementManager.isGlissandoPreviewNote(LINE_INDEX, X_INDEX_BETWEEN))
            .as("target note highlighted").isTrue();
    }

    @Test
    void testSlideOutHighlightsOnlySource() {
        addTwoNotes();
        primeGlissandoPreview(SLIDE_OUT);

        assertThat(PreviewElementManager.isGlissandoPreviewNote(LINE_INDEX, 0))
            .as("source note highlighted").isTrue();
        assertThat(PreviewElementManager.isGlissandoPreviewNote(LINE_INDEX, X_INDEX_BETWEEN))
            .as("target note not highlighted for slide-out").isFalse();
    }

    @Test
    void testNoHighlightWhenNoZone() {
        addTwoNotes();
        primeGlissandoPreview(null);

        assertThat(PreviewElementManager.isGlissandoPreviewNote(LINE_INDEX, 0))
            .as("no highlight without a glissando zone").isFalse();
    }

    @Test
    void testNoHighlightOnDifferentLine() {
        addTwoNotes();
        primeGlissandoPreview(CONNECTED);

        assertThat(PreviewElementManager.isGlissandoPreviewNote(OTHER_LINE_INDEX, 0))
            .as("no highlight on a different line").isFalse();
    }

    @Test
    void testNoHighlightWhenSourceAlreadyHasSameGlissando() {
        addTwoNotes();
        line.getElement(0).setGlissando(CONNECTED);
        primeGlissandoPreview(CONNECTED);

        assertThat(PreviewElementManager.isGlissandoPreviewNote(LINE_INDEX, 0))
            .as("no highlight when source already carries this glissando").isFalse();
    }
}
