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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.MouseEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.layout.ColumnSpan;
import songscribe.layout.LayoutResult;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;

/**
 * Drives {@link PreviewElementManager#trackMouse} through its pure-logic
 * suppression branches with a mocked {@link LineComponent}
 * and {@link MouseEvent} (matrix §7C, "trackMouse" row). Each branch ends in an
 * early return; the test asserts on the resulting static preview state:
 * <ul>
 *   <li>alt-key down clears the preview</li>
 *   <li>grace mode locks the insertion X to {@code getLockedInsertionXSs()}</li>
 *   <li>grace mode pins the insertion index to {@code getHostInsertionIndex()}</li>
 *   <li>outside grace mode the insertion index still comes from the layout</li>
 *   <li>an out-of-range staff position clears the preview</li>
 *   <li>a null layout result (mid-drag) resets the match flags without clearing</li>
 *   <li>the auto-maintained terminal suppresses the preview</li>
 *   <li>any preview type is suppressed in the gap between a paired grace note and its host</li>
 *   <li>inserting immediately before a paired grace note is still allowed</li>
 *   <li>clicking directly on the host to replace it is not suppressed</li>
 * </ul>
 */
class PreviewElementManagerTrackMouseTest extends PreviewElementManagerTestBase {

    /** A non-negative preview index pre-set so that a clear (which resets it to -1) is observable. */
    private static final int PRESET_X_INDEX = 5;

    /**
     * Middle-line Y far below the mouse so that {@code mouseYss - middleLineYSs} is a large
     * positive staff-space distance — guaranteeing an out-of-range staff position regardless
     * of the pixel-to-staff-space scale.
     */
    private static final double FAR_BELOW_MIDDLE_LINE_YSS = -10_000.0;

    /** Arbitrary locked insertion X (staff-spaces) the grace-mode manager reports while pairing. */
    private static final double LOCKED_INSERTION_X_SS = 12.5;

    /** The host slot the grace-mode manager is stubbed to report — right after the grace note. */
    private static final int HOST_SLOT_X_INDEX = 2;

    /** The index the layout is stubbed to report: the slot after the trailing breath mark. */
    private static final int PAST_BREATH_MARK_X_INDEX = 3;

    /** The index the layout is stubbed to report outside grace mode: the slot after the crotchet. */
    private static final int AFTER_CROTCHET_X_INDEX = 1;

    private GraceModeManager graceModeManager;

    @BeforeEach
    void trackMouseSetUp() {
        // trackMouse consults the grace-mode manager; the base does not stub it, so a
        // null return would NPE. Default isInProgress() is false (Mockito boolean default).
        graceModeManager = mock(GraceModeManager.class);
        editModeManagerMock.when(EditModeManager::getGraceModeManager).thenReturn(graceModeManager);
    }

    /**
     * A mock MouseEvent reporting the given coordinates and alt-key state.
     * <p>
     * Only the component-relative coordinates are stubbed, so {@code getXOnScreen()} and
     * {@code getYOnScreen()} both answer Mockito's int default of 0. {@code trackMouse}'s
     * cursor-dwell path keys off the <i>screen</i> position, so every event built here looks
     * like the same point and only the first one counts as a move. That is harmless for the
     * cases below, which assert on preview tracking rather than the cursor — but a test added
     * here that means to exercise the dwell has to stub the screen coordinates too.
     */
    private static MouseEvent mouseEvent(int xPx, int yPx, boolean altDown) {
        var e = mock(MouseEvent.class);
        when(e.getX()).thenReturn(xPx);
        when(e.getY()).thenReturn(yPx);
        when(e.isAltDown()).thenReturn(altDown);
        return e;
    }

    /** Stub the line component's layout so xIndex / element-at-x resolve to fixed values. */
    private void stubLayout(int insertionIndex, int elementAtX) {
        var layout = mock(LayoutResult.class);
        when(layout.findInsertionIndex(anyDouble(), any(Line.class))).thenReturn(insertionIndex);
        when(layout.findElementAtXSs(anyDouble(), any(Line.class), eq(ColumnSpan.HEAD))).thenReturn(elementAtX);
        when(lc.getLayoutResult()).thenReturn(layout);
    }

    @Test
    void testAltKeyClearsPreview() {
        PreviewElementManager.setCurrentXIndex(PRESET_X_INDEX);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, true));

        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("alt-key down clears the active preview line")
            .isNull();
        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("clearing resets the x-index to -1")
            .isEqualTo(-1);
    }

    @Test
    void testOutOfRangeStaffPositionClearsPreview() {
        when(lc.getMiddleLineYSs()).thenReturn(FAR_BELOW_MIDDLE_LINE_YSS);
        PreviewElementManager.setCurrentXIndex(PRESET_X_INDEX);

        // Mouse Y at 0 px with the middle line far below ⇒ staff position far above MAX ⇒ invalid.
        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("an out-of-range staff position clears the preview on this line")
            .isNull();
        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("clearing resets the x-index to -1")
            .isEqualTo(-1);
    }

    @Test
    void testGraceModeLocksInsertionX() {
        when(graceModeManager.isInProgress()).thenReturn(true);
        when(graceModeManager.getLockedInsertionXSs()).thenReturn(LOCKED_INSERTION_X_SS);
        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        // Layout left null (default) so the method returns at the mid-drag guard after the
        // grace X-lock has already been applied to currentMouseXSs.

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentMouseXSs())
            .as("grace mode locks the insertion X to the grace manager's locked value")
            .isEqualTo(LOCKED_INSERTION_X_SS);
    }

    @Test
    void testGraceModeTakesInsertionIndexFromTheHostSlot() {
        // What this pins down is only that trackMouse takes the index from grace mode rather
        // than from the layout. The layout is a mock, so the elements added below drive
        // nothing — they are here to make the two stubbed indices concrete: in a real
        // [crotchet] [grace] [breath mark] [terminal] line the breath mark occupies the grace
        // note's host slot (index 2), and the locked x — a fixed gap past the grace note —
        // resolves one slot further on (index 3). That the real layout resolves the locked x
        // past a trailing breath mark is not exercised here.
        song.withoutMutationTracking(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.GRACE_QUAVER.newInstance());
            line.addElement(ElementType.BREATH_MARK.newInstance());
        });

        when(graceModeManager.isInProgress()).thenReturn(true);
        when(graceModeManager.getLockedInsertionXSs()).thenReturn(LOCKED_INSERTION_X_SS);
        when(graceModeManager.getHostInsertionIndex()).thenReturn(HOST_SLOT_X_INDEX);
        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        setPreviewElement(ElementType.CROTCHET.newInstance());
        stubLayout(PAST_BREATH_MARK_X_INDEX, -1);
        PreviewElementManager.setCurrentXIndex(PRESET_X_INDEX);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("grace mode pins the insertion index to the host slot, not to what the locked x resolves to")
            .isEqualTo(HOST_SLOT_X_INDEX);
    }

    @Test
    void testOutsideGraceModeTakesInsertionIndexFromTheLayout() {
        // The counterpart to the test above: with grace mode off the index must still come from
        // the layout's x lookup. The stubbed index is deliberately non-zero, because an
        // unstubbed getHostInsertionIndex() answers Mockito's int default of 0 — so a zero here
        // would make the two sources indistinguishable and the grace-mode branch could be
        // dropped altogether without any test noticing.
        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));

        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        setPreviewElement(ElementType.CROTCHET.newInstance());
        stubLayout(AFTER_CROTCHET_X_INDEX, -1);
        PreviewElementManager.setCurrentXIndex(PRESET_X_INDEX);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("outside grace mode the insertion index comes from the layout's x lookup")
            .isEqualTo(AFTER_CROTCHET_X_INDEX);
    }

    @Test
    void testNullLayoutResetsMatchFlagsWithoutClearing() {
        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        when(lc.getLayoutResult()).thenReturn(null);
        PreviewElementManager.setCurrentXIndex(PRESET_X_INDEX);
        PreviewElementManager.setXPosSsMatchesElement(true);

        // Valid staff position, but a null layout (layout being recalculated mid-drag).
        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.isXPosSsMatchesElement())
            .as("a null layout clears the stale element-match flag")
            .isFalse();
        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("the mid-drag guard returns without clearing the preview line")
            .isSameAs(lc);
        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("the x-index is left untouched (not reset to -1)")
            .isEqualTo(PRESET_X_INDEX);
    }

    @Test
    void testTerminalBlockSuppressesPreview() {
        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        // A fresh song's line holds exactly the auto-maintained FINAL_DOUBLE_BARLINE terminal.
        var terminalIndex = line.elementCount() - 1;
        // A plain note cannot replace the terminal, so the position must stay blocked.
        setPreviewElement(ElementType.CROTCHET.newInstance());
        stubLayout(terminalIndex, terminalIndex);
        PreviewElementManager.setCurrentXIndex(PRESET_X_INDEX);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("hovering the auto-maintained terminal with a non-replacing preview clears it")
            .isNull();
        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("clearing resets the x-index to -1")
            .isEqualTo(-1);
    }

    /** Adds a paired grace note (index 0) and its host (index 1) to {@link #line}. */
    private void addPairedGraceAndHost() {
        song.withoutMutationTracking(() -> {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            line.addElement(grace);
            line.addElement(ElementType.CROTCHET.newInstance());
        });
    }

    @Test
    void testInsertionBetweenGraceAndHostSuppressesPreview() {
        // Layout:  [grace(CONNECTED glissando)] [host] [terminal]
        addPairedGraceAndHost();

        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        // A plain note, not a grace note — the suppression is not specific to grace previews.
        setPreviewElement(ElementType.CROTCHET.newInstance());
        // Insertion at the host's index, not over an element head: the gap between the pair.
        stubLayout(1, -1);
        PreviewElementManager.setCurrentXIndex(PRESET_X_INDEX);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("any preview type is suppressed in the gap between a grace note and its host")
            .isNull();
        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("clearing resets the x-index to -1")
            .isEqualTo(-1);
    }

    @Test
    void testInsertionBeforePairedGraceNoteIsAllowed() {
        // Layout:  [grace(CONNECTED glissando)] [host] [terminal]
        addPairedGraceAndHost();

        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        setPreviewElement(ElementType.CROTCHET.newInstance());
        // Insertion at the grace note's own index, not over an element head: before the pair.
        stubLayout(0, -1);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("inserting immediately before a paired grace note is not suppressed")
            .isSameAs(lc);
        assertThat(PreviewElementManager.getCurrentXIndex())
            .as("the insertion index is the grace note's own slot")
            .isEqualTo(0);
    }

    @Test
    void testReplacingHostIsNotSuppressed() {
        // Layout:  [grace(CONNECTED glissando)] [host] [terminal]
        addPairedGraceAndHost();

        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        setPreviewElement(ElementType.CROTCHET.newInstance());
        // The mouse sits directly over the host's head, so it matches an element rather than
        // resolving to a bare gap.
        stubLayout(1, 1);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getCurrentInsertionLine())
            .as("clicking directly on the host to replace it is not suppressed")
            .isSameAs(lc);
        assertThat(PreviewElementManager.isXPosSsMatchesElement())
            .as("the host counts as a replacement target")
            .isTrue();
    }

    @Test
    void testHoveringGraceNoteHidesGhostPreview() {
        // Layout: [grace] [terminal]
        song.withoutMutationTracking(() -> line.addElement(ElementType.GRACE_QUAVER.newInstance()));

        when(lc.getMiddleLineYSs()).thenReturn(0.0);
        setPreviewElement(ElementType.CROTCHET.newInstance());
        // Mouse is directly over the grace note's head.
        stubLayout(0, 0);

        PreviewElementManager.trackMouse(lc, mouseEvent(0, 0, false));

        assertThat(PreviewElementManager.getHoveredElementLocation())
            .as("a grace note under the cursor never shows the replacement highlight")
            .isNull();
        editModeManagerMock.verify(() -> EditModeManager.setPreviewElementVisible(false));
    }
}
