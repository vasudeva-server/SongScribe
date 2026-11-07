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

import java.util.List;

import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;

/**
 * The song-start rule ({@link SongStart}), kept by every edit: a deletion that leaves a barline
 * first in the song removes that barline too, with the key change behind it.
 * <p>
 * The rule judges the song across lines, because a deletion that empties the first line, or removes
 * it, passes the start of the song to the next line.
 */
final class SongStartRule implements ModificationRule {

    /**
     * {@inheritDoc}
     *
     * @effects when {@code batch} holds a deletion, removes the barlines that stand first in the
     *     song, as {@link SongStart#removeLeadingBars} states, into the same bracket as the
     *     deletion
     */
    @Override
    public void apply(Song song, List<Mutation> batch, ChangeReport.Builder report) {
        if (batch.stream().anyMatch(SongStartRule::canExposeLeadingBar)) {
            SongStart.removeLeadingBars(song);
        }
    }

    /**
     * No default arm, so a new mutation type does not compile until it is decided here.
     */
    private static boolean canExposeLeadingBar(Mutation mutation) {
        return switch (mutation) {
            case ElementDeletion deletion -> true;
            case LineDeletion deletion -> true;
            case ElementInsertion insertion -> false;
            case ElementReplacement replacement -> false;
            case ElementModification modification -> false;
            case LineInsertion insertion -> false;
            case LineKeyChange change -> false;
            case LineLayoutChange change -> false;
            case SpanAddition addition -> false;
            case SpanRemoval removal -> false;
            case TieAddition addition -> false;
            case TieRemoval removal -> false;
            case MetadataChange change -> false;
            case FontChange change -> false;
            case LayoutChange change -> false;
            case LyricsChange change -> false;
        };
    }
}
