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

import java.awt.*;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Tempo;

/**
 * Immutable snapshot of all composition-level fields, built by
 * {@link songscribe.io.CompositionIO.DocumentReader} during file parsing.
 * <p>
 * Passed to {@link songscribe.music.Composition#loadFrom(CompositionData)} so that
 * Composition can apply all fields atomically. After installation,
 * {@link songscribe.ui.component.Score#setComposition(songscribe.music.Composition)} posts
 * a {@link songscribe.message.notification.DocumentDidLoadNotification}.
 * <p>
 * Font fields are {@code @Nullable} because v1.0 files have no View section;
 * when null, the Composition retains its default (preferences-based) fonts.
 */
public record CompositionData(
    Tempo tempo,
    String number,
    String title,
    String place,
    int month,
    int day,
    String year,
    String lyrics,
    String underLyrics,
    String banglaLyrics,
    String translatedLyrics,
    String attribution,
    String footnotes,
    boolean unofficialTranslation,
    int defaultKeyAccidentalCount,
    KeyType defaultKeyType,
    @Nullable Font titleFont,
    @Nullable Font lyricsFont,
    @Nullable Font attributionFont,
    @Nullable Font annotationFont,
    double topPaddingSs,
    double attributionStartYSs,
    double rowHeightAdjustmentSs,
    double lineWidthSs,
    List<Line> lines,
    boolean hasBeenDynamicallyLaidOut,
    int formatVersion
) {}
