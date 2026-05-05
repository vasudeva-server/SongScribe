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

package songscribe.ui;

import module java.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.jthemedetecor.OsThemeDetector;

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppearanceManagerTest extends UnitTest {

    private MockedStatic<Prefs> prefsMock;
    private MockedStatic<OsThemeDetector> detectorMock;
    private Prefs mockPrefs;
    private OsThemeDetector mockDetector;
    private LafOperations mockLafOps;

    @BeforeEach
    void setUp() {
        prefsMock = mockStatic(Prefs.class);
        detectorMock = mockStatic(OsThemeDetector.class);

        mockPrefs = mock(Prefs.class);
        mockDetector = mock(OsThemeDetector.class);
        mockLafOps = mock(LafOperations.class);

        prefsMock.when(Prefs::getInstance).thenReturn(mockPrefs);
        detectorMock.when(OsThemeDetector::getDetector).thenReturn(mockDetector);

        AppearanceManager.setLafOperations(mockLafOps);
    }

    @AfterEach
    void tearDown() {
        AppearanceManager.reset();
        detectorMock.close();
        prefsMock.close();
    }

    @Nested
    class CreateLaf {

        @Test
        void testDarkReturnsCorrectLafClass() {
            var laf = AppearanceManager.createLaf(true);

            if (SystemInfo.isMacOS) {
                assertThat(laf).isInstanceOf(FlatMacDarkLaf.class);
            } else {
                assertThat(laf).isInstanceOf(FlatDarkLaf.class);
            }
        }

        @Test
        void testLightReturnsCorrectLafClass() {
            var laf = AppearanceManager.createLaf(false);

            if (SystemInfo.isMacOS) {
                assertThat(laf).isInstanceOf(FlatMacLightLaf.class);
            } else {
                assertThat(laf).isInstanceOf(FlatLightLaf.class);
            }
        }
    }

    @Nested
    class Init {

        @Test
        void testInitInstallsLafFromPreference() throws Exception {
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.LIGHT.key());

            AppearanceManager.init();

            verify(mockLafOps).installLaf(any(LookAndFeel.class));
        }

        @Test
        void testInitRegistersOsListenerForSystemPreference() throws Exception {
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.SYSTEM.key());
            when(mockDetector.isDark()).thenReturn(false);

            AppearanceManager.init();

            verify(mockLafOps).installLaf(any(LookAndFeel.class));
            verify(mockDetector).registerListener(any());
        }

        @Test
        void testInitSkipsOsListenerForNonSystemPreference() throws Exception {
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.DARK.key());

            AppearanceManager.init();

            verify(mockLafOps).installLaf(any(LookAndFeel.class));
            verify(mockDetector, never()).registerListener(any());
        }
    }

    @Nested
    class ResolveIsDark {

        @Test
        void testDarkPreferenceReturnsTrue() {
            assertThat(AppearanceManager.resolveIsDark(Appearance.DARK)).isTrue();
        }

        @Test
        void testLightPreferenceReturnsFalse() {
            assertThat(AppearanceManager.resolveIsDark(Appearance.LIGHT)).isFalse();
        }

        @Test
        void testSystemPreferenceDelegatesToOsDetector() {
            when(mockDetector.isDark()).thenReturn(true);
            assertThat(AppearanceManager.resolveIsDark(Appearance.SYSTEM)).isTrue();

            when(mockDetector.isDark()).thenReturn(false);
            assertThat(AppearanceManager.resolveIsDark(Appearance.SYSTEM)).isFalse();
        }

        @Test
        void testSystemPreferenceFallsBackToLightOnDetectorFailure() {
            detectorMock.when(OsThemeDetector::getDetector).thenThrow(
                new RuntimeException("Detection unavailable")
            );

            assertThat(AppearanceManager.resolveIsDark(Appearance.SYSTEM)).isFalse();
        }
    }

    @Nested
    class SwitchTheme {

        @Test
        void testNoOpWhenPreferenceUnchanged() throws Exception {
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.LIGHT.key());

            AppearanceManager.switchTheme(Appearance.LIGHT);

            verify(mockLafOps, never()).showSnapshot();
            verify(mockLafOps, never()).installLaf(any());
        }

        @Test
        void testSwitchCallsLafOpsInOrder() throws Exception {
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.LIGHT.key());

            AppearanceManager.switchTheme(Appearance.DARK);

            var inOrder = inOrder(mockLafOps);
            inOrder.verify(mockLafOps).showSnapshot();
            inOrder.verify(mockLafOps).installLaf(any(LookAndFeel.class));
            inOrder.verify(mockLafOps).updateUI();
            inOrder.verify(mockLafOps).hideSnapshotWithAnimation();
        }

        @Test
        void testSwitchFromSystemUnregistersOsListener() {
            // First set up as system to register the listener
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.LIGHT.key());
            when(mockDetector.isDark()).thenReturn(false);
            AppearanceManager.switchTheme(Appearance.SYSTEM);
            verify(mockDetector).registerListener(any());

            // Now switch away from system
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.SYSTEM.key());
            AppearanceManager.switchTheme(Appearance.DARK);

            verify(mockDetector).removeListener(any());
        }

        @Test
        void testSwitchSavesNewPreference() {
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.LIGHT.key());

            AppearanceManager.switchTheme(Appearance.DARK);

            verify(mockPrefs).put(PrefsKey.APPEARANCE, Appearance.DARK.key());
        }

        @Test
        void testSwitchToSystemRegistersOsListener() {
            when(mockPrefs.getString(PrefsKey.APPEARANCE)).thenReturn(Appearance.LIGHT.key());
            when(mockDetector.isDark()).thenReturn(false);

            AppearanceManager.switchTheme(Appearance.SYSTEM);

            verify(mockDetector).registerListener(any());
        }
    }
}
