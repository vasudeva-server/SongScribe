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

/**
 * The Swing half of the binding framework: views that make a Swing control's state
 * bindable.
 *
 * <p>{@link songscribe.binding} owns the values, the bindings and the propagation,
 * and knows nothing about Swing. This package supplies the adapters. {@code Controls}
 * returns two-way {@code Property} views over what the user edits — a checkbox's
 * selected state, a radio group's chosen constant, a text field's text. {@code Widgets}
 * returns write-only {@code WritableValue} sinks for presentation state Swing never
 * reports back: an enabled flag, a font, a preview. {@code Timing} chooses when a text
 * control's property notifies.
 *
 * <p>Every invariant of {@code songscribe.binding} holds here too — EDT only, and
 * values replaced rather than mutated.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>A view registers a Swing listener on its control and never unregisters it. The
 * listener lives as long as the control does, and both die with the dialog that built
 * them. Disposing a {@code Bindings} cancels the observations taken <i>on</i> a view;
 * it does not — and need not — unregister the view's own listener. A view over storage
 * that outlives the dialog cannot make that assumption and belongs elsewhere.
 */
@NullMarked
package songscribe.ui.binding;

import org.jspecify.annotations.NullMarked;
