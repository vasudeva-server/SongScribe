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
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;

class TieTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    // The minimum span width enforced by the production Math.max clamp.
    private static final double MIN_SPAN_WIDTH_SS = 1.0;

    // Anchor X used for span-width tests.
    private static final double ANCHOR_X_SS = 5.0;

    // end−anchor = 0.5, below the clamp floor.
    private static final double END_X_BELOW_FLOOR_SS = ANCHOR_X_SS + 0.5;

    // end−anchor = 1.0, exactly at the clamp floor.
    private static final double END_X_AT_FLOOR_SS = ANCHOR_X_SS + MIN_SPAN_WIDTH_SS;

    // end−anchor = 3.0, above the clamp floor.
    private static final double END_X_ABOVE_FLOOR_SS = ANCHOR_X_SS + 3.0;

    private static Tie createTie() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        return new Tie(anchor, end);
    }

    @Test
    void testContentHeightPxIsToPixelsOfSs() {
        var tie = createTie();
        assertThat(tie.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(Tie.TIE_ARC_HEIGHT_SS),
                within(EPSILON));
    }

    @Test
    void testContentHeightSsMatchesStylesheetConstant() {
        var tie = createTie();

        assertThat(tie.getContentHeightSs())
            .isEqualTo(Tie.TIE_ARC_HEIGHT_SS);
    }

    // -----------------------------------------------------------------------
    // Row 47 — getSpanWidthSs: Math.max(MIN_SPAN_WIDTH_SS, end−anchor) clamp
    // -----------------------------------------------------------------------

    @Test
    void testGetSpanWidthSsReturnsClamFloorWhenDifferenceIsBelowFloor() {
        var tie = createTie();

        assertThat(tie.getSpanWidthSs(ANCHOR_X_SS, END_X_BELOW_FLOOR_SS))
            .isCloseTo(MIN_SPAN_WIDTH_SS, within(EPSILON));
    }

    @Test
    void testGetSpanWidthSsReturnsClamFloorWhenDifferenceEqualsFloor() {
        var tie = createTie();

        assertThat(tie.getSpanWidthSs(ANCHOR_X_SS, END_X_AT_FLOOR_SS))
            .isCloseTo(MIN_SPAN_WIDTH_SS, within(EPSILON));
    }

    @Test
    void testGetSpanWidthSsReturnsDifferenceWhenAboveFloor() {
        var tie = createTie();
        var expectedSpan = END_X_ABOVE_FLOOR_SS - ANCHOR_X_SS;

        assertThat(tie.getSpanWidthSs(ANCHOR_X_SS, END_X_ABOVE_FLOOR_SS))
            .isCloseTo(expectedSpan, within(EPSILON));
    }

    // -----------------------------------------------------------------------
    // Phase 5 — arcSign()/isAbove(): every branch of the both-stem fallthrough
    // tree (Tie.arcSign() Javadoc). arcSign() > 0 == arc bulges down == below;
    // isAbove() == arcSign() < 0. A note type without a stem (SEMIBREVE) drives
    // the no-stem branches; ElementType.QUAVER (has a stem) drives the rest.
    // -----------------------------------------------------------------------

    // Sign convention constants — named per "No Magic Numbers", not re-derived per branch.
    private static final int ARC_SIGN_ABOVE = -1;
    private static final int ARC_SIGN_BELOW = 1;

    // Staff positions (Y-down; 0 = middle line) for the no-stem branch.
    private static final int SP_ABOVE_MIDDLE_LINE = -2;
    private static final int SP_BELOW_MIDDLE_LINE = 2;
    private static final int SP_ON_MIDDLE_LINE = 0;

    private static StaffElement noteWithStem(StaffElement.Direction direction) {
        var note = ElementType.QUAVER.newInstance();
        note.setDirection(direction);
        return note;
    }

    private static StaffElement noteWithoutStem(int staffPosition) {
        var note = ElementType.SEMIBREVE.newInstance();
        note.setStaffPosition(staffPosition);
        return note;
    }

    @Test
    void testBothStemsUpArcsBelow() {
        var tie = new Tie(noteWithStem(StaffElement.Direction.UP), noteWithStem(StaffElement.Direction.UP));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_BELOW);
        assertThat(tie.isAbove()).isFalse();
    }

    @Test
    void testBothStemsDownArcsAbove() {
        var tie = new Tie(noteWithStem(StaffElement.Direction.DOWN), noteWithStem(StaffElement.Direction.DOWN));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testConflictingStemsLeftUpRightDownArcsAbove() {
        var tie = new Tie(noteWithStem(StaffElement.Direction.UP), noteWithStem(StaffElement.Direction.DOWN));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testConflictingStemsLeftDownRightUpArcsAbove() {
        var tie = new Tie(noteWithStem(StaffElement.Direction.DOWN), noteWithStem(StaffElement.Direction.UP));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testLeftOnlyStemUpArcsBelow() {
        var tie = new Tie(noteWithStem(StaffElement.Direction.UP), noteWithoutStem(SP_ON_MIDDLE_LINE));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_BELOW);
        assertThat(tie.isAbove()).isFalse();
    }

    @Test
    void testLeftOnlyStemDownArcsAbove() {
        var tie = new Tie(noteWithStem(StaffElement.Direction.DOWN), noteWithoutStem(SP_ON_MIDDLE_LINE));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testRightOnlyStemUpArcsBelow() {
        var tie = new Tie(noteWithoutStem(SP_ON_MIDDLE_LINE), noteWithStem(StaffElement.Direction.UP));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_BELOW);
        assertThat(tie.isAbove()).isFalse();
    }

    @Test
    void testRightOnlyStemDownArcsAbove() {
        var tie = new Tie(noteWithoutStem(SP_ON_MIDDLE_LINE), noteWithStem(StaffElement.Direction.DOWN));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testNoStemAboveMiddleLineArcsAbove() {
        var tie = new Tie(noteWithoutStem(SP_ABOVE_MIDDLE_LINE), noteWithoutStem(SP_ABOVE_MIDDLE_LINE));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testNoStemBelowMiddleLineArcsBelow() {
        var tie = new Tie(noteWithoutStem(SP_BELOW_MIDDLE_LINE), noteWithoutStem(SP_BELOW_MIDDLE_LINE));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_BELOW);
        assertThat(tie.isAbove()).isFalse();
    }

    @Test
    void testNoStemOnMiddleLineArcsAbove() {
        var tie = new Tie(noteWithoutStem(SP_ON_MIDDLE_LINE), noteWithoutStem(SP_ON_MIDDLE_LINE));

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testNullAnchorElementFallsBackToNeutralDefaultArcsAbove() {
        var tie = createTie();
        tie.setAnchorElement(null);

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testNullEndElementFallsBackToNeutralDefaultArcsAbove() {
        var tie = createTie();
        tie.setEndElement(null);

        assertThat(tie.arcSign()).isEqualTo(ARC_SIGN_ABOVE);
        assertThat(tie.isAbove()).isTrue();
    }
}
