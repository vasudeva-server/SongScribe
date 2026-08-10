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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.hit.HitPriority;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;
import songscribe.layout.InsertionSpacingCalculator.InsertionResult;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;
import songscribe.layout.LyricRenderMetrics;
import songscribe.engraving.Staff;
import songscribe.Strings;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.ViewScale;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.dialog.AttachmentEditor;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.SelectionDragTracker;

/**
 * Unit tests for {@link LineComponent} coordinate conversions and layout-state semantics.
 *
 * <p>Tests use a real {@link LineComponent} instance (trivial constructor) and inject
 * dependencies via public setters or package-private fields to avoid triggering the
 * heavyweight layout engine.
 */
class LineComponentTest extends UnitTest {

    /**
     * Content beyond the minimum staff surround, used to build layouts whose measured
     * extents clear the {@link LineSpacing#MIN_ABOVE_MIDLINE_SS} floor — so the tests
     * observe the measured value rather than the floor.
     */
    private static final double CONTENT_HEADROOM_SS = 1.5;

    private static final int LYRICS_FONT_POINT_SIZE = 12;
    private static final Font LYRICS_FONT =
        new Font(Font.MONOSPACED, Font.PLAIN, LYRICS_FONT_POINT_SIZE);

    /** Lyric geometry for sizing; the gap is 0 since no verse baseline is asserted. */
    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** A staff too narrow for OVERFLOWING_NOTE_COUNT notes at their tightest legal spacing. */
    private static final double NARROW_LINE_WIDTH_SS = 20.0;

    /** Enough notes that NARROW_LINE_WIDTH_SS cannot hold them even fully compressed. */
    private static final int OVERFLOWING_NOTE_COUNT = 40;

    /** Few enough notes to leave NARROW_LINE_WIDTH_SS room to spare. */
    private static final int FITTING_NOTE_COUNT = 2;

    /** On the middle staff line, so the notes add no vertical extent of their own. */
    private static final int SP_ON_THE_MIDDLE_LINE = 0;

    /**
     * Well above the top staff line (negative is upward). Notes placed here give the line real
     * content above the staff, so its painted height clears the minimum staff surround.
     */
    private static final int SP_WELL_ABOVE_THE_STAFF = -12;

    /** A clean LineComponent under test. */
    private LineComponent lc;

    /**
     * A LineComponent that will run a real layout of {@code noteCount} notes, all at
     * {@code staffPosition}, against a {@link #NARROW_LINE_WIDTH_SS} staff. The score view is a mock,
     * which the layout tolerates because the line carries no lyrics — the only thing that would read
     * a font off it.
     * <p>
     * The notes go on the song's <em>second</em> line, so the component under test is not the first.
     * Laying out the first line measures the attribution block, which builds Swing labels and so
     * cannot run while {@code SwingUtilities} is mocked — and the attribution has nothing to do with
     * whether a line fits.
     */
    private static LineComponent componentWithNotes(int noteCount, int staffPosition) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
        when(scoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        when(scoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);

        // The width is set rather than left at its default: with no width provider installed outside
        // the running app, the default is not a value this test controls.
        var song = new Song();

        song.withoutMutationTracking(() -> song.setLineWidthSs(NARROW_LINE_WIDTH_SS));
        song.withoutMutationTracking(() -> song.addLine(song.lineCount(), new Line(song)));

        final var lineIndex = 1;
        var line = song.getLine(lineIndex);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < noteCount; i++) {
                var note = crotchet();
                note.setStaffPosition(staffPosition);
                line.addElement(note);
            }
        });

        var component = new LineComponent();
        component.setScoreView(scoreView);
        component.song = song;
        component.setLine(line, lineIndex);
        return component;
    }

    @BeforeEach
    void setUp() {
        lc = new LineComponent();
        // Restore default scale so tests that set a custom scale don't pollute others.
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    @AfterEach
    void tearDown() {
        // Reset to default scale after each test.
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    // -------------------------------------------------------------------------
    // staffPositionToYPx — converts staff position to pixel Y
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPositionToYPx {

        /**
         * For sp=0, the Y is exactly {@code getMiddleLineYPx()} (zero offset).
         */
        @Test
        void testSpZeroReturnsMiddleLineYPx() {
            lc.setMiddleLineYSs(5.0);
            // Default scale: 8 px per ss → middleLineYPx = round(8.0 * 5.0) = 40
            final var expectedMiddleLineYPx = 40;

            assertThat(lc.staffPositionToYPx(0))
                .as("sp=0 → no offset, returns middleLineYPx")
                .isEqualTo(expectedMiddleLineYPx);
        }

        /**
         * For sp=2, offset = round(ssToPx(spToSs(2))) = round(8.0 * 0.5 * 2) = round(8.0) = 8 px.
         * So result = middleLineYPx + 8.
         */
        @Test
        void testPositiveSpAddsDownwardOffset() {
            lc.setMiddleLineYSs(5.0);
            final var expectedMiddleLineYPx = 40;
            final var expectedOffset = 8; // round(8.0 * STAFF_POSITION_OFFSET_SS * 2) = round(8.0)

            assertThat(lc.staffPositionToYPx(2))
                .as("sp=2 → middleLineYPx + 8")
                .isEqualTo(expectedMiddleLineYPx + expectedOffset);
        }

        /**
         * For sp=-2, offset = -8 (upward in Y-down coords).
         */
        @Test
        void testNegativeSpAddsUpwardOffset() {
            lc.setMiddleLineYSs(5.0);
            final var expectedMiddleLineYPx = 40;
            final var expectedOffset = 8; // magnitude

            assertThat(lc.staffPositionToYPx(-2))
                .as("sp=-2 → middleLineYPx - 8")
                .isEqualTo(expectedMiddleLineYPx - expectedOffset);
        }
    }

    // -------------------------------------------------------------------------
    // getMiddleLineYPx — rounds ssToPx(middleLineYSs) to int
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetMiddleLineYPx {

        /**
         * Default scale (8 px/ss): middleLineYSs=5.0 → round(40.0) = 40.
         */
        @Test
        void testExactValueRoundsCorrectly() {
            lc.setMiddleLineYSs(5.0);

            assertThat(lc.getMiddleLineYPx())
                .as("5.0 ss × 8 px/ss = 40 px (exact)")
                .isEqualTo(40);
        }

        /**
         * With a fractional ss value, the result rounds to nearest int.
         * E.g. 5.1 ss × 8 px/ss = 40.8 → rounds to 41.
         */
        @Test
        void testFractionalValueRoundsToNearestInt() {
            var middleLineYSs = 5.1;
            lc.setMiddleLineYSs(middleLineYSs);
            // round(8.0 * 5.1) = round(40.8) = 41
            var expected = (int) Math.round(ScaleContext.ssToPx(middleLineYSs));

            assertThat(lc.getMiddleLineYPx())
                .as("5.1 ss × 8 px/ss = 40.8 → rounded to 41")
                .isEqualTo(expected);
        }
    }

    // -------------------------------------------------------------------------
    // calculateMiddleLineYSs — the line's own painted above-midline extent
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateMiddleLineYSs {

        /**
         * When {@code layoutResult} is already present (not dirty), {@code getMiddleLineYSs()}
         * computes {@code aboveStaffSs + STAFF_HALF_SS} from this line's own layout, without
         * re-running layout and without consulting any song-wide value.
         *
         * <p>The layout is built with more content above the staff than the minimum staff
         * surround so the measured extent, not {@link LineSpacing#MIN_ABOVE_MIDLINE_SS},
         * is what the midline placement reports.
         */
        @Test
        void testReturnsAboveStaffSsPlusHalfStaff() {
            var contentAboveStaffSs = Staff.MIN_ABOVE_STAFF_SS + CONTENT_HEADROOM_SS;
            var layout = LayoutResult.builder()
                .setContentAboveStaffSs(contentAboveStaffSs)
                .build();

            // Inject layout state: result present and not dirty, so performLayout() is skipped.
            lc.layoutResult = layout;
            lc.layoutDirty = false;
            // song must be non-null to trigger the lazy-calculation branch.
            lc.song = mock(Song.class);
            // middleLineYSs starts at 0.0 (JVM default) → lazy calc fires.

            assertThat(lc.getMiddleLineYSs())
                .as("midline sits at this line's own contentAboveStaffSs + STAFF_HALF_SS")
                .isEqualTo(contentAboveStaffSs + Staff.STAFF_HALF_SS);
        }

        /**
         * A line whose ink stops at the staff top still places its midline at the minimum
         * staff surround, not at {@code STAFF_HALF_SS}: {@code StaffLinesLayout} floors a
         * line's bounds there, and the staff must sit where that reservation put it or it
         * would be drawn clear of its own component.
         */
        @Test
        void testSparseLineUsesMinimumAboveMidlineFloor() {
            // No content above the staff at all.
            var layout = LayoutResult.builder().build();

            lc.layoutResult = layout;
            lc.layoutDirty = false;
            lc.song = mock(Song.class);

            assertThat(lc.getMiddleLineYSs())
                .as("no content above the staff → midline floored at MIN_ABOVE_MIDLINE_SS")
                .isEqualTo(LineSpacing.MIN_ABOVE_MIDLINE_SS);
        }
    }

    // -------------------------------------------------------------------------
    // getPreferredSize — null guard and pixel dimensions
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetPreferredSize {

        /**
         * When {@code song} is null, {@code getPreferredSize()} returns {@code (0, 0)}.
         */
        @Test
        void testNullSongReturnsDimensionZero() {
            // song is null by default on a fresh LineComponent.
            assertThat(lc.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When {@code line} is null (song set but no line), returns {@code (0, 0)}.
         */
        @Test
        void testNullLineReturnsDimensionZero() {
            lc.song = mock(Song.class);
            // line stays null (not set via setLine).

            assertThat(lc.getPreferredSize())
                .as("null line → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * With song, line, and an injected layout result, {@code getPreferredSize()} computes
         * ceiling-rounded pixel dimensions from the song's line width and this line's own
         * painted height.
         */
        @Test
        void testNonNullInputsReturnCeilingRoundedDimension() {
            // Sizing goes through the ViewScale seam, which scales by the fixed
            // DEFAULT_PIXELS_PER_STAFF_SPACE times the zoom factor — deliberately not the
            // mutable ScaleContext pps, so overriding that here would not affect the result.
            final var pxPerSs = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;

            // The line's own painted height, not a song-wide one: each line is now sized
            // to its own content.
            final var paintLineHeightSs = 9.5;

            var mockScoreView = mock(ScoreView.class);
            var mockCoordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);
            when(mockScoreView.getLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);

            // Set ScoreView before setting line (lineSelectionState is null → no coordinator call).
            lc.setScoreView(mockScoreView);

            // Set a real song/line to pass the null guards. The width is stubbed rather than
            // left at its default: outside the running app that default is a page-derived
            // value the test does not control (and is 0.0 when no provider is installed), so
            // a derived expectation could match a width read from the wrong source entirely.
            final var lineWidthSs = 42.5;
            var song = spy(new Song());
            doReturn(lineWidthSs).when(song).getLineWidthSs();
            lc.song = song;

            var line = song.getLine(0);
            // Use setLine so lineSelectionState is created.
            lc.setLine(line, 0);

            // Inject a mock layout result so performLayout() is not called. The layout drives
            // only the height — the width comes from the song, not from where the line's last
            // element happens to sit (issue #578).
            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.paintLineHeightSs(LYRIC_RENDER_METRICS)).thenReturn(paintLineHeightSs);
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;

            var size = lc.getPreferredSize();

            assertThat(size.width)
                .as("width = ceil(song lineWidthSs → view px)")
                .isEqualTo((int) Math.ceil(pxPerSs * lineWidthSs));

            assertThat(size.height)
                .as("height = ceil(this line's paintLineHeightSs → view px)")
                .isEqualTo((int) Math.ceil(pxPerSs * paintLineHeightSs));
        }

        /**
         * A line whose content cannot fit the staff is sized like any other: its height comes from
         * what it actually paints, and its width is the staff width — which is what clips the tail
         * running past the staff end.
         * <p>
         * Before issue #696, such a line had no layout at all and fell back to a fixed
         * {@link LineSpacing#MIN_LINE_HEIGHT_SS}. Were that fallback ever reinstated for an
         * overflowing line, the component would be shorter than the staff surround it draws, so the
         * staff would be clipped at top and bottom and would overlap the line above.
         */
        @Test
        void testOverflowingLineIsSizedToWhatItPaintsAndClippedToTheStaffWidth() {
            final var pxPerSs = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;
            var lineComponent = componentWithNotes(OVERFLOWING_NOTE_COUNT, SP_WELL_ABOVE_THE_STAFF);

            lineComponent.ensureLayout();

            var result = lineComponent.getLayoutResult();

            assertThat(result).as("layout produced no result for an over-full line").isNotNull();

            // States the case this test is about, so it cannot quietly become a test of a line that
            // fits if the width or note count ever drift.
            assertThat(result.overflowsStaffWidth())
                .as("the line under test must be one that cannot fit")
                .isTrue();

            var paintedHeightSs = result.paintLineHeightSs(LYRIC_RENDER_METRICS);
            var size = lineComponent.getPreferredSize();

            assertThat(size.height)
                .as("height = ceil(the over-full line's own painted height → view px)")
                .isEqualTo((int) Math.ceil(pxPerSs * paintedHeightSs));

            // Without this the equality above would also hold if the height were the old fixed
            // minimum and the painted height happened to equal it.
            assertThat(paintedHeightSs)
                .as("an over-full line of notes above the staff must paint taller than the minimum"
                    + " surround, or the height assertion cannot tell the two apart")
                .isGreaterThan(LineSpacing.MIN_LINE_HEIGHT_SS);

            assertThat(size.width)
                .as("width = ceil(staff width → view px), even though the content runs past it")
                .isEqualTo((int) Math.ceil(pxPerSs * NARROW_LINE_WIDTH_SS));

            // The width above is a clip, not a fit: the line really does extend beyond it.
            var line = lineComponent.getLine();

            assertThat(line).as("the component under test has no line").isNotNull();

            assertThat(result.getElementXSs(line.getElements().getLast()))
                .as("last element of an over-full line, against the staff width it is clipped to")
                .isGreaterThan(NARROW_LINE_WIDTH_SS);
        }
    }

    // -------------------------------------------------------------------------
    // Overflow warning — shown once per document, not once per over-full line
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OverflowWarning {

        private MockedStatic<OptionDialogs> dialogs;
        private MockedStatic<SwingUtilities> swing;

        /** Runnables handed to {@code SwingUtilities.invokeLater}, awaiting {@link #runDeferred}. */
        private final List<Runnable> deferred = new ArrayList<>();

        @BeforeEach
        void captureTheAlertPath() {
            // Run one layout before the mocks go in. The layout path initializes classes that build
            // Swing labels on the way (the tuplet glyph metrics, via GraphicUtils), and a label
            // cannot be constructed while SwingUtilities is mocked — JLabel asks it where the
            // mnemonic is and gets 0 instead of "nowhere". Class initialization happens once per
            // JVM, so paying for it here with real Swing available keeps it out of the mocked
            // window. A line that fits, so this warm-up raises no alert of its own.
            fittingComponent().ensureLayout();

            dialogs = mockStatic(OptionDialogs.class);
            swing = mockStatic(SwingUtilities.class);

            // The alert is deferred through invokeLater because layout runs during paint. This
            // stands in for the event queue: it holds each runnable until runDeferred() releases it.
            //
            // Holding them rather than running them inline is the whole point. Run inline, each
            // alert would complete before the next line is laid out, which would set the guard in
            // time no matter where the production code sets it — and the "one alert for several
            // over-full lines" test would pass even with the guard set after the dialog. Deferring
            // is also what lets the dialog be observed at all: left on the real event queue it would
            // run on the event dispatch thread, where this thread's static mock does not apply.
            swing.when(() -> SwingUtilities.invokeLater(any(Runnable.class)))
                .thenAnswer(invocation -> deferred.add(invocation.getArgument(0, Runnable.class)));

            // Each test states its own starting point rather than inheriting whatever an earlier
            // test left in the static guard.
            LineComponent.resetOverflowWarning();
        }

        @AfterEach
        void releaseTheAlertPath() {
            swing.close();
            dialogs.close();
            LineComponent.resetOverflowWarning();
        }

        /**
         * Drains the stand-in event queue, running what layout deferred. Drains repeatedly, since a
         * runnable may schedule another — Swing's own revalidate tasks land here too.
         */
        private void runDeferred() {
            while (!deferred.isEmpty()) {
                var pending = List.copyOf(deferred);
                deferred.clear();
                pending.forEach(Runnable::run);
            }
        }

        /** Asserts how many clipped-content alerts have been shown so far. */
        private void assertAlertCount(int expected, String description) {
            dialogs.verify(
                () -> OptionDialogs.showWarningMessage(
                    any(),
                    eq(Strings.ALERT_TITLE_LINES_DO_NOT_FIT),
                    eq(Strings.ALERT_LINES_DO_NOT_FIT)),
                times(expected).description(description));
        }

        /**
         * Several lines in a document typically overflow together, and the user is told once. The
         * guard is set before the dialog is scheduled rather than after it is dismissed, which is
         * what makes this hold; setting it afterwards would give one modal per over-full line
         * (refs #696).
         */
        @Test
        void testSeveralOverflowingLinesProduceOneAlert() {
            // All three laid out before anything deferred runs, as they are during one paint pass.
            overflowingComponent().ensureLayout();
            overflowingComponent().ensureLayout();
            overflowingComponent().ensureLayout();

            runDeferred();

            assertAlertCount(1, "alerts shown for three over-full lines in one document");
        }

        /**
         * A line that fits says nothing. Without this, the alert would be tied to laying out at all
         * rather than to a line that cannot fit, and every document would warn.
         */
        @Test
        void testALineThatFitsProducesNoAlert() {
            fittingComponent().ensureLayout();
            runDeferred();

            assertAlertCount(0, "alerts shown for a line with room to spare");
        }

        /**
         * Installing a document re-arms the warning, so the next document that cannot fit a line
         * says so. Without the reset, only the first over-full document of the session would warn.
         */
        @Test
        void testInstallingADocumentReArmsTheAlert() {
            overflowingComponent().ensureLayout();
            runDeferred();
            assertAlertCount(1, "alerts shown for the first document");

            LineComponent.resetOverflowWarning();
            overflowingComponent().ensureLayout();
            runDeferred();

            assertAlertCount(2, "alerts shown after a second document was installed");
        }

        /** A LineComponent wired for a real layout of a line its staff cannot hold. */
        private LineComponent overflowingComponent() {
            return componentWithNotes(OVERFLOWING_NOTE_COUNT, SP_ON_THE_MIDDLE_LINE);
        }

        /** The control: the same wiring, over a line with room to spare. */
        private LineComponent fittingComponent() {
            return componentWithNotes(FITTING_NOTE_COUNT, SP_ON_THE_MIDDLE_LINE);
        }
    }

    // -------------------------------------------------------------------------
    // ensureLayout / invalidateLayout — dirty-flag semantics
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LayoutDirtySemantics {

        /**
         * After {@link LineComponent#invalidateLayout()}, {@code layoutResult} is null
         * and {@code layoutDirty} is true.
         */
        @Test
        void testInvalidateLayoutNullsResultAndSetsDirty() {
            // Prime with a non-null layout result.
            lc.layoutResult = mock(LayoutResult.class);
            lc.layoutDirty = false;

            lc.invalidateLayout();

            assertThat(lc.getLayoutResult())
                .as("invalidateLayout nulls the cached result")
                .isNull();

            assertThat(lc.layoutDirty)
                .as("invalidateLayout marks layout as dirty")
                .isTrue();
        }

        /**
         * When song and line are null, {@link LineComponent#ensureLayout()} is a no-op —
         * it does not attempt to run the layout engine.
         */
        @Test
        void testEnsureLayoutDoesNothingWhenSongAndLineAreNull() {
            // song and line are both null by default.
            lc.layoutResult = null;
            lc.layoutDirty = true;

            // No exception, and result stays null (layout engine not called).
            lc.ensureLayout();

            assertThat(lc.getLayoutResult())
                .as("ensureLayout with null song/line leaves result null")
                .isNull();
        }
    }

    // -------------------------------------------------------------------------
    // setLine — state transitions
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetLine {

        /**
         * After {@link LineComponent#setLine}, {@code layoutDirty} is true and
         * {@code layoutResult} is null.
         */
        @Test
        void testSetLineSetsLayoutDirtyAndNullsResult() {
            var song = new Song();
            var line = song.getLine(0);

            // Prime with stale state to confirm reset.
            lc.layoutResult = mock(LayoutResult.class);
            lc.layoutDirty = false;

            lc.setLine(line, 0);

            assertThat(lc.layoutDirty)
                .as("setLine marks layout dirty")
                .isTrue();

            assertThat(lc.getLayoutResult())
                .as("setLine nulls the cached layout result")
                .isNull();

            assertThat(lc.getLine())
                .as("setLine keeps the supplied line")
                .isSameAs(line);
        }

        /**
         * When a {@link ScoreView} is already set and {@link LineComponent#setLine} is called,
         * the line is registered with the selection coordinator at its index.
         */
        @Test
        void testSetLineRegistersTheLineWithCoordinatorWhenScoreViewIsSet() {
            var song = new Song();
            var line = song.getLine(0);

            var mockScoreView = mock(ScoreView.class);
            var mockCoordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mockCoordinator);

            // scoreView is set before line; setScoreView() only registers if a line is
            // already set (it isn't yet), so no premature coordinator call.
            lc.setScoreView(mockScoreView);

            lc.setLine(line, 0);

            verify(mockCoordinator).registerLine(0, line);
        }
    }

    // -------------------------------------------------------------------------
    // readyLayout — null contract (row 15)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ReadyLayout {

        /**
         * When {@code line} is null, {@code readyLayout()} returns null.
         */
        @Test
        void testNullLineReturnsNull() {
            // line is null by default
            assertThat(lc.readyLayout())
                .as("null line → readyLayout returns null")
                .isNull();
        }

        /**
         * When a line and a non-null layout result are both set, {@code readyLayout()}
         * returns a non-null {@link LineComponent.ReadyLayout} wrapping them.
         */
        @Test
        void testNonNullLineAndLayoutReturnsReadyLayout() {
            var song = new Song();
            var line = song.getLine(0);
            lc.song = song;
            lc.setLine(line, 0);

            var mockLayout = mock(LayoutResult.class);
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;

            var ready = lc.readyLayout();
            assertThat(ready)
                .as("non-null line + layout → readyLayout is non-null")
                .isNotNull();

            assertThat(ready.line())
                .as("ReadyLayout.line() returns the set line")
                .isSameAs(line);
            assertThat(ready.layoutResult())
                .as("ReadyLayout.layoutResult() returns the set layout")
                .isSameAs(mockLayout);
        }

        /**
         * When {@code line} is set but {@code layoutResult} is null and {@code song} is also null
         * (so {@code ensureLayout} is a no-op), {@code readyLayout()} returns null.
         */
        @Test
        void testNullLayoutAfterEnsureReturnsNull() {
            // Set line but leave song null so ensureLayout() is a no-op
            // (ensureLayout checks song != null before running layout)
            var song = new Song();
            lc.setLine(song.getLine(0), 0);
            // song is not set on lc so ensureLayout() does nothing; layoutResult stays null

            assertThat(lc.readyLayout())
                .as("line set but layout stays null after ensureLayout() no-op → null")
                .isNull();
        }
    }

    // -------------------------------------------------------------------------
    // mouseClicked routing — right-button guard and grace-mode branch (row 12)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MouseClickedRouting {

        /**
         * A right-button click (BUTTON3) must return immediately, before any call to
         * {@code EditModeManager.getGraceModeManager()}.
         *
         * <p>Verifiable because: if the right-button guard is removed, the code calls
         * {@code getGraceModeManager()} which calls {@code EditModeManager.getGraceModeManager()};
         * without the static mock active the singleton is not initialized and that call throws
         * {@link AssertionError} (via {@code RuntimeError.exit}).
         * Passing without a static mock confirms the early return fires.
         */
        @Test
        void testRightButtonClickExitsBeforeGraceModeCheck() {
            var event = mouseEvent(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON3);
            // No mock for EditModeManager — if the right-button guard is removed this throws
            lc.mouseClicked(event);
            // Reaching here without AssertionError confirms the early return fired.
        }

        /**
         * When the grace-mode manager returns {@code true} from {@code mouseClicked},
         * the event is consumed and no further processing occurs.
         *
         * <p>Observable: if the grace-mode result is not respected, processing continues
         * to {@code selectionHandler.handleClick} which calls {@code lc.getScoreView()};
         * since {@code scoreView} is null this throws. Passing confirms the early return.
         */
        @Test
        void testGraceModeConsumingClickCausesEarlyReturn() {
            var event = mouseEvent(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1);
            var graceMock = mock(GraceModeManager.class);
            when(graceMock.mouseClicked(any(LineComponent.class), any(MouseEvent.class)))
                .thenReturn(true);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                // scoreView is null — if not consumed this would throw via getScoreView()
                lc.mouseClicked(event);
            }
            // Reaching here confirms grace-mode consumed the event.
        }
    }

    // -------------------------------------------------------------------------
    // mousePressed routing — right-button guard and grace-mode branch (row 13)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MousePressedRouting {

        /**
         * A right-button press (BUTTON3) must return immediately before any call to
         * {@code EditModeManager.getGraceModeManager()}.
         */
        @Test
        void testRightButtonPressExitsBeforeGraceModeCheck() {
            var event = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3);
            // No static mock — if the guard fires the method returns cleanly.
            lc.mousePressed(event);
        }

        /**
         * When the grace-mode manager consumes the press ({@code mousePressed} returns true),
         * no further processing occurs.
         *
         * <p>Observable: the paste-mode guard sits immediately after the grace-mode branch, so
         * without the early return {@code pasteMock.isInProgress()} would be called.
         *
         * <p>A ScoreView is required even though the press is consumed: the focus grab that
         * precedes every mode guard calls {@code getScoreView()} unconditionally.
         */
        @Test
        void testGraceModeConsumingPressCausesEarlyReturn() {
            var event = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
            var graceMock = mock(GraceModeManager.class);
            when(graceMock.mousePressed(any(LineComponent.class), any(MouseEvent.class)))
                .thenReturn(true);
            var pasteMock = mock(PasteModeManager.class);
            lc.setScoreView(mock(ScoreView.class));

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);
                lc.mousePressed(event);
            }

            verify(pasteMock, never()).isInProgress();
        }

        /**
         * A click on the score always gives the score focus, ahead of every mode guard — so a
         * press consumed by grace mode still grabs focus. Without this, {@code b} would be dead
         * after a grace-mode press.
         */
        @Test
        void testPressGrabsScoreViewFocusBeforeModeGuards() {
            var event = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
            var graceMock = mock(GraceModeManager.class);
            when(graceMock.mousePressed(any(LineComponent.class), any(MouseEvent.class)))
                .thenReturn(true);
            var mockScoreView = mock(ScoreView.class);
            lc.setScoreView(mockScoreView);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager)
                    .thenReturn(mock(PasteModeManager.class));
                lc.mousePressed(event);
            }

            verify(mockScoreView).requestFocusInWindow();
        }
    }

    // -------------------------------------------------------------------------
    // mouseDragged routing — paste mode suppresses rubber-band selection
    // -------------------------------------------------------------------------

    /**
     * A rubber-band drag announces itself to the selection coordinator via
     * {@code dragDidStart} before it does anything else, so that call is what these tests
     * observe. Paste mode already suppresses the press that would start a band; without the
     * matching guard in {@code mouseDragged}, the drag would still band from a stale press
     * point.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MouseDraggedRouting {

        /** Bounds large enough to contain the helper event's point. */
        private static final Dimension DRAG_COMPONENT_SIZE = new Dimension(100, 50);

        private SelectionDragTracker dragTracker;

        @BeforeEach
        void setUp() {
            var coordinator = mock(SelectionCoordinator.class);
            dragTracker = mock(SelectionDragTracker.class);
            when(coordinator.getDragTracker()).thenReturn(dragTracker);

            var mockScoreView = mock(ScoreView.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            // SELECT mode is what makes selection handling active for the drag.
            when(mockScoreView.getMode()).thenReturn(Mode.SELECT);
            lc.setScoreView(mockScoreView);

            // A non-null line is the other half of the selection-active gate, and it needs an
            // element on it: a line with nothing to sweep never arms a band, so the drag would
            // bail before reaching the paste guard these tests are about.
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(crotchet()));
            lc.song = song;
            lc.setLine(line, 0);

            // The drag clamps to the component bounds, which a zero-sized component
            // inverts into an empty range.
            lc.setSize(DRAG_COMPONENT_SIZE);
        }

        /**
         * Arms a band with a press on empty staff, then runs {@code mouseDragged} with paste mode
         * reporting the given in-progress state.
         *
         * <p>Only an armed band reaches the paste guard — a drag with nothing armed bails first,
         * which would let both tests pass for the wrong reason. The press is delivered straight to
         * the selection handler rather than through {@code LineComponent.mousePressed}, whose hit
         * cascade lays the line out and needs loaded fonts this fixture has no use for. Arming
         * before paste mode starts is also the scenario the drag guard exists for: a band armed by
         * an earlier press, with paste mode starting before the mouse moves.
         */
        private void dragWithPasteInProgress(boolean inProgress) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);
            when(pasteMock.isInProgress()).thenReturn(inProgress);

            // A null hit target is a genuine miss, which is exactly what arms a band.
            lc.getSelectionHandler().handlePress(mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1), null);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);

                lc.mouseDragged(mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1));
            }
        }

        @Test
        void testDragDuringPasteModeDoesNotStartASelection() {
            dragWithPasteInProgress(true);

            verify(dragTracker, never()).dragDidStart(any());
        }

        @Test
        void testDragOutsidePasteModeStartsASelection() {
            dragWithPasteInProgress(false);

            verify(dragTracker).dragDidStart(lc);
        }
    }

    // -------------------------------------------------------------------------
    // gracePreviewLineFrame — LINE_LEVEL vs. shifted frame (row 14)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GracePreviewLineFrame {

        /**
         * When this {@code LineComponent} is NOT the active grace line,
         * {@link LineComponent#gracePreviewLineFrame()} returns the canonical
         * {@link ElementFrame#LINE_LEVEL} constant.
         */
        @Test
        void testReturnsLineLevelWhenNotActiveGraceLine() {
            var graceMock = mock(GraceModeManager.class);
            // getGraceLineComponent() returns a different lc (or null) → not this lc
            when(graceMock.getGraceLineComponent()).thenReturn(null);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);

                assertThat(lc.gracePreviewLineFrame())
                    .as("not the active grace line → returns LINE_LEVEL")
                    .isSameAs(ElementFrame.LINE_LEVEL);
            }
        }

        /**
         * When this {@code LineComponent} IS the active grace line and a host insertion
         * preview is available, {@code gracePreviewLineFrame()} returns a frame carrying
         * the preview shift amount and from-index.
         */
        @Test
        void testReturnsShiftedFrameWhenThisIsActiveGraceLineWithPreview() {
            final var shiftSs = 3.5;
            final var insertionIndex = 2;
            // Only the shift is read here; the projected spring chain the fit gate would
            // solve is irrelevant to the preview frame, so an empty chain stands in for it.
            var preview = new InsertionResult(0.0, shiftSs, 0.0, List.of(), 0.0, 0.0);

            var graceMock = mock(GraceModeManager.class);
            when(graceMock.getGraceLineComponent()).thenReturn(lc);
            when(graceMock.getHostInsertionPreview()).thenReturn(preview);
            // getHostInsertionIndex() returns graceNoteIndex + 1; mock directly
            when(graceMock.getHostInsertionIndex()).thenReturn(insertionIndex);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);

                var frame = lc.gracePreviewLineFrame();

                assertThat(frame.hasPreviewShift())
                    .as("active grace line with preview → frame has preview shift")
                    .isTrue();
                assertThat(frame.previewShiftSs())
                    .as("previewShiftSs equals the InsertionResult shiftSs")
                    .isEqualTo(shiftSs);
                assertThat(frame.previewShiftFromIndex())
                    .as("fromIndex equals the insertion index")
                    .isEqualTo(insertionIndex);
            }
        }

        /**
         * When this {@code LineComponent} IS the active grace line but the host insertion
         * preview is null (no preview available), {@code gracePreviewLineFrame()} returns
         * {@link ElementFrame#LINE_LEVEL}.
         */
        @Test
        void testReturnsLineLevelWhenThisIsActiveGraceLineButNoPreview() {
            var graceMock = mock(GraceModeManager.class);
            when(graceMock.getGraceLineComponent()).thenReturn(lc);
            when(graceMock.getHostInsertionPreview()).thenReturn(null);

            try (var emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);

                assertThat(lc.gracePreviewLineFrame())
                    .as("active grace line but null preview → LINE_LEVEL")
                    .isSameAs(ElementFrame.LINE_LEVEL);
            }
        }
    }

    /**
     * Half-extent of a test hit region, in staff spaces. Large enough that a region built
     * around the origin with it contains every click point these tests produce, so a test
     * says "this click resolves to that target" without also pinning down geometry that
     * {@code HitRegionBuilderTest} owns.
     */
    private static final double TEST_REGION_HALF_EXTENT_SS = 10_000;

    /**
     * A registry whose one region covers the whole plane, so any point resolves to
     * {@code target}.
     */
    private static HitRegistry registryHitting(HitTarget target, int priority, boolean hoverTestable) {
        return HitRegistry.builder()
            .add(
                new Rectangle2D.Double(
                    -TEST_REGION_HALF_EXTENT_SS,
                    -TEST_REGION_HALF_EXTENT_SS,
                    2 * TEST_REGION_HALF_EXTENT_SS,
                    2 * TEST_REGION_HALF_EXTENT_SS),
                target,
                priority,
                hoverTestable)
            .build();
    }

    // -------------------------------------------------------------------------
    // Double-click on an element opens the lyric editor
    // -------------------------------------------------------------------------

    /**
     * These tests drive the real {@code mouseClicked} routing and assert on whether the lyric
     * editor was asked to open. Two collaborators are stubbed so the tests isolate the routing
     * rather than re-testing logic covered elsewhere: which element lies under the cursor
     * (covered by {@code HitRegionBuilderTest}) and which element a gesture resolves to
     * (covered by {@code LyricEditorEligibilityTest}). Opening is observed by stubbing
     * {@link LyricEditor} statically, since the call to {@code deselectAndOpenOn} is the
     * method's only observable effect.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DoubleClickLyricEditing {

        /** Index the stubbed hit registry reports as lying under the cursor. */
        private static final int HIT_INDEX = 0;

        private ScoreView mockScoreView;
        private Line line;
        private LayoutResult mockLayout;

        @BeforeEach
        void setUp() {
            mockScoreView = mock(ScoreView.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            // SELECT mode with no editor open is the state the gesture is designed for.
            when(mockScoreView.getMode()).thenReturn(Mode.SELECT);
            when(mockScoreView.getActiveLyricEditor()).thenReturn(null);
            lc.setScoreView(mockScoreView);

            var song = new Song();
            line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(crotchet()));
            lc.song = song;
            lc.setLine(line, 0);
            // Inject a clean layout so the heavyweight layout engine never runs. Its registry
            // reports the element and no lyric, so the click falls through to the element
            // branch under test rather than the double-click-on-lyric-text branch above it.
            mockLayout = mock(LayoutResult.class);
            when(mockLayout.getHitRegistry()).thenReturn(
                registryHitting(
                    new HitTarget.Element(line.getElement(HIT_INDEX)), HitPriority.ELEMENT, false));
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;
        }

        /**
         * Runs {@code mouseClicked} with the edit-mode managers stubbed, and hands the test the
         * static LyricEditor stub to assert against.
         */
        private void clickWith(MouseEvent event, Consumer<? super MockedStatic<LyricEditor>> assertions) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var lyricEditor = mockStatic(LyricEditor.class)) {

                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);

                lc.mouseClicked(event);

                assertions.accept(lyricEditor);
            }
        }

        @Test
        void testPlainDoubleClickOpensTheEditorOnTheClickedElement() {
            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                lyricEditor -> lyricEditor.verify(
                    () -> LyricEditor.deselectAndOpenOn(mockScoreView, line, HIT_INDEX)));
        }

        @Test
        void testSingleClickDoesNotOpenTheEditor() {
            // Without the click-count check every ordinary click on a note would pop open
            // a text field instead of selecting the note.
            clickWith(
                clickEvent(SINGLE_CLICK, NO_MODIFIERS),
                MockedStatic::verifyNoInteractions);
        }

        @Test
        void testShiftDoubleClickDoesNotOpenTheEditor() {
            // Shift+click extends a selection. This method runs before the selection handler
            // sees the click, so without the guard it would discard the selection being built.
            clickWith(
                clickEvent(DOUBLE_CLICK, InputEvent.SHIFT_DOWN_MASK),
                MockedStatic::verifyNoInteractions);
        }

        @Test
        void testDoubleClickDoesNotOpenASecondEditorWhileOneIsOpen() {
            // Without this guard a second editor would be stacked on the first, losing
            // whatever the user had typed into it.
            when(mockScoreView.getActiveLyricEditor()).thenReturn(mock(LyricEditor.class));

            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                lyricEditor -> lyricEditor.verify(
                    () -> LyricEditor.deselectAndOpenOn(any(), any(), anyInt()), never()));
        }

        @Test
        void testDoubleClickOutsideSelectModeDoesNotOpenTheEditor() {
            // EDIT mode without Alt down is not a context for editing lyrics.
            when(mockScoreView.getMode()).thenReturn(Mode.EDIT);

            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                MockedStatic::verifyNoInteractions);
        }

        @Test
        void testDoubleClickThatHitsNoElementDoesNotOpenTheEditor() {
            // Without the miss guard the editor would be asked to open on element -1.
            when(mockLayout.getHitRegistry()).thenReturn(HitRegistry.EMPTY);

            clickWith(clickEvent(DOUBLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);
        }
    }

    // -------------------------------------------------------------------------
    // Double-click on an attachment opens its edit dialog
    // -------------------------------------------------------------------------

    /**
     * These tests drive the real {@code mouseClicked} routing and observe the gesture through
     * the {@link AttachmentEditor} static mock, because the call to {@code edit} is the
     * method's only observable effect. Which attachment lies under the cursor is stubbed
     * ({@code HitRegionBuilderTest} owns the real geometry), and which dialog an attachment
     * maps to is owned by {@code AttachmentEditorTest}; what is tested here is only whether the
     * gesture reaches {@code edit} at all.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DoubleClickAttachmentEditing {

        private ScoreView mockScoreView;
        private Line line;
        private StaffElement element;
        private TempoChangeAttachment attachment;
        private LayoutResult mockLayout;

        @BeforeEach
        void setUp() {
            mockScoreView = mock(ScoreView.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            // SELECT mode with no editor open is the state the gesture is designed for.
            when(mockScoreView.getMode()).thenReturn(Mode.SELECT);
            when(mockScoreView.getActiveLyricEditor()).thenReturn(null);
            lc.setScoreView(mockScoreView);

            var song = new Song();
            line = song.getLine(0);
            element = crotchet();
            attachment = new TempoChangeAttachment(element, new Tempo());
            song.withoutMutationTracking(() -> {
                line.addElement(element);
                element.addAttachment(attachment);
            });
            lc.song = song;
            lc.setLine(line, 0);
            // Inject a clean layout so the heavyweight layout engine never runs. Its registry
            // reports the attachment, which outranks the element it sits over.
            mockLayout = mock(LayoutResult.class);
            when(mockLayout.getHitRegistry()).thenReturn(
                registryHitting(
                    new HitTarget.Attachment(attachment), HitPriority.ATTACHMENT, false));
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;
        }

        /** Runs {@code mouseClicked} with playback stopped, the state the gesture needs. */
        private void clickWith(
            MouseEvent event, Consumer<? super MockedStatic<AttachmentEditor>> assertions) {
            clickWhilePlaying(false, event, assertions);
        }

        /**
         * Runs {@code mouseClicked} with the edit-mode managers stubbed and playback reporting
         * {@code playing}, then hands the test the static {@link AttachmentEditor} stub to
         * assert against. {@link MainFrame} is stubbed too because the production code passes
         * {@code MainFrame.getInstance()} to {@code edit}; a null instance keeps the
         * heavyweight singleton from being constructed, and {@code edit} is itself stubbed so
         * it never dereferences the argument.
         */
        private void clickWhilePlaying(
            boolean playing,
            MouseEvent event,
            Consumer<? super MockedStatic<AttachmentEditor>> assertions) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var mainFrame = mockStatic(MainFrame.class);
                var playback = mockStatic(PlaybackController.class);
                var attachmentEditor = mockStatic(AttachmentEditor.class)) {

                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);
                mainFrame.when(MainFrame::getInstance).thenReturn(null);
                playback.when(PlaybackController::isPlaying).thenReturn(playing);

                lc.mouseClicked(event);

                assertions.accept(attachmentEditor);
            }
        }

        @Test
        void testPlainDoubleClickOpensTheEditorOnTheClickedAttachment() {
            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                attachmentEditor -> attachmentEditor.verify(
                    () -> AttachmentEditor.edit(any(), eq(attachment), eq(line)), times(1)));
        }

        @Test
        void testSingleClickDoesNotOpenTheEditor() {
            // Without the click-count check every ordinary click on an attachment would pop
            // open its dialog instead of merely selecting it.
            clickWith(clickEvent(SINGLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);
        }

        @Test
        void testShiftDoubleClickDoesNotOpenTheEditor() {
            // Shift+click is building a selection, and this runs before the selection handler
            // sees the click, so without the guard it would interrupt with a modal dialog.
            clickWith(
                clickEvent(DOUBLE_CLICK, InputEvent.SHIFT_DOWN_MASK),
                MockedStatic::verifyNoInteractions);
        }

        @Test
        void testDoubleClickInEditModeWithoutAltDoesNotOpenTheEditor() {
            // EDIT mode is for inserting elements; a click above or below the staff must keep
            // inserting there rather than opening a dialog.
            when(mockScoreView.getMode()).thenReturn(Mode.EDIT);

            clickWith(clickEvent(DOUBLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);
        }

        /**
         * The only branch where the gesture fires outside SELECT mode: {@code isSelectionActive}
         * admits an Alt-down click, since Alt switches to SELECT mode permanently on the press.
         * An inverted or dropped Alt term passes every negative test above, so without this it
         * would surface only in manual testing.
         */
        @Test
        void testAltDoubleClickInEditModeOpensTheEditor() {
            when(mockScoreView.getMode()).thenReturn(Mode.EDIT);

            clickWith(
                clickEvent(DOUBLE_CLICK, InputEvent.ALT_DOWN_MASK),
                attachmentEditor -> attachmentEditor.verify(
                    () -> AttachmentEditor.edit(any(), eq(attachment), eq(line)), times(1)));
        }

        /**
         * A note that merely carries an attachment is not the attachment. The lyric editor is
         * reported as already open so the element branch above declines outright, leaving the
         * attachment hit test as the only thing that can refuse this click.
         */
        @Test
        void testDoubleClickOnAnElementDoesNotOpenTheEditor() {
            when(mockScoreView.getActiveLyricEditor()).thenReturn(mock(LyricEditor.class));
            when(mockLayout.getHitRegistry()).thenReturn(
                registryHitting(new HitTarget.Element(element), HitPriority.ELEMENT, false));

            clickWith(clickEvent(DOUBLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);
        }

        /**
         * The fermata and dynamic outcome — an attachment with no dialog, standing in here as
         * the stubbed {@code edit}'s default answer of false. Nothing opens, and no element is
         * inserted at the click point; without the second half, every double-clicked fermata
         * would drop a note under itself.
         * <p>
         * This does not distinguish which step swallowed the click. The selection handler
         * consumes every click while selection is active, so a {@code mouseClicked} that
         * ignored {@code edit}'s answer and returned early itself would leave exactly the same
         * observable result — and would be equally correct, which is why nothing here tries to
         * tell the two apart.
         */
        @Test
        void testDoubleClickThatOpensNoDialogInsertsNoElement() {
            try (var previewManager = mockStatic(PreviewElementManager.class)) {
                clickWith(
                    clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                    // The gesture really did run and decline, so the assertion below is
                    // observing the aftermath of a refusal rather than of a click that never
                    // reached the attachment step at all.
                    attachmentEditor -> attachmentEditor.verify(
                        () -> AttachmentEditor.edit(any(), eq(attachment), eq(line))));

                previewManager.verifyNoInteractions();
            }
        }

        /**
         * A double-click during playback must not interrupt with a modal dialog. The guard
         * under test is {@code mouseClicked}'s own, which reads {@code PlaybackController} —
         * the same state the {@code DISABLE_WHEN_PLAYING} action flag resolves against, and the
         * one {@code mousePressed} already uses. The sequencer-running check further down the
         * path is left reporting "not playing", so this guard is the only thing that can refuse
         * the click.
         */
        @Test
        void testDoubleClickWhilePlayingDoesNotOpenTheEditor() {
            clickWhilePlaying(
                true, clickEvent(DOUBLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);
        }

        /**
         * An open lyric editor blocks the lyric gesture but must not block this one — the two
         * edit different things. Pins the deliberate absence of an active-editor condition on
         * the attachment step: copying the lyric step's guard onto it, or swapping the order of
         * the two steps, would silently break editing an attachment whenever a lyric editor
         * happened to be open on the line.
         */
        @Test
        void testDoubleClickWithTheLyricEditorOpenStillOpensTheEditor() {
            when(mockScoreView.getActiveLyricEditor()).thenReturn(mock(LyricEditor.class));

            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                attachmentEditor -> attachmentEditor.verify(
                    () -> AttachmentEditor.edit(any(), eq(attachment), eq(line)), times(1)));
        }

        /**
         * The click count must match exactly, not merely reach two. Swing reports a rising count
         * for each click of a longer train, so a loosened comparison would reopen the dialog on
         * the third click and on every one after it.
         */
        @Test
        void testTripleClickDoesNotOpenTheEditor() {
            clickWith(clickEvent(TRIPLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);
        }
    }

    // -------------------------------------------------------------------------
    // Click on a lyric — a registry-reported HitTarget.Lyric
    // -------------------------------------------------------------------------

    /**
     * These tests drive the real {@code mouseClicked} with the line's hit registry reporting a
     * lyric under the cursor. A one-region registry stands in for real layout geometry, which
     * {@code HitRegionBuilderTest} owns.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ClickOnLyric {

        /** Index of the sole element on the line, which the click resolves to. */
        private static final int LYRIC_ELEMENT_INDEX = 0;

        private static final int VERSE = Lyric.FIRST_VERSE;

        private ScoreView mockScoreView;
        private Line line;
        private StaffElement element;
        private LayoutResult mockLayout;

        @BeforeEach
        void setUp() {
            mockScoreView = mock(ScoreView.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            // SELECT mode with no editor open is the state the gesture is designed for.
            when(mockScoreView.getMode()).thenReturn(Mode.SELECT);
            when(mockScoreView.getActiveLyricEditor()).thenReturn(null);
            when(mockScoreView.findLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
            lc.setScoreView(mockScoreView);

            var song = new Song();
            line = song.getLine(0);
            element = crotchet();
            song.withoutMutationTracking(() -> line.addElement(element));
            lc.song = song;
            lc.setLine(line, 0);
            // Inject a clean layout so the heavyweight layout engine never runs; its registry
            // is stubbed per-test below, and reports nothing until then.
            mockLayout = mock(LayoutResult.class);
            when(mockLayout.getHitRegistry()).thenReturn(HitRegistry.EMPTY);
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;
        }

        /** Stubs the layout's registry to report {@code element}/{@code VERSE} as hit. */
        private void stubLyricHit() {
            when(mockLayout.getHitRegistry()).thenReturn(
                registryHitting(new HitTarget.Lyric(element, VERSE), HitPriority.LYRIC, true));
        }

        /**
         * Runs {@code mouseClicked} with the edit-mode managers stubbed to decline the click, and
         * hands the test the static {@link LyricEditor} stub to assert against. Unlike
         * {@code DoubleClickLyricEditing.clickWith} nothing here reports an element: the lyric
         * consumes the click, so nothing below it is reached.
         */
        private void clickWith(MouseEvent event, Consumer<? super MockedStatic<LyricEditor>> assertions) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var lyricEditor = mockStatic(LyricEditor.class)) {

                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);

                lc.mouseClicked(event);

                assertions.accept(lyricEditor);
            }
        }

        @Test
        void testDoubleClickOnNonBlankLyricOpensTheEditorOnTheClickedElement() {
            element.lyrics.add(new Lyric(VERSE, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
            stubLyricHit();

            clickWith(
                clickEvent(DOUBLE_CLICK, NO_MODIFIERS),
                lyricEditor -> lyricEditor.verify(() ->
                    LyricEditor.deselectAndOpenOn(mockScoreView, line, LYRIC_ELEMENT_INDEX)));
        }

        @Test
        void testDoubleClickOnBlankLyricDoesNotOpenTheEditor() {
            // A melisma carrier has no text of its own — a "blank" lyric.
            element.lyrics.add(new Lyric(VERSE, "", Lyric.Extend.STOP, null, false));
            stubLyricHit();

            clickWith(clickEvent(DOUBLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);
        }

        /**
         * The click must be swallowed by the lyric branch rather than falling through to element
         * insertion. This runs in EDIT mode deliberately: in SELECT mode the selection handler
         * reports every single click as handled before {@code mouseClicked} could ever reach
         * {@link PreviewElementManager}, so the assertion below would hold with the lyric branch
         * deleted outright. EDIT mode is also the only mode in which an insertion preview exists
         * at all, so it is the only mode where the bug this guards against can happen.
         */
        @Test
        void testSingleClickOnLyricConsumesTheClickWithoutOpeningTheEditorOrReachingPreviewElementManager() {
            when(mockScoreView.getMode()).thenReturn(Mode.EDIT);
            element.lyrics.add(new Lyric(VERSE, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
            stubLyricHit();

            try (var previewManager = mockStatic(PreviewElementManager.class)) {
                clickWith(clickEvent(SINGLE_CLICK, NO_MODIFIERS), MockedStatic::verifyNoInteractions);

                previewManager.verifyNoInteractions();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Press during playback — the press path is gated before the cascade
    // -------------------------------------------------------------------------

    /**
     * A press does nothing at all while the song is playing.
     * <p>
     * The guard reads {@code PlaybackController}, the same state the {@code DISABLE_WHEN_PLAYING}
     * action flag resolves against, and deliberately not the sequencer-running check that the
     * handlers further down the press path use. These tests leave that sequencer check reporting
     * "not playing" precisely so the guard under test is the only thing that can refuse the press:
     * with it removed, the press would fall through and select the lyric.
     * <p>
     * Selecting is the effect these tests watch because it is the one a unit test can observe
     * cheaply. The guard's other job is to stop the mode switch, which programmatic action
     * invocation let through during playback — see the comment on {@code mousePressed}.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PressDuringPlayback {

        private static final int PRESS_X_PX = 100;
        private static final int PRESS_Y_PX = 50;

        private SelectionCoordinator coordinator;
        private StaffElement element;

        @BeforeEach
        void setUp() {
            var mockScoreView = mock(ScoreView.class);
            coordinator = mock(SelectionCoordinator.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(coordinator);
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            // EDIT mode: a lyric is selectable in place there, which is the effect these tests
            // watch for.
            when(mockScoreView.getMode()).thenReturn(Mode.EDIT);
            when(mockScoreView.findLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
            lc.setScoreView(mockScoreView);

            var song = new Song();
            var line = song.getLine(0);
            element = crotchet();
            song.withoutMutationTracking(() -> line.addElement(element));
            lc.song = song;
            lc.setLine(line, 0);

            // Inject a clean layout reporting the lyric under the pointer, so the hit test — if
            // it is reached at all — resolves the press to that lyric.
            var mockLayout = mock(LayoutResult.class);
            when(mockLayout.getHitRegistry()).thenReturn(
                registryHitting(
                    new HitTarget.Lyric(element, Lyric.FIRST_VERSE), HitPriority.LYRIC, true));
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;
        }

        /** Runs the real {@code mousePressed} with playback reporting {@code playing}. */
        private void pressWhilePlaying(boolean playing) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var preview = mockStatic(PreviewElementManager.class);
                var playback = mockStatic(PlaybackController.class)) {

                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);
                playback.when(PlaybackController::isPlaying).thenReturn(playing);

                lc.mousePressed(new MouseEvent(
                    lc, MouseEvent.MOUSE_PRESSED, 0L, NO_MODIFIERS,
                    PRESS_X_PX, PRESS_Y_PX, PRESS_X_PX, PRESS_Y_PX,
                    SINGLE_CLICK, false, MouseEvent.BUTTON1));
            }
        }

        @Test
        void testPressOnALyricWhilePlayingSelectsNothing() {
            pressWhilePlaying(true);

            verify(coordinator, never()).selectLyric(any(), anyInt());
        }

        /**
         * The control for the test above: the same press with playback stopped does select, so a
         * pass there cannot come from the fixture failing to resolve the lyric in the first place.
         */
        @Test
        void testTheSamePressWithPlaybackStoppedSelectsTheLyric() {
            pressWhilePlaying(false);

            verify(coordinator).selectLyric(element, Lyric.FIRST_VERSE);
        }
    }

    // -------------------------------------------------------------------------
    // Mouse move over a lyric — insertion preview suppression
    // -------------------------------------------------------------------------

    /**
     * The rule that makes a lyric outrank the insertion preview: in EDIT mode, moving the pointer
     * over lyric text clears the preview instead of tracking it, so the staff positions a lyric box
     * covers cannot be clicked to insert a note. Endings and hairpins take the opposite deal — see
     * {@code LineSelectionHandler.handleEditModePress}.
     * <p>
     * Drives the real {@code mouseMoved} with the layout's lyric hit test stubbed; the real lyric
     * geometry behind it is covered by {@code LayoutResultTest}.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MoveOverLyric {

        /**
         * Pointer X, in view pixels, well past the clef/key-signature header. The header is tested
         * before the lyric row and clears the preview on its own, so a point inside it would make
         * the lyric test below pass for the wrong reason.
         * {@link #testMoveWithNoLyricUnderThePointerTracksTheMouse} is what rules that out: it
         * moves to this same X and expects the preview to be tracked, which only happens outside
         * the header.
         */
        private static final int X_PAST_HEADER_PX = 1000;

        private static final int Y_PX = 50;

        /** A mouse move carries no buttons and no click count. */
        private static final int NO_CLICKS = 0;

        private StaffElement element;
        private LayoutResult mockLayout;

        @BeforeEach
        void setUp() {
            var mockScoreView = mock(ScoreView.class);
            when(mockScoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
            when(mockScoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
            when(mockScoreView.getMode()).thenReturn(Mode.EDIT);
            when(mockScoreView.findLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
            lc.setScoreView(mockScoreView);

            var song = new Song();
            var line = song.getLine(0);
            element = crotchet();
            song.withoutMutationTracking(() -> line.addElement(element));
            lc.song = song;
            lc.setLine(line, 0);
            // Inject a clean layout so the heavyweight layout engine never runs; its lyric hit
            // test is stubbed per-test below.
            mockLayout = mock(LayoutResult.class);
            lc.layoutResult = mockLayout;
            lc.layoutDirty = false;
        }

        /**
         * Runs the real {@code mouseMoved} with the two edit-mode managers stubbed to decline the
         * event, and hands the test the {@link PreviewElementManager} stub to assert against.
         */
        private void moveWith(Consumer<? super MockedStatic<PreviewElementManager>> assertions) {
            var graceMock = mock(GraceModeManager.class);
            var pasteMock = mock(PasteModeManager.class);

            try (
                var emm = mockStatic(EditModeManager.class);
                var preview = mockStatic(PreviewElementManager.class)) {

                emm.when(EditModeManager::getGraceModeManager).thenReturn(graceMock);
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteMock);

                lc.mouseMoved(new MouseEvent(
                    lc, MouseEvent.MOUSE_MOVED, 0L, NO_MODIFIERS,
                    X_PAST_HEADER_PX, Y_PX, X_PAST_HEADER_PX, Y_PX,
                    NO_CLICKS, false, MouseEvent.NOBUTTON));

                assertions.accept(preview);
            }
        }

        @Test
        void testMoveOverALyricClearsThePreviewInsteadOfTrackingTheMouse() {
            when(mockLayout.getHitRegistry()).thenReturn(
                registryHitting(
                    new HitTarget.Lyric(element, Lyric.FIRST_VERSE), HitPriority.LYRIC, true));

            moveWith(preview -> {
                preview.verify(PreviewElementManager::clearPreviewElement);
                preview.verify(() -> PreviewElementManager.trackMouse(any(), any()), never());
            });
        }

        @Test
        void testMoveWithNoLyricUnderThePointerTracksTheMouse() {
            when(mockLayout.getHitRegistry()).thenReturn(HitRegistry.EMPTY);

            moveWith(preview -> {
                preview.verify(() -> PreviewElementManager.trackMouse(any(), any()));
                preview.verify(PreviewElementManager::clearPreviewElement, never());
            });
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Click count identifying a single click in a {@link MouseEvent}. */
    private static final int SINGLE_CLICK = 1;

    /** Click count identifying a double-click in a {@link MouseEvent}. */
    private static final int DOUBLE_CLICK = 2;

    /** Click count Swing reports for the third click of a click train. */
    private static final int TRIPLE_CLICK = 3;

    /** Empty modifier mask, for a click with no modifier key held. */
    private static final int NO_MODIFIERS = 0;

    /** Creates a left-button click event with the given click count and modifier mask. */
    private static MouseEvent clickEvent(int clickCount, int modifiers) {
        var source = new JPanel();
        return new MouseEvent(
            source,
            MouseEvent.MOUSE_CLICKED,
            0L,         // when (not examined by production code)
            modifiers,
            10, 10,     // x, y
            10, 10,     // xAbs, yAbs
            clickCount,
            false,      // popupTrigger
            MouseEvent.BUTTON1
        );
    }

    /** Creates a minimal mouse event with the given id and button on a fresh JPanel source. */
    private static MouseEvent mouseEvent(int id, int button) {
        var source = new JPanel();
        return new MouseEvent(
            source,
            id,
            0L,         // when (not examined by production code)
            0,          // modifiers
            10, 10,     // x, y
            10, 10,     // xAbs, yAbs
            1,          // clickCount
            false,      // popupTrigger
            button
        );
    }
}
