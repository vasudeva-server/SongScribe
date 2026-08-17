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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
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

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.ScaleContext;
import songscribe.dom.Ss;
import songscribe.font.FontKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.action.UIAction;
import songscribe.ui.binding.Controls;
import songscribe.ui.binding.Property;
import songscribe.ui.binding.Timing;
import songscribe.ui.binding.ValueProperty;
import songscribe.ui.binding.Widgets;
import songscribe.ui.binding.WritableValue;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.MyJTextField;
import songscribe.ui.component.NonBlankTextField;
import songscribe.ui.component.NumericTextField;
import songscribe.ui.component.score.BaseTitleComponent;
import songscribe.ui.component.score.SubtitleComponent;
import songscribe.ui.component.score.TitleComponent;
import songscribe.util.MyFontUtils;
import songscribe.util.UIUtils;

import static songscribe.ui.binding.ObservableValue.computed;

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

    // Title of song panel. The controls stay fields because the layout code adds
    // them; everything read or written after the build goes through the property
    // over each one.
    private final NumericTextField numberField =
        new NumericTextField(NUMBER_FIELD_COLUMNS, SONG_NUMBER_MIN, SONG_NUMBER_MAX, true);
    private final NonBlankTextField titleField =
        new NonBlankTextField(TITLE_FIELD_COLUMNS, Strings.get(Strings.DOCUMENT_UNTITLED));
    private final Property<String> number = Controls.text(numberField, Timing.WHILE_TYPING);
    private final Property<String> title =
        Controls.text(titleField, Timing.WHILE_TYPING, SongMetadata::normalizeTitle);

    // The title-font chooser's description label. The title font is this tab's own
    // context — no Swing component holds it — so titleFont is where it lives, and
    // the label and the preview are both written from it.
    private final JLabel titleFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final SpinnerModel takeFirstWordsSpinnerModel =
        new SpinnerNumberModel(TAKE_FIRST_WORDS_DEFAULT, TAKE_FIRST_WORDS_MIN, TAKE_FIRST_WORDS_MAX, 1);

    // Assigned in the constructor rather than here: it needs the owning dialog,
    // and field initializers run before the constructor body that captures it.
    private final TakeFirstLyricsWordAction takeAction;
    private final TitleComponent titlePreview = new TitleComponent();

    // Subtitle section — field, font-description label, and preview component.
    private final MyJTextField subtitleField = new MyJTextField(TITLE_FIELD_COLUMNS);
    private final Property<String> subtitle =
        Controls.text(subtitleField, Timing.WHILE_TYPING, SongMetadata::normalizeTitle);
    private final JLabel subtitleFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final SubtitleComponent subtitlePreview = new SubtitleComponent();

    // The page-colored rows the two previews sit in. Fields rather than locals in
    // initContents because their width is bound to the wrap width in the constructor,
    // which runs before the layout is built.
    private final PreviewRow titlePreviewRow = new PreviewRow(titlePreview);
    private final PreviewRow subtitlePreviewRow = new PreviewRow(subtitlePreview);

    // The two chosen fonts. Seeded with the system defaults, which the chooser rows
    // need something to answer with before populate replaces them on every opening.
    private final ValueProperty<Font> titleFont;
    private final ValueProperty<Font> subtitleFont;

    // Each font row owns two actions that subscribe themselves to the message bus, so this
    // tab holds the rows until dispose() releases them. Assigned while the sections are
    // built, from initContents() — a UI builder NullAway cannot follow.
    @SuppressWarnings("NullAway.Init")
    private FontSettingRow.Row titleFontRow;

    @SuppressWarnings("NullAway.Init")
    private FontSettingRow.Row subtitleFontRow;

    // Whether the subtitle preview is currently collapsed (empty). A ValueProperty
    // notifies only on a real change, so the effect over it re-packs the dialog on
    // the empty <-> non-empty transition that actually changes the tab's height,
    // and on no other keystroke.
    private final ValueProperty<Boolean> subtitleEmpty = new ValueProperty<>(true);

    // The song's lyrics, which the Take button derives a title from and is disabled without,
    // and the width the previews wrap at — the stored line width, not the pending one, so the
    // preview shows the score as it stands rather than as the Music tab might change it. Both
    // are set by populate on every opening.
    //
    // The lyrics stay a plain field: nothing observes them. The Take button re-derives
    // its own enablement from enableFromSongState() on every global UI event.
    private String lyricsText = "";
    private final ValueProperty<Ss> wrapWidthSs = new ValueProperty<>(new Ss(0));

    SongSettingsTitleTab(SongSettingsDialog dialog) {
        dialog.super(Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_TITLE));
        takeAction = new TakeFirstLyricsWordAction(getMainFrame());

        // Seeded because FontSettingRow.create captures titleFont::get during initContents();
        // populate replaces both on every opening.
        titleFont = new ValueProperty<>(FontSettingRow.defaultFont(FontKey.TITLE));
        subtitleFont = new ValueProperty<>(FontSettingRow.defaultFont(FontKey.SUBTITLE));

        // A song has to have a title, so OK is unavailable while the field is blank. The
        // condition reads the property, which follows the document, so it answers to a
        // paste or a cut as readily as to typing — where titleField's own guard speaks
        // only once focus leaves.
        requireValid(computed(() -> !title.get().isBlank()));

        // Previews show the chosen font at its natural size: they are never given a
        // ScoreView, so getViewScale() resolves to ViewScale.IDENTITY (no zoom). Each
        // preview sizes itself to its text and is centered in a PreviewRow, which
        // carries the page colour and is one score line wide.

        // Both previews normalize the field text themselves rather than reading what the
        // property answers: the property normalizes on focus loss, so a preview reading it
        // raw would show straight quotes while the user types where the score renders curly
        // ones.
        var dialogBindings = bindings();
        dialogBindings.bind(Widgets.preview(titlePreview), computed(() -> new BaseTitleComponent.Preview(
            Song.numberedTitle(number.get(), SongMetadata.normalizeTitle(title.get())),
            wrapWidthSs.get()
        )));
        dialogBindings.bind(Widgets.preview(subtitlePreview), computed(() -> new BaseTitleComponent.Preview(
            SongMetadata.normalizeTitle(subtitle.get()),
            wrapWidthSs.get()
        )));

        // The subtitle preview collapses to zero height when empty and expands when
        // non-empty. The dialog is packed to a fixed height at show time, so re-pack
        // when it crosses that line. Registering the effect after the binding keeps
        // the binding's settling write from re-packing a dialog that is not yet shown.
        dialogBindings.bind(subtitleEmpty, computed(() -> SongMetadata.normalizeTitle(subtitle.get()).isEmpty()));
        dialogBindings.onChange(subtitleEmpty, this::repackToContent);

        // Both rows are as wide as the line the previews wrap at, so the wrap the user
        // sees is the wrap the page performs. Bound rather than set once, because the
        // width is the song's and arrives with populate.
        WritableValue<Integer> titleRowWidthPx = titlePreviewRow::setLineWidthPx;
        WritableValue<Integer> subtitleRowWidthPx = subtitlePreviewRow::setLineWidthPx;
        dialogBindings.bind(titleRowWidthPx, wrapWidthSs, SongSettingsTitleTab::lineWidthPx);
        dialogBindings.bind(subtitleRowWidthPx, wrapWidthSs, SongSettingsTitleTab::lineWidthPx);

        dialogBindings.bind(Widgets.font(titlePreview), titleFont);
        dialogBindings.bind(Widgets.labelText(titleFontLabel), titleFont, MyFontUtils::getFullFontDescription);
        dialogBindings.bind(Widgets.font(subtitlePreview), subtitleFont);
        dialogBindings.bind(Widgets.labelText(subtitleFontLabel), subtitleFont, MyFontUtils::getFullFontDescription);

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
        var stackedPreview = new JPanel();
        stackedPreview.setLayout(new BoxLayout(stackedPreview, BoxLayout.Y_AXIS));
        stackedPreview.setOpaque(true);
        stackedPreview.setBackground(FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND));
        stackedPreview.add(titlePreviewRow);
        stackedPreview.add(subtitlePreviewRow);

        add(SongSettingsLayout.createPreviewSection(stackedPreview));
    }

    @Override
    protected void dispose() {
        titleFontRow.dispose();
        subtitleFontRow.dispose();
        takeAction.dispose();
    }

    /**
     * The width in pixels a preview wrapping at {@code lineWidthSs} occupies, at the
     * natural size a detached preview renders at. Rounded up because it is a size.
     *
     * @return the line width in pixels
     */
    private static int lineWidthPx(Ss lineWidthSs) {
        return (int) Math.ceil(ScaleContext.ssToPx(lineWidthSs.value()));
    }

    /**
     * A page-colored row one score line wide plus a gap at each end, carrying one
     * preview centered within it.
     * <p>
     * The content width is the song's line width, which is also the width the preview
     * wraps at, so the wrap happens at an edge the user can see: a title that nearly
     * fills the line looks like one. A row sized to its preview instead lets a one-line
     * title reach the full line width — wider than the dialog, which does not re-pack
     * while the user types — and run off both sides of the page area.
     * <p>
     * The gap at each end is padding outside that width, not a narrowing of it: the
     * preview still wraps at the line width, and the padding is what keeps text that
     * fills the line from touching the edge of the colored area.
     * <p>
     * The height stays the preview's own, taken through a zero-gap {@link FlowLayout}
     * that keeps the preview at its preferred size, so an empty preview (zero preferred
     * height) collapses the row to zero height instead of leaving a colored band.
     */
    private static final class PreviewRow extends JPanel {

        private final int horizontalPaddingPx =
            FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP);

        private int lineWidthPx = 0;

        private PreviewRow(JComponent preview) {
            super(new FlowLayout(FlowLayout.CENTER, 0, 0));
            setOpaque(true);
            setBackground(FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND));
            setBorder(BorderFactory.createEmptyBorder(
                0,
                horizontalPaddingPx,
                0,
                horizontalPaddingPx
            ));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            add(preview);
        }

        /** The line width plus the gap at each end, which is the row's own width. */
        private int paddedWidthPx() {
            return lineWidthPx + 2 * horizontalPaddingPx;
        }

        /**
         * Sets the width this row occupies, which is the width its preview wraps at.
         *
         * @effects revalidates, so a width arriving after the dialog is laid out still
         *     reaches the layout
         */
        private void setLineWidthPx(int lineWidthPx) {
            this.lineWidthPx = lineWidthPx;
            revalidate();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(paddedWidthPx(), super.getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(paddedWidthPx(), super.getMinimumSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(paddedWidthPx(), super.getMaximumSize().height);
        }
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

        titleFontRow = FontSettingRow.create(
            getMainFrame(),
            titleFontLabel,
            FontKey.TITLE,
            titleFont::get,
            titleFont::set
        );
        section.add(titleFontRow.panel());

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
        subtitleFontRow = FontSettingRow.create(
            getMainFrame(),
            subtitleFontLabel,
            FontKey.SUBTITLE,
            subtitleFont::get,
            subtitleFont::set
        );
        section.add(subtitleFontRow.panel());

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

    Font getTitleFont() {
        return titleFont.get();
    }

    Font getSubtitleFont() {
        return subtitleFont.get();
    }

    String getSubtitleText() {
        return subtitle.get();
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
        return title.get();
    }

    String getNumberText() {
        return number.get();
    }

    /**
     * Sets every control on this tab to show {@code input}.
     *
     * <p>Whatever is put in comes back out: this tab's getters called straight afterwards,
     * with nothing else touched, answer the same title, number, subtitle and two fonts.
     *
     * <p>The order of the writes below carries nothing. Every preview and label is derived
     * from these properties, so each write re-derives whatever depends on it.
     *
     * @param input the settings this opening of the dialog is showing
     */
    void populate(SongSettingsInput input) {
        var metadata = input.metadata();
        var fonts = input.fonts();

        lyricsText = input.lyrics().text();
        wrapWidthSs.set(input.lineWidthSs());
        titleFont.set(fonts.getFont(FontKey.TITLE));
        subtitleFont.set(fonts.getFont(FontKey.SUBTITLE));
        number.set(metadata.number());
        title.set(metadata.title());
        titleField.rememberCurrentText();
        subtitle.set(metadata.subtitle());
        takeAction.updateEnabledState();
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
            title.set(SongMetadata.titleFromLyrics(lyricsText, maxWords));

            // Lyrics that are all melisma underscores extract to nothing, which the guard ignores
            // — so the title the button failed to improve on is still what comes back if the user
            // then empties the field.
            titleField.rememberCurrentText();
        }
    }
}
