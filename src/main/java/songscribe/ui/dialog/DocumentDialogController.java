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
 * A {@link DialogController} that reaches the open document through the application window, for a
 * controller that is not handed what it edits.
 *
 * <p>Extending this states that the controller genuinely needs the running application, which
 * forces a test to stand up a mocked window. A controller that can be handed its subject extends
 * {@link DialogController} directly.
 */
abstract class DocumentDialogController<I extends @Nullable Copyable<I>, O extends @Nullable Object>
    extends DialogController<I, O> {

    private final MainFrame mainFrame;

    protected DocumentDialogController(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /**
     * For parenting an alert a commit has to raise, not for reaching the document.
     */
    protected final MainFrame getMainFrame() {
        return mainFrame;
    }

    protected final ScoreView requireScoreView() {
        return mainFrame.requireScoreView();
    }

    /**
     * A controller that outlives a document asks again rather than holding the answer.
     */
    protected final Song getSong() {
        return requireScoreView().getSong();
    }

    /**
     * However many fields {@code mutator} touches, the whole of it is <strong>one undoable
     * step</strong> and posts one {@code SongDidChangeNotification}.
     *
     * @param label the undo-step name, already resolved to display text
     */
    protected final void withModification(String label, Runnable mutator) {
        getSong().withModification(label, mutator);
    }
}
