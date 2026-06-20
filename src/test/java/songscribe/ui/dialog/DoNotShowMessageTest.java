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

import java.awt.Point;
import java.util.Collections;
import java.util.prefs.Preferences;

import javax.swing.JDialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link DoNotShowMessage}: suppression logic in
 * {@link DoNotShowMessage#setVisible} and pref-persistence in {@link DoNotShowMessage#setData}.
 */
class DoNotShowMessageTest extends MainFrameMockTest {

    // Use a unique prop name per test class to avoid cross-test contamination
    private static final String PROP_NAME = "testDoNotShowMessage";

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private DoNotShowMessage dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();

        // Always start with pref cleared
        Preferences.userRoot().node("songscribe").remove(PROP_NAME);

        dialog = new DoNotShowMessage(mainFrame(), "Test Title", "Test message.", PROP_NAME);
    }

    @AfterEach
    void tearDown() {
        // Clear the pref so other tests are not affected
        Preferences.userRoot().node("songscribe").remove(PROP_NAME);
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ---- setVisible ----

    @Test
    void testSetVisibleTrueShowsDialogWhenPrefNotSet() {
        // Pref is NOT set — super.setVisible(true) must be called, creating a JDialog
        try (var construction = mockConstruction(JDialog.class,
                (d, ctx) -> BaseDialogTestHelper.configureMockDialog(d, new Point(100, 100)))) {

            dialog.setVisible(true);

            assertThat(construction.constructed())
                .as("JDialog created when pref is not set")
                .hasSize(1);
        }
    }

    @Test
    void testSetVisibleTrueSuppressedWhenPrefAlreadySet() {
        // Mark pref as already shown
        Preferences.userRoot().node("songscribe").putBoolean(PROP_NAME, true);

        try (var construction = mockConstruction(JDialog.class)) {
            dialog.setVisible(true);

            assertThat(construction.constructed())
                .as("JDialog not created when pref is already set (suppressed)")
                .isEmpty();
        }
    }

    // ---- setData ----

    @Test
    void testSetDataWritesPrefWhenCheckboxSelected() {
        // Simulate checkbox being checked before setData is called
        dialog.dontShowCheck.setSelected(true);
        dialog.setData();

        var storedValue = Preferences.userRoot().node("songscribe").getBoolean(PROP_NAME, false);
        assertThat(storedValue)
            .as("pref set to true when checkbox is selected on setData")
            .isTrue();
    }

    @Test
    void testSetDataDoesNotWritePrefWhenCheckboxNotSelected() {
        dialog.dontShowCheck.setSelected(false);
        dialog.setData();

        var storedValue = Preferences.userRoot().node("songscribe").getBoolean(PROP_NAME, false);
        assertThat(storedValue)
            .as("pref remains false when checkbox is not selected on setData")
            .isFalse();
    }
}
