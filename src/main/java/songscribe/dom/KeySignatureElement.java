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

import org.jspecify.annotations.Nullable;

/**
 * A key change written into the middle of a line: one {@link Key}, taking effect at this
 * element's position and holding until the next key change or the end of the song.
 *
 * <p><b>Position invariant.</b> A key signature element is never the element at index 0 of a
 * line, and is always immediately preceded by an element whose {@link ElementType#isBarLine()}
 * or {@link ElementType#isRepeat()} is true. A key change belongs at the head of a measure,
 * and every path that can create one keeps it there: the editing UI inserts a
 * {@link ElementType#SINGLE_BARLINE} when the position the user chose has none, the deletion
 * pairing removes a key signature together with the barline it sits behind, and the MusicXML
 * reader rejects a document whose mid-measure {@code <key>} has no barline before it. Layout,
 * width and MusicXML writing all read the invariant rather than re-checking it.
 *
 * <p>A key signature carries no duration and no pitch, so it inherits
 * {@link StructuralElement}'s answers for staff position, dots and accidental.
 */
public class KeySignatureElement extends StructuralElement {

    private Key key;

    /**
     * The key this element changes <em>from</em>, when it was built to be measured off a line —
     * see {@link #forMeasurement}. Null on every element that belongs to a document, which reads
     * the key it changes from off the line it sits on instead.
     */
    private final @Nullable Key measurementPreviousKey;

    /**
     * @param key the key taking effect here; never null
     */
    public KeySignatureElement(Key key) {
        super(ElementType.KEY_SIGNATURE);
        this.key = key;
        measurementPreviousKey = null;
    }

    private KeySignatureElement(Key key, Key measurementPreviousKey) {
        super(ElementType.KEY_SIGNATURE);
        this.key = key;
        this.measurementPreviousKey = measurementPreviousKey;
    }

    private KeySignatureElement(KeySignatureElement source) {
        super(source);
        key = source.key;
        measurementPreviousKey = source.measurementPreviousKey;
    }

    /**
     * Returns a key signature built to be <em>measured</em> rather than stored: one that reports
     * {@link #getContentWidthSs()} against the stated {@code previousKey} instead of resolving it
     * from a line it does not sit on.
     *
     * <p>This is what lets an insertion be sized before it is committed. The key an inserted
     * signature will cancel is fixed by the elements <em>before</em> the insertion point, and an
     * insertion does not move those, so the caller can read it off the unmodified line
     * ({@code line.keyAt(insertionIndex - 1)}) and hand it here.
     *
     * <p>Never add one of these to a line. Its previous key is a snapshot taken when it was built,
     * where an element in a document re-reads its own each time; adding one would pin a width that
     * stops tracking the line around it. Build a plain {@link #KeySignatureElement(Key)} to commit.
     *
     * @param key         the key that would take effect at the measured position
     * @param previousKey the key in effect immediately before that position
     * @return a detached element that reports the drawn width of that change
     */
    public static KeySignatureElement forMeasurement(Key key, Key previousKey) {
        return new KeySignatureElement(key, previousKey);
    }

    /**
     * Whether writing a key signature at {@code insertionIndex} on {@code line} needs a barline
     * placed in front of it for this class's position invariant to hold.
     *
     * <p>Two callers have to agree about this and would otherwise each spell out the test: the fit
     * pre-check, which must measure the barline's column because it is part of the edit, and the
     * commit, which must actually write it. A disagreement between them accepts an edit that then
     * does not fit, or reserves room for a barline that never arrives — neither of which anything
     * would report.
     *
     * @param line the line the key signature would be written into
     * @param insertionIndex the index it would land at, at least
     *     {@link Line#FIRST_LEGAL_KEY_SIGNATURE_INDEX}
     * @return {@code true} when the element already at {@code insertionIndex - 1} is neither a
     *     barline nor a repeat, so a {@link ElementType#SINGLE_BARLINE} is owed in front
     */
    public static boolean needsBarlineBefore(Line line, int insertionIndex) {
        var precedingType = line.getElement(insertionIndex - 1).getType();

        return !precedingType.isBarLine() && !precedingType.isRepeat();
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public KeySignatureElement clone() {
        return new KeySignatureElement(this);
    }

    /**
     * @return the key taking effect at this element's position; never null
     */
    public Key getKey() {
        return key;
    }

    /**
     * Returns the drawn width of the key change this element makes: the accidentals
     * {@link KeyChange} lays out between the key in effect immediately before this element and
     * this element's own key.
     *
     * <p>Zero when this element re-states the key already in effect, because nothing is drawn.
     *
     * <p>The key changed from comes from one of three places, in this order: the snapshot an
     * element built by {@link #forMeasurement} carries; otherwise the line this element sits on,
     * where the position invariant guarantees an element before it to read the key from; otherwise
     * C major, which is the only defined answer when nothing states what precedes it, and yields
     * this signature's own accidentals with no cancellation. That last case is reachable only from
     * a preview of an element that is on no line and was not built to be measured — a clipboard
     * fragment, say. A change that cancels draws wider than it reports there, so an edit must not
     * be gated on it; build the element with {@link #forMeasurement} to gate on a real width.
     *
     * @return the laid-out width of this key change, in staff spaces
     */
    @Override
    public double getContentWidthSs() {
        return KeyChange.widthSs(previousKey(), key);
    }

    /** Resolves the key this element changes from, per {@link #getContentWidthSs()}'s three cases. */
    private Key previousKey() {
        var measured = measurementPreviousKey;

        if (measured != null) {
            return measured;
        }

        var line = getParentLine();

        if (line == null) {
            return new Key(KeyType.NONE, 0);
        }

        return line.keyAt(line.getElementIndex(this) - 1);
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
