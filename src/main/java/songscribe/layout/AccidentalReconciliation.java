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
import java.util.IdentityHashMap;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
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
 * {@code null} and {@code NATURAL} sound alike, as do {@code FLAT} and {@code NATURAL_FLAT}, and
 * there is no glyph worth drawing for a difference nobody can hear. The {@code null → NATURAL}
 * direction is the whole cross-key paste case — you cannot write "nothing" and get a natural in a
 * context that alters that pitch.
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
 * scan resets at the line boundary. Removing those needs the notator's judgement and is a separate
 * feature (#681) — deliberately not attempted here.
 *
 * <p>Parenthesized accidentals get no exemption: parentheses record that the notator chose to
 * write something they did not have to, which says nothing about whether a later edit obviated it.
 * Removal applies to <b>surviving</b> notes only — a pasted or inserted note keeps the notation it
 * arrived with, matching the "a fragment carries semantic content" rule that governs the
 * clipboard. Sound is preserved either way; this is only about what is drawn.
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
     * @param insertedSpans            The fragment's own range elements (its ties), which are not
     *                                 yet on the destination line; the projected tie escape needs
     *                                 them
     */
    public record InsertionRegion(
        Line line,
        int insertIndex,
        InsertionSpacingCalculator.@Nullable DeletedRange deleteRange,
        List<StaffElement> inserted,
        List<StaffElement.@Nullable Accidental> insertedPriorAccidentals,
        List<RangeElement> insertedSpans
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
            List<RangeElement> insertedSpans) {

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
        var line = region.line();
        var inserted = region.inserted();
        var priorAccidentals = region.insertedPriorAccidentals();
        var hasSourceContext = !priorAccidentals.isEmpty();
        var deleteRange = region.deleteRange();
        var insertIndex = region.insertIndex();
        var successorIndex = (deleteRange == null) ? insertIndex : (deleteRange.end() + 1);
        var sequence = new ArrayList<ProjectedElement>();

        for (var i = 0; i < insertIndex; i++) {
            sequence.add(ProjectedElement.survivor(line, i));
        }

        for (var i = 0; i < inserted.size(); i++) {
            var element = inserted.get(i);
            var priorAccidental = hasSourceContext ? priorAccidentals.get(i) : null;

            sequence.add(ProjectedElement.inserted(element, priorAccidental, hasSourceContext));
        }

        for (var i = successorIndex; i < line.effectiveElementCount(); i++) {
            sequence.add(ProjectedElement.survivor(line, i));
        }

        return reconcileSequence(line, sequence, region.insertedSpans(), insertIndex);
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
        if (changes.isEmpty()) {
            return List.of();
        }

        var changeByIndex = new ArrayList<@Nullable IntendedChange>(
            Collections.nCopies(line.elementCount(), null));
        var lowestChangedIndex = Integer.MAX_VALUE;

        for (var change : changes) {
            changeByIndex.set(change.index(), change);
            lowestChangedIndex = Math.min(lowestChangedIndex, change.index());
        }

        var sequence = new ArrayList<ProjectedElement>();

        for (var i = 0; i < line.elementCount(); i++) {
            var change = changeByIndex.get(i);

            if (change == null) {
                sequence.add(ProjectedElement.survivor(line, i));
                continue;
            }

            sequence.add(ProjectedElement.changed(line.getElement(i), change));
        }

        return reconcileSequence(line, sequence, List.of(), lowestChangedIndex);
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
     */
    private static List<AccidentalChange> reconcileSequence(
        Line line,
        List<ProjectedElement> sequence,
        List<RangeElement> insertedSpans,
        int startPosition) {

        var ties = collectTies(line, insertedSpans);
        var positionsByElement = new IdentityHashMap<StaffElement, Integer>();

        for (var position = 0; position < sequence.size(); position++) {
            positionsByElement.put(sequence.get(position).element, position);
        }

        var accidentalChanges = new ArrayList<AccidentalChange>();

        for (var position = startPosition; position < sequence.size(); position++) {
            var projected = sequence.get(position);

            if (projected.userChanged) {
                continue;
            }

            // Ahead of both branches, so a barline or a repeat is never a candidate for either.
            if (!projected.element.getType().isPitchedNote()) {
                continue;
            }

            var after = resolveOverProjection(line, sequence, position, ties, positionsByElement);

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
     * mutation, by scanning back over the projected sequence with exactly the rules
     * {@link StaffElement#findEffectiveAccidental} uses on a real line: stop at any barline or
     * repeat, escape that barrier through a tie whose anchor lies before it (repeatedly, for a
     * chain), match an earlier position with the same staff position and a non-null explicit
     * accidental, and otherwise fall back to the destination line's key signature.
     *
     * <p>The note's own explicit accidental is not consulted, matching the resolver's contract:
     * callers check it first. That is exactly what the removal branch of
     * {@link #reconcileSequence} needs — the context arriving at the note, with the note's own
     * accidental left out of it.
     */
    private static StaffElement.@Nullable Accidental resolveOverProjection(
        Line line,
        List<ProjectedElement> sequence,
        int position,
        List<Tie> ties,
        IdentityHashMap<StaffElement, Integer> positionsByElement) {

        var target = sequence.get(position);
        // The note whose incoming tie we may escape a barrier through. It advances to the anchor
        // on each escape, so a chain of ties is followed one link at a time.
        var tieEndElement = target.element;
        var cursor = position;

        barrierEscape:
        while (true) {
            for (var scanPosition = cursor - 1; scanPosition >= 0; scanPosition--) {
                var candidate = sequence.get(scanPosition);
                var elementType = candidate.element.getType();

                if (elementType.isBarLine() || elementType.isRepeat()) {
                    var anchorPosition = tieAnchorPositionBefore(
                        ties, positionsByElement, tieEndElement, scanPosition);

                    if (anchorPosition < 0) {
                        return keyInEffect(line, target.staffPosition);
                    }

                    // Resume at the anchor itself: it may be the note carrying the accidental.
                    // anchorPosition < scanPosition < cursor, so cursor strictly decreases on
                    // every escape and the loop is bounded by the sequence length.
                    tieEndElement = sequence.get(anchorPosition).element;
                    cursor = anchorPosition + 1;
                    continue barrierEscape;
                }

                if ((candidate.staffPosition == target.staffPosition) && (candidate.explicit != null)) {
                    return candidate.explicit;
                }
            }

            return keyInEffect(line, target.staffPosition);
        }
    }

    /**
     * Returns the projected position of the anchor of a tie that ends at {@code tieEndElement}
     * and starts before {@code scanPosition}, or −1 when there is none. An anchor that is not in
     * the projected sequence — deleted by this very mutation — yields no escape.
     *
     * <p>The end element is matched by reference identity: {@link StaffElement} overrides neither
     * {@code equals} nor {@code hashCode}, so a detached clone would otherwise match nothing
     * meaningful.
     */
    private static int tieAnchorPositionBefore(
        List<Tie> ties,
        IdentityHashMap<StaffElement, Integer> positionsByElement,
        StaffElement tieEndElement,
        int scanPosition) {

        for (var tie : ties) {
            if (tie.getEndElement() != tieEndElement) {
                continue;
            }

            var anchor = tie.getAnchorElement();

            if (anchor == null) {
                continue;
            }

            var anchorPosition = positionsByElement.get(anchor);

            if ((anchorPosition != null) && (anchorPosition < scanPosition)) {
                return anchorPosition;
            }
        }

        return -1;
    }

    /**
     * Returns every tie the projected tie escape may follow: the destination line's own ties plus
     * the inserted fragment's, which are not on the line yet. Non-tie spans (slurs and the like)
     * carry no accidental and are ignored.
     */
    private static List<Tie> collectTies(Line line, List<RangeElement> insertedSpans) {
        var ties = new ArrayList<>(line.findTies());

        for (var span : insertedSpans) {
            if (span instanceof Tie tie) {
                ties.add(tie);
            }
        }

        return ties;
    }

    /**
     * Returns the accidental the given line's key signature puts in effect for the pitch class of
     * {@code staffPosition}, or null when the key leaves it unaltered. This mirrors the key
     * fallback that is the last branch of {@link StaffElement#findEffectiveAccidental}.
     */
    private static StaffElement.@Nullable Accidental keyInEffect(Line line, int staffPosition) {
        if (!line.keyExists(StaffElement.getPitchIndex(staffPosition))) {
            return null;
        }

        return (line.getKeyType() == KeyType.FLATS)
            ? StaffElement.Accidental.FLAT
            : StaffElement.Accidental.SHARP;
    }

    /**
     * Returns the sounding adjustment in semitones of an accidental, treating null — no
     * accidental at all — as no adjustment. Accidentals are only ever compared through this:
     * {@code null} and {@code NATURAL} sound alike, as do {@code FLAT} and {@code NATURAL_FLAT}.
     */
    private static int adjustmentOf(StaffElement.@Nullable Accidental accidental) {
        return (accidental == null) ? 0 : StaffElement.getPitchAdjustment(accidental);
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

        private ProjectedElement(
            StaffElement element,
            int staffPosition,
            StaffElement.@Nullable Accidental before,
            StaffElement.@Nullable Accidental contextBefore,
            StaffElement.@Nullable Accidental explicit,
            boolean userChanged,
            boolean survivor) {

            this.element = element;
            this.staffPosition = staffPosition;
            this.before = before;
            this.contextBefore = contextBefore;
            this.explicit = explicit;
            this.userChanged = userChanged;
            this.survivor = survivor;
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
                element, element.getStaffPosition(), before, contextBefore, own, false, true);
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
                element, element.getStaffPosition(), before, null, own, !hasSourceContext, false);
        }

        /**
         * The projected position for a note the user is changing in place. Its "before" is its own
         * intended accidental, so it can never be reconciled against itself.
         */
        private static ProjectedElement changed(StaffElement element, IntendedChange change) {
            return new ProjectedElement(
                element, change.staffPosition(), change.accidental(), null, change.accidental(),
                true, false);
        }
    }
}
