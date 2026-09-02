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

/**
 * An element whose vertical extent is fixed by the element itself, independent of layout.
 * <p>
 * A decoration placed above or below the staff pushes the skyline out by its height, and
 * that height has one of two provenances. Either the element owns it — a constant, or the
 * bounds of the element's own glyph — or layout computes it, from a font, from where the
 * element's columns landed, or from typeset content built during the pass. This interface
 * is the first kind. The placement methods that take it read the height off the element
 * rather than accepting one, so a caller cannot reserve a box that disagrees with the
 * element it is reserving for.
 * <p>
 * The second kind is deliberately outside it, and the exclusions are not omissions: a text
 * annotation is sized by the annotation font, which no element in this package can reach; a
 * tuplet is as tall as its bracket only when a bracket is drawn, which layout decides; and
 * an attribution, song tempo mark or metronome mark takes its height from the typeset
 * content the layout pass builds for it. Each of those supplies its height explicitly, to a
 * placement method that accepts one.
 * <p>
 * Width is not here, and its absence is deliberate too. Three of this interface's
 * implementors reserve a width that is not their own — a trill reserves the span between
 * its endpoints, and a hairpin and a text dynamic reserve widths resolved against their
 * neighbours — so a width declared here would be a promise half the members could not keep.
 * A width query belongs on the individual element that has one.
 */
public interface IntrinsicHeight {

    /**
     * Returns the height this element reserves when stacked, in staff spaces.
     *
     * @return the element's own vertical extent, in staff spaces; never derived from layout
     *     state, so it is the same answer at every point in a layout pass
     */
    double intrinsicHeightSs();
}
