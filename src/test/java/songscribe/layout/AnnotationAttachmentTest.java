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

package songscribe.layout;

import java.awt.font.TextLayout;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.util.GraphicUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AnnotationAttachmentTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Copy {

        @Test
        void testCopyReturnsDistinctInstanceWithNewOwnerAndPreservesAnnotation() {
            var originalOwner = ElementType.CROTCHET.newInstance();
            var newOwner = ElementType.QUAVER.newInstance();
            var annotation = new Annotation("dolce");
            var original = new AnnotationAttachment(originalOwner, annotation);

            var copy = original.copy(newOwner);

            assertThat(copy).isNotSameAs(original);
            assertThat(copy).isExactlyInstanceOf(AnnotationAttachment.class);
            assertThat(copy.getOwnerElement()).isSameAs(newOwner);
            assertThat(((AnnotationAttachment) copy).getAnnotation()).isSameAs(annotation);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ComputeContentHeightSs {

        @Test
        void testUsesProvidedFont() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.QUAVER.newInstance();
            var attachment = new AnnotationAttachment("test");
            note.addAttachment(attachment);
            song.withoutMutationTracking(() -> line.addElement(note));

            var font = DocumentFonts.defaultFonts().getAnnotationFont();
            var expected = ScaleContext.textHeightSs(font).value();

            assertThat(attachment.computeContentHeightSs(font)).isCloseTo(expected, within(EPSILON));
        }

    }

    // -------------------------------------------------------------------------
    // Row 10 — computeContentWidthSs(font) returns the text advance in staff-space units
    //
    // The expected value is derived independently via TextLayout.getAdvance(),
    // which is the same underlying measurement that ScaleContext.textWidthSs
    // delegates to — so the oracle is the raw AWT primitive, not the production
    // wrapper being tested.
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ComputeContentWidthSs {

        // A short but non-trivial string whose advance is distinguishable from
        // both zero and degenerate single-char values.
        private static final String ANNOTATION_TEXT = "dolce";

        @Test
        void testReturnsTextAdvanceInStaffSpaces() {
            var font = DocumentFonts.defaultFonts().getAnnotationFont();
            var attachment = new AnnotationAttachment(ANNOTATION_TEXT);

            // Compute expected width from the raw AWT text advance converted to
            // staff-space units, bypassing ScaleContext.textWidthSs entirely.
            double rawAdvancePx = new TextLayout(ANNOTATION_TEXT, font, GraphicUtils.SCREEN_FRC).getAdvance();
            double expectedWidthSs = ScaleContext.pxToSs(rawAdvancePx);

            assertThat(attachment.computeContentWidthSs(font)).isCloseTo(expectedWidthSs, within(EPSILON));
        }

    }

    // -------------------------------------------------------------------------
    // Row 12 — getContentWidthSs/HeightSs/Px/Px all throw UnsupportedOperationException
    //
    // These accessors are guarded because width and height are font-dependent;
    // callers must use computeContentWidthSs/computeContentHeightSs instead.
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetContentDimensions {

        @Test
        void testGetContentHeightPxThrowsUnsupportedOperationException() {
            var attachment = new AnnotationAttachment("test");
            assertThatThrownBy(attachment::getContentHeightPx)
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testGetContentHeightSsThrowsUnsupportedOperationException() {
            var attachment = new AnnotationAttachment("test");
            assertThatThrownBy(attachment::getContentHeightSs)
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testGetContentWidthPxThrowsUnsupportedOperationException() {
            var attachment = new AnnotationAttachment("test");
            assertThatThrownBy(attachment::getContentWidthPx)
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testGetContentWidthSsThrowsUnsupportedOperationException() {
            var attachment = new AnnotationAttachment("test");
            assertThatThrownBy(attachment::getContentWidthSs)
                .isInstanceOf(UnsupportedOperationException.class);
        }

    }

}
