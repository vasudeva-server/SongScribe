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

import org.audiveris.proxymusic.WedgeType;
import org.jspecify.annotations.Nullable;

import songscribe.dom.Hairpin;
import songscribe.dom.Hairpin.Kind;

/**
 * Bidirectional mapping between a {@link Hairpin}'s {@link Kind} and its MusicXML
 * {@code <wedge type>}.
 */
final class WedgeTypeMapping {

    private WedgeTypeMapping() {}

    static WedgeType wedgeType(Hairpin hairpin) {
        return switch (hairpin.getKind()) {
            case Kind.CRESCENDO -> WedgeType.CRESCENDO;
            case Kind.DIMINUENDO -> WedgeType.DIMINUENDO;
        };
    }

    /** {@code null} for a type that does not start a hairpin. */
    static Hairpin.@Nullable Kind wedgeKind(WedgeType wedgeType) {
        return switch (wedgeType) {
            case CRESCENDO -> Kind.CRESCENDO;
            case DIMINUENDO -> Kind.DIMINUENDO;
            case STOP, CONTINUE -> null;
        };
    }
}
