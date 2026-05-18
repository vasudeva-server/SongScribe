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

package songscribe.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Tests record equality, field semantics, and the canonical-constructor invariant for {@link Lyric}. */
class LyricTest {

    private static final Lyric SINGLE = new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

    @Test
    void testEqualityRequiresSameSyllabic() {
        var asEnd = new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.END, false);

        assertThat(SINGLE).isNotEqualTo(asEnd);
        assertThat(SINGLE.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        assertThat(asEnd.syllabic()).isEqualTo(Lyric.Syllabic.END);
    }

    @Test
    void testEqualityRequiresSameCompound() {
        var compound = new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, true);
        var notCompound = new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false);

        assertThat(compound).isNotEqualTo(notCompound);
        assertThat(compound.compound()).isTrue();
        assertThat(notCompound.compound()).isFalse();
    }

    @SuppressWarnings("DuplicateExpressions")
    @Test
    void testEqualRecordsWithAllSyllabicValues() {
        var variants = new Variant[] {
            new Variant(Lyric.Syllabic.SINGLE, false),
            new Variant(Lyric.Syllabic.BEGIN, false),
            new Variant(Lyric.Syllabic.MIDDLE, false),
            new Variant(Lyric.Syllabic.END, false),
        };

        for (var variant : variants) {
            var a = new Lyric(1, "do", Lyric.Extend.NONE, variant.syllabic(), variant.compound());
            var b = new Lyric(1, "do", Lyric.Extend.NONE, variant.syllabic(), variant.compound());
            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }
    }

    @Test
    void testCarrierLyricWithNullSyllabicEqualsItself() {
        var stop = new Lyric(1, "", Lyric.Extend.STOP, null, false);
        var copy = new Lyric(1, "", Lyric.Extend.STOP, null, false);

        assertThat(stop).isEqualTo(copy);
        assertThat(stop).hasSameHashCodeAs(copy);
        assertThat(stop.syllabic()).isNull();
    }

    @Test
    void testCompoundFlagDistinguishesEqualSyllabicValues() {
        var compoundBegin = new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, true);
        var plainBegin = new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false);

        assertThat(compoundBegin).isNotEqualTo(plainBegin);
        assertThat(compoundBegin.syllabic()).isEqualTo(plainBegin.syllabic());
        assertThat(compoundBegin.compound()).isNotEqualTo(plainBegin.compound());
    }

    @Test
    void testInvariantRejectsNullSyllabicOnTextLyric() {
        assertThatThrownBy(() -> new Lyric(1, "do", Lyric.Extend.NONE, null, false))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testInvariantRejectsCarrierWithSyllabic() {
        assertThatThrownBy(() -> new Lyric(1, "", Lyric.Extend.STOP, Lyric.Syllabic.SINGLE, false))
            .isInstanceOf(IllegalStateException.class);
    }

    private record Variant(Lyric.Syllabic syllabic, boolean compound) {}
}
