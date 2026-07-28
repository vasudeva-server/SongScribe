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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link PreviewElementOverlay}:
 * <ul>
 *   <li>T14 — every condition in {@link PreviewElementManager#shouldShowPreviewOn} hides the
 *       overlay when violated, with every other condition satisfied.</li>
 *   <li>T15 — the x-position source switches between the grace-mode locked X and the calculated
 *       insertion X.</li>
 * </ul>
 */
class PreviewElementOverlayTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;
    private static final int LYRICS_FONT_POINT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, LYRICS_FONT_POINT_SIZE);
    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** An arbitrary locked grace-mode X, distinct from the calculated insertion X below. */
    private static final double GRACE_LOCKED_X_SS = 42.0;

    private FakeOverlayHost host;
    private LineComponent lc;
    private PreviewElementOverlay overlay;
    private MockedStatic<EditModeManager> editModeManagerMock;
    private GraceModeManager graceModeManagerMock;

    @BeforeEach
    void setUp() {
        host = new FakeOverlayHost();
        lc = new LineComponent();
        host.add(lc);

        var scoreView = mock(ScoreView.class);
        when(scoreView.getMode()).thenReturn(Mode.EDIT);
        when(scoreView.getActiveLyricEditor()).thenReturn(null);
        when(scoreView.getLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
        when(scoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(scoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        lc.setScoreView(scoreView);
        lc.song = new Song();

        // Empty line: LayoutResult.calculateInsertionXSs takes its deterministic
        // empty-line branch regardless of the tracked index or mouse X.
        lc.setLine(detachedLine(), 0);
        lc.layoutResult = LayoutResult.builder().build();
        lc.layoutDirty = false;

        overlay = new PreviewElementOverlay(host);

        editModeManagerMock = mockStatic(EditModeManager.class);
        graceModeManagerMock = mock(GraceModeManager.class);
        when(graceModeManagerMock.isInProgress()).thenReturn(false);
        when(graceModeManagerMock.getGraceLineComponent()).thenReturn(null);
        editModeManagerMock.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManagerMock);

        // Baseline: every gate condition satisfied, so the overlay would show.
        editModeManagerMock.when(EditModeManager::isPreviewElementVisible).thenReturn(true);
        editModeManagerMock.when(EditModeManager::getPreviewElement)
            .thenReturn(ElementType.CROTCHET.newInstance());

        PreviewElementManager.setCurrentPreviewLine(lc);
        PreviewElementManager.setCurrentXIndex(0);
        PreviewElementManager.setCurrentStaffPosition(0);
    }

    @AfterEach
    void tearDown() {
        PreviewElementManager.setCurrentPreviewLine(null);
        PreviewElementManager.setCurrentXIndex(-1);
        editModeManagerMock.close();
    }

    @Nested
    class VisibilityGates {

        @Test
        void testBaselineWithEveryConditionSatisfiedIsVisible() {
            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("every gate condition satisfied -> overlay shows")
                .isTrue();
        }

        @Test
        void testHiddenWhenLineDoesNotHaveTheTrackedPreview() {
            // "no preview element" gate: this line is not the one the manager is tracking.
            PreviewElementManager.setCurrentPreviewLine(null);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("PreviewElementManager.hasPreviewElement(lc) is false -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenInSelectMode() {
            when(lc.getScoreView().getMode()).thenReturn(Mode.SELECT);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("Mode.SELECT -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenLayoutResultIsNull() {
            lc.layoutResult = null;

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("null layoutResult -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenPreviewElementIsNull() {
            editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(null);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("null preview element -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenPreviewElementIsNotVisible() {
            editModeManagerMock.when(EditModeManager::isPreviewElementVisible).thenReturn(false);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("!isPreviewElementVisible() -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenForSlidePlaceholder() {
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.SLIDE.newInstance());

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("a slide placeholder has no note head -> hidden (the slide overlays draw instead)")
                .isFalse();
        }
    }

    @Nested
    class XPositionSource {

        /**
         * Rebuilds the overlay's ink and returns its bounds, failing loudly if nothing was
         * recorded — every test in this group configures a real notehead preview element, so a
         * null result means the fixture itself is broken, not the behavior under test.
         */
        private Rectangle2D requireBounds() {
            overlay.previewDidChange(lc);
            var bounds = overlay.getInkBoundsSs();

            if (bounds == null) {
                throw new AssertionError("expected non-null preview ink bounds");
            }

            return bounds;
        }

        /**
         * The two X sources produce the same relative ink (the recorded display list depends only
         * on the element's configuration, not on which X source translates it), so the difference
         * between the two runs' left edges must equal exactly the difference between the two X
         * sources.
         */
        @Test
        void testXSwitchesBetweenGraceLockedAndCalculatedInsertion() {
            var nonGraceBounds = requireBounds();

            when(graceModeManagerMock.isInProgress()).thenReturn(true);
            when(graceModeManagerMock.getLockedInsertionXSs()).thenReturn(GRACE_LOCKED_X_SS);

            var graceBounds = requireBounds();

            var domLine = lc.getLine();

            if (domLine == null) {
                throw new AssertionError("test setup did not attach a line");
            }

            var calculatedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(domLine);

            assertThat(graceBounds.getMinX() - nonGraceBounds.getMinX())
                .as("switching to grace mode shifts the ink by exactly (lockedX - calculatedX)")
                .isCloseTo(GRACE_LOCKED_X_SS - calculatedXSs, within(TOLERANCE));
        }
    }
}
