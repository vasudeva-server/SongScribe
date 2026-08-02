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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Tuplet;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.createNote;

import org.junit.jupiter.api.Nested;

class TupletTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    // Minimum span width enforced by getSpanWidthSs (documented clamp value).
    private static final double MIN_SPAN_WIDTH_SS = 1.0;

    // Grade value for a triplet.
    private static final int TRIPLET_GRADE = 3;

    // Grade value for a quintuplet.
    private static final int QUINTUPLET_GRADE = 5;

    private static Tuplet createTuplet() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        return Tuplet.withUnresolvedRatio(anchor, end, TRIPLET_GRADE);
    }

    @Test
    void testContentHeightSsIsBracketedReservedHeight() {
        var tuplet = createTuplet();

        // Re-derive the expected height from the public constants rather than from
        // bracketedHeightSs(), so the test fails if either the override or the formula changes.
        var expectedHeightSs =
            Tuplet.TUPLET_NUMBER_INK_HEIGHT_SS / 2.0 + Tuplet.BRACKET_ARM_HEIGHT_SS;

        assertThat(tuplet.getContentHeightSs()).isEqualTo(expectedHeightSs);
    }

    @Test
    void testContentHeightPxIsToPixelsOfSs() {
        var tuplet = createTuplet();
        assertThat(tuplet.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(tuplet.getContentHeightSs()), within(EPSILON));
    }

    @Test
    void testNumberOnlyHeightSsIsFullInkHeight() {
        assertThat(Tuplet.numberOnlyHeightSs())
            .isEqualTo(Tuplet.TUPLET_NUMBER_INK_HEIGHT_SS);
    }

    @Test
    void testBracketLineOffsetSsIsHalfInkHeight() {
        assertThat(Tuplet.bracketLineOffsetSs())
            .isCloseTo(Tuplet.TUPLET_NUMBER_INK_HEIGHT_SS / 2.0, within(EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 21 — getSpanWidthSs(): max(1.0, endX−anchorX)
    //          Both branches: clamp (span < 1.0) and geometry (span >= 1.0)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetSpanWidthSs {

        /**
         * When endX - anchorX is less than 1.0, the result must be clamped to 1.0.
         */
        @Test
        void testGetSpanWidthSsClampedToMinimumWhenGeometryIsTooNarrow() {
            var tuplet = createTuplet();

            // anchorX == endX => geometry = 0, which is below the minimum of 1.0.
            var anchorXSs = 2.0;
            var endXSs = 2.0;

            assertThat(endXSs - anchorXSs)
                .as("precondition: geometry width must be less than 1.0 for clamp branch")
                .isLessThan(MIN_SPAN_WIDTH_SS);

            assertThat(tuplet.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(MIN_SPAN_WIDTH_SS, within(EPSILON));
        }

        /**
         * When endX - anchorX exceeds 1.0, the result must equal the geometry.
         */
        @Test
        void testGetSpanWidthSsReturnsGeometryWhenLargerThanMinimum() {
            var tuplet = createTuplet();

            var anchorXSs = 0.0;
            var endXSs = 4.0;
            var expectedWidthSs = endXSs - anchorXSs;

            assertThat(expectedWidthSs)
                .as("precondition: geometry width must exceed 1.0 for geometry branch")
                .isGreaterThan(MIN_SPAN_WIDTH_SS);

            assertThat(tuplet.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(expectedWidthSs, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // bracketLeftEdgeXSs(): where the left arm sits relative to the anchor head
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BracketLeftEdgeXSs {

        private static final double ANCHOR_X_SS = 2.0;

        // A whole note draws no stem, so there is nothing for the arm to sit on and it must clear
        // the head's left edge — the same geometry a down-stem gets. The stored direction is
        // leftover state on a stemless element and must not move the arm; when it did, an UP whole
        // note put the arm flush against the head's right edge instead (#694, phase 6).
        @Test
        void testAStemlessAnchorPutsTheArmOutsideTheHeadLeftEdgeWhateverDirectionItStores() {
            var wholeNoteWidthSs = ElementType.SEMIBREVE.getElementWidthSs();
            var expectedXSs = ANCHOR_X_SS - Tuplet.ARM_EXTENSION_SS;

            for (var storedStemUp : new boolean[]{true, false}) {
                assertThat(Tuplet.bracketLeftEdgeXSs(
                    ANCHOR_X_SS, false, storedStemUp, NoteGeometry.STEM_WIDTH_SS, wholeNoteWidthSs))
                    .as("stemless anchor storing stemUp=%s", storedStemUp)
                    .isCloseTo(expectedXSs, within(EPSILON));
            }
        }

        // A stemmed anchor still distinguishes the two directions: an up-stem hangs off the head's
        // right edge, so the arm insets only the stem thickness and lands further right.
        @Test
        void testAnUpStemAnchorInsetsOnlyTheStemWidth() {
            var crotchetWidthSs = ElementType.CROTCHET.getElementWidthSs();
            var expectedXSs =
                ANCHOR_X_SS + crotchetWidthSs - NoteGeometry.STEM_WIDTH_SS - Tuplet.ARM_EXTENSION_SS;

            assertThat(Tuplet.bracketLeftEdgeXSs(
                ANCHOR_X_SS, true, true, NoteGeometry.STEM_WIDTH_SS, crotchetWidthSs))
                .isCloseTo(expectedXSs, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // bracketRightEdgeXSs(): where the right arm sits relative to the end head
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BracketRightEdgeXSs {

        private static final double END_X_SS = 7.0;

        // The right arm always clears the end note's whole head, whatever direction that note's stem
        // takes, and then extends outward past it.
        @Test
        void testTheArmClearsTheEndHeadAndExtendsPastIt() {
            var crotchetWidthSs = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).right();

            assertThat(Tuplet.bracketRightEdgeXSs(END_X_SS, crotchetWidthSs))
                .isCloseTo(END_X_SS + crotchetWidthSs + Tuplet.ARM_EXTENSION_SS, within(EPSILON));
        }

        // Ending on a whole note pushes the arm further right by exactly the extra head width, since
        // the whole notehead is the wider glyph. Measured with the black-notehead constant instead,
        // the arm landed inside a whole note's ink rather than clear of it (#694).
        @Test
        void testAWiderEndHeadPushesTheArmFurtherRight() {
            var blackRightSs = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).right();
            var wholeRightSs = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_WHOLE).right();

            assertThat(wholeRightSs)
                .as("precondition: the whole notehead really is the wider glyph")
                .isGreaterThan(blackRightSs);
            assertThat(Tuplet.bracketRightEdgeXSs(END_X_SS, wholeRightSs)
                - Tuplet.bracketRightEdgeXSs(END_X_SS, blackRightSs))
                .describedAs("the arm moves right by the whole head's extra width, nothing else")
                .isCloseTo(wholeRightSs - blackRightSs, within(EPSILON));
        }

        // A grace note's head is narrower than a full-size one, so its arm sits further left —
        // the same per-type measurement working in the other direction.
        @Test
        void testANarrowerGraceEndHeadPullsTheArmLeft() {
            var graceWidthSs = ElementType.GRACE_QUAVER.getElementWidthSs();
            var crotchetWidthSs = ElementType.CROTCHET.getElementWidthSs();

            assertThat(Tuplet.bracketRightEdgeXSs(END_X_SS, graceWidthSs))
                .isLessThan(Tuplet.bracketRightEdgeXSs(END_X_SS, crotchetWidthSs))
                .isCloseTo(END_X_SS + graceWidthSs + Tuplet.ARM_EXTENSION_SS, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // isNumberOnly(Line): true only when beamed at both ends with upward stems
    // -------------------------------------------------------------------------

    @SuppressWarnings({ "PackageVisibleInnerClass", "NullAway", "DataFlowIssue" })
    @Nested
    class IsNumberOnly {

        // Builds a line of two notes joined by a beam, with a tuplet spanning them.
        private Tuplet beamedTuplet(Line line, boolean upper) {
            var note1 = createNote(0, upper);
            var note2 = createNote(0, upper);
            line.addElement(note1);
            line.addElement(note2);
            line.addSpan(new Beam(note1, note2));

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, TRIPLET_GRADE);
            line.addSpan(tuplet);
            return tuplet;
        }

        @Test
        void testTrueWhenBeamedAtBothEndsWithUpwardStems() {
            var line = detachedLine();
            var tuplet = beamedTuplet(line, true);

            assertThat(tuplet.isNumberOnly(line)).isTrue();
        }

        @Test
        void testFalseWhenBeamedButStemsDown() {
            var line = detachedLine();
            var tuplet = beamedTuplet(line, false);

            assertThat(tuplet.isNumberOnly(line)).isFalse();
        }

        @Test
        void testFalseWhenNotBeamed() {
            var line = detachedLine();
            var note1 = createNote(0, true);
            var note2 = createNote(0, true);
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, TRIPLET_GRADE);
            line.addSpan(tuplet);

            assertThat(tuplet.isNumberOnly(line)).isFalse();
        }

        @Test
        void testFalseWhenEndNotBeamed() {
            var line = detachedLine();
            var note1 = createNote(0, true);
            var note2 = createNote(0, true);
            var note3 = createNote(0, true);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);

            // Beam covers the anchor (index 0) but not the tuplet end (index 2).
            line.addSpan(new Beam(note1, note2));

            var tuplet = Tuplet.withUnresolvedRatio(note1, note3, TRIPLET_GRADE);
            line.addSpan(tuplet);

            assertThat(tuplet.isNumberOnly(line)).isFalse();
        }

        @Test
        void testFalseWhenAnchorMissing() {
            var tuplet = Tuplet.withUnresolvedRatio(null, null, TRIPLET_GRADE);

            assertThat(tuplet.isNumberOnly(detachedLine())).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // createCopy() must carry every field — undo/redo round-trips through it,
    // so a missed field is silent data loss.
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CreateCopy {

        // A non-default ratio with a dotted written value, so no field can be
        // mistaken for another field's default.
        private static final int COPIED_NORMAL_NOTES = 4;
        private static final int COPIED_NOTE_VALUE_DOTS = 1;
        private static final int COPIED_VERTICAL_POSITION_SS = 3;

        @Test
        void testCopyCarriesEveryField() {
            var original = new Tuplet(
                ElementType.QUAVER.newInstance(),
                ElementType.QUAVER.newInstance(),
                QUINTUPLET_GRADE,
                COPIED_NORMAL_NOTES,
                ElementType.CROTCHET,
                COPIED_NOTE_VALUE_DOTS);
            original.setVerticalPositionSs(COPIED_VERTICAL_POSITION_SS);

            var copy = (Tuplet) original.copy(
                ElementType.QUAVER.newInstance(), ElementType.QUAVER.newInstance());

            assertThat(copy.getGrade()).isEqualTo(QUINTUPLET_GRADE);
            assertThat(copy.getNormalNotes()).isEqualTo(COPIED_NORMAL_NOTES);
            assertThat(copy.getNoteValue()).isEqualTo(ElementType.CROTCHET);
            assertThat(copy.getNoteValueDots()).isEqualTo(COPIED_NOTE_VALUE_DOTS);
            assertThat(copy.getVerticalPositionSs()).isEqualTo(COPIED_VERTICAL_POSITION_SS);
        }

        @Test
        void testCopyOfUnresolvedTupletIsUnresolved() {
            var original = Tuplet.withUnresolvedRatio(
                ElementType.QUAVER.newInstance(),
                ElementType.QUAVER.newInstance(),
                TRIPLET_GRADE);

            var copy = (Tuplet) original.copy(
                ElementType.QUAVER.newInstance(), ElementType.QUAVER.newInstance());

            assertThat(copy.isResolved()).isFalse();
            assertThat(copy.getNormalNotes()).isEqualTo(Tuplet.UNRESOLVED_NORMAL_NOTES);
            assertThat(copy.getNoteValue()).isNull();
            assertThat(copy.getGrade()).isEqualTo(TRIPLET_GRADE);
        }
    }
}
