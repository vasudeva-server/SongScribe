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
import songscribe.message.mutation.Mutation;

/**
 * The section rule of {@link Ending}: an ending's split closes an open repeat section
 * ({@link Ending#closesOpenSection}). An ending whose split an edit leaves closing no open
 * section is removed.
 * <p>
 * The rule judges every ending in the song, not only those on the lines the batch touched.
 * An edit on one line can open or close the section of an ending any number of lines later,
 * and a line inserted or deleted goes through no span sweep, so no smaller set of lines holds
 * every ending an edit can break.
 */
final class EndingSectionRule implements ModificationRule {

    /**
     * {@inheritDoc}
     *
     * @effects removes, through {@link Span#removeReported} with
     *     {@link Strings#DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION}, every ending in the song
     *     whose split closes no open repeat section; {@code batch} is not read
     */
    @Override
    public void apply(Song song, List<Mutation> batch, ChangeReport report) {
        for (var line : song.getLines()) {
            for (var ending : List.copyOf(line.spanRegistry().findEndings())) {
                if (!Ending.closesOpenSection(line, ending.getSplitIndex())) {
                    ending.removeReported(line, Strings.DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION);
                }
            }
        }
    }
}
