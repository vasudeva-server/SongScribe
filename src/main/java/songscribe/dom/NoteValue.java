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

import songscribe.Strings;
import songscribe.smufl.SMuFLGlyph;

import static songscribe.midi.MidiSequenceBuilder.PPQ;

/**
 * A note value — whole through thirty-second — the closed domain a note or a rest is
 * measured in. Each value knows its length in ticks, the word that names it, the flag a
 * note of it carries, and the note and rest types that carry it.
 *
 * <p>{@link #note()} and {@link #rest()} are the two inverses of
 * {@link ElementType#noteValue()}: {@code v.note().noteValue()} and
 * {@code v.rest().noteValue()} are both {@code v}, so a caller can walk from a value to a type
 * and back without losing which value it started from. The MusicXML mapping and the tuplet
 * validator both rely on that round trip.
 */
public enum NoteValue {
    WHOLE(PPQ * 4, Strings.LABEL_NOTE_VALUE_WHOLE),
    HALF(PPQ * 2, Strings.LABEL_NOTE_VALUE_HALF),
    QUARTER(PPQ, Strings.LABEL_NOTE_VALUE_QUARTER),
    EIGHTH(PPQ / 2, Strings.LABEL_NOTE_VALUE_EIGHTH),
    SIXTEENTH(PPQ / 4, Strings.LABEL_NOTE_VALUE_SIXTEENTH),
    THIRTY_SECOND(PPQ / 8, Strings.LABEL_NOTE_VALUE_THIRTY_SECOND);

    private final int ticks;
    private final String labelKey;

    NoteValue(int ticks, String labelKey) {
        this.ticks = ticks;
        this.labelKey = labelKey;
    }

    /**
     * @return this value's length in ticks, at {@link songscribe.midi.MidiSequenceBuilder#PPQ}
     *     ticks per quarter
     */
    public int ticks() {
        return ticks;
    }

    /**
     * @return the {@link Strings} key of the lowercase word naming this value
     */
    public String labelKey() {
        return labelKey;
    }

    /**
     * The flag a stemmed note of this value carries, for the given stem direction.
     *
     * @param direction {@code UP} for a flag on the right of the stem; {@code DOWN} for one on
     *                  the left
     * @return the flag glyph, or {@code null} for a whole, half or quarter, which carry none
     */
    public @Nullable SMuFLGlyph flagGlyph(Direction direction) {
        return switch (this) {
            case EIGHTH -> direction.isUp() ? SMuFLGlyph.FLAG_8TH_UP : SMuFLGlyph.FLAG_8TH_DOWN;
            case SIXTEENTH -> direction.isUp() ? SMuFLGlyph.FLAG_16TH_UP : SMuFLGlyph.FLAG_16TH_DOWN;
            case THIRTY_SECOND ->
                direction.isUp() ? SMuFLGlyph.FLAG_32ND_UP : SMuFLGlyph.FLAG_32ND_DOWN;
            case WHOLE, HALF, QUARTER -> null;
        };
    }

    /**
     * @return whether a note of this value carries a flag — an eighth or shorter
     */
    public boolean hasFlag() {
        return flagCount() > 0;
    }

    /**
     * @return the number of flags a stemmed note of this value carries, and the number of beams
     *     it replaces when the note is beamed instead: 0 for a whole, half or quarter, 1 for an
     *     eighth, 2 for a sixteenth, 3 for a thirty-second
     */
    public int flagCount() {
        return switch (this) {
            case WHOLE, HALF, QUARTER -> 0;
            case EIGHTH -> 1;
            case SIXTEENTH -> 2;
            case THIRTY_SECOND -> 3;
        };
    }

    /**
     * @return the pitched note type of this value
     */
    public ElementType note() {
        return switch (this) {
            case WHOLE -> ElementType.SEMIBREVE;
            case HALF -> ElementType.MINIM;
            case QUARTER -> ElementType.CROTCHET;
            case EIGHTH -> ElementType.QUAVER;
            case SIXTEENTH -> ElementType.SEMIQUAVER;
            case THIRTY_SECOND -> ElementType.DEMI_SEMIQUAVER;
        };
    }

    /**
     * @return the rest type of this value
     */
    public ElementType rest() {
        return switch (this) {
            case WHOLE -> ElementType.SEMIBREVE_REST;
            case HALF -> ElementType.MINIM_REST;
            case QUARTER -> ElementType.CROTCHET_REST;
            case EIGHTH -> ElementType.QUAVER_REST;
            case SIXTEENTH -> ElementType.SEMIQUAVER_REST;
            case THIRTY_SECOND -> ElementType.DEMI_SEMIQUAVER_REST;
        };
    }
}
