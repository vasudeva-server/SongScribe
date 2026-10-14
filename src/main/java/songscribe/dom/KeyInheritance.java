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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.Mutation;

/**
 * The key each line of a song inherits from the line before it.
 *
 * <h2>Key invariant</h2>
 * A song has no key of its own — its key is line 0's. Across the line list, line 0 establishes a
 * key of its own ({@link Line#getKey()} is non-null), and every other line's inherited key
 * equals the key in effect at the end of the line before it. {@link Line#getRunningKey()} is
 * total.
 */
public final class KeyInheritance {

    private final Song song;

    /**
     * State, not a cache: every mutation that can move a key drives {@link #maintainInvariant}
     * rather than an invalidate-and-recompute path.
     */
    private final Map<Line, Key> inheritedKeys = new IdentityHashMap<>();

    KeyInheritance(Song song) {
        this.song = song;
    }

    /**
     * A line insertion or deletion can promote a different line to index 0, and only the key the
     * song started in can tell {@link #maintainInvariant} what that line should now establish, so
     * this is asked before the mutation runs.
     *
     * @return null when {@code mutation} cannot promote a line to index 0, or while a document is
     *     being built, where line 0 may not be in a key yet — the load settles that at the end, in
     *     one pass
     */
    @Nullable
    Key startingKeyBefore(Mutation mutation) {
        if (!(mutation instanceof LineInsertion || mutation instanceof LineDeletion)) {
            return null;
        }

        var lines = song.getLines();

        if (lines.isEmpty()) {
            return null;
        }

        var firstLine = lines.getFirst();
        var ownKey = firstLine.getKey();
        return ownKey != null ? ownKey : inheritedKeys.get(firstLine);
    }

    /**
     * Distinct from {@link Line#getRunningKey()}: {@link Line#setKey}'s no-op normalization needs
     * the inherited value, not the line's own.
     *
     * @return null when nothing precedes it — line 0, a line not in this song, or any line while a
     *         load is still in progress
     */
    @Nullable
    Key inheritedKeyOf(Line line) {
        return inheritedKeys.get(line);
    }

    /**
     * @param startingKeyBefore what {@link #startingKeyBefore} answered for {@code mutation}
     *                          before it ran
     */
    void maintainInvariant(Mutation mutation, @Nullable Key startingKeyBefore) {
        switch (mutation) {
            case LineInsertion _ -> repairLineZeroKey(startingKeyBefore);
            case LineDeletion deletion -> {
                // A line out of the song inherits from nothing, so it must not go on answering
                // with what it inherited while it was in one. Dropping the entry also bounds the
                // line's lifetime: LineDeletion holds the line for undo, and without this the map
                // would keep it reachable for the song's whole life after that record is gone.
                inheritedKeys.remove(deletion.deletedLine());
                repairLineZeroKey(startingKeyBefore);
            }
            default -> {
                // Only a change to which lines exist can leave line 0 without a key of its own.
            }
        }

        var keyMove = keyMoveOf(mutation);

        if (keyMove != null) {
            propagateInheritedKeys(keyMove);
        }
    }

    /**
     * @param originIndex    the line {@link #inheritanceFrom} is asked about
     * @param reachFromIndex the first line {@link #keyMoveReach} names, at or before
     *                       {@code originIndex}: a line whose start changes also changes the
     *                       cautionary on the line before it
     */
    private record KeyMove(int originIndex, int reachFromIndex) {
    }

    /**
     * The one enumeration of the mutations that can move a key, read by
     * {@link #maintainInvariant} and {@link #keyMoveReach}; a second would drift out of step
     * and show as a line rendering the key it was in before the edit.
     *
     * @return {@code null} when {@code mutation} moves no key, including one naming a line no
     *     longer in this song or leaving no line to re-derive from
     */
    private @Nullable KeyMove keyMoveOf(Mutation mutation) {
        return switch (mutation) {
            case LineInsertion insertion -> lineStartKeyMove(insertion.line());
            case LineDeletion deletion -> departingLineKeyMove(deletion.lineIndex());
            case LineKeyChange change -> lineStartKeyMove(change.line());
            case ElementInsertion insertion -> midLineKeyMove(
                insertion.line(),
                changesKey(insertion.element()));
            case ElementDeletion deletion -> midLineKeyMove(
                deletion.line(),
                deletion.deletedElements().stream().anyMatch(KeyInheritance::changesKey));
            case ElementReplacement replacement -> midLineKeyMove(
                replacement.line(),
                changesKey(replacement.oldElement()) || changesKey(replacement.newElement()));
            case ElementModification modification -> midLineKeyMove(
                modification.line(),
                changesKey(modification.beforeElement()) || changesKey(modification.afterElement()));
            default -> null;
        };
    }

    private @Nullable KeyMove lineStartKeyMove(Line line) {
        if (!song.contains(line)) {
            return null;
        }

        var lineIndex = line.index();
        return new KeyMove(lineIndex, Math.max(lineIndex - 1, 0));
    }

    /**
     * @return null when no line stands where the chain is re-derived from: the song is now empty,
     *     or a later mutation in the same batch has since shortened it
     */
    private @Nullable KeyMove departingLineKeyMove(int lineIndex) {
        var originIndex = Math.max(lineIndex - 1, 0);

        if (originIndex >= song.lineCount()) {
            return null;
        }

        return new KeyMove(originIndex, originIndex);
    }

    private @Nullable KeyMove midLineKeyMove(Line line, boolean movesKey) {
        if (!movesKey) {
            return null;
        }

        if (!song.contains(line)) {
            return null;
        }

        var lineIndex = line.index();
        return new KeyMove(lineIndex, lineIndex);
    }

    private static boolean changesKey(StaffElement element) {
        return element.getType().isKeyChange();
    }

    /**
     * Returns the lines whose drawn key content {@code mutation}'s key move can have changed: the
     * line the move is felt from, the line before it when the move changes the key that line's
     * cautionary key signature warns of, and every following line that runs in the key it
     * inherits, through the end of the inheritance chain. Empty when the mutation moves no key.
     *
     * <p>The set is the whole chain rather than exactly the lines whose running key moved, because
     * it follows the same stopping rule propagation runs on. A line in it whose key did not move
     * draws what it drew before, so re-solving it costs work and never draws anything wrong.
     *
     * <p>A view holding per-line geometry needs this. A line's header signature and the spacing
     * around it are solved from the key that line runs in, and the room kept clear at its end is
     * solved from the cautionary it leads into — so a key move leaves stale geometry on lines no
     * mutation names, and the line ahead of the move is one of them.
     *
     * <p>Asked after the mutation has been applied, so the answer describes the document as it now
     * stands, which is what a stale view has to be brought up to.
     *
     * <p>A line insertion or deletion answers here too, but no view need ask: it changes which
     * lines exist, which costs a rebuild of every line rather than an invalidation of some.
     *
     * @param mutation the mutation to ask about
     * @return the lines, in song order, or empty when {@code mutation} moves no key
     */
    public List<Line> keyMoveReach(Mutation mutation) {
        var keyMove = keyMoveOf(mutation);

        if (keyMove == null) {
            return List.of();
        }

        var lines = song.getLines();
        var origin = lines.get(keyMove.originIndex());
        var reach = new ArrayList<>(lines.subList(keyMove.reachFromIndex(), keyMove.originIndex() + 1));

        for (var inheritance : inheritanceFrom(origin, origin.keyAtEndOfLine()).inheriting()) {
            reach.add(inheritance.line());
        }

        return List.copyOf(reach);
    }

    /**
     * Recorded as a {@link LineKeyChange} in the same batch, so undo puts the line back to
     * inheriting. Skipped while mutation tracking is suspended: a file reader adds its lines one
     * at a time, and a key guessed here would stand, because {@link #rebuildAfterParsing()} leaves
     * a line that already has one alone.
     */
    private void repairLineZeroKey(@Nullable Key startingKeyBefore) {
        var lines = song.getLines();

        if (lines.isEmpty() || song.isMutationTrackingSuspended()) {
            return;
        }

        var firstLine = lines.getFirst();

        if (firstLine.getKey() != null) {
            inheritedKeys.remove(firstLine);
            return;
        }

        var inherited = inheritedKeys.get(firstLine);
        Key materialized;

        if (inherited != null) {
            materialized = inherited;
        } else if (startingKeyBefore != null) {
            materialized = startingKeyBefore;
        } else {
            materialized = Key.DEFAULT;
        }

        // Cleared first so setKey's no-op normalization cannot collapse the repair back to null.
        inheritedKeys.remove(firstLine);
        firstLine.setKey(materialized);
    }

    private void propagateInheritedKeys(KeyMove keyMove) {
        var lines = song.getLines();

        // A document under construction may not have put line 0 in a key yet, and until it does
        // there is no key for any later line to be in either. The load settles the whole list in
        // one pass at the end.
        if (lines.isEmpty() || lines.getFirst().getKey() == null) {
            return;
        }

        var originIndex = keyMove.originIndex();
        var origin = lines.get(originIndex);

        // Line 0 inherits nothing. Any other origin is refreshed whether or not it has a new
        // predecessor: the entry is derived, so refreshing one that did not move stores what was
        // already there.
        if (originIndex > 0) {
            inheritedKeys.put(origin, lines.get(originIndex - 1).keyAtEndOfLine());
        }

        var chain = inheritanceFrom(origin, origin.keyAtEndOfLine());

        for (var inheritance : chain.inheriting()) {
            inheritedKeys.put(inheritance.line(), inheritance.inheritedKey());
        }

        var stop = chain.stop();

        if (stop != null) {
            inheritedKeys.put(stop.line(), stop.inheritedKey());
        }
    }

    /**
     * @param line establishes no key of its own
     */
    public record Inheritance(Line line, Key inheritedKey) {

        public Key keyAtEnd() {
            return line.keyAtEndUnder(inheritedKey);
        }
    }

    /**
     * @param inheriting consecutive and in song order; empty when the line after the origin has a
     *                   key of its own or the origin is the last line
     * @param stop       null when the inheriting lines run to the end of the song
     */
    public record InheritanceChain(List<Inheritance> inheriting, @Nullable Stop stop) {

        public InheritanceChain {
            inheriting = List.copyOf(inheriting);
        }

        /**
         * @param inheritedKey the key the line before it leaves off in, which the line runs in the
         *                     moment it gives up its own key
         */
        public record Stop(Line line, Key inheritedKey, Key ownKey) {
        }
    }

    /**
     * Returns the inheritance chain after {@code origin}, supposing {@code origin} leaves off in
     * {@code originEndKey}: every line after it that runs in the key it inherits, each with that
     * key, and the first line after them that establishes a key of its own.
     *
     * <p>This is the stopping rule the whole feature runs on — <em>forward to the first line with
     * its own key</em> — stated once: that line's running key cannot move, so nothing past it can
     * either. The line that stops the chain is still reported, because what it inherits is kept
     * current too: giving that line a key is compared against it, and clearing that line's key
     * makes it the key the line runs in.
     *
     * <p>Pure: it reads each line's own key and elements and never the inherited keys this song
     * keeps. A caller projecting an edit that has not happened yet therefore gets the chain the
     * edit will leave, provided the edit changes nothing after {@code origin} but the key
     * {@code origin} leaves off in — not a later line's own key, its elements, or which lines
     * exist.
     *
     * @param origin       the line the chain starts after; one of this song's lines
     * @param originEndKey the key to suppose {@code origin} leaves off in
     * @return the chain; with no inheriting lines and no stop when {@code origin} is the last line
     * @invariant the inheriting lines, then the stop, are consecutive, the first being the line
     *     after {@code origin}
     * @invariant the first line reported inherits {@code originEndKey}
     * @invariant each later line reported inherits the {@link Inheritance#keyAtEnd() end key} of
     *     the inheriting line before it
     */
    public InheritanceChain inheritanceFrom(Line origin, Key originEndKey) {
        var lines = song.getLines();
        var inheriting = new ArrayList<Inheritance>();
        var inheritedKey = originEndKey;

        for (var lineIndex = origin.index() + 1; lineIndex < lines.size(); lineIndex++) {
            var line = lines.get(lineIndex);
            var ownKey = line.getKey();

            if (ownKey != null) {
                return new InheritanceChain(inheriting, new InheritanceChain.Stop(line, inheritedKey, ownKey));
            }

            var inheritance = new Inheritance(line, inheritedKey);
            inheriting.add(inheritance);
            inheritedKey = inheritance.keyAtEnd();
        }

        return new InheritanceChain(inheriting, null);
    }

    /**
     * File readers build lines under suspended mutation tracking, so the per-mutation
     * propagation in {@link Song#applyChange} never ran for any of them.
     *
     * <p>Must be called while mutation tracking is still suspended, so the fix-up is silent — no
     * notification, no undo entry and no {@code modified} flag.
     *
     * <p>Line 0 has nothing to inherit from, so a document whose first line establishes no key of
     * its own falls back to {@link Key#DEFAULT}. Every reader is expected to have put the key its
     * file carried on line 0 before calling this — the fallback is for a file that named no key
     * anywhere, not the route by which a stated one arrives.
     */
    void rebuildAfterParsing() {
        // Emptied rather than overwritten: Song.loadFrom replaces the line list wholesale, without
        // a LineDeletion per line, so entries for the lines it discarded have nothing else to take
        // them out and would keep those lines reachable for the rest of the song's life.
        inheritedKeys.clear();

        var lines = song.getLines();

        if (lines.isEmpty()) {
            return;
        }

        var firstLine = lines.getFirst();

        if (firstLine.getKey() == null) {
            firstLine.setKey(Key.DEFAULT);
        }

        for (var lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            inheritedKeys.put(lines.get(lineIndex), lines.get(lineIndex - 1).keyAtEndOfLine());
        }
    }

    /**
     * One of the two places the stranded-key-change rule is enforced: an edit reaches only what
     * its own key move strands, so only a read sees a stranding an older document holds.
     * Removing a stranded key change leaves the chain {@link #rebuildAfterParsing()} settled
     * intact, so no second pass is owed.
     */
    private void removeStrandedKeyChanges() {
        for (var line : song.getLines()) {
            line.deleteRanges(line.redundantKeyChangeRanges(line.getRunningKey()));
        }
    }

    /**
     * One call because the order is not the reader's to choose: the stranding repair reads each
     * line's running key, which only the inheritance pass settles. Must be called while mutation
     * tracking is still suspended, so it is silent.
     */
    public void settleAfterParsing() {
        rebuildAfterParsing();
        removeStrandedKeyChanges();
    }
}
