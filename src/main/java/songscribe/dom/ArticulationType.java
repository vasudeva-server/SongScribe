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

import org.jspecify.annotations.Nullable;

import songscribe.shape.AccentShape;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * The kinds of articulation a note can carry, and what each one draws.
 * <p>
 * An articulation is drawn opposite the stem, outward from the staff:
 * <ul>
 *   <li>For downward stems: articulations go above the staff</li>
 *   <li>For upward stems: articulations go below the staff</li>
 * </ul>
 * The kind decides its ink — {@link #ink} — and the size that ink reserves when stacked —
 * {@link #intrinsicWidthSs} and {@link #intrinsicHeightSs} — which are the ink's own bounds.
 */
public enum ArticulationType {

    /**
     * Staccato - short, detached notes.
     * Drawn closest to the note head.
     */
    STACCATO(
        ArticulationType.STACCATO_MIDI_DURATION_PERCENT,
        new ArticulationInk.Glyphs(SMuFLGlyph.ARTIC_STACCATO_ABOVE, SMuFLGlyph.ARTIC_STACCATO_BELOW)),

    /**
     * Accent - emphasized notes.
     * Drawn outside of staccato when both are present.
     */
    ACCENT(null, new ArticulationInk.Outline(AccentShape.accent()));

    /** How much of a staccato note's written duration it sounds for in MIDI playback. */
    private static final int STACCATO_MIDI_DURATION_PERCENT = 33;

    /** MIDI duration as a percentage of the note's full duration, or null for no override. */
    private final @Nullable Integer midiDurationPercent;

    private final ArticulationInk ink;
    private final double intrinsicWidthSs;
    private final double intrinsicHeightSs;

    ArticulationType(@Nullable Integer midiDurationPercent, ArticulationInk ink) {
        this.midiDurationPercent = midiDurationPercent;
        this.ink = ink;

        var bounds = ink.boundsSs();
        intrinsicWidthSs = bounds.widthSs();
        intrinsicHeightSs = bounds.heightSs();
    }

    /**
     * Returns the MIDI duration as a percentage of the note's full duration.
     *
     * @return the percentage, or null if this articulation does not override the sounding duration
     */
    public @Nullable Integer getMidiDurationPercent() {
        return midiDurationPercent;
    }

    /**
     * Returns what this articulation draws: the staccato dot as a glyph pair, one per side of the
     * note, and the accent as the {@link AccentShape#accent()} wedge, one outline for either side.
     *
     * @return the ink; one per constant, never null
     */
    public ArticulationInk ink() {
        return ink;
    }

    /**
     * Returns the width of this articulation's ink in staff spaces.
     *
     * @return the width of {@link #ink()}'s bounds
     */
    public double intrinsicWidthSs() {
        return intrinsicWidthSs;
    }

    /**
     * Returns the height of this articulation's ink in staff spaces.
     *
     * @return the height of {@link #ink()}'s bounds
     */
    public double intrinsicHeightSs() {
        return intrinsicHeightSs;
    }
}
