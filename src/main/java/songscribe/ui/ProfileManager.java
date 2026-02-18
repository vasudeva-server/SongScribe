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
package songscribe.ui;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Tempo;
import songscribe.prefs.Prefs;
import songscribe.ui.action.Actions;
import songscribe.ui.component.IMainFrame;
import songscribe.util.Utils;

public class ProfileManager {

    public static final String PROFILE = "Profile";
    private static final String PROFILE_VERSION = "1.0";

    public static final String PLAIN = "Plain";
    public static final String BOLD = "Bold";
    public static final String ITALIC = "Italic";
    private final IMainFrame mainFrame;
    private String defaultProfileName = null;
    private Properties defaultProfile = null;

    public ProfileManager(IMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        var existingProfiles = enumerateProfiles();

        var savedDefault = Prefs.getInstance().getString("defaultProfile");

        if (existingProfiles.contains(savedDefault)) {
            setDefaultProfile(savedDefault);
        } else if (!existingProfiles.isEmpty()) {
            setDefaultProfile(existingProfiles.getFirst());
        } else {
            mainFrame.showErrorMessage(
                "There is not a single profile in your installation. Try reinstalling " + Constants.PACKAGE_NAME
            );
            System.exit(-1);
        }
    }

    public static String stringFontStyle(boolean isBold, boolean isItalic) {
        if (!isBold && !isItalic) {
            return PLAIN;
        }

        return (isBold ? BOLD : "") + (isItalic ? ITALIC : "");
    }

    public static int intFontStyle(String stringFontStyle) {
        if (stringFontStyle == null) {
            return Font.PLAIN;
        }

        var fontStyle = Font.PLAIN;

        if (stringFontStyle.contains(BOLD)) {
            fontStyle |= Font.BOLD;
        }

        if (stringFontStyle.contains(ITALIC)) {
            fontStyle |= Font.ITALIC;
        }

        return fontStyle;
    }

    public ArrayList<String> enumerateProfiles() {
        var profiles = new ArrayList<String>();
        var files = new File(Utils.getResourcePath("profiles")).listFiles();

        if (files != null) {
            profiles = Arrays.stream(files)
                .filter(
                    file ->
                        file.isFile() && (getProfile(file.getName()) != null)
                )
                .map(File::getName)
                .collect(Collectors.toCollection(ArrayList::new));
        }

        return profiles;
    }

    @Nullable
    public Properties getProfile(String name) {
        try (
            var reader = new FileInputStream(
                new File(Utils.getResourcePath("profiles"), name)
            )
        ) {
            var props = new Properties();
            props.load(reader);

            if (!PROFILE_VERSION.equals(props.getProperty(PROFILE))) {
                return null;
            }

            return props;
        } catch (IOException e) {
            mainFrame.showErrorMessage(
                "Could not load the profile " + name
            );
        }

        return null;
    }

    // TODO: Fix NPE warnings
    public void saveProfile(String name)
        throws IOException, FileNotFoundException {
        var dialog = Actions.COMPOSITION_SETTINGS_ACTION.getDialog();
        var props = new Properties();
        props.setProperty(PROFILE, PROFILE_VERSION);
        props.setProperty(
            ProfileKey.ATTRIBUTION.getKey(),
            dialog.attributionArea.getText()
        );
        props.setProperty(
            ProfileKey.TEMPO_TYPE.getKey(),
            ((Tempo.Type) Objects.requireNonNull(
                    dialog.tempoTypeCombo.getSelectedItem()
                )).name()
        );
        props.setProperty(
            ProfileKey.TEMPO.getKey(),
            dialog.tempoSpinnerModel.getValue().toString()
        );
        props.setProperty(
            ProfileKey.TEMPO_DESCRIPTION.getKey(),
            Objects.requireNonNull(
                dialog.tempoDescriptionCombo.getSelectedItem()
            ).toString()
        );

        var keyTypeAndCount = dialog.getKeyTypeAndCountFromCombo();
        props.setProperty(
            ProfileKey.KEY_TYPE.getKey(),
            keyTypeAndCount.getFirst().name()
        );
        props.setProperty(
            ProfileKey.KEYS.getKey(),
            String.valueOf(keyTypeAndCount.getSecond())
        );

        setFontFromComponent(
            dialog.titleFontPreview,
            props,
            ProfileKey.TITLE_FONT,
            ProfileKey.TITLE_FONT_SIZE,
            ProfileKey.TITLE_FONT_STYLE
        );
        setFontFromComponent(
            dialog.lyricsFontPreview,
            props,
            ProfileKey.LYRICS_FONT,
            ProfileKey.LYRICS_FONT_SIZE,
            ProfileKey.LYRICS_FONT_STYLE
        );
        setFontFromComponent(
            dialog.attributionFontPreview,
            props,
            ProfileKey.ATTRIBUTION_FONT,
            ProfileKey.ATTRIBUTION_FONT_SIZE,
            ProfileKey.ATTRIBUTION_FONT_SIZE
        );

        try (
            var writer = new FileOutputStream(
                new File(Utils.getResourcePath("profiles"), name)
            )
        ) {
            props.store(writer, null);
        }
    }

    private static void setFontFromComponent(
        @NotNull JComponent component,
        @NotNull Properties props,
        @NotNull ProfileKey fontKey,
        @NotNull ProfileKey sizeKey,
        @NotNull ProfileKey styleKey
    ) {
        var font = component.getFont();
        props.setProperty(fontKey.getKey(), font.getPSName());
        props.setProperty(sizeKey.getKey(), String.valueOf(font.getSize()));
        props.setProperty(styleKey.getKey(), PLAIN);
    }

    public void setDefaultProfile(String defaultProfileName) {
        this.defaultProfileName = defaultProfileName;
        defaultProfile = getProfile(defaultProfileName);
        Prefs.getInstance().put("defaultProfile", defaultProfileName);
    }

    public String getDefaultProfileName() {
        return defaultProfileName;
    }

    public String getDefaultProperty(ProfileKey pk) {
        // Try the new key first, then fall back to the legacy key for migration
        var value = defaultProfile.getProperty(pk.getKey());

        if ((value == null) && (pk.getLegacyKey() != null)) {
            value = defaultProfile.getProperty(pk.getLegacyKey());
        }

        return value;
    }

    public enum ProfileKey {
        ATTRIBUTION("Attribution", "RightInformation"),
        TEMPO_TYPE("TempoType"),
        TEMPO("Tempo"),
        TEMPO_DESCRIPTION("TempoDescription"),
        KEYS("Keys"),
        KEY_TYPE("KeyType"),
        TITLE_FONT("TitleFont"),
        TITLE_FONT_SIZE("TitleFontSize"),
        TITLE_FONT_STYLE("TitleFontStyle"),
        LYRICS_FONT("LyricsFont"),
        LYRICS_FONT_SIZE("LyricsFontSize"),
        LYRICS_FONT_STYLE("LyricsFontStyle"),
        ATTRIBUTION_FONT("AttributionFont", "GeneralFont"),
        ATTRIBUTION_FONT_SIZE("AttributionFontSize", "GeneralFontSize"),
        ANNOTATION_FONT("AnnotationFont"),
        ANNOTATION_FONT_SIZE("AnnotationFontSize");

        private final String key;
        private final String legacyKey;

        ProfileKey(String key) {
            this(key, null);
        }

        ProfileKey(String key, String legacyKey) {
            this.key = key;
            this.legacyKey = legacyKey;
        }

        public String getKey() {
            return key;
        }

        public String getLegacyKey() {
            return legacyKey;
        }
    }
}
