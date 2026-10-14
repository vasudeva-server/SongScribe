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

package songscribe.message.mutation;

/**
 * One recorded change to a song. Undo replays a batch in reverse, so the order in which records
 * are emitted is part of their meaning.
 *
 * <p>A record that removes state depending on an element is emitted before the change that
 * prompted it, so undo restores the element first and the dependent state finds what it points
 * at. The removals the modification rules make at the close of an edit are the exception, and
 * are emitted after the changes that broke them, because each judged the final state. A record
 * for terminal maintenance of another line is emitted after the line insertion or deletion that
 * prompted it, so undo handles it before the line operation.
 */
public sealed interface Mutation
    permits ElementInsertion, ElementDeletion, ElementModification, ElementReplacement,
            LineInsertion, LineDeletion,
            LineKeyChange, LineLayoutChange,
            SpanAddition,
            SpanRemoval,
            TieAddition, TieRemoval,
            MetadataChange, FontChange, LayoutChange, LyricsChange {
}
