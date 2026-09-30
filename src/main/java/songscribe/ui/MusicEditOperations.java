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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.Direction;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.EndingValidationResult;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.LineSpans;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Stem;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletGrade;
import songscribe.dom.TupletValidator;
import songscribe.error.RuntimeError;
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
        var grouped = line.nonGraceRange(range.range());

        if (grouped == null || grouped.size() < 2) {
            return;
        }

        var begin = grouped.begin();
        var end = grouped.end();
        var spans = line.spanRegistry();

        line.getSong().withModification(() -> {
            // Looked up rather than asked via LineSpans.sameBeamAt: the remove branch needs
            // the Beam object itself, and going through the boolean would only mean finding
            // it again afterwards.
            var beginBeam = spans.findBeamAt(begin);

            //noinspection ObjectEquality
            if (beginBeam == null || beginBeam != spans.findBeamAt(end)) {
                // canToggleBeaming has admitted the range, so both endpoints are beamable notes.
                new Beam(line.noteAt(begin), line.noteAt(end)).addTo(line);
            } else {
                beginBeam.removeFrom(line);
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
     * The break is a {@link Beam#removeFrom} followed by up to two {@link Beam#addTo} calls
     * so every step is a tracked mutation and undo restores the original span; mutating the
     * beam's endpoints in place would not be recorded. The two new spans do not re-merge,
     * since {@code addTo} merges overlapping spans without absorbing merely adjacent ones.
     * Grace notes between the two elements fall in the gap and belong to neither span.
     *
     * <p>The add branch, by contrast, can fuse two adjacent beam groups into one: {@code
     * addTo} widens at both ends, so with beams [0,1] and [2,3], beaming element 2 to its
     * predecessor yields a single [0,3]. That matches what the select-mode toggle does from the
     * same call, and the two must not disagree.
     *
     * @param line         the line holding the element
     * @param elementIndex the index of the later of the two elements
     * @return {@code true} when the line was modified
     */
    public static boolean toggleBeamWithPredecessor(Line line, int elementIndex) {
        if (!line.hasIndex(elementIndex)) {
            return false;
        }

        var targetType = line.getElement(elementIndex).getType();

        if (targetType.isGraceNote()) {
            return false;
        }

        var predecessorIndex = line.previousNonGraceIndex(elementIndex);

        if (predecessorIndex == null) {
            return false;
        }

        var spans = line.spanRegistry();
        var predecessorBeam = spans.findBeamAt(predecessorIndex);

        // The break branch needs the Beam object itself, not just the identity test, so it
        // looks both beams up rather than going through LineSpans.sameBeamAt.
        //noinspection ObjectEquality
        if (predecessorBeam != null && predecessorBeam == spans.findBeamAt(elementIndex)) {
            var anchorIndex = predecessorBeam.requireAnchorElementIndex();
            var endIndex = predecessorBeam.requireEndElementIndex();

            line.getSong().withModification(() -> {
                predecessorBeam.removeFrom(line);

                // Both selected elements are non-grace members of the beam, so they are notes.
                if (anchorIndex < predecessorIndex) {
                    new Beam(predecessorBeam.getAnchorElement(), line.noteAt(predecessorIndex)).addTo(line);
                }

                if (elementIndex < endIndex) {
                    new Beam(line.noteAt(elementIndex), predecessorBeam.getEndElement()).addTo(line);
                }
            });

            return true;
        }

        if (!targetType.isBeamable() || !line.getElement(predecessorIndex).getType().isBeamable()) {
            return false;
        }

        // Both are beamable, so both are notes.
        var anchorNote = line.noteAt(predecessorIndex);
        var endNote = line.noteAt(elementIndex);

        // Conflict: beaming may not connect what a tie already connects, the same rule
        // RangeQueries.canToggleBeaming applies. A break is never blocked by a tie.
        if (anchorNote.isTiedTo(endNote)) {
            return false;
        }

        line.getSong().withModification(() -> new Beam(anchorNote, endNote).addTo(line));

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
     *
     * @return {@code true} when the line was modified
     */
    private static boolean toggleTieInRange(ElementSelection range) {
        var line = range.line();

        // A tie joins the two notes the selection begins and ends on, and any separator
        // between them stays outside the tie (refs #527).
        var bounds = range.range();
        var beginIndex = bounds.begin();
        var endIndex = bounds.end();

        return line.getSong().withModificationResult(() -> {
            if (bounds.size() == 1) {
                return toggleBoundaryTie(line, beginIndex);
            }

            // canToggleTie has admitted the range, so both endpoints are pitched notes with at
            // most a legal separator between them, and the tie added to the first ends on the
            // second.
            var anchor = line.noteAt(beginIndex);

            if (anchor.isTiedTo(line.noteAt(endIndex))) {
                line.removeTie(anchor);
            } else {
                line.addTie(anchor);
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
     * The tie is recorded on the anchor's line, which is the line before {@code line} when
     * {@code index} is where a tie enters {@code line}. Add-versus-remove is decided by
     * {@link NoteElement#isTiedTo} over the two notes, so a same-line tie that merely ends on
     * the boundary note (a chained tie) is never mistaken for the boundary tie itself.
     *
     * @return Whether a partner was found and the tie toggled
     */
    private static boolean toggleBoundaryTie(Line line, int index) {
        var boundaryTie = RangeQueries.boundaryTieAt(line, index);

        if (boundaryTie == null) {
            return false;
        }

        var anchor = boundaryTie.anchor();
        var anchorLine = anchor.getParentLine();

        if (anchorLine == null) {
            throw RuntimeError.exit("a boundary tie candidate's anchor is in no line");
        }

        if (anchor.isTiedTo(boundaryTie.end())) {
            anchorLine.removeTie(anchor);
        } else {
            anchorLine.addTie(anchor);
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
     * so there is a single source of truth for the decision. The UI gates every refused case
     * via action enable state, so reaching one indicates a caller bug. Does nothing when
     * there is no selected range.
     *
     * @param grade the grade asked for
     * @param info  what {@link #canToggleTuplet()} answered for the current selection
     * @throws IllegalStateException if the selection is a strict sub-range of the existing
     *     tuplet; if {@code info.canToggle()} is {@code false}; or if the selection holds fewer
     *     than two non-grace elements, which means {@code info} is stale
     */
    public void toggleTuplet(TupletGrade grade, TupletToggleInfo info) {
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
        var span = line.nonGraceRange(range.range());

        // canToggleTuplet() rejects a selection without two non-grace elements, so reaching
        // this with no usable span means the caller passed stale info.
        if (span == null || span.size() < 2) {
            throw new IllegalStateException(
                "toggleTuplet requires at least two non-grace elements in the selection");
        }

        if (existing == null) {
            var newTuplet = createValidatedTuplet(line, span, grade);
            line.getSong().withModification(() -> newTuplet.addTo(line));
            return;
        }

        // Re-picking the grade the tuplet already has does nothing. The grade is shown
        // checked (Action.SELECTED_KEY), and a checked radio item that deletes what it
        // reports on would be a trap.
        if (existing.getGrade() == grade) {
            return;
        }

        var replacement = createValidatedTuplet(line, span, grade);

        line.getSong().withModification(() -> {
            existing.removeFrom(line);
            replacement.addTo(line);
        });
    }

    /**
     * Builds a tuplet whose ratio the validator derived for this span.
     * <p>
     * The UI gates tuplet creation on the same validator, so an invalid verdict here
     * means the caller skipped that gate — the same contract as the other
     * {@link IllegalStateException}s in {@link #toggleTuplet}.
     *
     * @return the tuplet over {@code span}, not yet added to {@code line}
     */
    private Tuplet createValidatedTuplet(Line line, ElementRange span, TupletGrade grade) {
        var result = TupletValidator.validateDerived(
            line, span, grade, TupletValidator.Strictness.STRICT);

        return switch (result) {
            case TupletValidator.Result.Valid(var ratio) ->
                new Tuplet(line.getElement(span.begin()), line.getElement(span.end()), grade, ratio);

            case TupletValidator.Result.Invalid(var reason) -> throw new IllegalStateException(
                "toggleTuplet called for a span that cannot carry a tuplet of " + grade + ": " + reason);
        };
    }

    // ========== Dynamics Operations ==========

    /**
     * Adds a hairpin over the current selection, or extends an existing same-type
     * hairpin to cover it.
     * <p>
     * The decision comes from {@link #resolveHairpinAction(Hairpin.Kind)} and the hairpin
     * from {@link Hairpin#spanning}, the same judge the resolution consults — nothing
     * here re-derives either. Two copies of that decision tree would eventually disagree,
     * and the first divergence turns an "Extend Crescendo" click into a second stray
     * crescendo under a mislabeled undo entry.
     */
    public void addHairpinToSelection(Hairpin.Kind kind) {
        var range = coordinator.getRange();

        if (range == null) {
            return;
        }

        var resolution = resolveHairpinAction(kind);
        var span = resolution.span();

        // A resolution carries a span exactly when it can add or extend.
        if (span == null) {
            return;
        }

        var resolvedState = resolution.state();

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

        // The resolution just answered Spanned for this selection, and nothing has
        // changed the line since, so the factory answers the same again.
        if (!(Hairpin.spanning(line, kind, range.range()) instanceof Spanning.Spanned<Hairpin>(var added))) {
            throw new IllegalStateException("Hairpin factory refused a selection the resolution accepted");
        }

        line.getSong().withModification(Strings.get(opNameKey), () -> {
            // Span.addTo applies the hairpin's displacement policy, which absorbs the
            // same-kind hairpins in reach — the extend path depends on that.
            added.addTo(line);

            // addTo widens the added hairpin in place to the converged range, so its
            // range after the add is the one to strip. A point dynamic on its anchor or end
            // element survives — the wedge pads away from it — but one stranded strictly
            // inside breaks the invariant DynamicMarkingAction.updateEnabledState() relies on.
            stripInteriorPointDynamics(line, added.range());
        });
    }

    /**
     * Removes the point dynamic (p, mf, ff …) from every element strictly inside
     * {@code hairpin}'s range, leaving the two bound elements alone.
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
    private void stripInteriorPointDynamics(Line line, ElementRange hairpin) {
        // Not line.isInsideHairpin(index): that asks about *any* hairpin, so with two
        // back-to-back hairpins it would strip dynamics outside the one being added.
        for (var index = hairpin.begin() + 1; index < hairpin.end(); index++) {
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
     * {@code span} is present exactly for {@code CAN_ADD} and {@code EXTEND}, and null
     * for every other state.
     *
     * @throws IllegalArgumentException if {@code span}'s presence disagrees with {@code state}
     */
    public record HairpinResolution(HairpinActionState state, @Nullable ElementRange span) {

        public HairpinResolution {
            var resolved = state == HairpinActionState.CAN_ADD || state == HairpinActionState.EXTEND;

            if (resolved != (span != null)) {
                throw new IllegalArgumentException("Hairpin resolution " + state + " with span " + span);
            }
        }
    }

    /**
     * Resolves what the {@code kind} hairpin menu item should do for the current
     * selection.
     * <p>
     * The resolution is type-aware, and each menu item asks for its own: with a
     * crescendo already on the notes just before the selection, the crescendo item
     * extends while the diminuendo item adds a back-to-back wedge. One shared verdict
     * could not say both.
     * <p>
     * {@link Hairpin#spanning} is the one judge of whether the selection holds a hairpin;
     * this method adds only what the menu needs on top of its answer.
     * <p>
     * <b>Step 0 — input guard.</b> No selection, or a selection that is a target rather
     * than an index range (a slide, ending or hairpin selection) → {@code INELIGIBLE}.
     * This has to come first: nothing below may touch an element index before it
     * passes.
     * <p>
     * <b>Step 1 — converge.</b> {@link Hairpin#converge} gives the range a {@code kind}
     * hairpin over the selection holds once it has absorbed the same-kind hairpins in
     * reach, and those hairpins. An absorbed hairpin that already covers the whole
     * selection leaves nothing to extend → {@code BLOCKED}.
     * <p>
     * <b>Step 2 — judge.</b> {@link Hairpin#spanning} judges the selection. A
     * {@link Spanning.Spanned} answer is {@code EXTEND} when the convergence absorbed a
     * hairpin and {@code CAN_ADD} when it absorbed none, over the converged range in
     * both cases. A {@link Spanning.Refused} answer maps its cause:
     * {@link Spanning.Cause#RANGE} → {@code INELIGIBLE}, {@link Spanning.Cause#OTHER_SPAN}
     * → {@code BLOCKED}.
     * <p>
     * The two "not now" states differ in why: {@code INELIGIBLE} means the selection
     * itself cannot carry a hairpin, whatever else is on the line; {@code BLOCKED} means
     * another hairpin is the obstacle.
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
     *   <li>One note plus the rest after it, no hairpin nearby: {@code INELIGIBLE} — a
     *       hairpin never ends on a rest.
     *   <li>A lone rest: {@code INELIGIBLE} — a rest cannot anchor a hairpin.
     * </ul>
     *
     * @param kind the hairpin kind the menu item adds
     * @return what the item would do for the current selection, with the span it would act on
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
        var selection = range.range();
        var convergence = Hairpin.converge(line, kind, selection);
        var absorbed = convergence.absorbed();

        // Tested against the selection, never the converged range: a hairpin absorbed
        // on a later widening pass covers the converged range by construction, and
        // would block every extension that reached it.
        if (absorbed.stream().anyMatch(hairpin -> hairpin.range().contains(selection))) {
            return blockedHairpinResolution();
        }

        return switch (Hairpin.spanning(line, kind, selection)) {
            case Spanning.Spanned<Hairpin> spanned -> new HairpinResolution(
                absorbed.isEmpty() ? HairpinActionState.CAN_ADD : HairpinActionState.EXTEND,
                convergence.range());
            case Spanning.Refused<Hairpin> refused -> switch (refused.cause()) {
                case RANGE -> ineligibleHairpinResolution();
                case OTHER_SPAN -> blockedHairpinResolution();
            };
        };
    }

    private static HairpinResolution ineligibleHairpinResolution() {
        return new HairpinResolution(HairpinActionState.INELIGIBLE, null);
    }

    private static HairpinResolution blockedHairpinResolution() {
        return new HairpinResolution(HairpinActionState.BLOCKED, null);
    }

    // ========== First-Second Ending Operations ==========

    public EndingValidationResult canMakeFirstSecondEnding() {
        var range = coordinator.getRange();

        if (range == null) {
            return EndingValidationResult.Invalid.INSTANCE;
        }

        var line = range.line();
        var bounds = range.range();
        var begin = bounds.begin();
        var end = bounds.end();

        // A multi-element selection never reaches the terminal (the selection coordinator
        // clamps it out), so if the selection ends just before it, extend end to include it
        // for an ending at the song's end to pass structural validation. Only extend if the
        // current end is not already a terminal. For FINAL_DOUBLE_BARLINE, isEndingTerminal()
        // returns true and the guard handles it. For REPEAT_RIGHT, isEndingTerminal() returns
        // false (deliberately excluded), so extendedEnd < line.elementCount() bounds check
        // prevents bad extension.
        var extendedEnd = end + 1;

        if (!line.getElement(end).getType().isEndingTerminal()
                && line.isTerminalAt(extendedEnd)) {
            end = extendedEnd;
        }

        // Ending's own rule decides whether a well-formed ending fits here: it refuses one
        // that overlaps, or is not separated from, an ending already on the line, and one
        // whose split closes no open repeated section.
        //
        // Where a bracket anchored here would open is Ending's rule, so that the anchor this
        // sets and the element layout later draws from cannot drift apart. It answers with
        // the anchor itself when the element in front of it is not a plain barline, or when
        // it has no predecessor on this line, since an ending never spans lines.
        var anchorIndex = Ending.openingElementIndex(line, begin);

        return switch (Ending.spanning(line, new ElementRange(anchorIndex, end))) {
            case Spanning.Spanned<Ending> spanned ->
                new EndingValidationResult.Valid(line, spanned.span());
            case Spanning.Refused<Ending> refused -> EndingValidationResult.Invalid.INSTANCE;
        };
    }


    public void makeFirstSecondEnding(EndingValidationResult.Valid valid) {
        var line = valid.line();

        line.getSong().withModification(() -> valid.ending().addTo(line));
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
     * forces that direction; null hands the direction back to the score.
     */
    private static void applyStemChange(
        Line line,
        int index,
        EnumSet<ElementField> stemFields,
        @Nullable Direction newDirection
    ) {
        // Rests, whole notes, and non-note elements carry no stem, and a grace note's stem is
        // always up whatever the notator asks, so a direction change on any of them would be an
        // invisible edit that still dirties the song and pushes an undo entry. Enforced here so
        // it holds for every caller, including the beam-group loop.
        if (!(line.getElement(index) instanceof NoteElement note)
            || !note.getType().isNoteWithStem()
            || note.getType().isGraceNote()) {
            return;
        }

        // Re-enabling auto on an already-auto note changes nothing. Recording a mutation
        // anyway would mark the song modified and push an empty entry onto the undo stack.
        if (newDirection == null && note.stem() instanceof Stem.Auto) {
            return;
        }

        var stem = newDirection == null ? Stem.AUTO : new Stem.Forced(newDirection);
        line.modifyElement(index, stemFields, () -> note.setStem(stem));
    }

    private void modifyStemDirection(StemDirectionChange change) {
        var range = coordinator.getRange();

        if (range == null) {
            return;
        }

        var line = range.line();
        var spans = line.spanRegistry();
        var bounds = range.range();
        var stemFields = EnumSet.of(ElementField.STEM);

        line.getSong().withModification(() -> {
            // Track which beam groups have already been processed to avoid modifying one twice.
            var processedBeams = new HashSet<Beam>();

            for (var i = bounds.begin(); i <= bounds.end(); i++) {
                // applyStemChange already ignores stemless elements. Stopping here as well
                // keeps one from dragging its entire beam group into the edit below.
                if (!(line.getElement(i) instanceof NoteElement note) || !note.getType().isNoteWithStem()) {
                    continue;
                }

                // A grace note sitting inside a beam span is not a member — the beam passes
                // over it and it keeps its own stem, so it flips on its own (refs #592).
                var beam = note.getType().isGraceNote() ? null : spans.findBeamAt(i);

                if (beam != null) {
                    // Modify the whole beam group together, once per group. The group's new
                    // direction is derived once from its anchor so every member agrees.
                    if (processedBeams.add(beam)) {
                        var newDirection = flippedDirection(beam.getAnchorElement(), change);

                        for (var member : beam.members()) {
                            applyStemChange(line, line.getElementIndex(member.note()), stemFields, newDirection);
                        }
                    }
                } else {
                    applyStemChange(line, i, stemFields, flippedDirection(note, change));
                }
            }

            // Apply the same change to every note a tie joins to the selection, in whichever
            // line each one sits. Chained ties (note1-2 tied, note2-3 tied separately) must be
            // walked to their full transitive closure so every note in the chain gets updated,
            // not just the immediate partner of a selected note, and a chain that crosses a
            // line break is followed into that line rather than stopped at the edge.
            var visited = new HashSet<NoteElement>();

            for (var i = bounds.begin(); i <= bounds.end(); i++) {
                if (line.getElement(i) instanceof NoteElement note) {
                    visited.add(note);
                }
            }

            var tiePartnersToModify = new ArrayList<NoteElement>();
            var pending = new ArrayDeque<>(visited);

            while (!pending.isEmpty()) {
                var note = pending.remove();

                for (var partner : note.tiePartners()) {
                    if (visited.add(partner)) {
                        pending.add(partner);
                        tiePartnersToModify.add(partner);
                    }
                }
            }

            for (var partner : tiePartnersToModify) {
                var partnerLine = partner.getParentLine();

                if (partnerLine != null) {
                    applyStemChange(
                        partnerLine,
                        partnerLine.getElementIndex(partner),
                        stemFields,
                        flippedDirection(partner, change));
                }
            }
        });
    }

    /**
     * Returns the direction a {@link StemDirectionChange#FLIP} of {@code note} would install, or
     * null when {@code change} does not need one.
     */
    private static @Nullable Direction flippedDirection(NoteElement note, StemDirectionChange change) {
        if (change != StemDirectionChange.FLIP) {
            return null;
        }

        return note.direction().opposite();
    }

    // ========== Tempo Operations ==========

    public boolean canChangeTempo() {
        return coordinator.canChangeTempo();
    }
}
