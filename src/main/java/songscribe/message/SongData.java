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

package songscribe.message;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Song.LyricsSource;
import songscribe.dom.Tempo;
import songscribe.ui.component.ScoreView;

/**
 * Immutable snapshot of all song-level fields, built by
 * {@link SongIO.DocumentReader} during file parsing.
 * <p>
 * Passed to {@link Song#loadFrom(SongData)} so that
 * Song can apply all fields atomically. After installation,
 * {@link ScoreView#setSong(Song)} posts
 * a {@link songscribe.message.notification.DocumentDidLoadNotification}.
 * <p>
 * The {@code tempo} field is {@code @Nullable}; a null value means the song
 * file contained no {@code <tempo>} element and the song has no explicit tempo.
 * Fonts are not stored on {@code SongData}; they are built directly into a
 * {@link songscribe.font.DocumentFonts} by the reader and installed on
 * {@link ScoreView} separately.
 */
public record SongData(
    @Nullable Tempo tempo,
    String number,
    String title,
    String place,
    int month,
    int day,
    String year,
    String underLyrics,
    String banglaLyrics,
    String translatedLyrics,
    String composer,
    String lyricist,
    LyricsSource lyricsSource,
    boolean arrangement,
    String footnotes,
    boolean unofficialTranslation,
    int defaultKeyAccidentalCount,
    KeyType defaultKeyType,
    double rowHeightAdjustmentSs,
    double lineWidthSs,
    List<Line> lines,
    boolean hasBeenDynamicallyLaidOut,
    int formatVersion,
    String subtitle
) {}
