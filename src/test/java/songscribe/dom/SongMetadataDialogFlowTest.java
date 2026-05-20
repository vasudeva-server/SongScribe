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
import static org.mockito.Mockito.mockStatic;

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
import songscribe.message.notification.MetadataDidChangeNotification;

/**
 * Tests the bracketing contract of {@link Song#metadataDidChange}, which is
 * the handler invoked (via MBassador) when {@code TextTab.setData} posts a
 * {@link MetadataDidChangeNotification} inside a {@code withModification} bracket.
 *
 * <p>The tests call {@code metadataDidChange} directly to avoid the real bus,
 * and verify that all field mutations are coalesced into a single
 * {@link SongDidChangeNotification}.
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
    // Multiple-field coalescing
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MultipleFieldCoalescing {

        @Test
        void testMultipleFieldsProduceSingleNotificationWithAllMutations() {
            // Calling metadataDidChange with N non-null fields must fire exactly one
            // SongDidChangeNotification carrying N MetadataChange mutations.
            var oldTitle = song.getTitle();
            var oldPlace = song.getPlace();
            var oldYear = song.getYear();
            var oldMonth = song.getMonth();
            var oldDay = song.getDay();

            var newTitle = "New Song Title";
            var newPlace = "Paris";
            var newYear = "2024";
            var newMonth = 3;
            var newDay = 15;

            song.metadataDidChange(new MetadataDidChangeNotification(
                newTitle, newPlace, newYear, null, null, newMonth, newDay, null
            ));

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(5);

            var titleChange = findMutation(notification, MetadataField.TITLE);
            assertThat(titleChange.oldValue()).isEqualTo(oldTitle);
            assertThat(titleChange.newValue()).isEqualTo(newTitle);

            var placeChange = findMutation(notification, MetadataField.PLACE);
            assertThat(placeChange.oldValue()).isEqualTo(oldPlace);
            assertThat(placeChange.newValue()).isEqualTo(newPlace);

            var yearChange = findMutation(notification, MetadataField.YEAR);
            assertThat(yearChange.oldValue()).isEqualTo(oldYear);
            assertThat(yearChange.newValue()).isEqualTo(newYear);

            var monthChange = findMutation(notification, MetadataField.MONTH);
            assertThat(monthChange.oldValue()).isEqualTo(oldMonth);
            assertThat(monthChange.newValue()).isEqualTo(newMonth);

            var dayChange = findMutation(notification, MetadataField.DAY);
            assertThat(dayChange.oldValue()).isEqualTo(oldDay);
            assertThat(dayChange.newValue()).isEqualTo(newDay);
        }

        @Test
        void testNullFieldsAreNotRecorded() {
            // Fields passed as null in the notification must not produce mutations.
            song.metadataDidChange(new MetadataDidChangeNotification(
                "Only Title", null, null, null, null, null, null, null
            ));

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            var change = (MetadataChange) notification.getMutations().getFirst();
            assertThat(change.field()).isEqualTo(MetadataField.TITLE);
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
            // In production, TextTab.setData calls:
            //   song.withModification(() -> MessageCenter.post(notification))
            // MBassador then dispatches to metadataDidChange, which has its own
            // withModification. We simulate this by calling metadataDidChange directly
            // inside an outer withModification bracket and verify that the depth
            // counter coalesces everything into one notification.
            var newTitle = "Dialog Title";
            var newPlace = "London";

            song.withModification(() ->
                song.metadataDidChange(new MetadataDidChangeNotification(
                    newTitle, newPlace, null, null, null, null, null, null
                ))
            );

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(2);

            assertThat(findMutation(notification, MetadataField.TITLE).newValue()).isEqualTo(newTitle);
            assertThat(findMutation(notification, MetadataField.PLACE).newValue()).isEqualTo(newPlace);
        }
    }


    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MetadataChange findMutation(SongDidChangeNotification notification, MetadataField field) {
        return notification.getMutations().stream()
            .filter(m -> m instanceof MetadataChange mc && mc.field() == field)
            .map(m -> (MetadataChange) m)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No MetadataChange for field " + field));
    }

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
