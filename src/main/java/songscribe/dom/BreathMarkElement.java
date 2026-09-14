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
 * A breath mark: a non-sounding element drawn as one glyph at a seat of its own, with nothing
 * an editor can add to it.
 *
 * <p>Answers its glyph and its seat from its type's {@link PlainGlyphAppearance}, so a question
 * only a breath mark can answer is total here and always answered from what the type declares.
 * {@link ElementType#newInstance()} builds one for every type whose appearance is a plain glyph
 * and for no other, which is what makes the narrowing to that arm sound.
 */
public final class BreathMarkElement extends StructuralElement {

    BreathMarkElement(ElementType type) {
        super(type);
    }

    private PlainGlyphAppearance appearance() {
        return (PlainGlyphAppearance) getType().appearance();
    }

    /**
     * @return the glyph this element is drawn with
     */
    public SMuFLGlyph glyph() {
        return appearance().glyph();
    }

    /**
     * Returns where this breath mark's glyph sits on the staff, which its type declares.
     *
     * @return the seat
     */
    public StaffPosition seatSp() {
        return new StaffPosition(appearance().seatSp());
    }

    @Override
    public BreathMarkElement clone() {
        var copy = new BreathMarkElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }
}
