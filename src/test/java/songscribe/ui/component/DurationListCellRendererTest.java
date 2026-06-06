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
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.smufl.SMuFLGlyph;

/**
 * Unit tests for {@link DurationListCellRenderer} covering:
 * <ul>
 *   <li>Row 80 — {@code noteGlyphFor}: each {@link Duration} value maps to the expected SMuFL glyph</li>
 * </ul>
 */
class DurationListCellRendererTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 80: noteGlyphFor — mapping switch covers all Duration values
    // -----------------------------------------------------------------------

    @Test
    void testNoteGlyphForSemiBreveReturnsNoteWhole() {
        assertThat(DurationListCellRenderer.noteGlyphFor(Duration.SEMI_BREVE))
            .isEqualTo(SMuFLGlyph.NOTE_WHOLE);
    }

    @Test
    void testNoteGlyphForMinimReturnsNoteHalfUp() {
        assertThat(DurationListCellRenderer.noteGlyphFor(Duration.MINIM))
            .isEqualTo(SMuFLGlyph.NOTE_HALF_UP);
    }

    @Test
    void testNoteGlyphForMinimDottedReturnsNoteHalfUp() {
        assertThat(DurationListCellRenderer.noteGlyphFor(Duration.MINIM_DOTTED))
            .isEqualTo(SMuFLGlyph.NOTE_HALF_UP);
    }

    @Test
    void testNoteGlyphForCrotchetReturnsNoteQuarterUp() {
        assertThat(DurationListCellRenderer.noteGlyphFor(Duration.CROTCHET))
            .isEqualTo(SMuFLGlyph.NOTE_QUARTER_UP);
    }

    @Test
    void testNoteGlyphForCrotchetDottedReturnsNoteQuarterUp() {
        assertThat(DurationListCellRenderer.noteGlyphFor(Duration.CROTCHET_DOTTED))
            .isEqualTo(SMuFLGlyph.NOTE_QUARTER_UP);
    }

    @Test
    void testNoteGlyphForQuaverReturnsNote8thUp() {
        assertThat(DurationListCellRenderer.noteGlyphFor(Duration.QUAVER))
            .isEqualTo(SMuFLGlyph.NOTE_8TH_UP);
    }

    @Test
    void testNoteGlyphForQuaverDottedReturnsNote8thUp() {
        assertThat(DurationListCellRenderer.noteGlyphFor(Duration.QUAVER_DOTTED))
            .isEqualTo(SMuFLGlyph.NOTE_8TH_UP);
    }
}
