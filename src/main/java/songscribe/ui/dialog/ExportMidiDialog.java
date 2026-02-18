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

import java.awt.*;
import java.io.File;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.swing.*;

import songscribe.prefs.Prefs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.playback.InstrumentDialog;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.FileUtils;

public class ExportMidiDialog extends StandardDialog {

    private final JComboBox<String> instrumentCombo;
    private final JCheckBox withRepeatCheck;
    private File saveFile = null;

    public ExportMidiDialog() {
        super("MIDI properties");
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        StandardDialog.addLabelToBox(center, "Instrument:", 5);
        instrumentCombo = new JComboBox<>(InstrumentDialog.INSTRUMENT_STRING);
        instrumentCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(instrumentCombo);
        center.add(Box.createVerticalStrut(15));
        withRepeatCheck = new JCheckBox("Export with repeats");
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
    protected void getData() {
        var prefs = Prefs.getInstance();
        instrumentCombo.setSelectedIndex(InstrumentDialog.programToIndex(prefs.getInt("instrument")));
        withRepeatCheck.setSelected(prefs.getBoolean("playWithRepeats"));
    }

    @Override
    protected void setData() {
        try {
            var score = mainFrame.getScore();
            var savedSettings = PlaybackController.getPlaybackSettings();

            // Apply export-specific overrides
            PlaybackController.setPlayWithRepeats(withRepeatCheck.isSelected());
            var index = instrumentCombo.getSelectedIndex();
            PlaybackController.setInstrument(index >= 0 ? InstrumentDialog.INSTRUMENT_PROGRAMS[index] : 0);
            PlaybackController.setTempoChangePercent(100);

            try {
                var sequence = PlaybackController.buildSequence(score.getComposition());
                MidiSystem.write(sequence, 1, saveFile);
                FileUtils.openExportFile(mainFrame, saveFile);
            } finally {
                // Restore previous playback settings
                PlaybackController.setPlayWithRepeats(savedSettings.playWithRepeats());
                PlaybackController.setInstrument(savedSettings.instrument());
                PlaybackController.setTempoChangePercent(savedSettings.tempoChangePercent());
            }
        } catch (IOException | InvalidMidiDataException e1) {
            mainFrame.showErrorMessage(MainFrame.COULD_NOT_SAVE_MESSAGE);
        }
    }
}
