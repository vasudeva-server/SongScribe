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

/**
 * One reason a {@link DialogBackEnd} refused the values a dialog gathered, in the form the user
 * will eventually see it: an alert title and an alert message.
 *
 * <p>Both are carried unresolved, so the back end states what is wrong without producing text —
 * see {@link LocalizedMessage} for why that separation matters. The title and the message are
 * both required because the alerts this replaces already carry both: a failing font change in
 * the song settings shows {@code alert.title.lines.do.not.fit} over
 * {@code alert.font.change.invalid}, and a message alone would lose the heading.
 *
 * <p>A failure is a statement about the input, not an instruction to display anything. Nothing
 * here decides whether, when or in what window the user is told; that is the presenting
 * dialog's decision.
 *
 * @param titleKey a {@link songscribe.Strings} constant naming the alert title. A bare key
 *                 rather than a {@link LocalizedMessage} because alert titles are topic labels
 *                 with no format arguments — {@link songscribe.ui.OptionDialogs} resolves every
 *                 title through the non-pattern {@code Strings.get}
 * @param message  the alert body, with whatever arguments its pattern needs
 */
public record ValidationFailure(String titleKey, LocalizedMessage message) {}
