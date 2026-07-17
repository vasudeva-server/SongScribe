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
 * Posted by {@code UndoController} whenever the undo/redo stacks change (a forward
 * edit pushed a step, an undo/redo moved a step between stacks, or a document load
 * cleared them). Subscribers such as the Undo/Redo actions refresh their enabled
 * state and menu labels in response. Handler name convention: {@code undoStateDidChange}.
 */
public class UndoStateDidChangeNotification extends Message {
}
