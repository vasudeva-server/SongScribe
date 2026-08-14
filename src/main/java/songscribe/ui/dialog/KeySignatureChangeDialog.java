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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.error.RuntimeError;
import songscribe.layout.KeyEditFitCalculator;
import songscribe.ui.KeyCellRenderer;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.BaseLabel;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.util.UIUtils;

/**
 * Names the key a change establishes, whether that change is the one at the start of a line or
 * one written into the middle of it: a combo over every valid {@link Key} plus, for a line's own
 * key on every line but the first, an explicit choice to inherit the key in effect at the end of
 * the previous line.
 *
 * <p>Line 0 can never inherit — there is nothing before it — and neither can a key signature
 * standing in the middle of a line, which exists precisely to name a key. The combo opens with
 * nothing selected regardless of the line's current key, so committing is always a deliberate
 * choice rather than a pre-selected no-op.
 *
 * <p><b>Which of the two changes this is comes from the bound index</b>, and it decides both
 * halves of the commit: which fit check refuses the edit, and which
 * {@link ScoreViewController} entry point writes it. See {@link KeySignatureInput}.
 *
 * <p><b>An edit that cannot be drawn is refused before it is written.</b> A key change claims
 * horizontal space on every line it re-keys, not only the one it is made on, and
 * {@link KeyEditFitCalculator} measures all of them; when the answer is no, the notator is told
 * and the dialog stays open on the choice they made, so a narrower signature can be picked
 * instead. The check runs before anything asks about accidental restatements, so a change that is
 * going to be refused never raises a prompt about accidentals it will never apply.
 */
public class KeySignatureChangeDialog extends StandardDialog {

    /**
     * The {@code insertionIndex} that binds this dialog to a line's <em>own</em> key — the one the
     * header draws and a cautionary warns of — rather than to a key signature standing in the
     * middle of the line.
     *
     * <p>Named because a bare 0 at a call site reads as "the first element" when it means the
     * opposite: no element at all. {@link KeySignatureElement}'s position invariant forbids a key
     * signature at index 0, which is what leaves the value free to carry this meaning.
     */
    public static final int LINE_OWN_KEY_INDEX = 0;

    /**
     * What this dialog is bound to: which line, the element index the change is anchored to,
     * and whether the caller is establishing a new key or editing one that already exists.
     *
     * @param line the line whose key this dialog adds or edits
     * @param insertionIndex the element index the key change is anchored to.
     *                       <b>{@link #LINE_OWN_KEY_INDEX} is the line's own key</b> — what the
     *                       header draws and what a cautionary at the end of the line before it
     *                       warns of; every other index is a key signature written into the
     *                       middle of the line at that position
     * @param op whether this is adding a key where the line previously inherited one, or
     *           editing a key the line already has
     */
    record KeySignatureInput(Line line, int insertionIndex, DialogOp op) {

        /**
         * Whether this dialog is bound to a key signature standing in the middle of the line
         * rather than to the line's own key. The two are refused by different fit checks and
         * commit by different routes, and this is the one thing that tells them apart.
         *
         * @return {@code true} when the bound index names a position inside the line
         */
        boolean isMidLineInsertion() {
            return insertionIndex != LINE_OWN_KEY_INDEX;
        }
    }

    final JComboBox<Object> keysCombo = new JComboBox<>();

    @Nullable KeySignatureInput input = null;

    public KeySignatureChangeDialog(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.DIALOG_KEY_SIGNATURE_CHANGE_TITLE));

        addLabeledField(contentPanel, Strings.get(Strings.LABEL_KEYSIG_SELECT_PROMPT), keysCombo, LabelPosition.TOP);
        keysCombo.setRenderer(new KeyChoiceRenderer());
        keysCombo.addItemListener(_ -> okButton.setEnabled(keysCombo.getSelectedIndex() != -1));
        UIUtils.forceLightModeCombo(keysCombo);

        okButton.setEnabled(false);
    }

    /**
     * Shows this dialog bound to {@code line}, ready to add or edit the key established at the
     * start of it, or — at any index but 0 — to write a key signature into the middle of it.
     *
     * <p>Whether the dialog names the commit "Add" or "Change" is derived from {@code line}'s
     * current state — {@link DialogOp#EDIT} when it already has its own key, otherwise
     * {@link DialogOp#ADD} — rather than asked of the caller, so a caller outside this package
     * never needs to name the protected {@link DialogOp} type.
     *
     * @param line the line whose key this dialog adds or edits
     * @param insertionIndex the element index the key change is anchored to, 0 for the line's own
     *                       key — see {@link KeySignatureInput}
     */
    public void showFor(Line line, int insertionIndex) {
        var op = line.getKey() != null ? DialogOp.EDIT : DialogOp.ADD;
        input = new KeySignatureInput(line, insertionIndex, op);
        setVisible(true);
    }

    @Override
    protected boolean getData() {
        var currentInput = requireInput();
        var line = currentInput.line();
        var song = line.getSong();
        var lineIndex = song.indexOfLine(line);
        var model = new DefaultComboBoxModel<>();

        for (var key : Key.allSignatures()) {
            model.addElement(key);
        }

        // Only a line's own key can be inherited, and only from a line before it. A key signature
        // standing in the middle of a line is a change; "no change here" is what Cancel means.
        if (!currentInput.isMidLineInsertion() && lineIndex != 0) {
            model.addElement(InheritChoice.INSTANCE);
        }

        keysCombo.setModel(model);
        keysCombo.setSelectedIndex(-1);
        okButton.setEnabled(false);

        return true;
    }

    /**
     * Refuses a key change that will not fit, and takes no other view of the notator's choice.
     *
     * <p>Which check runs follows the bound index: a line's own key is measured with
     * {@link KeyEditFitCalculator#lineKeyChangeFits}, which walks the whole inheritance chain the
     * change re-keys and the cautionary it creates on the line before it; a mid-line key signature
     * is measured with {@link KeyEditFitCalculator#keySignatureFits}, which additionally holds the
     * column the signature occupies and the barline {@link ScoreViewController#insertKeySignature}
     * inserts alongside it. Neither is a partial query and neither half is available on its own.
     *
     * <p>A refusal leaves the dialog open on the choice that was refused, so a narrower signature
     * can be chosen without starting the operation again, and leaves the model untouched: nothing
     * is written and nothing is asked, which is why this runs before {@link #setData}'s commit
     * rather than inside it.
     *
     * <p>The line's own key is measured as the key the line will <em>run</em> in, which for the
     * inherit choice is the key the line before it leaves off in rather than a choice of the
     * notator's — what has to fit is the layout the edit produces.
     *
     * @return {@code true} when the chosen change fits, or when nothing is chosen and
     *         {@link #setData} would commit nothing
     */
    /**
     * The OK lifecycle, wired to the pre-seam {@link #isValidData()} / {@link #setData()} pair
     * this class still carries.
     *
     * <p><strong>Temporary.</strong> This dialog has not been migrated to the back-end seam in
     * {@code .claude/guides/dialogs.md} — it still holds a {@link Line} and reaches through it
     * to the document, which that guide forbids. Phase 2 of {@code plans/776-design-pass.md}
     * rewrites it to take a bound input and a back end; this method exists only so the branch
     * builds until then.
     *
     * @return {@code true} when the change was committed and the dialog may close
     */
    @Override
    protected boolean commitOnOk() {
        if (!isValidData()) {
            return false;
        }

        setData();
        return true;
    }

    /**
     * Reaches the score view through {@link #getMainFrame()}.
     *
     * <p><strong>Temporary, and a deliberate rule violation.</strong>
     * {@code .claude/guides/dialogs.md} calls reaching the document through
     * {@code getMainFrame()} "the same rule broken in a longer spelling". It stands only until
     * Phase 2 replaces it with a back end.
     *
     * @return the score view; never null while a dialog the notator opened is up
     */
    private ScoreView requireScoreView() {
        var scoreView = getMainFrame().getScoreView();

        if (scoreView == null) {
            throw RuntimeError.exit("KeySignatureChangeDialog shown with no score view");
        }

        return scoreView;
    }

    private boolean isValidData() {
        var selectedIndex = keysCombo.getSelectedIndex();

        if (selectedIndex == -1) {
            return true;
        }

        var currentInput = requireInput();
        var line = currentInput.line();
        var lyricRenderMetrics = requireScoreView().getLyricRenderMetrics();
        boolean fits;

        if (currentInput.isMidLineInsertion()) {
            fits = KeyEditFitCalculator.keySignatureFits(
                line, currentInput.insertionIndex(), requireChosenKey(selectedIndex), lyricRenderMetrics);

            if (!fits) {
                // A mid-line signature occupies a column on this line, so "this line" is accurate.
                OptionDialogs.showErrorMessage(
                    contentPanel,
                    Strings.ALERT_TITLE_LINE_TOO_FULL,
                    Strings.ERROR_LINE_FULL_ELEMENT,
                    ElementType.KEY_SIGNATURE.categoryName()
                );
            }
        } else {
            fits = KeyEditFitCalculator.lineKeyChangeFits(
                line, runningKeyAfterChange(line, chosenKey(selectedIndex)), lyricRenderMetrics);

            if (!fits) {
                // Deliberately does not say "this line". A line-key change is refused for a line
                // the notator did not click: the cautionary it creates at the end of the previous
                // one, or the header of any line down the inheritance chain that it re-keys.
                // Naming the clicked line would send them to look at the wrong place.
                OptionDialogs.showErrorMessage(
                    contentPanel,
                    Strings.ALERT_TITLE_LINE_TOO_FULL,
                    Strings.ERROR_LINE_FULL_KEY_CHANGE
                );
            }
        }

        return fits;
    }

    /**
     * Commits the chosen change through {@link ScoreViewController}, which is the route every
     * pitch-moving edit takes: a key change moves pitches, so it owes the same accidental
     * reconciliation and the same restatement prompt as an inserted barline, and going through the
     * controller is what gets it both. Nothing is written here.
     *
     * <p>Cancelling at that prompt abandons the change and closes the dialog, the same reading a
     * declined confirm gets on every other edit: it is a decision about the change, not about
     * whether the notator is still choosing one.
     */
    private void setData() {
        var selectedIndex = keysCombo.getSelectedIndex();

        if (selectedIndex == -1) {
            return;
        }

        var currentInput = requireInput();
        var line = currentInput.line();
        var controller = requireController();

        if (currentInput.isMidLineInsertion()) {
            controller.insertKeySignature(
                line, currentInput.insertionIndex(), requireChosenKey(selectedIndex));
        } else {
            controller.changeLineKey(line, chosenKey(selectedIndex), opLabel(currentInput.op()));
        }
    }

    /**
     * @param selectedIndex an index into the combo's model
     * @return the key that entry names, or null for the inherit choice — the one entry in the
     *         combo that is not a {@link Key}
     */
    private @Nullable Key chosenKey(int selectedIndex) {
        return keysCombo.getItemAt(selectedIndex) instanceof Key key ? key : null;
    }

    /**
     * The same, for the mid-line route, where the inherit choice is not offered and a key signature
     * that names no key is not a thing that can be written.
     *
     * @param selectedIndex an index into the combo's model
     * @return the key that entry names
     */
    private Key requireChosenKey(int selectedIndex) {
        var key = chosenKey(selectedIndex);

        if (key == null) {
            throw RuntimeError.exit(
                "a mid-line key signature cannot inherit, so the combo must not offer that choice");
        }

        return key;
    }

    /**
     * The key {@code line} would run in once {@code key} is committed as its own key: {@code key}
     * itself, or — for the inherit choice — the key the line before it leaves off in, which is
     * what the line would then inherit. The inherit choice is offered only on a line that has one
     * before it.
     *
     * @param line the line whose own key is being set
     * @param key the chosen key, or null for the inherit choice
     * @return the key {@code line} runs in afterwards, which is what has to fit
     */
    private static Key runningKeyAfterChange(Line line, @Nullable Key key) {
        if (key != null) {
            return key;
        }

        var song = line.getSong();
        return song.getLine(song.indexOfLine(line) - 1).keyAtEndOfLine();
    }

    /**
     * @return the controller every commit goes through; a dialog the notator opened always has
     *         one, so its absence is corrupted application state rather than a case to degrade for
     */
    private ScoreViewController requireController() {
        var controller = requireScoreView().getController();

        if (controller == null) {
            throw RuntimeError.exit("KeySignatureChangeDialog committed with no score view controller");
        }

        return controller;
    }

    private KeySignatureInput requireInput() {
        var currentInput = input;

        if (currentInput == null) {
            throw RuntimeError.exit("KeySignatureChangeDialog shown without a bound line; call showFor first");
        }

        return currentInput;
    }

    private String opLabel(DialogOp op) {
        return Strings.get(switch (op) {
            case ADD -> Strings.ACTION_EDIT_OP_ADD_KEY;
            case EDIT -> Strings.ACTION_EDIT_OP_CHANGE_KEY;
            case REMOVE -> throw new IllegalArgumentException(
                "KeySignatureChangeDialog never derives DialogOp.REMOVE");
        });
    }

    /** The combo entry meaning "no key of its own; inherit the previous line's." */
    private enum InheritChoice { INSTANCE }

    /**
     * Renders a {@link Key} exactly as {@link KeyCellRenderer} does, and the one entry
     * {@link KeyCellRenderer} cannot represent — {@link InheritChoice}, and the transient "no
     * selection yet" state the combo opens in — as a plain label.
     */
    private static final class KeyChoiceRenderer implements ListCellRenderer<Object> {

        private final KeyCellRenderer keyCellRenderer = new KeyCellRenderer();

        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            @Nullable Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            if (value instanceof Key key) {
                @SuppressWarnings("unchecked") // this list holds only Key and InheritChoice entries
                var keyList = (JList<Key>) list;
                return keyCellRenderer.getListCellRendererComponent(
                    keyList, key, index, isSelected, cellHasFocus);
            }

            var text = value == InheritChoice.INSTANCE ? Strings.get(Strings.LABEL_KEYSIG_INHERIT) : "";
            return new BaseLabel(text, list, index, isSelected);
        }
    }
}
