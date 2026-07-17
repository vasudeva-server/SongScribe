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

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import songscribe.error.RuntimeError;

import com.uber.nullaway.annotations.Initializer;

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

    // How much to adjust the MIDI pitch for each Accidental value
    private static final int[] MIDI_PITCH_ADJUSTMENT = new int[]{
        0, // NATURAL
        -1, // FLAT
        1, // SHARP
        0, // DOUBLE_NATURAL
        -2, // DOUBLE_FLAT
        2, // DOUBLE_SHARP
        -1, // NATURAL_FLAT
        1, // NATURAL_SHARP
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

    // The line which owns this note
    protected Line line;

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
        line = source.line;
        lyrics.addAll(source.lyrics);
        setParentLine(source.getParentLine());

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
        line = source.line;
        direction = source.direction;
        slide = source.slide;
        stemDirectionAuto = source.stemDirectionAuto;

        // Copy LineElement hierarchy data
        setParentLine(source.getParentLine());

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

    void initType(ElementType noteType) {
        type = noteType;
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
        return ScaleContext.ssToPx(getType().getElementHeightSs(getDirection()));
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
        articulation.setParentElement(this);
        articulation.setParentLine(getParentLine());
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
        attachment.setParentLine(getParentLine());
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

    public void setGlissando() {
        slide = new Glissando();
    }

    public boolean isUpper() {
        return direction.isUp();
    }

    public void setUpper(boolean upper) {
        this.direction = upper ? Direction.UP : Direction.DOWN;
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

    /** Returns the verse-1 lyric for this element, or null if none is set. */
    public @Nullable Lyric getMainLyric() {
        return getLyricForVerse(1);
    }

    /** Returns the lyric for the given verse number, or null if none is set. */
    public @Nullable Lyric getLyricForVerse(int verse) {
        for (var lyric : lyrics) {
            if (lyric.verse() == verse) {
                return lyric;
            }
        }

        return null;
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

    public int getPreviewElementPitch(Line line) {
        return calculatePitch(getPreviewElementAccidental(line));
    }

    private int calculatePitch(@Nullable Accidental accidental) {
        var adjustment = (accidental != null) ? MIDI_PITCH_ADJUSTMENT[accidental.ordinal()] : 0;

        return (
            MIDI_PITCHES[getPitchIndex()] +
                (12 * (((staffPosition <= 0) ? -staffPosition : (-staffPosition - 6)) / 7)) +
                adjustment
        );
    }

    private @Nullable Accidental getPreviewElementAccidental(Line line) {
        if (accidental == null) {
            return getAccidental(line);
        }

        return accidental;
    }

    private @Nullable Accidental getAccidental(Line line) {
        if (line.keyExists(getPitchIndex())) {
            return (line.getKeyType() == KeyType.FLATS)
                ? Accidental.FLAT
                : Accidental.SHARP;
        }

        return null;
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
    public static int getPitchAdjustment(Accidental accidental) {
        return MIDI_PITCH_ADJUSTMENT[accidental.ordinal()];
    }

    public int getDefaultDurationWithDots() {
        return (int) (getDefaultDuration() * DOTTED_DURATION[dotCount]);
    }

    public int getDuration() {
        // Fermata extends the written duration by half again
        var hasFermata = findAttachment(FermataAttachment.class) != null;
        return (int) (getDefaultDurationWithDots() * (hasFermata ? 1.5f : 1.0f));
    }

    public Line getLine() {
        return line;
    }

    @Initializer
    public void setLine(Line line) {
        this.line = line;

        for (var attachment : attachments) {
            attachment.setParentLine(line);
        }

        for (var articulation : articulations) {
            articulation.setParentLine(line);
        }
    }

    public @Nullable Accidental findLastAccidental() {
        return findEffectiveAccidental(line, line.getElementIndex(this));
    }

    /**
     * Resolves the effective accidental for this note at the given insertion index within
     * the given line: the nearest earlier accidental at the same staff position, falling
     * back to the line's key signature.
     */
    public @Nullable Accidental findEffectiveAccidental(Line targetLine, int index) {
        for (var i = index - 1; i >= 0; i--) {
            var note = targetLine.getElement(i);

            if (
                (note.getStaffPosition() == staffPosition) &&
                    (note.getAccidental() != null)
            ) {
                return note.getAccidental();
            }
        }

        return getAccidental(targetLine);
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
        NATURAL("Natural", 1, 0, -1),
        FLAT("Flat", 1, 1, -1),
        SHARP("Sharp", 1, 2, -1),
        DOUBLE_NATURAL("Double natural", 2, 0, 0),
        DOUBLE_FLAT("Double flat", 2, 1, 1),
        DOUBLE_SHARP("Double sharp", 1, 3, -1),
        NATURAL_FLAT("Natural flat", 2, 0, 1),
        NATURAL_SHARP("Natural sharp", 2, 0, 2);

        @Nullable
        private final String displayName;
        private final int widthFactor;
        private final int[] components = new int[2];

        Accidental(
            @Nullable String displayName,
            int widthFactor,
            int firstComponent,
            int secondComponent
        ) {
            this.displayName = displayName;
            this.widthFactor = widthFactor;
            components[0] = firstComponent;
            components[1] = secondComponent;
        }

        public @Nullable String getDisplayName() {
            return displayName;
        }

        public int getWidthFactor() {
            return widthFactor;
        }

        public int getComponent(int i) {
            return components[i];
        }
    }

    // Marker base for a thing attached to a note (connecting glissando or trailing fall)
    public abstract static sealed class Slide permits Glissando, Fall {
    }

    public static final class Glissando extends Slide {

        // Transient cached geometry populated during the render pass, used for hit-testing
        public transient double cachedStartX;
        public transient double cachedStartY;
        public transient double cachedAngle;
        public transient double cachedCos;
        public transient double cachedSin;
        public transient double cachedLength;
        public transient boolean hasCachedGeometry;

    }

    public static final class Fall extends Slide {

        // A fall is independently clickable, so it caches the glyph's drawn rect for hit-testing
        public transient @Nullable Rectangle2D cachedHitBounds;

    }

}
