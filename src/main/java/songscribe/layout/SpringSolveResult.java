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

package songscribe.layout;

import org.jspecify.annotations.Nullable;

/**
 * The outcome of a {@link SpringSpacer#solve} call: the solved per-gap lengths, or infeasible,
 * meaning every gap is frozen at its strut and the line still overflows. Callers must branch on
 * {@link #isInfeasible()} before reading {@link #gapLengthsSs()}.
 */
public final class SpringSolveResult {
    private final double @Nullable [] gapLengthsSs;

    private SpringSolveResult(double @Nullable [] gapLengthsSs) {
        this.gapLengthsSs = gapLengthsSs;
    }

    public static SpringSolveResult solved(double[] gapLengthsSs) {
        return new SpringSolveResult(gapLengthsSs);
    }

    public static SpringSolveResult infeasible() {
        return new SpringSolveResult(null);
    }

    public boolean isInfeasible() {
        return gapLengthsSs == null;
    }

    /** Null when {@link #isInfeasible()}. */
    public double @Nullable [] gapLengthsSs() {
        return gapLengthsSs;
    }
}
