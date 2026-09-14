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

import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AccidentalState;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.engraving.StaffPosition;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.playback.PlayThread;

/**
 * Shifts the pitch of a range of notes as a single recorded mutation. This is the
 * one definition of moving a selection's pitch, shared by two callers that differ
 * only in cadence: a mouse drag ({@link NoteDragHandler}) runs the move live across
 * many mouse steps, an arrow-key press runs it once. Housing the logic here — rather
 * than on the drag handler — keeps it independent of mouse dragging, which an arrow
 * press is not.
 * <p>
 * A moved note has no pitch of its own at its new staff position: it adopts whatever
 * the song's chain walk decides is in force there once the move is committed. There is
 * no prompt and no fit gate — a pitch shift changes no element's horizontal extent, so
 * nothing it does can widen a line past its fit. The anchor note — the one the user
 * grabbed or pressed an arrow key on — is played only after that walk has run, since
 * only then is its sounding pitch known.
 */
public final class PitchShifter {

    private PitchShifter() {
    }

    /**
     * Captures the original state of a single note in a pitch-shift group.
     * {@code line} is the note's own line — a group formed across a cross-line tie holds
     * entries from two different lines, so every entry carries the line {@code index}
     * resolves into rather than assuming the group's caller-supplied line. {@code beforeClone}
     * is a pre-mutation snapshot the note is put back to before {@link #commitPitchShift}
     * records its move, so the {@link ElementModification#beforeElement()} of that record is
     * the note's real prior state; {@code originalStaffPosition} is the staff position the
     * shift delta is added to.
     */
    record PitchShiftEntry(Line line, int index, StaffPosition originalStaffPosition, NoteElement beforeClone) {}

    /**
     * Shifts the pitch of every pitched note in the inclusive {@code [begin, end]}
     * range (tie chains expanded) by {@code deltaSp} staff positions and commits
     * the change through the modification mechanism, producing the same recorded
     * mutation a mouse drag does. This is the entry point for arrow-key pitch
     * changes on a selection.
     * <p>
     * A tie chain that reaches {@code line}'s edge through a cross-line tie pulls the partner
     * note in the adjacent line into the group as well, so both endpoints move together. The
     * whole move — both lines — is one modification bracket, so one undo restores every note it
     * touched (see {@link #moveGroup}).
     * <p>
     * {@code deltaSp} is clamped so no note leaves the valid staff range; if the
     * range holds no notes or the clamped delta is zero, nothing happens.
     * Pre-mutation clones for the {@link ElementModification} records are captured
     * before the shift is applied. The move, the commit, and the single note played once the
     * commit's chain walk has decided its sounding pitch are the exact same operations a mouse
     * drag performs on release.
     * <p>
     * A grace-note/host collapse during the commit removes an element, shifting every
     * later index down by one. The active selection range is spliced through that removal
     * by the same mechanism every other structural mutation uses, so this method itself
     * has nothing to report back to its caller.
     */
    public static void shiftPitch(Line line, int begin, int end, int deltaSp) {
        var group = buildPitchShiftGroup(line, begin, end);

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
     * Moves every note in {@code group} — each on its own entry's line, so a group reaching
     * across a cross-line tie moves the partner note in the adjacent line right along with it —
     * to its original staff position plus {@code clampedDelta}. This is the single definition of
     * moving a selection's pitch, shared by mouse drag (called live for each mouse step) and
     * arrow keys (called once per keypress). {@code anchorIndex} is always resolved against
     * {@code line}, since the anchor is the note the caller's own selection or drag grabbed,
     * which is always on {@code line} itself. {@code clampedDelta} must already be clamped via
     * {@link #clampDelta}.
     * <p>
     * A moved note's accidental is left untouched here: the note has landed at a new staff
     * position, and what sounds there — and what the moved note itself now sounds — is for the
     * song's chain walk to decide once the move is committed, not for this method to guess at.
     */
    static void moveGroup(Line line, List<PitchShiftEntry> group, int anchorIndex, int clampedDelta) {
        for (var entry : group) {
            var note = entry.line().noteAt(entry.index());
            note.setStaffPosition(new StaffPosition(entry.originalStaffPosition().value() + clampedDelta));
        }
    }

    /**
     * Lets the anchor note last sounded on this move ring for its standard duration, then
     * stops it. Called once a move is finalized and its chain walk has run — mouse release for
     * a drag, {@link #shiftPitch} for an arrow shift — because the note-on that sounded the
     * anchor's post-walk pitch is left sustaining.
     */
    static void scheduleAnchorNoteOff(Line line, int anchorIndex) {
        if (Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            new PlayThread(line.noteAt(anchorIndex).getPitch(), false).start();
        }
    }

    /**
     * Builds the pitch-shift group for the inclusive {@code [begin, end]} range on {@code line}:
     * every note in the range — grace notes included, since they shift exactly like pitched
     * notes — with each note's tie chain fully expanded so tied notes move together, including
     * a chain that crosses into an adjacent line through a cross-line tie. Each entry captures
     * the note's own line, current staff position, and a pre-mutation clone. Returns an empty
     * list if the range holds no notes.
     */
    static List<PitchShiftEntry> buildPitchShiftGroup(Line line, int begin, int end) {
        // Collect all unique elements, expanding each selected note's tie chain to its full
        // transitive closure — chained ties (note1-2 tied, note2-3 tied separately, possibly
        // across a line break) must move as one unit even though each link is its own two-note
        // Tie. Traversed by element identity, not index arithmetic: a tie's two endpoints can
        // resolve through two different lines, so an index only ever means something once it is
        // paired with the line it belongs to.
        var groupElements = new LinkedHashSet<StaffElement>();
        var pending = new ArrayDeque<StaffElement>();

        for (var i = begin; i <= end; i++) {
            var element = line.getElement(i);

            if (element.getType().isNote()) {
                pending.add(element);
            }
        }

        while (!pending.isEmpty()) {
            var element = pending.remove();

            if (groupElements.add(element)) {
                addTiePartnersOf(element, pending);
            }
        }

        var group = new ArrayList<PitchShiftEntry>();

        for (var element : groupElements) {
            var elementLine = element.getParentLine();

            // A tie chain's separator (see addTiePartnersOf) has no pitch to shift, so it is
            // walked but never entered. An element detached mid-traversal (which should not
            // happen on a live selection) has nothing to resolve an index against.
            if (!(element instanceof NoteElement note) || elementLine == null) {
                continue;
            }

            var index = elementLine.getElementIndex(note);
            group.add(new PitchShiftEntry(elementLine, index, note.getStaffPosition(), note.clone()));
        }

        return group;
    }

    /**
     * Queues every element that a tie touching {@code element} pulls into the group.
     * <p>
     * A tie whose two endpoints share a line contributes its whole index range, not just the
     * two endpoints — a legal separator sitting between them (a barline, refs #726) travels
     * with the chain exactly as it did before this traversal became endpoint-based. A cross-line
     * tie has nothing between its endpoints by construction (the anchor is the last element of
     * one line, the end is the first of the next), so only the two endpoints themselves are
     * queued.
     */
    private static void addTiePartnersOf(StaffElement element, ArrayDeque<StaffElement> pending) {
        var elementLine = element.getParentLine();

        if (elementLine == null) {
            return;
        }

        for (var tie : elementLine.findTiesTouching(element)) {
            queueTieSpan(tie, pending);
        }
    }

    /** Queues the elements {@code tie} contributes to the pitch-shift group. See {@link #addTiePartnersOf}. */
    private static void queueTieSpan(Tie tie, ArrayDeque<StaffElement> pending) {
        var anchor = tie.getAnchorElement();
        var endElement = tie.getEndElement();
        var anchorLine = anchor.getParentLine();
        var endLine = endElement.getParentLine();

        //noinspection ObjectEquality
        if (anchorLine != null && anchorLine == endLine) {
            var anchorIndex = anchorLine.getElementIndex(anchor);
            var endIndex = anchorLine.getElementIndex(endElement);

            for (var j = anchorIndex; j <= endIndex; j++) {
                pending.add(anchorLine.getElement(j));
            }

            return;
        }

        pending.add(anchor);
        pending.add(endElement);
    }

    /**
     * Clamps {@code deltaSp} so that applying it leaves no note in {@code group}
     * outside {@link StaffPosition#MIN}..{@link StaffPosition#MAX}.
     */
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
     * Commits a pitch shift for every note in {@code group} as a single
     * SongDidChangeNotification: one PITCH/ACCIDENTAL {@link ElementModification} per note,
     * then grace-note cleanup — all coalesced into one modification bracket, so the shift and
     * the accidental restamps the song's chain walk makes for it are one undo step. The staff
     * positions must already have been mutated before this is called.
     * <p>
     * Every group note is first put back plainly to its {@code beforeClone}, and only then is
     * each move recorded through {@link Line#modifyElement}. The song walks the chain after
     * every recorded change and records its restamps between the moves, so a note whose move is
     * recorded later may already have been restamped by an earlier one; recording from the
     * restored notes gives each record the note's real prior state, which is what undo puts
     * back. A note that lands on another staff position has no pitch of its own there and is
     * recorded {@link AccidentalState.Adopting Adopting}, so the walk gives it what is in force
     * where it landed.
     * <p>
     * A connected glissando whose two notes land on the same pitch is removed from the model,
     * not merely hidden — a same-pitch glissando is not a state the document may hold. The
     * removal is silent and joins this bracket, so one undo brings both the shift and the
     * glissando back. A drag commits only on release, so passing through the target's pitch
     * mid-drag costs nothing; an arrow press commits on every press, so landing on it does
     * remove the glissando.
     * <p>
     * A group reaching across a cross-line tie holds entries from two lines; every step below
     * runs against each entry's own line, not {@code line}. The whole thing is still one undo
     * step — a bracket opened on {@code line} delegates to its {@link songscribe.dom.Song}, so
     * mutations recorded through the adjacent line's own {@code applyChange}/{@code removeElement}
     * coalesce into the same batch.
     */
    static void commitPitchShift(Line line, List<PitchShiftEntry> group) {
        line.withModification(Strings.get(Strings.ACTION_EDIT_OP_MOVE_NOTE), () -> {
            // Every note goes back before any move is recorded: a record's before-element is
            // the note as it stands when the record is made, and the walk run by an earlier
            // record reads the later notes where they really were.
            var landedPositions = new ArrayList<StaffPosition>(group.size());

            for (var entry : group) {
                var note = entry.line().noteAt(entry.index());
                var before = entry.beforeClone();
                landedPositions.add(note.getStaffPosition());
                note.setStaffPosition(before.getStaffPosition());
                note.setAccidentalState(before.accidentalState());
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
                                note.setAccidentalState(new AccidentalState.Adopting());
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
                        // matches its follower asks about this element. A negative index reads as
                        // no match, which covers "no preceding grace note".
                        var graceIdx = entryLine.precedingGraceNoteIndex(idx);

                        if (entryLine.isSamePitchAsFollower(graceIdx)) {
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
     * Removes the glissando owned by the element at {@code ownerIndex} when the shift has left it
     * {@linkplain Line#isSamePitchAsFollower spanning two notes at one pitch}, so it has nothing
     * to traverse.
     * <p>
     * Recorded through {@link Line#modifyElement} like every other slide strip, so undo restores
     * it. Only the same-pitch case is handled here: a glissando left pointing at something that
     * is not a note at all is a structural problem, and its repair belongs to {@link Line}.
     *
     * @param line       the line holding both notes
     * @param ownerIndex index of the note that owns the glissando; out-of-range is a no-op
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
