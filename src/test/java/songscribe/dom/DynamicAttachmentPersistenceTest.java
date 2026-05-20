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
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.DynamicAttachment.DynamicType;

/**
 * Unit tests verifying that DynamicAttachments on notes persist correctly
 * through a save/load round-trip.
 */
class DynamicAttachmentPersistenceTest extends UnitTest {

    private static final int FORTE_INDEX = 0;
    private static final int PIANISSIMO_INDEX = 1;
    private static final int UNTOUCHED_INDEX = 2;

    @Test
    void testRoundTripPreservesDynamicAttachments() throws Exception {
        var song = getSong();

        var reloaded = roundTrip(song);
        var reloadedLine = reloaded.getLine(0);

        var reloadedForte = reloadedLine.getElement(FORTE_INDEX).findAttachment(DynamicAttachment.class);
        var reloadedPianissimo = reloadedLine.getElement(PIANISSIMO_INDEX).findAttachment(DynamicAttachment.class);
        var reloadedUntouched = reloadedLine.getElement(UNTOUCHED_INDEX).findAttachment(DynamicAttachment.class);

        assertAll(
            () -> assertThat(reloadedForte)
                .as("forte attachment preserved after round-trip").isNotNull(),
            () -> assertThat(reloadedForte == null ? null : reloadedForte.getType())
                .as("forte attachment type preserved").isEqualTo(DynamicType.FORTE),
            () -> assertThat(reloadedPianissimo)
                .as("pianissimo attachment preserved after round-trip").isNotNull(),
            () -> assertThat(reloadedPianissimo == null ? null : reloadedPianissimo.getType())
                .as("pianissimo attachment type preserved").isEqualTo(DynamicType.PIANISSIMO),
            () -> assertThat(reloadedUntouched)
                .as("untouched note has no dynamic after round-trip").isNull()
        );
    }

    private static Song getSong() {
        var song = new Song();
        var line = song.getLine(0);

        song.withModification(() -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());

            var forteNote = line.getElement(FORTE_INDEX);
            forteNote.addAttachment(new DynamicAttachment(forteNote, DynamicType.FORTE));

            var pianissimoNote = line.getElement(PIANISSIMO_INDEX);
            pianissimoNote.addAttachment(new DynamicAttachment(pianissimoNote, DynamicType.PIANISSIMO));
        });
        return song;
    }
}
