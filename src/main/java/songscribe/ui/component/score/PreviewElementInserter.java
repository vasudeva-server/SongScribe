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
import songscribe.dom.ElementChange;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.Dottable;
import songscribe.dom.NoteElement;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.engraving.StaffPosition;
import songscribe.layout.AccidentalFitGate;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.message.mutation.ElementField;
import songscribe.ui.EndingConfirms;
import songscribe.ui.OptionDialogs;
import songscribe.ui.edit.EditModeManager;
import songscribe.undo.OpNames;

/**
 * Turns a click on the hover preview into an edit: an append, an insert, or a replacement.
 * <p>
 * The tracking state the click resolves against — which line, which insertion index, whether the
 * pointer is over an existing element head — belongs to {@link PreviewElementManager} and is read
 * from it here. What lives in this class is everything that happens once the click is known to be
 * legal: the chain walk over the line as the click will leave it, the spring-solver fit check
 * with the walk's accidentals in place, the confirmations, and the mutations themselves.
 */
final class PreviewElementInserter {

    private PreviewElementInserter() {
    }

    /**
     * A click the preview element owns, resolved against the line it landed on: where the
     * hover put it, and what the preview would place there.
     */
    private record PreviewClick(Line line, PreviewElementManager.PreviewHover hover, StaffElement previewElement) {

        int insertionIndex() {
            return hover.insertionIndex();
        }

        boolean appends() {
            return insertionIndex() == line.elementCount();
        }
    }

    /**
     * Resolves a click on {@code lc} into the click the preview element owns, or {@code null}
     * when it owns none there: the preview is not the click's target, nothing is hovered, or the
     * hovered position lies past the auto-maintained terminal.
     */
    private static @Nullable PreviewClick resolveClick(LineComponent lc) {
        if (!PreviewElementManager.isPreviewClickTarget(lc)) {
            return null;
        }

        var line = lc.getLine();
        var hover = PreviewElementManager.hover();
        var previewElement = EditModeManager.getPreviewElement();

        if (line == null || hover == null || previewElement == null) {
            return null;
        }

        // Belt-and-braces: block clicks that would try to insert past the auto-maintained
        // terminal. trackMouse already clears the preview at these positions.
        if (PreviewElementManager.isPositionBlockedByTerminal(
                line.getSong(), line, hover.insertionIndex(), hover.overElementX())) {
            return null;
        }

        return new PreviewClick(line, hover, previewElement);
    }

    /**
     * Handles a note-entry click on the preview element: an append, an insertion, or a
     * replacement of the element under the pointer. Called from {@code LineComponent.clicked()}.
     * <p>
     * A grace-note preview is refused. A grace note enters a line only through grace mode,
     * which gates it through {@link #acceptForGraceMode} and pairs it with its host.
     */
    static void handleClick(LineComponent lc) {
        var click = resolveClick(lc);

        if (click == null) {
            return;
        }

        var line = click.line();
        var hover = click.hover();
        var previewElement = click.previewElement();
        var insertionIndex = click.insertionIndex();
        var overElementX = hover.overElementX();
        var song = line.getSong();

        if (previewElement.getType().isGraceNote()) {
            return;
        }

        // Route a direct click on the terminal to replaceTerminal when the active preview
        // element can legally replace it. This bypasses the normal insertion path entirely.
        if (overElementX && isDirectClickOnTerminal(line, insertionIndex)) {
            var previewType = previewElement.getType();

            if (song.canReplaceTerminal(previewType)) {
                song.replaceTerminal(previewType);
                return;
            }
        }

        if (PreviewElementManager.isBreathMarkInsertionBlocked(
                previewElement, insertionIndex, line, overElementX)) {
            return;
        }

        // A grace note, a key change and the barline a key change sits behind may never be
        // replaced — ignore the click.
        if (overElementX && !line.canReplaceElementAt(insertionIndex)) {
            return;
        }

        // Every gap slot trackMouse hides the ghost for is refused here on the same rule —
        // ignore the click.
        if (!overElementX && !line.canInsertRunAt(insertionIndex, List.of(previewElement))) {
            return;
        }

        // Decided once and used both to ask and to act, so the question can never be asked about a
        // click that then does something else.
        var appends = click.appends();
        var replacesExisting = !appends && overElementX;
        var opLabel = OpNames.addLabel(previewElement.getType());

        // Every action runs in a modification bracket so the line.add/setElement calls inside
        // accumulate mutations and fire a SongDidChangeNotification, which the
        // ScoreViewController uses to invalidate the line's cached layout.
        if (replacesExisting) {
            // The replacement is built and gated before the bracket opens, so a refusal leaves
            // nothing recorded and no undo step.
            var existing = line.getElement(insertionIndex);
            var replacement = replacementFor(previewElement, existing, hover.staffPosition());

            if (!replacementFits(lc, line, insertionIndex, replacement)) {
                return;
            }

            song.withModification(opLabel, () -> modifyExistingElement(lc, insertionIndex, replacement, line));
            return;
        }

        song.withModification(opLabel, () -> {
            if (mergedRepeatInstead(line, insertionIndex)) {
                return;
            }

            var accepted = accept(lc, line, insertionIndex, previewElement);

            if (accepted != null) {
                accepted.land();
            }
        });
    }

    /**
     * Gates grace mode's insertion of the preview element at the hovered slot: the grace note on
     * the first click, and its host on the click that completes the pair. Nothing is inserted
     * here; the caller lands the result.
     * <p>
     * The element always goes in front of whatever the pointer is over, and never replaces it.
     * The grace note and its host land as a pair that ends in a note, so the only slots refused
     * are the two {@link Line#canInsertElementAt} refuses, inside an existing pair.
     *
     * @param lc the line the click landed on
     * @return the accepted insertion, or {@code null} when the insertion may not go ahead: the
     *     preview owns no click on {@code lc}, the slot is refused, or the gate declined it for
     *     want of room or at a confirmation, having told the user which
     */
    static @Nullable AcceptedInsertion acceptForGraceMode(LineComponent lc) {
        var click = resolveClick(lc);

        if (click == null || !click.line().canInsertElementAt(click.insertionIndex())) {
            return null;
        }

        return accept(lc, click.line(), click.insertionIndex(), click.previewElement());
    }

    /**
     * Runs the insertion gate for {@code element} at {@code index}: the chain walk, the fit check
     * and, unless the element is appended, the ending confirm. Mutates nothing on the line.
     *
     * @return the accepted insertion, or {@code null} when the line has no room or the user
     *     declined the confirm
     */
    private static @Nullable AcceptedInsertion accept(
        LineComponent lc, Line line, int index, StaffElement element) {

        // Appending needs no further confirmation: nothing follows the new element, so it can
        // invalidate no ending. Elsewhere the ending confirm runs inside the gate: declining it
        // must leave the line exactly as it was, on the same terms as a refusal for want of room.
        BooleanSupplier confirmed = index == line.elementCount()
            ? () -> true
            : () -> !new ElementChange.Insertion(line, index, element).removesConfirmableSpan()
                || EndingConfirms.confirmInvalidation(lc);

        if (walkAndCalculateInsertion(lc, line, element, index, confirmed) == null) {
            return null;
        }

        return new AcceptedInsertion(line, index, element);
    }

    /**
     * Whether the click merged a {@code REPEAT_LEFT} with a {@code REPEAT_RIGHT} at
     * {@code index} instead of placing the preview element, re-arming the preview when it did.
     * <p>
     * The merge is a {@code setElement} that {@code elementWasModified} performs itself, so a
     * commit always follows. The element armed is not necessarily the merged one:
     * {@code elementWasModified}'s {@code REPEAT_LEFT} branch merges at {@code index - 1},
     * leaving the arm on the untouched element after it. Harmless either way: that element's
     * predecessor is the merged {@code REPEAT_LEFT_RIGHT}, which is not beamable, so a beam
     * request against it is refused. An append arms the last element, the merged one.
     */
    private static boolean mergedRepeatInstead(Line line, int index) {
        if (!EditModeManager.elementWasModified(line, index)) {
            return false;
        }

        var armedIndex = index == line.elementCount() ? index - 1 : index;
        EditModeManager.previewElementDidChange(line, armedIndex);
        return true;
    }

    /**
     * Returns the element a click on {@code existing} with {@code previewElement} active puts in
     * its place: {@code existing} converted to the preview's type, so every decoration the
     * conversion keeps carries over, with the note-entry attributes the notator sets on the
     * preview via the toolbar and menu written over it.
     *
     * @param previewElement     The active preview element
     * @param existing           The element being replaced, left untouched
     * @param hoverStaffPosition The staff position the hover resolved the mouse Y to
     * @return A detached element, in no line, whose accidental state is the preview's
     */
    private static StaffElement replacementFor(
        StaffElement previewElement, StaffElement existing, StaffPosition hoverStaffPosition) {

        var replacement = StaffElement.convertedFrom(previewElement.getType(), existing);

        // The replacement and the preview are of the same type, so they are the same leaf: the
        // note-entry attributes the preview carries are exactly the ones the replacement takes.
        if (replacement instanceof Dottable dottable && previewElement instanceof Dottable previewDottable) {
            dottable.setDots(previewDottable.dots());
        }

        if (replacement instanceof NoteElement note && previewElement instanceof NoteElement previewNote) {
            // The preview note is Chosen or Adopting; the walk in the fit gate decides what the
            // replacement sounds and stamps it before the replacement lands.
            note.setAccidentalState(previewNote.accidentalState());

            // Articulations are a note-entry decoration the user sets on the preview element via
            // the toolbar/menu, exactly like the accidental above. Carry them over from the
            // preview, overriding any inherited from the existing element. Other attachments
            // (dynamics, annotations, trills, fermata) are not preview-settable, so they remain
            // copied from the existing element by the conversion.
            note.copyArticulationsFrom(previewNote);

            // A note takes the mouse Y position; anything else has no staff position to take.
            note.setStaffPosition(hoverStaffPosition);
            note.setStem(previewNote.stem());
        }

        return replacement;
    }

    /**
     * Returns whether {@code line} still fits once {@code replacement} stands at {@code index} in
     * place of the element there, with every accidental the chain walk stamps for that
     * replacement drawn, showing the line-full error when it does not. Mutates no live note; on a
     * true answer {@code replacement} is left stamped as the walk decided.
     */
    private static boolean replacementFits(LineComponent lc, Line line, int index, StaffElement replacement) {
        var edit = ProjectedEdit.replacing(
            line, StaffElementRun.ElementRange.single(index), List.of(replacement));
        var restamps = edit.walk();

        // The auto-maintained terminal barline is not measured: layout pins it flush-right, and
        // its column is built from the live element.
        var effectiveProjection = edit.elements().subList(0, line.effectiveElementCount());
        var lyricRenderMetrics = lc.getLyricRenderMetrics();

        var fits = AccidentalFitGate.fits(
            line, restamps, List.of(replacement), lyricRenderMetrics,
            () -> effectiveProjection.isEmpty()
                || InsertionSpacingCalculator.fitsInPlace(line, effectiveProjection, lyricRenderMetrics));

        if (!fits) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_ELEMENT,
                replacement.getType().kind().label()
            );
        }

        return fits;
    }

    /**
     * Returns {@code true} when {@code xIndex} points to the auto-maintained terminal
     * element on {@code line}.
     */
    private static boolean isDirectClickOnTerminal(Line line, int xIndex) {
        return line.isTerminalAt(xIndex);
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
     * @param layout  The line's current layout, which the projection measures from
     * @return The insertion result, or null if the line is full
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult calculateInsertionOrShowError(
        LineComponent lc, Line line, StaffElement element, int index, LayoutResult layout
    ) {
        var insertion = InsertionSpacingCalculator.calculateInsertion(
            line, element, index, layout, lc.getLyricRenderMetrics());
        var song = line.getSong();

        if (!insertion.fitsWithinLine(song.getLineWidthSs().value())) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_ELEMENT,
                element.getType().kind().label()
            );
            return null;
        }

        return insertion;
    }

    /**
     * Walks the chains this insertion changes, gates the insertion with the walk's accidentals in
     * place, and returns the insertion result — or null when the insertion is refused, in which
     * case nothing has been mutated.
     * <p>
     * The walk runs over a projection of the line with {@code previewElement} at {@code index}.
     * A preview note arrives {@code Chosen} or {@code Adopting} and is decided by the walk like any
     * note it reaches; a barline, repeat or key change decides nothing of its own but changes what
     * the notes after it are decided against. Either way the fit check measures the accidentals
     * the insertion will draw. A refusal leaves every live note as it was; an acceptance leaves the
     * preview element stamped as the walk decided, and the song's own walk once the bracket closes
     * is what stamps the live notes.
     *
     * @param lc             The LineComponent whose line is being inserted into
     * @param line           The line to insert into
     * @param previewElement The element being inserted
     * @param index          The insertion index
     * @param confirmed      Any further confirmation the caller requires, run only once the
     *                       element is known to fit; false means the insertion is refused
     * @return The insertion result, or null when refused
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult walkAndCalculateInsertion(
        LineComponent lc, Line line, StaffElement previewElement, int index, BooleanSupplier confirmed) {

        var restamps = ProjectedEdit.inserting(line, index, List.of(previewElement)).walk();

        // The gate runs inside the fit gate, with the walk's accidentals stamped so the projection
        // measures the right widths, so its result has to escape the lambda through a holder.
        var insertion = new InsertionSpacingCalculator.InsertionResult[1];

        var accepted = AccidentalFitGate.fits(
            line, restamps, List.of(previewElement), lc.getLyricRenderMetrics(), () -> {
                var result = calculateInsertionOrShowError(lc, line, previewElement, index, lc.requireLayoutResult());

                if ((result == null) || !confirmed.getAsBoolean()) {
                    return false;
                }

                insertion[0] = result;
                return true;
            });

        return accepted ? insertion[0] : null;
    }

    /**
     * Replaces an existing element with {@code replacement}, built by {@link #replacementFor}
     * and already through the fit gate. Called when the user clicks on an existing element head
     * with the preview element active. If the replacement is not a duration element and the
     * following element is a breath mark, that breath mark is deleted through
     * {@code deleteRange}, so the deletion's glissando and syllable repairs apply to it.
     *
     * @param lc           The LineComponent
     * @param elementIndex The index of the element to replace
     * @param replacement  The element to put in its place, stamped as the fit gate's walk decided
     * @param line         The line containing the element
     */
    private static void modifyExistingElement(
        LineComponent lc, int elementIndex, StaffElement replacement, Line line) {

        if (mergedRepeatInstead(line, elementIndex)) {
            return;
        }

        var previewType = replacement.getType();

        // Check whether replacing this element would affect a first-second ending,
        // and show the appropriate confirmation dialog if so.
        var endingEffect = new ElementChange.Replacement(line, elementIndex, replacement).endingEffect();

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

        // The beam sweep goes here rather than earlier, next to the element's own preparation:
        // a confirm above can still turn the user away, and a cancelled replacement that had
        // already stripped the beam would leave the bracket holding a removal with nothing to
        // justify it — the user says no and loses the beam anyway. Below the last bail-out, and
        // above setElement so reverse-order undo restores the old element before re-adding what
        // hung off it.
        //
        // Ties and tuplets are not swept here: setElement removes exactly the ones the
        // replacement invalidates, each by its own outcomeFor. Only the beam still needs a hand, because its
        // repair is not a yes-or-no question — the select-mode path trims a beam to its
        // beamable middle rather than dropping it, which a removal hook cannot express.

        // Every beam touching this element — the new element type may not be beamable at all.
        var spans = line.spanRegistry();
        var beam = spans.findBeamAt(elementIndex);

        while (beam != null) {
            beam.removeFrom(line);
            beam = spans.findBeamAt(elementIndex);
        }

        // Replace the element entirely (line.setElement marks the song modified)
        line.setElement(elementIndex, replacement);

        // A breath mark stands only after a duration element, so a replacement by anything else
        // takes the breath mark after it along. The effective range of a lone breath mark widens
        // nothing: it hosts no grace note, sits behind no barline, and neither another breath
        // mark nor a key change can follow it. Inside the caller's bracket, so undo restores the
        // breath mark before the old element, in reverse order. The grace-note cleanup below
        // reads elementIndex and elementIndex - 1 only, which this removal at elementIndex + 1
        // does not move.
        if (!previewType.isDuration() && line.isBreathMarkAt(elementIndex + 1)) {
            line.deleteRange(line.effectiveRange(StaffElementRun.ElementRange.single(elementIndex + 1)));
        }

        // Grace note cleanup: if the preceding element is a paired grace note (grace + connected
        // glissando to the replaced host), and the replacement is not a pitched note, remove the
        // grace note. For pitched note replacements the glissando reattaches automatically since
        // setElement preserves the element index.
        if (line.isHostOfPairedGraceNote(elementIndex)
                && !previewType.isPitchedNote()) {
            var graceNoteIndex = elementIndex - 1;

            // setElement carried the old host's lyrics onto the replacement, which is not a
            // pitched note and has no business holding a melisma carrier. Strip the glissando
            // first so the sync sees an unpaired grace and converges to teardown, removing the
            // carrier outright instead of leaving an empty lyric behind on the replacement.
            // No hand-back here: the host the syllable would return to no longer exists.
            // isHostOfPairedGraceNote answered true, so the element in front is a paired grace
            // note, which is a note.
            if (line.getElement(graceNoteIndex) instanceof NoteElement graceNote) {
                line.modifyElement(graceNoteIndex, ElementField.SLIDE, graceNote::removeSlide);
            }

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
