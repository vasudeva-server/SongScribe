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
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.music.BeatChange;
import songscribe.ui.OptionDialogs;
import songscribe.music.StaffElement;
import songscribe.ui.renderer.BeatChangeRenderer;

public class BeatChangeDialog extends StandardDialog {

    private @Nullable StaffElement selectedElement = null;
    private final JButton removeButton;
    private final ButtonGroup bg;

    public BeatChangeDialog() {
        super(Strings.get(Strings.DIALOG_BEAT_CHANGE_TITLE));
        var center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bg = new ButtonGroup();

        for (var bc : BeatChange.values()) {
            var rb = new JRadioButton();
            rb.setActionCommand(bc.name());
            bg.add(rb);
            var panel = new JPanel();
            panel.add(rb);
            var component = getBeatChangeComponent(bc);
            component.addMouseListener(
                new MouseListener() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        bg.setSelected(rb.getModel(), true);
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                    }
                }
            );
            panel.add(component);
            center.add(panel);
        }

        contentPanel.add(center);

        //----------------------south------------------------
        var south = new JPanel();
        removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
        removeButton.addActionListener(_ -> {
            var score = Objects.requireNonNull(getScore());
            Objects.requireNonNull(selectedElement).setBeatChange(null);
            setVisible(false);
            score.repaint();
            score.getComposition().setModified(true);
        });
        south.add(okButton);
        south.add(removeButton);
        south.add(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, south);
    }

    @Override
    protected boolean getData() {
        var score = Objects.requireNonNull(getScore());
        selectedElement = score.getSingleSelectedElement();
        BeatChange beatChange = null;

        if (selectedElement != null) {
            beatChange = selectedElement.getBeatChange();
        }

        var bcnull = beatChange == null;

        if (beatChange != null) {
            for (var elements = bg.getElements(); elements.hasMoreElements(); ) {
                var element = elements.nextElement();

                if (element.getActionCommand().equals(beatChange.name())) {
                    bg.setSelected(element.getModel(), true);
                }
            }
        }

        removeButton.setEnabled(!bcnull);

        if (bcnull) {
            okButton.setText(Strings.get(Strings.LABEL_BUTTON_ADD));
        } else {
            okButton.setText(Strings.get(Strings.LABEL_BUTTON_MODIFY));
        }

        return true;
    }

    @Override
    protected void setData() {
        if (bg.getSelection() == null) {
            OptionDialogs.showErrorMessage(
                getMainFrame(),
                Strings.get(Strings.DIALOG_TITLE_BEAT_CHANGE_ERROR),
                Strings.get(Strings.ERROR_BEAT_CHANGE_NO_SELECTION)
            );
            return;
        }

        Objects.requireNonNull(selectedElement).setBeatChange(
            BeatChange.valueOf(bg.getSelection().getActionCommand())
        );
    }

    private JComponent getBeatChangeComponent(BeatChange beatChange) {
        return new JComponent() {
            private final Dimension size = new Dimension(50, 30);

            @Override
            public Dimension getPreferredSize() {
                return size;
            }

            @Override
            public Dimension getSize() {
                return size;
            }

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints
                // .VALUE_ANTIALIAS_ON);
                // g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints
                // .VALUE_TEXT_ANTIALIAS_ON);
                var composition = getComposition();
                BeatChangeRenderer.getInstance().drawBeatChange(g2, beatChange, 2, 27, composition);
            }
        };
    }
}
