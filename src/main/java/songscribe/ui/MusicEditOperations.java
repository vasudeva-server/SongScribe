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

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.EndingValidationResult;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.SpanBound;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.mutation.ElementField;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.RangeQueries;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * Handles music editing operations for a song.
 * Extracted from ScoreView.java as part of Phase 5 of the ScoreView Cleanup refactoring.
 */
public final class MusicEditOperations {

    private static final int MIN_CONTENT_ELEMENTS = 4;

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

    /**
     * The current selection in the form every query and edit below takes it, or {@code null}
     * when nothing is selected.
     *
     * <p>The coordinator's range additionally carries the anchor a click or drag started from,
     * which matters only while the user is extending the selection. Nothing from here down
     * reads it, so it is dropped at the one point the selection crosses into the edit layer.
     */
    private @Nullable ElementSelection selectedRange() {
        var range = coordinator.getRange();

        return (range != null) ? range.toElementSelection() : null;
    }

    // ========== Beaming Operations ==========

    public boolean canToggleBeaming() {
        var range = selectedRange();
        return (range != null) && RangeQueries.canToggleBeaming(range);
    }

    public void toggleBeaming() {
        var range = selectedRange();

        if (range == null) {
            return;
        }

        var line = range.line();

        // The beam spans the selection's non-grace endpoints; a leading or trailing grace
        // note is left outside it (refs #592).
        var beginIndex = RangeQueries.nonGraceBegin(range);
        var endIndex = RangeQueries.nonGraceEnd(range);

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
     * <p>When one beam already covers both elements, it is broken <em>between</em> them rather
     * than removed wholesale. A single-element remainder is dropped, because a beam needs two
     * members:
     *
     * <p>Breaking a beam {@code [a,e]} between the two selected elements {@code p} and {@code t}
     * yields the two remainders {@code [a,p]} and {@code [t,e]}, minus any remainder of fewer than
     * two members. So the left remainder is dropped when {@code a == p}, the right one when
     * {@code t == e}, and when both hold the beam disappears entirely.
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
        if (!line.hasIndex(elementIndex)) {
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
        // RangeQueries.canToggleBeaming applies. A break is never blocked by a tie.
        if (line.sameTieAt(predecessorIndex, elementIndex)) {
            return false;
        }

        var anchorElement = line.getElement(predecessorIndex);
        var endElement = line.getElement(elementIndex);

        line.withModification(() -> line.addBeaming(new Beam(anchorElement, endElement)));

        return true;
    }

    // ========== Tie Operations ==========

    public boolean canToggleTie() {
        var range = selectedRange();
        return (range != null) && RangeQueries.canToggleTie(range);
    }

    public void toggleTie() {
        var range = selectedRange();

        if (range == null) {
            return;
        }

        toggleTieInRange(range);
    }

    /**
     * Adds or removes the tie the range describes, and returns whether the line was modified.
     *
     * <p>Applies no eligibility rule of its own — {@link RangeQueries#canToggleTie} is the gate,
     * and both entry points consult it before calling here.
     *
     * <p>The single-element case is a cross-line tie, which can find no partner to toggle; every
     * other case changes the line, because the range names the two endpoints outright.
     */
    private static boolean toggleTieInRange(ElementSelection range) {
        var line = range.line();

        // A tie spans the selection itself: its endpoints are the two notes, and any
        // separator between them stays outside the tie (refs #527).
        var beginIndex = range.begin();
        var endIndex = range.end();

        return line.withModificationResult(() -> {
            if (range.size() == 1) {
                return toggleBoundaryTie(line, beginIndex);
            }

            var exactTie = line.findExactTie(beginIndex, endIndex);

            if (exactTie == null) {
                var anchorElement = line.getElement(beginIndex);
                var endElement = line.getElement(endIndex);
                line.addTie(new Tie(anchorElement, endElement));
            } else {
                line.removeTie(exactTie);
            }

            return true;
        });
    }

    /**
     * Toggles a tie between the element at {@code elementIndex} and the note before it, and
     * returns whether the line was modified (#706).
     *
     * <p>Static and taking the line explicitly because the operation does not go through the
     * selection coordinator — the caller looks up the state.
     *
     * <p>Which pair that is — adjacent, separated by a barline, or across a line break — is
     * {@link RangeQueries#tieCandidateWithPredecessor}'s question, along with every rule that
     * decides whether the pair may be tied at all.
     */
    public static boolean toggleTieWithPredecessor(Line line, int elementIndex) {
        var range = RangeQueries.tieCandidateWithPredecessor(line, elementIndex);

        return (range != null) && toggleTieInRange(range);
    }

    /**
     * Toggles the cross-line tie candidate at {@code index} in {@code line}, if any (#493).
     * <p>
     * {@code Span.exactly}/{@code findExactTie} can never match a cross-line half — only an
     * {@code At} bound can equal a queried index, and a cross-line half's far bound is always
     * {@link SpanBound#BEFORE_LINE} or {@link SpanBound#AFTER_LINE} — so add-versus-remove is
     * decided by {@link Line#findTieBetween}, which matches the two real endpoints by identity
     * instead. That also keeps this from ever mistaking a same-line tie that merely ends on
     * the boundary note (a chained tie) for the boundary tie itself: such a tie's elements
     * never equal {@code boundaryTie}'s.
     *
     * @return Whether a partner was found and the tie toggled
     */
    private static boolean toggleBoundaryTie(Line line, int index) {
        var boundaryTie = RangeQueries.boundaryTieAt(line, index);

        if (boundaryTie == null) {
            return false;
        }

        var existingTie = line.findTieBetween(boundaryTie.anchor(), boundaryTie.end());

        if (existingTie == null) {
            line.addTie(new Tie(boundaryTie.anchor(), boundaryTie.end()));
        } else {
            line.removeTie(existingTie);
        }

        return true;
    }

    // ========== Slide Operations ==========

    public boolean canToggleGlissando() {
        var range = selectedRange();
        return (range != null) && RangeQueries.canToggleGlissando(range);
    }

    public void toggleGlissando(@Nullable LyricRenderMetrics lyricRenderMetrics) {
        var range = selectedRange();

        if (range == null) {
            return;
        }

        SlideOperations.toggleGlissando(range, lyricRenderMetrics);
    }

    /**
     * Whether the selection holds any note a fall could hang off.
     *
     * <p>Takes no metrics and consults no horizontal room: a line too full for the falls is a
     * commit-time refusal with an error message, made inside {@link SlideOperations#toggleFall}.
     * Solving for room here would run a full spacing solve on every selection and song change,
     * to answer a question the commit has to ask again anyway.
     */
    public boolean canToggleFall() {
        var range = selectedRange();
        return (range != null) && RangeQueries.canToggleFall(range);
    }

    public void toggleFall(@Nullable LyricRenderMetrics lyricRenderMetrics) {
        var range = selectedRange();

        if (range == null) {
            return;
        }

        SlideOperations.toggleFall(range, lyricRenderMetrics);
    }

    // ========== Tuplet Operations ==========

    public TupletToggleInfo canToggleTuplet() {
        var range = selectedRange();
        return (range != null)
            ? RangeQueries.canToggleTuplet(range)
            : new TupletToggleInfo(false, null, false);
    }

    /**
     * Handles four cases: (1) no existing tuplet → add; (2) existing tuplet, selection spans
     * its full span, requested grade matches → no-op; (3) existing tuplet, full coverage,
     * different grade → remove then add in one bracket (emits TupletRemoval +
     * TupletAddition); (4) existing tuplet, selection is a strict sub-range → rejected with
     * {@link IllegalStateException} so a programmatic caller cannot silently replace a tuplet
     * with a sub-range tuplet.
     *
     * <p>Deleting a tuplet is not one of them — the user selects the tuplet's number or
     * bracket and deletes it, which runs through {@code deleteSelectedTarget()}.</p>
     *
     * <p>Callers must pass the {@link TupletToggleInfo} obtained from {@link #canToggleTuplet()}
     * so there is a single source of truth for the decision. Any branch that would have been a
     * silent no-op in the old API now throws {@link IllegalStateException} — the UI gates these
     * via action enable state, so reaching them indicates a caller bug.
     */
    public void toggleTuplet(int tupletSize, TupletToggleInfo info) {
        var range = selectedRange();

        if (range == null) {
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

        var line = range.line();

        // The tuplet spans the selection's non-grace endpoints; a leading or trailing grace
        // note is left outside it (refs #592).
        var beginIndex = RangeQueries.nonGraceBegin(range);
        var endIndex = RangeQueries.nonGraceEnd(range);

        // canToggleTuplet() rejects a selection without two non-grace elements, so reaching
        // this with no usable span means the caller passed stale info.
        if (beginIndex < 0 || beginIndex == endIndex) {
            throw new IllegalStateException(
                "toggleTuplet requires at least two non-grace elements in the selection");
        }

        if (existing == null) {
            var newTuplet = createValidatedTuplet(line, beginIndex, endIndex, tupletSize);
            line.withModification(() -> line.addTuplet(newTuplet));
            return;
        }

        // Re-picking the grade the tuplet already has does nothing. The grade is shown
        // checked (Action.SELECTED_KEY), and a checked radio item that deletes what it
        // reports on would be a trap.
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
     * The decision and the span both come from a single
     * {@link #resolveHairpinAction(Hairpin.Kind)} call — nothing here re-derives them.
     * Two copies of that decision tree would eventually disagree, and the first
     * divergence turns an "Extend Crescendo" click into a second stray crescendo under
     * a mislabeled undo entry.
     */
    public void addHairpinToSelection(Hairpin.Kind kind) {
        var range = coordinator.getRange();

        if (range == null) {
            return;
        }

        var resolution = resolveHairpinAction(kind);
        var resolvedState = resolution.state();

        if (resolvedState != HairpinActionState.CAN_ADD && resolvedState != HairpinActionState.EXTEND) {
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
        var isCrescendo = kind == Hairpin.Kind.CRESCENDO;

        if (resolvedState == HairpinActionState.CAN_ADD) {
            opNameKey = isCrescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO
                : Strings.ACTION_HAIRPIN_DIMINUENDO;
        } else {
            opNameKey = isCrescendo
                ? Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND
                : Strings.ACTION_HAIRPIN_DIMINUENDO_EXTEND;
        }

        var line = range.line();

        // The span is always the resolved one, never a degenerate single element left
        // for the merge to absorb: a one-element hairpin is a shape the model never
        // otherwise produces, and it would survive as a stray if the merge missed it.
        var anchorElement = line.getElement(resolution.spanBegin());
        var endElement = line.getElement(resolution.spanEnd());

        line.withModification(Strings.get(opNameKey), () -> {
            // addCrescendo/addDiminuendo rather than the raw Line.addSpan adder: they
            // merge overlapping same-type spans, which the extend path depends on.
            var added = switch (kind) {
                case CRESCENDO -> {
                    var hairpin = new Crescendo(anchorElement, endElement);
                    line.addCrescendo(hairpin);
                    yield (Hairpin) hairpin;
                }
                case DIMINUENDO -> {
                    var hairpin = new Diminuendo(anchorElement, endElement);
                    line.addDiminuendo(hairpin);
                    yield (Hairpin) hairpin;
                }
            };

            // mergeOverlappingSpans widens the added hairpin in place, so its indices
            // after the add are the merged range — which can reach past what the
            // resolution computed. A point dynamic on the merged hairpin's anchor or end
            // element survives — the wedge pads away from it — but one stranded strictly
            // inside breaks the invariant DynamicMarkingAction.updateEnabledState() relies on.
            stripInteriorPointDynamics(line, added.getAnchorElementIndex(), added.getEndElementIndex());
        });
    }

    /**
     * Removes the point dynamic (p, mf, ff …) from every element strictly inside the
     * hairpin range {@code [anchorIndex, endIndex]}, leaving the two bound elements
     * alone.
     * <p>
     * A text dynamic may sit on a hairpin's anchor or end element, where the wedge pads
     * away from it; only the wedge's interior is off limits. A two-element hairpin has an
     * empty interior, so nothing is stripped.
     * <p>
     * Each removal goes through {@link Line#modifyElement} rather than
     * {@code StaffElement.removeAttachment}, which records no mutation: undo would
     * restore the hairpin while the point dynamics stayed gone forever, silently.
     * {@code modifyElement} nests inside an already-open modification bracket, so one
     * undo reverses both the hairpin and the strip.
     */
    private void stripInteriorPointDynamics(Line line, int anchorIndex, int endIndex) {
        // Not line.isInsideHairpin(index): that asks about *any* hairpin, so with two
        // back-to-back hairpins it would strip dynamics outside the one being added.
        for (var index = anchorIndex + 1; index < endIndex; index++) {
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

    /**
     * What one hairpin menu item should show for the current selection.
     * <p>
     * The resolution is per menu item, not a joint verdict about both: the crescendo
     * and diminuendo items are resolved separately and may legitimately disagree.
     * <p>
     * Two states mean "not now", and they differ in why. {@code INELIGIBLE} means the
     * span itself cannot carry a hairpin, whatever else is on the line.
     * {@code BLOCKED} means another hairpin is the obstacle.
     */
    public enum HairpinActionState {
        /** This item reads "Add …", disabled — the span cannot host a hairpin. */
        INELIGIBLE,
        /** This item reads "Add …", enabled. */
        CAN_ADD,
        /** This item reads "Extend …", enabled. */
        EXTEND,
        /** This item reads "Add …", disabled — another hairpin is in the way. */
        BLOCKED
    }

    /**
     * The resolved hairpin action for the current selection: what the menu should
     * show, and the exact span the execution path will use.
     *
     * <p>Returned by a single call so the label and the mutation cannot disagree —
     * {@code addHairpinToSelection} consumes this rather than recomputing.
     * {@code spanBegin}/{@code spanEnd} are meaningful only for
     * {@code CAN_ADD} and {@code EXTEND}; both are -1 otherwise.
     */
    public record HairpinResolution(
        HairpinActionState state,
        int spanBegin,
        int spanEnd
    ) {}

    /**
     * Resolves what the {@code kind} hairpin menu item should do for the current
     * selection.
     * <p>
     * The resolution is type-aware, and each menu item asks for its own: with a
     * crescendo already on the notes just before the selection, the crescendo item
     * extends while the diminuendo item adds a back-to-back wedge. One shared verdict
     * could not say both.
     * <p>
     * <b>Step 0 — input guard.</b> No selection, or a selection that is a target rather
     * than an index range (a slide, ending or hairpin selection) → {@code INELIGIBLE}.
     * This has to come first: nothing below may touch an element index before it
     * passes.
     * <p>
     * <b>Step 1 — converge the span.</b> Every {@code kind} hairpin within
     * {@link Line#SPAN_ADJACENCY_REACH} of the span widens it, repeatedly until it
     * stops growing, because {@code Line.addHairpin} absorbs exactly that
     * neighborhood and absorbing one hairpin can bring a further one into reach. The
     * presence of any such hairpin is what makes this an extension. One that already
     * covers the whole selection has nothing to extend → {@code BLOCKED}.
     * <p>
     * <b>Step 2 — validate the resolved span.</b> An endpoint the selection supplies
     * must be one the user could place ({@link Hairpin#canAnchorAt},
     * {@link Hairpin#canEndAt}) → {@code INELIGIBLE} otherwise; an endpoint
     * inherited from the hairpin being extended stays put, unchecked. The span may not
     * cross a repeat or a non-single barline. It may share at most one element with an
     * opposite-type hairpin — where one wedge ends and the next begins — and more than
     * that is a collision → {@code BLOCKED}. Finally, a new hairpin (never an
     * extension) needs {@value Hairpin#MIN_COLUMNS} columns to slope across.
     * <p>
     * The two "not now" states differ in why: {@code INELIGIBLE} means the span itself
     * cannot carry a hairpin, whatever else is on the line; {@code BLOCKED} means
     * another hairpin is the obstacle. Every return here follows that rule, including
     * the structural-boundary check, which blocks only when a hairpin widened the span
     * past the selection and is therefore the thing crossing the boundary.
     * <p>
     * <b>Do not cache this.</b> It is deliberately recomputed on every call. Two menu
     * items resolving independently costs a handful of O(spans) scans per selection
     * change, which is free; a cache keyed on the selection would go stale the moment
     * a hairpin is added or undone without the selection moving, leaving both items
     * lying about what they will do.
     *
     * <p><b>Worked examples.</b>
     *
     * <ul>
     *   <li>Crescendo on {@code [0, 4]}, selection {@code [4, 8]}: {@code CRESCENDO}
     *       resolves to {@code EXTEND} over {@code [0, 8]}, {@code DIMINUENDO} to
     *       {@code CAN_ADD} over {@code [4, 8]}. The two items legitimately disagree.
     *   <li>Crescendo on {@code [0, 4]}, selection {@code [2, 8]}: {@code DIMINUENDO}
     *       is {@code BLOCKED} — the overlap is more than the one shared endpoint.
     *   <li>One note plus the rest after it, no hairpin nearby: {@code CAN_ADD} — the
     *       trailing rest is the second column.
     *   <li>A lone rest: {@code INELIGIBLE} — a rest cannot anchor a hairpin.
     * </ul>
     */
    public HairpinResolution resolveHairpinAction(Hairpin.Kind kind) {
        var range = coordinator.getRange();

        // A slide, ending or hairpin selection is a target rather than a range, and
        // Line.getElement does not bounds check, so this guard has to come before
        // anything that touches an element index.
        if (range == null) {
            return ineligibleHairpinResolution();
        }

        var line = range.line();
        var begin = range.begin();
        var end = range.end();

        var spanBegin = begin;
        var spanEnd = end;
        var isExtend = false;
        var grew = false;

        // Line.addHairpin absorbs same-type spans within SPAN_ADJACENCY_REACH of the
        // span it is handed, so absorbing one can bring a further one into reach.
        // Widen until the union stops growing, or the menu label would promise a
        // narrower hairpin than the model is about to build, and the boundary and
        // opposite-type checks below would run against a span that never exists.
        // Each pass either strictly widens the union or ends the loop, so this
        // terminates in at most one pass per same-type hairpin on the line.
        do {
            var sameType = line.findSpans(
                kind.spanType(),
                Span.overlapping(
                    spanBegin - Line.SPAN_ADJACENCY_REACH,
                    spanEnd + Line.SPAN_ADJACENCY_REACH));
            isExtend = !sameType.isEmpty();

            var widenedBegin = spanBegin;
            var widenedEnd = spanEnd;

            for (var hairpin : sameType) {
                var anchorIndex = hairpin.getAnchorElementIndex();
                var endIndex = hairpin.getEndElementIndex();

                // A hairpin already covering the whole selection has nothing to extend.
                // Tested against the original selection, never the widened union: a
                // hairpin absorbed on a later pass covers the union by construction,
                // and would block every extension that reached it.
                if (anchorIndex <= begin && endIndex >= end) {
                    return blockedHairpinResolution();
                }

                widenedBegin = Math.min(widenedBegin, anchorIndex);
                widenedEnd = Math.max(widenedEnd, endIndex);
            }

            grew = widenedBegin != spanBegin || widenedEnd != spanEnd;
            spanBegin = widenedBegin;
            spanEnd = widenedEnd;
        } while (grew);

        // Only an endpoint the selection supplies has to be one the user could place.
        // An endpoint inherited from the hairpin being extended stays put — including a
        // rest anchor an older build left behind, which is not this action's to correct.
        var anchorComesFromSelection = spanBegin == begin;
        var endComesFromSelection = spanEnd == end;

        // spanEnd rather than end: canAnchorHairpin's lastIndex bounds the grace-note
        // host lookahead, and the hairpin really will reach spanEnd, so the host may
        // legitimately sit anywhere up to it.
        if (anchorComesFromSelection && !line.canAnchorHairpin(begin, spanEnd)) {
            return ineligibleHairpinResolution();
        }

        if (endComesFromSelection && !line.canEndHairpin(end)) {
            return ineligibleHairpinResolution();
        }

        // The union reaches past the selection only because a hairpin widened it, so
        // when extending, that hairpin is the obstacle. With none in play the selection
        // itself crosses the boundary.
        if (line.spansStructuralBoundary(spanBegin, spanEnd)) {
            return isExtend ? blockedHairpinResolution() : ineligibleHairpinResolution();
        }

        // Two hairpins may meet on one shared element; more than that is a collision.
        if (line.hasSpan(
                kind.opposite().spanType(),
                Span.overlappingBeyondEndpoint(spanBegin, spanEnd))) {
            return blockedHairpinResolution();
        }

        // Extending an existing hairpin has no such requirement — it only widens.
        if (!isExtend && !Hairpin.hasEnoughColumns(line.getElements(), spanBegin, spanEnd)) {
            return ineligibleHairpinResolution();
        }

        return new HairpinResolution(
            isExtend ? HairpinActionState.EXTEND : HairpinActionState.CAN_ADD, spanBegin, spanEnd);
    }

    private static HairpinResolution ineligibleHairpinResolution() {
        return new HairpinResolution(HairpinActionState.INELIGIBLE, -1, -1);
    }

    private static HairpinResolution blockedHairpinResolution() {
        return new HairpinResolution(HairpinActionState.BLOCKED, -1, -1);
    }

    // ========== First-Second Ending Operations ==========

    public EndingValidationResult canMakeFirstSecondEnding() {
        var range = coordinator.getRange();

        if (range == null) {
            return EndingValidationResult.invalid();
        }

        var line = range.line();
        var begin = range.begin();
        var end = range.end();

        // A multi-element selection never reaches the terminal (the selection coordinator
        // clamps it out), so if the selection ends just before it, extend end to include it
        // for an ending at the song's end to pass structural validation. Only extend if the
        // current end is not already a terminal. For FINAL_DOUBLE_BARLINE, isEndingTerminal()
        // returns true and the guard handles it. For REPEAT_RIGHT, isEndingTerminal() returns
        // false (deliberately excluded), so extendedEnd < line.elementCount() bounds check
        // prevents bad extension.
        var extendedEnd = end + 1;

        if (!line.getElement(end).getType().isEndingTerminal()
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

        // The split and the end state one repeat structure from its two sides. Replacing
        // either already refuses a pair that disagrees, so creation refuses it too rather
        // than making a state the ending could never be edited into.
        if (!Ending.isValidEnd(
                line.getElement(rightRepeatIndex).getType(),
                line.getElement(end).getType())) {
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
    // Endings outermost so each one's bounds are resolved once: asking the line per index
    // would re-filter its whole span list — beams, ties, tuplets and hairpins included —
    // and re-resolve every ending, on every iteration.
    private boolean hasOverlap(Line line, int begin, int end) {
        for (var ending : line.findEndings()) {
            var anchorBound = line.anchorIndexOf(ending);
            var endBound = line.endIndexOf(ending);

            for (var i = begin; i <= end; i++) {
                if (Span.containing(i).test(anchorBound, endBound)) {
                    return true;
                }
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

    // Examines the element preceding the selection start and determines what action
    // is needed (none, span extension, or invalid).
    private EndingValidationResult checkPrecedingElement(
        Line line, int selectionBegin, int selectionEnd
    ) {
        // Where a bracket anchored here would open is Ending's rule, so that the anchor this
        // sets and the element layout later draws from cannot drift apart. It answers with
        // the anchor itself when nothing in front of it is a bar — a note predecessor, or no
        // predecessor on this line, since an ending never spans lines.
        var precedingElementIndex = Ending.openingElementIndex(line, selectionBegin);

        if (precedingElementIndex == selectionBegin) {
            return EndingValidationResult.valid(
                EndingValidationResult.PrecedingAction.NONE,
                selectionBegin,
                selectionEnd
            );
        }

        var precedingType = line.getElement(precedingElementIndex).getType();

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
        var range = coordinator.getRange();

        if (range == null) {
            return;
        }

        var line = range.line();

        line.withModification(() -> {
            var start = result.getSpanStart();
            var end = result.getSpanEnd();

            // EXTEND_SPAN and NONE both anchor at the pre-computed span bounds
            // with no element insertion.
            var startElement = line.getElement(start);
            var endElement = line.getElement(end);
            line.addSpan(new Ending(startElement, endElement));
        });
    }


    // ========== Stem Direction Operations ==========

    private enum StemDirectionChange {
        FLIP,
        AUTO
    }

    public boolean canModifyStemDirection() {
        var range = selectedRange();
        return (range != null) && RangeQueries.canModifyStemDirection(range);
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
        var range = coordinator.getRange();

        if (range == null) {
            return;
        }

        var line = range.line();
        var stemFields = EnumSet.of(ElementField.UPPER, ElementField.STEM_DIRECTION_AUTO);

        line.withModification(() -> {
            // Track which beam groups have already been processed to avoid modifying one twice.
            var processedBeams = new HashSet<Beam>();

            for (var i = range.begin(); i <= range.end(); i++) {
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

            for (var i = range.begin(); i <= range.end(); i++) {
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

                // Receiver-relative resolution. Reading the pair off the tie takes the anchor
                // index from the anchor's own line and the end index from the end's own line,
                // so a tie crossing a line boundary yields 7..0 — an inverted range that walks
                // nothing and silently flips no partner at all. Asking `line` gives
                // At(7)/AFTER_LINE here and BEFORE_LINE/At(0) in the next line, so each line
                // walks exactly its own half. The off-edge bound clamps to this line's own
                // edge because every element below is indexed into `line`; the far half is not
                // reachable from here. findTieAt has already rejected a tie with an ABSENT
                // bound, so a non-At bound can only be BEFORE_LINE on the anchor or
                // AFTER_LINE on the end.
                var tieStart = line.anchorIndexOf(tieSpan).indexOr(0);
                var tieEnd = line.endIndexOf(tieSpan).indexOr(line.elementCount() - 1);

                for (var j = tieStart; j <= tieEnd; j++) {
                    if (visited.add(j)) {
                        pending.add(j);

                        if ((j < range.begin()) || (j > range.end())) {
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
