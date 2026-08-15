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

import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import songscribe.Strings;
import songscribe.dom.Duration;
import songscribe.dom.ScaleContext;
import songscribe.dom.Tempo;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.InputUtils;
import songscribe.ui.component.MyJTextField;
import songscribe.ui.dialog.backend.LineWidthRules;
import songscribe.util.LengthUnit;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

/**
 * The {@link SongSettingsDialog} Music tab: the song's tempo and line width.
 *
 * <p>There is no key control here. A song has no key of its own — every line carries one, set
 * from the score. See {@code docs/key-signatures.md}.
 *
 * <p>The line-width field is the only control here whose value can be wrong, and it is checked
 * twice over on purpose: once by its own {@link LineWidthVerifier} when focus leaves it, and
 * once by the back end when OK is pressed. Both ask
 * {@link LineWidthRules#validate(LineWidthEntry)}, so they cannot disagree, and only one of
 * them ever reports — OK stops at the verifier when the field still holds focus, and reaches
 * validation only when it does not.
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

    // The text the field was populated with, so an untouched field can be told from an edited
    // one. Set by revertLineWidthField on every show.
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

    /**
     * Sets every control on this tab to show {@code input}.
     *
     * <p>Whatever is put in comes back out: {@link #gather()} called straight afterwards, with
     * nothing else touched, answers the same tempo — and a line width that commits as
     * the one that came in rather than as a reading of its own rounded display text.
     *
     * @param input the settings this opening of the dialog is showing
     */
    void populate(SongSettingsInput input) {
        var music = input.music();
        tempoSection.setTempo(music.tempo());
        revertLineWidthField(music.lineWidthSs());
    }

    /**
     * @return what this tab's controls now say, for every state they can reach — including a
     *         line-width field left empty or holding text that is not a number
     */
    MusicChoices gather() {
        var tempo = new Tempo(
            tempoSection.getVisibleTempo(),
            tempoSection.getTempoType(),
            tempoSection.getTempoDescription(),
            !tempoSection.isShowOnlyDescription()
        );

        return new MusicChoices(tempo, lineWidthEntry());
    }

    /** What the line-width field says, and whether the user is the one who said it. */
    private LineWidthEntry lineWidthEntry() {
        var text = lineWidthField.getText();
        var edit = text.equals(loadedLineWidthText)
            ? LineWidthEntry.Edit.UNEDITED
            : LineWidthEntry.Edit.EDITED;

        return new LineWidthEntry(text, displayUnit(), edit);
    }

    private void revertLineWidthField(double lineWidthSs) {
        var unit = displayUnit();
        var displayValue = unit.fromInches(ScaleContext.ssToInches(lineWidthSs));
        var displayText = String.valueOf(Utils.roundToTwoDecimalPlaces(displayValue));

        loadedLineWidthText = displayText;
        lineWidthField.setText(displayText);
        unitLabel.setText(Strings.get(unit.labelKey()));
    }

    /** The unit the user has asked to see lengths in. */
    private static LengthUnit displayUnit() {
        return Prefs.getBoolean(PrefsKey.METRIC) ? LengthUnit.CENTIMETERS : LengthUnit.INCHES;
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
            return LineWidthRules.validate(lineWidthEntry()).isValid();
        }

        @Override
        public boolean shouldYieldFocus(JComponent source, JComponent target) {
            var result = LineWidthRules.validate(lineWidthEntry());

            if (result.isValid()) {
                return true;
            }

            dialog.showFailure(result.failures().getFirst());
            return false;
        }
    }
}
