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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;

import module java.desktop;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteColumnGeometry;
import songscribe.layout.NoteGeometry;
import songscribe.layout.SlideGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Verifies the fall-specific rendering: a {@link StaffElement.Fall} draws the
 * {@code brassFallLipShort} glyph one {@link NoteGeometry#FALL_GAP_SS} past the host
 * note's column-right edge, at the note's notehead-center Y, and is selected when the rect the
 * layout computed for that glyph is clicked.
 */
class FallRendererTest extends UnitTest {

    private static final SlideRenderer RENDERER = SlideRenderer.getInstance();

    private static final double TOLERANCE_SS = 0.001;

    /**
     * A deliberately non-zero middle line, so geometry that is not converted between layout space
     * (Y measured from the staff midline) and component space lands visibly off target.
     */
    private static final double MIDDLE_LINE_Y_SS = 4.0;

    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    private static StaffElement fallNote() {
        var note = crotchet();
        note.setUpper(true);
        note.setFall();
        return note;
    }

    /** The layout the engine would produce for {@code note}'s fall, in layout space. */
    private static LayoutResult fallLayout(StaffElement note) {
        var boundsSs = SlideGeometry.computeFallBoundsSs(
            SlideGeometry.noteContextAt(note, 0.0, false, 0.0));

        return LayoutResult.builder()
            .putSlideLayout(note, LayoutResult.SlideLayout.ofFall(boundsSs))
            .build();
    }

    private static LineInvariants fallInvariants(Line line, LayoutResult layoutResult) {
        return RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setCurrentLine(line)
            .setMiddleLineYSs(MIDDLE_LINE_Y_SS)
            .build();
    }

    @Test
    void testRenderFallDrawsTheLayoutGlyphRectAtNotePitchToTheRight() {
        var note = fallNote();
        var line = detachedLine();
        line.addElement(note);

        var invariants = fallInvariants(line, fallLayout(note));
        var recorder = new RecordingGraphics2D();
        recorder.setColor(Color.BLACK);

        RENDERER.renderSlide(recorder, line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        // The glyph hangs one gap past the note's column-right edge (elementX is 0 for an unmapped
        // note), at the note's notehead-center Y in component space; its ink is the glyph bbox
        // translated there.
        var columnRightXSs = NoteColumnGeometry.extentSs(note, false).rightSs();
        var glyphXSs = columnRightXSs + NoteGeometry.FALL_GAP_SS;
        var glyphYSs = NoteGeometry.noteStaffPositionToCoordinateSs(
            note.getStaffPosition(), MIDDLE_LINE_Y_SS);
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.BRASS_FALL_LIP_SHORT);

        var inkSs = recorder.displayList().inkBoundsSs();
        assertThat(inkSs).as("expected the fall glyph to draw").isNotNull();
        assertThat(inkSs.getMinX())
            .as("glyph left edge is one gap right of the note column")
            .isCloseTo(glyphXSs + bbox.left(), within(TOLERANCE_SS));
        assertThat(inkSs.getMinY())
            .as("glyph sits at the host note's pitch, converted into component space")
            .isCloseTo(glyphYSs + bbox.top(), within(TOLERANCE_SS));
    }

    @Test
    void testRenderFallBeforeBarlineDoesNotResolveTarget() {
        // Regression: a fall is a standalone trailing glyph with no target note. When a
        // non-renderable element (a final double barline) follows it, resolving a target
        // context used to call NoteColumnGeometry.extentSs on the barline and crash the paint.
        var note = fallNote();
        var line = detachedLine();
        line.addElement(note);
        line.addElement(finalDoubleBarline());

        var invariants = fallInvariants(line, fallLayout(note));
        var recorder = new RecordingGraphics2D();
        recorder.setColor(Color.BLACK);

        RENDERER.renderSlide(recorder, line, note, 0, invariants, ElementFrame.LINE_LEVEL);

        assertThat(recorder.displayList().inkBoundsSs())
            .as("the fall still renders when a barline follows")
            .isNotNull();
    }


}
