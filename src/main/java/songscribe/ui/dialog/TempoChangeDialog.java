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
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.ui.component.MainFrame;

/**
 * A {@link TempoSection} in a dialog, for editing the tempo change on an element.
 *
 * <p>The dialog owns nothing but the section: every control, and every rule about which of them
 * is enabled, belongs to {@code TempoSection}, which the song settings use as well.
 */
public class TempoChangeDialog extends AttachmentDialog<Tempo> {

    final TempoSection tempoSection = new TempoSection(
        Duration.values(),
        Strings.get(Strings.DIALOG_TEMPO_CHANGE_SHOW_ONLY_DESCRIPTION),
        "tempochanges", "tempos"
    );

    public TempoChangeDialog(MainFrame mainFrame, AttachmentBackEnd<Tempo> backEnd) {
        super(mainFrame, Strings.get(Strings.DIALOG_TEMPO_CHANGE_TITLE), backEnd);
        contentPanel.add(BorderLayout.CENTER, tempoSection);
    }

    @Override
    protected void populateControls(@Nullable Tempo existingChange) {
        tempoSection.setTempo(existingChange != null ? existingChange : new Tempo());
    }

    @Override
    protected Tempo gather() {
        return new Tempo(
            tempoSection.getVisibleTempo(),
            tempoSection.getTempoType(),
            tempoSection.getTempoDescription(),
            // The checkbox asks whether to show the description alone; Tempo stores the opposite
            // question — whether the metronome mark is shown as well.
            !tempoSection.isShowOnlyDescription()
        );
    }
}
