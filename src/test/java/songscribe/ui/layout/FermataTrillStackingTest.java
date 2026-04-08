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
import songscribe.music.ArticulationType;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.layout.stacking.VerticalStackingCalculator;

class FermataTrillStackingTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 0.001;
    private static final double NOTE_X_SS = 10.0;
    private static final double NOTE2_X_SS = 30.0;

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    private static StaffElement createNote(int staffPosition, boolean upper) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosition);
        note.setUpper(upper);
        return note;
    }

    private static ElementColumn columnFor(StaffElement note, double xSs) {
        var column = new ElementColumn(
            note, List.of(), 0.0, 1.0, -1.5, 2.0, null, 0.0, false
        );
        column.setXSs(xSs);
        return column;
    }

    private static ElementColumn columnFor(StaffElement note) {
        return columnFor(note, NOTE_X_SS);
    }

    private static LayoutResult stackColumns(List<ElementColumn> columns, Line line) {
        var builder = new LayoutResult.Builder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, line, builder, LINE_WIDTH_SS);
        return builder.build();
    }

    private static LayoutResult stackColumns(List<ElementColumn> columns) {
        return stackColumns(columns, new Line());
    }

    @Nested
    class FermataStacking {

        @Test
        void testFermataPositionedAboveNoteExtents() {
            var note = createNote(-2, false);
            note.setFermata(true);
            var result = stackColumns(List.of(columnFor(note)));

            var layout = require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout");

            // Fermata should be above the staff (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
            assertThat(layout.xSs()).isCloseTo(NOTE_X_SS, within(TOLERANCE));
        }

        @Test
        void testFermataHasPositiveDimensions() {
            var note = createNote(0, false);
            note.setFermata(true);
            var result = stackColumns(List.of(columnFor(note)));

            var layout = require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testFermataPositionedAboveArticulations() {
            var note = createNote(-2, false);
            note.setFermata(true);
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
            var result = stackColumns(List.of(columnFor(note)));

            var articulationLayout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "staccato DecorationLayout");
            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout");

            // Fermata top should be above (more negative than) articulation top
            assertThat(fermataLayout.ySs()).isLessThan(articulationLayout.ySs());
        }

        @Test
        void testFermataReservesSpaceInExtents() {
            // Two notes at same X, both with fermata
            var note1 = createNote(-2, false);
            note1.setFermata(true);
            var note2 = createNote(-2, false);
            note2.setFermata(true);

            var col1 = columnFor(note1);
            var col2 = columnFor(note2);
            col2.setXSs(NOTE_X_SS);

            var result = stackColumns(List.of(col1, col2));

            var layout1 = require(
                result.findAttachmentDecorationLayout(note1, FermataAttachment.class),
                "note1 fermata DecorationLayout");
            var layout2 = require(
                result.findAttachmentDecorationLayout(note2, FermataAttachment.class),
                "note2 fermata DecorationLayout");

            // Second fermata should be above the first (it stacks on top)
            assertThat(layout2.ySs()).isLessThan(layout1.ySs());
        }

        @Test
        void testFermataFromAttachmentProducesLayout() {
            var note = createNote(0, false);
            note.addAttachment(new FermataAttachment(note));
            // Also set legacy flag so LineRenderer dispatch works
            note.setFermata(true);

            var result = stackColumns(List.of(columnFor(note)));

            var layout = require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout from attachment");

            assertThat(layout.ySs()).isLessThan(0.0);
        }
    }

    @Nested
    class TrillStacking {

        @Test
        void testSingleNoteTrillPositionedAboveNote() {
            var note = createNote(-2, false);
            var line = new Line();
            line.addElement(note);
            var trill = new Trill(note);
            line.addRangeElement(trill);

            var result = stackColumns(List.of(columnFor(note)), line);

            var layout = require(
                result.getDecorationLayout(trill),
                "trill DecorationLayout");

            // Trill should be above the staff (negative Y)
            assertThat(layout.ySs()).isLessThan(0.0);
            assertThat(layout.xSs()).isCloseTo(NOTE_X_SS, within(TOLERANCE));
        }

        @Test
        void testTrillHasPositiveDimensions() {
            var note = createNote(0, false);
            var line = new Line();
            line.addElement(note);
            var trill = new Trill(note);
            line.addRangeElement(trill);

            var result = stackColumns(List.of(columnFor(note)), line);

            var layout = require(
                result.getDecorationLayout(trill),
                "trill DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testTrillPositionedAboveArticulation() {
            var note = createNote(-2, false);
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
            var line = new Line();
            line.addElement(note);
            var trill = new Trill(note);
            line.addRangeElement(trill);

            var result = stackColumns(List.of(columnFor(note)), line);

            var articulationLayout = require(
                result.getDecorationLayout(note.getArticulations().getFirst()),
                "accent DecorationLayout");
            var trillLayout = require(
                result.getDecorationLayout(trill),
                "trill DecorationLayout");

            // Trill should be above the articulation
            assertThat(trillLayout.ySs()).isLessThan(articulationLayout.ySs());
        }

        @Test
        void testMultiNoteTrillReservesFullSpan() {
            var note1 = createNote(-2, false);
            var note2 = createNote(0, false);
            var line = new Line();
            line.addElement(note1);
            line.addElement(note2);
            var trill = new Trill(note1, note2);
            line.addRangeElement(trill);

            var col1 = columnFor(note1, NOTE_X_SS);
            var col2 = columnFor(note2, NOTE2_X_SS);
            var result = stackColumns(List.of(col1, col2), line);

            var layout = require(
                result.getDecorationLayout(trill),
                "multi-note trill DecorationLayout");

            // Width should span from anchor X to beyond end note X
            assertThat(layout.widthSs()).isGreaterThan(NOTE2_X_SS - NOTE_X_SS);
        }

        @Test
        void testLegacyTrillFlagProducesLayout() {
            var note = createNote(-2, false);
            note.setTrill(true);
            var line = new Line();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note)), line);

            // Legacy flag should be bridged to a DecorationLayout
            var layout = result.findRangeElementDecorationLayout(note, Trill.class);
            assertThat(layout).describedAs("bridged legacy trill DecorationLayout").isNotNull();
        }

        @Test
        void testLegacyMultiNoteTrillProducesLayout() {
            var note1 = createNote(-2, false);
            note1.setTrill(true);
            var note2 = createNote(0, false);
            note2.setTrill(true);
            var line = new Line();
            line.addElement(note1);
            line.addElement(note2);

            var col1 = columnFor(note1, NOTE_X_SS);
            var col2 = columnFor(note2, NOTE2_X_SS);
            var result = stackColumns(List.of(col1, col2), line);

            // Legacy consecutive trill flags should produce a single bridged layout
            var layout = result.findRangeElementDecorationLayout(note1, Trill.class);
            assertThat(layout).describedAs("bridged legacy multi-note trill DecorationLayout").isNotNull();
        }
    }

    @Nested
    class FermataAndTrillInteraction {

        @Test
        void testFermataAndTrillDoNotOverlap() {
            var note = createNote(-2, false);
            note.setFermata(true);
            var line = new Line();
            line.addElement(note);
            var trill = new Trill(note);
            line.addRangeElement(trill);

            var result = stackColumns(List.of(columnFor(note)), line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout");
            var trillLayout = require(
                result.getDecorationLayout(trill),
                "trill DecorationLayout");

            // Both should be above staff, and one should be above the other
            // (order depends on processing order: fermata first, then trill)
            double fermataBottom = fermataLayout.ySs() + fermataLayout.heightSs();
            double trillBottom = trillLayout.ySs() + trillLayout.heightSs();

            // They should not overlap vertically
            boolean fermataAboveTrill = fermataLayout.ySs() + fermataLayout.heightSs() <= trillLayout.ySs() + TOLERANCE;
            boolean trillAboveFermata = trillLayout.ySs() + trillLayout.heightSs() <= fermataLayout.ySs() + TOLERANCE;
            assertThat(fermataAboveTrill || trillAboveFermata)
                .describedAs("fermata (y=%.3f, h=%.3f, bot=%.3f) and trill (y=%.3f, h=%.3f, bot=%.3f) should not overlap",
                    fermataLayout.ySs(), fermataLayout.heightSs(), fermataBottom,
                    trillLayout.ySs(), trillLayout.heightSs(), trillBottom)
                .isTrue();
        }
    }
}
