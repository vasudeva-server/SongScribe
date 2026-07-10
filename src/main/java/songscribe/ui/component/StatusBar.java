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

import module java.desktop;

import net.engio.mbassy.listener.Handler;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.io.musicxml.PitchSpelling;
import songscribe.layout.NoteGeometry;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.util.MyFontUtils;
import songscribe.util.StringUtils;
import songscribe.util.UIUtils;

/**
 * Status bar pinned at the bottom of the main window. In insert mode, displays
 * a plain-language description of the current preview element (the element
 * that will be inserted next), left-aligned across the bar.
 */
public final class StatusBar extends JComponent {

    private static final String EMPTY_CONTENT = " ";
    private static final double ACCIDENTAL_FONT_SIZE_FACTOR = 1.75;

    // Hand-tuned pixel offsets that drop each accidental glyph onto the text
    // baseline. They are specific to the Bravura Text face at
    // ACCIDENTAL_FONT_SIZE_FACTOR; deriving them from font metrics is fiddly and
    // prone to rounding error, so they are tuned by eye.
    private static final int FLAT_BASELINE_SHIFT_PX = 6;
    private static final int SHARP_BASELINE_SHIFT_PX = 5;
    private static final int ACCIDENTAL_HORIZONTAL_PADDING_PX = 1;

    private final JLabel baseLabel = new JLabel(EMPTY_CONTENT);
    private final JLabel accidentalLabel = new JLabel() {
        @Override
        public Dimension getPreferredSize() {
            var preferredSize = super.getPreferredSize();
            return new Dimension(preferredSize.width, baseLabel.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var shiftedGraphics = g.create();

            try {
                shiftedGraphics.translate(0, getBaselineShiftPx());
                super.paintComponent(shiftedGraphics);
            } finally {
                shiftedGraphics.dispose();
            }
        }
    };
    private final JLabel octaveDurationLabel = new JLabel();

    private StaffElement.@Nullable Accidental resolvedAccidental;

    public StatusBar() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(FlatLafProps.getColor(FlatLafKey.STATUS_BAR_BACKGROUND));
        setBorder(UIUtils.spacingBorder(FlatLafKey.STATUS_BAR_PADDING));

        accidentalLabel.setFont(MyFontUtils.getLocalFont("BravuraText.otf",
            (float) (accidentalLabel.getFont().getSize() * ACCIDENTAL_FONT_SIZE_FACTOR)
        ));
        accidentalLabel.setBorder(BorderFactory.createEmptyBorder(
            0, ACCIDENTAL_HORIZONTAL_PADDING_PX, 0, ACCIDENTAL_HORIZONTAL_PADDING_PX
        ));

        var notePanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        notePanel.setOpaque(false);
        notePanel.add(baseLabel);
        notePanel.add(accidentalLabel);
        notePanel.add(octaveDurationLabel);
        add(notePanel, BorderLayout.CENTER);

        MessageCenter.subscribe(this);
    }

    private int getBaselineShiftPx() {
        return switch (resolvedAccidental) {
            case StaffElement.Accidental.FLAT, StaffElement.Accidental.DOUBLE_FLAT -> FLAT_BASELINE_SHIFT_PX;
            case StaffElement.Accidental.SHARP -> SHARP_BASELINE_SHIFT_PX;
            case null, default -> 0;
        };
    }

    @Handler
    public void previewElementDidChange(PreviewElementDidChangeNotification message) {
        setContent(message.getPreviewElement(), message.getLine(), message.getIndex());
    }

    private void setContent(@Nullable StaffElement previewElement, @Nullable Line line, int index) {
        // Clear the bar unless the element was posted with its line context: a null
        // line means there is no positioned element to describe. The check also lets
        // NullAway know line is non-null where it is passed below.
        if (previewElement == null || line == null) {
            baseLabel.setText(EMPTY_CONTENT);
            clearNoteDetails();
            return;
        }

        var type = previewElement.getType();

        if (type.isRest()) {
            baseLabel.setText(getDurationText(previewElement));
            clearNoteDetails();
            return;
        }

        if (type.isNote()) {
            setNoteContent(previewElement, line, index);
            return;
        }

        baseLabel.setText(EMPTY_CONTENT);
        clearNoteDetails();
    }

    private void clearNoteDetails() {
        resolvedAccidental = null;
        accidentalLabel.setVisible(false);
        octaveDurationLabel.setVisible(false);
    }

    private void setNoteContent(StaffElement note, Line line, int index) {
        var pitch = PitchSpelling.spell(note.getStaffPosition());
        baseLabel.setText(String.valueOf(pitch.step()));

        var accidental = note.getAccidental();

        // If the note has no accidental, resolve it to the effective accidental at this point.
        if (accidental == null) {
            accidental = note.findEffectiveAccidental(line, index);
        }

        resolvedAccidental = resolveAccidental(accidental);

        if (resolvedAccidental == null) {
            accidentalLabel.setVisible(false);
        } else {
            accidentalLabel.setText(getAccidentalGlyphs(resolvedAccidental));
            accidentalLabel.setVisible(true);
        }

        octaveDurationLabel.setText(
            Strings.get(Strings.STATUS_BAR_OCTAVE_DURATION, pitch.octave(), getDurationText(note))
        );
        octaveDurationLabel.setVisible(true);
    }

    /**
     * Resolves an accidental to the form actually sounded, so a natural component never
     * appears: a plain natural is not displayed at all, and natural flat/sharp collapse
     * to a plain flat/sharp.
     */
    private static StaffElement.@Nullable Accidental resolveAccidental(StaffElement.@Nullable Accidental accidental) {
        return switch (accidental) {
            case null -> null;
            case StaffElement.Accidental.NATURAL -> null;
            case StaffElement.Accidental.NATURAL_FLAT -> StaffElement.Accidental.FLAT;
            case StaffElement.Accidental.NATURAL_SHARP -> StaffElement.Accidental.SHARP;
            default -> accidental;
        };
    }

    private static String getAccidentalGlyphs(StaffElement.Accidental accidental) {
        var text = new StringBuilder();

        for (var glyph : NoteGeometry.getAccidentalComponents(accidental)) {
            text.append(glyph.asString());
        }

        return text.toString();
    }

    private static String getDurationText(StaffElement element) {
        var name = element.getType().getName();

        if (name == null) {
            return "";
        }

        return StringUtils.capitalizeSentence(getDotPrefix(element.getDotCount()) + name.toLowerCase());
    }

    private static String getDotPrefix(int dotCount) {
        return switch (dotCount) {
            case 1 -> Strings.get(Strings.STATUS_BAR_DURATION_DOTTED) + ' ';
            case 2 -> Strings.get(Strings.STATUS_BAR_DURATION_DOUBLE_DOTTED) + ' ';
            default -> "";
        };
    }
}
