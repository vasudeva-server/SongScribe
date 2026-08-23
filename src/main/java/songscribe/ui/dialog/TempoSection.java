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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

import songscribe.Strings;
import songscribe.binding.Bindings;
import songscribe.binding.Property;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.dom.TempoMarking;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.binding.Controls;
import songscribe.ui.binding.Widgets;
import songscribe.ui.component.DurationListCellRenderer;
import songscribe.ui.component.InputUtils;

/**
 * A reusable panel containing the standard tempo controls:
 * note-type combo, BPM spinner, description combo, and
 * "show only description" checkbox.
 *
 * <p>Callers supply only the resource file names the description values are loaded from. The
 * note types and the checkbox label are the same for every caller, so the panel holds both.
 *
 * <p>The note-type combo and the BPM spinner stay enabled while the checkbox is selected.
 * {@link Tempo#getTempoType()} is the beat that beams group against and that tuplets are measured
 * in, and {@link Tempo#getRealTempo()} drives MIDI playback. Neither depends on the metronome mark
 * being drawn.
 *
 * <p><strong>The description combo and the checkbox cannot both say "nothing".</strong> A checked
 * checkbox is a {@link TempoMarking.TextOnly}, which refuses blank text, so a checked checkbox
 * with {@code (none)} chosen would leave {@link #getTempo()} nothing to build. Two bindings keep
 * that pair out of reach: the checkbox is disabled while the description is blank, and the
 * {@code (none)} row is barred while the checkbox is checked.
 */
class TempoSection extends JPanel {

    private static final int BPM_MIN = 40;
    private static final int BPM_MAX = 220;

    private final JComboBox<Duration> tempoTypeCombo;
    private final SpinnerModel tempoSpinnerModel =
        new SpinnerNumberModel(Tempo.DEFAULT_BPM, BPM_MIN, BPM_MAX, 1);
    private final OtherValueComboBox tempoDescriptionCombo;
    private final JCheckBox showOnlyDescriptionCheckBox;

    private final Property<Duration> tempoType;
    private final Property<Number> visibleTempo;
    private final Property<String> tempoDescription;
    private final Property<Boolean> showOnlyDescription;

    /**
     * @param bindings  the owning dialog's bindings, which the two rule bindings belong to and
     *                  which the dialog's close disposes
     * @param fileNames resource file names from which to load description values
     * @effects declares two bindings on {@code bindings}, and settles both controls through
     *          them before returning
     */
    TempoSection(Bindings bindings, List<String> fileNames) {
        tempoTypeCombo = DurationListCellRenderer.createCombo(Duration.values());

        showOnlyDescriptionCheckBox = new JCheckBox(
            Strings.get(Strings.DIALOG_TEMPO_SHOW_ONLY_DESCRIPTION)
        );

        tempoDescriptionCombo = new OtherValueComboBox(
            new OtherValuePrompt(
                Strings.get(Strings.DIALOG_TEMPO_TITLE),
                Strings.get(Strings.LABEL_TEMPO_OTHER_PROMPT)
            ),
            OtherValueComboBox.EmptyChoice.OFFERED,
            fileNames
        );

        tempoType = Controls.item(tempoTypeCombo);
        visibleTempo = Controls.number(tempoSpinnerModel);
        tempoDescription = Controls.item(tempoDescriptionCombo);
        showOnlyDescription = Controls.selected(showOnlyDescriptionCheckBox);

        var spinner = new JSpinner(tempoSpinnerModel);
        InputUtils.addNumericFilter(spinner);

        setLayout(new GridBagLayout());
        setAlignmentX(Component.LEFT_ALIGNMENT);

        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP));
        gbc.anchor = GridBagConstraints.WEST;
        add(tempoTypeCombo, gbc);

        gbc.gridx += 1;
        add(new JLabel("="), gbc);

        gbc.gridx += 1;
        add(spinner, gbc);

        gbc.gridx += 1;
        add(tempoDescriptionCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP), 0, 0, 0);
        add(showOnlyDescriptionCheckBox, gbc);

        // The two directions of one rule: a text-only marking needs text, so neither control
        // may be moved into the state where the other one leaves it with none.
        bindings.bind(
            Widgets.enabled(showOnlyDescriptionCheckBox),
            tempoDescription,
            description -> !description.isBlank()
        );
        bindings.bind(
            tempoDescriptionCombo::setEmptyChoiceSelectable,
            showOnlyDescription,
            textOnly -> !textOnly
        );

        // After the bindings, which settle both controls as they are declared. A cap taken
        // before them would be a cap on a layout that no longer holds.
        setMaximumSize(getPreferredSize());
    }

    /**
     * Populates all four controls from {@code tempo}.
     *
     * <p>The "show only description" checkbox is checked for a {@link TempoMarking.TextOnly}
     * marking, which is the marking that leaves the metronome out.
     *
     * @param tempo the tempo the controls are to show
     * @effects writes all four controls
     */
    void setTempo(Tempo tempo) {
        var marking = tempo.getMarking();

        tempoType.set(tempo.getTempoType());
        visibleTempo.set(tempo.getVisibleTempo());
        tempoDescription.set(marking.description());
        showOnlyDescription.set(marking instanceof TempoMarking.TextOnly);
    }

    /**
     * @return the tempo the four controls describe. Its marking is a
     *         {@link TempoMarking.TextOnly} while the "show only description" checkbox is
     *         checked, and a {@link TempoMarking.Metronome} otherwise.
     * @throws IllegalArgumentException when the checkbox is checked and the description is
     *                                  blank. The two bindings make that pair unreachable, so
     *                                  this fires only if one of them is wrong
     */
    Tempo getTempo() {
        var description = tempoDescription.get();
        var marking = showOnlyDescription.get()
            ? new TempoMarking.TextOnly(description)
            : (TempoMarking) new TempoMarking.Metronome(description);

        return new Tempo(visibleTempo.get().intValue(), tempoType.get(), marking);
    }
}
