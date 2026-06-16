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
package songscribe.prefs;

/**
 * Type-safe enumeration of the per-song document settings stored in
 * {@code system-defaults.json} (font names and sizes). These provide the
 * canonical defaults for new songs and are <b>never</b> written to the user's
 * {@code prefs.json}.
 * <p>
 * Kept distinct from {@link PrefsKey} so that the persisting API ({@code Prefs.put},
 * {@code Prefs.getString}, etc.) cannot accept a system key: system keys are
 * read-only and resolved exclusively through {@code Prefs.getDefaultString} /
 * {@code Prefs.getDefaultInt}.
 */
public enum SystemPrefsKey {
    ANNOTATION_FONT("annotationFont"),
    ANNOTATION_FONT_SIZE("annotationFontSize"),
    ATTRIBUTION_FONT("attributionFont"),
    ATTRIBUTION_FONT_SIZE("attributionFontSize"),
    BANGLA_FONT("banglaFont"),
    BANGLA_FONT_SIZE("banglaFontSize"),
    FOOTNOTE_FONT("footnoteFont"),
    FOOTNOTE_FONT_SIZE("footnoteFontSize"),
    LYRICS_FONT("lyricsFont"),
    LYRICS_FONT_SIZE("lyricsFontSize"),
    SUB_ATTRIBUTION_FONT("subAttributionFont"),
    SUB_ATTRIBUTION_FONT_SIZE("subAttributionFontSize"),
    SUBTITLE_FONT("subtitleFont"),
    SUBTITLE_FONT_SIZE("subtitleFontSize"),
    TITLE_FONT("titleFont"),
    TITLE_FONT_SIZE("titleFontSize");

    private final String key;

    SystemPrefsKey(String key) {
        this.key = key;
    }

    /** Returns the JSON key string as stored in {@code system-defaults.json}. */
    public String key() {
        return key;
    }
}
