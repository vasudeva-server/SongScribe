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
 * Type-safe enumeration of all preference keys stored in {@code defaults.json}.
 * {@link #ALL} is a sentinel used in notifications to indicate that multiple keys changed;
 * it does not correspond to a key in {@code defaults.json}.
 */
public enum PrefsKey {
    ALL("all"),
    ANNOTATION_FONT("annotationFont"),
    ANNOTATION_FONT_SIZE("annotationFontSize"),
    APPEARANCE("appearance"),
    ATTRIBUTION_FONT("attributionFont"),
    ATTRIBUTION_FONT_SIZE("attributionFontSize"),
    AUTO_SAVE_AFTER_STRIP_SHORT_A("autoSaveAfterStripShortA"),
    CONTROL("control"),
    DIALOG_GEOMETRY("dialogGeometry"),
    EXPORT_DPI("exportDpi"),
    FIRST_RUN("firstRun"),
    IMAGE_EXPORT_FILTER("imageExportFilter"),
    INSTRUMENT("instrument"),
    LAST_SEEN_WHATS_NEW_VERSION("lastSeenWhatsNewVersion"),
    LOOP_PLAYBACK("loopPlayback"),
    LYRICS_FONT("lyricsFont"),
    LYRICS_FONT_SIZE("lyricsFontSize"),
    METRIC("metric"),
    PAGE_SIZE("pageSize"),
    PLAY_INSERTED_NOTE("playInsertedNote"),
    PLAY_SELECTED_NOTE("playSelectedNote"),
    PLAY_WITH_REPEATS("playWithRepeats"),
    PLAYBACK_NOTE_DURATION("playbackNoteDuration"),
    PLAYBACK_VOLUME("playbackVolume"),
    RECENT_FILES("recentFiles"),
    SHOW_TIPS("showTips"),
    STRIP_SHORT_A("stripShortA"),
    TEMPO_CHANGE_PERCENT("tempoChangePercent"),
    TIP_INDEX("tipIndex"),
    TITLE_FONT("titleFont"),
    TITLE_FONT_SIZE("titleFontSize");

    private final String key;

    PrefsKey(String key) {
        this.key = key;
    }

    /** Returns the JSON key string as stored in {@code defaults.json} and {@code prefs.json}. */
    public String key() {
        return key;
    }
}
