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
import songscribe.error.RuntimeError;
import songscribe.smufl.SMuFLGlyph;

import org.jspecify.annotations.Nullable;

public class StaffElement extends LineElement implements Cloneable {

    protected @Nullable Slide slide;

    // MIDI pitches B4..A5, corresponding to the index returned by getPitchIndex()
    private static final int[] MIDI_PITCHES = new int[]{
        71,
        72,
        74,
        76,
        77,
        79,
        81,
    };

    // Note durations corresponding to dotCount
    private static final float[] DOTTED_DURATION = new float[]{
        1.0f,
        1.5f,
        1.75f,
    };

    public final List<Lyric> lyrics = new ArrayList<>();

    /**
     * User's manual horizontal offset from the layout-calculated position.
     * <p>
     * Final X position = layout.calculateBaseX(note) + xOffset
     * <p>
     * Default is 0 (no user adjustment). Positive values move right, negative left.
     */
    protected int xOffset = 0;

    /**
     * The staff position of the note, where each step is one diatonic
     * step (half staff-space). B4 (middle line) is 0.
     * <table>
     * <tr><th>Pitch<th>Value
     * <tr><td>D5<td>-2
     * <tr><td>C5<td>-1
     * <tr><td>B4<td>0
     * <tr><td>A4<td>1
     * <tr><td>G4<td>2
     * </table>
     */
    protected int staffPosition = 0;

    // How many dots the note has. Possible values are 0, 1, 2.
    protected int dotCount = 0;
    @Nullable
    protected Accidental accidental;
    protected boolean isAccidentalInParentheses = false;
    protected Direction direction = Direction.DOWN;
    private boolean stemDirectionAuto = true;

    // ========================================================================
    // LineElement hierarchy: articulations and attachments (Phase 3)
    // ========================================================================

    /** Articulations applied to this note (staccato, accent, etc.) */
    private final List<Articulation> articulations = new ArrayList<>();

    /** Attachments on this note (tempo, fermata, dynamics, etc.) */
    private final List<Attachment> attachments = new ArrayList<>();

    @Nullable
    private ElementType type;

    protected StaffElement() {
    }

    public StaffElement(ElementType noteType) {
        type = noteType;
    }

    /**
     * Creates a note of the target type, copying only applicable attributes from the source.
     * Uses a whitelist strategy: new attributes added to Note in the future default to
     * missing (visible, safe) rather than stale (invisible, potentially corrupt).
     */
    public StaffElement(ElementType targetType, StaffElement source) {
        type = targetType;

        // Always copy
        xOffset = source.xOffset;
        dotCount = source.dotCount;
        lyrics.addAll(source.lyrics);
        // The copy is born detached: only Line.attach parents a staff element, so a
        // copy reports null until a line actually takes it.

        // Deep-copy attachments
        for (var attachment : source.attachments) {
            addAttachment(attachment.copy(this));
        }

        // Copy only if target is a note (not a rest)
        if (targetType.isNote()) {
            accidental = source.accidental;
            isAccidentalInParentheses = source.isAccidentalInParentheses;
            slide = source.slide;
            direction = source.direction;
            stemDirectionAuto = source.stemDirectionAuto;
            staffPosition = source.staffPosition;

            copyArticulationsFrom(source);
        } else {
            // Rest: use default staff position for the target type
            staffPosition = targetType.getDefaultStaffPosition();
        }
    }

    protected StaffElement(StaffElement note) {
        copyStateFrom(note);
    }

    /**
     * Copies all cloneable state from {@code source} onto this element,
     * replacing the existing state while preserving this element's identity.
     * <p>
     * This is the single authoritative field list for element state copying:
     * the copy constructor (and therefore {@link #clone()}) delegates here, so
     * the three paths cannot drift. Undo replay of an
     * {@code ElementModification} restores in place through this method — a
     * swap via {@code setElement} would leave stale references inside span
     * records still sitting on the undo/redo stacks.
     */
    public final void copyStateFrom(StaffElement source) {
        type = source.type;
        xOffset = source.xOffset;
        staffPosition = source.staffPosition;
        dotCount = source.dotCount;
        accidental = source.accidental;
        isAccidentalInParentheses = source.isAccidentalInParentheses;
        direction = source.direction;
        // Give the copy its own Slide instance, so the two elements stay independently
        // identifiable even though a slide carries no state of its own.
        slide = source.slide == null ? null : source.slide.copy();
        stemDirectionAuto = source.stemDirectionAuto;

        // parentLine is deliberately not copied. Line.attach/detach own it, and
        // undo replay calls this on a *live* element using a detached snapshot as
        // the source — copying the snapshot's pointer would orphan the target.

        // Deep-copy attachments
        clearAttachments();

        for (var attachment : source.attachments) {
            addAttachment(attachment.copy(this));
        }

        // clearArticulations + deep copy
        copyArticulationsFrom(source);

        // Deep-copy lyrics (Lyric is an immutable record)
        lyrics.clear();
        lyrics.addAll(source.lyrics);
    }

    public ElementType getType() {
        if (type == null) {
            throw RuntimeError.exit("type not initialized");
        }

        return type;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public StaffElement clone() {
        return new StaffElement(this);
    }

    public int getDefaultDuration() {
        return getType().getDefaultDuration();
    }

    // ========================================================================
    // LineElement Implementation
    // ========================================================================

    // TODO: When the layout system moves to staff spaces, these should return ss directly
    //  and the ssToPx() conversion should move to the rendering boundary.

    @Override
    public double getContentWidthPx() {
        return ScaleContext.ssToPx(getType().getFullElementWidthSs());
    }

    public double getContentCenterX() {
        return ScaleContext.ssToPx(getType().getFullElementCenterXSs());
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.ssToPx(getType().getElementHeightSs(direction));
    }

    // ========================================================================
    // Articulations (Phase 3 LineElement hierarchy)
    // ========================================================================

    /**
     * Returns an unmodifiable view of the articulations on this note.
     */
    public List<Articulation> getArticulations() {
        return Collections.unmodifiableList(articulations);
    }

    /**
     * Adds an articulation to this element.
     */
    public void addArticulation(Articulation articulation) {
        articulation.setOwnerElement(this);
        articulations.add(articulation);
        addChild(articulation);
    }

    /**
     * Removes an articulation from this element.
     */
    public void removeArticulation(Articulation articulation) {
        if (articulations.remove(articulation)) {
            articulation.setOwnerElement(null);
            removeChild(articulation);
        }
    }

    /**
     * Returns the first articulation of the given type, or {@code null} if this note has none.
     */
    public @Nullable Articulation findArticulation(ArticulationType type) {
        for (var articulation : articulations) {
            if (articulation.getType() == type) {
                return articulation;
            }
        }

        return null;
    }

    /**
     * Returns true if this note has an articulation of the given type.
     */
    public boolean hasArticulation(ArticulationType type) {
        return findArticulation(type) != null;
    }

    /**
     * Returns the MIDI duration percentage from the first articulation that overrides duration,
     * or -1 if no articulation overrides duration.
     */
    public int findMidiDurationOverride() {
        for (var articulation : articulations) {
            if (articulation.getType().hasMidiDurationOverride()) {
                return articulation.getType().getMidiDurationPercent();
            }
        }

        return -1;
    }

    /**
     * Removes all articulations from this element.
     */
    public void clearArticulations() {
        for (var articulation : articulations) {
            articulation.setOwnerElement(null);
            removeChild(articulation);
        }

        articulations.clear();
    }

    /**
     * Replaces this element's articulations with deep copies of the source element's
     * articulations, reparented to this element.
     */
    public void copyArticulationsFrom(StaffElement source) {
        clearArticulations();

        for (var articulation : source.getArticulations()) {
            addArticulation(new Articulation(this, articulation.getType()));
        }
    }

    // ========================================================================
    // Attachments (Phase 3 LineElement hierarchy)
    // ========================================================================

    /**
     * Returns an unmodifiable view of the attachments on this note.
     */
    public List<Attachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    /**
     * Adds an attachment to this note.
     */
    public void addAttachment(Attachment attachment) {
        attachment.setOwnerElement(this);
        attachments.add(attachment);
        addChild(attachment);
    }

    /**
     * Removes an attachment from this note.
     */
    public void removeAttachment(Attachment attachment) {
        if (attachments.remove(attachment)) {
            attachment.setOwnerElement(null);
            removeChild(attachment);
        }
    }

    /**
     * Removes all attachments from this note.
     */
    public void clearAttachments() {
        for (var attachment : attachments) {
            attachment.setOwnerElement(null);
            removeChild(attachment);
        }

        attachments.clear();
    }

    /**
     * Finds the first attachment of the specified type.
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
     * Adds or removes this element's fermata to match the requested state, leaving it
     * unchanged if it already matches. Idempotent — never creates a duplicate fermata.
     */
    public void setFermata(boolean fermata) {
        var existing = findAttachment(FermataAttachment.class);

        if (fermata) {
            if (existing == null) {
                addAttachment(new FermataAttachment(this));
            }
        } else if (existing != null) {
            removeAttachment(existing);
        }
    }

    /**
     * Returns the horizontal offset from the layout-calculated position.
     * <p>
     * Final X position = layout.calculateBaseX(note) + xOffset
     *
     * @return The X offset value in pixels (0 = no user adjustment)
     */
    public int getXOffsetPx() {
        return xOffset;
    }

    /**
     * Sets the horizontal offset from the layout-calculated position.
     * <p>
     * Positive values move right, negative values move left.
     *
     * @param xPosPx The X offset value in pixels (0 = no user adjustment)
     */
    public void setXOffsetPx(int xPosPx) {
        xOffset = xPosPx;
    }

    public int getStaffPosition() {
        return staffPosition;
    }

    public void setStaffPosition(int staffPosition) {
        this.staffPosition = staffPosition;
    }

    /**
     * Returns whether the given staff position falls on a staff line, as opposed to a
     * space between lines. Staff positions alternate line/space with lines at even values.
     */
    public static boolean isLinePosition(int staffPosition) {
        return staffPosition % 2 == 0;
    }

    /**
     * Returns the number of ledger lines required for this note's staff position.
     * Consistent with {@link songscribe.ui.renderer.RenderingUtils#forEachLedgerLineYSs}.
     */
    public int getLedgerLineCount() {
        var a = Math.abs(staffPosition);

        if (a % 2 != 0) {
            a--;
        }

        return Math.max(0, (a - 4) / 2);
    }

    /**
     * Returns whether this note requires ledger lines (staff position beyond the staff).
     */
    public boolean hasLedgerLines() {
        return getLedgerLineCount() > 0;
    }

    public int getDotCount() {
        return dotCount;
    }

    public void setDotCount(int dotCount) {
        this.dotCount = dotCount;
    }

    public @Nullable Accidental getAccidental() {
        return accidental;
    }

    public void setAccidental(@Nullable Accidental accidental) {
        this.accidental = accidental;
        isAccidentalInParentheses = isAccidentalInParentheses &&
            (getAccidental() != null);
    }

    public boolean isAccidentalInParentheses() {
        return isAccidentalInParentheses;
    }

    public void setAccidentalInParentheses(boolean accidentalInParenthesis) {
        isAccidentalInParentheses = (getAccidental() != null) &&
            accidentalInParenthesis;
    }

    public @Nullable Slide getSlide() {
        return slide;
    }

    public boolean hasFall() {
        return slide instanceof Fall;
    }

    public void setFall() {
        slide = new Fall();
    }

    public void removeSlide() {
        slide = null;
    }

    public @Nullable Glissando getGlissando() {
        return slide instanceof Glissando glissando ? glissando : null;
    }

    public boolean hasGlissando() {
        return slide instanceof Glissando;
    }

    /**
     * Whether this element is a grace note paired with the note that follows it — a grace
     * note carrying a connecting {@link Glissando}. The pairing is what the element itself
     * can say about it, and it says so whether or not a following note is actually there;
     * the containing run adds only a bounds check on the grace note's own index (see
     * {@link LyricRun#isPairedGraceNote}). A caller that needs the host to work with checks
     * for one itself, as {@link LyricRun#syncGraceHostMelisma} does.
     */
    public boolean isPairedGraceNote() {
        return getType().isGraceNote() && hasGlissando();
    }

    public void setGlissando() {
        slide = new Glissando();
    }

    public boolean isUpper() {
        return direction.isUp();
    }

    public void setUpper(boolean upper) {
        direction = upper ? Direction.UP : Direction.DOWN;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    /** Returns the default stem direction for a note: up for grace notes or staff positions above the middle line, down otherwise. */
    public static Direction defaultDirection(StaffElement note) {
        return (note.getStaffPosition() > 0) || note.getType().isGraceNote()
            ? Direction.UP
            : Direction.DOWN;
    }

    /**
     * Returns the position of the given verse's lyric in {@link #lyrics}, or -1 if none is
     * set. The single place the verse lookup is written: {@link #getLyricForVerse} reads the
     * lyric off it, and the chain repairs in {@link LyricRun} — which have to rewrite a
     * lyric in place, and so need its position rather than its value — go through it too.
     */
    public int indexOfLyricForVerse(int verse) {
        for (var i = 0; i < lyrics.size(); i++) {
            if (lyrics.get(i).verse() == verse) {
                return i;
            }
        }

        return -1;
    }

    /** Returns the lyric for the given verse number, or null if none is set. */
    public @Nullable Lyric getLyricForVerse(int verse) {
        var index = indexOfLyricForVerse(verse);
        return index < 0 ? null : lyrics.get(index);
    }

    /**
     * An element is eligible to carry a lyric in {@code verse} if it is a non-rest, or a
     * rest that already carries a non-blank lyric in that verse.
     */
    public boolean isEligibleForLyric(int verse) {
        if (!getType().isRest()) {
            return true;
        }

        var lyric = getLyricForVerse(verse);
        return lyric != null && !lyric.text().isBlank();
    }

    /** Returns an unmodifiable view of all lyrics attached to this element. */
    public List<Lyric> getLyrics() {
        return Collections.unmodifiableList(lyrics);
    }

    /**
     * Sets or removes the verse-{@code verse} lyric on this element, enforcing the MusicXML 4.0
     * {@code <lyric>} content-model contract. Illegal combinations throw
     * {@link IllegalArgumentException} rather than silently normalizing.
     *
     * <p><b>Truth table</b> (MusicXML 4.0 {@code note.mod} §lyric):
     * <table>
     *   <tr><th>text</th><th>extend</th><th>action</th></tr>
     *   <tr><td>non-blank</td><td>{@link Lyric.Extend#NONE}</td>
     *       <td>syllable entry, no melisma</td></tr>
     *   <tr><td>non-blank</td><td>{@link Lyric.Extend#START}</td>
     *       <td>syllable entry + melisma start</td></tr>
     *   <tr><td>non-blank</td><td>{@link Lyric.Extend#CONTINUE}/{@link Lyric.Extend#STOP}</td>
     *       <td><b>throws</b> — carriers cannot have text</td></tr>
     *   <tr><td>blank/null</td><td>{@link Lyric.Extend#START}</td>
     *       <td><b>throws</b> — START requires text</td></tr>
     *   <tr><td>blank/null</td><td>{@link Lyric.Extend#CONTINUE}/{@link Lyric.Extend#STOP}</td>
     *       <td>extender-carrier entry; {@code syllabic} forced to {@code null}</td></tr>
     *   <tr><td>blank/null</td><td>{@link Lyric.Extend#NONE}</td>
     *       <td>removes the verse entry</td></tr>
     * </table>
     *
     * <p><b>Mutation contract:</b> production callers must invoke this from inside a
     * {@code Line.modifyElement} bracket so an {@code ElementModification} is recorded with the
     * {@code LYRIC} field. Calling it directly outside a bracket bypasses the mutation system
     * (no notification, no undo entry) and is permitted only for test setup that mirrors
     * {@code song.withoutMutationTracking}.
     *
     * @param verse    the verse number (typically 1)
     * @param syllabic the syllabic position of this lyric within its word; ignored (forced to
     *                 {@code null}) for carrier lyrics ({@link Lyric.Extend#STOP} /
     *                 {@link Lyric.Extend#CONTINUE})
     * @param compound {@code true} when this syllable joins the next via a compound-word boundary;
     *                 ignored (forced to {@code false}) for carrier lyrics
     * @param text     the syllable text; {@code null} or blank removes the entry for
     *                 {@link Lyric.Extend#NONE}, and creates a carrier for CONTINUE/STOP
     * @param extend   the melisma extender state
     * @throws IllegalArgumentException if {@code text} is non-blank and {@code extend} is
     *                                  CONTINUE or STOP, or if {@code text} is blank and
     *                                  {@code extend} is START
     */
    public void setLyricForVerse(int verse, Lyric.@Nullable Syllabic syllabic, boolean compound,
            @Nullable String text, Lyric.Extend extend) {
        var isBlankText = text == null || text.isBlank();
        var isCarrier = extend == Lyric.Extend.CONTINUE || extend == Lyric.Extend.STOP;

        if (!isBlankText && isCarrier) {
            throw new IllegalArgumentException(
                "carrier lyric (extend=" + extend + ") cannot have text; got: \"" + text + '"');
        }

        if (isBlankText && extend == Lyric.Extend.START) {
            throw new IllegalArgumentException(
                "melisma START requires non-blank text");
        }

        lyrics.removeIf(lyric -> lyric.verse() == verse);

        if (text != null && !text.isBlank()) {
            lyrics.add(new Lyric(verse, text, extend, syllabic, compound));
        } else if (isCarrier) {
            lyrics.add(new Lyric(verse, "", extend, null, false));
        }
        // blank + NONE: entry removed above, nothing to add
    }

    public boolean isStemDirectionAuto() {
        return stemDirectionAuto;
    }

    public void setStemDirectionAuto(boolean stemDirectionAuto) {
        this.stemDirectionAuto = stemDirectionAuto;
    }

    public int getPitch() {
        return calculatePitch(
            (accidental == null) ? findLastAccidental() : accidental
        );
    }

    private int calculatePitch(@Nullable Accidental accidental) {
        var adjustment = getPitchAdjustment(accidental);

        return (
            MIDI_PITCHES[getPitchIndex()] +
                (12 * (((staffPosition <= 0) ? -staffPosition : (-staffPosition - 6)) / 7)) +
                adjustment
        );
    }

    /**
     * Returns the accidental {@code key} puts in effect for the pitch class of
     * {@code staffPosition}.
     *
     * <p>Static and key-and-staff-position-taking so that the projected-layout resolver in
     * {@code AccidentalReconciliation}, which walks positions rather than live notes, shares this
     * one definition of the key fallback instead of restating it. The two must agree: the resolver
     * decides what the line <em>will</em> read as, and {@link #findEffectiveAccidental} decides
     * what it reads as now. It takes a {@link Key} rather than a {@link Line} for the same reason
     * — a line no longer has one key, so resolving which key applies is the caller's job and
     * happens before this call.
     *
     * @param key           the key in effect at the position being resolved
     * @param staffPosition the staff position whose pitch class is being asked about
     * @return {@link Accidental#FLAT} when {@code key} is a flat key that alters that pitch class,
     *         {@link Accidental#SHARP} when it is a sharp key that does, and null when the key
     *         leaves that pitch unaltered
     */
    public static @Nullable Accidental keyAccidentalFor(Key key, int staffPosition) {
        if (!key.altersPitchClass(getPitchIndex(staffPosition))) {
            return null;
        }

        return (key.keyType() == KeyType.FLATS)
            ? Accidental.FLAT
            : Accidental.SHARP;
    }

    /*
      Returns an index from 0 to 6 corresponding to MIDI_PITCHES.
      This is a base pitch index, not taking into account accidentals
      or the octave.
    */
    public int getPitchIndex() {
        return getPitchIndex(staffPosition);
    }

    /*
      Static form of {@link #getPitchIndex()} so callers that work with a raw
      staff position (e.g. the MusicXML PitchSpelling helper) share the single
      B4 = staffPosition 0 origin owned by MIDI_PITCHES, rather than re-deriving
      the lattice.
    */
    public static int getPitchIndex(int staffPosition) {
        return (((staffPosition <= 0) ? -staffPosition : (7 - (staffPosition % 7))) % 7);
    }

    // The sounding alteration, in semitones, that this Accidental applies to the
    // natural pitch. This is the same source the playback pitch (getPitch) uses,
    // so a MusicXML <alter> derived from it sounds identical to playback.
    //
    // A null accidental means no accidental at all, which sounds unaltered and so yields 0.
    // Every caller that compares accidentals by how they sound rather than by which enum
    // constant they are goes through here, so that rule is stated once.
    public static int getPitchAdjustment(@Nullable Accidental accidental) {
        return (accidental == null) ? 0 : accidental.semitones();
    }

    public int getDefaultDurationWithDots() {
        return (int) (getDefaultDuration() * DOTTED_DURATION[dotCount]);
    }

    public int getDuration() {
        // Fermata extends the written duration by half again
        var hasFermata = findAttachment(FermataAttachment.class) != null;
        return (int) (getDefaultDurationWithDots() * (hasFermata ? 1.5f : 1.0f));
    }

    public @Nullable Accidental findLastAccidental() {
        var line = getParentLine();

        // An element in no line has no key context, so no accidental is in effect.
        // This is what getPitch() and MusicXML pitch spelling see for a detached
        // element — a deliberate change from scanning whatever line it last sat in.
        if (line == null) {
            return null;
        }

        return findEffectiveAccidental(line, line.getElementIndex(this));
    }

    /**
     * Resolves the effective accidental for this note at the given insertion index within
     * the given line. This note's <em>own</em> explicit accidental is not consulted here —
     * callers ({@link #getPitch()}, {@code StatusBar.setNoteContent}) check it first and only
     * call this when it is null.
     *
     * <p>The backward scan has three outcomes:
     *
     * <ol>
     *   <li>It reaches a <em>barrier</em> — an element whose
     *       {@link ElementType#cancelsAccidentals()} is true — and stops there, falling back to
     *       the key signature. That method is shared with the projected-layout resolver in
     *       {@code AccidentalReconciliation}, so both agree on what cancels. When the barrier is
     *       itself a {@link KeySignatureElement}, its key <em>is</em> the key in effect at
     *       {@code index}: a key signature is a barrier, so no later one can sit between the two
     *       or the scan would have stopped at that one first. Reading it off the barrier is what
     *       lets this path — which runs per note, per layout pass and per {@link #getPitch()} —
     *       avoid walking the same elements a second time through {@link Line#keyAt}. A barline
     *       barrier carries no key, so that case still asks the line.</li>
     *   <li>It finds an earlier element at the same staff position carrying an explicit
     *       accidental, and returns it. Matching is by staff position, so the scan is
     *       octave-specific, as staff notation requires.</li>
     *   <li>It runs out of preceding elements and falls back to the key signature.</li>
     * </ol>
     *
     * <p>A barrier is <em>escaped</em> when this note ends a tie whose anchor sits before the
     * barrier: convention carries an accidental through a tie that crosses a barline. The
     * resolution then resumes at the anchor, and repeats, so a chain {@code A~B~C} carries an
     * accidental across two intervening barriers.
     */
    public @Nullable Accidental findEffectiveAccidental(Line targetLine, int index) {
        // The note whose incoming tie we may escape a barrier through. It advances to the
        // anchor on each escape, so a chain of ties is followed one link at a time.
        var tieEndElement = this;
        var cursor = index;

        barrierEscape:
        while (true) {
            for (
                var scanIndex = precedingIndex(targetLine, cursor);
                scanIndex >= 0;
                scanIndex = precedingIndex(targetLine, scanIndex)
            ) {
                var element = targetLine.getElement(scanIndex);
                var elementType = element.getType();

                if (elementType.cancelsAccidentals()) {
                    var anchor = tieAnchorBefore(targetLine, tieEndElement, scanIndex);

                    if (anchor == null) {
                        if (element instanceof KeySignatureElement keySignature) {
                            return keyAccidentalFor(keySignature.getKey(), staffPosition);
                        }

                        return keyInEffectAt(targetLine, index);
                    }

                    // Resume at the anchor itself: it may be the note carrying the accidental.
                    // anchorIndex < scanIndex < cursor, so cursor strictly decreases on every
                    // escape and the loop is bounded by the element count.
                    tieEndElement = anchor;
                    cursor = targetLine.getElementIndex(anchor) + 1;
                    continue barrierEscape;
                }

                if (
                    (element.getStaffPosition() == staffPosition) &&
                        (element.getAccidental() != null)
                ) {
                    return element.getAccidental();
                }
            }

            return keyInEffectAt(targetLine, index);
        }
    }

    /**
     * Returns the position the backward accidental scan visits before {@code index}, or −1 when
     * the scan is exhausted.
     *
     * <p>This is the single seam controlling how far back {@link #findEffectiveAccidental} looks.
     * Today it yields this line's positions from {@code index - 1} down to 0, because accidentals
     * reset at the end of a line by house convention — the repertoire is largely meterless, and
     * metered music here closes a row with a barline, so the row boundary and the measure
     * boundary coincide. If that convention is ever revisited so the scan continues into the
     * previous line, this method is the only thing that changes: the barrier test, the tie
     * escape, the staff-position match and the key fallback all stay as they are.
     */
    private int precedingIndex(Line targetLine, int index) {
        return index - 1;
    }

    /**
     * Returns the anchor of a tie that ends at {@code tieEndElement} and starts before
     * {@code scanIndex}, or null when there is none — that is, the note the accidental
     * resolution resumes at when a tie carries it across a barrier.
     *
     * <p>The end element is matched by reference identity: {@link StaffElement} overrides neither
     * {@code equals} nor {@code hashCode}, so a detached clone would otherwise match nothing
     * meaningful.
     *
     * <p>Filters {@link Line#getSpans} in place rather than calling
     * {@link Line#findTies}, which builds and copies a fresh list of every tie on the line. This
     * runs on paths that repeat: {@link #getPitch} routes here, and the glissando renderer calls
     * it on every repaint while the preview tracker calls it on every mouse move.
     */
    private @Nullable StaffElement tieAnchorBefore(Line targetLine, StaffElement tieEndElement, int scanIndex) {
        for (var span : targetLine.getSpans()) {
            if (!(span instanceof Tie tie) || (tie.getEndElement() != tieEndElement)) {
                continue;
            }

            var anchor = tie.getAnchorElement();

            if (anchor == null) {
                continue;
            }

            var anchorIndex = targetLine.getElementIndex(anchor);

            if ((anchorIndex >= 0) && (anchorIndex < scanIndex)) {
                return anchor;
            }
        }

        return null;
    }

    /**
     * Returns the accidental the key signature puts in effect for this note's pitch class at
     * {@code index} within {@code targetLine}.
     *
     * <p>A line may carry several keys — its own, and one per mid-line key signature — so which
     * one applies depends on {@code index}. {@link Line#keyAt} answers that; this method turns
     * the answer into an accidental for this note's pitch class.
     *
     * @param targetLine the line being resolved within
     * @param index      the index within {@code targetLine} at which the note sits
     * @return the accidental the key in effect there puts on this note's pitch class, or null when
     *         that key leaves it unaltered
     */
    private @Nullable Accidental keyInEffectAt(Line targetLine, int index) {
        return keyAccidentalFor(targetLine.keyAt(index), staffPosition);
    }

    @Override
    public String toString() {
        return "StaffElement{type=" + (type != null ? type.name() : "null") + ", staffPosition=" + staffPosition + '}';
    }

    /**
     * The vertical direction a stem, beam, or similar element points.
     */
    public enum Direction {
        UP,
        DOWN;

        /**
         * Returns true if this direction is {@link #UP}.
         */
        public boolean isUp() {
            return this == UP;
        }

        /**
         * Returns true if this direction is {@link #DOWN}.
         */
        public boolean isDown() {
            return this == DOWN;
        }

        /**
         * Returns the opposite direction: {@link #UP} for {@link #DOWN} and vice versa.
         */
        public Direction opposite() {
            return this == UP ? DOWN : UP;
        }

        /**
         * Returns this direction as a numeric sign: +1 for {@link #UP}, -1 for {@link #DOWN}.
         */
        public int sign() {
            return this == UP ? 1 : -1;
        }
    }

    public enum Accidental {
        NATURAL(0, SMuFLGlyph.ACCIDENTAL_NATURAL),
        FLAT(-1, SMuFLGlyph.ACCIDENTAL_FLAT),
        SHARP(1, SMuFLGlyph.ACCIDENTAL_SHARP),
        DOUBLE_FLAT(-2, SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT),
        DOUBLE_SHARP(2, SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);

        private final int semitones;
        private final SMuFLGlyph glyph;

        Accidental(int semitones, SMuFLGlyph glyph) {
            this.semitones = semitones;
            this.glyph = glyph;
        }

        public int semitones() {
            return semitones;
        }

        /**
         * The single SMuFL glyph this accidental draws. Grace notes use this same regular
         * glyph; the grace size comes from drawing it with a scaled-down font
         * ({@link ElementType#GRACE_NOTE_SCALE}), mirroring how grace noteheads are rendered.
         * There is no separate small-glyph table: Bravura's "small" accidentals are a distinct
         * private-use set with their own (non-proportional) metrics, and have no
         * double-flat/double-sharp variant, so they cannot scale uniformly with the regular
         * glyphs.
         */
        public SMuFLGlyph glyph() {
            return glyph;
        }
    }

    // Marker base for a thing attached to a note (connecting glissando or trailing fall)
    public sealed interface Slide permits Glissando, Fall {

        /**
         * Returns a fresh instance of this slide's concrete subtype. A slide carries no state
         * beyond its type — its geometry lives on the line's layout result, keyed by the owning
         * note — so the copy is simply a new instance.
         */
        Slide copy();
    }

    public static final class Glissando implements Slide {

        @Override
        public Slide copy() {
            return new Glissando();
        }

    }

    public static final class Fall implements Slide {

        @Override
        public Slide copy() {
            return new Fall();
        }

    }

}
