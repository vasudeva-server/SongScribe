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

package songscribe.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;

/**
 * Shared dialog-side vocabulary for the starting-tempo tests: the button indices
 * {@link InitialTempoConfirms#confirmTransfer} maps to decisions, and a stub that makes its
 * dialog answer a fixed one of them.
 */
public final class InitialTempoConfirmsTestSupport {

    /** Button indices, in the order {@code confirmTransfer} passes its options array. */
    public static final int CANCEL_INDEX = 0;
    public static final int NO_INDEX = 1;
    public static final int YES_INDEX = 2;

    private InitialTempoConfirmsTestSupport() {}

    /**
     * Opens a {@code mockStatic(OptionDialogs.class)} whose {@code showOptionDialog} answers
     * {@code answer}. The returned mock is itself the resource to close, so callers use it
     * directly in a try-with-resources — it needs no wrapper of its own.
     */
    public static MockedStatic<OptionDialogs> stubAnswer(int answer) {
        var mock = mockStatic(OptionDialogs.class);
        mock.when(() -> OptionDialogs.showOptionDialog(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any()
        )).thenReturn(answer);
        return mock;
    }
}
