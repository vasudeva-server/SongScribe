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
package songscribe.dom;

import org.jspecify.annotations.Nullable;

/**
 * One change the song made on its own, as the user is told about it: a span an edit broke and
 * the model removed, a tuplet a load repaired, an accidental a load converted.
 *
 * <p>The code that made the change builds the text, because it holds the domain knowledge of
 * what happened and why. The view that shows a {@link ChangeReport} only groups entries by
 * {@link #headingKey} and shows each one's {@link #description} at its {@link #where}; it never
 * interprets a change.
 *
 * @param headingKey  a {@code Strings} key whose value is plain text naming the kind of change
 *                    for this one entry, such as "A tie was removed"
 * @param description finished user-facing text saying why this change was made
 * @param where       the elements the change was made at, or {@code null} for a change with no
 *                    location in the song, such as the accidental conversion at load
 */
public record ReportedChange(String headingKey, String description, @Nullable ElementLocationRange where) { }
