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
 * Shared fixture vocabulary for the starting-tempo tests, which span three packages
 * ({@code songscribe.dom}, {@code songscribe.ui}, {@code songscribe.ui.component}) and would
 * otherwise each redeclare the same tempo values and attachment helpers.
 *
 * <p>Dialog-side helpers live in {@code songscribe.ui.InitialTempoConfirmsTestSupport}; this
 * class stays free of any UI dependency so DOM tests can use it.
 */
public final class InitialTempoTestSupport {

    /** The song's starting tempo before an edit displaces it. */
    public static final int ORIGINAL_TEMPO_BPM = 100;

    /** The tempo an incoming new-first element brings with it, colliding with the original. */
    public static final int TARGET_TEMPO_BPM = 140;

    private InitialTempoTestSupport() {}

    /** A {@link Tempo} distinguishable from any other by its visible BPM alone. */
    public static Tempo tempoOf(int visibleBpm) {
        var tempo = new Tempo();
        tempo.setVisibleTempo(visibleBpm);
        return tempo;
    }

    public static void attachTempo(StaffElement element, Tempo tempo) {
        element.addAttachment(new TempoChangeAttachment(element, tempo));
    }

    /** Attaches a beat change, whose value is irrelevant — only its presence is ever asserted. */
    public static void attachBeatChange(StaffElement element) {
        element.addAttachment(
            new BeatChangeAttachment(element, new BeatChange(Duration.CROTCHET, Duration.CROTCHET)));
    }

    /** Counts every {@link TempoChangeAttachment} on any element in the song. */
    public static long countTempoChangeAttachments(Song song) {
        var total = 0L;

        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            var line = song.getLine(lineIndex);

            for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
                total += line.getElement(elementIndex).getAttachments().stream()
                    .filter(TempoChangeAttachment.class::isInstance)
                    .count();
            }
        }

        return total;
    }
}
