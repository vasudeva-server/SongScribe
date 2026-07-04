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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.io.LegacyLyricsImporter;

/**
 * Tests record equality, field semantics, and the canonical-constructor invariant for {@link Lyric}.
 * Also covers the typographic-normalization seam added in Phase 3.
 */
class LyricTest extends UnitTest {

    private static final Lyric SINGLE = new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

    private Song song;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

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

    @Test
    void testInvariantRejectsCompoundOnContinueCarrier() {
        assertThatThrownBy(() -> new Lyric(1, "", Lyric.Extend.CONTINUE, null, true))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testInvariantRejectsCompoundOnNonContinuingSyllabic() {
        assertThatThrownBy(() -> new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, true))
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.END, true))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testInvariantAcceptsCompoundOnContinuingSyllabic() {
        assertThatCode(() -> new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, true))
            .doesNotThrowAnyException();

        assertThatCode(() -> new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, true))
            .doesNotThrowAnyException();
    }

    // --- Phase 7: typographic normalization seam ---

    @Test
    void testConstructorAppliesTypographicSubstitution() {
        // Straight apostrophe in "don't" must become a right single quotation mark (U+2019).
        var lyric = new Lyric(1, "don't", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        assertThat(lyric.text()).isEqualTo("don’t");
    }

    @Test
    void testConstructorStripsShortA() {
        // ă in a syllable must be replaced with a (unconditional short-A stripping).
        var lyric = new Lyric(1, "băiat", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        assertThat(lyric.text()).isEqualTo("baiat");
    }

    @Test
    void testConstructorNormalizationIsIdempotent() {
        // Constructing a Lyric from the already-normalized text of another Lyric is a no-op.
        var first = new Lyric(1, "don't", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var second = new Lyric(1, first.text(), Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        assertThat(second.text()).isEqualTo(first.text());
    }

    @Test
    void testCarrierLyricWithEmptyTextIsUnchanged() {
        // Empty text must survive normalization unchanged; carrier-lyric validation must pass.
        var carrier = new Lyric(1, "", Lyric.Extend.CONTINUE, null, false);

        assertThat(carrier.text()).isEmpty();
    }

    @SuppressWarnings("NullAway")
    @Test
    void testLegacyImportDoubleHyphenCompoundMarkerIsNotCorruptedToEmDash() {
        // The "--" compound-word marker in legacy lyric blobs is consumed by the parser
        // before new Lyric(...) is called, so the toTypographic "-- → —" rule must
        // never corrupt the syllable texts.
        var line = lineWithNotes(2);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "heart--garden");

        var heartLyric = line.getElement(0).getMainLyric();
        var gardenLyric = line.getElement(1).getMainLyric();

        assertThat(heartLyric).isNotNull();
        assertThat(gardenLyric).isNotNull();

        if (heartLyric == null || gardenLyric == null) {
            return; // unreachable — satisfies NullAway after the assertThat above
        }

        // syllabic: heart=BEGIN compound, garden=END; texts must not contain an em-dash.
        assertThat(heartLyric.text()).isEqualTo("heart");
        assertThat(heartLyric.syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
        assertThat(heartLyric.compound()).isTrue();
        assertThat(gardenLyric.text()).isEqualTo("garden");
        assertThat(gardenLyric.syllabic()).isEqualTo(Lyric.Syllabic.END);
        assertThat(gardenLyric.compound()).isFalse();
    }

    private Line lineWithNotes(int count) {
        var line = new Line(song);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < count; i++) {
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        });

        return line;
    }

    private record Variant(Lyric.Syllabic syllabic, boolean compound) {}
}
