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

package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.font.DocumentFonts;
import songscribe.model.ArticulationType;
import songscribe.model.ElementType;
import songscribe.model.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.stacking.VerticalStackingCalculator;

@SuppressWarnings("DataFlowIssue")
class ArticulationStackingTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 0.001;
    private static final double NOTE_X_SS = 10.0;

    /** Asserts value is not null and returns it non-null for NullAway. */
    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    /**
     * Creates a note with the given staff position, stem direction, and articulations.
     * Upper=true means stem up (note below middle), upper=false means stem down (note above middle).
     */
    private static StaffElement createNote(int staffPosition, boolean upper, ArticulationType... types) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosition);
        note.setUpper(upper);

        for (var type : types) {
            note.addArticulation(new Articulation(note, type));
        }

        return note;
    }

    /**
     * Creates an ElementColumn for a note at the standard test X position.
     */
    private static ElementColumn columnFor(StaffElement note) {
        var column = new ElementColumn(
            note, List.of(), 0.0, 1.0, -1.5, 2.0, null, 0.0, false
        );
        column.setXSs(NOTE_X_SS);
        return column;
    }

    /**
     * Runs vertical stacking on the given columns and returns the built LayoutResult.
     */
    private static LayoutResult stackColumns(List<ElementColumn> columns) {
        var builder = new LayoutResult.Builder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, detachedLine(), builder, LINE_WIDTH_SS, DocumentFonts.defaultsFromPrefs());
        return builder.build();
    }

    /**
     * Runs vertical stacking on a single column and returns the built LayoutResult.
     */
    private static LayoutResult stackSingleColumn(StaffElement note) {
        return stackColumns(List.of(columnFor(note)));
    }

    /**
     * Asserts that a combo accent+staccato note uses the precomposed glyph layout:
     * staccato holds the precomposed dimensions, accent has no separate layout entry.
     */
    private static void assertComboLayout(StaffElement note, LayoutResult result) {
        var staccatoArticulation = note.getArticulations().get(0);
        var accentArticulation = note.getArticulations().get(1);

        var staccatoLayout = require(
            result.getDecorationLayout(staccatoArticulation),
            "staccato DecorationLayout");
        assertThat(result.getDecorationLayout(accentArticulation))
            .describedAs("accent DecorationLayout should be null in combo mode")
            .isNull();

        var precomposedBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE);

        assertThat(staccatoLayout.widthSs())
            .isCloseTo(precomposedBBox.width(), within(TOLERANCE));
        assertThat(staccatoLayout.heightSs())
            .isCloseTo(precomposedBBox.height(), within(TOLERANCE));
        assertThat(staccatoLayout.ySs()).isLessThan(0.0);
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StemDownArticulationsAbove {

        @Test
        void testStaccatoPositionedAboveNoteExtents() {
            var note = createNote(-2, false, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            // For stem-down notes, articulation goes above (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
            assertThat(layout.xSs()).isCloseTo(NOTE_X_SS, within(TOLERANCE));
            assertThat(layout.heightSs()).isGreaterThan(0.0);
            assertThat(layout.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testAccentPositionedAboveNoteExtents() {
            var note = createNote(-2, false, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testAccentStacksAboveStaccatoWhenBothPresent() {
            var note = createNote(-2, false, ArticulationType.STACCATO, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            assertComboLayout(note, result);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StemUpArticulationsAbove {

        @Test
        void testStaccatoPositionedAboveStaff() {
            // Staff position 2 = within staff, stems up
            var note = createNote(2, true, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            // Even for stem-up notes, articulation goes above (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testAccentStacksAboveStaccatoWhenBothPresent() {
            var note = createNote(2, true, ArticulationType.STACCATO, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            assertComboLayout(note, result);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CollisionDetection {

        @Test
        void testArticulationsDoNotCollideWithStemTips() {
            // Stem-down note: stem tip is above the notehead (seeded into extents)
            var note = createNote(-2, false, ArticulationType.STACCATO);
            var column = columnFor(note);

            // Create builder with a StemLayout that extends above the note
            var builder = new LayoutResult.Builder();
            var stemTopSs = -3.5;
            builder.putStemLayout(note,
                new LayoutResult.StemLayout(stemTopSs, 0.0, 0.0, false));

            var calculator = new VerticalStackingCalculator();
            calculator.calculate(List.of(column), detachedLine(), builder, LINE_WIDTH_SS, DocumentFonts.defaultsFromPrefs());
            var result = builder.build();

            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            // Staccato top should be above the stem top (more negative)
            assertThat(layout.ySs()).isLessThan(stemTopSs);
        }

        @Test
        void testAboveStaffArticulationsReserveSpaceInExtents() {
            // Create two notes at the same X position with stem-down articulations
            var note1 = createNote(-2, false, ArticulationType.STACCATO);
            var note2 = createNote(-2, false, ArticulationType.STACCATO);

            var col1 = columnFor(note1);
            var col2 = columnFor(note2);
            // Same X position to force collision detection
            col2.setXSs(NOTE_X_SS);

            var result = stackColumns(List.of(col1, col2));

            var layout1 = require(
                result.getDecorationLayout(note1.getArticulations().getFirst()),
                "note1 staccato DecorationLayout");
            var layout2 = require(
                result.getDecorationLayout(note2.getArticulations().getFirst()),
                "note2 staccato DecorationLayout");

            // Second staccato should be above the first (higher layers stack)
            // because the first staccato reserves space via ySet
            assertThat(layout2.ySs()).isLessThan(layout1.ySs());
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PrecomposedGlyph {

        @Test
        void testSingleStaccatoStillUsesIndividualGlyph() {
            var note = createNote(-2, false, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var staccatoLayout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            var individualBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE);

            assertThat(staccatoLayout.widthSs())
                .isCloseTo(individualBBox.width(), within(TOLERANCE));
            assertThat(staccatoLayout.heightSs())
                .isCloseTo(individualBBox.height(), within(TOLERANCE));
        }

        @Test
        void testSingleAccentStillUsesIndividualGlyph() {
            var note = createNote(-2, false, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            var accentLayout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");

            var individualBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ARTIC_ACCENT_ABOVE);

            assertThat(accentLayout.widthSs())
                .isCloseTo(individualBBox.width(), within(TOLERANCE));
            assertThat(accentLayout.heightSs())
                .isCloseTo(individualBBox.height(), within(TOLERANCE));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DecorationLayoutProperties {

        @Test
        void testDecorationLayoutHasCorrectXPosition() {
            var note = createNote(0, false, ArticulationType.STACCATO);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");

            assertThat(layout.xSs()).isCloseTo(NOTE_X_SS, within(TOLERANCE));
        }

        @Test
        void testDecorationLayoutHasPositiveDimensions() {
            var note = createNote(0, false, ArticulationType.ACCENT);
            var result = stackSingleColumn(note);
            var layout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }
    }
}
