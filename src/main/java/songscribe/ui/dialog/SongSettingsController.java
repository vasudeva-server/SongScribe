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
 * <p>Holds no document of its own. {@link SongSettingsDialog} is reached from a cached menu action
 * and outlives every document, so the song and the view are resolved afresh on each call rather
 * than bound once — a controller that cached either would answer for a document that has since been
 * closed.
 *
 * <p><strong>The line width is read but never written.</strong> The Music tab used to carry a
 * line-width field, and with it the only free text in this dialog and the only thing about it that
 * could fail to parse. The width belongs to page setup now; what {@link #read} still hands over is
 * the stored width for the Title tab's previews to wrap at, and no path here writes one back. What
 * is left to judge is a tempo built from bounded controls, metadata that is free text but cannot be
 * wrong, and a set of fonts — which is why the one rule below is about fonts alone.
 */
public final class SongSettingsController extends DialogController<SongSettingsInput, SongSettingsOutput> {

    /**
     * A proposed change of lyrics font: the one in effect and the one being offered.
     *
     * <p>The two are a record rather than two parameters because they are the same type and
     * adjacent, so a call site could hand them over the wrong way round with nothing to catch it —
     * and the two directions answer opposite questions, since {@link #lyricsFit} refuses a change
     * that makes a fitting line overflow and allows the reverse.
     *
     * <p>The two may be equal: an OK that changed only the title still asks the question, and gets
     * an immediate yes.
     *
     * @param current   the lyrics font the document is laid out with now
     * @param candidate the lyrics font the dialog would commit
     */
    private record LyricsFontChange(Font current, Font candidate) {}

    /**
     * @param mainFrame the application window, through which each call reaches whatever document is
     *                  open at that moment
     */
    public SongSettingsController(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * {@inheritDoc}
     *
     * @return the open song's metadata, fonts, tempo, line width and lyrics context, as they now
     *         stand
     */
    @Override
    protected SongSettingsInput read() {
        var song = getSong();

        return new SongSettingsInput(
            song.getMetadata(),
            requireScoreView().getDocumentFonts(),
            song.getTempo(),
            new Ss(song.getLineWidthSs()),
            new LyricsContext(song.getLyricsText(), !song.getTranslatedLyrics().isEmpty())
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>One rule: the lyrics font must not break a line that fits today. It spans tabs — the
     * candidate font comes from the Fonts tab, the lines it has to fit come from the document —
     * which is why it is here rather than something a tab could have asked on its own.
     *
     * @return {@link ValidationResult#valid()} unless the proposed lyrics font would push a
     *         currently fitting line past the staff margin, in which case a single failure saying so
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
     * {@inheritDoc}
     *
     * <p>Three writes, one bracket, one undo step named for the dialog. They are separate writes
     * because they land in different places — a notification for the tempo, the song's own
     * metadata, the font set — and each is itself a no-op when nothing about it changed, so an OK
     * that changed nothing leaves the document clean.
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
     * Whether every line of {@code song} still fits once {@code lyricsFont}'s candidate is applied.
     *
     * <p>A wider lyrics font widens every syllable, which widens the minimum spacing between
     * columns and can push a line past the staff margin. Committing that anyway leaves the line laid
     * out on its collision floors with its tail clipped in red (refs #696). Refusing the change up
     * front keeps a fitting line fitting.
     *
     * <p>Both fonts are measured against the song's stored line width, which this dialog cannot
     * change, so the two solves differ in nothing but the font.
     *
     * <p><strong>A line that already overflows never refuses the change.</strong> The user has to be
     * able to reach a narrower font from a document that is already broken, and a rule that blocked
     * every change while a line overflowed would make that impossible. It is the same rule
     * {@code LyricEditor} applies to a widening syllable edit.
     *
     * <p>An unchanged font is accepted without measuring anything: every line would be compared with
     * itself, so nothing can be newly broken. Most OK presses land there, since this dialog also
     * edits the title, tempo and attribution.
     *
     * @param song       the song whose lines have to fit
     * @param lyricsFont the lyrics font now and the one being proposed
     * @return {@code true} when no line that fits today would stop fitting
     */
    private static boolean lyricsFit(Song song, LyricsFontChange lyricsFont) {
        var currentFont = lyricsFont.current();
        var newFont = lyricsFont.candidate();

        if (newFont.equals(currentFont)) {
            return true;
        }

        var lineWidthSs = song.getLineWidthSs();
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
     * Applies {@code tempo} to {@code song}, announcing it only when it changed.
     *
     * <p>Posts a {@link TempoDidChangeNotification} when the tempo differs from the song's and
     * <strong>nothing at all when it does not</strong> — an OK that touched only the title must not
     * dirty the document.
     *
     * <p>Called from inside {@link #commit}'s bracket, so the post reaches undo as one step. See
     * {@code docs/mutations.md}.
     *
     * <p>This dialog does not set a key. A song has no key of its own: every line carries one, set
     * from the score rather than from Song Settings. See {@code docs/key-signatures.md}.
     *
     * @param song  the song to apply the tempo to
     * @param tempo the tempo the controls describe
     */
    private static void applyTempo(Song song, Tempo tempo) {
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
