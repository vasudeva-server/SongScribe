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

package songscribe.message.command;

import songscribe.dom.TupletGrade;
import songscribe.message.Message;

/** Asks for the selection to become a tuplet of a grade. */
public class ToggleTupletCommand extends Message {

    private final TupletGrade grade;

    public ToggleTupletCommand(TupletGrade grade) {
        this.grade = grade;
    }

    public TupletGrade getGrade() {
        return grade;
    }

    @Override
    public String toString() {
        return super.toString() + "(tuplet = " + getGrade() + ')';
    }
}
