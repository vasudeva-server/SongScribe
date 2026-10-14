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

import songscribe.dom.Hairpin;
import songscribe.dom.Hairpin.Kind;

/**
 * Bidirectional mapping between a {@link Hairpin}'s {@link Kind} and its MusicXML
 * {@code <wedge type>} token ({@code "crescendo"}, {@code "diminuendo"}).
 *
 * <p>The wedge {@code type="stop"} token has no SongScribe counterpart and never appears in this
 * map.
 */
final class WedgeTypeMapping {

    // "stop" has no entry: it is a structural marker, not a kind.

    private static final Map<String, Hairpin.Kind> KIND_BY_TOKEN = Map.of(
        MusicXmlTags.WEDGE_CRESCENDO,  Hairpin.Kind.CRESCENDO,
        MusicXmlTags.WEDGE_DIMINUENDO, Hairpin.Kind.DIMINUENDO
    );

    private WedgeTypeMapping() {}

    static String wedgeType(Hairpin hairpin) {
        return switch (hairpin.getKind()) {
            case Kind.CRESCENDO -> MusicXmlTags.WEDGE_CRESCENDO;
            case Kind.DIMINUENDO -> MusicXmlTags.WEDGE_DIMINUENDO;
        };
    }

    /**
     * Returns {@code null} if the token is not a start-type wedge.
     */
    static Hairpin.@Nullable Kind wedgeKind(String wedgeType) {
        return KIND_BY_TOKEN.get(wedgeType);
    }
}
