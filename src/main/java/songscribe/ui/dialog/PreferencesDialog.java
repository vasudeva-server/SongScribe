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

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PageSizeDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.Appearance;
import songscribe.ui.AppearanceManager;
import songscribe.ui.Dialogs;
import songscribe.ui.layout.PageModel;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlaybackController;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

public class PreferencesDialog extends BaseDialog {

    private static final int EXTRA_WIDTH = 100;
    private static final int APPEARANCE_ICON_SIZE = 100;
    private static final int APPEARANCE_ITEM_GAP = 40;
    private static final int APPEARANCE_ICON_RADIO_GAP = 5;

    private static final int VOLUME_MIN = 50;
    private static final int VOLUME_MAX = 100;
    private static final int VOLUME_MAJOR_TICK = 25;

    private static final int TEMPO_MIN = 50;
    private static final int TEMPO_MAX = 150;
    private static final int TEMPO_MAJOR_TICK = 25;

    private static final int[] VALID_TEMPO_STOPS = { 50, 75, 100, 125, 150 };

    private static final int DURATION_STEP = 17;
    private static final int[] VALID_DURATION_STOPS = { 32, 49, 66, 83, 100 };

    private static String[] instrumentStrings = new String[0];
    private static int[] instrumentPrograms = new int[0];
    private static boolean instrumentsLoaded = false;

    // Play tab — Feedback section
    private final JCheckBox playInsertingNoteCheck = new JCheckBox(
        Strings.get(Strings.LABEL_PREFS_PLAY_INSERTED_NOTE)
    );
    private final JCheckBox playSelectedNoteCheck = new JCheckBox(
        Strings.get(Strings.LABEL_PREFS_PLAY_SELECTED_NOTE)
    );

    // Play tab — Playback section
    private final JSlider durationSlider = new JSlider(32, 100);
    private final JSlider volumeSlider = new JSlider(VOLUME_MIN, VOLUME_MAX);
    private final JSlider tempoSlider = new JSlider(TEMPO_MIN, TEMPO_MAX);

    // General tab — Page Size section
    private final JRadioButton letterRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_PAGE_SIZE_LETTER)
    );
    private final JRadioButton a4Radio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_PAGE_SIZE_A4)
    );

    // General tab — Measurement Units section
    private final JRadioButton inchesRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_UNITS_INCHES)
    );
    private final JRadioButton centimetersRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_UNITS_CENTIMETERS)
    );

    // General tab — Appearance section
    private final JRadioButton systemRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_APPEARANCE_SYSTEM)
    );
    private final JRadioButton lightRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_APPEARANCE_LIGHT)
    );
    private final JRadioButton darkRadio = new JRadioButton(
        Strings.get(Strings.LABEL_PREFS_APPEARANCE_DARK)
    );

    // Instruments tab
    private final JTabbedPane tabbedPane;
    private final JList<String> instrumentList;
    private final JButton scaleButton;
    private final ScaleAction scaleAction;

    public PreferencesDialog() {
        super(Strings.get(Strings.DIALOG_PREFERENCES_TITLE), false);

        var pageSizeGroup = new ButtonGroup();
        pageSizeGroup.add(letterRadio);
        pageSizeGroup.add(a4Radio);

        var unitsGroup = new ButtonGroup();
        unitsGroup.add(inchesRadio);
        unitsGroup.add(centimetersRadio);

        var appearanceGroup = new ButtonGroup();
        appearanceGroup.add(systemRadio);
        appearanceGroup.add(lightRadio);
        appearanceGroup.add(darkRadio);

        ensureInstrumentsLoaded();
        instrumentList = new JList<>(instrumentStrings);
        scaleAction = new ScaleAction();
        scaleButton = new JButton(scaleAction);

        var instrumentsTabIndex = 2;
        tabbedPane = createTabbedPane();
        tabbedPane.addTab(
            Strings.get(Strings.LABEL_PREFS_TAB_GENERAL),
            new GeneralTab()
        );
        tabbedPane.addTab(
            Strings.get(Strings.LABEL_PREFS_TAB_PLAY),
            new PlayTab()
        );
        tabbedPane.addTab(
            Strings.get(Strings.LABEL_PREFS_TAB_INSTRUMENTS),
            new InstrumentsTab()
        );

        tabbedPane.addChangeListener(_ -> {
            if (tabbedPane.getSelectedIndex() == instrumentsTabIndex) {
                PlaybackController.stop();
                instrumentList.requestFocusInWindow();
            } else {
                scaleAction.stop();
            }
        });

        contentPanel.add(BorderLayout.CENTER, tabbedPane);

        addChangeListeners();
    }

    @Override
    protected int getExtraWidth() {
        return EXTRA_WIDTH;
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            scaleAction.stop();
        }

        super.setVisible(visible);
    }

    public static void ensureInstrumentsLoaded() {
        if (instrumentsLoaded) {
            return;
        }

        instrumentsLoaded = true;

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
        instrumentStrings = pairs.stream().map(Map.Entry::getKey).toArray(String[]::new);
        instrumentPrograms = pairs.stream().mapToInt(Map.Entry::getValue).toArray();
    }

    public static int programToIndex(int program) {
        ensureInstrumentsLoaded();

        for (var i = 0; i < instrumentPrograms.length; i++) {
            if (instrumentPrograms[i] == program) {
                return i;
            }
        }

        return 0;
    }

    public static String[] getInstrumentStrings() {
        ensureInstrumentsLoaded();
        return instrumentStrings;
    }

    public static int[] getInstrumentPrograms() {
        ensureInstrumentsLoaded();
        return instrumentPrograms;
    }

    private void addChangeListeners() {
        // Feedback
        playInsertingNoteCheck.addActionListener(_ -> {
            Prefs.getInstance().put(
                PrefsKey.PLAY_INSERTED_NOTE, playInsertingNoteCheck.isSelected()
            );
            syncPlaybackPrefs();
        });

        playSelectedNoteCheck.addActionListener(_ -> {
            Prefs.getInstance().put(
                PrefsKey.PLAY_SELECTED_NOTE, playSelectedNoteCheck.isSelected()
            );
        });

        // Playback — use putTransient during drag to avoid disk writes per tick,
        // then save once on release.
        durationSlider.addChangeListener(_ -> {
            var prefs = Prefs.getInstance();
            prefs.putTransient(PrefsKey.PLAYBACK_NOTE_DURATION, durationSlider.getValue());

            if (!durationSlider.getValueIsAdjusting()) {
                prefs.save();
            }

            syncPlaybackPrefs();
        });

        volumeSlider.addChangeListener(_ -> {
            var prefs = Prefs.getInstance();
            prefs.putTransient(PrefsKey.PLAYBACK_VOLUME, volumeSlider.getValue());
            MidiController.setPlaybackVolume(volumeSlider.getValue());

            if (!volumeSlider.getValueIsAdjusting()) {
                prefs.save();
            }
        });

        tempoSlider.addChangeListener(_ -> {
            var prefs = Prefs.getInstance();
            prefs.putTransient(PrefsKey.TEMPO_CHANGE_PERCENT, tempoSlider.getValue());

            if (!tempoSlider.getValueIsAdjusting()) {
                prefs.save();
            }

            syncPlaybackPrefs();
        });

        // Instruments
        instrumentList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }

            var index = instrumentList.getSelectedIndex();
            Prefs.getInstance().put(
                PrefsKey.INSTRUMENT, index >= 0 ? instrumentPrograms[index] : 0
            );
            syncPlaybackPrefs();

            // Restart scale if it was already playing
            if (scaleAction.isPlaying()) {
                scaleAction.stop();
                scaleAction.play();
            }
        });

        // Page Size
        var pageSizeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Prefs.getInstance().put(PrefsKey.PAGE_SIZE, a4Radio.isSelected() ? "a4" : "letter");
                MessageCenter.post(new PageSizeDidChangeNotification());
            }
        };

        letterRadio.addActionListener(pageSizeListener);
        a4Radio.addActionListener(pageSizeListener);

        // Measurement Units
        var metricListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Prefs.getInstance().put(PrefsKey.METRIC, centimetersRadio.isSelected());
            }
        };

        inchesRadio.addActionListener(metricListener);
        centimetersRadio.addActionListener(metricListener);

        // Appearance
        var appearanceListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Appearance newAppearance;

                if (darkRadio.isSelected()) {
                    newAppearance = Appearance.DARK;
                } else if (lightRadio.isSelected()) {
                    newAppearance = Appearance.LIGHT;
                } else {
                    newAppearance = Appearance.SYSTEM;
                }

                AppearanceManager.switchTheme(newAppearance);
            }
        };

        systemRadio.addActionListener(appearanceListener);
        lightRadio.addActionListener(appearanceListener);
        darkRadio.addActionListener(appearanceListener);
    }

    private void syncPlaybackPrefs() {
        var score = getScore();

        if (score != null) {
            score.syncPlaybackPrefs();
        }
    }

    @Override
    protected boolean getData() {
        tabbedPane.setSelectedIndex(0);
        var prefs = Prefs.getInstance();

        // General — Page Size
        (PageModel.getInstance().getSize() == PageModel.Size.A4 ? a4Radio : letterRadio).setSelected(true);

        // General — Measurement Units
        (prefs.getBoolean(PrefsKey.METRIC) ? centimetersRadio : inchesRadio).setSelected(true);

        // General — Appearance
        (switch (AppearanceManager.getPreference()) {
            case LIGHT -> lightRadio;
            case DARK -> darkRadio;
            case SYSTEM -> systemRadio;
        }).setSelected(true);

        // Play — Feedback
        playInsertingNoteCheck.setSelected(prefs.getBoolean(PrefsKey.PLAY_INSERTED_NOTE));
        playSelectedNoteCheck.setSelected(prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE));

        // Play — Playback
        durationSlider.setValue(snapToNearestDurationStop(prefs.getInt(PrefsKey.PLAYBACK_NOTE_DURATION)));
        volumeSlider.setValue(prefs.getInt(PrefsKey.PLAYBACK_VOLUME));
        tempoSlider.setValue(snapToNearestTempoStop(prefs.getInt(PrefsKey.TEMPO_CHANGE_PERCENT)));

        // Instruments
        var instrumentIndex = programToIndex(prefs.getInt(PrefsKey.INSTRUMENT));
        instrumentList.setSelectedIndex(instrumentIndex);
        instrumentList.ensureIndexIsVisible(instrumentIndex);

        return true;
    }

    private static int snapToNearest(int value, int[] stops) {
        int closest = stops[0];
        int minDist = Math.abs(value - closest);

        for (int i = 1; i < stops.length; i++) {
            int dist = Math.abs(value - stops[i]);

            if (dist < minDist) {
                minDist = dist;
                closest = stops[i];
            }
        }

        return closest;
    }

    private static int snapToNearestTempoStop(int value) {
        return snapToNearest(value, VALID_TEMPO_STOPS);
    }

    private static int snapToNearestDurationStop(int value) {
        return snapToNearest(value, VALID_DURATION_STOPS);
    }

    private static Dictionary<Integer, JLabel> createPercentLabels(
        int min, int max, int step
    ) {
        //noinspection UseOfObsoleteCollectionType
        var labels = new Hashtable<Integer, JLabel>();

        for (int value = min; value <= max; value += step) {
            labels.put(value, new JLabel(value + "%"));
        }

        return labels;
    }

    private final class GeneralTab extends Tab {

        @Override
        protected void initContents() {
            add(createPageSizeSection());
            addSeparator();
            add(createMeasurementUnitsSection());
            addSeparator();
            add(createAppearanceSection());
        }

        private JPanel createPageSizeSection() {
            var section = new TitledSection(Strings.get(Strings.LABEL_PREFS_SECTION_PAGE_SIZE));
            section.add(letterRadio);
            section.addSeparator();
            section.add(a4Radio);
            return section;
        }

        private JPanel createMeasurementUnitsSection() {
            var section = new TitledSection(
                Strings.get(Strings.LABEL_PREFS_SECTION_MEASUREMENT_UNITS)
            );
            section.add(inchesRadio);
            section.addSeparator();
            section.add(centimetersRadio);
            return section;
        }

        private JPanel createAppearanceSection() {
            var section = new TitledSection(Strings.get(Strings.LABEL_PREFS_SECTION_APPEARANCE));
            var row = new JPanel(new FlowLayout(FlowLayout.CENTER, APPEARANCE_ITEM_GAP, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(createAppearanceItem(
                GraphicUtils.getScaledSVGIcon(new FlatSVGIcon("icons/appearance-system.svg"), APPEARANCE_ICON_SIZE),
                systemRadio
            ));
            row.add(createAppearanceItem(
                GraphicUtils.getScaledSVGIcon(new FlatSVGIcon("icons/appearance-light.svg"), APPEARANCE_ICON_SIZE),
                lightRadio
            ));
            row.add(createAppearanceItem(
                GraphicUtils.getScaledSVGIcon(new FlatSVGIcon("icons/appearance-dark.svg"), APPEARANCE_ICON_SIZE),
                darkRadio
            ));
            section.add(row);
            return section;
        }

        private JPanel createAppearanceItem(Icon icon, JRadioButton radio) {
            var panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            var iconLabel = new JLabel(icon);
            iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            radio.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(iconLabel);
            panel.add(Box.createVerticalStrut(APPEARANCE_ICON_RADIO_GAP));
            panel.add(radio);
            return panel;
        }
    }

    private final class InstrumentsTab extends Tab {

        @Override
        protected void initContents() {
            var panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

            instrumentList.setAlignmentY(Component.CENTER_ALIGNMENT);
            instrumentList.setVisibleRowCount(10);
            instrumentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            panel.add(new JScrollPane(instrumentList));
            panel.add(Box.createHorizontalStrut(HORIZONTAL_SPACER.width * 2));

            scaleButton.setText("\uEF4E");
            scaleButton.setFont(MyFontUtils.getIconFont().deriveFont(24f));
            scaleButton.setMargin(new Insets(8, 8, 8, 8));
            scaleButton.setAlignmentY(Component.CENTER_ALIGNMENT);
            scaleButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playScale"
            );
            scaleButton.getActionMap().put("playScale", scaleAction);
            panel.add(scaleButton);

            add(panel);
        }
    }

    private final class PlayTab extends Tab {

        private static final int SLIDER_SPACING = 20;
        private static final int SLIDER_EXTRA_WIDTH = 50;

        @Override
        protected void initContents() {
            add(createFeedbackSection());
            addSeparator();
            add(createPlaybackSection());
        }

        private JPanel createFeedbackSection() {
            var section = new TitledSection(
                Strings.get(Strings.LABEL_PREFS_SECTION_FEEDBACK)
            );

            section.add(playInsertingNoteCheck);
            section.addSeparator();
            section.add(playSelectedNoteCheck);

            return section;
        }

        private JPanel createPlaybackSection() {
            var section = new TitledSection(
                Strings.get(Strings.LABEL_PREFS_SECTION_PLAYBACK)
            );

            var border = (StandardTitledBorder) section.getBorder();
            border.setInsets(new Insets(35, 20, 20, 20));

            //noinspection UseOfObsoleteCollectionType
            var durationLabels = new Hashtable<Integer, JLabel>();
            durationLabels.put(32, new JLabel(Strings.get(Strings.LABEL_PREFS_STACCATO)));
            durationLabels.put(66, new JLabel(Strings.get(Strings.LABEL_PREFS_NORMAL)));
            durationLabels.put(100, new JLabel(Strings.get(Strings.LABEL_PREFS_LEGATO)));
            addSlider(section, Strings.get(Strings.LABEL_PREFS_PLAYBACK_DURATION), durationSlider, DURATION_STEP, durationLabels);

            section.add(Box.createVerticalStrut(SLIDER_SPACING));
            section.add(new JSeparator());
            section.add(Box.createVerticalStrut(SLIDER_SPACING));

            //noinspection UseOfObsoleteCollectionType
            var volumeLabels = new Hashtable<Integer, JLabel>();
            volumeLabels.put(50, new JLabel(Strings.get(Strings.LABEL_PREFS_SOFTER)));
            volumeLabels.put(75, new JLabel(Strings.get(Strings.LABEL_PREFS_SOFT)));
            volumeLabels.put(100, new JLabel(Strings.get(Strings.LABEL_PREFS_FULL)));
            addSlider(section, Strings.get(Strings.LABEL_PREFS_PLAYBACK_VOLUME), volumeSlider, VOLUME_MAJOR_TICK, volumeLabels);

            section.add(Box.createVerticalStrut(SLIDER_SPACING));
            section.add(new JSeparator());
            section.add(Box.createVerticalStrut(SLIDER_SPACING));

            addSlider(
                section, Strings.get(Strings.LABEL_PREFS_PLAYBACK_TEMPO), tempoSlider,
                TEMPO_MAJOR_TICK, createPercentLabels(TEMPO_MIN, TEMPO_MAX, TEMPO_MAJOR_TICK)
            );

            return section;
        }

        private void addSlider(
            JPanel section,
            String labelText,
            JSlider slider,
            int tickSpacing,
            Dictionary<Integer, JLabel> labels
        ) {
            addLabeledField(section, labelText, slider, LabelPosition.TOP);
            configureSlider(slider, tickSpacing, labels);
            var size = slider.getPreferredSize();
            slider.setPreferredSize(new Dimension(size.width + SLIDER_EXTRA_WIDTH, size.height));
        }
    }

    private class ScaleAction extends AbstractAction {

        private @Nullable MetaEventListener endListener = null;

        private static final int SCALE_VELOCITY = 70;

        // Db major scale: Db4, Eb4, F4, Gb4, Ab4, Bb4, C5, Db5
        private static final int[] SCALE = new int[] {
            61, 63, 65, 66, 68, 70, 72, 73,
        };

        private @Nullable Color defaultButtonBackground;

        private boolean playing = false;

        ScaleAction() {
            putValue(SHORT_DESCRIPTION, Strings.get(Strings.TOOLTIP_PLAY_INSTRUMENT));
            setEnabled(MidiController.sequencer != null);
        }

        boolean isPlaying() {
            return playing;
        }

        void stop() {
            if (!playing) {
                return;
            }

            var seq = MidiController.sequencer;

            if (seq != null && seq.isRunning()) {
                seq.stop();
            }

            if (seq != null && endListener != null) {
                seq.removeMetaEventListener(endListener);
                endListener = null;
            }

            setScalePlaying(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var seq = MidiController.sequencer;

            if (seq == null) {
                return;
            }

            if (seq.isRunning()) {
                stop();
                return;
            }

            play();
        }

        void play() {
            var seq = MidiController.sequencer;

            if (seq == null) {
                return;
            }

            // Apply the user's volume preference
            PlaybackController.applyVolumeFromPrefs();

            // Stop score playback if it's running
            if (PlaybackController.getState() == PlaybackController.PlaybackState.PLAYING) {
                PlaybackController.stop();
            }

            try {
                var sequence = new Sequence(Sequence.PPQ, PlaybackController.PPQ, 0);
                var track = sequence.createTrack();
                var selectedIndex = instrumentList.getSelectedIndex();
                var program = selectedIndex >= 0 ? instrumentPrograms[selectedIndex] : 0;
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
                        SCALE_VELOCITY
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

                seq.setSequence(sequence);
                seq.setTickPosition(0);

                setScalePlaying(true);

                endListener = message -> {
                    if (message.getType() == MidiMetaMessageTypes.END_OF_TRACK) {
                        SwingUtilities.invokeLater(() -> {
                            seq.setTickPosition(0);
                            seq.start();
                        });
                    }
                };

                seq.addMetaEventListener(endListener);
                seq.start();
            } catch (InvalidMidiDataException ex) {
                Dialogs.showErrorMessage(
                    getMainFrame(),
                    Strings.get(Strings.DIALOG_TITLE_PLAYBACK_ERROR),
                    Strings.get(Strings.ERROR_SCALE_PLAY)
                );
            }
        }

        private void setScalePlaying(boolean scalePlaying) {
            playing = scalePlaying;

            if (defaultButtonBackground == null) {
                defaultButtonBackground = scaleButton.getBackground();
            }

            scaleButton.setBackground(
                scalePlaying
                    ? UIManager.getColor("ToggleButton.toolbar.selectedBackground")
                    : defaultButtonBackground
            );
            scaleButton.repaint();
        }
    }
}
