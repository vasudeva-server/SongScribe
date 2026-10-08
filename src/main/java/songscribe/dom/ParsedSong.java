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

/**
 * What a file reader hands over: the song it read, the spans it found but did not build, and the
 * report entries it made. Both readers return one, and {@link #settle()} turns it into a loaded
 * song.
 *
 * @param song            the song as the reader read it, not yet installed
 * @param pendingTuplets  the tuplets the reader found in {@code song}, in staff-space units. Each
 *                        one's anchor and end sit in the same line of {@code song}, the end at or
 *                        after the anchor, and no two overlap
 * @param pendingEndings  the endings the reader found in {@code song}, in staff-space units. Each
 *                        one's anchor sits in a line of {@code song}, and its end sits in the same
 *                        line at or after the anchor
 * @param pendingHairpins the hairpins the reader found in {@code song}, in staff-space units, under
 *                        the same condition as {@code pendingEndings}
 * @param readerEntries   the report entries the reader made, in the order it made them
 */
public record ParsedSong(
    Song song,
    List<PendingTuplet> pendingTuplets,
    List<PendingEnding> pendingEndings,
    List<PendingHairpin> pendingHairpins,
    List<ReportedChange> readerEntries
) {

    public ParsedSong {
        pendingTuplets = List.copyOf(pendingTuplets);
        pendingEndings = List.copyOf(pendingEndings);
        pendingHairpins = List.copyOf(pendingHairpins);
        readerEntries = List.copyOf(readerEntries);
    }

    /**
     * Returns the entry a reader makes when it converted a retired accidental to the accidental
     * that sounds the same. One entry stands for every conversion in the file, and a reader adds
     * it after every other entry it makes.
     *
     * @return the entry under {@link Strings#DIALOG_REPORT_ACCIDENTALS_CONVERTED}, with no location
     */
    public static ReportedChange accidentalsConvertedEntry() {
        return new ReportedChange(
            Strings.DIALOG_REPORT_ACCIDENTALS_CONVERTED,
            Strings.get(Strings.DIALOG_REPORT_ACCIDENTALS_DETAIL),
            null);
    }

    /**
     * Runs every load pass over {@link #song}, and reports what the load changed. This is the only
     * way to run the load passes, and it runs them once, immediately after the read and before the
     * song is installed.
     *
     * <p>The passes run in this order, with mutation tracking suspended throughout: tuplets,
     * endings, hairpins, breath marks, then the accidental stamp. The reader's entries are recorded
     * after every pass, so the heading the reader's last entry stands under comes last in the
     * report.
     *
     * @return what the load changed: every entry the passes made, then {@link #readerEntries};
     *     empty when the load changed nothing. A non-empty report means the song differs from the
     *     file, so the caller marks the song modified once it is installed
     * @effects adds to {@link #song} every pending tuplet, ending and hairpin its pass does not
     *     drop; removes every breath mark that does not follow a pitched note or a rest, and with
     *     it any tuplet that held the mark, a removal the report does not mention; every note of
     *     {@link #song} holds a {@link ChainedAccidental} afterwards. Nothing is recorded for undo,
     *     no notification is posted and the song's modified flag is left alone
     */
    public ChangeReport settle() {
        return LoadPasses.run(this);
    }
}
