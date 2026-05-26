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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

class MigrationPipelineTest extends UnitTest {

    // -- Constants --

    // Arbitrary non-zero pixel values for the four song-level scalars; each distinct so a
    // cross-wired scalar would be caught.
    private static final double TOP_PADDING_PX = 80.0;
    private static final double LINE_WIDTH_PX = 400.0;
    private static final double ROW_HEIGHT_PX = 160.0;
    private static final double ATTRIBUTION_START_Y_PX = 240.0;

    // A pixel-valued linewidth divided by pps when the line-width-fix effect runs.
    private static final double STORED_PIXEL_LINE_WIDTH = 800.0;

    // Load-bearing for the ordering proof: >= LEGACY_LINE_WIDTH_PX_MIN before division and
    // < it after (1600/8 = 200), so line-width-fix can only have fired before pixels-to-ss.
    private static final double ORDERING_PROOF_LINE_WIDTH_PX = 1600.0;

    // Any non-zero top-padding so the fallback stage's gate stays closed.
    private static final double NON_ZERO_TOP_PADDING_SS = 10.0;

    // A version far beyond any real file, for the always-applies stage.
    private static final int FUTURE_VERSION = 99;

    // Known font sizes stubbed into Prefs so the fallback computation is deterministic.
    private static final int STUB_TITLE_FONT_SIZE = 24;
    private static final int STUB_ATTRIBUTION_FONT_SIZE = 14;

    // Non-zero tempoChangeYPosPx value for testing legacy format wiring.
    private static final int TEMPO_CHANGE_Y_POS_PX = 20;

    // Non-zero lyricsYPosSs value (in pixels) for PIXELS_TO_SS wiring test.
    private static final double LYRICS_Y_POS_PX = 48.0;

    // Attribution text with two lines for the TOP_PADDING_FALLBACK line-count term test.
    private static final String TWO_LINE_ATTRIBUTION = "Line 1\nLine 2";
    private static final int TWO_LINE_ATTRIBUTION_LINE_COUNT = 2;

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
            line.setTempoChangeYPosPx(TEMPO_CHANGE_Y_POS_PX);
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
            c.topPaddingSs = TOP_PADDING_PX;
            c.lineWidthSs = LINE_WIDTH_PX;
            c.rowHeightAdjustmentSs = ROW_HEIGHT_PX;
            c.attributionStartYSs = ATTRIBUTION_START_Y_PX;

            stage(StageId.PIXELS_TO_SS).apply().accept(c);

            assertThat(c.topPaddingSs).isEqualTo(TOP_PADDING_PX / pps);
            assertThat(c.lineWidthSs).isEqualTo(LINE_WIDTH_PX / pps);
            assertThat(c.rowHeightAdjustmentSs).isEqualTo(ROW_HEIGHT_PX / pps);
            assertThat(c.attributionStartYSs).isEqualTo(ATTRIBUTION_START_Y_PX / pps);
        }

        // Integration test via runPreAssembly: per-line lyricsYPosSs is also divided by pps.
        @Test
        void testEffectDividesLineFieldsByPps() {
            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            var c = ctx(2, 0);
            // Non-zero so TOP_PADDING_FALLBACK does not fire.
            c.topPaddingSs = TOP_PADDING_PX;
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
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TopPaddingFallbackStage {

        @Test
        void testAppliesWhenTopPaddingIsZero() {
            var c = ctx(2, 5);
            c.topPaddingSs = 0.0;
            assertThat(stage(StageId.TOP_PADDING_FALLBACK).appliesTo().test(c)).isTrue();
        }

        @Test
        void testDoesNotApplyWhenTopPaddingNonZero() {
            var c = ctx(2, 5);
            c.topPaddingSs = NON_ZERO_TOP_PADDING_SS;
            assertThat(stage(StageId.TOP_PADDING_FALLBACK).appliesTo().test(c)).isFalse();
        }

        // Stage-6 computed-value test. Stubs Prefs to known sizes and asserts the
        // exact fallback pixel value is written back, proving the formula is wired
        // correctly through the pipeline rather than just that the gate fires.
        @Test
        void testEffectComputesCorrectFallbackValue() {
            try (var prefsMock = mockStatic(Prefs.class)) {
                prefsMock.when(() -> Prefs.getInt(PrefsKey.TITLE_FONT_SIZE)).thenReturn(STUB_TITLE_FONT_SIZE);
                prefsMock.when(() -> Prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE)).thenReturn(STUB_ATTRIBUTION_FONT_SIZE);

                // attribution is empty (""), so Utils.lineCount returns 0
                var c = ctx(2, 5);
                stage(StageId.TOP_PADDING_FALLBACK).apply().accept(c);

                // ((2 * titleSize) + (0 * attributionSize)) - ssToRoundedPx(2.0)
                var expected = (double) (2 * STUB_TITLE_FONT_SIZE) - ScaleContext.ssToRoundedPx(2.0);
                assertThat(c.topPaddingSs).isEqualTo(expected);
            }
        }

        // Verifies the lineCount term contributes to the fallback when attribution is non-empty.
        // With empty attribution (above test) the term is 0; this test exercises the non-zero path.
        @Test
        void testEffectIncludesAttributionLineCountTerm() {
            try (var prefsMock = mockStatic(Prefs.class)) {
                prefsMock.when(() -> Prefs.getInt(PrefsKey.TITLE_FONT_SIZE)).thenReturn(STUB_TITLE_FONT_SIZE);
                prefsMock.when(() -> Prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE)).thenReturn(STUB_ATTRIBUTION_FONT_SIZE);

                var c = ctx(2, 5);
                c.attribution = TWO_LINE_ATTRIBUTION;
                stage(StageId.TOP_PADDING_FALLBACK).apply().accept(c);

                var expected = (double) (2 * STUB_TITLE_FONT_SIZE) +
                    (TWO_LINE_ATTRIBUTION_LINE_COUNT * STUB_ATTRIBUTION_FONT_SIZE) -
                    ScaleContext.ssToRoundedPx(2.0);
                assertThat(c.topPaddingSs).isEqualTo(expected);
            }
        }
    }

    // -- Top-level tests --

    // Verifies that runPreAssembly divides all four scalar fields by pps on a pre-2.1 context.
    @Test
    void testPreAssemblyScalarConversion() {
        var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
        var c = ctx(1, 0);
        // Non-zero so stage 6 (top-padding-fallback) does not fire after the division.
        c.topPaddingSs = TOP_PADDING_PX;
        c.lineWidthSs = LINE_WIDTH_PX;
        c.rowHeightAdjustmentSs = ROW_HEIGHT_PX;
        c.attributionStartYSs = ATTRIBUTION_START_Y_PX;

        MigrationPipeline.runPreAssembly(c);

        assertThat(c.topPaddingSs).isEqualTo(TOP_PADDING_PX / pps);
        assertThat(c.lineWidthSs).isEqualTo(LINE_WIDTH_PX / pps);
        assertThat(c.rowHeightAdjustmentSs).isEqualTo(ROW_HEIGHT_PX / pps);
        assertThat(c.attributionStartYSs).isEqualTo(ATTRIBUTION_START_Y_PX / pps);
    }

    // Asserts that PRE_ASSEMBLY registers exactly 6 stages in StageId enum order.
    @Test
    void testPreAssemblyStageListIsComplete() {
        var expectedIds = List.of(
            StageId.LEGACY_FORMAT,
            StageId.ANNOTATION_DYNAMICS,
            StageId.FINAL_TERMINAL,
            StageId.PIXELS_TO_SS,
            StageId.LINE_WIDTH_FIX,
            StageId.TOP_PADDING_FALLBACK
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
        c.topPaddingSs = TOP_PADDING_PX; // non-zero so stage 6 does not fire after division
        c.lineWidthSs = ORDERING_PROOF_LINE_WIDTH_PX;

        MigrationPipeline.runPreAssembly(c);

        assertThat(c.lineWidthSs).isEqualTo(ORDERING_PROOF_LINE_WIDTH_PX / pps);
    }
}
