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

import module java.desktop;

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
 * The two live together because the metrics are a cache of the lyrics font's hyphen and space
 * widths, so every write to the fonts is a potential invalidation of the metrics.
 * {@link #applyFonts} is the single place that keeps them in step.
 * <p>
 * {@code ScoreView} is the only caller and exposes each operation below as a delegate. The
 * view's own deferred-init members ({@code song}, {@code mainPanel}) are reached back through
 * it rather than mirrored here, so there is exactly one answer to "is there a document yet".
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
     * Returns internal document font storage. Do not retain — the reference is swapped on
     * each font change.
     *
     * @throws IllegalStateException if called before the fonts have been bootstrapped
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
     * Installs new document fonts and cascades them through the component tree.
     * <p>
     * Called by the load path ({@link ScoreView#openFile} after {@link ScoreView#setSong}) and
     * by new-document creation. Unlike {@link #setFonts}, this is not undoable and does not
     * record a {@link FontChange} mutation — it establishes the starting state, not a user edit.
     */
    void installFonts(DocumentFonts fonts) {
        documentFonts = fonts;
        applyFonts();
    }

    /**
     * Replaces the document fonts and cascades the change.
     *
     * <p>If {@code newFonts.equals(getFonts())} the call is a no-op: no mutation is
     * recorded, no cascade runs.
     *
     * <p>Otherwise records a single {@link FontChange} mutation inside the song's
     * modification bracket — a commit that changes multiple roles produces one undoable
     * group, not one per role.
     *
     * <p><b>Cost:</b> each non-no-op call triggers a full {@link LyricRenderMetrics} rebuild
     * and one Swing {@code revalidate()} pass. For batched changes, construct a single
     * {@link DocumentFonts} and call this method once rather than role-by-role.
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

    /**
     * Applies the current fonts to each JComponent leaf and rebuilds lyric render metrics.
     * Each call triggers one {@link LyricRenderMetrics} rebuild and one Swing
     * {@code revalidate()} pass on the ScoreView subtree. For batched font changes, build a
     * single {@link DocumentFonts} and call {@link #setFonts} once rather than role-by-role.
     */
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
     * Returns the song-wide lyric metrics, or null when StaffPanel has not populated them yet.
     * Unlike {@link #getLyricRenderMetrics()} this never exits: it is for callers that may run
     * before the first layout and can space a projection as if the line had no lyrics.
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
     * Rebuilds {@link LyricRenderMetrics} from the current lyrics font if it has changed.
     * Must be called before any line layout runs so that {@code LineComponent.performLayout}
     * reads up-to-date hyphen and space widths.
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
