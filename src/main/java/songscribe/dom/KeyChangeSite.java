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

import songscribe.error.RuntimeError;

/**
 * A place in a line where a key change is being made, and the key already in effect there.
 *
 * <p>There are three such places, and they differ only in where the key in effect is read from: a
 * line's own key comes off the line, a key change already standing somewhere in the line
 * carries its own, and a bare position inherits whatever is running there. Naming the three is what
 * lets one question — {@link #keyInEffect()} — answer for all of them.
 *
 * <p><strong>The site is stated by whoever resolved it, and never re-derived.</strong> Whether the
 * index carries a key change already is a fact the resolver has; asking the line to recover it
 * would put a lookup in the way of an answer that already arrived. That is also what frees
 * {@link Line#keyAt} from its inclusive bound: the one case that needs the key of a key change
 * sitting <em>on</em> the index reads it off the element instead.
 *
 * <p>Build one through {@link #lineKey}, {@link #existingKeyChange} or {@link #newPosition} rather
 * than through the canonical constructor, so the index convention is stated once here rather than
 * at each call site.
 *
 * @param line         the line the change is being made in
 * @param elementIndex the index within {@code line} the change is bound to; meaningless, and
 *                     {@link #LINE_OWN_KEY_INDEX}, for {@link Binding#LINE_KEY}
 * @param binding      which of the three places this is
 */
public record KeyChangeSite(Line line, int elementIndex, Binding binding) {

    /**
     * The {@link #elementIndex} a {@link Binding#LINE_KEY} site carries: no element at all.
     *
     * <p>Named because a bare 0 reads as "the first element" when it means the opposite.
     * {@link KeyChangeElement}'s position invariant forbids a key change at index 0, which is
     * what leaves the value free to carry this meaning.
     */
    public static final int LINE_OWN_KEY_INDEX = 0;

    /** Which of the three places in a line a key change is bound to. */
    public enum Binding {

        /** The line's own key: what the header draws, and what a cautionary warns of. */
        LINE_KEY,

        /** A key change already standing at the bound index. */
        EXISTING_KEY_CHANGE,

        /** A position inside the line with no key change on it. */
        NEW_POSITION
    }

    /**
     * @param line the line whose own key is being established or changed
     * @return a site bound to {@code line}'s own key
     */
    public static KeyChangeSite lineKey(Line line) {
        return new KeyChangeSite(line, LINE_OWN_KEY_INDEX, Binding.LINE_KEY);
    }

    /**
     * @param line           the line the key change stands on
     * @param keyChangeIndex the index of a {@link KeyChangeElement} within {@code line}
     * @return a site bound to the key change at {@code keyChangeIndex}
     */
    public static KeyChangeSite existingKeyChange(Line line, int keyChangeIndex) {
        return new KeyChangeSite(line, keyChangeIndex, Binding.EXISTING_KEY_CHANGE);
    }

    /**
     * @param line           the line a key change will be written into
     * @param insertionIndex the index it will land at, which carries no key change today
     * @return a site bound to that position
     */
    public static KeyChangeSite newPosition(Line line, int insertionIndex) {
        return new KeyChangeSite(line, insertionIndex, Binding.NEW_POSITION);
    }

    /**
     * The key sounding here as the document now stands, which is the key a change writing the same
     * value would leave unchanged.
     *
     * @return the key in effect at this site; never null, because every position in every line is
     *         in some key
     * @effects when the binding is {@link Binding#EXISTING_KEY_CHANGE} and no key change stands at
     *          {@link #elementIndex()}, the failure is logged, shown to the user as a fatal error,
     *          and the application exits, so the method does not return in that case
     */
    public Key keyInEffect() {
        return switch (binding) {
            case LINE_KEY -> line.getRunningKey();

            // Off the element, not out of Line.keyAt: the resolver pointed at this key change, so
            // its own key is the answer without a query whose bound has to be argued about.
            case EXISTING_KEY_CHANGE -> boundKeyChange().getKey();

            case NEW_POSITION -> line.keyAt(elementIndex);
        };
    }

    /**
     * Whether writing {@code key} here would change the document.
     *
     * <p>Not simply {@code key != keyInEffect()}. A {@link Binding#LINE_KEY} site on a line that
     * <em>inherits</em> its key has no key of its own, so writing the key the line already runs in
     * still changes something: the line begins establishing that key instead of following the line
     * before it, which is where {@code Song}'s inherited-key propagation stops and what decides
     * whether a later change to an earlier line reaches this one. It is also the difference the
     * undo step is named for — <em>Add</em> rather than <em>Change</em>.
     *
     * @param key the key a commit would write
     * @return {@code true} when that commit would leave the document saying something it does not
     *         say now
     */
    public boolean wouldChangeAnything(Key key) {
        return !key.equals(keyInEffect()) || establishesOwnKey();
    }

    /**
     * Whether a commit here would give {@link #line()} a key of its own that it does not have.
     *
     * @return {@code true} only for a {@link Binding#LINE_KEY} site on an inheriting line
     */
    private boolean establishesOwnKey() {
        return binding == Binding.LINE_KEY && line.getKey() == null;
    }

    /**
     * The key change standing at {@link #elementIndex()}, which is what a
     * {@link Binding#EXISTING_KEY_CHANGE} change is written onto.
     *
     * <p>The guard states the binding's precondition rather than defending against it: a site is
     * built for this binding only from an element the resolver hit-tested on {@link #line()}, so
     * anything else there means the line was mutated under a dialog that is modal over it.
     *
     * @return the key change at the bound index
     * @effects when no key change stands there — which includes every binding but
     *          {@link Binding#EXISTING_KEY_CHANGE} — the failure is logged, shown to the user as a
     *          fatal error, and the application exits, so the method does not return in that case
     */
    public KeyChangeElement boundKeyChange() {
        if (line.getElement(elementIndex) instanceof KeyChangeElement keyChange) {
            return keyChange;
        }

        throw RuntimeError.exit("no key change at the index this site was bound to");
    }
}
