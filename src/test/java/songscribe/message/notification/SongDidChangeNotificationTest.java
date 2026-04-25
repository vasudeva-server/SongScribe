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
package songscribe.message.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;

class SongDidChangeNotificationTest extends UnitTest {

    private Song song;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

    @Nested
    class GetLine {

        @Test
        void testAllSongScopedReturnsNull() {
            var notification = makeNotification(
                new MetadataChange(MetadataField.TITLE, "a", "b"),
                new LayoutChange(LayoutField.LINE_WIDTH_SS, 1.0, 2.0)
            );
            assertThat(notification.getLine()).isNull();
        }

        @Test
        void testEmptyMutationsReturnsNull() {
            var notification = makeNotification();
            assertThat(notification.getLine()).isNull();
        }

        @Test
        void testLineScopedPlusSongScopedReturnsLine() {
            var line = new Line();
            var notification = makeNotification(
                new ElementDeletion(line, 0, ElementType.CROTCHET.newInstance()),
                new MetadataChange(MetadataField.TITLE, "a", "b")
            );
            assertThat(notification.getLine()).isSameAs(line);
        }

        @Test
        void testMultipleLineScopedDifferentLinesReturnsNull() {
            var lineA = new Line();
            var lineB = new Line();
            var notification = makeNotification(
                new ElementDeletion(lineA, 0, ElementType.CROTCHET.newInstance()),
                new ElementInsertion(lineB, 0, ElementType.CROTCHET.newInstance())
            );
            assertThat(notification.getLine()).isNull();
        }

        @Test
        void testMultipleLineScopedSameLineReturnsLine() {
            var line = new Line();
            var notification = makeNotification(
                new ElementDeletion(line, 0, ElementType.CROTCHET.newInstance()),
                new ElementInsertion(line, 1, ElementType.CROTCHET.newInstance())
            );
            assertThat(notification.getLine()).isSameAs(line);
        }

        @Test
        void testRepeatedCallsReturnSameInstance() {
            var line = new Line();
            var notification = makeNotification(
                new ElementDeletion(line, 0, ElementType.CROTCHET.newInstance())
            );

            var first = notification.getLine();
            var second = notification.getLine();
            var third = notification.getLine();

            assertThat(first).isSameAs(line);
            assertThat(second).isSameAs(first);
            assertThat(third).isSameAs(first);
        }

        @Test
        void testSingleLineScopedReturnsLine() {
            var line = new Line();
            var notification = makeNotification(
                new ElementDeletion(line, 0, ElementType.CROTCHET.newInstance())
            );
            assertThat(notification.getLine()).isSameAs(line);
        }
    }

    @Nested
    class HasMutationOf {

        @Test
        void testFalseForAbsentSubclass() {
            var notification = makeNotification(
                new MetadataChange(MetadataField.TITLE, "a", "b")
            );
            assertThat(notification.hasMutationOf(LayoutChange.class)).isFalse();
            assertThat(notification.hasMutationOf(LineInsertion.class)).isFalse();
        }

        @Test
        void testFalseForEmptyMutationList() {
            var notification = makeNotification();
            assertThat(notification.hasMutationOf(MetadataChange.class)).isFalse();
        }

        @Test
        void testTrueForPresentSubclass() {
            var notification = makeNotification(
                new MetadataChange(MetadataField.TITLE, "a", "b"),
                new LyricsChange(LyricsField.UNDER, "x", "y")
            );
            assertThat(notification.hasMutationOf(MetadataChange.class)).isTrue();
            assertThat(notification.hasMutationOf(LyricsChange.class)).isTrue();
        }
    }

    @Test
    void testGetMutationsIsUnmodifiable() {
        // The notification constructor takes ownership of the supplied list and
        // assumes callers pass an already-immutable one. getMutations() must
        // still reject direct modification attempts.
        var first = new MetadataChange(MetadataField.TITLE, "a", "b");
        var notification = new SongDidChangeNotification(List.of(first), song);

        assertThat(notification.getMutations()).containsExactly(first);
        assertThatThrownBy(() -> notification.getMutations().add(first))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private SongDidChangeNotification makeNotification(Mutation... mutations) {
        return new SongDidChangeNotification(List.of(mutations), song);
    }
}
