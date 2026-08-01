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
package songscribe.io.musicxml;

import java.awt.Component;

import org.jspecify.annotations.Nullable;

/**
 * Bidirectional mapping between a SongScribe text alignment
 * ({@link Component#LEFT_ALIGNMENT} {@code 0.0} / {@link Component#CENTER_ALIGNMENT}
 * {@code 0.5} / {@link Component#RIGHT_ALIGNMENT} {@code 1.0}) and the MusicXML
 * {@code left-center-right} token used for a {@code <words>}/{@code <credit-words>}
 * {@code halign} or {@code justify} attribute.
 *
 * <p>{@code halign} and {@code justify} share the same {@code left-center-right}
 * enumeration, so the one token serves both attributes.
 */
final class TextAlignmentMapping {

    private TextAlignmentMapping() {}

    /**
     * Returns the MusicXML {@code left}/{@code center}/{@code right} token for the
     * given SongScribe {@code xAlignment}. An unrecognised value maps to
     * {@code left} (the model's default alignment).
     */
    static String alignToken(float xAlignment) {
        if (xAlignment == Component.RIGHT_ALIGNMENT) {
            return MusicXmlTags.HALIGN_RIGHT;
        }

        if (xAlignment == Component.CENTER_ALIGNMENT) {
            return MusicXmlTags.HALIGN_CENTER;
        }

        return MusicXmlTags.HALIGN_LEFT;
    }

    /**
     * Returns the SongScribe {@code xAlignment} for the given MusicXML
     * {@code left}/{@code center}/{@code right} token. An absent or unrecognised
     * token maps to {@link Component#LEFT_ALIGNMENT} (the model's default).
     */
    static float xAlignment(@Nullable String alignToken) {
        if (MusicXmlTags.HALIGN_RIGHT.equals(alignToken)) {
            return Component.RIGHT_ALIGNMENT;
        }

        if (MusicXmlTags.HALIGN_CENTER.equals(alignToken)) {
            return Component.CENTER_ALIGNMENT;
        }

        return Component.LEFT_ALIGNMENT;
    }
}
