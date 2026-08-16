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
package songscribe.dom;

import songscribe.util.Copyable;

public record BeatChange(Duration duration, Duration beat) implements Copyable<BeatChange> {

    /**
     * {@inheritDoc}
     *
     * @return {@code this}. Both components are {@link Duration} constants, so a beat change holds
     *         no mutable state for a copy to separate.
     */
    @Override
    public BeatChange copy() {
        return this;
    }

    /**
     * Translates a legacy beat-change enum name into the new record form. Called from the IO
     * layer when reading v2.4-or-earlier files. Each canonical name is paired with its
     * underscore-less form (the same stripping rule {@code StaffElementIO}'s accidental-map
     * loop applies), except for the two cases noted below, which pair the canonical name with a
     * historical misspelling that must keep being accepted because it was actually written into
     * old {@code .mssw} files.
     */
    public static BeatChange fromLegacyName(String legacyName) {
        return switch (legacyName) {
            case "QUAVER_EQUALS_QUAVER", "QUAVEREQUALSQUAVER" ->
                new BeatChange(Duration.QUAVER, Duration.QUAVER);
            case "DOTTED_CROCHET_EQUALS_MINIM", "DOTTEDCROCHETEQUALSMINIM" ->
                new BeatChange(Duration.CROTCHET_DOTTED, Duration.MINIM);
            case "MINIM_EQUALS_DOTTED_CROCHET", "MINIMEQUALSDOTTEDCROCHET" ->
                new BeatChange(Duration.MINIM, Duration.CROTCHET_DOTTED);
            // "CROTCHETQUALSDOTTEDCROCHET" is not the underscore-less form (missing E) — it is a
            // historical typo preserved in old .mssw files and must keep being accepted as-is.
            case "CROTCHET_EQUALS_DOTTED_CROCHET", "CROTCHETQUALSDOTTEDCROCHET" ->
                new BeatChange(Duration.CROTCHET, Duration.CROTCHET_DOTTED);
            // "DOTTEDCROCHETQUALSCROCHET" is not the underscore-less form (missing E) — it is a
            // historical typo preserved in old .mssw files and must keep being accepted as-is.
            case "DOTTED_CROCHET_EQUALS_CROCHET", "DOTTEDCROCHETQUALSCROCHET" ->
                new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET);
            default -> throw new IllegalArgumentException("unknown legacy beat change: " + legacyName);
        };
    }
}
