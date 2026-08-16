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
package songscribe.ui.dialog;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.error.RuntimeError;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.KeyEditFitCalculator;
import songscribe.ui.component.MainFrame;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.util.UIUtils;

/**
 * Everything {@link KeyChangeDialog} is not allowed to know: which line the notator pointed at,
 * what key is in effect there, whether the change will still fit, and which of the two commit
 * routes writes it.
 *
 * <p><strong>Three gestures in, one dialog out.</strong> A key change reaches the notator four
 * ways — the line header, the cautionary at the end of the line before it, an existing mid-line
 * key signature, and the insertion-point action — and the dialog is the same window every time,
 * showing one {@link Key} and answering with another. What differs is entirely on this side, so
 * each gesture gets its own entry point and hands over exactly what it already holds: a line, a
 * key signature it hit-tested, or an index the insertion predicate accepted. The header and the
 * cautionary share {@link #editLineKey} because they are the same edit — the cautionary depicts
 * the next line's key, so its caller passes that line.
 *
 * <p><strong>No caller states the binding twice.</strong> Whether the bound index carries a key
 * signature already is a fact only the caller has, and asking the line to recover it would put a
 * lookup in the way of an answer that arrived with the gesture. This is also what frees
 * {@link Line#keyAt} from its inclusive bound: the one binding that needs the key of a signature
 * sitting <em>on</em> the bound index reads it off the element.
 *
 * <p><strong>Two commit routes, not three.</strong> A line's own key is changed in place; a
 * mid-line key signature is <em>inserted</em>. Both mid-line bindings take the insert, which is
 * why editing an existing signature adds a second one in front of it rather than changing it —
 * a known defect this class does not fix. See {@link #insertKeyChange} and
 * {@code plans/design-pass/keys.md} group C item 6.
 *
 * <p><strong>An edit that cannot be drawn is refused before it is written.</strong> A key change
 * claims horizontal space on every line it re-keys, not only the one it is made on, and
 * {@link #validate} measures all of them. The refusal reaches the notator as a
 * {@link ValidationFailure}, so the dialog stays open on the choice that was refused and a
 * narrower signature can be picked without starting over. It runs before anything asks about
 * accidental restatements, so a change that is going to be refused never raises a prompt about
 * accidentals it will never apply.
 */
public final class KeyChangeDialogController extends DialogController<Key, Key> {

    private static final Logger LOG = LoggerFactory.getLogger(KeyChangeDialogController.class);

    /**
     * The {@link #elementIndex} a {@link Binding#LINE_KEY} controller carries: no element at all.
     *
     * <p>Named because a bare 0 reads as "the first element" when it means the opposite.
     * {@link KeyChangeElement}'s position invariant forbids a key signature at index 0, which is
     * what leaves the value free to carry this meaning.
     */
    private static final int LINE_OWN_KEY_INDEX = 0;

    /**
     * What the dialog is bound to, which is the whole of what varies between the gestures.
     *
     * <p>The two mid-line constants differ only in where the key already in effect is read from.
     * They commit identically — see {@link #insertKeyChange}.
     */
    private enum Binding {

        /** The line's own key: what the header draws, and what a cautionary warns of. */
        LINE_KEY,

        /** A key signature already standing at the bound index. */
        EXISTING_SIGNATURE,

        /** A position inside the line with no key signature on it. */
        NEW_POSITION
    }

    private final Line line;
    private final int elementIndex;
    private final Binding binding;

    private KeyChangeDialogController(MainFrame mainFrame, Line line, int elementIndex, Binding binding) {
        super(mainFrame);
        this.line = line;
        this.elementIndex = elementIndex;
        this.binding = binding;
    }

    /**
     * Opens the dialog on {@code line}'s own key — the key its header establishes.
     *
     * <p>Serves both gestures that edit a line's own key: a double-click on the header, and one on
     * the cautionary at the end of the line <em>before</em> it, which depicts this line's key.
     * That the two look different on screen is a rendering matter; the edit is the same one, so
     * the caller resolves which line it is about and this makes no distinction.
     *
     * @param mainFrame the window the dialog parents itself to
     * @param line the line whose own key is being established or changed
     */
    public static void editLineKey(MainFrame mainFrame, Line line) {
        open(mainFrame, new KeyChangeDialogController(mainFrame, line, LINE_OWN_KEY_INDEX, Binding.LINE_KEY));
    }

    /**
     * Opens the dialog on the key {@code signature} establishes.
     *
     * <p><strong>OK inserts a second key signature in front of this one rather than changing
     * it</strong> — the defect described on {@link #insertKeyChange}. This entry point exists
     * as the gesture's own route regardless, because it is the one that will carry the fix.
     *
     * <p>Opens nothing when {@code signature} is not an element of {@code line}, beeping instead:
     * the notator double-clicked a key signature and the program failed to find it, which is worth
     * a log entry, but there is nothing wrong with the document and no reason to take the
     * application down over one gesture that did not land.
     *
     * @param mainFrame the window the dialog parents itself to
     * @param line the line {@code signature} stands on
     * @param signature the key signature the notator double-clicked, which must be an element of
     *                  {@code line}
     */
    public static void editKeyChange(MainFrame mainFrame, Line line, KeyChangeElement signature) {
        var signatureIndex = line.getElementIndex(signature);

        if (signatureIndex < 0) {
            LOG.error("key signature to edit is not an element of the line it was hit-tested on");
            UIUtils.beep();
            return;
        }

        open(mainFrame, new KeyChangeDialogController(
            mainFrame, line, signatureIndex, Binding.EXISTING_SIGNATURE));
    }

    /**
     * Opens the dialog on the key running at {@code insertionIndex}, ready to write a new key
     * signature there.
     *
     * <p>The position has already been accepted by {@code KeyChangeAction.acceptsInsertionIndex},
     * which is where every rule about where a key signature may go lives — including that it never
     * lands on or beside one that is already there. This entry point is therefore for a position
     * with no key signature on it; {@link #editKeyChange} is for one that has.
     *
     * @param mainFrame the window the dialog parents itself to
     * @param line the line the key signature will be written into
     * @param insertionIndex the index it will land at, an index the insertion predicate accepted
     */
    public static void addKeyChange(MainFrame mainFrame, Line line, int insertionIndex) {
        open(mainFrame, new KeyChangeDialogController(
            mainFrame, line, insertionIndex, Binding.NEW_POSITION));
    }

    private static void open(MainFrame mainFrame, KeyChangeDialogController controller) {
        new KeyChangeDialog(mainFrame, controller.ops()).setVisible(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The key already in effect where this change is bound, read from whichever of the three
     * places the binding names. It is what the combo opens on and the one choice OK refuses, so
     * the dialog commits a change or nothing.
     *
     * @return the key in effect at the bound position; never null, because every position in every
     *         line is in some key
     */
    @Override
    protected Key read() {
        return switch (binding) {
            case LINE_KEY -> line.getRunningKey();

            // Off the element, not out of Line.keyAt: the notator pointed at this signature, so
            // its own key is the answer without a query whose bound has to be argued about.
            case EXISTING_SIGNATURE -> boundSignature().getKey();

            case NEW_POSITION -> line.keyAt(elementIndex);
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>Refuses a key change that will not fit, and takes no other view of the notator's choice.
     *
     * <p>Which measurement runs follows the binding. A line's own key is measured with
     * {@link KeyEditFitCalculator#lineKeyChangeFits}, which walks the whole inheritance chain the
     * change re-keys and the cautionary it creates on the line before it. A mid-line key signature
     * is measured with {@link KeyEditFitCalculator#midLineKeyChangeFits}, which additionally holds the
     * column the signature occupies and the barline {@link #insertKeyChange} inserts alongside
     * it. Neither is a partial query and neither half is available on its own.
     *
     * <p>Reading the two refusals apart matters: they name different lines. See
     * {@link #lineKeyRefusal()}.
     *
     * @param values the key the notator chose
     * @return {@link ValidationResult#valid()} when the change fits every line it touches,
     *         otherwise the one refusal saying which measurement failed
     */
    @Override
    protected ValidationResult validate(Key values) {
        var lyricRenderMetrics = requireScoreView().getLyricRenderMetrics();

        if (binding == Binding.LINE_KEY) {
            return refusalUnless(
                KeyEditFitCalculator.lineKeyChangeFits(line, values, lyricRenderMetrics),
                lineKeyRefusal());
        }

        return refusalUnless(
            KeyEditFitCalculator.midLineKeyChangeFits(line, elementIndex, values, lyricRenderMetrics),
            keySignatureRefusal());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Both routes reconcile the accidentals the key move affects and raise at most one
     * restatement prompt before opening their bracket, because a key change moves pitches and owes
     * the same protection an inserted barline owes. Cancelling at that prompt abandons the change:
     * nothing is mutated, no undo step exists, and the dialog closes all the same — a declined
     * confirm is a decision about the change, not about whether the notator is still choosing one.
     *
     * @param values the key the notator chose, already known to fit
     */
    @Override
    protected void commit(Key values) {
        if (binding == Binding.LINE_KEY) {
            changeLineKey(values);
        } else {
            insertKeyChange(values);
        }
    }

    /**
     * Establishes {@code key} at the start of the bound line, reconciling the accidentals the
     * change moves.
     *
     * <p>A key change moves pitches, so it owes the same protection an inserted barline owes: every
     * note whose sounding pitch the change would move is given an explicit accidental, and notation
     * the change makes redundant is cleared. Its reach is the inheritance chain — from this line
     * forward to the first line that establishes a key of its own — which is why this is the one
     * edit in the program whose reconciliation spans more than one line. See
     * {@code docs/key-signatures.md}.
     *
     * <p>The restatement prompt, when there is one, covers the whole reach in a single dialog and
     * is raised <b>before</b> the modification bracket opens, as every other confirm is. Cancelling
     * it leaves nothing mutated and no undo step.
     *
     * <p>The undo step is named for what the line was doing before: <em>Add</em> where it inherited
     * its key until now, <em>Change</em> where it already had one of its own. {@link Line#setKey}
     * may still normalize the chosen key back to inheritance, which is a fact about storage rather
     * than about what the notator did.
     *
     * <p><b>The fit check runs first</b>, in {@link #validate}. A change that will be refused for
     * not fitting must not first ask the user about accidentals it will never apply.
     *
     * @param key the key to establish at the start of the bound line
     */
    private void changeLineKey(Key key) {
        var reach = AccidentalReconciliation.lineKeyChangeReach(line, key);

        // Pure and pre-mutation, so the removals it would make can be read off it and asked about
        // before anything is written.
        var reconciled = AccidentalReconciliation.reconcileModification(
            reach, AccidentalReconciliation.RestatementRemoval.NONE);

        var decision = AccidentalRestatements.confirm(
            getMainFrame(), AccidentalRestatements.accidentalsClearedBy(reconciled));

        if (decision.isCancelled()) {
            return;
        }

        // Reconciled a second time only when the answer moves the result: accepted restatements
        // are cleared by the same walk everything else travels.
        var accidentalChanges = decision.answer() == AccidentalRestatements.Answer.YES
            ? AccidentalReconciliation.reconcileModification(reach, decision.removal())
            : reconciled;

        var reconciledLines = reach.stream()
            .map(AccidentalReconciliation.ModifiedLine::line)
            .toList();

        withModification(lineKeyLabel(), () -> {
            for (var reconciledLine : accidentalChanges) {
                AccidentalMaterializer.commit(reconciledLine.line(), reconciledLine.changes());
            }

            // The accepted restatements sitting past the reach's end, which no line of the reach
            // reconciles.
            AccidentalRestatements.commitOtherLines(decision, reconciledLines);
            line.setKey(key);
        });
    }

    /**
     * Writes a key change into the middle of the bound line at the bound index, adding ahead of it
     * the {@link ElementType#SINGLE_BARLINE} the chosen position needs when it does not already
     * follow a barline or repeat.
     *
     * <p><b>It inserts. It does not edit.</b> Both mid-line bindings arrive here, so a
     * {@link Binding#EXISTING_SIGNATURE} commit adds a second key signature immediately in front of
     * the one the notator double-clicked instead of changing it — and since the existing signature
     * still has the last word, the music from there on stays in the old key. No stray barline
     * appears, because a position that already follows a key signature already follows a barline.
     * Nothing in the program can change an existing mid-line key signature's key; the fix needs a
     * third commit route, costed in {@code plans/design-pass/keys.md} group C item 6. This contract
     * is what guards that defect from being mistaken for the intended behaviour.
     *
     * <p><b>The barline is what keeps {@link KeyChangeElement}'s position invariant true
     * without restricting where the user may click.</b> A position that already follows a barline
     * or a repeat takes the key signature alone; every other position takes two elements, barline
     * first. Either way they enter inside <b>one</b> modification bracket, so a single undo takes
     * back the whole edit rather than leaving the barline behind — see {@code docs/mutations.md}.
     *
     * <p>Positions are measured against the line as it stands, before the bracket opens: an
     * insertion moves nothing ahead of itself, so the key the change cancels and the positions of
     * its predecessors are already settled.
     *
     * <p><b>The fit check runs first</b>, in {@link #validate}, exactly as it does for
     * {@link #changeLineKey}: a change that will be refused for want of room must not first be
     * written and then taken back.
     *
     * <p>An inserted key signature moves pitches from its index forward, so it owes the same
     * reconciliation a change to the line's own key owes, and raises the same single restatement
     * prompt; cancelling it leaves nothing mutated and no undo step. Its reach differs in shape at
     * the head only: the host line is reconciled as an <em>insertion</em>, so the projection carries
     * the new element and the notes after it resolve against it, while the lines that inherit are
     * reached exactly as they are for a line-key change. See {@code docs/key-signatures.md}.
     *
     * @param key the key taking effect from the bound position on
     * @throws IndexOutOfBoundsException if the bound index is below
     *                                   {@link Line#FIRST_LEGAL_KEY_CHANGE_INDEX} or above
     *                                   {@link Line#effectiveElementCount()} — a key signature is
     *                                   never the first element on a line, and never lands past
     *                                   its end
     */
    private void insertKeyChange(Key key) {
        if (elementIndex < Line.FIRST_LEGAL_KEY_CHANGE_INDEX
            || elementIndex > line.effectiveElementCount()) {

            throw new IndexOutOfBoundsException(
                "key signature insertion index " + elementIndex + " out of bounds ["
                    + Line.FIRST_LEGAL_KEY_CHANGE_INDEX + ", "
                    + line.effectiveElementCount() + ']');
        }

        var inserted = new ArrayList<StaffElement>();

        if (KeyChangeElement.needsBarlineBefore(line, elementIndex)) {
            inserted.add(ElementType.SINGLE_BARLINE.newInstance());
        }

        inserted.add(new KeyChangeElement(key));

        // The head: the host line reconciled as an insertion, so the projection holds the new key
        // signature and every note after it resolves against the key it establishes. Empty prior
        // accidentals because these elements have no source context — nothing is being pasted.
        var region = new AccidentalReconciliation.InsertionRegion(
            line, elementIndex, null, inserted, List.of(), List.of());
        var hostChanges = AccidentalReconciliation.reconcile(
            region, AccidentalReconciliation.RestatementRemoval.NONE);

        // The tail: an existing key signature already standing after the insertion point still has
        // the last word on the key this line leaves off in, so the inserted key reaches the next
        // line only when none does.
        var tail = AccidentalReconciliation.linesInheriting(
            line, line.keyAtEndOfLineUnder(elementIndex, key));
        var reconciled = new ArrayList<AccidentalReconciliation.ReconciledLine>();

        reconciled.add(new AccidentalReconciliation.ReconciledLine(line, hostChanges));
        reconciled.addAll(AccidentalReconciliation.reconcileModification(
            tail, AccidentalReconciliation.RestatementRemoval.NONE));

        // One prompt for the whole edit, head and tail together, before any bracket opens.
        var decision = AccidentalRestatements.confirm(
            getMainFrame(), AccidentalRestatements.accidentalsClearedBy(reconciled));

        if (decision.isCancelled()) {
            return;
        }

        var accidentalChanges = decision.answer() == AccidentalRestatements.Answer.YES
            ? withRemovalApplied(region, tail, decision.removal())
            : reconciled;
        var reconciledLines = reconciled.stream()
            .map(AccidentalReconciliation.ReconciledLine::line)
            .toList();

        var spacing = InsertionSpacingCalculator.calculateFragmentInsertion(
            line, inserted, elementIndex, null, null, requireScoreView().getLyricRenderMetrics());
        var insertedCount = inserted.size();

        withModification(Strings.get(Strings.ACTION_EDIT_OP_ADD_KEY), () -> {
            // Applied before the elements land, as every reconciliation is: the changes name live
            // notes and their pre-insertion positions.
            for (var reconciledLine : accidentalChanges) {
                AccidentalMaterializer.commit(reconciledLine.line(), reconciledLine.changes());
            }

            AccidentalRestatements.commitOtherLines(decision, reconciledLines);

            // The lyric seams on either side of the insertion, repaired as they are for any
            // other bare element: the half that reads pre-insertion indices runs first, the
            // half that reads the successor runs once every element is in.
            line.repairNeighborsBeforeInsertion(elementIndex);

            for (var i = 0; i < insertedCount; i++) {
                var element = inserted.get(i);
                element.setXOffsetPx(ScaleContext.ssToRoundedPx(spacing.cloneXPositionsSs().get(i)));
                line.addElement(elementIndex + i, element);
            }

            line.adjustSyllablesForSuccessorAfterInsertion(elementIndex + insertedCount - 1);

            var tailShiftPx = ScaleContext.ssToRoundedPx(spacing.shiftForSubsequentElementsSs());

            for (var i = elementIndex + insertedCount; i < line.effectiveElementCount(); i++) {
                var element = line.getElement(i);
                element.setXOffsetPx(element.getXOffsetPx() + tailShiftPx);
            }
        });
    }

    /**
     * Re-runs an inserted key signature's reconciliation with the restatements the notator accepted
     * folded in, head and tail alike.
     *
     * <p>Only reached on {@link AccidentalRestatements.Answer#YES}: the removal is what changes the
     * result, so a No answer keeps the reconciliation already computed rather than repeating it.
     *
     * @param region the host line's insertion, exactly as first reconciled
     * @param tail the lines inheriting from the host, exactly as first reached
     * @param removal the accepted restatements and the staff positions they suppress
     * @return one entry per line, host first, in the same order as the original reconciliation
     */
    private static List<AccidentalReconciliation.ReconciledLine> withRemovalApplied(
        AccidentalReconciliation.InsertionRegion region,
        List<AccidentalReconciliation.ModifiedLine> tail,
        AccidentalReconciliation.RestatementRemoval removal) {

        var reconciled = new ArrayList<AccidentalReconciliation.ReconciledLine>();

        reconciled.add(new AccidentalReconciliation.ReconciledLine(
            region.line(), AccidentalReconciliation.reconcile(region, removal)));
        reconciled.addAll(AccidentalReconciliation.reconcileModification(tail, removal));

        return reconciled;
    }

    /**
     * The key signature this controller is bound to.
     *
     * <p>The guard states the binding's precondition rather than defending against it:
     * {@link #editKeyChange} is reached only with an element the caller hit-tested on this line,
     * so anything else there means the line was mutated under a dialog that is modal over it.
     *
     * @return the key signature standing at the bound index
     */
    private KeyChangeElement boundSignature() {
        if (line.getElement(elementIndex) instanceof KeyChangeElement signature) {
            return signature;
        }

        throw RuntimeError.exit("no key signature at the index this dialog was bound to");
    }

    /**
     * @return the undo-step name for a change to the bound line's own key, resolved: <em>Add</em>
     *         where the line inherited its key until now, <em>Change</em> where it had one of its
     *         own. Read before the mutation, so it describes what the notator did rather than what
     *         the line ended up storing
     */
    private String lineKeyLabel() {
        return Strings.get(line.getKey() != null
            ? Strings.ACTION_EDIT_OP_CHANGE_KEY
            : Strings.ACTION_EDIT_OP_ADD_KEY);
    }

    /**
     * The refusal for a line's own key.
     *
     * <p><b>Deliberately does not say "this line".</b> A line-key change is refused for a line the
     * notator did not click: the cautionary it creates at the end of the previous one, or the
     * header of any line down the inheritance chain that it re-keys. Naming the clicked line would
     * send them to look at the wrong place.
     *
     * @return the message a refused line-key change carries
     */
    private static LocalizedMessage lineKeyRefusal() {
        return new LocalizedMessage(Strings.ERROR_LINE_FULL_KEY_CHANGE);
    }

    /**
     * The refusal for a mid-line key signature, which does occupy a column on the line the notator
     * is looking at — so its message says "this line" and names what did not fit.
     *
     * <p>The category name is carried as a nested {@link LocalizedMessage} rather than as text:
     * deciding what is wrong is this side's job and wording it is the presenter's.
     *
     * @return the message a refused mid-line key signature carries
     */
    private static LocalizedMessage keySignatureRefusal() {
        return new LocalizedMessage(
            Strings.ERROR_LINE_FULL_ELEMENT,
            List.of(new LocalizedMessage(ElementType.KEY_CHANGE.categoryNameKey())));
    }

    /**
     * @param fits what the fit measurement answered
     * @param message the refusal to carry when it did not
     * @return a valid result when {@code fits}, otherwise that one refusal under the shared
     *         line-too-full title, which both routes report under because both are refusals about
     *         room on a line
     */
    private static ValidationResult refusalUnless(boolean fits, LocalizedMessage message) {
        return fits
            ? ValidationResult.valid()
            : ValidationResult.invalid(
                new ValidationFailure(Strings.ALERT_TITLE_LINE_TOO_FULL, message));
    }
}
