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
 * <h2>The policy</h2>
 *
 * <p><b>In force.</b> A written accidental stays in force at its staff position until an
 * accidental written at that position replaces it or a key change ends it. Barlines, repeats,
 * line breaks, rests, breath marks and notes at other positions change nothing about what is
 * in force. Where nothing is in force a note sounds its key's alteration for its pitch class,
 * {@link Accidental#NATURAL} when the key does not alter it. After any note, what is in force
 * at its position is that note's sounding alteration, whatever its state.
 *
 * <p><b>Chain.</b> The notes at one staff position from an anchor up to, and not including,
 * the next anchor or cancel at that position, or the next key change. A chain crosses
 * barlines, repeats and line breaks; its notes need not be adjacent.
 *
 * <p><b>Intervening.</b> Something intervenes before a note when the element immediately
 * before it, skipping rests and breath marks, is a note or grace note at another staff
 * position, a barline or a repeat, or when nothing but rests and breath marks stands before
 * it on its line. A tie changes nothing.
 *
 * <p><b>The five states</b> are decided for every note from its <em>sounding</em>
 * alteration, what is <em>in force</em> before it at its position, and the <em>key's</em>
 * alteration for its pitch class, by {@link ChainType#decide}:
 * {@link ChainType#IN_KEY} when sounding equals the key's and the key's is in force;
 * {@link ChainType#ANCHOR} when sounding differs from what is in force and from the key's;
 * {@link ChainType#CANCEL} when sounding differs from what is in force and equals the key's;
 * {@link ChainType#REPETITION} when sounding equals what is in force, which differs from the
 * key's, and something intervenes; {@link ChainType#IMPLICIT} when the same holds and nothing
 * intervenes. Anchors, cancels and repetitions are written; the other two are not.
 *
 * <h2>The walk</h2>
 *
 * <p>A walk keeps: {@code key}, the key in force at the current element, seeded at the edit's
 * start from the nearest key change before it on its line, else the key the line starts in;
 * {@code inForce[p]}, what is in force at staff position {@code p}, seeded the first time the
 * walk meets {@code p} by scanning back from the edit's start for the nearest note at {@code p}
 * and taking its sounding — the scan stops at a key change or the start of a line with its own
 * key, and finding no note gives {@code key}'s alteration for {@code p}; {@code open}, the
 * positions whose next note is still to be decided, starting as the edit's vacated positions;
 * {@code previous}, the element before the current one skipping rests and breath marks, or
 * none at the start of a line; and {@code span}, the elements of the edited range plus the
 * first element at or after its end that is not a rest or a breath mark.
 *
 * <p>For each element {@code e} from the edit's start onward, in song order across lines:
 * <ol>
 * <li><em>Key change.</em> Meeting a key change, or entering a line with its own key, is a key
 *     change at that element. If {@code e} is the first element walked, {@code key} is set
 *     from it, every {@code inForce[p]} becomes its alteration for {@code p}, and the walk goes
 *     on. Otherwise the walk stops.</li>
 * <li><em>Note.</em> A note at position {@code p} is decided when it is
 *     {@link AccidentalState.Chosen Chosen} or {@link AccidentalState.Adopting Adopting},
 *     when {@code p} is in {@code open}, when the edit rekeys, or when it is in {@code span}.
 *     Its sounding is {@code Chosen}'s alteration, {@code inForce[p]} for {@code Adopting},
 *     and its own for {@link AccidentalState.Stamped Stamped}; its type is decided as above.
 *     A restamp is emitted when the note was {@code Chosen} or {@code Adopting}, or its stored
 *     type differs. Then a {@code Chosen} or {@code Adopting} note adds {@code p} to
 *     {@code open} and a decided {@code Stamped} note removes it; decided or not,
 *     {@code inForce[p]} becomes the note's sounding.</li>
 * <li><em>Advance.</em> Unless {@code e} is a rest or a breath mark, {@code previous} becomes
 *     {@code e}.</li>
 * <li><em>Stop</em> after {@code e} when {@code e} is at or past the end of {@code span},
 *     {@code open} is empty and the edit does not rekey, or at the end of the song.</li>
 * </ol>
 *
 * <p>Stopping early is sound because after any note the in-force alteration at its position
 * is that note's sounding: an edit's effect at a position ends with the first untouched note
 * there, whose sounding is what it was before the edit, so every later note at that position
 * is decided against what it was decided against before. A key change is the exception, since
 * the key's alteration feeds every note's state, and so a rekeying edit walks on to the next
 * key change.
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
    }

    /**
     * Decides every note {@code edit} can reach in {@code view}, under the policy above.
     *
     * <p>Every note in the edited range that is {@code Chosen} or {@code Adopting} is decided,
     * so a caller that applies the result leaves no such note on the line.
     *
     * @param view the song the edit has landed in, or a projection of it as the edit will
     *             leave it
     * @param edit the edit, in {@code view}'s coordinates
     * @return the restamps, in song order, so that applying them left to right leaves every
     *     note stamped as decided; empty when no note's state changes
     * @invariant every restamp names a note {@code view} holds, on the live line it is on or
     *     is about to land on
     * @invariant no restamp names a note before the edit's start
     */
    public static List<Restamp> walk(AccidentalScanView view, ChainEdit edit) {
        return new Walk(view, edit).run();
    }

    /**
     * Decides every note in {@code view}, for the load pass: walks from the song's first
     * element, and again from every key change and every line with its own key, each walk
     * rekeying so that it decides every note up to the next key change.
     *
     * @param view the song to decide
     * @return the restamps, in song order, so that applying them left to right leaves every
     *     note in the song stamped as decided
     * @invariant afterwards every {@code Chosen} or {@code Adopting} note in {@code view} has a
     *     restamp
     */
    public static List<Restamp> walkSong(AccidentalScanView view) {
        var restamps = new ArrayList<Restamp>();
        var first = true;

        for (var lineIndex = 0; lineIndex < view.lineCount(); lineIndex++) {
            for (var index = 0; index < view.elementCount(lineIndex); index++) {
                var startsOwnKey = (index == 0) && (view.ownKey(lineIndex) != null);

                if (first || startsOwnKey || (view.elementAt(lineIndex, index) instanceof KeyChangeElement)) {
                    restamps.addAll(walk(view, new ChainEdit(lineIndex, index, index, Set.of(), true)));
                }

                first = false;
            }
        }

        return restamps;
    }

    /** A place in the song: an index on a line, ordered in song order. */
    private record Cursor(int lineIndex, int index) implements Comparable<Cursor> {

        @Override
        public int compareTo(Cursor other) {
            var byLine = Integer.compare(lineIndex, other.lineIndex);
            return (byLine != 0) ? byLine : Integer.compare(index, other.index);
        }

        Cursor next() {
            return new Cursor(lineIndex, index + 1);
        }
    }

    /** One walk's state; see the class Javadoc for what each field keeps. */
    private static final class Walk {

        private final AccidentalScanView view;
        private final ChainEdit edit;
        private final Cursor rangeStart;
        private final Cursor rangeEnd;
        private final @Nullable Cursor spanEnd;
        private final Map<StaffPosition, Accidental> inForce = new HashMap<>();
        private final Set<StaffPosition> open;
        private final List<Restamp> restamps = new ArrayList<>();
        private Key key;
        private boolean inForceIsKey = false;
        private @Nullable StaffElement previous;

        Walk(AccidentalScanView view, ChainEdit edit) {
            this.view = view;
            this.edit = edit;
            rangeStart = new Cursor(edit.lineIndex(), edit.from());
            rangeEnd = new Cursor(edit.lineIndex(), edit.to());
            spanEnd = firstUnskippedAtOrAfter(rangeEnd);
            open = new HashSet<>(edit.vacated());
            key = Key.DEFAULT;
        }

        List<Restamp> run() {
            var cursor = existingAtOrAfter(rangeStart);

            if (cursor == null) {
                return restamps;
            }

            key = keyBefore(rangeStart);
            previous = previousBefore(rangeStart);
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

                if (((ownKey != null) || (keyChange != null)) && !first) {
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
                case AccidentalState.Stamped stamped -> stamped.chained().accidental();
                case AccidentalState.Chosen chosen -> chosen.sounding();
                case AccidentalState.Adopting _ -> current;
            };
            var stamped = (state instanceof AccidentalState.Stamped);
            var decided = !stamped || open.contains(position) || edit.rekeys() || inSpan(cursor);

            if (decided) {
                var type = ChainType.decide(sounding, current, key.alterationFor(pitchClassOf(position)), intervenes(position));
                var stored = (state instanceof AccidentalState.Stamped stampedState) ? stampedState.chained().type() : null;

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
            if (edit.rekeys() || !open.isEmpty()) {
                return false;
            }

            var end = (spanEnd != null) ? spanEnd : rangeEnd;
            return cursor.compareTo(end) >= 0;
        }

        private boolean inSpan(Cursor cursor) {
            var inRange = (cursor.compareTo(rangeStart) >= 0) && (cursor.compareTo(rangeEnd) < 0);
            return inRange || cursor.equals(spanEnd);
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
            return key.alterationFor(pitchClassOf(position));
        }

        /** The key in force just before {@code cursor}: the nearest key change before it on its line, else the key the line starts in. */
        private Key keyBefore(Cursor cursor) {
            var keyChange = lastKeyChangeBefore(cursor.lineIndex(), cursor.index());
            return (keyChange != null) ? keyChange : view.line(cursor.lineIndex()).getRunningKey();
        }

        private @Nullable Key lastKeyChangeBefore(int lineIndex, int endExclusive) {
            for (var index = endExclusive - 1; index >= 0; index--) {
                if (view.elementAt(lineIndex, index) instanceof KeyChangeElement keyChange) {
                    return keyChange.getKey();
                }
            }

            return null;
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

        /** The first place at or after {@code cursor} that holds an element, or null at the end of the song. */
        private @Nullable Cursor existingAtOrAfter(Cursor cursor) {
            var lineIndex = cursor.lineIndex();
            var index = cursor.index();

            while ((lineIndex < view.lineCount()) && (index >= view.elementCount(lineIndex))) {
                lineIndex++;
                index = 0;
            }

            return (lineIndex < view.lineCount()) ? new Cursor(lineIndex, index) : null;
        }

        private static boolean skipped(StaffElement element) {
            var type = element.getType();
            return type.isRest() || type.isBreathMark();
        }

        private static PitchClass pitchClassOf(StaffPosition position) {
            return PitchClass.at(position.value());
        }
    }
}
