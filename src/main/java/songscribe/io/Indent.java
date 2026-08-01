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
package songscribe.io;

import java.io.PrintWriter;

/**
 * Tracks an output indentation width in spaces. The width changes in fixed
 * steps via {@link #indent()} / {@link #dedent()}, so a writer can descend and
 * ascend nesting levels without tracking absolute column numbers.
 */
public final class Indent {

    private final int step;
    private int width = 0;

    /**
     * @param step the number of spaces one nesting level adds
     */
    public Indent(int step) {
        this.step = step;
    }

    /** Descends one nesting level. */
    public void indent() {
        width += step;
    }

    /** Ascends one nesting level. */
    public void dedent() {
        width -= step;
    }

    /** Resets the indentation to column zero. */
    public void reset() {
        width = 0;
    }

    /**
     * Sets the indentation to an absolute width in spaces.
     *
     * <p>Used only by the legacy {@code .mssw} writers, which set an absolute
     * indent per element instead of nesting with {@link #indent()} /
     * {@link #dedent()}. The {@code .mssw} format is permanently supported for
     * migrating old files, so this method is not going away.
     */
    public void setIndent(int newWidth) {
        width = newWidth;
    }

    /** Prints the current indentation as spaces, without a trailing newline. */
    public void print(PrintWriter pw) {
        for (var i = 0; i < width; i++) {
            pw.print(' ');
        }
    }
}
