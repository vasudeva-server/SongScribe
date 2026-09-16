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

package songscribe.ui.action;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumSet;

import songscribe.Strings;
import songscribe.dom.Accidental;
import songscribe.dom.AccidentalState;
import songscribe.dom.ChainedAccidental;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.PitchClass;
import songscribe.message.MessageCenter;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.mutation.ElementField;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.MainFrame;
import songscribe.ui.playback.PlayThread;

/**
 * The toolbar control for one accidental. Against a note it reflects the note's place in its
 * accidental chain and pitches the note when toggled; the song's chain walk, not this action,
 * decides what is written.
 *
 * <p>Selected and enabled state are both decided from the note's {@link ChainedAccidental},
 * with {@code a} this action's accidental, {@code s} the note's sounding alteration and
 * {@code k} the key's alteration for the note's pitch class at the note:
 *
 * <table>
 *   <tr><th>state</th><th>selected ({@link #matchesElement})</th><th>enabled ({@link #canToggle})</th></tr>
 *   <tr><td>{@code IN_KEY}</td><td>{@code false}</td><td>{@code a != k}</td></tr>
 *   <tr><td>{@code ANCHOR}</td><td>{@code a == s}</td><td>{@code a != k}</td></tr>
 *   <tr><td>{@code REPETITION}</td><td>{@code a == s}</td><td>{@code a != s}</td></tr>
 *   <tr><td>{@code IMPLICIT}</td><td>{@code a == s}</td><td>{@code a != s}</td></tr>
 *   <tr><td>{@code CANCEL}</td><td>{@code a == s}</td><td>{@code a != s}</td></tr>
 * </table>
 *
 * <p>An {@code IN_KEY} note shows no accidental selected because it sounds its key, and the
 * key's own accidental cannot be pressed on it: that would change nothing. Every other note's
 * sounding accidental shows selected. Deselecting is allowed only on an {@code ANCHOR}, where
 * it returns the note to its key; a {@code REPETITION}, {@code IMPLICIT} or {@code CANCEL} note
 * already sounds what deselecting would give it, so its own accidental is disabled and a
 * different one can only be selected.
 */
// Widens: the accidental glyph is drawn to the left of the notehead, inside the note's column.
public final class AccidentalAction extends NoteOnlyAction implements UIAction.WidensColumn<NoteElement> {

    private static final EnumSet<ElementField> MODIFIED_FIELDS = EnumSet.of(ElementField.ACCIDENTAL);

    /**
     * The flags every accidental action carries: enabled for an empty selection — the preview
     * note — or a single selected element, never for several. Whether an accidental may be
     * toggled is decided per note by {@link #canToggle}, so the accidental controls act on one
     * note at a time and that decision never spans a selection.
     * <p>
     * Live in grace mode, where no staff selection is possible, so the toggle only decorates
     * the preview: the host note the user is placing arrives with the chosen accidental and the
     * pairing's tracked insertion walks the chain for it.
     */
    static final Flag[] ACCIDENTAL_FLAGS = withFlags(
        NoteOnlyAction.FLAGS,
        Flag.REQUIRES_OPTIONAL_SINGLE_SELECTION,
        Flag.ENABLED_IN_GRACE_MODE
    );

    /** The icon font's point size for an accidental sign, on the toolbar and wherever else one is shown. */
    public static final int ICON_SIZE_PT = 18;

    private final Accidental accidental;

    public static AccidentalAction createFlatAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            Accidental.FLAT,
            "flat", Strings.get(Strings.ACTION_ACCIDENTAL_FLAT_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_FLAT
        );
    }

    public static AccidentalAction createDoubleFlatAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            Accidental.DOUBLE_FLAT,
            "double-flat", Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT_TOOLTIP),
            KeyEvent.VK_F, InputEvent.SHIFT_DOWN_MASK,
            Strings.ACTION_EDIT_OP_TOGGLE_DOUBLE_FLAT
        );
    }

    public static AccidentalAction createNaturalAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            Accidental.NATURAL,
            "natural", Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_TOOLTIP),
            KeyEvent.VK_N, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_NATURAL
        );
    }

    public static AccidentalAction createSharpAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            Accidental.SHARP,
            "sharp", Strings.get(Strings.ACTION_ACCIDENTAL_SHARP_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_SHARP
        );
    }

    public static AccidentalAction createDoubleSharpAction(MainFrame mainFrame) {
        return new AccidentalAction(
            mainFrame,
            Accidental.DOUBLE_SHARP,
            "double-sharp", Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP_TOOLTIP), 0, 0,
            Strings.ACTION_EDIT_OP_TOGGLE_DOUBLE_SHARP
        );
    }

    /**
     * The tagged icon text drawing {@code accidental} in the icon font, the way
     * {@link UIAction#setIcon} reads it. Shared with every control that shows an accidental
     * as the toolbar does, so the two can never draw a sign differently.
     */
    public static String iconText(Accidental accidental) {
        return switch (accidental) {
            case FLAT -> "@\uF388";
            case DOUBLE_FLAT -> "@\uF389";
            case NATURAL -> "@\uF387";
            case SHARP -> "@\uF386";
            case DOUBLE_SHARP -> "@\uF38A";
        };
    }

    /**
     * The {@link Strings} key naming {@code accidental} to the user.
     */
    public static String nameKey(Accidental accidental) {
        return switch (accidental) {
            case FLAT -> Strings.ACTION_ACCIDENTAL_FLAT;
            case DOUBLE_FLAT -> Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT;
            case NATURAL -> Strings.ACTION_ACCIDENTAL_NATURAL;
            case SHARP -> Strings.ACTION_ACCIDENTAL_SHARP;
            case DOUBLE_SHARP -> Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP;
        };
    }

    private AccidentalAction(
        MainFrame mainFrame,
        Accidental accidental,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        String undoOpNameKey
    ) {
        super(
            mainFrame,
            Strings.get(nameKey(accidental)),
            iconText(accidental),
            ICON_SIZE_PT,
            actionCommand,
            tooltip,
            virtualKey,
            modifiers,
            ACCIDENTAL_FLAGS
        );
        this.accidental = accidental;
        setUndoOpNameKey(undoOpNameKey);
    }

    public Accidental getAccidental() {
        return accidental;
    }

    /**
     * Whether this action's accidental is the one {@code element} sounds, by the table in the
     * class Javadoc: never for an {@code IN_KEY} note, whose sounding is its key's.
     *
     * @param element a stamped note
     * @return {@code true} when this action shows as selected for the note
     * @throws IllegalStateException unless the note is {@link AccidentalState.Stamped}
     */
    @Override
    public boolean matchesElement(NoteElement element) {
        var chained = element.chainedAccidental();

        return switch (chained.type()) {
            case IN_KEY -> false;
            case ANCHOR, REPETITION, IMPLICIT, CANCEL -> accidental == chained.accidental();
        };
    }

    /**
     * Whether pressing this action on the note at {@code index} would change what it sounds, by
     * the table in the class Javadoc: an {@code IN_KEY} or {@code ANCHOR} note admits every
     * accidental but its key's, and any other note admits every accidental but its own.
     *
     * @param line  the line holding the note
     * @param index the note's index in {@code line}; the element there is a stamped note
     * @return {@code true} when the toggle is allowed
     * @throws IllegalStateException unless the note is {@link AccidentalState.Stamped}
     */
    @Override
    public boolean canToggle(Line line, int index) {
        var note = (NoteElement) line.getElement(index);
        var chained = note.chainedAccidental();

        return switch (chained.type()) {
            case IN_KEY, ANCHOR -> accidental != keyAlterationAt(line, index, note);
            case REPETITION, IMPLICIT, CANCEL -> accidental != chained.accidental();
        };
    }

    /**
     * Pitches {@code element} with this action's accidental when {@code selected}, and with its
     * key's alteration when not, leaving it {@link AccidentalState.Chosen} for the song's chain
     * walk to stamp. Deselecting reaches here only on an {@code ANCHOR}, the one state
     * {@link #canToggle} allows it on.
     */
    @Override
    public void applyToElement(Line line, int index, NoteElement element, boolean selected) {
        element.setAccidentalState(
            new AccidentalState.Chosen(selected ? accidental : keyAlterationAt(line, index, element)));
    }

    /**
     * The key's alteration for {@code note}'s pitch class at {@code index} in {@code line}:
     * {@code k} in the class Javadoc's table.
     */
    private static Accidental keyAlterationAt(Line line, int index, NoteElement note) {
        return line.keyAt(index).alterationFor(PitchClass.at(note.getStaffPosition().value()));
    }

    @Override
    public EnumSet<ElementField> modifiedFields() {
        return MODIFIED_FIELDS;
    }

    @Override
    protected void performAction(ActionEvent e) {
        toggleOnKeyboardShortcut(e);

        if (!applyToSelectionIfActive()) {
            MessageCenter.post(new UpdatePreviewElementCommand(UpdatePreviewElementCommand.Scope.DECORATIONS));
            return;
        }

        // The modification bracket has closed and the song's chain walk has stamped the note,
        // so its pitch is the one it now sounds.
        if (Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            if (requireScoreView().getSingleSelectedElement() instanceof NoteElement note) {
                new PlayThread(note.getPitch()).start();
            }
        }
    }
}
