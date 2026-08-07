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

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.font.DocumentFonts;

/**
 * Layout geometry for a tie whose anchor and end sit in different lines (#493's phases 9a/9b),
 * asserting the three facts from those phases' LilyPond findings that are values rather than
 * pixels — see the comment block at {@link LayoutEngine#calculateTies}. Appearance is confirmed
 * in phase 13; these three must hold regardless of what anyone looking at the rendered page
 * would see.
 */
class CrossLineTieLayoutTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;

    // A staff too narrow for the fixture's own content, which leaves its columns on their
    // collision floors so the closing barline ends up reaching the staff's end rather than
    // standing short of it.
    private static final double FLUSH_STAFF_RIGHT_MARGIN_SS = 12.0;

    // Extra untied notes prepended to the first line ahead of the anchor. Used to move the
    // anchor's own X far from where it sits in the baseline fixture without touching the second
    // line at all, so the width-independence test can tell a real dependency on the far line's
    // note position apart from mere coincidence.
    private static final int EXTRA_NOTES_BEFORE_ANCHOR = 3;

    private static LayoutEngine engine() {
        return engine(STAFF_RIGHT_MARGIN_SS);
    }

    private static LayoutEngine engine(double staffRightMarginSs) {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var metrics = LyricRenderMetrics.forFont(lyricsFont);
        return new LayoutEngine(metrics, staffRightMarginSs, DocumentFonts.defaultFonts());
    }

    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    /**
     * A tie whose anchor is the last element of {@code firstLine} and whose end is the first
     * element of {@code secondLine} — the shape a cross-line tie always has (#493).
     */
    private record CrossLineFixture(Song song, Line firstLine, Line secondLine, Tie tie) {

        /**
         * @param notesBeforeAnchor how many untied crotchets precede the anchor in the first
         *                          line; varying this moves the anchor's own X without touching
         *                          the second line at all, which is what the width-independence
         *                          test needs.
         */
        static CrossLineFixture create(int notesBeforeAnchor) {
            return create(notesBeforeAnchor, null);
        }

        /**
         * @param trailingType an element appended to the first line after the anchor, or null to
         *                     leave the anchor last. A barline may legally sit between two tied
         *                     notes, so this is a state a cross-line tie really reaches.
         */
        static CrossLineFixture create(int notesBeforeAnchor, @Nullable ElementType trailingType) {
            var song = new Song();
            var firstLine = song.getLine(0);
            var secondLine = new Line(song);
            var anchor = ElementType.CROTCHET.newInstance();
            var end = ElementType.CROTCHET.newInstance();
            var tie = new Tie(anchor, end);

            song.withoutMutationTracking(() -> {
                // Song() leaves an auto-added terminal barline on the line it creates, and
                // mutation tracking being suspended here means addLine never migrates it to the
                // new last line. Drop it, so the first line ends with the anchor — the shape a
                // cross-line tie is actually created in, the toggle being offered only when the
                // anchor is its line's last element.
                firstLine.removeElement(0);

                for (var i = 0; i < notesBeforeAnchor; i++) {
                    firstLine.addElement(ElementType.CROTCHET.newInstance());
                }

                firstLine.addElement(anchor);
                song.addLine(secondLine);
                secondLine.addElement(end);
                secondLine.addElement(ElementType.CROTCHET.newInstance());
                firstLine.addTie(tie);

                if (trailingType != null) {
                    firstLine.addElement(trailingType.newInstance());
                }
            });

            return new CrossLineFixture(song, firstLine, secondLine, tie);
        }
    }

    private static LayoutResult.TieLayout tieLayoutFor(Line line, Tie tie) {
        return tieLayoutFor(line, tie, STAFF_RIGHT_MARGIN_SS);
    }

    private static LayoutResult.TieLayout tieLayoutFor(Line line, Tie tie, double staffRightMarginSs) {
        var result = require(engine(staffRightMarginSs).layout(line), "LayoutResult");
        return require(result.getTieLayout(tie), "TieLayout");
    }

    /** The right edge of the column laid out for {@code line}'s last element. */
    private static double lastColumnRightEdgeXSs(Line line, double staffRightMarginSs) {
        var result = require(engine(staffRightMarginSs).layout(line), "LayoutResult");
        var lastElement = line.getElement(line.elementCount() - 1);
        return require(result.getElementColumn(lastElement), "ElementColumn").getRightEdgeXSs();
    }

    @Test
    void testBothHalvesShareOneArcSign() {
        var fixture = CrossLineFixture.create(0);
        var anchorHalf = tieLayoutFor(fixture.firstLine(), fixture.tie());
        var endHalf = tieLayoutFor(fixture.secondLine(), fixture.tie());

        // The outer control point sits on the bulge side of the baseline; the sign of that
        // offset is the arc's direction. arcSign() reads the notehead elements themselves rather
        // than either line's columns (LayoutEngine.tieLayout's Javadoc), so both halves must
        // land on the same side even though they come from two independent calls to
        // LayoutEngine.layout with no shared state between them.
        var anchorHalfBulgeSign = Math.signum(anchorHalf.cp1YSs() - anchorHalf.startYSs());
        var endHalfBulgeSign = Math.signum(endHalf.cp1YSs() - endHalf.startYSs());

        assertThat(anchorHalfBulgeSign)
            .as("both halves of a broken tie bulge on the same side of the baseline")
            .isNotZero()
            .isEqualTo(endHalfBulgeSign);
    }

    @Test
    void testEachHalfsWidthIsItsOwnSpanNotTheNotionalWhole() {
        var baseline = CrossLineFixture.create(0);
        var shiftedAnchor = CrossLineFixture.create(EXTRA_NOTES_BEFORE_ANCHOR);

        var anchorHalf = tieLayoutFor(baseline.firstLine(), baseline.tie());
        var endHalfBaseline = tieLayoutFor(baseline.secondLine(), baseline.tie());
        var endHalfShifted = tieLayoutFor(shiftedAnchor.secondLine(), shiftedAnchor.tie());

        // The open end of the anchor's own half runs all the way to this line's own right staff
        // edge, not to wherever the end note happens to sit in the other line's coordinate
        // frame — this line never even resolves a column for it.
        assertThat(anchorHalf.endXSs())
            .as("the anchor half's open end is this line's own staff-right margin, less the gap")
            .isEqualTo(STAFF_RIGHT_MARGIN_SS - LayoutEngine.NOTE_HEAD_GAP_SS);

        // Moving the anchor three notes further into the first line changes that line's own
        // spacing drastically, but the second line's half is computed only from the end note's
        // own column and its own line's left edge, so it must come out identical either way. If
        // it instead depended on a notional whole-tie width reaching back to wherever the anchor
        // sits, this would change too.
        assertThat(endHalfShifted.startXSs())
            .as("the end half's own width does not depend on where the anchor sits")
            .isEqualTo(endHalfBaseline.startXSs());
        assertThat(endHalfShifted.endXSs())
            .isEqualTo(endHalfBaseline.endXSs());
    }

    @Test
    void testEachHalfsCurveReturnsToTheBaselineAtItsOpenEnd() {
        var fixture = CrossLineFixture.create(0);
        var anchorHalf = tieLayoutFor(fixture.firstLine(), fixture.tie());
        var endHalf = tieLayoutFor(fixture.secondLine(), fixture.tie());

        assertThat(anchorHalf.openSide()).isEqualTo(OpenSide.END);
        assertThat(endHalf.openSide()).isEqualTo(OpenSide.START);

        // slur_shape's control points put both ends of every half back on the baseline —
        // (0,0)...(w,0) in LilyPond's own coordinates — so the open end meets the staff edge
        // flat, not mid-arc with a slope left hanging. The anchor half's open end is its endYSs;
        // the end half's open end is its startYSs.
        assertThat(anchorHalf.endYSs())
            .as("the anchor half's open (right) end returns to the same Y as its attached end")
            .isEqualTo(anchorHalf.startYSs());
        assertThat(endHalf.startYSs())
            .as("the end half's open (left) end returns to the same Y as its attached end")
            .isEqualTo(endHalf.endYSs());

        // Both halves seat the same-pitch note at the same absolute Y, so the baseline the open
        // end returns to is the same baseline the other half's attached end sits at too.
        assertThat(anchorHalf.startYSs()).isEqualTo(endHalf.endYSs());
    }

    @Test
    void testTheContinuationHalfBeginsWhereTheMusicDoesNotAtTheStaffsOwnLeftEdge() {
        var fixture = CrossLineFixture.create(0);
        var endHalf = tieLayoutFor(fixture.secondLine(), fixture.tie());
        var headerRightEdgeSs =
            HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(fixture.secondLine());

        // Taken to X 0 the arc would be drawn straight across the clef and key signature. It
        // starts at the header's right edge instead, standing off it by the same note-head gap
        // an attached end stands off its notehead — LilyPond applies that gap in one step to
        // both ends without asking whether either is a notehead.
        assertThat(headerRightEdgeSs)
            .as("the fixture's line has a header to clear, or this test proves nothing")
            .isGreaterThan(0.0);
        assertThat(endHalf.startXSs())
            .as("the continuation half starts a note-head gap past the header's right edge")
            .isEqualTo(headerRightEdgeSs + LayoutEngine.NOTE_HEAD_GAP_SS);
    }

    @Test
    void testTheAnchorHalfCarriesOverAClosingBarlineThatStandsShortOfTheStaffsEnd() {
        var fixture = CrossLineFixture.create(0, ElementType.SINGLE_BARLINE);
        var firstLine = fixture.firstLine();
        var anchorHalf = tieLayoutFor(firstLine, fixture.tie());
        var barline = firstLine.getElement(firstLine.elementCount() - 1);

        // A barline is a legal separator, so it may be inserted between the two tied notes
        // without breaking the tie — which puts one at the end of the anchor's line. Only the
        // last line's terminal is snapped flush right, so this one sits well short of the margin
        // with open staff to the right of it; stopping the arc at its left edge would end the
        // half mid-staff instead of at the edge the reader's eye leaves the line by.
        assertThat(barline.getType().isBarLine())
            .as("the fixture's first line really does end with a barline")
            .isTrue();
        assertThat(lastColumnRightEdgeXSs(firstLine, STAFF_RIGHT_MARGIN_SS))
            .as("that barline really does stand short of the staff's end")
            .isLessThan(STAFF_RIGHT_MARGIN_SS);
        assertThat(anchorHalf.endXSs())
            .as("the anchor half runs over the barline to the staff-right margin")
            .isEqualTo(STAFF_RIGHT_MARGIN_SS - LayoutEngine.NOTE_HEAD_GAP_SS);
    }

    @Test
    void testTheAnchorHalfStopsShortOfAClosingBarlineFlushWithTheStaffsEnd() {
        var fixture = CrossLineFixture.create(0, ElementType.SINGLE_BARLINE);
        var firstLine = fixture.firstLine();
        var anchorHalf = tieLayoutFor(fixture.firstLine(), fixture.tie(), FLUSH_STAFF_RIGHT_MARGIN_SS);
        var barline = firstLine.getElement(firstLine.elementCount() - 1);
        var result = require(engine(FLUSH_STAFF_RIGHT_MARGIN_SS).layout(firstLine), "LayoutResult");
        var barlineXSs = result.getElementXSs(barline);

        // A staff too narrow for its own content leaves the columns on their collision floors, so
        // the closing barline reaches the end of the staff — the shape a flush-right terminal
        // barline has, and the one arrangement with no open staff to carry the arc over into.
        // There the arc must stop at the barline's left edge, or it would be drawn through the
        // glyph and off the end of the staff.
        assertThat(lastColumnRightEdgeXSs(firstLine, FLUSH_STAFF_RIGHT_MARGIN_SS))
            .as("the closing barline really does reach the staff's end")
            .isGreaterThanOrEqualTo(FLUSH_STAFF_RIGHT_MARGIN_SS);
        assertThat(anchorHalf.endXSs())
            .as("the anchor half stops a note-head gap short of the flush closing barline")
            .isEqualTo(barlineXSs - LayoutEngine.NOTE_HEAD_GAP_SS);
    }
}
