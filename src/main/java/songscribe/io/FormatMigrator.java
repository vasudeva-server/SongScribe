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


import songscribe.music.DynamicsSpan;
import songscribe.music.ElementType;
import songscribe.music.Span;
import songscribe.music.SpanSet;
import songscribe.music.TupletSpan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.BeatChangeAttachment;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.TempoChangeAttachment;
import songscribe.ui.layout.Tie;
import songscribe.ui.layout.Trill;
import songscribe.ui.layout.Tuplet;
import songscribe.ui.layout.ScaleContext;

/**
 * Migrates composition data from legacy format (version 1) to new format (version 2).
 * <p>
 * Legacy format stores:
 * <ul>
 *   <li>Range data in IntervalSets (ties, tuplets, crescendo, etc.)</li>
 *   <li>Note attachments as inline properties (tempoChange, fermata, etc.)</li>
 * </ul>
 * <p>
 * New format stores:
 * <ul>
 *   <li>Range data as RangeElement objects in Line.rangeElements</li>
 *   <li>Note attachments as Attachment objects in Note.attachments</li>
 * </ul>
 * <p>
 * This migrator is called after loading a composition to populate the new data structures.
 * The legacy structures are preserved for backward compatibility during the transition period.
 *
 * <h2>Migration history</h2>
 * <ul>
 *   <li><b>v1 → v2</b>: {@link #migrate} — SpanSets converted to RangeElements;
 *       inline Note properties converted to Attachment objects.</li>
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
     * @param formatVersion The current format version (migration is skipped if >= 2)
     */
    public static void migrate(List<Line> lines, int formatVersion) {
        if (formatVersion >= 2) {
            return;
        }

        for (var line : lines) {
            migrateLine(line);
        }
    }

    /**
     * Converts pixel-based line-level position values to staff-space units.
     * <p>
     * Called when loading a pre-v2.1 file (which stores positions in pixels).
     * Composition-level pixel-to-ss conversion is handled by the caller.
     * <p>
     * Fields that are already unit-agnostic (e.g. Note.staffPosition, which is a
     * diatonic step count) are NOT converted.
     *
     * @param lines The lines with pixel values to convert
     */
    public static void migratePixelsToStaffSpace(List<Line> lines) {
        var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;

        for (var lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            var line = lines.get(lineIdx);

            // Line-level fields
            line.setLyricsYPosSs(line.getLyricsYPosSs() / pps);

            // TupletSpan.verticalPosition
            for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
                var tuplet = iter.next();

                if (tuplet.isVerticallyAdjusted()) {
                    tuplet.setVerticalPositionSs(tuplet.getVerticalPositionSs() / pps);
                }
            }

            // DynamicsSpan shifts (crescendo + diminuendo)
            migrateDynamicsSpans(line.getCrescendos(), pps);
            migrateDynamicsSpans(line.getDiminuendos(), pps);

            // Glissando translates and per-instance attachment offsets
            for (var i = 0; i < line.elementCount(); i++) {
                var note = line.getElement(i);

                if (note.getGlissando() != null) {
                    note.getGlissando().x1Translate /= pps;
                    note.getGlissando().x2Translate /= pps;
                }

                // Convert per-instance attachment offsets created by migrateLineLevelOffsets()
                for (var attachment : note.getAttachments()) {
                    if (attachment.getUserYOffsetSs() != 0) {
                        attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() / pps);
                    }
                }

                // Reset stale layout pixel xPos — layout now writes to LayoutResult, not Note
                note.setXOffsetPx(0);
            }

            // Convert per-instance RangeElement offsets (Ending, Trill)
            for (var element : line.getRangeElements()) {
                if (element instanceof Ending ending && ending.getYPositionSs() != 0) {
                    ending.setYPositionSs((int) Math.round(ending.getYPositionSs() / pps));
                } else if (element instanceof Trill trill && trill.getYPositionSs() != 0) {
                    trill.setYPositionSs((int) Math.round(trill.getYPositionSs() / pps));
                }
            }
        }
    }

    private static void migrateDynamicsSpans(
        SpanSet<DynamicsSpan> spanSet,
        double pps
    ) {
        for (var iter = spanSet.listIterator(); iter.hasNext(); ) {
            var span = iter.next();

            if (span.getX1ShiftSs() != 0 || span.getX2ShiftSs() != 0 || span.getYShiftSs() != 0) {
                span.setX1ShiftSs(span.getX1ShiftSs() / pps);
                span.setX2ShiftSs(span.getX2ShiftSs() / pps);
                span.setYShiftSs(span.getYShiftSs() / pps);
            }
        }
    }

    /**
     * Migrates a single line from legacy format to new format.
     *
     * @param line The line to migrate
     */
    private static void migrateLine(Line line) {
        // Migrate SpanSets to RangeElements
        migrateRangeElements(line);

        // Migrate Note attachments
        for (var i = 0; i < line.elementCount(); i++) {
            migrateElementAttachments(line.getElement(i));
        }

        // Migrate line-level Y offsets to per-instance offsets (Phase 11)
        migrateLineLevelOffsets(line);
    }

    /**
     * Migrates deprecated line-level Y position offsets to per-instance offsets.
     * <p>
     * This converts the legacy line-level fields (tempoChangeYPosPx, beatChangeYPosPx,
     * firstSecondEndingYPosPx, trillYPosPx) to per-instance offsets on the respective
     * element objects. After migration, the line-level fields can be ignored.
     * <p>
     * Also migrates below-staff annotations to above-staff with an appropriate userYOffset.
     *
     * @param line The line to migrate
     */
    @SuppressWarnings("deprecation")
    private static void migrateLineLevelOffsets(Line line) {
        // Migrate tempo change offset to per-instance
        int tempoOffset = line.getTempoChangeYPosPx();

        if (tempoOffset != 0) {
            for (var i = 0; i < line.elementCount(); i++) {
                var note = line.getElement(i);

                if (note.getTempoChange() != null) {
                    // Find the TempoChangeAttachment and add the line-level offset to its userYOffset
                    for (var attachment : note.getAttachments()) {
                        if (attachment instanceof TempoChangeAttachment) {
                            attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() + tempoOffset);
                        }
                    }
                }
            }
        }

        // Migrate beat change offset to per-instance
        int beatChangeOffset = line.getBeatChangeYPosPx();

        // BeatChange has a default offset, only migrate if different
        if (beatChangeOffset != ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.BEAT_CHANGE_DEFAULT_Y_SS)) {
            int delta = beatChangeOffset - ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.BEAT_CHANGE_DEFAULT_Y_SS);

            for (var i = 0; i < line.elementCount(); i++) {
                var note = line.getElement(i);

                if (note.getBeatChange() != null) {
                    for (var attachment : note.getAttachments()) {
                        if (attachment instanceof BeatChangeAttachment) {
                            attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() + delta);
                        }
                    }
                }
            }
        }

        // Migrate first/second ending offset to per-instance
        int endingOffset = line.getFirstSecondEndingYPosPx();

        if (endingOffset != ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.ENDING_DEFAULT_Y_SS)) {
            int delta = endingOffset - ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.ENDING_DEFAULT_Y_SS);

            for (var element : line.getRangeElements()) {
                if (element instanceof Ending ending) {
                    ending.setYPositionSs(ending.getYPositionSs() + delta);
                }
            }
        }

        // Migrate trill offset to per-instance
        int trillOffset = line.getTrillYPosPx();

        if (trillOffset != ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TRILL_DEFAULT_Y_SS)) {
            int delta = trillOffset - ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.TRILL_DEFAULT_Y_SS);

            for (var element : line.getRangeElements()) {
                if (element instanceof Trill trill) {
                    trill.setYPositionSs(trill.getYPositionSs() + delta);
                }
            }
        }

        // Migrate below-staff annotations to above-staff with userYOffset
        migrateAnnotationPositions(line);
    }

    /**
     * Migrates below-staff annotations to above-staff positioning.
     * <p>
     * Legacy documents may have annotations positioned below the staff using
     * the BELOW constant. The new layout system always positions annotations
     * above the staff, so we migrate below-staff annotations by:
     * 1. Setting yPosPx to ABOVE (the new default)
     * 2. Adding a userYOffset to preserve the visual position
     *
     * @param line The line containing annotations to migrate
     */
    private static void migrateAnnotationPositions(Line line) {
        for (var i = 0; i < line.elementCount(); i++) {
            var annotation = line.getElement(i).getAnnotation();

            if (annotation == null) {
                continue;
            }

            // Check if annotation is below staff (legacy positioning)
            int yPosPx = annotation.getYPosPx();

            if (yPosPx > 0) {
                // Below-staff annotation: convert to above-staff with offset
                // The visual position difference is: BELOW - ABOVE = yPosPx - ABOVE
                double offset = yPosPx - songscribe.music.Annotation.ABOVE;
                annotation.setUserYOffsetSs(annotation.getUserYOffsetSs() + offset);
                annotation.setYPosPx(songscribe.music.Annotation.ABOVE);
            }
        }
    }

    /**
     * Converts SpanSet-based ranges to RangeElement objects.
     *
     * @param line The line containing the SpanSets
     */
    private static void migrateRangeElements(Line line) {
        // Convert ties
        migrateSpanSet(line, line.getTies(), (l, span) -> {
            var startElement = l.getElement(span.getStart());
            var endElement = l.getElement(span.getEnd());

            return new Tie(startElement, endElement);
        });

        // Convert tuplets (with grade from span data)
        migrateSpanSet(line, line.getTuplets(), (l, span) -> {
            var startElement = l.getElement(span.getStart());
            var endElement = l.getElement(span.getEnd());
            int grade = extractTupletGrade((TupletSpan) span);

            return new Tuplet(startElement, endElement, grade);
        });

        // Convert crescendos
        migrateSpanSet(line, line.getCrescendos(), (l, span) -> {
            var startElement = l.getElement(span.getStart());
            var endElement = l.getElement(span.getEnd());

            return new Crescendo(startElement, endElement);
        });

        // Convert diminuendos
        migrateSpanSet(line, line.getDiminuendos(), (l, span) -> {
            var startElement = l.getElement(span.getStart());
            var endElement = l.getElement(span.getEnd());

            return new Diminuendo(startElement, endElement);
        });

        // Note: slurs are intentionally NOT migrated (being removed)
        // Note: beamings stay as SpanSet (used for beaming calculations)
    }

    /**
     * Functional interface for creating RangeElements from spans.
     */
    @FunctionalInterface
    private interface RangeElementFactory {
        RangeElement create(Line line, Span span);
    }

    /**
     * Helper method to migrate a SpanSet to RangeElements.
     */
    @SuppressWarnings("rawtypes")
    private static void migrateSpanSet(
        Line line,
        SpanSet spanSet,
        RangeElementFactory factory
    ) {
        for (var iter = spanSet.listIterator(); iter.hasNext(); ) {
            var span = (Span) iter.next();

            // Validate span bounds
            if (span.getStart() < 0 || span.getEnd() >= line.elementCount()) {
                continue;
            }

            if (span.getStart() > span.getEnd()) {
                continue;
            }

            var element = factory.create(line, span);
            line.addRangeElement(element);
        }
    }

    /**
     * Extracts tuplet grade from span data.
     * <p>
     * Span data format: "3" for triplet, "5" for quintuplet, etc.
     * Defaults to 3 (triplet) if data is null or invalid.
     *
     * @param span The span containing tuplet data
     * @return The tuplet grade (3, 5, 6, 7, etc.)
     */
    private static int extractTupletGrade(TupletSpan span) {
        return span.getGrade();
    }

    /**
     * Converts inline Note properties to Attachment objects.
     *
     * @param note The note to migrate
     */
    private static void migrateElementAttachments(StaffElement note) {
        // Tempo change attachment
        if (note.getTempoChange() != null) {
            var attachment = new TempoChangeAttachment(note, note.getTempoChange());
            note.addAttachment(attachment);
        }

        // Fermata attachment
        if (note.isFermata()) {
            var attachment = new FermataAttachment(note);
            note.addAttachment(attachment);
        }

        // Annotation attachment
        if (note.getAnnotation() != null) {
            var attachment = new AnnotationAttachment(note, note.getAnnotation());
            note.addAttachment(attachment);
        }

        // Beat change attachment
        if (note.getBeatChange() != null) {
            var attachment = new BeatChangeAttachment(note, note.getBeatChange());
            note.addAttachment(attachment);
        }

        // Single-note trill (converted to Trill RangeElement)
        // Note: Trills are RangeElements, not Attachments, but single-note trills
        // are stored on the Note. We add them to the Line's rangeElements.
        if (note.isTrill()) {
            var trill = new Trill(note);
            note.getLine().addRangeElement(trill);
        }

        // Note: ForceArticulation and DurationArticulation are not migrated here
        // as they are handled by the Articulation class system, not Attachments.
        // Dynamic attachments are not present in the legacy Note class.
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
    public static void migrateAnnotationDynamics(List<Line> lines) {
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
        var annotation = note.getAnnotation();

        if (annotation == null) {
            return;
        }

        var dynamicType = symbolMap.get(annotation.getAnnotation());

        if (dynamicType == null) {
            return;
        }

        // Remove the annotation (legacy field and any AnnotationAttachment created
        // by v1→v2 migration for the same annotation object).
        note.setAnnotation(null);
        var annotationAttachment = note.findAttachment(AnnotationAttachment.class);

        if (annotationAttachment != null) {
            note.removeAttachment(annotationAttachment);
        }

        // Only add a DynamicAttachment if one does not already exist.
        if (note.findAttachment(DynamicAttachment.class) == null) {
            note.addAttachment(new DynamicAttachment(note, dynamicType));
        }
    }

    /**
     * Enforces the terminal invariant on every line in the composition.
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
     * @param lines the lines to migrate (not yet attached to a {@code Composition})
     */
    public static void migrateFinalTerminal(List<Line> lines) {
        if (lines.isEmpty()) {
            return;
        }

        for (var i = 0; i < lines.size() - 1; i++) {
            stripFinalBarlines(lines.get(i));
        }

        var lastLine = lines.get(lines.size() - 1);
        stripNonTerminalFinalBarlines(lastLine);

        var lastIdx = lastLine.elementCount() - 1;

        if (lastIdx < 0) {
            lastLine.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());
            return;
        }

        var lastType = lastLine.getElement(lastIdx).getType();

        if (lastType.isValidTerminal()) {
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
