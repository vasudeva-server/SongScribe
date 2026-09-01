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
 * Horizontal metrics for the staff header — the clef, the key signature and the space
 * they leave before the music starts — ported from LilyPond. All values are in staff spaces.
 * <p>
 * The gaps between the header's parts come from the {@code space-alist} entries of
 * LilyPond's break-aligned grobs, read by {@code Break_alignment_interface} and
 * {@code Staff_spacing::get_spacing}. The spacing type decides which edge the distance is
 * measured from: {@code extra-space} and {@code shrink-space} measure from the left item's
 * right edge, {@code minimum-fixed-space} from its left edge.
 * <p>
 * The tables are directional: LilyPond reads the {@code space-alist} of the <em>left</em>
 * item and looks the gap up under the <em>right</em> item's name, so the entry to port is the
 * one for the order the two parts actually appear in. Only one order arises here — a
 * cancellation always precedes the key signature it makes way for, never follows it, per the
 * policy on {@code songscribe.dom.Key#accidentalsFrom} — so only that direction is ported.
 */
public final class StaffHeaderMetrics {

    /**
     * Gap from the clef's right edge to the key signature's left edge.
     * LilyPond {@code Clef} {@code space-alist}: the {@code key-signature} entry,
     * an {@code extra-space} of {@value}.
     */
    public static final double CLEF_GAP_SS = 0.82;

    /**
     * Gap from the key signature's right edge to the first note.
     * LilyPond {@code KeySignature} {@code space-alist}: the {@code first-note} entry, a
     * {@code shrink-space} of {@value}. {@code shrink-space}
     * makes this the distance at natural spacing, which a crowded line may compress but
     * never stretches.
     */
    public static final double KEY_SIGNATURE_FIRST_NOTE_GAP_SS = 2.5;

    /**
     * Span from the clef's <em>left</em> edge to the first note when no key signature
     * intervenes. LilyPond {@code Clef} {@code space-alist}: the {@code first-note} entry, a
     * {@code minimum-fixed-space} of {@value} — a floor on the whole
     * span rather than a gap added past the clef, so a clef narrower than this leaves the
     * remainder as open space.
     */
    public static final double CLEF_FIRST_NOTE_SPAN_SS = 5.0;

    private StaffHeaderMetrics() {}
}
