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
import java.util.function.Consumer;

import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;

/**
 * Ordered, two-phase migration of a parsed song from any legacy {@code .mssw} format to
 * the current one. Pre-assembly stages operate on the parsed {@link Line}s and the
 * song-level scalars carried by {@link MigrationContext}; post-assembly stages operate on
 * the assembled {@link Song} after {@code loadFrom}.
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
    static final List<SongMigration> PRE_ASSEMBLY = List.of(
        versioned(StageId.LEGACY_FORMAT, 2, 0, ctx -> FormatMigrator.migrate(ctx.lines, ctx.legacyLineOffsets, 1)),
        versioned(StageId.ANNOTATION_DYNAMICS, 2, 3, ctx -> FormatMigrator.migrateAnnotationDynamics(ctx.lines)),
        versioned(StageId.FINAL_TERMINAL, 2, 4, ctx -> FormatMigrator.migrateFinalTerminal(ctx.lines)),
        versioned(StageId.PIXELS_TO_SS, 2, 1, ctx -> {
            var pps = DocumentScale.PIXELS_PER_STAFF_SPACE;
            ctx.lineWidthSs /= pps;
            ctx.rowHeightAdjustmentSs /= pps;
            FormatMigrator.migratePixelsToStaffSpace(ctx.lines);
        }),
        new SongMigration(StageId.LINE_WIDTH_FIX,
            ctx -> ctx.majorVersion == 2 && ctx.minorVersion < 3 && ctx.lineWidthSs >= LEGACY_LINE_WIDTH_PX_MIN,
            ctx -> ctx.lineWidthSs /= DocumentScale.PIXELS_PER_STAFF_SPACE
        ));

    // Stages that run once ctx.song is set. Only the legacy-lyrics import is conditional; the
    // grace-host melisma repair and syllabic backfill run for every file.
    static final List<SongMigration> POST_ASSEMBLY = List.of(
        new SongMigration(StageId.LEGACY_LYRICS,
            ctx -> !ctx.lyrics.isBlank() && ctx.isBefore(2, PER_NOTE_LYRIC_VERSION),
            MigrationPipeline::applyLegacyLyrics),
        new SongMigration(StageId.GRACE_HOST_MELISMA,
            ctx -> true,
            MigrationPipeline::applyGraceHostMelismaRepair),
        new SongMigration(StageId.SYLLABIC_BACKFILL,
            ctx -> true,
            MigrationPipeline::applySyllabicBackfill));

    private MigrationPipeline() {
    }

    private static SongMigration versioned(StageId id, int major, int minor, Consumer<MigrationContext> apply) {
        return new SongMigration(id, ctx -> ctx.isBefore(major, minor), apply);
    }

    private static void applyLegacyLyrics(MigrationContext ctx) {
        LegacyLyricsImporter.importLegacyLyrics(requireSong(ctx).getLines(), ctx.lyrics);
    }

    // Bring imported grace-host pairs onto the automatic melisma: a legacy file may carry the
    // syllable on the host, or a syllable on the grace with no melisma across it. Runs before
    // the syllabic backfill, which reads the syllabic chain in element order and so must see
    // syllables at their final positions. The song is assembled by now, so tracking has been
    // resumed; suspend it again to keep the repair silent, as the rest of the load path is.
    private static void applyGraceHostMelismaRepair(MigrationContext ctx) {
        var song = requireSong(ctx);
        song.withoutMutationTracking(() -> song.getLines().forEach(Line::repairGraceHostMelismas));
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
        run(PRE_ASSEMBLY, ctx);
    }

    static void runPostAssembly(MigrationContext ctx) {
        run(POST_ASSEMBLY, ctx);
    }

    private static void run(List<SongMigration> migrations, MigrationContext ctx) {
        for (var migration : migrations) {
            if (migration.appliesTo().test(ctx)) {
                migration.apply().accept(ctx);
            }
        }
    }
}
