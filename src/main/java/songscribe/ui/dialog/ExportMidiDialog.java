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


import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.ui.playback.PlaybackController;
import songscribe.export.ExportUtils;

public class ExportMidiDialog extends StandardDialog {

    private final JComboBox<String> instrumentCombo;
    private final JCheckBox withRepeatCheck;
    private final File saveFile;

    public ExportMidiDialog(File saveFile) {
        super(Strings.get(Strings.DIALOG_EXPORT_MIDI_TITLE));
        this.saveFile = saveFile;
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        BaseDialog.addLabelToBox(center, Strings.get(Strings.LABEL_INSTRUMENT), 5);
        instrumentCombo = new JComboBox<>(PreferencesDialog.getInstrumentStrings());
        instrumentCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(instrumentCombo);
        center.add(Box.createVerticalStrut(15));
        withRepeatCheck = new JCheckBox(Strings.get(Strings.LABEL_EXPORT_WITH_REPEATS));
        withRepeatCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(withRepeatCheck);
        contentPanel.add(BorderLayout.CENTER, center);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    protected boolean getData() {
        instrumentCombo.setSelectedIndex(PreferencesDialog.programToIndex(Prefs.getInt(PrefsKey.INSTRUMENT)));
        withRepeatCheck.setSelected(Prefs.getBoolean(PrefsKey.PLAY_WITH_REPEATS));
        return true;
    }

    @Override
    protected void setData() {
        try {
            var scoreView = requireScoreView();
            var savedSettings = PlaybackController.getPlaybackSettings();

            // Apply export-specific overrides
            PlaybackController.setPlayWithRepeats(withRepeatCheck.isSelected());
            var index = instrumentCombo.getSelectedIndex();
            PlaybackController.setInstrument(index >= 0 ? PreferencesDialog.getInstrumentPrograms()[index] : 0);
            PlaybackController.setTempoChangePercent(100);

            try {
                var sequence = PlaybackController.buildSequence(scoreView.getSong());
                MidiSystem.write(sequence, 1, saveFile);
                ExportUtils.openExportedFile(saveFile);
            } finally {
                PlaybackController.applySettings(savedSettings);
            }
        } catch (IOException | InvalidMidiDataException e1) {
            OptionDialogs.showErrorMessage(
                getMainFrame(),
                Strings.ALERT_TITLE_EXPORT_ERROR,
                Strings.ERROR_FILE_SAVE
            );
        }
    }
}
