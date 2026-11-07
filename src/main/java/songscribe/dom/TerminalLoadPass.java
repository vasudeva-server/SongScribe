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

import songscribe.Strings;

/**
 * Gives a loaded song the terminal an edited song keeps: a final double barline stands only as
 * the song's last element, and the last element is a valid terminal.
 */
final class TerminalLoadPass {

    private TerminalLoadPass() {
    }

    /**
     * @param song the song being loaded
     * @invariant afterwards {@code song} holds no {@link ElementType#FINAL_DOUBLE_BARLINE} other
     *     than its last element, and when it has a line, its last element is a valid terminal
     *     ({@link ElementType#isValidSongTerminal()})
     * @effects records to the load's report, in document order, one entry under
     *     {@link ChangeHeading#FINAL_BARLINES_REMOVED} with
     *     {@link Strings#REPORT_REASON_FINAL_BARLINE_NOT_LAST} on the line of each final double bar
     *     line removed, before the entries of the spans its removal takes
     * @effects records no entry for making the last element a valid terminal; a span that change
     *     takes is reported by its own entry
     */
    static void run(Song song) {
        for (var line : song.getLines()) {
            var i = 0;

            while (i < line.elementCount()) {
                if (line.getElement(i).getType() == ElementType.FINAL_DOUBLE_BARLINE
                    && !isSongLastElement(line, i)) {
                    song.report(new RecordedChange(
                        ChangeHeading.FINAL_BARLINES_REMOVED,
                        Strings.get(Strings.REPORT_REASON_FINAL_BARLINE_NOT_LAST),
                        ChangeLines.on(line)));
                    line.removeElement(i);
                } else {
                    i++;
                }
            }
        }

        song.installTerminalAfterParsing();
    }

    private static boolean isSongLastElement(Line line, int index) {
        return line.isLast() && index == line.elementCount() - 1;
    }
}
