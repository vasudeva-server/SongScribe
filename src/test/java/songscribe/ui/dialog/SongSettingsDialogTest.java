/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.layout.PageModel;
import songscribe.message.MessageCenter;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.util.GraphicUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * Unit tests for {@link SongSettingsDialog}'s package-private static helpers,
 * which carry the dialog's pure logic so it stays directly testable even though
 * the tabs that drive them are Swing-bound private inner classes:
 * {@code extractLyricsTitle}, {@code validateLineWidthText},
 * {@code canonicalKeySelectionFrom}, and {@code applyMusicTabChanges}.
 *
 * <p>Row 19: {@link SongSettingsDialog.KeyCellRenderer#SELECTIONS} — 15
 * entries in canonical order — is fully covered below.
 *
 * <p>The title field's empty-value guard (a {@link songscribe.ui.component.NonEmptyGuard}
 * installed by {@code TitleTab}) is not re-tested here; its blank/non-blank
 * contract is covered comprehensively by {@code NonEmptyGuardTest}.
 */
class SongSettingsDialogTest extends UnitTest {

    // ── extractLyricsTitle — word extraction and capitalisation ──

    @Nested
    class ExtractLyricsTitle {

        private static final int TWO_WORDS = 2;
        private static final int MORE_WORDS_THAN_PRESENT = 10;
        private static final int ANY_MAX_WORDS = 4;

        @Test
        void testNormalLyricsExtractsFirstWord() {
            // "Hello world" with maxWords=1 should return "Hello".
            assertThat(SongSettingsDialog.extractLyricsTitle("Hello world", 1))
                .as("single word extracted from normal lyrics")
                .isEqualTo("Hello");
        }

        @Test
        void testNormalLyricsExtractsMultipleWords() {
            // "Hello world foo" with maxWords=2 should return "Hello World".
            assertThat(SongSettingsDialog.extractLyricsTitle("Hello world foo", TWO_WORDS))
                .as("two words extracted and second word capitalised")
                .isEqualTo("Hello World");
        }

        @Test
        void testNewlineTreatedAsWordSeparator() {
            assertThat(SongSettingsDialog.extractLyricsTitle("Hello\nworld", 1))
                .as("newline acts as a word separator")
                .isEqualTo("Hello");
        }

        @Test
        void testUnderscoresAreIgnored() {
            // Underscores are melisma markers and are skipped.
            assertThat(SongSettingsDialog.extractLyricsTitle("Hel_lo world", 1))
                .as("underscores are silently skipped; surrounding text is kept")
                .isEqualTo("Hello");
        }

        @Test
        void testHyphenPairIncrementsWordCountButDoesNotBreak() {
            // A double-hyphen increments wordCount but does NOT trigger the
            // maxWords break (which only fires on space/newline). For
            // "hel--lo world" with maxWords=1, the break fires at the space
            // before "world", yielding "hel-Lo" (capital L because firstLetter
            // is set after "--").
            assertThat(SongSettingsDialog.extractLyricsTitle("hel--lo world", 1))
                .as("double-hyphen increments wordCount but break only fires on space")
                .isEqualTo("hel-Lo");
        }

        @Test
        void testAllUnderscoreLyricsReturnsEmptyString() {
            // Lyrics consisting only of separator characters (underscores) leave
            // the buffer empty. The trailing-character trim must guard the empty
            // buffer rather than throwing StringIndexOutOfBoundsException.
            assertThat(SongSettingsDialog.extractLyricsTitle("___", ANY_MAX_WORDS))
                .as("all-underscore lyrics yield an empty title without throwing")
                .isEqualTo("");
        }

        @Test
        void testFewerWordsThanMaxCapitalisesAfterSpace() {
            // maxWords larger than the word count returns all the text; the
            // algorithm capitalises the first character after a space separator,
            // so "world" becomes "World".
            assertThat(SongSettingsDialog.extractLyricsTitle("Hello world", MORE_WORDS_THAN_PRESENT))
                .as("second word is capitalised because firstLetter is set after the space separator")
                .isEqualTo("Hello World");
        }
    }

    // ── validateLineWidthText — parse, unit conversion, range ──

    @Nested
    class ValidateLineWidthText {

        private static final double MIN_INCHES = PageModel.MIN_LINE_WIDTH_INCHES;
        private static final double MAX_INCHES = PageModel.MAX_LINE_WIDTH_INCHES;

        /** Small delta used to step just outside a boundary. */
        private static final double BOUNDARY_STEP = 0.01;

        /** A valid inch value that falls comfortably within [MIN, MAX]. */
        private static final double VALID_INCHES = 6.0;

        /** Floating-point comparison tolerance. */
        private static final double COMPARISON_TOLERANCE = 0.0001;

        /** Sentinel returned for empty, unparseable, or out-of-range text. */
        private static final double INVALID = -1.0;

        @Test
        void testEmptyTextReturnsMinusOne() {
            assertThat(SongSettingsDialog.validateLineWidthText("", false))
                .as("empty text is unparseable — returns -1")
                .isEqualTo(INVALID);
        }

        @Test
        void testNonNumericTextReturnsMinusOne() {
            assertThat(SongSettingsDialog.validateLineWidthText("abc", false))
                .as("non-numeric text returns -1")
                .isEqualTo(INVALID);
        }

        @Test
        void testValueBelowMinInchesReturnsMinusOne() {
            var belowMin = String.valueOf(MIN_INCHES - BOUNDARY_STEP);
            assertThat(SongSettingsDialog.validateLineWidthText(belowMin, false))
                .as("value below min inches returns -1")
                .isEqualTo(INVALID);
        }

        @Test
        void testValueAboveMaxInchesReturnsMinusOne() {
            var aboveMax = String.valueOf(MAX_INCHES + BOUNDARY_STEP);
            assertThat(SongSettingsDialog.validateLineWidthText(aboveMax, false))
                .as("value above max inches returns -1")
                .isEqualTo(INVALID);
        }

        @Test
        void testValidInchesReturnsWidthInInches() {
            var midInches = (MIN_INCHES + MAX_INCHES) / 2;
            var text = String.valueOf(midInches);
            assertThat(SongSettingsDialog.validateLineWidthText(text, false))
                .as("valid inches value returned as-is")
                .isCloseTo(midInches, org.assertj.core.data.Offset.offset(COMPARISON_TOLERANCE));
        }

        @Test
        void testValidCentimetresReturnsWidthInInches() {
            var cm = VALID_INCHES * GraphicUtils.CM_PER_INCH;
            var text = String.valueOf(cm);
            assertThat(SongSettingsDialog.validateLineWidthText(text, true))
                .as("valid cm value is converted to inches and returned")
                .isCloseTo(VALID_INCHES, org.assertj.core.data.Offset.offset(COMPARISON_TOLERANCE));
        }

        @Test
        void testCentimetreValueBelowMinReturnsMinusOne() {
            var belowMinCm = (MIN_INCHES - BOUNDARY_STEP) * GraphicUtils.CM_PER_INCH;
            var text = String.valueOf(belowMinCm);
            assertThat(SongSettingsDialog.validateLineWidthText(text, true))
                .as("cm value that converts below min inches returns -1")
                .isEqualTo(INVALID);
        }
    }

    // ── canonicalKeySelectionFrom — (any, 0) → (FLATS, 0) canonicalization ──

    @Nested
    class CanonicalKeySelectionFrom {

        private static final int SHARP_COUNT = 3;
        private static final int FLAT_COUNT = 2;

        @Test
        void testZeroAccidentalsCanonicalizesToFlatsZero() {
            // When accidental count is 0, the canonical type is always FLATS.
            var song = new Song();
            song.keySignatureDidChange(new KeySignatureDidChangeNotification(null, KeyType.FLATS, 0));

            var selection = SongSettingsDialog.canonicalKeySelectionFrom(song);

            assertThat(selection.keyType())
                .as("zero accidentals always maps to FLATS canonical type")
                .isEqualTo(KeyType.FLATS);
            assertThat(selection.count())
                .as("accidental count is preserved as 0")
                .isEqualTo(0);
        }

        @Test
        void testZeroSharpsAlsoCanonicalizesToFlatsZero() {
            // The combo has no "(SHARPS, 0)" entry, only "(FLATS, 0)", so a
            // (SHARPS, 0) song key must canonicalize to FLATS.
            var song = new Song();
            song.setDefaultKeyType(KeyType.SHARPS);
            song.setDefaultKeyAccidentalCount(0);

            var selection = SongSettingsDialog.canonicalKeySelectionFrom(song);

            assertThat(selection.keyType())
                .as("(SHARPS, 0) canonicalizes to FLATS type")
                .isEqualTo(KeyType.FLATS);
            assertThat(selection.count())
                .as("accidental count is still 0")
                .isEqualTo(0);
        }

        @Test
        void testNonZeroAccidentalCountPreservesKeyType() {
            var song = new Song();
            song.keySignatureDidChange(new KeySignatureDidChangeNotification(null, KeyType.SHARPS, SHARP_COUNT));

            var selection = SongSettingsDialog.canonicalKeySelectionFrom(song);

            assertThat(selection.keyType())
                .as("non-zero sharp count preserves SHARPS key type")
                .isEqualTo(KeyType.SHARPS);
            assertThat(selection.count())
                .as("accidental count is preserved")
                .isEqualTo(SHARP_COUNT);
        }

        @Test
        void testNonZeroFlatCountPreservesKeyType() {
            var song = new Song();
            song.keySignatureDidChange(new KeySignatureDidChangeNotification(null, KeyType.FLATS, FLAT_COUNT));

            var selection = SongSettingsDialog.canonicalKeySelectionFrom(song);

            assertThat(selection.keyType())
                .as("non-zero flat count preserves FLATS key type")
                .isEqualTo(KeyType.FLATS);
            assertThat(selection.count())
                .as("accidental count is preserved")
                .isEqualTo(FLAT_COUNT);
        }
    }

    // ── applyMusicTabChanges — change-detection and notification posting ──

    @Nested
    class ApplyMusicTabChanges {

        private static final int TEMPO_DELTA = 20;
        private static final int SHARPS_COUNT = 2;
        private static final int FLATS_COUNT = 3;
        private static final int NO_POSTS = 0;
        private static final int ONE_POST = 1;

        private Song song;
        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            // Construct Song before mocking so its constructor bus operations go to the real bus.
            song = new Song();
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        /** Returns the song's current effective tempo values as a convenience. */
        private Tempo currentTempo() {
            return song.getEffectiveTempo();
        }

        @Test
        void testNoChangePostsNoMessage() {
            // When all values match the song's current state, no notification is posted.
            var tempo = currentTempo();

            SongSettingsDialog.applyMusicTabChanges(
                song,
                tempo.getTempoType(),
                tempo.getVisibleTempo(),
                tempo.getTempoDescription(),
                tempo.shouldShowTempo(),
                SongSettingsDialog.canonicalKeySelectionFrom(song)
            );

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(NO_POSTS));
        }

        @Test
        void testTempoOnlyChangePostsTempoNotification() {
            var tempo = currentTempo();
            var newVisibleTempo = tempo.getVisibleTempo() + TEMPO_DELTA;

            SongSettingsDialog.applyMusicTabChanges(
                song,
                tempo.getTempoType(),
                newVisibleTempo,
                tempo.getTempoDescription(),
                tempo.shouldShowTempo(),
                SongSettingsDialog.canonicalKeySelectionFrom(song)
            );

            messageCenterMock.verify(
                () -> MessageCenter.post(any(TempoDidChangeNotification.class)),
                times(ONE_POST)
            );
            messageCenterMock.verify(
                () -> MessageCenter.post(any(KeySignatureDidChangeNotification.class)),
                times(NO_POSTS)
            );
        }

        @Test
        void testKeyOnlyChangePostsKeyNotification() {
            var tempo = currentTempo();
            // Change to sharps (default is FLATS, 0).
            var newKey = new SongSettingsDialog.KeySelection(KeyType.SHARPS, SHARPS_COUNT);

            SongSettingsDialog.applyMusicTabChanges(
                song,
                tempo.getTempoType(),
                tempo.getVisibleTempo(),
                tempo.getTempoDescription(),
                tempo.shouldShowTempo(),
                newKey
            );

            messageCenterMock.verify(
                () -> MessageCenter.post(any(KeySignatureDidChangeNotification.class)),
                times(ONE_POST)
            );
            messageCenterMock.verify(
                () -> MessageCenter.post(any(TempoDidChangeNotification.class)),
                times(NO_POSTS)
            );
        }

        @Test
        void testBothChangedPostsBothNotifications() {
            var tempo = currentTempo();
            var newVisibleTempo = tempo.getVisibleTempo() + TEMPO_DELTA;
            var newKey = new SongSettingsDialog.KeySelection(KeyType.FLATS, FLATS_COUNT);

            SongSettingsDialog.applyMusicTabChanges(
                song,
                tempo.getTempoType(),
                newVisibleTempo,
                tempo.getTempoDescription(),
                tempo.shouldShowTempo(),
                newKey
            );

            messageCenterMock.verify(
                () -> MessageCenter.post(any(TempoDidChangeNotification.class)),
                times(ONE_POST)
            );
            messageCenterMock.verify(
                () -> MessageCenter.post(any(KeySignatureDidChangeNotification.class)),
                times(ONE_POST)
            );
        }
    }

    // ── gatedWordsDate — pure gating helper ──

    @Nested
    class GatedWordsDate {

        private static final String WORDS_YEAR = "1984";
        private static final int WORDS_MONTH = 6;
        private static final int WORDS_DAY = 27;

        /**
         * When the "Different date" checkbox is not selected, gatedWordsDate
         * must return ("", 0, 0) regardless of the input values, so an unchecked
         * box never contributes a date to the commit or preview.
         */
        @Test
        void testUnselectedReturnsEmptyDate() {
            var result = SongSettingsDialog.gatedWordsDate(false, WORDS_YEAR, WORDS_MONTH, WORDS_DAY);

            assertThat(result.year())
                .as("unselected: year must be empty")
                .isEmpty();
            assertThat(result.month())
                .as("unselected: month must be 0")
                .isZero();
            assertThat(result.day())
                .as("unselected: day must be 0")
                .isZero();
        }

        /**
         * When the checkbox is not selected and inputs are themselves empty/zero,
         * the result is still ("", 0, 0) — the gate fires unconditionally.
         */
        @Test
        void testUnselectedWithEmptyInputsStillReturnsEmptyDate() {
            var result = SongSettingsDialog.gatedWordsDate(false, "", 0, 0);

            assertThat(result.year()).isEmpty();
            assertThat(result.month()).isZero();
            assertThat(result.day()).isZero();
        }

        /**
         * When selected, the three inputs pass through unchanged.
         */
        @Test
        void testSelectedPassesInputsThrough() {
            var result = SongSettingsDialog.gatedWordsDate(true, WORDS_YEAR, WORDS_MONTH, WORDS_DAY);

            assertThat(result.year())
                .as("selected: year passes through")
                .isEqualTo(WORDS_YEAR);
            assertThat(result.month())
                .as("selected: month passes through")
                .isEqualTo(WORDS_MONTH);
            assertThat(result.day())
                .as("selected: day passes through")
                .isEqualTo(WORDS_DAY);
        }

        /**
         * When selected with empty/zero inputs, the empty/zero values pass through
         * — gatedWordsDate does not apply any default.
         */
        @Test
        void testSelectedWithEmptyInputsPassesEmptyThrough() {
            var result = SongSettingsDialog.gatedWordsDate(true, "", 0, 0);

            assertThat(result.year()).isEmpty();
            assertThat(result.month()).isZero();
            assertThat(result.day()).isZero();
        }
    }

    // ── DateInputRow pure-logic — enable/disable predicates ──

    @Nested
    class DateInputRowLogic {

        /**
         * dayEnabled returns false when the year is invalid, regardless of month.
         */
        @Test
        void testDayDisabledWhenYearInvalid() {
            assertThat(SongSettingsDialog.DateInputRow.dayEnabled(false, 6))
                .as("day disabled when year is invalid")
                .isFalse();
        }

        /**
         * dayEnabled returns false when month == 0 (no month selected), even if
         * the year is valid.
         */
        @Test
        void testDayDisabledWhenMonthIsZero() {
            assertThat(SongSettingsDialog.DateInputRow.dayEnabled(true, 0))
                .as("day disabled when month is 0")
                .isFalse();
        }

        /**
         * dayEnabled returns true only when the year is valid AND a month is selected.
         */
        @Test
        void testDayEnabledWhenYearValidAndMonthSelected() {
            assertThat(SongSettingsDialog.DateInputRow.dayEnabled(true, 6))
                .as("day enabled when year valid and month selected")
                .isTrue();
        }

        /**
         * dayEnabled is false for year-invalid + month-zero (most restrictive case).
         */
        @Test
        void testDayDisabledWhenBothYearInvalidAndMonthZero() {
            assertThat(SongSettingsDialog.DateInputRow.dayEnabled(false, 0))
                .as("day disabled when year invalid and month 0")
                .isFalse();
        }
    }

    // ── Row 19: KeyCellRenderer.SELECTIONS — 15 entries, canonical order ──

    @Nested
    class KeyCellRendererSelections {

        private static final int EXPECTED_SELECTION_COUNT = 15;
        private static final int MAX_ACCIDENTALS = 7;

        @Test
        void testSelectionsHasExactly15Entries() {
            assertThat(SongSettingsDialog.KeyCellRenderer.SELECTIONS)
                .as("1 no-accidentals + 7 flats + 7 sharps = 15 entries")
                .hasSize(EXPECTED_SELECTION_COUNT);
        }

        @Test
        void testFirstEntryIsNoAccidentals() {
            var first = SongSettingsDialog.KeyCellRenderer.SELECTIONS.getFirst();

            assertThat(first.keyType())
                .as("first entry has FLATS type (canonical no-accidentals)")
                .isEqualTo(KeyType.FLATS);
            assertThat(first.count())
                .as("first entry has 0 accidentals")
                .isEqualTo(0);
        }

        @Test
        void testFlatEntriesAreInOrderAfterNoAccidentals() {
            // Entries 1–7 must be FLATS with counts 1..7 in ascending order.
            var selections = SongSettingsDialog.KeyCellRenderer.SELECTIONS;

            for (var i = 1; i <= MAX_ACCIDENTALS; i++) {
                var sel = selections.get(i);
                assertThat(sel.keyType())
                    .as("entry %d should be FLATS", i)
                    .isEqualTo(KeyType.FLATS);
                assertThat(sel.count())
                    .as("entry %d should have %d flat(s)", i, i)
                    .isEqualTo(i);
            }
        }

        @Test
        void testSharpEntriesAreInOrderAfterFlats() {
            // Entries 8–14 must be SHARPS with counts 1..7 in ascending order.
            var selections = SongSettingsDialog.KeyCellRenderer.SELECTIONS;
            var sharpStart = MAX_ACCIDENTALS + 1;

            for (var i = 0; i < MAX_ACCIDENTALS; i++) {
                var sel = selections.get(sharpStart + i);
                assertThat(sel.keyType())
                    .as("entry %d should be SHARPS", sharpStart + i)
                    .isEqualTo(KeyType.SHARPS);
                assertThat(sel.count())
                    .as("entry %d should have %d sharp(s)", sharpStart + i, i + 1)
                    .isEqualTo(i + 1);
            }
        }
    }
}
