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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import com.formdev.flatlaf.FlatClientProperties;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AttributionFormatter;
import songscribe.dom.AttributionLine;
import songscribe.dom.AttributionPane;
import songscribe.dom.Song;
import songscribe.dom.SongAttribution;
import songscribe.font.FontKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.binding.Controls;
import songscribe.binding.Property;
import songscribe.ui.binding.Timing;
import songscribe.binding.ValueProperty;
import songscribe.ui.binding.Widgets;
import songscribe.binding.WritableValue;
import songscribe.ui.component.MyJTextArea;
import songscribe.ui.component.MyJTextField;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;
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

    // Place and date panel
    private final MyJTextField placeField = new MyJTextField(PLACE_FIELD_COLUMNS);
    // dialog.super(...) completes before any field initializer here runs, so the
    // inherited bindings() already answers.
    private final SongSettingsDateInputRow musicDate = new SongSettingsDateInputRow(bindings());

    // Attribution panel
    private final MyJTextField composerField = new MyJTextField(COMPOSER_FIELD_COLUMNS);
    private final MyJTextArea lyricistField = new MyJTextArea(LYRICIST_ROWS, LYRICIST_COLUMNS);
    private final JComboBox<Song.LyricsSource> sourceCombo =
        new JComboBox<>(Song.LyricsSource.values());
    private final JCheckBox differentDateCheckbox = new JCheckBox(
        Strings.get(Strings.DIALOG_SONG_SETTINGS_DIFFERENT_DATE)
    );
    private final SongSettingsDateInputRow wordsDate = new SongSettingsDateInputRow(bindings());
    private final JPanel wordsDatePanel = new JPanel();
    private final JCheckBox unofficialTranslationCheck = new JCheckBox(
        Strings.get(Strings.DIALOG_SONG_SETTINGS_UNOFFICIAL_TRANSLATION)
    );

    // The unofficial-translation checkbox and the gap above it, as one thing that can be
    // hidden. Always built and shown only for a song that has a translation: what a dialog
    // shows arrives in populate, so which song is open is not something its construction
    // can decide — building the row conditionally would mean reaching for the document.
    private final JPanel translationRow = new JPanel();
    private final JCheckBox arrangementCheck = new JCheckBox(
        Strings.get(Strings.DIALOG_SONG_SETTINGS_ARRANGEMENT)
    );
    private final AttributionPaneWidget attributionPreview = new AttributionPaneWidget();

    // The controls stay fields because the layout code adds them; everything read or
    // written after the build goes through the property over each one.
    //
    // Every text property commits on focus loss rather than while typing: the preview
    // re-packs the dialog when its height changes, and a preview following each keystroke
    // would resize the window under the user mid-word.
    private final Property<String> place =
        Controls.text(placeField, Timing.ON_COMMIT, SongSettingsAttributionTab::normalizePlace);
    private final Property<String> composer =
        Controls.text(composerField, Timing.ON_COMMIT, SongSettingsAttributionTab::normalizeComposer);
    private final Property<String> lyricist =
        Controls.text(lyricistField, Timing.ON_COMMIT, this::normalizeLyricist);
    private final Property<Song.LyricsSource> lyricsSource = Controls.item(sourceCombo);
    private final Property<Boolean> differentDate = Controls.selected(differentDateCheckbox);
    private final Property<Boolean> unofficialTranslation = Controls.selected(unofficialTranslationCheck);
    private final Property<Boolean> arrangement = Controls.selected(arrangementCheck);

    // The attribution and sub-attribution fonts are this tab's own context: no Swing
    // component holds either, so these are where they live, and the chooser rows'
    // description labels and the preview are both written from them. Seeded with the
    // system defaults, which the chooser rows need something to answer with before
    // populate replaces them on every opening.
    private final FontSettingRow.DescriptionLabel attributionFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final FontSettingRow.DescriptionLabel subAttributionFontLabel = FontSettingRow.createFontDescriptionLabel();
    private final ValueProperty<Font> attributionFont =
        new ValueProperty<>(FontSettingRow.defaultFont(FontKey.ATTRIBUTION));
    private final ValueProperty<Font> subAttributionFont =
        new ValueProperty<>(FontSettingRow.defaultFont(FontKey.SUB_ATTRIBUTION));

    // Whether the song carries a translation, which decides both whether the
    // unofficial-translation checkbox is shown at all and whether the preview renders the
    // translation credit. Set by populate on every opening.
    private final ValueProperty<Boolean> hasTranslation = new ValueProperty<>(false);

    // What the preview shows. A ValueProperty notifies only on a real change, so the
    // effect over it re-packs the dialog when the preview's height can actually have
    // moved, and on no other notification a dependency makes. Assigned in the constructor,
    // which is the first point at which the derivation it is seeded from can run.
    private final ValueProperty<AttributionPaneWidget.PreviewState> previewState;

    // Each font row owns two actions that subscribe themselves to the message bus, so this
    // tab holds the rows until dispose() releases them. Assigned while the Fonts section is
    // built, from initContents() — a UI builder NullAway cannot follow.
    @SuppressWarnings("NullAway.Init")
    private FontSettingRow.Row attributionFontRow;

    @SuppressWarnings("NullAway.Init")
    private FontSettingRow.Row subAttributionFontRow;

    SongSettingsAttributionTab(SongSettingsDialog dialog) {
        dialog.super(Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_ATTRIBUTION));

        sourceCombo.setEditable(false);
        composerField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
        lyricistField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Song.SRI_CHINMOY);
        lyricistField.setLineWrap(true);
        lyricistField.setWrapStyleWord(true);
        attributionPreview.setOpaque(true);
        attributionPreview.setBackground(
            FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND)
        );

        var dialogBindings = bindings();

        // The words-date panel is visible exactly when the "different date" box is
        // checked, and the unofficial-translation row exactly when the song has a
        // translation. Both settle at registration, so neither needs an initial pass.
        dialogBindings.bind(Widgets.visible(wordsDatePanel), differentDate);
        dialogBindings.bind(Widgets.visible(translationRow), hasTranslation);

        // Showing or hiding the words-date panel changes the tab's height, and the dialog
        // is packed to a fixed height at show time. Registered after the visibility
        // binding above so the panel has already taken its new state when this re-packs,
        // and on the property rather than the preview because an unchecked box with empty
        // date fields leaves what the preview draws untouched.
        dialogBindings.onNotify(differentDate, this::repackToContent);

        dialogBindings.bind(
            Widgets.labelText(attributionFontLabel), attributionFont, MyFontUtils::getFullFontDescription);
        dialogBindings.bind(
            Widgets.labelText(subAttributionFontLabel), subAttributionFont, MyFontUtils::getFullFontDescription);

        // An empty lyricist inherits the composer, so a committed composer is mirrored
        // into a lyricist field the user has left blank.
        dialogBindings.onNotify(composer, this::inheritComposerIntoEmptyLyricist);

        // The preview is one derivation over every value above, folded through a
        // ValueProperty so the re-pack below runs on a change to what is drawn rather
        // than on every notification a dependency makes. Seeding the property from the
        // same derivation makes the binding's settling write an equal one, which is
        // silent.
        previewState = new ValueProperty<>(buildPreviewState());
        WritableValue<AttributionPaneWidget.PreviewState> preview = attributionPreview::setPreviewState;
        dialogBindings.bind(previewState, dialogBindings.computed(this::buildPreviewState));
        dialogBindings.bind(preview, previewState);

        // The preview's height changes with both the line count and the
        // attribution/sub-attribution font size. The dialog is packed to a fixed height
        // at show time, so a taller preview would be starved by the tab's GridBagLayout.
        // Registering the effect after the binding keeps the binding's settling write
        // from re-packing a dialog that is not yet shown.
        dialogBindings.onNotify(previewState, this::repackToContent);

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

        // Collapsible words-date panel, whose visibility follows the checkbox above
        // through a binding declared in the constructor.
        wordsDatePanel.setLayout(new BoxLayout(wordsDatePanel, BoxLayout.Y_AXIS));
        wordsDatePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addHorizontalDivider(wordsDatePanel);

        var wordsYearLabel = new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_YEAR));
        var wordsDateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wordsDateRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        wordsDate.addTo(wordsDateRow, wordsYearLabel);
        wordsDatePanel.add(wordsDateRow);
        section.add(wordsDatePanel);

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

        translationRow.setLayout(new BoxLayout(translationRow, BoxLayout.Y_AXIS));
        translationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        BaseDialog.addSeparator(translationRow);
        unofficialTranslationCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        translationRow.add(unofficialTranslationCheck);
        section.add(translationRow);

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

        attributionFontRow = FontSettingRow.create(
            mainFrame,
            wordsMusicLabel,
            new FontSettingRow.Spec(attributionFontLabel, FontKey.ATTRIBUTION, attributionFont)
        );
        section.add(attributionFontRow.panel());

        BaseDialog.addSeparator(section);

        subAttributionFontRow = FontSettingRow.create(
            mainFrame,
            datePlaceLabel,
            new FontSettingRow.Spec(subAttributionFontLabel, FontKey.SUB_ATTRIBUTION, subAttributionFont)
        );
        section.add(subAttributionFontRow.panel());

        UIUtils.setFlexibleWidth(section);
        return section;
    }

    @Override
    protected void dispose() {
        attributionFontRow.dispose();
        subAttributionFontRow.dispose();
    }

    Font getAttributionFont() {
        return attributionFont.get();
    }

    Font getSubAttributionFont() {
        return subAttributionFont.get();
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

    /** Applies typographic substitution to the place, leaving an empty place empty. */
    private static String normalizePlace(String text) {
        return StringUtils.processText(text, false);
    }

    /** Applies typographic substitution to the composer, coercing an empty one to Sri Chinmoy. */
    private static String normalizeComposer(String text) {
        return Song.coercePerson(StringUtils.processText(text, false));
    }

    /**
     * An empty lyricist inherits the composer credit — Sri Chinmoy when the composer is
     * itself empty; otherwise the typed name with typographic substitution applied.
     *
     * <p>The composer is read here rather than observed: this runs on focus loss, at
     * which point the committed composer is what the inheritance is supposed to name.
     */
    private String normalizeLyricist(String text) {
        return text.trim().isEmpty()
            ? normalizeComposer(composer.get())
            : StringUtils.processText(text, false);
    }

    /** Mirrors a committed composer into a lyricist field the user has left blank. */
    private void inheritComposerIntoEmptyLyricist() {
        if (lyricist.get().trim().isEmpty()) {
            lyricist.set(composer.get());
        }
    }

    /**
     * Resolves the lyricist credit from the widgets: the trimmed lyricist
     * field, or the composer credit when the lyricist field is blank
     * (matching the legacy field-by-field default).
     */
    private String resolveLyricistText(String composerText) {
        var lyricistText = lyricist.get().trim();
        return lyricistText.isEmpty() ? composerText : lyricistText;
    }

    /**
     * Builds what the preview draws from the tab's own values rather than from the
     * committed song, so it shows the edits in progress — including the fonts chosen in
     * this tab's font rows, which the document does not carry until the dialog commits.
     *
     * <p>Every value is read through a property, which is what makes this a derivation
     * the framework can track: a direct control read would be invisible to it and the
     * preview would answer stale.
     */
    private AttributionPaneWidget.PreviewState buildPreviewState() {
        // The SongAttribution constructor normalizes each field, so the raw property text
        // is passed through directly.
        var composerText = composer.get();
        var lyricistText = resolveLyricistText(composerText);
        var translation = unofficialTranslation.get();
        var gatedDate = getWordsDate();
        var credits = new SongAttribution(
            place.get(),
            musicDate.getYear(),
            musicDate.getMonth(),
            musicDate.getDay(),
            composerText,
            lyricistText,
            lyricsSource.get(),
            arrangement.get(),
            gatedDate.year(),
            gatedDate.month(),
            gatedDate.day()
        );
        var showTranslation = !translation && hasTranslation.get();

        return new AttributionPaneWidget.PreviewState(
            attributionFont.get(),
            subAttributionFont.get(),
            AttributionFormatter.lines(credits, showTranslation)
        );
    }

    String getPlaceText() {
        return place.get();
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
        return Song.coercePerson(composer.get());
    }

    String getLyricistText() {
        return resolveLyricistText(getComposerText());
    }

    Song.LyricsSource getLyricsSource() {
        return lyricsSource.get();
    }

    boolean isArrangement() {
        return arrangement.get();
    }

    boolean isUnofficialTranslation() {
        return unofficialTranslation.get();
    }

    /**
     * The date the words were written.
     *
     * <p>An unchecked "different date" box contributes no date at all, whatever its fields
     * still hold — the box is what says the words have a date of their own, and its three
     * fields keep their last values while it is hidden. Both the commit and the preview ask
     * here, so neither can disagree with what the box says.
     *
     * @return the three components as typed, or {@link WordsDate#NONE} when the box is
     *         unchecked
     */
    WordsDate getWordsDate() {
        if (!differentDate.get()) {
            return WordsDate.NONE;
        }

        return new WordsDate(wordsDate.getYear(), wordsDate.getMonth(), wordsDate.getDay());
    }

    /**
     * Sets every value on this tab to show {@code input}.
     *
     * <p>Whatever is put in comes back out: this tab's getters called straight afterwards,
     * with nothing else touched, answer the same values — with the one documented exception
     * that a words date equal to the music date arrives already collapsed to
     * {@link WordsDate#NONE}, because {@code SongMetadata} stores it that way.
     *
     * <p>The composer is written before the lyricist, so the inheritance effect the composer
     * fires writes a lyricist the stored one then replaces. Every other order carries
     * nothing: the preview, the two font labels and the two collapsible rows are all derived
     * from these values, so each write re-derives whatever depends on it.
     *
     * @param input the settings this opening of the dialog is showing
     */
    void populate(SongSettingsInput input) {
        var metadata = input.metadata();
        var fonts = input.fonts();

        hasTranslation.set(input.lyrics().hasTranslation());
        attributionFont.set(fonts.getFont(FontKey.ATTRIBUTION));
        subAttributionFont.set(fonts.getFont(FontKey.SUB_ATTRIBUTION));
        place.set(metadata.place());
        musicDate.setValues(metadata.year(), metadata.month(), metadata.day());
        composer.set(metadata.composer());
        lyricist.set(metadata.lyricist());
        lyricsSource.set(metadata.lyricsSource());
        arrangement.set(metadata.arrangement());
        unofficialTranslation.set(metadata.unofficialTranslation());

        var wordsYear = metadata.wordsYear();
        wordsDate.setValues(wordsYear, metadata.wordsMonth(), metadata.wordsDay());
        differentDate.set(!wordsYear.isEmpty());
    }

    /**
     * Thin Swing wrapper around {@link AttributionPane}.
     * Delegates measure and paint to the bare rendering surface;
     * holds the state one preview refresh writes so {@link #getPreferredSize()} and
     * {@link #paintComponent} can pass the fonts through.
     */
    private static final class AttributionPaneWidget extends JComponent {

        private final AttributionPane pane = new AttributionPane();

        // Null until the first write, which the owning tab's binding makes while it is
        // being constructed — before this widget is ever laid out or painted.
        @Nullable
        private PreviewState state = null;

        /**
         * Everything one preview refresh writes, as one value.
         * <p>
         * One record rather than three arguments so the binding into this widget can
         * compare against what it last wrote, and so the tab's re-pack effect fires on a
         * change to what is drawn rather than on every notification a dependency makes.
         *
         * @param attributionFont    the font the main credit lines render in
         * @param subAttributionFont the font the date and place lines render in
         * @param lines              the lines to draw, in order
         */
        record PreviewState(Font attributionFont, Font subAttributionFont, List<AttributionLine> lines) {}

        /**
         * Shows {@code state}, then invalidates size and paint once. Batched so a single
         * preview refresh triggers one {@code revalidate}/{@code repaint} rather than
         * three.
         */
        void setPreviewState(PreviewState state) {
            this.state = state;
            pane.setOverrideLines(state.lines());
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            if (state == null) {
                return new Dimension(0, 0);
            }

            return pane.getContentSizePx(state.attributionFont(), state.subAttributionFont());
        }

        @Override
        public Dimension getMaximumSize() {
            // Track the preferred size so the BoxLayout never stretches the
            // preview vertically. Computed dynamically because the preferred
            // size is zero until the first refresh writes the fonts.
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (state == null) {
                return;
            }

            var g2 = (Graphics2D) g;
            GraphicUtils.setRenderingHints(g2);
            // The preview always renders unzoomed (no ScoreView), so measure at natural scale.
            pane.render(
                g2, 0, 0, getWidth(), state.attributionFont(), state.subAttributionFont(),
                AttributionPane.NATURAL_ZOOM_FACTOR);
        }
    }
}
