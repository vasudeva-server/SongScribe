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

    /**
     * The two hairpin kinds a start {@code <wedge>} can open. Used by the reader
     * as a binding tag while a hairpin's end note is awaited — a plain two-value
     * tag rather than a {@code Class} token, since the hairpin is constructed
     * directly ({@code new Crescendo}/{@code new Diminuendo}), never reflectively.
     */
    enum WedgeKind {
        CRESCENDO,
        DIMINUENDO
    }

    // -------------------------------------------------------------------------
    // Reverse map: wedge type token → WedgeKind to open.
    // "stop" is intentionally absent — it is a structural marker, not a kind.
    // -------------------------------------------------------------------------

    private static final Map<String, WedgeKind> KIND_BY_TOKEN = Map.of(
        MusicXmlTags.WEDGE_CRESCENDO,  WedgeKind.CRESCENDO,
        MusicXmlTags.WEDGE_DIMINUENDO, WedgeKind.DIMINUENDO
    );

    private WedgeTypeMapping() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML {@code <wedge type>} token for the given {@link Hairpin},
     * or {@code null} if the hairpin type is not recognised.
     *
     * <p>{@link Crescendo} maps to {@code "crescendo"};
     * {@link Diminuendo} maps to {@code "diminuendo"}.
     */
    @Nullable
    static String wedgeType(Hairpin hairpin) {
        if (hairpin instanceof Crescendo) {
            return MusicXmlTags.WEDGE_CRESCENDO;
        }

        if (hairpin instanceof Diminuendo) {
            return MusicXmlTags.WEDGE_DIMINUENDO;
        }

        return null;
    }

    /**
     * Returns the {@link WedgeKind} for the given MusicXML {@code <wedge type>}
     * token, or {@code null} if the token is not a start-type wedge
     * (e.g. {@code "stop"} has no corresponding kind).
     */
    @Nullable
    static WedgeKind wedgeKind(String wedgeType) {
        return KIND_BY_TOKEN.get(wedgeType);
    }
}
