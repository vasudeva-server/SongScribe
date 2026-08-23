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
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.ui.component.MainFrame;

/**
 * A {@link TempoSection} in a dialog, for editing the tempo change on an element.
 *
 * <p>The dialog owns nothing but the section, which the song settings use as well.
 */
public class TempoChangeDialog extends AttachmentDialog<Tempo> {

    private final TempoSection tempoSection;

    public TempoChangeDialog(MainFrame mainFrame, DialogOps<@Nullable Tempo, Tempo> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_TEMPO_CHANGE_TITLE), ops);

        tempoSection = new TempoSection(bindings(), List.of("tempochanges", "tempos"));
        contentPanel.add(BorderLayout.CENTER, tempoSection);
    }

    @Override
    protected void populateControls(@Nullable Tempo existingChange) {
        tempoSection.setTempo(existingChange != null ? existingChange : new Tempo());
    }

    @Override
    protected Tempo gather() {
        return tempoSection.getTempo();
    }
}
