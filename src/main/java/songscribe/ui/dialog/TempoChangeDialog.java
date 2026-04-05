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
import songscribe.music.StaffElement;
import songscribe.music.Tempo;

public class TempoChangeDialog extends StandardDialog {

    private @Nullable StaffElement selectedElement = null;
    private final JButton removeButton;

    public TempoChangeDialog() {
        super(Strings.get(Strings.DIALOG_TEMPO_CHANGE_TITLE));

        removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
        removeButton.addActionListener(_ -> {
            var score = requireScore();

            if (selectedElement == null) {
                throw new IllegalStateException("no element selected");
            }

            selectedElement.setTempoChange(null);
            setVisible(false);
            score.repaint();
            score.getComposition().setModified(true);
        });

        // Insert Remove before the standard Cancel and OK buttons
        buttonPanel.add(removeButton, 0);
        removeButton.setVisible(false);

        var tab = new TempoTab();
        registerTab(tab);
        contentPanel.add(BorderLayout.CENTER, tab);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    protected boolean getData() {
        var score = requireScore();
        selectedElement = score.getSingleSelectedElement();

        var tempoChange = selectedElement != null ? selectedElement.getTempoChange() : null;
        var addingTempoChange = tempoChange == null;

        removeButton.setVisible(!addingTempoChange);
        okButton.setText(
            Strings.get(addingTempoChange ? Strings.LABEL_BUTTON_ADD : Strings.LABEL_BUTTON_MODIFY)
        );

        return super.getData();
    }

    private final class TempoTab extends Tab {

        private final TempoSection tempoSection = new TempoSection(
            Tempo.Type.displayValues(),
            Strings.get(Strings.DIALOG_TEMPO_CHANGE_SHOW_ONLY_DESCRIPTION),
            "tempochanges", "tempos"
        );

        private TempoTab() {
            build();
        }

        @Override
        protected void initContents() {
            add(tempoSection);
        }

        @Override
        protected boolean getData() {
            var element = selectedElement;

            if (element == null) {
                return true;
            }

            var tempoChange = element.getTempoChange();
            tempoSection.setTempo(
                tempoChange != null
                    ? tempoChange
                    : new Tempo(144, Tempo.Type.CROTCHET, "Slower", true)
            );

            return true;
        }

        @Override
        protected void setData() {
            if (selectedElement == null) {
                throw new IllegalStateException("no element selected");
            }

            selectedElement.setTempoChange(
                new Tempo(
                    tempoSection.getVisibleTempo(),
                    tempoSection.getTempoType(),
                    tempoSection.getTempoDescription(),
                    !tempoSection.isShowOnlyDescription()
                )
            );
            getComposition().setModified(true);
        }
    }
}
