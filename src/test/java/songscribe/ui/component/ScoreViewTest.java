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
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xml.sax.SAXException;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.io.SongIO;
import songscribe.io.SongLoadResult;
import songscribe.io.SongLoader;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.PageModel;
import songscribe.layout.StaffExtents;
import songscribe.message.Message;
import songscribe.message.MessageCenter;

/**
 * Unit tests for {@link ScoreView} behaviors not covered by {@link ScoreViewSetFontsTest}:
 * the {@link ScoreView#getDocumentFonts()} guard, the {@link ScoreView#installDocumentFonts}
 * non-mutation contract, {@link ScoreView#rebuildLyricRenderMetrics()} guard and
 * idempotency, {@link ScoreView#getSuggestedFileName()} branch logic,
 * {@link ScoreView#getNoteYPosPx(int, int)} coordinate formula, and
 * {@link ScoreView#drawWidthIfWiderLine(songscribe.dom.Line, boolean)} rescaling.
 */
class ScoreViewTest extends UnitTest {

    @Nested
    class GetDocumentFonts {

        @Test
        void testGetDocumentFontsThrowsIllegalStateExceptionBeforeInitialization() {
            // ScoreView(null) creates a headless instance without installing document fonts.
            var scoreView = new ScoreView(null);

            assertThatThrownBy(scoreView::getDocumentFonts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("documentFonts not initialized");
        }
    }

    @Nested
    class InstallDocumentFonts {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        @Test
        void testInstallDocumentFontsSetsFontsFieldWithoutPostingFontChange() {
            var scoreView = new ScoreView(null);
            var fonts = DocumentFonts.defaultFonts();

            scoreView.installDocumentFonts(fonts);

            // Must not post any message (not an undoable user edit, no FontChange recorded).
            messageCenterMock.verify(
                () -> MessageCenter.post(any(Message.class)),
                org.mockito.Mockito.never()
            );

            // getDocumentFonts() must return the installed instance.
            assertThat(scoreView.getDocumentFonts()).isSameAs(fonts);
        }
    }

    @Nested
    class RebuildLyricRenderMetrics {

        @Test
        void testRebuildLyricRenderMetricsIsNoOpWhenSongIsNull() {
            // song == null: early return must not throw.
            var scoreView = new ScoreView(null);
            scoreView.installDocumentFonts(DocumentFonts.defaultFonts());

            // documentFonts is set but song is null — must return silently.
            scoreView.rebuildLyricRenderMetrics();

            // The song == null guard must leave the field null. Without the guard the method
            // would silently build metrics from the installed fonts, so this assertion is what
            // catches the guard being removed.
            assertThat(scoreView.lyricRenderMetrics).isNull();
        }

        @Test
        void testRebuildLyricRenderMetricsIsNoOpWhenDocumentFontsIsNull() {
            // documentFonts == null: early return must not throw.
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());
            // documentFonts deliberately not installed.

            scoreView.rebuildLyricRenderMetrics();

            // The documentFonts == null guard must leave the field null (and not NPE on
            // documentFonts.getLyricsFont()).
            assertThat(scoreView.lyricRenderMetrics).isNull();
        }

        @Test
        void testRebuildLyricRenderMetricsIsIdempotentWhenLyricsFontUnchanged() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());
            scoreView.installDocumentFonts(DocumentFonts.defaultFonts());

            scoreView.rebuildLyricRenderMetrics();
            var firstMetrics = scoreView.getLyricRenderMetrics();

            // Same font: second call must return the same instance (idempotency guard).
            scoreView.rebuildLyricRenderMetrics();
            var secondMetrics = scoreView.getLyricRenderMetrics();

            assertThat(secondMetrics).isSameAs(firstMetrics);
        }

        @Test
        void testRebuildLyricRenderMetricsRebuildsWhenLyricsFontChanges() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());

            var initialFonts = DocumentFonts.defaultFonts();
            scoreView.installDocumentFonts(initialFonts);
            scoreView.rebuildLyricRenderMetrics();
            var firstMetrics = scoreView.getLyricRenderMetrics();

            // Change the lyrics font and install the updated DocumentFonts.
            var updatedFonts = new DocumentFonts(initialFonts);
            updatedFonts.setFont(FontKey.LYRICS, new Font("Serif", Font.PLAIN, 18));
            scoreView.installDocumentFonts(updatedFonts);

            scoreView.rebuildLyricRenderMetrics();
            var secondMetrics = scoreView.getLyricRenderMetrics();

            assertThat(secondMetrics).isNotSameAs(firstMetrics);
            assertThat(secondMetrics.lyricsFont())
                .isEqualTo(updatedFonts.getFont(FontKey.LYRICS));
        }
    }

    @Nested
    class GetSuggestedFileName {

        @Test
        void testGetSuggestedFileNameZeroPadsNumericSongNumber() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            var current3 = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "Mélodie", "3",
                current3.place(), current3.year(), current3.month(), current3.day(),
                current3.composer(), current3.lyricist(),
                current3.lyricsSource(), current3.arrangement(), current3.unofficialTranslation(),
                current3.subtitle(), "", 0, 0
            ));
            scoreView.setSong(song);

            // Numeric number → zero-padded to three digits; diacritics stripped from title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("003 Melodie");
        }

        @Test
        void testGetSuggestedFileNameUsesNonNumericSongNumberVerbatim() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            var currentA = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "Title", "A",
                currentA.place(), currentA.year(), currentA.month(), currentA.day(),
                currentA.composer(), currentA.lyricist(),
                currentA.lyricsSource(), currentA.arrangement(), currentA.unofficialTranslation(),
                currentA.subtitle(), "", 0, 0
            ));
            scoreView.setSong(song);

            // Non-numeric number → used as-is, followed by a space and the title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("A Title");
        }

        @Test
        void testGetSuggestedFileNameOmitsLeadingSeparatorWhenNumberIsEmpty() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            var currentEmpty = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "Mélodie", "",
                currentEmpty.place(), currentEmpty.year(), currentEmpty.month(), currentEmpty.day(),
                currentEmpty.composer(), currentEmpty.lyricist(),
                currentEmpty.lyricsSource(), currentEmpty.arrangement(), currentEmpty.unofficialTranslation(),
                currentEmpty.subtitle(), "", 0, 0
            ));
            scoreView.setSong(song);

            // Empty number → no leading separator; only diacritic-stripped title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("Melodie");
        }
    }

    @Nested
    class GetNoteYPosPx {

        @Test
        void testGetNoteYPosPxComputesCorrectCoordinate() {
            // Verify the formula: middleLineYPx + ssToPx(spToSs(staffPosition)) + lineIndex * rowHeightPx
            var scoreView = new ScoreView(null);
            var middleLineY = 100;
            var rowHeight = 120;
            var staffPosition = 2;
            var lineIndex = 1;
            scoreView.setMiddleLineYPx(middleLineY);
            scoreView.setRowHeightPx(rowHeight);

            var expected = (int) Math.round(
                middleLineY
                    + ScaleContext.ssToPx(StaffExtents.spToSs(staffPosition))
                    + lineIndex * rowHeight
            );

            assertThat(scoreView.getNoteYPosPx(staffPosition, lineIndex)).isEqualTo(expected);
        }
    }

    @Nested
    class DrawWidthIfWiderLine {

        private static final int LINE_WIDTH_PX = 800;
        private static final int FIRST_X = 0;
        private static final int MID_X = 500;
        private static final int END_X = 1000;
        private static final int END_X_WITHIN_THRESHOLD = 700;

        /** Returns a ScoreView whose song reports the given lineWidthPx. */
        private ScoreView scoreViewWithLineWidth(int lineWidthPx) {
            var songMock = mock(Song.class);
            when(songMock.getLineWidthPx()).thenReturn(lineWidthPx);
            var scoreView = new ScoreView(null);
            scoreView.setSong(songMock);
            return scoreView;
        }

        @Test
        void testDrawWidthIfWiderLineRescalesXOffsetsProportionallyWhenEndExceedsThreshold() {
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var elem0 = ElementType.CROTCHET.newInstance();
            var elem1 = ElementType.CROTCHET.newInstance();
            var elem2 = ElementType.CROTCHET.newInstance();
            elem0.setXOffsetPx(FIRST_X);
            elem1.setXOffsetPx(MID_X);
            elem2.setXOffsetPx(END_X);
            line.addElement(elem0);
            line.addElement(elem1);
            line.addElement(elem2);

            scoreView.drawWidthIfWiderLine(line, false);

            // Compute expected values using the same formula as production code.
            var idealSpace = (float)(ScaleContext.ssToPx(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS) + 20);
            var ratio = (LINE_WIDTH_PX - idealSpace - FIRST_X) / (float)(END_X - FIRST_X);
            var expectedMid = FIRST_X + Math.round((MID_X - FIRST_X) * ratio);
            var expectedEnd = FIRST_X + Math.round((END_X - FIRST_X) * ratio);

            // elem0 anchors at FIRST_X and must be untouched (guards the loop start index).
            assertThat(elem0.getXOffsetPx()).isEqualTo(FIRST_X);
            assertThat(elem1.getXOffsetPx()).isEqualTo(expectedMid);
            assertThat(elem2.getXOffsetPx()).isEqualTo(expectedEnd);
            // The proportional rescale must also record the spacing ratio on the line.
            assertThat(line.getElementSpacingRatio()).isEqualTo(ratio);
        }

        @Test
        void testDrawWidthIfWiderLineUsesEndNoteContentWidthAsIdealSpaceWhenRevalidateOnly() {
            // revalidateOnly=true takes the other idealSpace branch: the end note's own
            // content width rather than the default column-gap formula.
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var elem0 = ElementType.CROTCHET.newInstance();
            var elem1 = ElementType.CROTCHET.newInstance();
            elem0.setXOffsetPx(FIRST_X);
            elem1.setXOffsetPx(END_X);  // END_X > LINE_WIDTH_PX → exceeds threshold
            line.addElement(elem0);
            line.addElement(elem1);

            scoreView.drawWidthIfWiderLine(line, true);

            var idealSpace = (float) elem1.getContentWidthPx();
            var ratio = (LINE_WIDTH_PX - idealSpace - FIRST_X) / (float) (END_X - FIRST_X);
            var expectedEnd = FIRST_X + Math.round((END_X - FIRST_X) * ratio);

            assertThat(elem0.getXOffsetPx()).isEqualTo(FIRST_X);
            assertThat(elem1.getXOffsetPx()).isEqualTo(expectedEnd);
            assertThat(line.getElementSpacingRatio()).isEqualTo(ratio);
        }

        @Test
        void testDrawWidthIfWiderLineDoesNotChangeXOffsetsWhenEndNoteIsWithinThreshold() {
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var elem0 = ElementType.CROTCHET.newInstance();
            var elem1 = ElementType.CROTCHET.newInstance();
            var elem2 = ElementType.CROTCHET.newInstance();
            elem0.setXOffsetPx(FIRST_X);
            elem1.setXOffsetPx(MID_X);
            elem2.setXOffsetPx(END_X_WITHIN_THRESHOLD);
            line.addElement(elem0);
            line.addElement(elem1);
            line.addElement(elem2);

            scoreView.drawWidthIfWiderLine(line, false);

            // End note is within the threshold — x-offsets must be unchanged.
            assertThat(elem1.getXOffsetPx()).isEqualTo(MID_X);
            assertThat(elem2.getXOffsetPx()).isEqualTo(END_X_WITHIN_THRESHOLD);
        }

        @Test
        void testDrawWidthIfWiderLineIsNoOpWhenEffectiveElementCountIsOne() {
            // A line with exactly one effective element must not attempt rescaling:
            // effectiveCount <= 1 is the early-exit guard; removing it would cause
            // an index-out-of-bounds on line.getElement(effectiveCount - 1).
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var singleElem = ElementType.CROTCHET.newInstance();
            singleElem.setXOffsetPx(END_X);  // far enough to exceed threshold if rescaling ran
            line.addElement(singleElem);

            scoreView.drawWidthIfWiderLine(line, false);

            // x-offset must be completely unchanged — no rescaling occurred.
            assertThat(singleElem.getXOffsetPx()).isEqualTo(END_X);
        }
    }

    @Nested
    class SetKeyBindingsEnabled {

        private static final String ACTION_KEY = "testAction";
        private static final KeyStroke STROKE =
            KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);

        /**
         * Injects a synthetic binding into the score-view's keybinding map so
         * tests can exercise the enable/disable toggle without running full init().
         */
        private ScoreView scoreViewWithBinding() {
            var scoreView = new ScoreView(null);
            scoreView.scoreKeyBindings.put(STROKE, ACTION_KEY);
            // Register the action key in the component's input map to simulate
            // the state after installKeyBindings().
            scoreView.getInputMap(JComponent.WHEN_FOCUSED).put(STROKE, ACTION_KEY);
            return scoreView;
        }

        @Test
        void testSetKeyBindingsEnabledFalseReplacesEachBindingWithNoneSentinel() {
            var scoreView = scoreViewWithBinding();

            scoreView.setKeyBindingsEnabled(false);

            var inputMap = scoreView.getInputMap(JComponent.WHEN_FOCUSED);
            // The stroke must now map to the disabled sentinel, not the original action key.
            assertThat(inputMap.get(STROKE)).isEqualTo("none");
        }

        @Test
        void testSetKeyBindingsEnabledTrueRestoresOriginalActionKeys() {
            var scoreView = scoreViewWithBinding();

            // Disable then re-enable.
            scoreView.setKeyBindingsEnabled(false);
            scoreView.setKeyBindingsEnabled(true);

            var inputMap = scoreView.getInputMap(JComponent.WHEN_FOCUSED);
            // The original action key must be restored after re-enabling.
            assertThat(inputMap.get(STROKE)).isEqualTo(ACTION_KEY);
        }
    }

    @Nested
    class IsInitialized {

        @Test
        void testIsInitializedReturnsFalseBeforeSongIsSet() {
            // A headless ScoreView(null) has song == null until setSong() is called.
            var scoreView = new ScoreView(null);

            assertThat(scoreView.isInitialized()).isFalse();
        }

        @Test
        void testIsInitializedReturnsTrueAfterSongIsSet() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());

            assertThat(scoreView.isInitialized()).isTrue();
        }
    }

    /**
     * Unit tests for {@link ScoreView#openFile}: each failure branch returns {@code false}
     * and the success path returns {@code true}, installs fonts, sets the song, and fires
     * the {@code onFileOpened} callback.
     *
     * <p>Failure branches are driven by mocking {@link SongLoader#load} so no real file I/O
     * is needed. The success path uses a real fixture file to verify the full happy path.
     */
    @Nested
    class OpenFile {

        // stubFile is the dummy file used in failure tests; all failure branches
        // are exercised by mocking SongLoader.load within each test's try-with-resources.
        private static final File STUB_FILE = new File("stub.mssw");

        // An actual-inches value that clearly exceeds MAX_LINE_WIDTH_INCHES; used in the
        // direct LineWidthTooLarge test so the value is self-documenting rather than raw.
        private static final double EXCESS_LINE_WIDTH_INCHES = 100.0;

        @Test
        void testOpenFileReturnsFalseOnNewerVersion() {
            var cause = new SongIO.NewerVersionException();
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.NewerVersion(STUB_FILE, cause));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseOnParseError() {
            var cause = new SAXException("damaged");
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.ParseError(STUB_FILE, cause));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseOnIoError() {
            var cause = new IOException("permission denied");
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.IoError(STUB_FILE, cause));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseWhenLineTooWide() {
            // openFile converts a Success whose line exceeds MAX_LINE_WIDTH_INCHES into
            // a LineWidthTooLarge before the switch — verify the switch arm returns false.
            // We supply the conversion result directly so the test remains focused on the
            // switch arm rather than the conversion arithmetic.
            var e = new SongLoadResult.LineWidthTooLarge(STUB_FILE, EXCESS_LINE_WIDTH_INCHES, PageModel.MAX_LINE_WIDTH_INCHES);
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE)).thenReturn(e);

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileDetectsLineWidthTooLargeFromSuccessResult() {
            // Verify the conversion path: a Success whose song reports a line width that
            // exceeds MAX_LINE_WIDTH_INCHES must be converted to LineWidthTooLarge, causing
            // openFile to return false without setting a song.
            var songMock = mock(Song.class);
            // getLineWidthSs() is called inside openFile to convert to inches; return a
            // value large enough that any reasonable DPI pushes it past the max.
            var hugeLineWidthSs = 100_000.0;
            when(songMock.getLineWidthSs()).thenReturn(hugeLineWidthSs);
            var fonts = DocumentFonts.defaultFonts();
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.Success(songMock, fonts, null));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
                // Song must not have been installed — the file was refused.
                assertThat(scoreView.isInitialized()).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsTrueOnSuccessInstallsFontsSetsSongAndFiresCallback()
            throws URISyntaxException {

            // Use a real fixture file; SongLoader is not mocked here so the full
            // success code path runs with real I/O.
            var file = fixtureFile("full-line");
            var capturedFile = new AtomicReference<File>();
            var scoreView = new ScoreView(capturedFile::set);
            var result = scoreView.openFile(file, true);

            assertThat(result).isTrue();
            // installDocumentFonts was called — getDocumentFonts() must not throw.
            assertThat(scoreView.getDocumentFonts()).isNotNull();
            // setSong was called — isInitialized() checks song != null.
            assertThat(scoreView.isInitialized()).isTrue();
            // onFileOpened callback must have been invoked with the opened file.
            assertThat(capturedFile.get()).isEqualTo(file);
        }

        @Test
        void testOpenFileDoesNotFireCallbackWhenUpdateCurrentFileIsFalse()
            throws URISyntaxException {

            // updateCurrentFile=false: the onFileOpened guard must suppress the callback
            // even on a successful load; without the guard the callback would always fire.
            var file = fixtureFile("full-line");
            var capturedFile = new AtomicReference<File>();
            var scoreView = new ScoreView(capturedFile::set);
            var result = scoreView.openFile(file, false);

            assertThat(result).isTrue();
            // Song must still be installed.
            assertThat(scoreView.isInitialized()).isTrue();
            // Callback must not have been invoked.
            assertThat(capturedFile.get()).isNull();
        }
    }
}
