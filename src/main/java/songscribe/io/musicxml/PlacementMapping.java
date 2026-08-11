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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Annotation;

/**
 * Bidirectional mapping between {@link Annotation.Placement} and the MusicXML
 * {@code placement} token a {@code <direction>} carries ({@code "above"},
 * {@code "below"}).
 *
 * <p>The schema's {@code above-below} type has exactly these two values, so the
 * write direction is total. The read direction is not: {@code placement} is
 * optional, and a foreign file may carry a token this reader does not know. Both
 * come back {@code null}, which is how {@link MeasureMapper} decides a
 * {@code <direction>} is not one of ours.
 */
final class PlacementMapping {

    private static final Map<String, Annotation.Placement> PLACEMENT_BY_TOKEN = Map.of(
        MusicXmlTags.PLACEMENT_ABOVE, Annotation.Placement.ABOVE,
        MusicXmlTags.PLACEMENT_BELOW, Annotation.Placement.BELOW
    );

    private PlacementMapping() {}

    /** The MusicXML {@code placement} token for {@code placement}. */
    static String placementToken(Annotation.Placement placement) {
        return switch (placement) {
            case ABOVE -> MusicXmlTags.PLACEMENT_ABOVE;
            case BELOW -> MusicXmlTags.PLACEMENT_BELOW;
        };
    }

    /**
     * The {@link Annotation.Placement} for a MusicXML {@code placement} token, or
     * {@code null} when the token is absent or is not one this reader recognises.
     */
    static Annotation.@Nullable Placement placementFor(@Nullable String token) {
        return token == null ? null : PLACEMENT_BY_TOKEN.get(token);
    }
}
