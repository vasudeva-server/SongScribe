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
import songscribe.dom.KeyChangeSite;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.StaffElement;
import songscribe.layout.AccidentalReconciliation;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.KeyEditFitCalculator;
import songscribe.message.mutation.ElementField;
import songscribe.ui.component.MainFrame;
import songscribe.ui.edit.KeyChangeReconciliation;
import songscribe.util.UIUtils;

/**
 * Everything {@link KeyChangeDialog} is not allowed to know: which line the notator pointed at,
 * what key is in effect there, whether the change will still fit, and which of the three commit
 * routes writes it.
 *
 * <p><strong>Three gestures in, one dialog out.</strong> A key change reaches the notator four
 * ways — the line header, the cautionary at the end of the line before it, an existing mid-line
 * key change, and the insertion-point action — and the dialog is the same window every time,
 * showing one {@link Key} and answering with another. What differs is entirely on this side, so
 * each gesture gets its own entry point and hands over exactly what it already holds: a line, a
 * key change it hit-tested, or an index the insertion predicate accepted. The header and the
 * cautionary share {@link #editLineKey} because they are the same edit — the cautionary depicts
 * the next line's key, so its caller passes that line.
 *
 * <p><strong>What varies between the gestures is a {@link KeyChangeSite}.</strong> Each entry point
 * names the place the change is bound to, and the site answers what key is in effect there and
 * which key change, if any, already stands on it — so the three-way distinction is stated once,
 * in the document model, rather than as a shape this controller keeps for itself. It is also why
 * this controller's own comparison needs no cases: it asks the site.
 *
 * <p><strong>One commit route per binding.</strong> A line's own key and an existing mid-line key
 * change are both changed <em>in place</em>; a key change at a position that has none is
 * <em>inserted</em>. The two mid-line routes are separate because a swap and an insertion are
 * different edits all the way down — different fit measurement, different projection for the
 * accidental reconciliation, and different mutations — and only the insertion can owe a barline.
 *
 * <p><strong>An edit that cannot be drawn is refused before it is written.</strong> A key change
 * claims horizontal space on every line it re-keys, not only the one it is made on, and
 * {@link #validate} measures all of them. The refusal reaches the notator as a
 * {@link ValidationFailure}, so the dialog stays open on the choice that was refused and a
 * narrower signature can be picked without starting over. It runs before anything asks about
 * accidental restatements, so a change that is going to be refused never raises a prompt about
 * accidentals it will never apply.
 */
public final class KeyChangeDialogController extends DocumentDialogController<Key, Key> {

    private static final Logger LOG = LoggerFactory.getLogger(KeyChangeDialogController.class);

    private final KeyChangeSite site;
    private final Line line;
    private final int elementIndex;

    private KeyChangeDialogController(MainFrame mainFrame, KeyChangeSite site) {
        super(mainFrame);
        this.site = site;
        line = site.line();
        elementIndex = site.elementIndex();
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
        open(mainFrame, new KeyChangeDialogController(mainFrame, KeyChangeSite.lineKey(line)));
    }

    /**
     * Opens the dialog on the key {@code keyChange} establishes.
     *
     * <p>OK changes that key change's key in place — see {@link #changeMidLineKey}.
     *
     * <p>Opens nothing when {@code keyChange} is not an element of {@code line}, beeping instead:
     * the notator double-clicked a key change and the program failed to find it, which is worth
     * a log entry, but there is nothing wrong with the document and no reason to take the
     * application down over one gesture that did not land.
     *
     * @param mainFrame the window the dialog parents itself to
     * @param line the line {@code keyChange} stands on
     * @param keyChange the key change the notator double-clicked, which must be an element of
     *                  {@code line}
     */
    public static void editKeyChange(MainFrame mainFrame, Line line, KeyChangeElement keyChange) {
        var keyChangeIndex = line.getElementIndex(keyChange);

        if (keyChangeIndex < 0) {
            LOG.error("key change to edit is not an element of the line it was hit-tested on");
            UIUtils.beep();
            return;
        }

        open(mainFrame, new KeyChangeDialogController(
            mainFrame, KeyChangeSite.existingKeyChange(line, keyChangeIndex)));
    }

    /**
     * Opens the dialog on the key running at {@code insertionIndex}, ready to write a new key
     * change there.
     *
     * <p>The position has already been accepted by {@code KeyChangeAction.acceptsInsertionIndex},
     * which is where every rule about where a key change may go lives — including that it never
     * lands on or beside one that is already there. This entry point is therefore for a position
     * with no key change on it; {@link #editKeyChange} is for one that has.
     *
     * @param mainFrame the window the dialog parents itself to
     * @param line the line the key change will be written into
     * @param insertionIndex the index it will land at, an index the insertion predicate accepted
     */
    public static void addKeyChange(MainFrame mainFrame, Line line, int insertionIndex) {
        open(mainFrame, new KeyChangeDialogController(
            mainFrame, KeyChangeSite.newPosition(line, insertionIndex)));
    }

    private static void open(MainFrame mainFrame, KeyChangeDialogController controller) {
        new KeyChangeDialog(mainFrame, controller.ops()).setVisible(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The key already in effect where this change is bound, which the {@link KeyChangeSite}
     * answers for whichever of the three places it names. It is what the combo opens on and the
     * one choice a commit refuses, so the dialog writes a change or nothing.
     *
     * @return the key in effect at the bound position; never null, because every position in every
     *         line is in some key
     */
    @Override
    protected Key read() {
        return site.keyInEffect();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Whether the chosen key writes anything is a question about the place the change is bound
     * to, so the {@link KeyChangeSite} answers it — including the case where the key chosen is
     * already in effect and the commit still changes the document, which is a line taking a key of
     * its own for the first time.
     */
    @Override
    protected boolean dataWasModified(Key values) {
        return site.wouldChangeAnything(values);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Refuses a key change that will not fit, and takes no other view of the notator's choice.
     *
     * <p>Which measurement runs follows the binding, one per commit route. A line's own key is
     * measured with {@link KeyEditFitCalculator#lineKeyChangeFits}, which walks the whole
     * inheritance chain the change re-keys and the cautionary it creates on the line before it. A
     * <em>new</em> mid-line key change is measured with
     * {@link KeyEditFitCalculator#midLineKeyChangeInsertionFits}, which additionally holds the column the
     * key change occupies and the barline {@link #insertKeyChange} inserts alongside it. An
     * <em>existing</em> one is measured with {@link KeyEditFitCalculator#midLineKeyChangeSwapFits},
     * which replaces that column rather than adding one — measuring a swap as an insertion would
     * refuse it for want of room the line already has. None is a partial query and no half of one
     * is available on its own.
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

        return switch (site.binding()) {
            case LINE_KEY -> refusalUnless(
                KeyEditFitCalculator.lineKeyChangeFits(line, values, lyricRenderMetrics),
                lineKeyRefusal());

            case EXISTING_KEY_CHANGE -> refusalUnless(
                KeyEditFitCalculator.midLineKeyChangeSwapFits(line, elementIndex, values, lyricRenderMetrics),
                midLineKeyRefusal());

            case NEW_POSITION -> refusalUnless(
                KeyEditFitCalculator.midLineKeyChangeInsertionFits(line, elementIndex, values, lyricRenderMetrics),
                midLineKeyRefusal());
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>Every route reconciles the accidentals the key move affects and raises at most one
     * restatement prompt before opening its bracket, because a key change moves pitches and owes
     * the same protection an inserted barline owes. Cancelling at that prompt abandons the change:
     * nothing is mutated, no undo step exists, and the dialog closes all the same — a declined
     * confirm is a decision about the change, not about whether the notator is still choosing one.
     *
     * @param values the key the notator chose, already known to fit
     */
    @Override
    protected void commit(Key values) {
        switch (site.binding()) {
            case LINE_KEY -> changeLineKey(values);
            case EXISTING_KEY_CHANGE -> changeMidLineKey(values);
            case NEW_POSITION -> insertKeyChange(values);
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
     * {@code docs/key-changes.md}.
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
     * <p>A mid-line key change anywhere in that reach that would then restate the key in effect
     * before it is removed with its barline, in the same bracket, so one undo takes back the key
     * and the removals together. The reach carries those ranges, so this route neither looks for
     * them nor decides anything about them.
     *
     * <p><b>The fit check runs first</b>, in {@link #validate}. A change that will be refused for
     * not fitting must not first ask the user about accidentals it will never apply.
     *
     * @param key the key to establish at the start of the bound line
     */
    private void changeLineKey(Key key) {
        var reach = AccidentalReconciliation.lineKeyChangeReach(line, key);

        var confirmed = KeyChangeReconciliation.confirm(getMainFrame(), List.of(), reach);

        if (confirmed.isCancelled()) {
            return;
        }

        withModification(lineKeyLabel(), () -> {
            confirmed.apply();
            line.setKey(key);
        });
    }

    /**
     * Changes the key the key change at the bound index establishes, reconciling the accidentals
     * the change moves.
     *
     * <p><b>It edits in place</b>, except when the chosen key is the one already in effect where
     * the key change stands. The element keeps its identity and its index, so nothing on the line
     * moves and no barline is involved — the one the position invariant puts in front of it is
     * already there. The signature's column is re-solved against the new key on the next layout
     * pass, exactly as a line-key change's header is, so a key that draws more or fewer accidentals
     * needs no position bookkeeping here.
     *
     * <p><b>A key change told to restate the key in front of it is removed instead</b>, together
     * with the barline it sits behind. It would draw nothing and be invisible on screen while still
     * sitting in the document, which the invariant in {@code docs/key-changes.md} forbids — so
     * the removal is what the notator asked for, and is what this reconciles and commits. The same
     * goes for any key change further along the line that the new key strands.
     *
     * <p>The reach is the same one every key-moving edit has: this line from the key change's index
     * forward, then every line inheriting from it, up to the first with a key of its own. The
     * key change's own line is reconciled as a <em>replacement</em> — the projection carries a
     * key change for the new key where the old one stands — because the notes after it have to
     * resolve against the key that will be in effect there, not the one that is. One restatement
     * prompt covers the whole reach, raised before the modification bracket opens; cancelling it
     * leaves nothing mutated and no undo step. See {@code docs/key-changes.md}.
     *
     * <p><b>The fit check runs first</b>, in {@link #validate}, so a change that will be refused for
     * want of room never asks about accidentals it will never apply.
     *
     * @param key the key the bound key change is to establish instead
     */
    private void changeMidLineKey(Key key) {
        var keyChange = site.boundKeyChange();
        var keyBefore = line.keyAt(elementIndex - 1);
        var strandsItself = key == keyBefore;
        var ownRange = line.effectiveRange(elementIndex, elementIndex);

        // Every key change further along this line that the new key leaves restating it. Scanning
        // from past this one is the point: what stands before it is untouched by this edit.
        var strandedAfter = line.redundantKeyChangeRanges(elementIndex + 1, key);

        AccidentalReconciliation.Insertion insertion;

        if (strandsItself) {
            // A removal, described as one: the key change and its barline go, and nothing takes
            // their place.
            insertion = new AccidentalReconciliation.Insertion(
                ownRange.begin(),
                new InsertionSpacingCalculator.DeletedRange(ownRange.begin(), ownRange.end()),
                AccidentalReconciliation.ArrivingElements.NONE);
        } else {
            // A swap, described to the reconciliation as what it is: the old key change removed and
            // one for the new key put in its place. A fresh arrival because the replacement has no
            // source context — nothing is being pasted — and a key change carries no accidental of
            // its own to materialize in any case.
            insertion = new AccidentalReconciliation.Insertion(
                elementIndex,
                new InsertionSpacingCalculator.DeletedRange(elementIndex, elementIndex),
                AccidentalReconciliation.ArrivingElements.fresh(
                    List.of(KeyChangeElement.forMeasurement(key, keyBefore))));
        }

        // A key change already standing after this one still has the last word on the key the
        // line leaves off in, so the new key reaches the next line only when none does. A stranded
        // one among them says the same thing the new key does, so removing it moves nothing.
        var reach = new ArrayList<AccidentalReconciliation.ReachedLine>();

        reach.add(AccidentalReconciliation.ReachedLine.receiving(line, insertion, strandedAfter));
        reach.addAll(AccidentalReconciliation.linesInheriting(
            line, line.keyAtEndOfLineUnder(elementIndex + 1, key)));

        var confirmed = KeyChangeReconciliation.confirm(getMainFrame(), List.of(), reach);

        if (confirmed.isCancelled()) {
            return;
        }

        withModification(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_KEY), () -> {
            // Applied before the key moves, as every reconciliation is: the changes name live notes
            // resolved against the key still in effect. The sweep goes ahead of the bound key
            // change, whichever way it goes below, so its own index is still the one this route was
            // given.
            confirmed.apply();

            if (strandsItself) {
                line.deleteRange(ownRange);
            } else {
                // ElementField.KEY is what carries the change past this line:
                // Song.maintainKeyInvariant re-derives every following line's inherited key off the
                // resulting mutation, on undo and redo as well as forward.
                line.modifyElement(elementIndex, ElementField.KEY, () -> keyChange.setKey(key));
            }
        });
    }

    /**
     * Writes a key change into the middle of the bound line at the bound index, adding ahead of it
     * the {@link ElementType#SINGLE_BARLINE} the chosen position needs when it does not already
     * follow a barline or repeat.
     *
     * <p><b>It inserts. It does not edit.</b> Only {@link KeyChangeSite.Binding#NEW_POSITION}
     * arrives here. A key change already standing at the bound index is changed in place by
     * {@link #changeMidLineKey}, which is a different edit rather than a variant of this one: it
     * moves no element, owes no barline, and is measured as a swap.
     *
     * <p><b>The barline is what keeps {@link KeyChangeElement}'s position invariant true
     * without restricting where the user may click.</b> A position that already follows a barline
     * or a repeat takes the key change alone; every other position takes two elements, barline
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
     * <p>An inserted key change moves pitches from its index forward, so it owes the same
     * reconciliation a change to the line's own key owes, and raises the same single restatement
     * prompt; cancelling it leaves nothing mutated and no undo step. Its reach differs in shape at
     * the head only: the host line is reconciled as an <em>insertion</em>, so the projection carries
     * the new element and the notes after it resolve against it, while the lines that inherit are
     * reached exactly as they are for a line-key change. See {@code docs/key-changes.md}.
     *
     * <p>A key change further along the line, or on a line that inherits, which the inserted key
     * leaves restating what is already in effect before it is removed with its barline, in this
     * same bracket. The inserted key change itself can never be one of them: writing the key already
     * in effect at the bound position changes nothing, and never reaches a commit route.
     *
     * @param key the key taking effect from the bound position on
     * @throws IndexOutOfBoundsException if the bound index is below
     *                                   {@link Line#FIRST_LEGAL_KEY_CHANGE_INDEX} or above
     *                                   {@link Line#effectiveElementCount()} — a key change is
     *                                   never the first element on a line, and never lands past
     *                                   its end
     */
    private void insertKeyChange(Key key) {
        if (elementIndex < Line.FIRST_LEGAL_KEY_CHANGE_INDEX
            || elementIndex > line.effectiveElementCount()) {

            throw new IndexOutOfBoundsException(
                "key change insertion index " + elementIndex + " out of bounds ["
                    + Line.FIRST_LEGAL_KEY_CHANGE_INDEX + ", "
                    + line.effectiveElementCount() + ']');
        }

        var inserted = new ArrayList<StaffElement>();

        if (KeyChangeElement.needsBarlineBefore(line, elementIndex)) {
            inserted.add(ElementType.SINGLE_BARLINE.newInstance());
        }

        inserted.add(new KeyChangeElement(key));

        // Every key change further along this line that the inserted key leaves restating it.
        // Scanning from the insertion point is the point: what stands before it is untouched.
        var strandedAfter = line.redundantKeyChangeRanges(elementIndex, key);

        // The line the edit lands on, reconciled as an insertion, so the projection holds the new
        // key change and every note after it resolves against the key it establishes. A fresh
        // arrival because these elements have no source context — nothing is being pasted. Then
        // the lines that inherit: an existing key change already standing after the insertion
        // point still has the last word on the key this line leaves off in, so the inserted key
        // reaches the next line only when none does.
        var reach = new ArrayList<AccidentalReconciliation.ReachedLine>();

        reach.add(AccidentalReconciliation.ReachedLine.receiving(
            line,
            new AccidentalReconciliation.Insertion(
                elementIndex, null, AccidentalReconciliation.ArrivingElements.fresh(inserted)),
            strandedAfter));

        reach.addAll(AccidentalReconciliation.linesInheriting(
            line, line.keyAtEndOfLineUnder(elementIndex, key)));

        var confirmed = KeyChangeReconciliation.confirm(getMainFrame(), List.of(), reach);

        if (confirmed.isCancelled()) {
            return;
        }

        var spacing = InsertionSpacingCalculator.calculateFragmentInsertion(
            line, inserted, elementIndex, null, null, requireScoreView().getLyricRenderMetrics());

        withModification(Strings.get(Strings.ACTION_EDIT_OP_ADD_KEY), () -> {
            // Applied before the elements land, as every reconciliation is: the changes name live
            // notes and their pre-insertion positions, and the ranges the sweep removes name
            // pre-insertion indices too.
            confirmed.apply();

            line.insertRun(
                elementIndex,
                spacing.place(inserted),
                DocumentScale.ssToPx(spacing.shiftForSubsequentElementsSs()).positionPx());
        });
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
     * The refusal for a mid-line key change, which does occupy a column on the line the notator
     * is looking at — so its message says "this line" and names what did not fit.
     *
     * <p>The category name is carried as a nested {@link LocalizedMessage} rather than as text:
     * deciding what is wrong is this side's job and wording it is the presenter's.
     *
     * @return the message a refused mid-line key change carries
     */
    private static LocalizedMessage midLineKeyRefusal() {
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
