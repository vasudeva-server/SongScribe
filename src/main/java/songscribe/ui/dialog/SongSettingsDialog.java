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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.event.DocumentEvent;

import com.formdev.flatlaf.FlatClientProperties;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.dom.Song;
import songscribe.dom.Duration;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.KeySignatureDisplay;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.BaseLabel;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.InputUtils;
import songscribe.ui.component.MyJTextArea;
import songscribe.ui.component.MyJTextField;
import songscribe.ui.component.NonEmptyGuard;
import songscribe.ui.component.NumericTextField;
import songscribe.ui.component.score.SubtitleComponent;
import songscribe.ui.component.score.TitleComponent;
import songscribe.dom.AttributionFormatter;
import songscribe.dom.AttributionLine;
import songscribe.dom.AttributionPane;
import songscribe.dom.SongMetadata;
import songscribe.layout.PageModel;
import songscribe.dom.DocPx;
import songscribe.dom.ScaleContext;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;
import songscribe.util.StringUtils;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

public class SongSettingsDialog extends StandardDialog {

    /**
     * A single entry in the key-signature combo: a {@link KeyType} paired with
     * an accidental count. {@code (FLATS, 0)} is the canonical no-accidentals
     * value; {@code SHARPS, 0} is never produced.
     */
    public record KeySelection(KeyType keyType, int count) {}

    private static final int SONG_NUMBER_MIN = 1;
    private static final int SONG_NUMBER_MAX = 1000;
    private static final int YEAR_MIN = 1942;
    private static final int YEAR_MAX = 2007;
    private static final int MAX_YEAR_CHARS = 4;
    private static final int TAKE_FIRST_WORDS_DEFAULT = 4;
    private static final int TAKE_FIRST_WORDS_MIN = 1;
    private static final int TAKE_FIRST_WORDS_MAX = 10;
    private static final int DAYS_IN_MONTH_MAX = 31;
    private static final int LYRICIST_ROWS = 2;
    private static final int LYRICIST_COLUMNS = 20;
    private static final int NUMBER_FIELD_COLUMNS = 3;
    private static final int TITLE_FIELD_COLUMNS = 47;
    private static final int YEAR_FIELD_COLUMNS = 4;
    private static final int PLACE_FIELD_COLUMNS = 27;
    private static final int COMPOSER_FIELD_COLUMNS = 27;
    private static final int LINE_WIDTH_FIELD_COLUMNS = 6;

    private final FontTab fontTab = new FontTab();
    private final TitleTab textTab = new TitleTab();
    private final AttributionTab attributionTab = new AttributionTab();

    public SongSettingsDialog(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), true, DialogCategory.EXCLUSIVE);

        var tabbedPane = createTabbedPane();
        addTab(
            tabbedPane,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_TITLE),
            textTab
        );
        addTab(
            tabbedPane,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_ATTRIBUTION),
            attributionTab
        );
        addTab(
            tabbedPane,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_MUSIC),
            new MusicTab()
        );
        addTab(
            tabbedPane,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_FONTS),
            fontTab
        );

        contentPanel.add(BorderLayout.CENTER, tabbedPane);

        // Let Cancel bypass the range-validating fields' InputVerifiers so the
        // user can always dismiss the dialog without first fixing the value.
        cancelButton.setVerifyInputWhenFocusTarget(false);
    }

    /**
     * The title, place/date, and attribution fields are split across the Text
     * and Attribution tabs but together form a single {@link SongMetadata}
     * record. Run the per-tab commits via {@code super.setData()}, then coalesce
     * those fields here into one notification rather than posting per tab.
     */
    @Override
    protected void setData() {
        super.setData();
        commitMetadata();
        commitFonts();
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
    // These pure-logic units are inlined into the tab inner classes' Swing-bound
    // methods at their call sites. Per "Testability Over Encapsulation" they live
    // here as self-contained, directly unit-testable helpers, and the inner
    // classes delegate to them.

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

    /**
     * Builds a "Preview" section wrapping the given live-preview widget, with the
     * widget's own background bleeding into a top/bottom matte border. Shared by
     * the title and attribution tabs.
     */
    private static JPanel createPreviewSection(JComponent preview) {
        var section = new BaseDialog.TitledSection(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_PREVIEW)
        );
        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);
        section.add(createPreviewWrapper(preview, new Insets(gap, 0, gap, 0)));
        return section;
    }

    /**
     * Wraps {@code preview} in a panel bordered by the preview's own background
     * color, so the background bleeds into the {@code border} margins. Shared by
     * the title/attribution preview sections and the Fonts tab's font previews.
     */
    private static JPanel createPreviewWrapper(JComponent preview, Insets border) {
        var backgroundColor = preview.getBackground();
        var previewWrapper = new JPanel();
        previewWrapper.setOpaque(true);
        previewWrapper.setBackground(backgroundColor);
        previewWrapper.setBorder(BorderFactory.createMatteBorder(
            border.top, border.left, border.bottom, border.right, backgroundColor
        ));
        previewWrapper.setLayout(new BorderLayout());
        previewWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewWrapper.add(preview, BorderLayout.CENTER);
        return previewWrapper;
    }

    /**
     * Wraps a score preview component in a full-width {@link BorderLayout} row.
     * <p>
     * Score components cap their maximum size at their preferred size
     * ({@code lineWidthPx}); a vertical {@link BoxLayout} would honor that and
     * clamp the component below the section width, clipping the centered text.
     * BorderLayout's {@code CENTER} ignores the maximum and stretches the preview
     * to the row's full width, matching the score's full-line-width centering.
     */
    private static JPanel createFullWidthPreviewRow(JComponent preview, Color background) {
        var row = new JPanel(new BorderLayout());
        row.setOpaque(true);
        row.setBackground(background);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(preview, BorderLayout.CENTER);
        return row;
    }

    private final class TitleTab extends BaseDialog.Tab {

        // Title of song panel
        private final NumericTextField numberField =
            new NumericTextField(NUMBER_FIELD_COLUMNS, SONG_NUMBER_MIN, SONG_NUMBER_MAX, true);
        private final MyJTextField titleField = new MyJTextField(TITLE_FIELD_COLUMNS);

        // The title-font chooser's description label. The title font is this
        // tab's own context; titlePreview holds the chosen font.
        private final JLabel titleFontLabel = FontSettingRow.createFontDescriptionLabel();
        private final SpinnerModel takeFirstWordsSpinnerModel =
            new SpinnerNumberModel(TAKE_FIRST_WORDS_DEFAULT, TAKE_FIRST_WORDS_MIN, TAKE_FIRST_WORDS_MAX, 1);
        private final TakeFirstLyricsWordAction takeAction =
            new TakeFirstLyricsWordAction(getMainFrame());
        private final TitleComponent titlePreview = new TitleComponent();

        // Subtitle section — field, font-description label, and preview component.
        private final MyJTextField subtitleField = new MyJTextField(TITLE_FIELD_COLUMNS);
        private final JLabel subtitleFontLabel = FontSettingRow.createFontDescriptionLabel();
        private final SubtitleComponent subtitlePreview = new SubtitleComponent();

        // Tracks whether the subtitle preview is currently collapsed (empty), so
        // the dialog is re-packed only on the empty <-> non-empty transition that
        // actually changes the tab's height.
        private boolean subtitlePreviewEmpty = true;

        private TitleTab() {
            titleField.setInputVerifier(new NonEmptyGuard(
                titleField,
                contentPanel,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE,
                Strings.DOCUMENT_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_USE_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_CONTINUE_EDITING
            ));

            var pageBackground = FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND);
            titlePreview.setOpaque(true);
            titlePreview.setBackground(pageBackground);
            subtitlePreview.setOpaque(true);
            subtitlePreview.setBackground(pageBackground);

            // Previews show the chosen font at its natural size: they are never given a
            // ScoreView, so getViewScale() resolves to ViewScale.IDENTITY (no zoom).

            // Keep the title preview in sync as the user edits the number/title,
            // which together form the numbered title the score actually renders.
            var titlePreviewUpdater = new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateTitlePreview();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateTitlePreview();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateTitlePreview();
                }
            };
            numberField.getDocument().addDocumentListener(titlePreviewUpdater);
            titleField.getDocument().addDocumentListener(titlePreviewUpdater);

            // The subtitle preview depends only on the subtitle field, so update it
            // separately rather than firing it on every number/title keystroke.
            var subtitlePreviewUpdater = new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateSubtitlePreview();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateSubtitlePreview();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateSubtitlePreview();
                }
            };
            subtitleField.getDocument().addDocumentListener(subtitlePreviewUpdater);

            // When a field loses focus, replace its text with the normalized
            // (typographically substituted, trimmed) version so the field shows
            // exactly what the commit will save. The substitution is idempotent,
            // so re-firing the preview updater on setText is harmless.
            titleField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    titleField.setText(SongMetadata.normalizeTitle(titleField.getText()));
                }
            });
            subtitleField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    subtitleField.setText(SongMetadata.normalizeTitle(subtitleField.getText()));
                }
            });

            build();
        }

        @Override
        protected void initContents() {
            add(createTitleSection());
            addSectionSeparator(this);
            add(createSubtitleSection());
            addSectionSeparator(this);

            // Stack title and subtitle previews in a vertical panel so the preview
            // section mirrors the actual score layout (title above subtitle with gap).
            // The background must match the page color so createPreviewSection's matte
            // border bleeds correctly into the section border.
            //
            // Each preview is a ScoreComponent whose maximum size equals its
            // preferred size (lineWidthPx). A BoxLayout would honor that maximum
            // and clamp the component below the section width, clipping the
            // centered text. Wrapping each preview in a BorderLayout row stretches
            // it to the full section width (BorderLayout ignores maximum size), so
            // the text — centered internally within lineWidthPx — is never clipped.
            var pageBackground = FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND);
            var stackedPreview = new JPanel();
            stackedPreview.setLayout(new BoxLayout(stackedPreview, BoxLayout.Y_AXIS));
            stackedPreview.setOpaque(true);
            stackedPreview.setBackground(pageBackground);
            stackedPreview.add(createFullWidthPreviewRow(titlePreview, pageBackground));
            stackedPreview.add(createFullWidthPreviewRow(subtitlePreview, pageBackground));

            add(createPreviewSection(stackedPreview));
        }

        private JPanel createTitleSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_TITLE_OF_SONG)
            );
            addLabeledField(
                section,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_NUMBER),
                numberField,
                BaseDialog.LabelPosition.LEFT
            );

            addSeparator(section);

            addFilledFieldRow(
                section,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SONG_TITLE),
                titleField
            );

            addSeparator(section);

            section.add(FontSettingRow.create(
                getMainFrame(),
                titleFontLabel,
                FontKey.TITLE,
                titlePreview::getFont,
                this::applyTitleFont
            ));

            addLargeSeparator(section);
            section.add(createTakePanel());

            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createSubtitleSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_SUBTITLE)
            );

            addFilledFieldRow(
                section,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SUBTITLE),
                subtitleField
            );

            addSeparator(section);
            section.add(FontSettingRow.create(
                getMainFrame(),
                subtitleFontLabel,
                FontKey.SUBTITLE,
                subtitlePreview::getFont,
                this::applySubtitleFont
            ));

            UIUtils.setFlexibleWidth(section);
            return section;
        }

        // Build the field row by hand so the field stretches to fill the
        // remaining width. addLabeledField uses a FlowLayout, which would
        // pin the field to its fixed column width instead.
        private void addFilledFieldRow(JPanel section, String labelText, JComponent field) {
            var horizontalGap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP);
            var row = new JPanel(new BorderLayout(horizontalGap, 0));

            // Match the leading inset addLabeledField's FlowLayout gives the
            // number row, whose hgap also pads before the label.
            row.setBorder(BorderFactory.createEmptyBorder(0, horizontalGap, 0, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            var label = new JLabel(labelText);
            label.setLabelFor(field);
            row.add(label, BorderLayout.WEST);
            row.add(field, BorderLayout.CENTER);
            section.add(row);
        }

        private void applyTitleFont(Font font) {
            titlePreview.setFont(font);
            titlePreview.revalidate();
            titlePreview.repaint();
        }

        private void applySubtitleFont(Font font) {
            subtitlePreview.setFont(font);
            subtitlePreview.revalidate();
            subtitlePreview.repaint();
        }

        Font getTitleFont() {
            return titlePreview.getFont();
        }

        Font getSubtitleFont() {
            return subtitlePreview.getFont();
        }

        String getSubtitleText() {
            return subtitleField.getText();
        }

        private void updateTitlePreview() {
            // Normalize through the same seam the commit uses so the preview shows
            // the typographic substitution (and trimming) the score will render.
            titlePreview.setPreviewText(
                Song.numberedTitle(numberField.getText(), SongMetadata.normalizeTitle(titleField.getText()))
            );
        }

        private void updateSubtitlePreview() {
            var text = SongMetadata.normalizeTitle(subtitleField.getText());
            subtitlePreview.setPreviewText(text);

            // The subtitle preview collapses to zero height when empty and expands
            // when non-empty. The dialog is packed to a fixed height at show time,
            // so re-pack on the empty <-> non-empty transition to fit the new height.
            var empty = text.isEmpty();

            if (empty != subtitlePreviewEmpty) {
                subtitlePreviewEmpty = empty;
                repackToContent();
            }
        }

        private JPanel createTakePanel() {
            var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP), 0));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            panel.add(new JButton(takeAction));

            panel.add(new JLabel(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_THE_FIRST)
            ));

            var spinner = new JSpinner(takeFirstWordsSpinnerModel);
            var editor = (JSpinner.DefaultEditor) spinner.getEditor();
            var textField = editor.getTextField();
            textField.setEditable(false);
            textField.setFocusable(false);

            panel.add(spinner);
            panel.add(new JLabel(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_WORDS_FROM_LYRICS)
            ));

            return panel;
        }

        String getTitleText() {
            return titleField.getText();
        }

        String getNumberText() {
            return numberField.getText();
        }

        @Override
        protected boolean getData() {
            var song = getSong();
            var fonts = requireScoreView().getDocumentFonts();
            titlePreview.setSong(song);
            subtitlePreview.setSong(song);
            FontSettingRow.applyFont(
                fonts.getFont(FontKey.TITLE),
                titleFontLabel,
                this::applyTitleFont
            );
            FontSettingRow.applyFont(
                fonts.getFont(FontKey.SUBTITLE),
                subtitleFontLabel,
                this::applySubtitleFont
            );
            numberField.setText(song.getNumber());
            titleField.setText(song.getTitle());
            subtitleField.setText(song.getSubtitle());
            takeAction.updateEnabledState();
            updateTitlePreview();
            return true;
        }

        private final class TakeFirstLyricsWordAction extends UIAction {

            private TakeFirstLyricsWordAction(MainFrame mainFrame) {
                super(
                    mainFrame,
                    Strings.get(Strings.DIALOG_SONG_SETTINGS_TAKE),
                    "take-lyrics"
                );
            }

            // The button takes its words from the lyrics, so it is meaningless
            // without them. As a UIAction it re-derives its enabled state from
            // this hook on every global UI event (e.g. focusing the title
            // field), so the lyrics check must live here rather than being set
            // once.
            @Override
            protected boolean enableFromSongState() {
                return !getSong().getLyricsText().isEmpty();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                var maxWords = ((Number) takeFirstWordsSpinnerModel.getValue()).intValue();
                titleField.setText(extractLyricsTitle(getSong().getLyricsText(), maxWords));
            }
        }

    }

    private final class AttributionTab extends BaseDialog.Tab {

        // Place and date panel
        private final MyJTextField placeField = new MyJTextField(PLACE_FIELD_COLUMNS);
        private final DateInputRow musicDate = new DateInputRow(this::refreshPreview);

        // Attribution panel
        private final MyJTextField composerField = new MyJTextField(COMPOSER_FIELD_COLUMNS);
        private final MyJTextArea lyricistField = new MyJTextArea(LYRICIST_ROWS, LYRICIST_COLUMNS);
        private final JComboBox<Song.LyricsSource> sourceCombo =
            new JComboBox<>(Song.LyricsSource.values());
        private final JCheckBox differentDateCheckbox = new JCheckBox(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_DIFFERENT_DATE)
        );
        private final DateInputRow wordsDate = new DateInputRow(this::refreshPreview);
        private final JPanel wordsDatePanel = new JPanel();
        private final JCheckBox unofficialTranslationCheck = new JCheckBox(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_UNOFFICIAL_TRANSLATION)
        );
        private final JCheckBox arrangementCheck = new JCheckBox(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_ARRANGEMENT)
        );
        private final AttributionPaneWidget attributionPreview = new AttributionPaneWidget();

        // The attribution and sub-attribution fonts are this tab's own context.
        // The chooser rows write the chosen font into these fields (the source of
        // truth at commit) and their description labels show it. Seeded from the
        // document in the constructor so they are never null.
        private final JLabel attributionFontLabel = FontSettingRow.createFontDescriptionLabel();
        private final JLabel subAttributionFontLabel = FontSettingRow.createFontDescriptionLabel();
        private Font attributionFont;
        private Font subAttributionFont;

        private AttributionTab() {
            sourceCombo.setEditable(false);
            composerField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
            lyricistField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
            lyricistField.setLineWrap(true);
            lyricistField.setWrapStyleWord(true);
            attributionPreview.setOpaque(true);
            attributionPreview.setBackground(
                FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND)
            );

            var fonts = requireScoreView().getDocumentFonts();
            attributionFont = fonts.getFont(FontKey.ATTRIBUTION);
            subAttributionFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);

            build();
        }

        @Override
        protected void initContents() {
            add(createWordsSection());
            addSectionSeparator(this);
            add(createMusicSection());
            addSectionSeparator(this);
            add(createFontsSection());
            addSectionSeparator(this);
            add(createPreviewSection(attributionPreview));
        }

        private JPanel createWordsSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.ATTRIBUTION_ROLE_WORDS)
            );

            var horizontalGap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP);

            var wordsLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_WORDS));
            wordsLabel.setLabelFor(lyricistField);
            var sourceLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_SOURCE));
            sourceLabel.setLabelFor(sourceCombo);

            // Words label + lyricist field, with the field filling the full row.
            var lyricistRow = new JPanel(new BorderLayout(horizontalGap, 0));
            lyricistRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            // Carry the leading gap on the row rather than the label so the label's
            // width stays exactly the shared column width (it lines up with the
            // FlowLayout leading gap of the source row below).
            lyricistRow.setBorder(BorderFactory.createEmptyBorder(0, horizontalGap, 0, 0));
            var lyricistScroll = new JScrollPane(lyricistField);

            // BorderLayout.WEST stretches the label to the full height of the
            // two-row field, so top-align it and nudge it down to sit on the
            // first text line's baseline: past the scroll pane's border and the
            // text area's own top inset.
            wordsLabel.setVerticalAlignment(SwingConstants.TOP);
            wordsLabel.setBorder(BorderFactory.createEmptyBorder(
                lyricistScroll.getInsets().top + lyricistField.getInsets().top, 0, 0, 0
            ));

            // Line up the lyricist field and the source dropdown in one column.
            alignLabelWidths(wordsLabel, sourceLabel);

            lyricistRow.add(wordsLabel, BorderLayout.WEST);
            lyricistRow.add(lyricistScroll, BorderLayout.CENTER);
            section.add(lyricistRow);

            addSeparator(section);

            // Source row: source label + dropdown, different date checkbox.
            var sourceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, horizontalGap, 0));
            sourceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            sourceRow.add(sourceLabel);
            sourceRow.add(sourceCombo);
            addLargeSeparator(sourceRow);
            addLargeSeparator(sourceRow);
            sourceRow.add(differentDateCheckbox);
            section.add(sourceRow);

            // Collapsible words-date panel, hidden by default.
            wordsDatePanel.setLayout(new BoxLayout(wordsDatePanel, BoxLayout.Y_AXIS));
            wordsDatePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            wordsDatePanel.setVisible(false);
            addHorizontalDivider(wordsDatePanel);

            var wordsYearLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_YEAR));
            var wordsDateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wordsDateRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            wordsDate.addTo(wordsDateRow, wordsYearLabel);
            wordsDatePanel.add(wordsDateRow);
            section.add(wordsDatePanel);

            lyricistField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    // An empty lyricist inherits the composer (Sri Chinmoy when the
                    // composer is itself empty); otherwise show the committed value
                    // with typographic substitution applied.
                    if (lyricistField.getText().trim().isEmpty()) {
                        lyricistField.setText(
                            Song.coercePerson(StringUtils.processText(composerField.getText(), false))
                        );
                    } else {
                        lyricistField.setText(StringUtils.processText(lyricistField.getText(), false));
                    }

                    refreshPreview();
                }
            });

            sourceCombo.addActionListener(e -> refreshPreview());

            differentDateCheckbox.addActionListener(e -> {
                syncWordsDatePanel();
                refreshPreview();
            });

            // Don't let the section grow vertically
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createMusicSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.ATTRIBUTION_ROLE_MUSIC)
            );

            var horizontalGap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP);

            // Lay out the music row: the composer field fills the available space
            // while the arrangement checkbox stays right-aligned.
            var composerRow = new JPanel(new BorderLayout(horizontalGap, 0));
            composerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            var composerPanel = new JPanel(new BorderLayout(horizontalGap, 0));
            var composerLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_COMPOSER));
            composerLabel.setLabelFor(composerField);

            // Match the FlowLayout leading gap of the single-field rows below.
            composerLabel.setBorder(BorderFactory.createEmptyBorder(0, horizontalGap, 0, 0));
            composerPanel.add(composerLabel, BorderLayout.WEST);
            composerPanel.add(composerField, BorderLayout.CENTER);
            composerRow.add(composerPanel, BorderLayout.CENTER);

            // Arrangement checkbox, anchored to the right. BorderLayout.EAST
            // stretches it to the field's height; the checkbox centers its own
            // content vertically.
            composerRow.add(arrangementCheck, BorderLayout.EAST);
            section.add(composerRow);

            var song = getSong();

            if (!song.getTranslatedLyrics().isEmpty()) {
                addSeparator(section);
                unofficialTranslationCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
                section.add(unofficialTranslationCheck);
            }

            addHorizontalDivider(section);

            // addLabeledField wires setLabelFor for each row below.
            var yearLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_YEAR));
            var placeLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_PLACE));

            // Line up the year field (first in the date row) and the place field
            // in one column.
            alignLabelWidths(yearLabel, placeLabel);

            var datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            datePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(datePanel);

            musicDate.addTo(datePanel, yearLabel);

            addSeparator(section);

            addLabeledField(section, placeLabel, placeField);

            composerField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    // Show the committed value: typographic substitution applied,
                    // an empty field coerced to Sri Chinmoy.
                    composerField.setText(
                        Song.coercePerson(StringUtils.processText(composerField.getText(), false))
                    );

                    // An empty (or whitespace-only) lyricist inherits the
                    // composer, so mirror the normalized composer into it.
                    if (lyricistField.getText().trim().isEmpty()) {
                        lyricistField.setText(composerField.getText());
                    }

                    refreshPreview();
                }
            });

            unofficialTranslationCheck.addActionListener(e -> refreshPreview());
            arrangementCheck.addActionListener(e -> refreshPreview());

            placeField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    placeField.setText(StringUtils.processText(placeField.getText(), false));
                    refreshPreview();
                }
            });

            // Don't let the section grow vertically
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createFontsSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_FONTS)
            );

            var wordsMusicLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_WORDS_MUSIC));
            var datePlaceLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_DATE_PLACE));
            alignLabelWidths(wordsMusicLabel, datePlaceLabel);

            var mainFrame = getMainFrame();

            section.add(FontSettingRow.create(
                mainFrame,
                wordsMusicLabel,
                attributionFontLabel,
                FontKey.ATTRIBUTION,
                () -> attributionFont,
                this::applyAttributionFont
            ));

            addSeparator(section);

            section.add(FontSettingRow.create(
                mainFrame,
                datePlaceLabel,
                subAttributionFontLabel,
                FontKey.SUB_ATTRIBUTION,
                () -> subAttributionFont,
                this::applySubAttributionFont
            ));

            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private void applyAttributionFont(Font font) {
            attributionFont = font;
            refreshPreview();
        }

        private void applySubAttributionFont(Font font) {
            subAttributionFont = font;
            refreshPreview();
        }

        Font getAttributionFont() {
            return attributionFont;
        }

        Font getSubAttributionFont() {
            return subAttributionFont;
        }

        /**
         * Forces every label to the widest label's preferred width so the fields
         * that follow them line up in a column. Each label keeps its own height;
         * only the width is unified.
         */
        private static void alignLabelWidths(JLabel... labels) {
            // Measure the text width with the shared screen render context, whose
            // antialiasing and fractional metrics match how FlatLaf actually
            // paints it. This runs at build time, before the labels are in a
            // realized window, where the default getPreferredSize() measures
            // without those hints and rounds down — leaving the widest label ~1px
            // short of its locked width and triggering an ellipsis ("Place:" ->
            // "Pla...").
            var width = 0;

            for (var label : labels) {
                var insets = label.getInsets();
                var textWidth = (int) Math.ceil(
                    label.getFont().getStringBounds(label.getText(), GraphicUtils.SCREEN_FRC).getWidth()
                );
                width = Math.max(width, textWidth + insets.left + insets.right);
            }

            for (var label : labels) {
                var fixed = new Dimension(width, label.getPreferredSize().height);
                label.setPreferredSize(fixed);
                label.setMinimumSize(fixed);
            }
        }

        private void refreshPreview() {
            // Use the tab's in-progress fonts (not the committed document fonts)
            // so the preview reflects font edits made in this tab's font rows
            // before the dialog is committed.
            attributionPreview.setPreviewState(
                attributionFont,
                subAttributionFont,
                buildPreviewLines()
            );

            // The preview's height changes with both the line count and the
            // attribution/sub-attribution font size. The dialog is packed to a
            // fixed height at show time, so a taller preview would be starved by
            // the tab's GridBagLayout. Re-pack so the window fits the new height.
            repackToContent();
        }

        // The words-date panel is visible exactly when the "different date"
        // checkbox is selected; both the listener and getData() derive it here.
        private void syncWordsDatePanel() {
            wordsDatePanel.setVisible(differentDateCheckbox.isSelected());
        }

        /**
         * Resolves the lyricist credit from the widgets: the trimmed lyricist
         * field, or the composer credit when the lyricist field is blank
         * (matching the legacy field-by-field default).
         */
        private String resolveLyricistText(String composerText) {
            var lyricistText = lyricistField.getText().trim();
            return lyricistText.isEmpty() ? composerText : lyricistText;
        }

        private List<AttributionLine> buildPreviewLines() {
            var song = getSong();
            // Read live widget values, not committed Song state, so the preview
            // reflects uncommitted edits. The SongMetadata constructor normalizes
            // each field, so the raw widget text is passed through directly.
            var composerText = composerField.getText();
            var lyricistText = resolveLyricistText(composerText);
            var lyricsSource = (Song.LyricsSource) sourceCombo.getSelectedItem();

            if (lyricsSource == null) {
                lyricsSource = Song.LyricsSource.LYRICIST;
            }

            var arrangement = arrangementCheck.isSelected();
            var unofficialTranslation = unofficialTranslationCheck.isSelected();
            var gatedDate = gatedWordsDate(
                differentDateCheckbox.isSelected(),
                wordsDate.getYear(),
                wordsDate.getMonth(),
                wordsDate.getDay()
            );
            var metadata = new SongMetadata(
                textTab.getTitleText(),
                textTab.getNumberText(),
                getPlaceText(),
                getYearText(),
                getMonth(),
                getDay(),
                composerText,
                lyricistText,
                lyricsSource,
                arrangement,
                unofficialTranslation,
                textTab.getSubtitleText(),
                gatedDate.year(),
                gatedDate.month(),
                gatedDate.day()
            );
            var showTranslation = !unofficialTranslation && !song.getTranslatedLyrics().isEmpty();
            return AttributionFormatter.lines(metadata, showTranslation);
        }

        String getPlaceText() {
            return placeField.getText();
        }

        String getYearText() {
            return musicDate.getYear();
        }

        int getMonth() {
            return musicDate.getMonth();
        }

        int getDay() {
            return musicDate.getDay();
        }

        String getComposerText() {
            return Song.coercePerson(composerField.getText());
        }

        String getLyricistText() {
            return resolveLyricistText(getComposerText());
        }

        Song.LyricsSource getLyricsSource() {
            var lyricsSource = (Song.LyricsSource) sourceCombo.getSelectedItem();
            return lyricsSource != null ? lyricsSource : Song.LyricsSource.LYRICIST;
        }

        boolean isArrangement() {
            return arrangementCheck.isSelected();
        }

        boolean isUnofficialTranslation() {
            return unofficialTranslationCheck.isSelected();
        }

        boolean isDifferentDate() {
            return differentDateCheckbox.isSelected();
        }

        String getWordsYearText() {
            return wordsDate.getYear();
        }

        int getWordsMonth() {
            return wordsDate.getMonth();
        }

        int getWordsDay() {
            return wordsDate.getDay();
        }

        @Override
        protected boolean getData() {
            var song = getSong();
            placeField.setText(song.getPlace());
            musicDate.setValues(song.getYear(), song.getMonth(), song.getDay());
            composerField.setText(song.getComposer());
            lyricistField.setText(song.getLyricist());
            sourceCombo.setSelectedItem(song.getLyricsSource());
            arrangementCheck.setSelected(song.isArrangement());
            unofficialTranslationCheck.setSelected(song.isUnofficialTranslation());

            var wordsYear = song.getWordsYear();
            wordsDate.setValues(wordsYear, song.getWordsMonth(), song.getWordsDay());
            differentDateCheckbox.setSelected(!wordsYear.isEmpty());
            syncWordsDatePanel();
            wordsDate.updateFieldStates();

            // Populate both font rows' description labels from the document and
            // refresh the preview with the current fonts.
            var fonts = requireScoreView().getDocumentFonts();
            FontSettingRow.applyFont(fonts.getFont(FontKey.ATTRIBUTION), attributionFontLabel, this::applyAttributionFont);
            FontSettingRow.applyFont(fonts.getFont(FontKey.SUB_ATTRIBUTION), subAttributionFontLabel, this::applySubAttributionFont);
            return true;
        }

        /**
         * Thin Swing wrapper around {@link AttributionPane}.
         * Delegates measure and paint to the bare rendering surface;
         * stores the current fonts so {@link #getPreferredSize()} and
         * {@link #paintComponent} can pass them through.
         */
        private static final class AttributionPaneWidget extends JComponent {

            private final AttributionPane pane = new AttributionPane();

            @Nullable
            private Font attributionFont;

            @Nullable
            private Font subAttributionFont;

            /**
             * Sets both fonts and the override lines, then invalidates size and
             * paint once. Batched so a single preview refresh triggers one
             * {@code revalidate}/{@code repaint} rather than three.
             */
            void setPreviewState(
                Font attributionFont,
                Font subAttributionFont,
                @Nullable List<AttributionLine> lines
            ) {
                this.attributionFont = attributionFont;
                this.subAttributionFont = subAttributionFont;
                pane.setOverrideLines(lines);
                revalidate();
                repaint();
            }

            @Override
            public Dimension getPreferredSize() {
                if (attributionFont == null || subAttributionFont == null) {
                    return new Dimension(0, 0);
                }

                return pane.getContentSizePx(attributionFont, subAttributionFont);
            }

            @Override
            public Dimension getMaximumSize() {
                // Track the preferred size so the BoxLayout never stretches the
                // preview vertically. Computed dynamically because the preferred
                // size is zero until the fonts are set by the first refresh.
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                if (attributionFont == null || subAttributionFont == null) {
                    return;
                }

                var g2 = (Graphics2D) g;
                GraphicUtils.setRenderingHints(g2);
                pane.render(g2, 0, 0, getWidth(), attributionFont, subAttributionFont);
            }
        }

    }

    /**
     * A self-contained date-input row (year, month, day) that can be embedded
     * in any dialog panel. Owns its three widgets, wires their listeners, and
     * exposes pure predicate methods so callers can unit-test the enable/reset
     * logic without driving Swing.
     */
    static final class DateInputRow {

        private final NumericTextField yearField =
            new NumericTextField(YEAR_FIELD_COLUMNS, YEAR_MIN, YEAR_MAX, true, MAX_YEAR_CHARS);
        private final JComboBox<String> monthCombo = new JComboBox<>(
            new String[]{
                "",
                Strings.get(Strings.MONTH_JANUARY),
                Strings.get(Strings.MONTH_FEBRUARY),
                Strings.get(Strings.MONTH_MARCH),
                Strings.get(Strings.MONTH_APRIL),
                Strings.get(Strings.MONTH_MAY),
                Strings.get(Strings.MONTH_JUNE),
                Strings.get(Strings.MONTH_JULY),
                Strings.get(Strings.MONTH_AUGUST),
                Strings.get(Strings.MONTH_SEPTEMBER),
                Strings.get(Strings.MONTH_OCTOBER),
                Strings.get(Strings.MONTH_NOVEMBER),
                Strings.get(Strings.MONTH_DECEMBER),
            }
        );
        private final JComboBox<String> dayCombo;

        // Set while month/day combos are changed programmatically — by the year
        // focus listener's reset and by setValues' seeding — so their action
        // listeners skip the work (and the onChange callback) those changes trigger.
        private boolean adjustingDateFields;

        DateInputRow(Runnable onChange) {
            monthCombo.setEditable(false);

            var days = new String[DAYS_IN_MONTH_MAX + 1];
            days[0] = "";

            for (var i = 1; i <= DAYS_IN_MONTH_MAX; i++) {
                days[i] = Integer.toString(i);
            }

            dayCombo = new JComboBox<>(days);
            dayCombo.setEditable(false);

            yearField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    var yearValid = yearField.hasValidValue();

                    if (!yearValid) {
                        // Reset month/day programmatically; the guard keeps the
                        // combos' listeners from re-running this same validation.
                        adjustingDateFields = true;
                        monthCombo.setSelectedIndex(0);
                        dayCombo.setSelectedIndex(0);
                        adjustingDateFields = false;
                    }

                    updateFieldStates(yearValid);

                    if (yearValid) {
                        onChange.run();
                    }
                }
            });
            monthCombo.addActionListener(e -> {
                if (adjustingDateFields) {
                    return;
                }

                if (monthCombo.getSelectedIndex() == 0) {
                    dayCombo.setSelectedIndex(0);
                }

                var yearValid = yearField.hasValidValue();
                updateFieldStates(yearValid);

                if (yearValid) {
                    onChange.run();
                }
            });
            dayCombo.addActionListener(e -> {
                if (adjustingDateFields) {
                    return;
                }

                if (yearField.hasValidValue()) {
                    onChange.run();
                }
            });
        }

        /**
         * Returns true when the day combo should be enabled.
         * Pure: no side effects, safe to call from tests.
         */
        static boolean dayEnabled(boolean yearValid, int month) {
            return yearValid && month != 0;
        }

        /**
         * Appends the three labeled date fields (year, month, day) to {@code panel}.
         * The year field is added using the pre-built {@code yearLabel} so the caller
         * can column-align it before layout; month and day use inline left-position labels.
         */
        void addTo(JComponent panel, JLabel yearLabel) {
            BaseDialog.addLabeledField(panel, yearLabel, yearField);

            BaseDialog.addLabeledField(
                panel,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_MONTH),
                monthCombo,
                BaseDialog.LabelPosition.LEFT
            );

            BaseDialog.addLabeledField(
                panel,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_DAY),
                dayCombo,
                BaseDialog.LabelPosition.LEFT
            );
        }

        /**
         * Seeds the three widgets from stored song data and refreshes the
         * enable states to match. Seeding is guarded so the combos' listeners
         * do not fire the onChange callback once per field during population;
         * the caller refreshes the preview a single time afterward.
         */
        void setValues(String year, int month, int day) {
            adjustingDateFields = true;
            yearField.setText(year);
            monthCombo.setSelectedIndex(month);
            dayCombo.setSelectedIndex(day);
            adjustingDateFields = false;
            updateFieldStates(yearField.hasValidValue());
        }

        String getYear() {
            return yearField.getText();
        }

        int getMonth() {
            return monthCombo.getSelectedIndex();
        }

        int getDay() {
            return dayCombo.getSelectedIndex();
        }

        /**
         * Refreshes the enabled states of the month and day combos based on the
         * current year validity and month selection.
         */
        void updateFieldStates() {
            updateFieldStates(yearField.hasValidValue());
        }

        private void updateFieldStates(boolean yearValid) {
            // The month combo is enabled exactly when the year is valid.
            monthCombo.setEnabled(yearValid);
            dayCombo.setEnabled(dayEnabled(yearValid, monthCombo.getSelectedIndex()));
        }

    }

    private static void addHorizontalDivider(JComponent container) {
        addLargeSeparator(container);
        var separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(separator);
        addLargeSeparator(container);
    }

    private final class MusicTab extends BaseDialog.Tab {

        private final TempoSection tempoSection = new TempoSection(
            Duration.values(),
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SHOW_ONLY_DESCRIPTION),
            "tempos"
        );

        private final JComboBox<KeySelection> keyCombo = new JComboBox<>(
            KeyCellRenderer.SELECTIONS.toArray(new KeySelection[0])
        );

        private final MyJTextField lineWidthField = new MyJTextField(6);
        private final JLabel unitLabel = new JLabel();

        private MusicTab() {
            keyCombo.setRenderer(new KeyCellRenderer());
            keyCombo.setMaximumRowCount(7);

            // Key signature is always drawn in a light mode to match the score
            keyCombo.setOpaque(true);
            keyCombo.putClientProperty(
                FlatClientProperties.STYLE,
                "popupBackground: #FFFFFF; " +
                    "foreground: #000000; " +
                    "background: #FFFFFF; " +
                    "editableBackground: #FFFFFF"
            );

            InputUtils.addDecimalFilter(lineWidthField);
            lineWidthField.setInputVerifier(new LineWidthVerifier());

            build();
        }

        @Override
        protected void initContents() {
            add(createTempoSection(), constraints);
            addSectionSeparator(this);
            add(createKeySignatureSection());
            addSectionSeparator(this);
            add(createLineWidthSection());
        }

        private JPanel createTempoSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_TEMPO)
            );

            // Don't let the section grow vertically
            section.add(tempoSection);
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createKeySignatureSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_KEY_SIGNATURE)
            );
            keyCombo.setMaximumSize(keyCombo.getPreferredSize());
            keyCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(keyCombo);

            // Don't let the section grow vertically
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createLineWidthSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_LINE_WIDTH)
            );

            var row = new JPanel(
                new FlowLayout(FlowLayout.LEFT, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP), 0)
            );
            row.setBorder(BorderFactory.createEmptyBorder());
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            var label = new JLabel(Strings.get(Strings.LABEL_WIDTH));
            label.setLabelFor(lineWidthField);
            row.add(label);
            row.add(lineWidthField);
            row.add(unitLabel);
            section.add(row);

            // Don't let the section grow vertically
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        @Override
        protected boolean getData() {
            var song = getSong();
            tempoSection.setTempo(song.getEffectiveTempo());
            setKeyComboFromSong(song);
            revertLineWidthField();
            return true;
        }

        @Override
        protected void setData() {
            var song = getSong();
            applyMusicTabChanges(
                song,
                tempoSection.getTempoType(),
                tempoSection.getVisibleTempo(),
                tempoSection.getTempoDescription(),
                !tempoSection.isShowOnlyDescription(),
                getKeyTypeAndCountFromCombo()
            );

            // The field holds physical inches; convert to document pixels, then to staff
            // spaces, then back to pixels for updatePageLayout.
            var widthInches = validateLineWidth();
            var lineWidthDocPx = new DocPx(widthInches * GraphicUtils.getDpi());
            var lineWidthSs = ScaleContext.pxToSs(lineWidthDocPx.value());
            requireScoreView().updatePageLayout(ScaleContext.ssToRoundedPx(lineWidthSs));
        }

        @Override
        protected boolean isValidData() {
            return validateLineWidth() >= 0;
        }

        private void setKeyComboFromSong(Song song) {
            keyCombo.setSelectedItem(canonicalKeySelectionFrom(song));
        }

        private KeySelection getKeyTypeAndCountFromCombo() {
            var selected = (KeySelection) keyCombo.getSelectedItem();

            if (selected == null) {
                throw RuntimeError.exit("Key combo has no selection");
            }

            return selected;
        }

        private void revertLineWidthField() {
            var isMetric = Prefs.getBoolean(PrefsKey.METRIC);
            var lineWidthDocPx = new DocPx(ScaleContext.ssToPx(getSong().getLineWidthSs()));
            var lineWidthInches = lineWidthDocPx.value() / GraphicUtils.getDpi();
            var displayValue = isMetric
                ? lineWidthInches * GraphicUtils.CM_PER_INCH
                : lineWidthInches;
            lineWidthField.setText(
                String.valueOf(Utils.roundToTwoDecimalPlaces(displayValue))
            );
            unitLabel.setText(
                Strings.get(isMetric ? Strings.LABEL_UNIT_CM : Strings.LABEL_UNIT_INCHES)
            );
        }

        /**
         * Parses and validates the current line width field text.
         *
         * @return the width in inches if valid, or -1 if the text is empty,
         *         unparseable, or out of range
         */
        private double validateLineWidth() {
            return validateLineWidthText(
                lineWidthField.getText(),
                Prefs.getBoolean(PrefsKey.METRIC)
            );
        }

        private void showLineWidthError(String key, boolean isMetric) {
            var min = PageModel.MIN_LINE_WIDTH_INCHES;
            var max = PageModel.MAX_LINE_WIDTH_INCHES;

            if (isMetric) {
                min *= GraphicUtils.CM_PER_INCH;
                max *= GraphicUtils.CM_PER_INCH;
            }

            var unit = Strings.get(isMetric ? Strings.LABEL_UNIT_CM : Strings.LABEL_UNIT_INCHES);

            OptionDialogs.showErrorMessage(
                contentPanel,
                Strings.ALERT_TITLE_LINE_WIDTH_ERROR,
                key, min, max, unit
            );
        }

        /**
         * Validates the line width when focus leaves the field. While the value
         * is empty, unparseable, or out of range the field refuses to yield
         * focus and shows the reason, mirroring the number field on the Text
         * tab. Cancel bypasses this via
         * {@code cancelButton.setVerifyInputWhenFocusTarget(false)}.
         */
        private class LineWidthVerifier extends InputVerifier {

            @Override
            public boolean verify(JComponent input) {
                return validateLineWidth() >= 0;
            }

            @Override
            public boolean shouldYieldFocus(JComponent source, JComponent target) {
                if (validateLineWidth() >= 0) {
                    return true;
                }

                // validateLineWidth collapses both failure modes to -1, so parse
                // once more to distinguish an unparseable value from an in-range
                // violation and show the matching message.
                var isMetric = Prefs.getBoolean(PrefsKey.METRIC);

                try {
                    Double.parseDouble(lineWidthField.getText());
                } catch (NumberFormatException e) {
                    showLineWidthError(Strings.ERROR_LINE_WIDTH_INVALID, isMetric);
                    return false;
                }

                showLineWidthError(Strings.ERROR_LINE_WIDTH_RANGE, isMetric);
                return false;
            }
        }
    }

    private final class FontTab extends BaseDialog.Tab {

        // The Fonts tab owns the fonts that have no dedicated contextual tab:
        // lyrics and annotation. Title, attribution, and sub-attribution fonts
        // are owned by the Title and Attribution tabs respectively.
        private final JLabel lyricsFontLabel = FontSettingRow.createFontDescriptionLabel();
        private final ScoreTextPreview lyricsFontPreview = new ScoreTextPreview(
            "I shall bind myself at Your Feet.",
            "With this hope I have come to You",
            "   With tear-filled eyes.",
            "I shall worship You within the tumult",
            "   Of this life.",
            "I shall satisfy You on the strength",
            "   Of my surrender."
        );

        private final JLabel annotationFontLabel = FontSettingRow.createFontDescriptionLabel();
        private final ScoreTextPreview annotationFontPreview = new ScoreTextPreview(
            "D.C. al fine (a tempo)"
        );

        private FontTab() {
            super(FlatLafKey.DIALOG_SONG_SETTINGS_FONT_PADDING);

            var pageBackground = FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND);

            for (var preview : new ScoreTextPreview[]{ lyricsFontPreview, annotationFontPreview }) {
                preview.setBackground(pageBackground);
                preview.setForeground(Color.BLACK);
                preview.setOpaque(true);
            }

            build();
        }

        @Override
        protected void initContents() {
            var mainFrame = getMainFrame();

            var previewPadding = FlatLafProps.getInsets(FlatLafKey.DIALOG_SONG_SETTINGS_FONT_PREVIEW_PADDING);

            var lyricsSection = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_LYRICS_TRANSLATION)
            );
            lyricsSection.add(FontSettingRow.create(
                mainFrame, lyricsFontLabel, FontKey.LYRICS, lyricsFontPreview::getFont, lyricsFontPreview::setFont
            ));
            addLargeSeparator(lyricsSection);
            lyricsSection.add(createPreviewWrapper(lyricsFontPreview, previewPadding));
            UIUtils.setFlexibleWidth(lyricsSection);
            add(lyricsSection);

            addSectionSeparator(this);

            var annotationSection = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_ANNOTATION)
            );
            annotationSection.add(FontSettingRow.create(
                mainFrame, annotationFontLabel, FontKey.ANNOTATION, annotationFontPreview::getFont, annotationFontPreview::setFont
            ));
            addLargeSeparator(annotationSection);
            annotationSection.add(createPreviewWrapper(annotationFontPreview, previewPadding));
            UIUtils.setFlexibleWidth(annotationSection);
            add(annotationSection);
        }

        /**
         * Renders sample lines by hand with {@link GraphicUtils#setRenderingHints},
         * the same way the score draws its text. A {@link JLabel} can't be used
         * here: its UI re-imposes the platform text-antialiasing hint (LCD
         * subpixel on macOS) over any hint set on the graphics, which fringes
         * against the off-white page background and looks unlike the score.
         */
        private static final class ScoreTextPreview extends JComponent {

            private final List<String> lines;

            private ScoreTextPreview(String... lines) {
                this.lines = List.of(lines);
            }

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;

                if (isOpaque()) {
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }

                GraphicUtils.setRenderingHints(g2);
                g2.setFont(getFont());
                g2.setColor(getForeground());

                var metrics = g2.getFontMetrics();
                var lineHeight = metrics.getHeight();
                var baselineY = metrics.getAscent();

                for (var line : lines) {
                    g2.drawString(line, 0, baselineY);
                    baselineY += lineHeight;
                }
            }

            @Override
            public void setFont(Font font) {
                super.setFont(font);
                revalidate();
                repaint();
            }

            @Override
            public Dimension getPreferredSize() {
                var font = getFont();

                if (font == null) {
                    return new Dimension(0, 0);
                }

                var metrics = getFontMetrics(font);
                var width = 0;

                for (var line : lines) {
                    width = Math.max(width, metrics.stringWidth(line));
                }

                // Tight box: ascent + descent per line, with the font's leading
                // inserted only between lines, never below the last descender.
                var lineCount = lines.size();
                var glyphHeight = metrics.getAscent() + metrics.getDescent();
                var height = lineCount * glyphHeight + (lineCount - 1) * metrics.getLeading();

                return new Dimension(width, height);
            }
        }

        @Override
        protected boolean getData() {
            var fonts = requireScoreView().getDocumentFonts();
            FontSettingRow.applyFont(fonts.getFont(FontKey.LYRICS),     lyricsFontLabel,     lyricsFontPreview::setFont);
            FontSettingRow.applyFont(fonts.getFont(FontKey.ANNOTATION), annotationFontLabel, annotationFontPreview::setFont);
            return true;
        }

        Font getLyricsFont() {
            return lyricsFontPreview.getFont();
        }

        Font getAnnotationFont() {
            return annotationFontPreview.getFont();
        }
    }

    public static class KeyCellRenderer implements ListCellRenderer<KeySelection> {

        private static final float FONT_SIZE_PT = 120f;

        private static final Font FONT = MyFontUtils.getIconFont()
            .deriveFont(FONT_SIZE_PT);

        /**
         * All key-signature selections in display order:
         * no accidentals, then 1-7 flats, then 1-7 sharps.
         */
        public static final List<KeySelection> SELECTIONS;

        // MusescoreIcon font glyph per selection.
        private static final Map<KeySelection, String> GLYPHS;

        static {
            var selections = new ArrayList<KeySelection>(1 + 2 * KeySignature.MAX_ACCIDENTAL_COUNT);
            var glyphs = new HashMap<KeySelection, String>();

            var noAccidentals = new KeySelection(KeyType.FLATS, 0);
            selections.add(noAccidentals);
            glyphs.put(noAccidentals, "\uF377");

            var flatGlyphs = new String[]{
                "\uF37F", "\uF380", "\uF381", "\uF382", "\uF383", "\uF384", "\uF385"
            };

            for (var i = 0; i < KeySignature.MAX_ACCIDENTAL_COUNT; i++) {
                var flatSelection = new KeySelection(KeyType.FLATS, i + 1);
                selections.add(flatSelection);
                glyphs.put(flatSelection, flatGlyphs[i]);
            }

            var sharpGlyphs = new String[]{
                "\uF378", "\uF379", "\uF37A", "\uF37B", "\uF37C", "\uF37D", "\uF37E"
            };

            for (var i = 0; i < KeySignature.MAX_ACCIDENTAL_COUNT; i++) {
                var sharpSelection = new KeySelection(KeyType.SHARPS, i + 1);
                selections.add(sharpSelection);
                glyphs.put(sharpSelection, sharpGlyphs[i]);
            }

            SELECTIONS = List.copyOf(selections);
            GLYPHS = Map.copyOf(glyphs);
        }

        @Override
        public Component getListCellRendererComponent(
            JList<? extends KeySelection> list,
            KeySelection value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            return new KeyLabel(value, list, index, isSelected);
        }

        private static final class KeyLabel extends BaseLabel {

            private static final int CELL_PADDING_Y_PX = 10;
            private static final int GLYPH_BOX_WIDTH_PX;
            private static final int GLYPH_BOX_HEIGHT_PX;
            private static final int LABEL_GAP_PX;
            private static final int LABEL_WIDTH_PX;
            private static final Dimension CELL_SIZE;

            // GlyphVector and TextLayout are immutable once constructed for a fixed
            // FRC, so precompute one per selection and reuse across renders. Without
            // this cache, every getListCellRendererComponent call would allocate a
            // GlyphVector and a TextLayout.
            private record CellCache(
                GlyphVector glyphVector,
                Rectangle2D glyphBounds,
                TextLayout labelLayout
            ) {}

            private static final Map<KeySelection, CellCache> CELL_CACHE;

            static {
                var cache = new HashMap<KeySelection, CellCache>();

                var maxGlyphWidth = 0.0;
                var maxGlyphHeight = 0.0;
                var maxLabelWidth = 0;

                for (var selection : SELECTIONS) {
                    var glyph = GLYPHS.get(selection);

                    if (glyph == null) {
                        // glyph absent from the font => missing font resource, not a bad-selection bug
                        throw RuntimeError.missingResource(
                            "Missing glyph for key selection: " + selection
                        );
                    }

                    var glyphVector = FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, glyph);
                    var visualBounds = glyphVector.getVisualBounds();
                    maxGlyphWidth = Math.max(maxGlyphWidth, visualBounds.getWidth());
                    maxGlyphHeight = Math.max(maxGlyphHeight, visualBounds.getHeight());

                    var attributed = KeySignatureDisplay.getDisplayName(
                        selection.keyType(),
                        selection.count()
                    );
                    var textLayout = new TextLayout(
                        attributed.getIterator(),
                        GraphicUtils.SCREEN_FRC
                    );
                    maxLabelWidth = Math.max(
                        maxLabelWidth,
                        (int) Math.ceil(textLayout.getAdvance())
                    );

                    cache.put(selection, new CellCache(glyphVector, visualBounds, textLayout));
                }

                CELL_CACHE = Map.copyOf(cache);

                // Font metrics for icon fonts include large built-in whitespace that
                // doesn't reflect the actual ink bounds. Use visual bounds so the cell
                // tightly wraps the rendered image.
                GLYPH_BOX_WIDTH_PX = (int) Math.ceil(maxGlyphWidth);
                GLYPH_BOX_HEIGHT_PX = (int) Math.ceil(maxGlyphHeight);
                LABEL_GAP_PX = FlatLafProps.getInt(
                    FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP
                );
                LABEL_WIDTH_PX = maxLabelWidth;

                var cellWidth = GLYPH_BOX_WIDTH_PX + LABEL_GAP_PX + LABEL_WIDTH_PX;
                var cellHeight = GLYPH_BOX_HEIGHT_PX + 2 * CELL_PADDING_Y_PX;
                CELL_SIZE = new Dimension(cellWidth, cellHeight);
            }

            private final GlyphVector glyphVector;
            private final Rectangle2D glyphBounds;
            private final TextLayout labelLayout;

            private KeyLabel(
                KeySelection selection,
                JList<?> list,
                int index,
                boolean isSelected
            ) {
                super("", list, index, isSelected);
                setPreferredSize(CELL_SIZE);
                setBackground(isSelected ? list.getSelectionBackground() : Color.WHITE);
                setForeground(isSelected ? list.getSelectionForeground() : Color.BLACK);

                var cache = CELL_CACHE.get(selection);

                if (cache == null) {
                    throw RuntimeError.exit(
                        "No render cache for key selection: " + selection
                    );
                }

                glyphVector = cache.glyphVector();
                glyphBounds = cache.glyphBounds();
                labelLayout = cache.labelLayout();
            }

            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                var g2 = (Graphics2D) g;
                // Use the glyph's visual bounds (not advance width) for centering, since
                // the MusescoreIcon font has a large advance width unrelated to ink size.
                var contentXOffset = Math.max(0, (getWidth() - CELL_SIZE.width) / 2);
                var glyphX = (float) (contentXOffset
                    + (GLYPH_BOX_WIDTH_PX - glyphBounds.getWidth()) / 2
                    - glyphBounds.getX());
                var glyphY = (float) (CELL_PADDING_Y_PX - glyphBounds.getY());
                g2.drawGlyphVector(glyphVector, glyphX, glyphY);

                var labelX = (float) (contentXOffset + GLYPH_BOX_WIDTH_PX + LABEL_GAP_PX);
                var labelY = (getHeight() + labelLayout.getAscent()
                    - labelLayout.getDescent()) / 2f;
                labelLayout.draw(g2, labelX, labelY);
            }
        }
    }
}
