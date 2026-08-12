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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import java.awt.event.ActionEvent;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.prefs.Prefs;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Exercises the contract of {@link AttachmentDialog}, the template the three attachment dialogs
 * share, through a stub subclass and a stub back end. What each concrete dialog does with its own
 * controls is asserted in its own test class; what is asserted here is only what the template
 * promises for all of them.
 *
 * <p><b>Opening</b> — {@link AttachmentDialog#getData()} turns one domain fact, whether the element
 * already carries a change, into three presentation decisions: what OK is called, whether Remove is
 * offered, and what the controls are populated with. Both states of that fact are covered, and the
 * promise that opening is never declined is asserted alongside them.
 *
 * <p><b>Committing</b> — OK hands the back end exactly what the controls gathered, and the Remove
 * button routes through the back end's removal and then closes the window.
 *
 * <p><b>Validating</b> — both outcomes: a valid result lets OK proceed, an invalid one stops it.
 * That this is assertable at all without a window on screen is the point of the split — the
 * decision now comes back as a value instead of being fused to the alert that reports it.
 *
 * <p><b>No longer tested here, because the behaviour moved rather than went away:</b>
 * <ul>
 *   <li>resolving the element and line from the score's selection, which is now
 *       {@code AttachmentEditor}'s and is asserted there — the dialog is handed a bound back end
 *       and never looks at the score;</li>
 *   <li>{@code showFor}'s pre-binding, which no longer exists: binding happens at construction, so
 *       there is no unbound state to correct;</li>
 *   <li>the {@code IllegalStateException} guards on commit and removal, which protected against an
 *       unbound dialog. A dialog cannot now be constructed unbound, so nothing can reach them;</li>
 *   <li>the {@code canClearChange} veto. That hook had no production implementor — the only
 *       override in the tree was this class's own test double — and it was removed with the seam.
 *       A back end that needs to refuse says so with a {@link ValidationResult}.</li>
 * </ul>
 */
class AttachmentDialogTest extends MainFrameMockTest {

    private static final String GATHERED = "gathered";
    private static final String EXISTING = "existing";

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

    private record OpeningCase(String description, @Nullable String existing,
                               String expectedOkKey, boolean removeOffered) {}

    static Stream<OpeningCase> openingCases() {
        return Stream.of(
            new OpeningCase("nothing there yet, so OK adds and there is nothing to remove",
                null, Strings.LABEL_BUTTON_ADD, false),
            new OpeningCase("a change is there, so OK modifies and Remove is offered",
                EXISTING, Strings.LABEL_BUTTON_MODIFY, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("openingCases")
    void testGetDataLabelsTheButtonsForWhatOkWillDo(OpeningCase testCase) {
        var dialog = dialogFor(testCase.existing());

        var proceed = dialog.getData();

        assertThat(dialog.okButton.getText())
            .as("OK names the operation the element's current state calls for")
            .isEqualTo(Strings.get(testCase.expectedOkKey()));
        assertThat(dialog.removeButton.isVisible())
            .as("Remove is offered only when there is something to remove")
            .isEqualTo(testCase.removeOffered());
        assertThat(proceed)
            .as("the dialog never declines to open, its element having been resolved before it was built")
            .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("openingCases")
    void testGetDataPopulatesTheControlsFromWhatTheElementCarries(OpeningCase testCase) {
        var dialog = dialogFor(testCase.existing());

        dialog.getData();

        assertThat(dialog.populateCallCount)
            .as("the controls are populated once per opening, for a new attachment as for an existing one")
            .isEqualTo(1);
        assertThat(dialog.populatedWith)
            .as("the controls are populated with the change the element carries, or null for none")
            .isEqualTo(testCase.existing());
    }

    @Test
    void testSetDataCommitsExactlyWhatTheControlsGathered() {
        var dialog = dialogFor(null);

        dialog.setData();

        assertThat(dialog.backEnd.applied)
            .as("OK hands the back end the gathered change, unaltered and exactly once")
            .containsExactly(GATHERED);
    }

    private record ValidationCase(String description, ValidationResult result, boolean proceeds) {}

    static Stream<ValidationCase> validationCases() {
        return Stream.of(
            new ValidationCase("an accepted change lets OK proceed",
                ValidationResult.valid(), true),
            new ValidationCase("a refused change stops OK",
                ValidationResult.invalid(new ValidationFailure(
                    Strings.ALERT_TITLE_INFORMATION, new LocalizedMessage(Strings.ALERT_TITLE_INFORMATION))),
                false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validationCases")
    void testIsValidDataAnswersWhateverTheBackEndDecided(ValidationCase testCase) {
        var dialog = dialogFor(null);
        dialog.backEnd.validationResult = testCase.result();

        assertThat(dialog.isValidData())
            .as("the decision is the back end's; the dialog only reports it")
            .isEqualTo(testCase.proceeds());
        assertThat(dialog.backEnd.validated)
            .as("the back end is asked about the gathered change, not about something else")
            .containsExactly(GATHERED);
    }

    @Test
    void testRemoveButtonRemovesThroughTheBackEndAndClosesTheDialog() {
        var dialog = dialogFor(EXISTING);

        fireRemoveAction(dialog);

        assertThat(dialog.backEnd.removeCount)
            .as("the Remove button routes through the back end rather than touching the element")
            .isEqualTo(1);
        assertThat(dialog.closeCount)
            .as("the dialog closes once the removal is committed")
            .isEqualTo(1);
    }

    private ControlDialog dialogFor(@Nullable String existing) {
        return new ControlDialog(mainFrame(), new StubBackEnd(existing));
    }

    private static void fireRemoveAction(ControlDialog dialog) {
        var listeners = dialog.removeButton.getActionListeners();
        listeners[0].actionPerformed(
            new ActionEvent(dialog.removeButton, ActionEvent.ACTION_PERFORMED, ""));
    }

    /**
     * A back end that records what it was asked to do and answers whatever the test told it to.
     * The template's promises are all about what it hands the back end and when, so the back end
     * is the only place those promises are observable.
     */
    private static final class StubBackEnd implements AttachmentBackEnd<String> {

        private final @Nullable String existing;
        final List<String> validated = new ArrayList<>();
        final List<String> applied = new ArrayList<>();
        ValidationResult validationResult = ValidationResult.valid();
        int removeCount = 0;

        StubBackEnd(@Nullable String existing) {
            this.existing = existing;
        }

        @Override
        public @Nullable String existingChange() {
            return existing;
        }

        @Override
        public ValidationResult validate(String input) {
            validated.add(input);
            return validationResult;
        }

        @Override
        public void apply(String input) {
            applied.add(input);
        }

        @Override
        public void remove() {
            removeCount++;
        }
    }

    /**
     * The smallest concrete {@link AttachmentDialog}: controls that record what they were given
     * and always gather the same value, so that every assertion is about the template rather than
     * about any particular set of widgets.
     *
     * <p>{@code setVisible(false)} is counted rather than performed, because the full teardown
     * would reach a {@code JDialog} this fixture never creates.
     */
    private static final class ControlDialog extends AttachmentDialog<String> {

        final StubBackEnd backEnd;
        @Nullable String populatedWith = null;
        int populateCallCount = 0;
        int closeCount = 0;

        ControlDialog(MainFrame mainFrame, StubBackEnd backEnd) {
            super(mainFrame, "Control Dialog", backEnd);
            this.backEnd = backEnd;
        }

        @Override
        protected void populateControls(@Nullable String existingChange) {
            populatedWith = existingChange;
            populateCallCount++;
        }

        @Override
        protected String gatherChange() {
            return GATHERED;
        }

        @Override
        public void setVisible(boolean visible) {
            if (visible) {
                super.setVisible(true);
            } else {
                closeCount++;
            }
        }
    }
}
