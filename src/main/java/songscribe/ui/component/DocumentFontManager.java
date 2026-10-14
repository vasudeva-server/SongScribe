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

package songscribe.ui.component;

import java.awt.Font;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.mutation.FontChange;

/**
 * Owns one {@link ScoreView}'s document typography: the authoritative {@link DocumentFonts}
 * and the {@link LyricRenderMetrics} derived from the lyrics font.
 * <p>
 * The metrics cache the lyrics font's hyphen and space widths, so every write to the fonts
 * invalidates them; {@link #applyFonts} is the single place that keeps them in step.
 */
final class DocumentFontManager {

    private final ScoreView scoreView;

    // Authoritative document-level fonts. Null only before the view's first bootstrap.
    // installFonts() and setFonts() are the only write entries.
    @Nullable private DocumentFonts documentFonts = null;

    // Song-wide lyric render metrics shared across all line components.
    // Rebuilt by StaffPanel.ensureAllLineLayouts before any layout/paint runs.
    @Nullable private LyricRenderMetrics lyricRenderMetrics = null;

    DocumentFontManager(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    /**
     * Do not retain: the reference is swapped on each font change.
     */
    DocumentFonts getFonts() {
        if (documentFonts == null) {
            throw new IllegalStateException("documentFonts not initialized");
        }

        return documentFonts;
    }

    Font getFont(FontKey key) {
        return getFonts().getFont(key);
    }

    /**
     * Not undoable: records no {@link FontChange} mutation, since it establishes the starting
     * state, not a user edit.
     */
    void installFonts(DocumentFonts fonts) {
        documentFonts = fonts;
        applyFonts();
    }

    /**
     * Records a single {@link FontChange} mutation, so a commit that changes several roles is
     * one undoable group.
     */
    void setFonts(DocumentFonts newFonts) {
        var oldFonts = getFonts();

        if (newFonts.equals(oldFonts)) {
            return;
        }

        var song = scoreView.getSong();
        song.withModification(() ->
            song.applyChange(new FontChange(oldFonts, newFonts), () -> {
                documentFonts = newFonts;
                applyFonts();
            })
        );
    }

    void applyFonts() {
        var fonts = getFonts();
        var mainPanel = scoreView.getMainPanel();

        // Headless converter path: openFile -> installFonts runs without ScoreView.init().
        if (mainPanel == null) {
            return;
        }

        rebuildLyricRenderMetrics();
        var textPanel = mainPanel.getTextPanel();
        var lyricsFont = fonts.getLyricsFont();
        mainPanel.getTitleComponent().setFont(fonts.getTitleFont());
        mainPanel.getSubtitleComponent().setFont(fonts.getSubtitleFont());
        textPanel.getUnderLyricsComponent().setFont(lyricsFont);
        textPanel.getBanglaLyricsComponent().setFont(fonts.getBanglaFont());
        textPanel.getTranslationComponent().setFont(lyricsFont);
        mainPanel.getFootnotesComponent().setFont(fonts.getFootnoteFont());
        scoreView.revalidate();
    }

    /**
     * Null when StaffPanel has not populated them yet.
     */
    @Nullable LyricRenderMetrics findLyricRenderMetrics() {
        return lyricRenderMetrics;
    }

    LyricRenderMetrics getLyricRenderMetrics() {
        if (lyricRenderMetrics == null) {
            throw RuntimeError.exit(
                "LyricRenderMetrics accessed before StaffPanel populated it");
        }

        return lyricRenderMetrics;
    }

    /**
     * Must be called before any line layout runs, so layout reads up-to-date hyphen and space
     * widths.
     */
    void rebuildLyricRenderMetrics() {
        // isInitialized() is ScoreView's "has a song" test. The metrics are song-wide, so
        // there is nothing to build for a view that has not been handed a document yet.
        if (!scoreView.isInitialized() || documentFonts == null) {
            return;
        }

        var lyricsFont = documentFonts.getLyricsFont();

        if (lyricRenderMetrics != null && lyricRenderMetrics.lyricsFont().equals(lyricsFont)) {
            return;
        }

        lyricRenderMetrics = LyricRenderMetrics.forFont(lyricsFont);
    }
}
