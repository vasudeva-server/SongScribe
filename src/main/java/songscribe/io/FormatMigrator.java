/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.io;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.PendingHairpin;
import songscribe.dom.PendingTuplet;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;

/**
 * Migrates song data from legacy format (version 1) to new format (version 2).
 *
 * <p>The v2.1 → v2.2 step is a no-op because {@code stemDirectionAuto} defaults to {@code true}.
 */
public final class FormatMigrator {

    private FormatMigrator() {
    }

    /**
     * The caller is responsible for setting the format version on the resulting data.
     */
    static void migrate(List<? extends Line> lines, Map<Line, LegacyLineOffsets> legacyOffsets, int formatVersion) {
        if (formatVersion >= 2) {
            return;
        }

        for (var line : lines) {
            migrateLine(line, legacyOffsets);
        }
    }

    /**
     * @param ctx the parsed song, before assembly, with every value in pixels
     */
    static void migratePixelsToStaffSpace(MigrationContext ctx) {
        var pps = DocumentScale.PIXELS_PER_STAFF_SPACE;

        ctx.lineWidthSs /= pps;
        ctx.rowHeightAdjustmentSs /= pps;

        for (var line : ctx.lines) {
            var spans = line.spanRegistry();

            // Line-level fields
            line.setLyricsYPosSs(line.getLyricsYPosSs() / pps);

            // Per-instance attachment offsets
            for (var i = 0; i < line.elementCount(); i++) {
                var note = line.getElement(i);

                // Convert per-instance attachment offsets created by migrateLineLevelOffsets()
                for (var attachment : note.getAttachments()) {
                    if (attachment.getUserYOffsetSs() != 0) {
                        attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() / pps);
                    }
                }
            }

            // Convert per-instance Span offsets (Trill)
            for (var trill : spans.findSpans(Trill.class)) {
                if (trill.getUserYOffsetSs() != 0) {
                    trill.setUserYOffsetSs(trill.getUserYOffsetSs() / pps);
                }
            }
        }

        ctx.pendingTuplets = ctx.pendingTuplets.stream()
            .map(pending -> new PendingTuplet(
                pending.anchor(), pending.end(), pending.grade(), pending.statedRatio(), pending.yOffsetSs() / pps))
            .toList();

        ctx.pendingHairpins = ctx.pendingHairpins.stream()
            .map(pending -> new PendingHairpin(
                pending.kind(), pending.anchor(), pending.end(), pending.xOffsetSs() / pps, pending.yOffsetSs() / pps))
            .toList();
    }


    private static void migrateLine(Line line, Map<Line, LegacyLineOffsets> legacyOffsets) {
        migrateLineLevelOffsets(line, legacyOffsets.getOrDefault(line, LegacyLineOffsets.DEFAULTS));
    }

    private static void migrateLineLevelOffsets(Line line, LegacyLineOffsets offsets) {
        var spans = line.spanRegistry();

        // Migrate tempo change offset to per-instance. Tempo's default is 0, which also doubles as
        // the "unset" sentinel, so the raw offset already equals the delta from default — unlike the
        // three blocks below, no subtraction is needed.
        var tempoOffset = offsets.tempoChangeYPosPx();

        if (tempoOffset != LegacyLineOffsets.DEFAULTS.tempoChangeYPosPx()) {
            for (var i = 0; i < line.elementCount(); i++) {
                var note = line.getElement(i);
                var tempoAttachment = note.findAttachment(TempoChangeAttachment.class);

                if (tempoAttachment != null) {
                    tempoAttachment.setUserYOffsetSs(tempoAttachment.getUserYOffsetSs() + tempoOffset);
                }
            }
        }

        // Migrate beat change offset to per-instance; BeatChange has a non-zero default
        var beatChangeOffset = offsets.beatChangeYPosPx();
        var beatChangeDefault = LegacyLineOffsets.DEFAULTS.beatChangeYPosPx();

        if (beatChangeOffset != beatChangeDefault) {
            var delta = beatChangeOffset - beatChangeDefault;

            for (var i = 0; i < line.elementCount(); i++) {
                var note = line.getElement(i);

                for (var attachment : note.getAttachments()) {
                    if (attachment instanceof BeatChangeAttachment) {
                        attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() + delta);
                    }
                }
            }
        }

        // Migrate trill offset to per-instance
        var trillOffset = offsets.trillYPosPx();
        var trillDefault = LegacyLineOffsets.DEFAULTS.trillYPosPx();

        if (trillOffset != trillDefault) {
            var delta = trillOffset - trillDefault;

            for (var trill : spans.findSpans(Trill.class)) {
                trill.setUserYOffsetSs(trill.getUserYOffsetSs() + delta);
            }
        }
    }

    public static void migrateAnnotationDynamics(List<? extends Line> lines) {
        var symbolMap = buildDynamicSymbolMap();

        for (var line : lines) {
            for (var i = 0; i < line.elementCount(); i++) {
                migrateAnnotationDynamic(line.getElement(i), symbolMap);
            }
        }
    }

    private static Map<String, DynamicAttachment.DynamicType> buildDynamicSymbolMap() {
        var map = new HashMap<String, DynamicAttachment.DynamicType>();

        for (var type : DynamicAttachment.DynamicType.values()) {
            if (type.getGlyph() != null) {
                map.put(type.getSymbol(), type);
            }
        }

        return map;
    }

    private static void migrateAnnotationDynamic(
        StaffElement note,
        Map<String, DynamicAttachment.DynamicType> symbolMap
    ) {
        var annotationAttachment = note.findAttachment(AnnotationAttachment.class);

        if (annotationAttachment == null) {
            return;
        }

        var annotation = annotationAttachment.getAnnotation();
        var dynamicType = symbolMap.get(annotation.text());

        if (dynamicType == null) {
            return;
        }

        // Remove the AnnotationAttachment since it is being promoted to a DynamicAttachment.
        note.removeAttachment(annotationAttachment);

        // Only add a DynamicAttachment if one does not already exist.
        if (note.findAttachment(DynamicAttachment.class) == null) {
            note.addAttachment(new DynamicAttachment(dynamicType));
        }
    }

    /**
     * @param lines the lines to migrate (not yet attached to a {@code Song})
     */
    public static void migrateFinalTerminal(List<? extends Line> lines) {
        if (lines.isEmpty()) {
            return;
        }

        for (var i = 0; i < lines.size() - 1; i++) {
            stripFinalBarlines(lines.get(i));
        }

        var lastLine = lines.getLast();
        stripNonTerminalFinalBarlines(lastLine);

        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx < 0) {
            lastLine.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());
            return;
        }

        var lastType = lastLine.getElement(lastIdx).getType();

        if (lastType.isValidSongTerminal()) {
            return;
        }

        if (lastType.isReplaceableByTerminal()) {
            lastLine.setElement(lastIdx, ElementType.FINAL_DOUBLE_BARLINE.newInstance());
        } else {
            lastLine.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());
        }
    }

    private static void stripFinalBarlines(Line line) {
        for (var i = line.elementCount() - 1; i >= 0; i--) {
            if (line.getElement(i).getType() == ElementType.FINAL_DOUBLE_BARLINE) {
                line.removeElement(i);
            }
        }
    }

    private static void stripNonTerminalFinalBarlines(Line line) {
        for (var i = line.elementCount() - 2; i >= 0; i--) {
            if (line.getElement(i).getType() == ElementType.FINAL_DOUBLE_BARLINE) {
                line.removeElement(i);
            }
        }
    }
}
