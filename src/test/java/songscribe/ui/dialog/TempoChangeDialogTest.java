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
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.prefs.Prefs;
import songscribe.ui.dialog.backend.AttachmentTarget;
import songscribe.ui.dialog.backend.TempoChangeBackEnd;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Exercises the contract of {@link TempoChangeDialog}, which after the dialog seam is entirely
 * about its {@link TempoSection}: what is put into it and what is read back out.
 *
 * <p><b>Populating</b> — the two classes of input {@link TempoChangeDialog#populateControls} names:
 * an existing tempo, which the section shows, and {@code null}, for which it shows a default
 * {@link Tempo}.
 *
 * <p><b>Round trip</b> — the invariant that populating and then gathering returns an equivalent
 * {@link Tempo}, asserted as a property over representative tempi rather than as a table of
 * expected outputs. It is the one assertion that pins the show-flag inversion in both directions:
 * the section asks whether to show the description <em>alone</em> while {@link Tempo} stores
 * whether the metronome mark is shown <em>as well</em>, so a round trip only survives if the
 * dialog inverts consistently on the way in and on the way out. Sampled rather than enumerated —
 * the beats-per-minute is a number, so the domain is not finite.
 *
 * <p><b>Not tested here:</b> commit and removal, which are the back end's promises and are
 * asserted in {@code TempoChangeBackEndTest}; and the Add/Modify labelling, which the template
 * owns for the whole family and {@code AttachmentDialogTest} asserts once.
 */
class TempoChangeDialogTest extends MainFrameMockTest {

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private TempoChangeDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();

        var line = detachedLine();
        var element = crotchet();
        line.addElement(element);
        dialog = new TempoChangeDialog(mainFrame(), new TempoChangeBackEnd(new AttachmentTarget(line, element)));
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    private record PopulateCase(String description, @Nullable Tempo given, Tempo shown) {}

    static Stream<PopulateCase> populateCases() {
        return Stream.of(
            new PopulateCase("an existing tempo is shown as it stands",
                new Tempo(96, Duration.QUAVER, "Allegretto", false),
                new Tempo(96, Duration.QUAVER, "Allegretto", false)),
            new PopulateCase("no existing tempo shows the Tempo defaults", null, new Tempo())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("populateCases")
    void testPopulateControlsShowsTheExistingTempoOrTheDefault(PopulateCase testCase) {
        var shown = testCase.shown();

        dialog.populateControls(testCase.given());

        assertThat(dialog.tempoSection.getVisibleTempo())
            .as("the section shows the tempo being edited")
            .isEqualTo(shown.getVisibleTempo());
        assertThat(dialog.tempoSection.getTempoType())
            .as("the section shows the note value the tempo is counted in")
            .isEqualTo(shown.getTempoType());
        assertThat(dialog.tempoSection.getTempoDescription())
            .as("the section shows the tempo's description")
            .isEqualTo(shown.getTempoDescription());
        assertThat(dialog.tempoSection.isShowOnlyDescription())
            .as("show-only-description is the inverse of the tempo's show-tempo flag")
            .isEqualTo(!shown.shouldShowTempo());
    }

    static Stream<Tempo> roundTripTempi() {
        return Stream.of(
            new Tempo(),
            new Tempo(80, Duration.MINIM, "Largo", true),
            new Tempo(100, Duration.CROTCHET, "Andante", false)
        );
    }

    @ParameterizedTest
    @MethodSource("roundTripTempi")
    void testGatherReturnsWhatPopulateControlsPutIn(Tempo tempo) {
        dialog.populateControls(tempo);

        var gathered = dialog.gather();

        assertThat(gathered.getVisibleTempo())
            .as("the beats-per-minute survives the round trip")
            .isEqualTo(tempo.getVisibleTempo());
        assertThat(gathered.getTempoType())
            .as("the note value survives the round trip")
            .isEqualTo(tempo.getTempoType());
        assertThat(gathered.getTempoDescription())
            .as("the description survives the round trip")
            .isEqualTo(tempo.getTempoDescription());
        assertThat(gathered.shouldShowTempo())
            .as("the show-tempo flag survives the round trip, so the inversion is consistent")
            .isEqualTo(tempo.shouldShowTempo());
    }
}
