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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

class TempoChangeAttachmentTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Copy {

        @Test
        void testCopyReturnsDistinctInstanceWithNewOwnerAndPreservesTempo() {
            var tempo = new Tempo(120, Duration.CROTCHET, "Allegro", true);
            var originalOwner = crotchet();
            var newOwner = quaver();
            var original = new TempoChangeAttachment(originalOwner, tempo);

            var copy = original.copy(newOwner);

            assertThat(copy).isNotSameAs(original);
            assertThat(copy).isExactlyInstanceOf(TempoChangeAttachment.class);
            assertThat(copy.getOwnerElement()).isSameAs(newOwner);
            // The tempo must be deep-copied, not shared, so copy() means one thing across
            // the Attachment hierarchy regardless of whether a mutator exists today.
            var copiedTempo = ((TempoChangeAttachment) copy).getTempo();
            assertThat(copiedTempo).isNotSameAs(tempo);
            assertThat(copiedTempo.getVisibleTempo()).isEqualTo(tempo.getVisibleTempo());
            assertThat(copiedTempo.getTempoType()).isEqualTo(tempo.getTempoType());
            assertThat(copiedTempo.getTempoDescription()).isEqualTo(tempo.getTempoDescription());
            assertThat(copiedTempo.shouldShowTempo()).isEqualTo(tempo.shouldShowTempo());
        }
    }
}
