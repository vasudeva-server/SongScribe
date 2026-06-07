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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.layout.PageModel;
import songscribe.message.MessageCenter;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.ui.component.NonEmptyGuard;
import songscribe.util.GraphicUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SongSettingsDialog} static helpers:
 * {@code TextTab.parseIntFieldOrNull}, {@code TextTab.isTextTabUnchanged},
 * {@code TextTab.extractLyricsTitle}, {@code MusicTab.validateLineWidthText},
 * {@code canonicalKeySelectionFrom}, and {@code applyMusicTabChanges}.
 *
 * <p>Row 12 (TextTab.isValidData) is covered here via a direct test of
 * {@link NonEmptyGuard#validate()} since {@code isValidData} is a one-line
 * delegation to that method.
 *
 * <p>Rows 14–15 (TextTab.AddDateAndPlaceAction / getDateString): these methods
 * no longer exist in the production class; the date-formatting logic has moved
 * to {@code AttributionPane.buildSubAttributionLinesFromData} and is covered
 * comprehensively in {@code AttributionPaneTest}.
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

    // ── Row 17: canonicalKeySelectionFrom — (SHARPS, 0) → (FLATS, 0) canonicalization ──

    @Nested
    class CanonicalKeySelectionFrom {

        @Test
        void testZeroAccidentalsCanonicalizesToFlatsZero() {
            // When accidental count is 0, the canonical type is always FLATS —
            // the production code overrides whatever key type is in the song.
            // Drive a default Song (which starts at FLATS, 0).
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
            // The canonicalization must override SHARPS when count is 0 —
            // the combo has no "(SHARPS, 0)" entry, only "(FLATS, 0)".
            // Directly set the song's key type to SHARPS with 0 accidentals
            // to exercise the branch: accidentalCount == 0 → FLATS regardless.
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
            // A song with 3 sharps must return (SHARPS, 3) unchanged.
            var song = new Song();
            // Drive the song's key directly via its notification handler.
            song.keySignatureDidChange(new KeySignatureDidChangeNotification(null, KeyType.SHARPS, 3));

            var selection = SongSettingsDialog.canonicalKeySelectionFrom(song);

            assertThat(selection.keyType())
                .as("non-zero sharp count preserves SHARPS key type")
                .isEqualTo(KeyType.SHARPS);
            assertThat(selection.count())
                .as("accidental count is 3")
                .isEqualTo(3);
        }

        @Test
        void testNonZeroFlatCountPreservesKeyType() {
            // A song with 2 flats must return (FLATS, 2).
            var song = new Song();
            song.keySignatureDidChange(new KeySignatureDidChangeNotification(null, KeyType.FLATS, 2));

            var selection = SongSettingsDialog.canonicalKeySelectionFrom(song);

            assertThat(selection.keyType())
                .as("non-zero flat count preserves FLATS key type")
                .isEqualTo(KeyType.FLATS);
            assertThat(selection.count())
                .as("accidental count is 2")
                .isEqualTo(2);
        }
    }

    // ── Row 18: applyMusicTabChanges — change-detection and notification posting ──

    @Nested
    class ApplyMusicTabChanges {

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

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testTempoOnlyChangePostsTempoNotification() {
            // Only the visible tempo changes → exactly one TempoDidChangeNotification.
            var tempo = currentTempo();
            var newVisibleTempo = tempo.getVisibleTempo() + 20;

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
                times(1)
            );
            messageCenterMock.verify(
                () -> MessageCenter.post(any(KeySignatureDidChangeNotification.class)),
                times(0)
            );
        }

        @Test
        void testKeyOnlyChangePostsKeyNotification() {
            // Only the key changes → exactly one KeySignatureDidChangeNotification.
            var tempo = currentTempo();
            // Change to 2 sharps (default is FLATS, 0).
            var newKey = new SongSettingsDialog.KeySelection(KeyType.SHARPS, 2);

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
                times(1)
            );
            messageCenterMock.verify(
                () -> MessageCenter.post(any(TempoDidChangeNotification.class)),
                times(0)
            );
        }

        @Test
        void testBothChangedPostsBothNotifications() {
            // Both tempo and key change → both notifications are posted.
            var tempo = currentTempo();
            var newVisibleTempo = tempo.getVisibleTempo() + 10;
            var newKey = new SongSettingsDialog.KeySelection(KeyType.FLATS, 3);

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
                times(1)
            );
            messageCenterMock.verify(
                () -> MessageCenter.post(any(KeySignatureDidChangeNotification.class)),
                times(1)
            );
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
