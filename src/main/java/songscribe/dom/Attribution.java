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

package songscribe.dom;

import java.awt.Font;

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

    /** Margin from attribution bottom to score */
    public static final double ATTRIBUTION_MARGIN_BOTTOM_SS = 2.0;  // 16px

    /** The attribution text (composer, arranger, etc.). */
    private String text;

    /** Whether the text is right-aligned (default: true). */
    private boolean isRightAligned = true;

    /**
     * Creates an attribution with the specified text.
     *
     * @param text The attribution text
     */
    public Attribution(String text) {
        this.text = text;
        setMarginSs(0, 0, ATTRIBUTION_MARGIN_BOTTOM_SS, 0);
    }

    /**
     * Returns the attribution text.
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the attribution text.
     *
     * @param text The attribution text
     */
    public void setText(String text) {
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
        isRightAligned = rightAligned;
    }

    /**
     * Computes the content width from the actual attribution text.
     *
     * @param font the attribution font
     * @return width in staff-space units
     */
    public double computeContentWidthSs(Font font) {
        return ScaleContext.textWidthSs(font, text);
    }

    /**
     * Computes the content height from the attribution font.
     *
     * @param font the attribution font
     * @return height in staff-space units
     */
    public double computeContentHeightSs(Font font) {
        return ScaleContext.textHeightSs(font);
    }

    @Override
    public double getContentWidthPx() {
        throw new UnsupportedOperationException(
            "Attribution width is font-dependent; use computeContentWidthSs(font) instead.");
    }

    @Override
    public double getContentHeightPx() {
        throw new UnsupportedOperationException(
            "Attribution height is font-dependent; use computeContentHeightSs(font) instead.");
    }

    @Override
    public double getContentWidthSs() {
        throw new UnsupportedOperationException(
            "Attribution width is font-dependent; use computeContentWidthSs(font) instead.");
    }

    @Override
    public double getContentHeightSs() {
        throw new UnsupportedOperationException(
            "Attribution height is font-dependent; use computeContentHeightSs(font) instead.");
    }
}
