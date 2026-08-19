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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Map;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.layout.PageModel;
import songscribe.midi.MidiEventFactory;
import songscribe.midi.MidiSequenceBuilder;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.prefs.StartupAction;
import songscribe.ui.Appearance;

import songscribe.ui.AppearanceManager;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.TickSlider;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.GraphicUtils;
import songscribe.util.LengthUnit;
import songscribe.util.MyFontUtils;
import songscribe.util.UIUtils;

/**
 * The non-modal preferences window. There is no OK/Cancel cycle and nothing to revert: each
 * control writes its {@link Prefs} key the moment it changes, and closing the window keeps
 * every change made while it was open. This dialog does not subscribe to {@link
 * songscribe.prefs.PrefsDidChangeNotification} — a {@code resetAll()} triggered elsewhere
 * while this dialog is open leaves its widgets showing stale values until it is reopened.
 */
public class PreferencesDialog extends BaseDialog {

    private static String[] instrumentStrings = new String[0];
    private static int[] instrumentPrograms = new int[0];
    private static boolean instrumentsLoaded = false;

    // The volume values the slider's five stops stand for. Unevenly spaced, which is why the
    // slider is built over stop indices instead and this maps between the two.
    private static final int[] VALID_VOLUME_STOPS = { 50, 63, 75, 88, 100 };

    public PreferencesDialog(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.DIALOG_PREFERENCES_TITLE), Modality.MODELESS, DialogCategory.EXCLUSIVE);

        var tabbedContent = createTabbedContent();
        addTab(new GeneralTab());
        addTab(new PlayTab());
        addTab(new InstrumentsTab());

        contentPanel.add(BorderLayout.CENTER, tabbedContent);
    }

    @Override
    protected int getExtraWidth() {
        return FlatLafProps.getInt(FlatLafKey.DIALOG_PREFERENCES_EXTRA_WIDTH);
    }

    public static void ensureInstrumentsLoaded() {
        if (instrumentsLoaded) {
            return;
        }

        instrumentsLoaded = true;

        var names = new ArrayList<String>(128);
        var programs = new ArrayList<Integer>(128);

        if (MidiController.synthesizer != null) {
            var synthesizer = MidiController.synthesizer;
            var count = 0;

            for (var instrument : synthesizer.getLoadedInstruments()) {
                names.add(instrument.getName());
                programs.add(instrument.getPatch().getProgram());
                count += 1;

                if (count == 128) {
                    break;
                }
            }
        }

        // Sort instruments alphabetically by name, keeping programs in sync
        var nameCount = names.size();
        var pairs = new ArrayList<Map.Entry<String, Integer>>(nameCount);

        for (var i = 0; i < nameCount; i++) {
            pairs.add(Map.entry(names.get(i), programs.get(i)));
        }

        pairs.sort(Map.Entry.comparingByKey());
        instrumentStrings = pairs.stream().map(Map.Entry::getKey).toArray(String[]::new);
        instrumentPrograms = pairs.stream().mapToInt(Map.Entry::getValue).toArray();
    }

    /**
     * @param program a MIDI program number
     * @return the index of {@code program} in {@link #getInstrumentPrograms()}, or 0 if no
     *     loaded instrument uses that program
     */
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

    /**
     * The volume slider position a stored playback volume lands on.
     *
     * <p>The slider runs over stop indices rather than volumes, because {@link TickSlider} draws
     * evenly spaced ticks and these five volumes are not evenly spaced. This is the mapping back.
     *
     * @param volume a stored playback volume, which need not be one of the five stop values
     * @return the slider position, with a tie going to the quieter stop — see
     *         {@link TickSlider#nearestStopIndex}
     */
    private static int volumeToSliderIndex(int volume) {
        return TickSlider.nearestStopIndex(VALID_VOLUME_STOPS, volume);
    }

    // -----------------------------------------------------------------------
    // GeneralTab
    // -----------------------------------------------------------------------

    private final class GeneralTab extends Tab {


        private final JRadioButton letterRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_PAGE_SIZE_LETTER)
        );
        private final JRadioButton a4Radio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_PAGE_SIZE_A4)
        );
        private final JRadioButton inchesRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_UNITS_INCHES)
        );
        private final JRadioButton centimetersRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_UNITS_CENTIMETERS)
        );
        private final JRadioButton systemRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_APPEARANCE_SYSTEM)
        );
        private final JRadioButton lightRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_APPEARANCE_LIGHT)
        );
        private final JRadioButton darkRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_APPEARANCE_DARK)
        );
        private final JRadioButton doNothingRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_STARTUP_ACTION_DO_NOTHING)
        );
        private final JRadioButton showFileChooserRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_STARTUP_ACTION_SHOW_FILE_CHOOSER)
        );
        private final JRadioButton openMostRecentRadio = new JRadioButton(
            Strings.get(Strings.LABEL_PREFS_STARTUP_ACTION_OPEN_MOST_RECENT)
        );

        GeneralTab() {
            super(Strings.get(Strings.LABEL_PREFS_TAB_GENERAL));
            build();
        }

        @Override
        protected void initContents() {
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

            var startupActionGroup = new ButtonGroup();
            startupActionGroup.add(doNothingRadio);
            startupActionGroup.add(showFileChooserRadio);
            startupActionGroup.add(openMostRecentRadio);

            add(createPageSizeAndUnitsRow());
            addSectionSeparator(this);
            add(createAppearanceSection());
            addSectionSeparator(this);
            add(createStartupActionSection());

            addChangeListeners();
        }

        @Override
        protected boolean getData() {
            (PageModel.getSize() == PageModel.Size.A4
                ? a4Radio : letterRadio).setSelected(true);

            var units = LengthUnit.INCHES;

            try {
                units = LengthUnit.valueOf(Prefs.getString(PrefsKey.UNITS));
            } catch (IllegalArgumentException ignored) {}

            (switch (units) {
                case INCHES -> inchesRadio;
                case CENTIMETERS -> centimetersRadio;
            }).setSelected(true);

            (switch (AppearanceManager.getPreference()) {
                case LIGHT -> lightRadio;
                case DARK -> darkRadio;
                case SYSTEM -> systemRadio;
            }).setSelected(true);

            var startupAction = StartupAction.DO_NOTHING;

            try {
                startupAction = StartupAction.valueOf(Prefs.getString(PrefsKey.STARTUP_ACTION));
            } catch (IllegalArgumentException ignored) {}

            (switch (startupAction) {
                case SHOW_FILE_CHOOSER -> showFileChooserRadio;
                case OPEN_MOST_RECENT -> openMostRecentRadio;
                case DO_NOTHING -> doNothingRadio;
            }).setSelected(true);

            return true;
        }

        private void addChangeListeners() {
            var pageSizeListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    var size = a4Radio.isSelected() ? PageModel.Size.A4 : PageModel.Size.LETTER;
                    Prefs.put(PrefsKey.PAGE_SIZE, size.key());
                }
            };

            letterRadio.addActionListener(pageSizeListener);
            a4Radio.addActionListener(pageSizeListener);

            var unitsListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    var units = centimetersRadio.isSelected() ? LengthUnit.CENTIMETERS : LengthUnit.INCHES;
                    Prefs.put(PrefsKey.UNITS, units.name());
                }
            };

            inchesRadio.addActionListener(unitsListener);
            centimetersRadio.addActionListener(unitsListener);

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

            var startupActionListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StartupAction action;

                    if (showFileChooserRadio.isSelected()) {
                        action = StartupAction.SHOW_FILE_CHOOSER;
                    } else if (openMostRecentRadio.isSelected()) {
                        action = StartupAction.OPEN_MOST_RECENT;
                    } else {
                        action = StartupAction.DO_NOTHING;
                    }

                    Prefs.put(PrefsKey.STARTUP_ACTION, action.name());
                }
            };

            doNothingRadio.addActionListener(startupActionListener);
            showFileChooserRadio.addActionListener(startupActionListener);
            openMostRecentRadio.addActionListener(startupActionListener);
        }

        private JPanel createPageSizeAndUnitsRow() {
            var panel = new JPanel(new GridLayout(
                1, 2, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP), 0
            ));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(createPageSizeSection());
            panel.add(createMeasurementUnitsSection());
            return panel;
        }

        private JPanel createPageSizeSection() {
            var section = new TitledSection(Strings.get(Strings.LABEL_PREFS_SECTION_PAGE_SIZE));
            section.add(letterRadio);
            addSeparator(section);
            section.add(a4Radio);
            return section;
        }

        private JPanel createMeasurementUnitsSection() {
            var section = new TitledSection(
                Strings.get(Strings.LABEL_PREFS_SECTION_MEASUREMENT_UNITS)
            );
            section.add(inchesRadio);
            addSeparator(section);
            section.add(centimetersRadio);
            return section;
        }

        private JPanel createAppearanceSection() {
            var section = new TitledSection(Strings.get(Strings.LABEL_PREFS_SECTION_APPEARANCE));
            var row = new JPanel(new FlowLayout(FlowLayout.CENTER, FlatLafProps.getInt(FlatLafKey.DIALOG_PREFERENCES_GENERAL_APPEARANCE_ITEM_GAP), 0));
            row.setBorder(UIUtils.spacingBorder(FlatLafKey.DIALOG_PREFERENCES_GENERAL_APPEARANCE_PADDING));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            var iconSize = FlatLafProps.getInt(FlatLafKey.DIALOG_PREFERENCES_GENERAL_APPEARANCE_ICON_SIZE);
            row.add(createAppearanceItem(
                GraphicUtils.getScaledSVGIcon(new FlatSVGIcon("icons/appearance-system.svg"), iconSize),
                systemRadio
            ));
            row.add(createAppearanceItem(
                GraphicUtils.getScaledSVGIcon(new FlatSVGIcon("icons/appearance-light.svg"), iconSize),
                lightRadio
            ));
            row.add(createAppearanceItem(
                GraphicUtils.getScaledSVGIcon(new FlatSVGIcon("icons/appearance-dark.svg"), iconSize),
                darkRadio
            ));
            section.add(row);
            return section;
        }

        private JPanel createStartupActionSection() {
            var section = new TitledSection(Strings.get(Strings.LABEL_PREFS_SECTION_STARTUP_ACTION));
            section.add(new JLabel(Strings.get(Strings.LABEL_PREFS_STARTUP_ACTION_PROMPT)));
            addSeparator(section);
            section.add(doNothingRadio);
            addSeparator(section);
            section.add(showFileChooserRadio);
            addSeparator(section);
            section.add(openMostRecentRadio);
            return section;
        }

        private JPanel createAppearanceItem(Icon icon, JRadioButton radio) {
            var panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            var iconLabel = new JLabel(icon);
            iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            iconLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    radio.doClick();
                }
            });
            radio.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(iconLabel);
            panel.add(Box.createVerticalStrut(FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_GAP)));
            panel.add(radio);
            return panel;
        }
    }

    // -----------------------------------------------------------------------
    // PlayTab
    // -----------------------------------------------------------------------

    private final class PlayTab extends Tab {

        private static final int[] VALID_VOLUME_INDICES = { 0, 1, 2, 3, 4 };
        private static final @Nullable String[] VOLUME_LABELS = {
            Strings.get(Strings.LABEL_PREFS_SOFTER), null,
            Strings.get(Strings.LABEL_PREFS_SOFT), null,
            Strings.get(Strings.LABEL_PREFS_FULL),
        };

        private static final int[] VALID_TEMPO_STOPS = { 50, 75, 100, 125, 150 };
        private static final @Nullable String[] TEMPO_LABELS = { "50%", "75%", "100%", "125%", "150%" };

        private static final int[] VALID_DURATION_STOPS = { 32, 49, 66, 83, 100 };
        private static final @Nullable String[] DURATION_LABELS = {
            Strings.get(Strings.LABEL_PREFS_STACCATO), null,
            Strings.get(Strings.LABEL_PREFS_NORMAL), null,
            Strings.get(Strings.LABEL_PREFS_LEGATO),
        };

        private final JCheckBox playInsertingNoteCheck = new JCheckBox(
            Strings.get(Strings.LABEL_PREFS_PLAY_INSERTED_NOTE)
        );
        private final JCheckBox playSelectedNoteCheck = new JCheckBox(
            Strings.get(Strings.LABEL_PREFS_PLAY_SELECTED_NOTE)
        );

        private final TickSlider durationSlider =
            new TickSlider(VALID_DURATION_STOPS, DURATION_LABELS) {
                @Override
                protected void tickDidChange(int tick) {
                    Prefs.put(PrefsKey.PLAYBACK_NOTE_DURATION, tick);
                }
            };

        private final TickSlider volumeSlider =
            new TickSlider(VALID_VOLUME_INDICES, VOLUME_LABELS) {
                @Override
                protected void tickDidChange(int tick) {
                    var volume = VALID_VOLUME_STOPS[tick];
                    Prefs.put(PrefsKey.PLAYBACK_VOLUME, volume);
                    MidiController.setPlaybackVolume(volume);
                }
            };

        private final TickSlider tempoSlider =
            new TickSlider(VALID_TEMPO_STOPS, TEMPO_LABELS) {
                @Override
                protected void tickDidChange(int tick) {
                    Prefs.put(PrefsKey.TEMPO_CHANGE_PERCENT, tick);
                }
            };

        PlayTab() {
            super(Strings.get(Strings.LABEL_PREFS_TAB_PLAY));
            build();
            addChangeListeners();
        }

        @Override
        protected void initContents() {
            add(createFeedbackSection());
            addSectionSeparator(this);
            add(createPlaybackSection());
        }

        @Override
        protected boolean getData() {
            playInsertingNoteCheck.setSelected(Prefs.getBoolean(PrefsKey.PLAY_INSERTED_NOTE));
            playSelectedNoteCheck.setSelected(Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE));

            durationSlider.setSnappedValue(Prefs.getInt(PrefsKey.PLAYBACK_NOTE_DURATION));
            volumeSlider.setSnappedValue(volumeToSliderIndex(Prefs.getInt(PrefsKey.PLAYBACK_VOLUME)));
            tempoSlider.setSnappedValue(Prefs.getInt(PrefsKey.TEMPO_CHANGE_PERCENT));

            return true;
        }

        private void addChangeListeners() {
            playInsertingNoteCheck.addActionListener(_ -> Prefs.put(
                PrefsKey.PLAY_INSERTED_NOTE, playInsertingNoteCheck.isSelected()
            ));

            playSelectedNoteCheck.addActionListener(_ -> Prefs.put(
                PrefsKey.PLAY_SELECTED_NOTE, playSelectedNoteCheck.isSelected()
            ));
        }

        private JPanel createFeedbackSection() {
            var section = new TitledSection(
                Strings.get(Strings.LABEL_PREFS_SECTION_FEEDBACK)
            );

            section.add(playInsertingNoteCheck);
            addSeparator(section);
            section.add(playSelectedNoteCheck);

            return section;
        }

        private JPanel createPlaybackSection() {
            var section = new TitledSection(
                Strings.get(Strings.LABEL_PREFS_SECTION_PLAYBACK)
            );

            var border = (StandardTitledBorder) section.getBorder();
            border.setInsets(FlatLafProps.getInsets(FlatLafKey.DIALOG_PREFERENCES_PLAY_PLAYBACK_PADDING));

            addSliderRow(section, Strings.LABEL_PREFS_PLAYBACK_DURATION, durationSlider);
            addSliderRow(section, Strings.LABEL_PREFS_PLAYBACK_VOLUME, volumeSlider);
            addSliderRow(section, Strings.LABEL_PREFS_PLAYBACK_TEMPO, tempoSlider);

            return section;
        }

        private void addSliderRow(TitledSection section, String labelKey, TickSlider slider) {
            var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_PREFERENCES_PLAY_SLIDER_GAP);

            if (section.getComponentCount() > 0) {
                section.add(Box.createVerticalStrut(gap));
                section.add(new JSeparator());
                section.add(Box.createVerticalStrut(gap));
            }

            addLabeledField(section, Strings.get(labelKey), slider, LabelPosition.TOP);
        }

    }

    // -----------------------------------------------------------------------
    // InstrumentsTab
    // -----------------------------------------------------------------------

    private final class InstrumentsTab extends Tab {

        private final JList<String> instrumentList = new JList<>();
        private final ScaleAction scaleAction = new ScaleAction();
        private final JButton scaleButton = new JButton(scaleAction);

        InstrumentsTab() {
            super(Strings.get(Strings.LABEL_PREFS_TAB_INSTRUMENTS));
            build();

            addChangeListener();
            addClickListener();
        }

        @Override
        protected void initContents() {
            var panel = new JPanel(new GridBagLayout());
            var gc = new GridBagConstraints();

            var selectHintLabel = new JLabel(Strings.get(Strings.LABEL_PREFS_INSTRUMENT_SELECT_HINT));
            gc.gridx = 0;
            gc.gridy = 0;
            gc.weightx = 0.5;
            gc.weighty = 0;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.WEST;
            var horizontalGap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP);
            gc.insets = new Insets(0, horizontalGap, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_GAP), 0);
            panel.add(selectHintLabel, gc);

            ensureInstrumentsLoaded();
            instrumentList.setVisibleRowCount(instrumentStrings.length);
            instrumentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            gc.gridx = 0;
            gc.gridy = 1;
            gc.weightx = 0.5;
            gc.weighty = 0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.insets = new Insets(0, 0, 0, 0);
            panel.add(new JScrollPane(instrumentList), gc);

            scaleButton.setText("\uEF4E");
            scaleButton.setFont(MyFontUtils.getIconFont().deriveFont(FlatLafProps.getFloat(FlatLafKey.DIALOG_PREFERENCES_INSTRUMENTS_PLAY_BUTTON_SIZE)));
            scaleButton.setMargin(FlatLafProps.getInsets(FlatLafKey.DIALOG_PREFERENCES_INSTRUMENTS_PLAY_BUTTON_PADDING));
            UIUtils.setToolTipText(scaleButton, scaleAction);

            var spaceKey = (KeyStroke) scaleAction.getValue(Action.ACCELERATOR_KEY);
            scaleButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(spaceKey, "playScale");
            scaleButton.getActionMap().put("playScale", scaleAction);

            var scaleButtonRow = new JPanel();
            scaleButtonRow.setLayout(new BoxLayout(scaleButtonRow, BoxLayout.X_AXIS));
            scaleButtonRow.add(scaleButton);
            var extraGap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP);
            scaleButtonRow.add(Box.createHorizontalStrut(extraGap));
            scaleButtonRow.add(new JLabel(Strings.get(Strings.LABEL_PREFS_PLAY_SCALE)));

            gc.gridx = 1;
            gc.weightx = 0.5;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.WEST;
            var buttonGap = FlatLafProps.getInt(FlatLafKey.DIALOG_PREFERENCES_INSTRUMENTS_BUTTON_GAP);
            gc.insets = new Insets(0, buttonGap, 0, 0);
            panel.add(scaleButtonRow, gc);

            add(panel, constraints);
        }

        /** Plays a single note with the selected instrument, unless the scale is currently playing. */
        private void playSingleNoteIfNotScalePlaying() {
            if (scaleAction.isPlaying()) {
                return;
            }

            new PlayThread(ScaleAction.SINGLE_NOTE_PITCH).start();
        }

        @Override
        protected void tabWillShow() {
            PlaybackController.stop();

            ensureInstrumentsLoaded();

            var instrumentIndex = programToIndex(
                Prefs.getInt(PrefsKey.INSTRUMENT)
            );
            instrumentList.setListData(instrumentStrings);
            instrumentList.setSelectedIndex(instrumentIndex);
            instrumentList.ensureIndexIsVisible(instrumentIndex);
        }

        /**
         * The instrument list leads this tab. Declared rather than requested from
         * {@link #tabWillShow}, which runs before the window exists when the dialog is
         * opening — {@code requestFocusInWindow} is a no-op there, so the list used to take
         * focus only when the user switched to this tab in an already-open dialog.
         * {@link BaseDialog} reads this on both paths and defers the request until the
         * window is up.
         */
        @Override
        protected JComponent getInitialFocus() {
            return instrumentList;
        }

        @Override
        protected void tabWillHide() {
            scaleAction.stop();
        }

        private void addChangeListener() {
            instrumentList.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) {
                    return;
                }

                var index = instrumentList.getSelectedIndex();
                Prefs.put(
                    PrefsKey.INSTRUMENT, index >= 0 ? instrumentPrograms[index] : 0
                );

                if (scaleAction.isPlaying()) {
                    // Restart scale if it was already playing
                    scaleAction.stop();
                    scaleAction.play();
                } else {
                    playSingleNoteIfNotScalePlaying();
                }
            });
        }

        /** ListSelectionListener doesn't fire when clicking an already-selected item, so play it here instead. */
        private void addClickListener() {
            instrumentList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    var index = instrumentList.locationToIndex(e.getPoint());

                    if (index >= 0 && index == instrumentList.getSelectedIndex()) {
                        if (scaleAction.isPlaying()) {
                            scaleAction.stop();
                            scaleAction.play();
                        } else {
                            playSingleNoteIfNotScalePlaying();
                        }
                    }
                }
            });
        }

        private class ScaleAction extends AbstractAction {

            private @Nullable MetaEventListener endListener = null;

            private static final int SCALE_VELOCITY = 70;
            private static final int SCALE_TEMPO_BPM = 120;

            // Db4, played when the user selects an instrument in the list
            static final int SINGLE_NOTE_PITCH = 61;

            // Db major scale: Db4, Eb4, F4, Gb4, Ab4, Bb4, C5, Db5
            private static final int[] SCALE = new int[] {
                SINGLE_NOTE_PITCH, 63, 65, 66, 68, 70, 72, 73,
            };

            private @Nullable Color defaultButtonBackground;

            private boolean playing = false;

            ScaleAction() {
                putValue(NAME, Strings.get(Strings.TOOLTIP_PLAY_INSTRUMENT_TITLE));
                putValue(SHORT_DESCRIPTION, Strings.get(Strings.TOOLTIP_PLAY_INSTRUMENT));
                putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
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

                // Stop score playback if it's running. Waiting matters here: stopping a
                // sequencer is asynchronous, and this method loads and starts a sequence of
                // its own on that same sequencer a few lines below.
                if (PlaybackController.isPlaying()) {
                    PlaybackController.stopAndAwaitSequencer();
                }

                try {
                    var selectedIndex = instrumentList.getSelectedIndex();
                    var program = selectedIndex >= 0 ? instrumentPrograms[selectedIndex] : 0;

                    seq.setSequence(buildScaleSequence(program));
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
                    OptionDialogs.showErrorMessage(
                        getMainFrame(),
                        Strings.ALERT_TITLE_PLAYBACK_ERROR,
                        Strings.ERROR_SCALE_PLAY
                    );
                }
            }

            /**
             * Builds a one-track MIDI sequence that selects {@code program} and then plays
             * {@link #SCALE} at {@value #SCALE_TEMPO_BPM} BPM, each note held for half a beat.
             *
             * @param program the MIDI program (instrument) to select before the scale plays
             * @return a new, unplayed {@link Sequence} ready to hand to a {@link
             *     javax.sound.midi.Sequencer}
             * @throws InvalidMidiDataException if a MIDI event in the sequence is malformed
             */
            private static Sequence buildScaleSequence(int program) throws InvalidMidiDataException {
                var sequence = new Sequence(Sequence.PPQ, MidiSequenceBuilder.PPQ, 0);
                var track = sequence.createTrack();
                var programChange = new ShortMessage();
                programChange.setMessage(
                    ShortMessage.PROGRAM_CHANGE,
                    program,
                    0
                );
                track.add(new MidiEvent(programChange, 0));
                MidiEventFactory.addTempoEvent(track, 0, SCALE_TEMPO_BPM);

                var ticks = 0;

                for (var pitch : SCALE) {
                    var down = new ShortMessage();
                    down.setMessage(
                        ShortMessage.NOTE_ON,
                        pitch,
                        SCALE_VELOCITY
                    );
                    track.add(new MidiEvent(down, ticks));

                    ticks += MidiSequenceBuilder.PPQ / 2;
                    var up = new ShortMessage();
                    up.setMessage(
                        ShortMessage.NOTE_OFF,
                        pitch,
                        0
                    );
                    track.add(new MidiEvent(up, ticks));
                }

                return sequence;
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
}
