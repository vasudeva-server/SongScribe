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

package songscribe.ui.edit;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.prefs.PrefsKey;

/**
 * Callback interface for ScoreView actions needed by EditModeManager.
 */
public interface ScoreActions {

    /** The single authority for which preference keys {@link #syncPlaybackPrefs()} reads. */
    Set<PrefsKey> PLAYBACK_SYNC_PREFS_KEYS = Set.of(
        PrefsKey.PLAY_INSERTED_NOTE,
        PrefsKey.PLAY_WITH_REPEATS,
        PrefsKey.INSTRUMENT,
        PrefsKey.TEMPO_CHANGE_PERCENT,
        PrefsKey.PLAYBACK_NOTE_DURATION,
        PrefsKey.PLAYBACK_VOLUME
    );

    void clearSelection();

    void repaint();

    void setPreviewElement(@Nullable StaffElement element);

    /** Reads exactly the keys in {@link #PLAYBACK_SYNC_PREFS_KEYS}, so the two change together. */
    void syncPlaybackPrefs();

    void updatePageLayout(Ss lineWidthSs);

    void setKeyBindingsEnabled(boolean enabled);
}
