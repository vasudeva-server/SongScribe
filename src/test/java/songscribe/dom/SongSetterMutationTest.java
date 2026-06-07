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
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.notification.SongDidChangeNotification;

class SongSetterMutationTest extends UnitTest {

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
    // Metadata setters
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StringMetadataSetters {

        @Test
        void testSetTitlePostsMutation() {
            var oldTitle = song.getTitle();
            song.setTitle("New Title");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.TITLE);
            assertThat(mutation.oldValue()).isEqualTo(oldTitle);
            assertThat(mutation.newValue()).isEqualTo("New Title");
        }

        @Test
        void testSetTitleSameValuePostsNothing() {
            song.setTitle(song.getTitle());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetPlacePostsMutation() {
            var oldPlace = song.getPlace();
            song.setPlace("Paris");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.PLACE);
            assertThat(mutation.oldValue()).isEqualTo(oldPlace);
            assertThat(mutation.newValue()).isEqualTo("Paris");
        }

        @Test
        void testSetPlaceSameValuePostsNothing() {
            song.setPlace(song.getPlace());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetYearPostsMutation() {
            var oldYear = song.getYear();
            song.setYear("2024");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.YEAR);
            assertThat(mutation.oldValue()).isEqualTo(oldYear);
            assertThat(mutation.newValue()).isEqualTo("2024");
        }

        @Test
        void testSetYearSameValuePostsNothing() {
            song.setYear(song.getYear());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetComposerPostsMutation() {
            var oldComposer = song.getComposer();
            song.setComposer("Bach");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.COMPOSER);
            assertThat(mutation.oldValue()).isEqualTo(oldComposer);
            assertThat(mutation.newValue()).isEqualTo("Bach");
        }

        @Test
        void testSetComposerSameValuePostsNothing() {
            song.setComposer(song.getComposer());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetLyricistPostsMutation() {
            var oldLyricist = song.getLyricist();
            song.setLyricist("Mozart");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.LYRICIST);
            assertThat(mutation.oldValue()).isEqualTo(oldLyricist);
            assertThat(mutation.newValue()).isEqualTo("Mozart");
        }

        @Test
        void testSetLyricistSameValuePostsNothing() {
            song.setLyricist(song.getLyricist());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetNumberPostsMutation() {
            var oldNumber = song.getNumber();
            song.setNumber("42");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.NUMBER);
            assertThat(mutation.oldValue()).isEqualTo(oldNumber);
            assertThat(mutation.newValue()).isEqualTo("42");
        }

        @Test
        void testSetNumberSameValuePostsNothing() {
            song.setNumber(song.getNumber());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetFootnotesPostsMutation() {
            var oldFootnotes = song.getFootnotes();
            song.setFootnotes("See note 1");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.FOOTNOTES);
            assertThat(mutation.oldValue()).isEqualTo(oldFootnotes);
            assertThat(mutation.newValue()).isEqualTo("See note 1");
        }

        @Test
        void testSetFootnotesSameValuePostsNothing() {
            song.setFootnotes(song.getFootnotes());
            verifyNoNotificationPosted();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NumericAndTypedMetadataSetters {

        @Test
        void testSetMonthPostsMutation() {
            var oldMonth = song.getMonth();
            song.setMonth(oldMonth + 1);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.MONTH);
            assertThat(mutation.oldValue()).isEqualTo(oldMonth);
            assertThat(mutation.newValue()).isEqualTo(oldMonth + 1);
        }

        @Test
        void testSetMonthSameValuePostsNothing() {
            song.setMonth(song.getMonth());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetDayPostsMutation() {
            var oldDay = song.getDay();
            song.setDay(oldDay + 1);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.DAY);
            assertThat(mutation.oldValue()).isEqualTo(oldDay);
            assertThat(mutation.newValue()).isEqualTo(oldDay + 1);
        }

        @Test
        void testSetDaySameValuePostsNothing() {
            song.setDay(song.getDay());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetTempoPostsMutation() {
            var oldTempo = song.getEffectiveTempo();
            var newTempo = new Tempo();
            newTempo.setVisibleTempo(oldTempo.getVisibleTempo() + 10);

            song.setTempo(newTempo);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.TEMPO);
            assertThat(mutation.oldValue()).isSameAs(oldTempo);
            assertThat(mutation.newValue()).isSameAs(newTempo);
        }

        @Test
        void testSetTempoSameValuePostsNothing() {
            song.setTempo(song.getTempo());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetDefaultKeyAccidentalCountPostsMutation() {
            var oldCount = song.getDefaultKeyAccidentalCount();
            song.setDefaultKeyAccidentalCount(oldCount - 1);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.DEFAULT_KEY_ACCIDENTAL_COUNT);
            assertThat(mutation.oldValue()).isEqualTo(oldCount);
            assertThat(mutation.newValue()).isEqualTo(oldCount - 1);
        }

        @Test
        void testSetDefaultKeyAccidentalCountSameValuePostsNothing() {
            song.setDefaultKeyAccidentalCount(song.getDefaultKeyAccidentalCount());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetDefaultKeyTypePostsMutation() {
            var oldKeyType = song.getDefaultKeyType();
            var newKeyType = oldKeyType == KeyType.FLATS ? KeyType.SHARPS : KeyType.FLATS;

            song.setDefaultKeyType(newKeyType);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.DEFAULT_KEY_TYPE);
            assertThat(mutation.oldValue()).isEqualTo(oldKeyType);
            assertThat(mutation.newValue()).isEqualTo(newKeyType);
        }

        @Test
        void testSetDefaultKeyTypeSameValuePostsNothing() {
            song.setDefaultKeyType(song.getDefaultKeyType());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetUnofficialTranslationPostsMutation() {
            var oldValue = song.isUnofficialTranslation();
            song.setUnofficialTranslation(!oldValue);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.UNOFFICIAL_TRANSLATION);
            assertThat(mutation.oldValue()).isEqualTo(oldValue);
            assertThat(mutation.newValue()).isEqualTo(!oldValue);
        }

        @Test
        void testSetUnofficialTranslationSameValuePostsNothing() {
            song.setUnofficialTranslation(song.isUnofficialTranslation());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetLyricsSourcePostsMutation() {
            var oldSource = song.getLyricsSource();
            var newSource = oldSource == Song.LyricsSource.LYRICIST
                ? Song.LyricsSource.TEXT
                : Song.LyricsSource.LYRICIST;

            song.setLyricsSource(newSource);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.LYRICS_SOURCE);
            assertThat(mutation.oldValue()).isEqualTo(oldSource);
            assertThat(mutation.newValue()).isEqualTo(newSource);
        }

        @Test
        void testSetLyricsSourceWithSameValuePostsNothing() {
            song.setLyricsSource(song.getLyricsSource());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetArrangementPostsMutation() {
            var oldArrangement = song.isArrangement();
            song.setArrangement(!oldArrangement);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.ARRANGEMENT);
            assertThat(mutation.oldValue()).isEqualTo(oldArrangement);
            assertThat(mutation.newValue()).isEqualTo(!oldArrangement);
        }

        @Test
        void testSetArrangementWithSameValuePostsNothing() {
            song.setArrangement(song.isArrangement());
            verifyNoNotificationPosted();
        }
    }

    // -----------------------------------------------------------------------
    // Lyrics setters
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LyricsSetters {

        @Test
        void testSetUnderLyricsPostsMutation() {
            var oldLyrics = song.getUnderLyrics();
            song.setUnderLyrics("under text");

            var mutation = captureSingleLyricsChange();
            assertThat(mutation.field()).isEqualTo(LyricsField.UNDER);
            assertThat(mutation.oldText()).isEqualTo(oldLyrics);
            assertThat(mutation.newText()).isEqualTo("under text");
        }

        // setUnderLyrics delegates to processText before storing. STRIP_SHORT_A
        // defaults to true, so a string containing ă is stored with ă→a substitution.
        @Test
        void testSetUnderLyricsAppliesProcessTextTransformation() {
            song.setUnderLyrics("ă text");

            // processText replaces ă with a (STRIP_SHORT_A=true by default).
            assertThat(song.getUnderLyrics()).isEqualTo("a text");

            // The mutation record carries the processed value, not the raw input.
            var mutation = captureSingleLyricsChange();
            assertThat(mutation.newText()).isEqualTo("a text");
        }

        @Test
        void testSetUnderLyricsSameValuePostsNothing() {
            song.setUnderLyrics(song.getUnderLyrics());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetBanglaLyricsPostsMutation() {
            var oldLyrics = song.getBanglaLyrics();
            song.setBanglaLyrics("bangla text");

            var mutation = captureSingleLyricsChange();
            assertThat(mutation.field()).isEqualTo(LyricsField.BANGLA);
            assertThat(mutation.oldText()).isEqualTo(oldLyrics);
            assertThat(mutation.newText()).isEqualTo("bangla text");
        }

        @Test
        void testSetBanglaLyricsSameValuePostsNothing() {
            song.setBanglaLyrics(song.getBanglaLyrics());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetTranslatedLyricsPostsMutation() {
            var oldLyrics = song.getTranslatedLyrics();
            song.setTranslatedLyrics("translated text");

            var mutation = captureSingleLyricsChange();
            assertThat(mutation.field()).isEqualTo(LyricsField.TRANSLATED);
            assertThat(mutation.oldText()).isEqualTo(oldLyrics);
            assertThat(mutation.newText()).isEqualTo("translated text");
        }

        @Test
        void testSetTranslatedLyricsSameValuePostsNothing() {
            song.setTranslatedLyrics(song.getTranslatedLyrics());
            verifyNoNotificationPosted();
        }
    }

    // -----------------------------------------------------------------------
    // Layout setters
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LayoutSetters {

        @Test
        void testSetLineWidthSsPostsMutation() {
            var oldWidth = song.getLineWidthSs();
            song.setLineWidthSs(oldWidth + 1.0);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.LINE_WIDTH_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldWidth);
            assertThat(mutation.newValue()).isEqualTo(oldWidth + 1.0);
        }

        @Test
        void testSetLineWidthSsSameValuePostsNothing() {
            song.setLineWidthSs(song.getLineWidthSs());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetRowHeightAdjustmentSsPostsMutation() {
            var oldAdjustment = song.getRowHeightAdjustmentSs();
            song.setRowHeightAdjustmentSs(oldAdjustment + 1.5);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.ROW_HEIGHT_ADJUSTMENT_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldAdjustment);
            assertThat(mutation.newValue()).isEqualTo(oldAdjustment + 1.5);
        }

        @Test
        void testSetRowHeightAdjustmentSsSameValuePostsNothing() {
            song.setRowHeightAdjustmentSs(song.getRowHeightAdjustmentSs());
            verifyNoNotificationPosted();
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

    private MetadataChange captureSingleMetadataChange() {
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().getFirst()).isInstanceOf(MetadataChange.class);
        return (MetadataChange) notification.getMutations().getFirst();
    }

    private LyricsChange captureSingleLyricsChange() {
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().getFirst()).isInstanceOf(LyricsChange.class);
        return (LyricsChange) notification.getMutations().getFirst();
    }

    private LayoutChange captureSingleLayoutChange() {
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().getFirst()).isInstanceOf(LayoutChange.class);
        return (LayoutChange) notification.getMutations().getFirst();
    }

    private void verifyNoNotificationPosted() {
        messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
    }
}
