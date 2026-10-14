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
 *   <li>The accidental stamp runs after the tuplet and leading-bar passes, because a tuplet or
 *       bar line they remove changes what the chain walk reads.</li>
 *   <li>The leading-bar pass runs after the breath-mark pass, so no later pass sees a bar line
 *       first in the song: a breath mark that stands first is removed, and can leave a bar line
 *       first. It also runs after the tuplet, ending and hairpin passes, since it removes
 *       elements.</li>
 *   <li>The tuplet, ending and hairpin passes run before the breath-mark pass: a pending span
 *       names its endpoints as elements of the line the reader built, and the breath-mark pass
 *       removes elements.</li>
 *   <li>No pass adds or removes a line, so every entry's place stays right.</li>
 *   <li>The reader's entries are recorded last, so the heading of the reader's last entry comes
 *       last in the report, which orders its headings by first entry.</li>
 * </ul>
 */
final class LoadPasses {

    record LoadRun(ParsedSong parsed, ChangeReport.Builder report) {

        Song song() {
            return parsed.song();
        }
    }

    @FunctionalInterface
    interface PendingPass<P> {

        void run(Song song, List<P> pending, ChangeReport.Builder report);
    }

    // List order is execution order; the class comment states why it is this one.
    private static final List<LoadStage<LoadRun>> STAGES = List.of(
        pending(ParsedSong::pendingTuplets, TupletLoadPass::run),
        pending(ParsedSong::pendingEndings, SpanLoadPass::run),
        pending(ParsedSong::pendingHairpins, SpanLoadPass::run),
        run -> BreathMarkLoadPass.run(run.song(), run.report()),
        run -> LeadingBarLoadPass.run(run.song(), run.report()),
        run -> run.song().stampAccidentalsAfterParsing(),
        run -> run.parsed().readerEntries().forEach(run.report()::record));

    private LoadPasses() {
    }

    /**
     * Mutation tracking is suspended once around the whole run, so the passes' additions and
     * removals do not land in the undo history. The song's modified flag is left alone; the
     * caller marks it from the report once the song is installed.
     */
    static ChangeReport run(ParsedSong parsed) {
        var run = new LoadRun(parsed, new ChangeReport.Builder(parsed.song()));

        run.song().withoutMutationTracking(() -> LoadStage.runAll(STAGES, run));

        return run.report().build();
    }

    private static <P> LoadStage<LoadRun> pending(Function<ParsedSong, List<P>> input, PendingPass<P> pass) {
        return run -> pass.run(run.song(), input.apply(run.parsed()), run.report());
    }
}
