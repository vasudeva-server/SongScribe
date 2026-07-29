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

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.selection.ReflectionTestHelper;

/**
 * Verifies {@link UndoController#undoLabel()} derives the Edit-menu op-name from a
 * step's <em>dominant</em> mutation. Steps are produced by driving real edits (so the
 * captured batch carries companion mutations), then asserting the composed label.
 *
 * <p>Expected labels are built from the same {@link Strings} constants the production
 * code uses — never hardcoded English — but the dominant-mutation <em>selection</em> is
 * exercised independently: the tuplet-note-deletion case proves the precedence tier
 * beats companion ordering (a tuplet-removal companion is emitted before the primary
 * {@code ElementDeletion}, yet the label must read "Delete Note", not "Tuplet").
 */
class MutationLabelTest extends UnitTest {

    /** Notes in the line the hairpin label tests edit. */
    private static final int HAIRPIN_FIXTURE_NOTE_COUNT = 4;

    /** Selection that reaches past the pre-existing crescendo on [0, 1]. */
    private static final int EXTEND_SELECTION_BEGIN = 2;
    private static final int EXTEND_SELECTION_END = 3;

    /** Resets the singleton, drives one real edit, and returns the resulting undo label. */
    private static String undoLabelAfter(Song song, Runnable edit) {
        UndoController.resetForTest();
        song.withModification(edit);
        return UndoController.undoLabel();
    }

    /** Resets the singleton, posts a crafted batch, and returns the resulting undo label. */
    private static String undoLabelForBatch(List<Mutation> mutations) {
        UndoController.resetForTest();
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
    // Empty-stack plain labels
    // -----------------------------------------------------------------------

    @Test
    void testEmptyUndoStackShowsPlainUndoLabel() {
        UndoController.resetForTest();
        assertThat(UndoController.undoLabel()).isEqualTo(Strings.get(Strings.ACTION_EDIT_UNDO));
    }

    @Test
    void testEmptyRedoStackShowsPlainRedoLabel() {
        UndoController.resetForTest();
        assertThat(UndoController.redoLabel()).isEqualTo(Strings.get(Strings.ACTION_EDIT_REDO));
    }

    // -----------------------------------------------------------------------
    // One label per dominant mutation type
    // -----------------------------------------------------------------------

    @Test
    void testElementInsertionLabelsAddNote() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.addElement(1, UndoTestSupport.crotchet())))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_ADD_NOTE));
    }

    @Test
    void testElementDeletionLabelsDeleteNote() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.removeElement(0)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_DELETE_NOTE));
    }

    @Test
    void testElementReplacementLabelsReplaceNote() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.setElement(0, ElementType.QUAVER.newInstance())))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_REPLACE_NOTE));
    }

    @Test
    void testElementModificationLabelsEditNote() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () ->
            line.modifyElement(0, ElementField.FERMATA, () -> line.getElement(0).setFermata(true))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_EDIT_NOTE));
    }

    @Test
    void testLineInsertionLabelsAddLine() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song, () -> song.addLine(song.lineCount(), new Line(song))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_ADD_LINE));
    }

    @Test
    void testLineDeletionLabelsDeleteLine() {
        var song = songWithNotes(1);
        song.addLine(song.lineCount(), new Line(song));
        var lastIndex = song.lineCount() - 1;
        assertThat(undoLabelAfter(song, () -> song.removeLine(lastIndex)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_DELETE_LINE));
    }

    @Test
    void testLineKeyChangeLabelsChangeKey() {
        var song = songWithNotes(1);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.setKeyType(KeyType.SHARPS)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_KEY));
    }

    @Test
    void testLineLayoutChangeLabelsChangeLayout() {
        var song = songWithNotes(1);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.setLyricsYPosSs(line.getLyricsYPosSs() + 3.0)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_LAYOUT));
    }

    @Test
    void testLayoutChangeLabelsChangeLayout() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song, () -> song.setLineWidthSs(song.getLineWidthSs() + 10.0)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_LAYOUT));
    }

    @Test
    void testBeamingAdditionLabelsBeaming() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.addBeaming(new Beam(line.getElement(0), line.getElement(1)))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_BEAMING));
    }

    @Test
    void testTieAdditionLabelsTie() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.addTie(new Tie(line.getElement(0), line.getElement(1)))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_TIE));
    }

    @Test
    void testTupletAdditionLabelsTuplet() {
        var song = songWithNotes(3);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.addTuplet(new Tuplet(line.getElement(0), line.getElement(2), 3))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_TUPLET));
    }

    @Test
    void testCrescendoAdditionLabelsCrescendo() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song, () -> line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1)))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CRESCENDO));
    }

    @Test
    void testDiminuendoAdditionLabelsDiminuendo() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song,
            () -> line.addDiminuendo(new Diminuendo(line.getElement(0), line.getElement(1)))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_DIMINUENDO));
    }

    @Test
    void testRangeElementAdditionLabelsRangeElement() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        assertThat(undoLabelAfter(song,
            () -> line.addRangeElement(new Trill(line.getElement(0), line.getElement(1)))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_RANGE_ELEMENT));
    }

    @Test
    void testLyricsChangeLabelsEditLyrics() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song, () -> song.setUnderLyrics("under")))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_EDIT_LYRICS));
    }

    // FontChange targets ScoreView, not Song, so it cannot be produced by a Song-only
    // edit — a crafted single-mutation batch exercises the label path (no companion
    // ordering to worry about for this type).
    @Test
    void testFontChangeLabelsChangeFonts() {
        assertThat(undoLabelForBatch(List.of(new FontChange(new DocumentFonts(), new DocumentFonts()))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_FONTS));
    }

    // -----------------------------------------------------------------------
    // MetadataChange fields
    // -----------------------------------------------------------------------

    @Test
    void testMetadataAttributionChangeLabelsChangeAttribution() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song, () -> song.setMetadata(song.getMetadata().withTitle("New"))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_ATTRIBUTION));
    }

    @Test
    void testMetadataTempoChangeLabelsChangeTempo() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song, () -> song.setTempo(new Tempo(90, songscribe.dom.Duration.CROTCHET, "Slow", true))))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_TEMPO));
    }

    @Test
    void testMetadataFootnotesChangeLabelsChangeFootnotes() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song, () -> song.setFootnotes("note")))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_FOOTNOTES));
    }

    @Test
    void testMetadataDefaultKeyAccidentalCountChangeLabelsChangeKey() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song,
            () -> song.setDefaultKeyAccidentalCount(song.getDefaultKeyAccidentalCount() + 3)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_KEY));
    }

    @Test
    void testMetadataDefaultKeyTypeChangeLabelsChangeKey() {
        var song = songWithNotes(1);
        assertThat(undoLabelAfter(song, () -> song.setDefaultKeyType(KeyType.SHARPS)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_CHANGE_KEY));
    }

    // -----------------------------------------------------------------------
    // Precedence: dominant mutation beats companion ordering
    // -----------------------------------------------------------------------

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

        UndoController.resetForTest();
        operations.addHairpinToSelection(true);
        return UndoController.undoLabel();
    }

    @Test
    void testAddingCrescendoLabelsAddCrescendo() {
        var song = songWithNotes(HAIRPIN_FIXTURE_NOTE_COUNT);
        assertThat(hairpinUndoLabel(song, 0, 1))
            .isEqualTo(labeled(Strings.ACTION_HAIRPIN_CRESCENDO));
    }

    @Test
    void testExtendingCrescendoLabelsExtendCrescendoNotAddCrescendo() {
        var song = songWithNotes(HAIRPIN_FIXTURE_NOTE_COUNT);
        var line = song.getLine(0);
        song.withoutMutationTracking(() ->
            line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1))));

        // Selecting past the existing crescendo resolves to EXTEND_CRESCENDO, which must
        // declare a different op-name than the add path — the mutation batch is the same
        // shape either way, so a type-derived label could not tell them apart.
        assertThat(hairpinUndoLabel(song, EXTEND_SELECTION_BEGIN, EXTEND_SELECTION_END))
            .isEqualTo(labeled(Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND));
    }

    @Test
    void testDeletingNoteInsideTupletLabelsDeleteNoteNotTuplet() {
        var song = songWithNotes(3);
        var line = song.getLine(0);
        song.withoutMutationTracking(() ->
            line.addTuplet(new Tuplet(line.getElement(0), line.getElement(2), 3)));

        // Deleting a tuplet-spanned note emits the tuplet-removal companion BEFORE the
        // primary ElementDeletion, so a "first mutation" label would read "Tuplet".
        // The precedence tier must still select the ElementDeletion → "Delete Note".
        assertThat(undoLabelAfter(song, () -> line.removeElement(1)))
            .isEqualTo(labeled(Strings.ACTION_EDIT_OP_DELETE_NOTE));
    }
}
