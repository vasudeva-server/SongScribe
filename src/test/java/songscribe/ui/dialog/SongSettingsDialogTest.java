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

import java.awt.Font;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.font.SourceSans3Font;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.PageModel;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Unit tests for {@link SongSettingsDialog}'s package-private static helpers,
 * which carry the dialog's pure logic so it stays directly testable even though
 * the tabs that drive them are Swing-bound private inner classes:
 * {@code extractLyricsTitle}, {@code validateLineWidthText},
 * {@code canonicalKeySelectionFrom}, and {@code applyMusicTabChanges}.
 *
 * <p>Everything here is a pure test of a static helper, so this class deliberately extends
 * {@link UnitTest} and builds no dialog: {@link SongSettingsDialogShowTest} and
 * {@link SongSettingsDialogValidationTest} are where an assembled dialog — and the mocked
 * {@code MainFrame} singleton it needs — belong.
 *
 * <p>Row 19: {@link SongSettingsKeyCellRenderer#SELECTIONS} — 15
 * entries in canonical order — is fully covered below.
 *
 * <p>The title field's blank guard (a {@link songscribe.ui.component.NonEmptyGuard} installed by
 * {@code TitleTab}, which puts the previous title back rather than asking about it) is not
 * re-tested here; its contract is covered comprehensively by {@code NonEmptyGuardTest}.
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
                .isCloseTo(midInches, offset(COMPARISON_TOLERANCE));
        }

        @Test
        void testValidCentimetresReturnsWidthInInches() {
            var cm = VALID_INCHES * GraphicUtils.CM_PER_INCH;
            var text = String.valueOf(cm);
            assertThat(SongSettingsDialog.validateLineWidthText(text, true))
                .as("valid cm value is converted to inches and returned")
                .isCloseTo(VALID_INCHES, offset(COMPARISON_TOLERANCE));
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

        private static final int VISIBLE_TEMPO_DELTA = 20;
        private static final int SHARPS_COUNT = 2;
        private static final int EXPECTED_POST_COUNT = 2;
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

        @Test
        void testNoChangePostsNoMessage() {
            // Opening Song Settings and confirming it untouched must not dirty the document:
            // every submitted value already matches the song, so neither notification is due.
            var tempo = song.getTempo();

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
        void testTempoOnlyChangePostsOnlyTheTempoNotification() {
            // Each notification is gated on its own value-changed check, so editing the tempo
            // alone must not also announce a key-signature change the user never made.
            var tempo = song.getTempo();

            SongSettingsDialog.applyMusicTabChanges(
                song,
                tempo.getTempoType(),
                tempo.getVisibleTempo() + VISIBLE_TEMPO_DELTA,
                tempo.getTempoDescription(),
                tempo.shouldShowTempo(),
                SongSettingsDialog.canonicalKeySelectionFrom(song)
            );

            messageCenterMock.verify(
                () -> MessageCenter.post(any(TempoDidChangeNotification.class)), times(ONE_POST));
            messageCenterMock.verify(
                () -> MessageCenter.post(any(KeySignatureDidChangeNotification.class)),
                times(NO_POSTS));
        }

        @Test
        void testKeyOnlyChangePostsOnlyTheKeyNotification() {
            // The mirror of the test above: changing the key alone must not announce a tempo
            // change, which would record a spurious undo step for a tempo nobody edited.
            var tempo = song.getTempo();

            SongSettingsDialog.applyMusicTabChanges(
                song,
                tempo.getTempoType(),
                tempo.getVisibleTempo(),
                tempo.getTempoDescription(),
                tempo.shouldShowTempo(),
                new SongSettingsDialog.KeySelection(KeyType.SHARPS, SHARPS_COUNT)
            );

            messageCenterMock.verify(
                () -> MessageCenter.post(any(KeySignatureDidChangeNotification.class)),
                times(ONE_POST));
            messageCenterMock.verify(
                () -> MessageCenter.post(any(TempoDidChangeNotification.class)), times(NO_POSTS));
        }

        @Test
        void testBothChangedPostsBothNotificationsCarryingTheSubmittedValues() {
            var tempo = song.getTempo();
            var newVisibleTempo = tempo.getVisibleTempo() + VISIBLE_TEMPO_DELTA;
            var newKey = new SongSettingsDialog.KeySelection(KeyType.SHARPS, SHARPS_COUNT);

            SongSettingsDialog.applyMusicTabChanges(
                song,
                tempo.getTempoType(),
                newVisibleTempo,
                tempo.getTempoDescription(),
                tempo.shouldShowTempo(),
                newKey
            );

            var messageCaptor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(
                () -> MessageCenter.post(messageCaptor.capture()),
                times(EXPECTED_POST_COUNT)
            );

            var tempoNotifications = messageCaptor.getAllValues().stream()
                .filter(TempoDidChangeNotification.class::isInstance)
                .map(TempoDidChangeNotification.class::cast)
                .toList();
            var keyNotifications = messageCaptor.getAllValues().stream()
                .filter(KeySignatureDidChangeNotification.class::isInstance)
                .toList();

            assertThat(tempoNotifications)
                .as("exactly one TempoDidChangeNotification was posted")
                .hasSize(1);
            assertThat(keyNotifications)
                .as("exactly one KeySignatureDidChangeNotification was posted")
                .hasSize(1);

            var tempoNotification = tempoNotifications.get(0);
            assertThat(tempoNotification.getTempoType())
                .as("tempo type carries the submitted value")
                .isEqualTo(tempo.getTempoType());
            assertThat(tempoNotification.getVisibleTempo())
                .as("BPM carries the submitted value")
                .isEqualTo(newVisibleTempo);
            assertThat(tempoNotification.getTempoDescription())
                .as("description carries the submitted value")
                .isEqualTo(tempo.getTempoDescription());
            assertThat(tempoNotification.getShowTempo())
                .as("showTempo carries the submitted value")
                .isEqualTo(tempo.shouldShowTempo());
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
            assertThat(SongSettingsDateInputRow.dayEnabled(false, 6))
                .as("day disabled when year is invalid")
                .isFalse();
        }

        /**
         * dayEnabled returns false when month == 0 (no month selected), even if
         * the year is valid.
         */
        @Test
        void testDayDisabledWhenMonthIsZero() {
            assertThat(SongSettingsDateInputRow.dayEnabled(true, 0))
                .as("day disabled when month is 0")
                .isFalse();
        }

        /**
         * dayEnabled returns true only when the year is valid AND a month is selected.
         */
        @Test
        void testDayEnabledWhenYearValidAndMonthSelected() {
            assertThat(SongSettingsDateInputRow.dayEnabled(true, 6))
                .as("day enabled when year valid and month selected")
                .isTrue();
        }

        /**
         * dayEnabled is false for year-invalid + month-zero (most restrictive case).
         */
        @Test
        void testDayDisabledWhenBothYearInvalidAndMonthZero() {
            assertThat(SongSettingsDateInputRow.dayEnabled(false, 0))
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
            assertThat(SongSettingsKeyCellRenderer.SELECTIONS)
                .as("1 no-accidentals + 7 flats + 7 sharps = 15 entries")
                .hasSize(EXPECTED_SELECTION_COUNT);
        }

        @Test
        void testFirstEntryIsNoAccidentals() {
            var first = SongSettingsKeyCellRenderer.SELECTIONS.getFirst();

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
            var selections = SongSettingsKeyCellRenderer.SELECTIONS;

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
            var selections = SongSettingsKeyCellRenderer.SELECTIONS;
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

    // ── pendingLineWidthSs — an unedited field must not re-quantize its own width ──

    /**
     * The field's text is rounded to two decimal places of the display unit, so re-deriving the
     * width from it shifts a width the user never touched. Only an edited field is parsed.
     */
    @Nested
    class PendingLineWidthSs {

        /** Off the two-decimal grid in both inches and centimetres, so re-parsing would shift it. */
        private static final double UNROUNDED_LOADED_SS = 74.3178;

        private static final String LOADED_TEXT = "6.19";
        private static final String EDITED_INCHES = "7.25";
        private static final String EDITED_CM = "18.42";
        private static final String UNPARSEABLE_TEXT = "not a number";

        /** Parses cleanly but exceeds {@link PageModel#MAX_LINE_WIDTH_INCHES}. */
        private static final String OUT_OF_RANGE_INCHES = "20";

        private static final boolean IMPERIAL = false;
        private static final boolean METRIC = true;

        private static double ssForInches(double inches) {
            return ScaleContext.pxToSs(inches * GraphicUtils.getDpi());
        }

        @Test
        void testUneditedFieldReturnsTheLoadedWidthUnchanged() {
            assertThat(SongSettingsDialog.pendingLineWidthSs(
                LOADED_TEXT, LOADED_TEXT, UNROUNDED_LOADED_SS, IMPERIAL))
                .as("an untouched field must commit the exact width it was loaded with")
                .isEqualTo(UNROUNDED_LOADED_SS);
        }

        @Test
        void testEditedFieldIsParsedAsInches() {
            assertThat(SongSettingsDialog.pendingLineWidthSs(
                EDITED_INCHES, LOADED_TEXT, UNROUNDED_LOADED_SS, IMPERIAL))
                .as("an edited field is parsed as inches when the display unit is inches")
                .isEqualTo(ssForInches(Double.parseDouble(EDITED_INCHES)));
        }

        @Test
        void testEditedFieldIsParsedAsCentimetresWhenMetric() {
            assertThat(SongSettingsDialog.pendingLineWidthSs(
                EDITED_CM, LOADED_TEXT, UNROUNDED_LOADED_SS, METRIC))
                .as("an edited field is parsed as centimetres when the display unit is metric")
                .isEqualTo(ssForInches(Double.parseDouble(EDITED_CM) / GraphicUtils.CM_PER_INCH));
        }

        // validateLineWidthText collapses both rejections to -1; the caller validates before using
        // the result, so these only have to stay unmistakably invalid. They are separate tests
        // because they reach that -1 down different branches — one fails to parse at all, the
        // other parses and then fails the range check.

        @Test
        void testUnparseableFieldYieldsANegativeWidth() {
            assertThat(SongSettingsDialog.pendingLineWidthSs(
                UNPARSEABLE_TEXT, LOADED_TEXT, UNROUNDED_LOADED_SS, IMPERIAL))
                .as("text that is not a number cannot yield a usable width")
                .isNegative();
        }

        @Test
        void testOutOfRangeFieldYieldsANegativeWidth() {
            assertThat(SongSettingsDialog.pendingLineWidthSs(
                OUT_OF_RANGE_INCHES, LOADED_TEXT, UNROUNDED_LOADED_SS, IMPERIAL))
                .as("a width past the legal maximum cannot yield a usable width")
                .isNegative();
        }
    }

    // ── lyricsFit — refusing a lyrics font / line width that cannot hold the lyrics ──

    /**
     * The candidate is solved at the pending line width, the baseline at the width in effect now,
     * so both a widening font and a narrowing line width are caught, and a line that already
     * overflows is left alone.
     * <p>
     * {@code spring-spacing-infeasible-lyric} is the tight-but-fitting fixture (one line whose
     * last note has room for two more characters and no more), so any widening breaks it;
     * {@code overflowing-lines} is the already-broken one.
     */
    @Nested
    class LyricsFit {

        /** One line whose last note has room for two more characters and no more. */
        private static final String TIGHT_FIXTURE = "spring-spacing-infeasible-lyric";

        /**
         * Three lines. The first two already run past the width the file carries; the third is a
         * short 8-note line with room to spare. {@code LayoutEngineTest.OverflowingLines}
         * documents and guards that shape.
         * <p>
         * It carries no lyrics, so its verdict moves with the line width alone — changing the
         * lyrics font cannot affect it either way. Only use it for width-driven cases.
         */
        private static final String OVERFLOWING_FIXTURE = "overflowing-lines";

        /** Well past what the tight fixture line can absorb. */
        private static final float ENLARGED_FONT_FACTOR = 2f;

        /** Comfortably narrower than the fixture's font, so the tight line only gets slacker. */
        private static final float REDUCED_FONT_FACTOR = 0.5f;

        /** Well below the width the tight fixture line needs. */
        private static final double NARROWED_WIDTH_FACTOR = 0.5;

        /** A plain font, so the built two-line song does not depend on the score fonts. */
        private static final Font PLAIN_LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

        private static final String SYLLABLE = "la";

        /** Few enough that this line keeps fitting even under the enlarged font. */
        private static final int SHORT_LINE_ELEMENT_COUNT = 2;

        /** Many enough that this line stops fitting under the enlarged font. */
        private static final int TIGHT_LINE_ELEMENT_COUNT = 20;

        private static final double BUILT_LINE_WIDTH_SS = 90;

        @BeforeAll
        static void installLyricsFont() {
            // The fixtures' geometry was produced by the real app, so it only reproduces under the
            // real lyric font metrics — the family their lyric-font declaration names.
            // Reset the lazy cache before installing: an earlier test class may already have
            // resolved "Source Sans 3 SongScribe" while it was unregistered and cached the
            // substituted UI font, which the fixture's lyric-font declaration would then pick up.
            MyFontUtils.resetFontCache();
            SourceSans3Font.install();
        }

        private static Font lyricsFontOf(String fixtureName) throws Exception {
            return loadFixtureResult(fixtureName).fonts().getLyricsFont();
        }

        @Test
        void testUnchangedFontAndWidthFits() throws Exception {
            var song = loadFixture(TIGHT_FIXTURE);
            var font = lyricsFontOf(TIGHT_FIXTURE);

            assertThat(SongSettingsDialog.lyricsFit(song, font, font, song.getLineWidthSs()))
                .as("changing nothing must never be refused")
                .isTrue();
        }

        @Test
        void testLargerLyricsFontDoesNotFit() throws Exception {
            var song = loadFixture(TIGHT_FIXTURE);
            var font = lyricsFontOf(TIGHT_FIXTURE);
            var largerFont = font.deriveFont(font.getSize2D() * ENLARGED_FONT_FACTOR);

            assertThat(SongSettingsDialog.lyricsFit(song, font, largerFont, song.getLineWidthSs()))
                .as("a font this much wider cannot hold the fixture's syllables")
                .isFalse();
        }

        @Test
        void testSmallerLyricsFontFits() throws Exception {
            var song = loadFixture(TIGHT_FIXTURE);
            var font = lyricsFontOf(TIGHT_FIXTURE);
            var smallerFont = font.deriveFont(font.getSize2D() * REDUCED_FONT_FACTOR);

            assertThat(SongSettingsDialog.lyricsFit(song, font, smallerFont, song.getLineWidthSs()))
                .as("a narrower font only gives the line more slack")
                .isTrue();
        }

        // The case the early-return short-circuit used to miss: the font is untouched, but the
        // width the lyrics have to fit into shrank underneath them.
        @Test
        void testNarrowerLineWidthDoesNotFitEvenWithTheSameFont() throws Exception {
            var song = loadFixture(TIGHT_FIXTURE);
            var font = lyricsFontOf(TIGHT_FIXTURE);
            var narrowerWidthSs = song.getLineWidthSs() * NARROWED_WIDTH_FACTOR;

            assertThat(SongSettingsDialog.lyricsFit(song, font, font, narrowerWidthSs))
                .as("an unchanged font still has to fit the narrowed width")
                .isFalse();
        }

        @Test
        void testAlreadyOverflowingLineIsNotBlocked() throws Exception {
            var song = loadFixture(OVERFLOWING_FIXTURE);
            var font = lyricsFontOf(OVERFLOWING_FIXTURE);
            var narrowerWidthSs = song.getLineWidthSs() * NARROWED_WIDTH_FACTOR;

            // The first two lines are excused because they already overflow. The verdict
            // therefore rests on the short third line, which must still fit at the narrowed
            // width — assert that directly, so a fixture regenerated with less slack on that
            // line fails here saying so, rather than as a confusing failure below.
            assertThat(LyricEditFitCalculator.lineFits(
                song.getLine(song.lineCount() - 1), LyricRenderMetrics.forFont(font), narrowerWidthSs))
                .as("the fixture's short last line must tolerate the narrowed width")
                .isTrue();

            assertThat(SongSettingsDialog.lyricsFit(song, font, font, narrowerWidthSs))
                .as("a line that already overflows must not veto the change")
                .isTrue();
        }

        /**
         * Builds a song of {@code elementCounts.length} lines, line {@code i} carrying
         * {@code elementCounts[i]} quarter notes each with a one-syllable lyric.
         * <p>
         * The two fixtures cannot express this case. The tight one has a single line, and the
         * overflowing one carries no lyrics at all, so no change of lyrics font moves its verdict
         * either way.
         */
        private static Song songWithLines(int... elementCounts) {
            var song = new Song();

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < elementCounts.length; i++) {
                    // A song starts with one line already present; add the rest.
                    var line = i == 0 ? song.getLine(0) : new Line(song);

                    if (i > 0) {
                        song.addLine(line);
                    }

                    for (var j = 0; j < elementCounts[i]; j++) {
                        var element = crotchet();
                        element.setLyricForVerse(
                            Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, SYLLABLE, Lyric.Extend.NONE
                        );
                        line.addElement(element);
                    }
                }

                song.setLineWidthSs(BUILT_LINE_WIDTH_SS);
            });

            return song;
        }

        /**
         * The rejection here can only come from the second line: the first is short enough to keep
         * fitting under the enlarged font. A check that stopped after the first line, or that
         * skipped the last, would wave this through.
         */
        @Test
        void testALaterLineThatStopsFittingIsRefused() {
            var song = songWithLines(SHORT_LINE_ELEMENT_COUNT, TIGHT_LINE_ELEMENT_COUNT);
            var largerFont =
                PLAIN_LYRICS_FONT.deriveFont(PLAIN_LYRICS_FONT.getSize2D() * ENLARGED_FONT_FACTOR);
            var largerMetrics = LyricRenderMetrics.forFont(largerFont);
            var widthSs = song.getLineWidthSs();

            // Pin the premises, so a spacing-model change that invalidates either one fails here
            // saying which, rather than as an opaque failure of the assertion below.
            assertThat(LyricEditFitCalculator.lineFits(song.getLine(0), largerMetrics, widthSs))
                .as("the first line must survive the enlarged font, or it could be the one refusing")
                .isTrue();
            assertThat(LyricEditFitCalculator.lineFits(song.getLine(1), largerMetrics, widthSs))
                .as("the second line must break under the enlarged font, or there is nothing to refuse")
                .isFalse();

            assertThat(SongSettingsDialog.lyricsFit(song, PLAIN_LYRICS_FONT, largerFont, widthSs))
                .as("a later line pushed past the margin must refuse the change")
                .isFalse();
        }
    }
}
