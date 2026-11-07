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

/**
 * The last phase of a load: every mark the file carried is placed or reported, and the repairs
 * that remove and replace elements have not run.
 */
record PlacedLoad(Song song) {

    /**
     * The terminal stage runs after the placements, so a span on a final barline it removes, or
     * on a last element it replaces, is judged by the span sweep and reported. The grace-host
     * melisma repair precedes the syllabic backfill, which reads the syllabic chain in element
     * order. The breath-mark stage precedes the leading-bar stage, because a breath mark that
     * stands first is removed and can leave a barline first. The final accidental stamp is last,
     * because every removal changes what the chain walk reads.
     */
    void repair() {
        TerminalLoadPass.run(song);

        var lines = song.getLines();
        lines.forEach(Line::repairGraceHostMelismas);
        lines.forEach(Line::backfillSyllabic);

        BreathMarkLoadPass.run(song);
        LeadingBarLoadPass.run(song);
        song.stampAccidentalsAfterParsing();
    }
}
