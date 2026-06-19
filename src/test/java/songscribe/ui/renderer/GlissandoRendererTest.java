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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class GlissandoRendererTest extends UnitTest {

    /** Step size used as both the search step parameter and assertion tolerance basis. */
    private static final double STEP_SS = 0.1;
    private static final GlissandoRenderer RENDERER = GlissandoRenderer.getInstance();

    // ======================================================================
    // computeFarBoundsT tests
    // ======================================================================

    @Test
    void testComputeFarBoundsTDiagonal() {
        // Bounds from (0, 0) to (4, 2), center at (2, 1), going at 45° (nx=ny=1/√2)
        // tx = (4-2) / (1/√2) = 2√2 ≈ 2.83
        // ty = (2-1) / (1/√2) = √2 ≈ 1.41
        // Expected: min = ty ≈ 1.41
        var bounds = new Rectangle2D.Double(0, 0, 4, 2);
        var invariants = 1.0 / Math.sqrt(2);
        var t = GlissandoRenderer.computeFarBoundsT(bounds, 2, 1, invariants, invariants);
        assertThat(t).isCloseTo(Math.sqrt(2), within(STEP_SS));
    }

    @Test
    void testComputeFarBoundsTRightward() {
        // Bounds from (-2, -1) to (3, 1), center at (0, 0), going right (nx=1, ny=0)
        // Expected: t = (maxX - cx) / nx = (3 - 0) / 1 = 3.0
        var bounds = new Rectangle2D.Double(-2, -1, 5, 2);
        var t = GlissandoRenderer.computeFarBoundsT(bounds, 0, 0, 1, 0);
        assertThat(t).isCloseTo(3.0, within(STEP_SS));
    }

    // ======================================================================
    // findNoteAreaEntryPoint tests
    // ======================================================================

    @Test
    void testFindEntryPoint_circle() {
        // Circle centered at (0, 0) with radius 2; offsetSs baked into offset area
        var circle = new Area(new Ellipse2D.Double(-2, -2, 4, 4));
        var offsetSs = 0.3f;
        var offsetArea = NoteAreaBuilder.createOffsetArea(circle, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        // Direction: right (nx=1, ny=0)
        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 0, 0, 1, 0, STEP_SS);

        // Endpoint should be just past the offset area boundary (radius + offsetSs)
        assertThat(endpoint.x).isGreaterThanOrEqualTo(2.0 + offsetSs - STEP_SS);
        assertThat(endpoint.y).isCloseTo(0.0, within(STEP_SS * 2));
    }

    @Test
    void testFindEntryPoint_compositeArea() {
        // Rectangle + circle union; endpoint should be past the furthest component
        var composite = new Area(new Rectangle2D.Double(0, 0, 4, 2));
        composite.add(new Area(new Ellipse2D.Double(3, -1, 4, 4)));
        var offsetSs = 0.3f;
        var offsetArea = NoteAreaBuilder.createOffsetArea(composite, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        // Direction: right from center (2, 1)
        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 2, 1, 1, 0, STEP_SS);

        // The circle extends to x=7, offset boundary ~7+offsetSs; endpoint just past that
        assertThat(endpoint.x).isGreaterThanOrEqualTo(7.0);
        assertThat(endpoint.y).isCloseTo(1.0, within(STEP_SS * 2));
    }

    @Test
    void testFindEntryPoint_fallback() {
        // Center is outside the offset area — should return center
        var farRect = new Area(new Rectangle2D.Double(5, 5, 2, 2));
        var offsetSs = 0.3f;
        var offsetArea = NoteAreaBuilder.createOffsetArea(farRect, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 0, 0, 1, 0, STEP_SS);

        // Fallback: center returned
        assertThat(endpoint.x).isCloseTo(0.0, within(STEP_SS));
        assertThat(endpoint.y).isCloseTo(0.0, within(STEP_SS));
    }

    @Test
    void testFindEntryPoint_zeroDirection() {
        var rect = new Area(new Rectangle2D.Double(-1, -1, 2, 2));
        var offsetSs = 0.3f;
        var offsetArea = NoteAreaBuilder.createOffsetArea(rect, offsetSs);
        var offsetBounds = offsetArea.getBounds2D();

        // nx=ny=0 should trigger the zero-direction guard and return center
        var endpoint = GlissandoRenderer.findNoteAreaEntryPoint(
            offsetArea, offsetBounds, 0, 0, 0, 0, STEP_SS);

        assertThat(endpoint.x).isCloseTo(0.0, within(STEP_SS));
        assertThat(endpoint.y).isCloseTo(0.0, within(STEP_SS));
    }

    // ======================================================================
    // hitTestGlissando tests
    // ======================================================================

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
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 0.0, 0.0, 45.0, 10.0);

        var mid = 5.0 * Math.cos(Math.toRadians(45.0));
        assertThat(RENDERER.hitTestGlissando(mid, mid, line)).isEqualTo(0);
    }

    @Test
    void testHitTestGlissando_noCachedGeometry_skipped() {
        // Note has a Glissando object but hasCachedGeometry is false (default) — must be skipped
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);

        assertThat(RENDERER.hitTestGlissando(10.0, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointAfterEnd_returnsMinusOne() {
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        // localX = 15.1 - 5.0 = 10.1 > cachedLength (10.0)
        assertThat(RENDERER.hitTestGlissando(15.1, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointBeforeStart_returnsMinusOne() {
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        // localX = 4.9 - 5.0 = -0.1 < 0
        assertThat(RENDERER.hitTestGlissando(4.9, 3.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointBesideLine_returnsMinusOne() {
        // Same glissando, but click is 1.0 ss above (> halfHitSs = 0.5)
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        assertThat(RENDERER.hitTestGlissando(10.0, 4.0, line)).isEqualTo(-1);
    }

    @Test
    void testHitTestGlissando_pointOnLine_returnsNoteIndex() {
        // Horizontal glissando (angle=0) from (5.0, 3.0) with length 10.0
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        setCachedGeometry(Objects.requireNonNull(line.getElement(0).getGlissando()), 5.0, 3.0, 0.0, 10.0);

        // Click at the midpoint: localX=5, localY=0 — well within hit bounds
        assertThat(RENDERER.hitTestGlissando(10.0, 3.0, line)).isEqualTo(0);
    }

    @Test
    void testHitTestGlissando_secondNoteGlissando_returnsCorrectIndex() {
        // Three-note line; only note at index 1 has a cached glissando
        var note0 = ElementType.CROTCHET.newInstance();
        note0.setUpper(true);
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setUpper(true);
        note1.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setUpper(true);

        var line = detachedLine();
        line.addElement(note0);
        line.addElement(note1);
        line.addElement(note2);

        setCachedGeometry(Objects.requireNonNull(note1.getGlissando()), 5.0, 3.0, 0.0, 10.0);

        assertThat(RENDERER.hitTestGlissando(10.0, 3.0, line)).isEqualTo(1);
    }

    // ======================================================================
    // Unison connected glissando tests
    //
    // These verify that getPitch() (MIDI pitch) — not staff position —
    // governs whether a connected glissando is considered unison.
    // ======================================================================

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
        note1.setGlissando(StaffElement.Glissando.Type.CONNECTED);

        var note2 = ElementType.CROTCHET.newInstance();
        note2.setUpper(true);
        note2.setStaffPosition(staffPos2);
        note2.setAccidental(acc2);

        var line = detachedLine();
        line.addElement(note1);
        line.addElement(note2);

        return line;
    }

    @Test
    void testNonUnisonConnectedGlissandoDifferentPosition() {
        // Two notes at different staff positions — different MIDI pitch
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        var note1 = line.getElement(0);
        var note2 = line.getElement(1);

        assertThat(note1.getPitch()).isNotEqualTo(note2.getPitch());
    }

    @Test
    void testNonUnisonConnectedGlissandoSamePositionDifferentAccidental() {
        // Same staff position but natural vs sharp — different MIDI pitch
        // This confirms getPitch() is used, not getStaffPosition()
        var line = makeTwoNoteLineWithGlissando(0, StaffElement.Accidental.NATURAL, 0, StaffElement.Accidental.SHARP);
        var note1 = line.getElement(0);
        var note2 = line.getElement(1);

        assertThat(note1.getStaffPosition()).isEqualTo(note2.getStaffPosition());
        assertThat(note1.getPitch()).isNotEqualTo(note2.getPitch());
    }

    @Test
    void testUnisonConnectedGlissandoSamePitch() {
        // Two notes at same staff position, no accidentals — same MIDI pitch
        var line = makeTwoNoteLineWithGlissando(0, null, 0, null);
        var note1 = line.getElement(0);
        var note2 = line.getElement(1);

        assertThat(note1.getPitch()).isEqualTo(note2.getPitch());
    }

    // ======================================================================
    // Unison suppression — renderGlissando must not draw for same-pitch notes
    // (Row 20: verify the CONNECTED same-pitch early-return path)
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

        RENDERER.renderGlissando(g2, line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        // Unison guard fires before render(); no fill/draw should reach g2
        verify(g2, never()).fill(org.mockito.ArgumentMatchers.any());
        verify(g2, never()).draw(org.mockito.ArgumentMatchers.any());
    }

    // ======================================================================
    // determineGlissandoColor — standalone glissando selection, implied
    // target selection (CONNECTED), and no-provider fallback (Row 21)
    // ======================================================================

    private static LineInvariants.Builder editModeBuilder() {
        return RenderContextTestHelper.newContext(new Song())
            .setEditMode(true);
    }

    @Test
    void testDetermineGlissandoColor_standaloneGlissandoSelected_returnsSelectionColor() {
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isGlissandoSelected(0, 0)).thenReturn(true);
        var invariants = editModeBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineGlissandoColor(
            0, StaffElement.Glissando.Type.CONNECTED, invariants);

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDetermineGlissandoColor_connectedTargetNoteSelected_returnsSelectionColor() {
        // Implied target-note selection: index 0 source, index 1 is target
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isGlissandoSelected(0, 0)).thenReturn(false);
        when(selectionProvider.isElementSelected(1, 0)).thenReturn(true);
        var invariants = editModeBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineGlissandoColor(
            0, StaffElement.Glissando.Type.CONNECTED, invariants);

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDetermineGlissandoColor_slideOutTargetNoteSelected_returnsBlack() {
        // SLIDE_OUT does not imply target selection; even if isElementSelected(1) is true,
        // the type guard prevents the implied-selection branch from firing.
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isGlissandoSelected(0, 0)).thenReturn(false);
        when(selectionProvider.isElementSelected(1, 0)).thenReturn(true);
        var invariants = editModeBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineGlissandoColor(
            0, StaffElement.Glissando.Type.SLIDE_OUT, invariants);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDetermineGlissandoColor_noSelectionProvider_returnsBlack() {
        // selectionProvider is null → early return with BLACK
        var invariants = editModeBuilder()
            .setSelectionProvider(null)
            .build();

        var color = RENDERER.determineGlissandoColor(
            0, StaffElement.Glissando.Type.CONNECTED, invariants);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDetermineGlissandoColor_playingNote_returnsPlayingColor() {
        // getElementColor returns non-BLACK for the playing note → early return
        // before glissando-specific selection logic fires
        var invariants = editModeBuilder()
            .setPlayingNoteIndex(0)
            .build();

        var color = RENDERER.determineGlissandoColor(
            0, StaffElement.Glissando.Type.CONNECTED, invariants);

        assertThat(color).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // ======================================================================
    // computeEndpoints — direct tests (Row 23)
    // ======================================================================

    /**
     * Builds a NoteContext for a crotchet at the given layout-space center.
     */
    private static GlissandoRenderer.NoteContext noteContextAt(double cxSs, double cySs) {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        var area = NoteAreaBuilder.createOffsetArea(
            new Area(new Rectangle2D.Double(-0.5, -0.5, 1.0, 1.0)),
            (float) NoteAreaBuilder.MIN_GAP_SS);
        var bounds = area.getBounds2D();
        return new GlissandoRenderer.NoteContext(note, cxSs, cySs, area, bounds);
    }

    @Test
    void testComputeEndpoints_zeroLengthConnected_returnsNull() {
        // src and tgt at identical positions → direction vector is zero → null
        var src = noteContextAt(5.0, 0.0);
        var tgt = noteContextAt(5.0, 0.0);
        assertThat(GlissandoRenderer.computeEndpoints(src, tgt)).isNull();
    }

    @Test
    void testComputeEndpoints_slideOut_returnsNonNull_withCorrectAngle() {
        // SLIDE_OUT (tgt=null): direction is fixed at SLIDE_OUT_ANGLE_DEG = 30°
        var src = noteContextAt(0.0, 0.0);
        var result = GlissandoRenderer.computeEndpoints(src, null);
        assertThat(result).isNotNull();
        // The angle stored in Endpoints is atan2(ny, nx) = 30° in radians
        assertThat(Objects.requireNonNull(result).angle()).isCloseTo(Math.toRadians(30.0), within(0.01));
    }

    @Test
    void testComputeEndpoints_shortConnected_returnsNull() {
        // Notes placed only 0.1 ss apart → glissando too short to render (< MIN_RECT_LENGTH_SS = 1.0)
        // The offset areas overlap; after finding entry points the net length < 1.0 → null
        var src = noteContextAt(0.0, 0.0);
        var tgt = noteContextAt(0.1, 0.0);
        assertThat(GlissandoRenderer.computeEndpoints(src, tgt)).isNull();
    }

    @Test
    void testComputeEndpoints_connected_returnsHorizontalEndpoints() {
        // Two notes 10 ss apart on the same staff line → a horizontal CONNECTED
        // glissando. Guards the simplified endpoint math after manual translate
        // removal (issue #455): the source endpoint precedes the target, the run
        // is horizontal, and the cached length matches the X span.
        var src = noteContextAt(0.0, 0.0);
        var tgt = noteContextAt(10.0, 0.0);

        var result = GlissandoRenderer.computeEndpoints(src, tgt);
        assertThat(result).isNotNull();

        var endpoints = Objects.requireNonNull(result);
        assertThat(endpoints.angle()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.startXSs()).isLessThan(endpoints.endXSs());
        assertThat(endpoints.startYSs()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.endYSs()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.length())
            .isCloseTo(endpoints.endXSs() - endpoints.startXSs(), within(0.01));
    }
}
