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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.jspecify.annotations.Nullable;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.TupletSpan;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.PlaybackController;

/**
 * Tests that tuplet spans are removed when elements within them are modified
 * or inserted via {@link PreviewElementManager} — specifically:
 * <ul>
 *   <li>{@code modifyExistingElement}: replacing a note with one of a different
 *       duration type or dot count removes any containing tuplet.</li>
 *   <li>{@code modifyExistingElement}: replacing a note with the same type and
 *       dot count (e.g., different pitch only) preserves the tuplet.</li>
 *   <li>{@code insertElement}: inserting a note into an existing tuplet removes it
 *       without a confirmation dialog.</li>
 * </ul>
 */
class PreviewElementManagerTupletTest extends UnitTest {

    private static final int TRIPLET_SIZE = 3;

    // Static mocks
    private MockedStatic<MessageCenter> messageCenterMock;
    private MockedStatic<EditModeManager> editModeMgrMock;
    private MockedStatic<Score> scoreMock;
    private MockedStatic<PlaybackController> playbackMock;

    // Instance mocks
    private LineComponent lc;
    private EditModeManager editModeManager;

    // Real objects
    private Composition composition;
    private Line line;

    @BeforeEach
    void setUp() throws Exception {
        // Construct before mocking MessageCenter so constructor subscriptions
        // reach the real bus.
        composition = new Composition();
        line = composition.getLine(0);

        messageCenterMock = mockStatic(MessageCenter.class);
        editModeMgrMock = mockStatic(EditModeManager.class);
        scoreMock = mockStatic(Score.class);
        playbackMock = mockStatic(PlaybackController.class);

        lc = mock(LineComponent.class);
        var score = mock(Score.class);
        editModeManager = mock(EditModeManager.class);

        when(lc.isEditMode()).thenReturn(true);
        when(lc.getScore()).thenReturn(score);
        when(lc.getLine()).thenReturn(line);
        when(score.getControl()).thenReturn(Control.MOUSE);
        when(score.getMode()).thenReturn(Mode.EDIT);

        editModeMgrMock.when(EditModeManager::getInstance).thenReturn(editModeManager);
        when(editModeManager.hasPreviewElement()).thenReturn(true);
        when(editModeManager.elementWasModified(any(Line.class), anyInt())).thenReturn(false);

        playbackMock.when(PlaybackController::isPlaying).thenReturn(false);

        // defaultUpperNote is called when the preview element's stem direction is auto
        scoreMock.when(() -> Score.defaultUpperNote(any())).thenReturn(true);

        setStaticField("currentPreviewLine", lc);
        setStaticField("currentStaffPosition", 0);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Reset static cursor state to avoid cross-test contamination
        setStaticField("currentPreviewLine", null);
        setStaticField("currentXIndex", -1);
        setStaticField("xPosSsMatchesElement", false);

        playbackMock.close();
        scoreMock.close();
        editModeMgrMock.close();
        messageCenterMock.close();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void setStaticField(String name, @Nullable Object value) throws Exception {
        Field field = PreviewElementManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private void addNotes(int count, ElementType type) {
        composition.withoutMutationTracking(() -> {
            for (int i = 0; i < count; i++) {
                line.addElement(type.newInstance());
            }
        });
    }

    private void addTriplet(int start, int end) {
        line.getTuplets().addSpan(new TupletSpan(start, end, TRIPLET_SIZE));
    }

    private void setPreviewElement(StaffElement element) {
        when(editModeManager.getPreviewElement()).thenReturn(element);
    }

    // -----------------------------------------------------------------------
    // modifyExistingElement
    // -----------------------------------------------------------------------

    @Nested
    class ModifyExistingElement {

        /**
         * Routing: xPosSsMatchesElement=true → modifyExistingElement at index 1.
         * Three quavers with a triplet [0..2]. Replacing index 1 with a crotchet
         * (different type) must remove the tuplet.
         */
        @Test
        void testDifferentDurationTypeRemovesTuplet() throws Exception {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            setStaticField("xPosSsMatchesElement", true);
            setStaticField("currentXIndex", 1);
            setPreviewElement(ElementType.CROTCHET.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.getTuplets().isEmpty()).isTrue();
        }

        /**
         * Same type (quaver) but dot count changes from 0 to 1.
         * The tuplet must be removed.
         */
        @Test
        void testDifferentDotCountRemovesTuplet() throws Exception {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            var dottedQuaver = ElementType.QUAVER.newInstance();
            dottedQuaver.setDotCount(1);

            setStaticField("xPosSsMatchesElement", true);
            setStaticField("currentXIndex", 1);
            setPreviewElement(dottedQuaver);

            PreviewElementManager.handleClick(lc);

            assertThat(line.getTuplets().isEmpty()).isTrue();
        }

        /**
         * Same type and same dot count (only pitch differs): tuplet must be preserved.
         */
        @Test
        void testSameDurationAndDotCountPreservesTuplet() throws Exception {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            setStaticField("xPosSsMatchesElement", true);
            setStaticField("currentXIndex", 1);
            setPreviewElement(ElementType.QUAVER.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.getTuplets().isEmpty()).isFalse();
            assertThat(line.getTuplets().findSpan(0)).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // insertElement
    // -----------------------------------------------------------------------

    @Nested
    class InsertElement {

        /**
         * Routing: xPosSsMatchesElement=false, currentXIndex < elementCount → insertElement.
         * Three quavers with a triplet [0..2]. Inserting at index 1 (inside the span)
         * must remove the tuplet without a confirmation dialog.
         */
        @Test
        void testInsertingIntoTupletRemovesIt() throws Exception {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            setStaticField("xPosSsMatchesElement", false);
            setStaticField("currentXIndex", 1);
            setPreviewElement(ElementType.QUAVER.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.getTuplets().isEmpty()).isTrue();
        }

        /**
         * Inserting after a tuplet (at an index beyond the tuplet's span)
         * must leave the tuplet intact.
         */
        @Test
        void testInsertingAfterTupletPreservesIt() throws Exception {
            addNotes(3, ElementType.QUAVER);
            // Tuplet covers only [0..1]; inserting at index 3 (after index 2) is outside
            addTriplet(0, 1);

            setStaticField("xPosSsMatchesElement", false);
            setStaticField("currentXIndex", 3);
            setPreviewElement(ElementType.QUAVER.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.getTuplets().isEmpty()).isFalse();
            assertThat(line.getTuplets().findSpan(0)).isNotNull();
        }
    }
}
