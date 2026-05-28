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

package songscribe.error;

/**
 * Test-only access to {@link RuntimeError} internals.
 * Lives in the same package to reach package-private methods without opening production APIs.
 */
public final class RuntimeErrorTestHelper {

    /**
     * Installs an exit handler that throws {@link AssertionError} instead of calling
     * {@code System.exit}. Call once from a {@code @BeforeAll} in each test base class.
     */
    public static void install() {
        RuntimeError.setExitHandlerForTesting(status -> {
            RuntimeError.resetAlertShownForTesting();
            throw new AssertionError("Unexpected System.exit(" + status + ") in unit test");
        });
    }

    /**
     * Resets the re-entrancy guard so a subsequent test can trigger the handler again.
     * Call from {@code @BeforeEach}.
     */
    public static void reset() {
        RuntimeError.resetAlertShownForTesting();
    }

    private RuntimeErrorTestHelper() {}
}
