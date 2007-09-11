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
import java.util.Properties;

import javax.sound.midi.MidiSystem;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.Constants;
import songscribe.ui.component.MainFrame;
import songscribe.ui.playback.InstrumentDialog;
import songscribe.util.FileUtils;

public class ExportMidiDialog extends StandardDialog {

    private final JComboBox<String> instrumentCombo;
    private final JCheckBox withRepeatCheck;
    private File saveFile = null;

    public ExportMidiDialog(MainFrame mainFrame) {
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
        instrumentCombo.setSelectedIndex(
            Integer.parseInt(
                mainFrame.getProperties().getProperty(Constants.INSTRUMENT_PROP)
            )
        );
        withRepeatCheck.setSelected(
            mainFrame
                .getProperties()
                .getProperty(Constants.WITH_REPEAT_PROP)
                .equals(Constants.TRUE_VALUE)
        );
    }

    @Override
    protected void setData() {
        try {
            var props = getProperties();
            var score = mainFrame.getScore();
            score.musicDidChange(props);
            MidiSystem.write(score.getSequence(), 1, saveFile);
            score.musicDidChange(mainFrame.getProperties());
            FileUtils.openExportFile(mainFrame, saveFile);
        } catch (IOException e1) {
            mainFrame.showErrorMessage(MainFrame.COULD_NOT_SAVE_MESSAGE);
        }
    }

    @NotNull
    private Properties getProperties() {
        var props = new Properties(mainFrame.getProperties());
        props.setProperty(
            Constants.WITH_REPEAT_PROP,
            withRepeatCheck.isSelected()
                ? Constants.TRUE_VALUE
                : Constants.FALSE_VALUE
        );
        props.setProperty(
            Constants.INSTRUMENT_PROP,
            Integer.toString(instrumentCombo.getSelectedIndex())
        );
        props.setProperty(Constants.TEMPO_CHANGE_PROP, "100");
        return props;
    }
}
