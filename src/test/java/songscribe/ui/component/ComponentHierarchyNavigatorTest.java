/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LinePanel;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.StaffPanel;

/**
 * Unit tests for {@link ComponentHierarchyNavigator} covering:
 * <ul>
 *   <li>Row 39 — {@code getLineComponent}: returns null when {@code mainPanel} is null</li>
 *   <li>Row 40 — {@code getLineComponent}: returns matching {@link LineComponent} when found</li>
 *   <li>Row 41 — {@code getActualLineMiddleYPx}: returns 0 when {@code mainPanel} is null</li>
 *   <li>Row 42 — {@code getActualLineMiddleYPx}: sums Y offsets from mainPanel + staffPanel
 *                + linePanel + lineComponent + middleLineY</li>
 *   <li>Row 46 — {@code updateLayoutFromComponents}: single-panel fallback uses the
 *                line component's own height</li>
 *   <li>Row 47 — {@code updateLayoutFromComponents}: with &ge;2 panels, rowHeight =
 *                midY[1] - midY[0]</li>
 * </ul>
 *
 * <p>Rows 43–45 in the ledger describe a {@code findLineIndexAtPoint} method that
 * does not exist in the production class; those behaviors were never implemented.
 */
class ComponentHierarchyNavigatorTest extends UnitTest {

    // Y-coordinate constants for getActualLineMiddleYPx tests
    private static final int MAIN_PANEL_Y = 10;
    private static final int STAFF_PANEL_Y = 20;
    private static final int LINE_PANEL_Y = 30;
    private static final int LINE_COMPONENT_Y = 5;
    private static final int MIDDLE_LINE_Y_PX = 15;
    private static final int EXPECTED_MIDDLE_Y = MAIN_PANEL_Y + STAFF_PANEL_Y + LINE_PANEL_Y
        + LINE_COMPONENT_Y + MIDDLE_LINE_Y_PX;
    private static final int SINGLE_PANEL_LINE_HEIGHT_PX = 40;

    @AfterEach
    void resetScale() {
        // ScaleContext is a global singleton; restore the default after each test
        // so mutations in one test do not affect others.
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    // -----------------------------------------------------------------------
    // Row 39: getLineComponent returns null when mainPanel is null
    // -----------------------------------------------------------------------

    @Test
    void testGetLineComponentReturnsNullWhenMainPanelIsNull() {
        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(null);

        var navigator = new ComponentHierarchyNavigator(provider);

        assertThat(navigator.getLineComponent(0)).isNull();
    }

    // -----------------------------------------------------------------------
    // Row 40: getLineComponent returns the matching LineComponent by lineIndex
    // -----------------------------------------------------------------------

    @Test
    void testGetLineComponentReturnsMatchingLineComponent() {
        var provider = buildProvider(buildPanel(0), buildPanel(1), buildPanel(2));
        var navigator = new ComponentHierarchyNavigator(provider);

        // lineIndex 1 should return the LineComponent whose getLineIndex() == 1
        var result = navigator.getLineComponent(1);

        // Use extracting() to retrieve the line index without a direct @Nullable dereference
        assertThat(result)
            .isNotNull()
            .extracting(LineComponent::getLineIndex)
            .isEqualTo(1);
    }

    @Test
    void testGetLineComponentReturnsNullWhenNoLineMatchesIndex() {
        var provider = buildProvider(buildPanel(0), buildPanel(2));
        var navigator = new ComponentHierarchyNavigator(provider);

        // lineIndex 1 is absent from the panel list
        assertThat(navigator.getLineComponent(1)).isNull();
    }

    // -----------------------------------------------------------------------
    // Row 41: getActualLineMiddleYPx returns 0 when mainPanel is null
    // -----------------------------------------------------------------------

    @Test
    void testGetActualLineMiddleYPxReturnsZeroWhenMainPanelIsNull() {
        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(null);

        var navigator = new ComponentHierarchyNavigator(provider);

        assertThat(navigator.getActualLineMiddleYPx(0)).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Row 42: getActualLineMiddleYPx sums all Y contributions
    // -----------------------------------------------------------------------

    @Test
    void testGetActualLineMiddleYPxSumsAllYOffsets() {
        var lineComponent = mock(LineComponent.class);
        when(lineComponent.getLineIndex()).thenReturn(0);
        when(lineComponent.getY()).thenReturn(LINE_COMPONENT_Y);
        when(lineComponent.getMiddleLineYPx()).thenReturn(MIDDLE_LINE_Y_PX);

        var linePanel = mock(LinePanel.class);
        when(linePanel.getLineComponent()).thenReturn(lineComponent);
        when(linePanel.getY()).thenReturn(LINE_PANEL_Y);

        var staffPanel = mock(StaffPanel.class);
        when(staffPanel.getLinePanels()).thenReturn(List.of(linePanel));
        when(staffPanel.getY()).thenReturn(STAFF_PANEL_Y);

        var mainPanel = mock(MainPanel.class);
        when(mainPanel.getStaffPanel()).thenReturn(staffPanel);
        when(mainPanel.getY()).thenReturn(MAIN_PANEL_Y);

        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(mainPanel);

        var navigator = new ComponentHierarchyNavigator(provider);

        assertThat(navigator.getActualLineMiddleYPx(0)).isEqualTo(EXPECTED_MIDDLE_Y);
    }

    @Test
    void testGetActualLineMiddleYPxReturnsZeroWhenLineIndexOutOfBounds() {
        var lineComponent = mock(LineComponent.class);
        when(lineComponent.getLineIndex()).thenReturn(0);
        when(lineComponent.getY()).thenReturn(0);
        when(lineComponent.getMiddleLineYPx()).thenReturn(0);

        var linePanel = mock(LinePanel.class);
        when(linePanel.getLineComponent()).thenReturn(lineComponent);
        when(linePanel.getY()).thenReturn(0);

        var staffPanel = mock(StaffPanel.class);
        when(staffPanel.getLinePanels()).thenReturn(List.of(linePanel));
        when(staffPanel.getY()).thenReturn(0);

        var mainPanel = mock(MainPanel.class);
        when(mainPanel.getStaffPanel()).thenReturn(staffPanel);
        when(mainPanel.getY()).thenReturn(0);

        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(mainPanel);

        var navigator = new ComponentHierarchyNavigator(provider);

        // lineIndex 5 is beyond the single-element list
        assertThat(navigator.getActualLineMiddleYPx(5)).isEqualTo(0);
    }

    @Test
    void testGetActualLineMiddleYPxReturnsZeroWhenLineIndexIsNegative() {
        var provider = buildProvider(buildPanel(0));
        var navigator = new ComponentHierarchyNavigator(provider);

        // Negative index is out of bounds; production guards lineIndex < 0
        assertThat(navigator.getActualLineMiddleYPx(-1)).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Row 46: updateLayoutFromComponents — single-panel fallback
    //
    // Inter-line spacing is owned by StaffLinesLayout and derived from the measured
    // content of adjacent lines, so a lone line contributes no gap at all.
    // -----------------------------------------------------------------------

    @Test
    void testUpdateLayoutFromComponentsDoesNothingWhenMainPanelIsNull() {
        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(null);

        var navigator = new ComponentHierarchyNavigator(provider);
        var captured = new AtomicReference<int[]>();
        navigator.updateLayoutFromComponents(captured::set);

        // layoutUpdater must not have been called when mainPanel is null
        assertThat(captured.get()).isNull();
    }

    @Test
    void testUpdateLayoutFromComponentsDoesNothingWhenNoPanels() {
        var staffPanel = mock(StaffPanel.class);
        when(staffPanel.getLinePanels()).thenReturn(new ArrayList<>());
        when(staffPanel.getY()).thenReturn(0);

        var mainPanel = mock(MainPanel.class);
        when(mainPanel.getStaffPanel()).thenReturn(staffPanel);
        when(mainPanel.getY()).thenReturn(0);

        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(mainPanel);

        var navigator = new ComponentHierarchyNavigator(provider);
        var captured = new AtomicReference<int[]>();
        navigator.updateLayoutFromComponents(captured::set);

        // layoutUpdater must not have been called when there are no line panels
        assertThat(captured.get()).isNull();
    }

    @Test
    void testUpdateLayoutFromComponentsSinglePanelUsesLineHeight() {
        // Single line panel: height = 40px, line at y-sum = 0 for simplicity
        var lineComponent = mock(LineComponent.class);
        when(lineComponent.getLineIndex()).thenReturn(0);
        when(lineComponent.getY()).thenReturn(0);
        when(lineComponent.getMiddleLineYPx()).thenReturn(0);
        when(lineComponent.getHeight()).thenReturn(SINGLE_PANEL_LINE_HEIGHT_PX);

        var linePanel = mock(LinePanel.class);
        when(linePanel.getLineComponent()).thenReturn(lineComponent);
        when(linePanel.getY()).thenReturn(0);

        var staffPanel = mock(StaffPanel.class);
        when(staffPanel.getLinePanels()).thenReturn(List.of(linePanel));
        when(staffPanel.getY()).thenReturn(0);

        var mainPanel = mock(MainPanel.class);
        when(mainPanel.getStaffPanel()).thenReturn(staffPanel);
        when(mainPanel.getY()).thenReturn(0);

        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(mainPanel);

        var navigator = new ComponentHierarchyNavigator(provider);
        var captured = new AtomicReference<int[]>();
        navigator.updateLayoutFromComponents(captured::set);

        // With one line there is no adjacent pair, so no inter-line gap enters the row
        // height: it is the line component's own height and nothing more.
        var result = captured.get();
        assertThat(result).isNotNull();
        assertThat(result[1]).isEqualTo(SINGLE_PANEL_LINE_HEIGHT_PX);
    }

    // -----------------------------------------------------------------------
    // Row 47: updateLayoutFromComponents — two panels, rowHeight = midY[1] - midY[0]
    // -----------------------------------------------------------------------

    @Test
    void testUpdateLayoutFromComponentsTwoPanelsRowHeightIsMidpointDifference() {
        // Two panels with known middle-Y sums: midY[0]=50, midY[1]=110 → rowHeight=60
        var lineComponent0 = buildLineComponentAt(0, 50);
        var lineComponent1 = buildLineComponentAt(1, 110);

        var linePanel0 = mock(LinePanel.class);
        when(linePanel0.getLineComponent()).thenReturn(lineComponent0);
        when(linePanel0.getY()).thenReturn(0);

        var linePanel1 = mock(LinePanel.class);
        when(linePanel1.getLineComponent()).thenReturn(lineComponent1);
        when(linePanel1.getY()).thenReturn(0);

        var staffPanel = mock(StaffPanel.class);
        // Mutable list so getFirst() works on Java 21
        var panels = new ArrayList<>(List.of(linePanel0, linePanel1));
        when(staffPanel.getLinePanels()).thenReturn(panels);
        when(staffPanel.getY()).thenReturn(0);

        var mainPanel = mock(MainPanel.class);
        when(mainPanel.getStaffPanel()).thenReturn(staffPanel);
        when(mainPanel.getY()).thenReturn(0);

        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(mainPanel);

        var navigator = new ComponentHierarchyNavigator(provider);
        var captured = new AtomicReference<int[]>();
        navigator.updateLayoutFromComponents(captured::set);

        var result = captured.get();
        assertThat(result).isNotNull();
        // midY[0] = 0+0+0+50 = 50; midY[1] = 0+0+0+110 = 110; rowHeight = 60
        assertThat(result[1]).isEqualTo(60);
        assertThat(result[0]).isEqualTo(50);
    }

    // -----------------------------------------------------------------------
    // setupLineComponentState
    // -----------------------------------------------------------------------

    @Test
    void testSetupLineComponentStateDoesNothingWhenMainPanelIsNull() {
        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(null);

        var navigator = new ComponentHierarchyNavigator(provider);
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        var scoreView = mock(ScoreView.class);

        // Should complete without throwing — no line components to configure
        navigator.setupLineComponentState(selectionProvider, scoreView);
    }

    @Test
    void testSetupLineComponentStateSetsStateOnAllLineComponents() {
        var lineComponent0 = mock(LineComponent.class);
        when(lineComponent0.getLineIndex()).thenReturn(0);

        var lineComponent1 = mock(LineComponent.class);
        when(lineComponent1.getLineIndex()).thenReturn(1);

        var linePanel0 = mock(LinePanel.class);
        when(linePanel0.getLineComponent()).thenReturn(lineComponent0);

        var linePanel1 = mock(LinePanel.class);
        when(linePanel1.getLineComponent()).thenReturn(lineComponent1);

        var provider = buildProvider(linePanel0, linePanel1);
        var navigator = new ComponentHierarchyNavigator(provider);

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        var scoreView = mock(ScoreView.class);
        navigator.setupLineComponentState(selectionProvider, scoreView);

        verify(lineComponent0).setSelectionProvider(selectionProvider);
        verify(lineComponent0).setScoreView(scoreView);
        verify(lineComponent1).setSelectionProvider(selectionProvider);
        verify(lineComponent1).setScoreView(scoreView);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a provider containing a full mock hierarchy with the given line panels.
     */
    private ComponentHierarchyProvider buildProvider(LinePanel... linePanels) {
        var staffPanel = mock(StaffPanel.class);
        when(staffPanel.getLinePanels()).thenReturn(new ArrayList<>(List.of(linePanels)));
        when(staffPanel.getY()).thenReturn(0);

        var mainPanel = mock(MainPanel.class);
        when(mainPanel.getStaffPanel()).thenReturn(staffPanel);
        when(mainPanel.getY()).thenReturn(0);

        var provider = mock(ComponentHierarchyProvider.class);
        when(provider.getMainPanel()).thenReturn(mainPanel);
        return provider;
    }

    /**
     * Builds a mock {@link LinePanel} whose {@link LineComponent} reports the given {@code lineIndex}.
     */
    private LinePanel buildPanel(int lineIndex) {
        var lineComponent = mock(LineComponent.class);
        when(lineComponent.getLineIndex()).thenReturn(lineIndex);
        when(lineComponent.getY()).thenReturn(0);
        when(lineComponent.getMiddleLineYPx()).thenReturn(0);

        var linePanel = mock(LinePanel.class);
        when(linePanel.getLineComponent()).thenReturn(lineComponent);
        when(linePanel.getY()).thenReturn(0);
        return linePanel;
    }

    /**
     * Builds a mock {@link LineComponent} at the given index whose Y + middleLineYPx sums
     * to {@code totalMiddleY} (all other Y contributions are set to 0 in the caller).
     */
    private LineComponent buildLineComponentAt(int lineIndex, int totalMiddleY) {
        var lc = mock(LineComponent.class);
        when(lc.getLineIndex()).thenReturn(lineIndex);
        when(lc.getY()).thenReturn(0);
        when(lc.getMiddleLineYPx()).thenReturn(totalMiddleY);
        return lc;
    }
}
