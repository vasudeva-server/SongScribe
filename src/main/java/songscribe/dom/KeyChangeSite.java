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
 * <p>The site is stated by whoever resolved it, and never re-derived: {@link Line#keyBefore}
 * excludes a key change standing at the given index, so the one case that needs the key of a key
 * change sitting <em>on</em> the index reads it off the element instead.
 *
 * <p>Build one through {@link #lineKey}, {@link #existingKeyChange} or {@link #newPosition}
 * rather than through the canonical constructor, so the index convention is stated once.
 *
 * @param elementIndex the index within {@code line} the change is bound to; meaningless, and
 *                     {@link #LINE_OWN_KEY_INDEX}, for {@link Binding#LINE_KEY}
 */
public record KeyChangeSite(Line line, int elementIndex, Binding binding) {

    /**
     * The {@link #elementIndex} a {@link Binding#LINE_KEY} site carries: no element at all.
     * Named because a bare 0 reads as "the first element" when it means the opposite;
     * {@link KeyChangeElement}'s position invariant forbids a key change at index 0.
     */
    public static final int LINE_OWN_KEY_INDEX = 0;

    public enum Binding {

        LINE_KEY,

        EXISTING_KEY_CHANGE,

        NEW_POSITION
    }

    public static KeyChangeSite lineKey(Line line) {
        return new KeyChangeSite(line, LINE_OWN_KEY_INDEX, Binding.LINE_KEY);
    }

    /**
     * @param keyChangeIndex the index of a {@link KeyChangeElement} within {@code line}
     */
    public static KeyChangeSite existingKeyChange(Line line, int keyChangeIndex) {
        return new KeyChangeSite(line, keyChangeIndex, Binding.EXISTING_KEY_CHANGE);
    }

    /**
     * @param insertionIndex the index it will land at, which carries no key change today
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

            case NEW_POSITION -> line.keyBefore(elementIndex);
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

    private boolean establishesOwnKey() {
        return binding == Binding.LINE_KEY && line.getKey() == null;
    }

    /**
     * A site is built for this binding only from an element the resolver hit-tested on
     * {@link #line()}, so anything else there means the line was mutated under a modal dialog.
     */
    public KeyChangeElement boundKeyChange() {
        if (line.getElement(elementIndex) instanceof KeyChangeElement keyChange) {
            return keyChange;
        }

        throw RuntimeError.exit("no key change at the index this site was bound to");
    }
}
