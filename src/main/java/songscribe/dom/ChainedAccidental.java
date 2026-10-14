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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A note's accidental as the chain walk decided it: the alteration the note sounds, and the
 * place that note holds in the accidental chain at its staff position. The chain walk decides
 * every note from three facts — its <em>sounding</em> alteration, what is <em>in force</em> at
 * its staff position before it, and the <em>key's</em> alteration for its pitch class — and
 * from whether something <em>intervenes</em> before it: the element immediately before it,
 * skipping rests and breath marks, is a note or grace note at another staff position, a
 * barline or a repeat, or the note is the first element of its line. What is in force is the
 * last written accidental at that position, until an accidental written there replaces it or a
 * key change ends it; where nothing is in force, the key's alteration is. The five states:
 *
 * <ul>
 *   <li>{@link ChainType#IN_KEY} — sounding equals the key's, and the key's is in force. Not
 *       written.</li>
 *   <li>{@link ChainType#ANCHOR} — sounding differs from what is in force and from the key's.
 *       Written.</li>
 *   <li>{@link ChainType#CANCEL} — sounding differs from what is in force and equals the
 *       key's. Written.</li>
 *   <li>{@link ChainType#REPETITION} — sounding equals what is in force, which differs from
 *       the key's, and something intervenes. Written.</li>
 *   <li>{@link ChainType#IMPLICIT} — sounding equals what is in force, which differs from the
 *       key's, and nothing intervenes. Not written.</li>
 * </ul>
 *
 * <p>The constructor is package-private so that only {@code AccidentalChain} constructs one,
 * which makes every instance a decision of the rule. That is why this is a final class rather
 * than a record: a public record's canonical constructor cannot be restricted (JLS §8.10.4).
 *
 * <p>Immutable; two instances are equal when their sounding alterations and types are.
 */
public final class ChainedAccidental implements AccidentalState {

    /**
     * The place a note holds in the accidental chain, and with it whether the accidental is
     * written.
     */
    public enum ChainType {
        IN_KEY(false),
        ANCHOR(true),
        REPETITION(true),
        IMPLICIT(false),
        CANCEL(true);

        private final boolean written;

        ChainType(boolean written) {
            this.written = written;
        }

        public boolean isWritten() {
            return written;
        }

        /**
         * @param inForce       the alteration in force at the note's staff position before it;
         *                      the key's alteration where no written accidental is in force
         * @param keyAlteration {@link Accidental#NATURAL} when the key does not alter the pitch class
         */
        public static ChainType decide(
            Accidental sounding, Accidental inForce, Accidental keyAlteration, boolean intervenes
        ) {
            if (sounding == inForce) {
                if (inForce == keyAlteration) {
                    return IN_KEY;
                }

                return intervenes ? REPETITION : IMPLICIT;
            }

            return (sounding == keyAlteration) ? CANCEL : ANCHOR;
        }
    }

    private final Accidental sounding;
    private final ChainType type;

    ChainedAccidental(Accidental sounding, ChainType type) {
        this.sounding = sounding;
        this.type = type;
    }

    @Override
    public Accidental sounding() {
        return sounding;
    }

    @Override
    public @Nullable Accidental drawn() {
        return isWritten() ? sounding : null;
    }

    @Override
    public AccidentalState intent() {
        return new Chosen(sounding);
    }

    public ChainType type() {
        return type;
    }

    public boolean isWritten() {
        return type.isWritten();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ChainedAccidental that)) {
            return false;
        }

        return sounding == that.sounding && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sounding, type);
    }

    @Override
    public String toString() {
        return "ChainedAccidental{sounding=" + sounding + ", type=" + type + '}';
    }
}
