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

package songscribe.music;

import module java.desktop;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.TreeSet;

import kotlin.Pair;

import songscribe.Strings;
import songscribe.message.mutation.ElementField;
import songscribe.ui.layout.Ending;
import songscribe.ui.OptionDialogs;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Handles music editing operations for a composition.
 * Extracted from Score.java as part of Phase 5 of the Score Cleanup refactoring.
 */
public final class MusicEditOperations {

    private static final int MIN_CONTENT_ELEMENTS = 4;

    private final Composition composition;
    private final SelectionCoordinator coordinator;

    public MusicEditOperations(
        Composition composition,
        SelectionCoordinator coordinator
    ) {
        this.composition = composition;
        this.coordinator = coordinator;
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
        var beamings = line.getBeamings();

        line.withModification(() -> {
            if (state.shouldConnectSelection(beamings)) {
                line.addBeaming(new BeamInterval(state.getSelectionBegin(), state.getSelectionEnd()));
            } else {
                var existing = beamings.findInterval(state.getSelectionBegin());

                if (existing == null) {
                    throw new IllegalStateException(
                        "toggleBeaming remove branch entered with no beam interval at "
                            + state.getSelectionBegin()
                    );
                }

                line.removeBeaming(existing);
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
        var ties = line.getTies();

        line.withModification(() -> {
            var existing = ties.findInterval(state.getSelectionBegin());

            if (existing != null) {
                line.removeTie(existing);
            } else {
                line.addTie(new TieInterval(state.getSelectionBegin(), state.getSelectionEnd()));
            }
        });

        state.resetTieState();
    }

    // ========== Tuplet Operations ==========

    public Pair<Boolean, Boolean> canToggleTuplet() {
        var state = coordinator.getActiveSelection();
        return (state != null) ? state.canToggleTuplet() : new Pair<>(false, false);
    }

    public void toggleTuplet(int tupletSize) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var tuplets = line.getTuplets();

        line.withModification(() -> {
            var existing = tuplets.findInterval(state.getSelectionBegin());

            if (existing != null) {
                line.removeTuplet(existing);
            } else if (tupletSize > 0) {
                line.addTuplet(new TupletInterval(
                    state.getSelectionBegin(), state.getSelectionEnd(), tupletSize));
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

        line.withModification(() -> {
            var interval = new DynamicsInterval(state.getSelectionBegin(), state.getSelectionEnd());

            if (crescendo) {
                line.addCrescendo(interval);
            } else {
                line.addDiminuendo(interval);
            }
        });
    }

    public boolean canRemoveDynamicsFromSelection() {
        var state = coordinator.getActiveSelection();

        if (state == null || !state.hasElementSelection()) {
            return false;
        }

        var intervals = getDynamicsIntervalsFromSelection(state);

        return (
            !intervals.getFirst().isEmpty() || !intervals.getSecond().isEmpty()
        );
    }

    public void removeDynamicsFromSelection() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var intervals = getDynamicsIntervalsFromSelection(state);

        line.withModification(() -> {
            for (var interval : intervals.getFirst()) {
                line.removeCrescendo(interval);
            }

            for (var interval : intervals.getSecond()) {
                line.removeDiminuendo(interval);
            }
        });
    }

    private Pair<
        ArrayList<DynamicsInterval>,
        ArrayList<DynamicsInterval>
        > getDynamicsIntervalsFromSelection(LineSelectionState state) {
        var line = state.getLine();
        var crescendos = line.getCrescendos();
        var diminuendos = line.getDiminuendos();
        var crescendoIntervals = new ArrayList<DynamicsInterval>();
        var diminuendoIntervals = new ArrayList<DynamicsInterval>();
        var end = state.getSelectionEnd();
        var i = state.getSelectionBegin();

        while (i <= end) {
            var cres = crescendos.findInterval(i);

            if (cres != null) {
                crescendoIntervals.add(cres);
                i = cres.end + 1;
                continue;
            }

            var dim = diminuendos.findInterval(i);

            if (dim != null) {
                diminuendoIntervals.add(dim);
                i = dim.end + 1;
                continue;
            }

            i++;
        }

        return new Pair<>(crescendoIntervals, diminuendoIntervals);
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

        // Stage 1: Structural validation
        var rightRepeatIndex = validateEndingStructure(line, begin, end);

        if (rightRepeatIndex < 0) {
            return EndingValidationResult.invalid();
        }

        // Stage 2: Overlap check
        if (hasOverlap(line, begin, end)) {
            return EndingValidationResult.invalid();
        }

        // Stage 3: Backward search for enclosing repeated section
        var lineIndex = composition.indexOfLine(line);

        if (!hasEnclosingRepeat(lineIndex, begin)) {
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

            if (type == ElementType.REPEAT_RIGHT) {
                if (rightRepeatIndex >= 0) {
                    return -1;
                }

                rightRepeatIndex = i;
            }
        }

        if (contentCount < MIN_CONTENT_ELEMENTS || rightRepeatIndex < 0) {
            return -1;
        }

        // Selection must end with a terminal element
        if (!line.getElement(end).getType().isTerminal()) {
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

    // Returns true if any element in the selection range overlaps an existing ending interval.
    private boolean hasOverlap(Line line, int begin, int end) {
        for (var i = begin; i <= end; i++) {
            if (line.isInsideAnyEnding(i)) {
                return true;
            }
        }

        return false;
    }

    // Walks backward from just before the selection start, across lines if needed,
    // looking for an enclosing repeated section.
    private boolean hasEnclosingRepeat(int lineIndex, int selectionBegin) {
        // Special case: selection starts at beginning of composition
        if (lineIndex == 0 && selectionBegin == 0) {
            return false;
        }

        // Determine starting point for backward search
        var searchLineIndex = lineIndex;
        var searchElementIndex = selectionBegin - 1;

        if (searchElementIndex < 0) {
            searchLineIndex--;

            if (searchLineIndex < 0) {
                return false;
            }

            searchElementIndex = composition.getLine(searchLineIndex).elementCount() - 1;
        }

        // Walk backward
        while (searchLineIndex >= 0) {
            var searchLine = composition.getLine(searchLineIndex);

            while (searchElementIndex >= 0) {
                var type = searchLine.getElement(searchElementIndex).getType();

                if (type == ElementType.REPEAT_LEFT || type == ElementType.REPEAT_LEFT_RIGHT) {
                    return true;
                }

                if (type == ElementType.REPEAT_RIGHT) {
                    return false;
                }

                // All other elements are skipped
                searchElementIndex--;
            }

            searchLineIndex--;

            if (searchLineIndex >= 0) {
                searchElementIndex = composition.getLine(searchLineIndex).elementCount() - 1;
            }
        }

        // Reached beginning of composition — eligible
        return true;
    }

    // Examines the element immediately before the selection start and determines
    // what action is needed (barline insertion, interval extension, or invalid).
    private EndingValidationResult checkPrecedingElement(
        int lineIndex, int selectionBegin, int selectionEnd
    ) {
        // Find the preceding element (may be on a previous line)
        var precedingLineIndex = lineIndex;
        var precedingElementIndex = selectionBegin - 1;

        if (precedingElementIndex < 0) {
            precedingLineIndex--;

            if (precedingLineIndex < 0) {
                // Beginning of composition — invalid
                return EndingValidationResult.invalid();
            }

            precedingElementIndex = composition.getLine(precedingLineIndex).elementCount() - 1;
        }

        var precedingLine = composition.getLine(precedingLineIndex);
        var precedingType = precedingLine.getElement(precedingElementIndex).getType();

        if (precedingType.isContentElement()) {
            // Auto-insert barline; interval starts at selectionBegin (barline goes there)
            return EndingValidationResult.valid(
                EndingValidationResult.PrecedingAction.INSERT_BARLINE,
                selectionBegin,
                selectionEnd
            );
        }

        if (precedingType == ElementType.SINGLE_BARLINE || precedingType == ElementType.REPEAT_LEFT) {
            // Extend interval start backward to include the preceding element
            return EndingValidationResult.valid(
                EndingValidationResult.PrecedingAction.EXTEND_INTERVAL,
                precedingElementIndex,
                selectionEnd
            );
        }

        // Right repeat, left/right repeat, double barline, or final double barline — invalid
        return EndingValidationResult.invalid();
    }

    public void makeFirstSecondEnding(EndingValidationResult result) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();

        line.withModification(() -> {
            var start = result.getIntervalStart();
            var end = result.getIntervalEnd();

            switch (result.getPrecedingAction()) {
                case INSERT_BARLINE -> {
                    var barline = ElementType.SINGLEBARLINE.newInstance();
                    line.addElement(start, barline);
                    // addElement shifts all existing intervals; adjust our bounds
                    // to account for the inserted element
                    start++;
                    end++;
                }

                case EXTEND_INTERVAL -> {
                    // Start already includes the preceding barline/repeat
                }

                case NONE -> {
                    // No adjustment needed
                }
            }

            var startElement = line.getElement(start);
            var endElement = line.getElement(end);
            line.addRangeElement(new Ending(startElement, endElement, Ending.Type.FIRST));
        });
    }

    // ========== Trill Operations ==========

    public boolean canToggleTrill() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canToggleTrill();
    }

    public void toggleTrill() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();

        line.withModification(() -> {
            for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
                var index = i;
                line.modifyElement(
                    index,
                    ElementField.TRILL,
                    () -> {
                        var note = line.getElement(index);
                        note.setTrill(!note.isTrill());
                    }
                );
            }
        });
    }

    // ========== Lyrics Under Rests Operations ==========

    public boolean canToggleLyricsUnderRests() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canToggleLyricsUnderRests();
    }

    public void toggleLyricsUnderRests() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var index = state.getSelectionBegin();

        line.withModification(() -> line.modifyElement(
            index,
            ElementField.FORCE_SYLLABLE,
            () -> {
                var note = line.getElement(index);
                note.setForceSyllable(!note.isForceSyllable());
            }
        ));

        LyricsProcessor.spellLyrics(line);
    }

    // ========== Stem Direction Operations ==========

    public boolean canFlipStemDirection() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canFlipStemDirection();
    }

    public void flipStemDirection() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            OptionDialogs.showInfoMessage(
                null,
                Strings.ALERT_TITLE_STEM_DIRECTION,
                Strings.ERROR_STEM_NO_SELECTION
            );
            return;
        }

        var line = state.getLine();
        var stemFields = EnumSet.of(ElementField.UPPER, ElementField.STEM_DIRECTION_AUTO);

        line.withModification(() -> {
            // Track which beam groups have already been processed to avoid double-flipping.
            var processedBeamIntervals = new HashSet<Interval>();

            for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
                var note = line.getElement(i);

                if (note.getType().isRest()) {
                    continue;
                }

                var beamInterval = line.getBeamings().findInterval(i);

                if (beamInterval != null) {
                    // Flip the whole beam group together, once per group.
                    if (processedBeamIntervals.add(beamInterval)) {
                        var firstElement = line.getElement(beamInterval.getStart());
                        boolean newUpper = !firstElement.isUpper();

                        for (var j = beamInterval.getStart(); j <= beamInterval.getEnd(); j++) {
                            var beamIndex = j;
                            line.modifyElement(beamIndex, stemFields, () -> {
                                var beamElement = line.getElement(beamIndex);
                                beamElement.setStemDirectionAuto(false);
                                beamElement.setUpper(newUpper);
                            });
                        }
                    }
                } else {
                    var noteIndex = i;
                    boolean newUpper = !note.isUpper();
                    line.modifyElement(noteIndex, stemFields, () -> {
                        var target = line.getElement(noteIndex);
                        target.setStemDirectionAuto(false);
                        target.setUpper(newUpper);
                    });
                }
            }

            // Flip tie partners that fall outside the selection. IntervalSet merges
            // adjacent ties, so the interval may span more than two notes; all notes
            // in the interval that weren't already covered by the selection must flip.
            var tiePartnersToFlip = new TreeSet<Integer>();

            for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
                var tieInterval = line.getTies().findInterval(i);

                if (tieInterval == null) {
                    continue;
                }

                for (var j = tieInterval.start; j <= tieInterval.end; j++) {
                    if ((j < state.getSelectionBegin()) || (j > state.getSelectionEnd())) {
                        tiePartnersToFlip.add(j);
                    }
                }
            }

            for (var i : tiePartnersToFlip) {
                var partnerIndex = i;
                boolean newUpper = !line.getElement(partnerIndex).isUpper();
                line.modifyElement(partnerIndex, stemFields, () -> {
                    var note = line.getElement(partnerIndex);
                    note.setStemDirectionAuto(false);
                    note.setUpper(newUpper);
                });
            }
        });
    }

    // ========== Tempo Operations ==========

    public boolean canChangeTempo() {
        return coordinator.canChangeTempo();
    }
}
