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
package songscribe.ui.action;

import module java.desktop;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import songscribe.Version;
import songscribe.Strings;
import songscribe.ui.Dialogs;
import songscribe.data.MyFileFilter;
import songscribe.music.Annotation;
import songscribe.music.ArticulationType;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.ui.Constants;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.FileUtils;

/**
 * The following features are not supported in abc 2.1
 * 1. Accidentals in parentheses. I opened a forum for this topic (with no answer yet):
 * <a href="http://abcnotation.com/forums/viewtopic.php?f=7&t=260">...</a>
 * 2. Beat change. - implemented now
 * 3. Glissandos. Exported as slurs (parentheses) in ABC format.
 * 4. On syllabified lyrics no long hyphen between compound words (like God-Realisation)
 * 5. Syllables under grace notes. Solution: put together with the syllable of next note with \
 * 6. Forcing syllable under a rest (new SongScribe feature)
 */
public class ExportABCAction extends UIAction {

    private static final String[] ACCIDENTAL_MAP = new String[]{
        "=",
        "_",
        "^",
        "^^",
    };
    private static final String[] SHARP_KEYS = new String[]{
        "C",
        "G",
        "D",
        "A",
        "E",
        "B",
        "F#",
        "C#",
    };
    private static final String[] FLAT_KEYS = new String[]{
        "C",
        "F",
        "Bb",
        "Eb",
        "Ab",
        "Db",
        "Gb",
        "Cb",
    };

    private final PlatformFileDialog fileDialog;

    public ExportABCAction() {
        super(Strings.get(Strings.ACTION_EXPORT_ABC), "export-abc");
        fileDialog = new PlatformFileDialog(
            getMainFrame(),
            "Export ABC",
            false,
            new MyFileFilter(Strings.get(Strings.FILTER_ABC), "abc")
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var saveFile = FileUtils.showExportDialog(getScore(), fileDialog, "abc");

        if (saveFile == null) {
            return;
        }

        try (var writer = new PrintWriter(saveFile)) {
            var composition = getComposition();
            writeABC(composition, writer);
        } catch (IOException e1) {
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_EXPORT_ERROR),
                Strings.get(Strings.ERROR_FILE_SAVE)
            );
        }
    }

    static int determineCompositionUnitLength(Composition composition) {
        var unitLengths = getUnitLengthMap(composition);

        Map.Entry<Integer, Integer> maxValueEntry =
            new AbstractMap.SimpleEntry<>(0, Integer.MIN_VALUE);

        for (var entry : unitLengths.entrySet()) {
            if (entry.getValue() > maxValueEntry.getValue()) {
                maxValueEntry = entry;
            }
        }

        if (maxValueEntry.getValue() == Integer.MIN_VALUE) {
            return PlaybackController.PPQ * 4;
        }
        return maxValueEntry.getKey();
    }

    @NotNull
    private static Map<Integer, Integer> getUnitLengthMap(
        Composition composition
    ) {
        Map<Integer, Integer> unitLengths = new HashMap<>();

        for (
            var lineIndex = 0;
            lineIndex < composition.lineCount();
            lineIndex++
        ) {
            var line = composition.getLine(lineIndex);

            for (var noteIndex = 0; noteIndex < line.elementCount(); noteIndex++) {
                var note = line.getElement(noteIndex);

                if (note.getType().isPitchedNote()) {
                    Integer defaultDuration = note.getDefaultDuration();
                    unitLengths.merge(defaultDuration, 1, Integer::sum);
                }
            }
        }
        return unitLengths;
    }

    public static void writeABC(Composition composition, PrintWriter writer) {
        var compositionUnitLength = determineCompositionUnitLength(composition);
        writer.println("%abc-2.1");
        writer.println(
            "I:abc-creator " +
                Constants.PACKAGE_NAME +
                ' ' +
                Version.PUBLIC_VERSION
        );
        writer.println();

        // Tune header
        writer.println("X:1");
        writer.println("T:" + composition.getTitle().replace('\n', ' '));
        writer.println("W:" + composition.getUnderLyrics().replace('\n', ' '));
        writer.println("C:" + composition.getAttribution().replace('\n', ' '));
        writer.println("Q:" + translateTempo(composition.getTempo()));
        writer.println(
            "L:" +
                translateUnitLength(
                    compositionUnitLength,
                    PlaybackController.PPQ * 4
                ).asAbcString()
        );

        // Last
        writer.println(
            "K:" +
                translateKey(
                    composition.getDefaultKeyType(),
                    composition.getDefaultKeyAccidentalCount()
                )
        );
        translateComposition(writer, composition, compositionUnitLength);
    }

    public static String translateKey(KeyType keyType, int number) {
        var key = (keyType == KeyType.SHARPS)
            ? SHARP_KEYS[number]
            : FLAT_KEYS[number];
        return key + " major";
    }

    public static String translateTempo(Tempo tempo) {
        if (!tempo.shouldShowTempo()) {
            return '\'' + tempo.getTempoDescription() + '\'';
        }
        var fraction = translateUnitLength(
            tempo.getTempoType().getNote().getDuration(),
            PlaybackController.PPQ * 4
        );
        return (
            fraction.asAbcString() +
                '=' +
                tempo.getVisibleTempo() +
                " \"" +
                tempo.getTempoDescription() +
                '\''
        );
    }

    public static Fraction translateUnitLength(int duration, int unitLength) {
        var upper = duration;
        var lower = unitLength;

        for (var i = 2; i <= upper; i++) {
            while (((upper % i) == 0) && ((lower % i) == 0)) {
                upper /= i;
                lower /= i;
            }
        }

        return new Fraction(upper, lower);
    }

    public static String translatePitch(int staffPosition) {
        var sb = new StringBuilder(7);
        var pitch = ((getPitchType(staffPosition) + 1) % 7);
        var letter = (char) (pitch + ((staffPosition >= 0) ? 'A' : 'a'));
        sb.append(letter);

        for (var y = staffPosition; y >= 7; y -= 7) {
            sb.append(',');
        }

        for (var y = staffPosition; y < -7; y += 7) {
            sb.append('\'');
        }

        return sb.toString();
    }

    /**
     * @return 0 for B, 1 for C, 2 for D, ..., 6 for A
     */
    static int getPitchType(int staffPosition) {
        return (((staffPosition <= 0) ? -staffPosition : (7 - (staffPosition % 7))) % 7);
    }

    public static String translateAccidental(StaffElement.Accidental accidental) {
        // TODO: abc does not support accidental in parenthesis
        return IntStream.range(0, accidental.getWidthFactor())
            .mapToObj(i -> ACCIDENTAL_MAP[accidental.getComponent(i)])
            .collect(Collectors.joining());
    }

    public static String translateNoteLength(
        int duration,
        int compositionUnitLength
    ) {
        var fraction = translateUnitLength(duration, compositionUnitLength);

        if ((fraction.numerator() == 1) && (fraction.denominator() == 1)) {
            return "";
        }

        if (fraction.numerator() == 1) {
            return "/" + fraction.denominator();
        }

        if (fraction.denominator() == 1) {
            return Integer.toString(fraction.numerator());
        }

        return fraction.asAbcString();
    }

    static String translateRepeatAndBarLine(ElementType noteType) {
        return switch (noteType) {
            case REPEAT_LEFT -> "|:";
            case REPEAT_RIGHT -> ":|";
            case REPEAT_LEFT_RIGHT -> "::";
            case SINGLE_BARLINE -> "|";
            case DOUBLE_BARLINE -> "||";
            case FINAL_DOUBLE_BARLINE -> "|]";
            default -> "";
        };
    }

    static String translateDecorations(StaffElement note) {
        var sb = new StringBuilder(27);

        if (note.hasArticulation(ArticulationType.ACCENT)) {
            sb.append("!>!");
        }

        if (note.hasArticulation(ArticulationType.STACCATO)) {
            sb.append('.');
        }

        if (note.isFermata()) {
            sb.append("!fermata!");
        }

        if (note.isTrill()) {
            sb.append("!trill!");
        }

        return sb.toString();
    }

    static String translateAnnotation(Annotation annotation) {
        if (annotation != null) {
            var aboveDiff = Math.abs(annotation.getYPosPx() - Annotation.ABOVE);
            var belowDiff = Math.abs(annotation.getYPosPx() - Annotation.BELOW);
            return (
                '"' +
                    ((aboveDiff < belowDiff) ? "^" : "_") +
                    annotation.getAnnotation() +
                    '"'
            );
        }

        return "";
    }

    static String translateNote(StaffElement note, int compositionUnitLength) {
        var sb = new StringBuilder(27);

        if (note.getTempoChange() != null) {
            sb
                .append("[Q:")
                .append(translateTempo(note.getTempoChange()))
                .append(']');
        }

        sb.append(translateAnnotation(note.getAnnotation()));
        var noteType = note.getType();

        if (noteType.isNote()) {
            if (noteType.isGraceNote()) {
                sb.append("{/");
            }

            sb.append(translateDecorations(note));
            sb.append(translateAccidental(note.getAccidental()));
            sb.append(translatePitch(note.getStaffPosition()));
            var duration =
                switch (noteType) {
                    case GRACE_QUAVER -> ElementType.QUAVER.getDefaultDuration();
                    default -> note.getDefaultDurationWithDots();
                };

            sb.append(translateNoteLength(duration, compositionUnitLength));

            if (noteType.isGraceNote()) {
                sb.append('}');
            }
        }

        if (noteType.isRest()) {
            sb
                .append('z')
                .append(
                    translateNoteLength(
                        note.getDefaultDurationWithDots(),
                        compositionUnitLength
                    )
                );
        }

        if (noteType.isRepeat() || noteType.isBarLine()) {
            sb.append(translateRepeatAndBarLine(noteType));
        }

        if (noteType == ElementType.BREATH_MARK) {
            sb.append("!breath!");
        }

        return sb.toString();
    }

    static String translateLine(Line line, int compositionUnitLength) {
        var sb = new StringBuilder(27);

        for (var i = 0; i < line.elementCount(); i++) {
            if (line.getBeamings().isStartOfAnyInterval(i)) {
                sb.append(' ');
            }

            if (line.getFirstSecondEndings().isStartOfAnyInterval(i)) {
                sb.append("[1 ");
            }

            if (line.getTuplets().isStartOfAnyInterval(i)) {
                var interval = line.getTuplets().findInterval(i);

                if (interval != null) {
                    var numberOfNotes =
                        (interval.getEnd() - interval.getStart()) + 1;
                    sb
                        .append('(')
                        .append(interval.getGrade())
                        .append("::")
                        .append(numberOfNotes);
                }
            }

            if (isGlissandoBegin(line, i)) {
                sb.append('(');
            }

            sb.append(translateNote(line.getElement(i), compositionUnitLength));

            if (
                (line.getElement(i).getType() == ElementType.REPEAT_RIGHT) &&
                    line.getFirstSecondEndings().isInsideAnyInterval(i)
            ) {
                sb.append("[2 ");
            }

            if (line.getBeamings().isEndOfAnyInterval(i)) {
                sb.append(' ');
            }

            if (line.getFirstSecondEndings().isEndOfAnyInterval(i)) {
                sb.append("|] ");
            }

            var tieInterval = line.getTies().findInterval(i);

            if ((tieInterval != null) && (i < tieInterval.getEnd())) {
                sb.append('-');
            }

            if (isGlissandoEnd(line, i)) {
                sb.append(") ");
            }
        }

        return sb.toString().replace("  ", " ");
    }

    static boolean isGlissandoBegin(Line line, int n) {
        // Glissandos are represented as slurs in ABC format
        //noinspection ObjectEquality
        return line.getElement(n).getGlissando() != StaffElement.NO_GLISSANDO;
    }

    static boolean isGlissandoEnd(Line line, int n) {
        //noinspection ObjectEquality
        return (n > 0) && (line.getElement(n - 1).getGlissando() != StaffElement.NO_GLISSANDO);
    }

    static String translateLyrics(Line line) {
        var sb = new StringBuilder(270);

        for (var n = 0; n < line.elementCount(); n++) {
            var note = line.getElement(n);
            // TODO: syllable forcing under rests is not supported in abc

            if (note.getType().isNote()) {
                sb.append(translateSyllable(note.properties.syllable));
                // TODO: syllables under gracenotes are not supported in abc therefore me must put
                //  together with the next note
                if (note.getType().isGraceNote()) {
                    sb.append('\\');
                }

                sb.append(
                    switch (note.properties.syllableRelation) {
                        case NO -> ' ';
                        // TODO: long dash is not supported in abc
                        case ONE_DASH, DASH -> '-';
                        case EXTENDER -> '_';
                    }
                );
            }
        }

        return sb.toString();
    }

    static String translateSyllable(String syllable) {
        if (
            Constants.UNDERSCORE.equals(syllable) ||
                Constants.HYPHEN.equals(syllable)
        ) {
            return "";
        }

        return syllable.replace(Constants.NON_BREAKING_HYPHEN, "\\-");
    }

    static void translateComposition(
        PrintWriter writer,
        Composition composition,
        int compositionUnitLength
    ) {
        for (var l = 0; l < composition.lineCount(); l++) {
            var line = composition.getLine(l);
            writer.println(translateLine(line, compositionUnitLength));
            writer.println("w:" + translateLyrics(line));
        }
    }

    public record Fraction(int numerator, int denominator) {
        public String asAbcString() {
            return String.valueOf(numerator) + '/' + denominator;
        }

        @Override
        public @NotNull String toString() {
            return asAbcString();
        }
    }
}
