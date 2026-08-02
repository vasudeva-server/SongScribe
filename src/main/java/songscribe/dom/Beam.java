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

package songscribe.dom;

/**
 * Represents a beam group connecting a contiguous run of notes.
 * <p>
 * All computed beam geometry lives in LayoutResult.BeamLayout;
 * this class carries only the anchor/end pair inherited from {@link Span}.
 */
public class Beam extends Span {

    public Beam(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    @Override
    protected Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Beam(newAnchor, newEnd);
    }

    @Override
    public double getContentHeightSs() {
        return 0;
    }

    @Override
    public double getContentWidthPx() {
        return 0;
    }

    @Override
    public double getContentHeightPx() {
        return 0;
    }

    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(1.0, endXSs - anchorXSs);
    }
}
