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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ElementChange;
import songscribe.dom.Line;
import songscribe.dom.Dottable;
import songscribe.dom.NoteElement;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.engraving.StaffPosition;
import songscribe.layout.AccidentalFitGate;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.ui.FitRefusals;
import songscribe.ui.OptionDialogs;
import songscribe.ui.edit.EditModeManager;
import songscribe.undo.OpNames;

/**
 * Turns a click on the hover preview into an edit: an append, an insert, or a replacement.
 */
final class PreviewElementInserter {

    private PreviewElementInserter() {
    }

    private record PreviewClick(Line line, PreviewElementManager.PreviewHover hover, StaffElement previewElement) {

        int insertionIndex() {
            return hover.insertionIndex();
        }

        boolean appends() {
            return insertionIndex() == line.elementCount();
        }
    }

    /**
     * {@code null} when the preview element owns no click there.
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
     * A grace-note preview is refused: a grace note enters a line only through grace mode, via
     * {@link #acceptForGraceMode}.
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
        // replaced, and a bar line may not take the first place in the song — ignore the click.
        if (overElementX && !line.canReplaceElementAt(insertionIndex, previewElement.getType())) {
            return;
        }

        // Every gap slot trackMouse hides the ghost for is refused here on the same rule —
        // ignore the click.
        if (!overElementX && !line.canInsertRunAt(insertionIndex, List.of(previewElement))) {
            return;
        }

        // Decided once and used both to gate and to act, so the gate never judges a different
        // edit from the one the click then makes.
        var appends = click.appends();
        var replacesExisting = !appends && overElementX;
        var opLabel = OpNames.addLabel(previewElement.getType());

        // Every action runs in a modification bracket so the line.add/replace calls inside
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
     * Nothing is inserted here; the caller lands the result.
     *
     * @return {@code null} when the insertion may not go ahead: the preview owns no click on
     *     {@code lc}, the slot is refused, or {@link #accept} refused it for want of room
     */
    static @Nullable AcceptedInsertion acceptForGraceMode(LineComponent lc) {
        var click = resolveClick(lc);

        if (click == null || !click.line().canInsertElementAt(click.insertionIndex())) {
            return null;
        }

        return accept(lc, click.line(), click.insertionIndex(), click.previewElement());
    }

    /**
     * Mutates nothing on the line.
     *
     * @return {@code null} when the edited line or another line the walk widens has no room
     */
    private static @Nullable AcceptedInsertion accept(
        LineComponent lc, Line line, int index, StaffElement element) {

        if (!walkAndCalculateInsertion(lc, line, element, index)) {
            return null;
        }

        return new AcceptedInsertion(line, index, element);
    }

    /**
     * The element armed is not necessarily the merged one: the {@code REPEAT_LEFT} branch merges
     * at {@code index - 1}. Harmless: the armed element's predecessor is the merged
     * {@code REPEAT_LEFT_RIGHT}, which is not beamable, so a beam request is refused.
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
     * @param existing left untouched
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
            note.pitchAs(previewNote);

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
     * Shows the line-full error when the line does not fit. Mutates no live note; on true,
     * {@code replacement} is left stamped as the walk decided.
     */
    private static boolean replacementFits(LineComponent lc, Line line, int index, StaffElement replacement) {
        var edit = ProjectedEdit.replacing(
            line, StaffElementRun.ElementRange.single(index), List.of(replacement), line.getRunningKey());
        var restamps = edit.walk();

        // The auto-maintained terminal barline is not measured: layout pins it flush-right, and
        // its column is built from the live element.
        var effectiveProjection = edit.elements().subList(0, line.effectiveElementCount());
        var lyricRenderMetrics = lc.getLyricRenderMetrics();

        var verdict = AccidentalFitGate.judge(
            line, restamps, List.of(replacement), lyricRenderMetrics,
            () -> effectiveProjection.isEmpty()
                || InsertionSpacingCalculator.fitsInPlace(line, effectiveProjection, lyricRenderMetrics));

        return FitRefusals.accepts(verdict, null, () -> showLineFull(replacement));
    }

    private static boolean isDirectClickOnTerminal(Line line, int xIndex) {
        return line.isTerminalAt(xIndex);
    }

    /**
     * Runs <em>before</em> any mutation, so a rejected insert leaves the line exactly as it was.
     */
    private static boolean insertionFits(
        LineComponent lc, Line line, StaffElement element, int index, LayoutResult layout
    ) {
        var insertion = InsertionSpacingCalculator.calculateInsertion(
            line, element, index, layout, lc.getLyricRenderMetrics());

        return insertion.fitsWithinLine(line.getSong().getLineWidthSs().value());
    }

    private static void showLineFull(StaffElement element) {
        OptionDialogs.showErrorMessage(
            null,
            Strings.ALERT_TITLE_INSERT_ERROR,
            Strings.ERROR_LINE_FULL_ELEMENT,
            element.getType().kind().label()
        );
    }

    /**
     * A refused insertion has mutated nothing. An acceptance leaves the preview element stamped
     * as the walk decided; the song's own walk stamps the live notes once the bracket closes.
     */
    private static boolean walkAndCalculateInsertion(
        LineComponent lc, Line line, StaffElement previewElement, int index) {

        var restamps = ProjectedEdit.inserting(line, index, List.of(previewElement), line.getRunningKey()).walk();

        // The insertion's own check runs inside the fit gate, with the walk's accidentals stamped
        // so the projection measures the right widths.
        var verdict = AccidentalFitGate.judge(
            line, restamps, List.of(previewElement), lc.getLyricRenderMetrics(),
            () -> insertionFits(lc, line, previewElement, index, lc.requireLayoutResult()));

        return FitRefusals.accepts(verdict, null, () -> showLineFull(previewElement));
    }

    /**
     * A breath mark following a non-duration replacement is deleted through {@code deleteRange},
     * so the deletion's glissando and syllable repairs apply to it.
     *
     * @param replacement built by {@link #replacementFor} and already through the fit gate,
     *     stamped as the fit gate's walk decided
     */
    private static void modifyExistingElement(
        LineComponent lc, int elementIndex, StaffElement replacement, Line line) {

        if (mergedRepeatInstead(line, elementIndex)) {
            return;
        }

        var previewType = replacement.getType();

        // line.replace removes, and reports, each span the replacement invalidates, by that
        // span's own outcomeFor; it also marks the song modified.
        line.replace(ElementChange.Replacement.single(line, elementIndex, replacement));

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
        // a replacement preserves the element index.
        if (line.isPairedGraceNoteTarget(elementIndex)
                && !previewType.isPitchedNote()) {
            // line.replace carried the old host's lyrics onto the replacement, which is not a
            // pitched note and has no business holding a melisma carrier. The grace note's
            // syllable goes with it, and the deletion takes the carrier its melisma left on the
            // replacement along.
            line.deleteRange(line.effectiveRange(StaffElementRun.ElementRange.single(elementIndex - 1)));
            elementIndex--;
        }

        EditModeManager.previewElementDidChange(line, elementIndex);
    }
}
