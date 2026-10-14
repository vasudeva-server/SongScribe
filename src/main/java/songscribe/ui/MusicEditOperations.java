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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.Direction;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Stem;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletGrade;
import songscribe.dom.TupletValidator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.mutation.ElementField;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.RangeQueries;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * The music editing commands over the current selection, and the queries that enable them.
 * Each edit judges the selection again when it runs rather than trusting an answer a menu
 * cached.
 */
public final class MusicEditOperations {

    private final SelectionCoordinator coordinator;

    public MusicEditOperations(SelectionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /** {@code null} when no element range is selected: nothing is, or a target is. */
    private @Nullable ElementSelection selectedRange() {
        var range = coordinator.getRange();

        return (range != null) ? range.toElementSelection() : null;
    }

    public boolean canToggleBeaming() {
        var range = selectedRange();
        return (range != null) && RangeQueries.canToggleBeaming(range);
    }

    /**
     * A selection inside a beam removes the whole beam, unlike
     * {@link #toggleBeamWithPredecessor}, which breaks it.
     */
    public void toggleBeaming() {
        var selection = selectedRange();

        if (selection == null) {
            return;
        }

        var song = selection.line().getSong();

        switch (RangeQueries.beamToggle(selection)) {
            case RangeQueries.BeamToggle.Add(var beam) -> song.withModification(beam::addTo);
            case RangeQueries.BeamToggle.Remove(var beam) -> song.withModification(beam::removeFrom);
            case RangeQueries.BeamToggle.Ineligible() -> {
            }
        }
    }

    /**
     * Grace notes are transparent. Unlike the select-mode toggle, a beam joining both elements
     * is broken <em>between</em> them and its parts are kept, each only when
     * {@link Beam#spanning} accepts it; grace notes between the two belong to neither part.
     * Adding a beam absorbs every beam it overlaps, as in {@link #toggleBeaming}.
     */
    public static boolean toggleBeamWithPredecessor(Line line, int elementIndex) {
        if (!line.hasIndex(elementIndex)) {
            return false;
        }

        var predecessorIndex = line.previousNonGraceIndex(elementIndex);

        if (predecessorIndex == null) {
            return false;
        }

        var pair = new ElementSelection(line, new ElementRange(predecessorIndex, elementIndex));
        var song = line.getSong();

        return switch (RangeQueries.beamToggle(pair)) {
            case RangeQueries.BeamToggle.Add(var beam) -> {
                song.withModification(beam::addTo);
                yield true;
            }

            case RangeQueries.BeamToggle.Remove(var beam) -> {
                var left = new ElementRange(beam.requireAnchorElementIndex(), predecessorIndex);
                var right = new ElementRange(elementIndex, beam.requireEndElementIndex());

                song.withModification(() -> {
                    // The parts are judged once the beam is gone, or the factory would widen
                    // each of them back over it.
                    beam.removeFrom();
                    addBeamOver(line, left);
                    addBeamOver(line, right);
                });

                yield true;
            }

            case RangeQueries.BeamToggle.Ineligible() -> false;
        };
    }

    private static void addBeamOver(Line line, ElementRange range) {
        if (Beam.spanning(line, range) instanceof Spanning.Spanned<Beam>(var beam, var _)) {
            beam.addTo();
        }
    }

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

    /** Applies no eligibility rule: the caller has consulted {@link RangeQueries#canToggleTie}. */
    private static boolean toggleTieInRange(ElementSelection range) {
        var line = range.line();

        // A tie joins the two notes the selection begins and ends on, and any separator
        // between them stays outside the tie.
        var bounds = range.range();
        var beginIndex = bounds.begin();
        var endIndex = bounds.end();

        return line.getSong().withModificationResult(() -> {
            if (bounds.size() == 1) {
                return toggleBoundaryTie(line, beginIndex);
            }

            toggleTieBetween(line.noteAt(beginIndex), line.noteAt(endIndex));

            return true;
        });
    }

    /** The one statement of "if tied, remove, else add" behind every tie toggle. */
    private static void toggleTieBetween(NoteElement anchor, NoteElement end) {
        var anchorLine = anchor.requireParentLine();

        if (anchor.isTiedTo(end)) {
            anchorLine.removeTie(anchor);
        } else {
            anchorLine.addTie(anchor);
        }
    }

    public static boolean toggleTieWithPredecessor(Line line, int elementIndex) {
        var range = RangeQueries.tieCandidateWithPredecessor(line, elementIndex);

        return (range != null) && toggleTieInRange(range);
    }

    /** Add-versus-remove is decided over the two notes, so a chained same-line tie is never mistaken for the boundary tie. */
    private static boolean toggleBoundaryTie(Line line, int index) {
        var boundaryTie = RangeQueries.boundaryTieAt(line, index);

        if (boundaryTie == null) {
            return false;
        }

        toggleTieBetween(boundaryTie.anchor(), boundaryTie.end());

        return true;
    }

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

    /** Consults no horizontal room: a line too full for the falls is refused at commit, in {@link SlideOperations#toggleFall}. */
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

    public TupletToggleInfo canToggleTuplet() {
        var selection = selectedRange();
        return (selection != null) ? RangeQueries.canToggleTuplet(selection) : TupletToggleInfo.NONE;
    }

    /**
     * Does nothing when the selection is exactly a tuplet of {@code grade} or partly inside a
     * tuplet. Deleting a tuplet is not a toggle. The removal of a replaced tuplet is not
     * reported.
     */
    public void toggleTuplet(TupletGrade grade) {
        var selection = selectedRange();
        var site = (selection != null) ? RangeQueries.tupletSite(selection) : null;

        if (site == null) {
            return;
        }

        switch (RangeQueries.tupletOverlap(site.line(), site.range())) {
            case RangeQueries.TupletOverlap.NoTuplet() -> placeTuplet(site, grade, null);
            case RangeQueries.TupletOverlap.Covers(var existing) when existing.getGrade() != grade ->
                placeTuplet(site, grade, existing);
            case RangeQueries.TupletOverlap.Covers _, RangeQueries.TupletOverlap.PartlyInside _ -> {
            }
        }
    }

    /** @param replaced {@code null} when the new tuplet replaces none */
    private static void placeTuplet(Tuplet.Site site, TupletGrade grade, @Nullable Tuplet replaced) {
        if (!(Tuplet.spanning(site, grade, TupletValidator.Strictness.STRICT)
                instanceof Spanning.Spanned<Tuplet>(var tuplet, var _))) {
            return;
        }

        site.line().getSong().withModification(() -> {
            // Removed before the add, which would otherwise displace it with a report entry.
            if (replaced != null) {
                replaced.removeFrom();
            }

            tuplet.addTo();
        });
    }

    /** Adds the hairpin the resolution carries, so the menu item's wording and the edit cannot disagree. */
    public void addHairpinToSelection(Hairpin.Kind kind) {
        var selection = selectedRange();

        if (selection == null
            || !(RangeQueries.resolveHairpin(selection, kind) instanceof RangeQueries.HairpinResolution.Applicable applicable)) {
            return;
        }

        var line = selection.line();
        var hairpin = applicable.hairpin();

        line.getSong().withModification(Strings.get(applicable.nameKey()), () -> {
            hairpin.addTo();

            // The hairpin is built over the range it holds once it has absorbed the
            // same-kind hairpins in reach, so its range is the one to strip. A point dynamic
            // on its anchor or end element survives — the wedge pads away from it — but one
            // stranded strictly inside breaks the invariant
            // DynamicMarkingAction.updateEnabledState() relies on.
            stripInteriorPointDynamics(line, hairpin.range());
        });
    }

    /**
     * A text dynamic may sit on a hairpin's anchor or end element; only the interior is off
     * limits. Removal goes through {@link Line#modifyElement}, not
     * {@code StaffElement.removeAttachment}, which records no mutation: one undo then reverses
     * both the hairpin and the strip.
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

    public RangeQueries.HairpinResolution resolveHairpinAction(Hairpin.Kind kind) {
        var selection = selectedRange();

        return (selection != null)
            ? RangeQueries.resolveHairpin(selection, kind)
            : new RangeQueries.HairpinResolution.Ineligible(kind);
    }

    public boolean canMakeFirstSecondEnding() {
        var selection = selectedRange();

        return (selection != null)
            && RangeQueries.firstSecondEnding(selection) instanceof Spanning.Spanned<Ending>;
    }

    public boolean makeFirstSecondEnding() {
        var selection = selectedRange();

        if (selection == null
            || !(RangeQueries.firstSecondEnding(selection) instanceof Spanning.Spanned<Ending>(var ending, var _))) {
            return false;
        }

        selection.line().getSong().withModification(ending::addTo);

        return true;
    }


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

    /** A null {@code newDirection} hands the direction back to the score. */
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
        var selection = selectedRange();

        if (selection == null) {
            return;
        }

        var line = selection.line();
        var spans = line.spanRegistry();
        var bounds = selection.range();
        var stemFields = EnumSet.of(ElementField.STEM);

        line.getSong().withModification(() -> {
            var processedBeams = new HashSet<Beam>();

            for (var i = bounds.begin(); i <= bounds.end(); i++) {
                // applyStemChange already ignores stemless elements. Stopping here as well
                // keeps one from dragging its entire beam group into the edit below.
                if (!(line.getElement(i) instanceof NoteElement note) || !note.getType().isNoteWithStem()) {
                    continue;
                }

                // A grace note sitting inside a beam span is not a member — the beam passes
                // over it and it keeps its own stem, so it flips on its own.
                var beam = spans.findBeamJoining(i);

                if (beam != null) {
                    // The group's new direction is derived once from its anchor so every
                    // member agrees.
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

            var selected = new LinkedHashSet<NoteElement>();

            for (var i = bounds.begin(); i <= bounds.end(); i++) {
                if (line.getElement(i) instanceof NoteElement note) {
                    selected.add(note);
                }
            }

            var tiePartnersToModify = new LinkedHashSet<NoteElement>();

            for (var note : selected) {
                tiePartnersToModify.addAll(note.tieChain());
            }

            tiePartnersToModify.removeAll(selected);

            for (var partner : tiePartnersToModify) {
                var partnerLine = partner.requireParentLine();

                applyStemChange(
                    partnerLine,
                    partnerLine.getElementIndex(partner),
                    stemFields,
                    flippedDirection(partner, change));
            }
        });
    }

    /** Null when {@code change} does not need a direction. */
    private static @Nullable Direction flippedDirection(NoteElement note, StemDirectionChange change) {
        if (change != StemDirectionChange.FLIP) {
            return null;
        }

        return note.direction().opposite();
    }

    public boolean canChangeTempo() {
        return coordinator.canChangeTempo();
    }
}
