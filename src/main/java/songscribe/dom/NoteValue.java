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
 * measured in.
 *
 * <p>{@link #note()} and {@link #rest()} are the two inverses of
 * {@link ElementType#noteValue()}: {@code v.note().noteValue()} and {@code v.rest().noteValue()}
 * are both {@code v}. The MusicXML mapping and the tuplet validator rely on that round trip.
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

    public int ticks() {
        return ticks;
    }

    public String labelKey() {
        return labelKey;
    }

    /**
     * @return {@code null} for a whole, half or quarter, which carry none
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

    public boolean hasFlag() {
        return flagCount() > 0;
    }

    public int flagCount() {
        return switch (this) {
            case WHOLE, HALF, QUARTER -> 0;
            case EIGHTH -> 1;
            case SIXTEENTH -> 2;
            case THIRTY_SECOND -> 3;
        };
    }

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
