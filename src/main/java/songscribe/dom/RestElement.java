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
 * <p>Answers its glyph and its seat from its type's {@link DottableGlyphAppearance}, so a
 * question only a rest can answer is total here and always answered from what the type
 * declares. {@link ElementType#newInstance()} builds one for every type whose appearance is
 * dottable and for no other, which is what makes the narrowing to that arm sound.
 */
public final class RestElement extends StructuralElement implements Dottable {

    private Dots dots = Dots.NONE;

    RestElement(ElementType type) {
        super(type);
    }

    private DottableGlyphAppearance appearance() {
        return (DottableGlyphAppearance) getType().appearance();
    }

    /**
     * @return the glyph this element is drawn with
     */
    public SMuFLGlyph glyph() {
        return appearance().glyph();
    }

    /**
     * Returns where this rest's glyph sits on the staff, which its type declares.
     *
     * @return the seat
     */
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

    /**
     * Copies the dot count, the one thing a rest holds beyond the base class.
     *
     * @param source the rest whose dot count to take; always a {@code RestElement}, because a
     *     copy names an element of the same class
     */
    @Override
    protected void copySubtypeStateFrom(StaffElement source) {
        dots = ((RestElement) source).dots;
    }

    /**
     * Takes the dot count from any element that has one, so a dotted note turned into a rest
     * stays dotted; a source with no dots leaves the rest undotted.
     */
    @Override
    protected void applySubtypeConversionFrom(StaffElement source) {
        dots = Dottable.dotsOf(source);
    }
}
