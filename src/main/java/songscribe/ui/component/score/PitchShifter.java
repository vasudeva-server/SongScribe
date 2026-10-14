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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import songscribe.Strings;
import songscribe.dom.AccidentalState;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.engraving.StaffPosition;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.playback.PlayThread;

/**
 * Shifts the pitch of a range of notes as a single recorded mutation: the one definition of
 * moving a selection's pitch, shared by the mouse drag ({@link NoteDragHandler}) and the
 * arrow-key press.
 * <p>
 * A moved note has no pitch of its own at its new staff position: it adopts whatever the song's
 * chain walk decides is in force there once the move is committed. There is no prompt and no fit
 * gate, because a pitch shift changes no element's horizontal extent. The anchor note is played
 * only after that walk has run, since only then is its sounding pitch known.
 */
public final class PitchShifter {

    private PitchShifter() {
    }

    /**
     * {@code line} is the note's own line: a group across a cross-line tie holds entries from two
     * lines. {@code originalStaffPosition} is the one the note is put back to before
     * {@link #commitPitchShift} records its move, so the record's
     * {@link ElementModification#beforeElement()} is the note's real prior state.
     */
    record PitchShiftEntry(Line line, int index, StaffPosition originalStaffPosition) {}

    /**
     * A tie chain reaching {@code line}'s edge through a cross-line tie pulls the partner note in
     * the adjacent line into the group, so both endpoints move together. The whole move, across
     * both lines, is one modification bracket, so one undo restores every note it touched.
     */
    public static void shiftPitch(Line line, ElementRange range, int deltaSp) {
        var group = buildPitchShiftGroup(line, range);

        if (group.isEmpty()) {
            return;
        }

        var clampedDelta = clampDelta(group, deltaSp);

        if (clampedDelta == 0) {
            return;
        }

        // The first note in the range is the arrow-key analog of the grabbed note in a
        // drag: the group's anchor, the only note played as feedback.
        var anchorIndex = group.getFirst().index();

        // Exactly what a mouse drag does on each move: shift the group.
        moveGroup(line, group, anchorIndex, clampedDelta);

        commitPitchShift(line, group);

        // Played only now, after the song's chain walk run by commitPitchShift has decided what
        // the moved anchor sounds. A drag fires this on mouse release; an arrow press has no
        // release, so fire it here to let the played note ring for its standard duration.
        if (Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            PlayThread.sendNoteOn(line.noteAt(anchorIndex).getPitch());
            scheduleAnchorNoteOff(line, anchorIndex);
        }
    }

    /**
     * {@code clampedDelta} must already be clamped via {@link #clampDelta}. A moved note's
     * accidental is left untouched: the song's chain walk decides it once the move is committed.
     */
    static void moveGroup(Line line, List<PitchShiftEntry> group, int anchorIndex, int clampedDelta) {
        for (var entry : group) {
            var note = entry.line().noteAt(entry.index());
            note.setStaffPosition(new StaffPosition(entry.originalStaffPosition().value() + clampedDelta));
        }
    }

    /**
     * Call once a move is finalized and its chain walk has run, because the note-on that sounded
     * the anchor's post-walk pitch is left sustaining.
     */
    static void scheduleAnchorNoteOff(Line line, int anchorIndex) {
        if (Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            new PlayThread(line.noteAt(anchorIndex).getPitch(), false).start();
        }
    }

    /**
     * Every note in the range, grace notes included, comes with its whole tie chain
     * ({@link NoteElement#tieChain()}), so tied notes move together, across lines too.
     */
    static List<PitchShiftEntry> buildPitchShiftGroup(Line line, ElementRange range) {
        // Collected by note identity, not index arithmetic: a tie's two notes can sit in two
        // different lines, so an index only ever means something once it is paired with the
        // line it belongs to.
        var groupNotes = new LinkedHashSet<NoteElement>();

        for (var element : line.getElements(range)) {
            if (element instanceof NoteElement note && note.getType().isNote()) {
                groupNotes.addAll(note.tieChain());
            }
        }

        var group = new ArrayList<PitchShiftEntry>();

        for (var note : groupNotes) {
            var noteLine = note.requireParentLine();
            var index = noteLine.getElementIndex(note);
            group.add(new PitchShiftEntry(noteLine, index, note.getStaffPosition()));
        }

        return group;
    }

    static int clampDelta(List<PitchShiftEntry> group, int deltaSp) {
        var minDelta = Integer.MIN_VALUE;
        var maxDelta = Integer.MAX_VALUE;

        for (var entry : group) {
            minDelta = Math.max(minDelta, StaffPosition.MIN.value() - entry.originalStaffPosition().value());
            maxDelta = Math.min(maxDelta, StaffPosition.MAX.value() - entry.originalStaffPosition().value());
        }

        return Math.clamp(deltaSp, minDelta, maxDelta);
    }

    /**
     * The staff positions must already have been mutated. Everything is coalesced into one
     * modification bracket, so the shift and the accidental restamps the song's chain walk makes
     * for it are one undo step, across both lines of a cross-line tie group.
     * <p>
     * Every group note is first put back to its {@code originalStaffPosition}, then each move is
     * recorded: the song restamps between recorded moves, so only the restored notes give each
     * record the note's real prior state. A note on another staff position is recorded
     * {@link AccidentalState.Adopting Adopting}.
     * <p>
     * A connected glissando whose two notes land on the same pitch is removed, because a
     * same-pitch glissando is not a state the document may hold. The removal joins this
     * bracket, so one undo brings back both the shift and the glissando.
     */
    static void commitPitchShift(Line line, List<PitchShiftEntry> group) {
        line.getSong().withModification(Strings.get(Strings.ACTION_EDIT_OP_MOVE_NOTE), () -> {
            // Every note goes back before any move is recorded: a record's before-element is
            // the note as it stands when the record is made, and the walk run by an earlier
            // record reads the later notes where they really were.
            var landedPositions = new ArrayList<StaffPosition>(group.size());

            for (var entry : group) {
                var note = entry.line().noteAt(entry.index());
                landedPositions.add(note.getStaffPosition());
                note.setStaffPosition(entry.originalStaffPosition());
            }

            for (var i = 0; i < group.size(); i++) {
                var entry = group.get(i);
                var landedPosition = landedPositions.get(i);
                var note = entry.line().noteAt(entry.index());
                var moved = !landedPosition.equals(note.getStaffPosition());

                entry.line().modifyElement(
                        entry.index(),
                        EnumSet.of(ElementField.PITCH, ElementField.ACCIDENTAL),
                        () -> {
                            note.setStaffPosition(landedPosition);

                            if (moved) {
                                note.adopt();
                            }
                        }
                );
            }

            // Runs before the grace-note cleanup, while every group index is still the one the
            // group was built with — the removals below shift them. A shifted note can be either
            // end of a glissando, so both the one it owns and the one pointing at it are checked.
            // A group moving as a whole keeps its internal intervals, so only its edges can come
            // together with an outside note; the two checks cover exactly those.
            for (var entry : group) {
                removeSamePitchGlissando(entry.line(), entry.index());
                removeSamePitchGlissando(entry.line(), entry.index() - 1);
            }

            // Grace note validity checks, one line at a time. Within a line, iterate in reverse
            // index order so a removal never shifts an index this loop has not visited yet; that
            // ordering only holds within one line, since indices from two different lines are
            // unrelated numbers, so each line's entries are sorted and processed independently.
            var entriesByLine = new LinkedHashMap<Line, List<PitchShiftEntry>>();

            for (var entry : group) {
                entriesByLine.computeIfAbsent(entry.line(), key -> new ArrayList<>()).add(entry);
            }

            for (var lineEntries : entriesByLine.entrySet()) {
                var entryLine = lineEntries.getKey();

                var sortedEntries = lineEntries.getValue().stream()
                        .sorted((a, b) -> Integer.compare(b.index(), a.index()))
                        .toList();

                for (var entry : sortedEntries) {
                    var idx = entry.index();
                    var element = entryLine.getElement(idx);

                    if (element.getType().isGraceNote() && entryLine.isSamePitchAsFollower(idx)) {
                        // Grace note shifted to the same pitch as its following note — remove the grace note
                        OptionDialogs.showWarningMessage(
                                null,
                                Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                                Strings.WARNING_GRACE_NOTE_SAME_PITCH
                        );
                        entryLine.removeElement(idx);
                    } else if (!element.getType().isGraceNote()) {
                        // Host note shifted to the same pitch as its preceding grace note — remove the grace note
                        // The preceding grace note sits immediately before idx, so asking whether it
                        // matches its follower asks about this element.
                        var graceIdx = entryLine.precedingGraceNoteIndex(idx);

                        if (graceIdx != null && entryLine.isSamePitchAsFollower(graceIdx)) {
                            OptionDialogs.showWarningMessage(
                                    null,
                                    Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                                    Strings.WARNING_GRACE_NOTE_SAME_PITCH
                            );
                            entryLine.removeElement(graceIdx);
                        }
                    }
                }
            }
        });
    }

    /**
     * Only the same-pitch case is handled here; a glissando pointing at a non-note is repaired by
     * {@link Line}.
     */
    private static void removeSamePitchGlissando(Line line, int ownerIndex) {
        if (ownerIndex < 0 || !line.isSamePitchAsFollower(ownerIndex)) {
            return;
        }

        if (!(line.getElement(ownerIndex) instanceof NoteElement owner) || !owner.hasGlissando()) {
            return;
        }

        line.modifyElement(ownerIndex, ElementField.SLIDE, owner::removeSlide);
    }
}
