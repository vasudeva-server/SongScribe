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

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.stream.IntStream;

import javax.swing.JOptionPane;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import kotlin.Pair;
import songscribe.data.BeamInterval;
import songscribe.data.DynamicsInterval;
import songscribe.data.EndingInterval;
import songscribe.data.Interval;
import songscribe.data.TupletInterval;
import songscribe.data.IntervalSet;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TieContext;

/**
 * Handles music editing operations for a composition.
 * Extracted from Score.java as part of Phase 5 of the Score Cleanup refactoring.
 */
public final class MusicEditOperations {

    private final Composition composition;
    private final SelectionCoordinator coordinator;
    private final IMainFrame mainFrame;

    public MusicEditOperations(
        @NotNull Composition composition,
        @NotNull SelectionCoordinator coordinator,
        @NotNull IMainFrame mainFrame
    ) {
        this.composition = composition;
        this.coordinator = coordinator;
        this.mainFrame = mainFrame;
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

        if (state.shouldConnectSelection(beamings)) {
            beamings.addInterval(new BeamInterval(state.getSelectionBegin(), state.getSelectionEnd()));
            BeamCalculator.calculateLengthenings(state.getSelectionBegin(), line, true);
        } else {
            beamings.removeInterval(state.getSelectionBegin(), state.getSelectionEnd());
            BeamCalculator.calculateLengthenings(state.getSelectionBegin(), line, true);
            BeamCalculator.calculateLengthenings(state.getSelectionEnd(), line, true);
        }

        composition.setModified(true);
    }

    // ========== Tie Operations ==========

    public TieContext getTieContext() {
        var state = coordinator.getActiveSelection();
        return (state != null) ? state.getTieContext() : null;
    }

    public boolean canToggleTie() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canToggleTie();
    }

    public void toggleTie() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        // Get the context if necessary
        if (state.getTieContext() == null) {
            state.canToggleTie();
        }

        var line = state.getLine();
        var intervals = state.getTieContext().intervals();

        if (intervals != null) {
            intervals.removeInterval(state.getSelectionBegin(), state.getSelectionEnd());
        } else {
            line.getTies().addInterval(state.getSelectionBegin(), state.getSelectionEnd());
        }

        state.setTieContext(null);
        composition.setModified(true);
    }

    // ========== Tuplet Operations ==========

    @NotNull
    @Contract(" -> new")
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
        var interval = tuplets.findInterval(state.getSelectionBegin());

        if ((interval == null) || (tupletSize > 0)) {
            if (interval == null) {
                interval = tuplets.addInterval(new TupletInterval(
                    state.getSelectionBegin(), state.getSelectionEnd(), tupletSize));
            } else {
                interval.setGrade(tupletSize);
            }
        } else {
            tuplets.removeInterval(state.getSelectionBegin(), state.getSelectionEnd());
        }

        composition.setModified(true);
    }

    // ========== Dynamics Operations ==========

    public void addDynamicsToSelection(boolean crescendo) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var intervalSet = crescendo
            ? line.getCrescendos()
            : line.getDiminuendos();
        intervalSet.addInterval(new DynamicsInterval(state.getSelectionBegin(), state.getSelectionEnd()));
        composition.setModified(true);
    }

    public boolean canRemoveDynamicsFromSelection() {
        var state = coordinator.getActiveSelection();

        if (state == null || !state.hasNoteSelection()) {
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
        var crescendos = line.getCrescendos();
        var intervals = getDynamicsIntervalsFromSelection(state);
        var crescendoIntervals = intervals.getFirst();

        for (var interval : crescendoIntervals) {
            crescendos.removeInterval(interval);
        }

        var diminuendos = line.getDiminuendos();
        var diminuendoIntervals = intervals.getSecond();

        for (var interval : diminuendoIntervals) {
            diminuendos.removeInterval(interval);
        }

        composition.setModified(true);
    }

    @NotNull
    @Contract("_ -> new")
    private Pair<
        ArrayList<DynamicsInterval>,
        ArrayList<DynamicsInterval>
        > getDynamicsIntervalsFromSelection(@NotNull LineSelectionState state) {
        var line = state.getLine();
        var crescendos = line.getCrescendos();
        var diminuendos = line.getDiminuendos();
        var crescendoIntervals = new ArrayList<DynamicsInterval>();
        var diminuendoIntervals = new ArrayList<DynamicsInterval>();

        for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
            var interval = crescendos.findInterval(i);

            if (interval != null) {
                crescendoIntervals.add(interval);
            }

            interval = diminuendos.findInterval(i);

            if (interval != null) {
                diminuendoIntervals.add(interval);
            }
        }

        return new Pair<>(crescendoIntervals, diminuendoIntervals);
    }

    // ========== First-Second Ending Operations ==========

    public boolean canMakeFirstSecondEnding() {
        return true;
    }

    public void makeFirstSecondEnding() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        var repeatExists = IntStream.rangeClosed(
            state.getSelectionBegin(),
            state.getSelectionEnd()
        ).anyMatch(i -> line.getNote(i).getNoteType() == NoteType.REPEAT_RIGHT);

        if (!repeatExists) {
            var answer = mainFrame.showConfirmDialog(
                """
                    It does not make sense to create a first-second ending without a right side \
                    repeat.

                    Do you want to continue anyway?""",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );

            if (answer == JOptionPane.NO_OPTION) {
                return;
            }
        }

        line.getFirstSecondEndings().addInterval(new EndingInterval(state.getSelectionBegin(), state.getSelectionEnd(), 1));
        composition.setModified(true);
    }

    public void removeFirstSecondEnding() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        var line = state.getLine();
        line
            .getFirstSecondEndings()
            .removeInterval(state.getSelectionBegin(), state.getSelectionEnd());
        composition.setModified(true);
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

        for (var note : line.getNotes(state.getSelectionBegin(), state.getSelectionEnd())) {
            note.setTrill(!note.isTrill());
        }

        composition.setModified(true);
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
        var note = line.getNote(state.getSelectionBegin());
        note.setForceSyllable(!note.isForceSyllable());
        LyricsProcessor.spellLyrics(line);
        composition.setModified(true);
    }

    // ========== Partial Beam Operations ==========

    public boolean canFlipPartialBeamOrientation() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canFlipPartialBeamOrientation();
    }

    public void flipPartialBeamOrientation() {
        // Partial beam stub direction is now automatic from rhythmic context.
        // This operation is a no-op.
    }

    // ========== Stem Direction Operations ==========

    public boolean canFlipStemDirection() {
        var state = coordinator.getActiveSelection();
        return (state != null) && state.canFlipStemDirection();
    }

    public void flipStemDirection() {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            mainFrame.showInfoMessage(
                "You must select one or more notes in order to flip their stem direction."
            );
            return;
        }

        var line = state.getLine();

        for (var note : line.getNotes(state.getSelectionBegin(), state.getSelectionEnd())) {
            note.setUpper(!note.isUpper());
        }

        Interval lastInterval = null;

        for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
            var inverval = line.getBeamings().findInterval(i);

            //noinspection ObjectEquality
            if ((inverval != null) && (inverval != lastInterval)) {
                BeamCalculator.calculateLengthenings(i, line, false);
                lastInterval = inverval;
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

        lastInterval = null;

        for (var i : tiePartnersToFlip) {
            var note = line.getNote(i);
            note.setUpper(!note.isUpper());

            var beamInterval = line.getBeamings().findInterval(i);

            //noinspection ObjectEquality
            if ((beamInterval != null) && (beamInterval != lastInterval)) {
                BeamCalculator.calculateLengthenings(i, line, false);
                lastInterval = beamInterval;
            }
        }

        composition.setModified(true);
    }

    // ========== Tempo Operations ==========

    public boolean canChangeTempo() {
        return coordinator.canChangeTempo();
    }
}
