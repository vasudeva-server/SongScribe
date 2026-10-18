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

/**
 * Ordered, two-phase migration of a parsed song from any legacy {@code .mssw} format to
 * the current one.
 *
 * <p>Registration order in the two lists below <em>is</em> execution order.
 */
final class MigrationPipeline {

    // Minor version at which per-note <lyric> serialization was introduced.
    // Files saved before this version carry a legacy <lyrics> blob that must
    // be imported by LegacyLyricsImporter instead.
    static final int PER_NOTE_LYRIC_VERSION = 6;

    // Stages that run before the Song is assembled, in declaration order. Each is gated on the
    // file's format version.
    static final List<LoadStage<MigrationContext>> PRE_ASSEMBLY = List.of(
        versioned(2, 3, ctx -> FormatMigrator.migrateAnnotationDynamics(ctx.lines)));

    // Stages that run once the song holds its lines. The legacy-lyrics import is the only one,
    // and it runs only for a file that predates per-note lyrics.
    static final List<LoadStage<MigrationContext>> POST_ASSEMBLY = List.of(
        LoadStage.when(
            ctx -> !ctx.lyrics.isBlank() && ctx.isBefore(2, PER_NOTE_LYRIC_VERSION),
            MigrationPipeline::applyLegacyLyrics));

    private MigrationPipeline() {
    }

    private static LoadStage<MigrationContext> versioned(int major, int minor, LoadStage<MigrationContext> step) {
        return LoadStage.when(ctx -> ctx.isBefore(major, minor), step);
    }

    private static void applyLegacyLyrics(MigrationContext ctx) {
        LegacyLyricsImporter.importLegacyLyrics(ctx.song.getLines(), ctx.lyrics);
    }

    static void runPreAssembly(MigrationContext ctx) {
        LoadStage.runAll(PRE_ASSEMBLY, ctx);
    }

    static void runPostAssembly(MigrationContext ctx) {
        LoadStage.runAll(POST_ASSEMBLY, ctx);
    }
}
