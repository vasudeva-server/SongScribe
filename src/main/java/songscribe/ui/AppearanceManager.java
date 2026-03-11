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

import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.jthemedetecor.OsThemeDetector;

import songscribe.prefs.Prefs;

/**
 * Manages application appearance (light/dark theme) based on user preference
 * and OS theme changes.
 */
public final class AppearanceManager {

    private static final Logger LOGGER = Logger.getLogger(AppearanceManager.class.getName());
    private static final String PREF_KEY = "appearance";

    private static LafOperations lafOps = new DefaultLafOperations();
    private static boolean listenerRegistered = false;
    private static @Nullable Consumer<Boolean> osThemeListener = null;

    private AppearanceManager() {
    }

    /**
     * Initializes the appearance on startup. Reads the preference, installs the
     * appropriate LAF, and registers the OS theme listener if needed.
     */
    public static void init() {
        var preference = getPreference();
        var isDark = resolveIsDark(preference);
        var laf = createLaf(isDark);

        try {
            lafOps.installLaf(laf);
        } catch (UnsupportedLookAndFeelException | RuntimeException e) {
            throw new IllegalStateException("Failed to install initial look and feel: " + e.getMessage(), e);
        }

        if (preference == Appearance.SYSTEM) {
            registerOsListener();
        }
    }

    /**
     * Switches to the theme indicated by the given preference. Called when the
     * user changes the appearance preference.
     */
    public static void switchTheme(@NotNull Appearance preference) {
        var currentPreference = getPreference();

        if (preference == currentPreference) {
            return;
        }

        Prefs.getInstance().put(PREF_KEY, preference.key());

        if (!applyTheme(resolveIsDark(preference))) {
            Prefs.getInstance().put(PREF_KEY, currentPreference.key());
            return;
        }

        if (preference == Appearance.SYSTEM) {
            registerOsListener();
        } else {
            unregisterOsListener();
        }
    }

    /**
     * Returns the current appearance preference.
     */
    @NotNull
    public static Appearance getPreference() {
        return Appearance.fromKey(Prefs.getInstance().getString(PREF_KEY));
    }

    /**
     * Resolves a preference to whether dark mode should be used.
     */
    static boolean resolveIsDark(@NotNull Appearance preference) {
        return switch (preference) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> detectSystemDark();
        };
    }

    /**
     * Replaces the LAF operations implementation. Used by tests to inject mocks.
     */
    @VisibleForTesting
    static void setLafOperations(@NotNull LafOperations ops) {
        lafOps = ops;
    }

    /**
     * Resets internal state. Used by tests.
     */
    @VisibleForTesting
    static void reset() {
        unregisterOsListener();
        lafOps = new DefaultLafOperations();
    }

    private static boolean detectSystemDark() {
        try {
            return OsThemeDetector.getDetector().isDark();
        } catch (Exception e) {
            LOGGER.warning("OS theme detection unavailable, falling back to light: " + e.getMessage());
            return false;
        }
    }

    @NotNull
    static LookAndFeel createLaf(boolean isDark) {
        if (SystemInfo.isMacOS) {
            return isDark ? new FlatMacDarkLaf() : new FlatMacLightLaf();
        }

        return isDark ? new FlatDarkLaf() : new FlatLightLaf();
    }

    private static boolean applyTheme(boolean isDark) {
        var laf = createLaf(isDark);

        try {
            lafOps.showSnapshot();
            lafOps.installLaf(laf);
            lafOps.updateUI();
            lafOps.hideSnapshotWithAnimation();
            return true;
        } catch (UnsupportedLookAndFeelException | RuntimeException e) {
            LOGGER.warning("Failed to switch theme: " + e.getMessage());
            return false;
        }
    }

    private static void registerOsListener() {
        if (listenerRegistered) {
            return;
        }

        try {
            var detector = OsThemeDetector.getDetector();
            osThemeListener = isDark -> SwingUtilities.invokeLater(() -> applyTheme(isDark));
            detector.registerListener(osThemeListener);
            listenerRegistered = true;
        } catch (Exception e) {
            LOGGER.warning("Failed to register OS theme listener: " + e.getMessage());
        }
    }

    private static void unregisterOsListener() {
        if (!listenerRegistered) {
            return;
        }

        try {
            var detector = OsThemeDetector.getDetector();
            detector.removeListener(osThemeListener);
            osThemeListener = null;
            listenerRegistered = false;
        } catch (Exception e) {
            LOGGER.warning("Failed to unregister OS theme listener: " + e.getMessage());
        }
    }

    private static class DefaultLafOperations implements LafOperations {
        @Override
        public void installLaf(@NotNull LookAndFeel laf) throws UnsupportedLookAndFeelException {
            UIManager.setLookAndFeel(laf);
        }

        @Override
        public void showSnapshot() {
            FlatAnimatedLafChange.showSnapshot();
        }

        @Override
        public void updateUI() {
            FlatLaf.updateUI();
        }

        @Override
        public void hideSnapshotWithAnimation() {
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        }
    }
}
