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
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import module java.desktop;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class NoteRendererTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    // Staff positions: even = on a line, odd = in a space
    private static final int ON_LINE_STAFF_POSITION = 0;
    private static final int IN_SPACE_STAFF_POSITION = 1;

    // A beamed note's drawn stem is tucked inside the beam by half its thickness, so
    // recovering the length renderStem settled on means adding that inset back. This is
    // the unthickened half; a thickened beam adds half its thickening on top.
    private static final double BEAM_INSET_SS = LineThickness.BEAM_THICKNESS_SS / 2.0;

    /** A mock Graphics2D paired with the local (pre-transform) shapes it filled. */
    private record RecordingG2(Graphics2D g2, List<Shape> filledShapes) {}

    private static RecordingG2 recordingG2() {
        var g2 = mock(Graphics2D.class);
        when(g2.getTransform()).thenReturn(new AffineTransform());
        var filledShapes = new ArrayList<Shape>();
        doAnswer(answerVoid((Shape shape) -> filledShapes.add(shape))).when(g2).fill(any(Shape.class));
        doNothing().when(g2).translate(anyDouble(), anyDouble());

        return new RecordingG2(g2, filledShapes);
    }

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

    /**
     * A breath mark's clickable rect is built from its staff position, so the glyph has to be
     * drawn at the Y that staff position implies or the two come apart. They did once: the
     * renderer held its own copy of the offset and the enum's staff position disagreed with it by
     * a full staff space, leaving the click target off the glyph. Deriving both from the one
     * constant is what fixes it, and this fails if a literal offset is ever written back in.
     */
    @Test
    void testBreathMarkIsDrawnAtTheYItsStaffPositionImplies() {
        var line = detachedLine();
        var breathMark = ElementType.BREATH_MARK.newInstance();
        line.addElement(breathMark);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .build();

        var g2 = spy(RenderContextTestHelper.realG2());
        var drawnYSs = new ArrayList<Float>();
        doAnswer(invocation -> {
            drawnYSs.add(invocation.getArgument(2));
            return null;
        }).when(g2).drawString(anyString(), anyFloat(), anyFloat());

        NoteRenderer.getInstance().render(
            invariants, ElementFrame.LINE_LEVEL.withElement(0, 0.0), breathMark, g2);

        var expectedYSs = (float) NoteGeometry.noteStaffPositionToCoordinateSs(
            breathMark.getStaffPosition(), invariants.getMiddleLineYSs());

        assertThat(drawnYSs)
            .as("the breath mark glyph's baseline Y")
            .containsExactly(expectedYSs);
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
            return dots.getFirst()[0];
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
            assertThat(dots.getFirst()[1]).isCloseTo(NoteGeometry.DOT_ON_LINE_Y_SHIFT_SS, within(TOLERANCE));
        }

        @Test
        void testInSpacePositionHasZeroYOffset() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(1);
            note.setStaffPosition(IN_SPACE_STAFF_POSITION);

            var dots = collectDots(note, false, StaffElement.Direction.UP);
            assertThat(dots).hasSize(1);
            assertThat(dots.getFirst()[1]).isCloseTo(0.0, within(TOLERANCE));
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

        /**
         * Mirrors {@link #buildInvariantsWithStemLayout} but puts the note under a
         * beam, so {@code renderStem} takes its beamed branch. The beam needs a
         * second member to span; only the first note is ever rendered.
         */
        private static LineInvariants buildBeamedInvariantsWithStemLayout(
            StaffElement note, LayoutResult.StemLayout stemLayout
        ) {
            var line = detachedLine();
            var beamPartner = ElementType.QUAVER.newInstance();
            line.addElement(note);
            line.addElement(beamPartner);
            line.addBeaming(new Beam(note, beamPartner));

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
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, forcedShorteningSs, false, 0);

            var renderedLengthSs = renderedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(
                SMuFLConstants.STEM_LENGTH_SS - forcedShorteningSs, within(TOLERANCE));
        }

        @Test
        void testNaturalUpStemDrawsAtFullNaturalLength() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(0);
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, 0.0, false, 0);

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
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, lengtheningSs, forcedShorteningSs, false, 0);

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
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, forcedShorteningSs, false, 0);

            var renderedLengthSs = renderedDownStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(
                SMuFLConstants.STEM_LENGTH_SS - forcedShorteningSs, within(TOLERANCE));
        }

        @Test
        void testNaturalDownStemDrawsAtFullNaturalLength() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(false);
            note.setStaffPosition(0);
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, 0.0, false, 0);

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
                new LayoutResult.StemLayout(0.0, 0.0, 0.0, outOfRangeForcedShorteningSs, false, 0);

            var renderedLengthSs = renderedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs).isCloseTo(NoteGeometry.FORCED_STEM_FLOOR_SS, within(TOLERANCE));
        }

        // How far below FORCED_STEM_FLOOR_SS the beamed stem is driven — comfortably
        // past it, so the assertion cannot pass on rounding alone.
        private static final double SUB_FLOOR_MARGIN_SS = 0.25;

        // Enough forced shortening to drive the stem below FORCED_STEM_FLOOR_SS, which
        // is what quanting does to the inner stems of a concave beam group.
        private static final double SUB_FLOOR_SHORTENING_SS =
            SMuFLConstants.STEM_LENGTH_SS - NoteGeometry.FORCED_STEM_FLOOR_SS + SUB_FLOOR_MARGIN_SS;

        /**
         * The counterpart of {@link #renderedUpStemLengthSs} for a beamed note. The
         * drawn shape stops half a beam short of the logical tip, so the inset is
         * added back to recover the stem length {@code renderStem} settled on.
         */
        private static double renderedBeamedUpStemLengthSs(
            StaffElement note, LayoutResult.StemLayout stemLayout
        ) {
            var invariants = buildBeamedInvariantsWithStemLayout(note, stemLayout);
            var recording = recordingG2();

            NoteRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, note, recording.g2());

            assertThat(recording.filledShapes()).describedAs("stem fill call").hasSize(1);
            return -recording.filledShapes().getFirst().getBounds2D().getMinY() + BEAM_INSET_SS;
        }

        // The quanted beam position is authoritative for a beamed note: LilyPond's
        // quanting legitimately puts a stem below FORCED_STEM_FLOOR_SS, and flooring it
        // back up would push the stem past the beam edge it is supposed to meet. The
        // floor applies only on the unbeamed branch — see the test above, which pins
        // that half of the contract.
        @Test
        void testBeamedStemBelowTheForcedStemFloorIsNotClampedUp() {
            var note = ElementType.QUAVER.newInstance();
            note.setUpper(true);
            note.setStaffPosition(0);
            var stemLayout =
                new LayoutResult.StemLayout(0.0, 0.0, 0.0, SUB_FLOOR_SHORTENING_SS, false, 0);

            var renderedLengthSs = renderedBeamedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs)
                .describedAs("the quanted length survives, rather than being floored")
                .isCloseTo(SMuFLConstants.STEM_LENGTH_SS - SUB_FLOOR_SHORTENING_SS, within(TOLERANCE))
                .isLessThan(NoteGeometry.FORCED_STEM_FLOOR_SS);
        }

        // Guards the same branch from the other side: the beamed path must not be a
        // blanket "ignore forcedShorteningSs" — an ordinary beamed stem that stays
        // above the floor still shortens by exactly what the layout asked for.
        @Test
        void testBeamedStemAboveTheForcedStemFloorStillHonorsForcedShortening() {
            var note = ElementType.QUAVER.newInstance();
            note.setUpper(true);
            note.setStaffPosition(0);
            var forcedShorteningSs = 0.3;
            var stemLayout = new LayoutResult.StemLayout(0.0, 0.0, 0.0, forcedShorteningSs, false, 0);

            var renderedLengthSs = renderedBeamedUpStemLengthSs(note, stemLayout);

            assertThat(renderedLengthSs)
                .isCloseTo(SMuFLConstants.STEM_LENGTH_SS - forcedShorteningSs, within(TOLERANCE))
                .isGreaterThan(NoteGeometry.FORCED_STEM_FLOOR_SS);
        }
    }

    // ==========================================================================
    // renderStem — French beaming pulls inner stems back to the innermost beam
    // ==========================================================================

    @Nested
    class RenderStemFrenchBeaming {

        // Where the stem of an unshortened beamed note ends: the outer beam.
        private static final double OUTER_BEAM_LENGTH_SS = SMuFLConstants.STEM_LENGTH_SS;

        // What LayoutEngine stores for a stem with one beam passing through it, and for
        // one with two. Which stems get which is BeamMathTest's and LayoutEngineTest's
        // subject; here the level count is an input, so the arithmetic can be pinned
        // without building a note group that happens to produce it.
        private static final int ONE_BEAM_THROUGH = 1;
        private static final int TWO_BEAMS_THROUGH = 2;

        // A thickening large enough to be unmistakable in the assertions, and of the
        // same order as the real one (LayoutEngine caps it at BEAM_DEPTH_SS * 0.088).
        private static final double THICKENING_SS = 0.04;

        /**
         * Renders a beamed note whose layout asked for {@code frenchShorteningLevels}
         * levels of French shortening, and returns the length of the stem actually
         * drawn — the beam inset added back, so an unshortened stem measures exactly
         * {@link SMuFLConstants#STEM_LENGTH_SS}.
         *
         * <p>Passing a {@code thickeningSs} of 0 registers no {@link
         * LayoutResult.BeamLayout} at all, matching an unthickened beam.
         */
        private static double renderedStemLengthSs(
            boolean upper, int frenchShorteningLevels, double thickeningSs
        ) {
            var line = detachedLine();
            var note = ElementType.SEMIQUAVER.newInstance();
            note.setUpper(upper);
            note.setStaffPosition(ON_LINE_STAFF_POSITION);
            var beamPartner = ElementType.SEMIQUAVER.newInstance();
            line.addElement(note);
            line.addElement(beamPartner);
            var beam = new Beam(note, beamPartner);
            line.addBeaming(beam);

            var stemLayout =
                new LayoutResult.StemLayout(0.0, 0.0, 0.0, 0.0, false, frenchShorteningLevels);
            var builder = LayoutResult.builder().putStemLayout(note, stemLayout);

            if (thickeningSs != 0.0) {
                builder.putBeamLayout(beam, new LayoutResult.BeamLayout(
                    0.0, 0.0, upper, thickeningSs, Map.of(note, stemLayout)));
            }

            var invariants = RenderContextTestHelper.newContext(new Song())
                .setCurrentLine(line)
                .setLayoutResult(builder.build())
                .build();
            var recording = recordingG2();

            NoteRenderer.getInstance().render(invariants, ElementFrame.LINE_LEVEL, note, recording.g2());

            assertThat(recording.filledShapes()).describedAs("stem fill call").hasSize(1);
            var bounds = recording.filledShapes().getFirst().getBounds2D();

            // Mirrors RenderStemForcedShortening: an up-stem grows toward -Y, a down-stem
            // toward +Y, and either way the drawn end stops one inset short of the tip.
            var drawnLengthSs = upper ? -bounds.getMinY() : bounds.getMaxY();
            return drawnLengthSs + BEAM_INSET_SS + thickeningSs / 2.0;
        }

        private static double renderedUpStemLengthSs(int frenchShorteningLevels) {
            return renderedStemLengthSs(true, frenchShorteningLevels, 0.0);
        }

        @Test
        void testStemWithOneBeamThroughItStopsAtTheSecondaryBeam() {
            assertThat(renderedUpStemLengthSs(ONE_BEAM_THROUGH))
                .describedAs("a stem with one beam through it ends one beam translation early")
                .isCloseTo(
                    OUTER_BEAM_LENGTH_SS - LineThickness.BEAM_TRANSLATION_SS, within(TOLERANCE));
        }

        // The shortening scales with the level count rather than being a fixed one-beam
        // trim; two values are the minimum needed to rule out a hardcoded step.
        @Test
        void testStemWithTwoBeamsThroughItStopsTwoBeamsShort() {
            assertThat(renderedUpStemLengthSs(TWO_BEAMS_THROUGH))
                .describedAs("a stem with two beams through it ends two beam translations early")
                .isCloseTo(
                    OUTER_BEAM_LENGTH_SS - TWO_BEAMS_THROUGH * LineThickness.BEAM_TRANSLATION_SS,
                    within(TOLERANCE));
        }

        // The counterpart of the tests above: a stem the layout did not shorten must
        // render exactly as it did before French beaming existed.
        @Test
        void testUnshortenedStemStillReachesTheOuterBeam() {
            assertThat(renderedUpStemLengthSs(0))
                .describedAs("a stem with no beam through it runs out to the outer beam")
                .isCloseTo(OUTER_BEAM_LENGTH_SS, within(TOLERANCE));
        }

        // Down stems take the other branch of renderStem's direction split, so the
        // shortening has to be pinned there too — the same up/down pairing every test
        // in RenderStemForcedShortening follows.
        @Test
        void testDownStemWithOneBeamThroughItStopsAtTheSecondaryBeam() {
            assertThat(renderedStemLengthSs(false, ONE_BEAM_THROUGH, 0.0))
                .describedAs("an inner down-stem ends one beam translation early")
                .isCloseTo(
                    OUTER_BEAM_LENGTH_SS - LineThickness.BEAM_TRANSLATION_SS, within(TOLERANCE));
        }

        // Thickening widens the gap between stacked beams by the same amount it widens
        // each beam, so the step back must grow with it. Without this the shortening
        // could multiply the unthickened translation and still pass every test above,
        // leaving thickened inner stems hanging short of the beam they should touch.
        @Test
        void testShorteningStepsByTheThickenedTranslation() {
            assertThat(renderedStemLengthSs(true, ONE_BEAM_THROUGH, THICKENING_SS))
                .describedAs("the step back grows with the beam thickening")
                .isCloseTo(
                    OUTER_BEAM_LENGTH_SS - LineThickness.beamTranslationSs(THICKENING_SS),
                    within(TOLERANCE));
        }
    }

    // ==========================================================================
    // renderAccidental — selection color
    // ==========================================================================

    /**
     * An accidental is the one part of a note that can be selected on its own, so it is the one
     * part that may carry a color the rest of the note does not. These tests pin both halves of
     * that: the accidental picks up the selection color, and nothing else does.
     */
    @Nested
    class AccidentalSelectionColor {

        /** Stands in for the color {@code LineRenderer} installs before drawing a note. */
        private static final Color AMBIENT_COLOR = Color.MAGENTA;

        /** A glyph drawn by the note renderer, with the color in force at the time. */
        private record DrawnGlyph(String glyph, Color color) {}

        private List<DrawnGlyph> renderSharpenedNote(boolean accidentalSelected) {
            NoteGeometry.initializeAccidentalWidths();

            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(StaffElement.Accidental.SHARP);
            line.addElement(note);

            var selectionProvider = mock(LineComponent.SelectionProvider.class);
            when(selectionProvider.isSelected(new HitTarget.Accidental(note), 0))
                .thenReturn(accidentalSelected);

            var invariants = RenderContextTestHelper.newContext(new Song())
                .setCurrentLine(line)
                .setSelectionProvider(selectionProvider)
                .build();

            var g2 = spy(RenderContextTestHelper.realG2());
            g2.setColor(AMBIENT_COLOR);

            var drawn = new ArrayList<DrawnGlyph>();
            doAnswer(invocation -> {
                drawn.add(new DrawnGlyph(invocation.getArgument(0), g2.getColor()));
                return null;
            }).when(g2).drawString(anyString(), anyFloat(), anyFloat());

            NoteRenderer.getInstance().render(
                invariants, ElementFrame.LINE_LEVEL.withElement(0, 0.0), note, g2);

            return drawn;
        }

        private static List<Color> colorsOf(List<DrawnGlyph> drawn, SMuFLGlyph glyph) {
            return drawn.stream()
                .filter(entry -> entry.glyph().equals(glyph.asString()))
                .map(DrawnGlyph::color)
                .toList();
        }

        @Test
        void testSelectedAccidentalDrawsInTheSelectionColor() {
            var drawn = renderSharpenedNote(true);

            assertThat(colorsOf(drawn, SMuFLGlyph.ACCIDENTAL_SHARP))
                .as("the selected accidental")
                .containsOnly(ScoreView.getSelectionColor());
        }

        @Test
        void testSelectedAccidentalLeavesTheRestOfTheNoteInTheCallersColor() {
            var drawn = renderSharpenedNote(true);

            assertThat(colorsOf(drawn, SMuFLGlyph.NOTEHEAD_BLACK))
                .as("the note head the caller already colored")
                .containsOnly(AMBIENT_COLOR);
        }

        @Test
        void testUnselectedAccidentalKeepsTheCallersColor() {
            var drawn = renderSharpenedNote(false);

            assertThat(colorsOf(drawn, SMuFLGlyph.ACCIDENTAL_SHARP))
                .as("an unselected accidental")
                .containsOnly(AMBIENT_COLOR);
        }
    }

}
