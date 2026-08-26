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
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import songscribe.prefs.Prefs;
import songscribe.prefs.SystemPrefsKey;

/**
 * Mutable holder for the eight document-level font roles.
 *
 * <p>Only {@code ScoreView} and the load path retain a reference to this object.
 * External code receives individual {@link Font} instances or reads through
 * the {@link DocumentFontsHolder} interface.
 */
public final class DocumentFonts implements DocumentFontsHolder {

    /**
     * Maps each font role to its corresponding name and size {@link SystemPrefsKey} pair.
     * Declared once here; {@link #defaultFonts()} iterates over this table.
     */
    private record FontPrefsKeys(FontKey key, SystemPrefsKey nameKey, SystemPrefsKey sizeKey) {}

    private static final FontPrefsKeys[] FONT_PREFS_KEYS = {
        new FontPrefsKeys(FontKey.TITLE,           SystemPrefsKey.TITLE_FONT,           SystemPrefsKey.TITLE_FONT_SIZE),
        new FontPrefsKeys(FontKey.SUBTITLE,        SystemPrefsKey.SUBTITLE_FONT,        SystemPrefsKey.SUBTITLE_FONT_SIZE),
        new FontPrefsKeys(FontKey.LYRICS,          SystemPrefsKey.LYRICS_FONT,          SystemPrefsKey.LYRICS_FONT_SIZE),
        new FontPrefsKeys(FontKey.ATTRIBUTION,     SystemPrefsKey.ATTRIBUTION_FONT,     SystemPrefsKey.ATTRIBUTION_FONT_SIZE),
        new FontPrefsKeys(FontKey.SUB_ATTRIBUTION, SystemPrefsKey.SUB_ATTRIBUTION_FONT, SystemPrefsKey.SUB_ATTRIBUTION_FONT_SIZE),
        new FontPrefsKeys(FontKey.ANNOTATION,      SystemPrefsKey.ANNOTATION_FONT,      SystemPrefsKey.ANNOTATION_FONT_SIZE),
        new FontPrefsKeys(FontKey.FOOTNOTE,        SystemPrefsKey.FOOTNOTE_FONT,        SystemPrefsKey.FOOTNOTE_FONT_SIZE),
        new FontPrefsKeys(FontKey.BANGLA,          SystemPrefsKey.BANGLA_FONT,          SystemPrefsKey.BANGLA_FONT_SIZE),
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
        fonts.put(key, InstalledFonts.createFont(psName, size));
    }

    /**
     * Builds a {@code DocumentFonts} populated from the bundled {@code system-defaults.json},
     * ignoring any user preference overrides. Used wherever a fresh document or reset action
     * needs the canonical font set.
     */
    public static DocumentFonts defaultFonts() {
        return buildFrom(Prefs::getDefaultString, Prefs::getDefaultInt);
    }

    /**
     * Table-driven builder: iterates {@link #FONT_PREFS_KEYS} and calls the supplied
     * value-source functions to resolve each font name and size from the system defaults.
     */
    private static DocumentFonts buildFrom(
        Function<? super SystemPrefsKey, String> nameSource,
        ToIntFunction<? super SystemPrefsKey> sizeSource
    ) {
        var result = new DocumentFonts();

        for (var entry : FONT_PREFS_KEYS) {
            result.setFont(entry.key(), nameSource.apply(entry.nameKey()), sizeSource.applyAsInt(entry.sizeKey()));
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ||
            obj instanceof DocumentFonts other && Objects.equals(fonts, other.fonts);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fonts);
    }
}
