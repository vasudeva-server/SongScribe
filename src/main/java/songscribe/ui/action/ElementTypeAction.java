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
import songscribe.ui.component.MainFrame;
import songscribe.message.MessageCenter;
import songscribe.dom.ElementType;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.message.notification.BarWasSelectedNotification;
import songscribe.message.notification.DurationWasSelectedNotification;

public class ElementTypeAction extends StickyUIAction implements UIAction.ElementReplaceable {

    public enum Kind {DURATION, NON_DURATION}

    private static final Flag[] DURATION_FLAGS = new Flag[]{
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_WHEN_EDITING_TEXT,
    };

    static final Flag[] NON_DURATION_FLAGS = new Flag[]{
        Flag.DISABLE_IN_REST_MODE,
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_WHEN_EDITING_TEXT,
        Flag.DISABLE_IN_GRACE_MODE,
    };

    private final ElementType type;
    private final Kind kind;
    private final @Nullable SlideZone slideZone;

    public static ElementTypeAction createGraceEighthNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.GRACE_QUAVER,
            Strings.get(Strings.ACTION_DURATION_GRACE), "grace.svg", 26,
            "duration-grace-eighth", Strings.get(Strings.ACTION_DURATION_GRACE_TOOLTIP),
            KeyEvent.VK_G, 0,
            null,
            withFlags(DURATION_FLAGS, Flag.DISABLE_IN_REST_MODE, Flag.DISABLE_IN_SELECT_MODE, Flag.DISABLE_IN_GRACE_MODE)
        );
    }

    public static ElementTypeAction createThirtySecondNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.DEMI_SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND), "@\uF36B", 18,
            "duration-thirty-second", Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND_TOOLTIP),
            KeyEvent.VK_1, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createSixteenthNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_SIXTEENTH), "@\uF36A", 18,
            "duration-sixteenth", Strings.get(Strings.ACTION_DURATION_SIXTEENTH_TOOLTIP),
            KeyEvent.VK_2, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createEighthNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.QUAVER,
            Strings.get(Strings.ACTION_DURATION_EIGHTH), "@\uF369", 18,
            "duration-eighth", Strings.get(Strings.ACTION_DURATION_EIGHTH_TOOLTIP),
            KeyEvent.VK_3, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createQuarterNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.CROTCHET,
            Strings.get(Strings.ACTION_DURATION_QUARTER), "@\uF368", 18,
            "duration-quarter", Strings.get(Strings.ACTION_DURATION_QUARTER_TOOLTIP),
            KeyEvent.VK_4, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createHalfNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.MINIM,
            Strings.get(Strings.ACTION_DURATION_HALF), "@\uF367", 18,
            "duration-half", Strings.get(Strings.ACTION_DURATION_HALF_TOOLTIP),
            KeyEvent.VK_5, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createWholeNoteAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.SEMIBREVE,
            Strings.get(Strings.ACTION_DURATION_WHOLE), "@\uF366", 18,
            "duration-whole", Strings.get(Strings.ACTION_DURATION_WHOLE_TOOLTIP),
            KeyEvent.VK_6, 0,
            Strings.ACTION_EDIT_OP_CHANGE_DURATION,
            DURATION_FLAGS
        );
    }

    public static ElementTypeAction createGlissandoAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.SLIDE, SlideZone.GLISSANDO,
            Strings.get(Strings.ACTION_DURATION_GLISSANDO), "connecting-glissando.svg", 26,
            "glissando", Strings.get(Strings.ACTION_DURATION_GLISSANDO_TOOLTIP),
            KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK,
            Strings.ACTION_EDIT_OP_ADD_GLISSANDO,
            withFlags(NON_DURATION_FLAGS, Flag.DISABLE_IN_SELECT_MODE) // Glissandos do not have a duration
        );
    }

    public static ElementTypeAction createFallAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.DURATION, ElementType.SLIDE, SlideZone.FALL,
            Strings.get(Strings.ACTION_DURATION_FALL), "fall.svg", 26,
            "fall", Strings.get(Strings.ACTION_DURATION_FALL_TOOLTIP),
            KeyEvent.VK_F, 0,
            Strings.ACTION_EDIT_OP_ADD_FALL,
            withFlags(NON_DURATION_FLAGS, Flag.DISABLE_IN_SELECT_MODE) // Falls do not have a duration
        );
    }

    public static ElementTypeAction createLeftRepeatAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.NON_DURATION, ElementType.REPEAT_LEFT,
            Strings.get(Strings.ACTION_REPEAT_LEFT), "@\uEF68", 24,
            "left-repeat", Strings.get(Strings.ACTION_REPEAT_LEFT_TOOLTIP),
            KeyEvent.VK_L, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createRightRepeatAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.NON_DURATION, ElementType.REPEAT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_RIGHT), "@\uF345", 24,
            "right-repeat", Strings.get(Strings.ACTION_REPEAT_RIGHT_TOOLTIP),
            KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createLeftRightRepeatAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.NON_DURATION, ElementType.REPEAT_LEFT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT), "@\uF34B", 24,
            "left-right-repeat", Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT_TOOLTIP),
            0, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createDoubleBarlineAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.NON_DURATION, ElementType.DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_DOUBLE_FINE), "@\uF347", 24,
            "double-barline", Strings.get(Strings.ACTION_BARLINE_DOUBLE_FINE_TOOLTIP),
            KeyEvent.VK_D, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createSingleBarlineAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.NON_DURATION, ElementType.SINGLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_SINGLE), "@\uF346", 24,
            "single-barline", Strings.get(Strings.ACTION_BARLINE_SINGLE_TOOLTIP),
            0, 0,
            null,
            NON_DURATION_FLAGS
        );
    }

    public static ElementTypeAction createBreathMarkAction(MainFrame mainFrame) {
        return new ElementTypeAction(
            mainFrame,
            Kind.NON_DURATION, ElementType.BREATH_MARK,
            Strings.get(Strings.ACTION_BREATH_MARK), null, 0,
            "breath-mark", Strings.get(Strings.ACTION_BREATH_MARK_TOOLTIP),
            KeyEvent.VK_COMMA, 0,
            Strings.ACTION_EDIT_OP_ADD_BREATH_MARK,
            NON_DURATION_FLAGS
        );
    }

    private ElementTypeAction(
        MainFrame mainFrame,
        Kind kind,
        ElementType type,
        String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        @Nullable String undoOpNameKey,
        Flag... flags
    ) {
        this(mainFrame, kind, type, null, name, icon, size, actionCommand, tooltip, virtualKey, modifiers, undoOpNameKey, flags);
    }

    protected ElementTypeAction(
        MainFrame mainFrame,
        Kind kind,
        ElementType type,
        @Nullable SlideZone slideZone,
        String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        @Nullable String undoOpNameKey,
        Flag... flags
    ) {
        super(mainFrame, name, icon, size, actionCommand, tooltip, virtualKey, modifiers, flags);
        this.kind = kind;
        this.type = type;
        this.slideZone = slideZone;
        setUndoOpNameKey(undoOpNameKey);
    }

    public ElementType getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    public @Nullable SlideZone getSlideZone() {
        return slideZone;
    }

    @Override
    public boolean matchesSlide(StaffElement element) {
        return slideZone != null && slideZone.matches(element);
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

    /**
     * Whether this action replaces a barline or repeat element. Only these dynamic
     * replace actions (and {@link FinalTerminalAction}) compute a from-category label;
     * durations, slides and breath marks keep their static Tier-A key.
     */
    private boolean isBarLineOrRepeatReplace() {
        return kind == Kind.NON_DURATION && (type.isBarLine() || type.isRepeat());
    }

    /**
     * Resolves the Tier-A undo op-name. For barline/repeat replace actions the label
     * names the <em>selected</em> element's category ({@code Change Barline} /
     * {@code Change Repeat}), reusing {@link ElementType#isBarLine()}/{@link
     * ElementType#isRepeat()} — the same taxonomy the delete logic uses. Because this
     * runs on every dispatch (including no-selection ones that only set pen state), it
     * null-guards the selection and returns the static base key (super) when there is no
     * initialized score or nothing selected — never dereferencing a null element.
     */
    @Override
    public @Nullable String getUndoOpName() {
        if (!isBarLineOrRepeatReplace()) {
            return super.getUndoOpName();
        }

        var scoreView = getScoreView();

        if (scoreView == null || !scoreView.isInitialized()) {
            return super.getUndoOpName();
        }

        var selectedElements = scoreView.getSelectionCoordinator().getSelectedElements();

        if (selectedElements.isEmpty()) {
            return super.getUndoOpName();
        }

        var selectedType = selectedElements.getFirst().getType();

        if (selectedType.isRepeat()) {
            return Strings.get(Strings.ACTION_EDIT_OP_CHANGE_REPEAT);
        }

        if (selectedType.isBarLine()) {
            return Strings.get(Strings.ACTION_EDIT_OP_CHANGE_BARLINE);
        }

        return super.getUndoOpName();
    }

    @Override
    protected void performAction(ActionEvent e) {
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
