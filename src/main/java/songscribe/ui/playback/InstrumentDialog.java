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

import module java.desktop;

import java.util.ArrayList;
import java.util.Map;

import songscribe.Strings;
import songscribe.prefs.Prefs;
import songscribe.ui.Dialogs;
import songscribe.ui.dialog.StandardDialog;
import songscribe.util.MyFontUtils;

public class InstrumentDialog extends StandardDialog {

    public static final String[] INSTRUMENT_STRING;
    public static final int[] INSTRUMENT_PROGRAMS;

    static {
        var names = new ArrayList<String>(128);
        var programs = new ArrayList<Integer>(128);

        if (MidiController.synthesizer != null) {
            var count = 0;

            for (var instrument : MidiController.synthesizer.getLoadedInstruments()) {
                names.add(instrument.getName());
                programs.add(instrument.getPatch().getProgram());
                count += 1;

                if (count == 128) {
                    break;
                }
            }
        }

        // Sort instruments alphabetically by name, keeping programs in sync
        var pairs = new ArrayList<Map.Entry<String, Integer>>(names.size());

        for (var i = 0; i < names.size(); i++) {
            pairs.add(Map.entry(names.get(i), programs.get(i)));
        }

        pairs.sort(Map.Entry.comparingByKey());
        INSTRUMENT_STRING = pairs.stream().map(Map.Entry::getKey).toArray(String[]::new);
        INSTRUMENT_PROGRAMS = pairs.stream().mapToInt(Map.Entry::getValue).toArray();
    }

    private final JList<String> instrumentList = new JList<>(INSTRUMENT_STRING);
    private JButton scaleButton;
    private Color defaultButtonBackground;
    private ScaleAction scaleAction;

    public InstrumentDialog() {
        super(Strings.get(Strings.DIALOG_INSTRUMENTS_TITLE));
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.X_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 20));
        instrumentList.setAlignmentY(Component.CENTER_ALIGNMENT);
        instrumentList.setVisibleRowCount(10);
        instrumentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        center.add(new JScrollPane(instrumentList));
        center.add(Box.createHorizontalStrut(20));
        scaleAction = new ScaleAction();
        scaleButton = new JButton(scaleAction);
        defaultButtonBackground = scaleButton.getBackground();
        scaleButton.setText("\uEF4E");
        scaleButton.setFont(MyFontUtils.getIconFont().deriveFont(24f));
        scaleButton.setMargin(new Insets(8, 8, 8, 8));
        scaleButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        scaleButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playScale"
        );
        scaleButton.getActionMap().put("playScale", scaleAction);
        center.add(scaleButton);
        contentPanel.add(BorderLayout.CENTER, center);
        buttonPanel.remove(applyButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    public static int programToIndex(int program) {
        for (var i = 0; i < INSTRUMENT_PROGRAMS.length; i++) {
            if (INSTRUMENT_PROGRAMS[i] == program) {
                return i;
            }
        }

        return 0;
    }

    @Override
    protected void getData() {
        var index = programToIndex(Prefs.getInstance().getInt("instrument"));
        instrumentList.setSelectedIndex(index);
        instrumentList.ensureIndexIsVisible(index);
    }

    @Override
    protected void setData() {
        var index = instrumentList.getSelectedIndex();
        Prefs.getInstance().put("instrument", index >= 0 ? INSTRUMENT_PROGRAMS[index] : 0);
        mainFrame.getScore().syncPlaybackPrefs();
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            scaleAction.stop();
        }

        super.setVisible(visible);
    }

    private void setScalePlaying(boolean playing) {
        scaleButton.setBackground(playing ? UIManager.getColor("ToggleButton.toolbar.selectedBackground") : defaultButtonBackground);
        scaleButton.repaint();
    }

    private class ScaleAction extends AbstractAction {

        private MetaEventListener endListener = null;

        // Db major scale: Db4, Eb4, F4, Gb4, Ab4, Bb4, C5, Db5
        private final int[] SCALE = new int[] {
            61,
            63,
            65,
            66,
            68,
            70,
            72,
            73,
        };

        ScaleAction() {
            putValue(SHORT_DESCRIPTION, Strings.get(Strings.TOOLTIP_PLAY_INSTRUMENT));
            setEnabled(MidiController.sequencer != null);
        }

        void stop() {
            if (MidiController.sequencer != null && MidiController.sequencer.isRunning()) {
                MidiController.sequencer.stop();

                if (endListener != null) {
                    MidiController.sequencer.removeMetaEventListener(endListener);
                    endListener = null;
                }

                setScalePlaying(false);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (MidiController.sequencer == null) {
                return;
            }

            if (MidiController.sequencer.isRunning()) {
                stop();
                return;
            }

            try {
                var sequence = new Sequence(Sequence.PPQ, PlaybackController.PPQ, 0);
                var track = sequence.createTrack();
                var selectedIndex = instrumentList.getSelectedIndex();
                var program = selectedIndex >= 0 ? INSTRUMENT_PROGRAMS[selectedIndex] : 0;
                var programChange = new ShortMessage();
                programChange.setMessage(
                    ShortMessage.PROGRAM_CHANGE,
                    program,
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
                        PlaybackController.NOTE_VELOCITY
                    );
                    track.add(new MidiEvent(down, ticks));

                    ticks += PlaybackController.PPQ / 2;
                    var up = new ShortMessage();
                    up.setMessage(
                        ShortMessage.NOTE_OFF,
                        pitch,
                        0
                    );
                    track.add(new MidiEvent(up, ticks));
                }

                MidiController.sequencer.setSequence(sequence);
                MidiController.sequencer.setTickPosition(0);

                setScalePlaying(true);

                endListener = message -> {
                    if (message.getType() == MidiMetaMessageTypes.END_OF_TRACK) {
                        MidiController.sequencer.removeMetaEventListener(endListener);
                        endListener = null;
                        SwingUtilities.invokeLater(() -> setScalePlaying(false));
                    }
                };

                MidiController.sequencer.addMetaEventListener(endListener);
                MidiController.sequencer.start();
            } catch (InvalidMidiDataException ex) {
                Dialogs.showErrorMessage(
                    mainFrame,
                    Strings.get(Strings.DIALOG_TITLE_PLAYBACK_ERROR),
                    Strings.get(Strings.ERROR_SCALE_PLAY)
                );
            }
        }
    }
}
