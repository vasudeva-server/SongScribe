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

import org.jetbrains.annotations.NotNull;

import songscribe.data.Interval;
import songscribe.data.IntervalSet;
import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.BeatChangeAttachment;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.TempoAttachment;
import songscribe.ui.layout.Tie;
import songscribe.ui.layout.Trill;
import songscribe.ui.layout.Tuplet;

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
 */
public final class FormatMigrator {

    private FormatMigrator() {}

    /**
     * Migrates a composition from legacy format to new format.
     * <p>
     * If the composition is already in the new format (version 2+), this method does nothing.
     *
     * @param composition The composition to migrate
     */
    public static void migrate(@NotNull Composition composition) {
        if (composition.getFormatVersion() >= 2) {
            // Already in new format
            return;
        }

        // Migrate each line
        for (var line : composition.getLines()) {
            migrateLine(line);
        }

        // Mark as migrated
        composition.setFormatVersion(2);
    }

    /**
     * Migrates a single line from legacy format to new format.
     *
     * @param line The line to migrate
     */
    private static void migrateLine(@NotNull Line line) {
        // Migrate IntervalSets to RangeElements
        migrateRangeElements(line);

        // Migrate Note attachments
        for (var i = 0; i < line.noteCount(); i++) {
            migrateNoteAttachments(line.getNote(i));
        }
    }

    /**
     * Converts IntervalSet-based ranges to RangeElement objects.
     *
     * @param line The line containing the IntervalSets
     */
    private static void migrateRangeElements(@NotNull Line line) {
        // Convert ties
        migrateIntervalSet(line, line.getTies(), (l, interval) -> {
            var startNote = l.getNote(interval.getStart());
            var endNote = l.getNote(interval.getEnd());

            return new Tie(startNote, endNote);
        });

        // Convert tuplets (with grade from interval data)
        migrateIntervalSet(line, line.getTuplets(), (l, interval) -> {
            var startNote = l.getNote(interval.getStart());
            var endNote = l.getNote(interval.getEnd());
            int grade = extractTupletGrade(interval);

            return new Tuplet(startNote, endNote, grade);
        });

        // Convert first/second endings
        migrateIntervalSet(line, line.getFirstSecondEndings(), (l, interval) -> {
            var startNote = l.getNote(interval.getStart());
            var endNote = l.getNote(interval.getEnd());
            var endingType = extractEndingType(interval);

            return new Ending(startNote, endNote, endingType);
        });

        // Convert crescendos
        migrateIntervalSet(line, line.getCrescendos(), (l, interval) -> {
            var startNote = l.getNote(interval.getStart());
            var endNote = l.getNote(interval.getEnd());

            return new Crescendo(startNote, endNote);
        });

        // Convert diminuendos
        migrateIntervalSet(line, line.getDiminuendos(), (l, interval) -> {
            var startNote = l.getNote(interval.getStart());
            var endNote = l.getNote(interval.getEnd());

            return new Diminuendo(startNote, endNote);
        });

        // Note: slurs are intentionally NOT migrated (being removed)
        // Note: beamings stay as IntervalSet (used for beaming calculations)
    }

    /**
     * Functional interface for creating RangeElements from intervals.
     */
    @FunctionalInterface
    private interface RangeElementFactory {
        RangeElement create(Line line, Interval interval);
    }

    /**
     * Helper method to migrate an IntervalSet to RangeElements.
     */
    private static void migrateIntervalSet(
        @NotNull Line line,
        @NotNull IntervalSet intervalSet,
        @NotNull RangeElementFactory factory
    ) {
        for (var iter = intervalSet.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();

            // Validate interval bounds
            if (interval.getStart() < 0 || interval.getEnd() >= line.noteCount()) {
                continue;
            }

            if (interval.getStart() > interval.getEnd()) {
                continue;
            }

            var element = factory.create(line, interval);
            line.addRangeElement(element);
        }
    }

    /**
     * Extracts tuplet grade from interval data.
     * <p>
     * Interval data format: "3" for triplet, "5" for quintuplet, etc.
     * Defaults to 3 (triplet) if data is null or invalid.
     *
     * @param interval The interval containing tuplet data
     * @return The tuplet grade (3, 5, 6, 7, etc.)
     */
    private static int extractTupletGrade(@NotNull Interval interval) {
        var data = interval.getData();

        if (data == null || data.isEmpty()) {
            return 3; // Default to triplet
        }

        try {
            return Integer.parseInt(data);
        } catch (NumberFormatException e) {
            return 3; // Default to triplet
        }
    }

    /**
     * Extracts ending type from interval data.
     * <p>
     * Interval data format: "1" for first ending, "2" for second ending.
     * Defaults to FIRST if data is null or invalid.
     *
     * @param interval The interval containing ending data
     * @return The ending type
     */
    private static Ending.Type extractEndingType(@NotNull Interval interval) {
        var data = interval.getData();

        if (data == null || data.isEmpty()) {
            return Ending.Type.FIRST;
        }

        if ("2".equals(data)) {
            return Ending.Type.SECOND;
        }

        return Ending.Type.FIRST;
    }

    /**
     * Converts inline Note properties to Attachment objects.
     *
     * @param note The note to migrate
     */
    private static void migrateNoteAttachments(@NotNull Note note) {
        // Tempo change attachment
        if (note.getTempoChange() != null) {
            var attachment = new TempoAttachment(note, note.getTempoChange());
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
            var line = note.getLine();

            if (line != null) {
                line.addRangeElement(trill);
            }
        }

        // Note: ForceArticulation and DurationArticulation are not migrated here
        // as they are handled by the Articulation class system, not Attachments.
        // Dynamic attachments are not present in the legacy Note class.
    }
}
