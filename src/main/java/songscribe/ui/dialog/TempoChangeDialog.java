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
import songscribe.music.Duration;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;

public class TempoChangeDialog extends MetronomeChangeDialog<Tempo> {

    private final TempoSection tempoSection = new TempoSection(
        Duration.values(),
        Strings.get(Strings.DIALOG_TEMPO_CHANGE_SHOW_ONLY_DESCRIPTION),
        "tempochanges", "tempos"
    );

    public TempoChangeDialog() {
        super(Strings.get(Strings.DIALOG_TEMPO_CHANGE_TITLE));
        contentPanel.add(BorderLayout.CENTER, tempoSection);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    public static void showForElement(StaffElement element, Line line) {
        var dialog = new TempoChangeDialog();
        dialog.selectedElement = element;
        dialog.selectedLine = line;
        dialog.setVisible(true);
    }

    @Override
    protected ElementField getElementField() {
        return ElementField.TEMPO_CHANGE;
    }

    @Override
    protected @Nullable Tempo getExistingChange(StaffElement element) {
        return element.getTempoChange();
    }

    @Override
    protected void populateControls(@Nullable Tempo change) {
        tempoSection.setTempo(
            change != null
                ? change
                : new Tempo(120, Duration.CROTCHET, "Moderate", true)
        );
    }

    @Override
    protected void applyChange(StaffElement element) {
        element.setTempoChange(new Tempo(
            tempoSection.getVisibleTempo(),
            tempoSection.getTempoType(),
            tempoSection.getTempoDescription(),
            !tempoSection.isShowOnlyDescription()
        ));
    }

    @Override
    protected void clearChange(StaffElement element) {
        element.setTempoChange(null);
    }
}
