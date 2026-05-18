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
package songscribe.model;

import org.jspecify.annotations.Nullable;

public class Span {

    public int start, end;
    @Nullable
    public String data;

    public Span(int start, int end, @Nullable String data) {
        this.start = start;
        this.end = end;
        this.data = data;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @Nullable
    public String getData() {
        return data;
    }

    public void setData(@Nullable String data) {
        this.data = data;
    }

    public Span copyRange(int newStart, int newEnd) {
        return new Span(newStart, newEnd, data);
    }
}
