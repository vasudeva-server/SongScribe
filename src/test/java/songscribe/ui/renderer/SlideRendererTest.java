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
import songscribe.hit.HitTarget;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementColumnBuilder;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteColumnGeometry;
import songscribe.layout.NoteGeometry;
import songscribe.layout.SlideGeometry;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class SlideRendererTest extends UnitTest {

    private static final SlideRenderer RENDERER = SlideRenderer.getInstance();

    /**
     * A deliberately non-zero middle line for the hit tests, so a click point that is not
     * converted out of component space into midline-relative layout space misses every shape.
     */
    private static final double MIDDLE_LINE_Y_SS = 4.0;

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
    private SlideGeometry.@Nullable Endpoints computeConnectingGlissando(
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

        var src = SlideGeometry.noteContextAt(source, sourceColumn.getXSs(), false, 0.0);
        var tgt = SlideGeometry.noteContextAt(target, targetXSs, false, 0.0);

        return SlideGeometry.computeEndpoints(src, tgt);
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

    /**
     * Builds a layout holding synthetic glissando geometry for {@code note}, standing in for a
     * real layout pass. Angle is in degrees for readability; the method converts to radians.
     */
    private static LayoutResult glissandoLayout(
        StaffElement note,
        double startXSs, double startYSs,
        double angleDeg, double lengthSs) {
        var angleRad = Math.toRadians(angleDeg);
        var endpoints = new SlideGeometry.Endpoints(
            startXSs,
            startYSs,
            startXSs + lengthSs * Math.cos(angleRad),
            startYSs + lengthSs * Math.sin(angleRad),
            angleRad,
            lengthSs);

        return LayoutResult.builder()
            .putSlideLayout(note, LayoutResult.SlideLayout.ofGlissando(endpoints))
            .build();
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
    // Same-pitch suppression — renderGlissando must not draw for two notes at one pitch
    // (verify the connecting-glissando same-pitch early-return path)
    // ======================================================================

    @Test
    void testRenderGlissando_samePitchConnected_noDrawingOccurs() {
        // Two notes at the same staff position → same MIDI pitch → suppressed.
        // The layout is stubbed to hold drawable geometry, so only the renderer's own guard can
        // stop the draw — the branch that matters, since the preview path recomputes geometry
        // rather than reading the layout.
        var line = makeTwoNoteLineWithGlissando(0, null, 0, null);
        var note = line.getElement(0);
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(glissandoLayout(note, 5.0, 0.0, 0.0, 10.0))
            .setCurrentLine(line)
            .build();

        var g2 = mock(Graphics2D.class);

        RENDERER.renderSlide(g2, line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        // The guard fires before the geometry lookup; no fill/draw should reach g2
        verify(g2, never()).fill(any());
        verify(g2, never()).draw(any());
    }

    // ======================================================================
    // renderSlide — the drawn glissando comes from the layout
    // ======================================================================

    @Test
    void testRenderSlide_drawsTheLayoutSegmentTranslatedToComponentSpace() {
        var line = makeTwoNoteLineWithGlissando(0, null, -2, null);
        var note = line.getElement(0);
        var startXSs = 2.0;
        var startYSs = -1.5;
        var lengthSs = 6.0;
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(glissandoLayout(note, startXSs, startYSs, 0.0, lengthSs))
            .setCurrentLine(line)
            .setMiddleLineYSs(MIDDLE_LINE_Y_SS)
            .build();

        var recorder = new RecordingGraphics2D();
        recorder.setColor(Color.BLACK);

        RENDERER.renderSlide(recorder, line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        var inkSs = recorder.displayList().inkBoundsSs();
        assertThat(inkSs).as("expected the glissando to draw").isNotNull();
        assertThat(inkSs.getMinX())
            .as("the drawn segment starts where the layout put it")
            .isCloseTo(startXSs, within(GEOMETRY_TOLERANCE_SS));
        assertThat(inkSs.getMaxX())
            .as("the drawn segment ends one length further along")
            .isCloseTo(startXSs + lengthSs, within(GEOMETRY_TOLERANCE_SS));
        assertThat(inkSs.getCenterY())
            .as("the layout Y is midline-relative and must be shifted into component space")
            .isCloseTo(startYSs + MIDDLE_LINE_Y_SS, within(GEOMETRY_TOLERANCE_SS));
    }

    // ======================================================================
    // determineSlideColor — the slide's own selection, and the owner note's
    // color taking precedence over it
    // ======================================================================

    private static LineInvariants.Builder baseBuilder() {
        return RenderContextTestHelper.newContext(new Song());
    }

    @Test
    void testDetermineSlideColor_slideSelected_returnsSelectionColor() {
        var note = ElementType.CROTCHET.newInstance();
        note.setGlissando();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Slide(note), 0)).thenReturn(true);
        var invariants = baseBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineSlideColor(note, 0, invariants);

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDetermineSlideColor_anotherSlideSelected_returnsBlack() {
        var note = ElementType.CROTCHET.newInstance();
        note.setGlissando();
        var other = ElementType.CROTCHET.newInstance();
        other.setGlissando();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Slide(other), 0)).thenReturn(true);
        var invariants = baseBuilder()
            .setSelectionProvider(selectionProvider)
            .build();

        var color = RENDERER.determineSlideColor(note, 0, invariants);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDetermineSlideColor_noSelectionProvider_returnsBlack() {
        var note = ElementType.CROTCHET.newInstance();
        note.setGlissando();
        var invariants = baseBuilder()
            .setSelectionProvider(null)
            .build();

        var color = RENDERER.determineSlideColor(note, 0, invariants);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDetermineSlideColor_playingNote_returnsPlayingColor() {
        // getElementColor returns non-BLACK for the playing note → early return
        // before the slide's own selection is consulted
        var note = ElementType.CROTCHET.newInstance();
        note.setGlissando();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Slide(note), 0)).thenReturn(true);
        var invariants = baseBuilder()
            .setSelectionProvider(selectionProvider)
            .setPlayingNoteIndex(0)
            .build();

        var color = RENDERER.determineSlideColor(note, 0, invariants);

        assertThat(color).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // ======================================================================
    // determineSlideColor — a connecting glissando belongs to both notes it
    // joins, so the note it lands on colors it too
    // ======================================================================

    /** A two-element line: the first note carries {@code slide}, the second is its neighbour. */
    private Line slidePairLine(StaffElement owner) {
        var neighbour = ElementType.CROTCHET.newInstance();
        neighbour.setStaffPosition(2);
        var line = detachedLine();
        line.addElement(owner);
        line.addElement(neighbour);
        return line;
    }

    @Test
    void testDetermineSlideColor_glissandoTargetNoteSelected_returnsSelectionColor() {
        // The glissando is drawn from note 0; selecting note 1 — the note it lands on — must
        // color it, exactly as selecting note 0 does. Without this the user selects one end of
        // a glissando and the line stays black.
        var source = ElementType.CROTCHET.newInstance();
        source.setGlissando();
        var builder = baseBuilder();
        RenderContextTestHelper.enableSelection(builder, slidePairLine(source), 1);

        var color = RENDERER.determineSlideColor(source, 0, builder.build());

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDetermineSlideColor_fallDoesNotInheritFollowingNoteSelection() {
        // A fall is a standalone trailing glyph with no target note, so the element after its
        // host is an unrelated neighbour and must not color it.
        var host = ElementType.CROTCHET.newInstance();
        host.setFall();
        var builder = baseBuilder();
        RenderContextTestHelper.enableSelection(builder, slidePairLine(host), 1);

        var color = RENDERER.determineSlideColor(host, 0, builder.build());

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDetermineSlideColor_glissandoWithNeitherEndSelected_returnsBlack() {
        // The companion to the two above: with nothing selected the glissando is black, so those
        // tests are pinning the selection and not something that colors it unconditionally.
        var source = ElementType.CROTCHET.newInstance();
        source.setGlissando();
        var invariants = baseBuilder()
            .setCurrentLine(slidePairLine(source))
            .setSelectionProvider(mock(LineComponent.SelectionProvider.class))
            .build();

        var color = RENDERER.determineSlideColor(source, 0, invariants);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    // ======================================================================
    // SlideGeometry.noteContextAt — the wiring the renderer's previews rely on
    // ======================================================================

    @Test
    void testNoteContextAt_mapsEachExtentToItsFieldAndStaffPositionToY() {
        // Pins noteContextAt's wiring: the fall anchor comes from the stem-full extent, the
        // glissando attach edges from the stem-free extent (left < right, so a glissLeft/glissRight
        // swap is caught), and cySs from the staff position.
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(false);
        note.setStaffPosition(0);

        var ctx = SlideGeometry.noteContextAt(note, 0.0, false, MIDDLE_LINE_Y_SS);

        // elementXSs = 0, so each field equals the raw extent.
        var fullExtent = NoteColumnGeometry.extentSs(note, false);
        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, false);

        assertThat(ctx.columnRightXSs()).isCloseTo(fullExtent.rightSs(), within(GEOMETRY_TOLERANCE_SS));
        assertThat(ctx.glissRightXSs()).isCloseTo(attachExtent.rightSs(), within(GEOMETRY_TOLERANCE_SS));
        assertThat(ctx.glissLeftXSs()).isCloseTo(attachExtent.leftSs(), within(GEOMETRY_TOLERANCE_SS));
        // A glissLeft/glissRight swap would flip the ordering (left at the notehead left edge, 0;
        // right at the notehead right edge, positive).
        assertThat(ctx.glissLeftXSs()).isLessThan(ctx.glissRightXSs());
        assertThat(ctx.cySs()).isCloseTo(
            NoteGeometry.noteStaffPositionToCoordinateSs(0, MIDDLE_LINE_Y_SS), within(GEOMETRY_TOLERANCE_SS));
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
        var ctx = new SlideGeometry.NoteContext(note, cy, columnRight, glissLeft, glissRight);

        var shift = 3.0;
        var shifted = ctx.shiftedX(shift);

        assertThat(shifted.columnRightXSs()).isCloseTo(columnRight + shift, within(GEOMETRY_TOLERANCE_SS));
        assertThat(shifted.glissLeftXSs()).isCloseTo(glissLeft + shift, within(GEOMETRY_TOLERANCE_SS));
        assertThat(shifted.glissRightXSs()).isCloseTo(glissRight + shift, within(GEOMETRY_TOLERANCE_SS));
        assertThat(shifted.cySs()).isCloseTo(cy, within(GEOMETRY_TOLERANCE_SS));
    }

    // ======================================================================
    // renderPreviewGlissandoToPreviewElement — the grace→host preview line
    // ======================================================================

    /** The host preview's origin X: far enough right of the source to leave a drawable line. */
    private static final double PREVIEW_ELEMENT_X_SS = 6.0;

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
            .setMiddleLineYSs(MIDDLE_LINE_Y_SS)
            .build();
    }

    @Test
    void testRenderPreviewGlissandoToPreviewElement_spansSourceRightEdgeToPreviewLeftEdge() {
        var line = graceNoteLine();
        var previewElement = hostPreviewElement();
        var invariants = previewInvariants(line);

        var inkSs = recordPreviewGlissando(line, PREVIEW_SOURCE_INDEX, previewElement, invariants);

        assertThat(inkSs).as("expected the preview glissando to draw").isNotNull();

        var src = SlideGeometry.noteContextAt(
            line.getElement(PREVIEW_SOURCE_INDEX), 0.0, false, 0.0);
        var tgt = SlideGeometry.noteContextAt(previewElement, PREVIEW_ELEMENT_X_SS, false, 0.0);

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
        assertThat(inkSs.getCenterY())
            .as("the preview draws on the middle line, in component space")
            .isCloseTo(MIDDLE_LINE_Y_SS, within(GEOMETRY_TOLERANCE_SS));
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

        var unbeamed = SlideGeometry.noteContextAt(note, 0, false, MIDDLE_LINE_Y_SS);
        var beamed = SlideGeometry.noteContextAt(note, 0, true, MIDDLE_LINE_Y_SS);

        assertThat(beamed.glissRightXSs())
            .as("a beam removes the flag the augmentation dot has to clear")
            .isLessThan(unbeamed.glissRightXSs());
    }
}
