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

package songscribe.ui.renderer;

import module java.desktop;

import com.uber.nullaway.annotations.Initializer;

import org.jspecify.annotations.Nullable;

import songscribe.music.Song;
import songscribe.music.Span;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.layout.SongLayoutMetrics;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LyricRenderMetrics;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.menu.DebugState;

/**
 * Context passed to element renderers containing shared rendering state.
 * <p>
 * Avoids passing many individual parameters to every render method.
 * Contains both immutable data (song, fonts) and mutable state
 * (current line being rendered, middleLineY).
 * <p>
 * Note: Named ElementRenderContext to avoid conflict with the existing
 * RenderContext interface used by the legacy renderer.
 */
public class ElementRenderContext {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Alpha for the replaced-element highlight (semi-transparent red). */
    private static final int REPLACED_ELEMENT_ALPHA = 90;

    /** Color for an existing element that will be replaced by the current preview element. */
    private static final Color REPLACED_ELEMENT_COLOR = new Color(255, 0, 0, REPLACED_ELEMENT_ALPHA);

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    private final Song song;
    @Nullable
    private Line currentLine;
    private double middleLineYSs;
    private int lineIndex;
    private LayoutResult layoutResult;
    private SongLayoutMetrics songLayoutMetrics;
    private LyricRenderMetrics lyricRenderMetrics;
    @Nullable
    private StaffElement activelyEditedElement;
    private LineComponent.@Nullable SelectionProvider selectionProvider;
    private boolean editMode;
    private Color selectionColor = Score.getSelectionColor();
    private int playingNoteIndex = -1;
    private int playingGraceNoteIndex = -1;
    private int currentElementIndex = -1;
    private double overrideElementXSs = Double.NaN;
    private int previewShiftFromIndex = -1;
    private double previewShiftSs;

    /**
     * Creates a render context for the given song.
     *
     * @param song The song being rendered
     */
    public ElementRenderContext(Song song) {
        this.song = song;
    }

    /**
     * Returns the song being rendered.
     */
    public Song getSong() {
        return song;
    }

    /**
     * Returns the music notation font.
     * <p>
     * This font contains glyphs for notes, rests, accidentals, clefs, etc.
     */
    public Font getMusicFont() {
        return BaseElementRenderer.MUSIC_FONT;
    }

    /**
     * Returns the current line being rendered.
     */
    public @Nullable Line getCurrentLine() {
        return currentLine;
    }

    /**
     * Sets the current line being rendered.
     *
     * @param currentLine The line to render
     */
    public void setCurrentLine(@Nullable Line currentLine) {
        this.currentLine = currentLine;
    }

    /**
     * Returns the Y coordinate of the middle staff line (B line)
     * in staff-space units.
     */
    public double getMiddleLineYSs() {
        return middleLineYSs;
    }

    /**
     * Sets the Y coordinate of the middle staff line.
     *
     * @param middleLineYSs Y coordinate in staff-space units
     */
    public void setMiddleLineYSs(double middleLineYSs) {
        this.middleLineYSs = middleLineYSs;
    }

    /**
     * Returns the index of the current line within the song.
     */
    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * Sets the index of the current line.
     *
     * @param lineIndex Line index (0-based)
     */
    public void setLineIndex(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    /**
     * Returns the index of the element currently being rendered within the line,
     * or -1 if no element index is set (e.g. during line-level rendering passes).
     */
    public int getCurrentElementIndex() {
        return currentElementIndex;
    }

    /**
     * Sets the index of the element currently being rendered.
     * Set to -1 to clear.
     *
     * @param currentElementIndex element index (0-based), or -1 to clear
     */
    public void setCurrentElementIndex(int currentElementIndex) {
        this.currentElementIndex = currentElementIndex;
    }

    /**
     * Returns the pixels-per-staff-space scale factor.
     * Convenience accessor for renderers that need pixel conversion.
     */
    public double getPixelsPerStaffSpace() {
        return ScaleContext.getInstance().getPixelsPerStaffSpace();
    }

    /**
     * Returns the layout result for the current line.
     * <p>
     * The layout result contains calculated bounds for all elements on the line.
     * Renderers use this to get pre-calculated positions instead of
     * computing them during rendering.
     */
    public LayoutResult getLayoutResult() {
        return layoutResult;
    }

    /**
     * Sets the layout result for the current line.
     *
     * @param layoutResult The layout result from LayoutEngine
     */
    @Initializer
    public void setLayoutResult(LayoutResult layoutResult) {
        this.layoutResult = layoutResult;
    }

    /**
     * Returns the song-wide layout metrics.
     * <p>
     * Used by lyric renderers to look up per-verse baseline Y positions that
     * are uniform across every line in the song. Must be set via
     * {@link #setSongLayoutMetrics} before any rendering pass runs.
     */
    public SongLayoutMetrics getSongLayoutMetrics() {
        return songLayoutMetrics;
    }

    /** Sets the song-wide layout metrics. */
    @Initializer
    public void setSongLayoutMetrics(SongLayoutMetrics metrics) {
        this.songLayoutMetrics = metrics;
    }

    /** Returns the song-wide lyric render metrics. */
    public LyricRenderMetrics getLyricRenderMetrics() {
        return lyricRenderMetrics;
    }

    /** Sets the song-wide lyric render metrics. */
    @Initializer
    public void setLyricRenderMetrics(LyricRenderMetrics metrics) {
        this.lyricRenderMetrics = metrics;
    }

    /** Returns the element currently being edited in the lyric overlay, or null. */
    @Nullable
    public StaffElement getActivelyEditedElement() {
        return activelyEditedElement;
    }

    /** Sets the element being edited; pass null when no lyric editor is open. */
    public void setActivelyEditedElement(@Nullable StaffElement element) {
        this.activelyEditedElement = element;
    }

    /**
     * Returns the selection provider for checking element selection state.
     *
     * @return The selection provider, or null if not available
     */
    public LineComponent.@Nullable SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    /**
     * Sets the selection provider for checking element selection state.
     *
     * @param selectionProvider The selection provider from LineComponent
     */
    public void setSelectionProvider(LineComponent.@Nullable SelectionProvider selectionProvider) {
        this.selectionProvider = selectionProvider;
    }

    /**
     * Returns whether the score is in edit mode.
     */
    public boolean isEditMode() {
        return editMode;
    }

    /**
     * Sets whether the score is in edit mode.
     *
     * @param editMode true if in edit mode
     */
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    /**
     * Returns the color used to render selected elements and beams.
     * Defaults to {@link Score#SELECTION_STROKE_COLOR}; override during an
     * element pitch-drag to use {@code INSERTION_NOTE_COLOR} instead.
     */
    public Color getSelectionColor() {
        return selectionColor;
    }

    /**
     * Sets the color used to render selected elements and beams.
     *
     * @param selectionColor the color to use
     */
    public void setSelectionColor(Color selectionColor) {
        this.selectionColor = selectionColor;
    }

    /**
     * Returns the rendering color for the element at the given index.
     * <p>
     * Covers playback highlighting, selection, and hover (replaced-element) highlighting.
     * Does not include grace-cancel coloring (handled in LineRenderer).
     * <p>
     * Outside edit mode this always returns {@link Color#BLACK}: selection/hover/preview
     * highlights are edit-mode concepts, and playback rendering runs only while the score
     * is in edit mode.
     */
    public Color getElementColor(int elementIndex) {
        if (!editMode) {
            return Color.BLACK;
        }

        if (isElementPlaying(elementIndex) || isElementInPlayingTie(elementIndex)) {
            return Score.getPlayingNoteColor();
        }

        if (selectionProvider != null
                && selectionProvider.isElementSelected(elementIndex, lineIndex)) {
            return selectionColor;
        }

        var matched = PreviewElementManager.getHoveredElementLocation();

        if (matched != null && matched.matches(lineIndex, elementIndex)) {
            return REPLACED_ELEMENT_COLOR;
        }

        return Color.BLACK;
    }

    /**
     * Returns the index of the currently playing note, or -1 if none.
     */
    public int getPlayingNoteIndex() {
        return playingNoteIndex;
    }

    /**
     * Sets the index of the currently playing note.
     *
     * @param playingNoteIndex the playing note index, or -1 if none
     */
    public void setPlayingNoteIndex(int playingNoteIndex) {
        this.playingNoteIndex = playingNoteIndex;
    }

    /**
     * Returns the index of the currently playing grace note, or -1 if none.
     */
    public int getPlayingGraceNoteIndex() {
        return playingGraceNoteIndex;
    }

    /**
     * Sets the index of the currently playing grace note.
     *
     * @param playingGraceNoteIndex the playing grace note index, or -1 if none
     */
    public void setPlayingGraceNoteIndex(int playingGraceNoteIndex) {
        this.playingGraceNoteIndex = playingGraceNoteIndex;
    }

    /**
     * Returns whether the given element index is currently playing
     * (either as the primary note or as a grace note).
     *
     * @param elementIndex the element index to check
     * @return true if the element is currently playing
     */
    public boolean isElementPlaying(int elementIndex) {
        return elementIndex >= 0
                && (elementIndex == playingNoteIndex || elementIndex == playingGraceNoteIndex);
    }

    /**
     * Returns whether the given element index is part of the same tie span
     * as the currently playing note, making it co-highlighted during playback.
     *
     * @param elementIndex the element index to check
     * @return true if the element is in the same tie as the playing note
     */
    public boolean isElementInPlayingTie(int elementIndex) {
        if (playingNoteIndex < 0 || currentLine == null) {
            return false;
        }

        var tieSpan = currentLine.getTies().findSpan(playingNoteIndex);
        return tieSpan != null
                && tieSpan.getStart() <= elementIndex
                && elementIndex <= tieSpan.getEnd();
    }

    /**
     * Returns whether the playing note falls within the given span.
     * Used by tie rendering to determine whether a specific tie arc should
     * be highlighted during playback.
     *
     * @param span the span to test
     * @return true if the playing note index is within [span.start, span.end]
     */
    public boolean isPlayingNoteInSpan(Span span) {
        return playingNoteIndex >= 0
                && playingNoteIndex >= span.getStart()
                && playingNoteIndex <= span.getEnd();
    }

    /**
     * Sets a precise X coordinate for the next element render, bypassing layout lookup and
     * {@code element.getXPos()}. Used by the preview element so that {@link NoteRenderer}
     * applies device-pixel snapping to the raw computed double directly, matching the
     * path used for laid-out song elements. Call {@link #clearOverrideElementX()} after
     * rendering to reset.
     *
     * @param xSs the exact X coordinate in staff spaces
     */
    public void setOverrideElementXSs(double xSs) {
        this.overrideElementXSs = xSs;
    }

    /**
     * Returns whether an override element X is currently active.
     */
    public boolean hasOverrideElementX() {
        return !Double.isNaN(overrideElementXSs);
    }

    /**
     * Returns the override element X. Only valid when {@link #hasOverrideElementX()} is true.
     */
    public double getOverrideElementXSs() {
        return overrideElementXSs;
    }

    /**
     * Clears the override set by {@link #setOverrideElementXSs(double)}.
     */
    public void clearOverrideElementX() {
        overrideElementXSs = Double.NaN;
    }

    /**
     * Sets a preview shift to apply to all elements at {@code fromIndex} and beyond.
     * Used during grace note insert mode to visually displace subsequent elements
     * rightward to show where the host note will be inserted.
     *
     * @param fromIndex first element index to shift (inclusive)
     * @param shiftSs   shift amount in staff spaces (must be &gt;= 0)
     */
    public void setPreviewShift(int fromIndex, double shiftSs) {
        this.previewShiftFromIndex = fromIndex;
        this.previewShiftSs = shiftSs;
    }

    /** Returns whether a preview shift is currently active. */
    public boolean hasPreviewShift() {
        return previewShiftFromIndex >= 0;
    }

    /** Returns the first element index to shift. Only valid when {@link #hasPreviewShift()} is true. */
    public int getPreviewShiftFromIndex() {
        return previewShiftFromIndex;
    }

    /** Returns the shift amount in staff spaces. Only valid when {@link #hasPreviewShift()} is true. */
    public double getPreviewShiftSs() {
        return previewShiftSs;
    }

    /** Clears the preview shift set by {@link #setPreviewShift(int, double)}. */
    public void clearPreviewShift() {
        previewShiftFromIndex = -1;
        previewShiftSs = 0;
    }

    /**
     * Returns the resolved line thicknesses (LilyPond multiplier-derived).
     */
    public LineThickness getLineThickness() {
        return LineThickness.getInstance();
    }

    /**
     * Returns whether debug rendering is enabled via DEBUG environment variable.
     * <p>
     * This is intended for development-time debugging only.
     * For user-facing inspection, use {@link #isInspectorEnabled()}.
     */
    public boolean isDebugEnabled() {
        return DebugState.isDebugEnabled();
    }

    /**
     * Returns whether the inspector is enabled.
     * <p>
     * When enabled, renderers should draw additional visualization
     * such as bounding boxes and margin regions. This is the user-facing
     * debug feature controlled via the UI.
     */
    public boolean isInspectorEnabled() {
        return DebugState.isInspectorEnabled();
    }

    /**
     * Returns whether layout boxes should be shown for debugging.
     */
    public boolean isShowLayoutBoxes() {
        return DebugState.isShowLayoutBoxes();
    }

    /**
     * Returns whether bounding boxes should be shown for debugging.
     */
    public boolean isShowBoundingBoxes() {
        return DebugState.isShowBoundingBoxes();
    }

    /**
     * Returns whether margins should be shown for debugging.
     */
    public boolean isShowMargins() {
        return DebugState.isShowMargins();
    }
}
