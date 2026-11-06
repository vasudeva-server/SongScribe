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

package songscribe.ui.component.score;

import java.util.function.Function;

import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.Ss;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.ViewScale;
import songscribe.ui.dialog.SongSettingsDialog;

/**
 * The two title-style text blocks above the staves. Text that wraps past
 * {@link #maxRenderedLines()} is clipped, not shrunk.
 */
public enum TitleKind {

    /** Wraps to at most {@value SongMetadata#MAX_TITLE_LINES} lines. */
    TITLE(SongMetadata.MAX_TITLE_LINES, Song::getNumberedTitle, SongSettingsDialog.Section.TITLE),

    /**
     * Draws {@value SongMetadata#MAX_SUBTITLE_LINES} line. A subtitle too wide for it is not
     * wrapped onto a second: the first line draws and the rest does not.
     */
    SUBTITLE(SongMetadata.MAX_SUBTITLE_LINES, Song::getSubtitle, SongSettingsDialog.Section.SUBTITLE);

    private final int maxRenderedLines;

    private final Function<Song, String> songText;

    private final SongSettingsDialog.Section editorSection;

    TitleKind(int maxRenderedLines, Function<Song, String> songText, SongSettingsDialog.Section editorSection) {
        this.maxRenderedLines = maxRenderedLines;
        this.songText = songText;
        this.editorSection = editorSection;
    }

    /**
     * @return at least 1
     */
    int maxRenderedLines() {
        return maxRenderedLines;
    }

    /**
     * @return empty string for none
     */
    String songText(Song song) {
        return songText.apply(song);
    }

    SongSettingsDialog.Section editorSection() {
        return editorSection;
    }

    /** The gap above the text, drawn only when there is text. */
    int topGapPx(ViewScale scale) {
        return switch (this) {
            case TITLE -> 0;
            case SUBTITLE -> scale.toViewPx(new Ss(FlatLafProps.getFloat(FlatLafKey.SCORE_SUBTITLE_GAP))).positionPx();
        };
    }
}
