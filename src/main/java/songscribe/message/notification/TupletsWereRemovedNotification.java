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
 * Posted once per beat-defining edit whose new beat context forced one or more tuplets
 * out of the song, so the UI can tell the user why brackets they did not touch have
 * disappeared.
 *
 * <p>The removals themselves happen in {@code Song}'s beat-edit chokepoint, which cannot
 * raise an alert of its own: {@code songscribe.dom} must not depend on the UI. This
 * notification is the whole of the report — it carries no payload because the warning is
 * the same regardless of how many tuplets went or which edit dropped them.
 *
 * <p>Posted after the outermost modification bracket has closed, so a subscriber that
 * shows a modal alert does not block with the score still painted as it was before the
 * removals. It is never posted during undo/redo replay or while mutation tracking is
 * suspended, because neither re-derives the removals.
 */
public class TupletsWereRemovedNotification extends Message {
}
