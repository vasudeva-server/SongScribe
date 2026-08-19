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
 * The wording {@link OtherValueDialog} opens with: the window's title and the prompt above its one
 * field.
 *
 * <p>It carries <strong>resolved display text</strong>, not {@link songscribe.Strings} keys, which
 * is how every other dialog in this package takes its title — the caller resolves, the dialog
 * shows. A prompt built from keys would make the dialog the thing that decides when a string is
 * looked up, and it is the only dialog here that would.
 *
 * <p>It is a record rather than two constructor parameters because two adjacent transposable
 * {@code String}s at a call site are exactly what {@code .claude/rules/java.md} requires a parameter
 * object for: nothing but the argument order distinguishes a title from a prompt, and the compiler
 * cannot catch the transposition.
 *
 * @param title the dialog window's title
 * @param label the prompt shown above the field
 */
record OtherValuePrompt(String title, String label) {}
