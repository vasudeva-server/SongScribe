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
package songscribe.dom;

import org.jspecify.annotations.Nullable;

/**
 * The number a tuplet prints — <em>N</em> in "<em>N</em> notes in the time of <em>M</em>". The
 * range starts at two, since a group of one re-times nothing.
 */
public enum TupletGrade {
    DUPLET(2),
    TRIPLET(3),
    QUADRUPLET(4),
    QUINTUPLET(5),
    SEXTUPLET(6),
    SEPTUPLET(7);

    private final int number;

    TupletGrade(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    /**
     * @return {@code null} when no grade prints {@code number}
     */
    public static @Nullable TupletGrade of(int number) {
        for (var grade : values()) {
            if (grade.number == number) {
                return grade;
            }
        }

        return null;
    }
}
