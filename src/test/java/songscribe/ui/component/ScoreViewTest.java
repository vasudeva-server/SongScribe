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
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.awt.Font;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.message.Message;
import songscribe.message.MessageCenter;

/**
 * Unit tests for {@link ScoreView} behaviors not covered by {@link ScoreViewSetFontsTest}:
 * the {@link ScoreView#getDocumentFonts()} guard, the {@link ScoreView#installDocumentFonts}
 * non-mutation contract, {@link ScoreView#rebuildLyricRenderMetrics()} guard and
 * idempotency, and {@link ScoreView#getSuggestedFileName()} branch logic.
 */
class ScoreViewTest extends UnitTest {

    @Nested
    class GetDocumentFonts {

        @Test
        void testGetDocumentFontsThrowsIllegalStateExceptionBeforeInitialization() {
            // ScoreView(null) creates a headless instance without installing document fonts.
            var scoreView = new ScoreView(null);

            assertThatThrownBy(scoreView::getDocumentFonts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("documentFonts not initialized");
        }
    }

    @Nested
    class InstallDocumentFonts {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        @Test
        void testInstallDocumentFontsSetsFontsFieldWithoutPostingFontChange() {
            var scoreView = new ScoreView(null);
            var fonts = DocumentFonts.defaultsFromPrefs();

            scoreView.installDocumentFonts(fonts);

            // Must not post any message (not an undoable user edit, no FontChange recorded).
            messageCenterMock.verify(
                () -> MessageCenter.post(any(Message.class)),
                org.mockito.Mockito.never()
            );

            // getDocumentFonts() must return the installed instance.
            assertThat(scoreView.getDocumentFonts()).isSameAs(fonts);
        }
    }

    @Nested
    class RebuildLyricRenderMetrics {

        @Test
        void testRebuildLyricRenderMetricsIsNoOpWhenSongIsNull() {
            // song == null: early return must not throw.
            var scoreView = new ScoreView(null);
            scoreView.installDocumentFonts(DocumentFonts.defaultsFromPrefs());

            // documentFonts is set but song is null — must return silently.
            scoreView.rebuildLyricRenderMetrics();

            // lyricRenderMetrics was never populated; getLyricRenderMetrics() would throw,
            // so we only verify that rebuildLyricRenderMetrics() itself did not throw.
            // (The null guard leaves the field null; no further assertion is possible here.)
        }

        @Test
        void testRebuildLyricRenderMetricsIsNoOpWhenDocumentFontsIsNull() {
            // documentFonts == null: early return must not throw.
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());
            // documentFonts deliberately not installed.

            scoreView.rebuildLyricRenderMetrics();

            // No exception; metrics remain null.
        }

        @Test
        void testRebuildLyricRenderMetricsIsIdempotentWhenLyricsFontUnchanged() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());
            scoreView.installDocumentFonts(DocumentFonts.defaultsFromPrefs());

            scoreView.rebuildLyricRenderMetrics();
            var firstMetrics = scoreView.getLyricRenderMetrics();

            // Same font: second call must return the same instance (idempotency guard).
            scoreView.rebuildLyricRenderMetrics();
            var secondMetrics = scoreView.getLyricRenderMetrics();

            assertThat(secondMetrics).isSameAs(firstMetrics);
        }

        @Test
        void testRebuildLyricRenderMetricsRebuildsWhenLyricsFontChanges() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());

            var initialFonts = DocumentFonts.defaultsFromPrefs();
            scoreView.installDocumentFonts(initialFonts);
            scoreView.rebuildLyricRenderMetrics();
            var firstMetrics = scoreView.getLyricRenderMetrics();

            // Change the lyrics font and install the updated DocumentFonts.
            var updatedFonts = new DocumentFonts(initialFonts);
            updatedFonts.setFont(FontKey.LYRICS, new Font("Serif", Font.PLAIN, 18));
            scoreView.installDocumentFonts(updatedFonts);

            scoreView.rebuildLyricRenderMetrics();
            var secondMetrics = scoreView.getLyricRenderMetrics();

            assertThat(secondMetrics).isNotSameAs(firstMetrics);
            assertThat(secondMetrics.lyricsFont())
                .isEqualTo(updatedFonts.getFont(FontKey.LYRICS));
        }
    }

    @Nested
    class GetSuggestedFileName {

        @Test
        void testGetSuggestedFileNameZeroPadsNumericSongNumber() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            song.setNumber("3");
            song.setTitle("Mélodie");
            scoreView.setSong(song);

            // Numeric number → zero-padded to three digits; diacritics stripped from title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("003 Melodie");
        }

        @Test
        void testGetSuggestedFileNameUsesNonNumericSongNumberVerbatim() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            song.setNumber("A");
            song.setTitle("Title");
            scoreView.setSong(song);

            // Non-numeric number → used as-is, followed by a space and the title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("A Title");
        }
    }
}
