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

package songscribe.music;

import module java.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.Attachment;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.ScaleContext;

@SuppressWarnings("StaticInitializerReferencesSubClass")
public class StaffElement extends LineElement implements Cloneable {

    public static final StaffElement GLISSANDO_PLACEHOLDER = new StructuralElement();
    public static final StaffElement PASTE_PLACEHOLDER = new StructuralElement();
    protected @Nullable Glissando glissando;

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

    public final Properties properties = new Properties();

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
    @Nullable
    protected Tempo tempoChange = null;
    @Nullable
    protected BeatChange beatChange = null;
    @Nullable
    protected Annotation annotation = null;
    protected boolean upper = false;
    protected boolean trill = false;
    protected boolean fermata = false;
    protected int syllableMovement = 0;
    protected int syllableRelationMovement = 0;
    protected boolean forceSyllable = false;
    private boolean stemDirectionAuto = true;

    // The line which owns this note
    @SuppressWarnings("NullAway") // set by Line.addElement() before the element is used
    protected Line line = null;

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
        this.type = noteType;
    }

    /**
     * Creates a note of the target type, copying only applicable attributes from the source.
     * Uses a whitelist strategy: new attributes added to Note in the future default to
     * missing (visible, safe) rather than stale (invisible, potentially corrupt).
     */
    public StaffElement(ElementType targetType, StaffElement source) {
        this.type = targetType;

        // Always copy
        this.xOffset = source.xOffset;
        this.dotCount = source.dotCount;
        this.fermata = source.fermata;
        this.tempoChange = source.tempoChange;
        this.beatChange = source.beatChange;
        this.annotation = source.annotation;
        this.syllableMovement = source.syllableMovement;
        this.syllableRelationMovement = source.syllableRelationMovement;
        this.forceSyllable = source.forceSyllable;
        this.line = source.line;

        // Copy only if target is a note (not a rest)
        if (targetType.isNote()) {
            this.accidental = source.accidental;
            this.isAccidentalInParentheses = source.isAccidentalInParentheses;
            this.glissando = source.glissando;
            this.trill = source.trill;
            this.upper = source.upper;
            this.stemDirectionAuto = source.stemDirectionAuto;
            this.staffPosition = source.staffPosition;

            // Deep-copy articulations
            for (var art : source.articulations) {
                addArticulation(new Articulation(this, art.getType()));
            }
        } else {
            // Rest: use default staff position for the target type
            this.staffPosition = targetType.getDefaultStaffPosition();
        }

        setParentLine(source.getParentLine());
    }

    protected StaffElement(StaffElement note) {
        type = note.type;
        xOffset = note.xOffset;
        staffPosition = note.staffPosition;
        dotCount = note.dotCount;
        accidental = note.accidental;
        isAccidentalInParentheses = note.isAccidentalInParentheses;
        line = note.line;
        tempoChange = note.tempoChange;
        beatChange = note.beatChange;
        upper = note.upper;
        glissando = note.glissando;
        annotation = note.annotation;
        trill = note.trill;
        fermata = note.fermata;
        syllableMovement = note.syllableMovement;
        syllableRelationMovement = note.syllableRelationMovement;
        forceSyllable = note.forceSyllable;
        stemDirectionAuto = note.stemDirectionAuto;

        // Copy LineElement hierarchy data
        setParentLine(note.getParentLine());

        // Deep-copy articulations
        for (var art : note.articulations) {
            addArticulation(new Articulation(this, art.getType()));
        }
    }

    public ElementType getType() {
        return Objects.requireNonNull(type, "type not initialized");
    }

    void initType(ElementType noteType) {
        this.type = noteType;
    }

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
    //  and the toPixels() conversion should move to the rendering boundary.

    @Override
    public double getContentWidthPx() {
        return ScaleContext.getInstance().toPixels(getType().getElementWidthSs());
    }

    public double getContentCenterX() {
        return ScaleContext.getInstance().toPixels(getType().getCenterXSs());
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getType().getElementHeightSs(upper));
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
    public boolean removeArticulation(Articulation articulation) {
        if (articulations.remove(articulation)) {
            articulation.setOwnerElement(null);
            removeChild(articulation);

            return true;
        }

        return false;
    }

    /**
     * Returns true if this note has an articulation of the given type.
     */
    public boolean hasArticulation(ArticulationType type) {
        for (var articulation : articulations) {
            if (articulation.getType() == type) {
                return true;
            }
        }

        return false;
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
        attachment.setOwnerElement(this);
        attachment.setParentLine(getParentLine());
        attachments.add(attachment);
        addChild(attachment);
    }

    /**
     * Removes an attachment from this note.
     */
    public boolean removeAttachment(Attachment attachment) {
        if (attachments.remove(attachment)) {
            attachment.setOwnerElement(null);
            removeChild(attachment);

            return true;
        }

        return false;
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
     * Returns the horizontal offset from the layout-calculated position.
     * <p>
     * Deprecated: Use {@link #getXOffset()} for clarity.
     *
     * @return The X offset value
     */
    public int getXPosSs() {
        return xOffset;
    }

    /**
     * Sets the horizontal offset from the layout-calculated position.
     * <p>
     * Deprecated: Use {@link #setXOffset(int)} for clarity.
     *
     * @param xPosSs The X offset value in staff spaces
     */
    public void setXPosSs(int xPosSs) {
        this.xOffset = xPosSs;
    }

    /**
     * Returns the horizontal offset from the layout-calculated position.
     * <p>
     * Final X position = layout.calculateBaseX(note) + xOffset
     *
     * @return The X offset (0 = no user adjustment)
     */
    public int getXOffset() {
        return xOffset;
    }

    /**
     * Sets the horizontal offset from the layout-calculated position.
     * <p>
     * Positive values move right, negative values move left.
     *
     * @param xOffset The X offset (0 = no user adjustment)
     */
    public void setXOffset(int xOffset) {
        this.xOffset = xOffset;
    }

    public int getStaffPosition() {
        return staffPosition;
    }

    public void setStaffPosition(int staffPosition) {
        this.staffPosition = staffPosition;
    }

    /**
     * Returns the number of ledger lines required for this note's staff position.
     * Consistent with {@link songscribe.ui.renderer.BaseElementRenderer#forEachLedgerLineYSs}.
     */
    public int getLedgerLineCount() {
        int a = Math.abs(staffPosition);

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

    public @Nullable Glissando getGlissando() {
        return glissando;
    }

    public void setGlissando(Glissando.Type type) {
        if (glissando == null) {
            glissando = new Glissando(type);
        } else {
            glissando.type = type;
        }
    }

    public void removeGlissando() {
        glissando = null;
    }

    public @Nullable Tempo getTempoChange() {
        return tempoChange;
    }

    public void setTempoChange(@Nullable Tempo tempoChange) {
        this.tempoChange = tempoChange;
    }

    public @Nullable BeatChange getBeatChange() {
        return beatChange;
    }

    public void setBeatChange(@Nullable BeatChange beatChange) {
        this.beatChange = beatChange;
    }

    public boolean isUpper() {
        return upper;
    }

    public void setUpper(boolean upper) {
        this.upper = upper;
    }

    public @Nullable Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(@Nullable Annotation annotation) {
        this.annotation = annotation;
    }


    public boolean isTrill() {
        return trill;
    }

    public void setTrill(boolean trill) {
        this.trill = trill;
    }

    public boolean isFermata() {
        return fermata;
    }

    public void setFermata(boolean fermata) {
        this.fermata = fermata;
    }

    public int getSyllableMovement() {
        return syllableMovement;
    }

    public void setSyllableMovement(int syllableMovement) {
        this.syllableMovement = syllableMovement;
    }

    public int getSyllableRelationMovement() {
        return syllableRelationMovement;
    }

    public void setSyllableRelationMovement(int syllableRelationMovement) {
        this.syllableRelationMovement = syllableRelationMovement;
    }

    public boolean isForceSyllable() {
        return forceSyllable;
    }

    public void setForceSyllable(boolean forceSyllable) {
        this.forceSyllable = forceSyllable;
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

    public int getInsertionElementPitch(Line line) {
        return calculatePitch(getInsertionElementAccidental(line));
    }

    private int calculatePitch(@Nullable Accidental accidental) {
        int adjustment = (accidental != null) ? MIDI_PITCH_ADJUSTMENT[accidental.ordinal()] : 0;

        return (
            MIDI_PITCHES[getPitchIndex()] +
                (12 * (((staffPosition <= 0) ? -staffPosition : (-staffPosition - 6)) / 7)) +
                adjustment
        );
    }

    private @Nullable Accidental getInsertionElementAccidental(Line line) {
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
        return (((staffPosition <= 0) ? -staffPosition : (7 - (staffPosition % 7))) % 7);
    }

    public int getDefaultDurationWithDots() {
        return (int) (getDefaultDuration() * DOTTED_DURATION[dotCount]);
    }

    public int getDuration() {
        return (int) (getDefaultDurationWithDots() * (fermata ? 1.5f : 1.0f));
    }

    public Line getLine() {
        return line;
    }

    public void setLine(Line line) {
        this.line = line;
    }

    public @Nullable Accidental findLastAccidental() {
        if (line == null) {
            return null;
        }

        for (var i = line.getElementIndex(this) - 1; i >= 0; i--) {
            var note = line.getElement(i);

            if (
                (note.getStaffPosition() == staffPosition) &&
                    (note.getAccidental() != null)
            ) {
                return line.getElement(i).getAccidental();
            }
        }

        return getAccidental(line);
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

    public enum SyllableRelation {
        NO,
        EXTENDER,
        DASH,
        ONE_DASH,
    }

    public static class Glissando {

        public enum Type {CONNECTED, SLIDE_OUT}

        public Type type;
        public double x1Translate = 0;
        public double x2Translate = 0;

        // Transient cached geometry populated during the render pass, used for hit-testing
        public transient double cachedStartX;
        public transient double cachedStartY;
        public transient double cachedAngle;
        public transient double cachedCos;
        public transient double cachedSin;
        public transient double cachedLength;
        public transient boolean hasCachedGeometry;

        public Glissando(Type type) {
            this.type = type;
        }

    }

    public static class Properties {

        // Lyrics
        @Nullable
        public String syllable = null;
        @Nullable
        public SyllableRelation syllableRelation = null;
        public float longDashPosition = 0.0F;

        public final Line2D.Double stem = new Line2D.Double();
    }
}
