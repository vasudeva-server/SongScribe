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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Font;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.SlideZone;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link SlidePreviewOverlay} (via its two concrete subclasses):
 * <ul>
 *   <li>T16 — every condition in {@link PreviewElementManager#shouldShowSlidePreviewOn} hides
 *       both slide overlays, including the {@code sourceAlreadyHasSlide} case.</li>
 *   <li>T17 — the tracked zone selects exactly one of the two overlays; the other stays hidden.</li>
 * </ul>
 */
class SlidePreviewOverlayTest extends UnitTest {

    private static final int LYRICS_FONT_POINT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, LYRICS_FONT_POINT_SIZE);
    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** Distinct staff positions so the source and target notes are not musically identical. */
    private static final int SOURCE_STAFF_POSITION = 0;
    private static final int TARGET_STAFF_POSITION = 2;

    /** currentXIndex tracks one past the source note; sourceIndex = currentXIndex - 1. */
    private static final int SOURCE_INDEX = 0;
    private static final int TARGET_INDEX = 1;

    /** Line width large enough that the solver never rejects the two-note fixture. */
    private static final double WIDE_LINE_SS = 100.0;

    private FakeOverlayHost host;
    private LineComponent lc;
    private Line domLine;
    private FallPreviewOverlay fallOverlay;
    private GlissandoPreviewOverlay glissandoOverlay;
    private MockedStatic<EditModeManager> editModeManagerMock;

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

        // A real Song, not a mutation-suspended mock: the layout engine reads real song state
        // (line width, key signature) through the line's own song reference.
        var song = new Song();
        // The default line width provider is 0.0 outside a running app (no page-size provider
        // installed), which would make the solver infeasible for even one note.
        song.withModification(() -> song.setLineWidthSs(WIDE_LINE_SS));
        lc.song = song;
        domLine = song.getLine(0);

        var source = ElementType.CROTCHET.newInstance();
        source.setStaffPosition(SOURCE_STAFF_POSITION);
        var target = ElementType.CROTCHET.newInstance();
        target.setStaffPosition(TARGET_STAFF_POSITION);
        song.withModification(() -> {
            domLine.addElement(source);
            domLine.addElement(target);
        });

        // A non-zero line index skips the first-line attribution measurement in
        // LineComponent.performLayout, which needs real font metrics this fixture doesn't set up.
        lc.setLine(domLine, 1);
        // A real layout, not an empty placeholder: the glissando geometry needs the source and
        // target notes to actually be positioned apart, which only a real column layout provides.
        lc.ensureLayout();

        fallOverlay = new FallPreviewOverlay(host);
        glissandoOverlay = new GlissandoPreviewOverlay(host);

        editModeManagerMock = mockStatic(EditModeManager.class);
        editModeManagerMock.when(EditModeManager::getPreviewElement)
            .thenReturn(ElementType.SLIDE.newInstance());

        PreviewElementManager.setCurrentPreviewLine(lc);
        PreviewElementManager.setCurrentXIndex(TARGET_INDEX);
        PreviewElementManager.setCurrentSlideZone(SlideZone.GLISSANDO);
    }

    @AfterEach
    void tearDown() {
        PreviewElementManager.setCurrentPreviewLine(null);
        PreviewElementManager.setCurrentXIndex(-1);
        PreviewElementManager.setCurrentSlideZone(null);
        editModeManagerMock.close();
    }

    @Nested
    class VisibilityGates {

        @Test
        void testBaselineWithEveryConditionSatisfiedIsVisible() {
            glissandoOverlay.previewDidChange(lc);

            assertThat(glissandoOverlay.isVisible())
                .as("every gate condition satisfied -> overlay shows")
                .isTrue();
        }

        @Test
        void testHiddenWhenLineDoesNotHaveTheTrackedPreview() {
            PreviewElementManager.setCurrentPreviewLine(null);

            glissandoOverlay.previewDidChange(lc);

            assertThat(glissandoOverlay.isVisible())
                .as("this line is not the tracked preview line -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenPreviewElementIsNotASlidePlaceholder() {
            editModeManagerMock.when(EditModeManager::getPreviewElement)
                .thenReturn(ElementType.CROTCHET.newInstance());

            glissandoOverlay.previewDidChange(lc);

            assertThat(glissandoOverlay.isVisible())
                .as("preview element is not the slide placeholder -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenNoSlideZoneIsTracked() {
            PreviewElementManager.setCurrentSlideZone(null);

            glissandoOverlay.previewDidChange(lc);

            assertThat(glissandoOverlay.isVisible())
                .as("shouldShowSlidePreview() false (no tracked zone) -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenTrackedZoneDoesNotMatchThisOverlay() {
            PreviewElementManager.setCurrentSlideZone(SlideZone.FALL);

            glissandoOverlay.previewDidChange(lc);

            assertThat(glissandoOverlay.isVisible())
                .as("tracked zone is FALL, this overlay answers to GLISSANDO -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenLineHasNoDomLine() {
            var unattachedLc = new LineComponent();
            host.add(unattachedLc);
            // Deliberately never call setLine: getLine() stays null.

            glissandoOverlay.previewDidChange(unattachedLc);

            assertThat(glissandoOverlay.isVisible())
                .as("null dom line -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenSourceAlreadyHasThisSlide() {
            var source = domLine.getElement(SOURCE_INDEX);
            source.setGlissando();

            glissandoOverlay.previewDidChange(lc);

            assertThat(glissandoOverlay.isVisible())
                .as("source note already carries a glissando -> hidden")
                .isFalse();
        }
    }

    @Nested
    class ZoneSelection {

        @Test
        void testGlissandoZoneShowsOnlyGlissandoOverlay() {
            PreviewElementManager.setCurrentSlideZone(SlideZone.GLISSANDO);

            glissandoOverlay.previewDidChange(lc);
            fallOverlay.previewDidChange(lc);

            assertThat(glissandoOverlay.isVisible())
                .as("GLISSANDO zone -> glissando overlay shows")
                .isTrue();
            assertThat(fallOverlay.isVisible())
                .as("GLISSANDO zone -> fall overlay stays hidden")
                .isFalse();
        }

        @Test
        void testFallZoneShowsOnlyFallOverlay() {
            PreviewElementManager.setCurrentSlideZone(SlideZone.FALL);

            glissandoOverlay.previewDidChange(lc);
            fallOverlay.previewDidChange(lc);

            assertThat(fallOverlay.isVisible())
                .as("FALL zone -> fall overlay shows")
                .isTrue();
            assertThat(glissandoOverlay.isVisible())
                .as("FALL zone -> glissando overlay stays hidden")
                .isFalse();
        }
    }
}
