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

import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Hairpin;
import songscribe.dom.Hairpin.Kind;

/**
 * Bidirectional mapping between SongScribe {@link Hairpin} subclasses
 * ({@link Crescendo}, {@link Diminuendo}) and their MusicXML
 * {@code <wedge type>} token ({@code "crescendo"}, {@code "diminuendo"}).
 *
 * <p>The wedge {@code type="stop"} token has no SongScribe counterpart; it is
 * emitted at the end note using {@link MusicXmlTags#TYPE_STOP} and never appears
 * in this map.
 */
final class WedgeTypeMapping {

    // -------------------------------------------------------------------------
    // Reverse map: wedge type token → Hairpin.Kind to open.
    // "stop" is intentionally absent — it is a structural marker, not a kind.
    // -------------------------------------------------------------------------

    private static final Map<String, Hairpin.Kind> KIND_BY_TOKEN = Map.of(
        MusicXmlTags.WEDGE_CRESCENDO,  Hairpin.Kind.CRESCENDO,
        MusicXmlTags.WEDGE_DIMINUENDO, Hairpin.Kind.DIMINUENDO
    );

    private WedgeTypeMapping() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML {@code <wedge type>} token for the given {@link Hairpin}.
     *
     * <p>{@link Crescendo} maps to {@code "crescendo"};
     * {@link Diminuendo} maps to {@code "diminuendo"}.
     */
    static String wedgeType(Hairpin hairpin) {
        return switch (hairpin.getKind()) {
            case Kind.CRESCENDO -> MusicXmlTags.WEDGE_CRESCENDO;
            case Kind.DIMINUENDO -> MusicXmlTags.WEDGE_DIMINUENDO;
        };
    }

    /**
     * Returns the {@link Hairpin.Kind} for the given MusicXML {@code <wedge type>}
     * token, or {@code null} if the token is not a start-type wedge
     * (e.g. {@code "stop"} has no corresponding kind).
     */
    static Hairpin.@Nullable Kind wedgeKind(String wedgeType) {
        return KIND_BY_TOKEN.get(wedgeType);
    }
}
