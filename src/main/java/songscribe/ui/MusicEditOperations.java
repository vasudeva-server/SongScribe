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

package songscribe.ui;

import module java.desktop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.HashSet;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.mutation.ElementField;
import songscribe.dom.Beam;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.EndingValidationResult;
import songscribe.dom.Line;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Hairpin;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * Handles music editing operations for a song.
 * Extracted from ScoreView.java as part of Phase 5 of the ScoreView Cleanup refactoring.
 */
public final class MusicEditOperations {

    private static final int MIN_CONTENT_ELEMENTS = 4;

    // A brand new hairpin needs something to slope across, so it takes at least
    // two pitched notes. Extending an existing hairpin has no such requirement.
    private static final int MIN_HAIRPIN_NOTES = 2;

    // Mutable so the same MusicEditOperations instance can outlive a document
    // load — ScoreView holds it across setSong(), avoiding stale references.
    private Song song;
    private final SelectionCoordinator coordinator;

    public MusicEditOperations(
        Song song,
        SelectionCoordinator coordinator
    ) {
        this.song = song;
        this.coordinator = coordinator;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    // ========== Beaming Operations ==========

    public boolean canToggleBeaming() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canToggleBeaming();
    }

    public void toggleBeaming() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();

        // The beam spans the selection's non-grace endpoints; a leading or trailing grace
        // note is left outside it (refs #592).
        var beginIndex = state.getNonGraceSelectionBegin();
        var endIndex = state.getNonGraceSelectionEnd();

        if (beginIndex < 0 || beginIndex == endIndex) {
            return;
        }

        line.withModification(() -> {
            // Looked up rather than asked via Line.sameBeamAt: the remove branch needs the
            // Beam object itself, and going through the boolean would only mean finding it
            // again afterwards.
            var beginBeam = line.findBeamAt(beginIndex);

            //noinspection ObjectEquality
            if (beginBeam == null || beginBeam != line.findBeamAt(endIndex)) {
                var anchorElement = line.getElement(beginIndex);
                var endElement = line.getElement(endIndex);
                line.addBeaming(new Beam(anchorElement, endElement));
            } else {
                line.removeBeaming(beginBeam);
            }
        });
    }

    /**
     * Toggles a beam between the element at {@code elementIndex} and its nearest preceding
     * non-grace neighbor, and returns whether the line was modified.
     *
     * <p>Static and taking the line explicitly because the operation does not go through the
     * selection coordinator — the caller looks up the state.
     *
     * <p>Grace notes are transparent at both ends: the walk backward skips over them, and a
     * grace note is never itself a beam member, so a grace-note target is refused (refs #592).
     *
     * <p>Each branch that modifies the line re-arms {@code elementIndex} as the insertion
     * target first, so the commit notification promotes the same target straight back rather
     * than clearing it and disarming the key the instant the toggle succeeded. Arming has to
     * stay inside those branches: the refusing paths open no modification bracket, so nothing
     * would ever arrive to consume an arm made ahead of them, and the next unrelated edit
     * would adopt it.
     *
     * <p>When one beam already covers both elements, it is broken <em>between</em> them rather
     * than removed wholesale. A single-element remainder is dropped, because a beam needs two
     * members:
     *
     * <pre>
     *    a         p   t         e
     *    ├────────────────────────┤        before:  one beam [a,e]
     *    ├─────────┤   ├──────────┤        after:   [a,p] and [t,e]
     *
     *    a=p       t             e
     *    ├────────────────────────┤   →    ├──────────┤        only [t,e] survives
     *    a         p           t=e
     *    ├────────────────────────┤   →    ├─────────┤         only [a,p] survives
     *    a=p     t=e
     *    ├─────────┤                  →    (no beam)
     * </pre>
     *
     * The break is a {@link Line#removeBeaming} followed by up to two {@link Line#addBeaming}
     * calls so every step is a tracked mutation and undo restores the original span; mutating
     * the beam's endpoints in place would not be recorded. The two new spans do not re-merge,
     * since {@code addBeaming} merges overlapping spans without absorbing merely adjacent ones.
     * Grace notes between the two elements fall in the gap and belong to neither span.
     *
     * <p>The add branch, by contrast, can fuse two adjacent beam groups into one: {@code
     * addBeaming} widens at both ends, so with beams [0,1] and [2,3], beaming element 2 to its
     * predecessor yields a single [0,3]. That matches what the select-mode toggle does from the
     * same call, and the two must not disagree.
     */
    public static boolean toggleBeamWithPredecessor(Line line, int elementIndex) {
        if (elementIndex < 0 || elementIndex >= line.elementCount()) {
            return false;
        }

        var targetType = line.getElement(elementIndex).getType();

        if (targetType.isGraceNote()) {
            return false;
        }

        var predecessorIndex = line.nearestNonGraceIndex(elementIndex, -1);

        if (predecessorIndex < 0) {
            return false;
        }

        var predecessorBeam = line.findBeamAt(predecessorIndex);

        // The break branch needs the Beam object itself, not just the identity test, so it
        // looks both beams up rather than going through Line.sameBeamAt.
        //noinspection ObjectEquality
        if (predecessorBeam != null && predecessorBeam == line.findBeamAt(elementIndex)) {
            var anchorIndex = predecessorBeam.getAnchorElementIndex();
            var endIndex = predecessorBeam.getEndElementIndex();

            EditModeManager.armInsertion(line, elementIndex);

            line.withModification(() -> {
                line.removeBeaming(predecessorBeam);

                if (anchorIndex < predecessorIndex) {
                    line.addBeaming(new Beam(
                        line.getElement(anchorIndex),
                        line.getElement(predecessorIndex)));
                }

                if (elementIndex < endIndex) {
                    line.addBeaming(new Beam(
                        line.getElement(elementIndex),
                        line.getElement(endIndex)));
                }
            });

            return true;
        }

        if (!targetType.isBeamable() || !line.getElement(predecessorIndex).getType().isBeamable()) {
            return false;
        }

        // Conflict: beaming may not connect what a tie already connects, the same rule
        // LineSelectionState.canToggleBeaming applies. A break is never blocked by a tie.
        if (line.sameTieAt(predecessorIndex, elementIndex)) {
            return false;
        }

        var anchorElement = line.getElement(predecessorIndex);
        var endElement = line.getElement(elementIndex);

        EditModeManager.armInsertion(line, elementIndex);
        line.withModification(() -> line.addBeaming(new Beam(anchorElement, endElement)));

        return true;
    }

    // ========== Tie Operations ==========

    public boolean canToggleTie() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canToggleTie();
    }

    public void toggleTie() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();

        // A tie spans the selection itself: its endpoints are the two notes, and any
        // separator between them stays outside the tie (refs #527).
        var beginIndex = state.getSelectionBegin();
        var endIndex = state.getSelectionEnd();

        line.withModification(() -> {
            var exactTie = line.findExactTie(beginIndex, endIndex);

            if (exactTie == null) {
                var anchorElement = line.getElement(beginIndex);
                var endElement = line.getElement(endIndex);
                line.addTie(new Tie(anchorElement, endElement));
            } else {
                line.removeTie(exactTie);
            }
        });

        state.resetTieState();
    }

    // ========== Tuplet Operations ==========

    public TupletToggleInfo canToggleTuplet() {
        var state = coordinator.getActiveSelection();
        return (state != null) ? state.canToggleTuplet() : new TupletToggleInfo(false, null, false);
    }

    /**
     * Handles five cases: (1) tupletSize == 0 with existing tuplet → remove; (2) no existing
     * tuplet and tupletSize > 0 → add; (3) existing tuplet, selection spans its full span,
     * requested grade matches → no-op; (4) existing tuplet, full coverage, different grade →
     * remove then add in one bracket (emits TupletRemoval + TupletAddition); (5) existing
     * tuplet, selection is a strict sub-range → rejected with {@link IllegalStateException}
     * so a programmatic caller cannot silently replace a tuplet with a sub-range tuplet.
     *
     * <p>Callers must pass the {@link TupletToggleInfo} obtained from {@link #canToggleTuplet()}
     * so there is a single source of truth for the decision. Any branch that would have been a
     * silent no-op in the old API now throws {@link IllegalStateException} — the UI gates these
     * via action enable state, so reaching them indicates a caller bug.
     */
    public void toggleTuplet(int tupletSize, TupletToggleInfo info) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var existing = info.existing();

        // A sub-range selection reports canToggle == false, so this must be diagnosed before
        // the generic guard below or the caller loses the reason it was rejected.
        if ((existing != null) && !info.coversExisting()) {
            throw new IllegalStateException(
                "toggleTuplet with a strict sub-range of an existing tuplet is not allowed");
        }

        if (!info.canToggle()) {
            throw new IllegalStateException(
                "toggleTuplet called with info.canToggle() == false; caller must check canToggleTuplet() first");
        }

        var line = state.getLine();

        // The tuplet spans the selection's non-grace endpoints; a leading or trailing grace
        // note is left outside it (refs #592).
        var beginIndex = state.getNonGraceSelectionBegin();
        var endIndex = state.getNonGraceSelectionEnd();

        // canToggleTuplet() rejects a selection without two non-grace elements, so reaching
        // this with no usable span means the caller passed stale info.
        if (beginIndex < 0 || beginIndex == endIndex) {
            throw new IllegalStateException(
                "toggleTuplet requires at least two non-grace elements in the selection");
        }

        if (tupletSize == 0) {
            if (existing == null) {
                throw new IllegalStateException(
                    "toggleTuplet(0) requires an existing tuplet at the selection");
            }

            line.withModification(() -> line.removeTuplet(existing));
            return;
        }

        if (existing == null) {
            var newTuplet = createValidatedTuplet(line, beginIndex, endIndex, tupletSize);
            line.withModification(() -> line.addTuplet(newTuplet));
            return;
        }

        // Re-picking the grade the tuplet already has does nothing. The grade is shown
        // checked (Action.SELECTED_KEY), and a checked radio item that deletes what it
        // reports on would be a trap; Remove is the one way to delete a tuplet.
        if (existing.getGrade() == tupletSize) {
            return;
        }

        var replacement = createValidatedTuplet(line, beginIndex, endIndex, tupletSize);

        line.withModification(() -> {
            line.removeTuplet(existing);
            line.addTuplet(replacement);
        });
    }

    /**
     * Builds a tuplet whose ratio the validator derived for this span.
     * <p>
     * The UI gates tuplet creation on the same validator, so an invalid verdict here
     * means the caller skipped that gate — the same contract as the other
     * {@link IllegalStateException}s in {@link #toggleTuplet}.
     */
    private Tuplet createValidatedTuplet(Line line, int beginIndex, int endIndex, int tupletSize) {
        var lineIndex = song.indexOfLine(line);
        var result = TupletValidator.validateDerived(
            song, line, lineIndex, beginIndex, endIndex, tupletSize,
            TupletValidator.Strictness.STRICT);

        var noteValue = result.noteValue();

        if (!result.valid() || noteValue == null) {
            throw new IllegalStateException(
                "toggleTuplet called for a span that cannot carry a tuplet of " + tupletSize
                    + ": " + result.reason());
        }

        return new Tuplet(
            line.getElement(beginIndex),
            line.getElement(endIndex),
            tupletSize,
            result.normalNotes(),
            noteValue,
            result.noteValueDots());
    }

    // ========== Dynamics Operations ==========

    /**
     * Adds a hairpin over the current selection, or extends an existing same-type
     * hairpin to cover it.
     * <p>
     * The decision and the span both come from a single {@link #resolveHairpinAction()}
     * call — nothing here re-derives them. Two copies of that decision tree would
     * eventually disagree, and the first divergence turns an "Extend Crescendo"
     * click into a second stray crescendo under a mislabeled undo entry.
     */
    public void addHairpinToSelection(boolean crescendo) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var resolution = resolveHairpinAction();
        var resolvedState = resolution.state();
        var extendState = crescendo
            ? HairpinActionState.EXTEND_CRESCENDO
            : HairpinActionState.EXTEND_DIMINUENDO;

        if (resolvedState != HairpinActionState.CAN_ADD && resolvedState != extendState) {
            return;
        }

        // One action performs both Add and Extend, so the op name is chosen per
        // invocation rather than at construction time.
        //
        // These are the same keys HairpinAction puts on the menu item. The undo entry
        // and the menu item must read alike — "Extend Crescendo" then "Undo Extend
        // Crescendo" — so they share one string rather than two that happen to match
        // and could later be edited apart.
        String opNameKey;

        if (resolvedState == HairpinActionState.CAN_ADD) {
            opNameKey = crescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO
                : Strings.ACTION_HAIRPIN_DIMINUENDO;
        } else {
            opNameKey = crescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND
                : Strings.ACTION_HAIRPIN_DIMINUENDO_EXTEND;
        }

        var line = state.getLine();

        // The span is always the resolved one, never a degenerate single element left
        // for the merge to absorb: a one-element hairpin is a shape the model never
        // otherwise produces, and it would survive as a stray if the merge missed it.
        var anchorElement = line.getElement(resolution.spanBegin());
        var endElement = line.getElement(resolution.spanEnd());

        line.withModification(Strings.get(opNameKey), () -> {
            Hairpin added;

            if (crescendo) {
                var hairpin = new Crescendo(anchorElement, endElement);
                line.addCrescendo(hairpin);
                added = hairpin;
            } else {
                var hairpin = new Diminuendo(anchorElement, endElement);
                line.addDiminuendo(hairpin);
                added = hairpin;
            }

            // mergeOverlappingSpans widens the added hairpin in place, so its indices
            // after the add are the merged range — which can reach past what the
            // resolution computed. A point dynamic stranded inside a hairpin breaks the
            // invariant DynamicMarkingAction.updateEnabledState() relies on.
            stripPointDynamics(line, added.getAnchorElementIndex(), added.getEndElementIndex());
        });
    }

    /**
     * Removes the point dynamic (p, mf, ff …) from every element in the inclusive
     * range {@code [begin, end]}.
     * <p>
     * Each removal goes through {@link Line#modifyElement} rather than
     * {@code StaffElement.removeAttachment}, which records no mutation: undo would
     * restore the hairpin while the point dynamics stayed gone forever, silently.
     * {@code modifyElement} nests inside an already-open modification bracket, so one
     * undo reverses both the hairpin and the strip.
     */
    private void stripPointDynamics(Line line, int begin, int end) {
        for (var index = begin; index <= end; index++) {
            var element = line.getElement(index);
            var existing = element.findAttachment(DynamicAttachment.class);

            if (existing != null) {
                line.modifyElement(
                    index,
                    ElementField.DYNAMIC_ATTACHMENT,
                    () -> element.removeAttachment(existing));
            }
        }
    }

    private record HairpinScan(
        List<Crescendo> crescendos,
        List<Diminuendo> diminuendos
    ) {}

    /**
     * Finds every hairpin that overlaps the selection or sits within
     * {@link Line#SPAN_ADJACENCY_REACH} of it — the same neighborhood
     * {@code Line.mergeOverlappingSpans} absorbs when a hairpin is added.
     * <p>
     * The result is flat and untagged: it says which hairpins are nearby, not how
     * each one relates to the selection. Callers derive that from the hairpin's own
     * anchor and end indices.
     */
    private HairpinScan findHairpinsNearSelection(LineSelectionState state) {
        var line = state.getLine();
        var scanBegin = state.getSelectionBegin() - Line.SPAN_ADJACENCY_REACH;
        var scanEnd = state.getSelectionEnd() + Line.SPAN_ADJACENCY_REACH;

        return new HairpinScan(
            nearbySpans(line.getCrescendos(), scanBegin, scanEnd),
            nearbySpans(line.getDiminuendos(), scanBegin, scanEnd));
    }

    /**
     * Returns the spans in {@code spans} that overlap the inclusive range
     * {@code [scanBegin, scanEnd]}.
     */
    private static <H extends Hairpin> List<H> nearbySpans(List<H> spans, int scanBegin, int scanEnd) {
        var nearby = new ArrayList<H>();

        for (var span : spans) {
            if (span.overlaps(scanBegin, scanEnd)) {
                nearby.add(span);
            }
        }

        return nearby;
    }

    /**
     * What the hairpin menu items should show for the current selection.
     */
    public enum HairpinActionState {
        /** Both menu items read "Add …", disabled — the selection cannot host a hairpin. */
        INELIGIBLE,
        /** Both items read "Add …", enabled. */
        CAN_ADD,
        /** Crescendo item reads "Extend Crescendo", enabled; diminuendo reads "Add Diminuendo", disabled. */
        EXTEND_CRESCENDO,
        /** Diminuendo item reads "Extend Diminuendo", enabled; crescendo reads "Add Crescendo", disabled. */
        EXTEND_DIMINUENDO,
        /** Both items read "Add …", disabled — hairpins are present but no extension is possible. */
        BLOCKED
    }

    /**
     * The resolved hairpin action for the current selection: what the menu should
     * show, and the exact span the execution path will use.
     *
     * <p>Returned by a single call so the label and the mutation cannot disagree —
     * {@code addHairpinToSelection} consumes this rather than recomputing.
     * {@code spanBegin}/{@code spanEnd} are meaningful only for
     * {@code CAN_ADD} and {@code EXTEND_*}; both are -1 otherwise.
     */
    public record HairpinResolution(
        HairpinActionState state,
        int spanBegin,
        int spanEnd
    ) {}

    /**
     * Resolves what the hairpin menu items should do for the current selection.
     * <p>
     * <b>Step 0 — input guard.</b> No selection, or a selection that is not an
     * element selection (a slide, ending or hairpin selection leaves the element
     * indices at -1) → {@code INELIGIBLE}. This has to come first: nothing below
     * may touch an element index before it passes.
     * <p>
     * <b>Step 1 — structural eligibility</b> of the selection's {@code begin} and
     * {@code end} (see {@link #isHairpinEligibleSpan}). The end must be a pitched
     * note; the start must be a pitched note or a grace note whose host is one;
     * the range may not cross a repeat or a non-single barline. Any failure →
     * {@code INELIGIBLE}. There is deliberately no note-count test here — count is
     * an add-only gate and lives in Step 2, because a single note adjacent to an
     * existing hairpin extends it.
     * <p>
     * <b>Step 2 — hairpin relation analysis</b> over the candidates from
     * {@link #findHairpinsNearSelection}, each classified from its own anchor and
     * end indices:
     *
     * <pre>
     * ┌── no candidate ─────────────────────────────────────┬─► count &gt;= 2 ──► CAN_ADD
     * │                                                     └─► count &lt;  2 ──► INELIGIBLE
     * │       count = #{ i in [begin, end] : isPitchedNote(i) }
     * │
     * ├── an OPPOSITE-type hairpin is among the candidates ─────────────────► BLOCKED
     * │
     * ├── selection lies entirely inside one same-type hairpin ─────────────► BLOCKED
     * │   (no extension possible)
     * │
     * └── same-type candidates only, and the selection reaches
     *     outside their spans (left, right, or both) ── NO count requirement
     *           │
     *           ├─ union span crosses a structural boundary ──────────────► BLOCKED
     *           │
     *           └─ otherwise ──────────────► EXTEND_CRESCENDO / EXTEND_DIMINUENDO
     * </pre>
     *
     * <b>Extend eligibility</b> means all four of: the selection includes notes
     * inside or touching a same-type hairpin; it includes notes outside that
     * hairpin's span; no opposite-type hairpin is a candidate; and the union span
     * crosses no structural boundary. Extension may run left, right, or both
     * directions at once.
     * <p>
     * Adjacency counts as a relation, because {@code Line.addHairpin} always
     * absorbs adjacent same-type spans — treating adjacency as extension is what
     * keeps the menu label honest about what the model is about to do. Same-type
     * neighbours are absorbed rather than blocking: two same-type hairpins
     * separated by the selection merge into one wide gesture, exactly what
     * {@code mergeOverlappingSpans} would do anyway. Only an opposite-type hairpin
     * blocks, because a crescendo and a diminuendo cannot occupy one span; when
     * both types are candidates the result is {@code BLOCKED} regardless of which
     * action the user picks.
     *
     * <pre>
     * Worked examples (no hairpins anywhere near the selection)
     * ─────────────────────────────────────────────────────────
     *   ♪gr ♩  ♩  𝄽  ♩       count = 3  ✓   begin = grace ✓  CAN_ADD
     *   ♪gr ♩                count = 1  ✗   grace + host is one note
     *   ♩                    count = 1  ✗   single note, nothing to extend → INELIGIBLE
     *   ♩  𝄽  ♪gr            end is a grace note        ✗  Step 1
     *   ♩  𝄽  𝄽              end is a rest              ✗  Step 1
     *   ♩  ♩  ┃┃ ♩           spansStructuralBoundary    ✗  Step 1
     *   ♩  ♩  │  ♩           single barline is fine     ✓  CAN_ADD
     *
     * Single-note extend
     * ──────────────────
     *   ♩  ♩  ♩  ♩          selection = the 4th note only
     *   └─cresc─┘  ▲
     *              └─ adjacent to the crescendo → EXTEND_CRESCENDO
     *
     *   ♩  ♩  ♩  ♩          same note, no hairpin → INELIGIBLE
     *
     * Same-type absorb vs. opposite-type block
     * ────────────────────────────────────────
     *   ♩ ♩ ♩  ♩ ♩ ♩  ♩ ♩ ♩
     *   └cresc┘ └─sel─┘ └cresc┘   → EXTEND_CRESCENDO, union [0,8]
     *
     *   ♩ ♩ ♩  ♩ ♩ ♩  ♩ ♩ ♩
     *   └cresc┘ └─sel─┘ └─dim─┘   → BLOCKED (opposite type present)
     * </pre>
     */
    public HairpinResolution resolveHairpinAction() {
        var state = coordinator.getActiveSelection();

        // A slide, ending or hairpin selection leaves the element selection unset
        // (-1), and Line.getElement does not bounds check, so this guard has to
        // come before anything that touches an element index.
        if (state == null || !state.hasElementSelection()) {
            return ineligibleHairpinResolution();
        }

        var line = state.getLine();
        var begin = state.getSelectionBegin();
        var end = state.getSelectionEnd();

        if (!isHairpinEligibleSpan(line, begin, end)) {
            return ineligibleHairpinResolution();
        }

        var candidates = findHairpinsNearSelection(state);
        var crescendos = candidates.crescendos();
        var diminuendos = candidates.diminuendos();

        if (crescendos.isEmpty() && diminuendos.isEmpty()) {
            if (countPitchedNotes(line, begin, end) < MIN_HAIRPIN_NOTES) {
                return ineligibleHairpinResolution();
            }

            return new HairpinResolution(HairpinActionState.CAN_ADD, begin, end);
        }

        // A crescendo and a diminuendo cannot occupy one span, so neither action
        // can proceed once both types are in play.
        if (!crescendos.isEmpty() && !diminuendos.isEmpty()) {
            return blockedHairpinResolution();
        }

        List<? extends Hairpin> sameType;
        HairpinActionState extendState;

        if (crescendos.isEmpty()) {
            sameType = diminuendos;
            extendState = HairpinActionState.EXTEND_DIMINUENDO;
        } else {
            sameType = crescendos;
            extendState = HairpinActionState.EXTEND_CRESCENDO;
        }

        var spanBegin = begin;
        var spanEnd = end;

        for (var hairpin : sameType) {
            // Each index accessor scans the line for the element, so read them once.
            var anchorIndex = hairpin.getAnchorElementIndex();
            var endIndex = hairpin.getEndElementIndex();

            // A hairpin already covering the whole selection has nothing to extend.
            if (anchorIndex <= begin && endIndex >= end) {
                return blockedHairpinResolution();
            }

            spanBegin = Math.min(spanBegin, anchorIndex);
            spanEnd = Math.max(spanEnd, endIndex);
        }

        // Step 1 validated only the selection. The union is wider, and songs saved
        // by earlier builds may already hold a hairpin crossing a double barline,
        // since the restriction did not exist then. Refuse rather than widen it.
        if (line.spansStructuralBoundary(spanBegin, spanEnd)) {
            return blockedHairpinResolution();
        }

        return new HairpinResolution(extendState, spanBegin, spanEnd);
    }

    private static HairpinResolution ineligibleHairpinResolution() {
        return new HairpinResolution(HairpinActionState.INELIGIBLE, -1, -1);
    }

    private static HairpinResolution blockedHairpinResolution() {
        return new HairpinResolution(HairpinActionState.BLOCKED, -1, -1);
    }

    /**
     * Counts the pitched notes in the inclusive element range {@code [begin, end]}.
     * Grace notes and rests do not count.
     */
    private static int countPitchedNotes(Line line, int begin, int end) {
        var count = 0;

        for (var index = begin; index <= end; index++) {
            if (line.getElement(index).getType().isPitchedNote()) {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns whether the inclusive element range {@code [begin, end]} can carry a
     * hairpin at all, independent of any hairpin already on the line.
     * <p>
     * The end must be a pitched note. What may start one is {@link Line#canAnchorHairpin},
     * shared with the reshaping a deletion performs so the two can never disagree about
     * where a hairpin may begin. A lone grace note fails on the end test. Finally the
     * range may not cross a repeat or a non-single barline.
     */
    private static boolean isHairpinEligibleSpan(Line line, int begin, int end) {
        if (!line.getElement(end).getType().isPitchedNote()) {
            return false;
        }

        if (!line.canAnchorHairpin(begin, end)) {
            return false;
        }

        return !line.spansStructuralBoundary(begin, end);
    }

    // ========== First-Second Ending Operations ==========

    public EndingValidationResult canMakeFirstSecondEnding() {
        var state = coordinator.getActiveSelection();

        if (state == null || !state.hasElementSelection()) {
            return EndingValidationResult.invalid();
        }

        var line = state.getLine();
        var begin = state.getSelectionBegin();
        var end = state.getSelectionEnd();

        // The auto-maintained terminal is never selectable, so if the selection ends
        // just before it, extend end to include it so an ending at the song's
        // end passes structural validation. Only extend if the current end is not
        // already a terminal — if it is, the selection is already properly closed.
        var extendedEnd = end + 1;

        if (!line.getElement(end).getType().isTerminal()
                && extendedEnd < line.elementCount()
                && song.isAutoMaintainedTerminal(line.getElement(extendedEnd), line)) {
            end = extendedEnd;
        }

        // Stage 1: Structural validation
        var rightRepeatIndex = validateEndingStructure(line, begin, end);

        if (rightRepeatIndex < 0) {
            return EndingValidationResult.invalid();
        }

        // Stage 2: Overlap check
        var hasOverlap = hasOverlap(line, begin, end);

        if (hasOverlap) {
            return EndingValidationResult.invalid();
        }

        // Stage 3: Backward search for enclosing repeated section
        var lineIndex = song.indexOfLine(line);
        var hasEnclosing = hasEnclosingRepeat(lineIndex, begin);

        if (!hasEnclosing) {
            return EndingValidationResult.invalid();
        }

        // Stage 4: Preceding element check
        return checkPrecedingElement(line, begin, end);
    }

    // Returns the index of the right repeat within the selection, or -1 if invalid.
    private int validateEndingStructure(Line line, int begin, int end) {
        var contentCount = 0;
        var rightRepeatIndex = -1;

        for (var i = begin; i <= end; i++) {
            var type = line.getElement(i).getType();

            if (type.isNonContentElement()) {
                continue;
            }

            contentCount++;

            if (i < end && (type == ElementType.REPEAT_RIGHT || type == ElementType.REPEAT_LEFT_RIGHT)) {
                if (rightRepeatIndex >= 0) {
                    return -1;
                }

                rightRepeatIndex = i;
            }
        }

        if (contentCount < MIN_CONTENT_ELEMENTS || rightRepeatIndex < 0) {
            return -1;
        }

        // Validate first ending region (between optional leading element and right repeat):
        // one or more content elements, no barlines or repeats
        var firstEndingStart = begin;
        var firstType = line.getElement(begin).getType();

        if (firstType == ElementType.REPEAT_LEFT || firstType == ElementType.SINGLE_BARLINE) {
            firstEndingStart = begin + 1;
        }

        if (!validateEndingRegionContent(line, firstEndingStart, rightRepeatIndex - 1)) {
            return -1;
        }

        // Validate second ending region (between right repeat and terminal):
        // one or more content elements, no barlines or repeats
        if (!validateEndingRegionContent(line, rightRepeatIndex + 1, end - 1)) {
            return -1;
        }

        return rightRepeatIndex;
    }

    // Checks that a region contains one or more content elements and
    // no barlines or repeats (non-content elements are allowed).
    private boolean validateEndingRegionContent(Line line, int from, int to) {
        var hasContent = false;

        for (var i = from; i <= to; i++) {
            var type = line.getElement(i).getType();

            if (type.isNonContentElement()) {
                continue;
            }

            if (type.isBarLine() || type.isRepeat()) {
                return false;
            }

            if (type.isDuration()) {
                hasContent = true;
            }
        }

        return hasContent;
    }

    // Returns true if any element in the selection range overlaps an existing ending span.
    private boolean hasOverlap(Line line, int begin, int end) {
        var endings = LineEndingSupport.findEndings(line);

        for (var i = begin; i <= end; i++) {
            if (LineEndingSupport.isInsideAnyEnding(endings, i)) {
                return true;
            }
        }

        return false;
    }

    // Walks backward from just before the selection start, across as many preceding
    // lines as needed, looking for an enclosing repeated section.
    private boolean hasEnclosingRepeat(int lineIndex, int selectionBegin) {
        // Determine starting point for backward search. A negative element index means
        // the selection starts at the head of its line, so the walk below skips the
        // current line entirely: it either continues onto the previous line, or — when
        // there is none — falls straight through and reports the song start.
        var searchLineIndex = lineIndex;
        var searchElementIndex = selectionBegin - 1;

        // Walk backward
        while (searchLineIndex >= 0) {
            var searchLine = song.getLine(searchLineIndex);

            while (searchElementIndex >= 0) {
                var type = searchLine.getElement(searchElementIndex).getType();

                if (type == ElementType.REPEAT_LEFT || type == ElementType.REPEAT_LEFT_RIGHT) {
                    return true;
                }

                if (type == ElementType.REPEAT_RIGHT
                        || type == ElementType.DOUBLE_BARLINE
                        || type == ElementType.FINAL_DOUBLE_BARLINE) {
                    return false;
                }

                // All other elements are skipped
                searchElementIndex--;
            }

            searchLineIndex--;

            if (searchLineIndex >= 0) {
                searchElementIndex = song.getLine(searchLineIndex).elementCount() - 1;
            }
        }

        // Reached the beginning of the song without meeting a section delimiter —
        // the song start acts as an implicit left repeat, no matter which line the
        // selection is on.
        return true;
    }

    // Returns the index of the nearest element before selectionBegin that ending
    // validation treats as content, or -1 when the selection has no such predecessor
    // on this line.
    private int indexOfPrecedingContentElement(Line line, int selectionBegin) {
        for (var i = selectionBegin - 1; i >= 0; i--) {
            if (!line.getElement(i).getType().isNonContentElement()) {
                return i;
            }
        }

        return -1;
    }

    // Examines the element preceding the selection start and determines what action
    // is needed (none, span extension, or invalid).
    private EndingValidationResult checkPrecedingElement(
        Line line, int selectionBegin, int selectionEnd
    ) {
        // Grace notes, slides and breath marks are transparent everywhere else in this
        // validation, so skip back over them to reach the element that actually decides
        // where the 1st bracket anchors.
        var precedingElementIndex = indexOfPrecedingContentElement(line, selectionBegin);

        if (precedingElementIndex < 0) {
            // Nothing on this line precedes the selection, so there is no element the
            // span could be extended onto — an ending never spans lines. Whatever
            // precedes on earlier lines has already been vetted by the backward scan
            // for an enclosing repeat.
            return EndingValidationResult.valid(
                EndingValidationResult.PrecedingAction.NONE,
                selectionBegin,
                selectionEnd
            );
        }

        var precedingType = line.getElement(precedingElementIndex).getType();

        if (precedingType.isDuration()) {
            // Note/rest predecessor — anchor the 1st bracket to the note at
            // selectionBegin, whether or not the selection begins with a barline.
            return EndingValidationResult.valid(
                EndingValidationResult.PrecedingAction.NONE,
                selectionBegin,
                selectionEnd
            );
        }

        if (precedingType == ElementType.SINGLE_BARLINE
                || precedingType == ElementType.REPEAT_LEFT
                || precedingType == ElementType.REPEAT_LEFT_RIGHT) {
            // Extend span start backward to include the preceding element
            return EndingValidationResult.valid(
                EndingValidationResult.PrecedingAction.EXTEND_SPAN,
                precedingElementIndex,
                selectionEnd
            );
        }

        // Unreachable today: the only element types left are the right repeat, the
        // double barline and the final double barline, and the backward scan for an
        // enclosing repeat has already rejected the selection when one of those sits
        // before it. Kept as a defensive catch-all for element types added later.
        return EndingValidationResult.invalid();
    }

    public void makeFirstSecondEnding(EndingValidationResult result) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();

        line.withModification(() -> {
            var start = result.getSpanStart();
            var end = result.getSpanEnd();

            // EXTEND_SPAN and NONE both anchor at the pre-computed span bounds
            // with no element insertion.
            var startElement = line.getElement(start);
            var endElement = line.getElement(end);
            line.addRangeElement(new Ending(startElement, endElement));
        });
    }


    // ========== Stem Direction Operations ==========

    private enum StemDirectionChange {
        FLIP,
        AUTO
    }

    public boolean canModifyStemDirection() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canModifyStemDirection();
    }

    public void flipStemDirection() {
        modifyStemDirection(StemDirectionChange.FLIP);
    }

    public void autoStemDirection() {
        modifyStemDirection(StemDirectionChange.AUTO);
    }

    /**
     * Applies a stem change to the element at {@code index}. A non-null {@code newDirection}
     * forces that direction; null restores automatic stem direction, letting the layout engine
     * derive it.
     */
    private static void applyStemChange(
        Line line,
        int index,
        EnumSet<ElementField> stemFields,
        StaffElement.@Nullable Direction newDirection
    ) {
        // Rests, whole notes, and non-note elements carry no stem, so a direction change
        // would be an invisible edit that still dirties the song and pushes an undo entry.
        // Enforced here so it holds for every caller, including the beam-group loop.
        if (!line.getElement(index).getType().isNoteWithStem()) {
            return;
        }

        if (newDirection == null) {
            // Re-enabling auto on an already-auto element changes nothing. Recording a mutation
            // anyway would mark the song modified and push an empty entry onto the undo stack.
            if (line.getElement(index).isStemDirectionAuto()) {
                return;
            }

            line.modifyElement(index, stemFields, () -> line.getElement(index).setStemDirectionAuto(true));
            return;
        }

        line.modifyElement(index, stemFields, () -> {
            var target = line.getElement(index);
            target.setStemDirectionAuto(false);
            target.setDirection(newDirection);
        });
    }

    private void modifyStemDirection(StemDirectionChange change) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var stemFields = EnumSet.of(ElementField.UPPER, ElementField.STEM_DIRECTION_AUTO);

        line.withModification(() -> {
            // Track which beam groups have already been processed to avoid modifying one twice.
            var processedBeams = new HashSet<Beam>();

            for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
                var note = line.getElement(i);

                // applyStemChange already ignores stemless elements. Stopping here as well
                // keeps one from dragging its entire beam group into the edit below.
                if (!note.getType().isNoteWithStem()) {
                    continue;
                }

                // A grace note sitting inside a beam span is not a member — the beam passes
                // over it and it keeps its own stem, so it flips on its own (refs #592).
                var beam = note.getType().isGraceNote() ? null : line.findBeamAt(i);

                if (beam != null) {
                    // Modify the whole beam group together, once per group. The group's new
                    // direction is derived once from its anchor so every member agrees.
                    if (processedBeams.add(beam)) {
                        var anchorIndex = beam.getAnchorElementIndex();
                        var newDirection = flippedDirection(line, anchorIndex, change);

                        for (var j = anchorIndex; j <= beam.getEndElementIndex(); j++) {
                            if (line.getElement(j).getType().isGraceNote()) {
                                continue;
                            }

                            applyStemChange(line, j, stemFields, newDirection);
                        }
                    }
                } else {
                    applyStemChange(line, i, stemFields, flippedDirection(line, i, change));
                }
            }

            // Apply the same change to tie partners that fall outside the selection. Chained
            // ties (note1-2 tied, note2-3 tied separately) must be walked to their full
            // transitive closure so every note in the chain gets updated, not just the
            // immediate partner of a selected note.
            var visited = new TreeSet<Integer>();

            for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
                visited.add(i);
            }

            var tiePartnersToModify = new TreeSet<Integer>();
            var pending = new ArrayDeque<>(visited);

            while (!pending.isEmpty()) {
                var i = pending.remove();
                var tieSpan = line.findTieAt(i);

                if (tieSpan == null) {
                    continue;
                }

                var tieStart = tieSpan.getAnchorElementIndex();
                var tieEnd = tieSpan.getEndElementIndex();

                for (var j = tieStart; j <= tieEnd; j++) {
                    if (visited.add(j)) {
                        pending.add(j);

                        if ((j < state.getSelectionBegin()) || (j > state.getSelectionEnd())) {
                            tiePartnersToModify.add(j);
                        }
                    }
                }
            }

            for (var partnerIndex : tiePartnersToModify) {
                applyStemChange(line, partnerIndex, stemFields, flippedDirection(line, partnerIndex, change));
            }
        });
    }

    /**
     * Returns the direction a {@link StemDirectionChange#FLIP} of the element at {@code index}
     * would install, or null when {@code change} does not need one.
     */
    private static StaffElement.@Nullable Direction flippedDirection(
        Line line,
        int index,
        StemDirectionChange change
    ) {
        if (change != StemDirectionChange.FLIP) {
            return null;
        }

        return line.getElement(index).getDirection().opposite();
    }

    // ========== Tempo Operations ==========

    public boolean canChangeTempo() {
        return coordinator.canChangeTempo();
    }
}
