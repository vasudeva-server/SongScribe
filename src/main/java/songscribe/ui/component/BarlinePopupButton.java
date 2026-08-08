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

package songscribe.ui.component;

import songscribe.Strings;
import songscribe.ui.action.Actions;
import songscribe.util.GraphicUtils;

/**
 * The toolbar button that drops out a panel of the barline actions.
 */
public class BarlinePopupButton extends PopupToolbarButton {

    private static final String ICON = "barlines.svg";
    private static final int ICON_SIZE_PX = 30;

    public BarlinePopupButton() {
        super(Actions.BARLINE_ACTIONS);
        setIcon(GraphicUtils.getScaledSVGIcon(ICON, ICON_SIZE_PX, true));
        setToolTipText(Strings.get(Strings.TOOLTIP_BARLINES));
    }
}
