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

import songscribe.smufl.SMuFLGlyph;

/**
 * A rest or a breath mark: a non-sounding element drawn as one glyph at a seat of its own.
 *
 * <p>Answers its glyph and its seat from its type's {@link GlyphAppearance}, so a question only
 * a glyph element can answer is total here and always answered from what the type declares.
 * {@link ElementType#newInstance()} builds one for every type whose appearance is a glyph and
 * for no other, which is what makes the narrowing to that arm sound. A rest takes augmentation
 * dots; a breath mark does not.
 */
public final class GlyphElement extends StructuralElement {

    GlyphElement(ElementType type) {
        super(type);
    }

    private GlyphAppearance appearance() {
        return (GlyphAppearance) getType().appearance();
    }

    /**
     * @return the glyph this element is drawn with
     */
    public SMuFLGlyph glyph() {
        return appearance().glyph();
    }

    @Override
    public int getStaffPosition() {
        return appearance().seatSp();
    }

    @Override
    public int getDotCount() {
        return getType().isRest() ? dotCount : 0;
    }

    @Override
    public GlyphElement clone() {
        var copy = new GlyphElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }
}
