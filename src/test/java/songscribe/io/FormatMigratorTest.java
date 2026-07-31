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
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
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
            var offsets = Map.of(line, offsetsWithTempo(NON_ZERO_TEMPO_OFFSET_PX));

            FormatMigrator.migrate(List.of(line), offsets, FORMAT_VERSION_AT_THRESHOLD);

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
            var offsets = Map.of(line, offsetsWithTempo(NON_ZERO_TEMPO_OFFSET_PX));

            FormatMigrator.migrate(List.of(line), offsets, FORMAT_VERSION_LEGACY);

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

        // A multi-line migrate() must apply each line's own map entry and fall back to DEFAULTS
        // for a line absent from the map — guards the per-line getOrDefault identity keying.
        @Test
        void testAppliesPerLineOffsetsByIdentity() {
            var lineWithOffset = lineWith(ElementType.CROTCHET);
            lineWithOffset.getElement(0).addAttachment(new TempoChangeAttachment(new Tempo()));

            var lineWithoutOffset = lineWith(ElementType.CROTCHET);
            lineWithoutOffset.getElement(0).addAttachment(new TempoChangeAttachment(new Tempo()));

            // Only the first line carries a tempo offset; the second is absent from the map.
            var offsets = Map.of(lineWithOffset, offsetsWithTempo(NON_ZERO_TEMPO_OFFSET_PX));

            FormatMigrator.migrate(
                List.of(lineWithOffset, lineWithoutOffset), offsets, FORMAT_VERSION_LEGACY);

            var migrated = lineWithOffset.getElement(0).findAttachment(TempoChangeAttachment.class);
            var untouched = lineWithoutOffset.getElement(0).findAttachment(TempoChangeAttachment.class);
            assertThat(migrated).isNotNull();
            assertThat(untouched).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (migrated == null || untouched == null) {
                return;
            }

            // The mapped line gets its offset; the unmapped line falls back to DEFAULTS (tempo 0 → no-op).
            assertThat(migrated.getUserYOffsetSs()).isEqualTo(NON_ZERO_TEMPO_OFFSET_PX);
            assertThat(untouched.getUserYOffsetSs()).isEqualTo(0.0);
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
            var offsets = Map.of(line, offsetsWithTempo(NON_ZERO_TEMPO_OFFSET_PX));

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), offsets, FORMAT_VERSION_LEGACY);

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
            var nonDefaultBeatChangePx = ScaleContext.ssToRoundedPx(Line.BEAT_CHANGE_DEFAULT_Y_SS) + BEAT_CHANGE_OFFSET_DELTA_PX;
            var offsets = Map.of(line, offsetsWithBeatChange(nonDefaultBeatChangePx));

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), offsets, FORMAT_VERSION_LEGACY);

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

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            // Empty map → DEFAULTS used → beat-change offset equals default → no delta applied.
            FormatMigrator.migrate(List.of(line), Map.of(), FORMAT_VERSION_LEGACY);

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
            var ending = new Ending(note, note);
            line.addRangeElement(ending);

            var nonDefaultEndingPx = ScaleContext.ssToRoundedPx(Line.ENDING_DEFAULT_Y_SS) + ENDING_OFFSET_DELTA_PX;
            var offsets = Map.of(line, offsetsWithEnding(nonDefaultEndingPx));

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), offsets, FORMAT_VERSION_LEGACY);

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

            var nonDefaultTrillPx = ScaleContext.ssToRoundedPx(Line.TRILL_DEFAULT_Y_SS) + TRILL_OFFSET_DELTA_PX;
            var offsets = Map.of(line, offsetsWithTrill(nonDefaultTrillPx));

            // Route through the public API — migrateLineLevelOffsets is called for every legacy line.
            FormatMigrator.migrate(List.of(line), offsets, FORMAT_VERSION_LEGACY);

            // delta = nonDefault - default = TRILL_OFFSET_DELTA_PX; initial yPositionSs is 0.
            assertThat(trill.getYPositionSs()).isEqualTo(TRILL_OFFSET_DELTA_PX);
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
            var tuplet = Tuplet.withUnresolvedRatio(anchor, end, TUPLET_GRADE);
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
            var tuplet = Tuplet.withUnresolvedRatio(anchor, end, TUPLET_GRADE);
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


        // Row 17: Note with a non-zero attachment userYOffsetSs → divided by pps.
        @Test
        void testAttachmentUserYOffsetSsDividedByPps() {
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            var attachment = new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE);
            attachment.setUserYOffsetSs(NON_ZERO_ATTACHMENT_USER_Y_OFFSET_SS);
            note.addAttachment(attachment);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            assertThat(attachment.getUserYOffsetSs())
                .isEqualTo(NON_ZERO_ATTACHMENT_USER_Y_OFFSET_SS / pps);
        }

        // Row 18: Note with non-zero xOffsetPx → reset to 0 unconditionally.
        @Test
        void testNoteXOffsetPxResetToZero() {
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            note.setXOffsetPx(NON_ZERO_NOTE_X_OFFSET_PX);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            assertThat(note.getXOffsetPx()).isEqualTo(0);
        }

        // Row 19a: Ending with non-zero yPositionSs → divided by pps (rounded).
        @Test
        void testEndingYPositionSsDividedByPps() {
            var line = detachedLine();
            var anchor = ElementType.CROTCHET.newInstance();
            var end = ElementType.CROTCHET.newInstance();
            line.addElement(anchor);
            line.addElement(end);
            var ending = new Ending(anchor, end);
            ending.setYPositionSs(NON_ZERO_ENDING_Y_POSITION_SS);
            line.addRangeElement(ending);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            var expected = (int) Math.round(
                NON_ZERO_ENDING_Y_POSITION_SS / ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE
            );
            assertThat(ending.getYPositionSs()).isEqualTo(expected);
        }

        // Row 19b: Trill with non-zero yPositionSs → divided by pps (rounded).
        @Test
        void testTrillYPositionSsDividedByPps() {
            var line = detachedLine();
            var anchor = ElementType.CROTCHET.newInstance();
            line.addElement(anchor);
            var trill = new Trill(anchor);
            trill.setYPositionSs(NON_ZERO_TRILL_Y_POSITION_SS);
            line.addRangeElement(trill);

            FormatMigrator.migratePixelsToStaffSpace(List.of(line));

            var expected = (int) Math.round(
                NON_ZERO_TRILL_Y_POSITION_SS / ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE
            );
            assertThat(trill.getYPositionSs()).isEqualTo(expected);
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
     * Non-zero attachment userYOffsetSs (in pixels pre-migration) used to verify
     * division by pps in migratePixelsToStaffSpace. Must differ from 0 to enter the branch.
     */
    private static final double NON_ZERO_ATTACHMENT_USER_Y_OFFSET_SS = 16.0;

    /**
     * Non-zero xOffsetPx on a note used to verify unconditional reset to 0 in
     * migratePixelsToStaffSpace.
     */
    private static final int NON_ZERO_NOTE_X_OFFSET_PX = 8;

    /**
     * Non-zero Ending yPositionSs (in pixels pre-migration) used to verify rounding
     * division by pps. Must differ from 0 to enter the Ending branch.
     */
    private static final int NON_ZERO_ENDING_Y_POSITION_SS = 24;

    /**
     * Non-zero Trill yPositionSs (in pixels pre-migration) used to verify rounding
     * division by pps. Must differ from 0 to enter the Trill branch.
     */
    private static final int NON_ZERO_TRILL_Y_POSITION_SS = 24;

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

    /** A LegacyLineOffsets equal to DEFAULTS except for the tempo field. */
    private static LegacyLineOffsets offsetsWithTempo(int tempoChangeYPosPx) {
        var defaults = LegacyLineOffsets.DEFAULTS;
        return new LegacyLineOffsets(
            tempoChangeYPosPx, defaults.beatChangeYPosPx(), defaults.firstSecondEndingYPosPx(), defaults.trillYPosPx());
    }

    /** A LegacyLineOffsets equal to DEFAULTS except for the beat-change field. */
    private static LegacyLineOffsets offsetsWithBeatChange(int beatChangeYPosPx) {
        var defaults = LegacyLineOffsets.DEFAULTS;
        return new LegacyLineOffsets(
            defaults.tempoChangeYPosPx(), beatChangeYPosPx, defaults.firstSecondEndingYPosPx(), defaults.trillYPosPx());
    }

    /** A LegacyLineOffsets equal to DEFAULTS except for the first/second ending field. */
    private static LegacyLineOffsets offsetsWithEnding(int firstSecondEndingYPosPx) {
        var defaults = LegacyLineOffsets.DEFAULTS;
        return new LegacyLineOffsets(
            defaults.tempoChangeYPosPx(), defaults.beatChangeYPosPx(), firstSecondEndingYPosPx, defaults.trillYPosPx());
    }

    /** A LegacyLineOffsets equal to DEFAULTS except for the trill field. */
    private static LegacyLineOffsets offsetsWithTrill(int trillYPosPx) {
        var defaults = LegacyLineOffsets.DEFAULTS;
        return new LegacyLineOffsets(
            defaults.tempoChangeYPosPx(), defaults.beatChangeYPosPx(), defaults.firstSecondEndingYPosPx(), trillYPosPx);
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
