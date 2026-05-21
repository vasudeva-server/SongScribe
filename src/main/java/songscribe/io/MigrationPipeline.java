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
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.Utils;

/**
 * Ordered, two-phase migration of a parsed song from any legacy {@code .mssw} format to
 * the current one. Pre-assembly stages operate on the parsed {@link Line}s and the
 * song-level scalars carried by {@link MigrationContext}; post-assembly stages operate on
 * the assembled {@link songscribe.dom.Song} after {@code loadFrom}.
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

    // ┌─────────────────────────── PRE-ASSEMBLY ───────────────────────────┐
    // │ 1 legacy-format        isBefore(2,0)              → FormatMigrator.migrate(lines, 1)        │
    // │ 2 annotation-dynamics  isBefore(2,3)              → migrateAnnotationDynamics              │
    // │ 3 final-terminal       isBefore(2,4)              → migrateFinalTerminal                   │
    // │ 4 pixels-to-ss         isBefore(2,1)  ┌ scalars ┐ → migratePixelsToStaffSpace              │
    // │ 5 line-width-fix       v2 & minor<3 & lineWidthSs>=MIN └ lineWidthSs /= pps                │
    // │ 6 top-padding-fallback topPaddingSs == 0          (reads Prefs + attribution)             │
    // └─────────────────────────────────────────────────────────────────────┘
    //
    // Scalar-ordering invariant (correctness-critical). Stage 4 divides the scalars by pps;
    // stages 5 and 6 then read those *same* scalars. The guards are mutually safe only
    // because 4 runs first — after 4, a v2.0 file's lineWidthSs is small, so 5's >= MIN guard
    // cannot re-fire, and a 0 topPaddingSs stays 0 for 6. Reordering 4 after 5/6 silently
    // double-divides or computes the fallback in the wrong unit (no crash, just wrong layout).
    static final List<SongMigration> PRE_ASSEMBLY = List.of(
        versioned(StageId.LEGACY_FORMAT, 2, 0, ctx -> FormatMigrator.migrate(ctx.lines, 1)),
        versioned(StageId.ANNOTATION_DYNAMICS, 2, 3, ctx -> FormatMigrator.migrateAnnotationDynamics(ctx.lines)),
        versioned(StageId.FINAL_TERMINAL, 2, 4, ctx -> FormatMigrator.migrateFinalTerminal(ctx.lines)),
        versioned(StageId.PIXELS_TO_SS, 2, 1, ctx -> {
            // Scalars first, then lines — see the scalar-ordering invariant above.
            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            ctx.topPaddingSs /= pps;
            ctx.lineWidthSs /= pps;
            ctx.rowHeightAdjustmentSs /= pps;
            ctx.attributionStartYSs /= pps;
            FormatMigrator.migratePixelsToStaffSpace(ctx.lines);
        }),
        new SongMigration(StageId.LINE_WIDTH_FIX,
            ctx -> ctx.majorVersion == 2 && ctx.minorVersion < 3 && ctx.lineWidthSs >= LEGACY_LINE_WIDTH_PX_MIN,
            ctx -> ctx.lineWidthSs /= ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE),
        new SongMigration(StageId.TOP_PADDING_FALLBACK,
            ctx -> ctx.topPaddingSs == 0,
            MigrationPipeline::applyTopPaddingFallback));

    // ┌────────────────────────── POST-ASSEMBLY (ctx.song set) ─────────────┐
    // │ 7 legacy-lyrics        !lyrics.isBlank() && isBefore(2,6) → importLegacyLyrics             │
    // │ 8 syllabic-backfill    always                            → line.backfillSyllabic()         │
    // └─────────────────────────────────────────────────────────────────────┘
    static final List<SongMigration> POST_ASSEMBLY = List.of(
        new SongMigration(StageId.LEGACY_LYRICS,
            ctx -> !ctx.lyrics.isBlank() && ctx.isBefore(2, PER_NOTE_LYRIC_VERSION),
            MigrationPipeline::applyLegacyLyrics),
        new SongMigration(StageId.SYLLABIC_BACKFILL,
            ctx -> true,
            MigrationPipeline::applySyllabicBackfill));

    private static SongMigration versioned(StageId id, int major, int minor, Consumer<MigrationContext> apply) {
        return new SongMigration(id, ctx -> ctx.isBefore(major, minor), apply);
    }

    // Legacy fallback: if topPadding wasn't set in file, calculate initial value.
    // Layout calculation will recalculate this properly, but this provides
    // a reasonable default for any code that accesses topPadding before layout.
    private static void applyTopPaddingFallback(MigrationContext ctx) {
        var titleSize = Prefs.getInt(PrefsKey.TITLE_FONT_SIZE);
        var attributionSize = Prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE);
        ctx.topPaddingSs = ((2 * titleSize) +
            (Utils.lineCount(ctx.attribution) * attributionSize)) -
            ScaleContext.ssToRoundedPx(2.0);
    }

    private static void applyLegacyLyrics(MigrationContext ctx) {
        LegacyLyricsImporter.importLegacyLyrics(requireSong(ctx).getLines(), ctx.lyrics);
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
