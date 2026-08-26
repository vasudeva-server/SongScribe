/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/** Shared factory methods for whole {@link Song}s used across test classes. */
public final class SongFactory {

    private SongFactory() {}

    /**
     * Adds a note to {@code line}, then a signature establishing {@code key} behind the barline
     * its position invariant requires, then another note — so the line holds a mid-line key
     * change with a note either side of it.
     *
     * @param line the line to populate
     * @param key the key the signature establishes
     */
    public static void notesAroundSignature(Line line, Key key) {
        line.addElement(crotchet());
        line.addElement(singleBarline());
        line.addElement(keyChange(key));
        line.addElement(crotchet());
    }

    /** Populates one line of a song under construction. */
    @FunctionalInterface
    public interface LineBuilder {
        void build(Line line);
    }

    /**
     * A song of the lines {@code builders} populate, with the inherited-key chain settled across
     * all of them.
     *
     * <p>The default initial line {@link Song#Song()} installs is replaced by the caller's lines.
     * Each builder's elements are added before its line is inserted into the song, so no builder
     * runs with its line as the song's last, and the terminal-slot auto-maintenance does not
     * reorder elements during construction.
     *
     * <p>Only the inheritance chain is settled, not the whole of {@code settleKeysAfterParsing} —
     * a caller may deliberately build a line whose key change a later edit is meant to strand, and
     * the load-time repair would take it away before the test could use it. A builder that sets no
     * key leaves its line in the key the line before it ends in.
     *
     * @param builders one per line, in song order
     * @return the song
     */
    public static Song buildSong(LineBuilder... builders) {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            song.removeLine(0);

            for (var builder : builders) {
                var line = new Line(song);
                builder.build(line);
                song.addLine(line);
            }

            song.rebuildInheritedKeysAfterParsing();
        });

        return song;
    }
}
