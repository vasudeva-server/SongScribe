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

import songscribe.Strings;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.ui.dialog.LocalizedMessage;
import songscribe.ui.dialog.LyricsContext;
import songscribe.ui.dialog.MusicSettings;
import songscribe.ui.dialog.SongSettingsBackEnd;
import songscribe.ui.dialog.SongSettingsInput;
import songscribe.ui.dialog.SongSettingsOutput;
import songscribe.ui.dialog.ValidationFailure;
import songscribe.ui.dialog.ValidationResult;

/**
 * Reads and writes the open document for {@code SongSettingsDialog}.
 *
 * <p>Bound to the score view rather than to one song, because the dialog it serves is opened
 * from a cached menu action and outlives every document — see {@link SongSettingsBackEnd}.
 * Each opening reads the song the view holds at that moment.
 */
public final class ScoreSongSettingsBackEnd implements SongSettingsBackEnd {

    private final SongSettingsTarget target;

    public ScoreSongSettingsBackEnd(SongSettingsTarget target) {
        this.target = target;
    }

    @Override
    public SongSettingsInput read() {
        var song = target.getSong();

        return new SongSettingsInput(
            song.getMetadata(),
            // Copies, both of them: the dialog edits what it is given, and the document must
            // not change until OK.
            new DocumentFonts(target.getDocumentFonts()),
            new MusicSettings(
                song.getTempo().copy(),
                SongSettingsRules.canonicalKeySelectionFrom(song),
                song.getLineWidthSs()
            ),
            new LyricsContext(song.getLyricsText(), !song.getTranslatedLyrics().isEmpty())
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two rules, in an order that is a precondition rather than a preference: the line
     * width is settled first, because the fit check needs a width to measure against and a
     * width that does not parse has none. Both are reported through the same result, most
     * important first, and the line width is the more important because the other cannot even
     * be asked while it is wrong.
     *
     * <p>The fit check spans two tabs — the lyrics font comes from the Fonts tab, the width it
     * has to fit into from the Music tab — which is why it is here rather than something
     * either tab could have asked on its own.
     */
    @Override
    public ValidationResult validate(SongSettingsOutput output) {
        var lineWidth = output.music().lineWidth();
        var lineWidthResult = LineWidthRules.validate(lineWidth);

        if (!lineWidthResult.isValid()) {
            return lineWidthResult;
        }

        var song = target.getSong();
        var fits = SongSettingsRules.lyricsFit(
            song,
            new LyricsFontChange(
                target.getDocumentFonts().getFont(FontKey.LYRICS),
                output.fonts().getFont(FontKey.LYRICS)
            ),
            LineWidthRules.resolveSs(song, lineWidth)
        );

        if (!fits) {
            return ValidationResult.invalid(new ValidationFailure(
                Strings.ALERT_TITLE_LINES_DO_NOT_FIT,
                new LocalizedMessage(Strings.ALERT_FONT_CHANGE_INVALID)
            ));
        }

        return ValidationResult.valid();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Four writes, one bracket, one undo step named for the dialog. They are separate
     * writes because they land in different places — notifications for the tempo and key, the
     * song's own metadata, the page geometry, the font set — and each is itself a no-op when
     * nothing about it changed, so an OK that changed nothing leaves the document clean.
     */
    @Override
    public void apply(SongSettingsOutput output) {
        var song = target.getSong();
        var music = output.music();

        song.withModification(Strings.get(Strings.ACTION_EDIT_OP_SONG_SETTINGS), () -> {
            SongSettingsRules.applyMusicSettings(song, music.tempo(), music.key());
            target.updatePageLayout(LineWidthRules.resolveSs(song, music.lineWidth()));

            // Song.setMetadata short-circuits on an equal record, so an unchanged commit adds
            // nothing to the bracket rather than needing change detection here.
            song.postWithModification(new SongMetadataDidChangeNotification(output.metadata()));
            target.setFonts(output.fonts());
        });
    }
}
