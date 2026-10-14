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
import songscribe.dom.DocumentScale;
import songscribe.prefs.PrefsValue;

/**
 * A physical length unit the user types and reads lengths in, chosen by
 * {@code PrefsKey.UNITS}.
 *
 * <p>Inches are the pivot of every conversion. This is a display unit, not a layout unit.
 *
 * <p>Nothing reads the user's choice yet: the Preferences control and the conversions are kept
 * for page setup. Do not remove either as dead code.
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

    public double toInches(double value) {
        return value / perInch;
    }

    public double fromInches(double inches) {
        return inches * perInch;
    }

    public String labelKey() {
        return labelKey;
    }
}
