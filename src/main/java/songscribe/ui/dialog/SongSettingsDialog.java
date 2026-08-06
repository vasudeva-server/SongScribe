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

import module java.desktop;

import songscribe.Strings;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.dom.Song;
import songscribe.dom.Duration;
import songscribe.dom.KeyType;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.MyJTextField;
import songscribe.dom.SongMetadata;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.PageModel;
import songscribe.dom.ScaleContext;
import songscribe.util.GraphicUtils;

/**
 * The Song Settings dialog. Owns the four tabs — {@link SongSettingsTitleTab},
 * {@link SongSettingsAttributionTab}, {@link SongSettingsMusicTab} and
 * {@link SongSettingsFontTab} — and the commits that no single tab can make on
 * its own, because they coalesce fields the tabs split between them.
 */
public class SongSettingsDialog extends StandardDialog {

    /**
     * A single entry in the key-signature combo: a {@link KeyType} paired with
     * an accidental count. {@code (FLATS, 0)} is the canonical no-accidentals
     * value; {@code SHARPS, 0} is never produced.
     */
    public record KeySelection(KeyType keyType, int count) {}

    private final SongSettingsFontTab fontTab = new SongSettingsFontTab(this);
    private final SongSettingsTitleTab textTab = new SongSettingsTitleTab(this);
    private final SongSettingsAttributionTab attributionTab =
        new SongSettingsAttributionTab(this, textTab);

    // Built in the constructor rather than here, so its construction order relative to the
    // tabbed container is unchanged; the dialog-level fit check needs a handle on it.
    private final SongSettingsMusicTab musicTab;

    public SongSettingsDialog(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), true, DialogCategory.EXCLUSIVE);

        var tabbedContent = createTabbedContent();
        addTab(textTab);
        addTab(attributionTab);
        musicTab = new SongSettingsMusicTab(this);
        addTab(musicTab);
        addTab(fontTab);

        contentPanel.add(BorderLayout.CENTER, tabbedContent);

        // Let Cancel bypass the range-validating fields' InputVerifiers so the
        // user can always dismiss the dialog without first fixing the value.
        cancelButton.setVerifyInputWhenFocusTarget(false);
    }

    /**
     * The fit check spans two tabs — the lyrics font comes from the Fonts tab, the width it has
     * to fit into from the Music tab — so it belongs here rather than in either tab's own
     * {@code isValidData()}. Ordering matters: {@code super.isValidData()} runs the Music tab's
     * line-width validation first, so the width read below is always parseable.
     */
    @Override
    protected boolean isValidData() {
        if (!super.isValidData()) {
            return false;
        }

        var fits = lyricsFit(
            getSong(),
            requireScoreView().getDocumentFonts().getFont(FontKey.LYRICS),
            fontTab.getLyricsFont(),
            musicTab.getPendingLineWidthSs()
        );

        if (!fits) {
            OptionDialogs.showErrorMessage(
                contentPanel,
                Strings.ALERT_TITLE_LINES_DO_NOT_FIT,
                Strings.ALERT_FONT_CHANGE_INVALID
            );
        }

        return fits;
    }

    /**
     * Returns whether the song's lines still fit under the pending lyrics font and line width.
     * A wider lyrics font widens every syllable, which widens the minimum spacing between
     * columns; a narrower line width shrinks the budget those columns have to fit into. Either
     * can push a line past the staff margin, and committing that anyway leaves the line laid out
     * on its collision floors with its tail clipped in red (refs #696). Refusing the change up
     * front keeps a fitting line fitting.
     *
     * <p>The two solves are deliberately asymmetric: the candidate is measured at the width the
     * commit will apply, the baseline at the width in effect now. Measuring both at the pending
     * width would make a width-only change compare against itself and never reject.
     *
     * <p>A line that already overflows is deliberately not blocked, so the user can still switch
     * to a narrower font or a wider line to recover — the same rule
     * {@link songscribe.ui.component.LyricEditor} applies to a widening syllable edit.
     *
     * @param song           the song whose lines must fit
     * @param currentFont    the lyrics font in effect now
     * @param newFont        the lyrics font chosen in the dialog
     * @param newLineWidthSs the line width the commit will apply, in staff spaces
     * @return {@code true} when the change is safe to commit
     */
    static boolean lyricsFit(Song song, Font currentFont, Font newFont, double newLineWidthSs) {
        var currentLineWidthSs = song.getLineWidthSs();

        // Neither input to the solve changed, so the candidate probe below would be the
        // baseline probe: every line would agree with itself and nothing can be newly broken.
        // Most OK presses land here — this dialog also edits the title, key, tempo, and
        // attribution — so skip the whole-song scan rather than compute a foregone answer.
        if (newFont.equals(currentFont) && newLineWidthSs == currentLineWidthSs) {
            return true;
        }

        var currentMetrics = LyricRenderMetrics.forFont(currentFont);
        var newMetrics = LyricRenderMetrics.forFont(newFont);

        for (var i = 0; i < song.lineCount(); i++) {
            var line = song.getLine(i);

            // Probe the candidate first: it accepts almost every real change, and continuing
            // here spares the baseline its own rebuild-and-solve of the line.
            if (LyricEditFitCalculator.lineFits(line, newMetrics, newLineWidthSs)) {
                continue;
            }

            if (LyricEditFitCalculator.lineFits(line, currentMetrics, currentLineWidthSs)) {
                return false;
            }
        }

        return true;
    }

    /**
     * The Music tab's line-width field. The tabs are package-private, so this is how a
     * test drives the field whose text {@link #isValidData} turns into the pending width.
     */
    MyJTextField getLineWidthFieldForTest() {
        return musicTab.getLineWidthField();
    }

    /**
     * The title, place/date, and attribution fields are split across the Text
     * and Attribution tabs but together form a single {@link SongMetadata}
     * record. Run the per-tab commits via {@code super.setData()}, then coalesce
     * those fields here into one notification rather than posting per tab.
     */
    @Override
    protected void setData() {
        getSong().withModification(Strings.get(Strings.ACTION_EDIT_OP_SONG_SETTINGS), () -> {
            super.setData();
            commitMetadata();
            commitFonts();
        });
    }

    /**
     * Each font is owned by one tab (title by the Text tab, attribution and
     * sub-attribution by the Attribution tab, lyrics and annotation by the Fonts
     * tab), so no single tab can build the whole record. Coalesce each tab's
     * chosen font here, the way {@link #commitMetadata} coalesces the split
     * metadata fields. Bangla and footnote fonts are document-level but not
     * surfaced by this dialog, so the copy constructor carries them through.
     */
    private void commitFonts() {
        var newFonts = new DocumentFonts(requireScoreView().getDocumentFonts());
        newFonts.setFont(FontKey.TITLE,           textTab.getTitleFont());
        newFonts.setFont(FontKey.SUBTITLE,        textTab.getSubtitleFont());
        newFonts.setFont(FontKey.ATTRIBUTION,     attributionTab.getAttributionFont());
        newFonts.setFont(FontKey.SUB_ATTRIBUTION, attributionTab.getSubAttributionFont());
        newFonts.setFont(FontKey.LYRICS,          fontTab.getLyricsFont());
        newFonts.setFont(FontKey.ANNOTATION,      fontTab.getAnnotationFont());
        requireScoreView().setFonts(newFonts);
    }

    private void commitMetadata() {
        // The number and year fields validate their own range via an
        // InputVerifier, so by commit time the text is always valid.
        var number = textTab.getNumberText();
        var year = attributionTab.getYearText();
        var song = getSong();
        var title = textTab.getTitleText();
        var place = attributionTab.getPlaceText();
        var month = attributionTab.getMonth();
        var day = attributionTab.getDay();
        var composerText = attributionTab.getComposerText();
        var lyricistText = attributionTab.getLyricistText();
        var lyricsSource = attributionTab.getLyricsSource();
        var arrangement = attributionTab.isArrangement();
        var unofficialTranslation = attributionTab.isUnofficialTranslation();
        var wordsDate = gatedWordsDate(
            attributionTab.isDifferentDate(),
            attributionTab.getWordsYearText(),
            attributionTab.getWordsMonth(),
            attributionTab.getWordsDay()
        );

        // No change-detection here: Song.setMetadata short-circuits on an equal
        // record, so an unchanged commit produces an empty (no-op) modification
        // bracket that neither dirties the document nor posts a notification.
        var newMetadata = new SongMetadata(
            title,
            number,
            place,
            year,
            month,
            day,
            composerText,
            lyricistText,
            lyricsSource,
            arrangement,
            unofficialTranslation,
            textTab.getSubtitleText(),
            wordsDate.year(),
            wordsDate.month(),
            wordsDate.day()
        );
        song.postWithModification(new SongMetadataDidChangeNotification(newMetadata));
    }

    // ── Package-private static helpers ──
    //
    // These pure-logic units are inlined into the tab classes' Swing-bound
    // methods at their call sites. Per "Testability Over Encapsulation" they live
    // here as self-contained, directly unit-testable helpers, and the tabs
    // delegate to them.

    /**
     * Carries the three components of the words date for commit and preview.
     */
    record WordsDate(String year, int month, int day) {}

    /**
     * Returns the words date triple when the "Different date" checkbox is selected,
     * or {@code ("", 0, 0)} when it is not, so an unchecked box never contributes
     * a date to either the commit or the preview.
     */
    static WordsDate gatedWordsDate(boolean selected, String year, int month, int day) {
        if (selected) {
            return new WordsDate(year, month, day);
        }

        return new WordsDate("", 0, 0);
    }

    private static final int LYRICS_TITLE_BUFFER_CAPACITY = 50;

    /**
     * Builds a title from the first {@code maxWords} words of {@code lyrics},
     * capitalising the first letter of each word. Underscores (melisma markers)
     * are skipped and a double hyphen counts as a word break. Returns an empty
     * string when {@code lyrics} yields no characters (e.g. only underscores).
     */
    static String extractLyricsTitle(String lyrics, int maxWords) {
        var words = new StringBuilder(LYRICS_TITLE_BUFFER_CAPACITY);
        var wordCount = 0;
        var firstLetter = false;
        var lastHyphen = false;

        goThruString:
        for (var i = 0; i < lyrics.length(); i++) {
            switch (lyrics.charAt(i)) {
                case ' ', '\n' -> {
                    wordCount++;

                    if (wordCount >= maxWords) {
                        break goThruString;
                    }

                    words.append(' ');
                    firstLetter = true;
                }
                case '-' -> {
                    if (lastHyphen) {
                        words.append('-');
                        wordCount++;
                        firstLetter = true;
                    }

                    lastHyphen = !lastHyphen;
                }
                case '_' -> {
                }
                default -> {
                    if (firstLetter) {
                        words.append(
                            String.valueOf(lyrics.charAt(i)).toUpperCase()
                        );
                        firstLetter = false;
                    } else {
                        words.append(lyrics.charAt(i));
                    }

                    lastHyphen = false;
                }
            }
        }

        // Lyrics made up only of separators (e.g. all underscores) leave the
        // buffer empty; guard before indexing the last character so the trim
        // does not throw on an empty buffer.
        if (!words.isEmpty() && !Character.isLetter(words.charAt(words.length() - 1))) {
            words.deleteCharAt(words.length() - 1);
        }

        return words.toString();
    }

    /**
     * Parses and range-validates line-width field text.
     *
     * @param text     the raw field text
     * @param isMetric whether {@code text} is in centimetres (else inches)
     * @return the width in inches if valid, or -1 if the text is empty,
     *         unparseable, or out of range
     */
    static double validateLineWidthText(String text, boolean isMetric) {
        double value;

        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return -1;
        }

        var widthInches = isMetric ? value / GraphicUtils.CM_PER_INCH : value;

        if (widthInches < PageModel.MIN_LINE_WIDTH_INCHES || widthInches > PageModel.MAX_LINE_WIDTH_INCHES) {
            return -1;
        }

        return widthInches;
    }

    /**
     * The line width a commit should apply, in staff spaces. Returns a negative width when
     * {@code fieldText} is empty, unparseable, or out of range — callers validate first.
     * <p>
     * A field the user never edited returns {@code loadedLineWidthSs} rather than a width
     * re-derived from its text. The text is rounded to two decimal places of the display unit
     * for legibility, so parsing it back would quantize a width nobody changed — by up to a
     * fraction of a pixel, which is enough to push a line that only just fits past the staff
     * margin. Once the user types, the typed value <em>is</em> the intent and there is nothing
     * to preserve.
     *
     * @param fieldText          the line-width field's current text
     * @param loadedText         the text the field was populated with
     * @param loadedLineWidthSs  the width the field was populated from
     * @param isMetric           whether the field is displaying centimetres rather than inches
     * @return the width to commit in staff spaces, or a negative value when {@code fieldText}
     *         is empty, unparseable, or out of range
     */
    static double pendingLineWidthSs(
        String fieldText, String loadedText, double loadedLineWidthSs, boolean isMetric
    ) {
        if (fieldText.equals(loadedText)) {
            return loadedLineWidthSs;
        }

        var widthInches = validateLineWidthText(fieldText, isMetric);
        return ScaleContext.inchesToSs(widthInches);
    }

    /**
     * Maps the song's stored default key to its canonical combo entry. Zero
     * accidentals always canonicalises to {@code (FLATS, 0)} — the combo has no
     * {@code (SHARPS, 0)} entry — otherwise the stored key type is preserved.
     */
    static KeySelection canonicalKeySelectionFrom(Song song) {
        var accidentalCount = song.getDefaultKeyAccidentalCount();
        var keyType = accidentalCount == 0 ? KeyType.FLATS : song.getDefaultKeyType();
        return new KeySelection(keyType, accidentalCount);
    }

    /**
     * Posts a {@link TempoDidChangeNotification} and/or a
     * {@link KeySignatureDidChangeNotification} for whichever of tempo / key
     * differs from the song's current state, coalesced into one modification
     * bracket. Posts nothing when neither changed.
     */
    static void applyMusicTabChanges(
        Song song,
        Duration tempoType,
        int visibleTempo,
        String tempoDescription,
        boolean showTempo,
        KeySelection keySelection
    ) {
        var tempo = song.getEffectiveTempo();
        var tempoChanged = tempoType != tempo.getTempoType()
            || visibleTempo != tempo.getVisibleTempo()
            || !tempoDescription.equals(tempo.getTempoDescription())
            || showTempo != tempo.shouldShowTempo();

        var keyChanged = keySelection.keyType() != song.getDefaultKeyType()
            || keySelection.count() != song.getDefaultKeyAccidentalCount();

        // Wrap both notifications in one bracket so tempo and key changes coalesce
        // into a single SongDidChangeNotification when both are modified.
        if (tempoChanged || keyChanged) {
            song.withModification(() -> {
                if (tempoChanged) {
                    MessageCenter.post(new TempoDidChangeNotification(
                        tempoType,
                        visibleTempo,
                        tempoDescription,
                        showTempo
                    ));
                }

                if (keyChanged) {
                    MessageCenter.post(new KeySignatureDidChangeNotification(
                        null,
                        keySelection.keyType(),
                        keySelection.count()
                    ));
                }
            });
        }
    }
}
