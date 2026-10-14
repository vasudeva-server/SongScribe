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
import songscribe.dom.KeyChangeStranding;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.layout.KeyEditFitCalculator;
import songscribe.message.mutation.ElementField;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Everything {@link KeyChangeDialog} is not allowed to know: which line the notator pointed at,
 * what key is in effect there, whether the change will still fit, and which commit route writes it.
 *
 * <p>Each of the key-editing gestures has its own entry point, and a {@link KeyChangeSite} names
 * the place the change is bound to.
 *
 * <p><strong>One commit route per binding.</strong> A line's own key and an existing mid-line key
 * change are changed <em>in place</em>; a key change at a position that has none is
 * <em>inserted</em>. The two mid-line routes differ in fit measurement, stranding and mutations,
 * and only the insertion can owe a barline.
 *
 * <p><strong>An edit that cannot be drawn is refused before it is written.</strong> A key change
 * claims horizontal space on every line it re-keys, and {@link #validate} measures all of them.
 * The dialog stays open on the refused choice.
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
     * Serves both a double-click on the header and one on the cautionary at the end of the line
     * <em>before</em> it, which depicts this line's key: the caller resolves which line it is
     * about.
     */
    public static void editLineKey(MainFrame mainFrame, Line line) {
        open(mainFrame, new KeyChangeDialogController(mainFrame, KeyChangeSite.lineKey(line)));
    }

    /**
     * @param keyChange the key change the notator double-clicked, which must be an element of
     *                  {@code line}
     */
    public static void editKeyChange(MainFrame mainFrame, Line line, KeyChangeElement keyChange) {
        var keyChangeIndex = line.getElementIndex(keyChange);

        open(mainFrame, new KeyChangeDialogController(
            mainFrame, KeyChangeSite.existingKeyChange(line, keyChangeIndex)));
    }

    /**
     * The position has already been accepted by {@link Line#canInsertKeyChangeAt}, so this is for
     * a position with no key change on it; {@link #editKeyChange} is for one that has.
     *
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
     * The key already in effect where this change is bound: what the combo opens on.
     */
    @Override
    protected Key read() {
        return site.keyInEffect();
    }

    /**
     * The {@link KeyChangeSite} answers it, including the case where the chosen key is already in
     * effect and the commit still changes the document: a line taking a key of its own for the
     * first time.
     */
    @Override
    protected boolean dataWasModified(Key values) {
        return site.wouldChangeAnything(values);
    }

    /**
     * Refuses a key change that will not fit, and takes no other view of the notator's choice.
     * The measurement follows the binding: measuring a swap as an insertion would refuse it for
     * want of room the line already has. The two refusals name different lines — see
     * {@link #lineKeyRefusal()}.
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
     * Every route sweeps, inside its own modification bracket, the key changes the move strands.
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
     * A mid-line key change anywhere in the reach — this line from its start, then every line
     * inheriting from it, up to the first with a key of its own — that would restate the key in
     * effect before it is removed with its barline, in the same bracket, so one undo takes back
     * the key and the removals together. See {@code docs/key-changes.md}.
     *
     * <p>The undo step is named <em>Add</em> where the line inherited its key until now and
     * <em>Change</em> where it already had one of its own.
     */
    private void changeLineKey(Key key) {
        var stranding = KeyChangeStranding.ofLineKeyChange(line, key);

        withModification(lineKeyLabel(), () -> {
            stranding.sweep();
            line.setKey(key);
        });
    }

    /**
     * <b>It edits in place</b>, except when the chosen key is the one already in effect where
     * the key change stands. The element keeps its identity and its index.
     *
     * <p><b>A key change told to restate the key in front of it is removed instead</b>, together
     * with the barline it sits behind: it would be invisible while still in the document, which
     * the invariant in {@code docs/key-changes.md} forbids. The same goes for any key change
     * further along the line that the new key strands. The sweep runs first, so the key change's
     * own index is still the one this route was given when the in-place edit lands.
     */
    private void changeMidLineKey(Key key) {
        var keyChange = site.boundKeyChange();
        var keyBefore = line.keyBefore(elementIndex);
        var strandsItself = key == keyBefore;
        var ownRange = line.effectiveRange(StaffElementRun.ElementRange.single(elementIndex));

        // Every key change further along this line that the new key leaves restating it. Scanning
        // from past this one is the point: what stands before it is untouched by this edit.
        var strandedAfter = line.redundantKeyChangeRanges(elementIndex + 1, key);
        var onLine = new ArrayList<StaffElementRun.EffectiveRange>();

        if (strandsItself) {
            // A removal, described as one: the key change and its barline go, and nothing takes
            // their place.
            onLine.add(ownRange);
        }

        onLine.addAll(strandedAfter);

        // A key change already standing after this one still has the last word on the key the
        // line leaves off in, so the new key reaches the next line only when none does. A stranded
        // one among them says the same thing the new key does, so removing it moves nothing.
        var stranding = KeyChangeStranding.ofMidLineEdit(
            line, onLine, line.keyAtEndUnder(elementIndex + 1, key));

        withModification(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_KEY), () -> {
            // The sweep goes ahead of the bound key change, whichever way it goes below, so its
            // own index is still the one this route was given.
            stranding.sweep();

            if (!strandsItself) {
                // ElementField.KEY is what carries the change past this line:
                // Song.maintainKeyInvariant re-derives every following line's inherited key off the
                // resulting mutation, on undo and redo as well as forward.
                line.modifyElement(elementIndex, ElementField.KEY, () -> keyChange.setKey(key));
            }
        });
    }

    /**
     * <b>It inserts. It does not edit.</b> Only {@link KeyChangeSite.Binding#NEW_POSITION}
     * arrives here.
     *
     * <p><b>The barline is what keeps {@link KeyChangeElement}'s position invariant true
     * without restricting where the user may click.</b> A position that already follows a barline
     * or a repeat takes the key change alone; every other position takes a barline first. Either
     * way they enter inside <b>one</b> modification bracket, so a single undo takes back the whole
     * edit — see {@code docs/mutations.md}.
     *
     * <p>A key change further along the line, or on a line that inherits, which the inserted key
     * leaves restating what is already in effect is removed with its barline, in this same
     * bracket, before the new element lands. See {@code docs/key-changes.md}.
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

        if (line.needsBarlineBeforeKeyChangeAt(elementIndex)) {
            inserted.add(ElementType.SINGLE_BARLINE.newInstance());
        }

        inserted.add(new KeyChangeElement(key));

        // Every key change further along this line that the inserted key leaves restating it.
        // Scanning from the insertion point is the point: what stands before it is untouched.
        // An existing key change already standing after the insertion point still has the last
        // word on the key this line leaves off in, so the inserted key reaches the next line
        // only when none does.
        var strandedAfter = line.redundantKeyChangeRanges(elementIndex, key);
        var stranding = KeyChangeStranding.ofMidLineEdit(
            line, strandedAfter, line.keyAtEndUnder(elementIndex, key));

        withModification(Strings.get(Strings.ACTION_EDIT_OP_ADD_KEY), () -> {
            // Swept before the elements land, so the ranges it removes name pre-insertion
            // indices.
            stranding.sweep();

            line.insertRun(elementIndex, inserted);
        });
    }

    /**
     * Read before the mutation, so it describes what the notator did, not what the line stores.
     */
    private String lineKeyLabel() {
        return Strings.get(line.getKey() != null
            ? Strings.ACTION_EDIT_OP_CHANGE_KEY
            : Strings.ACTION_EDIT_OP_ADD_KEY);
    }

    /**
     * <b>Deliberately does not say "this line"</b>: the refusal can be for a line the notator did
     * not click, such as the previous line's cautionary or a line down the inheritance chain.
     */
    private static LocalizedMessage lineKeyRefusal() {
        return new LocalizedMessage(Strings.ERROR_LINE_FULL_KEY_CHANGE);
    }

    /**
     * A mid-line key change occupies a column on the line the notator is looking at, so the
     * message says "this line" and names what did not fit.
     */
    private static LocalizedMessage midLineKeyRefusal() {
        return new LocalizedMessage(
            Strings.ERROR_LINE_FULL_ELEMENT,
            List.of(new LocalizedMessage(ElementType.Kind.KEY_CHANGE.labelKey())));
    }

    private static ValidationResult refusalUnless(boolean fits, LocalizedMessage message) {
        return fits
            ? ValidationResult.valid()
            : ValidationResult.invalid(
                new ValidationFailure(Strings.ALERT_TITLE_LINE_TOO_FULL, message));
    }
}
