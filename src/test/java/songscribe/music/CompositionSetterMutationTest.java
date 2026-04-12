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
import songscribe.message.notification.CompositionDidChangeNotification;

class CompositionSetterMutationTest extends UnitTest {

    private Composition composition;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so the constructor's bus interactions
        // go to the real (unobserved) bus, not the mock.
        composition = new Composition();
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
            var oldTitle = composition.getTitle();
            composition.setTitle("New Title");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.TITLE);
            assertThat(mutation.oldValue()).isEqualTo(oldTitle);
            assertThat(mutation.newValue()).isEqualTo("New Title");
        }

        @Test
        void testSetTitleSameValuePostsNothing() {
            composition.setTitle(composition.getTitle());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetPlacePostsMutation() {
            var oldPlace = composition.getPlace();
            composition.setPlace("Paris");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.PLACE);
            assertThat(mutation.oldValue()).isEqualTo(oldPlace);
            assertThat(mutation.newValue()).isEqualTo("Paris");
        }

        @Test
        void testSetPlaceSameValuePostsNothing() {
            composition.setPlace(composition.getPlace());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetYearPostsMutation() {
            var oldYear = composition.getYear();
            composition.setYear("2024");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.YEAR);
            assertThat(mutation.oldValue()).isEqualTo(oldYear);
            assertThat(mutation.newValue()).isEqualTo("2024");
        }

        @Test
        void testSetYearSameValuePostsNothing() {
            composition.setYear(composition.getYear());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetAttributionPostsMutation() {
            var oldAttribution = composition.getAttribution();
            composition.setAttribution("Composed by Someone");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.ATTRIBUTION);
            assertThat(mutation.oldValue()).isEqualTo(oldAttribution);
            assertThat(mutation.newValue()).isEqualTo("Composed by Someone");
        }

        @Test
        void testSetAttributionSameValuePostsNothing() {
            composition.setAttribution(composition.getAttribution());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetNumberPostsMutation() {
            var oldNumber = composition.getNumber();
            composition.setNumber("42");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.NUMBER);
            assertThat(mutation.oldValue()).isEqualTo(oldNumber);
            assertThat(mutation.newValue()).isEqualTo("42");
        }

        @Test
        void testSetNumberSameValuePostsNothing() {
            composition.setNumber(composition.getNumber());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetFootnotesPostsMutation() {
            var oldFootnotes = composition.getFootnotes();
            composition.setFootnotes("See note 1");

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.FOOTNOTES);
            assertThat(mutation.oldValue()).isEqualTo(oldFootnotes);
            assertThat(mutation.newValue()).isEqualTo("See note 1");
        }

        @Test
        void testSetFootnotesSameValuePostsNothing() {
            composition.setFootnotes(composition.getFootnotes());
            verifyNoNotificationPosted();
        }
    }

    @Nested
    class NumericAndTypedMetadataSetters {

        @Test
        void testSetMonthPostsMutation() {
            var oldMonth = composition.getMonth();
            composition.setMonth(oldMonth + 1);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.MONTH);
            assertThat(mutation.oldValue()).isEqualTo(oldMonth);
            assertThat(mutation.newValue()).isEqualTo(oldMonth + 1);
        }

        @Test
        void testSetMonthSameValuePostsNothing() {
            composition.setMonth(composition.getMonth());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetDayPostsMutation() {
            var oldDay = composition.getDay();
            composition.setDay(oldDay + 1);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.DAY);
            assertThat(mutation.oldValue()).isEqualTo(oldDay);
            assertThat(mutation.newValue()).isEqualTo(oldDay + 1);
        }

        @Test
        void testSetDaySameValuePostsNothing() {
            composition.setDay(composition.getDay());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetTempoPostsMutation() {
            var oldTempo = composition.getTempo();
            var newTempo = new Tempo();
            newTempo.setVisibleTempo(oldTempo.getVisibleTempo() + 10);

            composition.setTempo(newTempo);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.TEMPO);
            assertThat(mutation.oldValue()).isSameAs(oldTempo);
            assertThat(mutation.newValue()).isSameAs(newTempo);
        }

        @Test
        void testSetTempoSameValuePostsNothing() {
            composition.setTempo(composition.getTempo());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetDefaultKeyAccidentalCountPostsMutation() {
            var oldCount = composition.getDefaultKeyAccidentalCount();
            composition.setDefaultKeyAccidentalCount(oldCount - 1);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.DEFAULT_KEY_ACCIDENTAL_COUNT);
            assertThat(mutation.oldValue()).isEqualTo(oldCount);
            assertThat(mutation.newValue()).isEqualTo(oldCount - 1);
        }

        @Test
        void testSetDefaultKeyAccidentalCountSameValuePostsNothing() {
            composition.setDefaultKeyAccidentalCount(composition.getDefaultKeyAccidentalCount());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetDefaultKeyTypePostsMutation() {
            var oldKeyType = composition.getDefaultKeyType();
            var newKeyType = oldKeyType == KeyType.FLATS ? KeyType.SHARPS : KeyType.FLATS;

            composition.setDefaultKeyType(newKeyType);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.DEFAULT_KEY_TYPE);
            assertThat(mutation.oldValue()).isEqualTo(oldKeyType);
            assertThat(mutation.newValue()).isEqualTo(newKeyType);
        }

        @Test
        void testSetDefaultKeyTypeSameValuePostsNothing() {
            composition.setDefaultKeyType(composition.getDefaultKeyType());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetUnofficialTranslationPostsMutation() {
            var oldValue = composition.isUnofficialTranslation();
            composition.setUnofficialTranslation(!oldValue);

            var mutation = captureSingleMetadataChange();
            assertThat(mutation.field()).isEqualTo(MetadataField.UNOFFICIAL_TRANSLATION);
            assertThat(mutation.oldValue()).isEqualTo(oldValue);
            assertThat(mutation.newValue()).isEqualTo(!oldValue);
        }

        @Test
        void testSetUnofficialTranslationSameValuePostsNothing() {
            composition.setUnofficialTranslation(composition.isUnofficialTranslation());
            verifyNoNotificationPosted();
        }
    }

    // -----------------------------------------------------------------------
    // Lyrics setters
    // -----------------------------------------------------------------------

    @Nested
    class LyricsSetters {

        @Test
        void testSetLyricsPostsMutation() {
            var oldLyrics = composition.getLyrics();
            composition.setLyrics("la-la-la");

            var mutation = captureSingleLyricsChange();
            assertThat(mutation.field()).isEqualTo(LyricsField.MAIN);
            assertThat(mutation.oldText()).isEqualTo(oldLyrics);
            assertThat(mutation.newText()).isEqualTo("la-la-la");
        }

        @Test
        void testSetLyricsSameValuePostsNothing() {
            composition.setLyrics(composition.getLyrics());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetUnderLyricsPostsMutation() {
            var oldLyrics = composition.getUnderLyrics();
            composition.setUnderLyrics("under text");

            var mutation = captureSingleLyricsChange();
            assertThat(mutation.field()).isEqualTo(LyricsField.UNDER);
            assertThat(mutation.oldText()).isEqualTo(oldLyrics);
            assertThat(mutation.newText()).isEqualTo("under text");
        }

        @Test
        void testSetUnderLyricsSameValuePostsNothing() {
            composition.setUnderLyrics(composition.getUnderLyrics());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetBanglaLyricsPostsMutation() {
            var oldLyrics = composition.getBanglaLyrics();
            composition.setBanglaLyrics("bangla text");

            var mutation = captureSingleLyricsChange();
            assertThat(mutation.field()).isEqualTo(LyricsField.BANGLA);
            assertThat(mutation.oldText()).isEqualTo(oldLyrics);
            assertThat(mutation.newText()).isEqualTo("bangla text");
        }

        @Test
        void testSetBanglaLyricsSameValuePostsNothing() {
            composition.setBanglaLyrics(composition.getBanglaLyrics());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetTranslatedLyricsPostsMutation() {
            var oldLyrics = composition.getTranslatedLyrics();
            composition.setTranslatedLyrics("translated text");

            var mutation = captureSingleLyricsChange();
            assertThat(mutation.field()).isEqualTo(LyricsField.TRANSLATED);
            assertThat(mutation.oldText()).isEqualTo(oldLyrics);
            assertThat(mutation.newText()).isEqualTo("translated text");
        }

        @Test
        void testSetTranslatedLyricsSameValuePostsNothing() {
            composition.setTranslatedLyrics(composition.getTranslatedLyrics());
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
            var oldFont = composition.getTitleFont();
            composition.setTitleFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.TITLE);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetTitleFontSameValuePostsNothing() {
            composition.setTitleFont(composition.getTitleFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetLyricsFontPostsMutation() {
            var oldFont = composition.getLyricsFont();
            composition.setLyricsFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.LYRICS);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetLyricsFontSameValuePostsNothing() {
            composition.setLyricsFont(composition.getLyricsFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetAttributionFontPostsMutation() {
            var oldFont = composition.getAttributionFont();
            composition.setAttributionFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.ATTRIBUTION);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetAttributionFontSameValuePostsNothing() {
            composition.setAttributionFont(composition.getAttributionFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetAnnotationFontPostsMutation() {
            var oldFont = composition.getAnnotationFont();
            composition.setAnnotationFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.ANNOTATION);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetAnnotationFontSameValuePostsNothing() {
            composition.setAnnotationFont(composition.getAnnotationFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetBanglaFontPostsMutation() {
            var oldFont = composition.getBanglaFont();
            composition.setBanglaFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.BANGLA);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetBanglaFontSameValuePostsNothing() {
            composition.setBanglaFont(composition.getBanglaFont());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetFootnoteFontPostsMutation() {
            var oldFont = composition.getFootnoteFont();
            composition.setFootnoteFont(ALT_FONT);

            var mutation = captureSingleFontChange();
            assertThat(mutation.field()).isEqualTo(FontField.FOOTNOTE);
            assertThat(mutation.oldFont()).isSameAs(oldFont);
            assertThat(mutation.newFont()).isSameAs(ALT_FONT);
        }

        @Test
        void testSetFootnoteFontSameValuePostsNothing() {
            composition.setFootnoteFont(composition.getFootnoteFont());
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
            var oldWidth = composition.getLineWidthSs();
            composition.setLineWidthSs(oldWidth + 1.0);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.LINE_WIDTH_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldWidth);
            assertThat(mutation.newValue()).isEqualTo(oldWidth + 1.0);
        }

        @Test
        void testSetLineWidthSsSameValuePostsNothing() {
            composition.setLineWidthSs(composition.getLineWidthSs());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetTopPaddingSsPostsMutation() {
            var oldPadding = composition.getTopPaddingSs();
            composition.setTopPaddingSs(oldPadding + 2.0, false);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.TOP_PADDING_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldPadding);
            assertThat(mutation.newValue()).isEqualTo(oldPadding + 2.0);
        }

        @Test
        void testSetAttributionStartYSsPostsMutation() {
            var oldY = composition.getAttributionStartYSs();
            composition.setAttributionStartYSs(oldY + 3.0);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.ATTRIBUTION_START_Y_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldY);
            assertThat(mutation.newValue()).isEqualTo(oldY + 3.0);
        }

        @Test
        void testSetAttributionStartYSsSameValuePostsNothing() {
            composition.setAttributionStartYSs(composition.getAttributionStartYSs());
            verifyNoNotificationPosted();
        }

        @Test
        void testSetRowHeightAdjustmentSsPostsMutation() {
            var oldAdjustment = composition.getRowHeightAdjustmentSs();
            composition.setRowHeightAdjustmentSs(oldAdjustment + 1.5);

            var mutation = captureSingleLayoutChange();
            assertThat(mutation.field()).isEqualTo(LayoutField.ROW_HEIGHT_ADJUSTMENT_SS);
            assertThat(mutation.oldValue()).isEqualTo(oldAdjustment);
            assertThat(mutation.newValue()).isEqualTo(oldAdjustment + 1.5);
        }

        @Test
        void testSetRowHeightAdjustmentSsSameValuePostsNothing() {
            composition.setRowHeightAdjustmentSs(composition.getRowHeightAdjustmentSs());
            verifyNoNotificationPosted();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private CompositionDidChangeNotification captureSingleDidChange() {
        var captor = ArgumentCaptor.forClass(Message.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof CompositionDidChangeNotification)
            .map(m -> (CompositionDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one CompositionDidChangeNotification, got: %s", didChanges)
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
