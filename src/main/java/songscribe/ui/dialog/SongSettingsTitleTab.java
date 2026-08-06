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

import javax.swing.event.DocumentEvent;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.font.FontKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.MyJTextField;
import songscribe.ui.component.NonEmptyGuard;
import songscribe.ui.component.NumericTextField;
import songscribe.ui.component.score.SubtitleComponent;
import songscribe.ui.component.score.TitleComponent;
import songscribe.util.UIUtils;

/**
 * The {@link SongSettingsDialog} Title tab: song number, title, and subtitle,
 * each with its own font chooser and a live preview of what the score renders.
 */
final class SongSettingsTitleTab extends BaseDialog.Tab {

    private static final int SONG_NUMBER_MIN = 1;
    private static final int SONG_NUMBER_MAX = 1000;
    private static final int NUMBER_FIELD_COLUMNS = 3;
    private static final int TITLE_FIELD_COLUMNS = 47;
    private static final int TAKE_FIRST_WORDS_DEFAULT = 4;
    private static final int TAKE_FIRST_WORDS_MIN = 1;
    private static final int TAKE_FIRST_WORDS_MAX = 10;

    private final SongSettingsDialog dialog;

    // Title of song panel
    private final NumericTextField numberField =
        new NumericTextField(NUMBER_FIELD_COLUMNS, SONG_NUMBER_MIN, SONG_NUMBER_MAX, true);
    private final MyJTextField titleField = new MyJTextField(TITLE_FIELD_COLUMNS);

    // The title-font chooser's description label. The title font is this
    // tab's own context; titlePreview holds the chosen font.
    private final JLabel titleFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final SpinnerModel takeFirstWordsSpinnerModel =
        new SpinnerNumberModel(TAKE_FIRST_WORDS_DEFAULT, TAKE_FIRST_WORDS_MIN, TAKE_FIRST_WORDS_MAX, 1);

    // Assigned in the constructor rather than here: it needs the owning dialog,
    // and field initializers run before the constructor body that captures it.
    private final TakeFirstLyricsWordAction takeAction;
    private final TitleComponent titlePreview = new TitleComponent();

    // Subtitle section — field, font-description label, and preview component.
    private final MyJTextField subtitleField = new MyJTextField(TITLE_FIELD_COLUMNS);
    private final JLabel subtitleFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final SubtitleComponent subtitlePreview = new SubtitleComponent();

    // Tracks whether the subtitle preview is currently collapsed (empty), so
    // the dialog is re-packed only on the empty <-> non-empty transition that
    // actually changes the tab's height.
    private boolean subtitlePreviewEmpty = true;

    SongSettingsTitleTab(SongSettingsDialog dialog) {
        dialog.super(Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_TITLE));
        this.dialog = dialog;
        takeAction = new TakeFirstLyricsWordAction(dialog.getMainFrame());

        titleField.setInputVerifier(new NonEmptyGuard(
            titleField,
            dialog.contentPanel,
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
        BaseDialog.addSectionSeparator(this);
        add(createSubtitleSection());
        BaseDialog.addSectionSeparator(this);

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

        add(SongSettingsLayout.createPreviewSection(stackedPreview));
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

    private JPanel createTitleSection() {
        var section = new BaseDialog.TitledSection(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_TITLE_OF_SONG)
        );
        addFilledFieldRow(
            section,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SONG_TITLE),
            titleField
        );

        BaseDialog.addSeparator(section);

        BaseDialog.addLabeledField(
            section,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_NUMBER),
            numberField,
            BaseDialog.LabelPosition.LEFT
        );

        BaseDialog.addSeparator(section);

        section.add(FontSettingRow.create(
            dialog.getMainFrame(),
            titleFontLabel,
            FontKey.TITLE,
            titlePreview::getFont,
            this::applyTitleFont
        ));

        BaseDialog.addLargeSeparator(section);
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

        BaseDialog.addSeparator(section);
        section.add(FontSettingRow.create(
            dialog.getMainFrame(),
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
            dialog.repackToContent();
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
        var song = dialog.getSong();
        var fonts = dialog.requireScoreView().getDocumentFonts();
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
            return !dialog.getSong().getLyricsText().isEmpty();
        }

        @Override
        protected void performAction(ActionEvent e) {
            var maxWords = ((Number) takeFirstWordsSpinnerModel.getValue()).intValue();
            titleField.setText(
                SongSettingsDialog.extractLyricsTitle(dialog.getSong().getLyricsText(), maxWords)
            );
        }
    }
}
