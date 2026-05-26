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

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;

class FormatMigratorTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 5 & 6 — migrate()
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Migrate {

        // Row 5: version guard — migrate(lines, 2) is a no-op; lines are untouched.
        // The in-method guard (formatVersion >= 2) is asserted here directly,
        // independent of the pipeline.
        @Test
        void testSkipsWhenFormatVersionAtThreshold() {
            var line = lineWith(ElementType.CROTCHET);
            var note = line.getElement(0);
            note.addAttachment(new TempoChangeAttachment(new Tempo()));
            // A non-zero tempoChangeYPosPx would cause userYOffsetSs to change if migration ran.
            line.setTempoChangeYPosPx(NON_ZERO_TEMPO_OFFSET_PX);

            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_AT_THRESHOLD);

            var attachment = note.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (attachment == null) {
                return;
            }

            // Offset must remain at the default (0.0) because migration was skipped.
            assertThat(attachment.getUserYOffsetSs()).isEqualTo(0.0);
        }

        // Row 6: migrate(lines, 1) iterates per-line — a line with a non-zero
        // tempoChangeYPosPx has its TempoChangeAttachment.userYOffsetSs updated.
        @Test
        void testIteratesPerLineAndAppliesTempoOffset() {
            var line = lineWith(ElementType.CROTCHET);
            var note = line.getElement(0);
            note.addAttachment(new TempoChangeAttachment(new Tempo()));
            line.setTempoChangeYPosPx(NON_ZERO_TEMPO_OFFSET_PX);

            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            var attachment = note.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (attachment == null) {
                return;
            }

            // Legacy migration adds the raw px value directly to userYOffsetSs
            // (no unit conversion — the px field is repurposed as an Ss delta).
            assertThat(attachment.getUserYOffsetSs()).isEqualTo(NON_ZERO_TEMPO_OFFSET_PX);
        }
    }

    // -----------------------------------------------------------------------
    // Rows 7 & 8 — migrateLineLevelOffsets()
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MigrateLineLevelOffsets {

        // Row 7: non-zero tempoChangeYPosPx → the raw px value is added to each
        // TempoChangeAttachment.userYOffsetSs on the line (legacy: no unit conversion).
        @Test
        void testNonZeroTempoOffsetAppliedToTempoAttachment() {
            var line = lineWith(ElementType.CROTCHET);
            var note = line.getElement(0);
            note.addAttachment(new TempoChangeAttachment(new Tempo()));
            line.setTempoChangeYPosPx(NON_ZERO_TEMPO_OFFSET_PX);

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            var attachment = note.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (attachment == null) {
                return;
            }

            assertThat(attachment.getUserYOffsetSs()).isEqualTo(NON_ZERO_TEMPO_OFFSET_PX);
        }

        // Row 8a: beatChangeYPosPx != default → delta (beatChangeYPosPx - defaultPx) is added
        // to each BeatChangeAttachment.userYOffsetSs.
        @Test
        void testNonDefaultBeatChangeOffsetAppliedToBeatChangeAttachment() {
            var line = buildLineWithBeatChange();
            var note = line.getElement(0);
            var beatChangeDefaultPx = ScaleContext.ssToRoundedPx(Line.BEAT_CHANGE_DEFAULT_Y_SS);
            var nonDefaultBeatChangePx = beatChangeDefaultPx + BEAT_CHANGE_OFFSET_DELTA_PX;
            line.setBeatChangeYPosPx(nonDefaultBeatChangePx);

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            var attachment = note.findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (attachment == null) {
                return;
            }

            // delta = nonDefault - default = BEAT_CHANGE_OFFSET_DELTA_PX; initial offset is 0.
            assertThat(attachment.getUserYOffsetSs()).isEqualTo(BEAT_CHANGE_OFFSET_DELTA_PX);
        }

        // Row 8b: beatChangeYPosPx == default → condition is false; no delta applied,
        // BeatChangeAttachment.userYOffsetSs stays at its initial value.
        @Test
        void testDefaultBeatChangeOffsetIsNoOp() {
            var line = buildLineWithBeatChange();
            var note = line.getElement(0);
            // Default is the field's initial value — no explicit set needed.

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            var attachment = note.findAttachment(BeatChangeAttachment.class);
            assertThat(attachment).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (attachment == null) {
                return;
            }

            assertThat(attachment.getUserYOffsetSs()).isEqualTo(0.0);
        }

        // Row 9: firstSecondEndingYPosPx != default → delta (endingOffset - endingDefaultPx) is
        // added to each Ending.yPositionSs on the line.
        @Test
        void testNonDefaultEndingOffsetAppliedToEndingYPosition() {
            var line = lineWith(ElementType.CROTCHET);
            var note = line.getElement(0);
            var ending = new Ending(note, note, Ending.Type.FIRST);
            line.addRangeElement(ending);

            var endingDefaultPx = ScaleContext.ssToRoundedPx(Line.ENDING_DEFAULT_Y_SS);
            var nonDefaultEndingPx = endingDefaultPx + ENDING_OFFSET_DELTA_PX;
            line.setFirstSecondEndingYPosPx(nonDefaultEndingPx);

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            // delta = nonDefault - default = ENDING_OFFSET_DELTA_PX; initial yPositionSs is 0.
            assertThat(ending.getYPositionSs()).isEqualTo(ENDING_OFFSET_DELTA_PX);
        }

        // Row 10: trillYPosPx != default → delta (trillOffset - trillDefaultPx) is
        // added to each Trill.yPositionSs on the line.
        @Test
        void testNonDefaultTrillOffsetAppliedToTrillYPosition() {
            var line = lineWith(ElementType.CROTCHET);
            var note = line.getElement(0);
            var trill = new Trill(note);
            line.addRangeElement(trill);

            var trillDefaultPx = ScaleContext.ssToRoundedPx(Line.TRILL_DEFAULT_Y_SS);
            var nonDefaultTrillPx = trillDefaultPx + TRILL_OFFSET_DELTA_PX;
            line.setTrillYPosPx(nonDefaultTrillPx);

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            // delta = nonDefault - default = TRILL_OFFSET_DELTA_PX; initial yPositionSs is 0.
            assertThat(trill.getYPositionSs()).isEqualTo(TRILL_OFFSET_DELTA_PX);
        }
    }

    // -----------------------------------------------------------------------
    // Rows 11 & 12 — migrateAnnotationPositions()
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MigrateAnnotationPositions {

        // Row 11: yPosPx > 0 (below staff) → yPosPx set to ABOVE constant;
        // userYOffsetSs adjusted to preserve visual position.
        @Test
        void testBelowStaffAnnotationMovedAboveWithOffsetAdjusted() {
            var line = lineWithAnnotation("text");
            var note = line.getElement(0);
            var attachment = note.findAttachment(AnnotationAttachment.class);
            assertThat(attachment).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (attachment == null) {
                return;
            }

            var annotation = attachment.getAnnotation();
            // Set a positive yPosPx to simulate a below-staff legacy annotation.
            annotation.setYPosPx(BELOW_STAFF_Y_POS_PX);

            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            // After migration, yPosPx must be the ABOVE constant.
            assertThat(annotation.getYPosPx()).isEqualTo(Annotation.ABOVE);

            // userYOffsetSs absorbs the positional difference so the visual position is preserved.
            // offset = oldYPosPx - ABOVE; initial userYOffsetSs was 0.
            var expectedOffset = (double) (BELOW_STAFF_Y_POS_PX - Annotation.ABOVE);
            assertThat(annotation.getUserYOffsetSs()).isEqualTo(expectedOffset);
        }

        // Row 12: yPosPx <= 0 (already above staff) → no change; position and offset unchanged.
        @Test
        void testAboveStaffAnnotationIsNoOp() {
            var line = lineWithAnnotation("text");
            var note = line.getElement(0);
            var attachment = note.findAttachment(AnnotationAttachment.class);
            assertThat(attachment).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (attachment == null) {
                return;
            }

            var annotation = attachment.getAnnotation();
            // Default yPosPx is ABOVE (negative), which satisfies yPosPx <= 0; no migration needed.
            var originalYPosPx = annotation.getYPosPx();
            var originalOffset = annotation.getUserYOffsetSs();

            FormatMigrator.migrate(List.of(line), FORMAT_VERSION_LEGACY);

            // Neither field must change — above-staff annotations are already in the correct form.
            assertThat(annotation.getYPosPx()).isEqualTo(originalYPosPx);
            assertThat(annotation.getUserYOffsetSs()).isEqualTo(originalOffset);
        }
    }

    // -----------------------------------------------------------------------
    // Rows 13–16 — migratePixelsToStaffSpace()
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MigratePixelsToStaffSpace {

        // Row 13: lyricsYPosSs is divided by pps.
        @Test
        void testLyricsYPosSsDividedByPps() {
            var line = detachedLine();
            line.setLyricsYPosSs(NON_ZERO_LYRICS_Y_POS_SS);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            assertThat(line.getLyricsYPosSs())
                .isEqualTo(NON_ZERO_LYRICS_Y_POS_SS / ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        }

        // Row 14a: Tuplet with non-zero verticalPositionSs → divided by pps (rounded).
        @Test
        void testTupletNonZeroVerticalPositionDividedByPps() {
            var line = detachedLine();
            var anchor = ElementType.CROTCHET.newInstance();
            var end = ElementType.CROTCHET.newInstance();
            line.addElement(anchor);
            line.addElement(end);
            var tuplet = new Tuplet(anchor, end, TUPLET_GRADE);
            tuplet.setVerticalPositionSs(NON_ZERO_TUPLET_VERTICAL_POS_SS);
            line.addRangeElement(tuplet);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            var expected = (int) Math.round(
                NON_ZERO_TUPLET_VERTICAL_POS_SS / ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE
            );
            assertThat(tuplet.getVerticalPositionSs()).isEqualTo(expected);
        }

        // Row 14b: Tuplet with zero verticalPositionSs → no-op (stays zero).
        @Test
        void testTupletZeroVerticalPositionIsNoOp() {
            var line = detachedLine();
            var anchor = ElementType.CROTCHET.newInstance();
            var end = ElementType.CROTCHET.newInstance();
            line.addElement(anchor);
            line.addElement(end);
            var tuplet = new Tuplet(anchor, end, TUPLET_GRADE);
            // verticalPositionSs defaults to 0 — no set needed
            line.addRangeElement(tuplet);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            assertThat(tuplet.getVerticalPositionSs()).isEqualTo(0);
        }

        // Row 15: Hairpin with non-zero x1/x2/yShiftSs → all divided by pps.
        @Test
        void testHairpinShiftsDividedByPps() {
            var line = detachedLine();
            var anchor = ElementType.CROTCHET.newInstance();
            var end = ElementType.CROTCHET.newInstance();
            line.addElement(anchor);
            line.addElement(end);
            var hairpin = new Crescendo(anchor, end);
            hairpin.setX1ShiftSs(NON_ZERO_HAIRPIN_X1_SHIFT_SS);
            hairpin.setX2ShiftSs(NON_ZERO_HAIRPIN_X2_SHIFT_SS);
            hairpin.setYShiftSs(NON_ZERO_HAIRPIN_Y_SHIFT_SS);
            line.addRangeElement(hairpin);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            assertThat(hairpin.getX1ShiftSs()).isEqualTo(NON_ZERO_HAIRPIN_X1_SHIFT_SS / pps);
            assertThat(hairpin.getX2ShiftSs()).isEqualTo(NON_ZERO_HAIRPIN_X2_SHIFT_SS / pps);
            assertThat(hairpin.getYShiftSs()).isEqualTo(NON_ZERO_HAIRPIN_Y_SHIFT_SS / pps);
        }

        // Row 16: Note with a Glissando → x1Translate and x2Translate divided by pps.
        @Test
        void testGlissandoTranslatesDividedByPps() {
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            note.setGlissando(StaffElement.Glissando.Type.CONNECTED);

            var glissando = note.getGlissando();
            assertThat(glissando).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (glissando == null) {
                return;
            }

            glissando.x1Translate = NON_ZERO_GLISSANDO_X1_TRANSLATE;
            glissando.x2Translate = NON_ZERO_GLISSANDO_X2_TRANSLATE;

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            assertThat(glissando.x1Translate).isEqualTo(NON_ZERO_GLISSANDO_X1_TRANSLATE / pps);
            assertThat(glissando.x2Translate).isEqualTo(NON_ZERO_GLISSANDO_X2_TRANSLATE / pps);
        }
    }

    // -----------------------------------------------------------------------
    // Test constants
    // -----------------------------------------------------------------------

    /** Format version at which migration is skipped (the threshold tested by the version guard). */
    private static final int FORMAT_VERSION_AT_THRESHOLD = 2;

    /** Format version that triggers migration (legacy v1 format). */
    private static final int FORMAT_VERSION_LEGACY = 1;

    /**
     * Arbitrary non-zero tempo offset in pixels used to exercise the tempo migration path.
     * The value must differ from 0 to ensure the migration branch is entered.
     */
    private static final int NON_ZERO_TEMPO_OFFSET_PX = 16;

    /**
     * Delta added to the beat-change default px value to produce a non-default input.
     * Must be non-zero to enter the beat-change migration branch.
     */
    private static final int BEAT_CHANGE_OFFSET_DELTA_PX = 8;

    /**
     * Delta added to the ending default px value to produce a non-default input.
     * Must be non-zero to enter the ending migration branch.
     */
    private static final int ENDING_OFFSET_DELTA_PX = 6;

    /**
     * Delta added to the trill default px value to produce a non-default input.
     * Must be non-zero to enter the trill migration branch.
     */
    private static final int TRILL_OFFSET_DELTA_PX = 4;

    /**
     * A positive yPosPx value representing a below-staff annotation (legacy positioning).
     * Must be positive to trigger the below-staff migration branch in migrateAnnotationPositions.
     */
    private static final int BELOW_STAFF_Y_POS_PX = Annotation.BELOW;

    /**
     * Non-zero lyricsYPosSs value used to verify division by pps in migratePixelsToStaffSpace.
     * Must differ from 0 so the division produces a distinct result.
     */
    private static final double NON_ZERO_LYRICS_Y_POS_SS = 16.0;

    /**
     * Tuplet grade used in migratePixelsToStaffSpace tests.
     * Three is the standard triplet grade.
     */
    private static final int TUPLET_GRADE = 3;

    /**
     * Non-zero tuplet verticalPositionSs (in pixels pre-migration) used to verify
     * rounding division by pps. Must be non-zero to enter the tuplet branch.
     */
    private static final int NON_ZERO_TUPLET_VERTICAL_POS_SS = 24;

    /**
     * Non-zero x1ShiftSs for hairpin shift migration test.
     */
    private static final double NON_ZERO_HAIRPIN_X1_SHIFT_SS = 8.0;

    /**
     * Non-zero x2ShiftSs for hairpin shift migration test.
     */
    private static final double NON_ZERO_HAIRPIN_X2_SHIFT_SS = 16.0;

    /**
     * Non-zero yShiftSs for hairpin shift migration test.
     */
    private static final double NON_ZERO_HAIRPIN_Y_SHIFT_SS = 8.0;

    /**
     * Non-zero x1Translate for glissando migration test.
     */
    private static final double NON_ZERO_GLISSANDO_X1_TRANSLATE = 8.0;

    /**
     * Non-zero x2Translate for glissando migration test.
     */
    private static final double NON_ZERO_GLISSANDO_X2_TRANSLATE = 16.0;

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Creates a line containing a single crotchet with a BeatChangeAttachment. */
    private static Line buildLineWithBeatChange() {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        note.addAttachment(
            new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET, Duration.CROTCHET))
        );
        return line;
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class MigrateAnnotationDynamics {

        // T45: Note with annotation "f" → converted to DynamicAttachment(FORTE)
        @Test
        void testAnnotationMatchingForteSymbol() {
            var line = lineWithAnnotation("f");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.FORTE);
        }

        // T46: Note with annotation "pp" → converted to DynamicAttachment(PIANISSIMO)
        @Test
        void testAnnotationMatchingPianissimoSymbol() {
            var line = lineWithAnnotation("pp");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.PIANISSIMO);
        }

        // T47: Note with annotation "forte" (non-symbol text) → not converted, annotation kept
        @Test
        void testAnnotationWithNonSymbolTextNotConverted() {
            var line = lineWithAnnotation("forte");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var note = line.getElement(0);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
            assertThat(note.findAttachment(AnnotationAttachment.class)).isNotNull();
        }

        // T48: Note with annotation "F" (wrong case) → not converted, annotation kept
        @Test
        void testAnnotationWithWrongCaseNotConverted() {
            var line = lineWithAnnotation("F");
            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            var note = line.getElement(0);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
            assertThat(note.findAttachment(AnnotationAttachment.class)).isNotNull();
        }

        // T49: Note with annotation "f" and existing DynamicAttachment →
        //      annotation removed, existing attachment preserved (no duplicate)
        @Test
        void testAnnotationRemovedWhenDynamicAlreadyExists() {
            var line = lineWithAnnotation("f");
            var note = line.getElement(0);
            var existingDynamic = new DynamicAttachment(note, DynamicType.MEZZO_FORTE);
            note.addAttachment(existingDynamic);

            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();

            var dynamic = note.findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isSameAs(existingDynamic);

            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.MEZZO_FORTE);
        }

        // Verify that a matching AnnotationAttachment is removed after conversion
        @Test
        void testMatchingAnnotationAttachmentRemoved() {
            var line = lineWithAnnotation("mf");
            var note = line.getElement(0);

            FormatMigrator.migrateAnnotationDynamics(List.of(line));

            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MigrateFinalTerminal {

        // T52: FINAL_DOUBLE_BARLINE on a non-last line → stripped; last line's own final preserved.
        @Test
        void testFinalBarlineOnNonLastLineIsRemoved() {
            var nonLast = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            var last = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(nonLast, last));

            assertThat(nonLast.elementCount()).isEqualTo(1);
            assertThat(nonLast.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T53: Multiple FINAL_DOUBLE_BARLINE elements on a non-last line → all removed.
        @Test
        void testMultipleFinalBarlinesOnNonLastLineAllRemoved() {
            var nonLast = lineWith(
                ElementType.FINAL_DOUBLE_BARLINE,
                ElementType.CROTCHET,
                ElementType.FINAL_DOUBLE_BARLINE
            );
            var last = lineWith(ElementType.FINAL_DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(nonLast, last));

            assertThat(nonLast.elementCount()).isEqualTo(1);
            assertThat(nonLast.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
        }

        // Interior REPEAT_RIGHT on a non-last line is not stripped.
        @Test
        void testRepeatRightOnNonLastLineIsPreserved() {
            var nonLast = lineWith(ElementType.CROTCHET, ElementType.REPEAT_RIGHT);
            var last = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(nonLast, last));

            assertThat(nonLast.elementCount()).isEqualTo(2);
            assertThat(nonLast.getElement(1).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
        }

        // T54: Last line ends in SINGLE_BARLINE → replaced with FINAL_DOUBLE_BARLINE.
        @Test
        void testSingleBarlineAtEndIsReplaced() {
            var last = lineWith(ElementType.CROTCHET, ElementType.SINGLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T55: Last line ends in DOUBLE_BARLINE → replaced.
        @Test
        void testDoubleBarlineAtEndIsReplaced() {
            var last = lineWith(ElementType.CROTCHET, ElementType.DOUBLE_BARLINE);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.getElement(last.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T56: Last line ends in REPEAT_RIGHT → preserved as valid terminal (no-op).
        @Test
        void testRepeatRightAtEndIsPreservedAsTerminal() {
            var last = lineWith(ElementType.CROTCHET, ElementType.REPEAT_RIGHT);
            var originalRepeat = last.getElement(1);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1)).isSameAs(originalRepeat);
        }

        // Misplaced FINAL_DOUBLE_BARLINE before a REPEAT_RIGHT: the barline is stripped,
        // leaving REPEAT_RIGHT as the terminal, which is a valid terminal (no-op).
        @Test
        void testMisplacedFinalBarlineBeforeRepeatRightLeavesRepeatAsTerminal() {
            var last = lineWith(
                ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE, ElementType.REPEAT_RIGHT);
            var originalRepeat = last.getElement(2);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(last.getElement(1)).isSameAs(originalRepeat);
        }

        // T57: Last line ends in REPEAT_LEFT_RIGHT → replaced.
        @Test
        void testRepeatLeftRightAtEndIsReplaced() {
            var last = lineWith(ElementType.CROTCHET, ElementType.REPEAT_LEFT_RIGHT);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.getElement(last.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T58: Last line ends in REPEAT_LEFT (non-replaceable) → FINAL_DOUBLE_BARLINE appended.
        @Test
        void testRepeatLeftAtEndGetsBarlineAppended() {
            var last = lineWith(ElementType.CROTCHET, ElementType.REPEAT_LEFT);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(3);
            assertThat(last.getElement(2).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T59: Last line ends in a note → FINAL_DOUBLE_BARLINE appended.
        @Test
        void testNoteAtEndGetsFinalBarlineAppended() {
            var last = lineWith(ElementType.CROTCHET);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T60: Last line already ends in FINAL_DOUBLE_BARLINE → no-op.
        @Test
        void testAlreadyEndsInFinalBarlineIsNoOp() {
            var last = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);
            var originalFinal = last.getElement(1);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(1)).isSameAs(originalFinal);
        }

        // T61: Last line is empty → FINAL_DOUBLE_BARLINE appended.
        @Test
        void testEmptyLastLineGetsFinalBarlineAppended() {
            var last = detachedLine();
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(1);
            assertThat(last.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // T62: Misplaced FINAL_DOUBLE_BARLINE (not last element) on last line → removed before install.
        @Test
        void testMisplacedFinalBarlineOnLastLineIsStrippedBeforeInstall() {
            // FINAL in middle of last line — should be stripped, then note ends it → appended.
            var last = lineWith(ElementType.FINAL_DOUBLE_BARLINE, ElementType.CROTCHET);
            FormatMigrator.migrateFinalTerminal(List.of(last));

            assertThat(last.elementCount()).isEqualTo(2);
            assertThat(last.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(last.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }
    }
}
