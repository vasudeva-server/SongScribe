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

import org.jspecify.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.Interval;
import songscribe.music.Line;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.menu.DebugState;

/**
 * Context passed to element renderers containing shared rendering state.
 * <p>
 * Avoids passing many individual parameters to every render method.
 * Contains both immutable data (composition, fonts) and mutable state
 * (current line being rendered, middleLineY).
 * <p>
 * Note: Named ElementRenderContext to avoid conflict with the existing
 * RenderContext interface used by the legacy renderer.
 */
public class ElementRenderContext {

    private final Composition composition;
    @Nullable
    private Line currentLine;
    private double middleLineYSs;
    private int lineIndex;
    @Nullable
    private LayoutResult layoutResult;
    private LineComponent.@Nullable SelectionProvider selectionProvider;
    private boolean editMode;
    private Color selectionColor = Score.getSelectionStrokeColor();
    private int playingNoteIndex = -1;
    private int playingGraceNoteIndex = -1;
    private int currentElementIndex = -1;
    private double overrideElementXSs = Double.NaN;

    /**
     * Creates a render context for the given composition.
     *
     * @param composition The composition being rendered
     */
    public ElementRenderContext(Composition composition) {
        this.composition = composition;
    }

    /**
     * Returns the composition being rendered.
     */
    public Composition getComposition() {
        return composition;
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
     * Returns the index of the current line within the composition.
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
     * Renderers can use this to get pre-calculated positions instead of
     * computing them during rendering.
     *
     * @return The layout result, or null if not available
     */
    public @Nullable LayoutResult getLayoutResult() {
        return layoutResult;
    }

    /**
     * Sets the layout result for the current line.
     *
     * @param layoutResult The layout result from LayoutEngine
     */
    public void setLayoutResult(@Nullable LayoutResult layoutResult) {
        this.layoutResult = layoutResult;
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
     * Covers edit mode, playback highlighting, and selection. Does not include
     * note-specific hover or grace-cancel coloring (handled in LineRenderer).
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
     * Returns whether the given element index is part of the same tie interval
     * as the currently playing note, making it co-highlighted during playback.
     *
     * @param elementIndex the element index to check
     * @return true if the element is in the same tie as the playing note
     */
    public boolean isElementInPlayingTie(int elementIndex) {
        if (playingNoteIndex < 0 || currentLine == null) {
            return false;
        }

        var interval = currentLine.getTies().findInterval(playingNoteIndex);
        return interval != null
                && interval.getStart() <= elementIndex
                && elementIndex <= interval.getEnd();
    }

    /**
     * Returns whether the playing note falls within the given interval.
     * Used by tie rendering to determine whether a specific tie arc should
     * be highlighted during playback.
     *
     * @param interval the interval to test
     * @return true if the playing note index is within [interval.start, interval.end]
     */
    public boolean isPlayingNoteInInterval(Interval interval) {
        return playingNoteIndex >= 0
                && playingNoteIndex >= interval.getStart()
                && playingNoteIndex <= interval.getEnd();
    }

    /**
     * Sets a precise X coordinate for the next element render, bypassing layout lookup and
     * {@code element.getXPos()}. Used by the preview element so that {@link NoteRenderer}
     * applies device-pixel snapping to the raw computed double directly, matching the
     * path used for laid-out composition elements. Call {@link #clearOverrideElementX()} after
     * rendering to reset.
     *
     * @param x the exact X coordinate in local (component) space
     */
    public void setOverrideElementXSs(double x) {
        this.overrideElementXSs = x;
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
