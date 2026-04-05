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

import java.util.EnumMap;

import songscribe.error.RuntimeError;
import songscribe.file.FileUtils;
import songscribe.music.Tempo;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.InputUtils;
import songscribe.ui.renderer.BaseElementRenderer;

/**
 * A reusable panel containing the standard tempo controls:
 * note-type combo, BPM spinner, description combo, and
 * "show only description" checkbox.
 *
 * <p>Callers supply the set of note types, the checkbox label text,
 * and the resource file names from which description values are loaded.
 */
class TempoSection extends JPanel {

    private final JComboBox<Tempo.Type> tempoTypeCombo;
    private final SpinnerModel tempoSpinnerModel = new SpinnerNumberModel(120, 40, 220, 1);
    private final JComboBox<String> tempoDescriptionCombo = new JComboBox<>();
    private final JCheckBox showOnlyDescriptionCheckBox;

    /**
     * @param types         the note types to show in the type combo
     * @param checkboxLabel the label for the "show only description" checkbox
     * @param fileNames     resource file names from which to load description values
     */
    TempoSection(Tempo.Type[] types, String checkboxLabel, String... fileNames) {
        tempoTypeCombo = new JComboBox<>(types);
        tempoTypeCombo.setRenderer(new NoteCellRenderer());
        tempoTypeCombo.setEditable(false);

        showOnlyDescriptionCheckBox = new JCheckBox(checkboxLabel);

        tempoDescriptionCombo.setEditable(true);

        for (var fileName : fileNames) {
            FileUtils.readComboValuesFromFile(tempoDescriptionCombo, fileName);
        }

        var spinner = new JSpinner(tempoSpinnerModel);
        InputUtils.addNumericFilter(spinner);

        setLayout(new GridBagLayout());
        setAlignmentX(Component.LEFT_ALIGNMENT);

        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_GAP));
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
        gbc.insets = new Insets(FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP), 0, 0, 0);
        add(showOnlyDescriptionCheckBox, gbc);

        // Prevent the panel from growing beyond its preferred size
        setMaximumSize(getPreferredSize());
    }

    /** Populates all controls from the given tempo. */
    void setTempo(Tempo tempo) {
        tempoTypeCombo.setSelectedItem(tempo.getTempoType());
        tempoSpinnerModel.setValue(tempo.getVisibleTempo());
        tempoDescriptionCombo.setSelectedItem(tempo.getTempoDescription());
        showOnlyDescriptionCheckBox.setSelected(!tempo.shouldShowTempo());
    }

    Tempo.Type getTempoType() {
        return (Tempo.Type) tempoTypeCombo.getSelectedItem();
    }

    int getVisibleTempo() {
        return (Integer) tempoSpinnerModel.getValue();
    }

    String getTempoDescription() {
        var item = tempoDescriptionCombo.getSelectedItem();
        return item != null ? (String) item : "";
    }

    boolean isShowOnlyDescription() {
        return showOnlyDescriptionCheckBox.isSelected();
    }

    private static final class NoteCellRenderer implements ListCellRenderer<Object> {

        private static final Dimension CELL_SIZE = new Dimension(14, 36);
        private static final Font FONT = BaseElementRenderer.getMusicFont()
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

            // IO aliases — map to the same glyph as their canonical forms
            tempoMap.put(Tempo.Type.SEMIBREVE, "\uE1D2");
            tempoMap.put(Tempo.Type.MINIMDOTTED, "\uE1D3");
            tempoMap.put(Tempo.Type.CROTCHETDOTTED, "\uE1D5");
            tempoMap.put(Tempo.Type.QUAVERDOTTED, "\uE1D7");

            for (var type : Tempo.Type.values()) {
                if (!tempoMap.containsKey(type)) {
                    throw RuntimeError.exit(
                        "tempoMap missing entry for Tempo.Type." + type.name());
                }
            }
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
                // Static initializer validates all keys, so get() is always non-null
                super(tempoMap.getOrDefault(tempo, ""), list, index, isSelected);
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
}
