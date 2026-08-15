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
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleFallCommand;
import songscribe.message.command.ToggleGlissandoCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreViewController;

public final class ToggleNotationAction extends UIAction {

    private final Predicate<? super ScoreViewController> canToggle;
    private final Supplier<? extends Message> commandFactory;

    public static ToggleNotationAction createBeamAction(MainFrame mainFrame) {
        return new ToggleNotationAction(
            mainFrame,
            Strings.get(Strings.ACTION_BEAM_TOGGLE),
            "beam.svg",
            28,
            "toggle-beam",
            Strings.get(Strings.ACTION_BEAM_TOGGLE_TOOLTIP),
            KeyEvent.VK_B,
            0,
            Flag.REQUIRES_MULTIPLE_SELECTION,
            ScoreViewController::canToggleBeaming,
            ToggleBeamCommand::new,
            Strings.ACTION_EDIT_OP_TOGGLE_BEAM
        );
    }

    /**
     * Unlike beaming, tying takes {@link Flag#REQUIRES_SELECTION} rather than
     * {@link Flag#REQUIRES_MULTIPLE_SELECTION}: a tie across a line break is offered on a
     * <b>single</b> selected note \u2014 a line's last note, or its first, past any barline or
     * repeat closing or opening the line \u2014 and pairs it with the matching note at the adjacent
     * line's own edge (#493). A size requirement is checked before
     * {@code canToggle} runs, so {@code REQUIRES_MULTIPLE_SELECTION} would reject that
     * selection before the boundary lookup was ever consulted. Sizes this admits but a tie
     * cannot use are rejected by {@code RangeQueries.canToggleTie}, which is the real gate:
     * it accepts a single element only when a cross-line partner exists.
     */
    public static ToggleNotationAction createTieAction(MainFrame mainFrame) {
        return new ToggleNotationAction(
            mainFrame,
            Strings.get(Strings.ACTION_TIE_TOGGLE),
            "@\uF373",
            20,
            "toggle-tie",
            Strings.get(Strings.ACTION_TIE_TOGGLE_TOOLTIP),
            KeyEvent.VK_T,
            0,
            Flag.REQUIRES_SELECTION,
            ScoreViewController::canToggleTie,
            ToggleTieCommand::new,
            Strings.ACTION_EDIT_OP_TOGGLE_TIE
        );
    }

    /**
     * Like tying, and unlike beaming, a glissando takes {@link Flag#REQUIRES_SELECTION}
     * rather than {@link Flag#REQUIRES_MULTIPLE_SELECTION}: a single selected note is
     * already eligible, because the glissando pairs it with the note before it. The real
     * gate is {@code ScoreViewController.canToggleGlissando}, which checks that such a
     * predecessor exists and that the pair can carry a glissando.
     * <p>
     * The undo-op-name key is {@code null} (Tier B): {@code SlideOperations} labels its own
     * {@code withModification} bracket with {@code OpNames.addSlideLabel} /
     * {@code OpNames.deleteSlideLabel}, so the action must not impose a static label.
     */
    public static ToggleNotationAction createGlissandoAction(MainFrame mainFrame) {
        return new ToggleNotationAction(
            mainFrame,
            Strings.get(Strings.ACTION_GLISSANDO_TOGGLE),
            "connecting-glissando.svg",
            26,
            "toggle-glissando",
            Strings.get(Strings.ACTION_GLISSANDO_TOGGLE_TOOLTIP),
            KeyEvent.VK_G,
            InputEvent.SHIFT_DOWN_MASK,
            Flag.REQUIRES_SELECTION,
            ScoreViewController::canToggleGlissando,
            ToggleGlissandoCommand::new,
            null
        );
    }

    /**
     * Like tying, and unlike beaming, a fall takes {@link Flag#REQUIRES_SELECTION} rather
     * than {@link Flag#REQUIRES_MULTIPLE_SELECTION}: a fall applies to a note directly, so
     * a single selected note is eligible. The real gate is
     * {@code ScoreViewController.canToggleFall}.
     * <p>
     * The undo-op-name key is {@code null} (Tier B): {@code SlideOperations} labels its own
     * {@code withModification} bracket with {@code OpNames.addSlideLabel} /
     * {@code OpNames.deleteSlideLabel}, so the action must not impose a static label.
     */
    public static ToggleNotationAction createFallAction(MainFrame mainFrame) {
        return new ToggleNotationAction(
            mainFrame,
            Strings.get(Strings.ACTION_FALL_TOGGLE),
            "fall.svg",
            26,
            "toggle-fall",
            Strings.get(Strings.ACTION_FALL_TOGGLE_TOOLTIP),
            KeyEvent.VK_F,
            0,
            Flag.REQUIRES_SELECTION,
            ScoreViewController::canToggleFall,
            ToggleFallCommand::new,
            null
        );
    }

    private ToggleNotationAction(
        MainFrame mainFrame,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        Flag selectionSizeFlag,
        Predicate<? super ScoreViewController> canToggle,
        Supplier<? extends Message> commandFactory,
        @Nullable String undoOpNameKey
    ) {
        super(
            mainFrame,
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            virtualKey,
            modifiers,
            selectionSizeFlag,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.canToggle = canToggle;
        this.commandFactory = commandFactory;
        setUndoOpNameKey(undoOpNameKey);
    }

    @Override
    @Handler
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        handleChange();
    }

    @Override
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        handleChange();
    }

    @Override
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        handleChange();
    }

    private void handleChange() {
        var ctrl = getScoreViewController();

        if (ctrl != null && updateEnabledState()) {
            setEnabled(canToggle.test(ctrl));
        }
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(commandFactory.get());
    }
}
