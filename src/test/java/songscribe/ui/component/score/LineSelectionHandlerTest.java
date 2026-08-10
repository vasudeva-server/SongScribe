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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
// Disambiguates from java.awt.List (java.desktop module)
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Ending;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.engraving.Staff;
import songscribe.font.DocumentFonts;
import songscribe.hit.HitPriority;
import songscribe.hit.HitRegion;
import org.jspecify.annotations.Nullable;

import songscribe.hit.HitRegistry;
import songscribe.ui.selection.Selection;
import songscribe.hit.HitTarget;
import songscribe.layout.ColumnSpan;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementColumnTestHelper;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineSelectionHandler.SelectionBand;
import songscribe.ui.playback.MidiController;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link LineSelectionHandler}.
 *
 * <p>Hit resolution itself belongs to {@link HitRegistry} and the layout-time registrations
 * ({@code HitRegistryTest}, {@code HitRegionBuilderTest} own those). What is tested here is
 * what this class actually does with a registry: convert a click point into layout space,
 * refuse to query at all when the component is not ready, and act on the answer. Tests
 * therefore hand the component a hand-built one-region registry rather than real geometry.
 *
 * <p>{@code calculateLineSelectionFromDrag} is tested through
 * {@link LineSelectionHandler#handleDrag} (its only caller), using a real {@link Song} /
 * {@link Line}, with mouse coordinates built from staff spaces through {@link #viewPx} so the
 * band/column-span overlap can be verified numerically.
 *
 * <p>Unless a test calls {@link #givenZoomedView}, the component reports
 * {@link ViewScale#IDENTITY} — 100% zoom, where the zoom factor is 1.0 and therefore invisible
 * to the arithmetic. The two tests that do call it are the only ones that can tell a correctly
 * applied zoom factor from a dropped one.
 */
class LineSelectionHandlerTest extends UnitTest {

    /**
     * Distance from the top of the component to the staff midline, in staff spaces. Non-zero
     * on purpose: layout space measures Y from the midline, so a handler that forgot to
     * subtract this would still pass every test with a zero midline.
     */
    private static final double MIDDLE_LINE_Y_SS = 5.0;

    /**
     * A view-pixel Y that lands exactly on the staff midline, and therefore on layout Y = 0.
     */
    private static final int MIDLINE_Y_PX = viewPx(MIDDLE_LINE_Y_SS);

    /** A view-pixel X at the left edge of the line, on layout X = 0. */
    private static final int ORIGIN_X_PX = 0;

    /** Side of a hand-built test hit region, in staff spaces. */
    private static final double REGION_SIDE_SS = 4;

    /** Verse number used by lyric fixtures, chosen to not be verse 1. */
    private static final int LYRIC_VERSE = 2;

    /** Three notes in the time of two — the tuplet in the unhandled-target list. */
    private static final int TRIPLET_GRADE = 3;

    /** Staff width the end-to-end fixtures' real {@link LayoutEngine} lays out against. */
    private static final double END_TO_END_STAFF_RIGHT_MARGIN_SS = 60.0;

    /** Point size of the lyric font the end-to-end fixtures' real {@link LayoutEngine} needs. */
    private static final int END_TO_END_LYRIC_FONT_SIZE_PX = 12;

    private LineComponent lc;
    private ScoreView mockScoreView;
    private LayoutResult mockLayout;
    private LineSelectionHandler handler;

    @BeforeEach
    void setUp() {
        lc = mock(LineComponent.class);
        mockScoreView = mock(ScoreView.class);
        mockLayout = mock(LayoutResult.class);

        when(lc.getScoreView()).thenReturn(mockScoreView);
        when(lc.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lc.getLine()).thenReturn(null);
        when(lc.getMiddleLineYSs()).thenReturn(MIDDLE_LINE_Y_SS);

        // Nothing is clickable until a test registers something.
        when(mockLayout.getHitRegistry()).thenReturn(HitRegistry.EMPTY);

        handler = new LineSelectionHandler(lc);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * Registers a two-note line, its selection state and a ready layout on the component —
     * everything {@code readyRegistry} requires before a query runs.
     */
    private Line givenLine() {
        return givenLine(ElementType.CROTCHET, ElementType.CROTCHET);
    }

    private Line givenLine(ElementType first, ElementType second) {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            line.addElement(first.newInstance());
            line.addElement(second.newInstance());
        });
        when(lc.getLine()).thenReturn(line);
        when(lc.readyLayout()).thenReturn(new LineComponent.ReadyLayout(line, mockLayout));
        when(lc.getLayoutResult()).thenReturn(mockLayout);
        return line;
    }

    /**
     * Makes {@code target} the only clickable thing on the line, occupying a small region
     * centered on the layout-space origin — the point {@link #MIDLINE_Y_PX} maps to.
     */
    private void givenClickableAtOrigin(HitTarget target, int priority, boolean hoverTestable) {
        when(mockLayout.getHitRegistry()).thenReturn(
            HitRegistry.builder()
                .add(
                    new Rectangle2D.Double(
                        -REGION_SIDE_SS / 2, -REGION_SIDE_SS / 2, REGION_SIDE_SS, REGION_SIDE_SS),
                    target,
                    priority,
                    hoverTestable)
                .build());
    }

    /** Builds a standalone {@link Ending}; targets carry it by reference only. */
    private static Ending newEnding() {
        return new Ending(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance());
    }

    /** Builds a standalone {@link Hairpin}; targets carry it by reference only. */
    private static Hairpin newHairpin() {
        return new Crescendo(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance());
    }

    /** A press at the layout-space origin, where {@link #givenClickableAtOrigin} puts its region. */
    private Point originPoint() {
        return new Point(ORIGIN_X_PX, MIDLINE_Y_PX);
    }

    // -------------------------------------------------------------------------
    // hitTest — coordinate conversion and readiness guards
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HitTest {

        /** A Y far enough from the midline that no test region can reach it. */
        private static final int Y_FAR_FROM_MIDLINE_PX = 1000;

        @BeforeEach
        void configureCommonStubs() {
            givenLine();
        }

        @Test
        void testPointInsideARegionResolvesToItsTarget() {
            var ending = newEnding();
            givenClickableAtOrigin(new HitTarget.Ending(ending), HitPriority.ENDING, false);

            assertThat(handler.hitTestViewPoint(originPoint())).isEqualTo(new HitTarget.Ending(ending));
        }

        @Test
        void testPointOutsideEveryRegionResolvesToNull() {
            givenClickableAtOrigin(new HitTarget.Ending(newEnding()), HitPriority.ENDING, false);

            assertThat(handler.hitTestViewPoint(new Point(ORIGIN_X_PX, Y_FAR_FROM_MIDLINE_PX))).isNull();
        }

        /**
         * The registry measures Y from the staff midline; a document point measures it from
         * the top of the component. Getting this subtraction wrong shifts every clickable
         * area by the height of the staff's upper half, which no priority test would catch:
         * the answers would simply be wrong everywhere at once.
         */
        @Test
        void testYIsMeasuredFromTheStaffMidlineRatherThanTheTopOfTheComponent() {
            var ending = newEnding();
            givenClickableAtOrigin(new HitTarget.Ending(ending), HitPriority.ENDING, false);

            assertThat(handler.hitTestViewPoint(new Point(ORIGIN_X_PX, MIDLINE_Y_PX)))
                .as("a point on the midline maps to layout Y = 0")
                .isEqualTo(new HitTarget.Ending(ending));
            assertThat(handler.hitTestViewPoint(new Point(ORIGIN_X_PX, 0)))
                .as("a point at the top of the component is above the region")
                .isNull();
        }

        /**
         * At 100% zoom the zoom factor is 1.0, so a conversion that dropped it, inverted it or
         * squared it would answer exactly as a correct one does. Zooming to 200% separates them:
         * the region now sits at twice the view pixel, and the pixel it occupied at 100% no
         * longer reaches it. Without this the user-visible failure — clicks landing on a
         * neighbouring note, or on nothing, whenever the score is zoomed — would go unnoticed.
         */
        @Test
        void testAtNonDefaultZoomAPointResolvesAgainstTheZoomedGeometry() {
            var ending = newEnding();
            givenClickableAtOrigin(new HitTarget.Ending(ending), HitPriority.ENDING, false);
            givenZoomedView();

            assertThat(handler.hitTestViewPoint(
                new Point(ORIGIN_X_PX, zoomedViewPx(MIDDLE_LINE_Y_SS))))
                .as("zoomed 2x, the midline sits at twice the view pixel")
                .isEqualTo(new HitTarget.Ending(ending));
            assertThat(handler.hitTestViewPoint(new Point(ORIGIN_X_PX, MIDLINE_Y_PX)))
                .as("the pixel the midline occupied at 100% is now above the region")
                .isNull();
        }

        @Test
        void testNoReadyLayoutResolvesToNull() {
            givenClickableAtOrigin(new HitTarget.Ending(newEnding()), HitPriority.ENDING, false);
            when(lc.readyLayout()).thenReturn(null);

            assertThat(handler.hitTestViewPoint(originPoint())).isNull();
        }

        // Each half of the readiness guard is checked on its own, because a test that nulls
        // both at once passes just as happily if the guard's "or" is ever changed to an "and".
        @Test
        void testNoLineResolvesToNull() {
            givenClickableAtOrigin(new HitTarget.Ending(newEnding()), HitPriority.ENDING, false);
            when(lc.getLine()).thenReturn(null);

            assertThat(handler.hitTestViewPoint(originPoint())).isNull();
        }

        /**
         * The layout is what carries the registry, so it has to be brought up to date before
         * the query rather than after it. Without this a click resolves against the regions
         * of whatever the line looked like before the last edit.
         */
        @Test
        void testTheLayoutIsMadeReadyBeforeTheRegistryIsRead() {
            handler.hitTestViewPoint(originPoint());

            // Ordered, not just "both happened": a handler that read a stale registry first and
            // refreshed the layout afterwards would satisfy two unordered verifications.
            var inOrder = inOrder(lc, mockLayout);
            inOrder.verify(lc).readyLayout();
            inOrder.verify(mockLayout).getHitRegistry();
        }
    }

    // -------------------------------------------------------------------------
    // hitTestLyricViewPoint — the hover query
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HitTestLyricViewPoint {

        private Line line;

        @BeforeEach
        void configureCommonStubs() {
            line = givenLine();
        }

        @Test
        void testPointOnALyricReturnsThatLyric() {
            var element = line.getElement(0);
            givenClickableAtOrigin(new HitTarget.Lyric(element, LYRIC_VERSE), HitPriority.LYRIC, true);

            assertThat(handler.hitTestLyricViewPoint(originPoint()))
                .isEqualTo(new HitTarget.Lyric(element, LYRIC_VERSE));
        }

        /**
         * Only lyric regions are hover-testable, so nothing else can answer here even when it
         * covers the point. Without this the mouse-move path would clear the insertion preview
         * over an ending or a note head.
         */
        @Test
        void testPointOnANonLyricTargetReturnsNull() {
            givenClickableAtOrigin(new HitTarget.Ending(newEnding()), HitPriority.ENDING, false);

            assertThat(handler.hitTestLyricViewPoint(originPoint())).isNull();
        }

        @Test
        void testNoReadyLayoutReturnsNull() {
            givenClickableAtOrigin(
                new HitTarget.Lyric(line.getElement(0), LYRIC_VERSE), HitPriority.LYRIC, true);
            when(lc.readyLayout()).thenReturn(null);

            assertThat(handler.hitTestLyricViewPoint(originPoint())).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // isWithinHeaderX
    // -------------------------------------------------------------------------

    /**
     * The header is the clef and key signature at the left of the staff. Nothing can be
     * inserted there, so edit mode uses this query to suppress the insertion preview and
     * click-to-insert while the pointer is over it. Get it wrong and a user in edit mode sees
     * the preview note drawn on top of the clef, or clicks just past the clef and nothing
     * appears.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IsWithinHeaderX {

        /**
         * How far either side of the header's right edge the probes sit. A whole staff space is
         * comfortably more than the half-pixel that rounding a probe to a whole view pixel can
         * move it, so neither probe can drift across the edge it is meant to be on.
         */
        private static final double EDGE_MARGIN_SS = 1;

        /** A Y below the staff entirely, around where the lyric row sits. */
        private static final int Y_WELL_BELOW_STAFF_PX = 1000;

        private double headerRightEdgeSs = 0;

        @BeforeEach
        void configureCommonStubs() {
            headerRightEdgeSs = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(givenLine());
        }

        @Test
        void testAnXInsideTheHeaderIsWithinIt() {
            var insideXPx = viewPx(headerRightEdgeSs - EDGE_MARGIN_SS);

            assertThat(handler.isWithinHeaderX(new Point(insideXPx, MIDLINE_Y_PX))).isTrue();
        }

        @Test
        void testAnXPastTheHeadersRightEdgeIsNotWithinIt() {
            var outsideXPx = viewPx(headerRightEdgeSs + EDGE_MARGIN_SS);

            assertThat(handler.isWithinHeaderX(new Point(outsideXPx, MIDLINE_Y_PX))).isFalse();
        }

        /**
         * The header owns its whole column, unlike the registry's staff-line region, which is
         * bounded vertically too. A point level with the lyrics, far below the staff, is still
         * in the header if its X is.
         */
        @Test
        void testTheAnswerIgnoresY() {
            var insideXPx = viewPx(headerRightEdgeSs - EDGE_MARGIN_SS);

            assertThat(handler.isWithinHeaderX(new Point(insideXPx, 0)))
                .as("at the top of the component")
                .isTrue();
            assertThat(handler.isWithinHeaderX(new Point(insideXPx, Y_WELL_BELOW_STAFF_PX)))
                .as("far below the staff")
                .isTrue();
        }

        /**
         * The X is converted through the view scale like every other mouse query, so the same
         * on-screen pixel means half the staff spaces at 200% zoom. A pixel that is past the
         * header at 100% is inside it once zoomed in — which is what a user sees: the clef
         * occupies twice the screen width, and the dead zone has to follow it.
         */
        @Test
        void testAtNonDefaultZoomTheHeaderCoversTwiceTheScreenWidth() {
            var pastEdgeAtIdentityPx = viewPx(headerRightEdgeSs + EDGE_MARGIN_SS);
            givenZoomedView();

            assertThat(handler.isWithinHeaderX(new Point(pastEdgeAtIdentityPx, MIDLINE_Y_PX)))
                .as("zoomed 2x, that pixel is only half as far along the staff")
                .isTrue();
            assertThat(handler.isWithinHeaderX(
                new Point(zoomedViewPx(headerRightEdgeSs + EDGE_MARGIN_SS), MIDLINE_Y_PX)))
                .as("the zoomed pixel for the same staff-space position is still outside")
                .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // The band drag path — shared fixture
    // -------------------------------------------------------------------------

    /**
     * The fixture every band-drag test runs on: a registered line, a real
     * {@link SelectionCoordinator}, and a mocked layout whose columns the test positions itself.
     * <p>
     * A base class rather than a helper object because JUnit's {@code @Nested} classes cannot
     * share instance state any other way, and the band tests are split across several of them —
     * the drag path's sweep, its geometry, its hit basis and its arming each read the same
     * fixture and would otherwise each rebuild it.
     */
    abstract class DragFixture {

        /**
         * How far a positioned column's right edge sits past its X — a real, non-zero width, so
         * the band's snap-to-column-extent behavior (spec §3) has something to snap to. Small
         * next to the gaps between the fixtures' element X positions, so no two columns overlap.
         */
        static final double ELEMENT_RIGHT_EXTENT_SS = 2;

        /** The left extent of a column with no leading accidental: its left edge is its X. */
        static final double NO_ACCIDENTAL_SS = 0;

        static final int COMPONENT_SIZE_PX = 1000;

        Line line = new Song().getLine(0);
        SelectionCoordinator coordinator = new SelectionCoordinator(mock(ScoreView.class));

        /**
         * The columns {@link #positionElement} has stubbed, tracked here rather than read back
         * off {@code mockLayout} — calling a mock method from inside another mock method's
         * {@code thenAnswer} would re-enter Mockito's stubbing machinery, not just answer a
         * plain query.
         */
        private final Map<StaffElement, ElementColumn> positionedColumns = new HashMap<>();

        @BeforeEach
        void registerTheDefaultTwoNoteLine() {
            register(givenLine());
        }

        /** A fresh song's first line, holding one element of each given type. */
        Line lineOf(ElementType... types) {
            var song = new Song();
            var newLine = song.getLine(0);
            song.withoutMutationTracking(() -> {
                for (var type : types) {
                    newLine.addElement(type.newInstance());
                }
            });
            return newLine;
        }

        Line threeNoteLine() {
            return lineOf(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);
        }

        /**
         * A line holding nothing but the song's auto-maintained terminal. Built by adding a note
         * — which is what appends the terminal — and taking it away again, since the terminal
         * cannot be added directly and a line that never held a note never grows one.
         */
        Line terminalOnlyLine() {
            var song = new Song();
            var newLine = song.getLine(0);
            song.withoutMutationTracking(() -> {
                newLine.addElement(ElementType.CROTCHET.newInstance());
                newLine.removeElement(0);
            });
            return newLine;
        }

        void register(Line lineToRegister) {
            line = lineToRegister;
            positionedColumns.clear();
            // A real coordinator, since the drag path's whole output is the range it assigns.
            coordinator = new SelectionCoordinator(mock(ScoreView.class));
            coordinator.registerLine(0, line);
            when(lc.getLine()).thenReturn(line);
            when(lc.getLayoutResult()).thenReturn(mockLayout);
            when(lc.readyLayout()).thenReturn(new LineComponent.ReadyLayout(line, mockLayout));
            when(lc.getLineIndex()).thenReturn(0);
            when(lc.getWidth()).thenReturn(COMPONENT_SIZE_PX);
            when(lc.getHeight()).thenReturn(COMPONENT_SIZE_PX);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);

            // Mirrors LayoutHitTester.findElementAtXSs against whatever positionElement has
            // stubbed on mockLayout.getElementColumn, so the handler's own columnAt lookup
            // resolves the same column a real layout would. The requested span is honored
            // rather than ignored, so a handler that asked for the wrong one is caught here.
            when(mockLayout.findElementAtXSs(anyDouble(), any(), any()))
                .thenAnswer(invocation -> elementIndexAtXSs(
                    invocation.getArgument(0), invocation.getArgument(2)));
        }

        /**
         * The lowest-index element among {@code line}'s whose positioned column contains
         * {@code xSs} under {@code span}, or -1 — the same first-match-wins tie-break
         * {@link songscribe.layout.LayoutHitTester#findElementAtXSs} uses.
         */
        private int elementIndexAtXSs(double xSs, ColumnSpan span) {
            for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
                var column = positionedColumns.get(line.getElement(elementIndex));

                if (column == null) {
                    continue;
                }

                var leftBoundSs = span == ColumnSpan.HEAD
                    ? column.getXSs()
                    : column.getLeftEdgeXSs();

                if (xSs >= leftBoundSs && xSs <= column.getRightEdgeXSs()) {
                    return elementIndex;
                }
            }

            return -1;
        }

        /** The range the drag left behind, failing rather than returning null. */
        Selection.Range selectedRange() {
            var range = coordinator.getRange();

            assertThat(range).as("the drag selected no range").isNotNull();

            return range;
        }

        /**
         * Stubs {@code element}'s column at {@code xSs} with a real, non-zero right extent
         * ({@link #ELEMENT_RIGHT_EXTENT_SS}), so the band has a column to snap out to instead
         * of the zero-width point {@link ElementColumnTestHelper}'s simplest overload builds.
         */
        void positionElement(int elementIndex, double xSs) {
            positionElement(elementIndex, xSs, NO_ACCIDENTAL_SS);
        }

        /**
         * Stubs {@code element}'s column at {@code xSs} reaching {@code leftExtentSs} to the left
         * of it, as a column with a leading accidental does. Left extents are negative, so the
         * column's left ink edge lands at {@code xSs + leftExtentSs} while its glyph body still
         * starts at {@code xSs} — the one case where the two column spans differ.
         */
        void positionElement(int elementIndex, double xSs, double leftExtentSs) {
            var element = line.getElement(elementIndex);
            var column = ElementColumnTestHelper.columnAt(
                element, xSs, leftExtentSs, ELEMENT_RIGHT_EXTENT_SS);
            when(mockLayout.getElementColumn(element)).thenReturn(column);
            positionedColumns.put(element, column);
        }

        /** The right edge of the column {@link #positionElement} placed at {@code xSs}. */
        static double rightEdgeOfColumnAt(double xSs) {
            return xSs + ELEMENT_RIGHT_EXTENT_SS;
        }

        /**
         * The leftmost x the band's leading edge can reach on the registered line: clear of the
         * staff header, whose own press selects the staff lines instead of sweeping.
         */
        double sweepLeftLimitSs() {
            return HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line) + headerGapSs();
        }

        /** The end of the staff, which the band reaches when no terminal stops it sooner. */
        double staffEndXSs() {
            return line.getSong().getLineWidthSs();
        }

        /** {@link LineSelectionHandler#HEADER_GAP_PX} in staff spaces. */
        static double headerGapSs() {
            return ScaleContext.pxToSs(LineSelectionHandler.HEADER_GAP_PX);
        }

        /**
         * Presses at {@code xSs} where nothing is clickable — an empty registry — so the press
         * is not handled and the drag that follows is free to sweep a band.
         */
        void pressAtSs(double xSs) {
            pressAtSs(xSs, MIDLINE_Y_PX);
        }

        /** {@link #pressAtSs(double)} at an explicit view-pixel Y, which nothing should consult. */
        void pressAtSs(double xSs, int yPx) {
            pressAt(pressEvent(viewPx(xSs), yPx));
        }

        void dragToSs(double xSs) {
            dragToSs(xSs, MIDLINE_Y_PX);
        }

        /** {@link #dragToSs(double)} at an explicit view-pixel Y, which nothing should consult. */
        void dragToSs(double xSs, int yPx) {
            handler.handleDrag(dragEvent(viewPx(xSs), yPx));
        }

        /**
         * The band {@code getSelectionBand} must report for a drag covering
         * {@code [leftSs, rightSs]}.
         * <p>
         * Staff spaces rather than pixels, because that is what the handler publishes: the band
         * names an interval of music, and only the renderer turns it into a rectangle. The
         * vertical extent is not the band's at all — it is the staff's, so the mouse's Y cannot
         * reach it (spec §6).
         */
        static SelectionBand expectedBand(double leftSs, double rightSs) {
            return new SelectionBand(leftSs, rightSs);
        }
    }

    // -------------------------------------------------------------------------
    // calculateLineSelectionFromDrag — tested through handleDrag
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateLineSelectionFromDrag extends DragFixture {

        // Three notes assigned distinct X positions via mocked ElementColumns (positionElement).
        // The band spans X=[0, DRAG_TARGET_SS]. Only element 0 at X=10 falls within it —
        // X=50 and X=90 do not.
        private static final double ELEMENT_0_X_SS = 10;
        private static final double ELEMENT_1_X_SS = 50;
        private static final double ELEMENT_2_X_SS = 90;

        /**
         * Both elements of the two-element fixture, at X positions inside the band. Both sit
         * clear of the staff header, as a real layout places them and as the band's leading edge
         * needs them to be: it cannot reach into the header, so a column parked there would never
         * be swept from the right.
         */
        private static final double NEAR_X_SS = 12;
        private static final double FAR_X_SS = 22;

        /**
         * A drag corner between {@link #NEAR_X_SS} and {@link #FAR_X_SS}, for the zoomed drag
         * below: a sweep that reaches exactly this far catches the near element and not the far
         * one, so a conversion that ignored the zoom factor would visibly over-reach.
         */
        private static final double BETWEEN_ELEMENTS_X_SS = 17;

        /**
         * Presses where nothing is clickable — an empty registry — so the press is not handled
         * and the drag that follows is free to rubber-band.
         */
        private void pressThenDragToCorner() {
            pressAt(pressEvent(ORIGIN_X_PX, 0));
            handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));
        }

        @Test
        void testDragRectEnclosingOneElementSelectsThatElement() {
            register(threeNoteLine());
            positionElement(0, ELEMENT_0_X_SS);
            positionElement(1, ELEMENT_1_X_SS);
            positionElement(2, ELEMENT_2_X_SS);

            pressThenDragToCorner();

            assertThat(selectedRange().begin()).isEqualTo(0);
            assertThat(selectedRange().end()).isEqualTo(0);
        }

        @Test
        void testDragRectEnclosingMultipleElementsSelectsRange() {
            positionElement(0, NEAR_X_SS);
            positionElement(1, FAR_X_SS);

            pressThenDragToCorner();

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isEqualTo(0);
            assertThat(range.end()).as("end").isEqualTo(1);
            assertThat(range.anchor())
                .as("the band anchors at the first element it caught")
                .isEqualTo(0);
        }

        /**
         * The rubber band sweeps staff spaces, not pixels, so at 200% zoom a drag to a given
         * on-screen corner covers half the staff spaces it would at 100%.
         * <p>
         * The corner used here lands between the two elements, so a correct conversion selects
         * only the near one. A conversion that dropped the zoom factor would read the same pixel
         * as twice the distance and drag the far element in as well — which is what a user
         * zoomed in would see: a rubber band that grabs notes it visibly never touched.
         */
        @Test
        void testAtNonDefaultZoomTheDragSweepsTheStaffSpacesItCovers() {
            givenZoomedView();
            positionElement(0, NEAR_X_SS);
            positionElement(1, FAR_X_SS);

            pressAt(pressEvent(ORIGIN_X_PX, 0));
            handler.handleDrag(dragEvent(
                zoomedViewPx(BETWEEN_ELEMENTS_X_SS), zoomedViewPx(DRAG_TARGET_SS)));

            assertThat(selectedRange().begin()).isEqualTo(0);
            assertThat(selectedRange().end()).isEqualTo(0);
        }

        /**
         * A band drawn right-to-left anchors at its first element too, not at the end the drag
         * started from.
         * <p>
         * The anchor is where the user's selection grows from, and a selection reads the same
         * to them whichever way they swept it out: the elements are in the same place and the
         * first Shift+Right has to extend it, not shrink it.
         */
        @Test
        void testABandDrawnRightToLeftStillAnchorsAtItsFirstElement() {
            positionElement(0, NEAR_X_SS);
            positionElement(1, FAR_X_SS);

            pressAt(pressEvent(DRAG_TARGET_X, DRAG_TARGET_Y));
            handler.handleDrag(dragEvent(ORIGIN_X_PX, 0));

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isEqualTo(0);
            assertThat(range.end()).as("end").isEqualTo(1);
            assertThat(range.anchor()).as("anchor").isEqualTo(0);
        }
    }

    // -------------------------------------------------------------------------
    // Band geometry — snap on entry, hold inside, track through gaps, reverse
    // -------------------------------------------------------------------------

    /**
     * Three evenly spaced columns with real gaps between them, which is what the anchor/lead
     * contribution rule of spec §3 needs to be observable: the band's edge has somewhere to snap
     * out to and somewhere to track continuously through.
     * <p>
     * The band itself is read back through {@link LineSelectionHandler#getSelectionBand}, the
     * only thing that publishes it. Asserting on that rather than on the selection alone is what
     * separates "the right elements ended up selected" from "the band has the right extent" —
     * several distinct bands select the same elements.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BandGeometry extends DragFixture {

        private static final double COLUMN_0_X_SS = 10;
        private static final double COLUMN_1_X_SS = 20;
        private static final double COLUMN_2_X_SS = 30;

        /** A point in the gap between column 0 and column 1, well clear of both. */
        private static final double IN_GAP_BEFORE_COLUMN_1_SS = 15;

        /** A second gap point further right, so a drag can move within the gap alone. */
        private static final double FURTHER_IN_GAP_SS = 17;

        /** A point in the gap between column 1 and column 2. */
        private static final double IN_GAP_AFTER_COLUMN_1_SS = 26;

        /** Two points inside column 1, so a drag can move without leaving it. */
        private static final double INSIDE_COLUMN_1_SS = 20.5;
        private static final double FURTHER_INSIDE_COLUMN_1_SS = 21.5;

        /** A point just past column 1's right edge — the first x that is out of it again. */
        private static final double JUST_PAST_COLUMN_1_SS = 22.5;

        /** Two points inside column 0, so a press inside a column can be followed by a nudge. */
        private static final double INSIDE_COLUMN_0_SS = 11;
        private static final double FURTHER_INSIDE_COLUMN_0_SS = 11.5;

        /** A point inside column 2, for the drag that reverses back across the anchor. */
        private static final double INSIDE_COLUMN_2_SS = 31;

        @BeforeEach
        void positionThreeSpacedColumns() {
            register(threeNoteLine());
            positionElement(0, COLUMN_0_X_SS);
            positionElement(1, COLUMN_1_X_SS);
            positionElement(2, COLUMN_2_X_SS);
        }

        /**
         * While the band is entirely in dead space both contributions are bare points, so it is
         * drawn from the press x to the mouse and selects nothing (spec §5).
         */
        @Test
        void testADragThatStaysWithinAGapSelectsNothing() {
            pressAtSs(IN_GAP_BEFORE_COLUMN_1_SS);
            dragToSs(FURTHER_IN_GAP_SS);

            assertThat(coordinator.getRange())
                .as("a band still in dead space selects nothing")
                .isNull();
            assertThat(handler.getSelectionBand())
                .as("the band tracks the mouse point for point")
                .isEqualTo(expectedBand(IN_GAP_BEFORE_COLUMN_1_SS, FURTHER_IN_GAP_SS));
        }

        /**
         * The instant the mouse enters a column the leading contribution becomes that column's
         * whole span, so the band jumps out to its far extent rather than stopping at the
         * mouse — the text-selection feel goal 3 asks for.
         */
        @Test
        void testEnteringAColumnSnapsTheBandToItsFarExtent() {
            pressAtSs(IN_GAP_BEFORE_COLUMN_1_SS);
            dragToSs(INSIDE_COLUMN_1_SS);

            assertThat(handler.getSelectionBand())
                .as("the band reaches the column's right edge, not the mouse")
                .isEqualTo(expectedBand(
                    IN_GAP_BEFORE_COLUMN_1_SS, rightEdgeOfColumnAt(COLUMN_1_X_SS)));

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isEqualTo(1);
            assertThat(range.end()).as("end").isEqualTo(1);
        }

        /**
         * Having snapped, the band holds while the mouse wanders inside the column. Without this
         * the edge would creep with the mouse and the snap would read as a glitch rather than as
         * the column being taken whole.
         */
        @Test
        void testMovingWithinAColumnLeavesTheBandAndSelectionUnchanged() {
            pressAtSs(IN_GAP_BEFORE_COLUMN_1_SS);
            dragToSs(INSIDE_COLUMN_1_SS);

            var bandOnEntry = handler.getSelectionBand();
            var rangeOnEntry = selectedRange();

            dragToSs(FURTHER_INSIDE_COLUMN_1_SS);

            assertThat(handler.getSelectionBand()).as("band").isEqualTo(bandOnEntry);
            assertThat(selectedRange()).as("selection").isEqualTo(rangeOnEntry);
        }

        /**
         * On the way out the leading contribution reverts to the mouse point, which is already
         * past the column's edge — so the band resumes tracking continuously without ever
         * shrinking back over the column it just took, and that column stays selected.
         */
        @Test
        void testLeavingAColumnResumesContinuousTrackingWithoutGivingItBack() {
            pressAtSs(IN_GAP_BEFORE_COLUMN_1_SS);
            dragToSs(INSIDE_COLUMN_1_SS);
            dragToSs(JUST_PAST_COLUMN_1_SS);

            assertThat(handler.getSelectionBand())
                .as("the band picks the mouse up where the column's edge left it")
                .isEqualTo(expectedBand(IN_GAP_BEFORE_COLUMN_1_SS, JUST_PAST_COLUMN_1_SS));

            dragToSs(IN_GAP_AFTER_COLUMN_1_SS);

            assertThat(handler.getSelectionBand())
                .as("and keeps tracking through the gap")
                .isEqualTo(expectedBand(IN_GAP_BEFORE_COLUMN_1_SS, IN_GAP_AFTER_COLUMN_1_SS));

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isEqualTo(1);
            assertThat(range.end()).as("end").isEqualTo(1);
        }

        /**
         * A press inside a column makes the anchor contribution that column's whole span, so the
         * band covers it from the very first movement. There is no mid-notehead edge to see.
         */
        @Test
        void testPressingInsideAColumnCoversItOnTheFirstMovement() {
            pressAtSs(INSIDE_COLUMN_0_SS);
            dragToSs(FURTHER_INSIDE_COLUMN_0_SS);

            assertThat(handler.getSelectionBand())
                .as("the band spans the whole column, not the two mouse points")
                .isEqualTo(expectedBand(COLUMN_0_X_SS, rightEdgeOfColumnAt(COLUMN_0_X_SS)));

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isEqualTo(0);
            assertThat(range.end()).as("end").isEqualTo(0);
        }

        /**
         * The anchor column is always fully enclosed, so the edge it contributes flips of its own
         * accord when the drag crosses back over it: press in column 1, sweep right to column 2,
         * then back left to column 0, and the band encloses columns 0–1 with column 1 still whole.
         */
        @Test
        void testReversingPastTheAnchorColumnKeepsItEnclosed() {
            pressAtSs(INSIDE_COLUMN_1_SS);
            dragToSs(INSIDE_COLUMN_2_SS);

            assertThat(handler.getSelectionBand())
                .as("rightward, the band runs from the anchor column's left edge")
                .isEqualTo(expectedBand(COLUMN_1_X_SS, rightEdgeOfColumnAt(COLUMN_2_X_SS)));

            var rightward = selectedRange();
            assertThat(rightward.begin()).as("begin, rightward").isEqualTo(1);
            assertThat(rightward.end()).as("end, rightward").isEqualTo(2);

            dragToSs(INSIDE_COLUMN_0_SS);

            assertThat(handler.getSelectionBand())
                .as("reversed, it runs to the anchor column's right edge")
                .isEqualTo(expectedBand(COLUMN_0_X_SS, rightEdgeOfColumnAt(COLUMN_1_X_SS)));

            var reversed = selectedRange();
            assertThat(reversed.begin()).as("begin, reversed").isZero();
            assertThat(reversed.end()).as("end, reversed").isEqualTo(1);
            assertThat(reversed.anchor())
                .as("the range still anchors at its first element (#748)")
                .isZero();
        }
    }

    // -------------------------------------------------------------------------
    // What the band sweeps — X only, clamped to the sweepable columns
    // -------------------------------------------------------------------------

    /**
     * The hit basis itself: which elements a given band takes, and which x values it can reach.
     * Vertical position is not consulted anywhere in the new model (spec §6), and the leading
     * edge is clamped to the sweepable content range (spec §3).
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BandHitBasis extends DragFixture {

        private static final double COLUMN_0_X_SS = 10;
        private static final double COLUMN_1_X_SS = 20;
        private static final double COLUMN_2_X_SS = 30;

        /** Where the auto-maintained terminal's own column sits, past all three notes. */
        private static final double TERMINAL_X_SS = 40;

        /** A press point in the gap before the first column, and a lead point inside column 1. */
        private static final double BEFORE_COLUMN_0_SS = 5;
        private static final double INSIDE_COLUMN_1_SS = 21;

        /**
         * How far a leading accidental reaches to the left of its note's X. Negative, as left
         * extents are, and small enough to stay clear of the column before it.
         */
        private static final double ACCIDENTAL_LEFT_EXTENT_SS = -3;

        /**
         * An x inside column 1's accidental but left of its notehead — inside the column's full
         * ink, outside its glyph body. The only place the two spans disagree.
         */
        private static final double INSIDE_COLUMN_1S_ACCIDENTAL_SS = COLUMN_1_X_SS - 2;

        /** A lead point far to the right of the last column, still inside the component. */
        private static final double PAST_THE_LAST_COLUMN_SS = 60;

        /** A second lead point further into the right margin, to show the clamp does not move. */
        private static final double FURTHER_INTO_THE_MARGIN_SS = 100;

        /** A view-pixel Y far above the staff, outside the component's bounds entirely. */
        private static final int FAR_ABOVE_THE_STAFF_PX = -5000;

        /** A view-pixel Y far below the lyric rows, likewise outside the component. */
        private static final int FAR_BELOW_THE_LYRICS_PX = 5000;

        /** A view-pixel Y at the very top of the component. */
        private static final int TOP_OF_THE_COMPONENT_PX = 0;

        /** The Y values one and the same horizontal drag is repeated at. */
        private static final List<Integer> WILDLY_DIFFERENT_Y_PX = List.of(
            FAR_ABOVE_THE_STAFF_PX, TOP_OF_THE_COMPONENT_PX, MIDLINE_Y_PX,
            FAR_BELOW_THE_LYRICS_PX);

        /** A staff position several ledger lines above the staff. */
        private static final int HIGH_LEDGER_LINE_SP = Staff.MIN_STAFF_POSITION_SP;

        private void positionThreeSpacedColumns() {
            positionElement(0, COLUMN_0_X_SS);
            positionElement(1, COLUMN_1_X_SS);
            positionElement(2, COLUMN_2_X_SS);
        }

        /**
         * The same x swept at four wildly different Y values produces the same band and the same
         * selection. This is goal 1: the gesture the user makes is horizontal, and the vertical
         * excursion that used to decide whether a tall note was caught no longer says anything.
         */
        @Test
        void testTheMousesYNeverAffectsTheBandOrTheSelection() {
            register(threeNoteLine());
            positionThreeSpacedColumns();

            var bands = new ArrayList<SelectionBand>();
            var ranges = new ArrayList<Selection.Range>();

            for (var yPx : WILDLY_DIFFERENT_Y_PX) {
                pressAtSs(BEFORE_COLUMN_0_SS, yPx);
                dragToSs(INSIDE_COLUMN_1_SS, yPx);
                bands.add(handler.getSelectionBand());
                ranges.add(selectedRange());
                handler.handleRelease();
            }

            assertThat(bands)
                .as("the same band at every y")
                .containsOnly(bands.getFirst());
            assertThat(ranges)
                .as("the same selection at every y")
                .containsOnly(ranges.getFirst());
        }

        /**
         * A note several ledger lines above the staff, a rest and a barline are three heights
         * that the old element hit rect measured differently — the note's box excluded its stem,
         * the barline's spanned the staff. One band takes all three alike now.
         */
        @Test
        void testANoteOnALedgerLineARestAndABarlineAreAllSweptAlike() {
            register(lineOf(
                ElementType.CROTCHET, ElementType.CROTCHET_REST, ElementType.SINGLE_BARLINE));
            line.getElement(0).setStaffPosition(HIGH_LEDGER_LINE_SP);
            positionThreeSpacedColumns();

            pressAtSs(BEFORE_COLUMN_0_SS);
            dragToSs(PAST_THE_LAST_COLUMN_SS);

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isZero();
            assertThat(range.end())
                .as("the note, the rest and the barline are all in the range")
                .isEqualTo(2);
        }

        /**
         * The leading edge stops at the auto-maintained terminal's left ink edge, so dragging on
         * into the right margin adds nothing and the terminal stays unreachable (issue #713).
         * Two lead points past the terminal are used, since a clamp that merely lagged would
         * still look right at the first of them.
         * <p>
         * The band does reach the bare staff between the last note and the terminal — the clamp
         * is the terminal, not the last column's right edge.
         * <p>
         * Two things keep the terminal out of the selection independently: this clamp (spec §3),
         * and the scan being bounded to {@code effectiveElementCount() - 1} whatever the band
         * covers (spec §4). Positioning the terminal at all — rather than leaving it unstubbed —
         * is what makes this a test of both, instead of one that passes because there was never
         * anything there to select.
         */
        @Test
        void testTheLeadingEdgeClampsAtTheTerminal() {
            register(threeNoteLine());
            positionThreeSpacedColumns();
            var terminalIndex = line.elementCount() - 1;
            positionElement(terminalIndex, TERMINAL_X_SS);

            pressAtSs(BEFORE_COLUMN_0_SS);
            dragToSs(PAST_THE_LAST_COLUMN_SS);

            var clampedBand = expectedBand(BEFORE_COLUMN_0_SS, TERMINAL_X_SS);
            assertThat(handler.getSelectionBand()).as("band").isEqualTo(clampedBand);

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isZero();
            assertThat(range.end())
                .as("the last real element is the rightmost one a band can reach")
                .isEqualTo(2)
                .isNotEqualTo(terminalIndex);

            dragToSs(FURTHER_INTO_THE_MARGIN_SS);

            assertThat(handler.getSelectionBand())
                .as("dragging further into the margin changes nothing")
                .isEqualTo(clampedBand);
            assertThat(selectedRange()).as("selection").isEqualTo(range);
        }

        /**
         * With nothing marking the end of the music — no terminal, or none the layout gave a
         * column — the leading edge runs all the way to the end of the staff rather than stopping
         * at the last note, so the band can sweep the bare staff past the final element.
         */
        @Test
        void testWithNoTerminalColumnTheLeadingEdgeReachesTheEndOfTheStaff() {
            register(threeNoteLine());
            positionThreeSpacedColumns();

            pressAtSs(BEFORE_COLUMN_0_SS);
            dragToSs(FURTHER_INTO_THE_MARGIN_SS);

            assertThat(handler.getSelectionBand())
                .as("band")
                .isEqualTo(expectedBand(BEFORE_COLUMN_0_SS, staffEndXSs()));
            assertThat(selectedRange().end())
                .as("every real element is swept")
                .isEqualTo(2);
        }

        /**
         * The leading edge cannot enter the staff header, whose own press selects the staff lines.
         * Dragging left past it holds the band's left edge one document pixel clear of it.
         */
        @Test
        void testTheLeadingEdgeClampsClearOfTheStaffHeader() {
            register(threeNoteLine());
            positionThreeSpacedColumns();

            pressAtSs(rightEdgeOfColumnAt(COLUMN_0_X_SS));
            dragToSs(0);

            assertThat(handler.getSelectionBand())
                .as("band")
                .isEqualTo(expectedBand(sweepLeftLimitSs(), rightEdgeOfColumnAt(COLUMN_0_X_SS)));
        }

        /**
         * A column's span is its full drawn ink, so a note's leading accidental is part of it.
         * A band whose leading edge reaches only the accidental — never the notehead itself —
         * still takes the note.
         * <p>
         * This is the one behavior that tells the two column spans apart. Were the handler to
         * ask for the glyph body instead of the full ink, the accidental would sit outside every
         * column, the drag below would land in what the hit test called a gap, and the note the
         * user visibly swept would not be selected.
         */
        @Test
        void testABandReachingOnlyANotesAccidentalStillTakesTheNote() {
            register(threeNoteLine());
            positionElement(0, COLUMN_0_X_SS);
            positionElement(1, COLUMN_1_X_SS, ACCIDENTAL_LEFT_EXTENT_SS);
            positionElement(2, COLUMN_2_X_SS);

            pressAtSs(INSIDE_COLUMN_1S_ACCIDENTAL_SS);
            dragToSs(INSIDE_COLUMN_1S_ACCIDENTAL_SS);

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isEqualTo(1);
            assertThat(range.end()).as("the accidental's own note alone").isEqualTo(1);
            assertThat(handler.getSelectionBand())
                .as("and the band covers the whole column, accidental included")
                .isEqualTo(expectedBand(
                    COLUMN_1_X_SS + ACCIDENTAL_LEFT_EXTENT_SS,
                    rightEdgeOfColumnAt(COLUMN_1_X_SS)));
        }

        /**
         * A grace note is an ordinary sweepable column with no special-case skip: a band that
         * covers its span selects it exactly as it would any other element.
         */
        @Test
        void testAGraceNoteIsSweptLikeAnyOtherColumn() {
            register(lineOf(
                ElementType.CROTCHET, ElementType.GRACE_QUAVER, ElementType.CROTCHET));
            positionThreeSpacedColumns();

            pressAtSs(INSIDE_COLUMN_1_SS);
            dragToSs(INSIDE_COLUMN_1_SS);

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isEqualTo(1);
            assertThat(range.end()).as("the grace note alone is selected").isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // What arms a band, and what a band does with nothing to sweep
    // -------------------------------------------------------------------------

    /**
     * The branches around the band rather than inside it: which presses arm one at all
     * (spec §2), what happens on a line with no sweepable columns (spec §3), and what a zoom
     * mid-drag does to the rectangle the band is published as (spec §7).
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BandArming extends DragFixture {

        private static final double COLUMN_0_X_SS = 10;
        private static final double COLUMN_1_X_SS = 20;

        /** A lead point past both columns, far enough that a band would sweep them both. */
        private static final double PAST_BOTH_COLUMNS_SS = 25;

        /**
         * Where column 0 lands after a re-layout under a live drag: still left of column 1, but
         * far enough from {@link #COLUMN_0_X_SS} that the press x is now in a gap.
         */
        private static final double RELAID_OUT_COLUMN_0_X_SS = 14;

        /** A press point in the gap between the two columns, and a lead point past them both. */
        private static final double RAW_BAND_LEFT_SS = 15;
        private static final double RAW_BAND_RIGHT_SS = 25;

        /** Asserts that the drag that just ran never became a band. */
        private void assertNoBand() {
            assertThat(handler.isDragging())
                .as("no band drag started")
                .isFalse();
            assertThat(handler.getSelectionBand())
                .as("no band is published for painting")
                .isNull();
        }

        /**
         * Shift on an element extends the selection on the click that follows (#748), so the
         * press deliberately leaves {@code pressHandled} false — but it must not arm a band with
         * it. Sweeping here would replace the range the user asked to grow.
         */
        @Test
        void testShiftPressingAnElementNeverArmsABand() {
            positionElement(0, COLUMN_0_X_SS);
            positionElement(1, COLUMN_1_X_SS);
            coordinator.activateLine(0);
            coordinator.selectRange(0, 1);
            var rangeBeforeThePress = selectedRange();
            givenClickableAtOrigin(
                new HitTarget.Element(line.getElement(0)), HitPriority.ELEMENT, false);

            pressAt(shiftPressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));
            dragToSs(PAST_BOTH_COLUMNS_SS);

            assertNoBand();
            assertThat(coordinator.getRange())
                .as("the range the Shift press was extending is untouched")
                .isEqualTo(rangeBeforeThePress);
        }

        /**
         * A plain press on an element selects it and is eligible for a pitch drag, so no band is
         * armed there either. Rests, whose press never spawns a playback thread, stand in for
         * elements generally.
         */
        @Test
        void testPlainPressingAnElementNeverArmsABand() {
            register(lineOf(ElementType.CROTCHET_REST, ElementType.CROTCHET_REST));
            positionElement(0, COLUMN_0_X_SS);
            positionElement(1, COLUMN_1_X_SS);
            givenClickableAtOrigin(
                new HitTarget.Element(line.getElement(0)), HitPriority.ELEMENT, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));
            dragToSs(PAST_BOTH_COLUMNS_SS);

            assertNoBand();

            var range = selectedRange();
            assertThat(range.begin()).as("begin").isZero();
            assertThat(range.end())
                .as("the pressed element alone stays selected")
                .isZero();
        }

        /**
         * An empty line — the state every line of a new song is in — has nothing to sweep, so the
         * press never arms and the drag is a no-op. Painting a band over bare staff that could
         * not select anything only tells the user a lie about what the gesture is doing.
         */
        @Test
        void testAnEmptyLineNeverArmsABand() {
            register(new Song().getLine(0));

            assertThat(line.effectiveElementCount())
                .as("the fixture line has no sweepable columns")
                .isZero();

            pressAtSs(RAW_BAND_LEFT_SS);
            dragToSs(RAW_BAND_RIGHT_SS);

            assertNoBand();
            assertThat(coordinator.getRange()).as("selection").isNull();
        }

        /** A line holding only the auto-maintained terminal behaves as an empty one does. */
        @Test
        void testATerminalOnlyLineNeverArmsABand() {
            register(terminalOnlyLine());

            assertThat(line.elementCount())
                .as("the fixture line holds the terminal and nothing else")
                .isEqualTo(1);
            assertThat(line.effectiveElementCount())
                .as("the terminal is not a sweepable column")
                .isZero();

            pressAtSs(RAW_BAND_LEFT_SS);
            dragToSs(RAW_BAND_RIGHT_SS);

            assertNoBand();
            assertThat(coordinator.getRange()).as("selection").isNull();
        }

        /**
         * No column is resolved once and remembered. The press's own column is looked up afresh
         * on every drag event, so a re-layout under a live drag — an element inserted elsewhere,
         * a spacing pass rerunning — is picked up rather than answered from a column that has
         * since moved.
         * <p>
         * Here the anchor's column is moved out from under the drag between two events. A
         * handler that had cached it on press would keep reporting the band over the column's
         * old position, painting a selection where the note no longer is.
         */
        @Test
        void testAColumnMovedMidDragIsPickedUpRatherThanRememberedFromThePress() {
            positionElement(0, COLUMN_0_X_SS);
            positionElement(1, COLUMN_1_X_SS);

            pressAtSs(COLUMN_0_X_SS);
            dragToSs(COLUMN_0_X_SS);

            assertThat(handler.getSelectionBand())
                .as("the band covers the anchor's column where it started")
                .isEqualTo(expectedBand(COLUMN_0_X_SS, rightEdgeOfColumnAt(COLUMN_0_X_SS)));

            positionElement(0, RELAID_OUT_COLUMN_0_X_SS);
            dragToSs(COLUMN_0_X_SS);

            assertThat(handler.getSelectionBand())
                .as("after the re-layout the press x is in a gap, so the band is a bare point")
                .isEqualTo(expectedBand(COLUMN_0_X_SS, COLUMN_0_X_SS));
            assertThat(coordinator.getRange())
                .as("and the column that moved away is no longer swept")
                .isNull();
        }

        /**
         * Nothing pixel-valued is stored or published: the band names an interval of music, so a
         * zoom mid-drag leaves it untouched (goal 4). The pixels move, the music does not.
         * <p>
         * This is the handler's half of that guarantee. The renderer's half — that the same
         * interval draws at scaled pixels — is
         * {@code LineRendererTest.RenderSelectionBand.testTheSameBandIsDrawnAtScaledPixelsWhenTheZoomChanges}.
         * A handler that cached a rectangle would fail here; one that cached staff spaces
         * correctly but painted them stale would fail there.
         */
        @Test
        void testAZoomMidDragLeavesTheBandsStaffSpaceEndpointsAlone() {
            positionElement(0, COLUMN_0_X_SS);
            positionElement(1, COLUMN_1_X_SS);

            pressAtSs(RAW_BAND_LEFT_SS);
            dragToSs(PAST_BOTH_COLUMNS_SS);

            var bandAtDefaultZoom = expectedBand(RAW_BAND_LEFT_SS, PAST_BOTH_COLUMNS_SS);
            assertThat(handler.getSelectionBand())
                .as("band at 100%")
                .isEqualTo(bandAtDefaultZoom);

            givenZoomedView();

            assertThat(handler.getSelectionBand())
                .as("the same music after the zoom, with no mouse movement to change it")
                .isEqualTo(bandAtDefaultZoom);
        }
    }

    // -------------------------------------------------------------------------
    // handlePress
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandlePress {

        private Line line;
        private SelectionCoordinator coordinator;

        @BeforeEach
        void configureCommonStubs() {
            line = givenLine(ElementType.CROTCHET_REST, ElementType.CROTCHET_REST);
            when(lc.getLineIndex()).thenReturn(0);
            when(lc.getWidth()).thenReturn(DRAG_TARGET_X * 2);
            when(lc.getHeight()).thenReturn(DRAG_TARGET_Y * 2);
            coordinator = mock(SelectionCoordinator.class);
            // Registered, so selectTarget finds a line for the index it is about to activate.
            when(coordinator.getLine(0)).thenReturn(line);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
        }

        /** Asserts that no rubber-band range was assigned. */
        private void assertNoRangeSelected() {
            verify(coordinator, never()).selectRange(anyInt(), anyInt(), anyInt());
            verify(coordinator, never()).selectRange(anyInt(), anyInt());
        }

        @Test
        void testPressOnEndingSelectsItAndNotifiesScoreView() {
            var ending = newEnding();
            givenClickableAtOrigin(new HitTarget.Ending(ending), HitPriority.ENDING, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(coordinator).select(new HitTarget.Ending(ending));
            verify(mockScoreView).selectionChanged();
        }

        /**
         * A press that selects an ending must be marked handled, so a rubber-band drag
         * does not start and immediately replace the ending selection.
         */
        @Test
        void testPressOnEndingSuppressesRubberBandDrag() {
            var ending = newEnding();
            givenClickableAtOrigin(new HitTarget.Ending(ending), HitPriority.ENDING, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));
            handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

            verify(coordinator).select(new HitTarget.Ending(ending));
            assertNoRangeSelected();
        }

        @Test
        void testPressOnHairpinSelectsIt() {
            var hairpin = newHairpin();
            givenClickableAtOrigin(new HitTarget.Hairpin(hairpin), HitPriority.HAIRPIN, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(coordinator).select(new HitTarget.Hairpin(hairpin));
        }

        /**
         * A slide is named by the element that owns it, so carrying the wrong element through
         * would select a different note's slide. Hitting element 1 rather than 0 is what makes
         * that visible.
         */
        @Test
        void testPressOnSlideSelectsTheSlideOfTheElementThatWasHit() {
            var owner = line.getElement(1);
            givenClickableAtOrigin(new HitTarget.Slide(owner), HitPriority.SLIDE, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(coordinator)
                .select(new HitTarget.Slide(owner));
            verify(mockScoreView).selectionChanged();
        }

        /**
         * A grace note's glissando cannot be selected. Pressing on one warns the user and must
         * count the press as handled, so a rubber-band drag does not start instead — which
         * would silently select notes the user never meant to touch.
         */
        @Test
        void testPressOnGraceGlissandoWarnsAndSuppressesRubberBandDrag() {
            givenClickableAtOrigin(
                new HitTarget.GraceGlissando(line.getElement(1)), HitPriority.SLIDE, false);

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));
                handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

                optionDialogsMock.verify(() -> OptionDialogs.showWarningMessage(
                    any(),
                    eq(Strings.ALERT_TITLE_GRACE_NOTE_WARNING),
                    eq(Strings.WARNING_GRACE_GLISSANDO_NOT_SELECTABLE)
                ));
            }

            verify(coordinator, never()).select(any());
            assertNoRangeSelected();
        }

        @Test
        void testPressOnStaffLineSelectsTheWholeLine() {
            givenClickableAtOrigin(new HitTarget.StaffLine(), HitPriority.STAFF_LINE, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(coordinator).select(new HitTarget.StaffLine());
            verify(mockScoreView).selectionChanged();
        }

        /**
         * A press that selects the staff lines must be marked handled, so a rubber-band drag
         * does not start and immediately replace that selection.
         * <p>
         * The staff-line region is the header column — the clef and key signature — so this is
         * what keeps a drag begun in the header from sweeping. Nothing in the drag path checks
         * for the header itself; the whole guarantee rests on this press being handled.
         */
        @Test
        void testPressOnStaffLineSuppressesRubberBandDrag() {
            givenClickableAtOrigin(new HitTarget.StaffLine(), HitPriority.STAFF_LINE, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));
            handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

            verify(coordinator).select(new HitTarget.StaffLine());
            assertNoRangeSelected();
        }

        @Test
        void testPressOnLyricSelectsItAndNotifiesScoreView() {
            var element = line.getElement(0);
            givenClickableAtOrigin(new HitTarget.Lyric(element, LYRIC_VERSE), HitPriority.LYRIC, true);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(coordinator).selectLyric(element, LYRIC_VERSE);
            verify(mockScoreView).selectionChanged();
        }

        /**
         * A press that selects a lyric must be marked handled, so a rubber-band drag does not
         * start and immediately replace the lyric selection.
         */
        @Test
        void testPressOnLyricSuppressesRubberBandDrag() {
            givenClickableAtOrigin(
                new HitTarget.Lyric(line.getElement(0), LYRIC_VERSE), HitPriority.LYRIC, true);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));
            handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

            assertThat(handler.isDragging())
                .as("no rubber-band drag started after a press on a lyric")
                .isFalse();
        }

        /**
         * A target names an element by identity; the index the selection range needs is derived
         * at the point of use, so pressing element 1 must select element 1.
         */
        @Test
        void testPressOnElementSelectsThatElementByIdentity() {
            givenClickableAtOrigin(
                new HitTarget.Element(line.getElement(1)), HitPriority.ELEMENT, false);

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(coordinator).selectSingleElement(0, 1);
        }

        /**
         * The newly selectable kinds all take the same route: they become the score's single
         * selected target. Listing them here rather than testing one keeps a kind from being
         * added to the vocabulary and quietly left unselectable.
         */
        @Test
        void testEveryNewlySelectableKindBecomesTheSelectedTarget() {
            var note = line.getElement(0);
            var articulation = new Articulation(
                note, ArticulationType.STACCATO);
            var fermata = new FermataAttachment(note);
            var tie = new Tie(note, line.getElement(1));
            var beam = new Beam(note, line.getElement(1));

            var targets = List.<HitTarget>of(
                new HitTarget.Articulation(articulation),
                new HitTarget.Attachment(fermata),
                new HitTarget.Accidental(note),
                new HitTarget.Tie(tie),
                new HitTarget.Beam(beam)
            );

            for (var target : targets) {
                handler.handlePress(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX), target);

                verify(coordinator, description(target + " is selected by a press on it"))
                    .select(target);
            }
        }

        @Test
        void testPressOnNothingSelectsNothing() {
            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(coordinator, never()).select(any());
            assertNoRangeSelected();
        }
    }

    // -------------------------------------------------------------------------
    // End-to-end: real layout -> real registry -> press dispatch, every HitTarget kind
    // -------------------------------------------------------------------------

    /**
     * Every other test in this class hands the component a hand-built one-region registry
     * (see {@link #givenClickableAtOrigin}). This is the one place a real {@link LayoutEngine}
     * lays out a real line, a real click resolves against its real {@link HitRegistry}, and the
     * press dispatches from that — the full chain HitRegionBuilderTest and the rest of this
     * class each exercise only half of.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndToEndClickResolution {

        /** Three notes in the time of two, undotted — this fixture's tuplet. */
        private static final int TUPLET_NORMAL_NOTES = 2;
        private static final int NO_DOTS = 0;

        private Line line;
        private HitRegistry registry;
        private SelectionCoordinator coordinator;

        private StaffElement sourceNote;
        private Hairpin hairpin;
        private Ending ending;
        private Articulation articulation;
        private FermataAttachment fermata;
        private StaffElement accidentalNote;
        private Tie tie;
        private Beam beam;
        private Trill trill;
        private Tuplet tuplet;

        /**
         * Lays out one line carrying one of every registrable {@link HitTarget} kind: an ending
         * over a repeat split (an ending only lays out when a repeat splits its two brackets), a
         * hairpin and a glissando between the first two notes, a lyric on the first note, an
         * articulation and an attachment each on their own note, an accidental on its own note, a
         * tie, a beam, a trill and a tuplet.
         */
        @BeforeEach
        void layOutOneOfEveryHitTargetKind() {
            var song = new Song();
            song.setLineWidthSs(UNCONSTRAINED_LINE_WIDTH_SS);
            line = song.getLine(0);

            var anchorBarline = ElementType.SINGLE_BARLINE.newInstance();
            var split = ElementType.REPEAT_RIGHT.newInstance();
            var endBarline = ElementType.SINGLE_BARLINE.newInstance();
            sourceNote = noteAt(0);
            // A different staff position than sourceNote: the layout and render passes register
            // no geometry for a glissando between two notes at the same pitch (Line.isSamePitchAsFollower).
            var targetNote = noteAt(4);
            var thirdNote = noteAt(0);
            var fourthNote = noteAt(0);
            var articulationNote = noteAt(0);
            var attachmentNote = noteAt(0);
            accidentalNote = noteAt(0);
            var beamNote1 = quaverAt(4);
            var beamNote2 = quaverAt(4);
            var trillNote1 = noteAt(0);
            var trillNote2 = noteAt(0);
            var tupletNote1 = noteAt(0);
            var tupletNote2 = noteAt(0);

            ending = new Ending(anchorBarline, endBarline);
            hairpin = new Crescendo(sourceNote, targetNote);
            articulation = new Articulation(ArticulationType.STACCATO);
            fermata = new FermataAttachment();
            tie = new Tie(thirdNote, fourthNote);
            beam = new Beam(beamNote1, beamNote2);
            trill = new Trill(trillNote1, trillNote2);
            tuplet = new Tuplet(
                tupletNote1, tupletNote2, TRIPLET_GRADE, TUPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);

            song.withoutMutationTracking(() -> {
                line.addElement(anchorBarline);
                line.addElement(sourceNote);
                line.addElement(targetNote);
                line.addElement(split);
                line.addElement(thirdNote);
                line.addElement(fourthNote);
                line.addElement(articulationNote);
                line.addElement(attachmentNote);
                line.addElement(accidentalNote);
                line.addElement(beamNote1);
                line.addElement(beamNote2);
                line.addElement(trillNote1);
                line.addElement(trillNote2);
                line.addElement(tupletNote1);
                line.addElement(tupletNote2);
                line.addElement(endBarline);
                line.addSpan(ending);
                line.addSpan(hairpin);
                line.addTie(tie);
                line.addBeaming(beam);
                line.addTrill(trill);
                line.addTuplet(tuplet);
                sourceNote.setGlissando();
                sourceNote.setLyricForVerse(
                    Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, "la", Lyric.Extend.NONE);
                articulationNote.addArticulation(articulation);
                attachmentNote.addAttachment(fermata);
                accidentalNote.setAccidental(StaffElement.Accidental.SHARP);
            });

            var layoutResult = layOutForHitTesting(line);
            registry = layoutResult.getHitRegistry();

            when(lc.getLine()).thenReturn(line);
            when(lc.readyLayout()).thenReturn(new LineComponent.ReadyLayout(line, layoutResult));
            when(lc.getLayoutResult()).thenReturn(layoutResult);
            when(lc.getLineIndex()).thenReturn(0);
            coordinator = mock(SelectionCoordinator.class);
            when(coordinator.getLine(0)).thenReturn(line);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
        }

        /** A stem-up crotchet at the given staff position, so its column extents are deterministic. */
        private static StaffElement noteAt(int staffPosition) {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(staffPosition);
            return note;
        }

        /** A stem-up quaver at the given staff position — beamable, unlike {@link #noteAt}. */
        private static StaffElement quaverAt(int staffPosition) {
            var note = ElementType.QUAVER.newInstance();
            note.setUpper(true);
            note.setStaffPosition(staffPosition);
            return note;
        }

        /** Presses at the real, laid-out center of {@code target}'s own region in {@link #registry}. */
        private void clickOn(HitTarget target) {
            pressAt(pressEvent(realPressPointFor(registry, target)));
        }

        @Test
        void testNoteHeadClickSelectsThatElementByIdentity() {
            clickOn(new HitTarget.Element(sourceNote));

            verify(coordinator).selectSingleElement(0, line.getElementIndex(sourceNote));
        }

        @Test
        void testLyricClickSelectsTheLyric() {
            clickOn(new HitTarget.Lyric(sourceNote, Lyric.FIRST_VERSE));

            verify(coordinator).selectLyric(sourceNote, Lyric.FIRST_VERSE);
        }

        @Test
        void testStaffLineHeaderClickSelectsTheWholeLine() {
            clickOn(new HitTarget.StaffLine());

            verify(coordinator).select(new HitTarget.StaffLine());
        }

        /**
         * Slide, Hairpin, Ending, Articulation, Attachment, Accidental, Tie, Beam, Trill and
         * Tuplet all take the same route through {@code handlePress}: they become the score's
         * single selected target. One real click per kind, at that kind's own laid-out region,
         * keeps a kind from being added to the vocabulary and quietly left unreachable by a
         * real click.
         */
        @Test
        void testEveryDecorationKindClickSelectsThatTarget() {
            var targets = List.<HitTarget>of(
                new HitTarget.Slide(sourceNote),
                new HitTarget.Hairpin(hairpin),
                new HitTarget.Ending(ending),
                new HitTarget.Articulation(articulation),
                new HitTarget.Attachment(fermata),
                new HitTarget.Accidental(accidentalNote),
                new HitTarget.Tie(tie),
                new HitTarget.Beam(beam),
                new HitTarget.Trill(trill),
                new HitTarget.Tuplet(tuplet)
            );

            for (var target : targets) {
                clickOn(target);

                verify(coordinator, description("a click on " + target + "'s own laid-out region selects it"))
                    .select(target);
            }
        }
    }

    /**
     * A grace note's glissando is not selectable, so it needs its own small fixture rather than
     * a place in {@link EndToEndClickResolution}'s combined line.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndToEndGraceGlissandoClick {

        private StaffElement graceNote;
        private HitRegistry registry;

        @BeforeEach
        void layOutAGraceNoteGlissando() {
            var song = new Song();
            song.setLineWidthSs(UNCONSTRAINED_LINE_WIDTH_SS);
            var line = song.getLine(0);
            graceNote = ElementType.GRACE_QUAVER.newInstance();
            var hostNote = ElementType.CROTCHET.newInstance();
            // A different staff position than graceNote: the layout and render passes register
            // no geometry for a glissando between two notes at the same pitch (Line.isSamePitchAsFollower).
            hostNote.setStaffPosition(4);

            song.withoutMutationTracking(() -> {
                line.addElement(graceNote);
                line.addElement(hostNote);
                graceNote.setGlissando();
            });

            var layoutResult = layOutForHitTesting(line);
            registry = layoutResult.getHitRegistry();

            when(lc.getLine()).thenReturn(line);
            when(lc.readyLayout()).thenReturn(new LineComponent.ReadyLayout(line, layoutResult));
            when(lc.getLayoutResult()).thenReturn(layoutResult);
            when(mockScoreView.getSelectionCoordinator())
                .thenReturn(mock(SelectionCoordinator.class));
        }

        @Test
        void testGraceGlissandoClickWarnsAndSelectsNothing() {
            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                pressAt(pressEvent(realPressPointFor(registry, new HitTarget.GraceGlissando(graceNote))));

                optionDialogsMock.verify(() -> OptionDialogs.showWarningMessage(
                    any(),
                    eq(Strings.ALERT_TITLE_GRACE_NOTE_WARNING),
                    eq(Strings.WARNING_GRACE_GLISSANDO_NOT_SELECTABLE)
                ));
            }

            verify(mockScoreView, never()).selectionChanged();
        }
    }

    // -------------------------------------------------------------------------
    // handleClick
    // -------------------------------------------------------------------------

    /**
     * The Shift+click half of range selection — the gesture that grows an existing selection
     * out to the element just clicked, from wherever its anchor sits (issue #748).
     * <p>
     * A click only reaches this path when its press was left unhandled, and a Shift+press on
     * an element is the one press that qualifies: every unmodified press on an element is
     * consumed on the way down, either by the pitch-drag handler or by the press handler
     * selecting it outright. Each test here therefore presses first, exactly as
     * {@code LineComponent} does, so {@code handleClick} sees the press target the real
     * sequence would have left behind.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleClick {

        private SelectionCoordinator coordinator;
        private Line line;

        @BeforeEach
        void configureCommonStubs() {
            line = givenLine();
            when(lc.getLineIndex()).thenReturn(0);
            when(mockScoreView.getMode()).thenReturn(Mode.SELECT);
            coordinator = mock(SelectionCoordinator.class);
            when(coordinator.getLine(0)).thenReturn(line);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
        }

        /** Makes the second element the only clickable thing, so a press at the origin names it. */
        private void givenSecondElementClickable() {
            givenClickableAtOrigin(
                new HitTarget.Element(line.getElement(1)), HitPriority.ELEMENT, false);
        }

        /**
         * The selection a previous click or drag left behind, which is what gives
         * {@code handleClick} an anchor to extend from. Its shape does not matter here — only
         * that a range exists — since the anchor arithmetic itself belongs to the coordinator.
         */
        private void givenExistingRange() {
            when(coordinator.getRange()).thenReturn(new Selection.Range(line, 0, 0, 0));
        }

        @Test
        void testShiftClickOnAnElementExtendsTheSelectionToItsIndex() {
            givenSecondElementClickable();
            givenExistingRange();

            pressAt(shiftPressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            assertThat(handler.handleClick(shiftClickEvent(ORIGIN_X_PX, MIDLINE_Y_PX)))
                .as("the click is handled")
                .isTrue();
            verify(mockScoreView).extendSelectionTo(1);
        }

        /**
         * With nothing selected there is no anchor, so the click extends nothing rather than
         * extending from an index it would have to invent.
         */
        @Test
        void testShiftClickWithNothingSelectedExtendsNothing() {
            givenSecondElementClickable();

            pressAt(shiftPressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            assertThat(handler.handleClick(shiftClickEvent(ORIGIN_X_PX, MIDLINE_Y_PX)))
                .as("the click is still handled — selection is active, there is just nothing to do")
                .isTrue();
            verify(mockScoreView, never()).extendSelectionTo(anyInt());
        }

        /**
         * Without Shift the click extends nothing: the press already selected the element on
         * its own, and extending on top of that would grow a range the user never asked for.
         */
        @Test
        void testAnUnmodifiedClickOnAnElementExtendsNothing() {
            givenSecondElementClickable();
            givenExistingRange();

            pressAt(pressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            assertThat(handler.handleClick(clickEvent(ORIGIN_X_PX, MIDLINE_Y_PX)))
                .as("the click is handled")
                .isTrue();
            verify(mockScoreView, never()).extendSelectionTo(anyInt());
        }

        /**
         * A Shift+click on something that is not an element — a lyric here — has no index to
         * extend to, so it leaves the selection alone rather than extending to whatever element
         * happens to lie underneath.
         */
        @Test
        void testShiftClickOnANonElementTargetExtendsNothing() {
            givenClickableAtOrigin(
                new HitTarget.Lyric(line.getElement(1), LYRIC_VERSE), HitPriority.LYRIC, true);
            givenExistingRange();

            pressAt(shiftPressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            handler.handleClick(shiftClickEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            verify(mockScoreView, never()).extendSelectionTo(anyInt());
        }

        /**
         * Outside SELECT mode the click is refused outright and reported unhandled, so
         * {@code LineComponent} goes on to treat it as an EDIT-mode gesture.
         */
        @Test
        void testClickIsNotHandledOutsideSelectMode() {
            when(mockScoreView.getMode()).thenReturn(Mode.EDIT);
            givenSecondElementClickable();
            givenExistingRange();

            pressAt(shiftPressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));

            assertThat(handler.handleClick(shiftClickEvent(ORIGIN_X_PX, MIDLINE_Y_PX)))
                .as("the click falls through to EDIT-mode handling")
                .isFalse();
            verify(mockScoreView, never()).extendSelectionTo(anyInt());
        }
    }

    // -------------------------------------------------------------------------
    // handleEditModePress
    // -------------------------------------------------------------------------

    /**
     * The EDIT-mode entry point that lets a lyric — and nothing else — be selected without
     * first switching to SELECT mode. The mode check itself lives in
     * {@code LineComponent.mousePressed}; this class only decides whether the press landed
     * on a lyric.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleEditModePress {

        private SelectionCoordinator coordinator;
        private Line line;

        @BeforeEach
        void configureCommonStubs() {
            line = givenLine();
            when(lc.getLineIndex()).thenReturn(0);
            coordinator = mock(SelectionCoordinator.class);
            when(coordinator.getLine(0)).thenReturn(line);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
        }

        /**
         * Resolves a press at the region the test registered and hands the result to the
         * method under test, the same way {@code LineComponent.mousePressed} does.
         */
        private boolean pressAtOrigin() {
            return handler.handleEditModePress(handler.hitTestViewPoint(originPoint()));
        }

        /**
         * A decoration is not selectable in EDIT mode even when it is what the press resolved
         * to and no preview is in the way. Endings and hairpins used to be, and this is what
         * pins down that they no longer are: they need SELECT mode or an alt+click like every
         * other kind.
         */
        @Test
        void testPressOnEndingIsNotHandled() {
            var ending = newEnding();
            givenClickableAtOrigin(new HitTarget.Ending(ending), HitPriority.ENDING, false);

            assertThat(pressAtOrigin())
                .as("press on an ending is left to EDIT-mode handling")
                .isFalse();

            verify(coordinator, never()).select(any());
            verify(mockScoreView, never()).selectionChanged();
        }

        @Test
        void testPressOnHairpinIsNotHandled() {
            givenClickableAtOrigin(new HitTarget.Hairpin(newHairpin()), HitPriority.HAIRPIN, false);

            assertThat(pressAtOrigin())
                .as("press on a hairpin is left to EDIT-mode handling")
                .isFalse();

            verify(coordinator, never()).select(any());
            verify(mockScoreView, never()).selectionChanged();
        }

        @Test
        void testPressOnLyricSelectsItAndReportsHandled() {
            var element = line.getElement(0);
            givenClickableAtOrigin(new HitTarget.Lyric(element, LYRIC_VERSE), HitPriority.LYRIC, true);

            assertThat(pressAtOrigin())
                .as("press on a lyric is handled")
                .isTrue();

            verify(coordinator).selectLyric(element, LYRIC_VERSE);
            verify(mockScoreView).selectionChanged();
            verify(lc).repaint();
        }

        /**
         * Selecting a lyric mid-playback would move the selection out from under the
         * playing sequence, so the press is refused outright.
         */
        @Test
        void testPressOnLyricDuringPlaybackIsNotHandled() {
            givenClickableAtOrigin(
                new HitTarget.Lyric(line.getElement(0), LYRIC_VERSE), HitPriority.LYRIC, true);

            try (var midiMock = mockStatic(MidiController.class)) {
                midiMock.when(MidiController::isPlaying).thenReturn(true);

                assertThat(pressAtOrigin())
                    .as("press on a lyric is refused while MIDI playback is running")
                    .isFalse();
            }

            verify(coordinator, never()).selectLyric(any(), anyInt());
            verify(mockScoreView, never()).selectionChanged();
        }

        /**
         * A lyric wins over the insertion preview (see
         * {@link LineSelectionHandler#handleEditModePress}) — but the preview can still reach
         * the lyric row when the mouse never moved to clear it, and this fallback guard is
         * what refuses the press in that case.
         */
        @Test
        void testPressOnLyricWithPreviewElementShowingIsNotHandled() {
            givenClickableAtOrigin(
                new HitTarget.Lyric(line.getElement(0), LYRIC_VERSE), HitPriority.LYRIC, true);
            when(lc.hasPreviewElement()).thenReturn(true);

            assertThat(pressAtOrigin())
                .as("press is left to the insertion preview")
                .isFalse();

            verify(coordinator, never()).selectLyric(any(), anyInt());
            verify(mockScoreView, never()).selectionChanged();
        }

        /**
         * An element head over a lyric box outranks the lyric, so the press falls through to
         * normal EDIT-mode handling rather than selecting the lyric underneath it.
         */
        @Test
        void testElementHeadOverLyricIsNotHandled() {
            givenClickableAtOrigin(
                new HitTarget.Element(line.getElement(0)), HitPriority.ELEMENT, false);

            assertThat(pressAtOrigin())
                .as("press on an element head is left to EDIT-mode handling")
                .isFalse();
            verify(mockScoreView, never()).selectionChanged();
        }

        @Test
        void testPressOnNothingIsNotHandled() {
            assertThat(pressAtOrigin())
                .as("press that hits nothing is left to EDIT-mode handling")
                .isFalse();
            verify(mockScoreView, never()).selectionChanged();
        }

        /**
         * A lyric is the only kind handled here. Every other variant is listed and fed in
         * directly — rather than resolved from a registry — so a kind cannot appear to be
         * refused merely because the test forgot to register it.
         */
        @Test
        void testEveryOtherTargetIsNotHandled() {
            var note = line.getElement(0);
            var second = line.getElement(1);
            var unhandledTargets = new ArrayList<HitTarget>(List.of(
                new HitTarget.Element(note),
                new HitTarget.Slide(note),
                new HitTarget.GraceGlissando(note),
                new HitTarget.StaffLine(),
                new HitTarget.Accidental(note),
                new HitTarget.Articulation(
                    new Articulation(note, ArticulationType.STACCATO)),
                new HitTarget.Attachment(new FermataAttachment(note)),
                new HitTarget.Tie(new Tie(note, second)),
                new HitTarget.Beam(new Beam(note, second)),
                new HitTarget.Trill(new Trill(note, second)),
                new HitTarget.Tuplet(Tuplet.withUnresolvedRatio(note, second, TRIPLET_GRADE)),
                new HitTarget.Hairpin(newHairpin()),
                new HitTarget.Ending(newEnding())
            ));
            unhandledTargets.add(null);

            for (var target : unhandledTargets) {
                assertThat(handler.handleEditModePress(target))
                    .as("%s is not handled by handleEditModePress", target)
                    .isFalse();
            }

            verify(mockScoreView, never()).selectionChanged();
        }
    }

    // -------------------------------------------------------------------------
    // selectElementAtIndex — stale-highlight repaint of the outgoing line
    // -------------------------------------------------------------------------

    /**
     * {@code selectElementAtIndex} is called directly rather than through
     * {@link LineSelectionHandler#handlePress}, because the press path is not where the
     * outgoing-line repaint matters: {@code handlePress} calls
     * {@code ScoreView.clearSelection()} first, which resets the coordinator's active line
     * to -1 and repaints the outgoing line itself, leaving nothing for
     * {@code selectElementAtIndex} to do. The state exercised here — a *different* line
     * still active on entry — is produced by {@code NoteDragHandler}, which selects a
     * pressed note without pre-clearing and so bypasses {@code handlePress} entirely.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectElementAtIndex {

        private static final int FIRST_LINE_INDEX = 0;
        private static final int SECOND_LINE_INDEX = 1;

        /** Line index registered with no selection state, to force a null selection result. */
        private static final int UNREGISTERED_LINE_INDEX = 2;

        private Song song;
        private Line firstLine;
        private Line secondLine;
        private SelectionCoordinator coordinator;
        private LineComponent firstLineComponent;

        @BeforeEach
        void buildTwoLineSong() {
            song = new Song();
            firstLine = song.getLine(FIRST_LINE_INDEX);
            secondLine = new Line(song);
            song.withoutMutationTracking(() -> {
                firstLine.addElement(ElementType.CROTCHET_REST.newInstance());
                secondLine.addElement(ElementType.CROTCHET_REST.newInstance());
            });
            song.addLine(secondLine);

            coordinator = new SelectionCoordinator(mock(ScoreView.class));
            coordinator.registerLine(FIRST_LINE_INDEX, firstLine);
            coordinator.registerLine(SECOND_LINE_INDEX, secondLine);

            firstLineComponent = mock(LineComponent.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
            when(mockScoreView.getLineComponent(FIRST_LINE_INDEX)).thenReturn(firstLineComponent);

            when(lc.getLine()).thenReturn(secondLine);
            when(lc.getLineIndex()).thenReturn(SECOND_LINE_INDEX);
        }

        /** The single selected element, or null if the selection is not one element. */
        private @Nullable StaffElement singleSelectedElement() {
            return coordinator.getSingleSelectedElement();
        }

        /**
         * The stale highlight of issue #625: the outgoing line's state is cleared by
         * {@code selectSingleElement}, so without an explicit repaint it keeps painting a
         * selection that no longer exists.
         */
        @Test
        void testSelectingOnAnotherLineRepaintsThePreviouslyActiveLine() {
            coordinator.selectSingleElement(FIRST_LINE_INDEX, 0);

            handler.selectElementAtIndex(0);

            assertThat(coordinator.isElementSelected(0, FIRST_LINE_INDEX))
                .as("outgoing line's selection was cleared")
                .isFalse();
            assertThat(singleSelectedElement())
                .as("target line's element is now selected")
                .isEqualTo(secondLine.getElement(0));
            verify(firstLineComponent).repaint();
            verify(mockScoreView).selectionChanged();
        }

        /**
         * Reselecting within the already-active line has no outgoing line, so the extra
         * repaint must be suppressed — this line is repainted by its own caller.
         */
        @Test
        void testSelectingOnTheAlreadyActiveLineDoesNotRepaintIt() {
            when(mockScoreView.getLineComponent(SECOND_LINE_INDEX)).thenReturn(lc);
            song.withoutMutationTracking(() -> secondLine.addElement(ElementType.CROTCHET_REST.newInstance()));
            coordinator.selectSingleElement(SECOND_LINE_INDEX, 0);

            handler.selectElementAtIndex(1);

            assertThat(singleSelectedElement())
                .as("selection moved to the newly clicked element")
                .isEqualTo(secondLine.getElement(1));
            verify(lc, never()).repaint();
        }

        /**
         * The first selection of a session: nothing was active, so
         * {@code getLineComponent(-1)} yields null and there is nothing to repaint.
         */
        @Test
        void testSelectingWithNoPreviouslyActiveLineRepaintsNothing() {
            when(mockScoreView.getLineComponent(-1)).thenReturn(null);

            handler.selectElementAtIndex(0);

            assertThat(singleSelectedElement())
                .as("target line's element is selected on the first click")
                .isEqualTo(secondLine.getElement(0));
            assertThat(coordinator.getActiveLineIndex())
                .as("target line became the active line")
                .isEqualTo(SECOND_LINE_INDEX);
            verify(firstLineComponent, never()).repaint();
        }

        /**
         * A line with no registered selection state cannot be selected, so no selection
         * change may be announced — but the outgoing line still repaints, since
         * {@code selectSingleElement} cleared it on the way through.
         */
        @Test
        void testSelectingOnALineWithNoRegisteredStateDoesNotNotify() {
            coordinator.selectSingleElement(FIRST_LINE_INDEX, 0);
            when(lc.getLineIndex()).thenReturn(UNREGISTERED_LINE_INDEX);

            handler.selectElementAtIndex(0);

            verify(mockScoreView, never()).selectionChanged();
            verify(firstLineComponent).repaint();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * The far corner of the rubber-band drags, in staff spaces. Large enough to enclose the
     * fixtures' element X positions, which are chosen relative to it.
     */
    private static final double DRAG_TARGET_SS = 30;

    private static final int DRAG_TARGET_X = viewPx(DRAG_TARGET_SS);
    private static final int DRAG_TARGET_Y = viewPx(DRAG_TARGET_SS);

    /**
     * The view-pixel coordinate a staff-space position sits at, at the default 100% zoom.
     * <p>
     * The handler converts mouse points through {@link ViewScale#toSs}, which folds the fixed
     * document scale in itself, so these tests have to feed it honest pixels rather than
     * treating one pixel as one staff space.
     */
    private static int viewPx(double ss) {
        return (int) Math.round(ss * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    /** The zoom the non-default-zoom tests install. Doubling keeps the pixel arithmetic exact. */
    private static final int ZOOMED_PERCENT = 200;

    /**
     * Points the component at a view zoomed to {@link #ZOOMED_PERCENT}, in place of the
     * {@link ViewScale#IDENTITY} every other test runs at. A fresh instance, never
     * {@code IDENTITY} — that one is shared and documented as read-only.
     */
    private void givenZoomedView() {
        var viewScale = new ViewScale();
        viewScale.setZoomPercent(ZOOMED_PERCENT);
        when(lc.getViewScale()).thenReturn(viewScale);
    }

    /**
     * The view-pixel coordinate a staff-space position sits at once {@link #givenZoomedView}
     * has doubled the zoom. Written as a doubling rather than as a percentage division so the
     * expected value never routes through the production arithmetic it is checking.
     */
    private static int zoomedViewPx(double ss) {
        return viewPx(ss) * 2;
    }

    /** No modifier keys held, for the event builders that spell out an unmodified gesture. */
    private static final int NO_MODIFIERS = 0;

    private MouseEvent mouseEvent(int id, int modifiers, int x, int y) {
        // Use the 10-arg constructor that sets xAbs/yAbs (screen coords) so
        // MouseEvent.getXOnScreen() / getYOnScreen() do not NPE in handler code.
        return new MouseEvent(lc, id, 0L, modifiers, x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private MouseEvent pressEvent(int x, int y) {
        return mouseEvent(MouseEvent.MOUSE_PRESSED, NO_MODIFIERS, x, y);
    }

    private MouseEvent pressEvent(Point point) {
        return pressEvent(point.x, point.y);
    }

    /** Presses at the event's point with the target the production caller resolves for it. */
    private void pressAt(MouseEvent event) {
        handler.handlePress(event, handler.hitTestViewPoint(event.getPoint()));
    }

    /**
     * A view-pixel point at the real, laid-out center of {@code target}'s own region in
     * {@code registry}, converted with {@link ScaleContext#ssToPx}. The end-to-end fixtures run
     * a real {@link LayoutEngine} layout, and every test in this class runs at
     * {@link ViewScale#IDENTITY}, so the document pixels {@code ssToPx} produces are the view
     * pixels the handler expects.
     */
    private Point realPressPointFor(HitRegistry registry, HitTarget target) {
        var boundsSs = regionFor(registry, target).shapeSs().getBounds2D();
        var xPx = (int) Math.round(ScaleContext.ssToPx(boundsSs.getCenterX()));
        var yPx = (int) Math.round(
            ScaleContext.ssToPx(boundsSs.getCenterY() + lc.getMiddleLineYSs()));
        return new Point(xPx, yPx);
    }

    private static HitRegion regionFor(HitRegistry registry, HitTarget target) {
        for (var region : registry.regions()) {
            if (region.target().equals(target)) {
                return region;
            }
        }

        throw new AssertionError("no hit region was registered for " + target);
    }

    /** Lays {@code line} out with a real {@link LayoutEngine}, for the end-to-end fixtures. */
    private static LayoutResult layOutForHitTesting(Line line) {
        return new LayoutEngine(
            LyricRenderMetrics.forFont(new Font(Font.DIALOG, Font.PLAIN, END_TO_END_LYRIC_FONT_SIZE_PX)),
            END_TO_END_STAFF_RIGHT_MARGIN_SS,
            DocumentFonts.defaultFonts()
        ).layout(line);
    }

    private MouseEvent shiftPressEvent(int x, int y) {
        return mouseEvent(MouseEvent.MOUSE_PRESSED, InputEvent.SHIFT_DOWN_MASK, x, y);
    }

    private MouseEvent dragEvent(int x, int y) {
        return mouseEvent(MouseEvent.MOUSE_DRAGGED, NO_MODIFIERS, x, y);
    }

    private MouseEvent clickEvent(int x, int y) {
        return mouseEvent(MouseEvent.MOUSE_CLICKED, NO_MODIFIERS, x, y);
    }

    private MouseEvent shiftClickEvent(int x, int y) {
        return mouseEvent(MouseEvent.MOUSE_CLICKED, InputEvent.SHIFT_DOWN_MASK, x, y);
    }
}
