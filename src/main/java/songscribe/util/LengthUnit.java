/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.util;

import songscribe.Strings;
import songscribe.prefs.PrefsValue;

/**
 * A physical length unit the user types and reads lengths in, chosen by
 * {@code PrefsKey.UNITS}.
 *
 * <p>Inches are the pivot: every conversion here goes through them, because inches are what
 * the layout's own limits are expressed in ({@code PageModel.MIN_LINE_WIDTH_INCHES} and its
 * maximum) and what {@link songscribe.dom.ScaleContext#inchesToSs} takes. Converting a value
 * to a unit and back returns it unchanged up to floating-point rounding.
 *
 * <p>This is a display unit, not a layout unit. Staff spaces and pixels — and the rules for
 * moving between them — are a separate matter, covered by
 * {@code .claude/guides/spatial-units.md}; nothing here is a suffix that guide governs.
 *
 * <p><b>Nothing reads the user's choice yet.</b> The Preferences dialog offers it and the
 * conversions below are unused, because the line-width field that displayed inches or
 * centimetres is gone. Page setup is where the choice is going, and the control stays in
 * Preferences until it lands there. Do not remove either as dead code.
 */
public enum LengthUnit implements PrefsValue {

    INCHES(1, Strings.LABEL_UNIT_INCHES),
    CENTIMETERS(GraphicUtils.CM_PER_INCH, Strings.LABEL_UNIT_CM);

    private final double perInch;
    private final String labelKey;

    @Override
    public String storedValue() {
        return name();
    }

    LengthUnit(double perInch, String labelKey) {
        this.perInch = perInch;
        this.labelKey = labelKey;
    }

    /**
     * Converts a length expressed in this unit to inches.
     *
     * @param value the length, in this unit
     * @return the same length in inches, which for {@link #INCHES} is {@code value} itself
     */
    public double toInches(double value) {
        return value / perInch;
    }

    /**
     * Converts a length expressed in inches to this unit.
     *
     * @param inches the length, in inches
     * @return the same length in this unit, which for {@link #INCHES} is {@code inches} itself
     */
    public double fromInches(double inches) {
        return inches * perInch;
    }

    /**
     * @return the {@link Strings} constant naming this unit as the user sees it abbreviated
     *         beside a field ({@code cm}, {@code inches}) — a key rather than resolved text,
     *         so that deciding which unit is in play stays free of locale
     */
    public String labelKey() {
        return labelKey;
    }
}
