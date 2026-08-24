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

import org.jspecify.annotations.Nullable;

import songscribe.dom.Song;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.util.Copyable;

/**
 * A {@link DialogController} that reaches the open document through the application window.
 *
 * <p><strong>For a controller that is not handed what it edits.</strong> Whoever opens a dialog
 * either resolves the line and element first and constructs the controller around them — which is
 * what {@code AttachmentDialogController} does, and those controllers need no window — or leaves
 * the controller to resolve the document itself on each opening. This is the second kind, and the
 * window is how it resolves.
 *
 * <p>Extending this is therefore a statement that the controller genuinely needs the running
 * application: it makes the whole document reachable, and it is what forces a test to stand up a
 * mocked window. A controller that can be handed its subject extends {@link DialogController}
 * directly and is testable with nothing on screen.
 *
 * @param <I> what the dialog shows — see {@link DialogController}
 * @param <O> what the dialog's controls say on OK — see {@link DialogController}
 */
abstract class DocumentDialogController<I extends @Nullable Copyable<I>, O>
    extends DialogController<I, O> {

    private final MainFrame mainFrame;

    /**
     * @param mainFrame the application window, which is also how the open document is reached
     */
    protected DocumentDialogController(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /**
     * @return the application window, for parenting an alert a commit has to raise before it can
     *         proceed — not for reaching the document, which the accessors below do directly
     */
    protected final MainFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * @return the view onto the open document, for the two document-wide settings that are written
     *         through the view rather than onto the song
     */
    protected final ScoreView requireScoreView() {
        return mainFrame.requireScoreView();
    }

    /**
     * @return the song now open, which is a different song each time a document is opened — so a
     *         controller that outlives one document asks again rather than holding the answer
     */
    protected final Song getSong() {
        return requireScoreView().getSong();
    }

    /**
     * Runs {@code mutator} against the open song inside one modification bracket named
     * {@code label}.
     *
     * <p>However many fields {@code mutator} touches, the whole of it is <strong>one undoable
     * step</strong> and posts one {@code SongDidChangeNotification}. Nesting is safe: a mutator
     * that opens further brackets still produces one step, per {@code docs/mutations.md}.
     *
     * @param label   the undo-step name, already resolved to display text, as it will read in the
     *                Edit menu after {@code Undo}
     * @param mutator the change to make
     */
    protected final void withModification(String label, Runnable mutator) {
        getSong().withModification(label, mutator);
    }
}
