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

package songscribe.ui.layout;

import org.jetbrains.annotations.NotNull;

/**
 * Represents attribution text (composer/arranger) displayed on the staff.
 * <p>
 * Attribution is typically rendered at the right edge of the staff. When it collides
 * with above-staff attachments (tempo, fermata, etc.), the staff moves down vertically
 * to accommodate both elements.
 * <p>
 * Right-aligned positioning: {@code x = staffWidth - attributionWidth - margin}
 */
public class Attribution extends LineElement {

    /** The attribution text (composer, arranger, etc.). */
    private @NotNull String text;

    /** Whether the text is right-aligned (default: true). */
    private boolean isRightAligned = true;

    /** Approximate character width for stub calculations. */
    private static final double CHAR_WIDTH = 8.0;

    /** Approximate text height for stub calculations. */
    private static final double TEXT_HEIGHT = 16.0;

    /**
     * Creates an attribution with the specified text.
     *
     * @param text The attribution text
     */
    public Attribution(@NotNull String text) {
        this.text = text;
        setMargin(
            LayoutStylesheet.px(LayoutStylesheet.ATTRIBUTION_PADDING_MU),
            LayoutStylesheet.px(LayoutStylesheet.ATTRIBUTION_PADDING_MU),
            LayoutStylesheet.px(LayoutStylesheet.ATTRIBUTION_MARGIN_BOTTOM_MU),
            LayoutStylesheet.px(LayoutStylesheet.ATTRIBUTION_PADDING_MU)
        );
    }

    /**
     * Returns the attribution text.
     */
    public @NotNull String getText() {
        return text;
    }

    /**
     * Sets the attribution text.
     *
     * @param text The attribution text
     */
    public void setText(@NotNull String text) {
        this.text = text;
    }

    /**
     * Returns whether the attribution is right-aligned.
     */
    public boolean isRightAligned() {
        return isRightAligned;
    }

    /**
     * Sets whether the attribution is right-aligned.
     *
     * @param rightAligned true for right-aligned, false for left-aligned
     */
    public void setRightAligned(boolean rightAligned) {
        this.isRightAligned = rightAligned;
    }

    /**
     * Returns the content width based on text length.
     * <p>
     * Stub implementation: approximates width based on character count.
     * Phase 6 rendering will use actual font metrics.
     */
    @Override
    public double getContentWidth() {
        return text.length() * CHAR_WIDTH;
    }

    /**
     * Returns the content height.
     * <p>
     * Stub implementation: returns typical text height.
     * Phase 6 rendering will use actual font metrics.
     */
    @Override
    public double getContentHeight() {
        return TEXT_HEIGHT;
    }

    /**
     * Calculates the X position for right-aligned rendering.
     *
     * @param staffWidth The width of the staff
     * @param rightMargin The right margin from the staff edge
     * @return The X position for the attribution
     */
    public double calculateRightAlignedX(double staffWidth, double rightMargin) {
        return staffWidth - getContentWidth() - rightMargin;
    }
}
