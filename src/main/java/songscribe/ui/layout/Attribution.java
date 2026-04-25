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

import java.awt.Font;

import songscribe.error.RuntimeError;

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
        setMarginSs(0, 0, LayoutStylesheet.ATTRIBUTION_MARGIN_BOTTOM_SS, 0);
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
        this.isRightAligned = rightAligned;
    }

    /**
     * Computes the content width from the actual attribution text.
     *
     * @param font the attribution font
     * @return width in staff-space units
     */
    public double computeContentWidthSs(Font font) {
        return ScaleContext.getInstance().textWidthSs(font, text);
    }

    @Override
    public double getContentWidthSs() {
        return computeContentWidthSs(requireAttributionFont());
    }

    /**
     * Returns the content height in staff-space units, derived from the song's
     * attribution font via {@code parentLine}.
     * <p>
     * {@code parentLine} must be non-null. Use {@link ScaleContext#textHeightSs(Font)}
     * directly when the font is already in hand.
     */
    @Override
    public double getContentHeightSs() {
        return ScaleContext.getInstance().textHeightSs(requireAttributionFont());
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.getInstance().toPixels(getContentWidthSs());
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getContentHeightSs());
    }

    private Font requireAttributionFont() {
        var parentLine = getParentLine();

        if (parentLine == null) {
            throw RuntimeError.exit(
                "Attribution content dimensions requested with null parentLine");
        }

        return parentLine.getSong().getAttributionFont();
    }
}
