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

import java.util.Arrays;
import java.util.EnumMap;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import kotlin.Pair;

import songscribe.Strings;
import songscribe.message.notification.FontDidChangeNotification;
import songscribe.message.notification.KeySignatureDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.message.notification.MetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.music.Composition;
import songscribe.music.KeyType;
import songscribe.music.Tempo;
import songscribe.prefs.Prefs;
import songscribe.ui.Dialogs;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.InputUtils;
import songscribe.ui.component.MyJTextArea;
import songscribe.ui.component.MyJTextField;
import songscribe.ui.component.NumericTextField;
import songscribe.ui.fontchooser.FontDialog;
import songscribe.file.FileUtils;
import songscribe.util.MyFontUtils;
import songscribe.util.UIUtils;

public class CompositionSettingsDialog extends StandardDialog {

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
    private JComboBox<String> dayCombo;
    private final NumericTextField yearField = new NumericTextField(5);

    // Attribution panel
    public final MyJTextArea attributionArea = new MyJTextArea(4, 27);

    // Tempo panel
    public final JComboBox<Tempo.Type> tempoTypeCombo = new JComboBox<>(
        // Only use non-IO values
        Arrays.copyOfRange(
            Tempo.Type.values(),
            0,
            Tempo.Type.QUAVER.ordinal() + 1
        )
    );

    public final SpinnerModel tempoSpinnerModel = new SpinnerNumberModel(
        120,
        40,
        220,
        1
    );

    public final JComboBox<String> tempoDescriptionCombo = new JComboBox<>();
    private final JCheckBox showOnlyDescriptionCheckBox = new JCheckBox(
        Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_SHOW_ONLY_DESCRIPTION)
    );

    // Key signature panel
    public final JComboBox<String> keyCombo = new JComboBox<>(
        new String[]{
            // MusescoreIcon font
            "\uF377", // No flats or sharps
            "\uF37F", // One flat
            "\uF380", // Two flats
            "\uF381", // Three flats
            "\uF382", // Four flats
            "\uF383", // Five flats
            "\uF384", // Six flats
            "\uF385", // Seven flats
            "\uF378", // One sharp
            "\uF379", // Two sharps
            "\uF37A", // Three sharps
            "\uF37B", // Four sharps
            "\uF37C", // Five sharps
            "\uF37D", // Six sharps
            "\uF37E", // Seven sharps
        }
    );

    // Fonts tab
    private final JLabel titleFontLabel = new JLabel();
    public final JComponent titleFontPreview = new JLabel(
        "Āmār Prāner Bijoye Mā"
    );

    private final JLabel lyricsFontLabel = new JLabel();
    public final JLabel lyricsFontPreview = new JLabel(
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
    public final JTextArea attributionFontPreview = new JTextArea(
        """
            Words and music
            by Sri Chinmoy"""
    );

    private final JLabel annotationFontLabel = new JLabel();
    public final JComponent annotationFontPreview = new JLabel(
        "D.C. al fine (a tempo)"
    );

    @SuppressWarnings("NullAway.Init")
    public CompositionSettingsDialog() {
        super(Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_TITLE));
        initFields();

        var tabbedPane = createTabbedPane();
        tabbedPane.addTab(
            Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_TAB_TEXT),
            new TextTab()
        );
        tabbedPane.addTab(
            Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_TAB_MUSIC),
            new MusicTab()
        );

        tabbedPane.addTab(
            Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_TAB_FONTS),
            new FontTab()
        );

        contentPanel.add(BorderLayout.CENTER, tabbedPane);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    private void initFields() {
        monthCombo.setEditable(false);

        var days = new String[32];
        days[0] = "";

        for (var i = 1; i <= 31; i++) {
            days[i] = Integer.toString(i);
        }

        dayCombo = new JComboBox<>(days);
        dayCombo.setEditable(false);

        tempoTypeCombo.setRenderer(new NoteCellRenderer());
        tempoTypeCombo.setEditable(false);

        // Default to a quarter note
        tempoTypeCombo.setSelectedIndex(4);

        // Default to 5 flats
        keyCombo.setRenderer(new KeyCellRenderer());
        keyCombo.setSelectedIndex(5);
        keyCombo.setMaximumRowCount(7);

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
    }

    private final class TextTab extends StandardDialog.Tab {

        @Override
        protected void initContents() {
            add(createTitleSection());
            addSeparator();
            add(createPlaceAndDateSection());
            addSeparator();
            add(createInfoSection());
        }

        private JPanel createTitleSection() {
            var section = new StandardDialog.TitledSection(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_SECTION_TITLE_OF_SONG)
            );
            addLabeledField(
                section,
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_NUMBER),
                numberField,
                StandardDialog.LabelPosition.LEFT
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
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_SONG_TITLE),
                scrollPane,
                StandardDialog.LabelPosition.TOP
            );

            section.addSeparator();

            section.add(createTakePanel());
            UIUtils.setCanGrow(section, true, false);
            UIUtils.setCanShrink(section, true, false);
            return section;
        }

        private JPanel createTakePanel() {
            var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            var action = new TakeFirstLyricsWordAction();
            var takeButton = new JButton(action);
            action.setEnabled(
                !getComposition().getLyrics().isEmpty()
            );
            panel.add(takeButton);

            panel.add(new JLabel(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_THE_FIRST)
            ));

            var spinner = new JSpinner(takeFirstWordsSpinnerModel);
            var editor = (JSpinner.DefaultEditor) spinner.getEditor();
            var textField = editor.getTextField();
            textField.setEditable(false);
            textField.setFocusable(false);

            panel.add(spinner);
            panel.add(new JLabel(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_WORDS_FROM_LYRICS)
            ));

            return panel;
        }

        private JPanel createPlaceAndDateSection() {
            var section = new StandardDialog.TitledSection(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_SECTION_PLACE_AND_DATE)
            );

            addLabeledField(
                section,
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_PLACE),
                placeField,
                StandardDialog.LabelPosition.LEFT
            );

            section.addSeparator();

            var datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            datePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(datePanel);

            addLabeledField(
                datePanel,
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_MONTH),
                monthCombo,
                StandardDialog.LabelPosition.LEFT
            );

            addLabeledField(
                datePanel,
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_DAY),
                dayCombo,
                StandardDialog.LabelPosition.LEFT
            );

            addLabeledField(
                datePanel,
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_YEAR),
                yearField,
                StandardDialog.LabelPosition.LEFT
            );

            // Don't let the section grow vertically
            UIUtils.setCanGrow(section, true, false);
            UIUtils.setCanShrink(section, true, false);
            return section;
        }

        private JPanel createInfoSection() {
            var section = new StandardDialog.TitledSection(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_SECTION_ATTRIBUTION),
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

            section.add(Box.createHorizontalStrut(13));

            var appendButtonPanel = new JPanel();
            appendButtonPanel.setLayout(
                new BoxLayout(appendButtonPanel, BoxLayout.Y_AXIS)
            );
            appendButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(appendButtonPanel);

            var dateString = getDateString();
            var button = new JButton(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_ADD_DATE)
            );
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(new AddDateAndPlaceAction(false));
            appendButtonPanel.add(button);

            appendButtonPanel.add(Box.createVerticalStrut(7));

            button = new JButton(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_ADD_DATE_PLACE)
            );
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(new AddDateAndPlaceAction(true));
            appendButtonPanel.add(button);

            // Size the panel to its preferred size
            appendButtonPanel.setMaximumSize(
                appendButtonPanel.getPreferredSize()
            );

            // Don't let the section grow vertically
            UIUtils.setCanGrow(section, true, false);
            UIUtils.setCanShrink(section, true, false);
            return section;
        }
    }

    private final class MusicTab extends StandardDialog.Tab {

        @Override
        protected void initContents() {
            add(createTempoSection(), constraints);
            addSeparator();
            add(createKeySignatureSection());
        }

        private JPanel createTempoSection() {
            var section = new StandardDialog.TitledSection(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_SECTION_TEMPO)
            );

            var contents = new JPanel(new GridBagLayout());
            contents.setAlignmentX(Component.LEFT_ALIGNMENT);

            // 7px gap between components
            var gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 0, 0, 7);
            gbc.anchor = GridBagConstraints.WEST;
            contents.add(tempoTypeCombo, gbc);

            gbc.gridx += 1;
            contents.add(new JLabel("="), gbc);

            var spinner = new JSpinner(tempoSpinnerModel);
            InputUtils.addNumericFilter(spinner);
            gbc.gridx += 1;
            contents.add(spinner, gbc);

            tempoDescriptionCombo.setEditable(true);
            FileUtils.readComboValuesFromFile(tempoDescriptionCombo, "tempos");
            gbc.gridx += 1;

            // Give a little more space to the left of the combo box
            gbc.insets = new Insets(0, 13, 0, 0);
            contents.add(tempoDescriptionCombo, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.gridwidth = 4;

            // Separate from the row above
            gbc.insets = new Insets(13, 0, 0, 0);
            contents.add(showOnlyDescriptionCheckBox, gbc);

            // Don't let the contents grow horizontally
            contents.setMaximumSize(contents.getPreferredSize());

            // Don't let the section grow vertically
            section.add(contents);
            UIUtils.setCanGrow(section, true, false);
            UIUtils.setCanShrink(section, true, false);
            return section;
        }

        private JPanel createKeySignatureSection() {
            var section = new StandardDialog.TitledSection(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_SECTION_KEY_SIGNATURE)
            );
            keyCombo.setMaximumSize(keyCombo.getPreferredSize());
            keyCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(keyCombo);

            // Don't let the section grow vertically
            UIUtils.setCanGrow(section, true, false);
            UIUtils.setCanShrink(section, true, false);
            return section;
        }
    }

    private final class FontTab extends StandardDialog.Tab {

        private static final int LARGE_PREVIEW_PADDING_X = 10;
        private static final int LARGE_PREVIEW_PADDING_Y = 8;
        private static final int SMALL_PREVIEW_PADDING_X = 13;
        private static final int SMALL_PREVIEW_PADDING_Y = 10;

        private FontTab() {
            super(13);
        }

        @Override
        protected void initContents() {
            var tabbedPane = createTabbedPane();
            tabbedPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_FONT_TITLE),
                createFontSection(
                    "Title",
                    titleFontLabel,
                    titleFontPreview,
                    true
                )
            );
            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_FONT_LYRICS),
                createFontSection(
                    "Lyrics",
                    lyricsFontLabel,
                    lyricsFontPreview,
                    false
                )
            );
            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_FONT_ATTRIBUTION),
                createFontSection(
                    "Attribution (tempo, beat change, attribution)",
                    attributionFontLabel,
                    attributionFontPreview,
                    false
                )
            );
            tabbedPane.addTab(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_FONT_ANNOTATION),
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
            var innerBorder = BorderFactory.createEmptyBorder(10, 20, 20, 20);
            container.setBorder(
                BorderFactory.createCompoundBorder(outerBorder, innerBorder)
            );

            var contents = new JPanel(new GridBagLayout());
            contents.setAlignmentX(Component.LEFT_ALIGNMENT);
            contents.setAlignmentY(Component.TOP_ALIGNMENT);
            contents.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));

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
            var labelInnerBorder = BorderFactory.createEmptyBorder(2, 4, 2, 4);
            fontLabel.setBorder(
                BorderFactory.createCompoundBorder(
                    labelOuterBorder,
                    labelInnerBorder
                )
            );

            contents.add(fontLabel, gbc);

            // Preview
            gbc.gridwidth = 2;
            gbc.insets = new Insets(13, 0, 0, 0);

            // Create padding around the preview by giving it a border and wrapping
            // it in a panel
            contents.add(
                UIUtils.padComponent(
                    preview,
                    isLarge ? LARGE_PREVIEW_PADDING_X : SMALL_PREVIEW_PADDING_X,
                    isLarge ? LARGE_PREVIEW_PADDING_Y : SMALL_PREVIEW_PADDING_Y
                ),
                gbc
            );

            // Choose button
            gbc.gridx = GridBagConstraints.RELATIVE;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.EAST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0.0;
            gbc.insets = new Insets(0, 13, 0, 0);
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
            UIUtils.setCanGrow(container, true, false);
            UIUtils.setCanShrink(container, true, false);
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
                    Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_CHOOSE),
                    "choose-font"
                );
                this.fontDescription = fontDescription;
                this.preview = preview;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                var selectedFont = FontDialog.showDialog(preview, null);

                if (selectedFont.isPresent()) {
                    var font = selectedFont.get();
                    fontDescription.setText(
                        MyFontUtils.getFullFontDescription(font)
                    );
                    preview.setFont(font);
                    preview.revalidate();
                    preview.repaint();
                }
            }
        }

        private final class ResetFontsAction extends UIAction {

            private ResetFontsAction() {
                super(
                    Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_RESET_TO_DEFAULTS),
                    "reset-fonts"
                );
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                var prefs = Prefs.getInstance();
                resetFont(
                    prefs,
                    titleFontLabel,
                    titleFontPreview,
                    "titleFont",
                    "titleFontSize"
                );
                resetFont(
                    prefs,
                    lyricsFontLabel,
                    lyricsFontPreview,
                    "lyricsFont",
                    "lyricsFontSize"
                );
                resetFont(
                    prefs,
                    attributionFontLabel,
                    attributionFontPreview,
                    "attributionFont",
                    "attributionFontSize"
                );
                resetFont(
                    prefs,
                    annotationFontLabel,
                    annotationFontPreview,
                    "annotationFont",
                    "annotationFontSize"
                );
                revalidate();
                repaint();
            }

            private static void resetFont(
                Prefs prefs,
                JLabel fontLabel,
                JComponent preview,
                String fontKey,
                String sizeKey
            ) {
                var font = MyFontUtils.createFont(
                    prefs.getString(fontKey),
                    prefs.getInt(sizeKey)
                );
                fontLabel.setText(MyFontUtils.getFullFontDescription(font));
                preview.setFont(font);
            }
        }
    }

    @Override
    protected boolean getData() {
        var composition = getComposition();

        numberField.setText(composition.getNumber());
        titleField.setText(composition.getTitle());
        placeField.setText(composition.getPlace());
        monthCombo.setSelectedIndex(composition.getMonth());
        dayCombo.setSelectedIndex(composition.getDay());
        yearField.setText(composition.getYear());
        attributionArea.setText(composition.getAttribution());

        tempoTypeCombo.setSelectedItem(composition.getTempo().getTempoType());
        tempoSpinnerModel.setValue(composition.getTempo().getVisibleTempo());
        tempoDescriptionCombo.setSelectedItem(
            composition.getTempo().getTempoDescription()
        );
        showOnlyDescriptionCheckBox.setSelected(
            !composition.getTempo().shouldShowTempo()
        );
        setKeyComboFromComposition(composition);

        var font = composition.getTitleFont();
        titleFontPreview.setFont(font);
        titleFontLabel.setText(MyFontUtils.getFullFontDescription(font));

        font = composition.getLyricsFont();
        lyricsFontPreview.setFont(font);
        lyricsFontLabel.setText(MyFontUtils.getFullFontDescription(font));

        font = composition.getAttributionFont();
        attributionFontPreview.setFont(font);
        attributionFontLabel.setText(MyFontUtils.getFullFontDescription(font));

        font = composition.getAnnotationFont();
        annotationFontPreview.setFont(font);
        annotationFontLabel.setText(MyFontUtils.getFullFontDescription(font));
        return true;
    }

    public void setKeyComboFromComposition(Composition composition) {
        var keyType = composition.getDefaultKeyType();
        var accidentalCount = composition.getDefaultKeyAccidentalCount();

        // If there are no flats or sharps, set the index to 0
        if (accidentalCount == 0) {
            keyCombo.setSelectedIndex(0);
            return;
        }

        // If there are flats, set the index to the number of flats
        if (keyType == KeyType.FLATS) {
            keyCombo.setSelectedIndex(accidentalCount);
            return;
        }

        // If there are sharps, set the index to the number of sharps + 7
        keyCombo.setSelectedIndex(accidentalCount + 7);
    }

    @Override
    protected void setData() {
        // Validate number field
        String number = numberField.getText();

        try {
            if (!number.isEmpty()) {
                Integer.parseInt(number);
            }
        } catch (NumberFormatException e) {
            Dialogs.showErrorMessage(
                contentPanel,
                Strings.get(Strings.DIALOG_TITLE_COMPOSITION_SETTINGS),
                Strings.get(Strings.ERROR_COMPOSITION_NUMBER)
            );
            number = null;
        }

        // Validate year field
        String year = yearField.getText();

        try {
            if (!year.isEmpty()) {
                Integer.parseInt(year);
            }
        } catch (NumberFormatException e) {
            Dialogs.showErrorMessage(
                contentPanel,
                Strings.get(Strings.DIALOG_TITLE_COMPOSITION_SETTINGS),
                Strings.get(Strings.ERROR_COMPOSITION_YEAR)
            );
            year = null;
        }

        MessageCenter.post(new MetadataDidChangeNotification(
            titleField.getText(),
            placeField.getText(),
            year,
            number,
            attributionArea.getText(),
            monthCombo.getSelectedIndex(),
            dayCombo.getSelectedIndex(),
            null
        ));

        MessageCenter.post(new TempoDidChangeNotification(
            (Tempo.Type) tempoTypeCombo.getSelectedItem(),
            (Integer) tempoSpinnerModel.getValue(),
            (String) tempoDescriptionCombo.getSelectedItem(),
            !showOnlyDescriptionCheckBox.isSelected()
        ));

        var typeAndCount = getKeyTypeAndCountFromCombo();

        MessageCenter.post(new KeySignatureDidChangeNotification(
            null,
            typeAndCount.getFirst(),
            typeAndCount.getSecond()
        ));

        MessageCenter.post(new FontDidChangeNotification(
            titleFontPreview.getFont(),
            lyricsFontPreview.getFont(),
            attributionFontPreview.getFont(),
            annotationFontPreview.getFont()
        ));
    }

    // SongScribe stores a key signature as a KeyType + the number of flats or sharps
    public Pair<KeyType, Integer> getKeyTypeAndCountFromCombo() {
        var index = keyCombo.getSelectedIndex();

        // Index 0 is no flats or sharps.
        // Index 1-7 is 1-7 flats.
        // Index 8-14 is 1-7 sharps.
        // If index >= 1, we want to return a number from 1-7 for the accidental count.
        if (index == 0) {
            return new Pair<>(KeyType.FLATS, 0);
        }

        if (index < 8) {
            return new Pair<>(KeyType.FLATS, index);
        }

        return new Pair<>(KeyType.SHARPS, index - 7);
    }

    private static JPanel createTitleAndInputPanel(
        String title,
        JComponent input
    ) {
        var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        StandardDialog.addLabelToBox(panel, title, 5);
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(input);
        return panel;
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

    public static class BaseLabel extends JLabel {

        private final JList<?> list;
        private final int index;
        private final boolean isSelected;

        private BaseLabel(
            String text,
            JList<?> list,
            int index,
            boolean isSelected
        ) {
            super(text);
            this.list = list;
            this.index = index;
            this.isSelected = isSelected;
            setOpaque(true);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Color color;

            // If index == -1, it's drawing the combo box, otherwise the popup
            if (index == -1) {
                color = list.getBackground();
            } else {
                color = isSelected
                    ? list.getSelectionBackground()
                    : list.getBackground();
            }

            g.setColor(color);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(
                isSelected
                    ? list.getSelectionForeground()
                    : list.getForeground()
            );
        }
    }

    public static class NoteCellRenderer implements ListCellRenderer<Object> {

        private static final Dimension CELL_SIZE = new Dimension(14, 36);
        private static final Font FONT = MyFontUtils.getNoteFont()
            .deriveFont(24f);

        private static final EnumMap<Tempo.Type, String> tempoMap =
            new EnumMap<>(Tempo.Type.class);

        static {
            tempoMap.put(Tempo.Type.QUAVER, "\uE1D7");
            tempoMap.put(Tempo.Type.QUAVER_DOTTED, "\uE1D7");
            tempoMap.put(Tempo.Type.CROTCHET, "\uE1D5");
            tempoMap.put(Tempo.Type.CROTCHET_DOTTED, "\uE1D5");
            tempoMap.put(Tempo.Type.MINIM, "\uE1D3");
            tempoMap.put(Tempo.Type.MINIM_DOTTED, "\uE1D3");
            tempoMap.put(Tempo.Type.SEMI_BREVE, "\uE1D2");
        }

        NoteCellRenderer() {
        }

        @Override
        public Component getListCellRendererComponent(
            JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            // FlatLaf has trouble drawing Bravura with the default renderer,
            // so we have to draw it ourselves.
            return new NoteLabel((Tempo.Type) value, list, index, isSelected);
        }

        private static final class NoteLabel extends BaseLabel {

            private static final String WHOLE_NOTE = "\uE1D2";
            private static final String EIGHTH_NOTE = "\uE1D7";
            private static final String DOT = "\uE1E7";
            private static final int DOT_OFFSET = 3;
            private static final int EIGHTH_DOT_OFFSET = 1;

            private final Tempo.Type tempo;

            private NoteLabel(
                Tempo.Type tempo,
                JList<?> list,
                int index,
                boolean isSelected
            ) {
                super(Objects.requireNonNull(tempoMap.get(tempo)), list, index, isSelected);
                this.tempo = tempo;
                setPreferredSize(CELL_SIZE);
                setMinimumSize(CELL_SIZE);
            }

            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setFont(FONT);

                // Adjust the positioning of the text based on which note it is.
                // We want a whole note to be centered vertically in the cell.
                var offset = getText().equals(WHOLE_NOTE)
                    ? (getHeight() / 2)
                    : 8;

                // Draw the note first. If there is a dot, draw that separately.
                var text = getText();
                var x = getWidth() / 2;
                var y = getHeight() - offset;

                g.drawString(text, x, y);

                if (tempo.getNote().getDotCount() > 0) {
                    var metrics = g.getFontMetrics();
                    var width = metrics.stringWidth(text);

                    // Notes with flags tuck the dot in closer
                    var dotOffset = text.equals(EIGHTH_NOTE)
                        ? EIGHTH_DOT_OFFSET
                        : DOT_OFFSET;

                    g.drawString(DOT, x + width + dotOffset, y);
                }
            }
        }
    }

    public static class KeyCellRenderer implements ListCellRenderer<Object> {

        private static final Font FONT = MyFontUtils.getIconFont()
            .deriveFont(80f);

        @Override
        public Component getListCellRendererComponent(
            JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            return new KeyLabel((String) value, list, index, isSelected);
        }

        private static final class KeyLabel extends BaseLabel {

            private static final Dimension CELL_SIZE = new Dimension(80, 80);

            private KeyLabel(
                String text,
                JList<?> list,
                int index,
                boolean isSelected
            ) {
                super(text, list, index, isSelected);
                setPreferredSize(CELL_SIZE);
            }

            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setFont(FONT);
                var metrics = g.getFontMetrics();
                var width = metrics.stringWidth(getText());
                var inset = (getWidth() - width) / 2;
                g.drawString(getText(), inset, getHeight());
            }
        }
    }

    private final class TakeFirstLyricsWordAction extends UIAction {

        private TakeFirstLyricsWordAction() {
            super(
                Strings.get(Strings.DIALOG_COMPOSITION_SETTINGS_TAKE),
                "take-lyrics"
            );
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var lyrics = getComposition().getLyrics();
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
                Dialogs.showErrorMessage(
                    getMainFrame(),
                    Strings.get(Strings.DIALOG_TITLE_COMPOSITION_SETTINGS),
                    Strings.get(Strings.ERROR_COMPOSITION_YEAR_REQUIRED)
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
                    Dialogs.showErrorMessage(
                        getMainFrame(),
                        Strings.get(Strings.DIALOG_TITLE_COMPOSITION_SETTINGS),
                        Strings.get(Strings.ERROR_COMPOSITION_PLACE_REQUIRED)
                    );
                    return;
                }
            }

            attributionArea.append(sb.toString());
        }
    }

}
