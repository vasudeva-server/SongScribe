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
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

/**
 * Migrates song data from legacy format (version 1) to new format (version 2).
 * <p>
 * Legacy format stores range data as inline properties (ties, tuplets, crescendo, etc.);
 * the new format stores it as Span objects in Line.spans. Note attachments
 * (tempoChange, fermata, etc.) are read directly into Attachment objects by StaffElementIO
 * and need no separate migration step.
 *
 * <h2>Migration history</h2>
 * <ul>
 *   <li><b>v1 → v2</b>: {@link #migrate} — legacy range data converted to Span
 *       objects.</li>
 *   <li><b>v2.0 → v2.1</b>: {@link #migratePixelsToStaffSpace} — pixel-based position
 *       fields converted to staff-space units.</li>
 *   <li><b>v2.1 → v2.2</b>: <em>Intentional no-op.</em> {@code stemDirectionAuto}
 *       defaults to {@code true}, so absence of the {@code <stemDirectionAuto/>} tag in
 *       existing v2.1 files is handled correctly by that default. Re-saving a v2.1 file
 *       stamps it as v2.2.</li>
 *   <li><b>v2.2 → v2.3</b>: {@link #migrateAnnotationDynamics} — text annotations
 *       whose text exactly matches a point dynamic symbol ({@code pp}, {@code p},
 *       {@code mp}, {@code mf}, {@code f}, {@code ff}) are converted to
 *       {@code DynamicAttachment} objects.</li>
 *   <li><b>v2.3 → v2.4</b>: {@link #migrateFinalTerminal} — enforces the terminal
 *       invariant: the last line ends in a valid terminal ({@code FINAL_DOUBLE_BARLINE} or
 *       {@code REPEAT_RIGHT}); all misplaced {@code FINAL_DOUBLE_BARLINE} elements on
 *       non-last lines or in non-terminal positions are removed.</li>
 * </ul>
 */
public final class FormatMigrator {

    private FormatMigrator() {
    }

    /**
     * Migrates lines from legacy format to new format.
     * <p>
     * If the format version is already 2+, this method does nothing.
     * The caller is responsible for setting the format version on the resulting data.
     *
     * @param lines The lines to migrate
     * @param legacyOffsets Per-line Y position offsets read from legacy XML, keyed by line identity.
     *                      Lines absent from the map use {@link LegacyLineOffsets#DEFAULTS}.
     * @param formatVersion The current format version (migration is skipped if >= 2)
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
     * Converts pixel-based line-level position values to staff-space units.
     * <p>
     * Called when loading a pre-v2.1 file (which stores positions in pixels).
     * Song-level pixel-to-ss conversion is handled by the caller.
     * <p>
     * Fields that are already unit-agnostic (e.g. Note.staffPosition, which is a
     * diatonic step count) are NOT converted.
     *
     * @param lines The lines with pixel values to convert
     */
    public static void migratePixelsToStaffSpace(List<? extends Line> lines) {
        var pps = DocumentScale.PIXELS_PER_STAFF_SPACE;

        for (var line : lines) {
            // Line-level fields
            line.setLyricsYPosSs(line.getLyricsYPosSs() / pps);

            // Tuplet.verticalPosition (pixel → staff-space conversion)
            for (var tuplet : line.findSpans(Tuplet.class)) {
                if (tuplet.getVerticalPositionSs() != 0) {
                    tuplet.setVerticalPositionSs(
                        (int) Math.round(tuplet.getVerticalPositionSs() / pps)
                    );
                }
            }

            // Hairpin shifts (crescendo + diminuendo)
            for (var hairpin : line.findSpans(Hairpin.class)) {
                if (hairpin.getX1ShiftSs() != 0 || hairpin.getX2ShiftSs() != 0 || hairpin.getYShiftSs() != 0) {
                    hairpin.setX1ShiftSs(hairpin.getX1ShiftSs() / pps);
                    hairpin.setX2ShiftSs(hairpin.getX2ShiftSs() / pps);
                    hairpin.setYShiftSs(hairpin.getYShiftSs() / pps);
                }
            }

            // Per-instance attachment offsets
            for (var i = 0; i < line.elementCount(); i++) {
                var note = line.getElement(i);

                // Convert per-instance attachment offsets created by migrateLineLevelOffsets()
                for (var attachment : note.getAttachments()) {
                    if (attachment.getUserYOffsetSs() != 0) {
                        attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() / pps);
                    }
                }

                // Reset stale layout pixel xPos — layout now writes to LayoutResult, not Note
                note.setXOffsetPx(0);
            }

            // Convert per-instance Span offsets (Ending, Trill)
            for (var ending : line.findSpans(Ending.class)) {
                if (ending.getYPositionSs() != 0) {
                    ending.setYPositionSs((int) Math.round(ending.getYPositionSs() / pps));
                }
            }

            for (var trill : line.findSpans(Trill.class)) {
                if (trill.getYPositionSs() != 0) {
                    trill.setYPositionSs((int) Math.round(trill.getYPositionSs() / pps));
                }
            }
        }
    }


    /**
     * Migrates a single line from legacy format to new format.
     *
     * @param line The line to migrate
     * @param legacyOffsets Per-line Y position offsets keyed by line identity.
     */
    private static void migrateLine(Line line, Map<Line, LegacyLineOffsets> legacyOffsets) {
        migrateLineLevelOffsets(line, legacyOffsets.getOrDefault(line, LegacyLineOffsets.DEFAULTS));
    }

    /**
     * Migrates deprecated line-level Y position offsets to per-instance offsets.
     * <p>
     * Converts the four legacy offsets carried by {@code offsets} to per-instance values on the
     * respective element/attachment objects. Also migrates below-staff annotations to above-staff
     * with an appropriate userYOffset.
     *
     * @param line    The line whose elements are updated
     * @param offsets The legacy Y position values read from the pre-Phase 11 XML for this line
     */
    private static void migrateLineLevelOffsets(Line line, LegacyLineOffsets offsets) {
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

        // Migrate first/second ending offset to per-instance
        var endingOffset = offsets.firstSecondEndingYPosPx();
        var endingDefault = LegacyLineOffsets.DEFAULTS.firstSecondEndingYPosPx();

        if (endingOffset != endingDefault) {
            var delta = endingOffset - endingDefault;

            for (var ending : line.findSpans(Ending.class)) {
                ending.setYPositionSs(ending.getYPositionSs() + delta);
            }
        }

        // Migrate trill offset to per-instance
        var trillOffset = offsets.trillYPosPx();
        var trillDefault = LegacyLineOffsets.DEFAULTS.trillYPosPx();

        if (trillOffset != trillDefault) {
            var delta = trillOffset - trillDefault;

            for (var trill : line.findSpans(Trill.class)) {
                trill.setYPositionSs(trill.getYPositionSs() + delta);
            }
        }
    }

    /**
     * Converts text annotations that exactly match a point dynamic symbol into
     * {@link DynamicAttachment} objects.
     * <p>
     * Called when loading files saved before v2.3 introduced native dynamic
     * serialization. Annotations whose text is one of {@code pp}, {@code p},
     * {@code mp}, {@code mf}, {@code f}, or {@code ff} (case-sensitive) are
     * replaced with the corresponding {@code DynamicAttachment}. If the note
     * already has a {@code DynamicAttachment}, the annotation is removed but
     * no duplicate is added.
     *
     * @param lines The lines to migrate
     */
    public static void migrateAnnotationDynamics(List<? extends Line> lines) {
        var symbolMap = buildDynamicSymbolMap();

        for (var line : lines) {
            for (var i = 0; i < line.elementCount(); i++) {
                migrateAnnotationDynamic(line.getElement(i), symbolMap);
            }
        }
    }

    /**
     * Builds a map from dynamic symbol string to {@link DynamicAttachment.DynamicType}
     * for the 6 UI types (those with a glyph).
     */
    private static Map<String, DynamicAttachment.DynamicType> buildDynamicSymbolMap() {
        var map = new HashMap<String, DynamicAttachment.DynamicType>();

        for (var type : DynamicAttachment.DynamicType.values()) {
            if (type.getGlyph() != null) {
                map.put(type.getSymbol(), type);
            }
        }

        return map;
    }

    /**
     * Migrates a single note's annotation to a {@link DynamicAttachment} if the
     * annotation text matches a dynamic symbol.
     */
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
            note.addAttachment(new DynamicAttachment(note, dynamicType));
        }
    }

    /**
     * Enforces the terminal invariant on every line in the song.
     * <p>
     * Called when loading any file saved before v2.4. The decision tree for the last line:
     * <ul>
     *   <li>Valid terminal last element ({@code FINAL_DOUBLE_BARLINE} or
     *       {@code REPEAT_RIGHT}) — no-op (both are conformant terminals).</li>
     *   <li>Replaceable ending barline ({@code SINGLE_BARLINE}, {@code DOUBLE_BARLINE},
     *       {@code REPEAT_LEFT_RIGHT}) — replaced with {@code FINAL_DOUBLE_BARLINE}.</li>
     *   <li>Any other element, or empty line — {@code FINAL_DOUBLE_BARLINE} appended.</li>
     * </ul>
     * All {@code FINAL_DOUBLE_BARLINE} elements on non-last lines, and any
     * {@code FINAL_DOUBLE_BARLINE} not in the terminal position of the last line, are
     * removed before applying the decision tree. Interior {@code REPEAT_RIGHT} elements
     * on non-last lines are left untouched.
     *
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

    /** Removes all {@code FINAL_DOUBLE_BARLINE} elements from {@code line} in reverse order. */
    private static void stripFinalBarlines(Line line) {
        for (var i = line.elementCount() - 1; i >= 0; i--) {
            if (line.getElement(i).getType() == ElementType.FINAL_DOUBLE_BARLINE) {
                line.removeElement(i);
            }
        }
    }

    /** Removes {@code FINAL_DOUBLE_BARLINE} elements that are not the last element of {@code line}. */
    private static void stripNonTerminalFinalBarlines(Line line) {
        for (var i = line.elementCount() - 2; i >= 0; i--) {
            if (line.getElement(i).getType() == ElementType.FINAL_DOUBLE_BARLINE) {
                line.removeElement(i);
            }
        }
    }
}
