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
 * The global preference keys stored in {@code user-defaults.json} and persisted to the user's
 * {@code prefs.json}. {@link #ALL} is a sentinel for notifications meaning multiple keys
 * changed; it is in no defaults file.
 */
public enum PrefsKey {
    ALL("all"),
    APPEARANCE("appearance"),
    DIALOG_GEOMETRY("dialogGeometry"),
    EXPORT_DPI("exportDpi"),
    FIRST_RUN("firstRun"),
    IMAGE_EXPORT_FILTER("imageExportFilter"),
    INSTRUMENT("instrument"),
    LAST_SEEN_WHATS_NEW_VERSION("lastSeenWhatsNewVersion"),
    LOOP_PLAYBACK("loopPlayback"),
    MESSAGES_PANEL_VISIBLE("messagesPanelVisible"),
    MESSAGES_PANEL_WIDTH("messagesPanelWidth"),
    PAGE_SIZE("pageSize"),
    PLAY_INSERTED_NOTE("playInsertedNote"),
    PLAY_SELECTED_NOTE("playSelectedNote"),
    PLAY_WITH_REPEATS("playWithRepeats"),
    PLAYBACK_NOTE_DURATION("playbackNoteDuration"),
    PLAYBACK_VOLUME("playbackVolume"),
    RECENT_FILES("recentFiles"),
    SHOW_TIPS("showTips"),
    STARTUP_ACTION("startupAction"),
    TEMPO_CHANGE_PERCENT("tempoChangePercent"),
    TIP_INDEX("tipIndex"),
    UNITS("units");

    private final String key;

    PrefsKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
