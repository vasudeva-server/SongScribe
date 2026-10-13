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
 * One entry of a finished {@link ChangeReport}: a change the song made on its own, as the user
 * is told about it — a span an edit broke and the model removed, a tuplet a load repaired, an
 * accidental a load converted.
 *
 * <p>The code that made the change builds the text, because it holds the domain knowledge of
 * what happened and why. The view that shows a {@link ChangeReport} shows each entry's heading
 * ({@link #headingKey}), place ({@link #where}) and {@link #description} as that code wrote
 * them; it never interprets a change.
 *
 * <p>A finished entry holds line indices only, resolved from its {@link RecordedChange} when the
 * report was built, and holds no model object, so it can outlive any change to the song.
 *
 * @param headingKey  a {@code Strings} key whose value is plain text naming the kind of change
 *                    for this one entry, such as "A tie was removed"
 * @param description finished user-facing text saying why this change was made
 * @param where       the lines the change was made on, as the song stood when the report was
 *                    built, or {@code null} for a change with no place in the song
 */
public record ReportedChange(String headingKey, String description, @Nullable LineRange where) { }
