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
package songscribe.ui.renderer;

import java.awt.*;
import java.awt.geom.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.EnumMap;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import songscribe.data.GeneralPathFile;
import songscribe.music.GraceSemiQuaver;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.component.Score;
import songscribe.util.Utils;

public class FughettaRenderer extends Renderer {

    private static final EnumMap<NoteType, String> noteHead = new EnumMap<>(
        NoteType.class
    );

    static {
        noteHead.put(NoteType.SEMIBREVE, "\uf077");
        noteHead.put(NoteType.MINIM, "\uf0cd");
        noteHead.put(NoteType.CROTCHET, "\uf0cf");
        noteHead.put(NoteType.QUAVER, "\uf0cf");
        noteHead.put(NoteType.SEMIQUAVER, "\uf0cf");
        noteHead.put(NoteType.DEMI_SEMIQUAVER, "\uf0cf");
        noteHead.put(NoteType.SEMIBREVE_REST, "\uf0ee");
        noteHead.put(NoteType.MINIM_REST, "\uf0ee");
        noteHead.put(NoteType.CROTCHET_REST, "\uf0ce");
        noteHead.put(NoteType.QUAVER_REST, "\uf0e4");
        noteHead.put(NoteType.SEMIQUAVER_REST, "\uf0c5");
        noteHead.put(NoteType.DEMI_SEMIQUAVER_REST, "\uf0a8");
    }

    private static final double upperCrotchetStemX =
        NOTE_FONT_SIZE / 3.6056337d;
    private static final double upperMinimStemX = NOTE_FONT_SIZE / 3.1411042f;
    private static final double tempoStemShortening = 2;
    private static final Line2D.Float upperStem = new Line2D.Float(
        0f,
        -NOTE_FONT_SIZE / 32f,
        0f,
        -NOTE_FONT_SIZE / 1.1429f
    );
    private static final Line2D.Float lowerStem = new Line2D.Float(
        0f,
        NOTE_FONT_SIZE / 60f,
        0f,
        NOTE_FONT_SIZE / 1.1429f
    );
    private static final Line2D.Float graceNoteUpperSlash = new Line2D.Float(
        NOTE_FONT_SIZE / 18.285715f,
        -NOTE_FONT_SIZE / 5.5652175f,
        NOTE_FONT_SIZE / 2.3703704f,
        -NOTE_FONT_SIZE / 2.6666667f
    );
    private static final Line2D.Float graceNoteLowerSlash = new Line2D.Float(
        -NOTE_FONT_SIZE / 11.906977f,
        NOTE_FONT_SIZE / 5.5652175f,
        NOTE_FONT_SIZE / 3.5310345f,
        NOTE_FONT_SIZE / 2.6666667f
    );
    private static final BasicStroke graceNoteSlashStroke = new BasicStroke(
        0.64f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );
    private static final double upperFlagX = NOTE_FONT_SIZE / 3.6834533d;
    private static final double upperFlagY = -NOTE_FONT_SIZE / 1.6623377d;
    private static final double upperFlag2Y = -NOTE_FONT_SIZE / 1.1851852d;
    private static final double upperFlag3Y = -NOTE_FONT_SIZE / 0.9411765d;
    private static final double lowerFlagY = NOTE_FONT_SIZE / 1.6d;
    private static final double lowerFlag2Y = NOTE_FONT_SIZE / 1.1428572d;
    private static final double lowerFlag3Y = NOTE_FONT_SIZE / 0.9078014d;
    private static final double flagYLength = 7;
    private static final double graceNoteScale = 0.6;
    private static final float SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE =
        5f;
    private static final double LOWER_STEM_CROTCHET_Y1_OFFSET = 0.6f;

    private static final String trebleClef = "\uf026";
    private static final String[] accidentals = new String[] {
        "",
        "\uf06e", // natural
        "\uf062", // flat
        "\uf023", // sharp
        "\uf06e\uf06e", // double-natural
        "\uf0ba", // double-flat
        "\uf0dc", // double-sharp
        "\uf06e\uf062", // natural-flat
        "\uf06e\uf023", // natural-sharp
    };
    private static final String[] accidentalParenthesis = new String[] {
        "",
        "\uf04e",
        "\uf041",
        "\uf061",
        "\uf06e\uf06e",
        "\uf08c",
        "\uf081",
        "\uf06e\uf062",
        "\uf06e\uf023",
    };
    private static final double manualParenthesisY =
        NOTE_FONT_SIZE / 3.5068493d;
    private static final String beginParenthesis = "\uf028";
    private static final String endParenthesis = "\uf029";

    private static final String mainUpperFlag = "\uf06a";
    private static final String secondUpperFlag = "\uf0fb";
    private static final String mainLowerFlag = "\uf04a";
    private static final String secondLowerFlag = "\uf0f0";

    private static final double repeatLeftThickX = 4.167d / 2d;
    private static final double repeatRightThickX =
        Note.NORMAL_IMAGE_WIDTH - repeatLeftThickX;
    private static final double repeatLeftRightThickX =
        Note.NORMAL_IMAGE_WIDTH / 2d;

    private static final double dotWidth = NOTE_FONT_SIZE / 9.142858d;
    private static final Ellipse2D.Double[] noteDots = new Ellipse2D.Double[] {
        new Ellipse2D.Double(13.1d, -dotWidth / 2, dotWidth, dotWidth),
        new Ellipse2D.Double(
            15.878d + dotWidth,
            -dotWidth / 2,
            dotWidth,
            dotWidth
        ),
    };
    private static float[] baseAccidentalWidths = null;
    private static float[] baseAccidentalParenthesisWidths = null;
    private static float beginParenthesisWidth = 0.0F, endParenthesisWidth =
        0.0F;
    private final GeneralPath breathMark;
    private final GeneralPath fermata;

    public FughettaRenderer(Score score)
        throws IOException, FileNotFoundException, FileNotFoundException {
        super(score);
        crotchetWidth = upperCrotchetStemX;
        beamX1Correction = 0.3d;
        beamX2Correction = 0.3d;

        // TODO: Replace with new String(Character.toChars(0x1D112)) from Bravura
        breathMark = GeneralPathFile.readGeneralPath(
            new File(Utils.getResourcePath("fonts/breathmark"))
        );

        // TODO: Replace with new String(Character.toChars(0x1D110)) from Bravura
        fermata = GeneralPathFile.readGeneralPath(
            new File(Utils.getResourcePath("fonts/fermata"))
        );
    }

    // TODO: All of this can go away, all possible accidentals are in Bravura
    public static void calculateAccidentalWidths(Graphics2D g2) {
        if (baseAccidentalWidths != null) {
            return;
        }

        var metrics = g2.getFontMetrics(fughetta);
        var accidentals1 = Note.Accidental.values();
        baseAccidentalWidths = new float[accidentals.length];

        for (var i = 0; i < baseAccidentalWidths.length; i++) {
            baseAccidentalWidths[i] = (accidentals[i].length() == 1)
                ? metrics.stringWidth(accidentals[i])
                : 0f;
        }

        for (var i = 0; i < baseAccidentalWidths.length; i++) {
            if (
                (baseAccidentalWidths[i] == 0f) &&
                (accidentals[i].length() == 2)
            ) {
                baseAccidentalWidths[i] =
                    baseAccidentalWidths[accidentals1[i].getComponent(0) + 1];
                baseAccidentalWidths[i] += SPACE_BETWEEN_TWO_ACCIDENTALS;
                baseAccidentalWidths[i] +=
                baseAccidentalWidths[accidentals1[i].getComponent(1) + 1];
            }
        }

        baseAccidentalParenthesisWidths =
            new float[accidentalParenthesis.length];
        beginParenthesisWidth = metrics.stringWidth(beginParenthesis);
        endParenthesisWidth = metrics.stringWidth(endParenthesis);

        for (var i = 0; i < baseAccidentalParenthesisWidths.length; i++) {
            baseAccidentalParenthesisWidths[i] = !accidentals[i].equals(
                        accidentalParenthesis[i]
                    )
                ? metrics.stringWidth(accidentalParenthesis[i])
                : (baseAccidentalWidths[i] +
                    beginParenthesisWidth +
                    endParenthesisWidth);
        }
    }

    public static float getAccidentalWidth(@NotNull Note note) {
        return note.isAccidentalInParentheses()
            ? baseAccidentalParenthesisWidths[note.getAccidental().ordinal()]
            : baseAccidentalWidths[note.getAccidental().ordinal()];
    }

    public static float getAccidentalComponentWidth(Note note, int component) {
        if (baseAccidentalWidths == null) {
            getAccidentalWidth(note);
        }

        return baseAccidentalWidths[note
                .getAccidental()
                .getComponent(component) +
            1];
    }

    @Override
    public void paintNote(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int lineIndex,
        boolean beamed
    ) {
        var transform = g2.getTransform();
        var color = g2.getColor();

        g2.translate(
            note.getXPos(),
            score.getNoteYPos(note.getYPos(), lineIndex)
        );
        var translatedTransform = g2.getTransform();
        g2.setFont(fughetta);
        drawNote(g2, note, lineIndex, beamed, translatedTransform, transform);
        drawStave(g2, note);
        drawAccidental(g2, note);
        g2.setTransform(transform);
        drawGlissando(g2, note, lineIndex);
        drawArticulation(g2, note, lineIndex);
        drawFermata(g2, note, lineIndex, transform);

        g2.setColor(color);
        g2.setTransform(transform);
    }

    private void drawFermata(
        Graphics2D g2,
        @NotNull Note note,
        int line,
        AffineTransform origTransform
    ) {
        if (note.isFermata()) {
            g2.translate(
                note.getXPos() - 5,
                score.getNoteYPos(getFermataYPos(note), line) + 12
            );
            g2.scale(0.0625, 0.0625);
            g2.scale(0.9, 0.8);
            g2.fill(fermata);
            g2.setTransform(origTransform);
        }
    }

    private void drawGlissando(
        Graphics2D g2,
        @NotNull Note note,
        int lineIndex
    ) {
        //noinspection ObjectEquality
        if (note.getGlissando() != Note.NO_GLISSANDO) {
            drawGlissando(
                g2,
                score.getComposition().getLine(lineIndex).getNoteIndex(note),
                note.getGlissando(),
                lineIndex
            );
        }
    }

    private void drawAccidental(Graphics2D g2, @NotNull Note note) {
        var accidental = note.getAccidental().ordinal();

        if (accidental > 0) {
            var resizeFactor = 1f;

            if (note.getNoteType().isGraceNote()) {
                g2.setFont(fughettaGrace);
                resizeFactor = GRACE_ACCIDENTAL_RESIZE_FACTOR;
            }

            if (
                !note.isAccidentalInParentheses() ||
                !accidentals[accidental].equals(
                        accidentalParenthesis[accidental]
                    )
            ) {
                drawSimpleAccidental(
                    g2,
                    note,
                    -ACCIDENTAL_PADDING - getAccidentalWidth(note),
                    resizeFactor
                );
            } else {
                var xPos = -ACCIDENTAL_PADDING - getAccidentalWidth(note);
                drawString(
                    g2,
                    beginParenthesis,
                    xPos * resizeFactor,
                    (float) manualParenthesisY * resizeFactor
                );
                xPos += beginParenthesisWidth;

                if (note.getAccidental().getComponent(1) == 1) {
                    xPos += 0.5f;
                }

                drawSimpleAccidental(g2, note, xPos, resizeFactor);
                drawString(
                    g2,
                    endParenthesis,
                    (-ACCIDENTAL_PADDING - endParenthesisWidth) * resizeFactor,
                    (float) manualParenthesisY * resizeFactor
                );
            }

            if (note.getNoteType().isGraceNote()) {
                g2.setFont(fughetta);
            }
        }
    }

    private static void drawStave(@NotNull Graphics2D g2, @NotNull Note note) {
        g2.setStroke(lineStroke);

        if (
            (Math.abs(note.getYPos()) > 5) &&
            note.getNoteType().drawStaveLongitude()
        ) {
            var i = note.getYPos();

            if ((note.getYPos() % 2) != 0) {
                i += (note.getYPos() > 0) ? -1 : 1;
            }

            for (
                var step = (note.getYPos() > 0) ? -2 : 2;
                Math.abs(i) > 5;
                i += step
            ) {
                var y1 = ((i - note.getYPos()) * (int) NOTE_FONT_SIZE) / 8;
                var x2 = Note.HOT_SPOT.x + 8f;

                if (note.getNoteType() == NoteType.SEMIBREVE) {
                    x2 += 3.4f;
                } else if (note.getNoteType() == NoteType.MINIM) {
                    x2 += 0.7f;
                }

                g2.draw(new Line2D.Float(Note.HOT_SPOT.x - 8, y1, x2, y1));
            }
        }
    }

    private void drawNote(
        Graphics2D g2,
        @NotNull Note note,
        int line,
        boolean beamed,
        AffineTransform translatedTransform,
        AffineTransform origTransform
    ) {
        var type = note.getNoteType();

        if (noteHead.containsKey(type)) {
            paintSimpleNote(g2, note, beamed, note.isUpper(), false);

            // If the note is beamed, draw a lengthened stem.
            if (beamed) {
                g2.setStroke(stemStroke);

                // Decrease the length a little so the stem does not extend past an angled beam.
                var offset = beamStroke.getLineWidth() * 0.1;

                if (note.isUpper()) {
                    note.acceleration.stem.setLine(
                        upperCrotchetStemX,
                        upperStem.y1,
                        upperCrotchetStemX,
                        (upperStem.y2 - note.acceleration.lengthening) + offset
                    );
                } else {
                    note.acceleration.stem.setLine(
                        lowerStem.x1,
                        lowerStem.y1 + LOWER_STEM_CROTCHET_Y1_OFFSET,
                        lowerStem.x2,
                        lowerStem.y2 - note.acceleration.lengthening - offset
                    );
                }

                g2.draw(note.acceleration.stem);
            }
        } else {
            switch (type) {
                case GRACE_QUAVER -> {
                    g2.scale(graceNoteScale, graceNoteScale);
                    drawString(g2, noteHead.get(NoteType.QUAVER), 0, 0);
                    g2.setStroke(stemStroke);

                    if (note.isUpper()) {
                        g2.translate(upperCrotchetStemX, 0);
                        g2.draw(upperStem);
                        g2.translate(-upperCrotchetStemX, 0);
                        drawString(
                            g2,
                            mainUpperFlag,
                            (float) upperFlagX,
                            (float) upperFlagY
                        );
                        g2.setTransform(translatedTransform);
                        g2.setStroke(graceNoteSlashStroke);
                        g2.draw(graceNoteUpperSlash);
                    } else {
                        g2.draw(lowerStem);
                        drawString(g2, mainLowerFlag, 0f, (float) lowerFlagY);
                        g2.setTransform(translatedTransform);
                        g2.setStroke(graceNoteSlashStroke);
                        g2.draw(graceNoteLowerSlash);
                    }
                }
                case GRACE_SEMIQUAVER -> {
                    g2.scale(graceNoteScale, graceNoteScale);
                    var graceSemiQuaver = (GraceSemiQuaver) note;
                    drawString(
                        g2,
                        noteHead.get(NoteType.QUAVER),
                        0,
                        (float) (((graceSemiQuaver.getY0Pos() -
                                    graceSemiQuaver.getYPos()) *
                                Score.NOTE_Y_OFFSET) /
                            graceNoteScale)
                    );
                    drawString(
                        g2,
                        noteHead.get(NoteType.QUAVER),
                        (float) (graceSemiQuaver.getX2DiffPos() /
                            graceNoteScale),
                        0
                    );
                    g2.setTransform(origTransform);
                    g2.translate(note.isUpper() ? -2.7 : -1.7, 0);
                    drawGraceSemiQuaverBeam(g2, note, line);
                }
                case REPEAT_LEFT -> drawRepeat(g2, repeatLeftThickX, 1d, true);
                case REPEAT_RIGHT -> drawRepeat(
                    g2,
                    repeatRightThickX,
                    -1f,
                    true
                );
                case REPEAT_LEFT_RIGHT -> {
                    drawRepeat(g2, repeatLeftRightThickX, 1f, true);
                    drawRepeat(g2, repeatLeftRightThickX, -1f, false);
                }
                case FINAL_DOUBLE_BARLINE,
                    DOUBLE_BARLINE,
                    SINGLE_BARLINE -> drawBarLine(g2, type);
                case BREATH_MARK -> {
                    g2.scale(0.0625, 0.0625);
                    g2.fill(breathMark);
                }
            }

            g2.setTransform(translatedTransform);
        }
    }

    private void drawSimpleAccidental(
        Graphics2D g2,
        @NotNull Note note,
        float startX,
        float resizeFactor
    ) {
        var accidental = note.getAccidental().ordinal();
        var x = startX * resizeFactor;
        var str = note.isAccidentalInParentheses()
            ? accidentalParenthesis[accidental]
            : accidentals[accidental];

        if (str.length() == 1) {
            drawString(g2, str, x, 0f);
        } else {
            drawString(g2, str.substring(0, 1), x, 0f);
            drawString(
                g2,
                str.substring(1),
                x +
                ((getAccidentalComponentWidth(note, 0) +
                        SPACE_BETWEEN_TWO_ACCIDENTALS) *
                    resizeFactor),
                0f
            );
        }
    }

    private void paintSimpleNote(
        Graphics2D g2,
        @NotNull Note note,
        boolean beamed,
        boolean upper,
        boolean isTempoNote
    ) {
        var nt = note.getNoteType();

        // draw the note head
        var headStr = noteHead.get(nt);
        var noteHeadXPos = 0f;

        if (nt.isNoteWithStem() && !upper) {
            noteHeadXPos -= stemStroke.getLineWidth() / 2;
        }

        drawString(g2, headStr, noteHeadXPos, 0f);

        // Draw the stem
        if (!beamed) {
            drawNoteStem(g2, note, upper, isTempoNote, nt);
            drawNoteFlags(g2, upper, isTempoNote, nt);
        }

        var at = drawNoteDots(g2, note, beamed, upper);
        g2.setTransform(at);
    }

    private static AffineTransform drawNoteDots(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean beamed,
        boolean upper
    ) {
        var at = g2.getTransform();

        if ((note.getYPos() % 2) == 0) {
            g2.translate(0, -NOTE_FONT_SIZE / 8);
        }

        if (note.getNoteType() == NoteType.SEMIBREVE) {
            g2.translate(3.5, 0);
        }

        if (note.getNoteType() == NoteType.MINIM) {
            g2.translate(1.4, 0);
        }

        if (note.getNoteType().isBeamable() && !beamed && upper) {
            g2.translate((note.getNoteType() == NoteType.QUAVER) ? 5 : 8, 0);
        }

        for (var i = 0; i < note.getDotCount(); i++) {
            g2.fill(noteDots[i]);
        }
        return at;
    }

    private void drawNoteFlags(
        Graphics2D g2,
        boolean upper,
        boolean isTempoNote,
        @NotNull NoteType type
    ) {
        if (type.isBeamable()) {
            var offset = (type == NoteType.QUAVER)
                ? 0f
                : SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE;

            if (upper) {
                if (isTempoNote) {
                    g2.translate(0, tempoStemShortening);
                }

                drawString(
                    g2,
                    mainUpperFlag,
                    (float) upperFlagX,
                    (float) upperFlagY + offset
                );

                if (type != NoteType.QUAVER) {
                    drawString(
                        g2,
                        secondUpperFlag,
                        (float) upperFlagX,
                        (float) upperFlag2Y + offset
                    );

                    if (type != NoteType.SEMIQUAVER) {
                        drawString(
                            g2,
                            secondUpperFlag,
                            (float) upperFlagX,
                            (float) upperFlag3Y + offset
                        );
                    }
                }

                if (isTempoNote) {
                    g2.translate(0, -tempoStemShortening);
                }
            } else {
                if (isTempoNote) {
                    g2.translate(0, -tempoStemShortening);
                }

                drawString(g2, mainLowerFlag, 0, (float) lowerFlagY - offset);

                if (type != NoteType.QUAVER) {
                    drawString(
                        g2,
                        secondLowerFlag,
                        0,
                        (float) lowerFlag2Y - offset
                    );

                    if (type != NoteType.SEMIQUAVER) {
                        drawString(
                            g2,
                            secondLowerFlag,
                            0,
                            (float) lowerFlag3Y - offset
                        );
                    }
                }

                if (isTempoNote) {
                    g2.translate(0, tempoStemShortening);
                }
            }
        }
    }

    private static void drawNoteStem(
        Graphics2D g2,
        Note note,
        boolean upper,
        boolean isTempoNote,
        @NotNull NoteType type
    ) {
        if (type.isNoteWithStem()) {
            var stemLengthOffset = 0d;
            var stemYOffset = 0d;

            if (isTempoNote) {
                stemLengthOffset = -tempoStemShortening;
            } else {
                if (type == NoteType.SEMIQUAVER) {
                    stemLengthOffset = flagYLength -
                    SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE;
                } else if (type == NoteType.DEMI_SEMIQUAVER) {
                    stemLengthOffset = (2 * flagYLength) -
                    SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE;
                } else if (type == NoteType.CROTCHET) {
                    stemYOffset = LOWER_STEM_CROTCHET_Y1_OFFSET;
                } else if (type == NoteType.MINIM) {
                    stemYOffset = 0.1f;
                }
            }

            if (upper) {
                var stemX = (type == NoteType.MINIM)
                    ? upperMinimStemX
                    : upperCrotchetStemX;
                note.acceleration.stem.setLine(
                    stemX,
                    upperStem.getY1(),
                    stemX,
                    upperStem.getY2() - stemLengthOffset
                );
            } else {
                note.acceleration.stem.setLine(
                    0d,
                    lowerStem.getY1() + stemYOffset,
                    0d,
                    lowerStem.getY2() + stemLengthOffset
                );
            }

            g2.setStroke(stemStroke);
            g2.draw(note.acceleration.stem);
        }
    }

    @Override
    protected int drawLineBeginning(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        g2.setFont(fughetta);

        // draw the treble clef
        var baseline =
            score.getMiddleLineY() +
            Score.STAFF_LINE_Y_OFFSET +
            (lineIndex * score.getRowHeight());
        drawString(g2, trebleClef, 5, baseline);
        var metrics = g2.getFontMetrics();
        var maxY = baseline + metrics.getMaxDescent();

        // draw the leading sharps or flats
        if (line.getKeyAccidentalCount() > 0) {
            var fsPos = score.getLeadingKeysPos();
            var fs = line.getKeyType().ordinal();

            for (var i = 0; i < line.getKeyAccidentalCount(); i++) {
                drawString(
                    g2,
                    accidentals[fs + 1],
                    fsPos,
                    score.getNoteYPos(
                        KEY_SIGNATURE_Y_POSITIONS[fs][i % 7],
                        lineIndex
                    )
                );
                fsPos += 9;
            }
        }

        return maxY;
    }

    @Override
    protected void drawKeySignatureChange(
        @NotNull Graphics2D g2,
        int l,
        KeyType[] keyTypes,
        @NotNull int[] keys,
        int[] froms,
        boolean[] isNatural
    ) {
        g2.setFont(fughetta);
        var fsPos = score.getComposition().getLineWidth() - 5;

        for (var key : keys) {
            fsPos -= key * 8;
        }

        for (var kt = 0; kt < keyTypes.length; kt++) {
            if (keyTypes[kt] == null) {
                break;
            }

            var fs = keyTypes[kt].ordinal();

            for (var i = 0; i < keys[kt]; i++) {
                drawString(
                    g2,
                    accidentals[(isNatural[kt] ? 0 : fs) + 1],
                    fsPos,
                    score.getNoteYPos(
                        KEY_SIGNATURE_Y_POSITIONS[fs][(i + froms[kt]) % 7],
                        l
                    )
                );

                fsPos += 8;
            }
        }
    }

    @Override
    protected void drawTempoChangeNote(
        Graphics2D g2,
        Note tempoNote,
        int x,
        int y
    ) {
        g2.setFont(fughetta);
        var at = g2.getTransform();
        g2.translate(x, y - ((NOTE_FONT_SIZE * TEMPO_CHANGE_ZOOM_Y) / 8.0));
        g2.scale(TEMPO_CHANGE_ZOOM_X, TEMPO_CHANGE_ZOOM_Y);
        paintSimpleNote(g2, tempoNote, false, true, true);
        g2.setTransform(at);
    }

    @Override
    protected void drawEndings(Graphics2D g2, int lineIndex, Line line) {
        for (
            var li = line.getFirstSecondEndings().listIterator();
            li.hasNext();
        ) {
            var interval = li.next();

            var start = interval.getStart();
            var end = interval.getEnd();

            var repeatRightPos = IntStream.rangeClosed(start, end)
                .filter(
                    i -> line.getNote(i).getNoteType() == NoteType.REPEAT_RIGHT
                )
                .findFirst()
                .orElse(-1);

            var repeatX = 0d;
            var startNote = line.getNote(start);

            if (start > 0) {
                var previousNote = line.getNote(start - 1);

                if (previousNote.getNoteType() == NoteType.SINGLE_BARLINE) {
                    --start;
                    startNote = previousNote;
                }
            }

            var endNote = line.getNote(end);

            if ((start < repeatRightPos) || (repeatRightPos == -1)) {
                double x2;

                if (repeatRightPos != -1) {
                    // The right edge of the bracket should align with the thin line of the repeat
                    repeatX = line.getNote(repeatRightPos).getXPos() +
                    repeatRightThickX;
                    x2 = repeatX - repeatThickThinDiff;
                } else {
                    // This should never happen, but does now because inserting notes doesn't
                    // recalc endings.
                    // In that case go halfway to the left edge of the next note.
                    double nextX = line.getNote(end + 1).getXPos();
                    x2 = endNote.getXPos();
                    x2 += (nextX - x2) / 2d;
                }

                double x1 = startNote.getXPos();

                // If the first ending starts on a bar line, align with the line.
                // Otherwise, go halfway to the right edge of the previous note.
                if (startNote.getNoteType() == NoteType.SINGLE_BARLINE) {
                    x1 += barLine.getX1();
                } else if (start > 0) {
                    var previousNote = line.getNote(start - 1);
                    double previousX =
                        previousNote.getXPos() +
                        previousNote.getRealUpNoteRect().width;
                    x1 -= (x1 - previousX) / 2d;
                }

                drawEnding(g2, line, lineIndex, x1, x2, 1);
            }

            if ((repeatRightPos != -1) && (end > repeatRightPos)) {
                double x2 = endNote.getXPos();
                var type = endNote.getNoteType();

                // If the end note is not a bar line and the next note is, extend to that
                if (
                    (type != NoteType.SINGLE_BARLINE) &&
                    (type != NoteType.DOUBLE_BARLINE) &&
                    ((end + 1) < line.noteCount())
                ) {
                    var nextNote = line.getNote(end + 1);
                    var nextType = nextNote.getNoteType();

                    if (
                        (nextType == NoteType.SINGLE_BARLINE) ||
                        (nextType == NoteType.DOUBLE_BARLINE)
                    ) {
                        ++end;
                        type = nextType;
                        x2 = nextNote.getXPos();
                    }
                }

                // The right edge of the second ending bracket should align with the left line
                // of a double bar line.
                if (
                    (type == NoteType.SINGLE_BARLINE) ||
                    (type == NoteType.DOUBLE_BARLINE)
                ) {
                    x2 += barLine.getX1();

                    if (type == NoteType.DOUBLE_BARLINE) {
                        x2 -= barLineSpace;
                    }
                } else {
                    // Otherwise go halfway to the next note if there is one,
                    // or a note width beyond the right edge of the note if not.

                    // Move to right edge of end note
                    var nextNote = line.getNote(end + 1);
                    x2 += nextNote.getRealUpNoteRect().width;

                    if (end < line.noteCount()) {
                        x2 += (nextNote.getXPos() - x2) / 2d;
                    } else {
                        x2 += nextNote.getRealUpNoteRect().width;
                    }
                }

                drawEnding(
                    g2,
                    line,
                    lineIndex,
                    repeatX + repeatThickThinDiff,
                    x2,
                    2
                );
            }
        }
    }
}
