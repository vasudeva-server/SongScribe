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

package songscribe.midi;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ArticulationType;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;

import static org.assertj.core.api.Assertions.assertThat;

class VelocityMapTest extends UnitTest {

    @Nested
    class AccentBoost {

        @Test
        void testAccentWithDynamicAppliesBoostToDynamicVelocity() {
            // T40: Accent + dynamic
            var composition = compositionWith(
                lineWith(noteWithDynamicAndAccent(DynamicType.FORTE))
            );
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);

            var forteVelocity = (int) Math.round(
                VelocityMap.MAX_VELOCITY * DynamicType.FORTE.getVelocityFraction());
            var expected = Math.min(forteVelocity + VelocityMap.ACCENT_BOOST, VelocityMap.MAX_VELOCITY);

            assertThat(map.getVelocity(0, 0)).isEqualTo(expected);
        }

        @Test
        void testAccentWithoutDynamicAppliesBoostToDefaultVelocity() {
            // T41: Accent without dynamic
            var composition = compositionWith(lineWith(accentedNote()));
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);

            var mfVelocity = (int) Math.round(
                VelocityMap.MAX_VELOCITY * VelocityMap.DEFAULT_VELOCITY_FRACTION);
            var expected = Math.min(mfVelocity + VelocityMap.ACCENT_BOOST, VelocityMap.MAX_VELOCITY);

            assertThat(map.getVelocity(0, 0)).isEqualTo(expected);
        }
    }

    @Nested
    class CeilingCap {

        @Test
        void testMasterVelocityAtMaxWithFfProduces127() {
            // T42: Master volume at max + ff = 127
            var composition = compositionWith(
                lineWith(noteWithDynamic(DynamicType.FORTISSIMO))
            );
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);
            assertThat(map.getVelocity(0, 0)).isEqualTo(VelocityMap.MAX_VELOCITY);
        }

        @Test
        void testVelocityNeverExceeds127() {
            // T39: accented ff should still cap at 127
            var composition = compositionWith(
                lineWith(noteWithDynamicAndAccent(DynamicType.FORTISSIMO))
            );
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);
            assertThat(map.getVelocity(0, 0)).isEqualTo(VelocityMap.MAX_VELOCITY);
        }
    }

    @Nested
    class DynamicMarking {

        @Test
        void testNoteWithForteUsesForteVelocity() {
            // T35
            var composition = compositionWith(
                lineWith(noteWithDynamic(DynamicType.FORTE))
            );
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);

            var expected = (int) Math.round(
                VelocityMap.MAX_VELOCITY * DynamicType.FORTE.getVelocityFraction());
            assertThat(map.getVelocity(0, 0)).isEqualTo(expected);
        }

        @Test
        void testNoteWithNoDynamicUsesDefaultMfVelocity() {
            // T36
            var composition = compositionWith(lineWith(plainNote()));
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);

            var expected = (int) Math.round(
                VelocityMap.MAX_VELOCITY * VelocityMap.DEFAULT_VELOCITY_FRACTION);
            assertThat(map.getVelocity(0, 0)).isEqualTo(expected);
        }
    }

    @Nested
    class Propagation {

        @Test
        void testCrossLinePropagation() {
            // T38: last dynamic on line N carries to line N+1
            var composition = compositionWith(
                lineWith(noteWithDynamic(DynamicType.PIANO)),
                lineWith(plainNote())
            );
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);

            var pianoVelocity = (int) Math.round(
                VelocityMap.MAX_VELOCITY * DynamicType.PIANO.getVelocityFraction());
            assertThat(map.getVelocity(1, 0)).isEqualTo(pianoVelocity);
        }

        @Test
        void testForwardPropagationWithinLine() {
            // T37: note after PIANO inherits PIANO velocity
            var composition = compositionWith(
                lineWith(noteWithDynamic(DynamicType.PIANO), plainNote())
            );
            var map = VelocityMap.build(composition, VelocityMap.MAX_VELOCITY);

            var pianoVelocity = (int) Math.round(
                VelocityMap.MAX_VELOCITY * DynamicType.PIANO.getVelocityFraction());
            assertThat(map.getVelocity(0, 1)).isEqualTo(pianoVelocity);
        }
    }


    // -- Helpers --

    private static StaffElement accentedNote() {
        var note = ElementType.CROTCHET.newInstance();
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        return note;
    }

    private static Composition compositionWith(Line... lines) {
        var composition = new Composition();
        composition.getLines().clear();

        for (var line : lines) {
            composition.getLines().add(line);
        }

        return composition;
    }

    private static Line lineWith(StaffElement... notes) {
        var line = new Line();

        for (var note : notes) {
            line.addElement(note);
        }

        return line;
    }

    private static StaffElement noteWithDynamic(DynamicType type) {
        var note = ElementType.CROTCHET.newInstance();
        note.addAttachment(new DynamicAttachment(note, type));
        return note;
    }

    private static StaffElement noteWithDynamicAndAccent(DynamicType type) {
        var note = noteWithDynamic(type);
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        return note;
    }

    private static StaffElement plainNote() {
        return ElementType.CROTCHET.newInstance();
    }
}
