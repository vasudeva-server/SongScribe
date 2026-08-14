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
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

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
import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;

/**
 * Tests for {@link Song}'s {@code @Handler} notification methods:
 * {@link Song#tempoDidChange} and {@link Song#layoutDidChange}.
 *
 * <p>Handlers are called directly to avoid the real bus, and
 * {@link MessageCenter} is mocked to intercept emitted notifications.
 *
 * <p>There is no key handler to test. A song has no key of its own — its key is line 0's — so a
 * key change is a {@link Line} edit made directly, not a broadcast the song reinterprets. The
 * cases that drove the old handler asserted a propagation heuristic that no longer exists:
 * rewriting every line whose key matched the old song default. Inheritance replaces it, and
 * {@code LineMutationTest} is where it is tested.
 */
class SongNotificationHandlerTest extends UnitTest {

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
    // tempoDidChange
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TempoDidChange {

        /** Non-default so a follow-up update carrying Tempo's defaults still counts as a change. */
        private static final int ESTABLISHED_BPM = Tempo.DEFAULT_BPM * 2;
        private static final String ESTABLISHED_DESCRIPTION = "Andante";

        private void establishTempo() {
            song.tempoDidChange(new TempoDidChangeNotification(
                Duration.MINIM, ESTABLISHED_BPM, ESTABLISHED_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO
            ));
            messageCenterMock.clearInvocations();
        }

        @Test
        void testAllNullSkipsHandler() {
            // An all-null update must not emit any notification.
            song.tempoDidChange(new TempoDidChangeNotification(null, null, null, null));

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testDefaultValuedUpdateOnAFreshSongPostsNothing() {
            // A fresh song already holds Tempo's exact defaults, so resending them is a no-op.
            song.tempoDidChange(new TempoDidChangeNotification(
                Tempo.DEFAULT_TYPE, Tempo.DEFAULT_BPM, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO
            ));

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testUpdateMatchingCurrentValuesPostsNothing() {
            establishTempo();

            song.tempoDidChange(new TempoDidChangeNotification(
                Duration.MINIM, ESTABLISHED_BPM, ESTABLISHED_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO
            ));

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testUpdateChangingOneFieldStillPostsAMutation() {
            establishTempo();

            var newDescription = "Presto";
            song.tempoDidChange(new TempoDidChangeNotification(
                Duration.MINIM, ESTABLISHED_BPM, newDescription, Tempo.DEFAULT_SHOW_TEMPO
            ));

            var mutation = captureSingleDidChange().getMutations().getFirst();
            assertThat(mutation)
                .asInstanceOf(type(MetadataChange.class))
                .extracting(MetadataChange::field)
                .isEqualTo(MetadataField.TEMPO);
        }

        @Test
        void testUpdateWithOnlyNullAndMatchingFieldsPostsNothing() {
            // This is the case the deleted all-null pre-filter did not catch: a mix of null
            // (leave-as-is) and matching-current-value fields must still be recognized as a no-op.
            establishTempo();

            song.tempoDidChange(new TempoDidChangeNotification(null, ESTABLISHED_BPM, null, null));

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testExistingTempoObjectIsNotMutatedInPlace() {
            // The handler must capture a before-clone so the mutation record carries a
            // stable old-state.  After the handler runs, the mutation's oldValue must
            // not equal its newValue (they are different objects with different state).
            establishTempo();

            // Get a reference to the live tempo object before the second call.
            var tempoBeforeUpdate = song.getTempo();
            assertThat(tempoBeforeUpdate).isNotNull();

            var updatedDescription = "Vivace";
            var updatedVisibleTempo = 80;
            song.tempoDidChange(new TempoDidChangeNotification(
                Duration.QUAVER, updatedVisibleTempo, updatedDescription, false
            ));

            var notification = captureSingleDidChange();
            var mutation = (MetadataChange) notification.getMutations().getFirst();
            assertThat(mutation.field()).isEqualTo(MetadataField.TEMPO);

            var oldTempoObj = mutation.oldValue();
            var newTempoObj = mutation.newValue();

            assertThat(oldTempoObj).isNotSameAs(newTempoObj);

            // asInstanceOf both narrows the type and fails loudly if the value
            // is not a Tempo, so the description assertions can never be skipped.
            assertThat(oldTempoObj)
                .asInstanceOf(type(Tempo.class))
                .extracting(Tempo::getTempoDescription)
                .isEqualTo(ESTABLISHED_DESCRIPTION);
            assertThat(newTempoObj)
                .asInstanceOf(type(Tempo.class))
                .extracting(Tempo::getTempoDescription)
                .isEqualTo(updatedDescription);
        }

        @Test
        void testAnEarlierEditsRecordedValueSurvivesALaterEdit() {
            // The handler edits the song's live Tempo in place rather than replacing it. If a
            // mutation record held that instance, the second edit below would silently rewrite
            // what the first edit recorded — and redoing the first edit would then apply the
            // second edit's values instead of its own.
            establishTempo();

            var firstDescription = "Largo";
            song.tempoDidChange(new TempoDidChangeNotification(null, null, firstDescription, null));

            var firstChange = (MetadataChange) captureSingleDidChange().getMutations().getFirst();
            messageCenterMock.clearInvocations();

            song.tempoDidChange(new TempoDidChangeNotification(null, null, "Presto", null));

            assertThat(firstChange.newValue())
                .asInstanceOf(type(Tempo.class))
                .extracting(Tempo::getTempoDescription)
                .as("the first edit's recorded after-state must still be its own")
                .isEqualTo(firstDescription);
            assertThat(firstChange.newValue())
                .as("a mutation record must not hold the song's live Tempo")
                .isNotSameAs(song.getTempo());
        }

        @Test
        void testEmitsMetadataChangeWithTempoField() {
            // Any non-null field must produce a MetadataChange(TEMPO) mutation.
            var newDescription = "Vivace";
            song.tempoDidChange(new TempoDidChangeNotification(null, null, newDescription, null));

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);

            var mutation = (MetadataChange) notification.getMutations().getFirst();
            assertThat(mutation.field()).isEqualTo(MetadataField.TEMPO);

            assertThat(mutation.newValue())
                .asInstanceOf(type(Tempo.class))
                .extracting(Tempo::getTempoDescription)
                .isEqualTo(newDescription);
        }
    }


    // -----------------------------------------------------------------------
    // layoutDidChange
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LayoutDidChange {

        private static final double NEW_ROW_HEIGHT_ADJUSTMENT_SS = 2.5;
        private static final double NEW_LINE_WIDTH_SS = 80.0;

        @Test
        void testNonNullFieldsDispatchedToSetters() {
            song.layoutDidChange(new LayoutDidChangeNotification(
                NEW_ROW_HEIGHT_ADJUSTMENT_SS,
                NEW_LINE_WIDTH_SS
            ));

            assertThat(song.getRowHeightAdjustmentSs()).isEqualTo(NEW_ROW_HEIGHT_ADJUSTMENT_SS);
            assertThat(song.getLineWidthSs()).isEqualTo(NEW_LINE_WIDTH_SS);
        }

        @Test
        void testNullFieldsAreSkipped() {
            // A notification with all-null fields must not call any setter, so values
            // remain at their Song defaults.
            var originalRowHeight = song.getRowHeightAdjustmentSs();
            var originalLineWidth = song.getLineWidthSs();

            song.layoutDidChange(new LayoutDidChangeNotification(null, null));

            assertThat(song.getRowHeightAdjustmentSs()).isEqualTo(originalRowHeight);
            assertThat(song.getLineWidthSs()).isEqualTo(originalLineWidth);
        }
    }

    // metadataDidChange record routing (apply-full-record, no-op when unchanged)
    // is covered by SongMetadataDialogFlowTest.AttributionMutation.


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
