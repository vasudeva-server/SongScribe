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
import songscribe.dom.Duration;
import songscribe.dom.ScaleContext;
import songscribe.layout.PageModel;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.InputUtils;
import songscribe.ui.component.MyJTextField;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

/**
 * The {@link SongSettingsDialog} Music tab: the song's tempo and line width.
 *
 * <p>There is no key signature here. A song has no key of its own — its key is line 0's — so a key
 * is changed on the score, at the line it takes effect on.
 */
final class SongSettingsMusicTab extends BaseDialog.Tab {

    private static final int LINE_WIDTH_FIELD_COLUMNS = 6;

    private final SongSettingsDialog dialog;

    private final TempoSection tempoSection = new TempoSection(
        Duration.values(),
        Strings.get(Strings.DIALOG_SONG_SETTINGS_SHOW_ONLY_DESCRIPTION),
        "tempos"
    );

    private final MyJTextField lineWidthField = new MyJTextField(LINE_WIDTH_FIELD_COLUMNS);
    private final JLabel unitLabel = new JLabel();

    // The width the field was populated with, and the text that was put in it, so an
    // unedited field can commit its exact loaded value instead of re-parsing the rounded
    // display text. Both are set by revertLineWidthField on every show.
    private double loadedLineWidthSs = 0;
    private String loadedLineWidthText = "";

    SongSettingsMusicTab(SongSettingsDialog dialog) {
        dialog.super(Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_MUSIC));
        this.dialog = dialog;

        InputUtils.addDecimalFilter(lineWidthField);
        lineWidthField.setInputVerifier(new LineWidthVerifier());

        build();
    }

    @Override
    protected void initContents() {
        add(createTempoSection(), constraints);
        BaseDialog.addSectionSeparator(this);
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
        var song = dialog.getSong();
        tempoSection.setTempo(song.getTempo());
        revertLineWidthField();
        return true;
    }

    @Override
    protected void setData() {
        var song = dialog.getSong();
        SongSettingsDialog.applyMusicTabChanges(
            song,
            tempoSection.getTempoType(),
            tempoSection.getVisibleTempo(),
            tempoSection.getTempoDescription(),
            !tempoSection.isShowOnlyDescription()
        );

        dialog.requireScoreView().updatePageLayout(getPendingLineWidthSs());
    }

    @Override
    protected boolean isValidData() {
        return validateLineWidthOrShowError();
    }

    /**
     * The line-width field. The tab is package-private, so this is how
     * {@link SongSettingsDialog} exposes the field a test drives.
     */
    MyJTextField getLineWidthField() {
        return lineWidthField;
    }

    private void revertLineWidthField() {
        var isMetric = Prefs.getBoolean(PrefsKey.METRIC);
        var lineWidthSs = dialog.getSong().getLineWidthSs();
        var lineWidthInches = ScaleContext.ssToInches(lineWidthSs);
        var displayValue = isMetric
            ? lineWidthInches * GraphicUtils.CM_PER_INCH
            : lineWidthInches;
        var displayText = String.valueOf(Utils.roundToTwoDecimalPlaces(displayValue));

        loadedLineWidthSs = lineWidthSs;
        loadedLineWidthText = displayText;

        lineWidthField.setText(displayText);
        unitLabel.setText(
            Strings.get(isMetric ? Strings.LABEL_UNIT_CM : Strings.LABEL_UNIT_INCHES)
        );
    }

    /** The line width the commit will apply, in staff spaces. */
    double getPendingLineWidthSs() {
        return SongSettingsDialog.pendingLineWidthSs(
            lineWidthField.getText(),
            loadedLineWidthText,
            loadedLineWidthSs,
            Prefs.getBoolean(PrefsKey.METRIC)
        );
    }

    /**
     * Parses and validates the current line width field text.
     *
     * @return the width in inches if valid, or -1 if the text is empty,
     *         unparseable, or out of range
     */
    private double validateLineWidth() {
        return SongSettingsDialog.validateLineWidthText(
            lineWidthField.getText(),
            Prefs.getBoolean(PrefsKey.METRIC)
        );
    }

    /**
     * Returns whether the line width field holds a usable value, showing the reason and
     * returning false when it does not.
     *
     * <p>The rejection has to be reported here rather than left to
     * {@link LineWidthVerifier}: OK only consults the verifier when the field still owns
     * focus, so on every other path a bad width silently made OK do nothing. The two never
     * both report — OK returns early when the verifier rejects, before validation runs.
     */
    private boolean validateLineWidthOrShowError() {
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

    private void showLineWidthError(String key, boolean isMetric) {
        var min = PageModel.MIN_LINE_WIDTH_INCHES;
        var max = PageModel.MAX_LINE_WIDTH_INCHES;

        if (isMetric) {
            min *= GraphicUtils.CM_PER_INCH;
            max *= GraphicUtils.CM_PER_INCH;
        }

        var unit = Strings.get(isMetric ? Strings.LABEL_UNIT_CM : Strings.LABEL_UNIT_INCHES);

        OptionDialogs.showErrorMessage(
            dialog.contentPanel,
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
            return validateLineWidthOrShowError();
        }
    }
}
