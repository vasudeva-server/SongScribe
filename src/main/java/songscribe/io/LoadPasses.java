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

import java.util.List;

import songscribe.Strings;
import songscribe.dom.BreathMarkLoadPass;
import songscribe.dom.ChangeReport;
import songscribe.dom.ChainedAccidental;
import songscribe.dom.EndingLoadPass;
import songscribe.dom.HairpinLoadPass;
import songscribe.dom.PendingEnding;
import songscribe.dom.PendingHairpin;
import songscribe.dom.PendingTuplet;
import songscribe.dom.ReportedChange;
import songscribe.dom.Song;
import songscribe.dom.TupletLoadPass;

/**
 * Runs the post-parse passes that settle a freshly loaded song, and reports what they changed.
 */
public final class LoadPasses {

    private LoadPasses() {
    }

    /**
     * What a reader hands the load passes along with the song it parsed.
     *
     * @param pendingTuplets       the tuplets the reader found in the song, as
     *                             {@link TupletLoadPass#run} requires them
     * @param pendingEndings       the endings the reader found in the song, as
     *                             {@link EndingLoadPass#run} requires them
     * @param pendingHairpins      the hairpins the reader found in the song, as
     *                             {@link HairpinLoadPass#run} requires them
     * @param accidentalsConverted whether the reader converted retired accidentals while
     *                             parsing
     */
    public record Input(
        List<PendingTuplet> pendingTuplets,
        List<PendingEnding> pendingEndings,
        List<PendingHairpin> pendingHairpins,
        boolean accidentalsConverted
    ) {
    }

    /**
     * Runs every post-parse pass over {@code song} and reports what the passes did,
     * {@code song} having just been parsed and not yet installed.
     *
     * <p>The passes run in this order: tuplets, endings, hairpins, breath marks, then the
     * accidental stamp. This is the only supported way to run them.
     *
     * <p>The load pass stamps every note ({@link Song#stampAccidentalsAfterParsing()}) once the
     * tuplet pass has settled which notes the song holds: a note the file writes an accidental on
     * sounds that accidental, a bare note adopts what is in force, and parentheses are ignored.
     * The tuplet pass must settle first — a tuplet it drops or adds changes which notes the walk
     * reads.
     *
     * <p>The ending and hairpin passes run before the breath-mark pass. The breath-mark pass
     * removes elements, and each pass reports the indices of the line as it saw it, so an ending
     * or a hairpin judged after a removal would report indices of a line the reader never built.
     * No ending or hairpin the rule accepts has a breath mark as an endpoint, so the breath-mark
     * pass cannot break one. The breath-mark pass reads no tuplet and changes no note; it runs
     * after the tuplet pass so that a removed breath mark's index in the report is its index in
     * the settled line.
     *
     * @param song  the song to settle, immediately after parsing and before it is installed
     * @param input what the reader found in {@code song} beside its elements
     * @return what the passes changed; empty when the load changed nothing
     * @effects adds to {@code song} every pending tuplet the tuplet pass does not drop, every
     *     pending ending the ending pass does not drop and every pending hairpin the hairpin pass
     *     does not drop; every note of {@code song} holds a {@link ChainedAccidental} afterwards
     */
    public static ChangeReport run(Song song, Input input) {
        var report = new ChangeReport();

        TupletLoadPass.run(song, input.pendingTuplets(), report);
        EndingLoadPass.run(song, input.pendingEndings(), report);
        HairpinLoadPass.run(song, input.pendingHairpins(), report);
        BreathMarkLoadPass.run(song, report);
        song.stampAccidentalsAfterParsing();

        if (input.accidentalsConverted()) {
            report.record(new ReportedChange(
                Strings.DIALOG_REPORT_ACCIDENTALS_CONVERTED,
                Strings.get(Strings.DIALOG_REPORT_ACCIDENTALS_DETAIL),
                null));
        }

        return report;
    }
}
