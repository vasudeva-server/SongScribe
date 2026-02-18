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

import java.awt.Font;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.Line;
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
    private Line currentLine;
    private int middleLineY;
    private int lineIndex;

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
     * Returns the Fughetta music notation font.
     * <p>
     * This font contains glyphs for notes, rests, accidentals, clefs, etc.
     */
    public @NotNull Font getFughettaFont() {
        return BaseElementRenderer.FUGHETTA;
    }

    /**
     * Returns the Fughetta font sized for grace notes.
     */
    public @NotNull Font getFughettaGraceFont() {
        return BaseElementRenderer.FUGHETTA_GRACE;
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
