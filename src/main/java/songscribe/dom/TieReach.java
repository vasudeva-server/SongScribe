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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

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

/** The lines beyond its own in which an edit can change a tie. */
public final class TieReach {

    private TieReach() {
    }

    /**
     * Returns every line, other than the one {@code mutation} names, in which {@code mutation}
     * can change a tie. A tie can cross a line break, so an edit at the edge of one line can
     * change a tie whose other note is in the line beside it.
     *
     * <p>Asked only after {@code mutation} has landed: each position it names is read in its line
     * as that line is then.
     *
     * @return the lines, in no promised order
     */
    public static List<Line> reach(Mutation mutation) {
        var origin = originOf(mutation);

        if (origin == null) {
            return List.of();
        }

        var line = origin.line();
        var firstNonSeparator = line.firstNonSeparatorIndexFrom(0);
        var lastNonSeparator = line.lastNonSeparatorIndexBefore(line.elementCount());
        var reach = new ArrayList<Line>();

        if (!line.isLast() && (lastNonSeparator == null || origin.last() >= lastNonSeparator)) {
            reach.add(line.nextLine());
        }

        if (!line.isFirst() && (firstNonSeparator == null || origin.first() <= firstNonSeparator)) {
            reach.add(line.previousLine());
        }

        return List.copyOf(reach);
    }

    private static @Nullable Origin originOf(Mutation mutation) {
        return switch (mutation) {
            case ElementInsertion insertion -> elementOrigin(insertion.line(), insertion.index());
            case ElementReplacement replacement -> elementOrigin(replacement.line(), replacement.index());
            case ElementDeletion deletion -> elementOrigin(deletion.line(), deletion.range().begin());
            case ElementModification modification -> elementOrigin(modification.line(), modification.index());
            case TieAddition(var line, var anchor) -> tieAnchorOrigin(line, anchor);
            case TieRemoval(var line, var anchor) -> tieAnchorOrigin(line, anchor);
            case SpanAddition(var line, var span) -> beamOrigin(line, span);
            case SpanRemoval(var line, var span) -> beamOrigin(line, span);
            case LineInsertion _, LineDeletion _, LineKeyChange _, LineLayoutChange _, MetadataChange _,
                 FontChange _, LayoutChange _, LyricsChange _ -> null;
        };
    }

    private static Origin elementOrigin(Line line, int position) {
        var beam = line.spanRegistry().findBeamAt(position);

        if (beam == null) {
            return new Origin(line, position, position);
        }

        var range = beam.range();

        return new Origin(line, range.begin(), range.end());
    }

    private static @Nullable Origin tieAnchorOrigin(Line line, NoteElement tieAnchor) {
        if (!line.contains(tieAnchor)) {
            return null;
        }

        var index = line.getElementIndex(tieAnchor);

        return new Origin(line, index, index);
    }

    private static @Nullable Origin beamOrigin(Line line, Span<?> span) {
        if (!(span instanceof Beam beam)) {
            return null;
        }

        var beamAnchor = beam.getAnchorElement();
        var beamEnd = beam.getEndElement();

        if (!line.contains(beamAnchor) || !line.contains(beamEnd)) {
            return null;
        }

        return new Origin(line, line.getElementIndex(beamAnchor), line.getElementIndex(beamEnd));
    }

    private record Origin(Line line, int first, int last) {
    }
}
