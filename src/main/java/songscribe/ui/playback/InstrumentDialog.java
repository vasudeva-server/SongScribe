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
package songscribe.ui.playback;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.swing.*;

import songscribe.ui.Constants;
import songscribe.ui.component.Score;
import songscribe.ui.dialog.StandardDialog;

public class InstrumentDialog extends StandardDialog {

    public static final String[] INSTRUMENT_STRING = instrumentComboFactory();
    private final JList<String> instrumentList = new JList<>(INSTRUMENT_STRING);

    public InstrumentDialog() {
        super("Instrument select");
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.X_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        instrumentList.setAlignmentY(Component.CENTER_ALIGNMENT);
        instrumentList.setVisibleRowCount(10);
        instrumentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        center.add(new JScrollPane(instrumentList));
        center.add(Box.createHorizontalStrut(20));
        var scaleButton = new JButton(new ScaleAction());
        scaleButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        center.add(scaleButton);
        contentPanel.add(BorderLayout.CENTER, center);
        buttonPanel.remove(applyButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    private static String[] instrumentComboFactory() {
        var vs = new ArrayList<String>(128);

        if (MidiController.synthesizer != null) {
            var i = 1;

            for (var instrument : MidiController.synthesizer.getLoadedInstruments()) {
                vs.add(i + " - " + instrument.getName());
                i += 1;

                if (i == 128) {
                    break;
                }
            }
        }

        return vs.toArray(new String[0]);
    }

    @Override
    protected void getData() {
        instrumentList.setSelectedIndex(
            Integer.parseInt(
                mainFrame.getProperties().getProperty(Constants.INSTRUMENT_PROP)
            )
        );
        instrumentList.ensureIndexIsVisible(instrumentList.getSelectedIndex());
    }

    @Override
    protected void setData() {
        mainFrame
            .getProperties()
            .setProperty(
                Constants.INSTRUMENT_PROP,
                Integer.toString(instrumentList.getSelectedIndex())
            );
        mainFrame.fireMusicChanged(this);
    }

    private class ScaleAction extends AbstractAction {

        private final int[] SCALE = new int[] {
            60,
            62,
            64,
            65,
            67,
            69,
            71,
            72,
        };

        ScaleAction() {
            putValue(NAME, "Play the scales");
            setEnabled(MidiController.sequencer != null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (MidiController.sequencer == null) {
                return;
            }

            try {
                var sequence = new Sequence(Sequence.PPQ, Score.PPQ, 0);
                var track = sequence.createTrack();
                var programChange = new ShortMessage();
                programChange.setMessage(
                    ShortMessage.PROGRAM_CHANGE,
                    instrumentList.getSelectedIndex(),
                    0
                );
                track.add(new MidiEvent(programChange, 0));
                var tempoMessage = new MetaMessage();
                var midiTempo = 60000000 / 120;
                tempoMessage.setMessage(
                    MidiMetaMessageTypes.SET_TEMPO,
                    new byte[] {
                        (byte) (midiTempo >> 16),
                        (byte) (midiTempo >> 8),
                        (byte) (midiTempo),
                    },
                    3
                );
                track.add(new MidiEvent(tempoMessage, 0));

                var ticks = 0;

                for (var pitch : SCALE) {
                    var down = new ShortMessage();
                    down.setMessage(
                        ShortMessage.NOTE_ON,
                        pitch,
                        Score.NOTE_VELOCITY
                    );
                    track.add(new MidiEvent(down, ticks));

                    ticks += Score.PPQ / 2;
                    var up = new ShortMessage();
                    up.setMessage(
                        ShortMessage.NOTE_OFF,
                        pitch,
                        Score.NOTE_VELOCITY
                    );
                    track.add(new MidiEvent(up, ticks));
                }

                MidiController.sequencer.setSequence(sequence);
                MidiController.sequencer.setTickPosition(0);
                MidiController.sequencer.start();
            } catch (InvalidMidiDataException ex) {
                mainFrame.showErrorMessage(
                    "Could not play the scale because of an unexpected error."
                );
            }
        }
    }
}
