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

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

/**
 * Mutable holder for the six document-level font roles.
 *
 * <p>Only {@code ScoreView} and the load path retain a reference to this object.
 * External code receives individual {@link Font} instances or reads through
 * the {@link DocumentFontsHolder} interface.
 */
public final class DocumentFonts implements DocumentFontsHolder {

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
        var prefs = Prefs.getInstance();
        var result = new DocumentFonts();
        result.setFont(FontKey.TITLE,       prefs.getString(PrefsKey.TITLE_FONT),       prefs.getInt(PrefsKey.TITLE_FONT_SIZE));
        result.setFont(FontKey.LYRICS,      prefs.getString(PrefsKey.LYRICS_FONT),      prefs.getInt(PrefsKey.LYRICS_FONT_SIZE));
        result.setFont(FontKey.ATTRIBUTION, prefs.getString(PrefsKey.ATTRIBUTION_FONT), prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE));
        result.setFont(FontKey.ANNOTATION,  prefs.getString(PrefsKey.ANNOTATION_FONT),  prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE));
        result.setFont(FontKey.FOOTNOTE,    prefs.getString(PrefsKey.FOOTNOTE_FONT),    prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE));
        result.setFont(FontKey.BANGLA,      prefs.getString(PrefsKey.BANGLA_FONT),      prefs.getInt(PrefsKey.BANGLA_FONT_SIZE));
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
