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

package songscribe.message.notification;

import songscribe.message.Message;

/**
 * Posted once per edit that forced one or more tuplets out of the song, so the UI can tell
 * the user why brackets they did not touch have disappeared.
 *
 * <p>The removals themselves happen in {@code songscribe.dom} and in the paste path, neither
 * of which may raise an alert of its own: {@code songscribe.dom} must not depend on the UI.
 * The only payload is {@link Cause}, because the warning differs by what the user just did
 * but not by how many tuplets went.
 *
 * <p>Posted after the outermost modification bracket has closed, so a subscriber that
 * shows a modal alert does not block with the score still painted as it was before the
 * removals. It is never posted during undo/redo replay or while mutation tracking is
 * suspended, because neither re-derives the removals.
 */
public class TupletsWereRemovedNotification extends Message {

    /** What the user did that cost them the tuplets. */
    public enum Cause {
        /** A change to the beat — the song's tempo, a tempo change, or a metric modulation. */
        BEAT_EDIT,

        /** A paste whose destination broke the pasted tuplet's span. */
        PASTE
    }

    private final Cause cause;

    public TupletsWereRemovedNotification(Cause cause) {
        this.cause = cause;
    }

    public Cause getCause() {
        return cause;
    }
}
