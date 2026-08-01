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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementColumnBuilder;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class SlideRendererTest extends UnitTest {

    /** Half-width used to define synthetic column extents in noteContextCenteredAt(). */
    private static final double HALF_COLUMN_SS = 0.5;

    private static final SlideRenderer RENDERER = SlideRenderer.getInstance();

    // ======================================================================
    // Connecting glissando with an accidental on the target (issue #443)
    // ======================================================================

    /**
     * Builds a real source→target note pair, positions them with the production spacing calculator,
     * computes their column extents via NoteColumnGeometry, and returns the computed glissando
     * endpoints (null if the line would be too short to draw). This exercises the full
     * layout→render geometry end-to-end so the reserved spacing is validated against where the
     * line actually ends.
     */
    private SlideRenderer.@Nullable Endpoints computeConnectingGlissando(
        ElementType sourceType, boolean targetHasAccidental) {
        NoteGeometry.initializeAccidentalWidths();

        var source = sourceType.newInstance();
        source.setUpper(true);
        source.setGlissando();
        var target = ElementType.CROTCHET.newInstance();
        target.setUpper(true);

        if (targetHasAccidental) {
            target.setAccidental(StaffElement.Accidental.SHARP);
        }

        // Same staff position for both → horizontal line, the shortest (worst) case for the length check.
        var sourceRightSs =
            ElementColumnBuilder.calculateRightExtentSs(source, false, StaffElement.Direction.UP);
        var targetRightSs =
            ElementColumnBuilder.calculateRightExtentSs(target, false, StaffElement.Direction.UP);
        var targetLeftSs = ElementColumnBuilder.calculateLeftExtentSs(target);
        var sourceColumn = new ElementColumn(source, List.of(), 0.0, sourceRightSs, sourceRightSs, 0, 0, null, 0, false);
        var targetColumn = new ElementColumn(target, List.of(), targetLeftSs, targetRightSs, targetRightSs, 0, 0, null, 0, false);
        var targetXSs = sourceColumn.getXSs()
            + HorizontalSpacingCalculator.buildSpring(
                sourceColumn, targetColumn, Song.DEFAULT_REST_LENGTH_SS).naturalLengthSs();

        // Source note: elementXSs = 0 (LayoutResult returns 0 when not in the map)
        var sourceExtent = NoteColumnGeometry.extentSs(source, false);
        var sourceGlissExtent = NoteColumnGeometry.glissandoAttachExtentSs(source, false);
        var src = new SlideRenderer.NoteContext(
            source,
            0.0,
            sourceExtent.rightSs(),         // columnRightXSs: stem-full fall anchor
            sourceGlissExtent.leftSs(),     // glissLeftXSs
            sourceGlissExtent.rightSs()     // glissRightXSs
        );

        // Target note: elementXSs = targetXSs
        var targetExtent = NoteColumnGeometry.extentSs(target, false);
        var targetGlissExtent = NoteColumnGeometry.glissandoAttachExtentSs(target, false);
        var tgt = new SlideRenderer.NoteContext(
            target,
            0.0,
            targetXSs + targetExtent.rightSs(),       // columnRightXSs
            targetXSs + targetGlissExtent.leftSs(),   // glissLeftXSs
            targetXSs + targetGlissExtent.rightSs()   // glissRightXSs
        );

        return SlideRenderer.computeEndpoints(src, tgt);
    }

    // #443: a regular note→note connecting glissando must still draw when the target has an
    // accidental — the reserved spacing has to leave room for the line's minimum visible length.
    @Test
    void testConnectingGlissandoDrawsWhenTargetNoteHasAccidental() {
        // computeEndpoints returns null when the line would be shorter than its minimum visible
        // length, so a non-null result means the glissando draws.
        var endpoints = computeConnectingGlissando(ElementType.CROTCHET, true);

        assertThat(endpoints).as("regular note glissando draws with an accidental on the target").isNotNull();
    }

    // #443: the same must hold for a grace→host glissando, the issue's reproduction.
    @Test
    void testConnectingGlissandoDrawsForGraceWhenHostHasAccidental() {
        var endpoints = computeConnectingGlissando(ElementType.GRACE_QUAVER, true);

        assertThat(endpoints).as("grace→host glissando draws with an accidental on the host").isNotNull();
    }

    // ======================================================================
    // hitTestGlissando tests
    // ======================================================================

    /**
     * Builds a hit-test context for a slide hit test, which reads only the point and the line.
     */
    private static int hitTestSlide(double xSs, double ySs, Line line) {
        return RENDERER.hitTestSlide(xSs, ySs, line);
    }

    /**
     * Injects synthetic cached geometry onto a glissando, bypassing the render pass.
     * Angle is in degrees for readability; the method converts to radians internally.
     */
    private static void setCachedGeometry(
        StaffElement.Glissando glissando,
        double startXSs, double startYSs,
        double angleDeg, double lengthSs) {
        var angleRad = Math.toRadians(angleDeg);
        glissando.cachedStartX = startXSs;
        glissando.cachedStartY = startYSs;
        glissando.cachedAngle = angleRad;
        glissando.cachedCos = Math.cos(angleRad);
        glissando.cachedSin = Math.sin(angleRad);
        glissando.cachedLength = lengthSs;
        glissando.hasCachedGeometry = true;
    }

    @Test
    void testHitTestGlissando_diagonalLine_returnsNoteIndex() {
        // 45° glissando from (0, 0), length 10; midpoint in world coords: (5·cos45°, 5·sin45°)
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        var glissando = line.getElement(0).getGlissando();
        assertThat(glissando).isNotNull();

        setCachedGeometry(glissando, 0.0, 0.0, 45.0, 10.0);

        var mid = 5.0 * Math.cos(Math.toRadians(45.0));
        assertThat(hitTestSlide(mid, mid, line)).isEqualTo(0);
    }

    @Test
    void testHitTestGlissando_noCachedGeometry_skipped() {
        // Note has a Glissando object but hasCachedGeometry is false (default) — must be skipped
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);

        assertThat(hitTestSlide(10.0, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointAfterEnd_returnsMinusOne() {
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        var glissando = line.getElement(0).getGlissando();
        assertThat(glissando).isNotNull();

        setCachedGeometry(glissando, 5.0, 3.0, 0.0, 10.0);

        // localX = 15.1 - 5.0 = 10.1 > cachedLength (10.0)
        assertThat(hitTestSlide(15.1, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointBeforeStart_returnsMinusOne() {
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        var glissando = line.getElement(0).getGlissando();
        assertThat(glissando).isNotNull();

        setCachedGeometry(glissando, 5.0, 3.0, 0.0, 10.0);

        // localX = 4.9 - 5.0 = -0.1 < 0
        assertThat(hitTestSlide(4.9, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointBesideLine_returnsMinusOne() {
        // Same glissando, but click is 1.0 ss above (> halfHitSs = 0.5)
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        var glissando = line.getElement(0).getGlissando();
        assertThat(glissando).isNotNull();

        setCachedGeometry(glissando, 5.0, 3.0, 0.0, 10.0);

        assertThat(hitTestSlide(10.0, 4.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointOnLine_returnsNoteIndex() {
        // Horizontal glissando (angle=0) from (5.0, 3.0) with length 10.0
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        var glissando = line.getElement(0).getGlissando();
        assertThat(glissando).isNotNull();

        setCachedGeometry(glissando, 5.0, 3.0, 0.0, 10.0);

        // Click at the midpoint: localX=5, localY=0 — well within hit bounds
        assertThat(hitTestSlide(10.0, 3.0, line)).isEqualTo(0);
    }

    @Test
    void testHitTestGlissando_secondNoteGlissando_returnsCorrectIndex() {
        // Three-note line; only note at index 1 has a cached glissando
        var note0 = ElementType.CROTCHET.newInstance();
        note0.setUpper(true);
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setUpper(true);
        note1.setGlissando();
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setUpper(true);

        var line = detachedLine();
        line.addElement(note0);
        line.addElement(note1);
        line.addElement(note2);

        var glissando = note1.getGlissando();
        assertThat(glissando).isNotNull();

        setCachedGeometry(glissando, 5.0, 3.0, 0.0, 10.0);

        assertThat(hitTestSlide(10.0, 3.0, line)).isEqualTo(1);
    }

    // A grace note's glissando is not selectable, so a hit on it reports GraceGlissando.
    @Test
    void testHitTestGlissando_onGraceNote_returnsGraceGlissando() {
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setUpper(true);
        grace.setGlissando();

        var line = detachedLine();
        line.addElement(grace);
        line.addElement(ElementType.CROTCHET.newInstance());

        var glissando = grace.getGlissando();
        assertThat(glissando).isNotNull();

        setCachedGeometry(glissando, 5.0, 3.0, 0.0, 10.0);

        assertThat(hitTestSlide(10.0, 3.0, line)).isEqualTo(0);
    }

    /**
     * Creates a line with two notes at the given staff positions and accidentals.
     */
    private static Line makeTwoNoteLineWithGlissando(
        int staffPos1, StaffElement.@Nullable Accidental acc1,
        int staffPos2, StaffElement.@Nullable Accidental acc2) {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setUpper(true);
        note1.setStaffPosition(staffPos1);
        note1.setAccidental(acc1);
        note1.setGlissando();

        var note2 = ElementType.CROTCHET.newInstance();
        note2.setUpper(true);
        note2.setStaffPosition(staffPos2);
        note2.setAccidental(acc2);

        var line = detachedLine();
        line.addElement(note1);
        line.addElement(note2);

        return line;
    }

    // ======================================================================
    // Unison suppression — renderGlissando must not draw for same-pitch notes
    // (verify the connecting-glissando same-pitch early-return path)
    // ======================================================================

    @Test
    void testRenderGlissando_unisonConnected_noDrawingOccurs() {
        // Two notes at the same staff position → same MIDI pitch → unison suppression
        var line = makeTwoNoteLineWithGlissando(0, null, 0, null);
        var note = line.getElement(0);
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(LayoutResult.builder().build())
            .setCurrentLine(line)
            .build();

        var g2 = mock(Graphics2D.class);

        RENDERER.renderSlide(g2, line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        // Unison guard fires before render(); no fill/draw should reach g2
        verify(g2, never()).fill(any());
        verify(g2, never()).draw(any());
    }

    // ======================================================================
    // determineGlissandoColor — standalone glissando selection, implied
    // target selection (CONNECTED), and no-provider fallback (Row 21)
    // ======================================================================

    private static LineInvariants.Builder baseBuilder() {
        return RenderContextTestHelper.newContext(new Song());
    }

    @Test
    void testDetermineGlissandoColor_standaloneGlissandoSelected_returnsSelectionColor() {
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSlideSelected(0, 0)).thenReturn(true);
        var invariants = baseBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineSlideColor(
            0, false, invariants);

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDetermineGlissandoColor_connectedTargetNoteSelected_returnsSelectionColor() {
        // Implied target-note selection: index 0 source, index 1 is target
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSlideSelected(0, 0)).thenReturn(false);
        when(selectionProvider.isElementSelected(1, 0)).thenReturn(true);
        var invariants = baseBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineSlideColor(
            0, true, invariants);

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDetermineGlissandoColor_fallNextElementSelected_returnsBlack() {
        // A fall has no target note, so selecting the element after the fall's host note
        // (index 1) must NOT highlight the fall.
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSlideSelected(0, 0)).thenReturn(false);
        when(selectionProvider.isElementSelected(1, 0)).thenReturn(true);
        var invariants = baseBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineSlideColor(
            0, false, invariants);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDetermineGlissandoColor_fallStandaloneSelected_returnsSelectionColor() {
        // A fall directly selected as a standalone slide still highlights.
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSlideSelected(0, 0)).thenReturn(true);
        var invariants = baseBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineSlideColor(
            0, true, invariants);

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDetermineGlissandoColor_noSelectionProvider_returnsBlack() {
        // selectionProvider is null → early return with BLACK
        var invariants = baseBuilder()
            .setSelectionProvider(null)
            .build();

        var color = RENDERER.determineSlideColor(
            0, false, invariants);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDetermineGlissandoColor_playingNote_returnsPlayingColor() {
        // getElementColor returns non-BLACK for the playing note → early return
        // before glissando-specific selection logic fires
        var invariants = baseBuilder()
            .setPlayingNoteIndex(0)
            .build();

        var color = RENDERER.determineSlideColor(
            0, false, invariants);

        assertThat(color).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // ======================================================================
    // resolveNoteContext — direct field-wiring tests
    // ======================================================================

    @Test
    void testResolveNoteContext_mapsEachExtentToItsFieldAndStaffPositionToY() {
        // Pins resolveNoteContext's wiring: the fall anchor comes from the stem-full extent, the
        // glissando attach edges from the stem-free extent (left < right, so a glissLeft/glissRight
        // swap is caught), and cySs from the staff position.
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(false);
        note.setStaffPosition(0);

        var line = detachedLine();
        line.addElement(note);

        var middleLineYSs = 4.0;
        var ctx = SlideRenderer.resolveNoteContext(
            note, 0, line, LayoutResult.builder().build(), middleLineYSs);

        // elementXSs = 0 (note absent from the empty LayoutResult), so each field equals the raw extent.
        var fullExtent = NoteColumnGeometry.extentSs(note, false);
        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, false);

        assertThat(ctx.columnRightXSs()).isCloseTo(fullExtent.rightSs(), within(0.001));
        assertThat(ctx.glissRightXSs()).isCloseTo(attachExtent.rightSs(), within(0.001));
        assertThat(ctx.glissLeftXSs()).isCloseTo(attachExtent.leftSs(), within(0.001));
        // A glissLeft/glissRight swap would flip the ordering (left at the notehead left edge, 0;
        // right at the notehead right edge, positive).
        assertThat(ctx.glissLeftXSs()).isLessThan(ctx.glissRightXSs());
        assertThat(ctx.cySs()).isCloseTo(
            RenderingUtils.noteStaffPositionToCoordinateSs(0, middleLineYSs), within(0.001));
    }

    // ======================================================================
    // computeEndpoints — direct tests
    // ======================================================================

    /**
     * Builds a NoteContext for a crotchet centerd at (cxSs, cySs) with a symmetric
     * half-column of HALF_COLUMN_SS: glissRightXSs = cxSs + HALF_COLUMN_SS,
     * glissLeftXSs = cxSs - HALF_COLUMN_SS, columnRightXSs = cxSs + HALF_COLUMN_SS.
     */
    private static SlideRenderer.NoteContext noteContextCenteredAt(double cxSs, double cySs) {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        return new SlideRenderer.NoteContext(
            note,
            cySs,
            cxSs + HALF_COLUMN_SS,  // columnRightXSs
            cxSs - HALF_COLUMN_SS,  // glissLeftXSs
            cxSs + HALF_COLUMN_SS   // glissRightXSs
        );
    }

    @Test
    void testComputeEndpoints_zeroAttachLength_returnsNull() {
        // Centers exactly 1.0 ss apart: tgt.glissLeft (1.0 - 0.5 = 0.5) coincides with
        // src.glissRight (0.0 + 0.5 = 0.5), so attachLength = 0 ≤ 2*gap. The early guard
        // rejects this before the unit-vector division, preventing a divide-by-zero/NaN.
        var src = noteContextCenteredAt(0.0, 0.0);
        var tgt = noteContextCenteredAt(1.0, 0.0);
        assertThat(SlideRenderer.computeEndpoints(src, tgt)).isNull();
    }

    @Test
    void testComputeEndpoints_sameCenterDrawnTooShort_returnsNull() {
        // Same center: attachLength = 1.0 (tgt.glissLeft = 4.5, src.glissRight = 5.5),
        // drawn length = 1.0 - 2*0.4 = 0.2 < MIN_RECT_LENGTH_SS → null
        var src = noteContextCenteredAt(5.0, 0.0);
        var tgt = noteContextCenteredAt(5.0, 0.0);
        assertThat(SlideRenderer.computeEndpoints(src, tgt)).isNull();
    }

    @Test
    void testComputeEndpoints_shortConnected_returnsNull() {
        // Notes 0.1 ss apart: attachLength = 0.9, drawn = 0.9 - 2*0.4 = 0.1 < MIN_RECT_LENGTH_SS → null
        var src = noteContextCenteredAt(0.0, 0.0);
        var tgt = noteContextCenteredAt(0.1, 0.0);
        assertThat(SlideRenderer.computeEndpoints(src, tgt)).isNull();
    }

    @Test
    void testComputeEndpoints_nullTarget_returnsNull() {
        // No following note to connect to: a connecting glissando cannot be drawn.
        var src = noteContextCenteredAt(0.0, 0.0);
        assertThat(SlideRenderer.computeEndpoints(src, null)).isNull();
    }

    @Test
    void testComputeEndpoints_connected_returnsHorizontalEndpoints() {
        // Two notes 10 ss apart on the same staff line → a horizontal CONNECTED glissando.
        // startX = 0.0 + 0.5 + 0.4 = 0.9, endX = 10.0 - 0.5 - 0.4 = 9.1
        var srcCx = 0.0;
        var tgtCx = 10.0;
        var src = noteContextCenteredAt(srcCx, 0.0);
        var tgt = noteContextCenteredAt(tgtCx, 0.0);

        var result = SlideRenderer.computeEndpoints(src, tgt);
        assertThat(result).isNotNull();

        var endpoints = result;
        assertThat(endpoints).isNotNull();
        var expectedStartX = srcCx + HALF_COLUMN_SS + NoteGeometry.GLISSANDO_DRAWN_GAP_SS;
        var expectedEndX = tgtCx - HALF_COLUMN_SS - NoteGeometry.GLISSANDO_DRAWN_GAP_SS;
        assertThat(endpoints.startXSs()).isCloseTo(expectedStartX, within(0.01));
        assertThat(endpoints.endXSs()).isCloseTo(expectedEndX, within(0.01));
        assertThat(endpoints.startYSs()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.endYSs()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.angle()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.length()).isCloseTo(expectedEndX - expectedStartX, within(0.01));
    }

    // ======================================================================
    // computeEndpoints — along-line gap
    // ======================================================================

    @Test
    void testComputeEndpoints_steepGlissandoHasSmallerHorizontalInsetThanShallow() {
        // Along-line gap: the horizontal component of the inset is gap * cos θ, so it
        // shrinks as the line steepens. A shallow (horizontal) glissando has full inset = gap;
        // a 45° glissando has a smaller horizontal inset.
        var shallowResult = SlideRenderer.computeEndpoints(
            noteContextCenteredAt(0.0, 0.0), noteContextCenteredAt(10.0, 0.0));
        assertThat(shallowResult).isNotNull();

        var steepDy = 10.0;
        var steepResult = SlideRenderer.computeEndpoints(
            noteContextCenteredAt(0.0, 0.0), noteContextCenteredAt(10.0, steepDy));
        assertThat(steepResult).isNotNull();

        var attachStartX = HALF_COLUMN_SS;  // src.glissRightXSs = 0 + HALF_COLUMN_SS
        var shallowHorizontalInset = shallowResult.startXSs() - attachStartX;
        var steepHorizontalInset = steepResult.startXSs() - attachStartX;

        // Steep glissando has a smaller horizontal inset (gap * cosθ < gap)
        assertThat(steepHorizontalInset).isLessThan(shallowHorizontalInset);
        // Shallow (θ = 0): horizontal inset = gap exactly
        assertThat(shallowHorizontalInset).isCloseTo(NoteGeometry.GLISSANDO_DRAWN_GAP_SS, within(0.001));

        // Both endpoints lie on the attach-to-attach ray for the steep case
        var attachEndX = 10.0 - HALF_COLUMN_SS;  // tgt.glissLeftXSs
        var dx = attachEndX - attachStartX;
        var attachLength = Math.sqrt(dx * dx + steepDy * steepDy);
        var unitX = dx / attachLength;
        var unitY = steepDy / attachLength;
        var gap = NoteGeometry.GLISSANDO_DRAWN_GAP_SS;

        assertThat(steepResult.startXSs()).isCloseTo(attachStartX + unitX * gap, within(0.001));
        assertThat(steepResult.startYSs()).isCloseTo(unitY * gap, within(0.001));
        assertThat(steepResult.endXSs()).isCloseTo(attachEndX - unitX * gap, within(0.001));
        assertThat(steepResult.endYSs()).isCloseTo(steepDy - unitY * gap, within(0.001));
    }

    @Test
    void testComputeEndpoints_descendingGlissandoTrimsYDownward() {
        // Descending slide (target below source, dy < 0): the along-line trim must move the start
        // endpoint downward (negative Y) and the end endpoint upward toward the source, mirroring
        // the ascending case. Catches a sign error in the Y component of the trim or in atan2.
        var descendingDy = -10.0;
        var result = SlideRenderer.computeEndpoints(
            noteContextCenteredAt(0.0, 0.0), noteContextCenteredAt(10.0, descendingDy));
        assertThat(result).isNotNull();

        var attachStartX = HALF_COLUMN_SS;        // src.glissRightXSs
        var attachEndX = 10.0 - HALF_COLUMN_SS;   // tgt.glissLeftXSs
        var dx = attachEndX - attachStartX;
        var attachLength = Math.hypot(dx, descendingDy);
        var unitX = dx / attachLength;
        var unitY = descendingDy / attachLength;
        var gap = NoteGeometry.GLISSANDO_DRAWN_GAP_SS;

        var endpoints = result;
        assertThat(endpoints).isNotNull();
        assertThat(endpoints.startYSs()).isCloseTo(unitY * gap, within(0.001));
        assertThat(endpoints.startYSs()).isLessThan(0.0);
        assertThat(endpoints.endYSs()).isCloseTo(descendingDy - unitY * gap, within(0.001));
        assertThat(endpoints.angle()).isCloseTo(Math.atan2(descendingDy, dx), within(0.001));
    }

    // ======================================================================
    // NoteContext.shiftedX
    // ======================================================================

    /**
     * Creates an up-stem crotchet (leading note).
     */
    private static StaffElement upStemNote() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        return note;
    }

    @Test
    void testShiftedX_shiftsAllXFields_leavesYUnchanged() {
        var note = upStemNote();
        var columnRight = 3.0;
        var glissLeft = 1.0;
        var glissRight = 3.0;
        var cy = 2.5;
        var ctx = new SlideRenderer.NoteContext(note, cy, columnRight, glissLeft, glissRight);

        var shift = 3.0;
        var shifted = ctx.shiftedX(shift);

        assertThat(shifted.columnRightXSs()).isCloseTo(columnRight + shift, within(0.001));
        assertThat(shifted.glissLeftXSs()).isCloseTo(glissLeft + shift, within(0.001));
        assertThat(shifted.glissRightXSs()).isCloseTo(glissRight + shift, within(0.001));
        assertThat(shifted.cySs()).isCloseTo(cy, within(0.001));
    }

    // ======================================================================
    // renderPreviewGlissandoToPreviewElement — the grace→host preview line
    // ======================================================================

    /** The host preview's origin X: far enough right of the source to leave a drawable line. */
    private static final double PREVIEW_ELEMENT_X_SS = 6.0;

    /** Middle-line Y for the preview-glissando fixtures. */
    private static final double PREVIEW_MIDDLE_LINE_Y_SS = 4.0;

    /**
     * Both endpoints sit on the middle line, so the drawn line is horizontal and its ink bounds
     * are its endpoints exactly in X — {@code drawRoundedLine}'s rectangle spans [0, length] in
     * line-local coordinates, with no cap bulge past either end.
     */
    private static final int PREVIEW_STAFF_POSITION = 0;

    /** The source (grace) note is the fixture line's only element. */
    private static final int PREVIEW_SOURCE_INDEX = 0;

    private static final double GEOMETRY_TOLERANCE_SS = 0.001;

    /** A line holding one grace note at the origin — the preview glissando's source. */
    private static Line graceNoteLine() {
        NoteGeometry.initializeAccidentalWidths();

        var source = ElementType.GRACE_QUAVER.newInstance();
        source.setStaffPosition(PREVIEW_STAFF_POSITION);

        var line = detachedLine();
        line.addElement(source);

        return line;
    }

    private static StaffElement hostPreviewElement() {
        var previewElement = ElementType.CROTCHET.newInstance();
        previewElement.setStaffPosition(PREVIEW_STAFF_POSITION);
        return previewElement;
    }

    /**
     * Runs the preview glissando against a recorder and returns the ink it produced, or null when
     * nothing was drawn. The source note is absent from the empty {@link LayoutResult}, which
     * reports 0 for it, so the source sits at the origin.
     */
    private static @Nullable Rectangle2D recordPreviewGlissando(
        Line line, int sourceIndex, StaffElement previewElement, LineInvariants invariants
    ) {
        var recorder = new RecordingGraphics2D();
        recorder.setColor(Color.BLACK);

        RENDERER.renderPreviewGlissandoToPreviewElement(
            recorder, sourceIndex, line, previewElement, PREVIEW_ELEMENT_X_SS, invariants);

        return recorder.displayList().inkBoundsSs();
    }

    private static LineInvariants previewInvariants(Line line) {
        return RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setMiddleLineYSs(PREVIEW_MIDDLE_LINE_Y_SS)
            .build();
    }

    @Test
    void testRenderPreviewGlissandoToPreviewElement_spansSourceRightEdgeToPreviewLeftEdge() {
        var line = graceNoteLine();
        var previewElement = hostPreviewElement();
        var invariants = previewInvariants(line);

        var inkSs = recordPreviewGlissando(line, PREVIEW_SOURCE_INDEX, previewElement, invariants);

        assertThat(inkSs).as("expected the preview glissando to draw").isNotNull();

        var src = SlideRenderer.resolveNoteContext(
            line.getElement(PREVIEW_SOURCE_INDEX), PREVIEW_SOURCE_INDEX, line,
            invariants.getLayoutResult(), PREVIEW_MIDDLE_LINE_Y_SS);
        var tgt = SlideRenderer.noteContextAt(
            previewElement, PREVIEW_ELEMENT_X_SS, false, PREVIEW_MIDDLE_LINE_Y_SS);

        // Which end attaches where is the whole content of the src/tgt argument order: the source
        // contributes its right attach edge and the preview its left, each trimmed inward by the
        // drawn gap. A swap would start at the preview's right edge and end at the source's left.
        assertThat(inkSs.getMinX())
            .as("starts one drawn gap right of the grace note's attach edge")
            .isCloseTo(
                src.glissRightXSs() + NoteGeometry.GLISSANDO_DRAWN_GAP_SS,
                within(GEOMETRY_TOLERANCE_SS));
        assertThat(inkSs.getMaxX())
            .as("stops one drawn gap left of the host preview's attach edge")
            .isCloseTo(
                tgt.glissLeftXSs() - NoteGeometry.GLISSANDO_DRAWN_GAP_SS,
                within(GEOMETRY_TOLERANCE_SS));
    }

    @Test
    void testRenderPreviewGlissandoToPreviewElement_negativeSourceIndex_drawsNothing() {
        var line = graceNoteLine();

        var inkSs = recordPreviewGlissando(line, -1, hostPreviewElement(), previewInvariants(line));

        assertThat(inkSs)
            .as("a negative source index has no note to start from -> nothing is drawn")
            .isNull();
    }

    @Test
    void testRenderPreviewGlissandoToPreviewElement_sourceIndexPastEnd_drawsNothing() {
        var line = graceNoteLine();

        var inkSs = recordPreviewGlissando(
            line, line.elementCount(), hostPreviewElement(), previewInvariants(line));

        assertThat(inkSs)
            .as("a source index past the last element has no note to start from -> nothing is drawn")
            .isNull();
    }

    @Test
    void testNoteContextAt_beamedMovesTheDottedAttachEdge() {
        // beamed is noteContextAt's one input that is not read off the note, and it reaches the
        // geometry through dot placement: an unbeamed up-stem quaver carries a flag, which pushes
        // its augmentation dot — and with it the right attach edge — further out.
        var note = ElementType.QUAVER.newInstance();
        note.setUpper(true);
        note.setStaffPosition(PREVIEW_STAFF_POSITION);
        note.setDotCount(1);

        var unbeamed = SlideRenderer.noteContextAt(note, 0, false, PREVIEW_MIDDLE_LINE_Y_SS);
        var beamed = SlideRenderer.noteContextAt(note, 0, true, PREVIEW_MIDDLE_LINE_Y_SS);

        assertThat(beamed.glissRightXSs())
            .as("a beam removes the flag the augmentation dot has to clear")
            .isLessThan(unbeamed.glissRightXSs());
    }
}
