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

import java.awt.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.layout2.LayoutResult;
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

    /** Default X position for key signature (after clef). */
    public static final int DEFAULT_LEADING_KEYS_POS = 32;

    private final Composition composition;
    private Line currentLine;
    private int middleLineY;
    private int lineIndex;
    private int leadingKeysPos = DEFAULT_LEADING_KEYS_POS;
    private LayoutResult layoutResult;
    private LineComponent.SelectionProvider selectionProvider;
    private boolean editMode;
    private double overrideNoteX = Double.NaN;

    /**
     * Creates a render context for the given composition.
     *
     * @param composition The composition being rendered
     */
    public ElementRenderContext(@NotNull Composition composition) {
        this.composition = composition;
    }

    /**
     * Returns the composition being rendered.
     */
    public @NotNull Composition getComposition() {
        return composition;
    }

    /**
     * Returns the music notation font.
     * <p>
     * This font contains glyphs for notes, rests, accidentals, clefs, etc.
     */
    public @NotNull Font getMusicFont() {
        return BaseElementRenderer.MUSIC_FONT;
    }

    /**
     * Returns the music notation font sized for grace notes.
     */
    public @NotNull Font getMusicGraceFont() {
        return BaseElementRenderer.MUSIC_FONT_GRACE;
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
     * relative to the component's coordinate system.
     */
    public int getMiddleLineY() {
        return middleLineY;
    }

    /**
     * Sets the Y coordinate of the middle staff line.
     *
     * @param middleLineY Y coordinate in component coordinates
     */
    public void setMiddleLineY(int middleLineY) {
        this.middleLineY = middleLineY;
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
     * Returns the X position where key signature accidentals begin.
     */
    public int getLeadingKeysPos() {
        return leadingKeysPos;
    }

    /**
     * Sets the X position where key signature accidentals begin.
     *
     * @param leadingKeysPos X position in pixels
     */
    public void setLeadingKeysPos(int leadingKeysPos) {
        this.leadingKeysPos = leadingKeysPos;
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
     * Returns the selection provider for checking note selection state.
     *
     * @return The selection provider, or null if not available
     */
    public @Nullable LineComponent.SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    /**
     * Sets the selection provider for checking note selection state.
     *
     * @param selectionProvider The selection provider from LineComponent
     */
    public void setSelectionProvider(@Nullable LineComponent.SelectionProvider selectionProvider) {
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
     * Sets a precise X coordinate for the next note render, bypassing layout lookup and
     * {@code note.getXPos()}. Used by the edit note preview so that {@link NoteRenderer}
     * applies device-pixel snapping to the raw computed double directly, matching the
     * path used for laid-out composition notes. Call {@link #clearOverrideNoteX()} after
     * rendering to reset.
     *
     * @param x the exact X coordinate in local (component) space
     */
    public void setOverrideNoteX(double x) {
        this.overrideNoteX = x;
    }

    /**
     * Returns whether an override note X is currently active.
     */
    public boolean hasOverrideNoteX() {
        return !Double.isNaN(overrideNoteX);
    }

    /**
     * Returns the override note X. Only valid when {@link #hasOverrideNoteX()} is true.
     */
    public double getOverrideNoteX() {
        return overrideNoteX;
    }

    /**
     * Clears the override set by {@link #setOverrideNoteX(double)}.
     */
    public void clearOverrideNoteX() {
        overrideNoteX = Double.NaN;
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
