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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ChainedAccidental.ChainType;
import songscribe.engraving.StaffPosition;

/**
 * The chain walk: the one statement of how the notes of a song decide their accidental
 * states, and the only thing that constructs a {@link ChainedAccidental}. A walk over the live
 * song after a recorded change and a walk over a projection of an edit that has not landed
 * both come here, so an edit's preview and its result cannot disagree about a note.
 *
 * <p>The five states, and the concepts they rest on — what is in force, a chain, and something
 * intervening — are stated in {@link ChainedAccidental}'s class Javadoc, which is the contract
 * of {@link ChainType#decide}, and in {@code docs/accidentals.md}.
 *
 * <p>A walk decides the notes an edit can reach and no more, which is sound because after any
 * note the in-force alteration at its position is that note's sounding: an edit's effect at a
 * position ends with the first untouched note there, whose sounding is what it was before the
 * edit, so every later note at that position is decided against what it was decided against
 * before. A key change is the exception, since the key's alteration feeds every note's state,
 * and so an edit that moved a key walks on to the next key change — {@link ChainEdit.Reach}
 * names the two.
 *
 * <p>A static holder with no instances.
 */
public final class AccidentalChain {

    private AccidentalChain() {
    }

    /**
     * A note whose stored state the walk changes.
     *
     * @param line  the live line the note is on or is about to land on
     * @param note  the note
     * @param state the state the note is decided to have
     */
    public record Restamp(Line line, NoteElement note, ChainedAccidental state) {

        /**
         * Gives the note the decided state, plainly: nothing is recorded, so a caller inside a
         * modification bracket passes this as the mutator of {@link Line#modifyElement}.
         *
         * @effects {@code note.accidentalState()} is {@code state} afterwards
         */
        public void stamp() {
            note.stamp(state);
        }
    }

    /**
     * Decides every note {@code edit} can reach in {@code song}, under the rule in {@link ChainedAccidental}'s class Javadoc.
     *
     * <p>Every note in the edited range that is {@code Chosen} or {@code Adopting} is decided,
     * so a caller that applies the result leaves no such note on the line.
     *
     * @param song the song the edit has landed in
     * @param edit the edit, in {@code song}'s coordinates
     * @return the restamps, in song order, so that applying them left to right leaves every
     *     note stamped as decided; empty when no note's state changes
     * @invariant every restamp names a note {@code song} holds, on the live line it is on
     * @invariant no restamp names a note before the edit's start
     */
    public static List<Restamp> walk(Song song, ChainEdit edit) {
        return walk(new AccidentalScanView.Live(song), edit);
    }

    /**
     * Decides every note {@code edit} can reach in {@code view}, under the rule in {@link ChainedAccidental}'s class Javadoc.
     * {@link ProjectedEdit} is what pairs a projected view with the edit it projects.
     *
     * @param view the song the edit has landed in, or a projection of it as the edit will
     *             leave it
     * @param edit the edit, in {@code view}'s coordinates
     * @return the restamps, as {@link #walk(Song, ChainEdit)} answers them
     * @invariant every restamp names a note {@code view} holds, on the live line it is on or
     *     is about to land on
     */
    static List<Restamp> walk(AccidentalScanView view, ChainEdit edit) {
        // A removal at the end of the song, or of its last line, starts one past every element.
        var first = existingAtOrAfter(view, edit.start());
        return (first == null) ? List.of() : new Walk(view, edit, first).run();
    }

    /** The first place at or after {@code cursor} that holds an element, or null at the end of the song. */
    private static @Nullable Cursor existingAtOrAfter(AccidentalScanView view, Cursor cursor) {
        var lineIndex = cursor.lineIndex();
        var index = cursor.index();

        while ((lineIndex < view.lineCount()) && (index >= view.elementCount(lineIndex))) {
            lineIndex++;
            index = 0;
        }

        return (lineIndex < view.lineCount()) ? new Cursor(lineIndex, index) : null;
    }

    /**
     * Decides every note in {@code song}, for the load pass: walks from the song's first
     * element, and again from every key change and every line with its own key, each walk
     * reaching the next key change so that it decides every note up to there.
     *
     * @param song the song to decide
     * @return the restamps, in song order, so that applying them left to right leaves every
     *     note in the song stamped as decided
     * @invariant afterwards every {@code Chosen} or {@code Adopting} note in {@code song} has a
     *     restamp
     */
    public static List<Restamp> walkSong(Song song) {
        var view = new AccidentalScanView.Live(song);
        var restamps = new ArrayList<Restamp>();
        var first = true;

        for (var lineIndex = 0; lineIndex < view.lineCount(); lineIndex++) {
            for (var index = 0; index < view.elementCount(lineIndex); index++) {
                var startsOwnKey = (index == 0) && (view.ownKey(lineIndex) != null);

                if (first || startsOwnKey || (view.elementAt(lineIndex, index) instanceof KeyChangeElement)) {
                    restamps.addAll(walk(view, ChainEdit.rekeyingFrom(new Cursor(lineIndex, index))));
                }

                first = false;
            }
        }

        return restamps;
    }

    /**
     * One walk's state and its loop.
     *
     * <p>A walk keeps: {@code key}, the key in force at the current element, seeded at the
     * edit's start from the nearest key change before it on its line, else the key the line
     * starts in, both as the view answers them; {@code inForce[p]}, what is in force at staff position {@code p}, seeded the
     * first time the walk meets {@code p} by scanning back from the edit's start for the nearest
     * note at {@code p} and taking its sounding — the scan stops at a key change or the start of
     * a line with its own key, and finding no note gives {@code key}'s alteration for {@code p};
     * {@code open}, the positions whose next note is still to be decided, starting as the edit's
     * vacated positions; {@code previous}, the element before the current one skipping rests
     * and breath marks, or none at the start of a line; and {@code span}, the elements of the
     * edited range plus the first element at or after its end that is not a rest or a breath
     * mark.
     *
     * <p>For each element {@code e} from the edit's start onward, in song order across lines:
     * <ol>
     * <li><em>Key change.</em> Meeting a key change, or entering a line with its own key, is a
     *     key change at that element. If {@code e} is the first element walked or lies in the
     *     edited range, {@code key} is set from it, every {@code inForce[p]} becomes its
     *     alteration for {@code p}, and the walk goes on. Otherwise the walk stops.</li>
     * <li><em>Note.</em> A note at position {@code p} is decided when it is
     *     {@link AccidentalState.Chosen Chosen} or {@link AccidentalState.Adopting Adopting},
     *     when {@code p} is in {@code open}, when the edit reaches the next key change, or when
     *     it is in {@code span}. Its sounding is {@code Chosen}'s alteration, {@code inForce[p]}
     *     for {@code Adopting}, and its own for a {@link ChainedAccidental}; its type is decided
     *     by {@link ChainType#decide}. A restamp is emitted when the note was {@code Chosen} or
     *     {@code Adopting}, or its stored type differs. Then a {@code Chosen} or
     *     {@code Adopting} note adds {@code p} to {@code open} and a decided
     *     {@code ChainedAccidental} note removes it; decided or not, {@code inForce[p]} becomes
     *     the note's sounding.</li>
     * <li><em>Advance.</em> Unless {@code e} is a rest or a breath mark, {@code previous}
     *     becomes {@code e}.</li>
     * <li><em>Stop</em> after {@code e} when {@code e} is at or past the end of {@code span},
     *     {@code open} is empty and the edit reaches only the untouched note, or at the end of
     *     the song.</li>
     * </ol>
     */
    private static final class Walk {

        private final AccidentalScanView view;
        private final Cursor rangeStart;
        private final Cursor rangeEnd;
        private final @Nullable Cursor spanEnd;
        private final Cursor firstWalked;
        private final boolean reachesNextKeyChange;
        private final Map<StaffPosition, Accidental> inForce = new HashMap<>();
        private final Set<StaffPosition> open;
        private final List<Restamp> restamps = new ArrayList<>();
        private Key key;
        private boolean inForceIsKey = false;
        private @Nullable StaffElement previous;

        /**
         * @param firstWalked the first place at or after the edit's start that holds an element
         */
        Walk(AccidentalScanView view, ChainEdit edit, Cursor firstWalked) {
            this.view = view;
            this.firstWalked = firstWalked;
            rangeStart = edit.start();
            rangeEnd = edit.end();
            spanEnd = firstUnskippedAtOrAfter(rangeEnd);
            reachesNextKeyChange = (edit.reach() == ChainEdit.Reach.TO_NEXT_KEY_CHANGE);
            open = new HashSet<>(edit.vacated());
            key = keyBefore(rangeStart);
            previous = previousBefore(rangeStart);
        }

        List<Restamp> run() {
            var cursor = firstWalked;
            var first = true;

            while (cursor != null) {
                var element = view.elementAt(cursor.lineIndex(), cursor.index());

                if (cursor.index() == 0) {
                    previous = null;
                }

                // A line's own key takes effect before its first element, which is still to be
                // decided under it; a key change is an element of its own and decides nothing.
                var ownKey = (cursor.index() == 0) ? view.ownKey(cursor.lineIndex()) : null;
                var keyChange = (element instanceof KeyChangeElement keyChangeElement) ? keyChangeElement.getKey() : null;

                if (((ownKey != null) || (keyChange != null)) && !first && !inRange(cursor)) {
                    break;
                }

                if (ownKey != null) {
                    rekey(ownKey);
                }

                if (keyChange != null) {
                    rekey(keyChange);
                } else if (element instanceof NoteElement note) {
                    decide(cursor, note);
                }

                if (!skipped(element)) {
                    previous = element;
                }

                first = false;

                if (stopsAfter(cursor)) {
                    break;
                }

                cursor = existingAtOrAfter(cursor.next());
            }

            return restamps;
        }

        private void decide(Cursor cursor, NoteElement note) {
            var position = note.getStaffPosition();
            var state = note.accidentalState();
            var current = inForceAt(position);
            var sounding = switch (state) {
                case ChainedAccidental chained -> chained.accidental();
                case AccidentalState.Chosen chosen -> chosen.sounding();
                case AccidentalState.Adopting _ -> current;
            };
            var stamped = (state instanceof ChainedAccidental);
            var decided = !stamped || open.contains(position) || reachesNextKeyChange || inSpan(cursor);

            if (decided) {
                var type = ChainType.decide(sounding, current, key.alterationAt(position), intervenes(position));
                var stored = (state instanceof ChainedAccidental chained) ? chained.type() : null;

                if (stored != type) {
                    restamps.add(new Restamp(view.line(cursor.lineIndex()), note, new ChainedAccidental(sounding, type)));
                }

                if (stamped) {
                    open.remove(position);
                } else {
                    open.add(position);
                }
            }

            inForce.put(position, sounding);
        }

        private boolean intervenes(StaffPosition position) {
            var before = previous;

            if (before == null) {
                return true;
            }

            if (before instanceof NoteElement other) {
                return !other.getStaffPosition().equals(position);
            }

            return before.getType().intervenesInChain();
        }

        private boolean stopsAfter(Cursor cursor) {
            if (reachesNextKeyChange || !open.isEmpty()) {
                return false;
            }

            var end = (spanEnd != null) ? spanEnd : rangeEnd;
            return cursor.compareTo(end) >= 0;
        }

        private boolean inSpan(Cursor cursor) {
            return inRange(cursor) || cursor.equals(spanEnd);
        }

        /** Whether {@code cursor} is in the edited range; the walk never stands before its start. */
        private boolean inRange(Cursor cursor) {
            return cursor.compareTo(rangeEnd) < 0;
        }

        /** Puts {@code newKey} in force: it ends every accidental in force at every position. */
        private void rekey(Key newKey) {
            key = newKey;
            inForce.clear();
            inForceIsKey = true;
        }

        private Accidental inForceAt(StaffPosition position) {
            var found = inForce.get(position);

            if (found != null) {
                return found;
            }

            var seeded = inForceIsKey ? keyAlterationFor(position) : inForceBeforeStart(position);
            inForce.put(position, seeded);

            return seeded;
        }

        /**
         * What is in force at {@code position} arriving at the edit's start: the sounding of the
         * nearest note there before it, unless a key change or the start of a line with its own
         * key comes first, in which case the key's alteration.
         */
        private Accidental inForceBeforeStart(StaffPosition position) {
            var lineIndex = rangeStart.lineIndex();
            var index = rangeStart.index() - 1;

            while (true) {
                for (; index >= 0; index--) {
                    var element = view.elementAt(lineIndex, index);

                    if (element.getType().endsAccidentalsInForce()) {
                        return keyAlterationFor(position);
                    }

                    if ((element instanceof NoteElement note) && note.getStaffPosition().equals(position)) {
                        return note.soundingAccidental();
                    }
                }

                if ((lineIndex == 0) || (view.ownKey(lineIndex) != null)) {
                    return keyAlterationFor(position);
                }

                lineIndex--;
                index = view.elementCount(lineIndex) - 1;
            }
        }

        private Accidental keyAlterationFor(StaffPosition position) {
            return key.alterationAt(position);
        }

        /**
         * The key in force just before {@code cursor}, read wholly from the view — the key its line
         * starts in and the key changes before it on that line — since a projection can move the
         * key a line starts in, and the live line would then answer the key it starts in now.
         */
        private Key keyBefore(Cursor cursor) {
            return view.keyBefore(cursor);
        }

        private @Nullable StaffElement previousBefore(Cursor cursor) {
            for (var index = cursor.index() - 1; index >= 0; index--) {
                var element = view.elementAt(cursor.lineIndex(), index);

                if (!skipped(element)) {
                    return element;
                }
            }

            return null;
        }

        private @Nullable Cursor firstUnskippedAtOrAfter(Cursor cursor) {
            var found = existingAtOrAfter(cursor);

            while ((found != null) && skipped(view.elementAt(found.lineIndex(), found.index()))) {
                found = existingAtOrAfter(found.next());
            }

            return found;
        }

        private @Nullable Cursor existingAtOrAfter(Cursor cursor) {
            return AccidentalChain.existingAtOrAfter(view, cursor);
        }

        private static boolean skipped(StaffElement element) {
            return element.getType().isTransparentInChain();
        }
    }
}
