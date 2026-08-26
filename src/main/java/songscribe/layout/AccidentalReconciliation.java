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
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
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
 * <p>Both bounds hold <em>within</em> a line, because the backward scan stays inside it. How far a
 * reconciliation <em>reaches</em> is a separate question, and one edit answers it differently:
 * a key change moves pitches on every line that inherits the key, so it is reconciled over a range
 * of lines — {@link #reconcileReach(List, RestatementRemoval)}, fed by
 * {@link #linesInheriting} — ending where the inheritance chain does. Every other edit is a range
 * of one. See {@code docs/key-changes.md} for the chain and its stopping rule.
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
     * The elements an edit brings onto a line, with what the tie exclusion and the pitch invariant
     * need in order to leave them sounding as they did where they came from.
     *
     * <p>The three lists travel together because they describe one arriving run, and separating
     * them at a call site is three same-typed arguments the compiler cannot tell apart.
     *
     * @param elements         The elements being inserted, in order
     * @param priorAccidentals Either <b>empty</b>, or the same size as {@code elements}. When the
     *                         same size, each entry is that element's effective accidental
     *                         <em>in its source context</em> (null when it sounded unaltered
     *                         there) — the paste case. When <b>empty</b>, the arriving elements
     *                         have no source context and are never materialized against
     *                         themselves; only the notes following them are candidates. That is
     *                         the fresh-insert case: a note the user is creating has no pitch it
     *                         "had", so the invariant does not reach it. "No source context" is
     *                         deliberately <em>not</em> encoded as a list of nulls — null already
     *                         means "sounded unaltered there", which is a different claim
     * @param spans            The arriving run's own spans (its ties), which are not yet on the
     *                         destination line; the tie exclusion needs them, so that a note
     *                         arriving already tied is left alone
     */
    public record ArrivingElements(
        List<StaffElement> elements,
        List<StaffElement.@Nullable Accidental> priorAccidentals,
        List<Span> spans) {

        /** Nothing arrives: what a pure deletion, or a key change edited in place, brings in. */
        public static final ArrivingElements NONE =
            new ArrivingElements(List.of(), List.of(), List.of());

        // Written out rather than compact: NullAway does not carry the type-use @Nullable
        // annotation from the record header onto the synthesized canonical constructor, so a
        // caller passing a list with null entries would otherwise be rejected.
        public ArrivingElements(
            List<StaffElement> elements,
            List<StaffElement.@Nullable Accidental> priorAccidentals,
            List<Span> spans) {

            if (!priorAccidentals.isEmpty() && (priorAccidentals.size() != elements.size())) {
                throw new IllegalArgumentException(
                    "priorAccidentals must be empty or the same size as elements, but was "
                        + priorAccidentals.size() + " for " + elements.size() + " elements");
            }

            this.elements = List.copyOf(elements);

            // List.copyOf rejects nulls, and a null entry here is meaningful ("sounded unaltered
            // in the source context"), so copy through a list that permits them.
            this.priorAccidentals =
                Collections.unmodifiableList(new ArrayList<>(priorAccidentals));
            this.spans = List.copyOf(spans);
        }

        /** A fresh insert: elements with no source context, and no spans of their own. */
        public static ArrivingElements fresh(List<StaffElement> elements) {
            return new ArrivingElements(elements, List.of(), List.of());
        }
    }

    /**
     * What an edit does to the elements of one line it reaches: puts {@code arriving} in at
     * {@code index}, having first taken {@code replacedRange} out when there is one.
     *
     * @param index         Index in the line where {@code arriving} will land, and the first index
     *                      of {@code replacedRange} when there is one
     * @param replacedRange The range the insertion itself replaces, or null for a pure insertion.
     *                      Distinct from {@link ReachedLine#removedRanges()}, which are the
     *                      further ranges a moved key strands elsewhere on the same line
     * @param arriving      The elements coming in; {@link ArrivingElements#NONE} for a pure
     *                      deletion, which is an insertion of nothing
     */
    public record Insertion(
        int index,
        InsertionSpacingCalculator.@Nullable DeletedRange replacedRange,
        ArrivingElements arriving) {

        // Written out rather than compact: NullAway does not carry the type-use @Nullable
        // annotation from the record header onto the synthesized canonical constructor.
        @SuppressWarnings("RedundantRecordConstructor")
        public Insertion(
            int index,
            InsertionSpacingCalculator.@Nullable DeletedRange replacedRange,
            ArrivingElements arriving) {

            this.index = index;
            this.replacedRange = replacedRange;
            this.arriving = arriving;
        }

        /**
         * The first index the line keeps standing after this insertion, and so the first one the
         * projection re-reads off the live line.
         */
        int successorIndex() {
            return replacedRange == null ? index : (replacedRange.end() + 1);
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
     * One line an edit reaches, described <em>before</em> any of it happens.
     *
     * <p>Four things can move a note's context on a line, and all four are stated here: a note the
     * user changed there, the key the line runs in, elements the edit puts in or takes out at one
     * place, and further ranges the edit removes elsewhere along the line. An edit that does only
     * one of them is the same description with the rest empty, rather than a second type — which
     * is what lets the line an edit lands on and the lines it merely re-keys travel in one list,
     * in song order, with no caller having to know which is which.
     *
     * @param line          The line, in its pre-edit state
     * @param runningKey    The key in effect at the <em>start</em> of {@code line} once the edit
     *                      commits. Equal to {@link Line#getRunningKey()} unless this edit moves
     *                      it. A mid-line {@link KeyChangeElement} still overrides it from its own
     *                      index forward, exactly as it does on the committed line
     * @param changes       Each changed note's intended post-change state on {@code line}; empty
     *                      for a line the edit re-keys without touching a note on it
     * @param insertion     What the edit puts in and takes out at one place on {@code line}, or
     *                      null when it changes which elements the line holds nowhere — the
     *                      ordinary in-place modification, and every line that merely inherits a
     *                      moved key
     * @param removedRanges The further inclusive ranges the edit removes from {@code line},
     *                      ascending and pairwise disjoint, as
     *                      {@link StaffElementRun#redundantKeyChangeRanges} reports them. A moved
     *                      key strands every mid-line key change that then restates the key in
     *                      effect before it, and the edit removes each one together with the
     *                      element it is paired with, so a re-keyed line and what disappears from
     *                      it are one description rather than two
     */
    public record ReachedLine(
        Line line,
        Key runningKey,
        List<IntendedChange> changes,
        @Nullable Insertion insertion,
        List<StaffElementRun.EffectiveRange> removedRanges) {

        // Written out rather than compact: NullAway does not carry the type-use @Nullable
        // annotation from the record header onto the synthesized canonical constructor.
        public ReachedLine(
            Line line,
            Key runningKey,
            List<IntendedChange> changes,
            @Nullable Insertion insertion,
            List<StaffElementRun.EffectiveRange> removedRanges) {

            this.line = line;
            this.runningKey = runningKey;
            this.changes = List.copyOf(changes);
            this.insertion = insertion;
            this.removedRanges = List.copyOf(removedRanges);
        }

        /** A line whose key does not move and whose elements stay: the in-place modification. */
        public static ReachedLine of(Line line, List<IntendedChange> changes) {
            return new ReachedLine(line, line.getRunningKey(), changes, null, List.of());
        }

        /**
         * A line a key change re-keys without the user having touched a note on it, carrying the
         * key changes {@code runningKey} strands on it.
         *
         * <p>The stranding query runs here rather than at each call site, so every reach — and so
         * every edit that moves a key — carries the removals it owes without a caller having to
         * know they exist.
         */
        public static ReachedLine reKeyed(Line line, Key runningKey) {
            return new ReachedLine(
                line, runningKey, List.of(), null, line.redundantKeyChangeRanges(runningKey));
        }

        /**
         * A line the edit inserts into, deletes from, or both — the line it lands on.
         *
         * <p>Its running key is the line's own: an insert or a delete cannot move the key a line
         * <em>starts</em> in, only the key it leaves off in, and a mid-line key change the edit
         * brings in or takes away is carried by {@code insertion} rather than by the running key.
         *
         * @param line          the line, in its pre-edit state
         * @param insertion     what the edit puts in and takes out
         * @param removedRanges the further ranges a key the edit brings in strands along the same
         *                      line, all of them at or after {@code insertion}'s successor index
         * @return the description
         */
        public static ReachedLine receiving(
            Line line, Insertion insertion, List<StaffElementRun.EffectiveRange> removedRanges) {

            return new ReachedLine(
                line, line.getRunningKey(), List.of(), insertion, removedRanges);
        }
    }

    /**
     * The accidentals one line of a modification's range has to change, as
     * {@link #reconcileReach(List, RestatementRemoval)} returns them. The caller applies
     * each line's changes to that line.
     *
     * @param line    The line the changes sit on
     * @param changes The changes to apply there, in element order; empty when that line needs none
     */
    public record ReconciledLine(Line line, List<AccidentalChange> changes) {

        public ReconciledLine {
            changes = List.copyOf(changes);
        }
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
     * One explicit accidental an edit is about to remove, as {@link #findRestatements} needs it to
     * resolve every removal of an edit in a single forward pass over the song.
     *
     * <p>Each removal carries its own line: an edit's removals no longer all sit on one line, since
     * a key change removes accidentals everywhere its reach takes it.
     *
     * @param line          The line the removed accidental sits on
     * @param fromIndex     The removed accidental's index on {@code line}; that removal's scan
     *                      starts just after it
     * @param staffPosition The staff position the removed accidental was written at
     * @param accidental    The accidental being removed
     */
    public record AccidentalRemoval(
        Line line, int fromIndex, int staffPosition, StaffElement.Accidental accidental) {}

    /**
     * One {@link AccidentalRemoval}'s progress through the {@link #findRestatements} pass, mutated
     * in place as the pass advances.
     */
    private static final class PendingRemoval {
        private final int lineIndex;
        private final int fromIndex;
        private final int adjustment;
        private boolean stopped = false;

        private PendingRemoval(int lineIndex, int fromIndex, int adjustment) {
            this.lineIndex = lineIndex;
            this.fromIndex = fromIndex;
            this.adjustment = adjustment;
        }

        /** Whether the pass has passed this removal's own position and can start collecting. */
        private boolean hasStarted(int lineIndex, int index) {
            if (lineIndex != this.lineIndex) {
                return lineIndex > this.lineIndex;
            }

            return index > fromIndex;
        }
    }

    /**
     * Returns the later notes that restate any accidental in {@code removals}: for each removal,
     * notes at its staff position carrying an explicit accidental of the same sounding adjustment,
     * from just after its own position through the end of the song.
     *
     * <p>Resolves every removal in a single forward pass over the song, so the cost is the length
     * of the song once, however many removals there are and however many lines they sit on — what
     * makes this affordable for an edit whose removals span a range of lines.
     *
     * <p>Each removal's scan <b>stops at an explicit cancellation</b> — the first explicit
     * accidental at that removal's staff position with a different adjustment. Past that point a
     * matching accidental reads as a fresh decision, not a restatement of the one being removed. A
     * cancellation reached by one removal's scan never stops another removal's scan, even one at
     * the same staff position.
     *
     * <p>No removal's scan stops at a barline or a line boundary, and that is the point: a
     * restatement on a later line is exactly the case no context arithmetic can identify, because
     * line reset makes it non-redundant on its own line. Only the notator can say whether it was
     * still meant.
     *
     * <p>Pure and pre-mutation, like everything else in this class.
     *
     * @param song     The song to scan
     * @param removals The accidentals this edit is about to remove, on whichever lines they sit;
     *                 one whose line the song does not hold is ignored
     * @param excluded Notes this edit itself deletes or changes, which are neither collected nor
     *                 allowed to stop any removal's scan — they are not going to be there afterwards
     * @return The restatements to offer, in song order (empty when there are none)
     */
    public static List<Restatement> findRestatements(
        Song song,
        Set<AccidentalRemoval> removals,
        Set<StaffElement> excluded) {

        var pendingByPosition = new HashMap<Integer, List<PendingRemoval>>();
        var startLineIndex = Integer.MAX_VALUE;

        for (var removal : removals) {
            var lineIndex = song.indexOfLine(removal.line());

            if (lineIndex < 0) {
                continue;
            }

            startLineIndex = Math.min(startLineIndex, lineIndex);
            pendingByPosition
                .computeIfAbsent(removal.staffPosition(), position -> new ArrayList<>())
                .add(new PendingRemoval(lineIndex, removal.fromIndex(), adjustmentOf(removal.accidental())));
        }

        if (pendingByPosition.isEmpty()) {
            return List.of();
        }

        var restatements = new ArrayList<Restatement>();

        for (var lineIndex = startLineIndex; lineIndex < song.lineCount(); lineIndex++) {
            var scanLine = song.getLine(lineIndex);

            for (var i = 0; i < scanLine.effectiveElementCount(); i++) {
                var element = scanLine.getElement(i);
                var own = element.getAccidental();

                if ((own == null) || !element.getType().isPitchedNote() || excluded.contains(element)) {
                    continue;
                }

                var pending = pendingByPosition.get(element.getStaffPosition());

                if (pending == null) {
                    continue;
                }

                var elementAdjustment = adjustmentOf(own);

                for (var state : pending) {
                    if (state.stopped || !state.hasStarted(lineIndex, i)) {
                        continue;
                    }

                    if (elementAdjustment != state.adjustment) {
                        state.stopped = true;
                    } else {
                        restatements.add(new Restatement(scanLine, element));
                    }
                }
            }
        }

        return restatements;
    }

    /**
     * Returns the whole range a change to {@code line}'s own key reconciles: {@code line} itself,
     * in the key it will then run in, followed by every line that inherits from it.
     *
     * <p>A change to a line's own key moves the key it leaves off in only when it holds no mid-line
     * {@link KeyChangeElement}; one that does fixes its end key, so the change reaches no further
     * than that mid-line change does. That is why the tail is derived here rather than asked of the
     * caller — but an edit that <em>adds or removes</em> a mid-line key change moves the end key
     * in a way only that edit knows, and calls {@link #linesInheriting} with it directly.
     *
     * @param line       The line whose own key changes
     * @param runningKey The key {@code line} will run in once the change commits
     * @return The range, in song order, always starting with {@code line}. Each entry carries the
     *         {@link ReachedLine#removedRanges() ranges} the move strands on its line, which the
     *         caller commits inside the same modification bracket as the key move itself
     */
    public static List<ReachedLine> lineKeyChangeReach(Line line, Key runningKey) {
        var keyAtEndOfLine = line.keyAtEndOfLineUnder(runningKey);

        var reach = new ArrayList<ReachedLine>();
        reach.add(ReachedLine.reKeyed(line, runningKey));
        reach.addAll(linesInheriting(line, keyAtEndOfLine));

        return reach;
    }

    /**
     * Returns the lines a deletion of {@code deletedLine} re-keys: every following line that then
     * inherits from the line before it, up to where the inheritance chain stops.
     *
     * <p>The reach is asked of the <em>deleted</em> line, so what comes back is the lines after it,
     * carrying the key the line before it leaves off in — which is exactly the key they will
     * inherit once it is gone. The deleted line is not in the result and needs no reconciliation:
     * every note on it goes away with it. That is the one reach with no head of its own, and the
     * reason a key move has to be able to describe itself without one.
     *
     * <p><b>Deleting line 0 moves nothing.</b> The line that follows it becomes line 0, and the
     * song's key invariant materializes the key it was already inheriting as a key of its own, so
     * it goes on sounding what it sounded and nothing downstream of it moves either.
     *
     * <p>Pure and pre-mutation, as every reconciliation input is.
     *
     * @param deletedLine the line about to be deleted
     * @return The lines the deletion re-keys, in song order; empty when {@code deletedLine} is
     *         line 0. Each entry carries the {@link ReachedLine#removedRanges() ranges} the move
     *         strands on its line, which the caller commits inside the same modification bracket
     *         as the deletion itself
     */
    public static List<ReachedLine> lineDeletionReach(Line deletedLine) {
        var song = deletedLine.getSong();
        var lineIndex = song.indexOfLine(deletedLine);

        if (lineIndex <= 0) {
            return List.of();
        }

        return linesInheriting(deletedLine, song.getLine(lineIndex - 1).keyAtEndOfLine());
    }

    /**
     * Returns the lines a key change reaches <em>beyond</em> the line it lands on: each following
     * line that inherits, carrying the key it then runs in, in song order.
     *
     * <p>The walk stops at the first line that establishes a key of its own — nothing past such a
     * line can move, because its own running key cannot — and at the first line the change leaves
     * running in the key it already ran in, for the same reason. That is the stopping rule
     * {@code docs/key-changes.md} states for the inheritance chain; this is the
     * accidental-reconciliation reach it governs.
     *
     * <p>Pure and pre-mutation, like everything else in this class: the lines are read as they
     * stand and nothing is written to them.
     *
     * @param line           The line the key change lands on, which is <b>not</b> in the result —
     *                       its own reconciliation is the caller's, and takes a different shape for
     *                       an inserted key change than for a change to the line's own key
     * @param keyAtEndOfLine The key {@code line} will leave off in once the change commits: its
     *                       last mid-line {@link KeyChangeElement}'s key when it holds one, and
     *                       its new running key when it does not
     * @return One {@link ReachedLine} per reached line, in song order, each carrying the
     *         {@link ReachedLine#removedRanges() ranges} the change strands on its line; empty
     *         when the change moves nothing downstream or when {@code line} is not in its song
     */
    public static List<ReachedLine> linesInheriting(Line line, Key keyAtEndOfLine) {
        var song = line.getSong();
        var lineIndex = song.indexOfLine(line);

        if (lineIndex < 0) {
            return List.of();
        }

        var reached = new ArrayList<ReachedLine>();
        var runningKey = keyAtEndOfLine;

        for (var index = lineIndex + 1; index < song.lineCount(); index++) {
            var nextLine = song.getLine(index);

            if ((nextLine.getKey() != null) || runningKey.equals(nextLine.getRunningKey())) {
                return reached;
            }

            reached.add(ReachedLine.reKeyed(nextLine, runningKey));

            // A mid-line change on that line fixes the key it leaves off in, so the change stops
            // propagating there even though the line itself was re-keyed. A stranded key change
            // among them cannot alter that: it restates the key already in effect, so the line
            // leaves off in the same key whether or not it survives.
            var lastKeyChangeKey = nextLine.lastKeyChangeKey();

            if (lastKeyChangeKey != null) {
                runningKey = lastKeyChangeKey;
            }
        }

        return reached;
    }

    /**
     * Returns the accidentals that must change for an insert, a delete or a paste-replace to
     * preserve every pitch the user did not change and to strand no notation it made redundant.
     *
     * <p>Reads the live line and mutates nothing; the caller applies the result before building
     * any projected layout.
     *
     * @param reached The line and what the edit does to it, described before any of it happens
     * @return The changes to apply, in projected element order (empty when the edit changes no
     *         pitch)
     */
    public static List<AccidentalChange> reconcile(ReachedLine reached) {
        return reconcile(reached, RestatementRemoval.NONE);
    }

    /**
     * As {@link #reconcile(ReachedLine)}, with the restatements the user accepted for this same
     * edit folded in: each accepted note on {@code reached}'s line is cleared by the returned
     * changes, and materialization is suspended at the removal's staff positions.
     *
     * <p>A {@link ReachedLine#removedRanges() removed range} is left out of the projected sequence
     * entirely. Both a stranded key change and the element it is paired with answer true to
     * {@code ElementType.cancelsAccidentals}, so every note after the pair resolved against a
     * context that pair began; with the pair gone they resolve back to whatever stands earlier on
     * the line, which moves sounding pitches.
     *
     * @param reached The line and what the edit does to it, described before any of it happens
     * @param removal The accepted restatements and the staff positions their acceptance suppresses
     * @return The changes to apply, in projected element order
     */
    public static List<AccidentalChange> reconcile(ReachedLine reached, RestatementRemoval removal) {
        var insertion = reached.insertion();

        return insertion == null
            ? reconcileInPlace(reached, removal)
            : reconcileReceiving(reached, insertion, removal);
    }

    /**
     * {@link #reconcile(ReachedLine, RestatementRemoval)} for a line the edit changes the elements
     * of. Everything below the insertion point is projected as it already stands; the arriving
     * elements go in; the rest of the line follows, less what the edit removes.
     *
     * <p>Every removed range lies at or after the insertion's successor index, so none of them can
     * reach back into the untouched head and the walk's start needs no lowering.
     */
    private static List<AccidentalChange> reconcileReceiving(
        ReachedLine reached, Insertion insertion, RestatementRemoval removal) {

        var line = reached.line();
        var arriving = insertion.arriving();
        var inserted = arriving.elements();
        var priorAccidentals = arriving.priorAccidentals();
        var hasSourceContext = !priorAccidentals.isEmpty();
        var insertIndex = insertion.index();
        var successorIndex = insertion.successorIndex();
        var strandedIndices = indicesIn(reached.removedRanges());
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
            if (strandedIndices.contains(i)) {
                continue;
            }

            // An accepted restatement is always later in the song than the accidental it restates,
            // so it can only ever land in this trailing run — never in the untouched head above.
            sequence.add(removal.notes().contains(line.getElement(i))
                ? ProjectedElement.removedByConsent(line, i)
                : ProjectedElement.survivor(line, i));
        }

        return reconcileSequence(
            new Projection(line, reached.runningKey(), sequence, arriving.spans(), insertIndex),
            removal);
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

        return reconcile(ReachedLine.of(line, changes), removal);
    }

    /**
     * Returns the accidentals that must change for an edit spanning a <em>range</em> of lines to
     * preserve every pitch the user did not change and to strand no notation it made redundant.
     *
     * <p>A key change is the edit that needs this: it moves pitches on every line that inherits
     * the key it changes, and stopping at the line the user clicked on would let notes on the
     * lines after it change pitch with nothing said. Every other edit is a range of one, which is
     * what {@link #reconcile(ReachedLine, RestatementRemoval)} is — the same walk, called once.
     * {@link #linesInheriting} builds the range beyond the line the edit lands on.
     *
     * <p>The lines are reconciled independently: accidental context resets at a line boundary, so
     * nothing one line's walk decides can reach the next. What crosses the boundary is the key,
     * and it arrives as each {@link ReachedLine}'s own {@code runningKey}.
     *
     * <p>Reads the live lines and mutates nothing; the caller applies the result before building
     * any projected layout.
     *
     * @param reach   The edit's range, in song order, each line in its pre-edit state
     * @param removal The accepted restatements and the staff positions their acceptance suppresses
     * @return One entry per line of {@code reach}, in the same order, holding that line's changes
     *         (empty for a line that needs none)
     */
    public static List<ReconciledLine> reconcileReach(
        List<ReachedLine> reach, RestatementRemoval removal) {

        var reconciled = new ArrayList<ReconciledLine>(reach.size());

        for (var reached : reach) {
            reconciled.add(new ReconciledLine(reached.line(), reconcile(reached, removal)));
        }

        return reconciled;
    }

    /** Every element index the given inclusive ranges cover, which is what a projection skips. */
    private static Set<Integer> indicesIn(List<StaffElementRun.EffectiveRange> ranges) {
        var indices = new HashSet<Integer>();

        for (var range : ranges) {
            for (var index = range.begin(); index <= range.end(); index++) {
                indices.add(index);
            }
        }

        return indices;
    }

    /**
     * {@link #reconcile(ReachedLine, RestatementRemoval)} for a line whose elements stay where
     * they are — a pitch shift, an accidental toggle, or a line that merely inherits a moved key.
     *
     * <p>A {@link ReachedLine#removedRanges() removed range} is left out of the projected sequence
     * entirely. Such a range only ever arrives on a line the edit re-keys, and a moved key starts
     * the walk at the top of the line, so every omitted index is above the walk's start: positions
     * below it are unshifted and the start doubles as a position in the shortened sequence, which
     * is what {@link Projection} takes it for.
     *
     * @param reached the line and everything this edit does to it, described pre-edit
     * @param removal the accepted restatements and the staff positions their acceptance suppresses
     * @return the changes to apply, in projected element order
     */
    private static List<AccidentalChange> reconcileInPlace(
        ReachedLine reached, RestatementRemoval removal) {

        var line = reached.line();
        var changes = reached.changes();
        var runningKey = reached.runningKey();
        var deletedRanges = reached.removedRanges();
        var removedOnLine = new ArrayList<Integer>();

        for (var i = 0; i < line.elementCount(); i++) {
            if (removal.notes().contains(line.getElement(i))) {
                removedOnLine.add(i);
            }
        }

        var deletedIndices = indicesIn(deletedRanges);

        // A moved key moves the context arriving at every note on the line, so the walk starts at
        // the top of it — and the emptiness short-circuit cannot fire, however little else the
        // edit does here. A line with ranges to remove is always such a line, which is why the
        // walk start below needs no further lowering to cover them.
        var keyMoved = !runningKey.equals(line.getRunningKey());

        if (changes.isEmpty() && removedOnLine.isEmpty() && !keyMoved) {
            return List.of();
        }

        var changeByIndex = new ArrayList<@Nullable IntendedChange>(
            Collections.nCopies(line.elementCount(), null));
        var lowestChangedIndex = keyMoved ? 0 : Integer.MAX_VALUE;

        for (var change : changes) {
            changeByIndex.set(change.index(), change);
            lowestChangedIndex = Math.min(lowestChangedIndex, change.index());
        }

        for (var index : removedOnLine) {
            lowestChangedIndex = Math.min(lowestChangedIndex, index);
        }

        var sequence = new ArrayList<ProjectedElement>();

        for (var i = 0; i < line.elementCount(); i++) {
            if (deletedIndices.contains(i)) {
                continue;
            }

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

        return reconcileSequence(
            new Projection(line, runningKey, sequence, List.of(), lowestChangedIndex), removal);
    }

    /**
     * A mutation's projected element sequence and everything the walk over it needs: the line the
     * mutation lands on, the key that line will run in afterwards, the fragment's own spans, and
     * the position the walk starts at.
     *
     * @param line          The line, in its pre-mutation state — the source of the ties the
     *                      exclusion consults, and of the pre-mutation contexts the elements
     *                      carry
     * @param runningKey    The key in effect at the start of {@code line} once the mutation
     *                      commits; the fallback a backward scan reaching the start of the line
     *                      resolves against
     * @param elements      The projected sequence, in order
     * @param insertedSpans Spans arriving with an inserted fragment, not yet on {@code line}
     * @param startPosition The first position in {@code elements} the walk visits; nothing before
     *                      it can change
     */
    private record Projection(
        Line line,
        Key runningKey,
        List<ProjectedElement> elements,
        List<Span> insertedSpans,
        int startPosition
    ) {}

    /**
     * Walks {@code projection}'s elements left to right from its start position, emitting an
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
        Projection projection, RestatementRemoval removal) {

        var sequence = projection.elements();
        var suppressedStaffPositions = new HashSet<>(removal.suppressedStaffPositions());
        var ties = collectTies(projection.line(), projection.insertedSpans());
        var positionsByElement = new IdentityHashMap<StaffElement, Integer>();

        for (var position = 0; position < sequence.size(); position++) {
            positionsByElement.put(sequence.get(position).element, position);
        }

        var accidentalChanges = new ArrayList<AccidentalChange>();

        for (var position = projection.startPosition(); position < sequence.size(); position++) {
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

            var after = resolveOverProjection(projection, position);

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
     * accidental, and otherwise fall back to the key.
     *
     * <p>Which key that is depends on the position, because a mid-line key change changes it —
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
        Projection projection, int position) {

        var sequence = projection.elements();
        var target = sequence.get(position);

        for (var scanPosition = position - 1; scanPosition >= 0; scanPosition--) {
            var candidate = sequence.get(scanPosition);
            var elementType = candidate.element.getType();

            if (elementType.cancelsAccidentals()) {
                return StaffElement.keyAccidentalFor(
                    keyOverProjection(projection, scanPosition), target.staffPosition);
            }

            if ((candidate.staffPosition == target.staffPosition) && (candidate.explicit != null)) {
                return candidate.explicit;
            }
        }

        // The scan passed every earlier position without meeting a barrier, and a key change is
        // a barrier, so there is none in front of this note: the key the line will run in stands.
        return StaffElement.keyAccidentalFor(projection.runningKey(), target.staffPosition);
    }

    /**
     * Returns the key in effect at {@code position} in the projected sequence: the key of the last
     * {@link KeyChangeElement} at or before it, and otherwise the key the line will run in.
     *
     * <p>The mirror of {@link Line#keyAt} over a projection rather than over the live element
     * list, and it has to be — the projection is what the line will hold once the mutation
     * commits, key changes added or removed included, and the running key it falls back to is
     * the one the mutation leaves the line in rather than the one it starts in.
     *
     * @param projection the mutation being previewed
     * @param position   the position within the projected sequence to resolve at, inclusive
     * @return the key in effect there; never null
     */
    private static Key keyOverProjection(Projection projection, int position) {
        var sequence = projection.elements();

        for (var scanPosition = position; scanPosition >= 0; scanPosition--) {
            if (sequence.get(scanPosition).element instanceof KeyChangeElement keyChange) {
                return keyChange.getKey();
            }
        }

        return projection.runningKey();
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
