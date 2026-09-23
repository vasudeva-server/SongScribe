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
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * An element occupying a column of a staff line: a note, a rest, a barline, a breath mark, a
 * mid-line key change. Abstract, and instantiated only through one of its leaves, one per
 * {@link ElementAppearance} arm — {@link NoteElement} for a note, {@link RestElement} for a
 * rest, {@link BreathMarkElement} for a breath mark, {@link BarElement} for a barline or a
 * repeat, {@link KeyChangeElement} for a key change — so that the class always states what the
 * {@link ElementType} is drawn
 * from, which {@link ElementType#newInstance()} is the general way to get right.
 *
 * <p>Abstract because every element is one of those leaves: the root states what all of them
 * share and is never an element on its own.
 *
 * <p>What every element carries besides its type: lyrics, at most one per verse, and
 * attachments, at most one of any one class. Both bounds are object invariants that hold
 * between calls. The lyric bound is kept by every write to the lyric list — {@link #putLyric}
 * and {@link #removeLyricForVerse} element by element, {@link #applyConversionFrom} and
 * {@link #copyStateFrom} by taking a whole list that already satisfies it; the attachment
 * bound is a precondition of {@link #addAttachment}, which the file readers enforce at the
 * boundary and the interactive paths keep by asking {@link #findAttachment} before adding.
 *
 * <p><b>The parent line.</b> {@link #getParentLine()} is the authoritative answer to "is this
 * element live in the document?": {@code null} means in no line, whether never added or
 * detached by a removal, and no membership scan is needed to decide. The line it answers is the
 * line whose elements hold this one, and that is a two-way tie holding at modification-bracket
 * boundaries: {@code getParentLine() == L} exactly when {@code L}'s elements contain this
 * element. Inside a bracket a re-parent may briefly have attached the element to one line while
 * another's list still holds it. What hangs on the element — its attachments, and a note's
 * articulations — is in whatever line the element is in, derived rather than stored, so
 * moving the element moves them with nothing to push down.
 */
public abstract sealed class StaffElement extends LineElement implements LineContent, Cloneable
    permits NoteElement, StructuralElement {

    /** Written only by {@code Line.attach} and {@code Line.detach}. */
    private @Nullable Line parentLine;

    /**
     * This element's lyrics, at most one per verse. {@link #putLyric} and
     * {@link #removeLyricForVerse} write it element by element; {@link #applyConversionFrom}
     * and {@link #copyStateFrom} write it by {@code addAll} from a list that already satisfies
     * the bound.
     */
    private final List<Lyric> lyrics = new ArrayList<>();

    /** Attachments on this element (tempo, fermata, dynamics, etc.), at most one per class. */
    private final List<Attachment> attachments = new ArrayList<>();

    /**
     * Written by the constructor and by {@link #copyStateFrom}, which undo replay uses to
     * restore a type change in place. A leaf answers its appearance from this field, so a type
     * restored here is drawn as the restored type with nothing else to keep current.
     */
    private ElementType type;

    protected StaffElement(ElementType type) {
        this.type = type;
    }

    /**
     * Returns an element of {@code targetType} carrying the attributes of {@code source} that
     * still apply under that type — what a type-change action leaves standing when the notator
     * turns a crotchet into a quaver, a note into a rest, or a barline into a repeat.
     *
     * <p>Uses a whitelist: an attribute added to an element in the future defaults to missing
     * (visible, safe) rather than stale (invisible, potentially corrupt).
     *
     * <p>A factory rather than a constructor because the class of the result follows
     * {@code targetType} rather than the class of {@code source} — a rest is a
     * {@link RestElement} and a key change a {@link KeyChangeElement} however the
     * element it displaces was built. {@link ElementType#newInstance()} is what settles that,
     * so the conversion and the type registry cannot disagree about which class a type gets.
     *
     * @param targetType the type the returned element is of
     * @param source the element being converted, left untouched
     * @return a detached element of {@code targetType}, in no line
     * @invariant the returned element's class is the one {@link ElementType#newInstance()} gives
     *     for {@code targetType}
     */
    public static StaffElement convertedFrom(ElementType targetType, StaffElement source) {
        var converted = targetType.newInstance();
        converted.applyConversionFrom(source);

        return converted;
    }

    /**
     * Copies onto this freshly built element the attributes of {@code source} that apply under
     * this element's own type: the base state every line element carries, through
     * {@link #copyBaseStateFrom}, so a nudge survives a duration change and a note-to-rest
     * swap; the lyrics and the attachments every staff element carries; then whatever
     * {@link #applySubtypeConversionFrom} takes. See {@link #convertedFrom}, which is the only
     * caller.
     */
    private void applyConversionFrom(StaffElement source) {
        copyBaseStateFrom(source);
        lyrics.addAll(source.lyrics);
        // The copy is born detached: only Line.attach parents a staff element, so a
        // copy reports null until a line actually takes it.

        // Deep-copy attachments
        for (var attachment : source.attachments) {
            addAttachment(attachment.copy());
        }

        applySubtypeConversionFrom(source);
    }

    /**
     * Copies onto this freshly built element the state {@code source} carries beyond
     * {@link #applyConversionFrom}'s field list, as far as this element's class has a field to
     * hold it. Does nothing here: a leaf that adds state of its own overrides it and takes what
     * the source's class can give — the field layout decides what a conversion carries, so a
     * source of another class contributes only what the two classes share, and a leaf whose
     * every answer derives from its type has nothing to take.
     *
     * <p>Unlike {@link #copySubtypeStateFrom}, {@code source} may be of any class: a
     * conversion turns a note into a rest or a barline into a repeat, so an override narrows
     * with a guarded pattern match rather than a cast.
     *
     * @param source the element being converted, of any class, left untouched
     */
    protected void applySubtypeConversionFrom(StaffElement source) {
    }

    /**
     * Copies all cloneable state from {@code source} onto this element,
     * replacing the existing state while preserving this element's identity.
     * <p>
     * This is the field list for the state a staff element holds itself; the base state travels
     * through {@link #copyBaseStateFrom} and the subtype's through the
     * {@link #copySubtypeStateFrom} this ends by calling, so each class owns exactly one list
     * and none of the three can be reached without the other two.
     * {@link #clone()} builds a bare element
     * and copies through here rather than through a copy constructor of its own, so there is
     * one field list per class and no second one to drift from it. Undo replay of an
     * {@code ElementModification} restores in place through this method — a
     * swap via {@code setElement} would leave stale references inside span
     * records still sitting on the undo/redo stacks.
     * <p>
     * Final so that every subtype's state travels by that one route: an override could copy
     * the subtype's fields and leave the base ones behind.
     *
     * @param source an element of this element's own class, whose state to take
     * @effects replaces this element's state; leaves its identity and its parent line alone
     */
    public final void copyStateFrom(StaffElement source) {
        copyBaseStateFrom(source);
        type = source.type;

        // parentLine is deliberately not copied. Line.attach/detach own it, and
        // undo replay calls this on a *live* element using a detached snapshot as
        // the source — copying the snapshot's pointer would orphan the target.

        // Deep-copy attachments
        clearAttachments();

        for (var attachment : source.attachments) {
            addAttachment(attachment.copy());
        }

        // Deep-copy lyrics (Lyric is an immutable record)
        lyrics.clear();
        lyrics.addAll(source.lyrics);

        copySubtypeStateFrom(source);
    }

    /**
     * Copies onto this element the state {@code source} carries beyond
     * {@link #copyStateFrom}'s field list. Does nothing here: a leaf that adds state of its own
     * overrides it, and a leaf whose every answer derives from its type has nothing to add.
     *
     * <p>Both copy paths — {@link #clone()} and undo replay of an {@code ElementModification} —
     * go through {@link #copyStateFrom}, so a leaf that adds a field and does not override this
     * gets an identity-preserving restore that quietly drops that field, which reaches the
     * notator as an element still holding the value the undone edit gave it.
     *
     * @param source an element of this element's own class, whose subtype state to copy
     */
    protected void copySubtypeStateFrom(StaffElement source) {
    }

    /**
     * Returns this element's type: what it is drawn as and what it is worth.
     *
     * <p>Fixed at construction. The only write after that is {@link #copyStateFrom}, which undo
     * replay uses to restore a type change onto the same element, so a caller holding the element
     * across an undo sees the restored type on the instance it already has.
     *
     * @return the type; never null
     */
    public ElementType getType() {
        return type;
    }

    /**
     * Returns the line this element is in. See the class Javadoc for the tie to that line's
     * elements.
     *
     * @return the line, or {@code null} when the element is in no line — never added, or
     *     detached by a removal
     */
    public @Nullable Line getParentLine() {
        return parentLine;
    }

    /**
     * Returns the line {@code element} is in, for a caller holding an element that may be
     * absent — the host of something hung on an element, which has none until it is added.
     *
     * @param element the element, or {@code null}
     * @return {@code element}'s line, or {@code null} when there is no element or it is in no
     *     line
     */
    static @Nullable Line parentLineOf(@Nullable StaffElement element) {
        return element != null ? element.getParentLine() : null;
    }

    /**
     * Sets the line this element is in. The only callers are {@code Line.attach} and
     * {@code Line.detach}; calling it elsewhere breaks the two-way tie in the class Javadoc.
     * Package-private so the compiler enforces that, rather than leaving it to a reader of
     * this comment.
     */
    void setParentLine(@Nullable Line parentLine) {
        this.parentLine = parentLine;
    }

    /**
     * Returns a detached copy of this element: same class, same state, in no line.
     *
     * <p>Every implementation builds a bare element of its own class and fills it in through
     * {@link #copyStateFrom}, so the state a copy carries is stated once per class rather than
     * once per copy path.
     *
     * @return the copy, in no line whatever line this element is in
     */
    @Override
    public abstract StaffElement clone();

    /**
     * Returns the distance in staff spaces from this element's origin to the right edge of the
     * glyph it draws — the notehead alone for a note, excluding stem, flag and augmentation dots.
     *
     * <p>The instance-level counterpart of {@link ElementType#getElementWidthSs()}, and the answer
     * every caller wants: a type knows one width, and for a mid-line key signature that width is
     * only a floor, since what a key signature draws depends on the key it establishes and the key
     * it cancels. Both the spacing column and the click hit rect read this, so a key signature is
     * as wide to the mouse as it is on the staff.
     *
     * @return the right edge of this element's glyph, measured from its origin, in staff spaces
     */
    public double getGlyphWidthSs() {
        return getType().getElementWidthSs();
    }

    /**
     * Returns the element's full advance width — the glyph plus whatever the type reserves
     * beside it — in staff spaces.
     *
     * @return {@link ElementType#getFullElementWidthSs()} for this element's type
     */
    public double getAdvanceWidthSs() {
        return getType().getFullElementWidthSs();
    }

    // ========================================================================
    // Attachments
    // ========================================================================

    /**
     * Returns the attachments on this element, in the order they were added.
     *
     * @return an unmodifiable view; empty when the element carries none
     */
    public List<Attachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    /**
     * Puts {@code attachment} on this element. Afterwards {@link Attachment#owner()} answers this
     * element, the attachment is in this element's line, and
     * {@link #findAttachment findAttachment(attachment.getClass())} returns it.
     *
     * @param attachment an attachment on no element, of a class this element does not already
     *     carry; the file readers convert a document that breaks the second into a rejected
     *     document, so no interior caller checks it
     * @effects appends it to {@link #getAttachments()} and makes this element its owner
     */
    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
        attachment.setOwner(this);
    }

    /**
     * Takes {@code attachment} off this element. An attachment this element does not hold is
     * left alone. Afterwards {@link Attachment#owner()} answers {@code null} for it.
     *
     * @param attachment the attachment to remove
     * @effects removes it from {@link #getAttachments()} and clears its owner
     */
    public void removeAttachment(Attachment attachment) {
        if (attachments.remove(attachment)) {
            attachment.setOwner(null);
        }
    }

    /**
     * Removes all attachments from this element.
     */
    private void clearAttachments() {
        for (var attachment : attachments) {
            attachment.setOwner(null);
        }

        attachments.clear();
    }

    /**
     * Returns the attachment of class {@code type} on this element. There is at most one of any
     * class, so this is the attachment rather than the first of several.
     *
     * @param type the attachment class to look for
     * @return the attachment, or {@code null} when this element carries none of that class
     */
    @SuppressWarnings("unchecked")
    public <T extends Attachment> @Nullable T findAttachment(Class<T> type) {
        for (var attachment : attachments) {
            if (type.isInstance(attachment)) {
                return (T) attachment;
            }
        }

        return null;
    }

    /**
     * Whether a syllable can be written on this element, given what stands in front of it.
     *
     * <p>Two things have to hold, and this is the only place they are stated together. The type
     * must be one a syllable can be sung on ({@link ElementType#bearsSyllableText()}), and this
     * element must not be the host of a paired grace note — a host's syllable belongs to the
     * grace note in front of it, so the host takes none of its own.
     *
     * <p>The predecessor is a parameter rather than something this element looks up, because the
     * two callers reach it differently and both are legitimate: {@link Line#canBearSyllableAt}
     * reads the line, while {@code LyricLayoutBuilder} reads the columns it is laying out, so
     * that a run of elements belonging to no line — a clipboard fragment, a projected insertion —
     * gets the same answer as a live line.
     *
     * @param predecessor the element immediately in front of this one, or {@code null} when this
     *     element is first in its line or run, which pairs it with no grace note
     * @return {@code true} when a syllable may be written on this element
     */
    public boolean canBearSyllable(@Nullable StaffElement predecessor) {
        return getType().bearsSyllableText()
            && !(predecessor instanceof NoteElement note && note.isPairedGraceNote());
    }

    /**
     * Returns the position of the given verse's lyric in {@link #lyrics}, or {@code null} if
     * none is set. The single place the verse lookup is written: {@link #getLyricForVerse}
     * reads the lyric off it, and {@link #putLyric} replaces the lyric at it.
     */
    private @Nullable Integer indexOfLyricForVerse(int verse) {
        for (var i = 0; i < lyrics.size(); i++) {
            if (lyrics.get(i).verse() == verse) {
                return i;
            }
        }

        return null;
    }

    /**
     * Returns this element's lyric for {@code verse}.
     *
     * @param verse the verse number, {@link Lyric#FIRST_VERSE} or above
     * @return the lyric, or {@code null} when nothing is written on this element in that verse
     */
    public @Nullable Lyric getLyricForVerse(int verse) {
        var index = indexOfLyricForVerse(verse);
        return index == null ? null : lyrics.get(index);
    }

    /**
     * Returns every lyric written on this element, across all verses.
     *
     * @return an unmodifiable view holding at most one lyric per verse, in no promised order
     */
    public List<Lyric> getLyrics() {
        return Collections.unmodifiableList(lyrics);
    }

    /**
     * Stores {@code lyric} as this element's lyric for {@link Lyric#verse()}, replacing the
     * one held for that verse if there is one. Afterwards
     * {@link #getLyricForVerse getLyricForVerse(lyric.verse())} returns {@code lyric}.
     *
     * <p>Which combinations of text, extend and syllabic are legal is {@code Lyric}'s own
     * content model, enforced by its constructor — a text-bearing lyric always bears text, so
     * an erased syllable is a {@link #removeLyricForVerse} rather than a lyric stored here with
     * blank text. This is the write every writer of a syllable shares — the interactive paths
     * through {@link LyricRun#writeLyricForVerse}, the file readers, and the chain repairs in
     * {@link LyricRun} that rewrite one verse's lyric.
     *
     * <p><b>Mutation contract:</b> production callers must invoke this from inside a
     * {@code Line.modifyElement} bracket so an {@code ElementModification} is recorded with the
     * {@code LYRIC} field. Calling it directly outside a bracket bypasses the mutation system
     * (no notification, no undo entry) and is permitted only for a reader running with mutation
     * tracking suspended, and for test setup that mirrors {@code song.withoutMutationTracking}.
     *
     * @param lyric the lyric to hold for its verse
     * @invariant {@link #getLyrics()} holds at most one lyric per verse
     */
    public void putLyric(Lyric lyric) {
        var index = indexOfLyricForVerse(lyric.verse());

        if (index == null) {
            lyrics.add(lyric);
        } else {
            lyrics.set(index, lyric);
        }
    }

    /**
     * Removes this element's lyric for {@code verse}. No effect when there is none.
     * Afterwards {@link #getLyricForVerse getLyricForVerse(verse)} returns {@code null}.
     *
     * <p><b>Mutation contract:</b> as for {@link #putLyric}.
     *
     * @param verse the verse whose lyric is removed
     */
    public void removeLyricForVerse(int verse) {
        lyrics.removeIf(lyric -> lyric.verse() == verse);
    }

    /**
     * Returns the time this element is written to occupy, in ticks: its type's duration
     * lengthened by its augmentation dots. Only a {@link Dottable} element carries dots; every
     * other element occupies its type's plain duration, which for all of them is none.
     *
     * @return the written duration in ticks, 0 for an element that occupies no time
     */
    public int writtenDuration() {
        return Dottable.dotsOf(this).augment(getType().getDefaultDuration());
    }

    /**
     * Returns the time this element sounds for in playback, in ticks: the
     * {@link #writtenDuration() written duration}, held longer when the element carries a
     * fermata. The tuplet ratio and any articulation's shortening are not applied here; they are
     * the track builder's, which reads this as the value they scale.
     *
     * @return the played duration in ticks, 0 for an element that occupies no time
     * @invariant equals {@link #writtenDuration()} when the element carries no
     *     {@link FermataAttachment}, and {@link FermataAttachment#heldDuration} of it when it does
     */
    public int playedDuration() {
        var written = writtenDuration();
        var fermata = findAttachment(FermataAttachment.class);

        return (fermata == null) ? written : fermata.heldDuration(written);
    }

    @Override
    public String toString() {
        return "StaffElement{type=" + type.name() + '}';
    }

}
