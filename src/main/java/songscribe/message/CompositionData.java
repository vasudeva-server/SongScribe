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

import java.awt.Font;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Tempo;

/**
 * Immutable snapshot of all composition-level fields, built by
 * {@link songscribe.io.CompositionIO.DocumentReader} during file parsing.
 * <p>
 * Passed to {@link songscribe.music.Composition#loadFrom(CompositionData)} so that
 * Composition can apply all fields atomically and post a single
 * {@link CompositionChangedMessage} with {@code ChangeType.FULL}.
 * <p>
 * Font fields are {@code @Nullable} because v1.0 files have no View section;
 * when null, the Composition retains its default (preferences-based) fonts.
 */
public record CompositionData(
    @NotNull Tempo tempo,
    @NotNull String number,
    @NotNull String title,
    @NotNull String place,
    int month,
    int day,
    @NotNull String year,
    @NotNull String lyrics,
    @NotNull String underLyrics,
    @NotNull String banglaLyrics,
    @NotNull String translatedLyrics,
    @NotNull String attribution,
    @NotNull String footnotes,
    boolean unofficialTranslation,
    int defaultKeyAccidentalCount,
    @NotNull KeyType defaultKeyType,
    @Nullable Font titleFont,
    @Nullable Font lyricsFont,
    @Nullable Font attributionFont,
    @Nullable Font annotationFont,
    double topPadding,
    double attributionStartY,
    double rowHeightAdjustment,
    double lineWidth,
    @NotNull List<Line> lines,
    boolean hasBeenDynamicallyLaidOut,
    int formatVersion
) {}
