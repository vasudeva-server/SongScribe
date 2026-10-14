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

import songscribe.engraving.StaffPosition;
import songscribe.smufl.SMuFLGlyph;

/**
 * A rest: a non-sounding element drawn as one glyph at a seat of its own, followed by its
 * augmentation dots.
 *
 * <p>{@link ElementType#newInstance()} builds one for every type whose appearance is dottable
 * and for no other, which is what makes the narrowing to that arm sound.
 */
public final class RestElement extends StructuralElement implements Dottable {

    private Dots dots = Dots.NONE;

    RestElement(ElementType type) {
        super(type);
    }

    private DottableGlyphAppearance appearance() {
        return (DottableGlyphAppearance) getType().appearance();
    }

    public SMuFLGlyph glyph() {
        return appearance().glyph();
    }

    public StaffPosition seatSp() {
        return new StaffPosition(appearance().seatSp());
    }

    @Override
    public Dots dots() {
        return dots;
    }

    @Override
    public void setDots(Dots dots) {
        this.dots = dots;
    }

    @Override
    public RestElement clone() {
        var copy = new RestElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }

    @Override
    protected void copySubtypeStateFrom(StaffElement source) {
        dots = ((RestElement) source).dots;
    }

    @Override
    protected void applySubtypeConversionFrom(StaffElement source) {
        dots = Dottable.dotsOf(source);
    }
}
