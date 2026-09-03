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
 * and every path that can create one keeps it there: the editing UI inserts a
 * {@link ElementType#SINGLE_BARLINE} when the position the user chose has none, the deletion
 * pairing removes a key change together with the barline it sits behind, and the MusicXML
 * reader rejects a document whose mid-measure {@code <key>} has no barline before it. Layout,
 * width and MusicXML writing all read the invariant rather than re-checking it.
 *
 * <p>A key change carries no duration and no pitch, so it inherits
 * {@link StructuralElement}'s answers for staff position, dots and accidental.
 */
public class KeyChangeElement extends StructuralElement {

    private Key key;

    /**
     * The key this element changes <em>from</em>, for as long as it sits on no line — see
     * {@link #forMeasurement}. Read only while {@link #getParentLine()} is null: an element that
     * belongs to a document reads the key it changes from off its line every time, so this can
     * never pin a stale width on one. Null on an element that was never given a key to measure
     * against, which is every element built to be committed rather than measured.
     */
    private @Nullable Key detachedPreviousKey;

    /**
     * @param key the key taking effect here; never null
     */
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
     * Returns a key change built to be <em>measured</em> rather than stored: one that can
     * report its {@link #extent()} while it sits on no line, by being told the key it changes
     * from instead of resolving one.
     *
     * <p>This is what lets an edit be sized before it is committed. The key an inserted key change
     * will cancel is fixed by the elements <em>before</em> the insertion point, and an insertion
     * does not move those, so the caller can read it off the unmodified line
     * ({@code line.keyAt(insertionIndex - 1)}) and hand it here.
     *
     * <p>Adding one to a line is harmless but pointless: once it has a line, the line is what it
     * reads, and the key handed here is never consulted again. Build a plain
     * {@link #KeyChangeElement(Key)} to commit.
     *
     * @param key         the key that would take effect at the measured position
     * @param previousKey the key in effect immediately before that position
     * @return a detached element that reports the drawn extent of that change
     */
    public static KeyChangeElement forMeasurement(Key key, Key previousKey) {
        return new KeyChangeElement(key, previousKey);
    }

    /**
     * Whether writing a key change at {@code insertionIndex} on {@code line} needs a barline
     * placed in front of it for this class's position invariant to hold.
     *
     * <p>Two callers have to agree about this and would otherwise each spell out the test: the fit
     * pre-check, which must measure the barline's column because it is part of the edit, and the
     * commit, which must actually write it. A disagreement between them accepts an edit that then
     * does not fit, or reserves room for a barline that never arrives — neither of which anything
     * would report.
     *
     * @param line the line the key change would be written into
     * @param insertionIndex the index it would land at, at least
     *     {@link Line#FIRST_LEGAL_KEY_CHANGE_INDEX}
     * @return {@code true} when the element already at {@code insertionIndex - 1} is neither a
     *     barline nor a repeat, so a {@link ElementType#SINGLE_BARLINE} is owed in front
     */
    public static boolean needsBarlineBefore(Line line, int insertionIndex) {
        var precedingType = line.getElement(insertionIndex - 1).getType();

        return !precedingType.isBarLine() && !precedingType.isRepeat();
    }

    @Override
    public KeyChangeElement clone() {
        var copy = new KeyChangeElement(key);
        copy.copyStateFrom(this);

        return copy;
    }

    /**
     * Copies the key this element establishes, and the key it was told to measure against while
     * detached ({@link #forMeasurement}).
     *
     * <p>The key is this element's whole reason to exist and nothing else on the line records
     * it, so an in-place restore that left it behind would undo a key edit into an element
     * still establishing the key the edit gave it.
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

    /**
     * @return the key taking effect at this element's position; never null
     */
    public Key getKey() {
        return key;
    }

    /**
     * Returns what this key change draws and how wide it is — the pair of keys the change runs
     * between, which is the smallest thing that can answer either question.
     *
     * <p>The key changed from is the line's, whenever this element is on one: the position
     * invariant guarantees an element before it to read the key from, and re-reading it each time
     * is what keeps the extent tracking the line around it. An element on no line reports against
     * the key it was told when {@link #forMeasurement} built it, which is what lets an edit be
     * sized before it is committed.
     *
     * <p>See {@code docs/key-changes.md} for the cancellation policy the accidentals follow.
     *
     * @return the extent of the change this element makes
     * @throws IllegalStateException if this element is on no line and was not built by
     *     {@link #forMeasurement}, so nothing states the key it changes from. A width guessed
     *     there would be too narrow for every change that cancels, and would be written into the
     *     document as though it were measured.
     */
    public KeySignatureExtent extent() {
        return new KeySignatureExtent(previousKey(), key);
    }

    /**
     * Returns the accidentals this key change draws, in the order they are laid out: the
     * cancelling naturals the policy calls for, if any, followed by the new key's own
     * accidentals.
     *
     * @return {@link #extent()}'s accidentals, left to right; empty when this element re-states
     *     the key already in effect, because a change to the same key draws nothing
     * @throws IllegalStateException under {@link #extent()}'s condition
     */
    public List<Key.DrawnAccidental> drawnAccidentals() {
        return extent().accidentals();
    }

    /**
     * Returns the drawn width of the key change this element makes: the room
     * {@link #drawnAccidentals()} takes.
     *
     * @return {@link #extent()}'s width in staff spaces; zero when this element re-states the key
     *     already in effect, because nothing is drawn
     * @throws IllegalStateException under {@link #extent()}'s condition
     */
    @Override
    public double getAdvanceWidthSs() {
        return extent().widthSs();
    }

    /**
     * Returns the same width {@link #getAdvanceWidthSs()} reports. A key change draws nothing
     * but its accidentals — no stem, no flag, no dots — so the glyph run and the content are the
     * same extent, and the type's own width is only the floor described in
     * {@code ElementType.computeKeySignatureBoundsSs}.
     *
     * @return the laid-out width of this key change, in staff spaces
     * @throws IllegalStateException under {@link #extent()}'s condition
     */
    @Override
    public double getGlyphWidthSs() {
        return getAdvanceWidthSs();
    }

    /** Resolves the key this element changes from, per {@link #extent()}'s two cases. */
    private Key previousKey() {
        var line = getParentLine();

        if (line != null) {
            return line.keyAt(line.getElementIndex(this) - 1);
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
     * Replaces the key this element establishes.
     *
     * <p>This is a plain field write. Everything that follows from a key change — the running
     * key of later lines, re-spelled accidentals, an invalidated layout — is the caller's to
     * drive through the mutation the edit belongs to, not a side effect of this setter.
     *
     * @param key the key taking effect here; never null
     */
    public void setKey(Key key) {
        this.key = key;
    }
}
