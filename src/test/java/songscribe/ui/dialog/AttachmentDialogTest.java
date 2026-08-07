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
package songscribe.ui.dialog;

import java.awt.event.ActionEvent;
import java.util.Collections;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AttachmentDialog} base-class behaviour:
 * element/line fetch on first show, add/modify branching, setData mutation
 * delegation, remove-button action, and null-guard on setData/remove.
 */
class AttachmentDialogTest extends MainFrameMockTest {

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ── Row 1: getData() fetches element/line from score when selectedElement is null ──

    @Test
    void testGetDataFetchesElementAndLineFromScoreWhenSelectedElementIsNull() {
        var element = ElementType.CROTCHET.newInstance();
        var line = lineWith(ElementType.CROTCHET);
        var song = mock(Song.class);
        when(song.getLine(anyInt())).thenReturn(line);

        var score = mockEnv().score();
        when(score.getSingleSelectedElement()).thenReturn(element);
        when(score.getSong()).thenReturn(song);

        var dialog = new ControlDialog(mainFrame(), /* existingChange= */ null);
        dialog.getData();

        assertThat(dialog.selectedElement)
            .as("selectedElement set from score selection on first call")
            .isSameAs(element);
        assertThat(dialog.selectedLine)
            .as("selectedLine set from score on first call")
            .isSameAs(line);
    }

    @Test
    void testGetDataDoesNotFetchFromScoreWhenSelectedElementAlreadySet() {
        var element = ElementType.CROTCHET.newInstance();
        var line = lineWith(ElementType.CROTCHET);

        var dialog = new ControlDialog(mainFrame(), /* existingChange= */ null);
        // Pre-set fields — simulates what showForElement would do
        dialog.selectedElement = element;
        dialog.selectedLine = line;

        dialog.getData();

        // score.getSingleSelectedElement() must NOT have been called
        var score = mockEnv().score();
        verify(score, never()).getSingleSelectedElement();
        assertThat(dialog.selectedElement)
            .as("selectedElement left intact when already set")
            .isSameAs(element);
    }

    @Test
    void testGetDataWhenScoreReturnsNullSelectionLeavesSelectedElementNull() {
        var song = mock(Song.class);
        var score = mockEnv().score();
        when(score.getSingleSelectedElement()).thenReturn(null);
        when(score.getSong()).thenReturn(song);

        var dialog = new ControlDialog(mainFrame(), /* existingChange= */ null);
        var result = dialog.getData();

        assertThat(dialog.selectedElement)
            .as("selectedElement remains null when score has no selection")
            .isNull();
        assertThat(result)
            .as("getData() returns true even when no element selected")
            .isTrue();
    }

    // ── Row 2: getData() add/modify branching — button text and removeButton visibility ──

    @Test
    void testGetDataWhenNoExistingChangeSetsAddTextAndHidesRemoveButton() {
        setupScoreWithElement(ElementType.CROTCHET);
        var dialog = new ControlDialog(mainFrame(), /* existingChange= */ null);
        dialog.getData();

        assertThat(dialog.okButton.getText())
            .as("okButton shows Add text when no existing change")
            .isEqualTo(Strings.get(Strings.LABEL_BUTTON_ADD));
        assertThat(dialog.removeButton.isVisible())
            .as("removeButton hidden when adding")
            .isFalse();
    }

    @Test
    void testGetDataWhenExistingChangeSetsSetsModifyTextAndShowsRemoveButton() {
        var element = setupScoreWithElement(ElementType.CROTCHET);
        // existingChange is non-null → modify branch
        var dialog = new ControlDialog(mainFrame(), /* existingChange= */ element);
        dialog.getData();

        assertThat(dialog.okButton.getText())
            .as("okButton shows Modify text when existing change present")
            .isEqualTo(Strings.get(Strings.LABEL_BUTTON_MODIFY));
        assertThat(dialog.removeButton.isVisible())
            .as("removeButton shown when modifying")
            .isTrue();
    }

    // ── Row 3: getData() returns true unconditionally ──

    @Test
    void testGetDataReturnsTrueUnconditionally() {
        setupScoreWithElement(ElementType.CROTCHET);
        var dialog = new ControlDialog(mainFrame(), null);

        assertThat(dialog.getData())
            .as("getData() always returns true")
            .isTrue();
    }

    // ── Row 4: setData() delegates mutation to line.modifyElement on correct index ──

    @Test
    void testSetDataCallsModifyElementOnCorrectIndexWithCorrectField() {
        var element = ElementType.CROTCHET.newInstance();
        var line = spy(detachedLine());
        line.addElement(element);

        var dialog = new ControlDialog(mainFrame(), null);
        dialog.selectedElement = element;
        dialog.selectedLine = line;

        dialog.setData();

        // Verify modifyElement was called with the correct index and the stub's field
        verify(line).modifyElement(eq(0), eq(ElementField.ANNOTATION), any(Runnable.class));
        assertThat(dialog.applyChangeCallCount)
            .as("applyChange invoked inside mutation")
            .isEqualTo(1);
    }

    // ── Row 5: remove button action — clearChange called and dialog hidden ──

    @Test
    void testRemoveButtonCallsClearChangeAndHidesDialog() {
        var element = ElementType.CROTCHET.newInstance();
        var line = spy(detachedLine());
        line.addElement(element);

        var dialog = new ControlDialog(mainFrame(), null);
        dialog.selectedElement = element;
        dialog.selectedLine = line;

        fireRemoveAction(dialog);

        verify(line).modifyElement(eq(0), eq(ElementField.ANNOTATION), any(Runnable.class));
        assertThat(dialog.clearChangeCallCount)
            .as("clearChange invoked by remove action")
            .isEqualTo(1);
        assertThat(dialog.closeCallCount)
            .as("dialog hidden after remove action")
            .isEqualTo(1);
    }

    // ── Row 5a: remove button vetoed by canClearChange ──

    /**
     * The veto must be checked before the modification bracket opens: {@code Line.modifyElement}
     * records unconditionally, so a guard placed one line too late still refuses and still leaves
     * the score untouched — but leaves a phantom undo step behind. Only the notification and
     * modified-flag assertions here can see that.
     */
    @Test
    void testRemoveButtonVetoedByCanClearChangeMakesNoModification() {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));
        song.setModified(false);

        var dialog = new VetoDialog(mainFrame());
        dialog.selectedElement = line.getElement(0);
        dialog.selectedLine = line;

        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            fireRemoveAction(dialog);
            messageCenterMock.verify(() -> MessageCenter.post(any(SongDidChangeNotification.class)), never());
        }

        assertThat(dialog.clearChangeCallCount)
            .as("clearChange not invoked when the removal is vetoed")
            .isZero();
        assertThat(song.isModified())
            .as("a vetoed removal records no mutation")
            .isFalse();
        assertThat(dialog.closeCallCount)
            .as("dialog left visible when the removal is vetoed")
            .isZero();
    }

    // ── Row 6: setData()/remove button throw when element or line is null ──

    @Test
    void testSetDataThrowsWhenSelectedElementIsNull() {
        var dialog = new ControlDialog(mainFrame(), null);
        // leave selectedElement and selectedLine null
        assertThatThrownBy(dialog::setData)
            .as("setData throws when element is null")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testSetDataThrowsWhenSelectedLineIsNull() {
        var element = ElementType.CROTCHET.newInstance();
        var dialog = new ControlDialog(mainFrame(), null);
        dialog.selectedElement = element;
        // leave selectedLine null
        assertThatThrownBy(dialog::setData)
            .as("setData throws when line is null")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testRemoveButtonThrowsWhenSelectedElementIsNull() {
        var dialog = new ControlDialog(mainFrame(), null);
        assertThatThrownBy(() -> fireRemoveAction(dialog))
            .as("remove action throws when element is null")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testRemoveButtonThrowsWhenSelectedLineIsNull() {
        var element = ElementType.CROTCHET.newInstance();
        var dialog = new ControlDialog(mainFrame(), null);
        dialog.selectedElement = element;
        assertThatThrownBy(() -> fireRemoveAction(dialog))
            .as("remove action throws when line is null")
            .isInstanceOf(IllegalStateException.class);
    }

    // ── Helpers ──

    /**
     * Stubs the mock score to return a single-element line and the given element
     * as the selection. Returns the element for use in the test.
     */
    private StaffElement setupScoreWithElement(ElementType type) {
        var element = type.newInstance();
        var line = lineWith(type);
        var song = mock(Song.class);
        when(song.getLine(anyInt())).thenReturn(line);

        var score = mockEnv().score();
        when(score.getSingleSelectedElement()).thenReturn(element);
        when(score.getSong()).thenReturn(song);

        return element;
    }

    private static void fireRemoveAction(ControlDialog dialog) {
        var listeners = dialog.removeButton.getActionListeners();
        var event = new ActionEvent(dialog.removeButton, ActionEvent.ACTION_PERFORMED, "");
        listeners[0].actionPerformed(event);
    }

    /**
     * Minimal concrete {@link AttachmentDialog} stub.
     *
     * <ul>
     *   <li>{@code existingChange} — value returned by {@link #getExistingChange}; {@code null}
     *       → adding branch, non-null → modify branch.</li>
     *   <li>Tracks how many times {@link #applyChange} and {@link #clearChange} are called.</li>
     *   <li>Overrides {@link #setVisible}{@code (false)} to count close calls without triggering
     *       the full dialog teardown (which would NPE on an uninitialised {@link javax.swing.JDialog}).</li>
     * </ul>
     */
    private static class ControlDialog extends AttachmentDialog<StaffElement> {

        final @Nullable StaffElement existingChange;
        int applyChangeCallCount = 0;
        int clearChangeCallCount = 0;
        int closeCallCount = 0;

        ControlDialog(MainFrame mainFrame, @Nullable StaffElement existingChange) {
            super(mainFrame, "Control Dialog");
            this.existingChange = existingChange;
        }

        @Override
        protected ElementField getElementField() {
            return ElementField.ANNOTATION;
        }

        @Override
        protected String opLabel(AttachmentOp op) {
            return Strings.get(switch (op) {
                case ADD -> Strings.ACTION_EDIT_OP_ADD_ANNOTATION;
                case CHANGE -> Strings.ACTION_EDIT_OP_CHANGE_ANNOTATION;
                case REMOVE -> Strings.ACTION_EDIT_OP_REMOVE_ANNOTATION;
            });
        }

        @Override
        protected @Nullable StaffElement getExistingChange(StaffElement element) {
            return existingChange;
        }

        @Override
        protected void populateControls(@Nullable StaffElement change) {
            // no-op for testing
        }

        @Override
        protected void applyChange(StaffElement element) {
            applyChangeCallCount++;
        }

        @Override
        protected void clearChange(StaffElement element) {
            clearChangeCallCount++;
        }

        @Override
        public void setVisible(boolean visible) {
            if (!visible) {
                closeCallCount++;
                // Skip super to avoid NPE on the uninitialised JDialog field
            } else {
                super.setVisible(true);
            }
        }
    }

    /** A {@link ControlDialog} whose Remove button is always vetoed. */
    private static class VetoDialog extends ControlDialog {

        VetoDialog(MainFrame mainFrame) {
            super(mainFrame, /* existingChange= */ null);
        }

        @Override
        protected boolean canClearChange(StaffElement element) {
            return false;
        }
    }
}
