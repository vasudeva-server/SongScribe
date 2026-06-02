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

package songscribe.prefs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.RecentDocumentsDidChangeNotification;

class RecentDocumentsManagerTest extends UnitTest {

    private static final Path PATH_A = Path.of("/music/song_a.mssw");
    private static final Path PATH_B = Path.of("/music/song_b.mssw");
    private static final Path PATH_C = Path.of("/music/song_c.mssw");
    // Path with a redundant ".." component — normalize() must collapse it.
    private static final Path UNNORMALIZED_PATH = Path.of("/music/subdir/../song_a.mssw");
    private static final Path NORMALIZED_PATH = UNNORMALIZED_PATH.normalize();

    // A null byte in a path string causes Path.of() to throw InvalidPathException.
    private static final String MALFORMED_PATH_STRING = "bad\u0000path";

    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
        RecentDocumentsManager.resetForTest();
    }

    // --- add: deduplication and move-to-front ---

    @Test
    void testAddMovesExistingEntryToFront() {
        // Add two entries, then re-add the first. The re-added entry must move to
        // position 0 and the list must not contain duplicates.
        RecentDocumentsManager.add(PATH_A);
        RecentDocumentsManager.add(PATH_B);
        RecentDocumentsManager.add(PATH_A);

        var recents = RecentDocumentsManager.getRecents();
        assertThat(recents).containsExactly(PATH_A, PATH_B);
    }

    @Test
    void testAddInsertsNewEntryAtFront() {
        // A brand-new path must land at index 0 and push existing entries down.
        RecentDocumentsManager.add(PATH_A);
        RecentDocumentsManager.add(PATH_B);

        var recents = RecentDocumentsManager.getRecents();
        assertThat(recents.get(0)).isEqualTo(PATH_B);
        assertThat(recents.get(1)).isEqualTo(PATH_A);
    }

    // --- add: MAX_SIZE cap ---

    @Test
    void testAddEnforcesMaxSizeCap() {
        // Fill the list to MAX_SIZE, then add one more. The list must stay at MAX_SIZE
        // and the oldest (first-added) entry must be dropped.
        for (int i = 0; i < RecentDocumentsManager.MAX_SIZE; i++) {
            RecentDocumentsManager.add(Path.of("/music/song_" + i + ".mssw"));
        }

        // This extra add must evict the oldest entry (song_0) from the tail.
        var extraPath = Path.of("/music/extra.mssw");
        RecentDocumentsManager.add(extraPath);

        var recents = RecentDocumentsManager.getRecents();
        assertThat(recents).hasSize(RecentDocumentsManager.MAX_SIZE);
        assertThat(recents.get(0)).isEqualTo(extraPath);
        assertThat(recents).doesNotContain(Path.of("/music/song_0.mssw"));
    }

    // --- add: path normalization ---

    @Test
    void testAddNormalizesPathBeforeInserting() {
        // A path with redundant ".." segments must be stored as its normalized form.
        // Passing the unnormalized path and the normalized path refer to the same entry.
        RecentDocumentsManager.add(UNNORMALIZED_PATH);

        var recents = RecentDocumentsManager.getRecents();
        assertThat(recents).containsExactly(NORMALIZED_PATH);
        assertThat(recents).doesNotContain(UNNORMALIZED_PATH);
    }

    // --- add: notification ---

    @Test
    void testAddPostsRecentDocumentsDidChangeNotification() {
        RecentDocumentsManager.add(PATH_A);

        var captor = ArgumentCaptor.forClass(RecentDocumentsDidChangeNotification.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue()).isInstanceOf(RecentDocumentsDidChangeNotification.class);
    }

    // --- remove ---

    @Test
    void testRemoveDeletesMatchingNormalizedPathAndPostsNotification() {
        RecentDocumentsManager.add(PATH_A);
        RecentDocumentsManager.add(PATH_B);

        // Clear the add-notification captures so we can count remove's notification cleanly.
        messageCenterMock.clearInvocations();

        RecentDocumentsManager.remove(PATH_A);

        var recents = RecentDocumentsManager.getRecents();
        assertThat(recents).doesNotContain(PATH_A);
        assertThat(recents).containsExactly(PATH_B);

        var captor = ArgumentCaptor.forClass(RecentDocumentsDidChangeNotification.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue()).isInstanceOf(RecentDocumentsDidChangeNotification.class);
    }

    // --- remove: no-op when absent ---

    @Test
    void testRemoveOnAbsentPathStillPostsNotification() {
        // remove() on a path not in the list must not throw and must still post the
        // notification — callers depend on always receiving the change signal.
        RecentDocumentsManager.add(PATH_A);
        messageCenterMock.clearInvocations();

        RecentDocumentsManager.remove(PATH_C);

        // PATH_A must be undisturbed.
        assertThat(RecentDocumentsManager.getRecents()).containsExactly(PATH_A);

        var captor = ArgumentCaptor.forClass(RecentDocumentsDidChangeNotification.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue()).isInstanceOf(RecentDocumentsDidChangeNotification.class);
    }

    // --- clear ---

    @Test
    void testClearEmptiesListAndPostsNotification() {
        RecentDocumentsManager.add(PATH_A);
        RecentDocumentsManager.add(PATH_B);
        messageCenterMock.clearInvocations();

        RecentDocumentsManager.clear();

        assertThat(RecentDocumentsManager.getRecents()).isEmpty();

        var captor = ArgumentCaptor.forClass(RecentDocumentsDidChangeNotification.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue()).isInstanceOf(RecentDocumentsDidChangeNotification.class);
    }

    // --- getRecents: returns unmodifiable copy ---

    @Test
    void testGetRecentsReturnsUnmodifiableCopy() {
        RecentDocumentsManager.add(PATH_A);

        var recents = RecentDocumentsManager.getRecents();

        // The returned list must be an independent, unmodifiable copy — mutating
        // it must throw and must not affect subsequent calls to getRecents().
        assertThrows(UnsupportedOperationException.class, () -> recents.add(PATH_B));
        assertThat(RecentDocumentsManager.getRecents()).containsExactly(PATH_A);
    }

    // --- constructor startup cleanup: strips non-existent paths ---

    @Test
    void testReloadStripsNonExistentPathsAndPersists() throws Exception {
        // Store two paths in prefs, only one of which exists on disk.
        var tempFile = Files.createTempFile("songscribe-test-", ".mssw");

        try {
            Prefs.putStringList(
                PrefsKey.RECENT_FILES,
                List.of(tempFile.toString(), "/no/such/file_xyz.mssw")
            );

            RecentDocumentsManager.reloadForTest();

            // Only the path that exists on disk must survive the cleanup.
            assertThat(RecentDocumentsManager.getRecents())
                .containsExactly(tempFile)
                .doesNotContain(Path.of("/no/such/file_xyz.mssw"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // --- constructor startup cleanup: skips malformed path strings ---

    @Test
    void testReloadSkipsMalformedPathStrings() throws Exception {
        // A null byte in a path string causes Path.of() to throw InvalidPathException;
        // the loading logic must silently skip it and load the valid entries.
        var tempFile = Files.createTempFile("songscribe-test-", ".mssw");

        try {
            Prefs.putStringList(
                PrefsKey.RECENT_FILES,
                List.of(MALFORMED_PATH_STRING, tempFile.toString())
            );

            RecentDocumentsManager.reloadForTest();

            // The valid, existing path is retained; the malformed one is silently dropped.
            assertThat(RecentDocumentsManager.getRecents())
                .containsExactly(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
