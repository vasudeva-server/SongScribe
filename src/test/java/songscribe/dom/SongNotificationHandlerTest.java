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
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;

/**
 * Tests for {@link Song}'s {@code @Handler} notification methods:
 * {@link Song#tempoDidChange}, {@link Song#keySignatureDidChange},
 * and {@link Song#layoutDidChange}.
 *
 * <p>Handlers are called directly to avoid the real bus, and
 * {@link MessageCenter} is mocked to intercept emitted notifications.
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

        @Test
        void testAllNullSkipsHandler() {
            // An all-null update must not emit any notification.
            song.tempoDidChange(new TempoDidChangeNotification(null, null, null, null));

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testInitsTempoWhenNull() {
            // If no tempo is set on the song, the handler initializes one from defaults
            // and applies the incoming fields.  To create the null-tempo precondition,
            // set the song tempo to null via withModification (which emits a mutation we
            // then discard by clearing invocations).
            song.withModification(() -> song.setTempo(null));
            messageCenterMock.clearInvocations();
            assertThat(song.getTempo()).isNull();

            var newDescription = "Allegro";
            song.tempoDidChange(new TempoDidChangeNotification(null, null, newDescription, null));

            var tempo = song.getTempo();
            assertThat(tempo)
                .as("handler must initialize tempo when it was null")
                .isNotNull();

            if (tempo != null) {
                assertThat(tempo.getTempoDescription()).isEqualTo(newDescription);
            }
        }

        @Test
        void testExistingTempoObjectIsNotMutatedInPlace() {
            // The handler must capture a before-clone so the mutation record carries a
            // stable old-state.  After the handler runs, the mutation's oldValue must
            // not equal its newValue (they are different objects with different state).
            var initialDescription = "Moderate";
            var initialTempo = 120;

            song.tempoDidChange(new TempoDidChangeNotification(
                Duration.CROTCHET, initialTempo, initialDescription, true
            ));
            // Clear invocations so captureSingleDidChange below sees only the second call.
            messageCenterMock.clearInvocations();

            // Get a reference to the live tempo object before the second call.
            var tempoBeforeUpdate = song.getTempo();
            assertThat(tempoBeforeUpdate).isNotNull();

            var updatedDescription = "Andante";
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
            assertThat(oldTempoObj).isInstanceOf(Tempo.class);
            assertThat(newTempoObj).isInstanceOf(Tempo.class);

            if (oldTempoObj instanceof Tempo oldTempo && newTempoObj instanceof Tempo newTempo) {
                assertThat(oldTempo.getTempoDescription()).isEqualTo(initialDescription);
                assertThat(newTempo.getTempoDescription()).isEqualTo(updatedDescription);
            }
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

            var resultTempoObj = mutation.newValue();
            assertThat(resultTempoObj).isInstanceOf(Tempo.class);
            if (resultTempoObj instanceof Tempo resultTempo) {
                assertThat(resultTempo.getTempoDescription()).isEqualTo(newDescription);
            }
        }
    }


    // -----------------------------------------------------------------------
    // keySignatureDidChange
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class KeySignatureDidChange {

        private static final int MATCHING_ACCIDENTAL_COUNT = Song.DEFAULT_KEY_ACCIDENTAL_COUNT;
        private static final KeyType MATCHING_KEY_TYPE = Song.DEFAULT_KEY_TYPE;

        private static final int NEW_ACCIDENTAL_COUNT = 3;
        private static final KeyType NEW_KEY_TYPE = KeyType.SHARPS;

        private static final int DIFFERENT_ACCIDENTAL_COUNT = 2;

        /**
         * Adds a line whose initial key matches the song defaults (so the song-level
         * handler will update it), plus a line with a different key (which the handler
         * must leave alone).
         */
        private Line addMatchingLine() {
            var line = new Line(song);
            song.withoutMutationTracking(() -> {
                line.setKeyAccidentalCount(MATCHING_ACCIDENTAL_COUNT);
                line.setKeyType(MATCHING_KEY_TYPE);
            });
            song.addLine(line);
            return line;
        }

        private Line addNonMatchingLine() {
            var line = new Line(song);
            song.withoutMutationTracking(() -> {
                line.setKeyAccidentalCount(DIFFERENT_ACCIDENTAL_COUNT);
                line.setKeyType(KeyType.SHARPS);
            });
            song.addLine(line);
            return line;
        }

        @Test
        void testSongLevelChangePropagatesToMatchingLinesOnly() {
            // Song-level update (lineIndex == null) must update song defaults and every
            // line whose key currently equals the old song defaults, but leave lines with
            // a different key unchanged.
            var matchingLine = addMatchingLine();
            var nonMatchingLine = addNonMatchingLine();

            // Clear addLine notifications so only keySignatureDidChange's mutations are captured.
            messageCenterMock.clearInvocations();

            song.keySignatureDidChange(new KeySignatureDidChangeNotification(
                null, NEW_KEY_TYPE, NEW_ACCIDENTAL_COUNT
            ));

            // Song-level defaults must be updated.
            assertThat(song.getDefaultKeyType()).isEqualTo(NEW_KEY_TYPE);
            assertThat(song.getDefaultKeyAccidentalCount()).isEqualTo(NEW_ACCIDENTAL_COUNT);

            // The matching line must follow the new song default.
            assertThat(matchingLine.getKeyType()).isEqualTo(NEW_KEY_TYPE);
            assertThat(matchingLine.getKeyAccidentalCount()).isEqualTo(NEW_ACCIDENTAL_COUNT);

            // The non-matching line must be left unchanged.
            assertThat(nonMatchingLine.getKeyAccidentalCount()).isEqualTo(DIFFERENT_ACCIDENTAL_COUNT);
        }

        @Test
        void testPerLineChangeAffectsExactlyThatLine() {
            // A per-line update (lineIndex != null) must change only the targeted line's
            // key signature and leave song defaults and other lines intact.
            var lineA = addMatchingLine();
            var lineB = addMatchingLine();

            // Clear addLine notifications so only keySignatureDidChange's mutations are captured.
            messageCenterMock.clearInvocations();

            // Target lineA (index 0 in the lines added above, but Song starts with 1 line,
            // so lineA is at index 1 and lineB at index 2).
            var lineAIndex = song.indexOfLine(lineA);
            song.keySignatureDidChange(new KeySignatureDidChangeNotification(
                lineAIndex, NEW_KEY_TYPE, NEW_ACCIDENTAL_COUNT
            ));

            // lineA must have the new key.
            assertThat(lineA.getKeyType()).isEqualTo(NEW_KEY_TYPE);
            assertThat(lineA.getKeyAccidentalCount()).isEqualTo(NEW_ACCIDENTAL_COUNT);

            // lineB must be unchanged.
            assertThat(lineB.getKeyType()).isEqualTo(MATCHING_KEY_TYPE);
            assertThat(lineB.getKeyAccidentalCount()).isEqualTo(MATCHING_ACCIDENTAL_COUNT);

            // Song-level defaults must be unchanged.
            assertThat(song.getDefaultKeyType()).isEqualTo(MATCHING_KEY_TYPE);
            assertThat(song.getDefaultKeyAccidentalCount()).isEqualTo(MATCHING_ACCIDENTAL_COUNT);
        }
    }


    // -----------------------------------------------------------------------
    // layoutDidChange
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LayoutDidChange {

        private static final double NEW_TOP_PADDING_SS = 4.0;
        private static final double NEW_ROW_HEIGHT_ADJUSTMENT_SS = 2.5;
        private static final double NEW_LINE_WIDTH_SS = 80.0;
        private static final double NEW_ATTRIBUTION_START_Y_SS = 10.0;

        @Test
        void testNonNullFieldsDispatchedToSettersAndSetByUserDerivedFromFlag() {
            // Non-null fields in the notification must be dispatched to the corresponding
            // setters.  topPaddingSetByUser=true must cause userSetTopPadding() to become true.
            song.layoutDidChange(new LayoutDidChangeNotification(
                NEW_TOP_PADDING_SS, true,
                NEW_ROW_HEIGHT_ADJUSTMENT_SS,
                NEW_LINE_WIDTH_SS,
                NEW_ATTRIBUTION_START_Y_SS
            ));

            assertThat(song.getTopPaddingSs()).isEqualTo(NEW_TOP_PADDING_SS);
            assertThat(song.userSetTopPadding()).isTrue();
            assertThat(song.getRowHeightAdjustmentSs()).isEqualTo(NEW_ROW_HEIGHT_ADJUSTMENT_SS);
            assertThat(song.getLineWidthSs()).isEqualTo(NEW_LINE_WIDTH_SS);
            assertThat(song.getAttributionStartYSs()).isEqualTo(NEW_ATTRIBUTION_START_Y_SS);
        }

        @Test
        void testTopPaddingSetByUserFalseWhenFlagIsNull() {
            // topPaddingSetByUser must be false when getTopPaddingSetByUser() returns null.
            song.layoutDidChange(new LayoutDidChangeNotification(
                NEW_TOP_PADDING_SS, null, null, null, null
            ));

            assertThat(song.getTopPaddingSs()).isEqualTo(NEW_TOP_PADDING_SS);
            assertThat(song.userSetTopPadding()).isFalse();
        }

        @Test
        void testNullFieldsAreSkipped() {
            // A notification with all-null fields must not call any setter, so values
            // remain at their Song defaults.
            var originalTopPadding = song.getTopPaddingSs();
            var originalRowHeight = song.getRowHeightAdjustmentSs();
            var originalLineWidth = song.getLineWidthSs();
            var originalAttributionY = song.getAttributionStartYSs();

            song.layoutDidChange(new LayoutDidChangeNotification(null, null, null, null, null));

            assertThat(song.getTopPaddingSs()).isEqualTo(originalTopPadding);
            assertThat(song.getRowHeightAdjustmentSs()).isEqualTo(originalRowHeight);
            assertThat(song.getLineWidthSs()).isEqualTo(originalLineWidth);
            assertThat(song.getAttributionStartYSs()).isEqualTo(originalAttributionY);
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
