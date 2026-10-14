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

import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.util.LoadStage;

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

    // Minimum linewidth value that can only be a pixel measurement, not staff spaces.
    // Used to detect v2.1–2.2 files written by a buggy writer that stored linewidth
    // as px floats instead of ss despite the format declaring ss storage from v2.1.
    // Valid ss values are well below this; observed buggy px values are 700+.
    static final double LEGACY_LINE_WIDTH_PX_MIN = 400.0;

    // Stages that run before the Song is assembled, in declaration order. Each is gated on the
    // file's format version, except the line-width fix, which is gated on the value itself.
    static final List<LoadStage<MigrationContext>> PRE_ASSEMBLY = List.of(
        versioned(2, 0, ctx -> FormatMigrator.migrate(ctx.lines, ctx.legacyLineOffsets, 1)),
        versioned(2, 3, ctx -> FormatMigrator.migrateAnnotationDynamics(ctx.lines)),
        versioned(2, 4, ctx -> FormatMigrator.migrateFinalTerminal(ctx.lines)),
        versioned(2, 1, FormatMigrator::migratePixelsToStaffSpace),
        LoadStage.when(
            ctx -> ctx.majorVersion == 2 && ctx.minorVersion < 3 && ctx.lineWidthSs >= LEGACY_LINE_WIDTH_PX_MIN,
            ctx -> ctx.lineWidthSs /= DocumentScale.PIXELS_PER_STAFF_SPACE));

    // Stages that run once ctx.song is set. Only the legacy-lyrics import is conditional; the
    // grace-host melisma repair and syllabic backfill run for every file.
    static final List<LoadStage<MigrationContext>> POST_ASSEMBLY = List.of(
        LoadStage.when(
            ctx -> !ctx.lyrics.isBlank() && ctx.isBefore(2, PER_NOTE_LYRIC_VERSION),
            MigrationPipeline::applyLegacyLyrics),
        MigrationPipeline::applyGraceHostMelismaRepair,
        MigrationPipeline::applySyllabicBackfill);

    private MigrationPipeline() {
    }

    private static LoadStage<MigrationContext> versioned(int major, int minor, LoadStage<MigrationContext> step) {
        return LoadStage.when(ctx -> ctx.isBefore(major, minor), step);
    }

    private static void applyLegacyLyrics(MigrationContext ctx) {
        LegacyLyricsImporter.importLegacyLyrics(requireSong(ctx).getLines(), ctx.lyrics);
    }

    // Runs before the syllabic backfill, which reads the syllabic chain in element order and so
    // must see syllables at their final positions.
    private static void applyGraceHostMelismaRepair(MigrationContext ctx) {
        requireSong(ctx).repairGraceHostMelismasAfterParsing();
    }

    // Normalize stored syllabic values to match relation-chain derivation. Required
    // for legacy files that may carry only locally-consistent values from the read
    // path or no <syllabic> at all (LegacyLyricsImporter / pre-syllabic XML).
    private static void applySyllabicBackfill(MigrationContext ctx) {
        requireSong(ctx).getLines().forEach(Line::backfillSyllabic);
    }

    // Post-assembly stages run only after getSong sets ctx.song; a null here is a
    // programming error (the pipeline was driven out of order), not a bad input.
    private static Song requireSong(MigrationContext ctx) {
        if (ctx.song == null) {
            throw new IllegalStateException("post-assembly stage ran before the song was assembled");
        }

        return ctx.song;
    }

    static void runPreAssembly(MigrationContext ctx) {
        LoadStage.runAll(PRE_ASSEMBLY, ctx);
    }

    static void runPostAssembly(MigrationContext ctx) {
        LoadStage.runAll(POST_ASSEMBLY, ctx);
    }
}
