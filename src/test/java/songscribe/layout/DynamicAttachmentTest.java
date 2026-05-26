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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

class DynamicAttachmentTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Copy {

        @Test
        void testCopyReturnsDistinctInstanceWithNewOwnerAndPreservesType() {
            var originalOwner = ElementType.CROTCHET.newInstance();
            var newOwner = ElementType.QUAVER.newInstance();
            var original = new DynamicAttachment(originalOwner, DynamicType.FORTE);

            var copy = original.copy(newOwner);

            assertThat(copy).isNotSameAs(original);
            assertThat(copy).isExactlyInstanceOf(DynamicAttachment.class);
            assertThat(copy.getOwnerElement()).isSameAs(newOwner);
            assertThat(((DynamicAttachment) copy).getType()).isEqualTo(DynamicType.FORTE);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DynamicTypeFields {

        @Test
        void testForteHasCorrectGlyph() {
            assertThat(DynamicType.FORTE.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_FORTE);
        }

        @Test
        void testForteHasCorrectVelocityFraction() {
            assertThat(DynamicType.FORTE.getVelocityFraction()).isEqualTo(0.78);
        }

        @Test
        void testFortePianoHasNullGlyph() {
            assertThat(DynamicType.FORTEPIANO.getGlyph()).isNull();
        }

        @Test
        void testFortissimoHasCorrectGlyph() {
            assertThat(DynamicType.FORTISSIMO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_FF);
        }

        @Test
        void testFortissimoHasCorrectVelocityFraction() {
            assertThat(DynamicType.FORTISSIMO.getVelocityFraction()).isEqualTo(1.00);
        }

        @Test
        void testMezzoForteHasCorrectGlyph() {
            assertThat(DynamicType.MEZZO_FORTE.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_MF);
        }

        @Test
        void testMezzoPianoHasCorrectGlyph() {
            assertThat(DynamicType.MEZZO_PIANO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_MP);
        }

        @Test
        void testPianissimoHasCorrectGlyph() {
            assertThat(DynamicType.PIANISSIMO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_PP);
        }

        @Test
        void testPianoHasCorrectGlyph() {
            assertThat(DynamicType.PIANO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_PIANO);
        }

        @Test
        void testSforzandoHasNullGlyph() {
            assertThat(DynamicType.SFORZANDO.getGlyph()).isNull();
        }

        @Test
        void testSymbolReturnsExpectedStringForAllTypes() {
            assertThat(DynamicType.PIANISSIMO.getSymbol()).isEqualTo("pp");
            assertThat(DynamicType.PIANO.getSymbol()).isEqualTo("p");
            assertThat(DynamicType.MEZZO_PIANO.getSymbol()).isEqualTo("mp");
            assertThat(DynamicType.MEZZO_FORTE.getSymbol()).isEqualTo("mf");
            assertThat(DynamicType.FORTE.getSymbol()).isEqualTo("f");
            assertThat(DynamicType.FORTISSIMO.getSymbol()).isEqualTo("ff");
            assertThat(DynamicType.SFORZANDO.getSymbol()).isEqualTo("sfz");
            assertThat(DynamicType.FORTEPIANO.getSymbol()).isEqualTo("fp");
        }

        @Test
        void testUiTypesHaveNonNullGlyphs() {
            assertThat(DynamicType.PIANISSIMO.getGlyph()).isNotNull();
            assertThat(DynamicType.PIANO.getGlyph()).isNotNull();
            assertThat(DynamicType.MEZZO_PIANO.getGlyph()).isNotNull();
            assertThat(DynamicType.MEZZO_FORTE.getGlyph()).isNotNull();
            assertThat(DynamicType.FORTE.getGlyph()).isNotNull();
            assertThat(DynamicType.FORTISSIMO.getGlyph()).isNotNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Dimensions {

        @Test
        void testContentHeightSsDelegatestoSmuflBBox() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.DYNAMIC_FORTE);
            assertThat(attachment.getContentHeightSs()).isEqualTo(bbox.height());
        }

        @Test
        void testContentWidthSsDelegatesToSmuflBBox() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.DYNAMIC_FORTE);
            assertThat(attachment.getContentWidthSs()).isEqualTo(bbox.width());
        }

        @Test
        void testNullGlyphTypeFallsBackToDefaultHeight() {
            var attachment = new DynamicAttachment(DynamicType.SFORZANDO);
            assertThat(attachment.getContentHeightSs()).isEqualTo(1.75);
        }

        @Test
        void testNullGlyphTypeFallsBackToDefaultWidth() {
            var attachment = new DynamicAttachment(DynamicType.SFORZANDO);
            assertThat(attachment.getContentWidthSs()).isEqualTo(2.5);
        }
    }
}
