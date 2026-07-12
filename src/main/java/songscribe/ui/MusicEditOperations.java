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

import songscribe.message.mutation.ElementField;
import songscribe.dom.Beam;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.EndingValidationResult;
import songscribe.dom.Line;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.ui.selection.LineSelectionState;
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

        line.withModification(() -> {
            var beginBeam = line.findBeamAt(state.getSelectionBegin());
            var endBeam = line.findBeamAt(state.getSelectionEnd());

            //noinspection ObjectEquality
            if (beginBeam == null || beginBeam != endBeam) {
                var anchorElement = line.getElement(state.getSelectionBegin());
                var endElement = line.getElement(state.getSelectionEnd());
                line.addBeaming(new Beam(anchorElement, endElement));
            } else {
                line.removeBeaming(beginBeam);
            }
        });
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

        line.withModification(() -> {
            var exactTie = line.findExactTie(state.getSelectionBegin(), state.getSelectionEnd());

            if (exactTie == null) {
                var anchorElement = line.getElement(state.getSelectionBegin());
                var endElement = line.getElement(state.getSelectionEnd());
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
     * requested grade matches → remove (toggle-off semantics); (4) existing tuplet, full
     * coverage, different grade → remove then add in one bracket (emits TupletRemoval +
     * TupletAddition); (5) existing tuplet, selection is a strict sub-range → rejected with
     * {@link IllegalStateException} so a programmatic caller cannot silently replace a tuplet
     * with a sub-range tuplet.
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

        if (!info.canToggle()) {
            throw new IllegalStateException(
                "toggleTuplet called with info.canToggle() == false; caller must check canToggleTuplet() first");
        }

        var line = state.getLine();
        var existing = info.existing();

        if (tupletSize == 0) {
            if (existing == null) {
                throw new IllegalStateException(
                    "toggleTuplet(0) requires an existing tuplet at the selection");
            }

            line.withModification(() -> line.removeTuplet(existing));
            return;
        }

        if (existing == null) {
            line.withModification(() -> line.addTuplet(new Tuplet(
                line.getElement(state.getSelectionBegin()),
                line.getElement(state.getSelectionEnd()),
                tupletSize)));
            return;
        }

        if (!info.coversExisting()) {
            throw new IllegalStateException(
                "toggleTuplet with a strict sub-range of an existing tuplet is not allowed");
        }

        line.withModification(() -> {
            line.removeTuplet(existing);

            if (existing.getGrade() != tupletSize) {
                line.addTuplet(new Tuplet(
                    line.getElement(state.getSelectionBegin()),
                    line.getElement(state.getSelectionEnd()),
                    tupletSize));
            }
        });
    }

    // ========== Dynamics Operations ==========

    public void addDynamicsToSelection(boolean crescendo) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var anchorElement = line.getElement(state.getSelectionBegin());
        var endElement = line.getElement(state.getSelectionEnd());

        line.withModification(() -> {
            if (crescendo) {
                line.addCrescendo(new Crescendo(anchorElement, endElement));
            } else {
                line.addDiminuendo(new Diminuendo(anchorElement, endElement));
            }
        });
    }

    public boolean canAddDynamicsToSelection() {
        var state = coordinator.getActiveSelection();

        if (state == null || !state.hasElementSelection()) {
            return false;
        }

        var hairpins = getDynamicsFromSelection(state);

        return hairpins.crescendos().isEmpty() && hairpins.diminuendos().isEmpty();
    }

    public boolean canRemoveDynamicsFromSelection() {
        var state = coordinator.getActiveSelection();

        if (state == null || !state.hasElementSelection()) {
            return false;
        }

        var hairpins = getDynamicsFromSelection(state);

        return !hairpins.crescendos().isEmpty() || !hairpins.diminuendos().isEmpty();
    }

    public void removeDynamicsFromSelection() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var hairpins = getDynamicsFromSelection(state);

        line.withModification(() -> {
            for (var hairpin : hairpins.crescendos()) {
                line.removeCrescendo(hairpin);
            }

            for (var hairpin : hairpins.diminuendos()) {
                line.removeDiminuendo(hairpin);
            }
        });
    }

    private record DynamicsSelection(
        List<Crescendo> crescendos,
        List<Diminuendo> diminuendos
    ) {}

    private DynamicsSelection getDynamicsFromSelection(LineSelectionState state) {
        var line = state.getLine();
        var selectionBegin = state.getSelectionBegin();
        var selectionEnd = state.getSelectionEnd();
        var crescendoList = new ArrayList<Crescendo>();
        var diminuendoList = new ArrayList<Diminuendo>();

        for (var re : line.getRangeElements()) {
            if (re instanceof Crescendo cres && cres.overlaps(selectionBegin, selectionEnd)) {
                crescendoList.add(cres);
            } else if (re instanceof Diminuendo dim && dim.overlaps(selectionBegin, selectionEnd)) {
                diminuendoList.add(dim);
            }
        }

        return new DynamicsSelection(crescendoList, diminuendoList);
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
        return checkPrecedingElement(lineIndex, begin, end);
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

            if (type.isContentElement()) {
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

    // Walks backward from just before the selection start, across lines if needed,
    // looking for an enclosing repeated section.
    private boolean hasEnclosingRepeat(int lineIndex, int selectionBegin) {
        // Determine starting point for backward search
        var searchLineIndex = lineIndex;
        var searchElementIndex = selectionBegin - 1;

        if (searchElementIndex < 0) {
            searchLineIndex--;

            if (searchLineIndex < 0) {
                return lineIndex == 0;
            }

            searchElementIndex = song.getLine(searchLineIndex).elementCount() - 1;
        }

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

        // Reached beginning of song — valid only on the first line
        return lineIndex == 0;
    }

    // Examines the element immediately before the selection start and determines
    // what action is needed (barline insertion, span extension, or invalid).
    private EndingValidationResult checkPrecedingElement(
        int lineIndex, int selectionBegin, int selectionEnd
    ) {
        // Find the preceding element (may be on a previous line)
        var precedingLineIndex = lineIndex;
        var precedingElementIndex = selectionBegin - 1;

        if (precedingElementIndex < 0) {
            precedingLineIndex--;

            if (precedingLineIndex < 0) {
                // Beginning of song — valid; the song start acts as an implicit left repeat
                return EndingValidationResult.valid(
                    EndingValidationResult.PrecedingAction.NONE,
                    selectionBegin,
                    selectionEnd
                );
            }

            precedingElementIndex = song.getLine(precedingLineIndex).elementCount() - 1;
        }

        var precedingLine = song.getLine(precedingLineIndex);
        var precedingType = precedingLine.getElement(precedingElementIndex).getType();

        if (precedingType.isContentElement()) {
            // Content predecessor — anchor the 1st bracket to the note at
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

        // Right repeat, double barline, or final double barline — invalid
        // (REPEAT_LEFT_RIGHT is handled by the EXTEND_SPAN branch above).
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

                if (note.getType().isRest()) {
                    continue;
                }

                var beam = line.findBeamAt(i);

                if (beam != null) {
                    // Modify the whole beam group together, once per group. The group's new
                    // direction is derived once from its anchor so every member agrees.
                    if (processedBeams.add(beam)) {
                        var anchorIndex = beam.getAnchorElementIndex();
                        var newDirection = flippedDirection(line, anchorIndex, change);

                        for (var j = anchorIndex; j <= beam.getEndElementIndex(); j++) {
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
