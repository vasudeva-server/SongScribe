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

import java.awt.Font;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.JDialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElementFactory;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.NoteGeometry;
import songscribe.prefs.Prefs;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link KeySignatureChangeDialog}'s contract: {@link KeySignatureChangeDialog#showFor}
 * derives {@code ADD} vs {@code EDIT} from whether the line already has its own key, {@code
 * getData()} offers "inherit" only for a line's own key on a line other than line 0 and opens with
 * nothing selected, {@code isValidData()} refuses a change that will not fit, and {@code setData()}
 * commits the one that does through {@link ScoreViewController}.
 *
 * <p>The combo's key entries are asserted against {@link Key#allSignatures()} directly rather
 * than a hand-written list, so a domain change reaches the test. Which combo index "inherit"
 * occupies is this class's own implementation choice (always last), not a promise the dialog
 * makes elsewhere, so tests select it by scanning for the one non-{@link Key} entry rather than
 * by a hard-coded index.
 *
 * <p><b>The two fit rejections</b> get a case each, because the second is on a route the first
 * never reaches: a line's own key is measured over the whole inheritance chain, a mid-line key
 * signature over the line it is spliced into. Both assert <em>the model</em> rather than the
 * alert — an alert that fires while the edit still lands is the failure worth catching — which is
 * why both run against a real song and a real {@link ScoreViewController} instead of a mock that
 * could not write anything even if it were called.
 *
 * <p><b>The ordering of the two prompts</b> — fit first, restatements second — is pinned by
 * asserting that a change refused for want of room never reaches
 * {@link AccidentalRestatements#confirm}. That is the only assertion that distinguishes the two
 * orders; the alert alone cannot, since it fires either way.
 *
 * <p>The commit-routing cases drive {@code setData()} directly against the mock controller, so
 * they assert the route taken and nothing about what the controller then does with it — the
 * accidental reconciliation behind {@code changeLineKey} and the implicit barline behind {@code
 * insertKeySignature} are their own suites' subjects.
 */
class KeySignatureChangeDialogTest extends MainFrameMockTest {

    private static final int A_LATER_LINE_INDEX = 2;

    /** The bound index that means "the line's own key" rather than a position inside the line. */
    private static final int LINE_OWN_KEY_INDEX = 0;

    /** A position inside a line, with elements on both sides of it. */
    private static final int A_MID_LINE_INDEX = 2;

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** Zero-width lyric metrics: every fixture here is lyric-less, so no syllable floor can bind. */
    private static final LyricRenderMetrics METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    private static final Key C_MAJOR = new Key(KeyType.NONE, 0);

    /** The widest signature there is, so a header or cautionary drawn from it is the costliest. */
    private static final Key WIDE_KEY = new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT);

    /** More notes than {@link #CROWDED_MARGIN_SS} can hold even fully compressed. */
    private static final int CROWDED_NOTE_COUNT = 24;
    private static final double CROWDED_MARGIN_SS = 20.0;

    /** Enough notes to have elements on both sides of {@link #A_MID_LINE_INDEX}. */
    private static final int ROOMY_NOTE_COUNT = 4;

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private KeySignatureChangeDialog dialog;

    /**
     * Measuring a line reads accidental widths out of a static table that has to be built first.
     * Without this the fit cases pass only when some earlier test class happens to have built it.
     */
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
        when(mockEnv().score().getLyricRenderMetrics()).thenReturn(METRICS);
        dialog = new KeySignatureChangeDialog(mainFrame());
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ── showFor / getData — the inherit choice, and the combo's key entries ──

    @Test
    void testShowForOnLineZeroOffersNoInheritChoice() {
        var line = lineWithOwnKey(new Key(KeyType.NONE, 0));

        showFor(line);

        assertThat(comboEntries()).as("line 0 offers no inherit choice").isEqualTo(Key.allSignatures());
    }

    @Test
    void testShowForOnALaterLineOffersAnInheritChoiceAfterTheKeys() {
        var line = lineWithOwnKey(new Key(KeyType.NONE, 0));
        when(line.getSong().indexOfLine(line)).thenReturn(A_LATER_LINE_INDEX);

        showFor(line);

        var entries = allComboEntries();
        assertThat(entries.subList(0, entries.size() - 1))
            .as("every key entry, in Key.allSignatures()'s order")
            .isEqualTo(Key.allSignatures());
        assertThat(entries.getLast())
            .as("the inherit choice is the one entry that is not a Key")
            .isNotInstanceOf(Key.class);
    }

    @Test
    void testShowForAMidLineKeySignatureOffersNoInheritChoice() {
        var line = lineWithOwnKey(new Key(KeyType.NONE, 0));
        when(line.getSong().indexOfLine(line)).thenReturn(A_LATER_LINE_INDEX);

        showFor(line, A_MID_LINE_INDEX);

        assertThat(comboEntries())
            .as("a key signature standing in the middle of a line names a key; it cannot inherit one")
            .isEqualTo(Key.allSignatures());
    }

    // ── Opening state, in both derived ops ──

    @ParameterizedTest(name = "{0}")
    @MethodSource("openingCases")
    void testOpensWithNothingSelectedAndOkDisabled(OpeningCase openingCase) {
        var line = openingCase.lineHasOwnKey()
            ? lineWithOwnKey(new Key(KeyType.SHARPS, 3))
            : inheritingLine();

        showFor(line);

        assertThat(dialog.keysCombo.getSelectedIndex()).as("nothing is pre-selected").isEqualTo(-1);
        assertThat(dialog.okButton.isEnabled()).as("OK starts disabled").isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("openingCases")
    void testPickingAnyEntryEnablesOk(OpeningCase openingCase) {
        var line = openingCase.lineHasOwnKey()
            ? lineWithOwnKey(new Key(KeyType.SHARPS, 3))
            : inheritingLine();

        showFor(line);
        dialog.keysCombo.setSelectedItem(Key.allSignatures().getFirst());

        assertThat(dialog.okButton.isEnabled()).as("picking an entry enables OK").isTrue();
    }

    private record OpeningCase(String description, boolean lineHasOwnKey) {
        @Override
        public String toString() {
            return description;
        }
    }

    static Stream<OpeningCase> openingCases() {
        return Stream.of(
            new OpeningCase("EDIT — line already has its own key", true),
            new OpeningCase("ADD — line inherits its key", false)
        );
    }

    // ── setData() — which ScoreViewController route each choice commits through ──

    @Test
    void testSetDataChoosingInheritClearsTheLinesOwnKeyThroughTheController() {
        var line = lineWithOwnKey(new Key(KeyType.SHARPS, 3));
        when(line.getSong().indexOfLine(line)).thenReturn(A_LATER_LINE_INDEX);

        showFor(line);
        dialog.keysCombo.setSelectedItem(inheritChoiceEntry());
        dialog.setData();

        verify(mockEnv().ctrl()).changeLineKey(
            same(line), isNull(), eq(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_KEY)));
    }

    @Test
    void testSetDataChoosingAKeyOnAnInheritingLineEstablishesItThroughTheController() {
        var line = inheritingLine();
        var chosenKey = new Key(KeyType.FLATS, 2);

        showFor(line);
        dialog.keysCombo.setSelectedItem(chosenKey);
        dialog.setData();

        verify(mockEnv().ctrl()).changeLineKey(
            same(line), eq(chosenKey), eq(Strings.get(Strings.ACTION_EDIT_OP_ADD_KEY)));
    }

    @Test
    void testSetDataAtAMidLineIndexWritesAKeySignatureThroughTheController() {
        var line = inheritingLine();
        var chosenKey = new Key(KeyType.FLATS, 2);

        showFor(line, A_MID_LINE_INDEX);
        dialog.keysCombo.setSelectedItem(chosenKey);
        dialog.setData();

        verify(mockEnv().ctrl()).insertKeySignature(same(line), eq(A_MID_LINE_INDEX), eq(chosenKey));
        verify(mockEnv().ctrl(), never()).changeLineKey(any(), any(), any());
    }

    @Test
    void testSetDataWithNoSelectionChangesNothing() {
        var line = lineWithOwnKey(new Key(KeyType.SHARPS, 3));

        showFor(line);
        dialog.setData();

        assertNothingWasCommitted();
    }

    // ── isValidData() — the two fit rejections, and their order against the restatement prompt ──

    @Test
    void testALineKeyChangeThatDoesNotFitIsRejectedAndLeavesTheModelUntouched() {
        var song = crowdedSongOf(2);
        var line = song.getLine(1);
        installRealController(song);

        showFor(line);
        dialog.keysCombo.setSelectedItem(WIDE_KEY);
        dialog.okButton.doClick();

        assertThat(line.getKey())
            .as("the refused change left the line inheriting, as it was")
            .isNull();
        assertKeysUnchangedFrom(song, C_MAJOR);
    }

    @Test
    void testAMidLineKeySignatureThatDoesNotFitIsRejectedAndLeavesTheModelUntouched() {
        var song = crowdedSongOf(1);
        var line = song.getLine(0);
        installRealController(song);
        var elementCountBefore = line.effectiveElementCount();

        showFor(line, A_MID_LINE_INDEX);
        dialog.keysCombo.setSelectedItem(WIDE_KEY);
        dialog.okButton.doClick();

        assertThat(line.effectiveElementCount())
            .as("the refused insertion wrote neither the key signature nor its barline")
            .isEqualTo(elementCountBefore);
        assertThat(elementTypesOf(line))
            .as("no key signature reached the line")
            .doesNotContain(ElementType.KEY_SIGNATURE);
    }

    @Test
    void testALineKeyChangeThatFitsIsCommitted() {
        var song = roomySongOf(2);
        var line = song.getLine(1);
        installRealController(song);

        showFor(line);
        dialog.keysCombo.setSelectedItem(WIDE_KEY);
        dialog.okButton.doClick();

        assertThat(line.getKey())
            .as("a change with room for it reaches the model")
            .isEqualTo(WIDE_KEY);
    }

    @Test
    void testAMidLineKeySignatureThatFitsIsCommitted() {
        var song = roomySongOf(1);
        var line = song.getLine(0);
        installRealController(song);

        showFor(line, A_MID_LINE_INDEX);
        dialog.keysCombo.setSelectedItem(WIDE_KEY);
        dialog.okButton.doClick();

        assertThat(elementTypesOf(line))
            .as("an insertion with room for it reaches the model")
            .contains(ElementType.KEY_SIGNATURE);
    }

    @Test
    void testAChangeRejectedForFitNeverAsksAboutRestatements() {
        var song = crowdedSongOf(2);
        var line = song.getLine(1);
        installRealController(song);

        try (var restatements = mockStatic(AccidentalRestatements.class)) {
            restatements
                .when(() -> AccidentalRestatements.confirm(any(), anyList()))
                .thenReturn(AccidentalRestatements.Decision.PROCEED);

            showFor(line);
            dialog.keysCombo.setSelectedItem(WIDE_KEY);
            dialog.okButton.doClick();

            restatements.verify(
                () -> AccidentalRestatements.confirm(any(), anyList()), never());
        }
    }

    // ── ElementType.categoryName() (Phase 12a task 2) ──

    @Test
    void testCategoryNameReturnsANameForKeySignatureRatherThanThrowing() {
        assertThat(ElementType.KEY_SIGNATURE.categoryName())
            .isEqualTo(Strings.get(Strings.LABEL_ELEMENT_CATEGORY_KEY_SIGNATURE));
    }

    // ── Helpers ──

    private static Line lineWithOwnKey(Key key) {
        var line = detachedLine();
        line.setKey(key);
        return line;
    }

    private static Line inheritingLine() {
        var line = detachedLine();
        line.setKey(null);
        return line;
    }

    /**
     * A real song of {@code lineCount} lines, in C major, with {@code noteCount} crotchets on its
     * first line and a staff {@code lineWidthSs} wide. Line 0 carries the notes because it is both
     * the line a mid-line insertion lands on and the line whose end a change on line 1 would put a
     * cautionary at.
     */
    private static Song songOf(int lineCount, double lineWidthSs, int noteCount) {
        var song = new Song();

        for (var index = 1; index < lineCount; index++) {
            song.addLine(new Line(song));
        }

        song.withModification(() -> {
            song.setLineWidthSs(lineWidthSs);
            song.getLine(0).setKey(C_MAJOR);
        });

        song.withoutMutationTracking(() -> {
            for (var index = 0; index < noteCount; index++) {
                song.getLine(0).addElement(StaffElementFactory.crotchet());
            }
        });

        return song;
    }

    /** A song whose staff is narrower than its own first line, so every key change is refused. */
    private static Song crowdedSongOf(int lineCount) {
        return songOf(lineCount, CROWDED_MARGIN_SS, CROWDED_NOTE_COUNT);
    }

    /** A song wide enough that no key change measured against it is ever refused. */
    private static Song roomySongOf(int lineCount) {
        return songOf(lineCount, UNCONSTRAINED_LINE_WIDTH_SS, ROOMY_NOTE_COUNT);
    }

    /**
     * Puts a real {@link ScoreViewController} behind the mock score view, so a commit this test
     * expects to be refused would actually reach the model if the refusal failed.
     */
    private void installRealController(Song song) {
        var scoreView = mockEnv().score();
        when(scoreView.getSong()).thenReturn(song);
        when(scoreView.getController()).thenReturn(new ScoreViewController(
            scoreView,
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class)));
    }

    private void assertNothingWasCommitted() {
        verify(mockEnv().ctrl(), never()).changeLineKey(any(), any(), any());
        verify(mockEnv().ctrl(), never()).insertKeySignature(any(), anyInt(), any());
    }

    /** Asserts every line of {@code song} still runs in {@code key}, as it did before the edit. */
    private static void assertKeysUnchangedFrom(Song song, Key key) {
        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            assertThat(song.getLine(lineIndex).getRunningKey())
                .as("line %d is still in the key it was in", lineIndex)
                .isEqualTo(key);
        }
    }

    private static List<ElementType> elementTypesOf(Line line) {
        var types = new ArrayList<ElementType>();

        for (var index = 0; index < line.effectiveElementCount(); index++) {
            types.add(line.getElement(index).getType());
        }

        return types;
    }

    /** Shows {@link #dialog} for {@code line}'s own key, with its JDialog window mocked out. */
    private void showFor(Line line) {
        showFor(line, LINE_OWN_KEY_INDEX);
    }

    /** Shows {@link #dialog} bound to {@code index} on {@code line}. */
    private void showFor(Line line, int index) {
        try (var construction = mockConstruction(JDialog.class,
                (mockDialog, context) -> BaseDialogTestHelper.configureMockDialog(mockDialog, new Point(100, 100)))) {
            dialog.showFor(line, index);
        }
    }

    private List<Object> allComboEntries() {
        var model = dialog.keysCombo.getModel();
        var entries = new ArrayList<Object>();

        for (var i = 0; i < model.getSize(); i++) {
            entries.add(model.getElementAt(i));
        }

        return entries;
    }

    private List<Key> comboEntries() {
        return allComboEntries().stream()
            .filter(Key.class::isInstance)
            .map(Key.class::cast)
            .toList();
    }

    private Object inheritChoiceEntry() {
        return allComboEntries().stream()
            .filter(entry -> !(entry instanceof Key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no inherit entry in the combo"));
    }
}
