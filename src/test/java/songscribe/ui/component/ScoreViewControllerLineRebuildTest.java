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

package songscribe.ui.component;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LinePanel;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.StaffPanel;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Guards the Phase-6 shared repaint fix: a {@code LineInsertion}/{@code LineDeletion}
 * batch must structurally rebuild the {@link StaffPanel} (the only primitive that adds
 * or removes {@link LinePanel}s), not merely invalidate existing panels — otherwise an
 * inserted line renders no panel and a deleted line leaves a stale one. This is the only
 * automated guard for the line-structure repaint; the model-only round-trip tests
 * deep-equal the {@link Song} and never touch the Swing tree.
 *
 * <p>This unit harness mocks {@link StaffPanel}, so it verifies {@code rebuildLayout()} is
 * invoked (the panel-count-changing primitive) rather than counting real panels, which
 * would require the full Swing component graph.
 */
class ScoreViewControllerLineRebuildTest extends UnitTest {

    private ScoreView scoreMock;
    private StaffPanel staffPanelMock;
    private LineComponent lineComponentMock;
    private Line line;
    private ScoreViewController controller;

    @BeforeEach
    void setUp() {
        line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());

        lineComponentMock = mock(LineComponent.class);

        var linePanelMock = mock(LinePanel.class);
        when(linePanelMock.getLine()).thenReturn(line);
        when(linePanelMock.getLineComponent()).thenReturn(lineComponentMock);

        staffPanelMock = mock(StaffPanel.class);
        when(staffPanelMock.getLinePanels()).thenReturn(List.of(linePanelMock));

        var mainPanelMock = mock(MainPanel.class);
        when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);

        scoreMock = mock(ScoreView.class);
        when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

        controller = new ScoreViewController(
            scoreMock,
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class)
        );
    }

    // The controller subscribes itself to the shared message bus in its constructor.
    // MBassador holds subscribers weakly, so an un-unsubscribed controller lingers until
    // GC — still handling every later test's notifications on its now-stale mocks, whose
    // Mockito invocation history then grows unbounded across the suite. Unsubscribe here
    // to keep each test's controller strictly test-scoped.
    @AfterEach
    void tearDown() {
        MessageCenter.unsubscribe(controller);
    }

    private void fire(SongDidChangeNotification notification) {
        controller.songDidChange(notification);
    }

    @Test
    void testLineInsertionRebuildsStaffPanel() {
        fire(new SongDidChangeNotification(List.of(new LineInsertion(1, detachedLine())), new Song()));

        verify(staffPanelMock).rebuildLayout();
        verify(scoreMock).setupLineComponentState();
    }

    @Test
    void testLineDeletionRebuildsStaffPanel() {
        fire(new SongDidChangeNotification(List.of(new LineDeletion(1, detachedLine())), new Song()));

        verify(staffPanelMock).rebuildLayout();
        verify(scoreMock).setupLineComponentState();
    }

    @Test
    void testSpanMutationInvalidatesInsteadOfRebuilding() {
        // A line-scoped span change must take the per-panel invalidation branch, not the
        // structural rebuild — the panel set is unchanged.
        fire(new SongDidChangeNotification(
            List.of(new BeamingAddition(line, new Beam(line.getElement(0), line.getElement(1)))),
            new Song()));

        verify(staffPanelMock, never()).rebuildLayout();
        verify(lineComponentMock).invalidateLayout();
    }

    @Test
    void testFullRelayoutMutationDoesNotRebuildStaffPanel() {
        // A full-relayout mutation (MetadataChange) invalidates every panel but must not
        // structurally rebuild the StaffPanel.
        fire(new SongDidChangeNotification(
            List.of(new MetadataChange(MetadataField.ATTRIBUTION, new Song().getMetadata(), new Song().getMetadata())),
            new Song()));

        verify(staffPanelMock, never()).rebuildLayout();
        verify(lineComponentMock).invalidateLayout();
        // viewChanged() re-syncs derived coordinates and fires only on the full-relayout
        // branch — it distinguishes this path from a mere line-layout invalidation.
        verify(scoreMock).viewChanged();
    }
}
