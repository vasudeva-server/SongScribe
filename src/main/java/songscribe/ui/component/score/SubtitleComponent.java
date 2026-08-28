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

import songscribe.dom.SongMetadata;
import songscribe.dom.Ss;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.dialog.SongSettingsDialog;

/**
 * Component that renders the song subtitle.
 * <p>
 * The subtitle is centered horizontally, below a gap derived from the
 * {@code SongScribe.score.subtitle.gap} FlatLaf property (in staff spaces).
 * When the subtitle is empty the component collapses to {@code (0, 0)} and
 * emits no gap, so songs without a subtitle are not affected.
 * <p>
 * The page draws {@value songscribe.dom.SongMetadata#MAX_SUBTITLE_LINES} line of it. A
 * subtitle too wide to fit on one line is not wrapped onto a second: the first line draws
 * and the rest does not, in the overflow colour.
 */
public class SubtitleComponent extends BaseTitleComponent {

    @Override
    protected String songText() {
        return getSong().getSubtitle();
    }

    @Override
    protected int maxRenderedLines() {
        return SongMetadata.MAX_SUBTITLE_LINES;
    }

    @Override
    protected SongSettingsDialog.Section editorSection() {
        return SongSettingsDialog.Section.SUBTITLE;
    }

    /**
     * The gap above the subtitle, in view pixels. The gap is a fixed FlatLaf property
     * (in staff spaces) but is converted per layout through the view zoom rather than
     * cached, so it tracks the current zoom.
     */
    @Override
    protected int topGapPx() {
        return toViewPx(new Ss(FlatLafProps.getFloat(FlatLafKey.SCORE_SUBTITLE_GAP))).positionPx();
    }
}
