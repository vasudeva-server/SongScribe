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
import java.awt.event.*;

import javax.swing.*;

import songscribe.util.UIUtils;

@SuppressWarnings("ALL")
public enum NoteType {
    // Notes
    SEMIBREVE(new Semibreve(), "Whole note", KeyEvent.VK_6, 0),
    MINIM(new Minim(), "Half note", KeyEvent.VK_5, 0),
    CROTCHET(new Crotchet(), "Quarter note", KeyEvent.VK_4, 0),
    QUAVER(new Quaver(), "Eighth note", KeyEvent.VK_3, 0),
    SEMIQUAVER(new Semiquaver(), "Sixteenth note", KeyEvent.VK_2, 0),
    DEMI_SEMIQUAVER(
        new Demisemiquaver(),
        "Thirtysecond note",
        KeyEvent.VK_1,
        0
    ),

    // Rests
    SEMIBREVE_REST(new SemibreveRest(), "Whole rest", KeyEvent.VK_6, -1),
    MINIM_REST(new MinimRest(), "Half rest", KeyEvent.VK_5, -1),
    CROTCHET_REST(new CrotchetRest(), "Quarter rest", KeyEvent.VK_4, -1),
    QUAVER_REST(new QuaverRest(), "Eighth rest", KeyEvent.VK_3, -1),
    SEMIQUAVER_REST(new SemiquaverRest(), "Sixteenth rest", KeyEvent.VK_2, -1),
    DEMI_SEMIQUAVER_REST(
        new DemisemiquaverRest(),
        "Thirtysecond rest",
        KeyEvent.VK_1,
        -1
    ),

    // Grace notes
    GRACE_QUAVER(new GraceQuaver(), "Grace eighth", KeyEvent.VK_G, 0),
    GRACE_SEMIQUAVER(new GraceSemiQuaver(), "Grace sixteenth"),

    // Other
    GLISSANDO(
        Note.GLISSANDO_NOTE,
        "Glissando",
        KeyEvent.VK_G,
        InputEvent.SHIFT_DOWN_MASK
    ),
    REPEAT_LEFT(new RepeatLeft(), "Repeat left", KeyEvent.VK_L, 0),
    REPEAT_RIGHT(new RepeatRight(), "Repeat right", KeyEvent.VK_R, 0),
    REPEAT_LEFT_RIGHT(new RepeatLeftRight(), "Repeate left/right"),
    BREATH_MARK(new BreathMark(), "Breath mark"),
    SINGLE_BARLINE(new SingleBarLine(), "Single barline"),
    DOUBLE_BARLINE(new DoubleBarLine(), "Double barline"),
    FINAL_DOUBLE_BARLINE(new FinalDoubleBarLine(), "Final double barline"),

    PASTE(Note.PASTE_NOTE, null),
    GRACE_SEMIQUAVER_EDIT_STEP1(new GraceSemiQuaverEditStep1(), null),

    // IO values
    SEMIBREVEREST(NoteType.SEMIBREVE_REST),
    MINIMREST(NoteType.MINIM_REST),
    CROTCHETREST(NoteType.CROTCHET_REST),
    QUAVERREST(NoteType.QUAVER_REST),
    SEMIQUAVERREST(NoteType.SEMIQUAVER_REST),
    DEMISEMIQUAVERREST(NoteType.DEMI_SEMIQUAVER_REST),
    GRACEQUAVER(NoteType.GRACE_QUAVER),
    GRACESEMIQUAVER(NoteType.GRACE_SEMIQUAVER),
    REPEATLEFT(NoteType.REPEAT_LEFT),
    REPEATRIGHT(NoteType.REPEAT_RIGHT),
    REPEATLEFTRIGHT(NoteType.REPEAT_LEFT_RIGHT),
    BREATHMARK(NoteType.BREATH_MARK),
    SINGLEBARLINE(NoteType.SINGLE_BARLINE),
    DOUBLEBARLINE(NoteType.DOUBLE_BARLINE),
    FINALDOUBLEBARLINE(NoteType.FINAL_DOUBLE_BARLINE);

    public static int getMenuShortcutKeyMask() {
        return !GraphicsEnvironment.isHeadless()
            ? UIUtils.MENU_SHORTCUT_MASK
            : 0;
    }

    private final Note instance;
    private final String name;
    private final KeyStroke acceleratorKey;

    NoteType(Note instance, String name) {
        this(instance, name, 0, 0);
    }

    NoteType(Note instance, String name, int keyCode, int modifiers) {
        this.instance = instance;
        this.name = name;

        if (keyCode != 0) {
            if (modifiers == -1) {
                modifiers = getMenuShortcutKeyMask();
            }

            this.acceleratorKey = KeyStroke.getKeyStroke(keyCode, modifiers);
        } else {
            this.acceleratorKey = null;
        }
    }

    NoteType(NoteType note) {
        this.instance = note.instance;
        this.name = note.name;
        this.acceleratorKey = note.acceleratorKey;
    }

    public Note getInstance() {
        return instance;
    }

    public Note newInstance() {
        return instance.clone();
    }

    public String getName() {
        return name;
    }

    public String getTip() {
        return UIUtils.makeTooltipWithKeystroke(name, acceleratorKey);
    }

    public KeyStroke getAcceleratorKey() {
        return acceleratorKey;
    }

    public boolean isRealNote() {
        //noinspection ConstantValue
        return (
            ordinal() >= SEMIBREVE.ordinal() &&
            ordinal() <= DEMI_SEMIQUAVER.ordinal()
        );
    }

    public boolean isNote() {
        return isRealNote() || isGraceNote();
    }

    public boolean isNoteWithStem() {
        return isNote() && this != SEMIBREVE;
    }

    public boolean isRest() {
        return (
            ordinal() >= SEMIBREVE_REST.ordinal() &&
            ordinal() <= DEMI_SEMIQUAVER_REST.ordinal()
        );
    }

    public boolean isBeamable() {
        return this == QUAVER || this == SEMIQUAVER || this == DEMI_SEMIQUAVER;
    }

    public boolean isRepeat() {
        return (
            this == REPEAT_LEFT ||
            this == REPEAT_RIGHT ||
            this == REPEAT_LEFT_RIGHT
        );
    }

    public boolean isBarLine() {
        return (
            ordinal() >= SINGLE_BARLINE.ordinal() &&
            ordinal() <= FINAL_DOUBLE_BARLINE.ordinal()
        );
    }

    public boolean isGraceNote() {
        return this == GRACE_QUAVER || this == GRACE_SEMIQUAVER;
    }

    public boolean drawStaveLongitude() {
        return this != BREATH_MARK && this != GRACE_SEMIQUAVER;
    }

    public boolean snapToEnd() {
        return (
            this == REPEAT_RIGHT ||
            this == SINGLE_BARLINE ||
            this == DOUBLE_BARLINE ||
            this == FINAL_DOUBLE_BARLINE
        );
    }
}
