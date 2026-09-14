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

import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.engraving.StaffHeaderMetrics;

/**
 * The header a staff line opens with: the treble clef and the key signature, each at the X the
 * layout placed it.
 * <p>
 * This names a notation concept the document has, though the document stores none: every line
 * opens with a clef, so no line records one, and a line's own key lives on {@link Line} — a
 * change part-way through a line is a {@link KeyChangeElement}. Layout builds one header per line
 * on every pass and the renderer paints it from there. The header is not an element of the line:
 * it takes part in no spacing and contributes nothing to the staff's bounds, being positioned
 * absolutely at the line start.
 * <p>
 * The signature draws the line's {@linkplain Line#getRunningKey() running key} — the line's own
 * key where it establishes one, and the key it inherits where it does not — so every line's
 * header shows the key actually in effect where the line begins.
 * <p>
 * Only the treble clef exists. The clef glyph's width is the font's advance for the G clef, and
 * the gap from there to the signature is the layout's ({@link StaffHeaderMetrics#CLEF_GAP_SS}),
 * so the header carries neither: {@link #keySignatureXSs} is where the layout put the signature,
 * and nothing here can disagree with it.
 * <p>
 * The signature's measurements are {@link Key#signatureWidthSs()}'s and
 * {@link Key#signatureHeightSs()}'s — the same run of accidentals the renderer draws, reached by
 * the same calls — so the header and a cautionary key change at the end of a line can never
 * disagree about how large one signature is.
 *
 * @param clefXSs         X of the clef glyph's origin, in staff spaces from the line's origin
 * @param keySignatureXSs X of the signature's left edge, in staff spaces from the line's origin
 * @param key             the key the signature draws; {@link Key#NO_ACCIDENTALS} draws nothing
 *                        and measures zero in both dimensions
 */
public record StaffHeader(double clefXSs, double keySignatureXSs, Key key) {}
