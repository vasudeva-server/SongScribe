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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.mutation.ElementField;
import songscribe.music.BeatChange;
import songscribe.music.Duration;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.DurationListCellRenderer;

public class BeatChangeDialog extends StandardDialog {

    private @Nullable StaffElement selectedElement = null;
    private @Nullable Line selectedLine = null;
    private final JButton removeButton;

    public BeatChangeDialog() {
        super(Strings.get(Strings.DIALOG_BEAT_CHANGE_TITLE));

        removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
        removeButton.addActionListener(_ -> {
            var element = selectedElement;
            var line = selectedLine;

            if (element == null || line == null) {
                throw new IllegalStateException("no element selected");
            }

            var elementIndex = line.getElementIndex(element);
            line.withModification(() -> line.modifyElement(
                elementIndex, ElementField.BEAT_CHANGE, () -> element.setBeatChange(null)
            ));
            setVisible(false);
        });

        buttonPanel.add(removeButton, 0);
        removeButton.setVisible(false);

        var tab = new BeatChangeTab();
        registerTab(tab);
        contentPanel.add(BorderLayout.CENTER, tab);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    protected boolean getData() {
        var score = requireScore();
        selectedElement = score.getSingleSelectedElement();
        selectedLine = score.getComposition().getLine(score.getSelectionCoordinator().getActiveLineIndex());

        var beatChange = selectedElement != null ? selectedElement.getBeatChange() : null;
        var addingBeatChange = beatChange == null;

        removeButton.setVisible(!addingBeatChange);
        okButton.setText(
            Strings.get(addingBeatChange ? Strings.LABEL_BUTTON_ADD : Strings.LABEL_BUTTON_MODIFY)
        );

        return super.getData();
    }

    private final class BeatChangeTab extends Tab {

        private final JComboBox<Duration> durationCombo =
            DurationListCellRenderer.createCombo(Duration.values());
        private final JComboBox<Duration> beatCombo =
            DurationListCellRenderer.createCombo(Duration.values());

        private BeatChangeTab() {
            build();
        }

        @Override
        protected void initContents() {
            var row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            var extraGap = FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP);
            row.add(durationCombo);
            row.add(Box.createHorizontalStrut(extraGap));
            row.add(new JLabel("="));
            row.add(Box.createHorizontalStrut(extraGap));
            row.add(beatCombo);
            add(row);
        }

        @Override
        protected boolean getData() {
            var element = selectedElement;

            if (element == null) {
                return true;
            }

            var beatChange = element.getBeatChange();

            if (beatChange != null) {
                durationCombo.setSelectedItem(beatChange.duration());
                beatCombo.setSelectedItem(beatChange.beat());
            } else {
                durationCombo.setSelectedItem(Duration.CROTCHET_DOTTED);
                beatCombo.setSelectedItem(Duration.CROTCHET);
            }

            return true;
        }

        @Override
        protected void setData() {
            var element = selectedElement;
            var line = selectedLine;

            if (element == null || line == null) {
                throw new IllegalStateException("no element selected");
            }

            var elementIndex = line.getElementIndex(element);
            line.withModification(() -> line.modifyElement(
                elementIndex,
                ElementField.BEAT_CHANGE,
                () -> element.setBeatChange(
                    new BeatChange(
                        (Duration) durationCombo.getSelectedItem(),
                        (Duration) beatCombo.getSelectedItem()
                    )
                )
            ));
        }
    }
}
