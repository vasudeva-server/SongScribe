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

import java.io.File;
import java.io.IOException;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.prefs.Prefs;
import songscribe.Strings;
import songscribe.ui.Dialogs;
import songscribe.ui.playback.InstrumentDialog;
import songscribe.ui.playback.PlaybackController;
import songscribe.file.FileUtils;

public class ExportMidiDialog extends StandardDialog {

    private final JComboBox<String> instrumentCombo;
    private final JCheckBox withRepeatCheck;
    private @Nullable File saveFile = null;

    public ExportMidiDialog() {
        super(Strings.get(Strings.DIALOG_EXPORT_MIDI_TITLE));
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        StandardDialog.addLabelToBox(center, Strings.get(Strings.LABEL_INSTRUMENT), 5);
        instrumentCombo = new JComboBox<>(InstrumentDialog.INSTRUMENT_STRING);
        instrumentCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(instrumentCombo);
        center.add(Box.createVerticalStrut(15));
        withRepeatCheck = new JCheckBox(Strings.get(Strings.LABEL_EXPORT_WITH_REPEATS));
        withRepeatCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(withRepeatCheck);
        contentPanel.add(BorderLayout.CENTER, center);
        buttonPanel.remove(applyButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    public void setSaveFile(File saveFile) {
        this.saveFile = saveFile;
    }

    @Override
    protected boolean getData() {
        var prefs = Prefs.getInstance();
        instrumentCombo.setSelectedIndex(InstrumentDialog.programToIndex(prefs.getInt("instrument")));
        withRepeatCheck.setSelected(prefs.getBoolean("playWithRepeats"));
        return true;
    }

    @Override
    protected void setData() {
        try {
            var score = Objects.requireNonNull(getScore());
            var savedSettings = PlaybackController.getPlaybackSettings();

            // Apply export-specific overrides
            PlaybackController.setPlayWithRepeats(withRepeatCheck.isSelected());
            var index = instrumentCombo.getSelectedIndex();
            PlaybackController.setInstrument(index >= 0 ? InstrumentDialog.INSTRUMENT_PROGRAMS[index] : 0);
            PlaybackController.setTempoChangePercent(100);

            try {
                var sequence = PlaybackController.buildSequence(score.getComposition());
                var file = Objects.requireNonNull(saveFile, "saveFile must be set before export");
                MidiSystem.write(sequence, 1, file);
                FileUtils.openExportFile(file);
            } finally {
                // Restore previous playback settings
                PlaybackController.setPlayWithRepeats(savedSettings.playWithRepeats());
                PlaybackController.setInstrument(savedSettings.instrument());
                PlaybackController.setTempoChangePercent(savedSettings.tempoChangePercent());
            }
        } catch (IOException | InvalidMidiDataException e1) {
            Dialogs.showErrorMessage(
                getMainFrame(),
                Strings.get(Strings.DIALOG_TITLE_EXPORT_ERROR),
                Strings.get(Strings.ERROR_FILE_SAVE)
            );
        }
    }
}
