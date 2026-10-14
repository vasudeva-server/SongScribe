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

import songscribe.error.RuntimeError;

/**
 * An element occupying a column of a staff line: a note, a rest, a barline, a breath mark, a
 * mid-line key change. Abstract, and instantiated only through one of its leaves;
 * {@link ElementType#newInstance()} is the general way to get the right one.
 *
 * <p>Lyrics hold at most one per script and attachments at most one of any one class, both
 * invariants between calls. The attachment bound is a precondition of {@link #addAttachment}.
 *
 * <p>{@link #getParentLine()} is the authoritative answer to "is this element live in the
 * document?", and a two-way tie holding at modification-bracket boundaries:
 * {@code getParentLine() == L} exactly when {@code L}'s elements contain this element. Inside a
 * bracket a re-parent may briefly break it.
 */
public abstract sealed class StaffElement extends LineElement implements LineContent, Cloneable
    permits NoteElement, StructuralElement {

    private @Nullable Line parentLine;

    private final List<Lyric> lyrics = new ArrayList<>();

    private final List<Attachment> attachments = new ArrayList<>();

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
     * Unlike {@link #copySubtypeStateFrom}, {@code source} may be of any class.
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
     * A leaf that adds a field must override this, or clone and undo replay quietly drop it and the
     * notator sees the value the undone edit gave it.
     *
     * @param source an element of this element's own class
     */
    protected void copySubtypeStateFrom(StaffElement source) {
    }

    public ElementType getType() {
        return type;
    }

    /**
     * @return {@code null} when the element is in no line
     */
    public @Nullable Line getParentLine() {
        return parentLine;
    }

    /**
     * For a caller that knows the element is in a line; otherwise ask {@link #getParentLine()}.
     */
    public Line requireParentLine() {
        var line = parentLine;

        if (line == null) {
            throw RuntimeError.exit("an element expected in a line is in no line: " + this);
        }

        return line;
    }

    static @Nullable Line parentLineOf(@Nullable StaffElement element) {
        return element != null ? element.getParentLine() : null;
    }

    /**
     * Only {@code Line.attach} and {@code Line.detach} call it, to keep the class's two-way tie.
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
     * The notehead alone for a note, excluding stem, flag and augmentation dots. Prefer it to
     * {@link ElementType#getElementWidthSs()}, which for a mid-line key signature is only a
     * floor: the spacing column and the click hit rect read this, so a key signature is as wide
     * to the mouse as on the staff.
     */
    public double getGlyphWidthSs() {
        return getType().getElementWidthSs();
    }

    public double getAdvanceWidthSs() {
        return getType().getFullElementWidthSs();
    }

    // ========================================================================
    // Attachments
    // ========================================================================

    public List<Attachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    /**
     * @param attachment an attachment on no element, of a class this element does not already
     *     carry; the file readers reject a document that breaks the second, so no interior
     *     caller checks it
     */
    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
        attachment.setOwner(this);
    }

    public void removeAttachment(Attachment attachment) {
        if (attachments.remove(attachment)) {
            attachment.setOwner(null);
        }
    }

    private void clearAttachments() {
        for (var attachment : attachments) {
            attachment.setOwner(null);
        }

        attachments.clear();
    }

    /**
     * @return {@code null} when this element carries none of that class
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
     * The only place the two conditions are stated together: the type bears syllable text
     * ({@link ElementType#bearsSyllableText()}), and this element is not the host of a paired
     * grace note, whose syllable belongs to the grace note.
     *
     * @param predecessor {@code null} when this element is first in its line or run, which pairs
     *     it with no grace note
     */
    public boolean canBearSyllable(@Nullable StaffElement predecessor) {
        return getType().bearsSyllableText()
            && !(predecessor instanceof NoteElement note && note.isPairedGraceNote());
    }

    private @Nullable Integer indexOfLyricForScript(Song.Script script) {
        for (var i = 0; i < lyrics.size(); i++) {
            if (lyrics.get(i).script() == script) {
                return i;
            }
        }

        return null;
    }

    /**
     * @return {@code null} when nothing is written on this element in that script
     */
    public @Nullable Lyric getLyricForScript(Song.Script script) {
        var index = indexOfLyricForScript(script);
        return index == null ? null : lyrics.get(index);
    }

    /**
     * Returns every lyric written on this element, across all scripts.
     *
     * @return an unmodifiable view holding at most one lyric per script, in no promised order
     */
    public List<Lyric> getLyrics() {
        return Collections.unmodifiableList(lyrics);
    }

    /**
     * Stores {@code lyric} as this element's lyric for {@link Lyric#script()}, replacing the
     * one held for that script if there is one. Afterwards
     * {@link #getLyricForScript getLyricForScript(lyric.script())} returns {@code lyric}.
     *
     * <p>Which combinations of text, extend and syllabic are legal is {@code Lyric}'s own
     * content model, enforced by its constructor — a text-bearing lyric always bears text, so
     * an erased syllable is a {@link #removeLyricForScript} rather than a lyric stored here with
     * blank text. This is the write every writer of a syllable shares — the interactive paths
     * through {@link LyricRun#writeLyricForScript}, the file readers, and the chain repairs in
     * {@link LyricRun} that rewrite one script's lyric.
     *
     * <p><b>Mutation contract:</b> production callers must invoke this from inside a
     * {@code Line.modifyElement} bracket so an {@code ElementModification} is recorded with the
     * {@code LYRIC} field. Calling it directly outside a bracket bypasses the mutation system
     * (no notification, no undo entry) and is permitted only for a reader running with mutation
     * tracking suspended, and for test setup that mirrors {@code song.withoutMutationTracking}.
     *
     * @param lyric the lyric to hold for its script
     * @invariant {@link #getLyrics()} holds at most one lyric per script
     */
    public void putLyric(Lyric lyric) {
        var index = indexOfLyricForScript(lyric.script());

        if (index == null) {
            lyrics.add(lyric);
        } else {
            lyrics.set(index, lyric);
        }
    }

    /**
     * Removes this element's lyric for {@code script}. No effect when there is none.
     * Afterwards {@link #getLyricForScript getLyricForScript(script)} returns {@code null}.
     *
     * <p><b>Mutation contract:</b> as for {@link #putLyric}.
     *
     * @param script the script whose lyric is removed
     */
    public void removeLyricForScript(Song.Script script) {
        lyrics.removeIf(lyric -> lyric.script() == script);
    }

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
