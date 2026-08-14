/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.undo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.quaver;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Duration;
import songscribe.dom.Hairpin;
import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.command.ToggleTieWithPreviousCommand;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.action.ActionsTestSupport;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Exercises the fallback half of {@link UndoController#undoLabel()}'s contract: what an
 * edit that declared no op-name is called. The declared-name half — a name used verbatim
 * when the initiator supplied one — belongs to {@link UndoOpNameLabelTest}.
 *
 * <p><b>The mutation domain is enumerated, not sampled.</b> The fallback maps a step to a
 * name through its dominant mutation, and {@code Mutation} is sealed, so the set of cases
 * is closed and every member of it belongs here — element insertion, deletion, range
 * deletion, replacement and modification; line insertion and deletion; line key and layout
 * changes; every span kind in both directions; lyrics and fonts; and each
 * {@code MetadataField} in turn, including the two that share the key-change name. Present
 * today: the additions of each span kind but not their removals, and no range deletion.
 *
 * <p><b>Dominance is a precedence order, not first-wins.</b> The row that proves it is
 * deleting a note inside a tuplet: the tuplet-removal companion is recorded first, yet the
 * step must read "Delete Note". A first-wins implementation passes every other case in this
 * class and fails this one.
 *
 * <p><b>Agreement between the two routes to one edit</b> — a tie or beam applied by its
 * insertion key must name the step the way the menu action naming the same edit does.
 * A disagreement is invisible in production and shows up only as two names for one thing.
 *
 * <p>Expected labels are resolved through the same {@link Strings} constants production
 * uses, never spelled out in English: the promise is which name is chosen, not what that
 * name says in one locale.
 *
 * <p>Steps come from driving real edits, so each captured batch carries the companion
 * mutations that make dominance a question at all. Where an edit for a given mutation type
 * would take an unreasonable fixture to drive, the batch is posted directly instead — those
 * cases test the mapping only, and cannot show what a real batch's companions would do to
 * it.
 *
 * <p>The empty-stack boundary — no steps yields the plain "Undo"/"Redo" — is common to both
 * halves of the contract and is exercised once, in {@link UndoOpNameLabelTest}.
 *
 * <p>Most cases below share one algorithm — build a song, optionally arrange some
 * pre-existing state, drive one edit, and check the resulting label — so they are rows in a
 * {@code record} case table rather than one hand-written {@code @Test} per row; only the
 * arrange/edit code and the expected key vary. A case whose fixture or assertion is
 * genuinely different (font changes, the last-insertion keys, the declared-name hairpin
 * cases) gets its own table or its own method instead of being forced into this one.
 */
class MutationLabelTest extends UnitTest {

    /** Notes in the line the hairpin label tests edit. */
    private static final int HAIRPIN_FIXTURE_NOTE_COUNT = 4;

    /** Selection that reaches past the pre-existing crescendo on [0, 1]. */
    // Any key that differs from the fixture song's own is enough to make the change land.
    private static final int ONE_SHARP = 1;

    private static final int EXTEND_SELECTION_BEGIN = 2;
    private static final int EXTEND_SELECTION_END = 3;

    private static final BiConsumer<Song, Line> NO_ARRANGE = (song, line) -> {
    };

    // The last-insertion key handlers read their undo label off the menu action the key
    // falls through to, which is the whole point of the two tests below.
    @BeforeEach
    void initializeActions() {
        ActionsTestSupport.initializeActions();
    }

    /** Resets the singleton, drives one real edit, and returns the resulting undo label. */
    private static String undoLabelAfter(Song song, Runnable edit) {
        UndoController.initialize();
        UndoController.reset();
        song.withModification(edit);
        return UndoController.undoLabel();
    }

    /** Resets the singleton, posts a crafted batch, and returns the resulting undo label. */
    private static String undoLabelForBatch(List<Mutation> mutations) {
        UndoController.initialize();
        UndoController.reset();
        MessageCenter.post(new SongDidChangeNotification(mutations, new Song()));
        return UndoController.undoLabel();
    }

    private static String labeled(String opKey) {
        return Strings.get(Strings.ACTION_EDIT_UNDO_LABELED, Strings.get(opKey));
    }

    private static Song songWithNotes(int count) {
        var song = new Song();
        UndoTestSupport.addCrotchets(song, song.getLine(0), count);
        return song;
    }

    // -----------------------------------------------------------------------
    // One label per dominant mutation type
    // -----------------------------------------------------------------------

    private record DominantMutationLabelCase(
        String description, int noteCount, BiConsumer<Song, Line> arrange,
        BiConsumer<Song, Line> edit, String expectedKey
    ) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dominantMutationLabelCases")
    void testDominantMutationLabel(DominantMutationLabelCase testCase) {
        var song = songWithNotes(testCase.noteCount());
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> testCase.arrange().accept(song, line));

        assertThat(undoLabelAfter(song, () -> testCase.edit().accept(song, line)))
            .isEqualTo(labeled(testCase.expectedKey()));
    }

    static Stream<DominantMutationLabelCase> dominantMutationLabelCases() {
        return Stream.of(
            new DominantMutationLabelCase("element insertion labels add note", 2, NO_ARRANGE,
                (song, line) -> line.addElement(1, UndoTestSupport.crotchet()), Strings.ACTION_EDIT_OP_ADD_NOTE),
            new DominantMutationLabelCase("element deletion labels delete note", 2, NO_ARRANGE,
                (song, line) -> line.removeElement(0), Strings.ACTION_EDIT_OP_DELETE_NOTE),
            new DominantMutationLabelCase("element replacement labels replace note", 2, NO_ARRANGE,
                (song, line) -> line.setElement(0, quaver()), Strings.ACTION_EDIT_OP_REPLACE_NOTE),
            new DominantMutationLabelCase("element modification labels edit note", 2, NO_ARRANGE,
                (song, line) -> line.modifyElement(0, ElementField.FERMATA, () -> line.getElement(0).setFermata(true)),
                Strings.ACTION_EDIT_OP_EDIT_NOTE),
            new DominantMutationLabelCase("line insertion labels add line", 1, NO_ARRANGE,
                (song, line) -> song.addLine(song.lineCount(), new Line(song)), Strings.ACTION_EDIT_OP_ADD_LINE),
            new DominantMutationLabelCase("line deletion labels delete line", 1,
                (song, line) -> song.addLine(song.lineCount(), new Line(song)),
                (song, line) -> song.removeLine(song.lineCount() - 1), Strings.ACTION_EDIT_OP_DELETE_LINE),
            new DominantMutationLabelCase("line key change labels change key", 1, NO_ARRANGE,
                (song, line) -> line.setKey(new Key(KeyType.SHARPS, ONE_SHARP)), Strings.ACTION_EDIT_OP_CHANGE_KEY),
            new DominantMutationLabelCase("line layout change labels change layout", 1, NO_ARRANGE,
                (song, line) -> line.setLyricsYPosSs(line.getLyricsYPosSs() + 3.0),
                Strings.ACTION_EDIT_OP_CHANGE_LAYOUT),
            new DominantMutationLabelCase("layout change labels change layout", 1, NO_ARRANGE,
                (song, line) -> song.setLineWidthSs(song.getLineWidthSs() + 10.0),
                Strings.ACTION_EDIT_OP_CHANGE_LAYOUT),
            new DominantMutationLabelCase("beaming addition labels beaming", 2, NO_ARRANGE,
                (song, line) -> line.addBeaming(new Beam(line.getElement(0), line.getElement(1))),
                Strings.ACTION_EDIT_OP_BEAMING),
            new DominantMutationLabelCase("tie addition labels tie", 2, NO_ARRANGE,
                (song, line) -> line.addTie(new Tie(line.getElement(0), line.getElement(1))),
                Strings.ACTION_EDIT_OP_TIE),
            new DominantMutationLabelCase("tuplet addition labels tuplet", 3, NO_ARRANGE,
                (song, line) -> line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(2), 3)),
                Strings.ACTION_EDIT_OP_TUPLET),
            new DominantMutationLabelCase("crescendo addition labels crescendo", 2, NO_ARRANGE,
                (song, line) -> line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1))),
                Strings.ACTION_EDIT_OP_CRESCENDO),
            new DominantMutationLabelCase("diminuendo addition labels diminuendo", 2, NO_ARRANGE,
                (song, line) -> line.addDiminuendo(new Diminuendo(line.getElement(0), line.getElement(1))),
                Strings.ACTION_EDIT_OP_DIMINUENDO),
            new DominantMutationLabelCase("span addition labels span", 2, NO_ARRANGE,
                (song, line) -> line.addSpan(new Trill(line.getElement(0), line.getElement(1))),
                Strings.ACTION_EDIT_OP_SPAN),
            new DominantMutationLabelCase("lyrics change labels edit lyrics", 1, NO_ARRANGE,
                (song, line) -> song.setUnderLyrics("under"), Strings.ACTION_EDIT_OP_EDIT_LYRICS),
            new DominantMutationLabelCase("metadata attribution change labels change attribution", 1, NO_ARRANGE,
                (song, line) -> song.setMetadata(song.getMetadata().withTitle("New")),
                Strings.ACTION_EDIT_OP_CHANGE_ATTRIBUTION),
            new DominantMutationLabelCase("metadata tempo change labels change tempo", 1, NO_ARRANGE,
                (song, line) -> song.setTempo(new Tempo(90, Duration.CROTCHET, "Slow", true)),
                Strings.ACTION_EDIT_OP_CHANGE_TEMPO),
            new DominantMutationLabelCase("metadata footnotes change labels change footnotes", 1, NO_ARRANGE,
                (song, line) -> song.setFootnotes("note"), Strings.ACTION_EDIT_OP_CHANGE_FOOTNOTES),
            // Deleting a tuplet-spanned note emits the tuplet-removal companion BEFORE the
            // primary ElementDeletion, so a "first mutation" label would read "Tuplet". The
            // precedence tier must still select the ElementDeletion -> "Delete Note".
            new DominantMutationLabelCase(
                "deleting a note inside a tuplet labels delete note, not tuplet", 3,
                (song, line) -> line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(2), 3)),
                (song, line) -> line.removeElement(1), Strings.ACTION_EDIT_OP_DELETE_NOTE)
        );
    }

    // -----------------------------------------------------------------------
    // Tier-A labels on the last-insertion keys
    //
    // The two cases above prove the mutation-kind fallback, which is what a tie or beam
    // mutation gets when nothing declares an op-name. The keys must not land there: they
    // perform the same edit as the menu action and have to read the same in the Edit menu.
    // -----------------------------------------------------------------------

    /** Drives one real last-insertion handler over a two-quaver line and returns the undo label. */
    private static String undoLabelAfterKeyCommand(Consumer<? super ScoreViewController> handler) {
        var song = new Song();
        var line = song.getLine(0);

        // Quavers, not crotchets: the same pair has to be both tieable and beamable, and a
        // crotchet has no flag to beam.
        song.withoutMutationTracking(() -> {
            line.addElement(quaver());
            line.addElement(quaver());
        });

        var controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class)
        );

        // MusicEditOperations is deliberately left unmocked: the label comes from the real
        // mutation reaching the real UndoController, which is what the assertion is about.
        try (
            var playback = mockStatic(PlaybackController.class);
            var editModeManager = mockStatic(EditModeManager.class)
        ) {
            playback.when(PlaybackController::isPlaying).thenReturn(false);
            editModeManager.when(EditModeManager::getLastInsertion)
                .thenReturn(new EditModeManager.Insertion(line, 1));

            UndoController.initialize();
            UndoController.reset();
            handler.accept(controller);

            return UndoController.undoLabel();
        }
    }

    private record LastInsertionKeyLabelCase(
        String description, Consumer<? super ScoreViewController> handler, String expectedKey
    ) {
    }

    /**
     * The last-insertion keys post their commands straight from {@code ScoreInputHandler},
     * bypassing the {@code UIAction} template that otherwise sets the Tier-A op-name. Without
     * {@code ScoreViewController.handleLastInsertionCommand} setting it, the step falls through
     * to {@link UndoController#opNameKey} and the key labels the very same edit differently from
     * the menu action that also performs it — "Undo Tie" against the menu's "Undo Toggle Tie".
     *
     * <p>Each handler reads its label off the real {@code Actions.*} constant, so this drives the
     * real handler against the real action: the expected key below is the one place each label
     * is written down, and the menu entry resolves from the same declaration.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("lastInsertionKeyLabelCases")
    void testLastInsertionKeyLabelMatchesMenuAction(LastInsertionKeyLabelCase testCase) {
        assertThat(undoLabelAfterKeyCommand(testCase.handler()))
            .isEqualTo(labeled(testCase.expectedKey()));
    }

    static Stream<LastInsertionKeyLabelCase> lastInsertionKeyLabelCases() {
        return Stream.of(
            new LastInsertionKeyLabelCase(
                "tie key labels the undo step the same way the menu action does",
                controller -> controller.handleToggleTieWithPrevious(new ToggleTieWithPreviousCommand()),
                Strings.ACTION_EDIT_OP_TOGGLE_TIE),
            // The beam key carries the same obligation, and read "Undo Beaming" before it did.
            new LastInsertionKeyLabelCase(
                "beam key labels the undo step the same way the menu action does",
                controller -> controller.handleToggleBeamWithPrevious(new ToggleBeamWithPreviousCommand()),
                Strings.ACTION_EDIT_OP_TOGGLE_BEAM)
        );
    }

    // FontChange targets ScoreView, not Song, so it cannot be produced by a Song-only
    // edit — a crafted single-mutation batch exercises the label path (no companion
    // ordering to worry about for this type). The one case in this class with no sibling
    // sharing its fixture shape, so it stays a plain @Test.
    @Test
    void testFontChangeLabelsChangeFonts() {
        assertThat(undoLabelForBatch(List.of(new FontChange(new DocumentFonts(), new DocumentFonts()))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_FONTS));
    }

    // -----------------------------------------------------------------------
    // Declared op-names: one action, two labels
    // -----------------------------------------------------------------------

    /**
     * Drives a hairpin add/extend over the given selection and returns the resulting
     * undo label.
     *
     * <p>The edit is run bare rather than through {@link #undoLabelAfter}: the op-name
     * is captured only at the outermost bracket, and {@code addHairpinToSelection}
     * opens that bracket itself with its own declared name.
     */
    private static String hairpinUndoLabel(Song song, int selectionBegin, int selectionEnd) {
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(song.getLine(0));
        var operations = new MusicEditOperations(song, coordinator);
        ReflectionTestHelper.selectRange(coordinator, selectionBegin, selectionEnd);

        UndoController.initialize();
        UndoController.reset();
        operations.addHairpinToSelection(Hairpin.Kind.CRESCENDO);
        return UndoController.undoLabel();
    }

    private record HairpinDeclaredLabelCase(
        String description, Consumer<Line> arrange, int selectionBegin, int selectionEnd, String expectedKey
    ) {
    }

    /**
     * Both cases drive {@code addHairpinToSelection} over a
     * {@value #HAIRPIN_FIXTURE_NOTE_COUNT}-note line, which resolves to either an add or an
     * extend depending on whether the selection reaches an existing crescendo — the same
     * mutation-batch shape either way, so a type-derived label could not tell add from extend
     * apart, only the declared op-name can.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("hairpinDeclaredLabelCases")
    void testHairpinDeclaredLabel(HairpinDeclaredLabelCase testCase) {
        var song = songWithNotes(HAIRPIN_FIXTURE_NOTE_COUNT);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> testCase.arrange().accept(line));

        assertThat(hairpinUndoLabel(song, testCase.selectionBegin(), testCase.selectionEnd()))
            .isEqualTo(labeled(testCase.expectedKey()));
    }

    static Stream<HairpinDeclaredLabelCase> hairpinDeclaredLabelCases() {
        return Stream.of(
            new HairpinDeclaredLabelCase("adding a crescendo labels add crescendo",
                line -> {
                }, 0, 1, Strings.ACTION_HAIRPIN_CRESCENDO),
            // Selecting past the existing crescendo resolves to EXTEND.
            new HairpinDeclaredLabelCase(
                "extending a crescendo labels extend crescendo, not add crescendo",
                line -> line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1))),
                EXTEND_SELECTION_BEGIN, EXTEND_SELECTION_END, Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND)
        );
    }
}
