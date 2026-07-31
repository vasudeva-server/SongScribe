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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.SlideZone;
import songscribe.dom.Song;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.engraving.Staff;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.component.ScoreView;
import songscribe.ui.playback.PlaybackController;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PasteModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for what actually drives {@link PreviewElementOverlay} and {@link FallPreviewOverlay}
 * through {@link PreviewElementManager} — the five former repaint sites inside
 * {@code trackMouse}/{@code handleClick} (T19), the three mode-driven restore/clear paths (T19),
 * rebuild re-targeting without an intervening mouse event (T20), and the position-vs-configuration
 * split inside {@code trackMouse} (T22).
 * <p>
 * Uses a real {@link LineComponent} (package-private field injection, mirroring
 * {@link PreviewElementOverlayTest}) parented to a real {@link FakeOverlayHost} so the installed
 * overlays' {@code isVisible()}/{@code getBounds()} reflect genuine Swing state rather than a
 * mocked host that always reports "hidden".
 */
class PreviewElementManagerOverlayTest extends UnitTest {

    /** A mouse Y that resolves to staff position 0 (on the middle line — no ledger lines). */
    private static final int ON_STAFF_Y_PX = 0;

    private static final double FIRST_INSERTION_X_SS = 5.0;
    private static final double SECOND_INSERTION_X_SS = 15.0;

    private static final int LYRICS_FONT_POINT_SIZE = 12;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, LYRICS_FONT_POINT_SIZE);
    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /**
     * A {@link LineComponent} that can report a mouse position without being realized on
     * screen. {@code Container.getMousePosition()} always returns null for an unshown
     * component, which would defeat the mode-driven restore tests: {@link
     * PreviewElementManager#restorePreviewElement} bails out immediately when it does.
     */
    private static final class TestLineComponent extends LineComponent {
        @Nullable
        private Point mousePositionForTest;

        void setMousePositionForTest(@Nullable Point point) {
            mousePositionForTest = point;
        }

        @Override
        public @Nullable Point getMousePosition() {
            return mousePositionForTest;
        }
    }

    private FakeOverlayHost host;
    private TestLineComponent lc;
    private Song song;
    private Line line;
    private LayoutResult layoutResult;
    private MockedStatic<EditModeManager> editModeManagerMock;
    private MockedStatic<PlaybackController> playbackMock;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        host = new FakeOverlayHost();

        song = new Song();
        line = song.getLine(0);

        lc = new TestLineComponent();
        host.add(lc);

        var scoreView = mock(ScoreView.class);
        when(scoreView.getMode()).thenReturn(Mode.EDIT);
        when(scoreView.getActiveLyricEditor()).thenReturn(null);
        when(scoreView.getLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
        when(scoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(scoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        lc.setScoreView(scoreView);
        lc.song = song;
        lc.setLine(line, 0);

        layoutResult = mock(LayoutResult.class);
        when(layoutResult.findElementAtXSs(anyDouble(), eq(line))).thenReturn(-1);
        // Tied to the actual xIndex argument (rather than a fixed call-order sequence) so it
        // stays correct regardless of how many times an unrelated overlay re-derives its X.
        when(layoutResult.calculateInsertionXSs(anyInt(), anyDouble(), any(), eq(line), eq(false)))
            .thenAnswer(invocation -> {
                int xIndex = invocation.getArgument(0);
                return xIndex == 0 ? FIRST_INSERTION_X_SS : SECOND_INSERTION_X_SS;
            });
        lc.layoutResult = layoutResult;
        lc.layoutDirty = false;

        editModeManagerMock = mockStatic(EditModeManager.class);
        playbackMock = mockStatic(PlaybackController.class);
        messageCenterMock = mockStatic(MessageCenter.class);
        playbackMock.when(PlaybackController::isPlaying).thenReturn(false);

        var graceModeManagerMock = mock(GraceModeManager.class);
        // -1 means "no host preview on this line". An unstubbed mock would answer 0, which is a
        // valid grace-note index, and would switch the connecting glissando on for every test here.
        when(graceModeManagerMock.hostPreviewGraceIndexOn(any())).thenReturn(-1);
        editModeManagerMock.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManagerMock);
        editModeManagerMock.when(EditModeManager::hasPreviewElement).thenReturn(true);
        editModeManagerMock.when(EditModeManager::isPreviewElementVisible).thenReturn(true);
        editModeManagerMock.when(() -> EditModeManager.elementWasModified(any(), anyInt())).thenReturn(false);
        editModeManagerMock.when(EditModeManager::getPreviewElement)
            .thenReturn(ElementType.CROTCHET.newInstance());

        PreviewElementManager.installOverlay(host);

        // A freshly constructed JComponent defaults to isVisible() == true (Swing's own
        // default, not a "shown" signal from this manager). Run one real bounds pass with no
        // target set — mirroring the host's first validateTree() in production — so every
        // test starts from a genuinely hidden overlay instead of that construction default.
        var freshOverlay = PreviewElementManager.getOverlay();

        if (freshOverlay != null) {
            freshOverlay.updateBounds();
        }

        var freshFallOverlay = PreviewElementManager.getFallOverlay();

        if (freshFallOverlay != null) {
            freshFallOverlay.updateBounds();
        }
    }

    @AfterEach
    void tearDown() {
        PreviewElementManager.resetOverlaysForTest();
        PreviewElementManager.setCurrentPreviewLine(null);
        PreviewElementManager.setCurrentXIndex(-1);
        PreviewElementManager.setXPosSsMatchesElement(false);
        PreviewElementManager.setCurrentSlideZone(null);
        PreviewElementManager.clearPendingTempoPrompt();
        messageCenterMock.close();
        playbackMock.close();
        editModeManagerMock.close();
    }

    private static MouseEvent mouseEvent(int xPx, int yPx, boolean altDown) {
        var e = mock(MouseEvent.class);
        when(e.getX()).thenReturn(xPx);
        when(e.getY()).thenReturn(yPx);
        when(e.isAltDown()).thenReturn(altDown);
        return e;
    }

    /** The view-pixel Y that {@code trackMouse} resolves to {@code targetSp}, at IDENTITY zoom. */
    private static int yPxForStaffPosition(int targetSp) {
        return (int) Math.round(ScaleContext.ssToPx(Staff.spToSs(targetSp)));
    }

    // -------------------------------------------------------------------------
    // T19 — the five former repaint sites
    // -------------------------------------------------------------------------

    @Test
    void testTrackMouseGeneralCaseShowsOverlay() {
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));

        var overlay = PreviewElementManager.getOverlay();

        assertThat(overlay).isNotNull();

        if (overlay == null) {
            return;
        }

        assertThat(overlay.getTargetLine())
            .as("trackMouse's general case anchors the overlay to the hovered line")
            .isSameAs(lc);
        assertThat(overlay.isVisible())
            .as("a plain note-head preview over open space shows the overlay")
            .isTrue();
    }

    @Test
    void testAltKeyClearsOverlay() {
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        // Real mouse-entered always precedes mouse-moved; restorePreviewElement (the
        // mode-driven restore path below) reads currentMouseLine, which only mouseEnteredLine
        // sets.
        PreviewElementManager.mouseEnteredLine(lc);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));
        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        assertThat(overlay.isVisible())
            .as("precondition: the preview overlay must be visible before the change under test")
            .isTrue();

        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, true));

        assertThat(overlay.isVisible())
            .as("clearPreviewElement (alt-key site) must hide the overlay")
            .isFalse();
    }

    @Test
    void testTrackMouseLineChangedMovesOverlayToOtherLine() {
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));

        // A second, independently laid-out line: same fixture shape as lc.
        var otherLine = new Line(song);
        song.addLine(otherLine);
        var otherLc = new LineComponent();
        host.add(otherLc);
        otherLc.setScoreView(lc.getScoreView());
        otherLc.song = song;
        otherLc.setLine(otherLine, 1);

        var otherLayoutResult = mock(LayoutResult.class);
        when(otherLayoutResult.findElementAtXSs(anyDouble(), eq(otherLine))).thenReturn(-1);
        when(otherLayoutResult.findInsertionIndex(anyDouble(), eq(otherLine))).thenReturn(0);
        when(otherLayoutResult.calculateInsertionXSs(anyInt(), anyDouble(), any(), eq(otherLine), eq(false)))
            .thenReturn(FIRST_INSERTION_X_SS);
        otherLc.layoutResult = otherLayoutResult;
        otherLc.layoutDirty = false;

        PreviewElementManager.trackMouse(otherLc, mouseEvent(0, ON_STAFF_Y_PX, false));

        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        assertThat(overlay.getTargetLine())
            .as("hovering a different line re-anchors the overlay to it")
            .isSameAs(otherLc);
        assertThat(overlay.isVisible())
            .as("precondition: the preview overlay must be visible before the change under test")
            .isTrue();
    }

    @Test
    void testTrackMouseSlidePlaceholderHidesNoteOverlay() {
        // A real pitched note ahead of the terminal, so the mouse can sit directly over it
        // without tripping the auto-maintained-terminal block.
        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        when(layoutResult.findElementAtXSs(anyDouble(), eq(line))).thenReturn(0);
        editModeManagerMock.when(EditModeManager::getPreviewElement)
            .thenReturn(ElementType.SLIDE.newInstance());

        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));

        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("the slide-placeholder branch still updates the tracked line")
            .isSameAs(lc);
        assertThat(overlay.isVisible())
            .as("a slide placeholder has no note head -> the note overlay stays hidden")
            .isFalse();
    }

    @Test
    void testHandleClickSlideCommittedHidesFallOverlay() {
        song.setLineWidthSs(100.0);
        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));
        editModeManagerMock.when(EditModeManager::getPreviewElement)
            .thenReturn(ElementType.SLIDE.newInstance());

        PreviewElementManager.setCurrentPreviewLine(lc);
        PreviewElementManager.setCurrentXIndex(1);
        PreviewElementManager.setCurrentSlideZone(SlideZone.FALL);
        PreviewElementManager.previewElementDidChange();

        var fallOverlay = PreviewElementManager.getFallOverlay();

        if (fallOverlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null fall overlay");
        }

        assertThat(fallOverlay.isVisible())
            .as("fixture sanity: the fall preview is visible before the click commits it")
            .isTrue();

        PreviewElementManager.handleClick(lc);

        assertThat(line.getElement(0).hasFall())
            .as("fixture sanity: the click actually applied the fall")
            .isTrue();
        assertThat(fallOverlay.isVisible())
            .as("handleClick's slide-committed site hides the fall preview once applied")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // T19 — the three mode-driven paths
    // -------------------------------------------------------------------------

    @Test
    void testModeDidChangeDrivesOverlayVisibility() {
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        // Real mouse-entered always precedes mouse-moved; restorePreviewElement (the
        // mode-driven restore path below) reads currentMouseLine, which only mouseEnteredLine
        // sets.
        PreviewElementManager.mouseEnteredLine(lc);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));
        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        assertThat(overlay.isVisible())
            .as("precondition: the preview overlay must be visible before the change under test")
            .isTrue();

        var toSelect = mock(ModeDidChangeNotification.class);
        when(toSelect.getMode()).thenReturn(Mode.SELECT);
        PreviewElementManager.instance().modeDidChange(toSelect);

        assertThat(overlay.isVisible())
            .as("leaving edit mode clears the overlay")
            .isFalse();

        lc.setMousePositionForTest(new Point(0, ON_STAFF_Y_PX));
        var toEdit = mock(ModeDidChangeNotification.class);
        when(toEdit.getMode()).thenReturn(Mode.EDIT);
        PreviewElementManager.instance().modeDidChange(toEdit);

        assertThat(overlay.isVisible())
            .as("returning to edit mode restores the overlay from the last tracked mouse line")
            .isTrue();
    }

    @Test
    void testPlaybackStateDidChangeDrivesOverlayVisibility() {
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        // Real mouse-entered always precedes mouse-moved; restorePreviewElement (the
        // mode-driven restore path below) reads currentMouseLine, which only mouseEnteredLine
        // sets.
        PreviewElementManager.mouseEnteredLine(lc);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));
        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        assertThat(overlay.isVisible())
            .as("precondition: the preview overlay must be visible before the change under test")
            .isTrue();

        playbackMock.when(PlaybackController::isPlaying).thenReturn(true);
        PreviewElementManager.instance().playbackStateDidChange(mock(PlaybackStateDidChangeNotification.class));

        assertThat(overlay.isVisible())
            .as("playback starting clears the overlay")
            .isFalse();

        playbackMock.when(PlaybackController::isPlaying).thenReturn(false);
        lc.setMousePositionForTest(new Point(0, ON_STAFF_Y_PX));
        PreviewElementManager.instance().playbackStateDidChange(mock(PlaybackStateDidChangeNotification.class));

        assertThat(overlay.isVisible())
            .as("playback stopping restores the overlay from the last tracked mouse line")
            .isTrue();
    }

    @Test
    void testPasteModeDidChangeDrivesOverlayVisibility() {
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        // Real mouse-entered always precedes mouse-moved; restorePreviewElement (the
        // mode-driven restore path below) reads currentMouseLine, which only mouseEnteredLine
        // sets.
        PreviewElementManager.mouseEnteredLine(lc);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));
        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        assertThat(overlay.isVisible())
            .as("precondition: the preview overlay must be visible before the change under test")
            .isTrue();

        PreviewElementManager.instance().pasteModeDidChange(new PasteModeDidChangeNotification(true));

        assertThat(overlay.isVisible())
            .as("entering paste mode clears the overlay")
            .isFalse();

        lc.setMousePositionForTest(new Point(0, ON_STAFF_Y_PX));
        PreviewElementManager.instance().pasteModeDidChange(new PasteModeDidChangeNotification(false));

        assertThat(overlay.isVisible())
            .as("leaving paste mode restores the overlay from the last tracked mouse line")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // T20 — rebuild re-target without an intervening mouse event
    // -------------------------------------------------------------------------

    @Test
    void testRetargetShowsOverlayFromManagerStateWithoutMouseEvent() {
        // Manager state set directly, mirroring what a rebuild leaves behind — never through
        // trackMouse/mouseMoved.
        PreviewElementManager.setCurrentPreviewLine(lc);
        PreviewElementManager.setCurrentXIndex(0);
        PreviewElementManager.setCurrentStaffPosition(0);

        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        assertThat(overlay.isVisible())
            .as("setting manager state alone must not show the overlay")
            .isFalse();

        overlay.retarget();

        assertThat(overlay.getTargetLine())
            .as("retarget() re-resolves the target from PreviewElementManager's tracked line")
            .isSameAs(lc);
        assertThat(overlay.isVisible())
            .as("retarget() shows the overlay from manager state alone")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // T22 — position (xIndex-only) vs. configuration (staffPosition) changes
    // -------------------------------------------------------------------------

    @Test
    void testXIndexOnlyChangeRepositionsWithoutChangingHeight() {
        // A second, real element so xIndex=1 lands before the terminal rather than on the
        // always-blocked append-after-terminal slot (elementCount would otherwise be 1).
        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));
        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        var boundsBefore = overlay.getBounds();

        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(1);
        PreviewElementManager.trackMouse(lc, mouseEvent(1, ON_STAFF_Y_PX, false));
        var boundsAfter = overlay.getBounds();

        assertThat(boundsAfter.x)
            .as("a different insertion index moves the overlay horizontally")
            .isNotEqualTo(boundsBefore.x);
        assertThat(boundsAfter.height)
            .as("an xIndex-only change repositions the same ink rather than rebuilding it")
            .isEqualTo(boundsBefore.height);
    }

    @Test
    void testStaffPositionChangeRebuildsDifferentBounds() {
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));
        var overlay = PreviewElementManager.getOverlay();

        if (overlay == null) {
            throw new AssertionError("expected installOverlay to install a non-null overlay");
        }

        var boundsOnStaff = overlay.getBounds();

        // Far above the staff: crosses the ledger-line threshold, flipping the stem direction
        // and adding ledger lines, so the recorded ink itself changes shape.
        var farAboveYPx = yPxForStaffPosition(Staff.MIN_STAFF_POSITION_SP);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, farAboveYPx, false));
        var boundsFarAbove = overlay.getBounds();

        assertThat(boundsFarAbove)
            .as("a staff-position change that crosses the ledger-line threshold rebuilds the ink")
            .isNotEqualTo(boundsOnStaff);
    }

    @Test
    void testXIndexOnlyChangeReanchorsTheGraceGlissandoOverlay() {
        // The grace→host glissando first appears on exactly this path: entering the insert phase
        // hides and then restores the preview element, which resets currentXIndex, so the next
        // move reports a changed index with an unchanged configuration. The visibility flag the
        // restore flipped is not one of the fields configurationChanged tracks, so if the move
        // path did not drive this overlay nothing would.
        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));
        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(0);
        PreviewElementManager.trackMouse(lc, mouseEvent(0, ON_STAFF_Y_PX, false));

        var graceGlissandoOverlay = PreviewElementManager.getGraceGlissandoOverlay();

        if (graceGlissandoOverlay == null) {
            throw new AssertionError("expected installOverlay to install a grace glissando overlay");
        }

        // Stands in for the hidePreviewElement(false) grace mode performs on its way into the
        // insert phase: the overlay is unanchored, and only the move that follows brings it back.
        graceGlissandoOverlay.previewDidChange(null);

        assertThat(graceGlissandoOverlay.getTargetLine())
            .as("precondition: the overlay must be unanchored before the move under test")
            .isNull();

        when(layoutResult.findInsertionIndex(anyDouble(), eq(line))).thenReturn(1);
        PreviewElementManager.trackMouse(lc, mouseEvent(1, ON_STAFF_Y_PX, false));

        assertThat(graceGlissandoOverlay.getTargetLine())
            .as("an xIndex-only move re-anchors the grace glissando, not just the note head")
            .isSameAs(lc);
    }
}
