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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


import org.jspecify.annotations.Nullable;

import songscribe.Version;
import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;
import songscribe.dom.Annotation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.ui.Constants;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.layout.LineEndingSupport;
import songscribe.midi.MidiSequenceBuilder;
import songscribe.dom.AttributionFormatter;

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
public final class ExportABCAction extends UIAction {

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

    public static ExportABCAction createAction(MainFrame mainFrame) {
        return new ExportABCAction(mainFrame);
    }

    private ExportABCAction(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.ACTION_EXPORT_ABC), "export-abc");
        setFlags(Flag.OPENS_DIALOG);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var scoreView = requireScoreView();
        var saveFile = PlatformFileDialog.showSaveDialog(
            getMainFrame(),
            "Export ABC",
            Strings.get(Strings.FILTER_ABC),
            scoreView.getSuggestedFileName(),
            "abc"
        );

        if (saveFile == null) {
            return;
        }

        try (var writer = new PrintWriter(saveFile)) {
            var song = getSong();
            writeABC(song, writer);
        } catch (IOException e1) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_EXPORT_ERROR,
                Strings.ERROR_FILE_SAVE
            );
        }
    }

    static int determineSongUnitLength(Song song) {
        var unitLengths = getUnitLengthMap(song);

        Map.Entry<Integer, Integer> maxValueEntry =
            new AbstractMap.SimpleEntry<>(0, Integer.MIN_VALUE);

        for (var entry : unitLengths.entrySet()) {
            if (entry.getValue() > maxValueEntry.getValue()) {
                maxValueEntry = entry;
            }
        }

        if (maxValueEntry.getValue() == Integer.MIN_VALUE) {
            return MidiSequenceBuilder.PPQ * 4;
        }

        return maxValueEntry.getKey();
    }

    private static Map<Integer, Integer> getUnitLengthMap(
        Song song
    ) {
        var unitLengths = new HashMap<Integer, Integer>();

        for (
            var lineIndex = 0;
            lineIndex < song.lineCount();
            lineIndex++
        ) {
            var line = song.getLine(lineIndex);

            for (var noteIndex = 0; noteIndex < line.effectiveElementCount(); noteIndex++) {
                var note = line.getElement(noteIndex);

                if (note.getType().isPitchedNote()) {
                    var defaultDuration = note.getDefaultDuration();
                    unitLengths.merge(defaultDuration, 1, Integer::sum);
                }
            }
        }

        return unitLengths;
    }

    public static void writeABC(Song song, PrintWriter writer) {
        var songUnitLength = determineSongUnitLength(song);
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
        writer.println("T:" + song.getTitle().replace('\n', ' '));
        writer.println("W:" + song.getUnderLyrics().replace('\n', ' '));
        writer.println("C:" + AttributionFormatter.singleLineText(song.getMetadata(), song.showTranslation()));
        writer.println("Q:" + translateTempo(song.getEffectiveTempo()));
        writer.println(
            "L:" +
                translateUnitLength(
                    songUnitLength,
                    MidiSequenceBuilder.PPQ * 4
                ).asAbcString()
        );

        // Last
        writer.println(
            "K:" +
                translateKey(
                    song.getDefaultKeyType(),
                    song.getDefaultKeyAccidentalCount()
                )
        );
        translateSong(writer, song, songUnitLength);
    }

    public static String translateKey(KeyType keyType, int number) {
        var key = (keyType == KeyType.SHARPS)
            ? SHARP_KEYS[number]
            : FLAT_KEYS[number];
        return key + " major";
    }

    public static String translateTempo(Tempo tempo) {
        // The ABC 2.1 Q: field wraps the tempo description in double quotes
        // (e.g. Q:"Allegro" 1/4=120), not single quotes.
        if (!tempo.shouldShowTempo()) {
            return '"' + tempo.getTempoDescription() + '"';
        }
        var fraction = translateUnitLength(
            tempo.getTempoType().getNote().getDuration(),
            MidiSequenceBuilder.PPQ * 4
        );
        return (
            fraction.asAbcString() +
                '=' +
                tempo.getVisibleTempo() +
                " \"" +
                tempo.getTempoDescription() +
                '"'
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

        for (var pos = staffPosition; pos >= 7; pos -= 7) {
            sb.append(',');
        }

        for (var pos = staffPosition; pos < -7; pos += 7) {
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
        int songUnitLength
    ) {
        var fraction = translateUnitLength(duration, songUnitLength);

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

        if (note.findAttachment(FermataAttachment.class) != null) {
            sb.append("!fermata!");
        }

        var noteLine = note.getLine();

        if (noteLine != null) {
            var noteIndex = noteLine.getElementIndex(note);

            if (noteIndex >= 0) {
                var hasTrill = noteLine.findRangeElements(Trill.class).stream()
                    .anyMatch(t -> {
                        var anchorIndex = t.getAnchorElementIndex();
                        var endIndex = t.getEndElementIndex();
                        return noteIndex >= anchorIndex && noteIndex <= endIndex;
                    });

                if (hasTrill) {
                    sb.append("!trill!");
                }
            }
        }

        return sb.toString();
    }

    static String translateAnnotation(@Nullable Annotation annotation) {
        if (annotation != null) {
            var marker = annotation.getPlacement() == Annotation.Placement.ABOVE ? "^" : "_";
            return '"' + marker + annotation.getAnnotation() + '"';
        }

        return "";
    }

    static String translateNote(StaffElement note, int songUnitLength) {
        var sb = new StringBuilder(27);

        var tempoAttachment = note.findAttachment(TempoChangeAttachment.class);

        if (tempoAttachment != null) {
            sb
                .append("[Q:")
                .append(translateTempo(tempoAttachment.getTempo()))
                .append(']');
        }

        var annotationAttachment = note.findAttachment(AnnotationAttachment.class);
        sb.append(translateAnnotation(
            annotationAttachment != null ? annotationAttachment.getAnnotation() : null));
        var noteType = note.getType();

        if (noteType.isNote()) {
            if (noteType.isGraceNote()) {
                sb.append("{/");
            }

            sb.append(translateDecorations(note));
            var accidental = note.getAccidental();

            if (accidental != null) {
                sb.append(translateAccidental(accidental));
            }

            sb.append(translatePitch(note.getStaffPosition()));

            var duration = (noteType == ElementType.GRACE_QUAVER)
                ? ElementType.QUAVER.getDefaultDuration()
                : note.getDefaultDurationWithDots();

            sb.append(translateNoteLength(duration, songUnitLength));

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
                        songUnitLength
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

    static String translateLine(Line line, int songUnitLength) {
        var sb = new StringBuilder(27);

        var endings = LineEndingSupport.findEndings(line);

        for (var i = 0; i < line.effectiveElementCount(); i++) {
            if (line.isStartOfAnyBeam(i)) {
                sb.append(' ');
            }

            if (LineEndingSupport.isStartOfAnyEnding(endings, i)) {
                sb.append("[1 ");
            }

            var tuplet = line.findTupletAt(i);

            if (tuplet != null && tuplet.getAnchorElementIndex() == i) {
                var numberOfNotes =
                    (tuplet.getEndElementIndex() - tuplet.getAnchorElementIndex()) + 1;
                sb
                    .append('(')
                    .append(tuplet.getGrade())
                    .append("::")
                    .append(numberOfNotes);
            }

            if (isGlissandoBegin(line, i)) {
                sb.append('(');
            }

            var element = line.getElement(i);
            sb.append(translateNote(element, songUnitLength));

            if (
                (element.getType() == ElementType.REPEAT_RIGHT) &&
                    LineEndingSupport.isInsideAnyEnding(endings, i)
            ) {
                sb.append("[2 ");
            }

            if (line.isEndOfAnyBeam(i)) {
                sb.append(' ');
            }

            if (LineEndingSupport.isEndOfAnyEnding(endings, i)) {
                sb.append("|] ");
            }

            var tieSpan = line.findTieAt(i);

            if ((tieSpan != null) && (i < tieSpan.getEndElementIndex())) {
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
        return line.getElement(n).hasGlissando();
    }

    static boolean isGlissandoEnd(Line line, int n) {
        return (n > 0) && line.getElement(n - 1).hasGlissando();
    }

    static String translateLyrics(Line line) {
        var sb = new StringBuilder(270);

        for (var noteIndex = 0; noteIndex < line.effectiveElementCount(); noteIndex++) {
            var note = line.getElement(noteIndex);
            var noteType = note.getType();

            if (!noteType.isNote()) {
                continue;
            }

            var lyric = note.getMainLyric();

            if (lyric == null
                || lyric.extend() == Lyric.Extend.STOP
                || lyric.extend() == Lyric.Extend.CONTINUE) {
                // Extender continuation under a note without its own syllable text.
                sb.append('_');
                continue;
            }

            sb.append(translateSyllable(lyric.text()));

            // TODO: syllables under gracenotes are not supported in abc therefore me must put
            //  together with the next note
            if (noteType.isGraceNote()) {
                sb.append('\\');
            }

            if (lyric.extend() == Lyric.Extend.START) {
                sb.append('_');
            } else {
                sb.append(Lyric.syllabicContinues(lyric.syllabic()) ? '-' : ' ');
            }
        }

        return sb.toString();
    }

    static String translateSyllable(@Nullable String syllable) {
        if (syllable == null) {
            return "";
        }

        if (
            Constants.UNDERSCORE.equals(syllable) ||
                Constants.HYPHEN.equals(syllable)
        ) {
            return "";
        }

        return syllable.replace(Constants.SOFT_HYPHEN, "\\-");
    }

    static void translateSong(
        PrintWriter writer,
        Song song,
        int songUnitLength
    ) {
        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            var line = song.getLine(lineIndex);
            writer.println(translateLine(line, songUnitLength));
            writer.println("w:" + translateLyrics(line));
        }
    }


    public record Fraction(int numerator, int denominator) {
        public String asAbcString() {
            return String.valueOf(numerator) + '/' + denominator;
        }

        @Override
        public String toString() {
            return asAbcString();
        }
    }
}
