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
 * One change the song made on its own, as a recorder hands it to a {@link ChangeReport.Builder}
 * while an edit or a load runs.
 *
 * <p>A change names lines, never elements, and by {@link Line} object rather than index: line
 * numbers are resolved only when the report is built ({@link ChangeReport.Builder#build()}), so a
 * line added or removed later in the same edit does not make the entry wrong.
 *
 * @param headingKey a {@code Strings} key whose value is plain text
 * @param where      {@code null} only for a change with no place in the song
 */
public record RecordedChange(String headingKey, String description, @Nullable ChangeLines where) { }
