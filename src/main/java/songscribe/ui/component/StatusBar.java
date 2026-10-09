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

package songscribe.ui.component;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.BarElement;
import songscribe.dom.BreathMarkElement;
import songscribe.dom.DiatonicPitch;
import songscribe.dom.Dots;
import songscribe.dom.Dottable;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.RestElement;
import songscribe.dom.StaffElement;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.util.StringUtils;
import songscribe.util.UIUtils;

/**
 * Status bar pinned at the bottom of the main window. In insert mode, displays
 * a plain-language description of the current preview element (the element
 * that will be inserted next), left-aligned across the bar.
 */
public final class StatusBar extends JPanel {

    private static final String EMPTY_CONTENT = " ";

    private final JLabel baseLabel = new JLabel(EMPTY_CONTENT);
    private final JLabel octaveDurationLabel = new JLabel();

    public StatusBar() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(
            new ThemeAwareMatteBorder(1, 0, 0, 0, "ToolBar.separatorColor"),
            UIUtils.spacingBorder(FlatLafKey.STATUS_BAR_PADDING)
        ));

        var notePanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        notePanel.setOpaque(false);
        notePanel.add(baseLabel);
        notePanel.add(octaveDurationLabel);

        // BorderLayout stretches CENTER/LINE_END children to the bar's full height, but
        // FlowLayout only centers a row within its own natural height, leaving the shorter
        // panel's content pinned at the top. GridBagLayout's LINE_START/LINE_END anchors
        // center vertically while still packing each panel to its leading/trailing edge.
        var noteConstraints = new GridBagConstraints();
        noteConstraints.gridx = 0;
        noteConstraints.weightx = 1.0;
        noteConstraints.anchor = GridBagConstraints.LINE_START;
        add(notePanel, noteConstraints);

        var zoomConstraints = new GridBagConstraints();
        zoomConstraints.gridx = 1;
        zoomConstraints.anchor = GridBagConstraints.LINE_END;
        add(new ZoomStatusBarPanel(), zoomConstraints);

        MessageSubscription.addProcessListener(this);
    }

    @Override
    public void updateUI() {
        // setUI() (called by super) triggers updateUI() before our fields are initialized
        super.updateUI();

        setBackground(FlatLafProps.getColor(FlatLafKey.STATUS_BAR_BACKGROUND));
    }

    @Handler
    public void previewElementDidChange(PreviewElementDidChangeNotification message) {
        setContent(message.getPreviewElement(), message.getLine());
    }

    private void setContent(@Nullable StaffElement previewElement, @Nullable Line line) {
        // Clear the bar unless the element was posted with its line context: a null
        // line means there is no positioned element to describe.
        if (previewElement == null || line == null) {
            showWithoutNoteDetails(EMPTY_CONTENT);
            return;
        }

        switch (previewElement) {
            case RestElement rest -> showWithoutNoteDetails(getDurationText(rest));
            case NoteElement note -> setNoteContent(note);
            case BreathMarkElement _, BarElement _, KeyChangeElement _ -> showWithoutNoteDetails(EMPTY_CONTENT);
        }
    }

    private void showWithoutNoteDetails(String text) {
        baseLabel.setText(text);
        octaveDurationLabel.setVisible(false);
    }

    /**
     * Names the note by the letter, octave and duration of its staff position, never its
     * accidental: a preview note that adopts what is in force has no pitch until it is placed.
     */
    private void setNoteContent(NoteElement note) {
        var pitch = DiatonicPitch.at(note.getStaffPosition().value());
        baseLabel.setText(pitch.pitchClass().name());

        octaveDurationLabel.setText(
            Strings.get(Strings.STATUS_BAR_OCTAVE_DURATION, pitch.octave(), getDurationText(note))
        );
        octaveDurationLabel.setVisible(true);
    }

    private static <E extends StaffElement & Dottable> String getDurationText(E element) {
        return StringUtils.capitalizeSentence(
            getDotPrefix(element.dots()) + element.getType().label());
    }

    private static String getDotPrefix(Dots dots) {
        return switch (dots) {
            case NONE -> "";
            case ONE -> Strings.get(Strings.STATUS_BAR_DURATION_DOTTED) + ' ';
            case TWO -> Strings.get(Strings.STATUS_BAR_DURATION_DOUBLE_DOTTED) + ' ';
        };
    }
}
