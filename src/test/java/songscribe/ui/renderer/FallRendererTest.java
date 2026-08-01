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

import module java.desktop;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Verifies the new fall-specific rendering: a {@link StaffElement.Fall} draws the
 * {@code brassFallLipShort} glyph one {@link NoteGeometry#FALL_GAP_SS} past the host
 * note's column-right edge, at the note's notehead-center Y, caches the glyph's drawn rect, and is
 * selected when that rect is clicked.
 */
class FallRendererTest extends UnitTest {

    private static final SlideRenderer RENDERER = SlideRenderer.getInstance();

    private static final double TOLERANCE_SS = 0.001;

    // A synthetic hit rect for the click-to-select tests, decoupled from font metrics.
    private static final double HIT_RECT_X_SS = 5.0;
    private static final double HIT_RECT_Y_SS = 3.0;
    private static final double HIT_RECT_WIDTH_SS = 1.5;
    private static final double HIT_RECT_HEIGHT_SS = 2.0;

    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    private static StaffElement fallNote() {
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        note.setFall();
        return note;
    }

    @Test
    void testRenderFallCachesGlyphRectAtNotePitchToTheRight() {
        var note = fallNote();
        var line = detachedLine();
        line.addElement(note);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(LayoutResult.builder().build())
            .setCurrentLine(line)
            .build();

        var slide = note.getSlide();
        assertThat(slide).isNotNull();

        var fall = (StaffElement.Fall) slide;
        assertThat(fall.cachedHitBounds).as("hit bounds unset before render").isNull();

        RENDERER.renderSlide(
            mock(Graphics2D.class), line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        // The glyph hangs one gap past the note's column-right edge (elementX is 0 for an unmapped
        // note), at the note's notehead-center Y; its drawn rect is the glyph bbox translated there.
        var columnRightXSs = NoteColumnGeometry.extentSs(note, false).rightSs();
        var glyphXSs = columnRightXSs + NoteGeometry.FALL_GAP_SS;
        var glyphYSs = RenderingUtils.noteStaffPositionToCoordinateSs(
            note.getStaffPosition(), invariants.getMiddleLineYSs());
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.BRASS_FALL_LIP_SHORT);

        var bounds = fall.cachedHitBounds;
        assertThat(bounds).isNotNull();
        assertThat(bounds.getX())
            .as("glyph left edge is one gap right of the note column")
            .isCloseTo(glyphXSs + bbox.left(), within(TOLERANCE_SS));
        assertThat(bounds.getY())
            .as("glyph sits at the host note's pitch")
            .isCloseTo(glyphYSs + bbox.top(), within(TOLERANCE_SS));
        assertThat(bounds.getWidth()).isCloseTo(bbox.width(), within(TOLERANCE_SS));
        assertThat(bounds.getHeight()).isCloseTo(bbox.height(), within(TOLERANCE_SS));
    }

    @Test
    void testRenderFallBeforeBarlineDoesNotResolveTargetAndCachesRect() {
        // Regression: a fall is a standalone trailing glyph with no target note. When a
        // non-renderable element (a final double barline) follows it, resolving a target
        // context used to call NoteColumnGeometry.extentSs on the barline and crash the paint.
        var note = fallNote();
        var line = detachedLine();
        line.addElement(note);
        line.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(LayoutResult.builder().build())
            .setCurrentLine(line)
            .build();

        var slide = note.getSlide();
        assertThat(slide).isNotNull();

        var fall = (StaffElement.Fall) slide;

        RENDERER.renderSlide(
            mock(Graphics2D.class), line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        assertThat(fall.cachedHitBounds)
            .as("the fall still renders and caches its hit rect when a barline follows")
            .isNotNull();
    }

    @Test
    void testRenderPreviewFallDrawsWithoutMutatingSourceNote() {
        // The hover preview draws over a plain note (no fall yet) and must not resolve the
        // following element nor attach a slide — it is a transient preview only.
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(true);
        var line = detachedLine();
        line.addElement(note);
        line.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(LayoutResult.builder().build())
            .setCurrentLine(line)
            .build();

        RENDERER.renderPreviewFall(mock(Graphics2D.class), 0, line, invariants);

        assertThat(note.getSlide())
            .as("preview rendering leaves the source note unchanged")
            .isNull();
    }

    /**
     * Builds a hit-test context for a slide hit test, which reads only the point and the line.
     */
    private static int hitTestSlide(double xSs, double ySs, Line line) {
        return RENDERER.hitTestSlide(xSs, ySs, line);
    }

    @Test
    void testClickInsideFallRectSelectsOwnerNote() {
        var note = fallNote();
        var line = detachedLine();
        line.addElement(note);

        var slide = note.getSlide();
        assertThat(slide).isNotNull();

        var fall = (StaffElement.Fall) slide;
        fall.cachedHitBounds = new Rectangle2D.Double(
            HIT_RECT_X_SS, HIT_RECT_Y_SS, HIT_RECT_WIDTH_SS, HIT_RECT_HEIGHT_SS);

        var clickXSs = HIT_RECT_X_SS + HIT_RECT_WIDTH_SS / 2.0;
        var clickYSs = HIT_RECT_Y_SS + HIT_RECT_HEIGHT_SS / 2.0;

        assertThat(hitTestSlide(clickXSs, clickYSs, line)).isEqualTo(0);
    }

    @Test
    void testClickOutsideFallRectIsNotAHit() {
        var note = fallNote();
        var line = detachedLine();
        line.addElement(note);

        var slide = note.getSlide();
        assertThat(slide).isNotNull();

        var fall = (StaffElement.Fall) slide;
        fall.cachedHitBounds = new Rectangle2D.Double(
            HIT_RECT_X_SS, HIT_RECT_Y_SS, HIT_RECT_WIDTH_SS, HIT_RECT_HEIGHT_SS);

        // Origin is well clear of the rect.
        assertThat(hitTestSlide(0.0, 0.0, line)).isEqualTo(-1);
    }

    @Test
    void testFallWithoutCachedBoundsIsNotHit() {
        var note = fallNote();
        var line = detachedLine();
        line.addElement(note);

        // No render pass occurred, so cachedHitBounds is null and nothing can be hit.
        assertThat(hitTestSlide(HIT_RECT_X_SS, HIT_RECT_Y_SS, line)).isEqualTo(-1);
    }
}
