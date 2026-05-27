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

package songscribe.message.mutation;

/**
 * Identifies which layout property changed in a {@link LayoutChange} mutation.
 * All current layout fields are staff-space measurements stored as {@code Double};
 * {@link LayoutChange}'s canonical constructor validates values against this type.
 */
public enum LayoutField {
    LINE_WIDTH_SS(Double.class),
    ROW_HEIGHT_ADJUSTMENT_SS(Double.class),
    ATTRIBUTION_START_Y_SS(Double.class);

    private final Class<?> expectedType;

    LayoutField(Class<?> expectedType) {
        this.expectedType = expectedType;
    }

    public Class<?> getExpectedType() {
        return expectedType;
    }
}
