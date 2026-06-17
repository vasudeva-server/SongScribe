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
package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.SongMetadataDidChangeNotification;

/**
 * Tests the bracketing contract of {@link Song#metadataDidChange}, which is
 * the handler invoked (via MBassador) when {@code SongSettingsDialog.setData} posts a
 * {@link SongMetadataDidChangeNotification} inside a {@code postWithModification} bracket.
 *
 * <p>The tests call {@code metadataDidChange} directly to avoid the real bus,
 * and verify that the change is recorded as a single coarse
 * {@link MetadataField#ATTRIBUTION} mutation in a {@link SongDidChangeNotification}.
 */
class SongMetadataDialogFlowTest extends UnitTest {

    private Song song;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so the constructor's bus interactions
        // go to the real (unobserved) bus, not the mock.
        song = new Song();
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }


    // -----------------------------------------------------------------------
    // Coarse ATTRIBUTION mutation
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AttributionMutation {

        @Test
        void testProducesSingleAttributionMutation() {
            // metadataDidChange must emit exactly one SongDidChangeNotification
            // carrying exactly one ATTRIBUTION MetadataChange mutation.
            var oldMetadata = song.getMetadata();
            var newMetadata = new SongMetadata(
                "New Song Title",
                "",
                "Paris",
                "2024",
                3,
                15,
                "Bach",
                "Mozart",
                Song.LyricsSource.TEXT,
                true,
                false,
                "", "", 0, 0
            );

            song.metadataDidChange(new SongMetadataDidChangeNotification(newMetadata));

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);

            var change = (MetadataChange) notification.getMutations().getFirst();
            assertThat(change.field()).isEqualTo(MetadataField.ATTRIBUTION);
            assertThat(change.oldValue()).isEqualTo(oldMetadata);
            assertThat(change.newValue()).isEqualTo(newMetadata);
        }

        @Test
        void testSongStateReflectsNewMetadata() {
            // After metadataDidChange, song getters must delegate to the new record.
            var newMetadata = new SongMetadata(
                "Dialog Title",
                "",
                "London",
                "2025",
                0,
                0,
                "Beethoven",
                "Beethoven",
                Song.LyricsSource.LYRICIST,
                false,
                false,
                "", "", 0, 0
            );

            song.metadataDidChange(new SongMetadataDidChangeNotification(newMetadata));

            // This test's concern is the notification→handler wiring: that
            // metadataDidChange applies the carried record. Per-getter field
            // delegation is covered thoroughly (all 11 fields, distinct literals)
            // by SongSetterMutationTest.testGettersDelegateToMetadataRecord, so
            // it is not duplicated here.
            assertThat(song.getMetadata()).isEqualTo(newMetadata);
        }

        @Test
        void testNoOpWhenMetadataUnchanged() {
            // setMetadata is a no-op when the record equals the current one, so no
            // SongDidChangeNotification should be emitted.
            var currentMetadata = song.getMetadata();
            song.metadataDidChange(new SongMetadataDidChangeNotification(currentMetadata));

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }
    }


    // -----------------------------------------------------------------------
    // Dialog-flow bracketing
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DialogFlowBracketing {

        @Test
        void testOuterAndInnerBracketsCoalesceIntoSingleNotification() {
            // In production, SongSettingsDialog.setData calls:
            //   song.postWithModification(new SongMetadataDidChangeNotification(metadata))
            // MBassador then dispatches to metadataDidChange, which calls setMetadata.
            // We simulate this by calling metadataDidChange directly inside an outer
            // withModification bracket and verify that the depth counter coalesces
            // everything into one notification.
            var newMetadata = new SongMetadata(
                "Dialog Title",
                "",
                "London",
                "",
                0,
                0,
                Song.SRI_CHINMOY,
                Song.SRI_CHINMOY,
                Song.LyricsSource.LYRICIST,
                false,
                false,
                "", "", 0, 0
            );

            song.withModification(() ->
                song.metadataDidChange(new SongMetadataDidChangeNotification(newMetadata))
            );

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);

            var change = (MetadataChange) notification.getMutations().getFirst();
            assertThat(change.field()).isEqualTo(MetadataField.ATTRIBUTION);
            assertThat(change.newValue()).isEqualTo(newMetadata);
        }
    }


    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SongDidChangeNotification captureSingleDidChange() {
        var captor = ArgumentCaptor.forClass(Message.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof SongDidChangeNotification)
            .map(m -> (SongDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.getFirst();
    }
}
