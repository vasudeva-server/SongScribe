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
package songscribe.ui.dialog;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link PropertiesStateStore#put(String, String)} null-handling logic.
 */
class PropertiesStateStoreTest extends UnitTest {

    private Preferences mockPrefs;
    private PropertiesStateStore store;

    @BeforeEach
    void setUp() {
        mockPrefs = mock(Preferences.class);
        store = new PropertiesStateStore(mockPrefs);
    }

    // -- put(key, null) delegates to prefs.remove --

    @Test
    void testPutWithNullValueCallsPrefsRemove() {
        store.put("myKey", null);
        verify(mockPrefs).remove("myKey");
    }

    // -- put(key, non-null) delegates to prefs.put --

    @Test
    void testPutWithNonNullValueCallsPrefsPut() {
        store.put("myKey", "myValue");
        verify(mockPrefs).put("myKey", "myValue");
    }
}
