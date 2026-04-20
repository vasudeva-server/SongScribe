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

import module java.desktop;

import songscribe.Strings;
import songscribe.music.ElementType;

/**
 * Action that sets the composition's auto-maintained terminal to a specific valid
 * terminal type ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}). Renders as
 * a {@link javax.swing.JRadioButtonMenuItem} whose selected state tracks
 * {@link songscribe.music.Composition#currentTerminalType()}.
 */
public final class FinalTerminalAction extends ElementTypeAction {

    public static FinalTerminalAction createFinalDoubleBarline() {
        return new FinalTerminalAction(
            ElementType.FINAL_DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE), "@\uF34A",
            "final-double-barline", Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE_TOOLTIP)
        );
    }

    public static FinalTerminalAction createFinalRightRepeat() {
        return new FinalTerminalAction(
            ElementType.REPEAT_RIGHT,
            Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT), "@\uF345",
            "final-right-repeat", Strings.get(Strings.ACTION_BARLINE_FINAL_RIGHT_REPEAT_TOOLTIP)
        );
    }

    private FinalTerminalAction(
        ElementType type,
        String name,
        String icon,
        String actionCommand,
        String tooltip
    ) {
        super(Kind.NON_DURATION, type, null, name, icon, 24, actionCommand, tooltip, 0, 0, NON_DURATION_FLAGS);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        getComposition().replaceTerminal(getType());
    }

    @Override
    protected boolean enableFromCompositionState() {
        if (!super.enableFromCompositionState()) {
            setSelected(false);
            return false;
        }

        var score = getScore();
        setSelected(score != null && score.isInitialized()
            && score.getComposition().currentTerminalType() == getType());
        return true;
    }
}
