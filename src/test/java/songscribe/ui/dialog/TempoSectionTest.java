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
import javax.swing.JCheckBox;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.binding.Bindings;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.dom.TempoMarking;

import static org.assertj.core.api.Assertions.assertThat;

class TempoSectionTest extends UnitTest {

    private static final String DESCRIPTION = "Poco a poco accelerando";
    private static final int BPM = 100;

    // The (none) row is the description combo's first, by OtherValueComboBox's contract.
    private static final int NONE_INDEX = 0;

    private static TempoSection tempoSection() {
        return new TempoSection(new Bindings(), List.of("tempos"));
    }

    /**
     * The section lays its controls out as its own children, so a test reaches one by type
     * rather than through an accessor the production code has no use for.
     *
     * @param section the section to search
     * @param type    the control's class; the description combo and the note-type combo are
     *                told apart by it, since only the first is an {@link OtherValueComboBox}
     * @return the one child of that type
     * @throws AssertionError when the section has no such child
     */
    private static <T> T childOfType(TempoSection section, Class<T> type) {
        for (var component : section.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
        }

        throw new AssertionError("The tempo section has no " + type.getSimpleName());
    }

    private static Tempo metronome(String description) {
        return new Tempo(BPM, Duration.CROTCHET, new TempoMarking.Metronome(description));
    }

    private static Tempo textOnly(String description) {
        return new Tempo(BPM, Duration.CROTCHET, new TempoMarking.TextOnly(description));
    }

    @Test
    void testSetTempoThenGetTempoRoundTrips() {
        var tempoSection = tempoSection();
        var tempo = new Tempo(BPM, Duration.QUAVER, new TempoMarking.TextOnly(DESCRIPTION));

        tempoSection.setTempo(tempo);
        var gathered = tempoSection.getTempo();

        assertThat(gathered.getVisibleTempo()).isEqualTo(tempo.getVisibleTempo());
        assertThat(gathered.getTempoType()).isEqualTo(tempo.getTempoType());
        assertThat(gathered.getMarking()).isEqualTo(tempo.getMarking());
    }

    @Test
    void testCheckBoxIsDisabledWhileTheDescriptionIsEmpty() {
        var tempoSection = tempoSection();
        var checkBox = childOfType(tempoSection, JCheckBox.class);

        tempoSection.setTempo(metronome(""));
        assertThat(checkBox.isEnabled()).isFalse();

        tempoSection.setTempo(metronome(DESCRIPTION));
        assertThat(checkBox.isEnabled()).isTrue();
    }

    @Test
    void testNoneRowIsBarredWhileTheCheckBoxIsChecked() {
        var tempoSection = tempoSection();
        var descriptionCombo = childOfType(tempoSection, OtherValueComboBox.class);

        tempoSection.setTempo(textOnly(DESCRIPTION));

        descriptionCombo.setSelectedIndex(NONE_INDEX);
        assertThat(descriptionCombo.getSelectedItem()).isEqualTo(DESCRIPTION);

        tempoSection.setTempo(metronome(DESCRIPTION));

        descriptionCombo.setSelectedIndex(NONE_INDEX);
        assertThat(descriptionCombo.getSelectedItem()).isEmpty();
    }
}
