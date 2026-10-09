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
import java.util.function.Function;

import songscribe.util.LoadStage;

/**
 * The passes that settle a freshly read song, in the order they run. {@link ParsedSong#settle()}
 * is what runs them.
 *
 * <p>The order is fixed by what each pass reads:
 * <ul>
 *   <li>The accidental stamp runs after the tuplet pass, because a tuplet it drops or adds changes
 *       which notes the chain walk reads.</li>
 *   <li>The ending and hairpin passes run before the breath-mark pass. The breath-mark pass removes
 *       elements, and each pass reports the indices of the line as it saw it, so an ending or a
 *       hairpin judged after a removal would report indices of a line the reader never built. No
 *       ending or hairpin the rule accepts has a breath mark as an endpoint, so the breath-mark
 *       pass cannot break one.</li>
 *   <li>The breath-mark pass runs after the tuplet pass, so that a removed breath mark's index in
 *       the report is its index in the settled line. A removal can remove a tuplet that holds the
 *       mark, and that removal is not reported, because tracking is suspended.</li>
 *   <li>The reader's entries are recorded last, so the heading of the reader's last entry — the
 *       accidentals-converted entry, when the reader made one — comes last in the report, which
 *       orders its headings by first entry.</li>
 * </ul>
 */
final class LoadPasses {

    /**
     * One run of the passes: the song being settled, and the report every pass records into.
     *
     * @param parsed what the reader handed over
     * @param report the load's report
     */
    record LoadRun(ParsedSong parsed, ChangeReport.Builder report) {

        Song song() {
            return parsed.song();
        }
    }

    /**
     * A pass over the pending spans of one kind that a reader found.
     *
     * @param <P> the kind of pending span
     */
    @FunctionalInterface
    interface PendingPass<P> {

        /**
         * Adds to {@code song} each of {@code pending} that can stand, and records into
         * {@code report} what it did.
         *
         * @param song    the song being settled
         * @param pending the pending spans of this kind the reader found in {@code song}
         * @param report  the load's report
         */
        void run(Song song, List<P> pending, ChangeReport.Builder report);
    }

    // List order is execution order; the class comment states why it is this one.
    private static final List<LoadStage<LoadRun>> STAGES = List.of(
        pending(ParsedSong::pendingTuplets, TupletLoadPass::run),
        pending(ParsedSong::pendingEndings, SpanLoadPass::run),
        pending(ParsedSong::pendingHairpins, SpanLoadPass::run),
        run -> BreathMarkLoadPass.run(run.song(), run.report()),
        run -> run.song().stampAccidentalsAfterParsing(),
        run -> run.parsed().readerEntries().forEach(run.report()::record));

    private LoadPasses() {
    }

    /**
     * Runs every stage over {@code parsed}, with mutation tracking suspended once around the whole
     * run: outside suspension the passes' additions and removals would land in the undo history,
     * and a freshly opened song could be undone back to a song without its spans. The song's
     * modified flag is left alone; the caller marks it from the report once the song is installed.
     *
     * @param parsed what the reader handed over
     * @return the load's report, as {@link ParsedSong#settle()} states it
     * @effects as {@link ParsedSong#settle()} states them
     */
    static ChangeReport run(ParsedSong parsed) {
        var run = new LoadRun(parsed, new ChangeReport.Builder());

        run.song().withoutMutationTracking(() -> LoadStage.runAll(STAGES, run));

        return run.report().build();
    }

    /** The stage that runs {@code pass} over the pending spans {@code input} selects. */
    private static <P> LoadStage<LoadRun> pending(Function<ParsedSong, List<P>> input, PendingPass<P> pass) {
        return run -> pass.run(run.song(), input.apply(run.parsed()), run.report());
    }
}
