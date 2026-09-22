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
package songscribe.io;

import songscribe.dom.AccidentalState;
import songscribe.dom.BreathMarkLoadPass;
import songscribe.dom.Song;
import songscribe.dom.TupletLoadPass;

/**
 * What a load changed in the document on its way in, as one value.
 *
 * @param tupletReport         every tuplet the post-parse pass removed or completed, in
 *                             document order; empty when the file's tuplets were taken as
 *                             they stood
 * @param breathMarkReport     every breath mark the post-parse pass removed for not following
 *                             a note or a rest, in document order; empty when every breath
 *                             mark stood where it may
 * @param accidentalsConverted whether retired accidentals were converted
 */
public record LoadMigrations(
    TupletLoadPass.Report tupletReport,
    BreathMarkLoadPass.Report breathMarkReport,
    boolean accidentalsConverted
) {

    /**
     * Whether this load changed nothing in the document.
     *
     * @return {@code true} when no tuplet was removed or completed, no breath mark was removed
     *         and no retired accidental was converted
     */
    public boolean isEmpty() {
        return tupletReport.isEmpty() && breathMarkReport.isEmpty() && !accidentalsConverted;
    }

    /**
     * Runs every post-parse pass over {@code song} and reports what the migrations did,
     * {@code song} having just been parsed and not yet installed.
     *
     * <p>The load pass stamps every note ({@link Song#stampAccidentalsAfterParsing()}) once the
     * tuplet pass has settled which notes the song holds: a note the file writes an accidental on
     * sounds that accidental, a bare note adopts what is in force, and parentheses are ignored.
     * This is the only supported way to run the passes, because the tuplet pass must settle
     * first — a tuplet it drops or completes changes which notes the walk reads. The breath-mark
     * pass reads no tuplet and changes no note, so its place in the order is free; it runs after
     * the tuplet pass so that a removed breath mark's index in the report is its index in the
     * settled line.
     *
     * @param song                 the song to settle, immediately after parsing and before it
     *                             is installed
     * @param accidentalsConverted whether the reader converted retired accidentals while
     *                             parsing
     * @return what the migrations changed
     * @effects every note of {@code song} holds a {@link ChainedAccidental} afterwards
     */
    public static LoadMigrations run(Song song, boolean accidentalsConverted) {
        var tupletReport = TupletLoadPass.run(song);
        var breathMarkReport = BreathMarkLoadPass.run(song);
        song.stampAccidentalsAfterParsing();

        return new LoadMigrations(tupletReport, breathMarkReport, accidentalsConverted);
    }
}
