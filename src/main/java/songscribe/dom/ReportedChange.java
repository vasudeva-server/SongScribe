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
 * is told about it.
 *
 * <p>It holds no model object, so it can outlive any change to the song.
 *
 * @param headingKey a {@code Strings} key whose value is plain text
 * @param where      as the song stood when the report was built, or {@code null} for a change
 *                   with no place in the song
 */
public record ReportedChange(String headingKey, String description, @Nullable LineRange where) { }
