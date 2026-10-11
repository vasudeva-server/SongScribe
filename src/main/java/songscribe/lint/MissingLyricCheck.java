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

package songscribe.lint;

import java.util.List;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.LyricRun;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;

/**
 * The check of {@link LintRule#MISSING_LYRIC}: in every verse the song carries, every slot is
 * sung on some syllable.
 *
 * <p>A <em>slot</em> is an element a syllable can go on ({@code StaffElement.canBearSyllable}): a
 * note or a grace note, but not the host of a paired grace note, whose syllable is the grace
 * note's. A slot is sung on a syllable when any of these holds for the verse:
 *
 * <ul>
 *   <li>it holds a lyric for the verse, a melisma carrier included;</li>
 *   <li>a lyric span covers it: a melisma, or a word whose next syllable stands on a later note,
 *       runs over it ({@link LyricRun#lyricSpanEnd}). A span crosses a line break and stops at a
 *       repeat;</li>
 *   <li>a tie from the note before it reaches it ({@code NoteElement.incomingTie()}), so it
 *       continues a note already sung.</li>
 * </ul>
 *
 * <p>Every verse is checked, not only the active one: a verse is the song's lyrics in one
 * language, so a translation must have a syllable on every note too, and a finding does not
 * depend on which verse is shown. A song with no lyric in any verse carries no verse and gives no
 * violation.
 *
 * <p>Each slot sung on no syllable gives one violation per verse, with the text
 * {@link Strings#LINT_REASON_LYRIC_MISSING} and the mark {@link LintMark.MissingLyric} naming the
 * slot and the verse.
 */
final class MissingLyricCheck implements LintCheck {

    /**
     * Finds every slot that a verse leaves without a syllable.
     *
     * @param song the song to examine; read only
     * @return the violations in the song order of their slots; on one slot, in ascending verse
     *     order
     * @invariant every violation's mark is a {@link LintMark.MissingLyric} whose verse is a verse
     *     some lyric of the song carries
     * @invariant a slot gets at most one violation for each verse
     */
    @Override
    public List<Violation> check(Song song) {
        var found = new SongOrderedViolations();

        for (var verse : song.verseNumbers()) {
            checkVerse(song, verse, found);
        }

        return found.inSongOrder();
    }

    /**
     * One forward pass over {@code song} for {@code verse}. A span is walked once, where it opens,
     * and the pass resumes after its last covered element, so each element is visited once.
     */
    private static void checkVerse(Song song, int verse, SongOrderedViolations found) {
        var lineStartPosition = 0;
        var openIntoLine = false;

        for (var line : song.getLines()) {
            var index = 0;

            if (openIntoLine) {
                var carried = line.lyricSpanEnd(0, verse);
                index = carried.lastCoveredIndex() + 1;
                openIntoLine = carried.openAtRunEnd();
            }

            while (index < line.elementCount()) {
                var element = line.getElement(index);
                var lyric = element.getLyricForVerse(verse);

                if (lyric != null && LyricRun.opensLyricSpan(lyric)) {
                    var span = line.lyricSpanEnd(index + 1, verse);
                    index = span.lastCoveredIndex() + 1;
                    openIntoLine = span.openAtRunEnd();
                    continue;
                }

                if (lyric == null && isUnsungSlot(line, index)) {
                    var violation = new Violation(Strings.LINT_LYRICS_MISSING,
                                                  Strings.get(Strings.LINT_REASON_LYRIC_MISSING, verse),
                                                  new LintMark.MissingLyric(element, verse));
                    found.add(lineStartPosition + index, violation);
                }

                index++;
            }

            lineStartPosition += line.elementCount();
        }
    }

    /**
     * Whether the element at {@code index} is a slot that no tie reaches, so it needs a syllable
     * of its own.
     */
    private static boolean isUnsungSlot(Line line, int index) {
        return line.canBearSyllableAt(index)
            && !(line.getElement(index) instanceof NoteElement note && note.incomingTie() != null);
    }
}
