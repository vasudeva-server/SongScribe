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
package songscribe.ui.dialog;

import module java.desktop;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import songscribe.MainFrameMockTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BaseDialogPositionTest extends MainFrameMockTest {

    private static final Point DIALOG_POSITION = new Point(200, 300);
    private static final Point DIALOG2_POSITION = new Point(400, 500);

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);

        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());

        BaseDialogTestHelper.configureMockFrame(mainFrame());

        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
    }

    @AfterEach
    void tearDown() {
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
        prefsMock.close();
        uiUtilsMock.close();
    }

    @Test
    void testFirstOpenUsesDefaultPosition() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d, DIALOG_POSITION))) {
            new TestDialog().setVisible(true);

            uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()));
        }
    }

    @Test
    void testSecondOpenRestoresSavedLocation() {
        try (var construction = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d, DIALOG_POSITION))) {
            var first = new TestDialog();
            first.setVisible(true);
            first.setVisible(false);

            new TestDialog().setVisible(true);

            // positionDialog must only have been called for the first open
            uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()));

            // The second JDialog instance must have its location set to the saved point
            var secondDialog = construction.constructed().get(1);
            verify(secondDialog).setLocation(DIALOG_POSITION);
        }
    }

    @Test
    void testPositionNotRestoredIfNeverClosed() {
        try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d, DIALOG_POSITION))) {
            // Open first instance but never close it — nothing is saved
            new TestDialog().setVisible(true);

            // Open a second instance; no saved position exists, so default positioning applies
            new TestDialog().setVisible(true);

            uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()), Mockito.times(2));
        }
    }

    @Test
    void testDistinctClassesHaveIndependentPositions() {
        // Each JDialog is constructed in the order: TestDialog open, TestDialog2 open,
        // TestDialog open again, TestDialog2 open again. Alternate between the two positions
        // so each class's mock dialog returns its expected saved location.
        var counter = new AtomicInteger(0);

        try (var construction = mockConstruction(JDialog.class, (d, ctx) ->
            configureMockDialog(d, counter.getAndIncrement() % 2 == 0 ? DIALOG_POSITION : DIALOG2_POSITION)
        )) {
            var dialog1 = new TestDialog();
            dialog1.setVisible(true);
            dialog1.setVisible(false);

            var dialog2 = new TestDialog2();
            dialog2.setVisible(true);
            dialog2.setVisible(false);

            // Open fresh instances — each should restore its own class's saved position
            new TestDialog().setVisible(true);
            new TestDialog2().setVisible(true);

            // positionDialog called only for the two first opens
            uiUtilsMock.verify(
                () -> UIUtils.positionDialog(any(), any()),
                Mockito.times(2)
            );

            // TestDialog's second JDialog instance gets DIALOG_POSITION
            verify(construction.constructed().get(2)).setLocation(DIALOG_POSITION);

            // TestDialog2's second JDialog instance gets DIALOG2_POSITION
            verify(construction.constructed().get(3)).setLocation(DIALOG2_POSITION);
        }
    }


    // -- helpers --

    private static void configureMockDialog(JDialog dialog, Point location) {
        BaseDialogTestHelper.configureMockDialog(dialog, location);
    }

    private static class TestDialog extends BaseDialog {

        TestDialog() {
            super("Test Dialog", false);
        }
    }

    private static class TestDialog2 extends BaseDialog {

        TestDialog2() {
            super("Test Dialog 2", false);
        }
    }

    private static class TestResizableDialog extends BaseDialog {

        TestResizableDialog() {
            super("Test Resizable Dialog", false);
        }

        @Override
        protected boolean isResizable() {
            return true;
        }
    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GeometryPersistence {

        @SuppressWarnings("unchecked")
        @Test
        void testPersistOnCloseNonResizable() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d, DIALOG_POSITION))) {
                var dialog = new TestDialog();
                dialog.setVisible(true);
                dialog.setVisible(false);

                var captor = ArgumentCaptor.forClass(Map.class);
                prefsMock.verify(() -> Prefs.putMap(eq(PrefsKey.DIALOG_GEOMETRY), captor.capture()));
                var saved = (Map<String, Object>) captor.getValue().get("TestDialog");
                assertThat(saved).containsKeys("x", "y");
                assertThat(saved).doesNotContainKeys("width", "height");
            }
        }

        @SuppressWarnings("unchecked")
        @Test
        void testPersistOnCloseResizable() {
            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d, DIALOG_POSITION))) {
                var dialog = new TestResizableDialog();
                dialog.setVisible(true);
                dialog.setVisible(false);

                var captor = ArgumentCaptor.forClass(Map.class);
                prefsMock.verify(() -> Prefs.putMap(eq(PrefsKey.DIALOG_GEOMETRY), captor.capture()));
                var saved = (Map<String, Object>) captor.getValue().get("TestResizableDialog");
                assertThat(saved).containsKeys("x", "y", "width", "height");
            }
        }

        @Test
        void testRestoreFromPrefs() {
            prefsMock.when(() -> Prefs.getMap(PrefsKey.DIALOG_GEOMETRY)).thenReturn(
                Map.of("TestDialog", Map.of("x", 200.0, "y", 300.0))
            );

            try (var construction = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d, DIALOG_POSITION))) {
                new TestDialog().setVisible(true);

                // Should not call positionDialog since prefs had geometry
                uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()), never());

                // Should set the location from prefs
                var dialog = construction.constructed().getFirst();
                verify(dialog).setLocation(new Point(200, 300));
            }
        }

        @Test
        void testMissingKeyFallsBackToDefaultPosition() {
            prefsMock.when(() -> Prefs.getMap(PrefsKey.DIALOG_GEOMETRY)).thenReturn(Collections.emptyMap());

            try (var ignored = mockConstruction(JDialog.class, (d, ctx) -> configureMockDialog(d, DIALOG_POSITION))) {
                new TestDialog().setVisible(true);

                uiUtilsMock.verify(() -> UIUtils.positionDialog(any(), any()));
            }
        }
    }
}
