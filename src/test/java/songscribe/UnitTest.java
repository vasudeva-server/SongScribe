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

package songscribe;

import songscribe.SongScribe;
import songscribe.ui.Dialogs;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for unit tests. Suppresses modal error dialogs
 * so tests don't block on user interaction.
 */
public abstract class UnitTest {

    private static volatile boolean bannerShown = false;

    @BeforeAll
    static void suppressDialogs() {
        Dialogs.setSuppressDialogs(true);

        if (!bannerShown) {
            bannerShown = true;
            SongScribe.logBanner("SongScribe (Unit Tests)");
        }
    }
}
