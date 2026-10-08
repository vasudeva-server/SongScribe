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
 * Each query asks {@link RangeQueries}, and each edit judges the selection again when it runs
 * rather than trusting an answer a menu cached.
 */
public final class MusicEditOperations {

    private final SelectionCoordinator coordinator;

    public MusicEditOperations(SelectionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * The current selection in the form every query and edit below over the selected range
     * reads it through, or {@code null}
     * when no element range is selected: nothing is, or a target such as a slide, an ending or
     * a hairpin is.
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

    /**
     * Adds or removes the beam over the current selection, as {@link RangeQueries#beamToggle}
     * decides when the command runs. A selection inside a beam removes the whole beam, unlike
     * {@link #toggleBeamWithPredecessor}, which breaks it. Does nothing when no element range is
     * selected or the selection can be neither beamed nor unbeamed.
     *
     * @effects adds or removes one beam inside one modification bracket; adding it absorbs every
     *     beam it overlaps
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
     * Toggles a beam between the element at {@code elementIndex} and its nearest preceding
     * non-grace neighbor, and returns whether the line was modified. This is the toggle while
     * typing; {@link #toggleBeaming} is the toggle over a selection.
     *
     * <p>Static and taking the line explicitly because the operation does not go through the
     * selection coordinator — the caller looks up the state.
     *
     * <p>The two elements are asked about as a two-note selection, through
     * {@link RangeQueries#beamToggle}, which asks {@link Beam#spanning} for every beam rule. Grace
     * notes are transparent: the walk backward skips over them, and a grace-note target leaves
     * the pair with one non-grace note, which no beam joins.
     *
     * <p>The two removal behaviors differ on purpose. From select mode, a selection inside a beam
     * removes the whole beam. While typing, one beam joining both elements is broken
     * <em>between</em> them and its parts are kept: a beam {@code [a,e]} broken between
     * {@code p} and {@code t} yields {@code [a,p]} and {@code [t,e]}, each kept only when
     * {@link Beam#spanning} accepts it, so a part of one note is dropped. Grace notes between the
     * two elements fall in the gap and belong to neither part. The break removes the beam and
     * adds its parts, so every step is a tracked mutation and undo restores the original beam.
     *
     * <p>The add case agrees with the select-mode toggle over the same two notes: a beam absorbs
     * every beam it overlaps, so with beams [0,1] and [2,3], beaming element 2 to its
     * predecessor yields a single [0,3].
     *
     * @param line         the line holding the element
     * @param elementIndex the index of the later of the two elements; one outside {@code line}
     *                     modifies nothing
     * @return {@code true} when the line was modified
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

    /** Adds the beam {@link Beam#spanning} builds over {@code range}, when it builds one. */
    private static void addBeamOver(Line line, ElementRange range) {
        if (Beam.spanning(line, range) instanceof Spanning.Spanned<Beam>(var beam, var _)) {
            beam.addTo();
        }
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
        // between them stays outside the tie.
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
            toggleTieBetween(line.noteAt(beginIndex), line.noteAt(endIndex));

            return true;
        });
    }

    /**
     * Removes the tie from {@code anchor} when it ends on {@code end}, and otherwise ties
     * {@code anchor}, recording either on the anchor's line. The one statement of "if tied,
     * remove, else add" behind every tie toggle; whether the pair is already tied is
     * {@link NoteElement#isTiedTo}'s question.
     *
     * @param anchor the earlier note of the pair, in a line
     * @param end    the later note, which a tie added to {@code anchor} ends on
     */
    private static void toggleTieBetween(NoteElement anchor, NoteElement end) {
        var anchorLine = anchor.requireParentLine();

        if (anchor.isTiedTo(end)) {
            anchorLine.removeTie(anchor);
        } else {
            anchorLine.addTie(anchor);
        }
    }

    /**
     * Toggles a tie between the element at {@code elementIndex} and the note before it, and
     * returns whether the line was modified.
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
     * Toggles the cross-line tie candidate at {@code index} in {@code line}, if any.
     * <p>
     * The tie is recorded on the anchor's line, which is the line before {@code line} when
     * {@code index} is where a tie enters {@code line}. Add-versus-remove is decided over the two
     * notes ({@link #toggleTieBetween}), so a same-line tie that merely ends on the boundary
     * note (a chained tie) is never mistaken for the boundary tie itself.
     *
     * @return Whether a partner was found and the tie toggled
     */
    private static boolean toggleBoundaryTie(Line line, int index) {
        var boundaryTie = RangeQueries.boundaryTieAt(line, index);

        if (boundaryTie == null) {
            return false;
        }

        toggleTieBetween(boundaryTie.anchor(), boundaryTie.end());

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

    /**
     * Returns what the tuplet menu offers for the current selection.
     *
     * @return {@link RangeQueries#canToggleTuplet}'s answer for the selection, or
     *     {@link TupletToggleInfo#NONE} when no element range is selected
     */
    public TupletToggleInfo canToggleTuplet() {
        var selection = selectedRange();
        return (selection != null) ? RangeQueries.canToggleTuplet(selection) : TupletToggleInfo.NONE;
    }

    /**
     * Makes the current selection a tuplet of {@code grade}, judging the selection when it
     * runs. The tuplet covers the selection from its first non-grace element to its last
     * ({@link RangeQueries#tupletSite}), and {@link RangeQueries#tupletOverlap} decides what
     * happens:
     * <ul>
     *   <li>{@link RangeQueries.TupletOverlap.NoTuplet}: adds the tuplet of {@code grade},
     *       when {@link Tuplet#spanning} accepts it there.</li>
     *   <li>{@link RangeQueries.TupletOverlap.Covers} a tuplet of another grade: replaces it
     *       with the tuplet of {@code grade}, when {@link Tuplet#spanning} accepts that.</li>
     *   <li>{@link RangeQueries.TupletOverlap.Covers} a tuplet of {@code grade}: nothing. The
     *       grade is shown checked, and a checked item that deleted what it reports on would
     *       be a trap.</li>
     *   <li>{@link RangeQueries.TupletOverlap.PartlyInside}: nothing, since a tuplet over the
     *       selection would destroy the one it reaches into.</li>
     * </ul>
     * Deleting a tuplet is not a toggle: the user selects its number or bracket and deletes
     * it. Does nothing when no element range is selected or it holds only grace notes.
     *
     * @param grade the grade asked for
     * @effects adds one tuplet, or removes one and adds its replacement, inside one modification
     *     bracket; the removal is not reported
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

    /**
     * Adds the tuplet of {@code grade} that {@link Tuplet#spanning} builds at {@code site},
     * removing {@code replaced} first, or does nothing when the factory refuses.
     *
     * @param replaced the tuplet the new one replaces, or {@code null} when it replaces none
     */
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

    // ========== Dynamics Operations ==========

    /**
     * Adds a {@code kind} hairpin over the current selection, or extends a same-kind hairpin
     * to cover it, as {@link RangeQueries#resolveHairpin} resolves the selection when this
     * runs. Does nothing when no element range is selected or the resolution is
     * {@link RangeQueries.HairpinResolution.Ineligible} or
     * {@link RangeQueries.HairpinResolution.Blocked}.
     * <p>
     * The hairpin added is the one the resolution carries, so the menu item's wording and the
     * edit cannot disagree: an "Extend Crescendo" click never adds a second stray crescendo.
     *
     * @param kind the hairpin kind to add
     * @effects inside one modification bracket named by the resolution's
     *     {@link RangeQueries.HairpinResolution#nameKey}: adds the hairpin, which absorbs the
     *     same-kind hairpins it reaches, and removes the point dynamic from every element
     *     strictly inside the hairpin's range
     */
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
     * Resolves what the {@code kind} hairpin menu item does for the current selection.
     *
     * @param kind the hairpin kind the menu item adds
     * @return {@link RangeQueries#resolveHairpin} for the selection, or
     *     {@link RangeQueries.HairpinResolution.Ineligible} when no element range is selected,
     *     which includes a selected slide, ending or hairpin
     */
    public RangeQueries.HairpinResolution resolveHairpinAction(Hairpin.Kind kind) {
        var selection = selectedRange();

        return (selection != null)
            ? RangeQueries.resolveHairpin(selection, kind)
            : new RangeQueries.HairpinResolution.Ineligible(kind);
    }

    // ========== First-Second Ending Operations ==========

    /**
     * Returns whether a first/second ending can be made from the current selection. It serves
     * the menu item's enabled state; {@link #makeFirstSecondEnding} judges again when it runs.
     *
     * @return {@code true} when an element range is selected and
     *     {@link RangeQueries#firstSecondEnding} answers {@link Spanning.Spanned} for it
     */
    public boolean canMakeFirstSecondEnding() {
        var selection = selectedRange();

        return (selection != null)
            && RangeQueries.firstSecondEnding(selection) instanceof Spanning.Spanned<Ending>;
    }

    /**
     * Makes a first/second ending from the current selection, judging the selection when it
     * runs, and returns whether it made one.
     *
     * @return {@code true} when an element range is selected and
     *     {@link RangeQueries#firstSecondEnding} answers {@link Spanning.Spanned} for it;
     *     {@code false}, with the song unchanged, otherwise
     * @effects adds the ending {@link RangeQueries#firstSecondEnding} built, inside one
     *     modification bracket
     */
    public boolean makeFirstSecondEnding() {
        var selection = selectedRange();

        if (selection == null
            || !(RangeQueries.firstSecondEnding(selection) instanceof Spanning.Spanned<Ending>(var ending, var _))) {
            return false;
        }

        selection.line().getSong().withModification(ending::addTo);

        return true;
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
        var selection = selectedRange();

        if (selection == null) {
            return;
        }

        var line = selection.line();
        var spans = line.spanRegistry();
        var bounds = selection.range();
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
                // over it and it keeps its own stem, so it flips on its own.
                var beam = spans.findBeamJoining(i);

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

            // Apply the same change to every note a tie chain joins to the selection, in
            // whichever line each one sits.
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
