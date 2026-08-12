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

import java.util.Collections;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.StaffElement;
import songscribe.prefs.Prefs;
import songscribe.ui.dialog.backend.AttachmentTarget;
import songscribe.ui.dialog.backend.BeatChangeBackEnd;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Exercises the contract of {@link BeatChangeDialog}, which after the dialog seam is entirely
 * about controls: what goes into them, what comes back out, and what the buttons say.
 *
 * <p><b>Populating</b> — the two classes of input {@link BeatChangeDialog#populateControls} names:
 * an existing beat change, which the combos show, and {@code null}, for which they show
 * {@link BeatChangeDialog#DEFAULT_BEAT_CHANGE}.
 *
 * <p><b>Round trip</b> — the invariant that populating and then gathering returns the same
 * {@link BeatChange}, asserted as a property rather than a table of expected outputs. Enumerated
 * over {@link Duration} rather than sampled, so a new note value reaches this test on its own; each
 * value is exercised in both combos at once, since the two are independent and identically built.
 *
 * <p><b>Button labelling</b> — the presentation decision {@link AttachmentDialog#getData()} makes
 * from a domain fact: OK reads Add and Remove is hidden when the element carries no beat change,
 * and OK reads Modify and Remove is offered when it does.
 *
 * <p><b>Not tested here:</b> the class contract's promise that both combos always carry a
 * selection, which {@link BeatChangeDialog#gatherChange()} relies on. Nothing reachable through
 * the UI can empty a combo built over {@link Duration}, so the guard behind it is unreachable by
 * construction and forcing it — by installing an empty model — would assert against a state the
 * contract says cannot exist. Commit and removal are the back end's promises and are asserted in
 * {@code BeatChangeBackEndTest}.
 */
class BeatChangeDialogTest extends MainFrameMockTest {

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private StaffElement element;
    private BeatChangeDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();

        var line = detachedLine();
        element = crotchet();
        line.addElement(element);
        dialog = new BeatChangeDialog(mainFrame(), new BeatChangeBackEnd(new AttachmentTarget(line, element)));
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    private record PopulateCase(String description, @Nullable BeatChange given, BeatChange shown) {}

    static Stream<PopulateCase> populateCases() {
        return Stream.of(
            new PopulateCase("an existing beat change is shown as it stands",
                new BeatChange(Duration.MINIM, Duration.QUAVER),
                new BeatChange(Duration.MINIM, Duration.QUAVER)),
            new PopulateCase("no existing beat change shows the default",
                null,
                BeatChangeDialog.DEFAULT_BEAT_CHANGE)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("populateCases")
    void testPopulateControlsShowsTheExistingChangeOrTheDefault(PopulateCase testCase) {
        dialog.populateControls(testCase.given());

        assertThat(dialog.durationCombo.getSelectedItem())
            .as("the duration combo shows the change being edited")
            .isEqualTo(testCase.shown().duration());
        assertThat(dialog.beatCombo.getSelectedItem())
            .as("the beat combo shows the change being edited")
            .isEqualTo(testCase.shown().beat());
    }

    @ParameterizedTest
    @EnumSource(Duration.class)
    void testGatherChangeReturnsWhateverPopulateControlsPutIn(Duration duration) {
        var change = new BeatChange(duration, duration);

        dialog.populateControls(change);

        assertThat(dialog.gatherChange())
            .as("populating and gathering with nothing in between is the identity")
            .isEqualTo(change);
    }

    private record ButtonCase(String description, @Nullable BeatChange existing,
                              String expectedOkKey, boolean removeOffered) {}

    static Stream<ButtonCase> buttonCases() {
        return Stream.of(
            new ButtonCase("nothing there yet, so OK adds and there is nothing to remove",
                null, Strings.LABEL_BUTTON_ADD, false),
            new ButtonCase("a change is there, so OK modifies and Remove is offered",
                new BeatChange(Duration.MINIM, Duration.QUAVER), Strings.LABEL_BUTTON_MODIFY, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buttonCases")
    void testGetDataLabelsTheButtonsForWhatOkWillDo(ButtonCase testCase) {
        if (testCase.existing() != null) {
            element.addAttachment(new BeatChangeAttachment(element, testCase.existing()));
        }

        dialog.getData();

        assertThat(dialog.okButton.getText())
            .as("OK names the operation the element's current state calls for")
            .isEqualTo(Strings.get(testCase.expectedOkKey()));
        assertThat(dialog.removeButton.isVisible())
            .as("Remove is offered only when there is something to remove")
            .isEqualTo(testCase.removeOffered());
    }
}
