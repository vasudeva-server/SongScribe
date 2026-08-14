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

package songscribe.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Key;
import songscribe.dom.KeySignatureElement;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;

/**
 * Decides which notes must be given an explicit accidental — or have one taken away — so that an
 * edit does not silently change any pitch the user did not ask to change, and does not strand
 * notation it made redundant.
 *
 * <h2>The invariant</h2>
 * Every note keeps the pitch it had, unless the user changed that note. Two populations are
 * protected: <b>pasted or inserted</b> notes keep the pitch they had in their source context, and
 * <b>surviving</b> notes keep the pitch they had before the mutation. A note the user themselves
 * changed is never protected — it is supposed to change — so it never appears in a result.
 *
 * <h2>The rule</h2>
 * For a note whose effective accidental would change:
 * <pre>
 * if (adjustment(before) != adjustment(after)) {
 *     note.accidental = (before != null) ? before : NATURAL;
 * }
 * </pre>
 * Comparison is always by <em>sounding adjustment</em>
 * ({@link StaffElement#getPitchAdjustment}, with null treated as 0), never by enum identity:
 * {@code null} and {@code NATURAL} sound alike, and there is no glyph worth drawing for a
 * difference nobody can hear. The {@code null → NATURAL} direction is the whole cross-key paste
 * case — you cannot write "nothing" and get a natural in a context that alters that pitch.
 *
 * <p>The key signature never appears in the algorithm. It is already the last branch of
 * {@link StaffElement#findEffectiveAccidental}, so resolving <em>before</em> against the source
 * context and <em>after</em> against the destination compares the two keys implicitly.
 *
 * <h2>The mirror rule — removal</h2>
 * A note's explicit accidental is cleared when this edit <b>both</b> moved the context arriving at
 * that note <b>and</b> left the accidental sounding identical to the new context:
 * <pre>
 * clear when adjustment(contextBefore) != adjustment(contextAfter)   // this edit moved the context
 *       and  adjustment(own)           == adjustment(contextAfter);  // own is now redundant
 * </pre>
 * Without it the reconciliation is add-only and strands the accidentals it added. In D♭ major, on a
 * bare {@code F G F}: toggling a flat onto index 0 materializes a natural on index 2, and toggling
 * the flat back off leaves that natural behind — the pitch is right, the notation is not.
 *
 * <p>The second condition is what makes removal safe to apply unattended. An accidental that was
 * <em>already</em> redundant when it was written can never be removed: "already redundant" means
 * {@code adjustment(own) == adjustment(contextBefore)}, which together with the second condition
 * forces {@code adjustment(contextBefore) == adjustment(contextAfter)} and so contradicts the
 * first. A deliberate restatement, or a courtesy accidental placed where the note already sounded
 * that way, therefore survives every edit that does not move its context — and restatements are
 * the norm in this repertoire.
 *
 * <p>That is also the rule's limit. A restatement of the accidental <em>being removed</em> is
 * invisible to this arithmetic, and on its own line it may be doing real work, since the backward
 * scan resets at the line boundary. Removing those needs the notator's judgement, which is why
 * {@link #findRestatements} only <em>offers</em> them and why nothing here removes one unasked.
 *
 * <h2>Restatement removal — the consented inversion</h2>
 * {@link #findRestatements} scans forward through the song for later notes at a removed
 * accidental's staff position asserting the same sounding adjustment. When the user accepts them,
 * the caller passes them back as a {@link RestatementRemoval}, and this unit does two things it
 * does nowhere else:
 * <ol>
 *   <li>emits a clearing {@link AccidentalChange} for each accepted note, so the removals travel
 *       the same apply-and-gate path as everything else rather than needing their own; and</li>
 *   <li><b>suspends materialization</b> at the removal's staff position, from the removal forward
 *       until the next explicit accidental at that position.</li>
 * </ol>
 * The suspension deliberately inverts the invariant at the top of this class: a bare note that
 * inherited the removed accidental is <em>supposed</em> to change pitch here, and materializing to
 * protect it would defeat the whole feature. The dialog is the consent that licenses that, which is
 * why suppression can only ever arrive from a caller and is empty for every other edit. Removal
 * still runs at a suppressed position — only materialization is suspended.
 *
 * <p>Suppressing by staff position rather than by note is safe rather than merely convenient: a
 * bare note at that position before any explicit accidental there necessarily resolved through the
 * removed accidental or through the key, and in the key case before and after already agree, so
 * nothing was going to be materialized.
 *
 * <p>Parenthesized accidentals get no exemption: parentheses record that the notator chose to
 * write something they did not have to, which says nothing about whether a later edit obviated it.
 * Removal applies to <b>surviving</b> notes only — a pasted or inserted note keeps the notation it
 * arrived with, matching the "a fragment carries semantic content" rule that governs the
 * clipboard. Sound is preserved either way; this is only about what is drawn.
 *
 * <h2>The tie exclusion</h2>
 * A note that ends a tie is never a candidate for either branch. A tie asserts that two notes are
 * one sounding pitch, so the tied note has no pitch of its own for the invariant to protect: when
 * the anchor moves, the tied note is <em>supposed</em> to move with it, and the accidental that
 * says so is the anchor's. Without the exclusion, toggling a flat onto the first of two tied
 * bare A's materializes a natural on the second — an edit the user did not ask for, notating a
 * pitch the tie forbids.
 *
 * <p>The exclusion also settles what the backward scan has to do about ties: nothing.
 * {@link StaffElement#findEffectiveAccidental} escapes a barrier through a tie whose anchor lies
 * before it, but that can only ever apply to a note ending such a tie — exactly the notes this
 * exclusion drops before the scan runs. {@link #resolveOverProjection} therefore stops dead at the
 * first barrier, with no escape to mirror.
 *
 * <h2>The two bounds</h2>
 * <ol>
 *   <li>Only a staff position carrying an explicit accidental in the removed or the inserted
 *       content can change the context arriving at the boundary.</li>
 *   <li>For each such position, only the <em>first</em> following note lacking its own accidental
 *       needs fixing; later ones resolve from it.</li>
 * </ol>
 * Both bounds are satisfied <em>structurally</em> by the single left-to-right pass, not by an
 * early exit: once the first following note at a staff position materializes, later notes at that
 * position resolve from the materialized accidental and match their own {@code before}, so they
 * emit nothing; and a note that already carries an explicit accidental of its own is skipped
 * outright. An extra early stop would be a pure optimization over a pass bounded by one line's
 * element count that runs once per mutation, not once per frame — so none is added.
 *
 * <p>Both bounds stay within the line, because the backward scan does.
 *
 * <h2>Pure and pre-mutation</h2>
 * This unit reads the live, unmutated line and <b>mutates nothing</b>. Callers apply the returned
 * {@link AccidentalChange}s. That is mandatory rather than stylistic: accidentals must be
 * materialized <em>before</em> the projected column chain is built, because
 * {@code ElementColumnBuilder} derives element extents including accidental width and
 * {@link LayoutEngine} treats accidental widths as a layout input. With that ordering, both the
 * fit gate and the committed layout are correct with no per-position shift machinery. The
 * apply-and-gate side lives in a separate class so this one stays pure — do not add mutating
 * helpers here.
 */
public final class AccidentalReconciliation {

    private AccidentalReconciliation() {
        // Prevent instantiation - utility class with static methods only
    }

    /**
     * A note whose explicit accidental must change so that a mutation neither moves its pitch nor
     * strands notation the mutation made redundant.
     *
     * @param note       The note to change
     * @param accidental The accidental to set on it, or null to clear the one it carries
     */
    public record AccidentalChange(StaffElement note, StaffElement.@Nullable Accidental accidental) {
        // Written out rather than compact: NullAway does not carry the type-use @Nullable
        // annotation from the record header onto the synthesized canonical constructor, so callers
        // passing a null accidental — a removal — would otherwise be rejected.
        @SuppressWarnings("RedundantRecordConstructor")
        public AccidentalChange(StaffElement note, StaffElement.@Nullable Accidental accidental) {
            this.note = note;
            this.accidental = accidental;
        }
    }

    /**
     * An insert, a delete, or a paste-replace, described <em>before</em> any of it happens.
     *
     * @param insertIndex              Index in {@code line} where {@code inserted} will land, and
     *                                 the first index of {@code deleteRange} when there is one
     * @param deleteRange              The range this mutation removes, or null when nothing is
     *                                 removed
     * @param inserted                 The elements being inserted, in order
     * @param insertedPriorAccidentals Either <b>empty</b>, or the same size as {@code inserted}.
     *                                 When the same size, each entry is that element's effective
     *                                 accidental <em>in its source context</em> (null when it
     *                                 sounded unaltered there) — the paste case. When
     *                                 <b>empty</b>, the inserted elements have no source context
     *                                 and are never materialized against themselves; only the
     *                                 notes following them are candidates. That is the
     *                                 fresh-insert case: a note the user is creating has no pitch
     *                                 it "had", so the invariant does not reach it. "No source
     *                                 context" is deliberately <em>not</em> encoded as a list of
     *                                 nulls — null already means "sounded unaltered there", which
     *                                 is a different claim.
     * @param insertedSpans            The fragment's own spans (its ties), which are not
     *                                 yet on the destination line; the tie exclusion needs them,
     *                                 so that a note arriving already tied is left alone
     */
    public record InsertionRegion(
        Line line,
        int insertIndex,
        InsertionSpacingCalculator.@Nullable DeletedRange deleteRange,
        List<StaffElement> inserted,
        List<StaffElement.@Nullable Accidental> insertedPriorAccidentals,
        List<Span> insertedSpans
    ) {
        // Written out rather than compact: NullAway does not carry the type-use @Nullable
        // annotations from the record header onto the synthesized canonical constructor, so
        // callers passing a null delete range or a list with null entries are rejected.
        public InsertionRegion(
            Line line,
            int insertIndex,
            InsertionSpacingCalculator.@Nullable DeletedRange deleteRange,
            List<StaffElement> inserted,
            List<StaffElement.@Nullable Accidental> insertedPriorAccidentals,
            List<Span> insertedSpans) {

            if (!insertedPriorAccidentals.isEmpty() && (insertedPriorAccidentals.size() != inserted.size())) {
                throw new IllegalArgumentException(
                    "insertedPriorAccidentals must be empty or the same size as inserted, but was "
                        + insertedPriorAccidentals.size() + " for " + inserted.size() + " inserted elements");
            }

            this.line = line;
            this.insertIndex = insertIndex;
            this.deleteRange = deleteRange;
            this.inserted = List.copyOf(inserted);

            // List.copyOf rejects nulls, and a null entry here is meaningful ("sounded unaltered
            // in the source context"), so copy through a list that permits them.
            this.insertedPriorAccidentals =
                Collections.unmodifiableList(new ArrayList<>(insertedPriorAccidentals));
            this.insertedSpans = List.copyOf(insertedSpans);
        }
    }

    /**
     * One note's intended post-change state for an in-place modification.
     *
     * @param index         The note's index in the line, unchanged by the modification
     * @param accidental    The explicit accidental the note will carry afterwards, or null
     * @param staffPosition The staff position the note will sit at afterwards
     */
    public record IntendedChange(
        int index,
        StaffElement.@Nullable Accidental accidental,
        int staffPosition
    ) {
    }

    /**
     * A later note asserting the same sounding adjustment as an accidental an edit is about to
     * remove. Carries its line because the scan crosses line boundaries and the caller reconciles
     * one line at a time.
     *
     * @param line The line the note sits on
     * @param note The note itself, which still carries its explicit accidental
     */
    public record Restatement(Line line, StaffElement note) {}

    /**
     * The restatements the user accepted for one edit, and the staff positions whose
     * materialization that acceptance suspends. See the class javadoc for why suppression is by
     * staff position and why it can only ever come from a caller.
     *
     * @param notes                    Every accepted restatement note, across every line the edit
     *                                 touches. A reconciliation of one line simply ignores the
     *                                 notes that are not on it, so the same set is passed to each
     * @param suppressedStaffPositions The staff positions of the accidentals this edit removes.
     *                                 Materialization is suspended at each, from the edit point
     *                                 forward until the next explicit accidental there
     */
    public record RestatementRemoval(Set<StaffElement> notes, Set<Integer> suppressedStaffPositions) {

        /** The edit removed nothing the user had to consent to — every edit outside this feature. */
        public static final RestatementRemoval NONE = new RestatementRemoval(Set.of(), Set.of());

        public RestatementRemoval {
            // Identity semantics come for free: StaffElement overrides neither equals nor hashCode.
            notes = Set.copyOf(notes);
            suppressedStaffPositions = Set.copyOf(suppressedStaffPositions);
        }
    }

    /**
     * Returns the later notes that restate an accidental this edit is about to remove: notes at
     * {@code staffPosition} carrying an explicit accidental of the same sounding adjustment as
     * {@code removed}, from just after {@code fromIndex} on {@code line} through the end of the
     * song.
     *
     * <p>The scan <b>stops at an explicit cancellation</b> — the first explicit accidental at that
     * staff position with a different adjustment. Past that point a matching accidental reads as a
     * fresh decision, not a restatement of the one being removed.
     *
     * <p>It does not stop at a barline or a line boundary, and that is the point: a restatement on
     * a later line is exactly the case no context arithmetic can identify, because line reset makes
     * it non-redundant on its own line. Only the notator can say whether it was still meant.
     *
     * <p>Pure and pre-mutation, like everything else in this class.
     *
     * @param song          The song to scan
     * @param line          The line the removed accidental sits on
     * @param fromIndex     The removed accidental's index on {@code line}; the scan starts after it
     * @param staffPosition The staff position the removed accidental was written at
     * @param removed       The accidental being removed
     * @param excluded      Notes this edit itself deletes or changes, which are neither collected
     *                      nor allowed to stop the scan — they are not going to be there afterwards
     * @return The restatements to offer, in song order (empty when there are none)
     */
    public static List<Restatement> findRestatements(
        Song song,
        Line line,
        int fromIndex,
        int staffPosition,
        StaffElement.Accidental removed,
        Set<StaffElement> excluded) {

        var startLineIndex = song.indexOfLine(line);

        if (startLineIndex < 0) {
            return List.of();
        }

        var removedAdjustment = adjustmentOf(removed);
        var restatements = new ArrayList<Restatement>();

        for (var lineIndex = startLineIndex; lineIndex < song.lineCount(); lineIndex++) {
            var scanLine = song.getLine(lineIndex);
            var start = (lineIndex == startLineIndex) ? (fromIndex + 1) : 0;

            for (var i = start; i < scanLine.effectiveElementCount(); i++) {
                var element = scanLine.getElement(i);
                var own = element.getAccidental();

                if ((own == null)
                    || (element.getStaffPosition() != staffPosition)
                    || !element.getType().isPitchedNote()
                    || excluded.contains(element)) {

                    continue;
                }

                if (adjustmentOf(own) != removedAdjustment) {
                    return restatements;
                }

                restatements.add(new Restatement(scanLine, element));
            }
        }

        return restatements;
    }

    /**
     * Returns the accidentals that must change for an insert, a delete or a paste-replace to
     * preserve every pitch the user did not change and to strand no notation it made redundant.
     *
     * <p>Reads the live line and mutates nothing; the caller applies the result before building
     * any projected layout.
     *
     * @param region The mutation, described before any of it happens
     * @return The changes to apply, in projected element order (empty when the mutation changes no
     *         pitch)
     */
    public static List<AccidentalChange> reconcile(InsertionRegion region) {
        return reconcile(region, RestatementRemoval.NONE);
    }

    /**
     * As {@link #reconcile(InsertionRegion)}, with the restatements the user accepted for this same
     * edit folded in: each accepted note on {@code region}'s line is cleared by the returned
     * changes, and materialization is suspended at the removal's staff positions.
     *
     * @param region  The mutation, described before any of it happens
     * @param removal The accepted restatements and the staff positions their acceptance suppresses
     * @return The changes to apply, in projected element order
     */
    public static List<AccidentalChange> reconcile(InsertionRegion region, RestatementRemoval removal) {
        var line = region.line();
        var inserted = region.inserted();
        var priorAccidentals = region.insertedPriorAccidentals();
        var hasSourceContext = !priorAccidentals.isEmpty();
        var deleteRange = region.deleteRange();
        var insertIndex = region.insertIndex();
        var successorIndex = (deleteRange == null) ? insertIndex : (deleteRange.end() + 1);
        var sequence = new ArrayList<ProjectedElement>();

        // Everything before the insertion point is behind the walk's start position, so it needs
        // only what the backward scan reads off it.
        for (var i = 0; i < insertIndex; i++) {
            sequence.add(ProjectedElement.survivorBeforeMutation(line, i));
        }

        for (var i = 0; i < inserted.size(); i++) {
            var element = inserted.get(i);
            var priorAccidental = hasSourceContext ? priorAccidentals.get(i) : null;

            sequence.add(ProjectedElement.inserted(element, priorAccidental, hasSourceContext));
        }

        for (var i = successorIndex; i < line.effectiveElementCount(); i++) {
            // An accepted restatement is always later in the song than the accidental it restates,
            // so it can only ever land in this trailing run — never in the untouched head above.
            sequence.add(removal.notes().contains(line.getElement(i))
                ? ProjectedElement.removedByConsent(line, i)
                : ProjectedElement.survivor(line, i));
        }

        return reconcileSequence(line, sequence, region.insertedSpans(), insertIndex, removal);
    }

    /**
     * Returns the accidentals that must change for an in-place modification — a pitch shift, an
     * accidental toggle, and the like — to preserve every pitch the user did not change and to
     * strand no notation it made redundant. The notes named in {@code changes} are the ones the
     * user did change, so they are never in the result.
     *
     * <p>Reads the live line and mutates nothing; the caller applies the result before building
     * any projected layout.
     *
     * @param line    The line being modified, in its pre-modification state
     * @param changes Each changed note's intended post-change state
     * @return The changes to apply, in element order (empty when the modification changes no other
     *         note's pitch and strands no accidental)
     */
    public static List<AccidentalChange> reconcileModification(Line line, List<IntendedChange> changes) {
        return reconcileModification(line, changes, RestatementRemoval.NONE);
    }

    /**
     * As {@link #reconcileModification(Line, List)}, with the restatements the user accepted for
     * this same edit folded in: each accepted note on {@code line} is cleared by the returned
     * changes, and materialization is suspended at the removal's staff positions.
     *
     * <p>This is also the entry point for a line the edit does not otherwise touch — one that only
     * holds accepted restatements. Such a call passes an empty {@code changes}, which is why the
     * emptiness short-circuit has to consider the removal too.
     *
     * @param line    The line being modified, in its pre-modification state
     * @param changes Each changed note's intended post-change state
     * @param removal The accepted restatements and the staff positions their acceptance suppresses
     * @return The changes to apply, in element order
     */
    public static List<AccidentalChange> reconcileModification(
        Line line, List<IntendedChange> changes, RestatementRemoval removal) {

        var removedOnLine = new ArrayList<Integer>();

        for (var i = 0; i < line.elementCount(); i++) {
            if (removal.notes().contains(line.getElement(i))) {
                removedOnLine.add(i);
            }
        }

        if (changes.isEmpty() && removedOnLine.isEmpty()) {
            return List.of();
        }

        var changeByIndex = new ArrayList<@Nullable IntendedChange>(
            Collections.nCopies(line.elementCount(), null));
        var lowestChangedIndex = Integer.MAX_VALUE;

        for (var change : changes) {
            changeByIndex.set(change.index(), change);
            lowestChangedIndex = Math.min(lowestChangedIndex, change.index());
        }

        for (var index : removedOnLine) {
            lowestChangedIndex = Math.min(lowestChangedIndex, index);
        }

        var sequence = new ArrayList<ProjectedElement>();

        for (var i = 0; i < line.elementCount(); i++) {
            var change = changeByIndex.get(i);

            if (removal.notes().contains(line.getElement(i))) {
                sequence.add(ProjectedElement.removedByConsent(line, i));
                continue;
            }

            if (change == null) {
                // Below the lowest changed index the walk never visits the position, so it needs
                // only what the backward scan reads off it.
                sequence.add((i < lowestChangedIndex)
                    ? ProjectedElement.survivorBeforeMutation(line, i)
                    : ProjectedElement.survivor(line, i));
                continue;
            }

            sequence.add(ProjectedElement.changed(line.getElement(i), change));
        }

        return reconcileSequence(line, sequence, List.of(), lowestChangedIndex, removal);
    }

    /**
     * Walks {@code sequence} left to right from {@code startPosition}, emitting an
     * {@link AccidentalChange} for every protected note whose sounding accidental would otherwise
     * change, and for every surviving note whose own accidental this edit made redundant. Nothing
     * before the mutation point can change, which is why the walk starts there.
     *
     * <p>Each emitted change is written back onto the projected position before the walk moves on,
     * so the rest of the pass resolves against it: a materialized accidental becomes explicit, and
     * a removed one stops being seen at all. That is bound 2 of the class javadoc, satisfied
     * structurally rather than by an early exit.
     *
     * <p>{@code removal}'s suppressed staff positions are consumed as the walk runs: a position
     * leaves the set at the first explicit accidental the walk meets there, because that accidental
     * re-establishes the context and ends the region the user consented to change.
     */
    private static List<AccidentalChange> reconcileSequence(
        Line line,
        List<ProjectedElement> sequence,
        List<Span> insertedSpans,
        int startPosition,
        RestatementRemoval removal) {

        var suppressedStaffPositions = new HashSet<>(removal.suppressedStaffPositions());
        var ties = collectTies(line, insertedSpans);
        var positionsByElement = new IdentityHashMap<StaffElement, Integer>();

        for (var position = 0; position < sequence.size(); position++) {
            positionsByElement.put(sequence.get(position).element, position);
        }

        var accidentalChanges = new ArrayList<AccidentalChange>();

        for (var position = startPosition; position < sequence.size(); position++) {
            var projected = sequence.get(position);

            // Ahead of everything below, so a barline or a repeat is never a candidate for any of
            // it.
            if (!projected.element.getType().isPitchedNote()) {
                continue;
            }

            // An explicit accidental re-establishes the context at its staff position, which is
            // exactly where the consented pitch change stops propagating. Ahead of the skips below
            // because that is true of the accidental whoever wrote it — including one the user is
            // writing with this very edit, and including one on a tied note.
            if (projected.explicit != null) {
                suppressedStaffPositions.remove(projected.staffPosition);
            }

            // An accepted restatement is not reconciled — the user already decided it goes. It is
            // emitted here rather than applied by the caller so that every accidental this edit
            // writes travels one path, and so a refused fit gate rolls this back with the rest.
            if (projected.consentRemoved) {
                accidentalChanges.add(new AccidentalChange(projected.element, null));
                continue;
            }

            // Must stay below the consent-removed test above, which flags its notes user-changed
            // too: reached first, this skip would swallow their clearing changes.
            if (projected.userChanged) {
                continue;
            }

            // A tied note has no pitch of its own to protect, so it is a candidate for neither
            // materialization nor removal.
            if (endsTie(ties, positionsByElement, projected.element, position)) {
                continue;
            }

            var after = resolveOverProjection(line, sequence, position);

            if (projected.explicit != null) {
                if (removesRedundantAccidental(projected, after)) {
                    // Clearing it here is what lets the rest of the pass resolve past it — the
                    // same mechanism the materialization below relies on.
                    projected.explicit = null;
                    accidentalChanges.add(new AccidentalChange(projected.element, null));
                }

                continue;
            }

            if (adjustmentOf(projected.before) == adjustmentOf(after)) {
                continue;
            }

            // The user consented to this position's pitch changing. Materializing here would hand
            // the removed accidental straight back and defeat the removal — see the class javadoc.
            if (suppressedStaffPositions.contains(projected.staffPosition)) {
                continue;
            }

            var accidental = (projected.before != null) ? projected.before : StaffElement.Accidental.NATURAL;
            projected.explicit = accidental;
            accidentalChanges.add(new AccidentalChange(projected.element, accidental));
        }

        return accidentalChanges;
    }

    /**
     * Whether the note at {@code projected} — which carries an explicit accidental of its own —
     * should have it cleared, given the context {@code after} that now arrives at it. Both
     * conditions of the mirror rule must hold:
     * <ol>
     *   <li>this edit <em>moved</em> the context arriving at the note, and</li>
     *   <li>the note's own accidental now sounds the same as that context, so drawing it says
     *       nothing.</li>
     * </ol>
     * Together they make an accidental that was <em>already</em> redundant when it was written
     * unremovable, because "already redundant" means {@code adjustment(own) ==
     * adjustment(contextBefore)}, which with condition 2 forces {@code adjustment(contextBefore) ==
     * adjustment(after)} and so contradicts condition 1. That is what keeps a deliberate
     * restatement alive through every edit that does not move its context — see the class javadoc
     * for why that property, and not a heuristic, is what makes removal safe unattended.
     *
     * <p>Only a surviving note is a candidate: an inserted or pasted note keeps the notation it
     * arrived with.
     */
    private static boolean removesRedundantAccidental(
        ProjectedElement projected, StaffElement.@Nullable Accidental after) {

        return projected.survivor
            && (adjustmentOf(projected.contextBefore) != adjustmentOf(after))
            && (adjustmentOf(projected.explicit) == adjustmentOf(after));
    }

    /**
     * Resolves the accidental in effect for the note at {@code position} <em>after</em> the
     * mutation, by scanning back over the projected sequence with the rules
     * {@link StaffElement#findEffectiveAccidental} uses on a real line: stop at any barline or
     * repeat, match an earlier position with the same staff position and a non-null explicit
     * accidental, and otherwise fall back to the key signature.
     *
     * <p>Which key that is depends on the position, because a mid-line key signature changes it —
     * and it is read off the <em>projected</em> sequence, not off the line, because the mutation
     * being previewed may itself add or remove one. That is what keeps this resolver and
     * {@link StaffElement#findEffectiveAccidental} agreeing about a pitch: a preview and the
     * committed result disagreeing is exactly the failure this guards.
     *
     * <p>The resolver's tie escape — carrying an accidental across a barrier through a tie — has
     * no counterpart here, and cannot need one: it could only ever fire for a note ending a tie
     * anchored earlier in the sequence, and {@link #reconcileSequence} drops every such note
     * before the scan runs. See the tie exclusion in the class javadoc.
     *
     * <p>The note's own explicit accidental is not consulted, matching the resolver's contract:
     * callers check it first. That is exactly what the removal branch of
     * {@link #reconcileSequence} needs — the context arriving at the note, with the note's own
     * accidental left out of it.
     */
    private static StaffElement.@Nullable Accidental resolveOverProjection(
        Line line, List<ProjectedElement> sequence, int position) {

        var target = sequence.get(position);

        for (var scanPosition = position - 1; scanPosition >= 0; scanPosition--) {
            var candidate = sequence.get(scanPosition);
            var elementType = candidate.element.getType();

            if (elementType.cancelsAccidentals()) {
                return StaffElement.keyAccidentalFor(
                    keyOverProjection(line, sequence, scanPosition), target.staffPosition);
            }

            if ((candidate.staffPosition == target.staffPosition) && (candidate.explicit != null)) {
                return candidate.explicit;
            }
        }

        // The scan passed every earlier position without meeting a barrier, and a key signature is
        // a barrier, so there is none in front of this note: the line's own running key stands.
        return StaffElement.keyAccidentalFor(line.getRunningKey(), target.staffPosition);
    }

    /**
     * Returns the key in effect at {@code position} in the projected sequence: the key of the last
     * {@link KeySignatureElement} at or before it, and otherwise {@code line}'s running key.
     *
     * <p>The mirror of {@link Line#keyAt} over a projection rather than over the live element
     * list, and it has to be — the projection is what the line will hold once the mutation
     * commits, key signatures added or removed included.
     *
     * @param line      the destination line, whose running key is the fallback
     * @param sequence  the projected element sequence
     * @param position  the position within {@code sequence} to resolve at, inclusive
     * @return the key in effect there; never null
     */
    private static Key keyOverProjection(
        Line line, List<ProjectedElement> sequence, int position) {

        for (var scanPosition = position; scanPosition >= 0; scanPosition--) {
            if (sequence.get(scanPosition).element instanceof KeySignatureElement keySignature) {
                return keySignature.getKey();
            }
        }

        return line.getRunningKey();
    }

    /**
     * Whether the note at {@code position} ends a tie whose anchor lies earlier in the projected
     * sequence — the case the tie exclusion of the class javadoc covers. An anchor that is not in
     * the projected sequence, because this very mutation deletes it, leaves no tie to honor.
     *
     * <p>The end element is matched by reference identity: {@link StaffElement} overrides neither
     * {@code equals} nor {@code hashCode}, so a detached clone would otherwise match nothing
     * meaningful.
     */
    private static boolean endsTie(
        List<? extends Tie> ties,
        IdentityHashMap<StaffElement, Integer> positionsByElement,
        StaffElement element,
        int position) {

        for (var tie : ties) {
            if (tie.getEndElement() != element) {
                continue;
            }

            var anchor = tie.getAnchorElement();

            if (anchor == null) {
                continue;
            }

            var anchorPosition = positionsByElement.get(anchor);

            if ((anchorPosition != null) && (anchorPosition < position)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns every tie the exclusion has to consider: the destination line's own ties plus
     * the inserted fragment's, which are not on the line yet. Non-tie spans (slurs and the like)
     * carry no accidental and are ignored.
     */
    private static List<Tie> collectTies(Line line, List<Span> insertedSpans) {
        var ties = new ArrayList<>(line.findTies());

        for (var span : insertedSpans) {
            if (span instanceof Tie tie) {
                ties.add(tie);
            }
        }

        return ties;
    }

    /**
     * Returns the sounding adjustment in semitones of an accidental, treating null — no
     * accidental at all — as no adjustment. Accidentals are only ever compared through this:
     * {@code null} and {@code NATURAL} sound alike.
     */
    private static int adjustmentOf(StaffElement.@Nullable Accidental accidental) {
        return StaffElement.getPitchAdjustment(accidental);
    }

    /**
     * One position in the projected element sequence: the element that will sit there, the staff
     * position and explicit accidental it will have, and what it sounded like <em>before</em> the
     * mutation. {@code explicit} is the only mutable part — it is updated whenever a change is
     * emitted, so the rest of the pass resolves against it.
     *
     * <p>Built through {@link #survivor}, {@link #inserted} and {@link #changed} rather than
     * directly: which population a position belongs to decides both what its "before" means and
     * whether it may be materialized or de-materialized at all, and three named factories say that
     * where a row of positional booleans would not.
     */
    private static final class ProjectedElement {
        private final StaffElement element;
        private final int staffPosition;

        /**
         * What this note sounded like before the mutation: its own accidental when it has one,
         * else the context it inherited. The materialize branch preserves this.
         */
        private final StaffElement.@Nullable Accidental before;

        /**
         * The context that arrived at this note before the mutation, <em>ignoring</em> its own
         * accidental. Meaningful only for a survivor, which is the only population the removal
         * branch considers — nothing else has a pre-mutation context on this line to compare with.
         */
        private final StaffElement.@Nullable Accidental contextBefore;

        private StaffElement.@Nullable Accidental explicit;

        /** True for a note the user is changing or creating — never protected, never emitted. */
        private final boolean userChanged;

        /** True for a note that is on the line already and stays there through the mutation. */
        private final boolean survivor;

        /**
         * True for a restatement the user accepted for removal. Such a note is also flagged
         * {@link #userChanged}, since the user did decide it, so the walk must test this flag
         * <em>first</em> — the user-changed skip would otherwise swallow the clearing change this
         * one exists to emit.
         */
        private final boolean consentRemoved;

        private ProjectedElement(
            StaffElement element,
            int staffPosition,
            StaffElement.@Nullable Accidental before,
            StaffElement.@Nullable Accidental contextBefore,
            StaffElement.@Nullable Accidental explicit,
            boolean userChanged,
            boolean survivor,
            boolean consentRemoved) {

            this.element = element;
            this.staffPosition = staffPosition;
            this.before = before;
            this.contextBefore = contextBefore;
            this.explicit = explicit;
            this.userChanged = userChanged;
            this.survivor = survivor;
            this.consentRemoved = consentRemoved;
        }

        /**
         * The projected position for the element at {@code index} of the unmutated {@code line}:
         * the pitch it has today, resolved on the live line, and its own explicit accidental if it
         * has one.
         *
         * <p>The context is resolved whether or not the note carries an accidental of its own,
         * because the removal branch compares against the context specifically — a note with an
         * accidental is exactly the case that branch exists for.
         */
        private static ProjectedElement survivor(Line line, int index) {
            var element = line.getElement(index);
            var own = element.getAccidental();
            var contextBefore = element.findEffectiveAccidental(line, index);
            var before = (own != null) ? own : contextBefore;

            return new ProjectedElement(
                element, element.getStaffPosition(), before, contextBefore, own, false, true, false);
        }

        /**
         * The projected position for a surviving element that sits <em>before</em> the mutation
         * point, which is where {@link #reconcileSequence} starts its walk.
         *
         * <p>Such a position is never a candidate, so its {@code before} and {@code contextBefore}
         * are never read: the backward scan behind it reads only the element's type, its staff
         * position, and its explicit accidental. Resolving what it sounded like anyway would run
         * one backward scan per element on the untouched part of the line and discard every
         * result, so both are left null here.
         *
         * <p><b>This factory is only correct below the walk's start position.</b> A position built
         * with it that the walk actually visits would read as if it had sounded unaltered, which
         * is a claim, not an absence — use {@link #survivor} for anything at or after the start.
         */
        private static ProjectedElement survivorBeforeMutation(Line line, int index) {
            var element = line.getElement(index);

            return new ProjectedElement(
                element, element.getStaffPosition(), null, null, element.getAccidental(), false, true,
                false);
        }

        /**
         * The projected position for a surviving note whose explicit accidental the user accepted
         * for removal — a restatement of an accidental this same edit takes away.
         *
         * <p>It reads as already cleared, so the rest of the pass resolves past it exactly as it
         * does past an accidental the mirror rule removes, and it is never a candidate itself: the
         * user decided it, so there is nothing left to reconcile about it.
         */
        private static ProjectedElement removedByConsent(Line line, int index) {
            var element = line.getElement(index);

            return new ProjectedElement(
                element, element.getStaffPosition(), null, null, null, true, true, true);
        }

        /**
         * The projected position for an element this mutation brings in.
         *
         * @param priorAccidental  What the element sounded like in its source context, or null
         *                         when it sounded unaltered there or has no source context at all
         * @param hasSourceContext Whether the element comes with a source context. Without one it
         *                         has no pitch it "had" — a note the user is creating — so it is
         *                         never a candidate itself; only the notes that follow it are.
         */
        private static ProjectedElement inserted(
            StaffElement element,
            StaffElement.@Nullable Accidental priorAccidental,
            boolean hasSourceContext) {

            var own = element.getAccidental();
            var before = (own != null) ? own : priorAccidental;

            return new ProjectedElement(
                element, element.getStaffPosition(), before, null, own, !hasSourceContext, false, false);
        }

        /**
         * The projected position for a note the user is changing in place. Its "before" is its own
         * intended accidental, so it can never be reconciled against itself.
         */
        private static ProjectedElement changed(StaffElement element, IntendedChange change) {
            return new ProjectedElement(
                element, change.staffPosition(), change.accidental(), null, change.accidental(),
                true, false, false);
        }
    }
}
