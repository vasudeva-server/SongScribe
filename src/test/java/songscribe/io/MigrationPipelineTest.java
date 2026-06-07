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

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

class MigrationPipelineTest extends UnitTest {

    // -- Constants --

    // Arbitrary non-zero pixel values for the two song-level scalars; each distinct so a
    // cross-wired scalar would be caught.
    private static final double LINE_WIDTH_PX = 400.0;
    private static final double ROW_HEIGHT_PX = 160.0;

    // A pixel-valued linewidth divided by pps when the line-width-fix effect runs.
    private static final double STORED_PIXEL_LINE_WIDTH = 800.0;

    // Load-bearing for the ordering proof: >= LEGACY_LINE_WIDTH_PX_MIN before division and
    // < it after (1600/8 = 200), so line-width-fix can only have fired before pixels-to-ss.
    private static final double ORDERING_PROOF_LINE_WIDTH_PX = 1600.0;

    // A version far beyond any real file, for the always-applies stage.
    private static final int FUTURE_VERSION = 99;

    // Non-zero tempo Y offset value (in pixels) for testing legacy format wiring.
    private static final int TEMPO_CHANGE_Y_POS_PX = 20;

    // Non-zero lyricsYPosSs value (in pixels) for PIXELS_TO_SS wiring test.
    private static final double LYRICS_Y_POS_PX = 48.0;

    // -- Helpers --

    private static MigrationContext ctx(int major, int minor) {
        var c = new MigrationContext();
        c.majorVersion = major;
        c.minorVersion = minor;
        return c;
    }

    private static SongMigration stage(StageId id) {
        for (var migration : MigrationPipeline.PRE_ASSEMBLY) {
            if (migration.id() == id) {
                return migration;
            }
        }

        for (var migration : MigrationPipeline.POST_ASSEMBLY) {
            if (migration.id() == id) {
                return migration;
            }
        }

        throw new IllegalArgumentException("Unknown stage: " + id);
    }

    private static Song mockSongWithLines() {
        var song = mock(Song.class);
        when(song.getLines()).thenReturn(new ArrayList<>());
        return song;
    }

    // -- Nested test classes --

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AnnotationDynamicsStage {

        @Test
        void testAppliesBeforeThreshold() {
            assertThat(stage(StageId.ANNOTATION_DYNAMICS).appliesTo().test(ctx(2, 2))).isTrue();
        }

        @Test
        void testDoesNotApplyAtThreshold() {
            assertThat(stage(StageId.ANNOTATION_DYNAMICS).appliesTo().test(ctx(2, 3))).isFalse();
        }

        // Effect: delegates to FormatMigrator with no error on an empty line list.
        @Test
        void testEffectRunsOnEmptyLines() {
            stage(StageId.ANNOTATION_DYNAMICS).apply().accept(ctx(2, 2));
        }

        // Wiring test: a forte annotation text is promoted to a DynamicAttachment and the
        // AnnotationAttachment is removed, proving the stage delegates to migrateAnnotationDynamics.
        @Test
        void testEffectConvertsForteAnnotationToDynamicAttachment() {
            var c = ctx(2, 2);
            var line = lineWithAnnotation("f");
            c.lines.add(line);

            stage(StageId.ANNOTATION_DYNAMICS).apply().accept(c);

            var note = line.getElement(0);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNotNull();
            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FinalTerminalStage {

        @Test
        void testAppliesBeforeThreshold() {
            assertThat(stage(StageId.FINAL_TERMINAL).appliesTo().test(ctx(2, 3))).isTrue();
        }

        @Test
        void testDoesNotApplyAtThreshold() {
            assertThat(stage(StageId.FINAL_TERMINAL).appliesTo().test(ctx(2, 4))).isFalse();
        }

        // Effect: a line that ends in a note gets FINAL_DOUBLE_BARLINE appended.
        @Test
        void testEffectAppliesFinalBarline() {
            var c = ctx(2, 3);
            var line = detachedLine();
            line.addElement(ElementType.CROTCHET.newInstance());
            c.lines.add(line);

            stage(StageId.FINAL_TERMINAL).apply().accept(c);

            assertThat(line.getElement(line.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LegacyFormatStage {

        @Test
        void testAppliesBeforeThreshold() {
            assertThat(stage(StageId.LEGACY_FORMAT).appliesTo().test(ctx(1, 5))).isTrue();
        }

        @Test
        void testDoesNotApplyAtThreshold() {
            assertThat(stage(StageId.LEGACY_FORMAT).appliesTo().test(ctx(2, 0))).isFalse();
        }

        // Effect: delegates to FormatMigrator.migrate with no error on an empty line list.
        @Test
        void testEffectRunsOnEmptyLines() {
            stage(StageId.LEGACY_FORMAT).apply().accept(ctx(1, 5));
        }

        // Wiring test: a non-zero tempoChangeYPosPx is migrated to a per-instance
        // TempoChangeAttachment.userYOffsetSs, proving the stage delegates to FormatMigrator.migrate.
        @Test
        void testEffectMigratesTempoChangeYPosToAttachmentOffset() {
            var c = ctx(1, 5);
            var line = detachedLine();
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            note.addAttachment(new TempoChangeAttachment(note, new Tempo()));
            c.legacyLineOffsets.put(line, new LegacyLineOffsets(TEMPO_CHANGE_Y_POS_PX,
                LegacyLineOffsets.DEFAULTS.beatChangeYPosPx(),
                LegacyLineOffsets.DEFAULTS.firstSecondEndingYPosPx(),
                LegacyLineOffsets.DEFAULTS.trillYPosPx()));
            c.lines.add(line);

            stage(StageId.LEGACY_FORMAT).apply().accept(c);

            var attachment = note.findAttachment(TempoChangeAttachment.class);
            assertThat(attachment).isNotNull();
            if (attachment == null) return;
            assertThat(attachment.getUserYOffsetSs()).isEqualTo(TEMPO_CHANGE_Y_POS_PX);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LegacyLyricsStage {

        @Test
        void testAppliesBeforeThreshold() {
            var c = ctx(2, MigrationPipeline.PER_NOTE_LYRIC_VERSION - 1);
            c.lyrics = "some lyrics";
            c.song = mockSongWithLines();
            assertThat(stage(StageId.LEGACY_LYRICS).appliesTo().test(c)).isTrue();
        }

        @Test
        void testDoesNotApplyAtThreshold() {
            var c = ctx(2, MigrationPipeline.PER_NOTE_LYRIC_VERSION);
            c.lyrics = "some lyrics";
            assertThat(stage(StageId.LEGACY_LYRICS).appliesTo().test(c)).isFalse();
        }

        @Test
        void testDoesNotApplyWhenLyricsBlank() {
            var c = ctx(2, MigrationPipeline.PER_NOTE_LYRIC_VERSION - 1);
            c.lyrics = "   ";
            assertThat(stage(StageId.LEGACY_LYRICS).appliesTo().test(c)).isFalse();
        }

        // Direct effect test: after the stage runs with a single-word lyrics blob, the first
        // note on the first line carries a lyric record with that word.
        @Test
        void testEffectPopulatesLyricRecords() {
            var c = ctx(2, MigrationPipeline.PER_NOTE_LYRIC_VERSION - 1);
            var song = mock(Song.class);
            var line = detachedLine();
            line.addElement(ElementType.CROTCHET.newInstance());
            var songLines = new ArrayList<Line>();
            songLines.add(line);
            when(song.getLines()).thenReturn(songLines);
            c.song = song;
            c.lyrics = "hello";

            stage(StageId.LEGACY_LYRICS).apply().accept(c);

            var note = line.getElement(0);
            assertThat(note.lyrics).hasSize(1);
            assertThat(note.lyrics.get(0).text()).isEqualTo("hello");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineWidthFixStage {

        @Test
        void testAppliesWhenVersionAndWidthMatch() {
            var c = ctx(2, 2);
            c.lineWidthSs = MigrationPipeline.LEGACY_LINE_WIDTH_PX_MIN;
            assertThat(stage(StageId.LINE_WIDTH_FIX).appliesTo().test(c)).isTrue();
        }

        @Test
        void testDoesNotApplyWhenMajorVersionAbove2() {
            var c = ctx(3, 0);
            c.lineWidthSs = MigrationPipeline.LEGACY_LINE_WIDTH_PX_MIN;
            assertThat(stage(StageId.LINE_WIDTH_FIX).appliesTo().test(c)).isFalse();
        }

        @Test
        void testDoesNotApplyWhenMinorVersionAtThreshold() {
            var c = ctx(2, 3);
            c.lineWidthSs = MigrationPipeline.LEGACY_LINE_WIDTH_PX_MIN;
            assertThat(stage(StageId.LINE_WIDTH_FIX).appliesTo().test(c)).isFalse();
        }

        @Test
        void testDoesNotApplyWhenWidthBelowMin() {
            var c = ctx(2, 2);
            c.lineWidthSs = MigrationPipeline.LEGACY_LINE_WIDTH_PX_MIN - 1;
            assertThat(stage(StageId.LINE_WIDTH_FIX).appliesTo().test(c)).isFalse();
        }

        @Test
        void testEffectDividesLineWidthByPps() {
            var c = ctx(2, 2);
            c.lineWidthSs = STORED_PIXEL_LINE_WIDTH;
            stage(StageId.LINE_WIDTH_FIX).apply().accept(c);
            assertThat(c.lineWidthSs).isEqualTo(STORED_PIXEL_LINE_WIDTH / ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PixelsToSsStage {

        @Test
        void testAppliesBeforeThreshold() {
            assertThat(stage(StageId.PIXELS_TO_SS).appliesTo().test(ctx(2, 0))).isTrue();
        }

        @Test
        void testDoesNotApplyAtThreshold() {
            assertThat(stage(StageId.PIXELS_TO_SS).appliesTo().test(ctx(2, 1))).isFalse();
        }

        @Test
        void testEffectDividesAllScalarsByPps() {
            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            var c = ctx(2, 0);
            c.lineWidthSs = LINE_WIDTH_PX;
            c.rowHeightAdjustmentSs = ROW_HEIGHT_PX;

            stage(StageId.PIXELS_TO_SS).apply().accept(c);

            assertThat(c.lineWidthSs).isEqualTo(LINE_WIDTH_PX / pps);
            assertThat(c.rowHeightAdjustmentSs).isEqualTo(ROW_HEIGHT_PX / pps);
        }

        // Integration test via runPreAssembly: per-line lyricsYPosSs is also divided by pps.
        @Test
        void testEffectDividesLineFieldsByPps() {
            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            var c = ctx(2, 0);
            var line = detachedLine();
            line.setLyricsYPosSs(LYRICS_Y_POS_PX);
            c.lines.add(line);

            MigrationPipeline.runPreAssembly(c);

            assertThat(line.getLyricsYPosSs()).isEqualTo(LYRICS_Y_POS_PX / pps);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SyllabicBackfillStage {

        @Test
        void testAlwaysAppliesRegardlessOfVersion() {
            assertThat(stage(StageId.SYLLABIC_BACKFILL).appliesTo().test(ctx(FUTURE_VERSION, FUTURE_VERSION))).isTrue();
        }

        // Effect: runs against a song with no lines without error.
        @Test
        void testEffectRunsOnSongWithNoLines() {
            var c = ctx(2, 5);
            c.song = mockSongWithLines();
            stage(StageId.SYLLABIC_BACKFILL).apply().accept(c);
        }

        // Proves the forEach actually fires: two notes both loaded with BEGIN syllabic
        // (a stale value from a legacy read path). After backfill, note[1]'s predecessor
        // continues (BEGIN), so deriveSyllabic yields MIDDLE — a change that would be
        // invisible if the forEach were deleted.
        @Test
        void testEffectNormalizesStaleSyllabicMarkers() {
            var c = ctx(2, 5);
            var line = detachedLine();
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            // Both notes carry BEGIN — the stale value a legacy read path would produce.
            note0.lyrics.add(new Lyric(1, "a", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            note1.lyrics.add(new Lyric(1, "b", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));

            var song = mock(Song.class);
            var songLines = new ArrayList<Line>();
            songLines.add(line);
            when(song.getLines()).thenReturn(songLines);
            c.song = song;

            stage(StageId.SYLLABIC_BACKFILL).apply().accept(c);

            // note[1] has a BEGIN predecessor, so its syllabic must be MIDDLE after normalization.
            assertThat(note1.lyrics.get(0).syllabic()).isEqualTo(Lyric.Syllabic.MIDDLE);
            // note[0] has no predecessor, its own BEGIN signals continuation — stays BEGIN.
            assertThat(note0.lyrics.get(0).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RequireSong {

        // requireSong is a programming-error guard: if a post-assembly stage runs before
        // the song is assembled (ctx.song == null), the pipeline was driven out of order.
        @Test
        void testThrowsIllegalStateExceptionWhenSongNull() {
            var c = ctx(2, 5);
            // c.song is null by default — simulates a caller that forgot to assemble first.
            assertThatThrownBy(() -> stage(StageId.SYLLABIC_BACKFILL).apply().accept(c))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    // -- Top-level tests --

    // Verifies that runPreAssembly divides scalar fields by pps on a pre-2.1 context.
    @Test
    void testPreAssemblyScalarConversion() {
        var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
        var c = ctx(1, 0);
        c.lineWidthSs = LINE_WIDTH_PX;
        c.rowHeightAdjustmentSs = ROW_HEIGHT_PX;

        MigrationPipeline.runPreAssembly(c);

        assertThat(c.lineWidthSs).isEqualTo(LINE_WIDTH_PX / pps);
        assertThat(c.rowHeightAdjustmentSs).isEqualTo(ROW_HEIGHT_PX / pps);
    }

    // Asserts that PRE_ASSEMBLY registers exactly 5 stages in StageId enum order.
    @Test
    void testPreAssemblyStageListIsComplete() {
        var expectedIds = List.of(
            StageId.LEGACY_FORMAT,
            StageId.ANNOTATION_DYNAMICS,
            StageId.FINAL_TERMINAL,
            StageId.PIXELS_TO_SS,
            StageId.LINE_WIDTH_FIX
        );
        var actualIds = MigrationPipeline.PRE_ASSEMBLY.stream()
            .map(SongMigration::id)
            .toList();
        assertThat(actualIds).isEqualTo(expectedIds);
    }

    // Asserts that POST_ASSEMBLY registers exactly LEGACY_LYRICS then SYLLABIC_BACKFILL.
    @Test
    void testPostAssemblyStageListIsComplete() {
        var expectedIds = List.of(StageId.LEGACY_LYRICS, StageId.SYLLABIC_BACKFILL);
        var actualIds = MigrationPipeline.POST_ASSEMBLY.stream()
            .map(SongMigration::id)
            .toList();
        assertThat(actualIds).isEqualTo(expectedIds);
    }

    // Ordering invariant: stage 4 (pixels-to-ss) must fire before stage 5 (line-width-fix).
    //
    // The proof: start with lineWidthSs=1600 on a v2.0 context (both stages are eligible).
    //   Stage 4 first → 1600/8 = 200.  Stage 5 gate (>= 400) misses.  Final: 200.
    //   Stage 5 first → 1600/8 = 200, then stage 4 → 200/8 = 25.      Final: 25.
    //
    // Asserting 200 proves the documented order is preserved.
    @Test
    void testStageOrderingPreservesScalarInvariant() {
        var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
        var c = ctx(2, 0);
        c.lineWidthSs = ORDERING_PROOF_LINE_WIDTH_PX;

        MigrationPipeline.runPreAssembly(c);

        assertThat(c.lineWidthSs).isEqualTo(ORDERING_PROOF_LINE_WIDTH_PX / pps);
    }
}
