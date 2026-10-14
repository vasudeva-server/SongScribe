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
package songscribe.ui.dialog;

import java.awt.Font;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.Tempo;
import songscribe.font.FontKey;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.ui.component.MainFrame;

/**
 * The document half of {@link SongSettingsDialog}: what it shows, what it may refuse, and what OK
 * writes.
 *
 * <p>Holds no document of its own: the dialog is reached from a cached menu action and outlives
 * every document, so the song and the view are resolved afresh on each call.
 *
 * <p><strong>The line width is read but never written.</strong> It belongs to page setup;
 * {@link #read} hands it over for the Title tab's previews to wrap at.
 */
public final class SongSettingsController extends DocumentDialogController<SongSettingsInput, SongSettingsOutput> {

    /**
     * A proposed change of lyrics font: the one in effect and the one being offered. The two
     * directions answer opposite questions, since {@link #lyricsFit} refuses a change that makes
     * a fitting line overflow and allows the reverse.
     *
     * <p>The two may be equal: an OK that changed only the title still asks the question.
     */
    private record LyricsFontChange(Font current, Font candidate) {}

    public SongSettingsController(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected SongSettingsInput read() {
        var song = getSong();

        return new SongSettingsInput(
            song.getMetadata(),
            requireScoreView().getDocumentFonts(),
            song.getTempo(),
            song.getLineWidthSs(),
            new LyricsContext(song.getLyricsText(), !song.getTranslatedLyrics().isEmpty())
        );
    }

    /**
     * One rule: the lyrics font must not break a line that fits today. It spans tabs, which is why
     * it is here and not in a tab.
     */
    @Override
    protected ValidationResult validate(SongSettingsOutput values) {
        var lyricsFontChange = new LyricsFontChange(
            requireScoreView().getDocumentFonts().getFont(FontKey.LYRICS),
            values.fonts().getFont(FontKey.LYRICS)
        );

        if (lyricsFit(getSong(), lyricsFontChange)) {
            return ValidationResult.valid();
        }

        return ValidationResult.invalid(new ValidationFailure(
            Strings.ALERT_TITLE_LINES_DO_NOT_FIT,
            new LocalizedMessage(Strings.ALERT_FONT_CHANGE_INVALID)
        ));
    }

    /**
     * Three writes, one bracket, one undo step named for the dialog. Each write is a no-op when
     * nothing about it changed, so an OK that changed nothing leaves the document clean.
     */
    @Override
    protected void commit(SongSettingsOutput values) {
        var song = getSong();
        var scoreView = requireScoreView();

        withModification(Strings.get(Strings.ACTION_EDIT_OP_SONG_SETTINGS), () -> {
            applyTempo(song, values.tempo());

            // Song.setMetadata short-circuits on an equal record, so an unchanged commit adds
            // nothing to the bracket rather than needing change detection here.
            song.postWithModification(new SongMetadataDidChangeNotification(values.metadata()));
            scoreView.setFonts(values.fonts());
        });
    }

    /**
     * Refuses a wider lyrics font that pushes a fitting line past the staff margin.
     *
     * <p><strong>A line that already overflows never refuses the change</strong>, so a narrower
     * font stays reachable from a document that is already broken. It is the same rule
     * {@code LyricEditor} applies to a widening syllable edit.
     *
     * <p>An unchanged font is accepted without measuring anything.
     */
    private static boolean lyricsFit(Song song, LyricsFontChange lyricsFont) {
        var currentFont = lyricsFont.current();
        var newFont = lyricsFont.candidate();

        if (newFont.equals(currentFont)) {
            return true;
        }

        var lineWidthSs = song.getLineWidthSs().value();
        var currentMetrics = LyricRenderMetrics.forFont(currentFont);
        var newMetrics = LyricRenderMetrics.forFont(newFont);

        for (var i = 0; i < song.lineCount(); i++) {
            var line = song.getLine(i);

            // Probe the candidate first: it accepts almost every real change, and continuing
            // here spares the baseline its own rebuild-and-solve of the line.
            if (LyricEditFitCalculator.lineFits(line, newMetrics, lineWidthSs)) {
                continue;
            }

            if (LyricEditFitCalculator.lineFits(line, currentMetrics, lineWidthSs)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Posts a {@link TempoDidChangeNotification} when the tempo differs from the song's and
     * <strong>nothing at all when it does not</strong> — an OK that touched only the title must not
     * dirty the document. Called from inside {@link #commit}'s bracket.
     */
    private static void applyTempo(Song song, Tempo tempo) {
        if (tempo.equals(song.getTempo())) {
            return;
        }

        song.withModification(() -> MessageCenter.post(new TempoDidChangeNotification(tempo)));
    }
}
