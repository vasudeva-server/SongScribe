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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLGlyph;

class NoteRendererTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    // Staff positions: even = on a line, odd = in a space
    private static final int ON_LINE_STAFF_POSITION = 0;
    private static final int IN_SPACE_STAFF_POSITION = 1;

    // ==========================================================================
    // getNoteHeadGlyph (row 1)
    // ==========================================================================

    @Test
    void testGetNoteHeadGlyphReturnsSemibreveGlyph() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.SEMIBREVE))
            .isEqualTo(SMuFLGlyph.NOTEHEAD_WHOLE);
    }

    @Test
    void testGetNoteHeadGlyphReturnsMinimGlyph() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.MINIM))
            .isEqualTo(SMuFLGlyph.NOTEHEAD_HALF);
    }

    @Test
    void testGetNoteHeadGlyphReturnsCrotchetGlyph() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.CROTCHET))
            .isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
    }

    @Test
    void testGetNoteHeadGlyphReturnsQuaverGlyph() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.QUAVER))
            .isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
    }

    @Test
    void testGetNoteHeadGlyphReturnsSemiquaverGlyph() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.SEMIQUAVER))
            .isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
    }

    @Test
    void testGetNoteHeadGlyphReturnsDemiSemiquaverGlyph() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.DEMI_SEMIQUAVER))
            .isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
    }

    @Test
    void testGetNoteHeadGlyphReturnsGraceQuaverGlyph() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.GRACE_QUAVER))
            .isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
    }

    @Test
    void testGetNoteHeadGlyphReturnsNullForNonNoteType() {
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.CROTCHET_REST)).isNull();
        assertThat(NoteRenderer.getNoteHeadGlyph(ElementType.SINGLE_BARLINE)).isNull();
    }

    // ==========================================================================
    // getNoteHeadChar (row 2)
    // ==========================================================================

    @Test
    void testGetNoteHeadCharReturnsNullForNonNoteType() {
        assertThat(NoteRenderer.getNoteHeadChar(ElementType.SEMIBREVE_REST)).isNull();
    }

    @Test
    void testGetNoteHeadCharReturnsSemibreveString() {
        var expected = SMuFLGlyph.NOTEHEAD_WHOLE.asString();
        assertThat(NoteRenderer.getNoteHeadChar(ElementType.SEMIBREVE)).isEqualTo(expected);
    }

    @Test
    void testGetNoteHeadCharReturnsMinimString() {
        var expected = SMuFLGlyph.NOTEHEAD_HALF.asString();
        assertThat(NoteRenderer.getNoteHeadChar(ElementType.MINIM)).isEqualTo(expected);
    }

    @Test
    void testGetNoteHeadCharReturnsCrotchetString() {
        var expected = SMuFLGlyph.NOTEHEAD_BLACK.asString();
        assertThat(NoteRenderer.getNoteHeadChar(ElementType.CROTCHET)).isEqualTo(expected);
    }

    // ==========================================================================
    // computeBaseStemGeometry (row 3)
    // ==========================================================================

    @Nested
    class ComputeBaseStemGeometry {

        @Test
        void testBlackNoteHeadStemUpUsesBlackUpAnchor() {
            var geom = NoteRenderer.computeBaseStemGeometry(ElementType.CROTCHET, true);
            var anchor = Engraving.NOTEHEAD_BLACK_STEM_UP_SE;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(Engraving.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testBlackNoteHeadStemDownUsesBlackDownAnchor() {
            var geom = NoteRenderer.computeBaseStemGeometry(ElementType.CROTCHET, false);
            var anchor = Engraving.NOTEHEAD_BLACK_STEM_DOWN_NW;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS / 2;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(Engraving.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testMinimStemUpUsesHalfUpAnchor() {
            var geom = NoteRenderer.computeBaseStemGeometry(ElementType.MINIM, true);
            var anchor = Engraving.NOTEHEAD_HALF_STEM_UP_SE;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(Engraving.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testMinimStemDownUsesHalfDownAnchor() {
            var geom = NoteRenderer.computeBaseStemGeometry(ElementType.MINIM, false);
            var anchor = Engraving.NOTEHEAD_HALF_STEM_DOWN_NW;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS / 2;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(Engraving.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testGraceNoteUsesSmallAnchorAndGraceStemLength() {
            var geom = NoteRenderer.computeBaseStemGeometry(ElementType.GRACE_QUAVER, true);
            var anchor = NoteGeometry.STEM_UP_SE_BLACK_SMALL;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(Engraving.GRACE_NOTE_STEM_LENGTH_SS, within(TOLERANCE));
        }
    }

    // ==========================================================================
    // StemGeometry.stemTipYSs (row 4)
    // ==========================================================================

    @Test
    void testStemTipYSsUpIsAnchorMinusLength() {
        var geom = new NoteRenderer.StemGeometry(0.5, 0.3, 3.5);
        var expectedTip = 0.3 - 3.5;

        assertThat(geom.stemTipYSs(true)).isCloseTo(expectedTip, within(TOLERANCE));
    }

    @Test
    void testStemTipYSsDownIsAnchorPlusLength() {
        var geom = new NoteRenderer.StemGeometry(0.5, 0.3, 3.5);
        var expectedTip = 0.3 + 3.5;

        assertThat(geom.stemTipYSs(false)).isCloseTo(expectedTip, within(TOLERANCE));
    }

    // ==========================================================================
    // forEachDotPosition (row 5)
    // ==========================================================================

    @Nested
    class ForEachDotPosition {

        private static List<double[]> collectDots(StaffElement note, boolean beamed, boolean upper) {
            var result = new ArrayList<double[]>();
            NoteRenderer.forEachDotPosition(note, beamed, upper, (x, y) -> result.add(new double[]{x, y}));
            return result;
        }

        @Test
        void testNoDotCountProducesNoDots() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(0);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            assertThat(collectDots(note, false, true)).isEmpty();
        }

        @Test
        void testSemibreveAppliesXOffset() {
            var note = ElementType.SEMIBREVE.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, true);
            assertThat(dots).hasSize(1);

            var expectedX = NoteRenderer.FIRST_DOT_X_SS + NoteRenderer.DOT_X_ADJUST_SEMIBREVE_SS;
            assertThat(dots.get(0)[0]).isCloseTo(expectedX, within(TOLERANCE));
        }

        @Test
        void testMinimAppliesXOffset() {
            var note = ElementType.MINIM.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, true);
            assertThat(dots).hasSize(1);

            var expectedX = NoteRenderer.FIRST_DOT_X_SS + NoteRenderer.DOT_X_ADJUST_MINIM_SS;
            assertThat(dots.get(0)[0]).isCloseTo(expectedX, within(TOLERANCE));
        }

        @Test
        void testBeamableUnbeamedUpperQuaverAppliesXOffset() {
            var note = ElementType.QUAVER.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, true);
            assertThat(dots).hasSize(1);

            var expectedX = NoteRenderer.FIRST_DOT_X_SS + NoteRenderer.DOT_X_ADJUST_QUAVER_SS;
            assertThat(dots.get(0)[0]).isCloseTo(expectedX, within(TOLERANCE));
        }

        @Test
        void testBeamableUnbeamedUpperSemiquaverAppliesLargerXOffset() {
            var note = ElementType.SEMIQUAVER.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, true);
            assertThat(dots).hasSize(1);

            var expectedX = NoteRenderer.FIRST_DOT_X_SS + NoteRenderer.DOT_X_ADJUST_SEMIQUAVER_SS;
            assertThat(dots.get(0)[0]).isCloseTo(expectedX, within(TOLERANCE));
        }

        @Test
        void testOnLinePositionShiftsYUp() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(ON_LINE_STAFF_POSITION);

            var dots = collectDots(note, false, true);
            assertThat(dots).hasSize(1);
            assertThat(dots.get(0)[1]).isCloseTo(NoteRenderer.DOT_ON_LINE_Y_SHIFT_SS, within(TOLERANCE));
        }

        @Test
        void testInSpacePositionHasZeroYOffset() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, true);
            assertThat(dots).hasSize(1);
            assertThat(dots.get(0)[1]).isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        void testMultipleDotsSpacedByDotSpacing() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(2);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, false);
            assertThat(dots).hasSize(2);

            var firstX = dots.get(0)[0];
            var secondX = dots.get(1)[0];
            assertThat(secondX - firstX).isCloseTo(NoteRenderer.DOT_SPACING_SS, within(TOLERANCE));
        }
    }

}
