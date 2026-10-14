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

package songscribe.engraving;

/**
 * Horizontal metrics for the staff header (clef, key signature, and the space before the
 * music), ported from LilyPond's {@code space-alist} entries, in staff spaces. Only the
 * direction that arises here is ported: a cancellation precedes the key signature, never
 * follows it.
 */
public final class StaffHeaderMetrics {

    /**
     * Gap from the clef's right edge to the key signature's left edge.
     */
    public static final double CLEF_GAP_SS = 0.82;

    /**
     * Gap from the key signature's right edge to the first note, at natural spacing: a crowded
     * line may compress it but never stretches it.
     */
    public static final double KEY_SIGNATURE_FIRST_NOTE_GAP_SS = 2.5;

    /**
     * Span from the clef's <em>left</em> edge to the first note when no key signature
     * intervenes. A floor on the whole span, not a gap added past the clef.
     */
    public static final double CLEF_FIRST_NOTE_SPAN_SS = 5.0;

    private StaffHeaderMetrics() {}
}
