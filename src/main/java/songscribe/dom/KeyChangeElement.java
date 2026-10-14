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

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A key change written into the middle of a line: one {@link Key}, taking effect at this
 * element's position and holding until the next key change or the end of the song.
 *
 * <p><b>Position invariant.</b> A key change element is never the element at index 0 of a
 * line, and is always immediately preceded by an element whose {@link ElementType#isBarLine()}
 * or {@link ElementType#isRepeat()} is true. A key change belongs at the head of a measure,
 * and every path that can create one keeps it there. Layout, width and MusicXML writing read
 * the invariant rather than re-checking it.
 */
public final class KeyChangeElement extends StructuralElement {

    private Key key;

    /**
     * The key this element changes <em>from</em> while it sits on no line — see
     * {@link #forMeasurement}. Read only while {@link #getParentLine()} is null. Null on an
     * element never given a key to measure against.
     */
    private @Nullable Key detachedPreviousKey;

    public KeyChangeElement(Key key) {
        super(ElementType.KEY_CHANGE);
        this.key = key;
        detachedPreviousKey = null;
    }

    private KeyChangeElement(Key key, Key detachedPreviousKey) {
        super(ElementType.KEY_CHANGE);
        this.key = key;
        this.detachedPreviousKey = detachedPreviousKey;
    }

    /**
     * Lets an edit be sized before it is committed. Once the element has a line, the key handed
     * here is never consulted again; build a plain {@link #KeyChangeElement(Key)} to commit.
     */
    public static KeyChangeElement forMeasurement(Key key, Key previousKey) {
        return new KeyChangeElement(key, previousKey);
    }

    @Override
    public KeyChangeElement clone() {
        var copy = new KeyChangeElement(key);
        copy.copyStateFrom(this);

        return copy;
    }

    /**
     * Copies the key this element establishes, so an in-place restore undoes a key edit.
     *
     * @param source the key change whose key to take; always a {@code KeyChangeElement},
     *               because a copy names an element of the same class
     */
    @Override
    protected void copySubtypeStateFrom(StaffElement source) {
        var sourceKeyChange = (KeyChangeElement) source;
        key = sourceKeyChange.key;
        detachedPreviousKey = sourceKeyChange.detachedPreviousKey;
    }

    public Key getKey() {
        return key;
    }

    /**
     * On a line, the key changed from is the line's, re-read on every call so the extent tracks
     * the line around it. On no line it is the key {@link #forMeasurement} was given; an element
     * it did not build has none, and a guessed width would be too narrow for every change that
     * cancels. See {@code docs/key-changes.md}.
     */
    public KeySignatureExtent extent() {
        return new KeySignatureExtent(previousKey(), key);
    }

    public List<Key.DrawnAccidental> drawnAccidentals() {
        return extent().accidentals();
    }

    @Override
    public double getAdvanceWidthSs() {
        return extent().widthSs();
    }

    /**
     * The glyph run and the content are the same extent; the type's own width is only the floor
     * described in {@code ElementType.computeKeySignatureBoundsSs}.
     */
    @Override
    public double getGlyphWidthSs() {
        return getAdvanceWidthSs();
    }

    private Key previousKey() {
        var line = getParentLine();

        if (line != null) {
            return line.keyBefore(line.getElementIndex(this));
        }

        var detached = detachedPreviousKey;

        if (detached == null) {
            throw new IllegalStateException(
                "key change for " + key + " is on no line and was not told the key it changes"
                    + " from; build it with forMeasurement to measure it before it is committed");
        }

        return detached;
    }

    /**
     * A plain field write. Everything that follows from a key change is the caller's to drive
     * through the mutation the edit belongs to, not a side effect of this setter.
     */
    public void setKey(Key key) {
        this.key = key;
    }
}
