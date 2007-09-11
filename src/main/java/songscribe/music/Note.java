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

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class Note implements Cloneable {

    public static final Point HOT_SPOT = new Point(5, 27);
    public static final int NORMAL_IMAGE_WIDTH = 18;

    // TODO: This will go away when we use Leland font instead of images
    public static final Rectangle[] REAL_NATURAL_FLAT_SHARP_RECT =
        new Rectangle[] {
            new Rectangle(0, 17, 6, 22),
            new Rectangle(0, 15, 7, 19),
            new Rectangle(0, 17, 8, 22),
            new Rectangle(0, 23, 9, 10),
        };

    public static final Note GLISSANDO_NOTE = new GlissandoNote();
    public static final Note PASTE_NOTE = new PasteNote();
    public static final Glissando NO_GLISSANDO = new Glissando(
        Integer.MAX_VALUE
    );
    protected Glissando glissando = NO_GLISSANDO;

    // MIDI pitches B4..A5, corresponding to the index returned by getPitchIndex()
    private static final int[] MIDI_PITCHES = new int[] {
        71,
        72,
        74,
        76,
        77,
        79,
        81,
    };

    // How much to adjust the MIDI pitch for each Accidental value
    private static final int[] MIDI_PITCH_ADJUSTMENT = new int[] {
        0, // NONE
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
    private static final float[] DOTTED_DURATION = new float[] {
        1.0f,
        1.5f,
        1.75f,
    };

    // TODO: These can probably be removed since getColoredNote and getColoredImage are not used
    private static final ArrayList<ColoredNote> coloredNotes =
        new ArrayList<>();
    private static final ArrayList<ColoredImage> coloredImages =
        new ArrayList<>();

    public final Acceleration acceleration = new Acceleration();

    // The horizontal position of the note in the line
    protected int xPos = 0;

    /**
     * The y position of the note in the sheet
     * <table>
     * <tr><th>Pitch<th>Value
     * <tr><td>D5<td>-2
     * <tr><td>C5<td>-1
     * <tr><td>B4<td>0
     * <tr><td>A4<td>1
     * <tr><td>G4<td>2
     * </table>
     */
    protected int yPos = 0;

    // How many dots the note has. Possible values are 0, 1, 2.
    protected int dotCount = 0;
    protected Accidental accidental = Accidental.NONE;
    protected boolean isAccidentalInParentheses = false;
    protected Tempo tempoChange = null;
    protected BeatChange beatChange = null;
    protected Annotation annotation = null;
    protected boolean upper = false;
    protected ForceArticulation forceArticulation = null;
    protected DurationArticulation durationArticulation = null;
    protected boolean trill = false;
    protected boolean fermata = false;
    protected int syllableMovement = 0;
    protected int syllableRelationMovement = 0;
    protected boolean forceSyllable = false;
    protected boolean invertFractionBeamOrientation = false;

    // The line which owns this note
    protected Line line = null;

    // TODO: This will go away when getActiveNotePitchString is moved to StatusBar
    private final StringBuilder pitchStringBuffer = new StringBuilder(10);

    protected Note() {}

    protected Note(@NotNull Note note) {
        xPos = note.xPos;
        yPos = note.yPos;
        dotCount = note.dotCount;
        accidental = note.accidental;
        isAccidentalInParentheses = note.isAccidentalInParentheses;
        line = note.line;
        tempoChange = note.tempoChange;
        beatChange = note.beatChange;
        upper = note.upper;
        glissando = note.glissando;
        forceArticulation = note.forceArticulation;
        durationArticulation = note.durationArticulation;
        annotation = note.annotation;
        trill = note.trill;
        fermata = note.fermata;
        syllableMovement = note.syllableMovement;
        syllableRelationMovement = note.syllableRelationMovement;
        forceSyllable = note.forceSyllable;
        invertFractionBeamOrientation = note.invertFractionBeamOrientation;
    }

    public abstract NoteType getNoteType();

    @Override
    public abstract Note clone();

    public abstract Rectangle getRealUpNoteRect();

    public abstract Rectangle getRealDownNoteRect();

    public abstract int getDefaultDuration();

    public int getXPos() {
        return xPos;
    }

    public void setXPos(int xPos) {
        this.xPos = xPos;
    }

    public int getYPos() {
        return yPos;
    }

    public void setYPos(int yPos) {
        this.yPos = yPos;
    }

    public int getDotCount() {
        return dotCount;
    }

    public void setDotCount(int dotCount) {
        this.dotCount = dotCount;
    }

    public Accidental getAccidental() {
        return accidental;
    }

    public void setAccidental(Accidental accidental) {
        this.accidental = accidental;
        isAccidentalInParentheses = isAccidentalInParentheses &&
        (getAccidental() != Accidental.NONE);
    }

    public boolean isAccidentalInParentheses() {
        return isAccidentalInParentheses;
    }

    public void setAccidentalInParentheses(boolean accidentalInParenthesis) {
        isAccidentalInParentheses = (getAccidental() != Accidental.NONE) &&
        accidentalInParenthesis;
    }

    public Glissando getGlissando() {
        return glissando;
    }

    public void setGlissando(int pitch) {
        //noinspection ObjectEquality
        if (glissando == NO_GLISSANDO) {
            glissando = new Glissando(pitch);
        } else {
            glissando.pitch = pitch;
        }
    }

    public Tempo getTempoChange() {
        return tempoChange;
    }

    public void setTempoChange(Tempo tempoChange) {
        this.tempoChange = tempoChange;
    }

    public BeatChange getBeatChange() {
        return beatChange;
    }

    public void setBeatChange(BeatChange beatChange) {
        this.beatChange = beatChange;
    }

    public boolean isUpper() {
        return upper;
    }

    public void setUpper(boolean upper) {
        this.upper = upper;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    public ForceArticulation getForceArticulation() {
        return forceArticulation;
    }

    public void setForceArticulation(
        @Nullable ForceArticulation forceArticulation
    ) {
        this.forceArticulation = forceArticulation;
    }

    public DurationArticulation getDurationArticulation() {
        return durationArticulation;
    }

    public void setDurationArticulation(
        DurationArticulation durationArticulation
    ) {
        this.durationArticulation = durationArticulation;
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

    public boolean isInvertFractionBeamOrientation() {
        return invertFractionBeamOrientation;
    }

    public void setInvertFractionBeamOrientation(
        boolean invertFractionBeamOrientation
    ) {
        this.invertFractionBeamOrientation = invertFractionBeamOrientation;
    }

    public int getPitch() {
        return calculatePitch(
            (accidental == Accidental.NONE) ? findLastAccidental() : accidental
        );
    }

    public int getEditNotePitch(Line line) {
        return calculatePitch(getEditNoteAccidental(line));
    }

    private int calculatePitch(Accidental accidental) {
        return (
            MIDI_PITCHES[getPitchIndex()] +
            (12 * (((yPos <= 0) ? -yPos : (-yPos - 6)) / 7)) +
            MIDI_PITCH_ADJUSTMENT[accidental.ordinal()]
        );
    }

    private Accidental getEditNoteAccidental(Line line) {
        if (accidental == Accidental.NONE) {
            return getAccidental(line);
        }

        return accidental;
    }

    @NotNull
    private Accidental getAccidental(Line line) {
        if (line.keyExists(getPitchIndex())) {
            return (line.getKeyType() == KeyType.FLATS)
                ? Accidental.FLAT
                : Accidental.SHARP;
        }

        return Accidental.NONE;
    }

    // TODO: Fix this to use American Standard Pitch Notation,
    //  move to StatusBar
    public String getEditNotePitchString(Line line) {
        pitchStringBuffer.delete(0, pitchStringBuffer.length());
        var activeNoteAccidental = getEditNoteAccidental(line);

        if (activeNoteAccidental == Accidental.FLAT) {
            pitchStringBuffer.append('b');
            pitchStringBuffer.append(' ');
        } else if (activeNoteAccidental == Accidental.SHARP) {
            pitchStringBuffer.append('#');
            pitchStringBuffer.append(' ');
        }

        pitchStringBuffer.append((char) (((getPitchIndex() + 1) % 7) + 'A'));

        if (yPos < 0) {
            pitchStringBuffer.append('\'');
        } else if (yPos > 6) {
            pitchStringBuffer.append(',');
        }

        return pitchStringBuffer.toString();
    }

    /*
      Returns an index from 0 to 6 corresponding to MIDI_PITCHES.
      This is a base pitch index, not taking into account accidentals
      or the octave.
    */
    public int getPitchIndex() {
        return (((yPos <= 0) ? -yPos : (7 - (yPos % 7))) % 7);
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

    public Accidental findLastAccidental() {
        for (var i = line.getNoteIndex(this) - 1; i >= 0; i--) {
            var note = line.getNote(i);

            if (
                (note.getYPos() == yPos) &&
                (note.getAccidental() != Accidental.NONE)
            ) {
                return line.getNote(i).getAccidental();
            }
        }

        return getAccidental(line);
    }

    public enum Accidental {
        NONE(null, 0, -1, -1),
        NATURAL("Natural", 1, 0, -1),
        FLAT("Flat", 1, 1, -1),
        SHARP("Sharp", 1, 2, -1),
        DOUBLE_NATURAL("Double natural", 2, 0, 0),
        DOUBLE_FLAT("Double flat", 2, 1, 1),
        DOUBLE_SHARP("Double sharp", 1, 3, -1),
        NATURAL_FLAT("Natural flat", 2, 0, 1),
        NATURAL_SHARP("Natural sharp", 2, 0, 2);

        private final String displayName;
        private final int widthFactor;
        private final int[] components = new int[2];

        Accidental(
            String displayName,
            int widthFactor,
            int firstComponent,
            int secondComponent
        ) {
            this.displayName = displayName;
            this.widthFactor = widthFactor;
            components[0] = firstComponent;
            components[1] = secondComponent;
        }

        public String getDisplayName() {
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

        public int pitch;
        public int x1Translate = 0;
        public int x2Translate = 0;

        public Glissando(int pitch) {
            this.pitch = pitch;
        }
    }

    /**
     * @param noteType key
     * @param image    value
     */
    private record ColoredNote(
        NoteType noteType,
        Color color,
        boolean upper,
        Image image
    ) {}

    private record ColoredImage(
        Image originalImage,
        Color color,
        Image coloredImage
    ) {}

    public static class Acceleration {

        // Lengthening for beaming
        public int lengthening = 0;
        // Stem
        public final Line2D.Double stem = new Line2D.Double();
        // Lyrics
        public String syllable = null;
        public SyllableRelation syllableRelation = null;
        public float longDashPosition = 0.0F;
    }
}
