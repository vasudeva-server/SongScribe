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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.music.Crotchet;
import songscribe.music.Demisemiquaver;
import songscribe.music.KeyType;
import songscribe.music.Minim;
import songscribe.music.Note;
import songscribe.music.Quaver;
import songscribe.music.Semibreve;
import songscribe.music.Semiquaver;
import songscribe.music.Tempo;
import songscribe.ui.component.Score;

public class ExportABCActionTest {

    @SuppressWarnings("FieldCanBeLocal")
    private ExportABCAction action = null;

    @BeforeEach
    public void init() {
        action = new ExportABCAction();
    }

    @Test
    public void testTranslateKey() {
        assertEquals(
            "C# major",
            ExportABCAction.translateKey(KeyType.SHARPS, 7)
        );
        assertEquals(
            "F# major",
            ExportABCAction.translateKey(KeyType.SHARPS, 6)
        );
        assertEquals(
            "B major",
            ExportABCAction.translateKey(KeyType.SHARPS, 5)
        );
        assertEquals(
            "E major",
            ExportABCAction.translateKey(KeyType.SHARPS, 4)
        );
        assertEquals(
            "A major",
            ExportABCAction.translateKey(KeyType.SHARPS, 3)
        );
        assertEquals(
            "D major",
            ExportABCAction.translateKey(KeyType.SHARPS, 2)
        );
        assertEquals(
            "G major",
            ExportABCAction.translateKey(KeyType.SHARPS, 1)
        );
        assertEquals(
            "C major",
            ExportABCAction.translateKey(KeyType.SHARPS, 0)
        );
        assertEquals("C major", ExportABCAction.translateKey(KeyType.FLATS, 0));
        assertEquals("F major", ExportABCAction.translateKey(KeyType.FLATS, 1));
        assertEquals(
            "Bb major",
            ExportABCAction.translateKey(KeyType.FLATS, 2)
        );
        assertEquals(
            "Eb major",
            ExportABCAction.translateKey(KeyType.FLATS, 3)
        );
        assertEquals(
            "Ab major",
            ExportABCAction.translateKey(KeyType.FLATS, 4)
        );
        assertEquals(
            "Db major",
            ExportABCAction.translateKey(KeyType.FLATS, 5)
        );
        assertEquals(
            "Gb major",
            ExportABCAction.translateKey(KeyType.FLATS, 6)
        );
        assertEquals(
            "Cb major",
            ExportABCAction.translateKey(KeyType.FLATS, 7)
        );
    }

    @Test
    public void testTranslateTempo() {
        assertEquals(
            "\"Moderato\"",
            ExportABCAction.translateTempo(
                new Tempo(120, Tempo.Type.CROTCHET, "Moderato", false)
            )
        );
        assertEquals(
            "1/4=120 \"Moderato\"",
            ExportABCAction.translateTempo(
                new Tempo(120, Tempo.Type.CROTCHET, "Moderato", true)
            )
        );
        assertEquals(
            "3/8=140 \"Fast\"",
            ExportABCAction.translateTempo(
                new Tempo(140, Tempo.Type.CROTCHET_DOTTED, "Fast", true)
            )
        );
        assertEquals(
            "1/2=60 \"Slow\"",
            ExportABCAction.translateTempo(
                new Tempo(60, Tempo.Type.MINIM, "Slow", true)
            )
        );
        assertEquals(
            "3/4=70 \"Slow\"",
            ExportABCAction.translateTempo(
                new Tempo(70, Tempo.Type.MINIM_DOTTED, "Slow", true)
            )
        );
        assertEquals(
            "1/8=80 \"Very slow\"",
            ExportABCAction.translateTempo(
                new Tempo(80, Tempo.Type.QUAVER, "Very slow", true)
            )
        );
        assertEquals(
            "3/16=70 \"Very slow\"",
            ExportABCAction.translateTempo(
                new Tempo(70, Tempo.Type.QUAVER_DOTTED, "Very slow", true)
            )
        );
        assertEquals(
            "1/1=50 \"Even slower\"",
            ExportABCAction.translateTempo(
                new Tempo(50, Tempo.Type.SEMI_BREVE, "Even slower", true)
            )
        );
    }

    @Test
    public void testTranslateUnitLength() {
        assertEquals(
            new ExportABCAction.Fraction(4, 1),
            ExportABCAction.translateUnitLength(
                Tempo.Type.SEMI_BREVE.getNote().getDuration(),
                Score.PPQ
            )
        );
        assertEquals(
            new ExportABCAction.Fraction(2, 1),
            ExportABCAction.translateUnitLength(
                Tempo.Type.MINIM.getNote().getDuration(),
                Score.PPQ
            )
        );
        assertEquals(
            new ExportABCAction.Fraction(3, 1),
            ExportABCAction.translateUnitLength(
                Tempo.Type.MINIM_DOTTED.getNote().getDuration(),
                Score.PPQ
            )
        );
        assertEquals(
            new ExportABCAction.Fraction(1, 1),
            ExportABCAction.translateUnitLength(
                Tempo.Type.CROTCHET.getNote().getDuration(),
                Score.PPQ
            )
        );
        assertEquals(
            new ExportABCAction.Fraction(3, 2),
            ExportABCAction.translateUnitLength(
                Tempo.Type.CROTCHET_DOTTED.getNote().getDuration(),
                Score.PPQ
            )
        );
        assertEquals(
            new ExportABCAction.Fraction(1, 2),
            ExportABCAction.translateUnitLength(
                Tempo.Type.QUAVER.getNote().getDuration(),
                Score.PPQ
            )
        );
        assertEquals(
            new ExportABCAction.Fraction(3, 4),
            ExportABCAction.translateUnitLength(
                Tempo.Type.QUAVER_DOTTED.getNote().getDuration(),
                Score.PPQ
            )
        );
    }

    @Test
    public void testTranslatePitch() {
        assertPitch("c''", -15);
        assertPitch("b'", -14);
        assertPitch("c'", -8);
        assertPitch("b", -7);
        assertPitch("c", -1);
        assertPitch("B", 0);
        assertPitch("C", 6);
        assertPitch("B,", 7);
        assertPitch("E,", 11);
        assertPitch("C,", 13);
        assertPitch("B,,", 14);
    }

    private static void assertPitch(String expected, int yPos) {
        var crotchet = new Crotchet();
        crotchet.setYPos(yPos);
        assertEquals(
            expected,
            ExportABCAction.translatePitch(crotchet.getYPos())
        );
    }

    @Test
    public void testTranslateAccidental() {
        assertEquals(
            "",
            ExportABCAction.translateAccidental(Note.Accidental.NONE)
        );
        assertEquals(
            "=",
            ExportABCAction.translateAccidental(Note.Accidental.NATURAL)
        );
        assertEquals(
            "_",
            ExportABCAction.translateAccidental(Note.Accidental.FLAT)
        );
        assertEquals(
            "^",
            ExportABCAction.translateAccidental(Note.Accidental.SHARP)
        );
        assertEquals(
            "==",
            ExportABCAction.translateAccidental(Note.Accidental.DOUBLE_NATURAL)
        );
        assertEquals(
            "__",
            ExportABCAction.translateAccidental(Note.Accidental.DOUBLE_FLAT)
        );
        assertEquals(
            "^^",
            ExportABCAction.translateAccidental(Note.Accidental.DOUBLE_SHARP)
        );
        assertEquals(
            "=_",
            ExportABCAction.translateAccidental(Note.Accidental.NATURAL_FLAT)
        );
        assertEquals(
            "=^",
            ExportABCAction.translateAccidental(Note.Accidental.NATURAL_SHARP)
        );
    }

    @Test
    public void testTranslateNoteLength() {
        var compositionUnitLength = Score.PPQ * 4;
        assertEquals(
            "/32",
            ExportABCAction.translateNoteLength(
                new Demisemiquaver().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "3/64",
            ExportABCAction.translateNoteLength(
                makeDotted(new Demisemiquaver(), 1).getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "7/128",
            ExportABCAction.translateNoteLength(
                makeDotted(new Demisemiquaver(), 2).getDuration(),
                compositionUnitLength
            )
        );

        assertEquals(
            "/16",
            ExportABCAction.translateNoteLength(
                new Semiquaver().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "3/32",
            ExportABCAction.translateNoteLength(
                makeDotted(new Semiquaver(), 1).getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "7/64",
            ExportABCAction.translateNoteLength(
                makeDotted(new Semiquaver(), 2).getDuration(),
                compositionUnitLength
            )
        );

        assertEquals(
            "/8",
            ExportABCAction.translateNoteLength(
                new Quaver().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "3/16",
            ExportABCAction.translateNoteLength(
                makeDotted(new Quaver(), 1).getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "7/32",
            ExportABCAction.translateNoteLength(
                makeDotted(new Quaver(), 2).getDuration(),
                compositionUnitLength
            )
        );

        assertEquals(
            "/4",
            ExportABCAction.translateNoteLength(
                new Crotchet().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "3/8",
            ExportABCAction.translateNoteLength(
                makeDotted(new Crotchet(), 1).getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "7/16",
            ExportABCAction.translateNoteLength(
                makeDotted(new Crotchet(), 2).getDuration(),
                compositionUnitLength
            )
        );

        assertEquals(
            "/2",
            ExportABCAction.translateNoteLength(
                new Minim().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "3/4",
            ExportABCAction.translateNoteLength(
                makeDotted(new Minim(), 1).getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "7/8",
            ExportABCAction.translateNoteLength(
                makeDotted(new Minim(), 2).getDuration(),
                compositionUnitLength
            )
        );

        assertEquals(
            "",
            ExportABCAction.translateNoteLength(
                new Semibreve().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "3/2",
            ExportABCAction.translateNoteLength(
                makeDotted(new Semibreve(), 1).getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "7/4",
            ExportABCAction.translateNoteLength(
                makeDotted(new Semibreve(), 2).getDuration(),
                compositionUnitLength
            )
        );

        compositionUnitLength = Score.PPQ;
        assertEquals(
            "4",
            ExportABCAction.translateNoteLength(
                new Semibreve().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "2",
            ExportABCAction.translateNoteLength(
                new Minim().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "",
            ExportABCAction.translateNoteLength(
                new Crotchet().getDuration(),
                compositionUnitLength
            )
        );
        assertEquals(
            "/2",
            ExportABCAction.translateNoteLength(
                new Quaver().getDuration(),
                compositionUnitLength
            )
        );
    }

    private static Note makeDotted(Note note, int dotted) {
        note.setDotCount(dotted);
        return note;
    }
}
