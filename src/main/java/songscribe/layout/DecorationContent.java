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

package songscribe.layout;

/**
 * The typeset content of one above-staff decoration: every piece it draws, positioned and measured
 * once, by the layout pass that placed it.
 * <p>
 * A decoration's content rides on its layout — see
 * {@link LayoutResult.DecorationLayout.Typeset} — so that the drawn ink cannot disagree with the
 * box that layout, hit testing and vertical stacking measured. The renderer walks the content and
 * decides nothing: it resolves no font, measures no advance and computes no position of its own.
 * That is what makes the agreement structural rather than a matter of two passes staying in step,
 * and it keeps text measurement out of the paint loop, where it would run for every decoration on
 * screen on every scroll and every zoom step.
 * <p>
 * Every measurement a member carries is in staff spaces and therefore zoom-invariant, the document
 * scale being a compile-time constant. Content is rebuilt by every layout pass and holds no cache:
 * nothing has to invalidate it, because nothing outlives the layout that produced it.
 * <p>
 * The hierarchy is sealed so the one site that must know which kind it holds dispatches without a
 * {@code default} arm.
 */
public sealed interface DecorationContent permits AttributionContent, MetronomeContent {}
