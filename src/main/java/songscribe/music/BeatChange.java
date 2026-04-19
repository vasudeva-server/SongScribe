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
package songscribe.music;

public record BeatChange(Duration duration, Duration beat) {

    /**
     * Translates a legacy beat-change enum name (canonical, underscore-less, or typo variant)
     * into the new record form. Called from the IO layer when reading v2.4-or-earlier files.
     */
    public static BeatChange fromLegacyName(String legacyName) {
        return switch (legacyName) {
            case "QUAVER_EQUALS_QUAVER", "QUAVEREQUALSQUAVER" ->
                new BeatChange(Duration.QUAVER, Duration.QUAVER);
            case "DOTTED_CROCHET_EQUALS_MINIM", "DOTTEDCROCHETEQUALSMINIM" ->
                new BeatChange(Duration.CROTCHET_DOTTED, Duration.MINIM);
            case "MINIM_EQUALS_DOTTED_CROCHET", "MINIMEQUALSDOTTEDCROCHET" ->
                new BeatChange(Duration.MINIM, Duration.CROTCHET_DOTTED);
            case "CROTCHET_EQUALS_DOTTED_CROCHET", "CROTCHETQUALSDOTTEDCROCHET" ->
                new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED);
            case "DOTTED_CROCHET_EQUALS_CROCHET", "DOTTEDCROCHETQUALSCROCHET" ->
                new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET);
            default -> throw new IllegalArgumentException("unknown legacy beat change: " + legacyName);
        };
    }
}
