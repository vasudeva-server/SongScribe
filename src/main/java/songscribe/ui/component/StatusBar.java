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
    private static final double ACCIDENTAL_FONT_SIZE_FACTOR = 1.2;
    private static final int ACCIDENTAL_HORIZONTAL_PADDING_PX = 1;

    /** Chord-symbol accidental glyphs from Bravura, which sit on the text baseline without adjustment. */
    private enum Accidental {
        FLAT('\uED60'),
        DOUBLE_FLAT('\uED64'),
        SHARP('\uED62'),
        DOUBLE_SHARP('\uED63');

        private final char codepoint;

        Accidental(char codepoint) {
            this.codepoint = codepoint;
        }

        public String codePoint() {
            return String.valueOf(codepoint);
        }
    }

    private final JLabel baseLabel = new JLabel(EMPTY_CONTENT);

    // Bravura's font-wide ascent/descent are sized for a five-line staff, not a single
    // glyph, so JLabel's default ascent-based text layout places the glyph far above
    // where it should sit. Painting it manually at baseLabel's baseline instead avoids
    // relying on that metric.
    private final JLabel accidentalLabel = new JLabel() {
        @Override
        public Dimension getPreferredSize() {
            var preferredSize = super.getPreferredSize();
            return new Dimension(preferredSize.width, baseLabel.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var text = getText();

            if (text.isEmpty()) {
                return;
            }

            var g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setFont(getFont());
            g2d.setColor(getForeground());

            var insets = getInsets();
            var baseline = insets.top + baseLabel.getFontMetrics(baseLabel.getFont()).getAscent();
            g2d.drawString(text, insets.left, baseline);
        }
    };
    private final JLabel octaveDurationLabel = new JLabel();

    private @Nullable Accidental resolvedAccidental;

    public StatusBar() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(FlatLafProps.getColor(FlatLafKey.STATUS_BAR_BACKGROUND));
        setBorder(UIUtils.spacingBorder(FlatLafKey.STATUS_BAR_PADDING));

        accidentalLabel.setFont(MyFontUtils.getLocalFont("Bravura.otf",
            (float) (accidentalLabel.getFont().getSize() * ACCIDENTAL_FONT_SIZE_FACTOR)
        ));
        accidentalLabel.setBorder(BorderFactory.createEmptyBorder(
            0, ACCIDENTAL_HORIZONTAL_PADDING_PX * 2, 0, ACCIDENTAL_HORIZONTAL_PADDING_PX * 2
        ));

        var notePanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        notePanel.setOpaque(false);
        notePanel.add(baseLabel);
        notePanel.add(accidentalLabel);
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

        MessageCenter.subscribe(this);
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
            accidentalLabel.setText(resolvedAccidental.codePoint());
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
     * to a plain flat/sharp. DOUBLE_NATURAL is not reachable through normal note entry and
     * has no chord-symbol glyph, so it is treated like NATURAL.
     */
    private static @Nullable Accidental resolveAccidental(StaffElement.@Nullable Accidental accidental) {
        return switch (accidental) {
            case null -> null;
            case StaffElement.Accidental.NATURAL, StaffElement.Accidental.DOUBLE_NATURAL -> null;
            case StaffElement.Accidental.FLAT, StaffElement.Accidental.NATURAL_FLAT -> Accidental.FLAT;
            case StaffElement.Accidental.SHARP, StaffElement.Accidental.NATURAL_SHARP -> Accidental.SHARP;
            case StaffElement.Accidental.DOUBLE_FLAT -> Accidental.DOUBLE_FLAT;
            case StaffElement.Accidental.DOUBLE_SHARP -> Accidental.DOUBLE_SHARP;
        };
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
