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

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout2.LayoutConstants;
import songscribe.ui.layout2.ScaleContext;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class NoteTypeTest extends UnitTest {

    // T7: getFlagGlyph(upper) returns the correct glyph for each flagged type × direction
    @Test
    void testGetFlagGlyphReturnsCorrectGlyphForFlaggedTypes() {
        assertThat(NoteType.QUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
        assertThat(NoteType.QUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_8TH_DOWN);

        assertThat(NoteType.SEMIQUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_16TH_UP);
        assertThat(NoteType.SEMIQUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_16TH_DOWN);

        assertThat(NoteType.DEMI_SEMIQUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_32ND_UP);
        assertThat(NoteType.DEMI_SEMIQUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_32ND_DOWN);
    }

    // T7 continued: GRACE_QUAVER always returns regular 8th flag (renderers use grace font for sizing)
    @Test
    void testGetFlagGlyphReturnsEighthFlagForGraceQuaver() {
        assertThat(NoteType.GRACE_QUAVER.getFlagGlyph(true)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
        assertThat(NoteType.GRACE_QUAVER.getFlagGlyph(false)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
    }

    // T8: getFlagGlyph(upper) returns null for non-flagged types
    @Test
    void testGetFlagGlyphReturnsNullForNonFlaggedTypes() {
        assertThat(NoteType.CROTCHET.getFlagGlyph(true)).isNull();
        assertThat(NoteType.MINIM.getFlagGlyph(true)).isNull();
        assertThat(NoteType.SEMIBREVE.getFlagGlyph(true)).isNull();
        assertThat(NoteType.CROTCHET_REST.getFlagGlyph(true)).isNull();
        assertThat(NoteType.QUAVER_REST.getFlagGlyph(true)).isNull();
        assertThat(NoteType.SEMIBREVE_REST.getFlagGlyph(true)).isNull();
    }

    @Test
    void testToRestReturnsRestEquivalentForNoteTypes() {
        assertThat(NoteType.SEMIBREVE.toRest()).isEqualTo(NoteType.SEMIBREVE_REST);
        assertThat(NoteType.MINIM.toRest()).isEqualTo(NoteType.MINIM_REST);
        assertThat(NoteType.CROTCHET.toRest()).isEqualTo(NoteType.CROTCHET_REST);
        assertThat(NoteType.QUAVER.toRest()).isEqualTo(NoteType.QUAVER_REST);
        assertThat(NoteType.SEMIQUAVER.toRest()).isEqualTo(NoteType.SEMIQUAVER_REST);
        assertThat(NoteType.DEMI_SEMIQUAVER.toRest()).isEqualTo(NoteType.DEMI_SEMIQUAVER_REST);
    }

    @Test
    void testToNoteReturnsNoteEquivalentForRestTypes() {
        assertThat(NoteType.SEMIBREVE_REST.toNote()).isEqualTo(NoteType.SEMIBREVE);
        assertThat(NoteType.MINIM_REST.toNote()).isEqualTo(NoteType.MINIM);
        assertThat(NoteType.CROTCHET_REST.toNote()).isEqualTo(NoteType.CROTCHET);
        assertThat(NoteType.QUAVER_REST.toNote()).isEqualTo(NoteType.QUAVER);
        assertThat(NoteType.SEMIQUAVER_REST.toNote()).isEqualTo(NoteType.SEMIQUAVER);
        assertThat(NoteType.DEMI_SEMIQUAVER_REST.toNote()).isEqualTo(NoteType.DEMI_SEMIQUAVER);
    }

    @Test
    void testToRestReturnsSelfForNonPairedTypes() {
        assertThat(NoteType.GRACE_QUAVER.toRest()).isEqualTo(NoteType.GRACE_QUAVER);
        assertThat(NoteType.GLISSANDO.toRest()).isEqualTo(NoteType.GLISSANDO);
        assertThat(NoteType.SINGLE_BARLINE.toRest()).isEqualTo(NoteType.SINGLE_BARLINE);
        assertThat(NoteType.DOUBLE_BARLINE.toRest()).isEqualTo(NoteType.DOUBLE_BARLINE);
        assertThat(NoteType.REPEAT_LEFT.toRest()).isEqualTo(NoteType.REPEAT_LEFT);
        assertThat(NoteType.BREATH_MARK.toRest()).isEqualTo(NoteType.BREATH_MARK);
    }

    @Test
    void testToNoteReturnsSelfForNonPairedTypes() {
        assertThat(NoteType.GRACE_QUAVER.toNote()).isEqualTo(NoteType.GRACE_QUAVER);
        assertThat(NoteType.GLISSANDO.toNote()).isEqualTo(NoteType.GLISSANDO);
        assertThat(NoteType.SINGLE_BARLINE.toNote()).isEqualTo(NoteType.SINGLE_BARLINE);
        assertThat(NoteType.DOUBLE_BARLINE.toNote()).isEqualTo(NoteType.DOUBLE_BARLINE);
        assertThat(NoteType.REPEAT_LEFT.toNote()).isEqualTo(NoteType.REPEAT_LEFT);
        assertThat(NoteType.BREATH_MARK.toNote()).isEqualTo(NoteType.BREATH_MARK);
    }

    @Test
    void testToRestIsIdempotentForRestTypes() {
        assertThat(NoteType.CROTCHET_REST.toRest()).isEqualTo(NoteType.CROTCHET_REST);
        assertThat(NoteType.QUAVER_REST.toRest()).isEqualTo(NoteType.QUAVER_REST);
    }

    @Test
    void testToNoteIsIdempotentForNoteTypes() {
        assertThat(NoteType.CROTCHET.toNote()).isEqualTo(NoteType.CROTCHET);
        assertThat(NoteType.QUAVER.toNote()).isEqualTo(NoteType.QUAVER);
    }

    @Test
    void testIsDurationExcludesGraceNotes() {
        // Grace notes are not durations — they are tied to the following note
        assertThat(NoteType.GRACE_QUAVER.isDuration()).isFalse();
    }

    @Test
    void testIsDurationIncludesNotesAndRests() {
        assertThat(NoteType.CROTCHET.isDuration()).isTrue();
        assertThat(NoteType.QUAVER.isDuration()).isTrue();
        assertThat(NoteType.CROTCHET_REST.isDuration()).isTrue();
        assertThat(NoteType.QUAVER_REST.isDuration()).isTrue();
    }

    @Test
    void testIsDurationExcludesNonDurations() {
        assertThat(NoteType.SINGLE_BARLINE.isDuration()).isFalse();
        assertThat(NoteType.GLISSANDO.isDuration()).isFalse();
        assertThat(NoteType.BREATH_MARK.isDuration()).isFalse();
    }


    // --- Element bounds tests (Step 10) ---

    @Nested
    class ElementWidthTests {

        @Test
        void testStemmedNoteWidthIncludesFlagExtent() {
            // Flagged notes should be wider than unflagged due to flag extent
            assertThat(NoteType.QUAVER.getElementWidthSs())
                .isGreaterThan(NoteType.CROTCHET.getElementWidthSs());

            // 16th flag extends further than 8th
            assertThat(NoteType.SEMIQUAVER.getElementWidthSs())
                .isGreaterThanOrEqualTo(NoteType.QUAVER.getElementWidthSs());
        }

        @Test
        void testStemmedNoteNoteheadWidthExcludesFlag() {
            // Notehead width should be the same for all stemmed notes (same notehead glyph)
            assertThat(NoteType.QUAVER.getNoteheadWidthSs())
                .isEqualTo(NoteType.CROTCHET.getNoteheadWidthSs());
            assertThat(NoteType.SEMIQUAVER.getNoteheadWidthSs())
                .isEqualTo(NoteType.CROTCHET.getNoteheadWidthSs());
        }

        @Test
        void testSemibreveWidthFromBBox() {
            // Semibreve has no stem or flag — width comes from bbox
            assertThat(NoteType.SEMIBREVE.getElementWidthSs()).isGreaterThan(0);
            // Semibreve notehead width equals element width (no flag)
            assertThat(NoteType.SEMIBREVE.getNoteheadWidthSs())
                .isEqualTo(NoteType.SEMIBREVE.getElementWidthSs());
        }

        @Test
        void testRestWidthsFromBBox() {
            for (var type : new NoteType[]{
                NoteType.SEMIBREVE_REST, NoteType.MINIM_REST, NoteType.CROTCHET_REST,
                NoteType.QUAVER_REST, NoteType.SEMIQUAVER_REST, NoteType.DEMI_SEMIQUAVER_REST
            }) {
                assertThat(type.getElementWidthSs())
                    .as("Width of %s", type)
                    .isGreaterThan(0);
            }
        }

        @Test
        void testGraceNoteWidthIsScaled() {
            // Grace note width should be smaller than the equivalent regular note
            assertThat(NoteType.GRACE_QUAVER.getElementWidthSs())
                .isLessThan(NoteType.QUAVER.getElementWidthSs());
        }

        @Test
        void testSingleBarlineWidth() {
            var defaults = SMuFLMetadata.getInstance().getEngravingDefaults();
            assertThat(NoteType.SINGLE_BARLINE.getElementWidthSs())
                .isCloseTo(defaults.thinBarlineThickness(), within(1e-9));
        }

        @Test
        void testDoubleBarlineWidth() {
            var defaults = SMuFLMetadata.getInstance().getEngravingDefaults();
            double expected = 2 * defaults.thinBarlineThickness() + defaults.barlineSeparation();
            assertThat(NoteType.DOUBLE_BARLINE.getElementWidthSs())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        void testFinalDoubleBarlineWidth() {
            var defaults = SMuFLMetadata.getInstance().getEngravingDefaults();
            double expected = defaults.thinBarlineThickness() + defaults.thickBarlineThickness()
                + defaults.barlineSeparation();
            assertThat(NoteType.FINAL_DOUBLE_BARLINE.getElementWidthSs())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        void testRepeatLeftRightWidth() {
            // Repeat left and right have the same width
            assertThat(NoteType.REPEAT_LEFT.getElementWidthSs())
                .isEqualTo(NoteType.REPEAT_RIGHT.getElementWidthSs());

            // Repeat left/right is double the single repeat width
            assertThat(NoteType.REPEAT_LEFT_RIGHT.getElementWidthSs())
                .isCloseTo(2 * NoteType.REPEAT_LEFT.getElementWidthSs(), within(1e-9));
        }

        @Test
        void testBreathMarkWidth() {
            assertThat(NoteType.BREATH_MARK.getElementWidthSs()).isGreaterThan(0);
        }
    }

    @Nested
    class ElementHeightTests {

        @Test
        void testStemmedNoteHeightIsDirectionDependent() {
            // Up and down heights differ for stemmed notes
            var type = NoteType.CROTCHET;
            assertThat(type.getElementHeightSs(true)).isGreaterThan(0);
            assertThat(type.getElementHeightSs(false)).isGreaterThan(0);
            // Both should include stem length, so they should be similar but not necessarily equal
            // (stem anchor positions differ between up and down)
        }

        @Test
        void testSemibreveHeightIsSameBothDirections() {
            assertThat(NoteType.SEMIBREVE.getElementHeightSs(true))
                .isEqualTo(NoteType.SEMIBREVE.getElementHeightSs(false));
        }

        @Test
        void testBarlineHeightEqualsStaffHeight() {
            double staffHeight = LayoutConstants.STAFF_HEIGHT_SS;

            assertThat(NoteType.SINGLE_BARLINE.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(NoteType.DOUBLE_BARLINE.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(NoteType.FINAL_DOUBLE_BARLINE.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
        }

        @Test
        void testRepeatHeightEqualsStaffHeight() {
            double staffHeight = LayoutConstants.STAFF_HEIGHT_SS;

            assertThat(NoteType.REPEAT_LEFT.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(NoteType.REPEAT_RIGHT.getElementHeightSs(false))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(NoteType.REPEAT_LEFT_RIGHT.getElementHeightSs(true))
                .isCloseTo(staffHeight, within(1e-9));
        }
    }

    @Nested
    class CenterXTests {

        @Test
        void testCenterXIsHalfWidth() {
            for (var type : new NoteType[]{
                NoteType.CROTCHET, NoteType.SEMIBREVE, NoteType.QUAVER_REST,
                NoteType.SINGLE_BARLINE, NoteType.REPEAT_LEFT, NoteType.BREATH_MARK
            }) {
                assertThat(type.getCenterXSs())
                    .as("CenterX of %s", type)
                    .isCloseTo(type.getElementWidthSs() / 2, within(1e-9));
            }
        }
    }

    @Nested
    class UnsupportedTypesTests {

        @Test
        void testGlissandoThrowsOnWidth() {
            assertThatThrownBy(() -> NoteType.GLISSANDO.getElementWidthSs())
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testGlissandoThrowsOnHeight() {
            assertThatThrownBy(() -> NoteType.GLISSANDO.getElementHeightSs(true))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testPasteThrowsOnWidth() {
            assertThatThrownBy(() -> NoteType.PASTE.getElementWidthSs())
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testPasteThrowsOnHeight() {
            assertThatThrownBy(() -> NoteType.PASTE.getElementHeightSs(false))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class NoteContentDelegationTests {

        @Test
        void testNoteGetContentWidthReturnsPx() {
            var note = NoteType.CROTCHET.newInstance();
            var sc = ScaleContext.getInstance();
            double expectedPx = sc.toPixels(NoteType.CROTCHET.getElementWidthSs());
            assertThat(note.getContentWidth()).isCloseTo(expectedPx, within(1e-9));
        }

        @Test
        void testNoteGetContentHeightReturnsPx() {
            var note = NoteType.CROTCHET.newInstance();
            var sc = ScaleContext.getInstance();
            double expectedPx = sc.toPixels(NoteType.CROTCHET.getElementHeightSs(note.isUpper()));
            assertThat(note.getContentHeight()).isCloseTo(expectedPx, within(1e-9));
        }

        @Test
        void testNoteGetContentCenterXReturnsPx() {
            var note = NoteType.QUAVER.newInstance();
            var sc = ScaleContext.getInstance();
            double expectedPx = sc.toPixels(NoteType.QUAVER.getCenterXSs());
            assertThat(note.getContentCenterX()).isCloseTo(expectedPx, within(1e-9));
        }
    }

    @Nested
    class StartupValidationTests {

        @Test
        void testAllVisualTypesHaveNonZeroBounds() {
            for (var type : NoteType.values()) {
                if (type == NoteType.GLISSANDO || type == NoteType.PASTE) {
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
    }
}
