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
 * Posted by {@code songscribe.ui.component.ActivationGate} once the reactivation glass pane
 * actually comes down and mouse events resume reaching the score.
 * <p>
 * This is later than raw window activation: while the app is in the background the glass pane
 * stays up to intercept the reactivating click, and it only comes down once that click arrives
 * or, for keyboard-based activation (e.g. Cmd+Tab), once a short timeout elapses. Until then,
 * {@code Component.getMousePosition()} on anything beneath the glass pane returns null — the
 * glass pane is what a mouse-position lookup finds under the pointer. A listener that needs an
 * accurate mouse position once the app is usable again (e.g. re-showing the hover preview) must
 * react to this message rather than to window activation directly. Pairs with
 * {@link ApplicationDidEnterBackgroundNotification}.
 */
public class ApplicationDidBecomeActiveNotification extends Message {
}
