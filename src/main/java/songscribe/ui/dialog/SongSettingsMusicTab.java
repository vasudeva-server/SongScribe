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

import java.util.List;
import javax.swing.JPanel;

import songscribe.Strings;
import songscribe.dom.Tempo;
import songscribe.util.UIUtils;

/**
 * The {@link SongSettingsDialog} Music tab: the song's tempo.
 *
 * <p>There is no key control here: a song has no key of its own.
 *
 * <p>Nothing on this tab can be wrong: every state its controls can reach describes a tempo, so
 * it has no {@code InputVerifier} and contributes nothing to validation.
 */
final class SongSettingsMusicTab extends BaseDialog.Tab {

    private final TempoSection tempoSection;

    SongSettingsMusicTab(SongSettingsDialog dialog) {
        dialog.super(Strings.get(Strings.DIALOG_SONG_SETTINGS_TAB_MUSIC));

        tempoSection = new TempoSection(bindings(), List.of("tempos"));
        build();
    }

    @Override
    protected void initContents() {
        add(createTempoSection(), constraints);
    }

    private JPanel createTempoSection() {
        var section = new BaseDialog.TitledSection(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_TEMPO)
        );

        section.add(tempoSection);
        UIUtils.setFlexibleWidth(section);
        return section;
    }

    /**
     * Whatever is put in comes back out: {@link #gather()} called straight afterwards, with
     * nothing else touched, answers the same tempo.
     */
    void populate(SongSettingsInput input) {
        tempoSection.setTempo(input.tempo());
    }

    Tempo gather() {
        return tempoSection.getTempo();
    }
}
