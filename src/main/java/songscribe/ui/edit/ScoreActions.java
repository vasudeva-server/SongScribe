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
 * <p>
 * This interface decouples EditModeManager from ScoreView, allowing it to request
 * ScoreView to perform UI-related actions without creating a circular dependency.
 * Created as part of Phase 6 of the ScoreView Cleanup refactoring.
 */
public interface ScoreActions {

    /**
     * The single authority for which preference keys {@link #syncPlaybackPrefs()} reads.
     * A caller deciding whether a preference change warrants a sync checks membership here
     * instead of keeping its own copy of the key list.
     */
    Set<PrefsKey> PLAYBACK_SYNC_PREFS_KEYS = Set.of(
        PrefsKey.PLAY_INSERTED_NOTE,
        PrefsKey.PLAY_WITH_REPEATS,
        PrefsKey.INSTRUMENT,
        PrefsKey.TEMPO_CHANGE_PERCENT,
        PrefsKey.PLAYBACK_NOTE_DURATION,
        PrefsKey.PLAYBACK_VOLUME
    );

    /**
     * Clears the current selection.
     */
    void clearSelection();

    /**
     * Repaints the scoreView.
     */
    void repaint();

    /**
     * Sets the insertion note.
     *
     * @param element The note to set as the insertion note
     */
    void setPreviewElement(@Nullable StaffElement element);

    /**
     * Adjusts the drawing width if the line is wider than the current width.
     *
     * @param line The line to check
     * @param revalidateOnly Whether to only revalidate without changing width
     */
    void drawWidthIfWiderLine(Line line, boolean revalidateOnly);

    /**
     * Synchronizes playback-related preferences. Reads exactly the keys named in
     * {@link #PLAYBACK_SYNC_PREFS_KEYS} — a caller adding a key to that set must add the
     * matching read here, and vice versa.
     */
    void syncPlaybackPrefs();

    /**
     * Updates the page layout for the given line width.
     *
     * @param lineWidthSs line width in staff spaces
     */
    void updatePageLayout(Ss lineWidthSs);

    /**
     * Enables or disables key bindings (disabled during lyric text editing).
     *
     * @param enabled {@code true} to enable, {@code false} to disable
     */
    void setKeyBindingsEnabled(boolean enabled);
}
