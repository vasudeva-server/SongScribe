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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.description;
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
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
import songscribe.font.DocumentFonts;
import songscribe.hit.HitPriority;
import songscribe.hit.HitRegion;
import org.jspecify.annotations.Nullable;

import songscribe.hit.HitRegistry;
import songscribe.ui.selection.Selection;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
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
 * {@link Line} and an identity {@link ScaleContext} so the drag-rect/element-rect intersection
 * can be verified numerically.
 */
class LineSelectionHandlerTest extends UnitTest {

    /**
     * Distance from the top of the component to the staff midline, in staff spaces. Non-zero
     * on purpose: layout space measures Y from the midline, so a handler that forgot to
     * subtract this would still pass every test with a zero midline.
     */
    private static final double MIDDLE_LINE_Y_SS = 5.0;

    /**
     * A document-pixel Y that lands exactly on the staff midline, and therefore on layout
     * Y = 0. With the identity scale these tests install, pixels and staff spaces are the
     * same number.
     */
    private static final int MIDLINE_Y_PX = (int) MIDDLE_LINE_Y_SS;

    /** A document-pixel X at the left edge of the line, on layout X = 0. */
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

    private MockedStatic<ScaleContext> scaleContextMock;

    private LineComponent lc;
    private ScoreView mockScoreView;
    private LayoutResult mockLayout;
    private LineSelectionHandler handler;

    @BeforeEach
    void setUp() {
        scaleContextMock = mockStatic(ScaleContext.class);

        lc = mock(LineComponent.class);
        mockScoreView = mock(ScoreView.class);
        mockLayout = mock(LayoutResult.class);

        when(lc.getScoreView()).thenReturn(mockScoreView);
        when(lc.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(lc.getLine()).thenReturn(null);
        when(lc.getMiddleLineYSs()).thenReturn(MIDDLE_LINE_Y_SS);

        // Identity scale, so a point's pixel coordinates read directly as staff spaces.
        scaleContextMock.when(() -> ScaleContext.pxToSs(anyDouble()))
            .thenAnswer(inv -> inv.getArgument(0));

        // Nothing is clickable until a test registers something.
        when(mockLayout.getHitRegistry()).thenReturn(HitRegistry.EMPTY);

        handler = new LineSelectionHandler(lc);
    }

    @AfterEach
    void tearDown() {
        scaleContextMock.close();
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

            assertThat(handler.hitTest(originPoint())).isEqualTo(new HitTarget.Ending(ending));
        }

        @Test
        void testPointOutsideEveryRegionResolvesToNull() {
            givenClickableAtOrigin(new HitTarget.Ending(newEnding()), HitPriority.ENDING, false);

            assertThat(handler.hitTest(new Point(ORIGIN_X_PX, Y_FAR_FROM_MIDLINE_PX))).isNull();
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

            assertThat(handler.hitTest(new Point(ORIGIN_X_PX, MIDLINE_Y_PX)))
                .as("a point on the midline maps to layout Y = 0")
                .isEqualTo(new HitTarget.Ending(ending));
            assertThat(handler.hitTest(new Point(ORIGIN_X_PX, 0)))
                .as("a point at the top of the component is above the region")
                .isNull();
        }

        @Test
        void testNoReadyLayoutResolvesToNull() {
            givenClickableAtOrigin(new HitTarget.Ending(newEnding()), HitPriority.ENDING, false);
            when(lc.readyLayout()).thenReturn(null);

            assertThat(handler.hitTest(originPoint())).isNull();
        }

        // Each half of the readiness guard is checked on its own, because a test that nulls
        // both at once passes just as happily if the guard's "or" is ever changed to an "and".
        @Test
        void testNoLineResolvesToNull() {
            givenClickableAtOrigin(new HitTarget.Ending(newEnding()), HitPriority.ENDING, false);
            when(lc.getLine()).thenReturn(null);

            assertThat(handler.hitTest(originPoint())).isNull();
        }

        /**
         * The layout is what carries the registry, so it has to be brought up to date before
         * the query rather than after it. Without this a click resolves against the regions
         * of whatever the line looked like before the last edit.
         */
        @Test
        void testTheLayoutIsMadeReadyBeforeTheRegistryIsRead() {
            handler.hitTest(originPoint());

            verify(lc).readyLayout();
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
    // hitTestElementIndex
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HitTestElementIndex {

        private Line line;

        @BeforeEach
        void configureCommonStubs() {
            line = givenLine();
        }

        @Test
        void testPointOnAnElementReturnsItsIndex() {
            givenClickableAtOrigin(
                new HitTarget.Element(line.getElement(1)), HitPriority.ELEMENT, false);

            assertThat(handler.hitTestElementIndex(originPoint())).isEqualTo(1);
        }

        /**
         * A lyric outranks the element beneath it, so a point resolving to a lyric reports no
         * element — the callers that ask this question (the double-click lyric editor, the
         * drag anchor) must not treat lyric text as the note under the cursor.
         */
        @Test
        void testPointOnANonElementTargetReturnsMinusOne() {
            givenClickableAtOrigin(
                new HitTarget.Lyric(line.getElement(0), LYRIC_VERSE), HitPriority.LYRIC, true);

            assertThat(handler.hitTestElementIndex(originPoint())).isEqualTo(-1);
        }

        @Test
        void testPointOnNothingReturnsMinusOne() {
            assertThat(handler.hitTestElementIndex(originPoint())).isEqualTo(-1);
        }
    }

    // -------------------------------------------------------------------------
    // calculateLineSelectionFromDrag — tested through handleDrag
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateLineSelectionFromDrag {

        // Three notes assigned distinct X positions via a mocked LayoutResult.
        // The drag rect (in px, converted 1:1 to ss) spans X=[0,30], Y=[0,30] before the
        // midline shift. Only element 0 at X=10 falls within; X=50 and X=90 do not.
        private static final double ELEMENT_0_X_SS = 10;
        private static final double ELEMENT_1_X_SS = 50;
        private static final double ELEMENT_2_X_SS = 90;

        /** Both elements of the two-element fixture, at X positions inside the drag rect. */
        private static final double NEAR_X_SS = 5;
        private static final double FAR_X_SS = 15;

        private static final int COMPONENT_SIZE_PX = 1000;

        private Line line = new Song().getLine(0);
        private SelectionCoordinator coordinator = new SelectionCoordinator(mock(ScoreView.class));

        @BeforeEach
        void registerTheDefaultTwoNoteLine() {
            register(givenLine());
        }

        private Line threeNoteLine() {
            var song = new Song();
            var newLine = song.getLine(0);
            song.withoutMutationTracking(() -> {
                newLine.addElement(ElementType.CROTCHET.newInstance());
                newLine.addElement(ElementType.CROTCHET.newInstance());
                newLine.addElement(ElementType.CROTCHET.newInstance());
            });
            return newLine;
        }

        private void register(Line lineToRegister) {
            line = lineToRegister;
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
        }

        /** The range the drag left behind, failing rather than returning null. */
        private Selection.Range selectedRange() {
            var range = coordinator.getRange();

            assertThat(range).as("the drag selected no range").isNotNull();

            return range;
        }

        private void positionElement(int elementIndex, double xSs) {
            when(mockLayout.getElementXSs(line.getElement(elementIndex))).thenReturn(xSs);
        }

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

            assertThat(selectedRange().begin()).isEqualTo(0);
            assertThat(selectedRange().end()).isEqualTo(1);
        }

        /**
         * A drag that starts on a note anchors there, so extending afterwards grows the
         * selection from the note the user grabbed rather than from whichever end happens to
         * be nearer.
         * <p>
         * Shift is held because that is the one gesture that can rubber-band from a note at
         * all: an unmodified press on a note selects it and counts as handled, which stops the
         * drag before it begins.
         */
        @Test
        void testAnchorIsTheElementUnderTheDragStart() {
            positionElement(0, NEAR_X_SS);
            positionElement(1, FAR_X_SS);
            givenClickableAtOrigin(
                new HitTarget.Element(line.getElement(1)), HitPriority.ELEMENT, false);

            pressAt(shiftPressEvent(ORIGIN_X_PX, MIDLINE_Y_PX));
            handler.handleDrag(dragEvent(DRAG_TARGET_X, DRAG_TARGET_Y));

            assertThat(selectedRange().anchor()).isEqualTo(1);
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
            // This class's default ScaleContext mock stubs only pxToSs, to the identity scale
            // other tests in this file rely on. A real LayoutEngine needs the rest of
            // ScaleContext too (font metrics, ssToPx), so this fixture swaps in the real class.
            scaleContextMock.close();
            scaleContextMock = mockStatic(ScaleContext.class, CALLS_REAL_METHODS);

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
                line.addRangeElement(ending);
                line.addRangeElement(hairpin);
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
            // See EndToEndClickResolution.layOutOneOfEveryHitTargetKind for why the real
            // ScaleContext replaces this class's default identity stub here.
            scaleContextMock.close();
            scaleContextMock = mockStatic(ScaleContext.class, CALLS_REAL_METHODS);

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

    private static final int DRAG_TARGET_X = 30;
    private static final int DRAG_TARGET_Y = 30;

    private MouseEvent pressEvent(int x, int y) {
        // Use the 10-arg constructor that sets xAbs/yAbs (screen coords) so
        // MouseEvent.getXOnScreen() / getYOnScreen() do not NPE in handler code.
        return new MouseEvent(lc, MouseEvent.MOUSE_PRESSED, 0L, 0, x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private MouseEvent pressEvent(Point point) {
        return pressEvent(point.x, point.y);
    }

    /** Presses at the event's point with the target the production caller resolves for it. */
    private void pressAt(MouseEvent event) {
        handler.handlePress(event, handler.hitTestViewPoint(event.getPoint()));
    }

    /**
     * A document-pixel point at the real, laid-out center of {@code target}'s own region in
     * {@code registry}, converted with the real {@link ScaleContext#ssToPx} — the end-to-end
     * fixtures swap in the real {@link ScaleContext} in place of this class's default identity
     * scale stub, so a real {@link LayoutEngine} layout can run.
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
        return new MouseEvent(
            lc, MouseEvent.MOUSE_PRESSED, 0L, InputEvent.SHIFT_DOWN_MASK,
            x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private MouseEvent dragEvent(int x, int y) {
        return new MouseEvent(lc, MouseEvent.MOUSE_DRAGGED, 0L, 0, x, y, x, y, 1, false, MouseEvent.BUTTON1);
    }
}
