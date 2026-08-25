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

package songscribe.ui.component.score;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.message.mutation.ElementField;
import songscribe.ui.EndingConfirms;
import songscribe.ui.OptionDialogs;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.undo.OpNames;

/**
 * Turns a click on the hover preview into an edit: an append, an insert, or a replacement.
 * <p>
 * The tracking state the click resolves against — which line, which insertion index, whether the
 * pointer is over an existing element head — belongs to {@link PreviewElementManager} and is read
 * from it here. What lives in this class is everything that happens once the click is known to be
 * legal: accidental reconciliation, the spring-solver fit check, the confirmations, and the
 * mutations themselves.
 */
final class PreviewElementInserter {

    private PreviewElementInserter() {
    }

    /**
     * Handles a click on the preview element, performing the appropriate action
     * (append, insert, or modify). Called from {@code LineComponent.mouseClicked()}.
     * <p>
     * When {@code forceInsert} is {@code true} always inserts rather than modifying an existing
     * element at the same x position. Used by grace mode, which must insert a new host note even
     * when an existing note occupies the locked insertion slot.
     */
    static void handleClick(LineComponent lc, boolean forceInsert) {
        if (!PreviewElementManager.isPreviewClickTarget(lc)) {
            return;
        }

        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var previewElement = EditModeManager.getPreviewElement();

        // Belt-and-braces: block clicks that would try to insert past the auto-maintained
        // terminal. trackMouse already clears the preview at these positions.
        var song = line.getSong();

        if (PreviewElementManager.isPositionBlockedByTerminal(
                song, line, PreviewElementManager.getCurrentXIndex(),
                PreviewElementManager.isXPosSsMatchesElement())) {
            return;
        }

        // Route a direct click on the terminal to replaceTerminal when the active preview
        // element can legally replace it. This bypasses the normal insertion path entirely.
        if (PreviewElementManager.isXPosSsMatchesElement()) {
            if (previewElement != null
                    && isDirectClickOnTerminal(song, line, PreviewElementManager.getCurrentXIndex())) {
                var previewType = previewElement.getType();

                if (song.canReplaceTerminal(previewType)) {
                    song.replaceTerminal(previewType);
                    return;
                }
            }
        }

        if (PreviewElementManager.isBreathMarkInsertionBlocked(
                previewElement, PreviewElementManager.getCurrentXIndex(), line,
                PreviewElementManager.isXPosSsMatchesElement())) {
            return;
        }

        // A grace note, a key signature and the barline a key signature sits behind may never be
        // replaced — ignore the click.
        if (PreviewElementManager.isXPosSsMatchesElement()
                && !line.canReplaceElementAt(PreviewElementManager.getCurrentXIndex())) {
            return;
        }

        // isPreviewClickTarget (checked at entry) guarantees a preview element; this guard
        // proves that to the null-checker before deriving the op-name.
        if (previewElement == null) {
            return;
        }

        // Decided once and used both to ask and to act, so the question can never be asked about a
        // click that then does something else.
        var appends = PreviewElementManager.getCurrentXIndex() == line.elementCount();
        var replacesExisting = !appends && !forceInsert && PreviewElementManager.isXPosSsMatchesElement();

        // Replacing a note takes its explicit accidental away, so this path asks the same question
        // every other removal path does — here, before the bracket opens, since a dialog must never
        // be open inside one. An append or an insert removes nothing and is never asked.
        var decision = replacesExisting
            ? confirmReplacementRestatements(lc, line, PreviewElementManager.getCurrentXIndex(), previewElement)
            : AccidentalRestatements.Decision.PROCEED;

        if (decision.isCancelled()) {
            return;
        }

        // Determine action based on position. Wrap in a modification bracket so the
        // line.add/setElement calls inside actually accumulate mutations and fire a
        // SongDidChangeNotification, which the ScoreViewController uses to
        // invalidate the line's cached layout.
        line.withModification(OpNames.addLabel(previewElement.getType()), () -> {
            if (appends) {
                addPreviewElement(lc, line);
            } else if (replacesExisting) {
                modifyExistingElement(lc, PreviewElementManager.getCurrentXIndex(), line, decision);
            } else {
                insertElement(lc, PreviewElementManager.getCurrentXIndex(), line);
            }
        });
    }

    /**
     * Returns {@code true} when {@code xIndex} points to the auto-maintained terminal
     * element on {@code line}.
     */
    private static boolean isDirectClickOnTerminal(Song song, Line line, int xIndex) {
        return xIndex < line.elementCount()
            && song.isAutoMaintainedTerminal(line.getElement(xIndex), line);
    }

    /**
     * Calculates the insertion result for adding an element to a line.
     * <p>
     * The line is free to compress to absorb the new element, so the error fires only when the
     * spring solver reports the line INFEASIBLE — it overflows the margin even with every gap
     * squeezed down to its collision floor. This runs <em>before</em> any mutation, so a rejected
     * insert leaves the line exactly as it was; there is nothing half-applied to compensate for.
     *
     * @param lc      The LineComponent whose line is being inserted into
     * @param line    The line to insert into
     * @param element The element to insert
     * @param index   The insertion index
     * @param layout  Layout result for position lookup; null falls back to {@code xOffset}
     * @return The insertion result, or null if the line is full
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult calculateInsertionOrShowError(
        LineComponent lc, Line line, StaffElement element, int index, @Nullable LayoutResult layout
    ) {
        var insertion = InsertionSpacingCalculator.calculateInsertion(
            line, element, index, layout, lc.getLyricRenderMetrics());
        var song = line.getSong();

        if (!insertion.fitsWithinLine(song.getLineWidthSs())) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_ELEMENT,
                element.getType().categoryName()
            );
            return null;
        }

        return insertion;
    }

    /**
     * Reconciles the accidentals this insertion changes, applies them, and returns the insertion
     * result — or null when the insertion is refused, in which case nothing has been mutated.
     * <p>
     * The inserted element itself is never materialized: it is a note the user is creating, so it
     * has no pitch it "had" and the invariant (every note keeps the pitch it had, unless the user
     * changed that note) does not reach it. That is what the empty prior-accidental list below
     * says — {@link AccidentalReconciliation} reads an empty list as "no source context, never
     * materialize these elements themselves". What does need reconciling is the other direction:
     * an element arriving with an explicit accidental changes the context reaching the following
     * notes at its staff position — and so does a barline or repeat, which carries no accidental of
     * its own but cancels every accidental before it.
     *
     * @param lc             The LineComponent whose line is being inserted into
     * @param line           The line to insert into
     * @param previewElement The element being inserted
     * @param index          The insertion index
     * @param confirmed      Any further confirmation the caller requires, run only once the
     *                       element is known to fit; false means the insertion is refused
     * @return The insertion result, or null when refused
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult materializeAndCalculateInsertion(
        LineComponent lc, Line line, StaffElement previewElement, int index, BooleanSupplier confirmed) {

        // Reconcile unconditionally: an element with no explicit accidental of its own can still
        // change the context reaching the notes after it (a barline or repeat cancels every prior
        // accidental), so "no accidental" never meant "nothing to reconcile". This pass is cheap —
        // one line's elements, once per insertion — so it is not worth guessing when to skip it.
        var accidentalChanges = AccidentalReconciliation.reconcile(new AccidentalReconciliation.InsertionRegion(
            line, index, null, List.of(previewElement), List.of(), List.of()));

        // The gate runs inside the materializer, with the accidentals applied so the projection
        // measures the right widths, so its result has to escape the lambda through a holder.
        var insertion = new InsertionSpacingCalculator.InsertionResult[1];

        var accepted = AccidentalMaterializer.applyIfAccepted(
            line, accidentalChanges, List.of(previewElement), () -> {
                var result = calculateInsertionOrShowError(lc, line, previewElement, index, lc.getLayoutResult());

                if ((result == null) || !confirmed.getAsBoolean()) {
                    return false;
                }

                insertion[0] = result;
                return true;
            });

        return accepted ? insertion[0] : null;
    }

    /**
     * Adds a preview element to the end of the line.
     *
     * @param lc   The LineComponent
     * @param line The line to add the element to
     */
    private static void addPreviewElement(LineComponent lc, Line line) {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return;
        }

        var elementCount = line.elementCount();

        if (EditModeManager.elementWasModified(line, elementCount)) {
            // This branch is reached only after elementWasModified performed a setElement
            // merging REPEAT_LEFT with REPEAT_RIGHT, so a commit always follows, and the
            // element it arms is a REPEAT_LEFT_RIGHT, which is not beamable.
            EditModeManager.previewElementDidChange(line, elementCount - 1);
            return;
        }

        // Appending needs no further confirmation: nothing follows the new element, so it can
        // invalidate no ending.
        var insertion = materializeAndCalculateInsertion(lc, line, previewElement, elementCount, () -> true);

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.ssToRoundedPx(insertion.insertedElementXSs()));
        line.addElement(previewElement);

        var newLastIndex = line.elementCount() - 1;
        EditModeManager.previewElementDidChange(line, newLastIndex);
    }

    @Nullable
    private static StaffElement validateAndGetPreviewElement(Line line, int elementIndex) {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return null;
        }

        if (EditModeManager.elementWasModified(line, elementIndex)) {
            // Same reasoning as addPreviewElement's identical guard: this branch is reached only
            // after elementWasModified performed a setElement merging REPEAT_LEFT with
            // REPEAT_RIGHT, so a commit always follows. Unlike there, the armed index is not
            // necessarily the merged element — elementWasModified's REPEAT_LEFT branch merges at
            // elementIndex - 1, leaving the arm on the untouched element after it. Harmless
            // either way: that element's predecessor is the merged REPEAT_LEFT_RIGHT, which is
            // not beamable, so a beam request against it is refused.
            EditModeManager.previewElementDidChange(line, elementIndex);
            return null;
        }

        return previewElement;
    }

    private static void insertElement(LineComponent lc, int xIndex, Line line) {
        var previewElement = validateAndGetPreviewElement(line, xIndex);

        if (previewElement == null) {
            return;
        }

        // If inserting into a tuplet, remove it — the new element changes the rhythmic grouping.
        var tuplet = line.findTupletAt(xIndex - 1);

        if ((tuplet != null) && ((xIndex - 1) < tuplet.getEndElementIndex())) {
            line.removeTuplet(tuplet);
        }

        // The ending confirm runs inside the insertion gate: declining it must leave the line
        // exactly as it was, on the same terms as a refusal for want of room.
        var insertion = materializeAndCalculateInsertion(lc, line, previewElement, xIndex,
            () -> !line.hasEndingInvalidatedByInsertion(xIndex, previewElement.getType())
                || EndingConfirms.confirmInvalidation(lc));

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.ssToRoundedPx(insertion.insertedElementXSs()));

        // Grace mode inserts its grace note with mutation tracking suspended, so a repair made
        // here would never reach the undo record. It takes the repairs over, running them
        // inside the bracket that makes the pairing undoable.
        var repairsDeferred = GraceModeManager.deferInsertionRepairs();

        if (!repairsDeferred) {
            line.repairNeighborsBeforeInsertion(xIndex, List.of(previewElement));
        }

        line.addElement(xIndex, previewElement);

        if (!repairsDeferred) {
            line.repairNeighborsAfterInsertion(xIndex, previewElement);
        }

        var shift = ScaleContext.ssToRoundedPx(insertion.shiftForSubsequentElementsSs());

        for (var i = xIndex + 1; i < line.effectiveElementCount(); i++) {
            var element = line.getElement(i);
            element.setXOffsetPx(element.getXOffsetPx() + shift);
        }

        EditModeManager.previewElementDidChange(line, xIndex);
    }

    /**
     * Asks whether replacing the element at {@code elementIndex} should also take away the later
     * notes that restate the accidental it removes.
     *
     * <p>The replacement carries the old accidental's staff position forward only when it lands at
     * that same position as a pitched note — a replacement that is not a pitched note bears no
     * accidental at all, whatever the preview holds, since applying the staff position snaps it to
     * its default place too. Anything else gives the old accidental up.
     *
     * <p>Must be called before the modification bracket opens, and its answer honored — Cancel
     * means the click does nothing at all.
     */
    private static AccidentalRestatements.Decision confirmReplacementRestatements(
        LineComponent lc, Line line, int elementIndex, StaffElement previewElement) {

        var existing = line.getElement(elementIndex);
        var staffPosition = existing.getStaffPosition();
        var landsAtSamePosition = previewElement.getType().isPitchedNote()
            && (PreviewElementManager.getCurrentStaffPosition() == staffPosition);

        return AccidentalRestatements.confirm(
            lc,
            line,
            List.of(new AccidentalRestatements.EditedNote(
                elementIndex,
                staffPosition,
                existing.getAccidental(),
                landsAtSamePosition ? previewElement.getAccidental() : null)));
    }

    /**
     * Replaces an existing element with the current preview element's type and pitch.
     * Note-entry decorations the user sets on the preview element via the toolbar/menu
     * (accidental, dot count, articulations) are taken from the preview. Other
     * decorations not settable on the preview (trill, annotation, tempo/beat change,
     * dynamic attachments, lyrics, fermata, x position) are preserved from the existing element.
     * Called when the user clicks on an existing element head with the preview element active.
     *
     * @param lc           The LineComponent
     * @param elementIndex The index of the element to replace
     * @param line         The line containing the element
     * @param decision     The restatements the user accepted for this replacement, from
     *                     {@link #confirmReplacementRestatements}
     */
    private static void modifyExistingElement(
        LineComponent lc, int elementIndex, Line line, AccidentalRestatements.Decision decision) {

        var previewElement = validateAndGetPreviewElement(line, elementIndex);

        if (previewElement == null) {
            return;
        }

        // Deep-copy the existing element under the new type to carry over all decorations
        // (fermata, trill, annotation, tempo/beat change, articulations, attachments, lyrics,
        // x position), then override with the preview's note-entry attributes.
        var existing = line.getElement(elementIndex);
        var previewType = previewElement.getType();
        var replacement = StaffElement.convertedFrom(previewType, existing);
        replacement.setDotCount(previewElement.getDotCount());
        replacement.setAccidental(previewElement.getAccidental());
        replacement.setAccidentalInParentheses(previewElement.isAccidentalInParentheses());
        replacement.setStemDirectionAuto(previewElement.isStemDirectionAuto());

        // Articulations are a note-entry decoration the user sets on the preview element via the
        // toolbar/menu, exactly like the accidental above. Carry them over from the preview,
        // overriding any inherited from the existing element. Other attachments (dynamics,
        // annotations, trills, fermata) are not preview-settable, so they remain copied from
        // the existing element by the copy constructor.
        replacement.copyArticulationsFrom(previewElement);

        // Rests snap to their default staff position; pitched notes use the mouse Y position
        PreviewElementManager.applyStaffPosition(replacement, PreviewElementManager.getCurrentStaffPosition());

        if (replacement.isStemDirectionAuto()) {
            replacement.setDirection(StaffElement.defaultDirection(replacement));
        } else {
            replacement.setDirection(previewElement.getDirection());
        }

        // Check whether replacing this element would affect a first-second ending,
        // and show the appropriate confirmation dialog if so.
        var endingEffect = line.findEndingReplacementEffect(elementIndex, replacement);

        switch (endingEffect) {
            case Ending.EndingEffect.Invalidate _ -> {
                if (!EndingConfirms.confirmInvalidation(lc)) {
                    return;
                }
                // proceed: line.setElement will remove the ending via isInvalidatedByReplacement
            }
            case Ending.EndingEffect.CompensateEnd ce -> {
                if (!EndingConfirms.confirmCompensateEnd(lc, ce)) {
                    return;
                }
                EndingConfirms.applyCompensatingEndChange(line, ce);
            }
            case Ending.EndingEffect.CompensateSplit cs -> {
                if (!EndingConfirms.confirmCompensateSplit(lc, cs, previewType)) {
                    return;
                }
                EndingConfirms.applyCompensatingSplitChange(line, cs);
            }
            case Ending.EndingEffect.None _ -> {}
        }

        // Every bail-out is behind us, so the replacement is going to happen: record the accepted
        // restatements in the same bracket, making them and the replacement one undo step. Ahead of
        // setElement so the scan still reads the pre-replacement line, and because undo replays a
        // step in reverse — the accidentals come back last, once the old element is back.
        AccidentalRestatements.commitAllLines(decision);

        // The beam sweep goes here rather than earlier, next to the element's own preparation:
        // a confirm above can still turn the user away, and a cancelled replacement that had
        // already stripped the beam would leave the bracket holding a removal with nothing to
        // justify it — the user says no and loses the beam anyway. Below the last bail-out, and
        // above setElement so reverse-order undo restores the old element before re-adding what
        // hung off it.
        //
        // Ties and tuplets are not swept here: setElement removes exactly the ones the
        // replacement invalidates, via Tie.isInvalidatedByReplacement and
        // Tuplet.isInvalidatedByReplacement. Only the beam still needs a hand, because its
        // repair is not a yes-or-no question — the select-mode path trims a beam to its
        // beamable middle rather than dropping it, which a removal hook cannot express.

        // Every beam touching this element — the new element type may not be beamable at all.
        var beam = line.findBeamAt(elementIndex);

        while (beam != null) {
            line.removeBeaming(beam);
            beam = line.findBeamAt(elementIndex);
        }

        // Replace the element entirely (line.setElement marks the song modified)
        line.setElement(elementIndex, replacement);

        // Grace note cleanup: if the preceding element is a paired grace note (grace + connected
        // glissando to the replaced host), and the replacement is not a pitched note, remove the
        // grace note. For pitched note replacements the glissando reattaches automatically since
        // setElement preserves the element index.
        if (line.isHostOfPairedGraceNote(elementIndex)
                && !previewType.isPitchedNote()) {
            var graceNoteIndex = elementIndex - 1;
            var graceNote = line.getElement(graceNoteIndex);

            // setElement carried the old host's lyrics onto the replacement, which is not a
            // pitched note and has no business holding a melisma carrier. Strip the glissando
            // first so the sync sees an unpaired grace and converges to teardown, removing the
            // carrier outright instead of leaving an empty lyric behind on the replacement.
            // No hand-back here: the host the syllable would return to no longer exists.
            line.modifyElement(graceNoteIndex, ElementField.SLIDE, graceNote::removeSlide);
            line.syncGraceHostMelisma(graceNoteIndex);

            // Any melisma reaching past this pair still has to be unwound before the grace
            // note leaves the line, and the removal must be tracked for undo.
            line.adjustExtendsForDeletion(graceNoteIndex);
            line.removeElement(graceNoteIndex);
            elementIndex--;
        }

        EditModeManager.previewElementDidChange(line, elementIndex);
    }
}
