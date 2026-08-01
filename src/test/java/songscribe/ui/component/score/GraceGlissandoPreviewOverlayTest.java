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
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.renderer.RenderingUtils;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link GraceGlissandoPreviewOverlay} — the connecting glissando drawn between a
 * grace note awaiting its host and the host-note insertion preview (refs #650):
 * <ul>
 *   <li>the visibility gate, {@link PreviewElementManager#graceHostPreviewSourceIndexOn};</li>
 *   <li>the ink spanning the gap between the grace note and the locked host slot, and tracking
 *       the preview element's staff position as the user moves it.</li>
 * </ul>
 */
class GraceGlissandoPreviewOverlayTest extends UnitTest {

    private static final int LYRICS_FONT_POINT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, LYRICS_FONT_POINT_SIZE);
    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** The grace note sits on the middle line, so hosts above and below it are both reachable. */
    private static final int GRACE_STAFF_POSITION = 0;

    /** A staff position above the grace note's (negative is higher-pitched). */
    private static final int HOST_STAFF_POSITION_ABOVE = -4;

    /** A staff position below the grace note's, the same distance away. */
    private static final int HOST_STAFF_POSITION_BELOW = 4;

    /**
     * The grace note follows a leading note, so a source index that ignored the one grace mode
     * reports and used the line's first element instead would land on the wrong note.
     */
    private static final int GRACE_INDEX = 1;

    /** The leading note's staff position — distinct from the grace note's, so the two are apart. */
    private static final int LEADING_STAFF_POSITION = 2;

    /** Line width large enough that the solver never rejects the fixture. */
    private static final double WIDE_LINE_SS = 100.0;

    private FakeOverlayHost host;
    private LineComponent lc;
    private Line domLine;
    private StaffElement graceNote;
    private StaffElement previewElement;
    private GraceGlissandoPreviewOverlay overlay;
    private GraceModeManager graceModeManagerMock;
    private MockedStatic<EditModeManager> editModeManagerMock;

    /** The X the host note would be inserted at, mirroring {@code getLockedInsertionXSs}. */
    private double lockedXSs;

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

        // A leading note so the grace note is not at index 0: the source index the overlay draws
        // from then has to come from grace mode rather than from a hardcoded first element.
        var leadingNote = ElementType.CROTCHET.newInstance();
        leadingNote.setStaffPosition(LEADING_STAFF_POSITION);

        graceNote = ElementType.GRACE_QUAVER.newInstance();
        graceNote.setStaffPosition(GRACE_STAFF_POSITION);
        song.withModification(() -> {
            domLine.addElement(leadingNote);
            domLine.addElement(graceNote);
        });

        // A non-zero line index skips the first-line attribution measurement in
        // LineComponent.performLayout, which needs real font metrics this fixture doesn't set up.
        lc.setLine(domLine, 1);
        // A real layout, not an empty placeholder: the glissando's source endpoint is the grace
        // note's laid-out position, which only a real column layout provides.
        lc.ensureLayout();

        var layoutResult = lc.getLayoutResult();

        assertThat(layoutResult).as("the fixture line must lay out for the ink to be resolvable").isNotNull();

        var graceColumn = layoutResult.getElementColumn(graceNote);

        assertThat(graceColumn).as("the grace note must have a laid-out column").isNotNull();

        // The same slot GraceModeManager.getLockedInsertionXSs() resolves: one fixed grace->host
        // gap past the grace note's column. The host is an undecorated crotchet, so its left
        // extent contributes nothing.
        lockedXSs = graceColumn.getXSs()
            + graceColumn.getRightExtentSs()
            + HorizontalSpacingCalculator.GRACE_HOST_REST_SS;

        previewElement = ElementType.CROTCHET.newInstance();
        previewElement.setStaffPosition(HOST_STAFF_POSITION_ABOVE);

        graceModeManagerMock = mock(GraceModeManager.class);
        when(graceModeManagerMock.hostPreviewGraceIndexOn(any())).thenReturn(GRACE_INDEX);
        when(graceModeManagerMock.getLockedInsertionXSs()).thenReturn(lockedXSs);

        editModeManagerMock = mockStatic(EditModeManager.class);
        editModeManagerMock.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManagerMock);
        editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(previewElement);
        editModeManagerMock.when(EditModeManager::isPreviewElementVisible).thenReturn(true);

        overlay = new GraceGlissandoPreviewOverlay(host);

        PreviewElementManager.setCurrentPreviewLine(lc);
    }

    @AfterEach
    void tearDown() {
        PreviewElementManager.setCurrentPreviewLine(null);
        editModeManagerMock.close();
    }

    /** Rebuilds the overlay's ink and returns its bounds, failing if it drew nothing. */
    private Rectangle2D rebuiltInkSs() {
        overlay.previewDidChange(lc);

        var inkSs = overlay.getLastInkBoundsSs();

        assertThat(inkSs).as("expected the preview glissando to produce ink").isNotNull();

        return inkSs;
    }

    @Nested
    class VisibilityGates {

        @Test
        void testBaselineWithEveryConditionSatisfiedIsVisible() {
            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("a grace note awaiting a visible host preview -> the connecting line shows")
                .isTrue();
        }

        @Test
        void testHiddenWhenGraceModeIsNotShowingAHostPreview() {
            when(graceModeManagerMock.hostPreviewGraceIndexOn(any())).thenReturn(-1);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("no host preview in progress -> nothing to connect to -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenLineHasNoDomLine() {
            var unattachedLc = new LineComponent();
            host.add(unattachedLc);
            // Deliberately never call setLine: getLine() stays null.

            overlay.previewDidChange(unattachedLc);

            assertThat(overlay.isVisible())
                .as("null dom line -> no source note to draw from -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenThereIsNoPreviewElement() {
            editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(null);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("no host ghost to run to -> hidden")
                .isFalse();
        }

        @Test
        void testHiddenWhenThePreviewElementIsNotVisible() {
            editModeManagerMock.when(EditModeManager::isPreviewElementVisible).thenReturn(false);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("the host ghost is hidden -> the line running to it must go with it")
                .isFalse();
        }

        @Test
        void testHiddenWhenLineDoesNotHaveTheTrackedPreview() {
            PreviewElementManager.setCurrentPreviewLine(null);

            overlay.previewDidChange(lc);

            assertThat(overlay.isVisible())
                .as("this line is not the tracked preview line -> hidden")
                .isFalse();
        }
    }

    @Nested
    class Ink {

        @Test
        void testInkSpansTheGapBetweenTheGraceNoteAndTheHostSlot() {
            var layoutResult = lc.getLayoutResult();

            assertThat(layoutResult).as("the fixture line must stay laid out").isNotNull();

            var inkSs = rebuiltInkSs();

            assertThat(inkSs.getMinX())
                .as("the line starts to the right of the grace note's own origin")
                .isGreaterThan(layoutResult.getElementXSs(graceNote));
            assertThat(inkSs.getMaxX())
                .as("the line stops short of the host preview's origin")
                .isLessThan(lockedXSs);
        }

        @Test
        void testInkTracksTheHostPreviewsStaffPosition() {
            var middleLineYSs = lc.getMiddleLineYSs();
            var graceCySs = RenderingUtils.noteStaffPositionToCoordinateSs(
                GRACE_STAFF_POSITION, middleLineYSs);

            previewElement.setStaffPosition(HOST_STAFF_POSITION_ABOVE);
            var aboveInkSs = rebuiltInkSs();

            previewElement.setStaffPosition(HOST_STAFF_POSITION_BELOW);
            var belowInkSs = rebuiltInkSs();

            // A negative staff position is higher-pitched, so it maps to a smaller Y.
            assertThat(aboveInkSs.getMinY())
                .as("a host above the grace note pulls the line's top edge above the grace note")
                .isLessThan(graceCySs - Staff.STAFF_POSITION_OFFSET_SS);
            assertThat(belowInkSs.getMaxY())
                .as("a host below the grace note pushes the line's bottom edge below it")
                .isGreaterThan(graceCySs + Staff.STAFF_POSITION_OFFSET_SS);
            assertThat(belowInkSs.getMinY())
                .as("moving the host from above to below the grace note moves the ink down")
                .isGreaterThan(aboveInkSs.getMinY());
        }
    }

    /**
     * The teardown {@code GraceModeManager.resetState()} depends on: it clears the state the gate
     * reads and then makes one {@link PreviewElementManager#previewElementDidChange()} call to take
     * the overlays down with it. Nothing else removes this overlay on the way out of grace mode, so
     * a commit would leave the preview line doubling the real glissando it just became, and an
     * abort would leave one running to a grace note that has already been removed.
     */
    @Nested
    class ManagerDrivenTeardown {

        @BeforeEach
        void installOverlays() {
            // previewElementDidChange() drives every installed overlay, not just this one, so the
            // sibling note-head preview needs the grace-mode state it reads to be coherent too:
            // it takes its X from the locked slot while grace mode is in progress, and from the
            // tracked insertion index once it is over.
            when(graceModeManagerMock.isInProgress()).thenReturn(true);
            PreviewElementManager.setCurrentXIndex(GRACE_INDEX + 1);
            PreviewElementManager.installOverlay(host);
        }

        @AfterEach
        void removeOverlays() {
            PreviewElementManager.resetOverlaysForTest();
            PreviewElementManager.setCurrentXIndex(-1);
        }

        @Test
        void testPreviewElementDidChangeHidesTheOverlayOnceGraceModeIsOver() {
            var installed = PreviewElementManager.getGraceGlissandoOverlay();

            assertThat(installed).as("expected installOverlay to install a grace glissando overlay").isNotNull();

            PreviewElementManager.previewElementDidChange();

            assertThat(installed.isVisible())
                .as("precondition: the connecting line shows while the host preview is up")
                .isTrue();

            // resetState() clears graceLineComponent and the state, which is exactly what turns
            // the gate off.
            when(graceModeManagerMock.hostPreviewGraceIndexOn(any())).thenReturn(-1);
            when(graceModeManagerMock.isInProgress()).thenReturn(false);

            PreviewElementManager.previewElementDidChange();

            assertThat(installed.isVisible())
                .as("grace mode is over -> the connecting line comes down with it")
                .isFalse();
        }
    }
}
