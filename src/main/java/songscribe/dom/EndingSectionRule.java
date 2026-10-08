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

import songscribe.Strings;
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
 * The section rule of {@link Ending}: an ending's split closes an open repeat section
 * ({@link Ending#closesOpenSection}). An ending whose split an edit leaves closing no open
 * section is removed.
 * <p>
 * The rule judges every ending in the song, not only those on the lines the batch touched.
 * An edit on one line can open or close the section of an ending any number of lines later,
 * and a line inserted or deleted goes through no span sweep, so no smaller set of lines holds
 * every ending an edit can break.
 * <p>
 * The song is judged only when the batch holds a mutation that adds, removes, replaces or
 * changes an element, or that inserts or deletes a line: those are the mutations that can open
 * or close a repeat section.
 */
final class EndingSectionRule implements ModificationRule {

    /**
     * {@inheritDoc}
     *
     * @effects when {@code batch} holds a mutation that can change a repeat section, removes,
     *     through {@link Span#removeReported} with
     *     {@link Strings#DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION}, every ending in the song
     *     whose split closes no open repeat section
     */
    @Override
    public void apply(Song song, List<Mutation> batch, ChangeReport report) {
        if (batch.stream().noneMatch(EndingSectionRule::canChangeSections)) {
            return;
        }

        for (var line : song.getLines()) {
            for (var ending : List.copyOf(line.spanRegistry().findEndings())) {
                if (!Ending.closesOpenSection(line, ending.getSplitIndex())) {
                    ending.removeReported(Strings.DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION);
                }
            }
        }
    }

    /**
     * Whether {@code mutation} can open or close a repeat section. The switch is exhaustive over
     * {@link Mutation} with no default arm, so a new mutation type does not compile until it is
     * decided here.
     */
    private static boolean canChangeSections(Mutation mutation) {
        return switch (mutation) {
            case ElementInsertion insertion -> true;
            case ElementDeletion deletion -> true;
            case ElementReplacement replacement -> true;
            case ElementModification modification -> true;
            case LineInsertion insertion -> true;
            case LineDeletion deletion -> true;
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
