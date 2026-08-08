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
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.createNote;
import static songscribe.dom.StaffElementFactory.repeatRight;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.*;
import songscribe.font.DocumentFonts;

import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.layout.stacking.StackingContext;
import songscribe.layout.stacking.StackingUtils;
import songscribe.layout.stacking.StructuralStacker;
import songscribe.layout.stacking.VerticalStackingCalculator;

class StructuralTierStackingTest extends UnitTest {

    @BeforeAll
    static void initializeAccidentalWidths() {
        NoteGeometry.initializeAccidentalWidths();
    }

    private static final double LINE_WIDTH_SS = 64.0;
    private static final double TOLERANCE = 0.001;
    private static final double NOTE1_X_SS = 10.0;
    private static final double NOTE2_X_SS = 30.0;
    private static final double NOTE3_X_SS = 50.0;

    // An ascending run of staff positions. The rising note tips tilt the tuplet bracket, so a
    // script on the middle note meets an interpolated bracket height rather than an endpoint one.
    private static final int SLOPED_RUN_LOW_SP = -4;
    private static final int SLOPED_RUN_MID_SP = -2;
    private static final int SLOPED_RUN_HIGH_SP = 0;

    // A note above staff center, high enough that an accidental beside it reaches the fermata's
    // horizontal neighbourhood.
    private static final int ABOVE_CENTER_SP = -2;

    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    private static ElementColumn columnFor(StaffElement note, double xSs) {
        var column = new ElementColumn(
            note, List.of(), 0.0, 1.0, -1.5, 2.0, null, 0.0, false
        );
        column.setXSs(xSs);
        return column;
    }

    /**
     * Adds note1, a REPEAT_RIGHT split, and note2 to the line (every ending must have a
     * split between its two brackets) and returns their three stacking columns, laid out
     * left-to-right at the NOTE1/NOTE2/NOTE3 X positions. Shared by the ending fixtures.
     */
    private static List<ElementColumn> addSplitEndingElements(
        Line line, StaffElement note1, StaffElement note2
    ) {
        var split = repeatRight();
        line.addElement(note1);
        line.addElement(split);
        line.addElement(note2);
        return List.of(
            columnFor(note1, NOTE1_X_SS),
            columnFor(split, NOTE2_X_SS),
            columnFor(note2, NOTE3_X_SS));
    }

    private static LayoutResult stackColumns(List<ElementColumn> columns, Line line) {
        var builder = new LayoutResultBuilder();
        var calculator = new VerticalStackingCalculator();
        calculator.calculate(columns, line, builder, LINE_WIDTH_SS, DocumentFonts.defaultFonts());
        return builder.build();
    }

    /**
     * Runs only the structural stacker against fresh (zero-top) extents.
     * Use when you need exact Y control — no note-attached layer seeding occurs.
     */
    private static LayoutResult stackDirectly(List<ElementColumn> columns, Line line) {
        var builder = new LayoutResultBuilder();
        var context = new StackingContext(columns, line, builder);
        var stacker = new StructuralStacker(context, new StaffExtents(LINE_WIDTH_SS));
        stacker.stackTuplets();
        stacker.stackRemaining();
        return builder.build();
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class HairpinStacking {

        @Test
        void testCrescendoPositionedAboveNoteAttachedLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Hairpin should be above the staff (negative Y = higher)
            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testDiminuendoPositionedAboveNoteAttachedLayer() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var diminuendo = new Diminuendo(note1, note2);
            line.addSpan(diminuendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(diminuendo),
                "diminuendo DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testHairpinHasPositiveDimensions() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testHairpinPositionedAboveNoteDecorationsWhenPresent() {
            var note1 = createNote(-2, false);
            note1.addAttachment(new FermataAttachment(note1));
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note1, FermataAttachment.class),
                "fermata DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Hairpin should be above fermata (more negative Y)
            assertThat(hairpinLayout.ySs()).isLessThan(fermataLayout.ySs());
        }

        @Test
        void testAdjacentHairpinsShareOneBaseline() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var note3 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);

            // Back-to-back hairpins: crescendo note1→note2, diminuendo note2→note3. They hang on
            // the same column, so DynamicGrouper puts them in one group and they are placed on a
            // single shared reference line rather than one stacking outside the other's reservation.
            var crescendo = new Crescendo(note1, note2);
            var diminuendo = new Diminuendo(note2, note3);
            line.addSpan(crescendo);
            line.addSpan(diminuendo);

            var result = stackColumns(
                List.of(
                    columnFor(note1, NOTE1_X_SS),
                    columnFor(note2, NOTE2_X_SS),
                    columnFor(note3, NOTE3_X_SS)),
                line);

            var crescLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var dimLayout = require(
                result.getDecorationLayout(diminuendo),
                "diminuendo DecorationLayout");

            assertThat(crescLayout.ySs()).isLessThan(0.0);
            assertThat(dimLayout.ySs()).isLessThan(0.0);
            // Same height, same shared reference line, so the two wedges are level.
            assertThat(dimLayout.ySs()).isCloseTo(crescLayout.ySs(), within(TOLERANCE));
        }

        @Test
        void testDynamicBeforeHairpinAnchorSharesReferenceLineWithHairpin() {
            // note1 carries a text dynamic immediately before the crescendo's anchor (note2), so
            // DynamicGrouper puts both marks in one group and StructuralStacker gives them a
            // common reference line rather than separately stacked bounding-box centers.
            var note1 = createNote(0, false);
            var dynamic = new DynamicAttachment(note1, DynamicAttachment.DynamicType.MEZZO_FORTE);
            note1.addAttachment(dynamic);
            var note2 = createNote(0, false);
            var note3 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);

            var crescendo = new Crescendo(note2, note3);
            line.addSpan(crescendo);

            var result = stackColumns(
                List.of(
                    columnFor(note1, NOTE1_X_SS),
                    columnFor(note2, NOTE2_X_SS),
                    columnFor(note3, NOTE3_X_SS)),
                line);

            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var dynamicLayout = require(
                result.findAttachmentDecorationLayout(note1, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            var hairpinReferenceYSs = hairpinLayout.ySs() + hairpinLayout.heightSs() / 2;
            var dynamicReferenceYSs = dynamicLayout.ySs() + dynamicLayout.heightSs()
                - dynamic.getContentBottomSs()
                - StructuralStacker.DYNAMIC_TEXT_BASELINE_OFFSET_SS;

            // Not the same bounding-box center: the two members deliberately hang at different
            // offsets below the shared line (half the hairpin's height vs. the dynamic's baseline
            // drop plus descent), so only the derived reference line, not ySs() + heightSs() / 2 on
            // both sides, is expected to match.
            assertThat(dynamicReferenceYSs).isCloseTo(hairpinReferenceYSs, within(TOLERANCE));
        }

        @Test
        void testSingletonDynamicAdjacentToNoHairpinUsesUnchangedSoloPath() {
            // note1's dynamic sits two elements away from the crescendo's anchor (note3), so
            // DynamicGrouper gives it a singleton group placed via the unchanged
            // placeAndReserveClamped path rather than the grouped shared-baseline path.
            var note1 = createNote(0, false);
            var dynamic = new DynamicAttachment(note1, DynamicAttachment.DynamicType.FORTE);
            note1.addAttachment(dynamic);
            var gapNote = createNote(0, false);
            var note3 = createNote(0, false);
            var note4 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(gapNote);
            line.addElement(note3);
            line.addElement(note4);

            var crescendo = new Crescendo(note3, note4);
            line.addSpan(crescendo);

            var result = stackDirectly(
                List.of(
                    columnFor(note1, NOTE1_X_SS),
                    columnFor(gapNote, NOTE1_X_SS + 10.0),
                    columnFor(note3, NOTE2_X_SS),
                    columnFor(note4, NOTE3_X_SS)),
                line);

            var dynamicLayout = require(
                result.findAttachmentDecorationLayout(note1, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            var expectedYSs = StackingUtils.STAFF_TOP_INK_Y_SS
                - NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS - dynamicLayout.heightSs();
            assertThat(dynamicLayout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testDynamicAfterHairpinEndSharesReferenceLineWithHairpin() {
            // The mirror of the test above: the dynamic sits immediately *after* the crescendo's
            // end rather than before its anchor. Group membership for this side is covered in
            // DynamicGrouperTest; what is pinned here is that the resulting placement really puts
            // both marks on one reference line, which membership alone does not prove.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var note3 = createNote(0, false);
            var dynamic = new DynamicAttachment(note3, DynamicAttachment.DynamicType.PIANO);
            note3.addAttachment(dynamic);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var result = stackColumns(
                List.of(
                    columnFor(note1, NOTE1_X_SS),
                    columnFor(note2, NOTE2_X_SS),
                    columnFor(note3, NOTE3_X_SS)),
                line);

            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var dynamicLayout = require(
                result.findAttachmentDecorationLayout(note3, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            var hairpinReferenceYSs = hairpinLayout.ySs() + hairpinLayout.heightSs() / 2;
            var dynamicReferenceYSs = dynamicLayout.ySs() + dynamicLayout.heightSs()
                - dynamic.getContentBottomSs()
                - StructuralStacker.DYNAMIC_TEXT_BASELINE_OFFSET_SS;

            assertThat(dynamicReferenceYSs).isCloseTo(hairpinReferenceYSs, within(TOLERANCE));
        }

        @Test
        void testGroupWithNoResolvableMemberPlacesNothing() {
            // Both marks are grouped, but neither element was given a column — the situation when
            // a group's members belong to a line region this layout pass is not laying out. The
            // shared reference line starts at positive infinity and must never reach a placement:
            // if it did, both marks would be written at an absurd Y instead of being skipped.
            var dynamicNote = createNote(0, false);
            var dynamic = new DynamicAttachment(dynamicNote, DynamicAttachment.DynamicType.FORTE);
            dynamicNote.addAttachment(dynamic);
            var anchorNote = createNote(0, false);
            var endNote = createNote(0, false);
            var strangerNote = createNote(0, false);
            var line = detachedLine();
            line.addElement(dynamicNote);
            line.addElement(anchorNote);
            line.addElement(endNote);

            var crescendo = new Crescendo(anchorNote, endNote);
            line.addSpan(crescendo);

            // A column for an element that is not in the group, so the stacking pass runs with a
            // non-empty column list yet resolves neither group member.
            var result = stackDirectly(List.of(columnFor(strangerNote, NOTE1_X_SS)), line);

            assertThat(result.getDecorationLayout(crescendo))
                .as("an unresolvable hairpin is skipped, not placed at the infinite reference line")
                .isNull();
            assertThat(result.findAttachmentDecorationLayout(dynamicNote, DynamicAttachment.class))
                .as("an unresolvable dynamic is skipped too")
                .isNull();
        }

        @Test
        void testGroupWithOneResolvableMemberPlacesItWhereALoneHairpinWouldGo() {
            // A two-member group in which only the hairpin resolves. It goes through the grouped
            // two-pass path rather than the single-member path, and the two must agree: taking the
            // most outward of one demand is that demand. If the passes ever disagreed, a hairpin
            // would jump vertically the moment a neighbouring dynamic lost its column.
            var dynamicNote = createNote(0, false);
            var dynamic = new DynamicAttachment(dynamicNote, DynamicAttachment.DynamicType.FORTE);
            dynamicNote.addAttachment(dynamic);
            var anchorNote = createNote(0, false);
            var endNote = createNote(0, false);
            var line = detachedLine();
            line.addElement(dynamicNote);
            line.addElement(anchorNote);
            line.addElement(endNote);

            var crescendo = new Crescendo(anchorNote, endNote);
            line.addSpan(crescendo);

            // No column for dynamicNote, so the group has two members but one resolvable placement.
            var result = stackDirectly(
                List.of(columnFor(anchorNote, NOTE1_X_SS), columnFor(endNote, NOTE2_X_SS)), line);

            var expectedYSs = StackingUtils.anchorCeilingSs(0)
                - StructuralStacker.HAIRPIN_MARGIN_SS - crescendo.getContentHeightSs();
            var layout = require(result.getDecorationLayout(crescendo), "crescendo layout");
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testHairpinYEqualsAnchorCeilingMinusMarginMinusHeight() {
            // Fresh structural extents (top=0.0), sp=0 → ceilingSs = anchorCeilingSs(0).
            // Single crescendo: no prior reservation → ceiling is just the anchor ceiling.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var ceilingSs = StackingUtils.anchorCeilingSs(0);
            var expectedYSs = ceilingSs - StructuralStacker.HAIRPIN_MARGIN_SS
                - crescendo.getContentHeightSs();
            var layout = require(result.getDecorationLayout(crescendo), "crescendo layout");
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testDiminuendoYEqualsAnchorCeilingMinusMarginMinusHeight() {
            // Diminuendo follows the same placement path as crescendo.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var diminuendo = new Diminuendo(note1, note2);
            line.addSpan(diminuendo);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var ceilingSs = StackingUtils.anchorCeilingSs(0);
            var expectedYSs = ceilingSs - StructuralStacker.HAIRPIN_MARGIN_SS
                - diminuendo.getContentHeightSs();
            var layout = require(result.getDecorationLayout(diminuendo), "diminuendo layout");
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class TupletStacking {

        @Test
        void testTupletSpanPositionedAboveStaff() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(tuplet),
                "tuplet DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testTupletHasPositiveDimensions() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var layout = require(
                result.getDecorationLayout(tuplet),
                "tuplet DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        // A fermata and a trill carry an outside-staff-priority in LilyPond, so the tuplet bracket
        // skips them (tuplet-bracket.cc lines 688-690) and they float outside it instead. The two
        // clearance tests below pin that: each script must end up clear of the bracket (issue #520).

        @Test
        void testFermataOnTupletNoteClearsBracket() {
            var note1 = createNote(-2, false);
            note1.addAttachment(new FermataAttachment(note1));
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note1, FermataAttachment.class),
                "fermata DecorationLayout");
            var tupletLayout = require(
                result.getDecorationLayout(tuplet),
                "tuplet DecorationLayout");

            // The fermata sits on the anchor note, where the sloped bracket's top edge is exactly
            // its layout ySs. The fermata's bottom must not reach it.
            assertThat(fermataLayout.ySs() + fermataLayout.heightSs())
                .isLessThanOrEqualTo(tupletLayout.ySs());
        }

        @Test
        void testTrillOnTupletNoteClearsBracket() {
            var note1 = createNote(-2, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);
            var trill = new Trill(note1, note2);
            line.addSpan(trill);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var trillLayout = require(result.getDecorationLayout(trill), "trill DecorationLayout");
            var tupletLayout = require(result.getDecorationLayout(tuplet), "tuplet DecorationLayout");

            // The trill is flat and the bracket is linear in x, so the bracket's highest point under
            // the trill's span is at whichever end is more outward.
            var bracketTopSs = Math.min(tupletLayout.ySs(), tupletLayout.ySs() + tupletLayout.dySs());
            assertThat(trillLayout.ySs() + trillLayout.heightSs()).isLessThanOrEqualTo(bracketTopSs);
        }

        @Test
        void testFermataOnInteriorTupletNoteClearsBracket() {
            // The scripts above sit on the anchor, where the bracket's top edge is exactly its
            // layout ySs. An interior note instead meets the sloped edge at an interpolated height,
            // so this pins that the clearance is read at the script's own X rather than an endpoint.
            var note1 = createNote(SLOPED_RUN_LOW_SP, false);
            var note2 = createNote(SLOPED_RUN_MID_SP, false);
            note2.addAttachment(new FermataAttachment(note2));
            var note3 = createNote(SLOPED_RUN_HIGH_SP, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(note3);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note3, 3);
            line.addSpan(tuplet);

            var result = stackColumns(
                List.of(
                    columnFor(note1, NOTE1_X_SS),
                    columnFor(note2, NOTE2_X_SS),
                    columnFor(note3, NOTE3_X_SS)),
                line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note2, FermataAttachment.class),
                "fermata DecorationLayout");
            var tupletLayout = require(
                result.getDecorationLayout(tuplet),
                "tuplet DecorationLayout");

            // The bracket's top edge is linear from its anchor over widthSs, so its height at the
            // interior note's X is the anchor Y plus the slope's share of that run.
            var interiorFractionOfSpan =
                (NOTE2_X_SS - tupletLayout.xSs()) / tupletLayout.widthSs();
            var bracketTopAtNote2Ss =
                tupletLayout.ySs() + tupletLayout.dySs() * interiorFractionOfSpan;

            assertThat(fermataLayout.ySs() + fermataLayout.heightSs())
                .describedAs("interior-note fermata clears the sloped bracket at its own X")
                .isLessThanOrEqualTo(bracketTopAtNote2Ss);
        }

        // The converse of the two tests above, and structural rather than a #520 regression guard:
        // the bracket's ceiling is derived from note tips and the staccato/accent obstacle set alone
        // (StructuralStacker.scriptObstacles), so a fermata is not an input to it and cannot move
        // it. This held before #520 as well. It pins the exclusion against a future change that
        // folds fermata into that obstacle set.
        @Test
        void testBracketIgnoresFermataOnItsOwnNote() {
            var withFermataYSs = stackTupletOverTwoNotes(true);
            var withoutFermataYSs = stackTupletOverTwoNotes(false);

            assertThat(withFermataYSs).isCloseTo(withoutFermataYSs, within(TOLERANCE));
        }

        private static double stackTupletOverTwoNotes(boolean withFermata) {
            var note1 = createNote(-2, false);

            if (withFermata) {
                note1.addAttachment(new FermataAttachment(note1));
            }

            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            return require(result.getDecorationLayout(tuplet), "tuplet DecorationLayout").ySs();
        }

        @Test
        void testTupletYEqualsTipCeilingMinusMarginMinusHeight() {
            // Both notes at the same staff position -> flat contour (dySs == 0), so the
            // left-endpoint ceiling is just the shared tip's absolute top. Tuplets place via the
            // tip-based verbatim-anchor placement (not the staffPosition/anchorCeilingSs path), so
            // the raw ceiling is ElementColumn.getAbsoluteTopYSs(), not anchorCeilingSs(0).
            // The clearance then applies a staff-top clamp: a tip below the top staff line raises
            // the ceiling to the staff-padding-widened staff top so the bracket stays above the
            // staff. These notes sit at the middle line (tip below the staff top), so the clamp binds.
            // Tuplets are stacked before hairpins, so no prior structural reservation.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            // The bracket LINE floats TUPLET_BRACKET_PADDING_SS above the tip (StructuralStacker's
            // TUPLET_ARM_MARGIN_SS, applied to the box/arm bottom, is the padding minus the arm
            // height so the line lands exactly there).
            // The tip sits below the staff top, so the clamp binds: staffTopClampSs returns the
            // staff-top centerline, from which the tuplet path subtracts its staff-padding.
            var tipCeilingSs = StackingUtils.staffTopClampSs(
                columnFor(note1, NOTE1_X_SS).getAbsoluteTopYSs())
                - StructuralStacker.TUPLET_STAFF_PADDING_SS;
            var expectedYSs = tipCeilingSs - StructuralStacker.TUPLET_ARM_MARGIN_SS
                - tuplet.getContentHeightSs();
            var layout = require(result.getDecorationLayout(tuplet), "tuplet layout");
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testTupletWithNullAnchorOrEndElementIsSkippedWithoutDecorationLayout() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);
            tuplet.setEndElement(null);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            assertThat(result.getDecorationLayout(tuplet)).isNull();
        }

        @Test
        void testTupletWithMissingColumnForEndElementIsSkippedWithoutDecorationLayout() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            // note2's column is omitted, so columnsByElement.get(endNote) resolves to null.
            var result = stackDirectly(List.of(columnFor(note1, NOTE1_X_SS)), line);

            assertThat(result.getDecorationLayout(tuplet)).isNull();
        }

        @Test
        void testBracketedTupletReservesBracketedHeight() {
            // Non-beamed tuplet → bracketed. Y placement is covered by
            // testTupletYEqualsAnchorCeilingMinusMarginMinusHeight; this asserts the stacker
            // selected the bracketed height and not the number-only height.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var layout = require(result.getDecorationLayout(tuplet), "tuplet layout");
            assertThat(layout.heightSs()).isCloseTo(Tuplet.bracketedHeightSs(), within(TOLERANCE));
            assertThat(layout.heightSs()).isNotCloseTo(Tuplet.numberOnlyHeightSs(), within(TOLERANCE));
        }

        @Test
        void testNumberOnlyTupletReservesNumberOnlyHeight() {
            // Beamed, stems-up notes → numberOnly. Asserts the stacker selected the
            // number-only height and not the bracketed height.
            var note1 = createNote(0, true);
            var note2 = createNote(0, true);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addSpan(new Beam(note1, note2));

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            var layout = require(result.getDecorationLayout(tuplet), "tuplet layout");
            assertThat(layout.heightSs()).isCloseTo(Tuplet.numberOnlyHeightSs(), within(TOLERANCE));
            assertThat(layout.heightSs()).isNotCloseTo(Tuplet.bracketedHeightSs(), within(TOLERANCE));
        }

        @Test
        void testNumberOnlyTupletCentersNumberOnTheBracketPadding() {
            // A number-only tuplet draws no bracket, but LilyPond still centers the number on where
            // the bracket line would be: the ink CENTER — not the ink bottom — sits
            // TUPLET_BRACKET_PADDING_SS above the tip (issue #653). The reserved box top is the ink
            // top, so it lands half an ink height beyond that center.
            var note1 = createNote(0, true);
            var note2 = createNote(0, true);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);
            line.addSpan(new Beam(note1, note2));

            var tuplet = Tuplet.withUnresolvedRatio(note1, note2, 3);
            line.addSpan(tuplet);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            // Same staff-top clamp as the bracketed case: these notes sit at the middle line.
            var tipCeilingSs = StackingUtils.staffTopClampSs(
                columnFor(note1, NOTE1_X_SS).getAbsoluteTopYSs())
                - StructuralStacker.TUPLET_STAFF_PADDING_SS;
            var expectedInkTopYSs = tipCeilingSs - StructuralStacker.TUPLET_BRACKET_PADDING_SS
                - Tuplet.TUPLET_NUMBER_INK_HEIGHT_SS / 2.0;

            var layout = require(result.getDecorationLayout(tuplet), "tuplet layout");
            assertThat(layout.ySs()).isCloseTo(expectedInkTopYSs, within(TOLERANCE));

            // ySs alone only pins margin + height, which is the same sum for both branches by
            // construction; assert the margin too so a failure localizes to the branch selection.
            assertThat(layout.marginSs())
                .isCloseTo(StructuralStacker.TUPLET_NUMBER_MARGIN_SS, within(TOLERANCE));
        }

    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class TextDynamicsStacking {

        @Test
        void testTextDynamicsPositionedAboveStaff() {
            var note = createNote(0, false);
            note.addAttachment(new DynamicAttachment(note,
                DynamicAttachment.DynamicType.FORTE));

            var line = detachedLine();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testTextDynamicsHasPositiveDimensions() {
            var note = createNote(0, false);
            note.addAttachment(new DynamicAttachment(note,
                DynamicAttachment.DynamicType.PIANO));

            var line = detachedLine();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isGreaterThan(0.0);
        }

        @Test
        void testTextDynamicsPositionedBelowHairpins() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            note1.addAttachment(new DynamicAttachment(note1,
                DynamicAttachment.DynamicType.MEZZO_FORTE));

            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var result = stackColumns(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)),
                line);

            var dynamicsLayout = require(
                result.findAttachmentDecorationLayout(note1, DynamicAttachment.class),
                "text dynamics DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");

            // Hairpins are processed before text dynamics in the structural tier,
            // so text dynamics should stack above the hairpin
            // (both are above-staff, so the text dynamics Y should be more negative)
            assertThat(dynamicsLayout.ySs())
                .describedAs("text dynamics should stack above hairpin")
                .isLessThan(hairpinLayout.ySs());
        }

        @Test
        void testTextDynamicsXIsCenteredOnNotehead() {
            // Verifies: centeredXSs = columnXSs + noteheadCenterXSs - contentWidthSs/2
            var note = createNote(0, false);
            var dynamic = new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE);
            note.addAttachment(dynamic);

            var line = detachedLine();
            line.addElement(note);

            var result = stackDirectly(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            var noteType = note.getType();
            var noteheadCenterXSs = noteType.getElementCenterXSs()
                + NoteGeometry.getNoteheadXOffsetSs(noteType, note.getDirection());
            var expectedXSs = NOTE1_X_SS + noteheadCenterXSs - dynamic.getContentWidthSs() / 2.0;
            assertThat(layout.xSs()).isCloseTo(expectedXSs, within(TOLERANCE));
        }

        @Test
        void testIsolatedTextDynamicsClampsToStaffPadding() {
            // stackDirectly runs against fresh (zero-top) extents: no real reservation under the
            // dynamic's footprint, so only the staff-padding clamp applies.
            var note = createNote(0, false);
            var dynamic = new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE);
            note.addAttachment(dynamic);

            var line = detachedLine();
            line.addElement(note);

            var result = stackDirectly(List.of(columnFor(note, NOTE1_X_SS)), line);

            var layout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            var expectedYSs = StackingUtils.STAFF_TOP_INK_Y_SS
                - NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS - layout.heightSs();
            assertThat(layout.ySs()).isCloseTo(expectedYSs, within(TOLERANCE));
        }

        @Test
        void testTextDynamicsClearsFermataByDynamicPadding() {
            // The structural tier starts from a copy of the note-attached layer's top extents
            // (StaffExtents.copyTopFrom), so a tier-2 fermata already placed above the staff is a
            // real reservation the dynamic's placement must clear by DYNAMIC_PADDING_SS.
            var note = createNote(0, false);
            var fermata = new FermataAttachment(note);
            note.addAttachment(fermata);
            var dynamic = new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE);
            note.addAttachment(dynamic);

            var line = detachedLine();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout");
            var dynamicLayout = require(
                result.findAttachmentDecorationLayout(note, DynamicAttachment.class),
                "text dynamics DecorationLayout");

            assertThat(fermataLayout.ySs() - (dynamicLayout.ySs() + dynamicLayout.heightSs()))
                .describedAs("text dynamics must clear the fermata by DYNAMIC_PADDING_SS")
                .isCloseTo(NoteAttachedStacker.DYNAMIC_PADDING_SS, within(TOLERANCE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass" })
    @Nested
    class SpanElementGuards {

        @Test
        void testHairpinWithNullAnchorIsSkipped() {
            // Resolving the span's columns fails on a null anchor, so no layout is written.
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            line.addElement(note1);
            line.addElement(note2);

            var crescendo = new Crescendo(note1, note2);
            crescendo.setAnchorElement(null);  // trigger null guard
            line.addSpan(crescendo);

            var result = stackDirectly(
                List.of(columnFor(note1, NOTE1_X_SS), columnFor(note2, NOTE2_X_SS)), line);

            // No layout should have been emitted for the null-anchor span element.
            assertThat(result.getDecorationLayout(crescendo)).isNull();
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class EndingStacking {

        @Test
        void testEndingPositionedAboveStaff() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            var columns = addSplitEndingElements(line, note1, note2);

            var ending = new Ending(note1, note2);
            line.addSpan(ending);

            var result = stackColumns(columns, line);

            var layout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            assertThat(layout.ySs()).isLessThan(0.0);
        }

        @Test
        void testEndingHasPositiveDimensions() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            var columns = addSplitEndingElements(line, note1, note2);

            var ending = new Ending(note1, note2);
            line.addSpan(ending);

            var result = stackColumns(columns, line);

            var layout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            assertThat(layout.widthSs()).isGreaterThan(0.0);
            assertThat(layout.heightSs()).isCloseTo(
                Ending.VOLTA_TICK_HEIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testEndingPositionedAboveHairpins() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            var columns = addSplitEndingElements(line, note1, note2);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var ending = new Ending(note1, note2);
            line.addSpan(ending);

            var result = stackColumns(columns, line);

            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var endingLayout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            // Ending should be above hairpin (more negative Y)
            assertThat(endingLayout.ySs()).isLessThan(hairpinLayout.ySs());
        }

        @Test
        void testEndingSpanProducesDecorationLayout() {
            var note1 = createNote(0, false);
            var note2 = createNote(0, false);
            var line = detachedLine();
            var columns = addSplitEndingElements(line, note1, note2);

            var ending = new Ending(note1, note2);
            line.addSpan(ending);

            var result = stackColumns(columns, line);

            var decorationLayout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            assertThat(decorationLayout.ySs()).isLessThan(0.0);
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class StructuralTierInteraction {

        @Test
        void testAllStructuralElementsAboveNoteAttachedLayer() {
            var note1 = createNote(-2, false);
            note1.addAttachment(new FermataAttachment(note1));
            var note2 = createNote(0, false);
            note2.addAttachment(new DynamicAttachment(note2,
                DynamicAttachment.DynamicType.FORTE));

            var line = detachedLine();
            var columns = addSplitEndingElements(line, note1, note2);

            var crescendo = new Crescendo(note1, note2);
            line.addSpan(crescendo);

            var ending = new Ending(note1, note2);
            line.addSpan(ending);

            var result = stackColumns(columns, line);

            var fermataLayout = require(
                result.findAttachmentDecorationLayout(note1, FermataAttachment.class),
                "fermata DecorationLayout");
            var hairpinLayout = require(
                result.getDecorationLayout(crescendo),
                "crescendo DecorationLayout");
            var endingLayout = require(
                result.getDecorationLayout(ending),
                "ending DecorationLayout");

            // All structural elements should be above the fermata (note-attached)
            assertThat(hairpinLayout.ySs())
                .describedAs("hairpin above fermata")
                .isLessThan(fermataLayout.ySs());
            assertThat(endingLayout.ySs())
                .describedAs("ending above fermata")
                .isLessThan(fermataLayout.ySs());
        }

        // Scripts must ignore accidentals: the accidental is seeded into the structural layer only
        // after the outer scripts have stacked against it. Before #520 the scripts stacked against
        // their own layer, which accidentals never touched, so this could not regress; now they
        // share a layer and only the call order in VerticalStackingCalculator keeps them apart.
        @Test
        void testFermataIgnoresAccidentalOnSameNote() {
            var withAccidentalYSs = stackFermataNote(true);
            var withoutAccidentalYSs = stackFermataNote(false);

            assertThat(withAccidentalYSs)
                .describedAs("accidental does not push the fermata outward")
                .isCloseTo(withoutAccidentalYSs, within(TOLERANCE));
        }

        private static double stackFermataNote(boolean withAccidental) {
            var note = createNote(ABOVE_CENTER_SP, false);
            note.addAttachment(new FermataAttachment(note));

            if (withAccidental) {
                note.setAccidental(StaffElement.Accidental.SHARP);
            }

            var line = detachedLine();
            line.addElement(note);

            var result = stackColumns(List.of(columnFor(note, NOTE1_X_SS)), line);

            return require(
                result.findAttachmentDecorationLayout(note, FermataAttachment.class),
                "fermata DecorationLayout").ySs();
        }
    }
}
