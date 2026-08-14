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
package songscribe.ui.dialog.backend;

import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.MessageCenter;
import songscribe.message.notification.TempoDidChangeNotification;

/**
 * The judgments Song Settings makes about the document: what it may refuse, what it reads out
 * of the song to show, and what it writes back.
 *
 * <p>Each is stated against the song rather than against a dialog, which is what lets the
 * dialog hold none.
 */
public final class SongSettingsRules {

    private SongSettingsRules() {}

    /**
     * Whether every line of {@code song} still fits once {@code lyricsFont} and
     * {@code newLineWidthSs} are applied.
     *
     * <p>A wider lyrics font widens every syllable, which widens the minimum spacing between
     * columns; a narrower line width shrinks the budget those columns have to fit into. Either
     * can push a line past the staff margin, and committing that anyway leaves the line laid
     * out on its collision floors with its tail clipped in red (refs #696). Refusing the change
     * up front keeps a fitting line fitting.
     *
     * <p>The two solves are deliberately asymmetric: the candidate font is measured at
     * {@code newLineWidthSs}, the current font at the width in effect now. Measuring both at
     * the pending width would make a width-only change compare against itself and never
     * refuse.
     *
     * <p><strong>A line that already overflows never refuses the change.</strong> The user has
     * to be able to reach a narrower font or a wider line from a document that is already
     * broken, and a rule that blocked every change while a line overflowed would make that
     * impossible. It is the same rule {@code LyricEditor} applies to a widening syllable edit.
     *
     * <p>Changing neither input is accepted without measuring anything: every line would be
     * compared with itself, so nothing can be newly broken. Most OK presses land there, since
     * this dialog also edits the title, tempo and attribution.
     *
     * @param song            the song whose lines have to fit
     * @param lyricsFont      the lyrics font now and the one being proposed
     * @param newLineWidthSs  the line width the commit will apply, in staff spaces
     * @return {@code true} when no line that fits today would stop fitting
     */
    public static boolean lyricsFit(Song song, LyricsFontChange lyricsFont, double newLineWidthSs) {
        var currentFont = lyricsFont.current();
        var newFont = lyricsFont.candidate();
        var currentLineWidthSs = song.getLineWidthSs();

        if (newFont.equals(currentFont) && newLineWidthSs == currentLineWidthSs) {
            return true;
        }

        var currentMetrics = LyricRenderMetrics.forFont(currentFont);
        var newMetrics = LyricRenderMetrics.forFont(newFont);

        for (var i = 0; i < song.lineCount(); i++) {
            var line = song.getLine(i);

            // Probe the candidate first: it accepts almost every real change, and continuing
            // here spares the baseline its own rebuild-and-solve of the line.
            if (LyricEditFitCalculator.lineFits(line, newMetrics, newLineWidthSs)) {
                continue;
            }

            if (LyricEditFitCalculator.lineFits(line, currentMetrics, currentLineWidthSs)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Applies {@code tempo} to {@code song}, announcing it only when it changed.
     *
     * <p>Posts a {@link TempoDidChangeNotification} when the tempo differs from the song's and
     * <strong>nothing at all when it does not</strong> — an OK that touched only the title must
     * not dirty the document.
     *
     * <p>The post sits inside a modification bracket, so it reaches undo as one step. See
     * {@code docs/mutations.md}.
     *
     * <p>This dialog does not set a key. A song has no key of its own: every line carries one,
     * set from the score rather than from Song Settings. See {@code docs/key-signatures.md}.
     *
     * @param song  the song to apply the settings to
     * @param tempo the tempo the controls describe
     */
    public static void applyMusicSettings(Song song, Tempo tempo) {
        if (Tempo.haveSameValue(tempo, song.getTempo())) {
            return;
        }

        song.withModification(() -> MessageCenter.post(new TempoDidChangeNotification(
            tempo.getTempoType(),
            tempo.getVisibleTempo(),
            tempo.getTempoDescription(),
            tempo.shouldShowTempo()
        )));
    }
}
