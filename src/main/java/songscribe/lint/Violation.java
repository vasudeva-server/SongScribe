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

package songscribe.lint;

/**
 * One thing a {@link LintCheck} found wrong with the song, before it is tied to its rule. A
 * violation has exactly one mark: one finding, one place.
 *
 * @param heading     the heading the Messages panel and the tooltip show above the violation,
 *                    already resolved through {@code Strings.get}. The check chooses it for each
 *                    violation, so one rule can list different kinds of breach under different
 *                    headings.
 * @param description the user-facing text that says what is wrong, already resolved through
 *                    {@code Strings.get}; the view shows it as it is and never interprets it
 * @param mark        where the violation sits and how the score draws it
 */
public record Violation(String heading, String description, LintMark mark) {
}
