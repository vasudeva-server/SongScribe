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

import songscribe.smufl.Notehead;
import songscribe.smufl.StemmedNotehead;

/**
 * A sounding element: a note or a grace note, drawn from a notehead, with a pitch, a stem, an
 * optional accidental and everything else {@link StaffElement} holds. The one leaf that answers
 * {@link StaffElement}'s questions as they are asked, where {@link StructuralElement} answers
 * several of them from its type instead.
 *
 * <p>Answers its notehead from its type's {@link NoteheadAppearance}, so a question only a note
 * can answer — which notehead, which stem anchors — is total here and always answered from what
 * the type declares. {@link ElementType#newInstance()} builds one for every type whose
 * appearance is a notehead and for no other, which is what makes the narrowing to that arm
 * sound.
 */
public final class NoteElement extends StaffElement {

    NoteElement(ElementType type) {
        super(type);
    }

    /**
     * @return the notehead this note is drawn with
     */
    public Notehead notehead() {
        return ((NoteheadAppearance) getType().appearance()).notehead();
    }

    /**
     * The notehead, narrowed to the arm a stem attaches to. Ask it only of a note whose type
     * {@link ElementType#isNoteWithStem()}: a whole note has no stemmed notehead to answer.
     *
     * @return this note's notehead as a {@link StemmedNotehead}
     */
    public StemmedNotehead stemmedNotehead() {
        return (StemmedNotehead) notehead();
    }

    @Override
    public NoteElement clone() {
        var copy = new NoteElement(getType());
        copy.copyStateFrom(this);

        return copy;
    }
}
