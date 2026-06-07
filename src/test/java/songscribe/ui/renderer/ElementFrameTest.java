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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class ElementFrameTest extends UnitTest {

    private static final double OVERRIDE_X_SS = 4.5;
    private static final double PREVIEW_SHIFT_SS = 2.0;

    // A finite override X is reported as active
    @Test
    void testHasOverrideElementXFalseForNaN() {
        var frame = new ElementFrame(0, Double.NaN, -1, 0);

        assertThat(frame.hasOverrideElementX()).isFalse();
    }

    @Test
    void testHasOverrideElementXTrueForFiniteValue() {
        var frame = new ElementFrame(0, OVERRIDE_X_SS, -1, 0);

        assertThat(frame.hasOverrideElementX()).isTrue();
        assertThat(frame.overrideElementXSs()).isEqualTo(OVERRIDE_X_SS);
    }

    // A negative from-index means no preview shift
    @Test
    void testHasPreviewShiftFalseForNegativeIndex() {
        var frame = new ElementFrame(0, Double.NaN, -1, 0);

        assertThat(frame.hasPreviewShift()).isFalse();
    }

    @Test
    void testHasPreviewShiftTrueForNonNegativeIndex() {
        var frame = new ElementFrame(0, Double.NaN, 0, PREVIEW_SHIFT_SS);

        assertThat(frame.hasPreviewShift()).isTrue();
        assertThat(frame.previewShiftFromIndex()).isEqualTo(0);
        assertThat(frame.previewShiftSs()).isEqualTo(PREVIEW_SHIFT_SS);
    }

    // The canonical line-level frame carries no element index, override, or shift
    @Test
    void testLineLevelHasNoElementOverrideOrShift() {
        assertThat(ElementFrame.LINE_LEVEL.currentElementIndex()).isEqualTo(-1);
        assertThat(ElementFrame.LINE_LEVEL.hasOverrideElementX()).isFalse();
        assertThat(ElementFrame.LINE_LEVEL.hasPreviewShift()).isFalse();
    }

    // lineLevelWithPreviewShift copies LINE_LEVEL's index/override and attaches shift args
    @Test
    void testLineLevelWithPreviewShiftHasNoElementIndexAndCarriesShift() {
        var fromIndex = 3;
        var frame = ElementFrame.lineLevelWithPreviewShift(fromIndex, PREVIEW_SHIFT_SS);

        assertThat(frame.currentElementIndex()).isEqualTo(ElementFrame.LINE_LEVEL.currentElementIndex());
        assertThat(frame.hasOverrideElementX()).isFalse();
        assertThat(frame.previewShiftFromIndex()).isEqualTo(fromIndex);
        assertThat(frame.previewShiftSs()).isEqualTo(PREVIEW_SHIFT_SS);
    }

    // withElement creates a per-element frame and inherits the preview shift from the parent
    @Test
    void testWithElementSetsIndexAndOverrideAndInheritsPreviewShift() {
        var fromIndex = 2;
        var parent = ElementFrame.lineLevelWithPreviewShift(fromIndex, PREVIEW_SHIFT_SS);
        var elementIndex = 5;
        var frame = parent.withElement(elementIndex, OVERRIDE_X_SS);

        assertThat(frame.currentElementIndex()).isEqualTo(elementIndex);
        assertThat(frame.overrideElementXSs()).isEqualTo(OVERRIDE_X_SS);
        assertThat(frame.hasOverrideElementX()).isTrue();
        assertThat(frame.previewShiftFromIndex()).isEqualTo(fromIndex);
        assertThat(frame.previewShiftSs()).isEqualTo(PREVIEW_SHIFT_SS);
    }
}
