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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TempoSection}: setTempo/getters round-trip, null-selection guards.
 */
class TempoSectionTest extends UnitTest {

    private TempoSection section;

    @BeforeEach
    void setUp() {
        // Pass no file names so no resource reading is needed; the description combo starts empty.
        section = new TempoSection(Duration.values(), "Show only description");
    }

    // -- setTempo / getters round-trip --

    @Test
    void testSetTempoAndGettersRoundTrip() {
        var tempo = new Tempo(96, Duration.QUAVER, "Allegretto", false);
        section.setTempo(tempo);

        assertThat(section.getTempoType())
            .as("getTempoType after setTempo")
            .isEqualTo(Duration.QUAVER);
        assertThat(section.getVisibleTempo())
            .as("getVisibleTempo after setTempo")
            .isEqualTo(96);
        assertThat(section.getTempoDescription())
            .as("getTempoDescription after setTempo")
            .isEqualTo("Allegretto");
        // shouldShowTempo=false means showOnlyDescription=true
        assertThat(section.isShowOnlyDescription())
            .as("isShowOnlyDescription when shouldShowTempo is false")
            .isTrue();
    }

    // -- getTempoType() ISE when no selection --

    @Test
    void testGetTempoTypeThrowsIllegalStateExceptionWhenSelectionIsNull() {
        // Clear the combo selection; getSelectedItem() returns null
        section.tempoTypeCombo.setSelectedItem(null);

        assertThatThrownBy(() -> section.getTempoType())
            .as("getTempoType must throw ISE when no item is selected")
            .isInstanceOf(IllegalStateException.class);
    }

    // -- getTempoDescription() returns empty string when combo selection is null --

    @Test
    void testGetTempoDescriptionReturnsEmptyStringWhenSelectionIsNull() {
        // The description combo is editable; explicitly clear its selection
        section.tempoDescriptionCombo.setSelectedItem(null);

        assertThat(section.getTempoDescription())
            .as("getTempoDescription must return empty string when selection is null")
            .isEmpty();
    }
}
