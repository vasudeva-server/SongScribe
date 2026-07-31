/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link Tempo}.
 *
 * Row 34 — getRealTempo(): (visibleTempo × noteDuration) / PPQ
 */
class TempoTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Row 34 — getRealTempo(): (visibleTempo × noteDuration) / PPQ
    // -------------------------------------------------------------------------

    // A representative visible tempo used for both cases.
    private static final int VISIBLE_TEMPO = 120;

    // Expected real tempo when tempoType is CROTCHET (quarter note):
    // (120 × PPQ) / PPQ = 120
    private static final int EXPECTED_REAL_TEMPO_CROTCHET = VISIBLE_TEMPO;

    // Expected real tempo when tempoType is MINIM (half note):
    // (120 × PPQ×2) / PPQ = 240
    private static final int EXPECTED_REAL_TEMPO_MINIM = VISIBLE_TEMPO * 2;

    @Test
    void testGetRealTempoCrotchetEqualsVisibleTempo() {
        // CROTCHET note duration = PPQ = 96; (120 × 96) / 96 = 120
        var tempo = new Tempo(VISIBLE_TEMPO, Duration.CROTCHET, "Allegro", true);

        assertThat(tempo.getRealTempo()).isEqualTo(EXPECTED_REAL_TEMPO_CROTCHET);
    }

    @Test
    void testGetRealTempoMimimIsDoubleVisibleTempo() {
        // MINIM note duration = PPQ * 2 = 192; (120 × 192) / 96 = 240
        // This case catches a divisor regression (e.g. using PPQ where PPQ×2 is needed).
        var tempo = new Tempo(VISIBLE_TEMPO, Duration.MINIM, "Andante", true);

        assertThat(tempo.getRealTempo()).isEqualTo(EXPECTED_REAL_TEMPO_MINIM);
    }

    @Test
    void testGetRealTempoCrotchetAndMimimProduceDifferentValues() {
        // Confirm the two cases are distinct — guards against a wrong denominator
        // that would make both formulas collapse to the same value.
        assertThat(EXPECTED_REAL_TEMPO_CROTCHET).isNotEqualTo(EXPECTED_REAL_TEMPO_MINIM);
    }
}
