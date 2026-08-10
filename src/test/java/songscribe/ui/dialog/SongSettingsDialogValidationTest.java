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
import java.awt.Point;

import javax.swing.JDialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.verification.VerificationMode;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.PageModel;

import songscribe.ui.OptionDialogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Unit tests for the parts of {@link SongSettingsDialog} that only exist once the dialog is
 * assembled: the dialog-level fit check that spans the Fonts and Music tabs, and the Music tab's
 * line-width rejection reporting. {@link SongSettingsDialogTest} covers the package-private static
 * helpers these delegate to; what is tested here is the wiring between them — that the fit check
 * receives the font in effect and the font just chosen the right way round, that the width it
 * measures against comes from the line-width field rather than from the width already stored, and
 * that each kind of rejection reports the matching message.
 *
 * <p>The song is built here rather than loaded from a fixture because the widths involved have to
 * sit inside the dialog's own legal range ({@link PageModel#MIN_LINE_WIDTH_INCHES} to
 * {@link PageModel#MAX_LINE_WIDTH_INCHES}) — otherwise the Music tab rejects the width and the fit
 * check is never reached. A fixture carries whatever width the machine that produced it had, in
 * staff spaces, and converting that to inches depends on the running screen's dots-per-inch, so no
 * fixture can be relied on to land inside that range everywhere the suite runs.
 */
class SongSettingsDialogValidationTest extends MainFrameMockTest {

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static final String SYLLABLE = "la";

    /**
     * Chosen so the line fits at {@link PageModel#MAX_LINE_WIDTH_INCHES} but not at
     * {@link PageModel#MIN_LINE_WIDTH_INCHES}; both premises are asserted in
     * {@link #testANarrowedLineWidthIsRefusedWithTheFitAlert} rather than left implicit.
     */
    private static final int LINE_ELEMENT_COUNT = 20;

    /** Enough to overflow the line even at the widest legal width. */
    private static final float ENLARGED_FONT_FACTOR = 20f;

    /** Comfortably narrower, so the line only gets slacker. */
    private static final float REDUCED_FONT_FACTOR = 0.5f;

    private static final String MIN_WIDTH_TEXT = String.valueOf(PageModel.MIN_LINE_WIDTH_INCHES);

    /** An emptied field is the reachable unparseable case: a filter blocks non-numeric typing. */
    private static final String EMPTY_WIDTH_TEXT = "";

    /** Parses cleanly but exceeds {@link PageModel#MAX_LINE_WIDTH_INCHES}. */
    private static final String OUT_OF_RANGE_WIDTH_TEXT = "20";

    /** Where the mocked dialog window reports itself to be; nothing reads it. */
    private static final Point ORIGIN = new Point(0, 0);

    private SongSettingsDialog dialog;
    private Song song;
    private DocumentFonts fonts;

    @BeforeEach
    void setUp() {
        song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < LINE_ELEMENT_COUNT; i++) {
                var element = crotchet();
                element.setLyricForVerse(
                    Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, SYLLABLE, Lyric.Extend.NONE
                );
                line.addElement(element);
            }

            song.setLineWidthSs(ScaleContext.inchesToSs(PageModel.MAX_LINE_WIDTH_INCHES));
        });

        // Every role has to be populated: the dialog's tabs read the title, subtitle, attribution
        // and sub-attribution fonts as they build, and an unset role throws. Only the lyrics font
        // matters to what is being tested; the rest just have to be present.
        fonts = new DocumentFonts();

        for (var key : FontKey.values()) {
            fonts.setFont(key, LYRICS_FONT);
        }

        var scoreView = mockEnv().score();
        when(scoreView.getSong()).thenReturn(song);
        when(scoreView.getDocumentFonts()).thenReturn(fonts);

        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetSavedGeometry();

        dialog = new SongSettingsDialog(mainFrame());
        populateTabs();
    }

    /**
     * Populates every tab the way opening the dialog does. This goes through {@code setVisible}
     * rather than calling {@code getData} directly because the Attribution tab's population
     * re-packs the window to fit its live preview, and the window only exists once the dialog has
     * been shown. The window itself is mocked away, so nothing blocks and nothing is drawn.
     */
    private void populateTabs() {
        BaseDialog.resetVisibleBlockingDialogCount();

        try (var ignored = mockConstruction(JDialog.class,
            (mockDialog, context) -> BaseDialogTestHelper.configureMockDialog(mockDialog, ORIGIN))) {
            dialog.setVisible(true);
        }
    }

    /**
     * Leaves the Fonts tab holding {@code chosen} while the score still reports {@link #LYRICS_FONT}
     * as the one in effect — the state the dialog is in once the user has picked a font. The tab has
     * no setter: it loads whatever the score reports when the dialog is populated, so populate it
     * from a stand-in and then put the real fonts back.
     */
    private void chooseLyricsFont(Font chosen) {
        var chosenFonts = new DocumentFonts(fonts);
        chosenFonts.setFont(FontKey.LYRICS, chosen);

        var scoreView = mockEnv().score();
        when(scoreView.getDocumentFonts()).thenReturn(chosenFonts);
        populateTabs();
        when(scoreView.getDocumentFonts()).thenReturn(fonts);
    }

    private static Font scaledLyricsFont(float factor) {
        return LYRICS_FONT.deriveFont(LYRICS_FONT.getSize2D() * factor);
    }

    private void setLineWidthText(String text) {
        dialog.getLineWidthFieldForTest().setText(text);
    }

    private static void verifyFitAlert(MockedStatic<OptionDialogs> dialogs) {
        verifyFitAlert(dialogs, times(1));
    }

    private static void verifyFitAlert(
        MockedStatic<OptionDialogs> dialogs, VerificationMode mode
    ) {
        dialogs.verify(() -> OptionDialogs.showErrorMessage(
            any(),
            eq(Strings.ALERT_TITLE_LINES_DO_NOT_FIT),
            eq(Strings.ALERT_FONT_CHANGE_INVALID)
        ), mode);
    }

    // ── The fit check's inputs ──

    @Test
    void testUnchangedFontAndWidthIsAccepted() {
        try (var dialogs = mockStatic(OptionDialogs.class)) {
            assertThat(dialog.isValidData())
                .as("changing nothing must never be refused")
                .isTrue();

            dialogs.verifyNoInteractions();
        }
    }

    /**
     * Also pins the argument order: were the font in effect and the font just chosen passed the
     * other way round, the enlarged font would be treated as the baseline, the line would count as
     * already overflowing, and the change would be waved through.
     */
    @Test
    void testALyricsFontTooWideForTheLineIsRefusedWithTheFitAlert() {
        chooseLyricsFont(scaledLyricsFont(ENLARGED_FONT_FACTOR));

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            assertThat(dialog.isValidData())
                .as("a lyrics font this much wider cannot hold the line's syllables")
                .isFalse();

            verifyFitAlert(dialogs);
        }
    }

    @Test
    void testASmallerLyricsFontIsAccepted() {
        chooseLyricsFont(scaledLyricsFont(REDUCED_FONT_FACTOR));

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            assertThat(dialog.isValidData())
                .as("a narrower font only gives the line more slack")
                .isTrue();

            dialogs.verifyNoInteractions();
        }
    }

    /**
     * The width has to come from the field the user typed in, not from the width already stored on
     * the song. If it came from the song, this would compare the stored width against itself, take
     * the unchanged-inputs shortcut, and accept.
     */
    @Test
    void testANarrowedLineWidthIsRefusedWithTheFitAlert() {
        var metrics = LyricRenderMetrics.forFont(LYRICS_FONT);
        var line = song.getLine(0);
        assertThat(LyricEditFitCalculator.lineFits(line, metrics, song.getLineWidthSs()))
            .as("the line must fit at the width it starts at, or nothing below is newly broken")
            .isTrue();
        assertThat(LyricEditFitCalculator.lineFits(
            line, metrics, ScaleContext.inchesToSs(PageModel.MIN_LINE_WIDTH_INCHES)))
            .as("the line must not fit at the narrowed width, or there is nothing to refuse")
            .isFalse();

        setLineWidthText(MIN_WIDTH_TEXT);

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            assertThat(dialog.isValidData())
                .as("an unchanged font still has to fit the narrowed width")
                .isFalse();

            verifyFitAlert(dialogs);
        }
    }

    // ── Line-width rejection reporting ──
    //
    // Both messages come from the same collapsed -1 sentinel, so the only thing telling them apart
    // is the re-parse in validateLineWidthOrShowError. These pin which one each failure produces,
    // and that the fit check never also fires — the width is rejected before it runs.

    @Test
    void testAnEmptyLineWidthReportsTheInvalidNumberMessage() {
        setLineWidthText(EMPTY_WIDTH_TEXT);

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            assertThat(dialog.isValidData())
                .as("an empty width cannot be committed")
                .isFalse();

            dialogs.verify(() -> OptionDialogs.showErrorMessage(
                any(),
                eq(Strings.ALERT_TITLE_LINE_WIDTH_ERROR),
                eq(Strings.ERROR_LINE_WIDTH_INVALID),
                any(), any(), any()
            ));
            verifyFitAlert(dialogs, never());
        }
    }

    @Test
    void testAnOutOfRangeLineWidthReportsTheRangeMessage() {
        setLineWidthText(OUT_OF_RANGE_WIDTH_TEXT);

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            assertThat(dialog.isValidData())
                .as("a width outside the legal range cannot be committed")
                .isFalse();

            dialogs.verify(() -> OptionDialogs.showErrorMessage(
                any(),
                eq(Strings.ALERT_TITLE_LINE_WIDTH_ERROR),
                eq(Strings.ERROR_LINE_WIDTH_RANGE),
                any(), any(), any()
            ));
            verifyFitAlert(dialogs, never());
        }
    }

    // ── The width the commit applies ──

    /**
     * The commit has to apply the width the fit check just approved. Re-parsing the field's rounded
     * display text here, or falling back to the width already stored, would both commit something
     * other than what was validated.
     */
    @Test
    void testTheCommitAppliesTheWidthFromTheField() {
        var editedInches = PageModel.MIN_LINE_WIDTH_INCHES;
        setLineWidthText(String.valueOf(editedInches));

        dialog.setData();

        verify(mockEnv().score()).updatePageLayout(ScaleContext.inchesToSs(editedInches));
    }
}
