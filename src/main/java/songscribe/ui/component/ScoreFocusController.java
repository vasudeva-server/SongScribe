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

package songscribe.ui.component;

import module java.desktop;

import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

interface FocusRestorationCallback {

    boolean requestFocusInWindow();
}

public final class ScoreFocusController implements FocusListener {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreFocusController.class);

    // Except for clicks on a few components such as lyrics fields, we don't want
    // the score to lose focus when other parts of the UI are clicked. When the score
    // loses focus, if the component gaining focus is not in this list, a thread is
    // started which gives focus back to the score.
    private final ArrayList<Component> componentsAllowedToGainFocus =
        new ArrayList<>();

    private final FocusRestorationCallback callback;

    public ScoreFocusController(@NotNull FocusRestorationCallback callback) {
        this.callback = callback;
    }

    @Override
    public void focusGained(FocusEvent e) {
    }

    @Override
    public void focusLost(@NotNull FocusEvent e) {
        if (!componentsAllowedToGainFocus.contains(e.getOppositeComponent())) {
            new FocusLostThread().start();
        }
    }

    public void allowFocusInComponent(Component component) {
        componentsAllowedToGainFocus.add(component);
    }

    private class FocusLostThread extends Thread {

        @Override
        public void run() {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                LOG.error("Focus restoration interrupted", e);
            }

            callback.requestFocusInWindow();
        }
    }
}
