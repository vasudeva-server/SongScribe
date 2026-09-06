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
 * A non-sounding element: a rest, a barline, a repeat, a breath mark or a key change. Has no
 * pitch, so it carries no accidental, and spans the staff, so the middle line is its position by
 * definition. Each leaf holds the appearance arm it is drawn from — {@link GlyphElement},
 * {@link BarElement}, {@link KeyChangeElement} — and overrides what that arm answers
 * differently: a glyph sits at its own seat, and a rest takes dots.
 */
public abstract sealed class StructuralElement extends StaffElement
    permits GlyphElement, BarElement, KeyChangeElement {

    protected StructuralElement(ElementType type) {
        super(type);
    }

    @Override
    public int getStaffPosition() {
        return 0;
    }

    @Override
    public int getDotCount() {
        return 0;
    }

    @Override
    public StaffElement.@Nullable Accidental getAccidental() {
        return null;
    }
}
