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
import songscribe.ui.component.score.TitleComponent;
import songscribe.dom.AttributionFormatter;
import songscribe.dom.AttributionLine;
import songscribe.dom.AttributionPane;
import songscribe.dom.SongMetadata;
import songscribe.layout.PageModel;
import songscribe.dom.ScaleContext;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;
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

    private final FontTab fontTab = new FontTab();
    private final TitleTab textTab = new TitleTab();
    private final AttributionTab attributionTab = new AttributionTab();

    public SongSettingsDialog() {
        super(Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), true, DialogCategory.EXCLUSIVE);

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
            unofficialTranslation
        );
        song.postWithModification(new SongMetadataDidChangeNotification(newMetadata));
    }

    // ── Package-private static helpers ──
    //
    // These pure-logic units are inlined into the tab inner classes' Swing-bound
    // methods at their call sites. Per "Testability Over Encapsulation" they live
    // here as self-contained, directly unit-testable helpers, and the inner
    // classes delegate to them.

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
        if (words.length() > 0 && !Character.isLetter(words.charAt(words.length() - 1))) {
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
        var backgroundColor = preview.getBackground();
        var previewWrapper = new JPanel();
        previewWrapper.setOpaque(true);
        previewWrapper.setBackground(backgroundColor);
        previewWrapper.setBorder(BorderFactory.createMatteBorder(gap, 0, gap, 0, backgroundColor));
        previewWrapper.setLayout(new BorderLayout());
        previewWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewWrapper.add(preview, BorderLayout.CENTER);
        section.add(previewWrapper);

        return section;
    }

    private final class TitleTab extends BaseDialog.Tab {

        // Title of song panel
        private final NumericTextField numberField =
            new NumericTextField(3, SONG_NUMBER_MIN, SONG_NUMBER_MAX, true);
        private final MyJTextField titleField = new MyJTextField(47);

        // Non-shared font-name display for the title font row. Mirrors the
        // shared font preview so we can prototype the new layout without
        // touching FontTab's data wiring (refactor later).
        private final JTextField titleFontField = new JTextField(31);
        private final SpinnerModel takeFirstWordsSpinnerModel =
            new SpinnerNumberModel(TAKE_FIRST_WORDS_DEFAULT, TAKE_FIRST_WORDS_MIN, TAKE_FIRST_WORDS_MAX, 1);
        private final TakeFirstLyricsWordAction takeAction =
            new TakeFirstLyricsWordAction(SongSettingsDialog.this.getMainFrame());
        private final TitleComponent titlePreview = new TitleComponent();

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

            titleFontField.setEditable(false);
            titleFontField.setFocusable(false);

            titlePreview.setOpaque(true);
            titlePreview.setBackground(
                FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND)
            );

            // Keep the preview in sync as the user edits the number/title, which
            // together form the numbered title the score actually renders.
            var previewUpdater = new DocumentListener() {
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
            numberField.getDocument().addDocumentListener(previewUpdater);
            titleField.getDocument().addDocumentListener(previewUpdater);

            // The title font is edited via the shared font preview (this tab's
            // font row and the Fonts tab both target it); mirror its font onto
            // the preview so font changes show immediately, before commit.
            fontTab.titleFontPreview.addPropertyChangeListener("font", e -> {
                var font = fontTab.titleFontPreview.getFont();
                titleFontField.setText(MyFontUtils.getFullFontDescription(font));
                titlePreview.setFont(font);
                titlePreview.revalidate();
                titlePreview.repaint();
            });

            build();
        }

        @Override
        protected void initContents() {
            add(createTitleSection());
            addSectionSeparator(this);
            add(createPreviewSection(titlePreview));
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

            addLabeledField(
                section,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SONG_TITLE),
                titleField,
                BaseDialog.LabelPosition.TOP
            );

            addLargeSeparator(section);
            section.add(createTakePanel());

            addSectionSeparator(section);
            var separator = new JSeparator();
            separator.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(separator);
            addSectionSeparator(section);

            section.add(createTitleFontRow());
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private void updateTitlePreview() {
            titlePreview.setPreviewTitle(
                Song.numberedTitle(numberField.getText(), titleField.getText())
            );
        }

        private JPanel createTitleFontRow() {
            var mainFrame = getMainFrame();

            var row = new JPanel(new FlowLayout(
                FlowLayout.LEFT,
                FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP),
                0
            ));
            row.setBorder(BorderFactory.createEmptyBorder());
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            addLabeledField(row, "Font:", titleFontField, BaseDialog.LabelPosition.LEFT);

            var buttons = new JPanel();
            buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
            buttons.add(new JButton(new FontTab.ChooseFontAction(
                mainFrame,
                fontTab.titleFontLabel,
                fontTab.titleFontPreview
            )));
            addLargeSeparator(buttons);
            buttons.add(new JButton(new FontTab.ResetFontAction(
                mainFrame,
                FontKey.TITLE,
                fontTab.titleFontLabel,
                fontTab.titleFontPreview
            )));
            row.add(buttons);

            UIUtils.setFlexibleWidth(row);
            return row;
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
            titlePreview.setSong(song);
            titlePreview.setFont(
                requireScoreView().getDocumentFonts().getFont(FontKey.TITLE)
            );
            numberField.setText(song.getNumber());
            titleField.setText(song.getTitle());
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
        private final MyJTextField placeField = new MyJTextField(27);
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
        private final NumericTextField yearField =
            new NumericTextField(4, YEAR_MIN, YEAR_MAX, true, MAX_YEAR_CHARS);

        // Attribution panel
        private final MyJTextField composerField = new MyJTextField(27);
        private final MyJTextArea lyricistField = new MyJTextArea(LYRICIST_ROWS, LYRICIST_COLUMNS);
        private final JComboBox<Song.LyricsSource> sourceCombo =
            new JComboBox<>(Song.LyricsSource.values());
        private final JCheckBox unofficialTranslationCheck = new JCheckBox(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_UNOFFICIAL_TRANSLATION)
        );
        private final JCheckBox arrangementCheck = new JCheckBox(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_ARRANGEMENT)
        );
        private final AttributionPaneWidget attributionPreview = new AttributionPaneWidget();

        // Non-shared font-name displays for the font rows. Mirror the shared font
        // previews owned by FontTab so the rows show the current font without
        // duplicating FontTab's data wiring.
        private final JTextField wordsMusicFontField = new JTextField(31);
        private final JTextField datePlaceFontField = new JTextField(31);

        // Set while the year focus listener programmatically resets the month/day
        // combos, so their action listeners skip the work that reset triggers.
        private boolean adjustingDateFields;

        private AttributionTab() {
            monthCombo.setEditable(false);

            var days = new String[DAYS_IN_MONTH_MAX + 1];
            days[0] = "";

            for (var i = 1; i <= DAYS_IN_MONTH_MAX; i++) {
                days[i] = Integer.toString(i);
            }

            dayCombo = new JComboBox<>(days);
            dayCombo.setEditable(false);

            sourceCombo.setEditable(false);
            composerField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
            lyricistField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
            lyricistField.setLineWrap(true);
            lyricistField.setWrapStyleWord(true);
            attributionPreview.setOpaque(true);
            attributionPreview.setBackground(
                FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND)
            );

            wordsMusicFontField.setEditable(false);
            wordsMusicFontField.setFocusable(false);
            datePlaceFontField.setEditable(false);
            datePlaceFontField.setFocusable(false);

            // The attribution and sub-attribution fonts are edited via the shared
            // font previews (this tab's font rows and the Fonts tab both target
            // them); mirror their descriptions onto the read-only fields and
            // refresh the live preview as the fonts change, before commit.
            fontTab.attributionFontPreview.addPropertyChangeListener("font", e -> {
                wordsMusicFontField.setText(
                    MyFontUtils.getFullFontDescription(fontTab.attributionFontPreview.getFont())
                );
                refreshPreview();
            });
            fontTab.subAttributionFontPreview.addPropertyChangeListener("font", e -> {
                datePlaceFontField.setText(
                    MyFontUtils.getFullFontDescription(fontTab.subAttributionFontPreview.getFont())
                );
                refreshPreview();
            });

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

            // Source label + dropdown, left-aligned below the lyricist field.
            var sourceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, horizontalGap, 0));
            sourceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            sourceRow.add(sourceLabel);
            sourceRow.add(sourceCombo);
            section.add(sourceRow);

            lyricistField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    refreshPreview();
                }
            });

            sourceCombo.addActionListener(e -> refreshPreview());

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

            addLargeSeparator(section);
            var separator = new JSeparator();
            separator.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(separator);
            addLargeSeparator(section);

            var yearLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_YEAR));
            yearLabel.setLabelFor(yearField);
            var placeLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_PLACE));
            placeLabel.setLabelFor(placeField);

            // Line up the year field (first in the date row) and the place field
            // in one column.
            alignLabelWidths(yearLabel, placeLabel);

            var datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            datePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(datePanel);

            datePanel.add(leftLabeledRow(yearLabel, yearField));

            addLabeledField(
                datePanel,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_MONTH),
                monthCombo,
                BaseDialog.LabelPosition.LEFT
            );

            addLabeledField(
                datePanel,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_DAY),
                dayCombo,
                BaseDialog.LabelPosition.LEFT
            );

            addSeparator(section);

            section.add(leftLabeledRow(placeLabel, placeField));

            composerField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    if (lyricistField.getText().isEmpty()) {
                        lyricistField.setText(composerField.getText());
                    }

                    refreshPreview();
                }
            });

            unofficialTranslationCheck.addActionListener(e -> refreshPreview());
            arrangementCheck.addActionListener(e -> refreshPreview());

            var previewOnFocusLost = new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    refreshPreview();
                }
            };
            placeField.addFocusListener(previewOnFocusLost);
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

                    updateDateFieldStates(yearValid);

                    if (yearValid) {
                        refreshPreview();
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
                updateDateFieldStates(yearValid);

                if (yearValid) {
                    refreshPreview();
                }
            });
            dayCombo.addActionListener(e -> {
                if (adjustingDateFields) {
                    return;
                }

                if (yearField.hasValidValue()) {
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

            section.add(createFontRow(
                wordsMusicLabel,
                wordsMusicFontField,
                FontKey.ATTRIBUTION,
                fontTab.attributionFontLabel,
                fontTab.attributionFontPreview
            ));

            addSeparator(section);

            section.add(createFontRow(
                datePlaceLabel,
                datePlaceFontField,
                FontKey.SUB_ATTRIBUTION,
                fontTab.subAttributionFontLabel,
                fontTab.subAttributionFontPreview
            ));

            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createFontRow(
            JLabel rowLabel,
            JTextField fontField,
            FontKey fontKey,
            JLabel fontLabel,
            JComponent preview
        ) {
            rowLabel.setLabelFor(fontField);
            var row = leftLabeledRow(rowLabel, fontField);

            var mainFrame = getMainFrame();
            var buttons = new JPanel();
            buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
            buttons.add(new JButton(new FontTab.ChooseFontAction(mainFrame, fontLabel, preview)));
            addLargeSeparator(buttons);
            buttons.add(new JButton(new FontTab.ResetFontAction(mainFrame, fontKey, fontLabel, preview)));
            row.add(buttons);

            UIUtils.setFlexibleWidth(row);
            return row;
        }

        /**
         * A left-aligned {@code label + field} row, matching the structure that
         * {@link #addLabeledField} produces for {@link LabelPosition#LEFT}, so a
         * caller can hand in a pre-sized label to column-align the field.
         */
        private static JPanel leftLabeledRow(JLabel label, JComponent field) {
            var row = new JPanel(new FlowLayout(
                FlowLayout.LEFT,
                FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP),
                0
            ));
            row.setBorder(BorderFactory.createEmptyBorder());
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(label);
            row.add(field);
            return row;
        }

        /**
         * Forces every label to the widest label's preferred width so the fields
         * that follow them line up in a column. Each label keeps its own height;
         * only the width is unified.
         */
        private static void alignLabelWidths(JLabel... labels) {
            var width = 0;

            for (var label : labels) {
                width = Math.max(width, label.getPreferredSize().width);
            }

            for (var label : labels) {
                var fixed = new Dimension(width, label.getPreferredSize().height);
                label.setPreferredSize(fixed);
                label.setMinimumSize(fixed);
            }
        }

        private void refreshPreview() {
            // Read the in-progress fonts from FontTab's shared previews (not the
            // committed document fonts) so the preview reflects font edits made
            // in this tab's font rows before the dialog is committed.
            attributionPreview.setPreviewState(
                fontTab.attributionFontPreview.getFont(),
                fontTab.subAttributionFontPreview.getFont(),
                buildPreviewLines()
            );

            // The preview's height changes with both the line count and the
            // attribution/sub-attribution font size. The dialog is packed to a
            // fixed height at show time, so a taller preview would be starved by
            // the tab's GridBagLayout. Re-pack so the window fits the new height.
            repackToContent();
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
                unofficialTranslation
            );
            var showTranslation = !unofficialTranslation && !song.getTranslatedLyrics().isEmpty();
            return AttributionFormatter.lines(metadata, showTranslation);
        }

        String getPlaceText() {
            return placeField.getText();
        }

        String getYearText() {
            return yearField.getText();
        }

        int getMonth() {
            return monthCombo.getSelectedIndex();
        }

        int getDay() {
            return dayCombo.getSelectedIndex();
        }

        private void updateDateFieldStates(boolean yearValid) {
            monthCombo.setEnabled(yearValid);
            dayCombo.setEnabled(yearValid && monthCombo.getSelectedIndex() != 0);
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

        @Override
        protected boolean getData() {
            var song = getSong();
            placeField.setText(song.getPlace());
            monthCombo.setSelectedIndex(song.getMonth());
            dayCombo.setSelectedIndex(song.getDay());
            yearField.setText(song.getYear());
            updateDateFieldStates(yearField.hasValidValue());
            composerField.setText(song.getComposer());
            lyricistField.setText(song.getLyricist());
            sourceCombo.setSelectedItem(song.getLyricsSource());
            arrangementCheck.setSelected(song.isArrangement());
            unofficialTranslationCheck.setSelected(song.isUnofficialTranslation());
            refreshPreview();
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

            var widthInches = validateLineWidth();
            var lineWidthPx = (int) Math.round(widthInches * GraphicUtils.getDpi());
            requireScoreView().updatePageLayout(lineWidthPx);
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
            var lineWidthInches = ScaleContext.ssToPx(
                getSong().getLineWidthSs()
            ) / GraphicUtils.getDpi();
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

        private final JLabel titleFontLabel = new JLabel();
        private final JComponent titleFontPreview = new JLabel(
            "Āmār Prāner Bijoye Mā"
        );

        private final JLabel lyricsFontLabel = new JLabel();
        private final JLabel lyricsFontPreview = new JLabel(
            """
                <html>I shall bind myself at Your Feet.<br>
                With this hope I have come to You<br>
                &nbsp;&nbsp;&nbsp;With tear-filled eyes.<br>
                I shall worship You within the tumult<br>
                &nbsp;&nbsp;&nbsp;Of this life.<br>
                I shall satisfy You on the strength<br>
                &nbsp;&nbsp;&nbsp;Of my surrender.</html>
                """
        );

        private final JLabel attributionFontLabel = new JLabel();
        private final MyJTextArea attributionFontPreview = new MyJTextArea(
            "Words and Music by Sri Chinmoy"
        );

        private final JLabel subAttributionFontLabel = new JLabel();
        private final MyJTextArea subAttributionFontPreview = new MyJTextArea(
            """
                September 1972
                New York, USA"""
        );

        private final JLabel annotationFontLabel = new JLabel();
        private final JComponent annotationFontPreview = new JLabel(
            "D.C. al fine (a tempo)"
        );

        private FontTab() {
            super(FlatLafKey.DIALOG_SONG_SETTINGS_FONT_PADDING);

            for (var preview : new JComponent[]{
                titleFontPreview,
                lyricsFontPreview,
                attributionFontPreview,
                subAttributionFontPreview,
                annotationFontPreview,
            }) {
                preview.setBackground(UIManager.getColor("TextField.background"));
                preview.setOpaque(true);

                if (preview instanceof JTextArea text) {
                    text.setEditable(false);
                }
            }

            build();
        }

        @Override
        protected void initContents() {
            var mainFrame = SongSettingsDialog.this.getMainFrame();
            var tabbedPane = createTabbedPane();
            tabbedPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT_LYRICS),
                createFontSection(
                    mainFrame,
                    "Lyrics",
                    lyricsFontLabel,
                    lyricsFontPreview,
                    false
                )
            );
            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT_ANNOTATION),
                createFontSection(
                    mainFrame,
                    "Annotation",
                    annotationFontLabel,
                    annotationFontPreview,
                    false
                )
            );
            add(tabbedPane);
            addSectionSeparator(this);

            constraints.fill = GridBagConstraints.NONE;
            add(new JButton(new ResetFontsAction(mainFrame)));
        }

        private static JPanel createFontSection(
            MainFrame mainFrame,
            String title,
            JLabel fontLabel,
            JComponent preview,
            boolean isLarge
        ) {
            var container = new JPanel();
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
            var outerBorder = BorderFactory.createMatteBorder(
                0,
                1,
                1,
                1,
                UIManager.getColor("Component.borderColor")
            );
            var innerBorder = UIUtils.spacingBorder(FlatLafKey.DIALOG_SONG_SETTINGS_FONT_CONTAINER_PADDING);
            container.setBorder(
                BorderFactory.createCompoundBorder(outerBorder, innerBorder)
            );

            var contents = new JPanel(new GridBagLayout());
            contents.setAlignmentX(Component.LEFT_ALIGNMENT);
            contents.setAlignmentY(Component.TOP_ALIGNMENT);
            var tabsMarginTop = FlatLafProps.getInt(FlatLafKey.DIALOG_TABS_MARGIN_TOP);
            contents.setBorder(BorderFactory.createEmptyBorder(tabsMarginTop, 0, 0, 0));

            // Font name label
            var gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            gbc.weighty = 0.0;

            // Add a border around the font name label with inner padding
            var labelOuterBorder = BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor")
            );
            var labelInnerBorder = UIUtils.spacingBorder(FlatLafKey.DIALOG_SONG_SETTINGS_FONT_LABEL_PADDING);
            fontLabel.setBorder(
                BorderFactory.createCompoundBorder(
                    labelOuterBorder,
                    labelInnerBorder
                )
            );

            contents.add(fontLabel, gbc);

            // Preview
            gbc.gridwidth = 2;
            gbc.insets = new Insets(FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP), 0, 0, 0);

            // Create padding around the preview by giving it a border and wrapping
            // it in a panel
            contents.add(
                UIUtils.padComponent(
                    preview,
                    FlatLafProps.getInsets(
                        isLarge
                            ? FlatLafKey.DIALOG_SONG_SETTINGS_FONT_PREVIEW_LARGE_PADDING
                            : FlatLafKey.DIALOG_SONG_SETTINGS_FONT_PREVIEW_SMALL_PADDING
                    )
                ),
                gbc
            );

            // Choose button
            gbc.gridx = GridBagConstraints.RELATIVE;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.EAST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0.0;
            gbc.insets = new Insets(0, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP), 0, 0);
            contents.add(
                new JButton(new ChooseFontAction(mainFrame, fontLabel, preview)),
                gbc
            );

            // Add glue at the bottom to push the contents to the top
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(0, 0, 0, 0);
            contents.add(Box.createGlue(), gbc);

            container.add(contents);
            UIUtils.setFlexibleWidth(container);
            return container;
        }

        private static final class ChooseFontAction extends UIAction {

            private final JLabel fontDescription;
            private final JComponent preview;

            private ChooseFontAction(
                MainFrame mainFrame,
                JLabel fontDescription,
                JComponent preview
            ) {
                super(
                    mainFrame,
                    Strings.get(Strings.DIALOG_SONG_SETTINGS_CHOOSE),
                    "choose-font"
                );
                this.fontDescription = fontDescription;
                this.preview = preview;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                var selectedFont = FontDialog.showDialog(preview);
                fontDescription.setText(
                    MyFontUtils.getFullFontDescription(selectedFont)
                );
                preview.setFont(selectedFont);
                preview.revalidate();
                preview.repaint();
            }
        }

        private static final class ResetFontAction extends UIAction {

            private final FontKey fontKey;
            private final JLabel fontDescription;
            private final JComponent preview;

            private ResetFontAction(
                MainFrame mainFrame,
                FontKey fontKey,
                JLabel fontDescription,
                JComponent preview
            ) {
                super(
                    mainFrame,
                    Strings.get(Strings.DIALOG_SONG_SETTINGS_RESET),
                    "reset-font"
                );
                this.fontKey = fontKey;
                this.fontDescription = fontDescription;
                this.preview = preview;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                applyFont(
                    DocumentFonts.defaultsFromPrefs().getFont(fontKey),
                    fontDescription,
                    preview
                );
                preview.revalidate();
                preview.repaint();
            }
        }

        private final class ResetFontsAction extends UIAction {

            private ResetFontsAction(MainFrame mainFrame) {
                super(
                    mainFrame,
                    Strings.get(Strings.DIALOG_SONG_SETTINGS_RESET_TO_DEFAULTS),
                    "reset-fonts"
                );
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                applyDefaultFonts();
                revalidate();
                repaint();
            }
        }

        private void applyDefaultFonts() {
            var defaults = DocumentFonts.defaultsFromPrefs();
            applyFont(defaults.getFont(FontKey.TITLE),           titleFontLabel,           titleFontPreview);
            applyFont(defaults.getFont(FontKey.LYRICS),          lyricsFontLabel,          lyricsFontPreview);
            applyFont(defaults.getFont(FontKey.ATTRIBUTION),     attributionFontLabel,     attributionFontPreview);
            applyFont(defaults.getFont(FontKey.SUB_ATTRIBUTION), subAttributionFontLabel,  subAttributionFontPreview);
            applyFont(defaults.getFont(FontKey.ANNOTATION),      annotationFontLabel,      annotationFontPreview);
        }

        private static void applyFont(Font font, JLabel label, JComponent preview) {
            label.setText(MyFontUtils.getFullFontDescription(font));
            preview.setFont(font);
        }

        @Override
        protected boolean getData() {
            var fonts = requireScoreView().getDocumentFonts();
            applyFont(fonts.getFont(FontKey.TITLE),           titleFontLabel,           titleFontPreview);
            applyFont(fonts.getFont(FontKey.LYRICS),          lyricsFontLabel,          lyricsFontPreview);
            applyFont(fonts.getFont(FontKey.ATTRIBUTION),     attributionFontLabel,     attributionFontPreview);
            applyFont(fonts.getFont(FontKey.SUB_ATTRIBUTION), subAttributionFontLabel,  subAttributionFontPreview);
            applyFont(fonts.getFont(FontKey.ANNOTATION),      annotationFontLabel,      annotationFontPreview);
            return true;
        }

        @Override
        protected void setData() {
            // Bangla and footnote fonts are document-level but this dialog only
            // exposes the five primary fonts; preserve them by copying the current state.
            // TODO: add bangla and footnote font rows here and to ResetFontsAction.
            var newFonts = new DocumentFonts(requireScoreView().getDocumentFonts());
            newFonts.setFont(FontKey.TITLE,           titleFontPreview.getFont());
            newFonts.setFont(FontKey.LYRICS,          lyricsFontPreview.getFont());
            newFonts.setFont(FontKey.ATTRIBUTION,     attributionFontPreview.getFont());
            newFonts.setFont(FontKey.SUB_ATTRIBUTION, subAttributionFontPreview.getFont());
            newFonts.setFont(FontKey.ANNOTATION,      annotationFontPreview.getFont());
            requireScoreView().setFonts(newFonts);
        }
    }

    public static class KeyCellRenderer implements ListCellRenderer<KeySelection> {

        private static final float FONT_SIZE_PT = 120f;
        private static final int MAX_ACCIDENTAL_COUNT = 7;

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
            var selections = new ArrayList<KeySelection>(1 + 2 * MAX_ACCIDENTAL_COUNT);
            var glyphs = new HashMap<KeySelection, String>();

            var noAccidentals = new KeySelection(KeyType.FLATS, 0);
            selections.add(noAccidentals);
            glyphs.put(noAccidentals, "\uF377");

            var flatGlyphs = new String[]{
                "\uF37F", "\uF380", "\uF381", "\uF382", "\uF383", "\uF384", "\uF385"
            };

            for (var i = 0; i < MAX_ACCIDENTAL_COUNT; i++) {
                var sel = new KeySelection(KeyType.FLATS, i + 1);
                selections.add(sel);
                glyphs.put(sel, flatGlyphs[i]);
            }

            var sharpGlyphs = new String[]{
                "\uF378", "\uF379", "\uF37A", "\uF37B", "\uF37C", "\uF37D", "\uF37E"
            };

            for (var i = 0; i < MAX_ACCIDENTAL_COUNT; i++) {
                var sel = new KeySelection(KeyType.SHARPS, i + 1);
                selections.add(sel);
                glyphs.put(sel, sharpGlyphs[i]);
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
                        throw RuntimeError.exit(
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

                var w = GLYPH_BOX_WIDTH_PX + LABEL_GAP_PX + LABEL_WIDTH_PX;
                var h = GLYPH_BOX_HEIGHT_PX + 2 * CELL_PADDING_Y_PX;
                CELL_SIZE = new Dimension(w, h);
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
