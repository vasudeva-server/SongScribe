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

import java.util.List;

import com.formdev.flatlaf.FlatClientProperties;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AttributionFormatter;
import songscribe.dom.AttributionLine;
import songscribe.dom.AttributionPane;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.font.FontKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.MyJTextArea;
import songscribe.ui.component.MyJTextField;
import songscribe.util.GraphicUtils;
import songscribe.util.StringUtils;
import songscribe.util.UIUtils;

/**
 * The {@link SongSettingsDialog} Attribution tab: the words/music credits, their
 * dates and place, the attribution fonts, and a live preview of the attribution
 * block the score renders.
 */
final class SongSettingsAttributionTab extends BaseDialog.Tab {

    private static final int LYRICIST_ROWS = 2;
    private static final int LYRICIST_COLUMNS = 20;
    private static final int PLACE_FIELD_COLUMNS = 27;
    private static final int COMPOSER_FIELD_COLUMNS = 27;

    private final SongSettingsDialog dialog;

    // The title tab owns the title/subtitle/number the preview's metadata record needs.
    private final SongSettingsTitleTab titleTab;

    // Place and date panel
    private final MyJTextField placeField = new MyJTextField(PLACE_FIELD_COLUMNS);
    private final SongSettingsDateInputRow musicDate = new SongSettingsDateInputRow(this::refreshPreview);

    // Attribution panel
    private final MyJTextField composerField = new MyJTextField(COMPOSER_FIELD_COLUMNS);
    private final MyJTextArea lyricistField = new MyJTextArea(LYRICIST_ROWS, LYRICIST_COLUMNS);
    private final JComboBox<Song.LyricsSource> sourceCombo =
        new JComboBox<>(Song.LyricsSource.values());
    private final JCheckBox differentDateCheckbox = new JCheckBox(
        Strings.get(Strings.DIALOG_SONG_SETTINGS_DIFFERENT_DATE)
    );
    private final SongSettingsDateInputRow wordsDate = new SongSettingsDateInputRow(this::refreshPreview);
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

    SongSettingsAttributionTab(SongSettingsDialog dialog, SongSettingsTitleTab titleTab) {
        dialog.super(Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_ATTRIBUTION));
        this.dialog = dialog;
        this.titleTab = titleTab;

        sourceCombo.setEditable(false);
        composerField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
        lyricistField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
        lyricistField.setLineWrap(true);
        lyricistField.setWrapStyleWord(true);
        attributionPreview.setOpaque(true);
        attributionPreview.setBackground(
            FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND)
        );

        var fonts = dialog.requireScoreView().getDocumentFonts();
        attributionFont = fonts.getFont(FontKey.ATTRIBUTION);
        subAttributionFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);

        build();
    }

    @Override
    protected void initContents() {
        add(createWordsSection());
        BaseDialog.addSectionSeparator(this);
        add(createMusicSection());
        BaseDialog.addSectionSeparator(this);
        add(createFontsSection());
        BaseDialog.addSectionSeparator(this);
        add(SongSettingsLayout.createPreviewSection(attributionPreview));
    }

    private static void addHorizontalDivider(JComponent container) {
        BaseDialog.addLargeSeparator(container);
        var separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(separator);
        BaseDialog.addLargeSeparator(container);
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

        BaseDialog.addSeparator(section);

        // Source row: source label + dropdown, different date checkbox.
        var sourceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, horizontalGap, 0));
        sourceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sourceRow.add(sourceLabel);
        sourceRow.add(sourceCombo);
        BaseDialog.addLargeSeparator(sourceRow);
        BaseDialog.addLargeSeparator(sourceRow);
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

        var song = dialog.getSong();

        if (!song.getTranslatedLyrics().isEmpty()) {
            BaseDialog.addSeparator(section);
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

        BaseDialog.addSeparator(section);

        BaseDialog.addLabeledField(section, placeLabel, placeField);

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

        var mainFrame = dialog.getMainFrame();

        section.add(FontSettingRow.create(
            mainFrame,
            wordsMusicLabel,
            attributionFontLabel,
            FontKey.ATTRIBUTION,
            () -> attributionFont,
            this::applyAttributionFont
        ));

        BaseDialog.addSeparator(section);

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
        dialog.repackToContent();
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
        var song = dialog.getSong();
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
        var gatedDate = SongSettingsDialog.gatedWordsDate(
            differentDateCheckbox.isSelected(),
            wordsDate.getYear(),
            wordsDate.getMonth(),
            wordsDate.getDay()
        );
        var metadata = new SongMetadata(
            titleTab.getTitleText(),
            titleTab.getNumberText(),
            getPlaceText(),
            getYearText(),
            getMonth(),
            getDay(),
            composerText,
            lyricistText,
            lyricsSource,
            arrangement,
            unofficialTranslation,
            titleTab.getSubtitleText(),
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
        var song = dialog.getSong();
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
        var fonts = dialog.requireScoreView().getDocumentFonts();
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
            // The preview always renders unzoomed (no ScoreView), so measure at natural scale.
            pane.render(
                g2, 0, 0, getWidth(), attributionFont, subAttributionFont,
                AttributionPane.NATURAL_ZOOM_FACTOR);
        }
    }
}
