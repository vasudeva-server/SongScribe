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

package songscribe.ui.component.score;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.Strings;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.OptionDialogs;
import songscribe.engraving.Staff;
import songscribe.ui.playback.PlayThread;

/**
 * Shifts the pitch of a range of notes as a single recorded mutation. This is the
 * one definition of "move a selection's pitch," shared by two callers that differ
 * only in cadence: a mouse drag ({@link NoteDragHandler}) runs the move live across
 * many mouse steps, an arrow-key press runs it once. Housing the logic here — rather
 * than on the drag handler — keeps it independent of mouse dragging, which an arrow
 * press is not.
 */
public final class PitchShifter {

    private PitchShifter() {
    }

    /**
     * Captures the original state of a single note in a pitch-shift group.
     * {@code beforeClone} is a pre-mutation snapshot used as the
     * {@link ElementModification#beforeElement()} for the mutation record;
     * {@code originalStaffPositionSp} is the staff position the shift delta is
     * added to.
     */
    record PitchShiftEntry(int index, int originalStaffPositionSp, StaffElement beforeClone) {}

    /**
     * Shifts the pitch of every pitched note in the inclusive {@code [begin, end]}
     * range (tie chains expanded) by {@code deltaSp} staff positions and commits
     * the change through the modification mechanism, producing the same recorded
     * mutation a mouse drag does. This is the entry point for arrow-key pitch
     * changes on a selection.
     * <p>
     * {@code deltaSp} is clamped so no note leaves the valid staff range; if the
     * range holds no notes or the clamped delta is zero, nothing happens.
     * Pre-mutation clones for the {@link ElementModification} records are captured
     * before the shift is applied. The move, the single note played, and the commit
     * are the exact same operations a mouse drag performs — a drag runs them live
     * across many mouse steps, an arrow key runs them once per press.
     * <p>
     * A grace-note/host collapse during the commit removes an element, shifting every
     * later index down by one. Returns null if {@code [begin, end]} is still valid as
     * the selection range (nothing removed, or nothing removed within/before it);
     * otherwise returns the range adjusted for the removals, or {@code {-1, -1}} if
     * every element in the range was removed and the selection should be cleared.
     */
    public static int @Nullable [] shiftPitch(Line line, int begin, int end, int deltaSp) {
        var group = buildPitchShiftGroup(line, begin, end);

        if (group.isEmpty()) {
            return null;
        }

        var clampedDelta = clampDelta(group, deltaSp);

        if (clampedDelta == 0) {
            return null;
        }

        // The first note in the range is the arrow-key analog of the grabbed note in a
        // drag: the group's anchor, the only note played as feedback.
        var anchorIndex = group.getFirst().index();

        // Exactly what a mouse drag does on each move: shift the group, play the anchor.
        moveGroupAndPlayAnchor(line, group, anchorIndex, clampedDelta);

        // A drag fires this on mouse release; an arrow press has no release, so fire it
        // here to let the played note ring for its standard duration.
        scheduleAnchorNoteOff(line, anchorIndex);

        var removedIndices = commitPitchShift(line, group);

        if (removedIndices.isEmpty()) {
            return null;
        }

        return adjustRangeForRemovals(begin, end, removedIndices);
    }

    /**
     * Recomputes the {@code [begin, end]} selection range after {@code removedIndices}
     * (indices in the pre-removal element list) were removed, each shifting every later
     * index down by one. A removal below {@code begin} shifts the whole range down;
     * a removal within {@code [begin, end]} only shrinks {@code end}. Returns
     * {@code {-1, -1}} if the adjusted range is empty (every element in the original
     * range was removed).
     */
    static int[] adjustRangeForRemovals(int begin, int end, List<Integer> removedIndices) {
        var newBegin = begin;
        var newEnd = end;

        for (var removedIndex : removedIndices) {
            if (removedIndex < begin) {
                newBegin--;
                newEnd--;
            } else if (removedIndex <= end) {
                newEnd--;
            }
        }

        if (newEnd < newBegin) {
            return new int[] {-1, -1};
        }

        return new int[] {newBegin, newEnd};
    }

    /**
     * Moves every note in {@code group} to its original staff position plus
     * {@code clampedDelta} and plays the {@code anchorIndex} note's pitch
     * transition — note-off its pre-move pitch, note-on its post-move pitch — when
     * {@link PrefsKey#PLAY_SELECTED_NOTE} is on. This is the single definition of
     * WHAT happens on one pitch move, shared by mouse drag (called live for each
     * mouse step) and arrow keys (called once per keypress). Only the anchor sounds,
     * exactly as a drag plays only the grabbed note. {@code clampedDelta} must
     * already be clamped via {@link #clampDelta}.
     */
    static void moveGroupAndPlayAnchor(Line line, List<PitchShiftEntry> group, int anchorIndex, int clampedDelta) {
        var playSelected = Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE);

        // Note-off the anchor's currently-sounding (pre-move) pitch
        if (playSelected) {
            PlayThread.sendNoteOff(line.getElement(anchorIndex).getPitch());
        }

        for (var entry : group) {
            var note = line.getElement(entry.index());
            note.setStaffPosition(entry.originalStaffPositionSp() + clampedDelta);
            note.setDirection(StaffElement.defaultDirection(note));
        }

        // Note-on the anchor's new (post-move) pitch
        if (playSelected) {
            PlayThread.sendNoteOn(line.getElement(anchorIndex).getPitch());
        }
    }

    /**
     * Lets the anchor note that {@link #moveGroupAndPlayAnchor} last sounded ring for
     * its standard duration, then stops it. Called once a move is finalized — mouse
     * release for a drag, keypress for an arrow shift — because the last note-on is
     * left sustaining.
     */
    static void scheduleAnchorNoteOff(Line line, int anchorIndex) {
        if (Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            new PlayThread(line.getElement(anchorIndex).getPitch(), false).start();
        }
    }

    /**
     * Builds the pitch-shift group for the inclusive {@code [begin, end]} range:
     * every note in the range — grace notes included, since they shift exactly like
     * pitched notes — with each note's tie chain fully expanded so tied notes move
     * together. Each entry captures the note's current staff position and a
     * pre-mutation clone. Returns an empty list if the range holds no notes.
     */
    static List<PitchShiftEntry> buildPitchShiftGroup(Line line, int begin, int end) {
        // Collect all unique indices, expanding each selected note's tie chain
        var groupIndices = new LinkedHashSet<Integer>();

        for (var i = begin; i <= end; i++) {
            var element = line.getElement(i);

            if (!element.getType().isNote()) {
                continue;
            }

            var tie = line.findTieAt(i);

            if (tie != null) {
                for (var j = tie.getAnchorElementIndex(); j <= tie.getEndElementIndex(); j++) {
                    groupIndices.add(j);
                }
            } else {
                groupIndices.add(i);
            }
        }

        var group = new ArrayList<PitchShiftEntry>();

        for (var idx : groupIndices) {
            var note = line.getElement(idx);
            group.add(new PitchShiftEntry(idx, note.getStaffPosition(), note.clone()));
        }

        return group;
    }

    /**
     * Clamps {@code deltaSp} so that applying it leaves no note in {@code group}
     * outside {@link Staff#MIN_STAFF_POSITION_SP}..{@link Staff#MAX_STAFF_POSITION_SP}.
     */
    static int clampDelta(List<PitchShiftEntry> group, int deltaSp) {
        var minDelta = Integer.MIN_VALUE;
        var maxDelta = Integer.MAX_VALUE;

        for (var entry : group) {
            minDelta = Math.max(minDelta, Staff.MIN_STAFF_POSITION_SP - entry.originalStaffPositionSp());
            maxDelta = Math.min(maxDelta, Staff.MAX_STAFF_POSITION_SP - entry.originalStaffPositionSp());
        }

        return Math.clamp(deltaSp, minDelta, maxDelta);
    }

    /**
     * Commits a pitch shift for every note in {@code group} as a single
     * SongDidChangeNotification: one PITCH {@link ElementModification} per note
     * (carrying its pre-mutation {@code beforeClone}), followed by grace-note
     * cleanup — all coalesced into one modification bracket. The staff positions
     * must already have been mutated before this is called.
     * <p>
     * A connected glissando that becomes unison is left intact: the renderer hides
     * it while the two notes share a pitch and shows it again when they diverge, so
     * moving a note through unison — by drag or arrow key — never destroys it.
     * <p>
     * Returns the indices (in the pre-removal element list) of every element removed
     * by a grace-note/host collapse, so a caller tracking a selection range can adjust
     * it for the resulting index shift. Empty if nothing was removed.
     */
    static List<Integer> commitPitchShift(Line line, List<PitchShiftEntry> group) {
        var removedIndices = new ArrayList<Integer>();

        line.withModification(() -> {
            for (var entry : group) {
                line.applyChange(
                        new ElementModification(line, entry.index(), EnumSet.of(ElementField.PITCH), entry.beforeClone()),
                        () -> {}
                );
            }

            // Grace note validity checks — iterate in reverse index order to avoid index shifting from removals
            var sortedEntries = group.stream()
                    .sorted((a, b) -> Integer.compare(b.index(), a.index()))
                    .toList();

            for (var entry : sortedEntries) {
                var idx = entry.index();
                var element = line.getElement(idx);

                if (element.getType().isGraceNote()
                        && idx + 1 < line.elementCount()
                        && element.getPitch() == line.getElement(idx + 1).getPitch()) {
                    // Grace note shifted to the same pitch as its following note — remove the grace note
                    OptionDialogs.showWarningMessage(
                            null,
                            Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                            Strings.WARNING_GRACE_NOTE_SAME_PITCH
                    );
                    line.removeElement(idx);
                    removedIndices.add(idx);
                } else if (!element.getType().isGraceNote()) {
                    // Host note shifted to the same pitch as its preceding grace note — remove the grace note
                    var graceIdx = line.precedingGraceNoteIndex(idx);

                    if (graceIdx >= 0
                            && line.getElement(graceIdx).getPitch() == element.getPitch()) {
                        OptionDialogs.showWarningMessage(
                                null,
                                Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                                Strings.WARNING_GRACE_NOTE_SAME_PITCH
                        );
                        line.removeElement(graceIdx);
                        removedIndices.add(graceIdx);
                    }
                }
            }
        });

        return removedIndices;
    }
}
