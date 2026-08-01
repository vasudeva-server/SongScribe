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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
import songscribe.engraving.LineThickness;

class ElementTypeTest extends UnitTest {

    @Test
    void testAllVisualTypesHaveNonZeroBounds() {
        for (var type : ElementType.values()) {
            // IO aliases have the same bounds as their canonical type
            assertThat(type.getFullElementWidthSs())
                .as("widthSs of %s", type)
                .isGreaterThan(0);
            assertThat(type.getElementHeightSs(StaffElement.Direction.UP))
                .as("heightUpSs of %s", type)
                .isGreaterThan(0);
            assertThat(type.getElementHeightSs(StaffElement.Direction.DOWN))
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
            assertThat(type.getFullElementCenterXSs())
                .as("CenterX of %s", type)
                .isCloseTo(type.getFullElementWidthSs() / 2, within(1e-9));
        }
    }

    // T7: getFlagGlyph(direction) returns the correct glyph for each flagged type × direction
    @Test
    void testGetFlagGlyphReturnsCorrectGlyphForFlaggedTypes() {
        assertThat(ElementType.QUAVER.getFlagGlyph(StaffElement.Direction.UP)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
        assertThat(ElementType.QUAVER.getFlagGlyph(StaffElement.Direction.DOWN)).isEqualTo(SMuFLGlyph.FLAG_8TH_DOWN);

        assertThat(ElementType.SEMIQUAVER.getFlagGlyph(StaffElement.Direction.UP)).isEqualTo(SMuFLGlyph.FLAG_16TH_UP);
        assertThat(ElementType.SEMIQUAVER.getFlagGlyph(StaffElement.Direction.DOWN)).isEqualTo(SMuFLGlyph.FLAG_16TH_DOWN);

        assertThat(ElementType.DEMI_SEMIQUAVER.getFlagGlyph(StaffElement.Direction.UP)).isEqualTo(SMuFLGlyph.FLAG_32ND_UP);
        assertThat(ElementType.DEMI_SEMIQUAVER.getFlagGlyph(StaffElement.Direction.DOWN)).isEqualTo(SMuFLGlyph.FLAG_32ND_DOWN);
    }

    // T7 continued: GRACE_QUAVER always returns regular 8th flag (renderers use grace font for sizing)
    @Test
    void testGetFlagGlyphReturnsEighthFlagForGraceQuaver() {
        assertThat(ElementType.GRACE_QUAVER.getFlagGlyph(StaffElement.Direction.UP)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
        assertThat(ElementType.GRACE_QUAVER.getFlagGlyph(StaffElement.Direction.DOWN)).isEqualTo(SMuFLGlyph.FLAG_8TH_UP);
    }

    // T8: getFlagGlyph(direction) returns null for non-flagged types
    @Test
    void testGetFlagGlyphReturnsNullForNonFlaggedTypes() {
        assertThat(ElementType.CROTCHET.getFlagGlyph(StaffElement.Direction.UP)).isNull();
        assertThat(ElementType.MINIM.getFlagGlyph(StaffElement.Direction.UP)).isNull();
        assertThat(ElementType.SEMIBREVE.getFlagGlyph(StaffElement.Direction.UP)).isNull();
        assertThat(ElementType.CROTCHET_REST.getFlagGlyph(StaffElement.Direction.UP)).isNull();
        assertThat(ElementType.QUAVER_REST.getFlagGlyph(StaffElement.Direction.UP)).isNull();
        assertThat(ElementType.SEMIBREVE_REST.getFlagGlyph(StaffElement.Direction.UP)).isNull();
    }

    @Test
    void testEndingAnchorXOffsetPerBranch() {
        // Branch 1: REPEAT_LEFT — anchor on the thin barline that follows thick | sep | thin
        var expectedRepeatLeft = LineThickness.THICK_BARLINE_SS
            + LineThickness.BARLINE_SEPARATION_SS
            + LineThickness.THIN_BARLINE_SS / 2;
        assertThat(ElementType.REPEAT_LEFT.endingAnchorXOffsetSs())
            .isCloseTo(expectedRepeatLeft, within(1e-9));

        // Branch 2: generic barline/repeat — center of rightmost thin barline
        for (var type : new ElementType[]{
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE,
            ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT
        }) {
            var expected = type.getFullElementWidthSs() - LineThickness.THIN_BARLINE_SS / 2;
            assertThat(type.endingAnchorXOffsetSs())
                .as("endingAnchorXOffsetSs() of %s", type)
                .isCloseTo(expected, within(1e-9));
        }

        // Branch 3: all non-barline, non-repeat types — offset is zero
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE, ElementType.BREATH_MARK
        }) {
            assertThat(type.endingAnchorXOffsetSs())
                .as("endingAnchorXOffsetSs() of %s", type)
                .isZero();
        }
    }

    @Test
    void testGetSMuFLGlyphMapping() {
        // Each note type maps to the expected glyph
        assertThat(ElementType.SEMIBREVE.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.NOTEHEAD_WHOLE);
        assertThat(ElementType.MINIM.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.NOTEHEAD_HALF);
        assertThat(ElementType.CROTCHET.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
        assertThat(ElementType.QUAVER.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
        assertThat(ElementType.SEMIQUAVER.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
        assertThat(ElementType.DEMI_SEMIQUAVER.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
        assertThat(ElementType.GRACE_QUAVER.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.NOTEHEAD_BLACK);
        assertThat(ElementType.SEMIBREVE_REST.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.REST_WHOLE);
        assertThat(ElementType.MINIM_REST.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.REST_HALF);
        assertThat(ElementType.CROTCHET_REST.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.REST_QUARTER);
        assertThat(ElementType.QUAVER_REST.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.REST_8TH);
        assertThat(ElementType.SEMIQUAVER_REST.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.REST_16TH);
        assertThat(ElementType.DEMI_SEMIQUAVER_REST.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.REST_32ND);
        assertThat(ElementType.BREATH_MARK.getSMuFLGlyph()).isEqualTo(SMuFLGlyph.BREATH_MARK_COMMA);

        // Barline, repeat, and other non-glyph types must return null
        for (var type : new ElementType[]{
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SLIDE
        }) {
            assertThat(type.getSMuFLGlyph())
                .as("getSMuFLGlyph() of %s should be null", type)
                .isNull();
        }
    }

    @Test
    void testDrawStaveLongitudeIsTrueForAllExceptBreathMark() {
        // Only BREATH_MARK returns false; every other canonical type returns true
        assertThat(ElementType.BREATH_MARK.drawStaveLongitude()).isFalse();

        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.drawStaveLongitude())
                .as("drawStaveLongitude() of %s", type)
                .isTrue();
        }
    }

    @Test
    void testIsBarLineMembership() {
        // True set: single, double, final double
        for (var type : new ElementType[]{
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isBarLine()).as("%s.isBarLine()", type).isTrue();
        }

        // False set: everything else
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.BREATH_MARK
        }) {
            assertThat(type.isBarLine()).as("%s.isBarLine()", type).isFalse();
        }
    }

    @Test
    void testIsBeamableMembership() {
        // True set: quaver, semiquaver, demi-semiquaver
        for (var type : new ElementType[]{
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER
        }) {
            assertThat(type.isBeamable()).as("%s.isBeamable()", type).isTrue();
        }

        // False set: everything else (notes without flags, rests, grace notes, non-duration types)
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.BREATH_MARK,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isBeamable()).as("%s.isBeamable()", type).isFalse();
        }
    }

    @Test
    void testIsDurationAndIsNonContentElementAreNeverBothTrue() {
        // The ending-anchor set (isDuration) and the skipped-but-present set
        // (isNonContentElement) must stay disjoint: no type may both anchor an
        // ending and be skipped during ending validation.
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.BREATH_MARK,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isDuration() && type.isNonContentElement())
                .as("isDuration() AND isNonContentElement() for %s should never both be true", type)
                .isFalse();
        }
    }

    @Test
    void testIsGraceNoteMembership() {
        // True set: only GRACE_QUAVER
        assertThat(ElementType.GRACE_QUAVER.isGraceNote()).isTrue();

        // False set: everything else
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isGraceNote()).as("%s.isGraceNote()", type).isFalse();
        }
    }

    @Test
    void testIsNoteMembership() {
        // True set: all pitched notes and grace notes
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.GRACE_QUAVER
        }) {
            assertThat(type.isNote()).as("%s.isNote()", type).isTrue();
        }

        // False set: rests, non-duration types
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isNote()).as("%s.isNote()", type).isFalse();
        }
    }

    @Test
    void testIsNoteWithStemMembership() {
        // True set: all pitched notes and GRACE_QUAVER except SEMIBREVE (whole note has no stem)
        for (var type : new ElementType[]{
            ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.GRACE_QUAVER
        }) {
            assertThat(type.isNoteWithStem()).as("%s.isNoteWithStem()", type).isTrue();
        }

        // False set: SEMIBREVE, rests, non-duration types
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isNoteWithStem()).as("%s.isNoteWithStem()", type).isFalse();
        }
    }

    @Test
    void testIsPitchedNoteMembership() {
        // True set: SEMIBREVE through DEMI_SEMIQUAVER (the 6 pitched note types)
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER
        }) {
            assertThat(type.isPitchedNote()).as("%s.isPitchedNote()", type).isTrue();
        }

        // False set: rests, grace notes, non-duration types
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isPitchedNote()).as("%s.isPitchedNote()", type).isFalse();
        }
    }

    @Test
    void testIsNonContentElementMembership() {
        // True set: grace notes, glissando, breath mark
        assertThat(ElementType.GRACE_QUAVER.isNonContentElement()).isTrue();
        assertThat(ElementType.SLIDE.isNonContentElement()).isTrue();
        assertThat(ElementType.BREATH_MARK.isNonContentElement()).isTrue();

        // False set: all other types
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isNonContentElement()).as("%s.isNonContentElement()", type).isFalse();
        }
    }

    @Test
    void testIsNonDurationMembership() {
        // True set: barlines, repeats, breath mark
        for (var type : new ElementType[]{
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.BREATH_MARK
        }) {
            assertThat(type.isNonDuration()).as("%s.isNonDuration()", type).isTrue();
        }

        // False set: notes, rests, grace notes; SLIDE is explicitly NOT a non-duration
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER,
            ElementType.SLIDE
        }) {
            assertThat(type.isNonDuration()).as("%s.isNonDuration()", type).isFalse();
        }
    }

    @Test
    void testIsNonDurationExcludesGlissando() {
        // SLIDE is neither a duration nor a non-duration — it belongs to neither group
        assertThat(ElementType.SLIDE.isNonDuration()).isFalse();
        assertThat(ElementType.SLIDE.isDuration()).isFalse();
    }

    @Test
    void testIsRepeatMembership() {
        // True set: repeat left, repeat right, repeat left/right
        for (var type : new ElementType[]{
            ElementType.REPEAT_LEFT, ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT
        }) {
            assertThat(type.isRepeat()).as("%s.isRepeat()", type).isTrue();
        }

        // False set: everything else
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.isRepeat()).as("%s.isRepeat()", type).isFalse();
        }
    }

    @Test
    void testIsReplaceableByTerminalMembership() {
        // True set: single barline, double barline, repeat right, repeat left/right
        for (var type : new ElementType[]{
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE,
            ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT
        }) {
            assertThat(type.isReplaceableByTerminal()).as("%s.isReplaceableByTerminal()", type).isTrue();
        }

        // False set: everything else; REPEAT_LEFT is explicitly excluded
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.FINAL_DOUBLE_BARLINE,
            ElementType.REPEAT_LEFT
        }) {
            assertThat(type.isReplaceableByTerminal()).as("%s.isReplaceableByTerminal()", type).isFalse();
        }
    }

    @Test
    void testIsReplaceableByTerminalExcludesRepeatLeft() {
        // REPEAT_LEFT is explicitly excluded — a left-facing repeat does not terminate a line
        assertThat(ElementType.REPEAT_LEFT.isReplaceableByTerminal()).isFalse();
    }

    @Test
    void testIsTerminalMembership() {
        // True set: barlines and REPEAT_LEFT
        for (var type : new ElementType[]{
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE,
            ElementType.REPEAT_LEFT
        }) {
            assertThat(type.isTerminal()).as("%s.isTerminal()", type).isTrue();
        }

        // False set: everything else; REPEAT_RIGHT and REPEAT_LEFT_RIGHT are NOT terminals
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT_RIGHT
        }) {
            assertThat(type.isTerminal()).as("%s.isTerminal()", type).isFalse();
        }
    }

    @Test
    void testIsValidTerminalMembership() {
        // True set: only FINAL_DOUBLE_BARLINE and REPEAT_RIGHT
        assertThat(ElementType.FINAL_DOUBLE_BARLINE.isValidTerminal()).isTrue();
        assertThat(ElementType.REPEAT_RIGHT.isValidTerminal()).isTrue();

        // False set: everything else
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_LEFT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE
        }) {
            assertThat(type.isValidTerminal()).as("%s.isValidTerminal()", type).isFalse();
        }
    }

    @Test
    void testSnapToEndMembership() {
        // True set: repeat right, single barline, double barline, final double barline
        for (var type : new ElementType[]{
            ElementType.REPEAT_RIGHT,
            ElementType.SINGLE_BARLINE, ElementType.DOUBLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE
        }) {
            assertThat(type.snapToEnd()).as("%s.snapToEnd()", type).isTrue();
        }

        // False set: everything else
        for (var type : new ElementType[]{
            ElementType.SEMIBREVE, ElementType.MINIM, ElementType.CROTCHET,
            ElementType.QUAVER, ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST,
            ElementType.GRACE_QUAVER, ElementType.SLIDE, ElementType.BREATH_MARK,
            ElementType.REPEAT_LEFT, ElementType.REPEAT_LEFT_RIGHT
        }) {
            assertThat(type.snapToEnd()).as("%s.snapToEnd()", type).isFalse();
        }
    }

    @Test
    void testTerminalFlushRightXIsLineWidthMinusBaseWidth() {
        // terminalFlushRightXSs = lineWidthSs − terminalType.baseWidthSs
        // Expected values are derived from the barline thickness constants, not from the
        // production method, so a bug in the formula cannot make the test pass falsely.
        final var lineWidthSs = 40.0;

        // FINAL_DOUBLE_BARLINE: baseWidthSs = thin + thick + sep
        var finalBarBaseWidth = LineThickness.THIN_BARLINE_SS
            + LineThickness.THICK_BARLINE_SS
            + LineThickness.BARLINE_SEPARATION_SS;
        assertThat(ElementType.terminalFlushRightXSs(lineWidthSs, ElementType.FINAL_DOUBLE_BARLINE))
            .isCloseTo(lineWidthSs - finalBarBaseWidth, within(1e-9));

        // REPEAT_RIGHT: base width read independently via the width accessor
        var repeatRightBaseWidth = ElementType.REPEAT_RIGHT.getElementWidthSs();
        assertThat(ElementType.terminalFlushRightXSs(lineWidthSs, ElementType.REPEAT_RIGHT))
            .isCloseTo(lineWidthSs - repeatRightBaseWidth, within(1e-9));
    }

    @Test
    void testIsDurationExcludesGraceNotes() {
        // Grace notes are not durations — they are tied to the following note
        assertThat(ElementType.GRACE_QUAVER.isDuration()).isFalse();
    }

    @Test
    void testIsDurationExcludesNonDurations() {
        assertThat(ElementType.SINGLE_BARLINE.isDuration()).isFalse();
        assertThat(ElementType.SLIDE.isDuration()).isFalse();
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
        assertThat(ElementType.SLIDE.toNote()).isEqualTo(ElementType.SLIDE);
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
        assertThat(ElementType.SLIDE.toRest()).isEqualTo(ElementType.SLIDE);
        assertThat(ElementType.SINGLE_BARLINE.toRest()).isEqualTo(ElementType.SINGLE_BARLINE);
        assertThat(ElementType.DOUBLE_BARLINE.toRest()).isEqualTo(ElementType.DOUBLE_BARLINE);
        assertThat(ElementType.REPEAT_LEFT.toRest()).isEqualTo(ElementType.REPEAT_LEFT);
        assertThat(ElementType.BREATH_MARK.toRest()).isEqualTo(ElementType.BREATH_MARK);
    }

    // --- Element bounds tests (Step 10) ---

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ElementContentDelegationTests {

        @Test
        void testElementGetContentCenterXReturnsPx() {
            var element = ElementType.QUAVER.newInstance();
            var expectedPx = ScaleContext.ssToPx(ElementType.QUAVER.getFullElementCenterXSs());
            assertThat(element.getContentCenterX()).isCloseTo(expectedPx, within(1e-9));
        }

        @Test
        void testElementGetContentHeightReturnsPx() {
            var element = ElementType.CROTCHET.newInstance();
            var expectedPx = ScaleContext.ssToPx(ElementType.CROTCHET.getElementHeightSs(element.getDirection()));
            assertThat(element.getContentHeightPx()).isCloseTo(expectedPx, within(1e-9));
        }

        @Test
        void testElementGetContentWidthReturnsPx() {
            var element = ElementType.CROTCHET.newInstance();
            var expectedPx = ScaleContext.ssToPx(ElementType.CROTCHET.getFullElementWidthSs());
            assertThat(element.getContentWidthPx()).isCloseTo(expectedPx, within(1e-9));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ElementHeightTests {

        private static final double OFFSET_TOLERANCE_SS = 1e-9;

        // ------------------------------------------------------------------
        // Notehead top/bottom offsets (refs #694). Everything that reserves vertical space for a
        // stemless element derives its expected value from these two accessors, so without a test
        // that pins them to the font metadata a wrong sign or a wrong field here would go
        // unnoticed everywhere.
        // ------------------------------------------------------------------

        @Test
        void testRestOffsetsMatchItsGlyphBoundingBox() {
            // The rest glyph's own box, measured from the note center: top above, bottom below.
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.REST_QUARTER);

            assertThat(ElementType.CROTCHET_REST.getNoteheadTopOffsetSs())
                .as("the top offset is the glyph's own top, above the center so negative")
                .isCloseTo(bbox.top(), within(OFFSET_TOLERANCE_SS))
                .isNegative();
            assertThat(ElementType.CROTCHET_REST.getNoteheadBottomOffsetSs())
                .as("the bottom offset is the top plus the glyph's height, below the center")
                .isCloseTo(bbox.top() + bbox.height(), within(OFFSET_TOLERANCE_SS))
                .isPositive();
        }

        @Test
        void testNoteheadOffsetsSpanOneStaffSpaceAroundTheCenter() {
            // A notehead is one staff space tall and sits centered on its staff position, so it
            // reaches half a staff space each way. Stated as literals because that symmetry is the
            // property under test, not something to re-read from the glyph.
            assertThat(ElementType.CROTCHET.getNoteheadTopOffsetSs())
                .isCloseTo(-0.5, within(OFFSET_TOLERANCE_SS));
            assertThat(ElementType.CROTCHET.getNoteheadBottomOffsetSs())
                .isCloseTo(0.5, within(OFFSET_TOLERANCE_SS));
        }

        @Test
        void testBarlineOffsetsSpanTheFullStaffHeight() {
            // A barline's bounds come from a different branch of the type initializer than notes
            // and rests, so it needs its own check: it spans the whole staff, centered.
            var halfStaffHeightSs = Staff.STAFF_HEIGHT_SS / 2;

            assertThat(ElementType.SINGLE_BARLINE.getNoteheadTopOffsetSs())
                .isCloseTo(-halfStaffHeightSs, within(OFFSET_TOLERANCE_SS));
            assertThat(ElementType.SINGLE_BARLINE.getNoteheadBottomOffsetSs())
                .isCloseTo(halfStaffHeightSs, within(OFFSET_TOLERANCE_SS));
        }

        @Test
        void testBarlineOffsetsReachFurtherThanANotehead() {
            // The practical consequence: a barline's collision band is far taller than a notehead's.
            assertThat(ElementType.SINGLE_BARLINE.getNoteheadTopOffsetSs())
                .isLessThan(ElementType.CROTCHET.getNoteheadTopOffsetSs());
            assertThat(ElementType.SINGLE_BARLINE.getNoteheadBottomOffsetSs())
                .isGreaterThan(ElementType.CROTCHET.getNoteheadBottomOffsetSs());
        }

        @Test
        void testBarlineHeightEqualsStaffHeight() {
            var staffHeight = Staff.STAFF_HEIGHT_SS;

            assertThat(ElementType.SINGLE_BARLINE.getElementHeightSs(StaffElement.Direction.UP))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.DOUBLE_BARLINE.getElementHeightSs(StaffElement.Direction.UP))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.FINAL_DOUBLE_BARLINE.getElementHeightSs(StaffElement.Direction.UP))
                .isCloseTo(staffHeight, within(1e-9));
        }

        @Test
        void testRepeatHeightEqualsStaffHeight() {
            var staffHeight = Staff.STAFF_HEIGHT_SS;

            assertThat(ElementType.REPEAT_LEFT.getElementHeightSs(StaffElement.Direction.UP))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.REPEAT_RIGHT.getElementHeightSs(StaffElement.Direction.DOWN))
                .isCloseTo(staffHeight, within(1e-9));
            assertThat(ElementType.REPEAT_LEFT_RIGHT.getElementHeightSs(StaffElement.Direction.UP))
                .isCloseTo(staffHeight, within(1e-9));
        }

        @Test
        void testSemibreveHeightIsSameBothDirections() {
            assertThat(ElementType.SEMIBREVE.getElementHeightSs(StaffElement.Direction.UP))
                .isEqualTo(ElementType.SEMIBREVE.getElementHeightSs(StaffElement.Direction.DOWN));
        }

        @Test
        void testStemmedNoteHeightIsDirectionDependent() {
            // NOTEHEAD_BLACK is symmetric so heightUp == heightDown for CROTCHET — but the
            // top Y offset IS direction-dependent: stem-up top is the stem tip, stem-down top
            // is the notehead top.  Asserting != here catches an up/down swap that would still
            // pass a ">0" or equality check on the height alone.
            var type = ElementType.CROTCHET;
            assertThat(type.getElementHeightSs(StaffElement.Direction.UP)).isGreaterThan(0);
            assertThat(type.getElementHeightSs(StaffElement.Direction.DOWN)).isGreaterThan(0);
            assertThat(type.getTopYOffsetSs(StaffElement.Direction.UP))
                .isNotEqualTo(type.getTopYOffsetSs(StaffElement.Direction.DOWN));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ElementWidthTests {

        private static final double WIDTH_TOLERANCE_SS = 1e-9;

        @Test
        void testBreathMarkWidth() {
            assertThat(ElementType.BREATH_MARK.getFullElementWidthSs()).isGreaterThan(0);
        }

        @Test
        void testDoubleBarlineWidth() {
            var expected = 2 * LineThickness.THIN_BARLINE_SS + LineThickness.BARLINE_SEPARATION_SS;
            assertThat(ElementType.DOUBLE_BARLINE.getFullElementWidthSs())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        void testFinalDoubleBarlineWidth() {
            var expected = LineThickness.THIN_BARLINE_SS + LineThickness.THICK_BARLINE_SS
                + LineThickness.BARLINE_SEPARATION_SS;
            assertThat(ElementType.FINAL_DOUBLE_BARLINE.getFullElementWidthSs())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        void testGraceNoteWidthIsScaled() {
            // Grace note width should be smaller than the equivalent regular note
            assertThat(ElementType.GRACE_QUAVER.getFullElementWidthSs())
                .isLessThan(ElementType.QUAVER.getFullElementWidthSs());
        }

        @Test
        void testRepeatLeftRightWidth() {
            // Repeat left and right have the same width
            assertThat(ElementType.REPEAT_LEFT.getFullElementWidthSs())
                .isEqualTo(ElementType.REPEAT_RIGHT.getFullElementWidthSs());

            // Repeat left/right shares the thick bar, so it equals 2 * single - thick
            var expected = 2 * ElementType.REPEAT_LEFT.getFullElementWidthSs() - LineThickness.THICK_BARLINE_SS;
            assertThat(ElementType.REPEAT_LEFT_RIGHT.getFullElementWidthSs())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        void testRestWidthsFromBBox() {
            for (var type : new ElementType[]{
                ElementType.SEMIBREVE_REST, ElementType.MINIM_REST, ElementType.CROTCHET_REST,
                ElementType.QUAVER_REST, ElementType.SEMIQUAVER_REST, ElementType.DEMI_SEMIQUAVER_REST
            }) {
                assertThat(type.getFullElementWidthSs())
                    .as("Width of %s", type)
                    .isGreaterThan(0);
            }
        }

        @Test
        void testSemibreveWidthFromBBox() {
            // Semibreve has no stem or flag — width comes from bbox
            assertThat(ElementType.SEMIBREVE.getFullElementWidthSs()).isGreaterThan(0);
            // Semibreve notehead width equals element width (no flag)
            assertThat(ElementType.SEMIBREVE.getElementWidthSs())
                .isEqualTo(ElementType.SEMIBREVE.getFullElementWidthSs());
        }

        // ------------------------------------------------------------------
        // Per-type head width (refs #694). Expected values are read straight from the font
        // metadata, so these fail if a type is ever measured with the wrong glyph again.
        // ------------------------------------------------------------------

        @Test
        void testWholeNoteWidthIsTheWholeNoteheadGlyphRightEdge() {
            assertThat(ElementType.SEMIBREVE.getElementWidthSs())
                .isCloseTo(SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_WHOLE).right(), within(WIDTH_TOLERANCE_SS));
        }

        @Test
        void testWholeNoteIsWiderThanTheBlackAndHalfNoteheads() {
            // The whole notehead is the widest of the three, which is why measuring it with the
            // black-notehead constant left it short — the bug behind the lyric-centering drift.
            assertThat(ElementType.SEMIBREVE.getElementWidthSs())
                .as("a whole note is wider than a quarter note's head")
                .isGreaterThan(ElementType.CROTCHET.getElementWidthSs())
                .as("and wider than a half note's head too, not just the black one")
                .isGreaterThan(ElementType.MINIM.getElementWidthSs());
        }

        @Test
        void testHalfAndQuarterNoteheadsShareTheBlackNoteheadWidth() {
            // noteheadHalf and noteheadBlack are the same width, which is what
            // SMuFLConstants.NOTE_HEAD_WIDTH_SS holds — the constant is right for these two types
            // and wrong only for the whole note.
            assertThat(ElementType.MINIM.getElementWidthSs())
                .isCloseTo(SMuFLConstants.NOTE_HEAD_WIDTH_SS, within(WIDTH_TOLERANCE_SS))
                .isCloseTo(ElementType.CROTCHET.getElementWidthSs(), within(WIDTH_TOLERANCE_SS));
        }

        @Test
        void testCrotchetWidthIsTheBlackNoteheadGlyphRightEdge() {
            assertThat(ElementType.CROTCHET.getElementWidthSs())
                .isCloseTo(SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).right(), within(WIDTH_TOLERANCE_SS));
        }

        @Test
        void testGraceNoteWidthIsTheBlackNoteheadScaledDown() {
            // A grace note draws the regular black notehead at GRACE_NOTE_SCALE, so its measured
            // width must be scaled too — the unscaled bbox overstates the drawn ink.
            var blackRightSs = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).right();

            assertThat(ElementType.GRACE_QUAVER.getElementWidthSs())
                .isCloseTo(ElementType.GRACE_NOTE_SCALE * blackRightSs, within(WIDTH_TOLERANCE_SS))
                .isLessThan(blackRightSs);
        }

        @Test
        void testRestWidthIsTheGlyphRightEdgeNotItsBoundingBoxWidth() {
            // The quarter rest's bounding box starts a hair right of the origin, so its right edge
            // and its raw box width differ. The right edge is the one layout wants: it is the
            // distance from the element origin to the last of the ink.
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.REST_QUARTER);

            assertThat(bbox.left())
                .as("precondition: this glyph must have a non-zero left bearing to tell the two apart")
                .isNotEqualTo(0.0);
            assertThat(ElementType.CROTCHET_REST.getElementWidthSs())
                .isCloseTo(bbox.right(), within(WIDTH_TOLERANCE_SS));
        }

        @Test
        void testSingleBarlineWidth() {
            assertThat(ElementType.SINGLE_BARLINE.getFullElementWidthSs())
                .isCloseTo(LineThickness.THIN_BARLINE_SS, within(1e-9));
        }

        @Test
        void testStemmedNoteNoteheadWidthExcludesFlag() {
            // Notehead width should be the same for all stemmed notes (same notehead glyph)
            assertThat(ElementType.QUAVER.getElementWidthSs())
                .isEqualTo(ElementType.CROTCHET.getElementWidthSs());
            assertThat(ElementType.SEMIQUAVER.getElementWidthSs())
                .isEqualTo(ElementType.CROTCHET.getElementWidthSs());
        }

        @Test
        void testStemmedNoteWidthIncludesFlagExtent() {
            // Flagged notes should be wider than unflagged due to flag extent
            assertThat(ElementType.QUAVER.getFullElementWidthSs())
                .isGreaterThan(ElementType.CROTCHET.getFullElementWidthSs());

            // 16th flag extends further than 8th
            assertThat(ElementType.SEMIQUAVER.getFullElementWidthSs())
                .isGreaterThan(ElementType.QUAVER.getFullElementWidthSs());
        }
    }

}
