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

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.Strings;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.ui.OptionDialogs;
import songscribe.ui.edit.AccidentalRestatements;
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
     * {@code line} is the note's own line — a group formed across a cross-line tie holds
     * entries from two different lines, so every entry carries the line {@code index}
     * resolves into rather than assuming the group's caller-supplied line. {@code beforeClone}
     * is a pre-mutation snapshot used as the {@link ElementModification#beforeElement()} for the
     * mutation record; {@code originalStaffPositionSp} is the staff position the shift delta is
     * added to.
     */
    record PitchShiftEntry(Line line, int index, int originalStaffPositionSp, StaffElement beforeClone) {}

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
     * touched (see {@link #moveGroupAndPlayAnchor}).
     * <p>
     * {@code deltaSp} is clamped so no note leaves the valid staff range; if the
     * range holds no notes or the clamped delta is zero, nothing happens.
     * Pre-mutation clones for the {@link ElementModification} records are captured
     * before the shift is applied. The move, the single note played, and the commit
     * are the exact same operations a mouse drag performs — a drag runs them live
     * across many mouse steps, an arrow key runs them once per press.
     * <p>
     * A grace-note/host collapse during the commit removes an element, shifting every
     * later index down by one. The active selection range is spliced through that removal
     * by the same mechanism every other structural mutation uses, so this method itself
     * has nothing to report back to its caller.
     *
     * @param parent The component to parent the restatement prompt on, or null when there is no
     *               owning window. Without it the prompt is placed against the screen rather than
     *               the score window
     */
    public static void shiftPitch(
            @Nullable Component parent, Line line, int begin, int end, int deltaSp) {
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

        // Asked before anything is mutated, so Cancel is simply "nothing happened" — the same
        // contract the two early returns above already have.
        var decision = confirmRestatements(parent, line, group);

        if (decision.isCancelled()) {
            return;
        }

        // The accidentals this shift must make explicit so no pitch the user did not touch
        // changes: each note the group vacates may have been lending its explicit accidental to a
        // later note at the same staff position. Must run here, while the line is still
        // unmutated — the reconciliation reads the live line as its "before".
        //
        // No fit gate: a pitch shift changes no element's horizontal extent, and clearing the
        // moved notes' accidentals only ever narrows them. A materialization on a following note
        // can widen the line, but each vacated staff position yields at most one materialization
        // and the moved note gave up its own accidental glyph in exchange.
        var accidentalChanges = AccidentalReconciliation.reconcileModification(
            line, intendedChanges(group, line, clampedDelta), decision.removal());

        // Exactly what a mouse drag does on each move: shift the group, play the anchor.
        moveGroupAndPlayAnchor(line, group, anchorIndex, clampedDelta);

        // A drag fires this on mouse release; an arrow press has no release, so fire it
        // here to let the played note ring for its standard duration.
        scheduleAnchorNoteOff(line, anchorIndex);

        commitPitchShift(line, group, accidentalChanges, decision);
    }

    /**
     * Moves every note in {@code group} — each on its own entry's line, so a group reaching
     * across a cross-line tie moves the partner note in the adjacent line right along with it —
     * to its original staff position plus {@code clampedDelta}, and plays the {@code anchorIndex}
     * note's pitch transition — note-off its pre-move pitch, note-on its post-move pitch — when
     * {@link PrefsKey#PLAY_SELECTED_NOTE} is on. This is the single definition of
     * WHAT happens on one pitch move, shared by mouse drag (called live for each
     * mouse step) and arrow keys (called once per keypress). Only the anchor sounds,
     * exactly as a drag plays only the grabbed note. {@code anchorIndex} is always resolved
     * against {@code line}, since the anchor is the note the caller's own selection or drag
     * grabbed, which is always on {@code line} itself. {@code clampedDelta} must
     * already be clamped via {@link #clampDelta}.
     * <p>
     * Every moved note's explicit accidental is <b>cleared</b>. That is intended, matching
     * MuseScore: an accidental is written for the staff position it was written on, and a note
     * that leaves that position has no claim to it. Undo restores it — {@link #commitPitchShift}
     * records an {@link ElementModification} carrying the pre-move clone, and undo restores that
     * snapshot whole via {@link StaffElement#copyStateFrom}, which copies the accidental. The
     * {@code EnumSet<ElementField>} on the record only tells subscribers which fields changed; it
     * does not restrict what undo restores.
     */
    static void moveGroupAndPlayAnchor(Line line, List<PitchShiftEntry> group, int anchorIndex, int clampedDelta) {
        var playSelected = Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE);

        // Note-off the anchor's currently-sounding (pre-move) pitch
        if (playSelected) {
            PlayThread.sendNoteOff(line.getElement(anchorIndex).getPitch());
        }

        for (var entry : group) {
            var note = entry.line().getElement(entry.index());
            note.setStaffPosition(entry.originalStaffPositionSp() + clampedDelta);

            // See the javadoc: the accidental belonged to the staff position, not to the note.
            note.setAccidental(null);

            if (note.isStemDirectionAuto()) {
                note.setDirection(StaffElement.defaultDirection(note));
            }
        }

        // Note-on the anchor's new (post-move) pitch
        if (playSelected) {
            PlayThread.sendNoteOn(line.getElement(anchorIndex).getPitch());
        }
    }

    /**
     * Asks whether this shift should also take away the later notes that restate the accidentals
     * it removes — every explicit accidental in {@code group}, since a note that leaves a staff
     * position gives up the accidental written for it, which is why each entry states no accidental
     * at all as its post-shift one.
     *
     * <p>Read off each entry's {@code beforeClone} rather than the live element, because a drag has
     * already moved and cleared the live notes by the time it asks. Must be called before any
     * modification bracket opens, and its answer honored — Cancel means the shift does not happen.
     *
     * <p>{@link AccidentalRestatements#confirm} indexes every note it is given into one line, so
     * a group reaching across a cross-line tie is filtered to the entries that actually sit on
     * {@code line} before asking — the restatement offer stays scoped to the line the caller is
     * editing, exactly as it was before cross-line groups existed. The partner note in the
     * adjacent line still shifts and still clears its own accidental (see
     * {@link #moveGroupAndPlayAnchor}); it is only the "later notes restating what this removed"
     * search that does not extend past {@code line}.
     *
     * @param parent The component to parent the dialog on, or null when there is no owning window
     * @param line   The line the group sits on, in its pre-shift state for an arrow press and its
     *               post-drag state for a drag; only the group's indices are read off it
     * @param group  The notes being shifted
     */
    static AccidentalRestatements.Decision confirmRestatements(
            @Nullable Component parent, Line line, List<PitchShiftEntry> group) {

        var entriesOnLine = entriesOnLine(group, line);
        var edited = new ArrayList<AccidentalRestatements.EditedNote>(entriesOnLine.size());

        for (var entry : entriesOnLine) {
            edited.add(new AccidentalRestatements.EditedNote(
                    entry.index(),
                    entry.originalStaffPositionSp(),
                    entry.beforeClone().getAccidental(),
                    null));
        }

        return AccidentalRestatements.confirm(parent, line, edited);
    }

    /**
     * The entries of {@code group} whose own line is {@code line}, in {@code group}'s order.
     * <p>
     * A cross-line group holds entries from two lines, but every accidental-reconciliation
     * call ({@link AccidentalRestatements#confirm}, {@link AccidentalReconciliation}) — here
     * and in {@link NoteDragHandler}, which runs the same reconciliation on mouse release —
     * indexes its input into a single line. Filtering to one line's entries here is what keeps
     * a same-line group's behavior identical to before this method existed — the filter is a
     * no-op when every entry already shares one line.
     */
    static List<PitchShiftEntry> entriesOnLine(List<PitchShiftEntry> group, Line line) {
        var entries = new ArrayList<PitchShiftEntry>(group.size());

        for (var entry : group) {
            //noinspection ObjectEquality
            if (entry.line() == line) {
                entries.add(entry);
            }
        }

        return entries;
    }

    /**
     * The post-shift state of every note in {@code group} <em>that sits on {@code line}</em>, as
     * {@link AccidentalReconciliation} describes it. These are the notes the user moved
     * deliberately, so they are never materialized themselves — only the notes that inherited
     * the accidental they are taking away are. The accidental is always null because
     * {@link #moveGroupAndPlayAnchor} clears it.
     * <p>
     * Takes {@code line} and filters by it rather than trusting the caller to have done so:
     * every index it emits is indexed straight into one line by
     * {@link AccidentalReconciliation}, and a group reaching across a cross-line tie holds
     * entries whose indices belong to the adjacent line. Passing those through would reconcile
     * against whatever note happened to sit at that index here, or run off the end of a
     * shorter line. The filter is a no-op for a group that is already all on one line.
     */
    static List<AccidentalReconciliation.IntendedChange> intendedChanges(
            List<PitchShiftEntry> group, Line line, int clampedDelta) {

        var entries = entriesOnLine(group, line);
        var changes = new ArrayList<AccidentalReconciliation.IntendedChange>(entries.size());

        for (var entry : entries) {
            changes.add(new AccidentalReconciliation.IntendedChange(
                    entry.index(), null, entry.originalStaffPositionSp() + clampedDelta));
        }

        return changes;
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

            if (elementLine == null) {
                // Detached mid-traversal (should not happen on a live selection) — nothing to
                // resolve an index or a staff position against.
                continue;
            }

            var index = elementLine.getElementIndex(element);
            group.add(new PitchShiftEntry(elementLine, index, element.getStaffPosition(), element.clone()));
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

        if (anchor == null || endElement == null) {
            return;
        }

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
     * SongDidChangeNotification: one PITCH/ACCIDENTAL {@link ElementModification} per note
     * (carrying its pre-mutation {@code beforeClone}), then {@code accidentalChanges},
     * then grace-note cleanup — all coalesced into one modification bracket, so the shift and
     * the accidentals it forces on or takes away from other notes are one undo step. The staff
     * positions must already have been mutated before this is called.
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
    static void commitPitchShift(
            Line line,
            List<PitchShiftEntry> group,
            List<AccidentalReconciliation.AccidentalChange> accidentalChanges,
            AccidentalRestatements.Decision decision) {

        line.withModification(Strings.get(Strings.ACTION_EDIT_OP_MOVE_NOTE), () -> {
            for (var entry : group) {
                var entryLine = entry.line();

                entryLine.applyChange(
                        new ElementModification(entryLine, entry.index(),
                                EnumSet.of(ElementField.PITCH, ElementField.ACCIDENTAL),
                                entry.beforeClone(), entryLine.getElement(entry.index()).clone()),
                        () -> {}
                );
            }

            // Recorded in the same bracket so the shift and its reconciliation are one undo step.
            // Applied before the grace-note cleanup, while every index is still the one the
            // reconciliation saw. Scoped to line: see confirmRestatements/entriesOnLine.
            AccidentalMaterializer.commit(line, accidentalChanges);

            // Accepted restatements on later lines join the same step.
            AccidentalRestatements.commitOtherLines(decision, line);

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

        var owner = line.getElement(ownerIndex);

        if (!owner.hasGlissando()) {
            return;
        }

        line.modifyElement(ownerIndex, ElementField.SLIDE, owner::removeSlide);
    }
}
