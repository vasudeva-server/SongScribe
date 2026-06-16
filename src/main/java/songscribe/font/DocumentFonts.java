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

package songscribe.font;

import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

/**
 * Mutable holder for the eight document-level font roles.
 *
 * <p>Only {@code ScoreView} and the load path retain a reference to this object.
 * External code receives individual {@link Font} instances or reads through
 * the {@link DocumentFontsHolder} interface.
 */
public final class DocumentFonts implements DocumentFontsHolder {

    /**
     * Maps each font role to its corresponding name and size {@link PrefsKey} pair.
     * Declared once here; both {@link #defaultsFromPrefs()} and
     * {@link #defaultsFromSystemDefaults()} iterate over this table.
     */
    private record FontPrefsKeys(FontKey key, PrefsKey nameKey, PrefsKey sizeKey) {}

    private static final FontPrefsKeys[] FONT_PREFS_KEYS = {
        new FontPrefsKeys(FontKey.TITLE,           PrefsKey.TITLE_FONT,           PrefsKey.TITLE_FONT_SIZE),
        new FontPrefsKeys(FontKey.SUBTITLE,        PrefsKey.SUBTITLE_FONT,        PrefsKey.SUBTITLE_FONT_SIZE),
        new FontPrefsKeys(FontKey.LYRICS,          PrefsKey.LYRICS_FONT,          PrefsKey.LYRICS_FONT_SIZE),
        new FontPrefsKeys(FontKey.ATTRIBUTION,     PrefsKey.ATTRIBUTION_FONT,     PrefsKey.ATTRIBUTION_FONT_SIZE),
        new FontPrefsKeys(FontKey.SUB_ATTRIBUTION, PrefsKey.SUB_ATTRIBUTION_FONT, PrefsKey.SUB_ATTRIBUTION_FONT_SIZE),
        new FontPrefsKeys(FontKey.ANNOTATION,      PrefsKey.ANNOTATION_FONT,      PrefsKey.ANNOTATION_FONT_SIZE),
        new FontPrefsKeys(FontKey.FOOTNOTE,        PrefsKey.FOOTNOTE_FONT,        PrefsKey.FOOTNOTE_FONT_SIZE),
        new FontPrefsKeys(FontKey.BANGLA,          PrefsKey.BANGLA_FONT,          PrefsKey.BANGLA_FONT_SIZE),
    };

    private final EnumMap<FontKey, Font> fonts = new EnumMap<>(FontKey.class);

    public DocumentFonts() {}

    /** Independent copy — mutations to either instance do not affect the other. */
    public DocumentFonts(DocumentFonts other) {
        fonts.putAll(other.fonts);
    }

    @Override
    public Font getFont(FontKey key) {
        var font = fonts.get(key);

        if (font == null) {
            throw new IllegalStateException("Font not set for role: " + key);
        }

        return font;
    }

    public void setFont(FontKey key, Font font) {
        fonts.put(key, font);
    }

    /** Resolves the font by PS name and size, then stores it. Used by the IO load path. */
    public void setFont(FontKey key, String psName, int size) {
        fonts.put(key, MyFontUtils.createFont(psName, size));
    }

    /**
     * Builds a {@code DocumentFonts} populated from the current user preferences,
     * one entry per role. This is the single authoritative mapping from
     * {@link FontKey} to {@link PrefsKey}.
     */
    public static DocumentFonts defaultsFromPrefs() {
        return buildFrom(Prefs::getString, Prefs::getInt);
    }

    /**
     * Builds a {@code DocumentFonts} populated from the bundled {@code defaults.json},
     * ignoring any user preference overrides. Used by reset actions in settings dialogs.
     */
    public static DocumentFonts defaultsFromSystemDefaults() {
        return buildFrom(Prefs::getDefaultString, Prefs::getDefaultInt);
    }

    /**
     * Table-driven builder: iterates {@link #FONT_PREFS_KEYS} and calls the supplied
     * value-source functions to resolve each font name and size. Callers pass either
     * the user-preference accessors or the system-default accessors.
     */
    private static DocumentFonts buildFrom(
        Function<PrefsKey, String> nameSource,
        ToIntFunction<PrefsKey> sizeSource
    ) {
        var result = new DocumentFonts();

        for (var entry : FONT_PREFS_KEYS) {
            result.setFont(entry.key(), nameSource.apply(entry.nameKey()), sizeSource.applyAsInt(entry.sizeKey()));
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof DocumentFonts other)) {
            return false;
        }

        return Objects.equals(fonts, other.fonts);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fonts);
    }
}
