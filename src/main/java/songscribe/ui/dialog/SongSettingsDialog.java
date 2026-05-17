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
import java.util.Objects;


import com.formdev.flatlaf.FlatClientProperties;

import songscribe.Strings;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.music.Song;
import songscribe.music.Duration;
import songscribe.music.KeyType;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.ui.KeySignatureDisplay;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.BaseLabel;
import songscribe.ui.component.InputUtils;
import songscribe.ui.component.MyJTextArea;
import songscribe.ui.component.MyJTextField;
import songscribe.ui.component.NonEmptyGuard;
import songscribe.ui.component.NumericTextField;
import songscribe.ui.layout.PageModel;
import songscribe.ui.layout.ScaleContext;
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


    public SongSettingsDialog() {
        super(Strings.get(Strings.DIALOG_SONG_SETTINGS_TITLE), true, DialogCategory.EXCLUSIVE);

        var tabbedPane = createTabbedPane();
        addTab(
            tabbedPane,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_TEXT),
            new TextTab()
        );
        addTab(
            tabbedPane,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_MUSIC),
            new MusicTab()
        );
        addTab(
            tabbedPane,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_FONTS),
            new FontTab()
        );

        contentPanel.add(BorderLayout.CENTER, tabbedPane);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    private final class TextTab extends BaseDialog.Tab {

        // Title of song panel
        private final NumericTextField numberField = new NumericTextField(3);
        private final MyJTextArea titleField = new MyJTextArea(3, 47);
        private final SpinnerModel takeFirstWordsSpinnerModel =
            new SpinnerNumberModel(4, 1, 10, 1);

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
        private final NumericTextField yearField = new NumericTextField(5);

        // Attribution panel
        private final MyJTextArea attributionArea = new MyJTextArea(4, 27);

        private final NonEmptyGuard titleGuard;
        private final NonEmptyGuard attributionGuard;

        private TextTab() {
            monthCombo.setEditable(false);

            var days = new String[32];
            days[0] = "";

            for (var i = 1; i <= 31; i++) {
                days[i] = Integer.toString(i);
            }

            dayCombo = new JComboBox<>(days);
            dayCombo.setEditable(false);

            titleGuard = new NonEmptyGuard(
                titleField,
                contentPanel,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE,
                Strings.DOCUMENT_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_USE_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_CONTINUE_EDITING
            );

            attributionGuard = new NonEmptyGuard(
                attributionArea,
                contentPanel,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_ATTRIBUTION,
                Strings.SONG_DEFAULT_ATTRIBUTION,
                Strings.DIALOG_SONG_SETTINGS_USE_DEFAULT,
                Strings.DIALOG_SONG_SETTINGS_CONTINUE_EDITING
            );

            titleGuard.addExemptComponent(okButton);
            titleGuard.addExemptComponent(cancelButton);
            attributionGuard.addExemptComponent(okButton);
            attributionGuard.addExemptComponent(cancelButton);

            build();
        }

        @Override
        protected void initContents() {
            add(createTitleSection());
            addSeparator();
            add(createPlaceAndDateSection());
            addSeparator();
            add(createInfoSection());
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

            section.addSeparator();

            var scrollPane = new JScrollPane(titleField);
            scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
            scrollPane.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            );
            scrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            );
            scrollPane.setMaximumSize(scrollPane.getPreferredSize());

            addLabeledField(
                section,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SONG_TITLE),
                scrollPane,
                BaseDialog.LabelPosition.TOP
            );

            section.addSeparator();

            section.add(createTakePanel());
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createTakePanel() {
            var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_GAP), 0));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            var action = new TakeFirstLyricsWordAction();
            var takeButton = new JButton(action);
            action.setEnabled(
                !getSong().getLyricsText().isEmpty()
            );
            panel.add(takeButton);

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

        private JPanel createPlaceAndDateSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_PLACE_AND_DATE)
            );

            addLabeledField(
                section,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_PLACE),
                placeField,
                BaseDialog.LabelPosition.LEFT
            );

            section.addSeparator();

            var datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            datePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(datePanel);

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

            addLabeledField(
                datePanel,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_YEAR),
                yearField,
                BaseDialog.LabelPosition.LEFT
            );

            // Don't let the section grow vertically
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        private JPanel createInfoSection() {
            var section = new BaseDialog.TitledSection(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_ATTRIBUTION),
                BoxLayout.X_AXIS
            );

            var scrollPane = new JScrollPane(attributionArea);
            scrollPane.setAlignmentY(Component.CENTER_ALIGNMENT);
            scrollPane.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            );
            scrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            );
            scrollPane.setMaximumSize(scrollPane.getPreferredSize());
            section.add(scrollPane);

            section.add(Box.createHorizontalStrut(FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP)));

            var appendButtonPanel = new JPanel();
            appendButtonPanel.setLayout(
                new BoxLayout(appendButtonPanel, BoxLayout.Y_AXIS)
            );
            appendButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(appendButtonPanel);

            var dateString = getDateString();
            var button = new JButton(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_ADD_DATE)
            );
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(new AddDateAndPlaceAction(false));
            appendButtonPanel.add(button);

            appendButtonPanel.add(Box.createVerticalStrut(FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_SONG_SETTINGS_LYRICS_BUTTON_GAP)));

            button = new JButton(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_ADD_DATE_PLACE)
            );
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(new AddDateAndPlaceAction(true));
            appendButtonPanel.add(button);

            // Size the panel to its preferred size
            appendButtonPanel.setMaximumSize(
                appendButtonPanel.getPreferredSize()
            );

            // Don't let the section grow vertically
            UIUtils.setFlexibleWidth(section);
            return section;
        }

        @Override
        protected boolean getData() {
            var song = getSong();
            numberField.setText(song.getNumber());
            titleField.setText(song.getTitle());
            placeField.setText(song.getPlace());
            monthCombo.setSelectedIndex(song.getMonth());
            dayCombo.setSelectedIndex(song.getDay());
            yearField.setText(song.getYear());
            attributionArea.setText(song.getAttribution());
            return true;
        }

        @Override
        protected boolean isValidData() {
            return titleGuard.validate() && attributionGuard.validate();
        }

        @Override
        protected void setData() {
            // Validate number field
            var number = numberField.getText();

            try {
                if (!number.isEmpty()) {
                    Integer.parseInt(number);
                }
            } catch (NumberFormatException e) {
                OptionDialogs.showErrorMessage(
                    contentPanel,
                    Strings.ALERT_TITLE_SONG_SETTINGS,
                    Strings.ERROR_SONG_NUMBER
                );
                number = null;
            }

            // Validate year field
            var year = yearField.getText();

            try {
                if (!year.isEmpty()) {
                    Integer.parseInt(year);
                }
            } catch (NumberFormatException e) {
                OptionDialogs.showErrorMessage(
                    contentPanel,
                    Strings.ALERT_TITLE_SONG_SETTINGS,
                    Strings.ERROR_SONG_YEAR
                );
                year = null;
            }

            var song = getSong();

            if (titleField.getText().equals(song.getTitle())
                    && placeField.getText().equals(song.getPlace())
                    && Objects.equals(year, song.getYear())
                    && Objects.equals(number, song.getNumber())
                    && attributionArea.getText().equals(song.getAttribution())
                    && monthCombo.getSelectedIndex() == song.getMonth()
                    && dayCombo.getSelectedIndex() == song.getDay()) {
                return;
            }

            song.postWithModification(new MetadataDidChangeNotification(
                titleField.getText(),
                placeField.getText(),
                year,
                number,
                attributionArea.getText(),
                monthCombo.getSelectedIndex(),
                dayCombo.getSelectedIndex(),
                null
            ));
        }

        private String getDateString() {
            var year = yearField.getText();

            // There at least has to be a year
            if (year.isEmpty()) {
                return "";
            }

            var sb = new StringBuilder(30);

            if (monthCombo.getSelectedIndex() > 0) {
                sb.append(monthCombo.getSelectedItem());

                if (dayCombo.getSelectedIndex() > 0) {
                    sb.append(' ');
                    sb.append(dayCombo.getSelectedItem());
                }

                sb.append(", ");
            }

            // We know that the field will only contain numbers
            sb.append(Integer.parseInt(yearField.getText()));
            return sb.toString();
        }

        private final class TakeFirstLyricsWordAction extends UIAction {

            private TakeFirstLyricsWordAction() {
                super(
                    Strings.get(Strings.DIALOG_SONG_SETTINGS_TAKE),
                    "take-lyrics"
                );
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                var lyrics = getSong().getLyricsText();
                var words = new StringBuilder(50);
                var wordCount = 0;
                var firstLetter = false;
                var lastHyphen = false;

                goThruString:
                for (var i = 0; i < lyrics.length(); i++) {
                    switch (lyrics.charAt(i)) {
                        case ' ', '\n' -> {
                            wordCount++;

                            if (
                                wordCount >=
                                    ((Number) takeFirstWordsSpinnerModel.getValue()).intValue()
                            ) {
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

                if (!Character.isLetter(words.charAt(words.length() - 1))) {
                    words.deleteCharAt(words.length() - 1);
                }

                titleField.setText(words.toString());
            }
        }

        private final class AddDateAndPlaceAction extends AbstractAction {

            private final boolean includePlace;

            private AddDateAndPlaceAction(boolean includePlace) {
                this.includePlace = includePlace;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                var attribution = attributionArea.getText();
                var sb = new StringBuilder(100);

                var date = getDateString();

                if (!date.isEmpty()) {
                    if (attribution.charAt(attribution.length() - 1) != '\n') {
                        sb.append('\n');
                    }

                    sb.append(date);
                } else {
                    OptionDialogs.showErrorMessage(
                        getMainFrame(),
                        Strings.ALERT_TITLE_SONG_SETTINGS,
                        Strings.ERROR_SONG_YEAR_REQUIRED
                    );
                    return;
                }

                if (includePlace) {
                    if (!placeField.getText().isEmpty()) {
                        if (attribution.charAt(attribution.length() - 1) != '\n') {
                            sb.append('\n');
                        }

                        sb.append(placeField.getText());
                    } else {
                        OptionDialogs.showErrorMessage(
                            getMainFrame(),
                            Strings.ALERT_TITLE_SONG_SETTINGS,
                            Strings.ERROR_SONG_PLACE_REQUIRED
                        );
                        return;
                    }
                }

                attributionArea.append(sb.toString());
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
            lineWidthField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    var isMetric = Prefs.getInstance().getBoolean(PrefsKey.METRIC);
                    var text = lineWidthField.getText();

                    double value;

                    try {
                        value = Double.parseDouble(text);
                    } catch (NumberFormatException ex) {
                        showLineWidthError(Strings.ERROR_LINE_WIDTH_INVALID, isMetric);
                        revertLineWidthField();
                        return;
                    }

                    var widthInches = isMetric ? value / GraphicUtils.CM_PER_INCH : value;

                    if (widthInches < PageModel.MIN_LINE_WIDTH_INCHES || widthInches > PageModel.MAX_LINE_WIDTH_INCHES) {
                        showLineWidthError(Strings.ERROR_LINE_WIDTH_RANGE, isMetric);
                        revertLineWidthField();
                    }
                }
            });

            build();
        }

        @Override
        protected void initContents() {
            add(createTempoSection(), constraints);
            addSeparator();
            add(createKeySignatureSection());
            addSeparator();
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
                new FlowLayout(FlowLayout.LEFT, FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_GAP), 0)
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
            var tempo = song.getEffectiveTempo();
            var tempoType = tempoSection.getTempoType();
            var visibleTempo = tempoSection.getVisibleTempo();
            var tempoDescription = tempoSection.getTempoDescription();
            var showTempo = !tempoSection.isShowOnlyDescription();
            var typeAndCount = getKeyTypeAndCountFromCombo();

            var tempoChanged = tempoType != tempo.getTempoType()
                || visibleTempo != tempo.getVisibleTempo()
                || !tempoDescription.equals(tempo.getTempoDescription())
                || showTempo != tempo.shouldShowTempo();

            var keyChanged = typeAndCount.keyType() != song.getDefaultKeyType()
                || typeAndCount.count() != song.getDefaultKeyAccidentalCount();

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
                            typeAndCount.keyType(),
                            typeAndCount.count()
                        ));
                    }
                });
            }

            var widthInches = validateLineWidth();
            var lineWidthPx = (int) Math.round(widthInches * GraphicUtils.getDpi());
            var score = getScore();

            if (score != null) {
                score.updatePageLayout(lineWidthPx);
            }
        }

        @Override
        protected boolean isValidData() {
            return validateLineWidth() >= 0;
        }

        private void setKeyComboFromSong(Song song) {
            var accidentalCount = song.getDefaultKeyAccidentalCount();
            // (FLATS, 0) is the canonical no-accidentals entry.
            var keyType = accidentalCount == 0
                ? KeyType.FLATS
                : song.getDefaultKeyType();
            keyCombo.setSelectedItem(new KeySelection(keyType, accidentalCount));
        }

        private KeySelection getKeyTypeAndCountFromCombo() {
            var selected = (KeySelection) keyCombo.getSelectedItem();

            if (selected == null) {
                throw RuntimeError.exit("Key combo has no selection");
            }

            return selected;
        }

        private void revertLineWidthField() {
            var isMetric = Prefs.getInstance().getBoolean(PrefsKey.METRIC);
            var lineWidthInches = ScaleContext.getInstance().ssToPx(
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
            var isMetric = Prefs.getInstance().getBoolean(PrefsKey.METRIC);

            double value;

            try {
                value = Double.parseDouble(lineWidthField.getText());
            } catch (NumberFormatException e) {
                return -1;
            }

            var widthInches = isMetric ? value / GraphicUtils.CM_PER_INCH : value;

            if (widthInches < PageModel.MIN_LINE_WIDTH_INCHES || widthInches > PageModel.MAX_LINE_WIDTH_INCHES) {
                return -1;
            }

            return widthInches;
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
            """
                Words and music
                by Sri Chinmoy"""
        );

        private final JLabel annotationFontLabel = new JLabel();
        private final JComponent annotationFontPreview = new JLabel(
            "D.C. al fine (a tempo)"
        );

        private FontTab() {
            super(FlatLafKeys.DIALOG_SONG_SETTINGS_FONT_PADDING);

            for (var preview : new JComponent[]{
                titleFontPreview,
                lyricsFontPreview,
                attributionFontPreview,
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
            var tabbedPane = createTabbedPane();
            tabbedPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT_TITLE),
                createFontSection(
                    "Title",
                    titleFontLabel,
                    titleFontPreview,
                    true
                )
            );
            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT_LYRICS),
                createFontSection(
                    "Lyrics",
                    lyricsFontLabel,
                    lyricsFontPreview,
                    false
                )
            );
            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT_ATTRIBUTION),
                createFontSection(
                    "Attribution (tempo, beat change, attribution)",
                    attributionFontLabel,
                    attributionFontPreview,
                    false
                )
            );
            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT_ANNOTATION),
                createFontSection(
                    "Annotation",
                    annotationFontLabel,
                    annotationFontPreview,
                    false
                )
            );
            add(tabbedPane);
            addSeparator();

            constraints.fill = GridBagConstraints.NONE;
            add(new JButton(new ResetFontsAction()));
        }

        private static JPanel createFontSection(
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
            var innerBorder = UIUtils.spacingBorder(FlatLafKeys.DIALOG_SONG_SETTINGS_FONT_CONTAINER_PADDING);
            container.setBorder(
                BorderFactory.createCompoundBorder(outerBorder, innerBorder)
            );

            var contents = new JPanel(new GridBagLayout());
            contents.setAlignmentX(Component.LEFT_ALIGNMENT);
            contents.setAlignmentY(Component.TOP_ALIGNMENT);
            int tabsMarginTop = FlatLafProps.get(FlatLafKeys.DIALOG_TABS_MARGIN_TOP);
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
            var labelInnerBorder = UIUtils.spacingBorder(FlatLafKeys.DIALOG_SONG_SETTINGS_FONT_LABEL_PADDING);
            fontLabel.setBorder(
                BorderFactory.createCompoundBorder(
                    labelOuterBorder,
                    labelInnerBorder
                )
            );

            contents.add(fontLabel, gbc);

            // Preview
            gbc.gridwidth = 2;
            gbc.insets = new Insets(FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP), 0, 0, 0);

            // Create padding around the preview by giving it a border and wrapping
            // it in a panel
            contents.add(
                UIUtils.padComponent(
                    preview,
                    FlatLafProps.get(
                        isLarge
                            ? FlatLafKeys.DIALOG_SONG_SETTINGS_FONT_PREVIEW_LARGE_PADDING
                            : FlatLafKeys.DIALOG_SONG_SETTINGS_FONT_PREVIEW_SMALL_PADDING
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
            gbc.insets = new Insets(0, FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP), 0, 0);
            contents.add(
                new JButton(new ChooseFontAction(fontLabel, preview)),
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
                JLabel fontDescription,
                JComponent preview
            ) {
                super(
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

        private final class ResetFontsAction extends UIAction {

            private ResetFontsAction() {
                super(
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
            applyFont(defaults.getFont(FontKey.TITLE),       titleFontLabel,       titleFontPreview);
            applyFont(defaults.getFont(FontKey.LYRICS),      lyricsFontLabel,      lyricsFontPreview);
            applyFont(defaults.getFont(FontKey.ATTRIBUTION), attributionFontLabel, attributionFontPreview);
            applyFont(defaults.getFont(FontKey.ANNOTATION),  annotationFontLabel,  annotationFontPreview);
        }

        private static void applyFont(Font font, JLabel label, JComponent preview) {
            label.setText(MyFontUtils.getFullFontDescription(font));
            preview.setFont(font);
        }

        @Override
        protected boolean getData() {
            var fonts = requireScore().getDocumentFonts();
            applyFont(fonts.getFont(FontKey.TITLE),       titleFontLabel,       titleFontPreview);
            applyFont(fonts.getFont(FontKey.LYRICS),      lyricsFontLabel,      lyricsFontPreview);
            applyFont(fonts.getFont(FontKey.ATTRIBUTION), attributionFontLabel, attributionFontPreview);
            applyFont(fonts.getFont(FontKey.ANNOTATION),  annotationFontLabel,  annotationFontPreview);
            return true;
        }

        @Override
        protected void setData() {
            // Bangla and footnote fonts are document-level but this dialog only
            // exposes the four primary fonts; preserve them by copying the current state.
            // TODO: add bangla and footnote font rows here and to ResetFontsAction.
            var newFonts = new DocumentFonts(requireScore().getDocumentFonts());
            newFonts.setFont(FontKey.TITLE,       titleFontPreview.getFont());
            newFonts.setFont(FontKey.LYRICS,      lyricsFontPreview.getFont());
            newFonts.setFont(FontKey.ATTRIBUTION, attributionFontPreview.getFont());
            newFonts.setFont(FontKey.ANNOTATION, annotationFontPreview.getFont());
            requireScore().setFonts(newFonts);
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
                LABEL_GAP_PX = FlatLafProps.get(
                    FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP
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
                var g2d = (Graphics2D) g;
                // Use the glyph's visual bounds (not advance width) for centering, since
                // the MusescoreIcon font has a large advance width unrelated to ink size.
                var contentXOffset = Math.max(0, (getWidth() - CELL_SIZE.width) / 2);
                var glyphX = (float) (contentXOffset
                    + (GLYPH_BOX_WIDTH_PX - glyphBounds.getWidth()) / 2
                    - glyphBounds.getX());
                var glyphY = (float) (CELL_PADDING_Y_PX - glyphBounds.getY());
                g2d.drawGlyphVector(glyphVector, glyphX, glyphY);

                var labelX = (float) (contentXOffset + GLYPH_BOX_WIDTH_PX + LABEL_GAP_PX);
                var labelY = (getHeight() + labelLayout.getAscent()
                    - labelLayout.getDescent()) / 2f;
                labelLayout.draw(g2d, labelX, labelY);
            }
        }
    }
}
