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

package songscribe.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;

/**
 * Coordinates beaming for a group of notes.
 * <p>
 * BeamGroup references notes but doesn't contain them - notes remain direct
 * children of Line. Notes query their BeamGroup for adjusted stem lengths
 * during rendering, and the BeamGroupRenderer draws beam bars after notes
 * are rendered.
 * <p>
 * This follows Option B from the design doc: BeamGroup as a coordinator
 * rather than a container.
 */
public class BeamGroup extends LineElement {

    private final List<Note> beamedNotes = new ArrayList<>();
    private double beamAngle = 0.0;
    private double baseStemLength = 0.0;

    public BeamGroup() {}

    /**
     * Adds a note to this beam group.
     * Notes must be added in order (left to right).
     *
     * @param note The note to add
     */
    public void addNote(@NotNull Note note) {
        beamedNotes.add(note);
    }

    /**
     * Removes a note from this beam group.
     *
     * @param note The note to remove
     * @return true if the note was removed
     */
    public boolean removeNote(@NotNull Note note) {
        return beamedNotes.remove(note);
    }

    /**
     * Returns an unmodifiable view of beamed notes.
     */
    public @NotNull List<Note> getBeamedNotes() {
        return Collections.unmodifiableList(beamedNotes);
    }

    /**
     * Returns the number of notes in this beam group.
     */
    public int getNoteCount() {
        return beamedNotes.size();
    }

    /**
     * Returns whether this beam group has any notes.
     */
    public boolean isEmpty() {
        return beamedNotes.isEmpty();
    }

    /**
     * Returns the first note in this beam group.
     *
     * @return The first note, or null if the group is empty
     */
    public Note getFirstNote() {
        return beamedNotes.isEmpty() ? null : beamedNotes.get(0);
    }

    /**
     * Returns the last note in this beam group.
     *
     * @return The last note, or null if the group is empty
     */
    public Note getLastNote() {
        return beamedNotes.isEmpty() ? null : beamedNotes.get(beamedNotes.size() - 1);
    }

    /**
     * Returns whether this beam group contains the specified note.
     */
    public boolean containsNote(@NotNull Note note) {
        return beamedNotes.contains(note);
    }

    /**
     * Calculates beam angle and stem targets.
     * Should be called after all notes are added and positioned.
     * <p>
     * Algorithm:
     * 1. Find highest and lowest note heads
     * 2. Determine beam direction (stems up or down)
     * 3. Calculate beam angle based on note positions
     * 4. Calculate stem target points for each note
     */
    public void calculateBeam() {
        if (beamedNotes.size() < 2) {
            beamAngle = 0.0;
            baseStemLength = 0.0;
            return;
        }

        // TODO: Implement full beam calculation algorithm
        // For now, stub implementation returns 0 for all values
        beamAngle = 0.0;
        baseStemLength = 0.0;
    }

    /**
     * Returns the stem target Y position for a specific note.
     * Notes query this during rendering to get adjusted stem length.
     *
     * @param note The note to get the stem target for
     * @return The calculated Y position where stem should meet beam
     */
    public double getStemTargetY(@NotNull Note note) {
        // TODO: Implement stem target calculation based on beam angle
        // For now, return 0 (no adjustment)
        return 0.0;
    }

    /**
     * Returns the calculated beam angle.
     */
    public double getBeamAngle() {
        return beamAngle;
    }

    /**
     * Returns the base stem length before beam adjustment.
     */
    public double getBaseStemLength() {
        return baseStemLength;
    }

    @Override
    public double getContentWidth() {
        if (beamedNotes.isEmpty()) {
            return 0;
        }

        var first = beamedNotes.get(0);
        var last = beamedNotes.get(beamedNotes.size() - 1);

        return last.getX() - first.getX() + last.getContentWidth();
    }

    @Override
    public double getContentHeight() {
        // Height of beam bars (typically 3-4 pixels per beam level)
        return 4.0;
    }
}
