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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Issue #713: exactly two entries stand for a valid terminal type — {@link
 * FinalDoubleBarlineAction} for {@code FINAL_DOUBLE_BARLINE} and the right-repeat entry for
 * {@code REPEAT_RIGHT}. While the terminal is selected those two are enabled, and choosing
 * either retypes the terminal through {@link Song#replaceTerminal}. Every other barline and
 * repeat entry — the plain double barline included — stays inapplicable and disabled.
 * <p>
 * Applicability and enablement live here. Which entry shows the check-mark is decided by
 * {@code ActionReflector} composing {@code appliesTo} with {@code matchesElement}, so it is
 * covered by {@code ReflectionIntegrationTest} rather than here.
 * <p>
 * The final double barline is the asymmetric one: the right repeat is also an ordinary drawable
 * repeat, whereas the final double barline acts on the terminal and nothing else.
 */
class TerminalTypeActionTest extends MainFrameMockTest {

    private static final String TERMINAL_ANNOTATION_TEXT = "Fine";

    private Song song;
    private FinalDoubleBarlineAction finalDoubleBarlineAction;
    private ElementTypeAction doubleBarlineAction;
    private ElementTypeAction rightRepeatAction;
    private ElementTypeAction singleBarlineAction;

    @BeforeEach
    void setUp() {
        // PlaybackController's static initializer creates UIAction instances with
        // keyboard shortcuts that call mainFrame.getRootPane(), so we need a JRootPane mock.
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockEnv().frame().getRootPane()).thenReturn(mockRootPane);

        song = new Song();
        when(mockEnv().score().isInitialized()).thenReturn(true);
        when(mockEnv().score().getSong()).thenReturn(song);

        finalDoubleBarlineAction = FinalDoubleBarlineAction.createAction(mainFrame());
        doubleBarlineAction = ElementTypeAction.createDoubleBarlineAction(mainFrame());
        rightRepeatAction = ElementTypeAction.createRightRepeatAction(mainFrame());
        singleBarlineAction = ElementTypeAction.createSingleBarlineAction(mainFrame());
    }

    // -------------------------------------------------------------------------
    // appliesTo — which entries can act on the terminal at all
    // -------------------------------------------------------------------------

    @Test
    void testFinalDoubleBarlineAndRightRepeatApplyToTerminal() {
        var terminal = terminal();

        assertThat(finalDoubleBarlineAction.appliesTo(terminal)).isTrue();
        assertThat(rightRepeatAction.appliesTo(terminal)).isTrue();
    }

    // The plain double barline no longer stands in for the final one, so it is now as
    // inapplicable to the terminal as every other entry.
    @Test
    void testOtherBarlineAndRepeatEntriesDoNotApplyToTerminal() {
        var terminal = terminal();

        assertThat(doubleBarlineAction.appliesTo(terminal)).isFalse();
        assertThat(singleBarlineAction.appliesTo(terminal)).isFalse();
        assertThat(ElementTypeAction.createLeftRepeatAction(mainFrame()).appliesTo(terminal)).isFalse();
        assertThat(ElementTypeAction.createLeftRightRepeatAction(mainFrame()).appliesTo(terminal)).isFalse();
    }

    // An ordinary barline is unaffected by the terminal carve-out: every drawable entry applies.
    @Test
    void testEveryDrawableEntryStillAppliesToAnOrdinaryBarline() {
        var ordinaryBarline = addOrdinaryBarline();

        assertThat(doubleBarlineAction.appliesTo(ordinaryBarline)).isTrue();
        assertThat(rightRepeatAction.appliesTo(ordinaryBarline)).isTrue();
        assertThat(singleBarlineAction.appliesTo(ordinaryBarline)).isTrue();
    }

    // The final double barline is not drawable — it acts on the terminal alone, so an ordinary
    // barline leaves it inapplicable and therefore disabled.
    @Test
    void testFinalDoubleBarlineDoesNotApplyToAnOrdinaryBarline() {
        assertThat(finalDoubleBarlineAction.appliesTo(addOrdinaryBarline())).isFalse();
    }

    // The predicate resolves the song through StaffElement.getParentLine(), so an element
    // belonging to no line is not the terminal — and this entry applies to nothing else.
    @Test
    void testUnparentedFinalDoubleBarlineIsNotTreatedAsTheTerminal() {
        var unparented = ElementType.FINAL_DOUBLE_BARLINE.newInstance();

        assertThat(finalDoubleBarlineAction.appliesTo(unparented)).isFalse();
    }

    // -------------------------------------------------------------------------
    // matchesElement — which entry is checked for the terminal's current type
    // -------------------------------------------------------------------------

    @Test
    void testFinalDoubleBarlineEntryChecksAFinalDoubleBarlineTerminal() {
        var terminal = terminal();

        assertThat(finalDoubleBarlineAction.matchesElement(terminal)).isTrue();
        assertThat(rightRepeatAction.matchesElement(terminal)).isFalse();
    }

    @Test
    void testRightRepeatEntryChecksARightRepeatTerminal() {
        song.replaceTerminal(ElementType.REPEAT_RIGHT);
        var terminal = terminal();

        assertThat(rightRepeatAction.matchesElement(terminal)).isTrue();
        assertThat(finalDoubleBarlineAction.matchesElement(terminal)).isFalse();
    }

    // The plain double barline must never check for a final double barline terminal, or the
    // Barline menu would show two barline types checked at once.
    @Test
    void testDoubleBarlineEntryNeverChecksTheTerminal() {
        assertThat(doubleBarlineAction.matchesElement(terminal())).isFalse();
    }

    // -------------------------------------------------------------------------
    // Enablement and reflection through the full updateEnabledState() chain
    // -------------------------------------------------------------------------

    /**
     * With the terminal selected, the two entries that can retype it are enabled and every
     * other barline entry is disabled. Asserted through {@code updateEnabledState()} rather
     * than the predicates alone, because that is what actually drives menu enablement.
     * <p>
     * Enablement only — an action's checked state is set by {@code ActionReflector}, never by
     * {@code updateEnabledState()}, so which entry carries the check-mark is covered in
     * {@code ReflectionIntegrationTest}.
     */
    @Test
    void testTerminalSelectionEnablesThePairAndDisablesTheOthers() {
        selectTerminal();

        assertThat(finalDoubleBarlineAction.updateEnabledState()).isTrue();
        assertThat(rightRepeatAction.updateEnabledState()).isTrue();
        assertThat(doubleBarlineAction.updateEnabledState()).isFalse();
        assertThat(singleBarlineAction.updateEnabledState()).isFalse();
    }

    @Test
    void testEntriesAreDisabledWhenAnOrdinaryNoteIsSelected() {
        var line = song.getLine(0);
        song.withModification(() -> line.addElement(0, ElementType.CROTCHET.newInstance()));
        selectElement(line, 0);

        assertThat(finalDoubleBarlineAction.updateEnabledState()).isFalse();
        assertThat(doubleBarlineAction.updateEnabledState()).isFalse();
        assertThat(rightRepeatAction.updateEnabledState()).isFalse();
    }

    /**
     * With nothing selected the drawable entries arm the pen and stay enabled, but the final
     * double barline has no pen to arm — {@code REQUIRES_SINGLE_SELECTION} is what holds it
     * disabled, and without that flag it would sit enabled in the menu doing nothing.
     */
    @Test
    void testFinalDoubleBarlineIsDisabledWhenNothingIsSelected() {
        assertThat(finalDoubleBarlineAction.updateEnabledState()).isFalse();
        assertThat(doubleBarlineAction.updateEnabledState()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Invoking an entry while the terminal is selected retypes the terminal
    // -------------------------------------------------------------------------

    /**
     * Retyping must go through {@link Song#replaceTerminal}, not through the selection: the
     * selection path replaces the element outright, which the DOM guard in
     * {@code Line.setElement} refuses for the terminal slot with an
     * {@code IllegalStateException}. A regression here therefore throws rather than silently
     * doing the wrong thing.
     */
    @Test
    void testFinalDoubleBarlineEntryRetypesTheTerminalToFinalDoubleBarline() {
        song.replaceTerminal(ElementType.REPEAT_RIGHT);
        selectTerminal();

        perform(finalDoubleBarlineAction, "final-double-barline");

        assertThat(song.currentTerminalType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }

    @Test
    void testRightRepeatEntryRetypesTheTerminalToRightRepeat() {
        selectTerminal();

        perform(rightRepeatAction, "right-repeat");

        assertThat(song.currentTerminalType()).isEqualTo(ElementType.REPEAT_RIGHT);
    }

    // Retyping the terminal is a direct edit — it must never raise a confirmation dialog.
    @Test
    void testRetypingTheTerminalRaisesNoConfirmation() {
        selectTerminal();

        try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
            perform(rightRepeatAction, "right-repeat");

            optionDialogsMock.verifyNoInteractions();
        }

        assertThat(song.currentTerminalType()).isEqualTo(ElementType.REPEAT_RIGHT);
    }

    // -------------------------------------------------------------------------
    // Song.replaceTerminal contract
    // -------------------------------------------------------------------------

    // replaceTerminal is a no-op when the incoming type already matches the current terminal.
    // Important: a mutant removing the early-return guard would call withModification, setting
    // modified=true and emitting a spurious SongDidChangeNotification.
    @Test
    void testReplaceTerminalIsNoOpWhenTypeMatchesCurrent() {
        // A fresh Song defaults to FINAL_DOUBLE_BARLINE, so modified starts false.
        assertThat(song.isModified()).isFalse();

        song.replaceTerminal(ElementType.FINAL_DOUBLE_BARLINE);

        // Terminal is unchanged and no mutation was committed (modified stays false).
        assertThat(song.currentTerminalType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        assertThat(song.isModified()).isFalse();
    }

    @Test
    void testReplaceTerminalThrowsForNonTerminalType() {
        assertThatThrownBy(() -> song.replaceTerminal(ElementType.CROTCHET))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Retyping must carry the terminal's attachments across. The terminal is selectable
     * precisely so an annotation can be attached to it (issue #713), so building the
     * replacement bare would delete the user's annotation the moment they switched the barline
     * type — the exact workflow the feature exists to support. Retyping an ordinary barline
     * already preserves attachments, so losing them here would also be inconsistent.
     */
    @Test
    void testReplaceTerminalPreservesAnAttachedAnnotation() {
        var terminal = terminal();
        terminal.addAttachment(
            new AnnotationAttachment(terminal, new Annotation(TERMINAL_ANNOTATION_TEXT)));

        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        var retyped = terminal();
        assertThat(retyped.getType()).isEqualTo(ElementType.REPEAT_RIGHT);

        var attachment = retyped.findAttachment(AnnotationAttachment.class);
        assertThat(attachment)
            .as("the annotation must survive the terminal's type swap")
            .isNotNull();
        assertThat(attachment.getAnnotation().getAnnotation())
            .isEqualTo(TERMINAL_ANNOTATION_TEXT);
    }

    /**
     * Invoking a drawable barline entry while the terminal is selected must not retype the
     * terminal into an ordinary barline. Normally the entry is disabled, but a Swing action can
     * still be fired by a keyboard shortcut whose enabled flag has not been refreshed, so this
     * fires it directly to confirm the applicability check — not merely the greyed-out menu
     * item — is what keeps the terminal intact.
     */
    @Test
    void testDrawableBarlineEntryLeavesTheTerminalUnchangedWhenFiredDirectly() {
        selectTerminal();

        perform(singleBarlineAction, "single-barline");

        assertThat(song.currentTerminalType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }

    // -- helpers --

    private StaffElement terminal() {
        var line = song.getLine(0);
        return line.getElement(line.elementCount() - 1);
    }

    /** Adds a barline at the head of the first line, where it can never be the terminal. */
    private StaffElement addOrdinaryBarline() {
        var line = song.getLine(0);
        song.withModification(() -> line.addElement(0, ElementType.SINGLE_BARLINE.newInstance()));
        return line.getElement(0);
    }

    private void selectTerminal() {
        var line = song.getLine(0);
        selectElement(line, line.elementCount() - 1);
    }

    /**
     * Puts the mock score view into SELECT mode behind a real coordinator, then collapses the
     * selection onto one element of {@code line} exactly as the click path does.
     */
    private void selectElement(Line line, int elementIndex) {
        when(mockEnv().score().getMode()).thenReturn(Mode.SELECT);

        var coordinator = new SelectionCoordinator(mockEnv().score());
        when(mockEnv().score().getSelectionCoordinator()).thenReturn(coordinator);
        when(mockEnv().score().getSelectionSize()).thenReturn(1);

        coordinator.registerLine(0, line);
        coordinator.activateLine(0);
        coordinator.selectSingleElement(0, elementIndex);
    }

    /** Fires the action as a toolbar button would — the source is the action, not a root pane. */
    private void perform(ElementTypeAction action, String actionCommand) {
        action.actionPerformed(
            new ActionEvent(action, ActionEvent.ACTION_PERFORMED, actionCommand));
    }
}
