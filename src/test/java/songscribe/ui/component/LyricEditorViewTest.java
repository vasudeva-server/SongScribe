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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import java.awt.Shape;

import javax.swing.plaf.basic.BasicTextUI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LyricEditorViewTest extends LyricEditorTestSupport {

    // -----------------------------------------------------------------------
    // keepAllocationAtContentOrigin — pure static geometry logic
    // -----------------------------------------------------------------------

    @Nested
    class KeepAllocationAtContentOrigin {

        @Test
        void testNullInputReturnsAdjusted() {
            var adjusted = new Rectangle(5, 10, 100, 20);
            var result = LyricEditor.keepAllocationAtContentOrigin(null, adjusted);
            // When input is null, the method returns adjusted unchanged.
            assertThat(result).isSameAs(adjusted);
        }

        @Test
        void testNullAdjustedReturnsNull() {
            var input = new Rectangle(5, 10, 100, 20);
            var result = LyricEditor.keepAllocationAtContentOrigin(input, null);
            assertThat(result).isNull();
        }

        @Test
        void testAdjustedXLeftOfInputXIsClamped() {
            // adjusted.x < input.x → clamp adjusted.x to input.x and return adjusted.
            var input = new Rectangle(10, 5, 80, 20);
            var adjusted = new Rectangle(3, 5, 80, 20);  // x=3 < input.x=10
            var result = LyricEditor.keepAllocationAtContentOrigin(input, adjusted);
            assertThat(result).isNotNull();

            assertThat(result.getBounds().x).isEqualTo(10);
        }

        @Test
        void testAdjustedXEqualToInputXIsReturnedUnchanged() {
            var input = new Rectangle(10, 5, 80, 20);
            var adjusted = new Rectangle(10, 5, 80, 20);  // x == input.x
            var result = LyricEditor.keepAllocationAtContentOrigin(input, adjusted);
            // No clamping needed — the original adjusted shape is returned.
            assertThat(result).isSameAs(adjusted);
        }

        @Test
        void testAdjustedXRightOfInputXIsReturnedUnchanged() {
            var input = new Rectangle(10, 5, 80, 20);
            var adjusted = new Rectangle(15, 5, 75, 20);  // x=15 > input.x=10
            var result = LyricEditor.keepAllocationAtContentOrigin(input, adjusted);
            assertThat(result).isSameAs(adjusted);
        }
    }

    // -----------------------------------------------------------------------
    // LeadingSlackFieldView.adjustAllocation — coordinate-shift branch
    // -----------------------------------------------------------------------

    /**
     * Retrieves the {@link LyricEditor.LeadingSlackFieldView} installed by the
     * {@link LyricEditor} UI. The root view wraps the actual document view as its
     * first child, which is always the {@code LeadingSlackFieldView} for a
     * single-line text field using {@code LyricTextFieldUI}.
     */
    private LyricEditor.LeadingSlackFieldView slackViewOf(LyricEditor editor) {
        var rootView = ((BasicTextUI) editor.getUI()).getRootView(editor);
        var view = rootView.getView(0);
        assertThat(view).as("view is LeadingSlackFieldView").isInstanceOf(LyricEditor.LeadingSlackFieldView.class);
        return (LyricEditor.LeadingSlackFieldView) view;
    }

    @Nested
    class AdjustAllocationWithLeadingSlack {

        @Test
        void testNotPaintingWithLeadingSlackPassesThroughUntouched() {
            // When paintingWithLeadingSlack=false (the normal non-paint path),
            // adjustAllocation delegates to super.adjustAllocation without shifting x.
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            var view = slackViewOf(editor);

            assertThat(view.paintingWithLeadingSlack).isFalse();

            // With no paint context, super.adjustAllocation returns the input
            // allocation unchanged for an empty single-line field.
            var allocation = new Rectangle(20, 5, 100, 20);
            var result = view.adjustAllocation(allocation);

            // The x coordinate must NOT have been shifted by LEADING_PAINT_SLACK_PX.
            assertThat(result).as("adjustAllocation must not return null for a non-null allocation").isNotNull();

            assertThat(result.getBounds().x).isEqualTo(20);
        }

        @Test
        void testPaintingWithLeadingSlackShiftsXBySlackConstant() {
            // When paintingWithLeadingSlack=true, adjustAllocation shifts the allocation
            // x right by LEADING_PAINT_SLACK_PX and narrows width by the same amount
            // before delegating to super.adjustAllocation.
            var element = crotchet();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(element));

            var editor = new LyricEditor(score, line, element);
            var view = slackViewOf(editor);

            // Simulate being inside the paint-with-leading-slack pass.
            view.paintingWithLeadingSlack = true;
            var allocation = new Rectangle(20, 5, 100, 20);
            var result = view.adjustAllocation(allocation);
            view.paintingWithLeadingSlack = false;

            // The x passed to super.adjustAllocation was 20 + 1 = 21;
            // keepAllocationAtContentOrigin ensures the result x is >= 21.
            assertThat(result).as("adjustAllocation must not return null for a non-null allocation").isNotNull();

            assertThat(result.getBounds().x).isGreaterThanOrEqualTo(20 + LyricEditor.LEADING_PAINT_SLACK_PX);
        }
    }
}
