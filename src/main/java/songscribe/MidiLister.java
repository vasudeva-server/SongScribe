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
package songscribe;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.swing.*;

public final class MidiLister {

    private static final JFrame frame = new JFrame("MidiLister");
    private static final JTextField inputFileField = new JTextField(60);
    private static final JButton openButton = new JButton("Megnyit");
    private static final JButton listButton = new JButton("Listáz");
    private static final JTextArea listArea = new JTextArea(30, 80);
    private static File previousFile = new File(".");

    private MidiLister() {}

    public static void main(String[] args) {
        var north = new JPanel();
        north.add(inputFileField);
        north.add(openButton);
        var center = new JPanel();
        center.add(listButton);
        var south = new JPanel();
        south.add(new JScrollPane(listArea));
        frame.getContentPane().add(north, BorderLayout.NORTH);
        frame.getContentPane().add(center, BorderLayout.CENTER);
        frame.getContentPane().add(south, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        listArea.setEditable(false);
        listButton.addActionListener(_ -> list());
        openButton.addActionListener(_ -> {
            //JFileChooser jfc = new JFileChooser(previousFile.isDirectory() ? previousFile :
            // previousFile.getParentFile());
            var jfc = new JFileChooser(previousFile);
            jfc.showOpenDialog(frame);

            if (jfc.getSelectedFile() != null) {
                previousFile = jfc.getSelectedFile();
                inputFileField.setText(previousFile.getAbsolutePath());
            }
        });
        /*try {
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
        } catch (MidiUnavailableException e) {
            Log.LOGGER.severe(e.toString());
            System.exit(0);
        }*/
    }

    private static void list() {
        try (var stream = new FileInputStream(inputFileField.getText())) {
            var seq = MidiSystem.getSequence(stream);

            listArea.append("File name: " + inputFileField.getText() + "\n");
            listArea.append("Tick length: " + seq.getTickLength() + "\n");
            var tracks = seq.getTracks();
            listArea.append("Number of tracks: " + tracks.length + "\n");

            for (var t = 0; t < tracks.length; t++) {
                var activeTrack = tracks[t];
                listArea.append("Track " + t + "\n");

                for (var e = 0; e < activeTrack.size(); e++) {
                    var activeEvent = activeTrack.get(e);
                    var activeMessage = activeEvent.getMessage();
                    listArea.append("Data: ");
                    var message = activeMessage.getMessage();

                    for (var b : message) {
                        listArea.append(Integer.toHexString(b & 0xFF) + ' ');
                    }

                    listArea.append("   Tick: " + activeEvent.getTick() + "\n");
                }
            }

            listArea.append("\n");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error opening the file");
        } catch (InvalidMidiDataException e) {
            JOptionPane.showMessageDialog(frame, "A MIDI file format error");
        }
    }
}
