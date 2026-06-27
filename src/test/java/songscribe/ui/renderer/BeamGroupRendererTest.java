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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementLocation;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.layout.LayoutResult;
import songscribe.layout.StaffExtents;
import songscribe.smufl.Engraving;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;

class BeamGroupRendererTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;
    private static final BeamGroupRenderer RENDERER = BeamGroupRenderer.getInstance();

    // ======================================================================
    // getBeamLevel tests
    // ======================================================================

    @Test
    void testGetBeamLevelAllQuavers_returns0() {
        var line = lineWith(ElementType.QUAVER, ElementType.QUAVER);

        assertThat(RENDERER.getBeamLevel(line, 0, 1)).isEqualTo(0);
    }

    @Test
    void testGetBeamLevelSemiquaverPresent_returns1() {
        // Mix of quaver + semiquaver — max level should be 1
        var line = lineWith(ElementType.QUAVER, ElementType.SEMIQUAVER);

        assertThat(RENDERER.getBeamLevel(line, 0, 1)).isEqualTo(1);
    }

    @Test
    void testGetBeamLevelDemiSemiquaverPresent_returns2() {
        // A 32nd note (DEMI_SEMIQUAVER) raises max level to 2
        var line = lineWith(ElementType.QUAVER, ElementType.DEMI_SEMIQUAVER);

        assertThat(RENDERER.getBeamLevel(line, 0, 1)).isEqualTo(2);
    }

    @Test
    void testGetBeamLevelNonBeamableType_returns0() {
        // Crotchet (quarter note) is not beamable; level stays at 0
        var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);

        assertThat(RENDERER.getBeamLevel(line, 0, 1)).isEqualTo(0);
    }

    // ======================================================================
    // isNoteTypeInLevel tests
    // ======================================================================

    @Test
    void testIsNoteTypeInLevel_quaverAtLevel0_returnsTrue() {
        var line = lineWith(ElementType.QUAVER);

        assertThat(RENDERER.isNoteTypeInLevel(line, 0, 0)).isTrue();
    }

    @Test
    void testIsNoteTypeInLevel_quaverAtLevel1_returnsFalse() {
        // A quaver (8th note) qualifies only at beam level 0, not higher
        var line = lineWith(ElementType.QUAVER);

        assertThat(RENDERER.isNoteTypeInLevel(line, 0, 1)).isFalse();
    }

    @Test
    void testIsNoteTypeInLevel_semiquaverAtLevel0_returnsTrue() {
        // SEMIQUAVER qualifies at both level 0 and level 1
        var line = lineWith(ElementType.SEMIQUAVER);

        assertThat(RENDERER.isNoteTypeInLevel(line, 0, 0)).isTrue();
    }

    @Test
    void testIsNoteTypeInLevel_semiquaverAtLevel1_returnsTrue() {
        var line = lineWith(ElementType.SEMIQUAVER);

        assertThat(RENDERER.isNoteTypeInLevel(line, 0, 1)).isTrue();
    }

    @Test
    void testIsNoteTypeInLevel_semiquaverAtLevel2_returnsFalse() {
        // SEMIQUAVER does not qualify at level 2 (only DEMI_SEMIQUAVER does)
        var line = lineWith(ElementType.SEMIQUAVER);

        assertThat(RENDERER.isNoteTypeInLevel(line, 0, 2)).isFalse();
    }

    @Test
    void testIsNoteTypeInLevel_crotchetNotBeamable_returnsFalse() {
        var line = lineWith(ElementType.CROTCHET);

        assertThat(RENDERER.isNoteTypeInLevel(line, 0, 0)).isFalse();
    }

    @Test
    void testIsNoteTypeInLevel_graceNoteChecksSurroundingNeighbors_bothQuavers_returnsTrue() {
        // Grace note checks neighbours: quaver, grace, quaver — all at level 0
        var line = detachedLine();
        var note0 = ElementType.QUAVER.newInstance();
        var graceNote = ElementType.GRACE_QUAVER.newInstance();
        var note2 = ElementType.QUAVER.newInstance();
        line.addElement(note0);
        line.addElement(graceNote);
        line.addElement(note2);

        // Grace note (index 1) checks note0 and note2 at level 0
        assertThat(RENDERER.isNoteTypeInLevel(line, 1, 0)).isTrue();
    }

    @Test
    void testIsNoteTypeInLevel_graceNoteChecksNeighbors_onlyQuavers_falseAtLevel1() {
        // Quaver neighbors don't qualify at level 1, so grace note returns false
        var line = detachedLine();
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.QUAVER.newInstance());

        assertThat(RENDERER.isNoteTypeInLevel(line, 1, 1)).isFalse();
    }

    @Test
    void testIsNoteTypeInLevel_multipleAdjacentGraceNotes_skipsToOuterNeighbors() {
        // Two adjacent grace notes: outer quaver neighbors qualify at level 0.
        // Line: [quaver(0), grace(1), grace(2), quaver(3)]
        // Target index=2: begin walks back past grace(1) to quaver(0), end goes to quaver(3).
        var line = detachedLine();
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.QUAVER.newInstance());

        assertThat(RENDERER.isNoteTypeInLevel(line, 2, 0)).isTrue();
    }

    @Test
    void testIsNoteTypeInLevel_graceNoteWithNonGraceNeighborBefore_usesDirectNeighbors() {
        // Line: [quaver(0), quaver(1), grace(2), quaver(3)]
        // Target index=2: begin=1 is a quaver (not grace) so the backward while exits immediately;
        // forward while also exits immediately; outer neighbors are quavers → level 0 = true.
        var line = detachedLine();
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.QUAVER.newInstance());

        assertThat(RENDERER.isNoteTypeInLevel(line, 2, 0)).isTrue();
    }

    @Test
    void testIsNoteTypeInLevel_graceNoteWithMultipleGracesAfter_skipsToOuterRight() {
        // Line: [quaver(0), grace(1), grace(2), quaver(3)], target index=1
        // Forward scan: end=2 is grace → end advances to 3 (quaver).
        // Both outer neighbors are quavers → level 0 = true.
        var line = detachedLine();
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.QUAVER.newInstance());

        assertThat(RENDERER.isNoteTypeInLevel(line, 1, 0)).isTrue();
    }

    @Test
    void testIsNoteTypeInLevel_graceNoteAtStart_noLeftNeighbor_returnsFalse() {
        // Line: [grace(0), quaver(1)], target=0: begin=-1 → begin < 0 → false.
        var line = detachedLine();
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.QUAVER.newInstance());

        assertThat(RENDERER.isNoteTypeInLevel(line, 0, 0)).isFalse();
    }

    @Test
    void testIsNoteTypeInLevel_graceNoteAtEnd_noRightNeighbor_returnsFalse() {
        // Line: [quaver(0), grace(1)], target=1: end=2 >= elementCount(2) → false.
        var line = detachedLine();
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());

        assertThat(RENDERER.isNoteTypeInLevel(line, 1, 0)).isFalse();
    }

    @Test
    void testIsNoteTypeInLevel_graceNoteRightNeighborNotBeamable_returnsFalse() {
        // Line: [quaver(0), grace(1), crotchet(2)], target=1.
        // Left neighbor qualifies; right neighbor is crotchet (non-beamable) → false.
        var line = detachedLine();
        line.addElement(ElementType.QUAVER.newInstance());
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());

        assertThat(RENDERER.isNoteTypeInLevel(line, 1, 0)).isFalse();
    }

    // ======================================================================
    // stemTipYSsOffset tests
    // ======================================================================

    @Test
    void testStemTipYSsOffset_withLayout_stemUp_returnsTopYSs() {
        var layout = new LayoutResult.StemLayout(-3.5, 0.5, 0.0, false);
        var element = ElementType.QUAVER.newInstance();

        var result = BeamGroupRenderer.stemTipYSsOffset(layout, true, element);

        assertThat(result).isCloseTo(-3.5, within(TOLERANCE));
    }

    @Test
    void testStemTipYSsOffset_withLayout_stemDown_returnsBottomYSs() {
        var layout = new LayoutResult.StemLayout(-3.5, 0.5, 0.0, false);
        var element = ElementType.QUAVER.newInstance();

        var result = BeamGroupRenderer.stemTipYSsOffset(layout, false, element);

        assertThat(result).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void testStemTipYSsOffset_nullLayout_stemUp_usesStaffPositionMinusStemLength() {
        // staffPos=4 → elementYSs = 4 * 0.5 = 2.0; upper tip = 2.0 - STEM_LENGTH_SS
        var element = ElementType.QUAVER.newInstance();
        element.setStaffPosition(4);
        var expectedYSs = StaffExtents.spToSs(4) - Engraving.STEM_LENGTH_SS;

        var result = BeamGroupRenderer.stemTipYSsOffset(null, true, element);

        assertThat(result).isCloseTo(expectedYSs, within(TOLERANCE));
    }

    @Test
    void testStemTipYSsOffset_nullLayout_stemDown_usesStaffPositionPlusStemLength() {
        var element = ElementType.QUAVER.newInstance();
        element.setStaffPosition(-2);
        var expectedYSs = StaffExtents.spToSs(-2) + Engraving.STEM_LENGTH_SS;

        var result = BeamGroupRenderer.stemTipYSsOffset(null, false, element);

        assertThat(result).isCloseTo(expectedYSs, within(TOLERANCE));
    }

    // ======================================================================
    // getBeamHighlightColor tests
    // ======================================================================

    /** Build a minimal LineInvariants in edit mode with the given line set as current. */
    private static LineInvariants editModeInvariants(Line line) {
        return RenderContextTestHelper.newContext(new Song())
            .setEditMode(true)
            .setCurrentLine(line)
            .build();
    }

    @Test
    void testGetBeamHighlightColor_nonEditMode_returnsNull() {
        var line = lineWith(ElementType.QUAVER, ElementType.QUAVER);
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setEditMode(false)
            .setCurrentLine(line)
            .build();

        assertThat(RENDERER.getBeamHighlightColor(invariants, 0, 1)).isNull();
    }

    @Test
    void testGetBeamHighlightColor_twoOrMoreBeamableRemaining_returnsNull() {
        // Neither note is selected or hovered: 2 beamable notes remain → no highlight
        var line = lineWith(ElementType.QUAVER, ElementType.QUAVER);
        var invariants = editModeInvariants(line);

        assertThat(RENDERER.getBeamHighlightColor(invariants, 0, 1)).isNull();
    }

    @Test
    void testGetBeamHighlightColor_oneSelected_oneBeamableRemaining_returnsSelectionColor() {
        // One note selected, one beamable note remaining (< 2) → selection color
        var line = lineWith(ElementType.QUAVER, ElementType.QUAVER);
        var builder = RenderContextTestHelper.newContext(new Song())
            .setEditMode(true)
            .setCurrentLine(line);
        RenderContextTestHelper.enableSelection(builder, 0);
        var invariants = builder.build();

        assertThat(RENDERER.getBeamHighlightColor(invariants, 0, 1))
            .isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testGetBeamHighlightColor_selectionTakesPriorityOverHover() {
        // Element 0 selected, element 1 hovered — selection must win over hover
        var line = lineWith(ElementType.QUAVER, ElementType.QUAVER);
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isElementSelected(0, 0)).thenReturn(true);
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setEditMode(true)
            .setCurrentLine(line)
            .setSelectionProvider(selectionProvider)
            .setSelectionColor(Color.BLUE)
            .build();

        // Element 1 is hovered: anyHovered=true. Element 0 is selected: anySelected=true.
        // remainingBeamableNotes=0 < 2 → selection wins → returns Color.BLUE, not preview color.
        try (var previewMock = mockStatic(PreviewElementManager.class)) {
            previewMock.when(PreviewElementManager::getHoveredElementLocation)
                .thenReturn(new ElementLocation(0, 1));

            var result = RENDERER.getBeamHighlightColor(invariants, 0, 1);

            assertThat(result).isEqualTo(Color.BLUE);
        }
    }

    @Test
    void testGetBeamHighlightColor_nonBeamableNoteInRange_notCounted() {
        // A crotchet in the range is not beamable: remainingBeamableNotes stays 0 (< 2).
        // No note is selected or hovered, so the result is null.
        var line = lineWith(ElementType.CROTCHET);
        var invariants = editModeInvariants(line);

        assertThat(RENDERER.getBeamHighlightColor(invariants, 0, 0)).isNull();
    }

    @Test
    void testGetBeamHighlightColor_hoveredNote_oneBeamableRemaining_returnsReplacedElementColor() {
        // One note hovered (not selected), one beamable remaining → replaced-element color
        var line = lineWith(ElementType.QUAVER, ElementType.QUAVER);
        var invariants = editModeInvariants(line);

        try (var previewMock = mockStatic(PreviewElementManager.class)) {
            // Element 0 is hovered; element 1 is beamable and not hovered → remainingBeamableNotes=1 < 2
            previewMock.when(PreviewElementManager::getHoveredElementLocation)
                .thenReturn(new ElementLocation(0, 0));

            var result = RENDERER.getBeamHighlightColor(invariants, 0, 1);

            assertThat(result).isEqualTo(LineInvariants.REPLACED_ELEMENT_COLOR);
        }
    }

    @Test
    void testGetBeamHighlightColor_noSelectionNoHover_noRemainingBeamable_returnsNull() {
        // Neither selected nor hovered, no beamable notes remaining — returns null
        // (One beamable note that is itself not selected/hovered still counts as remaining)
        // But with only one total note and one beamable, remaining = 1 < 2 but
        // no anySelected and no anyHovered → returns null
        var line = lineWith(ElementType.QUAVER);
        var invariants = editModeInvariants(line);

        // No selection, no hover: remainingBeamableNotes = 1 (< 2), anySelected = false,
        // anyHovered = false → method returns null
        assertThat(RENDERER.getBeamHighlightColor(invariants, 0, 0)).isNull();
    }
}
