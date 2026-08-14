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

package songscribe.dom;

import java.util.stream.Stream;

/**
 * One key change of each kind anything downstream of a key change distinguishes.
 *
 * <p>Nothing that consumes a key change — what the staff header draws, which key a note resolves
 * against, what {@code <cancel>} a MusicXML writer owes — depends on <em>which</em> keys are
 * involved. All of them depend on the same three things: whether the type changed, how many
 * accidentals the previous key held, and how many the next one holds. These constants are one
 * representative per combination of those, so a test that runs all of them has covered the domain
 * without enumerating the 15&nbsp;&times;&nbsp;15 ordered pairs of {@link Key#allSignatures()},
 * most of which are the same case with different letters on it.
 *
 * <p>Where a kind has an extreme, its representative sits at the extreme: the type changes go
 * through {@link TestKeys#ALL_SHARPS} and {@link TestKeys#ALL_FLATS} so every pitch class is
 * altered and the accidental counts are at {@link Key#MAX_ACCIDENTAL_COUNT}. That also makes each
 * pair maximally contrasting, which is what a test asking <em>which key was consulted</em> needs:
 * two keys that agree at a note's position cannot show that the wrong one was read.
 */
public enum KeyChangeKind {

    /** The key does not change, which is the one kind that draws and cancels nothing. */
    UNCHANGED(TestKeys.E_MAJOR, TestKeys.E_MAJOR),

    /** Same type, more accidentals than before. */
    SAME_TYPE_ADDING(TestKeys.D_MAJOR, TestKeys.E_MAJOR),

    /**
     * Same type, fewer accidentals than before — the kind a conventional engraver would cancel,
     * and where this program's policy restates the new signature instead.
     */
    SAME_TYPE_DROPPING(TestKeys.E_MAJOR, TestKeys.D_MAJOR),

    /** Sharps to no key at all: everything is cancelled and nothing replaces it. */
    SHARPS_TO_NO_KEY(TestKeys.ALL_SHARPS, TestKeys.C_MAJOR),

    /** Flats to no key at all, which cancels the opposite sign to {@link #SHARPS_TO_NO_KEY}. */
    FLATS_TO_NO_KEY(TestKeys.ALL_FLATS, TestKeys.C_MAJOR),

    /** No key at all to sharps: there is nothing to cancel, only a new signature to state. */
    NO_KEY_TO_SHARPS(TestKeys.C_MAJOR, TestKeys.ALL_SHARPS),

    /** Sharps to flats, the only kind that puts two full runs on the staff at once. */
    SHARPS_TO_FLATS(TestKeys.ALL_SHARPS, TestKeys.ALL_FLATS),

    /** Flats to sharps, which is {@link #SHARPS_TO_FLATS} the other way round. */
    FLATS_TO_SHARPS(TestKeys.ALL_FLATS, TestKeys.ALL_SHARPS);

    private final Key previous;
    private final Key next;

    KeyChangeKind(Key previous, Key next) {
        this.previous = previous;
        this.next = next;
    }

    /** @return the key in effect before the change */
    public Key previous() {
        return previous;
    }

    /** @return the key the change establishes */
    public Key next() {
        return next;
    }

    /**
     * The kinds that actually change the key, for cases that can only fail when the two keys
     * differ — asking which of them was consulted proves nothing when they are the same key.
     *
     * @return every kind except {@link #UNCHANGED}
     */
    public static Stream<KeyChangeKind> changes() {
        return Stream.of(values()).filter(kind -> !kind.previous.equals(kind.next));
    }

    @Override
    public String toString() {
        return name() + " (" + previous + " -> " + next + ')';
    }
}
