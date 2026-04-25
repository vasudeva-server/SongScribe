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
package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.FontField;
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
        void testSetAttributionPostsMutation() {
            var oldAttribution = song.getAttribution();
            song.setAttribution("Composed by Someone");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.ATTRIBUTION);
            assertThat(mutation.oldValue()).isEqualTo(oldAttribution);
            assertThat(mutation.newValue()).isEqualTo("Composed by Someone");
        }

        @Test
        void testSetAttributionSameValuePostsNothing() {
            song.setAttribution(song.getAttribution());
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
            var oldTempo = song.getTempo();
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
    }

    // -----------------------------------------------------------------------
    // Lyrics setters
    // -----------------------------------------------------------------------

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
    // Font setters
    // -----------------------------------------------------------------------

    @Nested
    class FontSetters {

        private static final Font ALT_FONT = new Font(Font.DIALOG, Font.BOLD, 99);

        @Test
        void testSetTitleFontPostsMutation() {
            var oldFont = song.getTitleFont();
            song.setTitleFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.TITLE);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetTitleFontSameValuePostsNothing() {
            song.setTitleFont(song.getTitleFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetLyricsFontPostsMutation() {
            var oldFont = song.getLyricsFont();
            song.setLyricsFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.LYRICS);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetLyricsFontSameValuePostsNothing() {
            song.setLyricsFont(song.getLyricsFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetAttributionFontPostsMutation() {
            var oldFont = song.getAttributionFont();
            song.setAttributionFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.ATTRIBUTION);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetAttributionFontSameValuePostsNothing() {
            song.setAttributionFont(song.getAttributionFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetAnnotationFontPostsMutation() {
            var oldFont = song.getAnnotationFont();
            song.setAnnotationFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.ANNOTATION);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetAnnotationFontSameValuePostsNothing() {
            song.setAnnotationFont(song.getAnnotationFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetBanglaFontPostsMutation() {
            var oldFont = song.getBanglaFont();
            song.setBanglaFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.BANGLA);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetBanglaFontSameValuePostsNothing() {
            song.setBanglaFont(song.getBanglaFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetFootnoteFontPostsMutation() {
            var oldFont = song.getFootnoteFont();
            song.setFootnoteFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.FOOTNOTE);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetFootnoteFontSameValuePostsNothing() {
            song.setFootnoteFont(song.getFootnoteFont());
            verifyNoNotificationPosted();
        }
    }

    // -----------------------------------------------------------------------
    // Layout setters
    // -----------------------------------------------------------------------

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
        void testSetTopPaddingSsPostsMutation() {
            var oldPadding = song.getTopPaddingSs();
            song.setTopPaddingSs(oldPadding + 2.0, false);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.TOP_PADDING_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldPadding);
            assertThat(mutation.newValue()).isEqualTo(oldPadding + 2.0);
        }

        @Test
        void testSetAttributionStartYSsPostsMutation() {
            var oldY = song.getAttributionStartYSs();
            song.setAttributionStartYSs(oldY + 3.0);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.ATTRIBUTION_START_Y_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldY);
            assertThat(mutation.newValue()).isEqualTo(oldY + 3.0);
        }

        @Test
        void testSetAttributionStartYSsSameValuePostsNothing() {
            song.setAttributionStartYSs(song.getAttributionStartYSs());
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

        return didChanges.get(0);
    }

    private MetadataChange captureSingleMetadataChange() {
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().get(0)).isInstanceOf(MetadataChange.class);
        return (MetadataChange) notification.getMutations().get(0);
    }

    private LyricsChange captureSingleLyricsChange() {
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().get(0)).isInstanceOf(LyricsChange.class);
        return (LyricsChange) notification.getMutations().get(0);
    }

    private FontChange captureSingleFontChange() {
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().get(0)).isInstanceOf(FontChange.class);
        return (FontChange) notification.getMutations().get(0);
    }

    private LayoutChange captureSingleLayoutChange() {
        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().get(0)).isInstanceOf(LayoutChange.class);
        return (LayoutChange) notification.getMutations().get(0);
    }

    private void verifyNoNotificationPosted() {
        messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
    }
}
