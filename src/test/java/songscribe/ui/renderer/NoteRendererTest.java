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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.engraving.SMuFLConstants;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

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
            var geom = NoteGeometry.computeBaseStemGeometry(ElementType.CROTCHET, StaffElement.Direction.UP);
            var anchor = SMuFLConstants.NOTEHEAD_BLACK_STEM_UP_SE;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testBlackNoteHeadStemDownUsesBlackDownAnchor() {
            var geom = NoteGeometry.computeBaseStemGeometry(ElementType.CROTCHET, StaffElement.Direction.DOWN);
            var anchor = SMuFLConstants.NOTEHEAD_BLACK_STEM_DOWN_NW;
            // Down-stem: anchor marks the stem's LEFT edge, used directly (no head-shift compensation).
            var expectedStemLeftX = anchor.x();

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testMinimStemUpUsesHalfUpAnchor() {
            var geom = NoteGeometry.computeBaseStemGeometry(ElementType.MINIM, StaffElement.Direction.UP);
            var anchor = SMuFLConstants.NOTEHEAD_HALF_STEM_UP_SE;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testMinimStemDownUsesHalfDownAnchor() {
            var geom = NoteGeometry.computeBaseStemGeometry(ElementType.MINIM, StaffElement.Direction.DOWN);
            var anchor = SMuFLConstants.NOTEHEAD_HALF_STEM_DOWN_NW;
            // Down-stem: anchor marks the stem's LEFT edge, used directly (no head-shift compensation).
            var expectedStemLeftX = anchor.x();

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testGraceNoteUsesSmallAnchorAndGraceStemLength() {
            var geom = NoteGeometry.computeBaseStemGeometry(ElementType.GRACE_QUAVER, StaffElement.Direction.UP);
            var anchor = NoteGeometry.STEM_UP_SE_BLACK_SMALL;
            var expectedStemLeftX = anchor.x() - NoteGeometry.STEM_WIDTH_SS;

            assertThat(geom.stemLeftXSs()).isCloseTo(expectedStemLeftX, within(TOLERANCE));
            assertThat(geom.anchorYSs()).isCloseTo(anchor.y(), within(TOLERANCE));
            assertThat(geom.lengthSs()).isCloseTo(SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS, within(TOLERANCE));
        }
    }

    // ==========================================================================
    // StemGeometry.stemTipYSs (row 4)
    // ==========================================================================

    @Test
    void testStemTipYSsUpIsAnchorMinusLength() {
        var geom = new NoteGeometry.StemGeometry(0.5, 0.3, 3.5);
        var expectedTip = 0.3 - 3.5;

        assertThat(geom.stemTipYSs(StaffElement.Direction.UP)).isCloseTo(expectedTip, within(TOLERANCE));
    }

    @Test
    void testStemTipYSsDownIsAnchorPlusLength() {
        var geom = new NoteGeometry.StemGeometry(0.5, 0.3, 3.5);
        var expectedTip = 0.3 + 3.5;

        assertThat(geom.stemTipYSs(StaffElement.Direction.DOWN)).isCloseTo(expectedTip, within(TOLERANCE));
    }

    // ==========================================================================
    // forEachDotPosition (row 5)
    // ==========================================================================

    @Nested
    class ForEachDotPosition {

        private static List<double[]> collectDots(
            StaffElement note, boolean beamed, StaffElement.Direction direction
        ) {
            var result = new ArrayList<double[]>();
            NoteGeometry.forEachDotPosition(
                note, beamed, direction, (x, y) -> result.add(new double[]{x, y})
            );
            return result;
        }

        private static StaffElement dottedNote(ElementType type) {
            var note = type.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);
            return note;
        }

        private static double dotXSs(ElementType type, boolean beamed, StaffElement.Direction direction) {
            var dots = collectDots(dottedNote(type), beamed, direction);
            assertThat(dots).hasSize(1);
            return dots.get(0)[0];
        }

        // Expected first-dot X derived independently from SMuFL metadata: the notehead's right
        // edge plus one augmentation-dot width (the LilyPond pad-by-one-dot-width gap).
        private static double expectedNoteheadDotXSs(SMuFLGlyph noteheadGlyph) {
            return SMuFLMetadata.requireBBox(noteheadGlyph).right() + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS;
        }

        // Expected first-dot X for an unbeamed up-stem flagged note: the flag's right edge (the
        // flag clears the notehead) plus one dot width.
        private static double expectedFlaggedDotXSs(SMuFLGlyph flagGlyph) {
            var stemLeftXSs =
                NoteGeometry.computeBaseStemGeometry(ElementType.QUAVER, StaffElement.Direction.UP)
                    .stemLeftXSs();
            var flagRightSs = stemLeftXSs + SMuFLMetadata.requireBBox(flagGlyph).right();
            return flagRightSs + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS;
        }

        @Test
        void testNoDotCountProducesNoDots() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(0);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            assertThat(collectDots(note, false, StaffElement.Direction.UP)).isEmpty();
        }

        @Test
        void testCrotchetDotSitsOneDotWidthRightOfNotehead() {
            assertThat(dotXSs(ElementType.CROTCHET, false, StaffElement.Direction.UP))
                .isCloseTo(expectedNoteheadDotXSs(SMuFLGlyph.NOTEHEAD_BLACK), within(TOLERANCE));
        }

        @Test
        void testSemibreveDotClearsWiderNotehead() {
            var semibreveX = dotXSs(ElementType.SEMIBREVE, false, StaffElement.Direction.UP);

            assertThat(semibreveX).isCloseTo(expectedNoteheadDotXSs(SMuFLGlyph.NOTEHEAD_WHOLE), within(TOLERANCE));
            // The whole notehead is wider than a black one, so its dot sits further right.
            assertThat(semibreveX).isGreaterThan(expectedNoteheadDotXSs(SMuFLGlyph.NOTEHEAD_BLACK));
        }

        @Test
        void testMinimDotMatchesCrotchet() {
            // Half and black noteheads share the same right edge, so their dots align.
            assertThat(dotXSs(ElementType.MINIM, false, StaffElement.Direction.UP))
                .isCloseTo(dotXSs(ElementType.CROTCHET, false, StaffElement.Direction.UP), within(TOLERANCE));
        }

        @Test
        void testUnbeamedUpperQuaverDotClearsFlag() {
            var quaverX = dotXSs(ElementType.QUAVER, false, StaffElement.Direction.UP);

            assertThat(quaverX).isCloseTo(expectedFlaggedDotXSs(SMuFLGlyph.FLAG_8TH_UP), within(TOLERANCE));
            // The flag extends past the notehead, so the dot sits further right than a crotchet's.
            assertThat(quaverX).isGreaterThan(dotXSs(ElementType.CROTCHET, false, StaffElement.Direction.UP));
        }

        @Test
        void testUnbeamedUpperSemiquaverDotClearsFlag() {
            assertThat(dotXSs(ElementType.SEMIQUAVER, false, StaffElement.Direction.UP))
                .isCloseTo(expectedFlaggedDotXSs(SMuFLGlyph.FLAG_16TH_UP), within(TOLERANCE));
        }

        @Test
        void testUnbeamedUpperDemiSemiquaverDotClearsFlag() {
            assertThat(dotXSs(ElementType.DEMI_SEMIQUAVER, false, StaffElement.Direction.UP))
                .isCloseTo(expectedFlaggedDotXSs(SMuFLGlyph.FLAG_32ND_UP), within(TOLERANCE));
        }

        @Test
        void testBeamedQuaverDotIgnoresFlag() {
            // A beamed note has no flag, so its dot sits at the notehead like a crotchet's.
            assertThat(dotXSs(ElementType.QUAVER, true, StaffElement.Direction.UP))
                .isCloseTo(expectedNoteheadDotXSs(SMuFLGlyph.NOTEHEAD_BLACK), within(TOLERANCE));
        }

        @Test
        void testLowerQuaverDotIgnoresFlag() {
            // A down-stem flag is to the left of the notehead, so it never pushes the dot right.
            assertThat(dotXSs(ElementType.QUAVER, false, StaffElement.Direction.DOWN))
                .isCloseTo(expectedNoteheadDotXSs(SMuFLGlyph.NOTEHEAD_BLACK), within(TOLERANCE));
        }

        @Test
        void testOnLinePositionShiftsYUp() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(ON_LINE_STAFF_POSITION);

            var dots = collectDots(note, false, StaffElement.Direction.UP);
            assertThat(dots).hasSize(1);
            assertThat(dots.get(0)[1]).isCloseTo(NoteGeometry.DOT_ON_LINE_Y_SHIFT_SS, within(TOLERANCE));
        }

        @Test
        void testInSpacePositionHasZeroYOffset() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, StaffElement.Direction.UP);
            assertThat(dots).hasSize(1);
            assertThat(dots.get(0)[1]).isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        void testMultipleDotsSpacedByDotSpacing() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(2);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, StaffElement.Direction.DOWN);
            assertThat(dots).hasSize(2);

            var firstX = dots.get(0)[0];
            var secondX = dots.get(1)[0];
            assertThat(secondX - firstX).isCloseTo(NoteGeometry.DOT_SPACING_SS, within(TOLERANCE));
        }
    }

    // ==========================================================================
    // renderStem — forced-shortening reads the layout-supplied forcedShorteningSs
    // ==========================================================================

    @Nested
    class RenderStemForcedShortening {

        private static final double TOLERANCE = 1e-9;

        /** A mock Graphics2D paired with the local (pre-transform) shapes it filled. */
        private record RecordingG2(Graphics2D g2, List<Shape> filledShapes) {}

        private static RecordingG2 recordingG2() {
            var g2 = mock(Graphics2D.class);
            when(g2.getTransform()).thenReturn(new AffineTransform());
            var filledShapes = new ArrayList<Shape>();
            doAnswer(invocation -> {
                filledShapes.add(invocation.getArgument(0));
                return null;
            }).when(g2).fill(any(Shape.class));
            doAnswer(invocation -> null).when(g2).translate(anyDouble(), anyDouble());

            return new RecordingG2(g2, filledShapes);
        }

        private static LineInvariants buildInvariantsWithStemLayout(
            StaffElement note, LayoutResult.StemLayout stemLayout
        ) {
            var line = detachedLine();
            line.addElement(note);
            var layoutResult = LayoutResult.builder()
                .putStemLayout(note, stemLayout)
                .build();

            return RenderContextTestHelper.newContext(new Song())
                .setCurrentLine(line)
                .setLayoutResult(layoutResult)
                .build();
        }

        // renderStem draws stemLeftXSs/drawTop/(drawBottom-drawTop) directly as the fill Shape's
        // local coordinates (translate() on a mock does not transform the Shape argument), so an
        // unbeamed up-stem's rendered length is exactly -shape.getBounds2D().getMinY().
        private static double renderedUpStemLengthSs(StaffElement note, LayoutResult.StemLayout stemLayout) {
            var invariants = buildInvariantsWithStemLayout(note, stemLayout);
            var recording = recordingG2();

            NoteRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, note, recording.g2());

            assertThat(recording.filledShapes()).describedAs("stem fill call").hasSize(1);
            return -recording.filledShapes().getFirst().getBounds2D().getMinY();
        }

        // Mirrors renderedUpStemLengthSs: an unbeamed down-stem's stemTipY is +stemLength, drawn
        // directly as drawBottom, so its rendered length is exactly shape.getBounds2D().getMaxY().
        private static double renderedDownStemLengthSs(StaffElement note, LayoutResult.StemLayout stemLayout) {
            var invariants = buildInvariantsWithStemLayout(note, stemLayout);
            var recording = recordingG2();

            NoteRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, note, recording.g2());

            assertThat(recording.filledShapes()).describedAs("stem fill call").hasSize(1);
            return recording.filledShapes().getFirst().getBounds2D().getMaxY();
        }

        @Test
        void testForcedUpStemDrawsShorterByForcedShorteningSs() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(0);
            var forcedShorteningSs = 0.3;
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, forcedShorteningSs, false);

            var renderedLengthSs = renderedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(
                SMuFLConstants.STEM_LENGTH_SS - forcedShorteningSs, within(TOLERANCE));
        }

        @Test
        void testNaturalUpStemDrawsAtFullNaturalLength() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(0);
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, 0.0, false);

            var renderedLengthSs = renderedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testForcedShorteningCombinesWithLengtheningInTheRenderedStem() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(0);
            var lengtheningSs = 0.8;
            var forcedShorteningSs = 0.3;
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, lengtheningSs, forcedShorteningSs, false);

            var renderedLengthSs = renderedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(
                SMuFLConstants.STEM_LENGTH_SS + lengtheningSs - forcedShorteningSs, within(TOLERANCE));
        }

        @Test
        void testForcedDownStemDrawsShorterByForcedShorteningSs() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(false);
            note.setStaffPosition(0);
            var forcedShorteningSs = 0.3;
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, forcedShorteningSs, false);

            var renderedLengthSs = renderedDownStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(
                SMuFLConstants.STEM_LENGTH_SS - forcedShorteningSs, within(TOLERANCE));
        }

        @Test
        void testNaturalDownStemDrawsAtFullNaturalLength() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(false);
            note.setStaffPosition(0);
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, 0.0, false);

            var renderedLengthSs = renderedDownStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        }

        @Test
        void testStemLengthClampsToForcedStemFloorWhenForcedShorteningExceedsIt() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(0);
            var outOfRangeForcedShorteningSs = SMuFLConstants.STEM_LENGTH_SS;
            var stemLayout =
                new LayoutResult.StemLayout(0.0, 0.0, 0.0, outOfRangeForcedShorteningSs, false);

            var renderedLengthSs = renderedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(NoteGeometry.FORCED_STEM_FLOOR_SS, within(TOLERANCE));
        }
    }

}
