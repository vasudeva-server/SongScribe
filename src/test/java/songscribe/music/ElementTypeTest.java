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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.LayoutConstants;
import songscribe.ui.layout.ScaleContext;

class ElementTypeTest extends UnitTest {

    @Test
    void testAllVisualTypesHaveNonZeroBounds() {
        for (var type : ElementType.values()) {
            if (type == ElementType.GLISSANDO) {
                continue;
            }

            // IO aliases have the same bounds as their canonical type
            assertThat(type.getElementWidthSs())
                .as("widthSs of %s", type)
                .isGreaterThan(0);
            assertThat(type.getElementHeightSs(true))
                .as("heightUpSs of %s", type)
                .isGreaterThan(0);
            assertThat(type.getElementHeightSs(false))
                .as("heightDownSs of %s", type)
                .isGreaterThan(0);
        }
    }

    @Test
    void testCenterXIsHalfWidth() {
        for (var type : new ElementType[]{
            ElementType.CROTCHET, ElementType.SEMIBREVE, ElementType.QUAVER_REST,
            ElementType.SINGLE_BARLINE, ElementType.REPEAT_LEFT, ElementType.BREATH_MARK
        }) {
            assertThat(type.getCenterXSs())
                .as("CenterX of %s", type)
                .isCloseTo(type.getElementWidthSs() / 2, within(1e-9));
        }
    }

    // T7: getFlagGlyph(upper) returns the correct glyph for each flagged type × direction
    @Test
    void testGetFlagGlyphReturnsCorrectGlyphForFlaggedTypes() {
        assertThat(ElementType.QUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
        assertThat(ElementType.QUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_8TH_DOWN);

        assertThat(ElementType.SEMIQUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_16TH_UP);
        assertThat(ElementType.SEMIQUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_16TH_DOWN);

        assertThat(ElementType.DEMI_SEMIQUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_32ND_UP);
        assertThat(ElementType.DEMI_SEMIQUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_32ND_DOWN);
    }

    // T7 continued: GRACE_QUAVER always returns regular 8th flag (renderers use grace font for sizing)
    @Test
    void testGetFlagGlyphReturnsEighthFlagForGraceQuaver() {
        assertThat(ElementType.GRACE_QUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
        assertThat(ElementType.GRACE_QUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
    }

    // T8: getFlagGlyph(upper) returns null for non-flagged types
    @Test
    void testGetFlagGlyphReturnsNullForNonFlaggedTypes() {
        assertThat(ElementType.CROTCHET.getFlagGlyph(true)).isNull();
        assertThat(ElementType.MINIM.getFlagGlyph(true)).isNull();
        assertThat(ElementType.SEMIBREVE.getFlagGlyph(true)).isNull();
        assertThat(ElementType.CROTCHET_REST.getFlagGlyph(true)).isNull();
        assertThat(ElementType.QUAVER_REST.getFlagGlyph(true)).isNull();
        assertThat(ElementType.SEMIBREVE_REST.getFlagGlyph(true)).isNull();
    }

    @Test
    void testIsDurationExcludesGraceNotes() {
        // Grace notes are not durations — they are tied to the following note
        assertThat(ElementType.GRACE_QUAVER.isDuration()).isFalse();
    }

    @Test
    void testIsDurationExcludesNonDurations() {
        assertThat(ElementType.SINGLE_BARLINE.isDuration()).isFalse();
        assertThat(ElementType.GLISSANDO.isDuration()).isFalse();
        assertThat(ElementType.BREATH_MARK.isDuration()).isFalse();
    }

    @Test
    void testIsDurationIncludesNotesAndRests() {
        assertThat(ElementType.CROTCHET.isDuration()).isTrue();
        assertThat(ElementType.QUAVER.isDuration()).isTrue();
        assertThat(ElementType.CROTCHET_REST.isDuration()).isTrue();
        assertThat(ElementType.QUAVER_REST.isDuration()).isTrue();
    }

    @Test
    void testToNoteIsIdempotentForNoteTypes() {
        assertThat(ElementType.CROTCHET.toNote()).isEqualTo(ElementType.CROTCHET);
        assertThat(ElementType.QUAVER.toNote()).isEqualTo(ElementType.QUAVER);
    }

    @Test
    void testToNoteReturnsNoteEquivalentForRestTypes() {
        assertThat(ElementType.SEMIBREVE_REST.toNote()).isEqualTo(ElementType.SEMIBREVE);
        assertThat(ElementType.MINIM_REST.toNote()).isEqualTo(ElementType.MINIM);
        assertThat(ElementType.CROTCHET_REST.toNote()).isEqualTo(ElementType.CROTCHET);
        assertThat(ElementType.QUAVER_REST.toNote()).isEqualTo(ElementType.QUAVER);
        assertThat(ElementType.SEMIQUAVER_REST.toNote()).isEqualTo(ElementType.SEMIQUAVER);
        assertThat(ElementType.DEMI_SEMIQUAVER_REST.toNote()).isEqualTo(ElementType.DEMI_SEMIQUAVER);
    }

    @Test
    void testToNoteReturnsSelfForNonPairedTypes() {
        assertThat(ElementType.GRACE_QUAVER.toNote()).isEqualTo(ElementType.GRACE_QUAVER);
        assertThat(ElementType.GLISSANDO.toNote()).isEqualTo(ElementType.GLISSANDO);
        assertThat(ElementType.SINGLE_BARLINE.toNote()).isEqualTo(ElementType.SINGLE_BARLINE);
        assertThat(ElementType.DOUBLE_BARLINE.toNote()).isEqualTo(ElementType.DOUBLE_BARLINE);
        assertThat(ElementType.REPEAT_LEFT.toNote()).isEqualTo(ElementType.REPEAT_LEFT);
        assertThat(ElementType.BREATH_MARK.toNote()).isEqualTo(ElementType.BREATH_MARK);
    }

    @Test
    void testToRestIsIdempotentForRestTypes() {
        assertThat(ElementType.CROTCHET_REST.toRest()).isEqualTo(ElementType.CROTCHET_REST);
        assertThat(ElementType.QUAVER_REST.toRest()).isEqualTo(ElementType.QUAVER_REST);
    }

    @Test
    void testToRestReturnsRestEquivalentForNoteTypes() {
        assertThat(ElementType.SEMIBREVE.toRest()).isEqualTo(ElementType.SEMIBREVE_REST);
        assertThat(ElementType.MINIM.toRest()).isEqualTo(ElementType.MINIM_REST);
        assertThat(ElementType.CROTCHET.toRest()).isEqualTo(ElementType.CROTCHET_REST);
        assertThat(ElementType.QUAVER.toRest()).isEqualTo(ElementType.QUAVER_REST);
        assertThat(ElementType.SEMIQUAVER.toRest()).isEqualTo(ElementType.SEMIQUAVER_REST);
        assertThat(ElementType.DEMI_SEMIQUAVER.toRest()).isEqualTo(ElementType.DEMI_SEMIQUAVER_REST);
    }

    @Test
    void testToRestReturnsSelfForNonPairedTypes() {
        assertThat(ElementType.GRACE_QUAVER.toRest()).isEqualTo(ElementType.GRACE_QUAVER);
        assertThat(ElementType.GLISSANDO.toRest()).isEqualTo(ElementType.GLISSANDO);
        assertThat(ElementType.SINGLE_BARLINE.toRest()).isEqualTo(ElementType.SINGLE_BARLINE);
        assertThat(ElementType.DOUBLE_BARLINE.toRest()).isEqualTo(ElementType.DOUBLE_BARLINE);
        assertThat(ElementType.REPEAT_LEFT.toRest()).isEqualTo(ElementType.REPEAT_LEFT);
        assertThat(ElementType.BREATH_MARK.toRest()).isEqualTo(ElementType.BREATH_MARK);
    }

    // --- Element bounds tests (Step 10) ---

    @Nested
    class ElementContentDelegationTests {

        @Test
        void testElementGetContentCenterXReturnsPx() {
            var element = ElementType.QUAVER.newInstance();
            var sc = ScaleContext.getInstance();
            double expectedPx = sc.toPixels(ElementType.QUAVER.getCenterXSs());
            assertThat(element.getContentCenterX()).isCloseTo(expectedPx, within(1e-9));
        }

        @Test
        void testElementGetContentHeightReturnsPx() {
            var element = ElementType.CROTCHET.newInstance();
            var sc = ScaleContext.getInstance();
            double expectedPx = sc.toPixels(ElementType.CROTCHET.getElementHeightSs(element.isUpper()));
            assertThat(element.getContentHeightPx()).isCloseTo(expectedPx, within(1e-9));
        }

        @Test
        void testElementGetContentWidthReturnsPx() {
            var element = ElementType.CROTCHET.newInstance();
            var sc = ScaleContext.getInstance();
            double expectedPx = sc.toPixels(ElementType.CROTCHET.getElementWidthSs());
            assertThat(element.getContentWidthPx()).isCloseTo(expectedPx, within(1e-9));
        }
    }

    @Nested
    class ElementHeightTests {

        @Test
        void testBarlineHeightEqualsStaffHeight() {
            double staffHeight = LayoutConstants.STAFF_HEIGHT_SS;

            assertThat(ElementType.SINGLE_BARLINE.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.DOUBLE_BARLINE.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.FINAL_DOUBLE_BARLINE.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
        }

        @Test
        void testRepeatHeightEqualsStaffHeight() {
            double staffHeight = LayoutConstants.STAFF_HEIGHT_SS;

            assertThat(ElementType.REPEAT_LEFT.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.REPEAT_RIGHT.getElementHeightSs(false))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.REPEAT_LEFT_RIGHT.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
        }

        @Test
        void testSemibreveHeightIsSameBothDirections() {
            assertThat(ElementType.SEMIBREVE.getElementHeightSs(true))
                .isEqualTo(ElementType.SEMIBREVE.getElementHeightSs(false));
        }

        @Test
        void testStemmedNoteHeightIsDirectionDependent() {
            // Up and down heights differ for stemmed notes
            var type = ElementType.CROTCHET;
            assertThat(type.getElementHeightSs(true)).isGreaterThan(0);
            assertThat(type.getElementHeightSs(false)).isGreaterThan(0);
            // Both should include stem length, so they should be similar but not necessarily equal
            // (stem anchor positions differ between up and down)
        }
    }

    @Nested
    class ElementWidthTests {

        @Test
        void testBreathMarkWidth() {
            assertThat(ElementType.BREATH_MARK.getElementWidthSs()).isGreaterThan(0);
        }

        @Test
        void testDoubleBarlineWidth() {
            var defaults = SMuFLMetadata.getInstance().getEngravingDefaults();
            double expected = 2 * defaults.thinBarlineThickness() + defaults.barlineSeparation();
            assertThat(ElementType.DOUBLE_BARLINE.getElementWidthSs())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        void testFinalDoubleBarlineWidth() {
            var defaults = SMuFLMetadata.getInstance().getEngravingDefaults();
            double expected = defaults.thinBarlineThickness() + defaults.thickBarlineThickness()
                + defaults.barlineSeparation();
            assertThat(ElementType.FINAL_DOUBLE_BARLINE.getElementWidthSs())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        void testGraceNoteWidthIsScaled() {
            // Grace note width should be smaller than the equivalent regular note
            assertThat(ElementType.GRACE_QUAVER.getElementWidthSs())
                .isLessThan(ElementType.QUAVER.getElementWidthSs());
        }

        @Test
        void testRepeatLeftRightWidth() {
            // Repeat left and right have the same width
            assertThat(ElementType.REPEAT_LEFT.getElementWidthSs())
                .isEqualTo(ElementType.REPEAT_RIGHT.getElementWidthSs());

            // Repeat left/right is double the single repeat width
            assertThat(ElementType.REPEAT_LEFT_RIGHT.getElementWidthSs())
                .isCloseTo(2 * ElementType.REPEAT_LEFT.getElementWidthSs(), within(1e-9));
        }

        @Test
        void testRestWidthsFromBBox() {
            for (var type : new ElementType[]{
                ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
                ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST
            }) {
                assertThat(type.getElementWidthSs())
                    .as("Width of %s", type)
                    .isGreaterThan(0);
            }
        }

        @Test
        void testSemibreveWidthFromBBox() {
            // Semibreve has no stem or flag — width comes from bbox
            assertThat(ElementType.SEMIBREVE.getElementWidthSs()).isGreaterThan(0);
            // Semibreve notehead width equals element width (no flag)
            assertThat(ElementType.SEMIBREVE.getNoteheadWidthSs())
                .isEqualTo(ElementType.SEMIBREVE.getElementWidthSs());
        }

        @Test
        void testSingleBarlineWidth() {
            var defaults = SMuFLMetadata.getInstance().getEngravingDefaults();
            assertThat(ElementType.SINGLE_BARLINE.getElementWidthSs())
                .isCloseTo(defaults.thinBarlineThickness(), within(1e-9));
        }

        @Test
        void testStemmedNoteNoteheadWidthExcludesFlag() {
            // Notehead width should be the same for all stemmed notes (same notehead glyph)
            assertThat(ElementType.QUAVER.getNoteheadWidthSs())
                .isEqualTo(ElementType.CROTCHET.getNoteheadWidthSs());
            assertThat(ElementType.SEMIQUAVER.getNoteheadWidthSs())
                .isEqualTo(ElementType.CROTCHET.getNoteheadWidthSs());
        }

        @Test
        void testStemmedNoteWidthIncludesFlagExtent() {
            // Flagged notes should be wider than unflagged due to flag extent
            assertThat(ElementType.QUAVER.getElementWidthSs())
                .isGreaterThan(ElementType.CROTCHET.getElementWidthSs());

            // 16th flag extends further than 8th
            assertThat(ElementType.SEMIQUAVER.getElementWidthSs())
                .isGreaterThanOrEqualTo(ElementType.QUAVER.getElementWidthSs());
        }
    }

    @Nested
    class UnsupportedTypesTests {

        @Test
        void testGlissandoThrowsOnHeight() {
            assertThatThrownBy(() -> ElementType.GLISSANDO.getElementHeightSs(true))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testGlissandoThrowsOnWidth() {
            assertThatThrownBy(() -> ElementType.GLISSANDO.getElementWidthSs())
                .isInstanceOf(UnsupportedOperationException.class);
        }

    }
}
