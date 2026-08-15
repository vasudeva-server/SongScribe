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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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
import songscribe.ui.component.score.BaseTitleComponent;
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
    private final NonEmptyGuard titleBlankGuard;
    private final JLabel subtitleFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final SubtitleComponent subtitlePreview = new SubtitleComponent();

    // Tracks whether the subtitle preview is currently collapsed (empty), so
    // the dialog is re-packed only on the empty <-> non-empty transition that
    // actually changes the tab's height.
    private boolean subtitlePreviewEmpty = true;

    // The song's lyrics, which the Take button derives a title from and is disabled without,
    // and the width the previews wrap at — the stored line width, not the pending one, so the
    // preview shows the score as it stands rather than as the Music tab might change it. Both
    // are set by populate on every opening.
    private String lyricsText = "";
    private double previewWrapWidthSs = 0;

    SongSettingsTitleTab(SongSettingsDialog dialog) {
        dialog.super(Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_TITLE));
        this.dialog = dialog;
        takeAction = new TakeFirstLyricsWordAction(dialog.getMainFrame());

        titleBlankGuard = new NonEmptyGuard(titleField, Strings.get(Strings.DOCUMENT_UNTITLED));
        titleField.setInputVerifier(titleBlankGuard);

        // Previews show the chosen font at its natural size: they are never given a
        // ScoreView, so getViewScale() resolves to ViewScale.IDENTITY (no zoom). Each
        // preview sizes itself to its text; the page colour that used to be painted on
        // the preview itself now comes from the row panel createPreviewRow wraps it in.

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
        // Each preview now sizes itself to its text rather than to the song's line
        // width, so the row that carries the page colour must center the preview
        // within the section rather than stretching it to fill the row.
        var pageBackground = FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND);
        var stackedPreview = new JPanel();
        stackedPreview.setLayout(new BoxLayout(stackedPreview, BoxLayout.Y_AXIS));
        stackedPreview.setOpaque(true);
        stackedPreview.setBackground(pageBackground);
        stackedPreview.add(createPreviewRow(titlePreview, pageBackground));
        stackedPreview.add(createPreviewRow(subtitlePreview, pageBackground));

        add(SongSettingsLayout.createPreviewSection(stackedPreview));
    }

    /**
     * Wraps a score preview component in a page-colored row that centers it
     * horizontally.
     * <p>
     * The row itself stretches to the full section width (its default maximum
     * size lets the enclosing {@link BoxLayout} do so), but a zero-gap
     * {@link FlowLayout} keeps the preview at its own preferred size rather than
     * stretching it, so an empty preview (zero preferred height) collapses the
     * row to zero height instead of leaving a colored band.
     */
    private static JPanel createPreviewRow(JComponent preview, Color pageBackground) {
        var row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(true);
        row.setBackground(pageBackground);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(preview);
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

    /**
     * The two controls a caller can be sent to on this tab.
     * <p>
     * {@link SongSettingsDialog#show} hands one of these straight to
     * {@code showTab} as the caret target, so the section-to-field mapping is an identity
     * a test can assert rather than a chain of enums to follow.
     */
    JTextField getTitleField() {
        return titleField;
    }

    JTextField getSubtitleField() {
        return subtitleField;
    }

    private void updateTitlePreview() {
        // Normalize through the same seam the commit uses so the preview shows
        // the typographic substitution (and trimming) the score will render.
        titlePreview.setPreview(new BaseTitleComponent.Preview(
            Song.numberedTitle(numberField.getText(), SongMetadata.normalizeTitle(titleField.getText())),
            previewWrapWidthSs
        ));
    }

    private void updateSubtitlePreview() {
        var text = SongMetadata.normalizeTitle(subtitleField.getText());
        subtitlePreview.setPreview(new BaseTitleComponent.Preview(text, previewWrapWidthSs));

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

    /**
     * Sets every control on this tab to show {@code input}.
     *
     * <p>Whatever is put in comes back out: this tab's getters called straight afterwards,
     * with nothing else touched, answer the same title, number, subtitle and two fonts.
     *
     * <p>The preview width is set before any field, because writing to a field fires the
     * preview updaters and they wrap at it.
     *
     * @param input the settings this opening of the dialog is showing
     */
    void populate(SongSettingsInput input) {
        var metadata = input.metadata();
        var fonts = input.fonts();

        lyricsText = input.lyrics().text();
        previewWrapWidthSs = input.music().lineWidthSs();

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
        numberField.setText(metadata.number());
        titleField.setText(metadata.title());
        titleBlankGuard.rememberCurrentText();
        subtitleField.setText(metadata.subtitle());
        takeAction.updateEnabledState();
        updateTitlePreview();
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
            return !lyricsText.isEmpty();
        }

        @Override
        protected void performAction(ActionEvent e) {
            var maxWords = ((Number) takeFirstWordsSpinnerModel.getValue()).intValue();
            titleField.setText(SongMetadata.titleFromLyrics(lyricsText, maxWords));

            // Lyrics that are all melisma underscores extract to nothing, which the guard ignores
            // — so the title the button failed to improve on is still what comes back if the user
            // then empties the field.
            titleBlankGuard.rememberCurrentText();
        }
    }
}
