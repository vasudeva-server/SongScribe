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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.SongDidChangeNotification;

class SongBracketTest extends UnitTest {

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

    @Test
    void testApplyChangeOutsideBracketThrows() {
        var mutation = attributionChange();
        var ranInTryBlock = new boolean[]{false};

        assertThatThrownBy(() -> song.applyChange(mutation, () -> ranInTryBlock[0] = true))
            .isInstanceOf(IllegalStateException.class);

        //noinspection ConstantValue -- false because the lambda is not run
        assertThat(ranInTryBlock[0])
            .as("mutator must not run when applyChange is called outside a bracket")
            .isFalse();
        messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
    }

    @Test
    void testApplyChangeInsideBracketAccumulatesAndRunsMutator() {
        var mutation = attributionChange();
        var mutatorRan = new boolean[]{false};

        song.withModification(() -> song.applyChange(mutation, () -> mutatorRan[0] = true));

        assertThat(mutatorRan[0]).isTrue();
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).containsExactly(mutation);
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NestedBrackets {

        @Test
        void testNestedBracketsFireSingleDidChangeAtOutermostClose() {
            var outer = attributionChange();
            var inner1 = attributionChange();
            var inner2 = attributionChange();

            song.withModification(() -> {
                song.applyChange(outer, () -> {});
                song.withModification(() -> {
                    song.applyChange(inner1, () -> {});
                    song.applyChange(inner2, () -> {});
                });
            });

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).containsExactly(outer, inner1, inner2);
        }

        @Test
        void testNestedBracketsAcrossBeginEndPair() {
            var first = attributionChange();
            var second = attributionChange();

            song.beginModification();
            song.beginModification();
            song.applyChange(first, () -> {});
            song.endModification();
            song.applyChange(second, () -> {});
            song.endModification();

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).containsExactly(first, second);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WithModificationLifecycle {

        @Test
        void testEndModificationRunsEvenWhenBodyThrows() {
            var thrown = new RuntimeException("body failure");

            assertThatThrownBy(() -> song.withModification(() -> {
                throw thrown;
            }))
                .isSameAs(thrown);

            // Bracket must be closed: applyChange outside the bracket must throw IllegalStateException
            // (not silently accumulate into a stranded depth counter).
            var followUp = attributionChange();
            assertThatThrownBy(() -> song.applyChange(followUp, () -> {}))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void testEmptyBodyDoesNotPostNotification() {
            song.withModification(() -> {});

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testRunsBodyOnce() {
            var runs = new int[]{0};
            song.withModification(() -> runs[0]++);
            assertThat(runs[0]).isEqualTo(1);
        }
    }

    @Test
    void testDocumentWasSavedSetsModifiedFalse() {
        song.setModified(true);
        song.documentWasSaved(new DocumentWasSavedNotification());
        assertThat(song.isModified()).isFalse();
    }

    @Test
    void testEndSuspendMutationTrackingWithoutBeginThrowsISE() {
        assertThatThrownBy(() -> song.endSuspendMutationTracking())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testNotificationCarriesThisSong() {
        song.withModification(() ->
            song.applyChange(attributionChange(), () -> {})
        );

        var notification = captureSingleDidChange();
        assertThat(notification.getSong()).isSameAs(song);
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SuspensionTracking {

        @Test
        void testNestedWithoutMutationTrackingResumesAfterBothComplete() {
            song.withoutMutationTracking(() -> {
                assertThat(song.isMutationTrackingSuspended()).isTrue();
                song.withoutMutationTracking(() ->
                    assertThat(song.isMutationTrackingSuspended()).isTrue()
                );
                // Inner scope ended but outer is still active.
                assertThat(song.isMutationTrackingSuspended()).isTrue();
            });
            // Both scopes ended; tracking is fully resumed.
            assertThat(song.isMutationTrackingSuspended()).isFalse();
        }
    }

    /**
     * Returns a minimal but valid {@link MetadataChange} using the song's
     * current metadata as oldValue and a title-only variant as newValue.
     * The bracket tests care about the mutation <em>object identity</em>, not its contents.
     */
    private MetadataChange attributionChange() {
        var oldMeta = song.getMetadata();
        var newMeta = new SongMetadata(
            "Changed Title",
            oldMeta.number(),
            oldMeta.place(),
            oldMeta.year(),
            oldMeta.month(),
            oldMeta.day(),
            oldMeta.composer(),
            oldMeta.lyricist(),
            oldMeta.lyricsSource(),
            oldMeta.arrangement(),
            oldMeta.unofficialTranslation()
        );
        return new MetadataChange(MetadataField.ATTRIBUTION, oldMeta, newMeta);
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
