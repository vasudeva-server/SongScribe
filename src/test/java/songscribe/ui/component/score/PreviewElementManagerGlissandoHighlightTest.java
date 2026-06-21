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
    void testNoHighlightWhenSourceIndexNegative() {
        addTwoNotes();
        when(lc.getLineIndex()).thenReturn(LINE_INDEX);
        // Insertion point left of every note: sourceIndex = currentXIndex - 1 = -1.
        PreviewElementManager.setCurrentXIndex(0);
        PreviewElementManager.setCurrentGlissandoZone(CONNECTED);

        assertThat(PreviewElementManager.isGlissandoPreviewNote(LINE_INDEX, 0))
            .as("no highlight when the insertion point is left of all notes").isFalse();
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

    // -----------------------------------------------------------------------
    // GlissandoPreviewNotes.highlights — pure record logic, no preview state
    // -----------------------------------------------------------------------

    @Test
    void testHighlightsMatchesSourceAndTargetOnSameLineOnly() {
        var notes = new PreviewElementManager.GlissandoPreviewNotes(LINE_INDEX, 0, X_INDEX_BETWEEN);

        assertThat(notes.highlights(LINE_INDEX, 0)).as("source highlighted").isTrue();
        assertThat(notes.highlights(LINE_INDEX, X_INDEX_BETWEEN)).as("target highlighted").isTrue();
        assertThat(notes.highlights(LINE_INDEX, X_INDEX_BETWEEN + 1))
            .as("an element that is neither source nor target is not highlighted").isFalse();
        assertThat(notes.highlights(OTHER_LINE_INDEX, 0))
            .as("the source index on a different line is not highlighted").isFalse();
    }

    @Test
    void testHighlightsSlideOutNeverMatchesAbsentTarget() {
        // A slide-out carries no target (targetIndex == -1). A -1 element query must not match it.
        var slideOut = new PreviewElementManager.GlissandoPreviewNotes(LINE_INDEX, 0, -1);

        assertThat(slideOut.highlights(LINE_INDEX, 0)).as("source highlighted").isTrue();
        assertThat(slideOut.highlights(LINE_INDEX, -1))
            .as("a -1 query must not match the absent slide-out target").isFalse();
    }

    @Test
    void testNoneHighlightsNothing() {
        var none = PreviewElementManager.GlissandoPreviewNotes.NONE;

        assertThat(none.highlights(0, 0)).as("NONE never highlights a real element").isFalse();
        assertThat(none.highlights(-1, -1))
            .as("NONE never highlights even a query matching its own sentinels").isFalse();
    }

    // -----------------------------------------------------------------------
    // sourceAlreadyHasGlissando
    // -----------------------------------------------------------------------

    @Test
    void testSourceAlreadyHasGlissandoMatchesOnlySameType() {
        addTwoNotes();
        line.getElement(0).setGlissando(CONNECTED);

        assertThat(PreviewElementManager.sourceAlreadyHasGlissando(line, 0, CONNECTED))
            .as("matches when the source carries the same glissando type").isTrue();
        assertThat(PreviewElementManager.sourceAlreadyHasGlissando(line, 0, SLIDE_OUT))
            .as("does not match a different glissando type").isFalse();
        assertThat(PreviewElementManager.sourceAlreadyHasGlissando(line, 1, CONNECTED))
            .as("does not match a source with no glissando").isFalse();
    }

    @Test
    void testSourceAlreadyHasGlissandoNullTypeMatchesNothing() {
        addTwoNotes();
        line.getElement(0).setGlissando(CONNECTED);

        // A null type (no active zone) trivially matches nothing, even when the source has one.
        assertThat(PreviewElementManager.sourceAlreadyHasGlissando(line, 0, null))
            .as("a null query type never matches an existing glissando").isFalse();
    }
}
