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

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.component.MainFrame;

/**
 * The final double barline entry, at the foot of <b>Notation ▸ Barline</b> and in the barline
 * popup panel in {@code BarlineToolbar}. Unlike every other barline entry it applies to the song's
 * auto-maintained terminal alone (issue #713): it arms no pen and retypes no ordinary barline, so
 * it is enabled only while the terminal is selected, and it is checked while that terminal is a
 * {@code FINAL_DOUBLE_BARLINE}.
 * <p>
 * Everything else comes from {@link ElementTypeAction}: {@code matchesElement} compares the
 * terminal against this entry's own type, and {@code performAction} routes through
 * {@code Song.replaceTerminal} because {@code Line.setElement} refuses any other path into the
 * terminal slot.
 */
public final class FinalDoubleBarlineAction extends ElementTypeAction {

    public static FinalDoubleBarlineAction createAction(MainFrame mainFrame) {
        return new FinalDoubleBarlineAction(mainFrame);
    }

    private FinalDoubleBarlineAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Kind.NON_DURATION, ElementType.FINAL_DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE), "final-barline.svg", 30,
            "final-double-barline", Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE_TOOLTIP),
            0, 0,
            null,
            // REQUIRES_SINGLE_SELECTION is what disables the entry when nothing is selected;
            // appliesTo() below rules out every selection that is not the terminal.
            withFlags(NON_DURATION_FLAGS, Flag.REQUIRES_SINGLE_SELECTION)
        );
    }

    @Override
    public boolean appliesTo(StaffElement element) {
        return Song.isAutoMaintainedTerminalOfItsSong(element);
    }
}
