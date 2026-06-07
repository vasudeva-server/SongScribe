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

import javax.swing.JTextArea;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.layout.PageModel;
import songscribe.ui.component.NonEmptyGuard;
import songscribe.util.GraphicUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SongSettingsDialog} static helpers:
 * {@code TextTab.parseIntFieldOrNull}, {@code TextTab.isTextTabUnchanged},
 * {@code TextTab.extractLyricsTitle}, and {@code MusicTab.validateLineWidthText}.
 *
 * <p>Row 12 (TextTab.isValidData) is covered here via a direct test of
 * {@link NonEmptyGuard#validate()} since {@code isValidData} is a one-line
 * delegation to that method.
 */
class SongSettingsDialogTest extends UnitTest {

    // ── Row 11: TextTab.parseIntFieldOrNull — number/year field validation ──

    @Nested
    class ParseIntFieldOrNull {

        @Test
        void testEmptyTextReturnsEmptyString() {
            // Empty string is a valid "not provided" value — must pass through unchanged.
            assertThat(SongSettingsDialog.parseIntFieldOrNull(""))
                .as("empty text is valid; returned as-is")
                .isEqualTo("");
        }

        @Test
        void testNumericTextReturnsItself() {
            assertThat(SongSettingsDialog.parseIntFieldOrNull("42"))
                .as("valid integer text is returned unchanged")
                .isEqualTo("42");
        }

        @Test
        void testNonNumericTextReturnsNull() {
            assertThat(SongSettingsDialog.parseIntFieldOrNull("abc"))
                .as("non-numeric text triggers parse failure and returns null")
                .isNull();
        }

        @Test
        void testDecimalNumberReturnsNull() {
            // "1.5" is not an integer — must be rejected.
            assertThat(SongSettingsDialog.parseIntFieldOrNull("1.5"))
                .as("decimal string is not a valid integer field value")
                .isNull();
        }

        @Test
        void testNegativeIntegerReturnsItself() {
            // Negative numbers are parseable as int.
            assertThat(SongSettingsDialog.parseIntFieldOrNull("-10"))
                .as("negative integer text is parseable and returned as-is")
                .isEqualTo("-10");
        }
    }

    // ── Row 10: TextTab.isTextTabUnchanged — change-detection guard ──

    @Nested
    class IsTextTabUnchanged {

        /**
         * Returns {@code true} when all parameters match the default Song values,
         * meaning the helper correctly detects that nothing has changed.
         */
        @Test
        void testReturnsTrueWhenAllFieldsMatchSong() {
            var song = new Song();
            var result = SongSettingsDialog.isTextTabUnchanged(
                song,
                song.getTitle(),
                song.getPlace(),
                song.getYear(),
                song.getNumber(),
                song.getMonth(),
                song.getDay(),
                song.getComposer(),
                song.getLyricist(),
                song.getLyricsSource(),
                song.isArrangement(),
                song.isUnofficialTranslation()
            );

            assertThat(result)
                .as("all fields identical to song — no change detected")
                .isTrue();
        }

        @Test
        void testReturnsFalseWhenTitleDiffers() {
            var song = new Song();
            var result = SongSettingsDialog.isTextTabUnchanged(
                song,
                "Different Title",
                song.getPlace(),
                song.getYear(),
                song.getNumber(),
                song.getMonth(),
                song.getDay(),
                song.getComposer(),
                song.getLyricist(),
                song.getLyricsSource(),
                song.isArrangement(),
                song.isUnofficialTranslation()
            );

            assertThat(result)
                .as("changed title must be detected as a change")
                .isFalse();
        }

        @Test
        void testReturnsFalseWhenYearIsNull() {
            // year=null means the year field had a non-numeric value (parse failed).
            // The song's stored year is "" (default), which differs from null.
            var song = new Song();
            var result = SongSettingsDialog.isTextTabUnchanged(
                song,
                song.getTitle(),
                song.getPlace(),
                null,  // invalid year
                song.getNumber(),
                song.getMonth(),
                song.getDay(),
                song.getComposer(),
                song.getLyricist(),
                song.getLyricsSource(),
                song.isArrangement(),
                song.isUnofficialTranslation()
            );

            assertThat(result)
                .as("null year (parse error) differs from stored empty year — must be detected")
                .isFalse();
        }

        @Test
        void testReturnsFalseWhenArrangementDiffers() {
            var song = new Song();
            var result = SongSettingsDialog.isTextTabUnchanged(
                song,
                song.getTitle(),
                song.getPlace(),
                song.getYear(),
                song.getNumber(),
                song.getMonth(),
                song.getDay(),
                song.getComposer(),
                song.getLyricist(),
                song.getLyricsSource(),
                !song.isArrangement(),  // toggled
                song.isUnofficialTranslation()
            );

            assertThat(result)
                .as("toggled arrangement flag must be detected as a change")
                .isFalse();
        }
    }

    // ── Row 12: TextTab.isValidData → NonEmptyGuard.validate() ──

    @Nested
    class IsValidData {

        /**
         * {@code TextTab.isValidData} delegates entirely to
         * {@code NonEmptyGuard.validate()}, so we test that method directly here
         * with suppressed dialogs (set by {@link UnitTest#suppressDialogs}).
         *
         * <p>We use the 7-arg {@link NonEmptyGuard} constructor (with a default-value
         * key) because its suppressed-dialog path ({@code showOptionDialog}) checks
         * suppression before calling {@code Strings.get}, unlike the 4-arg
         * constructor's {@code showWarningMessage} which calls {@code Strings.get}
         * unconditionally.
         */
        private NonEmptyGuard guardFor(JTextArea field) {
            // Mirror the TextTab constructor: guard with default-value option.
            return new NonEmptyGuard(
                field,
                field,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE,
                Strings.DOCUMENT_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_USE_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_CONTINUE_EDITING
            );
        }

        @Test
        void testNonEmptyTitleReturnsTrueWithoutDialog() {
            var titleField = new JTextArea("My Song");
            var guard = guardFor(titleField);

            assertThat(guard.validate())
                .as("non-blank title field: isValidData → true")
                .isTrue();
        }

        @Test
        void testEmptyTitleReturnsFalseWhenDialogSuppressed() {
            // With dialogs suppressed, showOptionDialog returns CLOSED_OPTION
            // (not useDefaultIndex=1), so validate() returns false.
            var titleField = new JTextArea("");
            var guard = guardFor(titleField);

            assertThat(guard.validate())
                .as("blank title field: isValidData → false (dialog suppressed)")
                .isFalse();
        }

        @Test
        void testBlankWhitespaceTitleReturnsFalse() {
            var titleField = new JTextArea("   ");
            var guard = guardFor(titleField);

            assertThat(guard.validate())
                .as("whitespace-only title is blank: isValidData → false")
                .isFalse();
        }
    }

    // ── Row 13: TextTab.extractLyricsTitle — word extraction and capitalisation ──

    @Nested
    class ExtractLyricsTitle {

        @Test
        void testNormalLyricsExtractsFirstWord() {
            // "Hello world" with maxWords=1 should return "Hello"
            assertThat(SongSettingsDialog.extractLyricsTitle("Hello world", 1))
                .as("single word extracted from normal lyrics")
                .isEqualTo("Hello");
        }

        @Test
        void testNormalLyricsExtractsMultipleWords() {
            // "Hello world foo" with maxWords=2 should return "Hello World"
            assertThat(SongSettingsDialog.extractLyricsTitle("Hello world foo", 2))
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
            // maxWords break (which only fires on space/newline).
            // "hel--lo world" with maxWords=1: after "--", wordCount=1 but the
            // loop continues; the break fires at the space before "world".
            // Result: "hel-Lo" (capital L because firstLetter=true after "--").
            assertThat(SongSettingsDialog.extractLyricsTitle("hel--lo world", 1))
                .as("double-hyphen increments wordCount but break only fires on space; includes text up to space")
                .isEqualTo("hel-Lo");
        }

        @Test
        void testAllUnderscoreLyricsThrowsStringIndexOutOfBoundsException() {
            // Bug: when lyrics consist only of separator characters (underscores),
            // the buffer stays empty and words.charAt(words.length() - 1) throws
            // StringIndexOutOfBoundsException. This test documents the existing bug.
            assertThatThrownBy(
                () -> SongSettingsDialog.extractLyricsTitle("___", 4)
            )
                .as("all-underscore lyrics expose the empty-buffer IOOBE bug")
                .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        void testFewerWordsThanMaxCapitalisesAfterSpace() {
            // maxWords=10 but only 2 words exist — all text is returned.
            // The algorithm capitalises the first character of each word after a
            // space separator, so "world" becomes "World".
            assertThat(SongSettingsDialog.extractLyricsTitle("Hello world", 10))
                .as("second word is capitalised because firstLetter=true after the space separator")
                .isEqualTo("Hello World");
        }
    }

    // ── Row 16: MusicTab.validateLineWidthText — parse, unit conversion, range ──

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

        @Test
        void testEmptyTextReturnsMinusOne() {
            assertThat(SongSettingsDialog.validateLineWidthText("", false))
                .as("empty text is unparseable — returns -1")
                .isEqualTo(-1.0);
        }

        @Test
        void testNonNumericTextReturnsMinusOne() {
            assertThat(SongSettingsDialog.validateLineWidthText("abc", false))
                .as("non-numeric text returns -1")
                .isEqualTo(-1.0);
        }

        @Test
        void testValueBelowMinInchesReturnsMinusOne() {
            // Just below the minimum (5.0 inches).
            var belowMin = String.valueOf(MIN_INCHES - BOUNDARY_STEP);
            assertThat(SongSettingsDialog.validateLineWidthText(belowMin, false))
                .as("value below min inches returns -1")
                .isEqualTo(-1.0);
        }

        @Test
        void testValueAboveMaxInchesReturnsMinusOne() {
            // Just above the maximum (7.77 inches).
            var aboveMax = String.valueOf(MAX_INCHES + BOUNDARY_STEP);
            assertThat(SongSettingsDialog.validateLineWidthText(aboveMax, false))
                .as("value above max inches returns -1")
                .isEqualTo(-1.0);
        }

        @Test
        void testValidInchesReturnsWidthInInches() {
            // Midpoint between min and max is a safe valid value.
            var midInches = (MIN_INCHES + MAX_INCHES) / 2;
            var text = String.valueOf(midInches);
            assertThat(SongSettingsDialog.validateLineWidthText(text, false))
                .as("valid inches value returned as-is")
                .isCloseTo(midInches, org.assertj.core.data.Offset.offset(COMPARISON_TOLERANCE));
        }

        @Test
        void testValidCentimetresReturnsWidthInInches() {
            // VALID_INCHES * 2.54 cm — a valid value well within range.
            var cm = VALID_INCHES * GraphicUtils.CM_PER_INCH;
            var text = String.valueOf(cm);
            assertThat(SongSettingsDialog.validateLineWidthText(text, true))
                .as("valid cm value is converted to inches and returned")
                .isCloseTo(VALID_INCHES, org.assertj.core.data.Offset.offset(COMPARISON_TOLERANCE));
        }

        @Test
        void testCentimetreValueBelowMinReturnsMinusOne() {
            // Below the min when converted from cm to inches.
            var belowMinCm = (MIN_INCHES - BOUNDARY_STEP) * GraphicUtils.CM_PER_INCH;
            var text = String.valueOf(belowMinCm);
            assertThat(SongSettingsDialog.validateLineWidthText(text, true))
                .as("cm value that converts below min inches returns -1")
                .isEqualTo(-1.0);
        }
    }
}
