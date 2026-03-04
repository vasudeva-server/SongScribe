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

package songscribe.music;

import org.junit.jupiter.api.Test;

import songscribe.smufl.SMuFLGlyph;

import static org.assertj.core.api.Assertions.assertThat;

class NoteTypeTest {

    // T7: getFlagGlyph(upper) returns the correct glyph for each flagged type × direction
    @Test
    void testGetFlagGlyphReturnsCorrectGlyphForFlaggedTypes() {
        assertThat(NoteType.QUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
        assertThat(NoteType.QUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_8TH_DOWN);

        assertThat(NoteType.SEMIQUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_16TH_UP);
        assertThat(NoteType.SEMIQUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_16TH_DOWN);

        assertThat(NoteType.DEMI_SEMIQUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_32ND_UP);
        assertThat(NoteType.DEMI_SEMIQUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_32ND_DOWN);
    }

    // T7 continued: GRACE_QUAVER always returns small 8th flag (stem always up)
    @Test
    void testGetFlagGlyphReturnsSmallEighthFlagForGraceQuaver() {
        assertThat(NoteType.GRACE_QUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP_SMALL);
        assertThat(NoteType.GRACE_QUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP_SMALL);
    }

    // T8: getFlagGlyph(upper) returns null for non-flagged types
    @Test
    void testGetFlagGlyphReturnsNullForNonFlaggedTypes() {
        assertThat(NoteType.CROTCHET.getFlagGlyph(true)).isNull();
        assertThat(NoteType.MINIM.getFlagGlyph(true)).isNull();
        assertThat(NoteType.SEMIBREVE.getFlagGlyph(true)).isNull();
        assertThat(NoteType.CROTCHET_REST.getFlagGlyph(true)).isNull();
        assertThat(NoteType.QUAVER_REST.getFlagGlyph(true)).isNull();
        assertThat(NoteType.SEMIBREVE_REST.getFlagGlyph(true)).isNull();
    }
}
