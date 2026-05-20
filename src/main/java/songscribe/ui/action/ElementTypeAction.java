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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.message.notification.BarWasSelectedNotification;
import songscribe.message.notification.DurationWasSelectedNotification;

public class ElementTypeAction extends StickyUIAction implements UIAction.ElementReplaceable {

    public enum Kind {DURATION, NON_DURATION}

    private static final Flag[] DURATION_FLAGS = new Flag[]{
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_IN_ADJUSTMENT_MODE,
        Flag.DISABLE_WHEN_EDITING_TEXT,
    };

    static final Flag[] NON_DURATION_FLAGS = new Flag[]{
        Flag.DISABLE_IN_REST_MODE,
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_IN_ADJUSTMENT_MODE,
        Flag.DISABLE_WHEN_EDITING_TEXT,
        Flag.DISABLE_IN_GRACE_MODE,
    };

    private final ElementType type;
    private final Kind kind;
    private final StaffElement.Glissando.@Nullable Type glissandoType;

    public static ElementTypeAction createGraceEighthNoteAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.GRACE_QUAVER,
            Strings.get(Strings.ACTION_DURATION_GRACE), "grace.svg", 26,
            "duration-grace-eighth", Strings.get(Strings.ACTION_DURATION_GRACE_TOOLTIP),
            KeyEvent.VK_G, 0,
            withFlags(DURATION_FLAGS, Flag.DISABLE_IN_REST_MODE, Flag.DISABLE_IN_SELECT_MODE, Flag.DISABLE_IN_GRACE_MODE)
        );
    }

    public static ElementTypeAction createThirtySecondNoteAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.DEMI_SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND), "@\uF36B", 18,
            "duration-thirty-second", Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND_TOOLTIP),
            KeyEvent.VK_1, 0,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createSixteenthNoteAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_SIXTEENTH), "@\uF36A", 18,
            "duration-sixteenth", Strings.get(Strings.ACTION_DURATION_SIXTEENTH_TOOLTIP),
            KeyEvent.VK_2, 0,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createEighthNoteAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.QUAVER,
            Strings.get(Strings.ACTION_DURATION_EIGHTH), "@\uF369", 18,
            "duration-eighth", Strings.get(Strings.ACTION_DURATION_EIGHTH_TOOLTIP),
            KeyEvent.VK_3, 0,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createQuarterNoteAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.CROTCHET,
            Strings.get(Strings.ACTION_DURATION_QUARTER), "@\uF368", 18,
            "duration-quarter", Strings.get(Strings.ACTION_DURATION_QUARTER_TOOLTIP),
            KeyEvent.VK_4, 0,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createHalfNoteAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.MINIM,
            Strings.get(Strings.ACTION_DURATION_HALF), "@\uF367", 18,
            "duration-half", Strings.get(Strings.ACTION_DURATION_HALF_TOOLTIP),
            KeyEvent.VK_5, 0,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createWholeNoteAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.SEMIBREVE,
            Strings.get(Strings.ACTION_DURATION_WHOLE), "@\uF366", 18,
            "duration-whole", Strings.get(Strings.ACTION_DURATION_WHOLE_TOOLTIP),
            KeyEvent.VK_6, 0,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createGlissandoAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.GLISSANDO, StaffElement.Glissando.Type.CONNECTED,
            Strings.get(Strings.ACTION_DURATION_GLISSANDO), "connecting-glissando.svg", 26,
            "glissando", Strings.get(Strings.ACTION_DURATION_GLISSANDO_TOOLTIP),
            KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK,
            withFlags(NON_DURATION_FLAGS, Flag.DISABLE_IN_SELECT_MODE) // Glissandos do not have a duration
        );
    }

    public static ElementTypeAction createSlideOutAction() {
        return new ElementTypeAction(
            Kind.DURATION, ElementType.GLISSANDO, StaffElement.Glissando.Type.SLIDE_OUT,
            Strings.get(Strings.ACTION_DURATION_SLIDE_OUT), "slide-out.svg", 26,
            "slide-out", Strings.get(Strings.ACTION_DURATION_SLIDE_OUT_TOOLTIP),
            KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK,
            withFlags(NON_DURATION_FLAGS, Flag.DISABLE_IN_SELECT_MODE) // Glissandos do not have a duration
        );
    }

    public static ElementTypeAction createLeftRepeatAction() {
        return new ElementTypeAction(
            Kind.NON_DURATION, ElementType.REPEAT_LEFT,
            Strings.get(Strings.ACTION_REPEAT_LEFT), "@\uEF68", 24,
            "left-repeat", Strings.get(Strings.ACTION_REPEAT_LEFT_TOOLTIP),
            KeyEvent.VK_L, 0,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createRightRepeatAction() {
        return new ElementTypeAction(
            Kind.NON_DURATION, ElementType.REPEAT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_RIGHT), "@\uF345", 24,
            "right-repeat", Strings.get(Strings.ACTION_REPEAT_RIGHT_TOOLTIP),
            KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createLeftRightRepeatAction() {
        return new ElementTypeAction(
            Kind.NON_DURATION, ElementType.REPEAT_LEFT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT), "@\uF34B", 24,
            "left-right-repeat", Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT_TOOLTIP),
            0, 0,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createDoubleBarlineAction() {
        return new ElementTypeAction(
            Kind.NON_DURATION, ElementType.DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_DOUBLE_FINE), "@\uF347", 24,
            "double-barline", Strings.get(Strings.ACTION_BARLINE_DOUBLE_FINE_TOOLTIP),
            KeyEvent.VK_D, 0,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createSingleBarlineAction() {
        return new ElementTypeAction(
            Kind.NON_DURATION, ElementType.SINGLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_SINGLE), "@\uF346", 24,
            "single-barline", Strings.get(Strings.ACTION_BARLINE_SINGLE_TOOLTIP),
            0, 0,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createBreathMarkAction() {
        return new ElementTypeAction(
            Kind.NON_DURATION, ElementType.BREATH_MARK,
            Strings.get(Strings.ACTION_BREATH_MARK), null, 0,
            "breath-mark", Strings.get(Strings.ACTION_BREATH_MARK_TOOLTIP),
            0, 0,
            NON_DURATION_FLAGS
        );
    }

    private ElementTypeAction(
        Kind kind,
        ElementType type,
        String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        this(kind, type, null, name, icon, size, actionCommand, tooltip, virtualKey, modifiers, flags);
    }

    protected ElementTypeAction(
        Kind kind,
        ElementType type,
        StaffElement.Glissando.@Nullable Type glissandoType,
        String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(name, icon, size, actionCommand, tooltip, virtualKey, modifiers, flags);
        this.kind = kind;
        this.type = type;
        this.glissandoType = glissandoType;
    }

    public ElementType getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    public StaffElement.Glissando.@Nullable Type getGlissandoType() {
        return glissandoType;
    }

    @Override
    public boolean matchesGlissandoType(StaffElement.Glissando.Type type) {
        return glissandoType == type;
    }

    @Override
    public boolean appliesTo(StaffElement element) {
        if (kind == Kind.DURATION) {
            return element.getType().isDuration();
        }

        // Breath marks and barlines/repeats are distinct non-duration subcategories
        if (type == ElementType.BREATH_MARK) {
            return element.getType() == ElementType.BREATH_MARK;
        }

        return element.getType().isBarLine() || element.getType().isRepeat();
    }

    @Override
    public boolean matchesElement(StaffElement element) {
        return element.getType().toNote() == type;
    }

    @Override
    public StaffElement createReplacement(StaffElement element, boolean selected) {
        var targetType = element.getType().isRest() ? type.toRest() : type.toNote();
        return new StaffElement(targetType, element);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!doActionPerformed(e)) {
            return;
        }

        if (applyToSelectionIfActive()) {
            return;
        }

        if (kind == Kind.DURATION) {
            MessageCenter.post(new DurationWasSelectedNotification(type));
        } else {
            MessageCenter.post(new BarWasSelectedNotification(type));
        }
    }
}
