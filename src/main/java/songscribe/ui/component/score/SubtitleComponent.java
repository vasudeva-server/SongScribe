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

import songscribe.dom.ScaleContext;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;

/**
 * Component that renders the song subtitle.
 * <p>
 * The subtitle is centered horizontally, below a gap derived from the
 * {@code SongScribe.score.subtitle.gap} FlatLaf property (in staff spaces).
 * When the subtitle is empty the component collapses to {@code (0, 0)} and
 * emits no gap, so songs without a subtitle are not affected.
 */
public class SubtitleComponent extends BaseTitleComponent {

    // The gap is a fixed FlatLaf property, so resolve it once at construction
    // instead of on every paint/measure. The component is only ever built after
    // FlatLaf defaults are installed (app startup, or the test @BeforeAll).
    private final int topGapPx =
        ScaleContext.ssToRoundedPx(FlatLafProps.getFloat(FlatLafKey.SCORE_SUBTITLE_GAP));

    @Override
    protected String songText() {
        return getSong().getSubtitle();
    }

    @Override
    protected int topGapPx() {
        return topGapPx;
    }
}
