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

package songscribe.ui.action;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.component.MainFrame;

/**
 * The final double barline entry. Unlike every other barline entry it applies to the song's
 * auto-maintained terminal alone.
 */
public final class FinalDoubleBarlineAction extends ElementTypeAction {

    public static FinalDoubleBarlineAction createAction(MainFrame mainFrame) {
        return new FinalDoubleBarlineAction(mainFrame);
    }

    private FinalDoubleBarlineAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Role.NON_DURATION, ElementType.FINAL_DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE), "final-barline.svg", 30,
            "final-double-barline", Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE_TOOLTIP),
            0, 0,
            null,
            // REQUIRES_SINGLE_SELECTION is what disables the entry when nothing is selected;
            // admit() below rules out every selection that is not the terminal.
            withFlags(NON_DURATION_FLAGS, Flag.REQUIRES_SINGLE_SELECTION)
        );
    }

    @Override
    public @Nullable StaffElement admit(StaffElement element) {
        return Song.isTerminalOfItsSong(element) ? element : null;
    }
}
