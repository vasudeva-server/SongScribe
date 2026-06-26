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

import java.util.List;
import java.util.Objects;

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

class GlissandoRendererTest extends UnitTest {

    /** Gap between column edge and glissando endpoint, mirroring GlissandoRenderer.GLISSANDO_DRAWN_GAP_SS. */
    private static final double GLISSANDO_DRAWN_GAP_SS = 0.25;

    /** Half-width used to define synthetic column extents in noteContextAt(). */
    private static final double HALF_COLUMN_SS = 0.5;

    private static final GlissandoRenderer RENDERER = GlissandoRenderer.getInstance();

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
    private GlissandoRenderer.@Nullable Endpoints computeConnectingGlissando(
        ElementType sourceType, boolean targetHasAccidental) {
        NoteGeometry.initializeAccidentalWidths();

        var source = sourceType.newInstance();
        source.setUpper(true);
        source.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        var target = ElementType.CROTCHET.newInstance();
        target.setUpper(true);

        if (targetHasAccidental) {
            target.setAccidental(StaffElement.Accidental.SHARP);
        }

        // Same staff position for both → horizontal line, the shortest (worst) case for the length check.
        var sourceRightSs = ElementColumnBuilder.calculateRightExtentSs(source, false, true);
        var targetRightSs = ElementColumnBuilder.calculateRightExtentSs(target, false, true);
        var targetLeftSs = ElementColumnBuilder.calculateLeftExtentSs(target);
        var sourceColumn = new ElementColumn(source, List.of(), 0.0, sourceRightSs, sourceRightSs, 0, 0, null, 0, false);
        var targetColumn = new ElementColumn(target, List.of(), targetLeftSs, targetRightSs, targetRightSs, 0, 0, null, 0, false);
        var targetXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(sourceColumn, targetColumn);

        // Source note: elementXSs = 0 (LayoutResult returns 0 when not in the map)
        var sourceExtent = NoteColumnGeometry.extentSs(source, false);
        var src = new GlissandoRenderer.NoteContext(
            source,
            0.0,
            sourceExtent.leftSs(),
            sourceExtent.rightSs(),
            null
        );

        // Target note: elementXSs = targetXSs; translate the local flag bbox to layout space if present.
        var targetExtent = NoteColumnGeometry.extentSs(target, false);
        var targetCySs = 0.0;
        Rectangle2D targetFlagBBoxLayout = null;

        if (targetExtent.flagBBoxLocal() != null) {
            var localFlag = targetExtent.flagBBoxLocal();
            targetFlagBBoxLayout = new Rectangle2D.Double(
                localFlag.getX() + targetXSs,
                localFlag.getY() + targetCySs,
                localFlag.getWidth(),
                localFlag.getHeight()
            );
        }

        var tgt = new GlissandoRenderer.NoteContext(
            target,
            targetCySs,
            targetXSs + targetExtent.leftSs(),
            targetXSs + targetExtent.rightSs(),
            targetFlagBBoxLayout
        );

        return GlissandoRenderer.computeEndpoints(src, tgt);
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
    // computeEndpoints — direct tests
    // ======================================================================

    /**
     * Builds a NoteContext for a crotchet centred at (cxSs, cySs) with a symmetric
     * half-column extent of HALF_COLUMN_SS on each side, no flag bbox.
     * startX = cxSs + HALF_COLUMN_SS + GLISSANDO_DRAWN_GAP_SS
     * endX   = cxSs - HALF_COLUMN_SS - GLISSANDO_DRAWN_GAP_SS
     */
    private static GlissandoRenderer.NoteContext noteContextAt(double cxSs, double cySs) {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        return new GlissandoRenderer.NoteContext(
            note,
            cySs,
            cxSs - HALF_COLUMN_SS,
            cxSs + HALF_COLUMN_SS,
            null
        );
    }

    @Test
    void testComputeEndpoints_zeroLengthConnected_returnsNull() {
        // src and tgt at identical column edges → endX < startX → null
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
        // Notes placed only 0.1 ss apart: endX = 0.1 - 0.5 - 0.25 = -0.65 < startX = 0.5 + 0.25 = 0.75 → null
        var src = noteContextAt(0.0, 0.0);
        var tgt = noteContextAt(0.1, 0.0);
        assertThat(GlissandoRenderer.computeEndpoints(src, tgt)).isNull();
    }

    @Test
    void testComputeEndpoints_connected_returnsHorizontalEndpoints() {
        // Two notes 10 ss apart on the same staff line → a horizontal CONNECTED glissando.
        // startX = 0.0 + 0.5 + 0.25 = 0.75, endX = 10.0 - 0.5 - 0.25 = 9.25
        var srcCx = 0.0;
        var tgtCx = 10.0;
        var src = noteContextAt(srcCx, 0.0);
        var tgt = noteContextAt(tgtCx, 0.0);

        var result = GlissandoRenderer.computeEndpoints(src, tgt);
        assertThat(result).isNotNull();

        var endpoints = Objects.requireNonNull(result);
        var expectedStartX = srcCx + HALF_COLUMN_SS + GLISSANDO_DRAWN_GAP_SS;
        var expectedEndX = tgtCx - HALF_COLUMN_SS - GLISSANDO_DRAWN_GAP_SS;
        assertThat(endpoints.startXSs()).isCloseTo(expectedStartX, within(0.01));
        assertThat(endpoints.endXSs()).isCloseTo(expectedEndX, within(0.01));
        assertThat(endpoints.startYSs()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.endYSs()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.angle()).isCloseTo(0.0, within(0.01));
        assertThat(endpoints.length()).isCloseTo(expectedEndX - expectedStartX, within(0.01));
    }

    // ======================================================================
    // computeEndpoints — conditional-flag matrix
    // ======================================================================

    /**
     * Builds a NoteContext with explicit column edges and a flag bbox in layout space.
     */
    private static GlissandoRenderer.NoteContext noteContextWithFlag(
        StaffElement note,
        double columnLeftXSs, double columnRightXSs, double cySs,
        Rectangle2D flagBBoxLayout
    ) {
        return new GlissandoRenderer.NoteContext(
            note, cySs, columnLeftXSs, columnRightXSs, flagBBoxLayout
        );
    }

    /**
     * Creates an up-stem crotchet (leading note).
     */
    private static StaffElement upStemNote() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        return note;
    }

    /**
     * Creates a down-stem crotchet (trailing note).
     */
    private static StaffElement downStemNote() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(false);
        return note;
    }

    @Test
    void testComputeEndpoints_leadingUpStemFlagIntersectsLine_startXPushedToFlagRight() {
        // Leading up-stem note, column X=[0, 1]. Flag bbox spans X=[0.5, 2.0], Y=[-1, 1].
        // The nominal start (columnRight + gap = 1.25) and the horizontal line at Y=0 run
        // through the flag, so startX is pushed past the flag's right edge to flagRight + gap.
        var srcNote = upStemNote();
        var columnLeft = 0.0;
        var columnRight = 1.0;
        var flagRight = 2.0;
        var flagBBox = new Rectangle2D.Double(0.5, -1.0, flagRight - 0.5, 2.0);
        var src = noteContextWithFlag(srcNote, columnLeft, columnRight, 0.0, flagBBox);

        // Target note far to the right with no flag, at Y=0 (horizontal line)
        var tgtNote = upStemNote();
        var tgt = new GlissandoRenderer.NoteContext(tgtNote, 0.0, 9.0, 10.0, null);

        var result = GlissandoRenderer.computeEndpoints(src, tgt);
        assertThat(result).isNotNull();

        var expectedStartX = flagRight + GLISSANDO_DRAWN_GAP_SS;
        assertThat(Objects.requireNonNull(result).startXSs()).isCloseTo(expectedStartX, within(0.01));
    }

    @Test
    void testComputeEndpoints_leadingUpStemFlagClearsLine_startXAtColumnRight() {
        // Leading up-stem note; flag bbox is entirely above Y=0 so the horizontal line clears it.
        var srcNote = upStemNote();
        var columnLeft = 0.0;
        var columnRight = 1.0;
        // Flag bbox placed far above the notehead Y line: Y=[-5, -3]
        var flagBBox = new Rectangle2D.Double(0.0, -5.0, 2.0, 2.0);
        var src = noteContextWithFlag(srcNote, columnLeft, columnRight, 0.0, flagBBox);

        var tgtNote = upStemNote();
        var tgt = new GlissandoRenderer.NoteContext(tgtNote, 0.0, 9.0, 10.0, null);

        var result = GlissandoRenderer.computeEndpoints(src, tgt);
        assertThat(result).isNotNull();

        var expectedStartX = columnRight + GLISSANDO_DRAWN_GAP_SS;
        assertThat(Objects.requireNonNull(result).startXSs()).isCloseTo(expectedStartX, within(0.01));
    }

    @Test
    void testComputeEndpoints_trailingDownStemFlagIntersectsLine_endXPushedToFlagLeft() {
        // Trailing down-stem note; flag is on the left side.
        // Source note at [0, 1]; target at [8, 9] with flag spanning [7.0, 8.5] in X and [-1, 1] in Y.
        // The nominal endX = 8.0 - 0.25 = 7.75, which lands inside the flag [7.0, 8.5].
        var srcNote = upStemNote();
        var src = new GlissandoRenderer.NoteContext(srcNote, 0.0, 0.0, 1.0, null);

        var tgtNote = downStemNote();
        var flagLeft = 7.0;
        var flagBBox = new Rectangle2D.Double(flagLeft, -1.0, 1.5, 2.0);
        var tgt = noteContextWithFlag(tgtNote, 8.0, 9.0, 0.0, flagBBox);

        var result = GlissandoRenderer.computeEndpoints(src, tgt);
        assertThat(result).isNotNull();

        var expectedEndX = flagLeft - GLISSANDO_DRAWN_GAP_SS;
        assertThat(Objects.requireNonNull(result).endXSs()).isCloseTo(expectedEndX, within(0.01));
    }

    @Test
    void testComputeEndpoints_trailingDownStemFlagClearsLine_endXAtColumnLeft() {
        // Trailing down-stem note; flag bbox placed above Y=0 so the horizontal line clears it.
        var srcNote = upStemNote();
        var src = new GlissandoRenderer.NoteContext(srcNote, 0.0, 0.0, 1.0, null);

        var tgtNote = downStemNote();
        var columnLeft = 8.0;
        // Flag bbox placed far above the notehead Y line: Y=[-5, -3]
        var flagBBox = new Rectangle2D.Double(7.0, -5.0, 2.0, 2.0);
        var tgt = noteContextWithFlag(tgtNote, columnLeft, 9.0, 0.0, flagBBox);

        var result = GlissandoRenderer.computeEndpoints(src, tgt);
        assertThat(result).isNotNull();

        var expectedEndX = columnLeft - GLISSANDO_DRAWN_GAP_SS;
        assertThat(Objects.requireNonNull(result).endXSs()).isCloseTo(expectedEndX, within(0.01));
    }

    @Test
    void testComputeEndpoints_slideOutLeadingFlagIntersectsRay_startXPushedToFlagRight() {
        // SLIDE_OUT (tgt=null) from an up-stem note whose flag bbox straddles the slide-out ray.
        // The ray departs at SLIDE_OUT_ANGLE_DEG from the nominal start (columnRight + gap = 1.25);
        // the flag spans X=[1.0, 2.5], Y=[-0.5, 1.0], so the ray origin sits inside it and startX
        // is pushed past the flag's right edge to flagRight + gap.
        var srcNote = upStemNote();
        var columnRight = 1.0;
        var flagRight = 2.5;
        var flagBBox = new Rectangle2D.Double(1.0, -0.5, flagRight - 1.0, 1.5);
        var src = noteContextWithFlag(srcNote, 0.0, columnRight, 0.0, flagBBox);

        var result = GlissandoRenderer.computeEndpoints(src, null);
        assertThat(result).isNotNull();

        var expectedStartX = flagRight + GLISSANDO_DRAWN_GAP_SS;
        assertThat(Objects.requireNonNull(result).startXSs()).isCloseTo(expectedStartX, within(0.01));
    }

    // ======================================================================
    // NoteContext.shiftedX
    // ======================================================================

    @Test
    void testShiftedX_shiftsColumnEdgesAndFlagBBoxX_leavesYUnchanged() {
        var note = upStemNote();
        var columnLeft = 1.0;
        var columnRight = 3.0;
        var flagX = 2.0;
        var flagY = -4.0;
        var flagW = 1.5;
        var flagH = 1.0;
        var cy = 2.5;
        var flagBBox = new Rectangle2D.Double(flagX, flagY, flagW, flagH);
        var ctx = new GlissandoRenderer.NoteContext(note, cy, columnLeft, columnRight, flagBBox);

        var shift = 3.0;
        var shifted = ctx.shiftedX(shift);

        assertThat(shifted.columnLeftXSs()).isCloseTo(columnLeft + shift, within(0.001));
        assertThat(shifted.columnRightXSs()).isCloseTo(columnRight + shift, within(0.001));
        assertThat(shifted.cySs()).isCloseTo(cy, within(0.001));
        assertThat(shifted.flagBBoxLayout()).isNotNull();
        var shiftedFlag = Objects.requireNonNull(shifted.flagBBoxLayout());
        assertThat(shiftedFlag.getX()).isCloseTo(flagX + shift, within(0.001));
        assertThat(shiftedFlag.getY()).isCloseTo(flagY, within(0.001));
        assertThat(shiftedFlag.getWidth()).isCloseTo(flagW, within(0.001));
        assertThat(shiftedFlag.getHeight()).isCloseTo(flagH, within(0.001));
    }
}
