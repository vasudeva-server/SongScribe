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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.AfterEach;
import javax.swing.JOptionPane;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.engio.mbassy.listener.Handler;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.layout.NoteGeometry;
import songscribe.ui.OptionDialogs;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.layout.Ending;
import songscribe.layout.EndingLineFixture;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LineEndingSupport;
import songscribe.message.Message;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.InsertLineCommand;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.command.SelectAllElementsCommand;
import songscribe.message.MessageCenter;
import songscribe.ui.action.PasteboardAction;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.dom.SongMetadata;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.prefs.PrefsKey;
import songscribe.ui.EndingConfirms;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.action.Actions;
import songscribe.ui.action.DurationActionGroup;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.action.ModeAction;
import songscribe.ui.adjustment.HorizontalAdjustment;
import songscribe.ui.adjustment.VerticalAdjustment;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LinePanel;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.StaffPanel;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;

class ScoreViewControllerTest extends UnitTest {

    // Measuring a projected line reads accidental widths out of a static table that has to be
    // built first. Without this the class passes only when some earlier test class happens to
    // have built it, and fails whenever it runs alone.
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DeleteLyric {

        @Test
        void testDeleteRemovesSelectedLyricAndClearsLyricSelection() {
            var song = new Song();
            var line = song.getLine(0);
            var element = ElementType.CROTCHET.newInstance();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "om", Lyric.Extend.NONE);
            song.withoutMutationTracking(() -> line.addElement(element));

            var selectionCoordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            selectionCoordinator.selectLyric(element, 1);
            assertThat(selectionCoordinator.getLyricSelection()).isEqualTo(
                new SelectionCoordinator.LyricSelection(element, 1));
            assertThat(line.getElementIndex(element)).isGreaterThanOrEqualTo(0);

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.isFocusOwner()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.canDeleteLine()).thenReturn(false);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                selectionCoordinator,
                mock(ClipboardManager.class)
            );

            // handleDelete is package-private — no reflection needed
            controller.handleDelete();

            assertThat(element.getLyricForVerse(1)).isNull();
            assertThat(selectionCoordinator.hasLyricSelection()).isFalse();
            assertThat(selectionCoordinator.hasActiveSelection()).isFalse();
            verify(scoreMock).selectionChanged();
            verify(scoreMock).repaint();
        }
    }

    // -----------------------------------------------------------------------
    // handleDelete — rows 16-20
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleDelete {

        private static StaffElement crotchet() {
            return ElementType.CROTCHET.newInstance();
        }

        private static ScoreViewController buildController(
            Song song,
            SelectionCoordinator coordinator,
            ScoreView scoreMock
        ) {
            return new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                mock(ClipboardManager.class)
            );
        }

        // Row 16: contiguous-range path
        @Test
        void testHandleDeleteContiguousRangeStripsGlissandoAndShiftsAndRemovesElements() {
            // Layout: [A(gliss), B, C, D] — select [B, C] (indices 1..2)
            // Expected: glissando removed from A; B and C deleted; D shifted;
            // A and D remain.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            noteA.setGlissando();
            noteA.setXOffsetPx(0);
            var noteB = crotchet();
            noteB.setXOffsetPx(10);
            var noteC = crotchet();
            noteC.setXOffsetPx(20);
            var noteD = crotchet();
            noteD.setXOffsetPx(30);

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(noteC);
                line.addElement(noteD);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 1, 2);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // Glissando stripped from the note before the selection
            assertThat(noteA.hasGlissando()).isFalse();
            // B and C are gone; A and D remain (plus terminal barline = 3 total)
            assertThat(line.elementCount()).isEqualTo(3);
            assertThat(line.getElement(0)).isSameAs(noteA);
            assertThat(line.getElement(1)).isSameAs(noteD);
            // D shifted left: shift = noteB.x - noteD.x = 10 - 30 = -20, so D.x = 30 - 20 = 10
            assertThat(noteD.getXOffsetPx()).isEqualTo(10);
        }

        // Regression: deleting all but the last few notes of a line used to crash.
        // SongDidChangeNotification fires synchronously from inside the modification
        // bracket, before handleDelete's trailing score.deselect() runs, so a
        // songDidChange handler (like TrillAction, via the @Handler it inherits from
        // UIAction) that reads the selection's begin/end
        // indices while enabling itself would see the pre-deletion range applied to the
        // now-shrunk line, throwing IndexOutOfBoundsException in Line.getElements. The
        // fix clears the selection before the elements are removed.
        @Test
        void testHandleDeleteAllButLastFewNotesDoesNotCrashSongDidChangeHandlers() {
            final var noteCount = 20;
            final var keepCount = 4;
            var song = new Song();
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < noteCount; i++) {
                    line.addElement(crotchet());
                }
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            // Select everything but the last keepCount notes, mirroring "delete all but
            // the last 4 notes".
            ReflectionTestHelper.selectRange(coordinator, 0, noteCount - keepCount - 1);
            var activeSelection = coordinator.getActiveSelection();

            if (activeSelection == null) {
                throw new IllegalStateException("Expected an active selection");
            }

            var state = activeSelection;
            var controller = buildController(song, coordinator, scoreMock);

            var caughtDuringNotification = new Exception[1];

            var listener = new Object() {
                @Handler
                void onSongDidChange(SongDidChangeNotification notification) {
                    try {
                        // Mirrors TrillAction.enableFromSelection reading the selection
                        // state's begin/end range while the song is changing.
                        state.canToggleTrill();
                    } catch (Exception e) {
                        caughtDuringNotification[0] = e;
                    }
                }
            };

            MessageCenter.subscribe(listener);

            try {
                controller.handleDelete();
            } finally {
                MessageCenter.unsubscribe(listener);
            }

            assertThat(caughtDuringNotification[0])
                .as("songDidChange handlers must not see a stale out-of-range selection")
                .isNull();
            assertThat(line.elementCount()).isEqualTo(keepCount + 1);
        }

        // Issue #456: the contiguous-range path (which bypasses deleteNote) must also
        // cascade-delete a breath mark immediately following the selection.
        @Test
        void testHandleDeleteContiguousRangeAlsoRemovesTrailingBreathMark() {
            // [A, breath, B] — select [A] (index 0). Expected: A and the breath mark
            // removed; B remains (shifted), plus the terminal barline.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            noteA.setXOffsetPx(0);
            var breath = ElementType.BREATH_MARK.newInstance();
            breath.setXOffsetPx(10);
            var noteB = crotchet();
            noteB.setXOffsetPx(20);

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(breath);
                line.addElement(noteB);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // noteA and the breath mark are gone; noteB remains (plus terminal barline = 2 total).
            assertThat(line.elementCount()).isEqualTo(2);
            assertThat(line.getElement(0)).isSameAs(noteB);
        }

        // Issue #456: a breath mark that does not immediately follow the selection
        // must be left intact by the contiguous-range path.
        @Test
        void testHandleDeleteContiguousRangeLeavesNonAdjacentBreathMark() {
            // [A, B, breath] — select [A] (index 0). Only A is removed; B and the
            // breath mark remain (plus the terminal barline).
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            var noteB = crotchet();
            var breath = ElementType.BREATH_MARK.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(breath);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // noteB and the breath mark remain (plus terminal barline = 3 total).
            assertThat(line.elementCount()).isEqualTo(3);
            assertThat(line.getElement(0)).isSameAs(noteB);
            assertThat(line.getElement(1)).isSameAs(breath);
        }

        // Issue #456: a multi-element contiguous selection (begin != end) followed by a
        // breath mark must extend the range to the breath mark, not just for a single cell.
        @Test
        void testHandleDeleteMultiElementRangeRemovesTrailingBreathMark() {
            // [A, B, breath, C] — select [A, B] (indices 0..1). Expected: A, B, and the
            // breath mark removed; C remains (plus the terminal barline).
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            var noteB = crotchet();
            var breath = ElementType.BREATH_MARK.newInstance();
            var noteC = crotchet();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(breath);
                line.addElement(noteC);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // Only noteC remains (plus terminal barline = 2 total).
            assertThat(line.elementCount()).isEqualTo(2);
            assertThat(line.getElement(0)).isSameAs(noteC);
        }

        // Issue #456: when the trailing breath mark is the last effective element, the
        // gap-fill shift loop is skipped (no element follows it); the breath mark must
        // still be removed.
        @Test
        void testHandleDeleteRemovesTrailingBreathMarkWhenItIsLastElement() {
            // [A, breath] — select [A] (index 0). Both A and the breath mark are removed,
            // leaving only the terminal barline.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            var breath = ElementType.BREATH_MARK.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(breath);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // Nothing but the terminal barline remains.
            assertThat(line.effectiveElementCount()).isEqualTo(0);
        }

        // Row 17: paired-grace-note at selection start falls back to deleteSelection
        @Test
        void testHandleDeleteFallsBackToDeleteSelectionWhenPairedGraceNotePrecedesSelection() {
            // [A, G(paired), B, C] — select [B, C] (indices 2..3);
            // index 2 is the host of a paired grace note (G at index 1).
            // The fallback per-element loop must delete G+B and C, leaving only A.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            // Paired grace note: GRACE_QUAVER with CONNECTED glissando
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            var noteB = crotchet();
            var noteC = crotchet();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(grace);
                line.addElement(noteB);
                line.addElement(noteC);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 2, 3);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // G (paired), B, and C all removed; A and terminal barline remain
            assertThat(line.elementCount()).isEqualTo(2);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        // Issue #456: the deleteSelection fallback path (taken when the selection starts
        // on the host of a paired grace note) must also cascade-delete a trailing breath
        // mark, via deleteNote's recursion.
        @Test
        void testHandleDeleteDeleteSelectionPathCascadesTrailingBreathMark() {
            // [A, G(paired), B, breath] — select [B] (index 2); index 2 hosts the paired
            // grace note G at index 1. The fallback deleteSelection loop removes G+B, and
            // deleteNote's recursion cascades to the breath mark. Only A and the terminal
            // barline remain.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            var noteB = crotchet();
            var breath = ElementType.BREATH_MARK.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(grace);
                line.addElement(noteB);
                line.addElement(breath);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 2, 2);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // G (paired), B, and the breath mark all removed; A and terminal barline remain.
            assertThat(line.elementCount()).isEqualTo(2);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        // Row 18: glissando selection removes the glissando from source element
        @Test
        void testHandleDeleteGlissandoSelectionRemovesGlissandoFromElement() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            noteA.setFall();
            var noteB = crotchet();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectGlissando(coordinator, 0);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            assertThat(noteA.hasFall()).isFalse();
            // Only the glissando is removed; elements are unchanged (noteA, noteB, barline)
            assertThat(line.elementCount()).isEqualTo(3);
            // The removal must be recorded as a mutation so the resulting notification triggers a
            // relayout — a fall occupies horizontal space, so deleting it must reflow the line. A
            // bare removeSlide() leaves the song unmodified and the layout stale.
            assertThat(song.isModified()).isTrue();
        }

        // Row 1 of the pair-destruction trace: deleting a paired grace note's slide un-pairs
        // it, which dissolves the automatic grace-host melisma. handleDelete must capture the
        // pairing BEFORE removeSlide() strips the glissando — afterwards there is no pairing
        // left to read, so the sync would never run and the melisma would survive its pair.
        @Test
        void testHandleDeleteSlideOnPairedGraceNoteTearsDownAutomaticMelisma() {
            // [G(paired, "om" START), H(text-less STOP carrier)] — delete G's glissando.
            var song = new Song();
            var line = song.getLine(0);
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            grace.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "om", Lyric.Extend.START);
            var host = crotchet();
            host.setLyricForVerse(1, null, false, "", Lyric.Extend.STOP);

            song.withoutMutationTracking(() -> {
                line.addElement(grace);
                line.addElement(host);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectGlissando(coordinator, 0);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            assertThat(grace.hasGlissando()).isFalse();

            // The host's carrier must be REMOVED, not merely cleared to an empty lyric: an
            // empty-lyric host is still a valid backward target for lyric navigation.
            assertThat(host.getLyricForVerse(1)).isNull();

            var graceLyric = grace.getLyricForVerse(1);

            if (graceLyric == null) {
                throw new AssertionError("the now-ordinary former grace note lost its syllable");
            }

            // Both elements survive the un-pairing, so the syllable simply stays put —
            // only its melisma goes away.
            assertThat(graceLyric.extend()).isEqualTo(Lyric.Extend.NONE);
            assertThat(graceLyric.text()).isEqualTo("om");
            assertThat(graceLyric.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        }

        // Ending selection removes the ending from the line and is recorded as a mutation
        @Test
        void testHandleDeleteEndingSelectionRemovesEndingFromLine() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            var split = ElementType.REPEAT_RIGHT.newInstance();
            var noteB = crotchet();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(split);
                line.addElement(noteB);
            });

            var ending = new Ending(noteA, noteB);
            song.withoutMutationTracking(() -> line.addRangeElement(ending));

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectEnding(coordinator, ending);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            assertThat(LineEndingSupport.findEndings(line)).doesNotContain(ending);
            // The removal must be recorded as a mutation so undo restores the ending.
            assertThat(song.isModified()).isTrue();
            // The deleted ending must not stay selected, or Delete would remain enabled while
            // pointing at an ending no longer in the line. ScoreView owns the actual clearing,
            // so at this seam the controller's contract is that it asks for it.
            verify(scoreMock).deselect();
        }

        /**
         * Builds a two-note line carrying {@code hairpin}, selects the hairpin, deletes it
         * through the controller, and returns the song so the caller can assert on it.
         */
        private Song deleteSelectedHairpin(BiFunction<StaffElement, StaffElement, Hairpin> hairpinFactory) {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            var noteB = crotchet();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
            });

            var hairpin = hairpinFactory.apply(noteA, noteB);
            song.withoutMutationTracking(() -> line.addRangeElement(hairpin));

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectHairpin(coordinator, hairpin);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            assertThat(line.getRangeElements())
                .as("the selected hairpin is gone from the line")
                .doesNotContain(hairpin);
            // Without a mutation bracket the deletion would be invisible to undo: the
            // hairpin would vanish and no undo step would bring it back.
            assertThat(song.isModified())
                .as("the removal was recorded so undo can restore it")
                .isTrue();

            return song;
        }

        // A selected crescendo is removed by Delete and the removal is undoable.
        @Test
        void testHandleDeleteHairpinSelectionRemovesCrescendoFromLine() {
            var song = deleteSelectedHairpin(Crescendo::new);

            assertThat(song.getLine(0).getCrescendos()).isEmpty();
        }

        // The diminuendo case is separate because the controller switches on the hairpin's
        // subtype: were the two branches swapped, a crescendo-only test would still pass.
        @Test
        void testHandleDeleteHairpinSelectionRemovesDiminuendoFromLine() {
            var song = deleteSelectedHairpin(Diminuendo::new);

            assertThat(song.getLine(0).getDiminuendos()).isEmpty();
        }

        // Row 19: no element/glissando selection, canDeleteLine() true → removes line
        @Test
        void testHandleDeleteRemovesSelectedLineWhenCanDeleteLine() {
            var song = new Song();
            var line = song.getLine(0);
            // Add a second line so this covers the plain multi-line removal; the
            // sole-line case is covered separately below.
            song.withoutMutationTracking(() -> song.addLine(1, new Line(song)));

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.canDeleteLine()).thenReturn(true);

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            // No element or glissando selection — mark the line itself as selected so
            // getSelectedLine() returns 0 rather than -1.
            var state = coordinator.getActiveSelection();

            if (state != null) {
                state.setLineSelected(true);
            }

            var controller = buildController(song, coordinator, scoreMock);
            var lineCountBefore = song.lineCount();
            controller.handleDelete();

            assertThat(song.lineCount()).isEqualTo(lineCountBefore - 1);
        }

        // Row 19a: deleting the sole line is legal — Song.removeLine swaps in a fresh
        // empty line, so the action succeeds and the song still has exactly one line.
        @Test
        void testHandleDeleteRemovesSoleLineAndLeavesAFreshOne() {
            var song = new Song();
            var line = song.getLine(0);

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.canDeleteLine()).thenReturn(true);

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            var state = coordinator.getActiveSelection();

            if (state != null) {
                state.setLineSelected(true);
            }

            var controller = buildController(song, coordinator, scoreMock);
            controller.handleDelete();

            assertThat(song.lineCount()).isEqualTo(1);
            assertThat(song.getLine(0)).isNotSameAs(line);
        }

        // Row 20: confirmInvalidation() returns false → deletion is aborted
        @Test
        void testHandleDeleteAbortsWhenEndingInvalidationNotConfirmed() {
            // Mock the line to report that the selection invalidates an ending,
            // and mock EndingConfirms.confirmInvalidation() to return false.
            // Verify the song's withModification is never called (no deletion occurs).
            var noteA = crotchet();
            var noteB = crotchet();

            var songMock = mock(Song.class);
            var lineMock = mock(Line.class);
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(songMock);

            var coordinatorMock = mock(SelectionCoordinator.class);
            var stateMock = mock(LineSelectionState.class);
            when(coordinatorMock.getLyricSelection()).thenReturn(null);
            when(coordinatorMock.getActiveSelection()).thenReturn(stateMock);
            when(stateMock.hasElementSelection()).thenReturn(true);
            when(stateMock.getLine()).thenReturn(lineMock);
            when(stateMock.getSelectionBegin()).thenReturn(0);
            when(stateMock.getSelectionEnd()).thenReturn(1);
            when(lineMock.getElements(0, 1)).thenReturn(List.of(noteA, noteB));
            when(lineMock.hasEndingInvalidatedByDeletion(any())).thenReturn(true);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(false);

                controller.handleDelete();

                // No removal should have happened — withModification never called
                verify(songMock, never()).withModification(any());
            }
        }
    }

    // -----------------------------------------------------------------------
    // songDidChange — stale-selection guard
    // -----------------------------------------------------------------------

    /**
     * The undo-side mirror of
     * {@code HandleDelete.testHandleDeleteAllButLastFewNotesDoesNotCrashSongDidChangeHandlers}.
     * Forward delete clears the selection itself before shrinking the line, so it never
     * reaches this guard; undo has no such courtesy. Undoing an insertion removes elements
     * while leaving the selection untouched, which leaves the selected range running past
     * the end of the line, and every later reader of that range indexes off the end and
     * throws. The {@code songDidChange} handler is the only thing standing between an undo
     * and that crash, so these tests pin the call down where it is made rather than only on
     * {@link LineSelectionState}, where the method it calls is already covered.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SongDidChangeStaleSelection {

        /** Notes added to the fixture line, ahead of the song-maintained terminal barline. */
        private static final int NOTE_COUNT = 4;

        /** Notes the simulated undo removes — enough to strand the end of the selection. */
        private static final int UNDONE_NOTE_COUNT = 2;

        private Song song;
        private Line line;
        private SelectionCoordinator coordinator;
        private LineSelectionState state;
        private MusicEditOperations operationsMock;

        @BeforeEach
        void setUp() {
            song = new Song();
            line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < NOTE_COUNT; i++) {
                    line.addElement(ElementType.CROTCHET.newInstance());
                }
            });

            coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, NOTE_COUNT - 1);

            var activeSelection = coordinator.getActiveSelection();

            if (activeSelection == null) {
                throw new IllegalStateException("Expected an active selection");
            }

            state = activeSelection;
            operationsMock = mock(MusicEditOperations.class);
        }

        private ScoreViewController buildController() {
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);

            // getMainPanel() is left returning null so the handler stops right after the
            // guard and the tuplet cache. The repaint branches past that point want the
            // whole Swing tree, and none of them bear on the selection.
            return new ScoreViewController(
                scoreMock,
                operationsMock,
                coordinator,
                mock(ClipboardManager.class)
            );
        }

        /**
         * Shrinks the line the way undoing an insertion does: elements go away and the
         * selection is left exactly as it was.
         */
        private void undoTheInsertions() {
            song.withoutMutationTracking(() -> {
                for (var i = 0; i < UNDONE_NOTE_COUNT; i++) {
                    line.removeElement(0);
                }
            });
        }

        /**
         * The mutation list is left empty on purpose: the guard runs on every song change,
         * ahead of every branch that inspects the mutations, so no particular mutation is
         * what triggers it.
         */
        private SongDidChangeNotification songDidChange() {
            return new SongDidChangeNotification(List.of(), song);
        }

        @Test
        void testSongDidChangeClearsSelectionLeftRunningPastTheEndOfTheLine() {
            undoTheInsertions();

            assertThat(state.getSelectionEnd())
                .as("the fixture must strand the selection, or this test proves nothing")
                .isGreaterThanOrEqualTo(line.elementCount());

            buildController().songDidChange(songDidChange());

            assertThat(state.hasElementSelection()).isFalse();
        }

        @Test
        void testSongDidChangeLeavesSelectionStillWithinTheLineAlone() {
            // The guard must not be a blanket "clear on every song change": that would pass
            // the test above while making the user's selection vanish after any edit.
            buildController().songDidChange(songDidChange());

            assertThat(state.hasElementSelection()).isTrue();
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(NOTE_COUNT - 1);
        }

        /**
         * The guard has to run before {@code warmTupletCache}, which is the first reader of
         * the selected range in this handler. Nothing but program order keeps the two in
         * that sequence, so reading the selection from inside the cache warm-up is the only
         * way to notice the day someone reorders them.
         */
        @Test
        void testSongDidChangeClearsTheSelectionBeforeWarmingTheTupletCache() {
            var controller = buildController();
            undoTheInsertions();

            // Stubbed only now, so the sole recorded call is the one the handler makes.
            var selectionSurvivedIntoCacheWarmUp = new boolean[1];

            when(operationsMock.canToggleTuplet()).thenAnswer(invocation -> {
                selectionSurvivedIntoCacheWarmUp[0] = state.hasElementSelection();
                return new TupletToggleInfo(false, null, false);
            });

            controller.songDidChange(songDidChange());

            // Without this the assertion below would pass vacuously if the warm-up stopped
            // being called at all.
            verify(operationsMock).canToggleTuplet();
            assertThat(selectionSurvivedIntoCacheWarmUp[0])
                .as("warmTupletCache must not be the first to read a stranded selection")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // handleCopy — rows 21, 22
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleCopy {

        @Test
        void testHandleCopyCopiesSelectedElementRangeIntoClipboard() {
            // Row 21: handleCopy with an active element selection must copy exactly
            // the selected range into the ClipboardManager.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            var noteC = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(noteC);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1);  // select noteA and noteB

            var clipboardManager = new ClipboardManager();
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            controller.handleCopy();

            // Two elements should have been copied (indices 0..1 inclusive)
            assertThat(clipboardManager.getSize()).isEqualTo(2);
            var fragment = clipboardManager.getFragment();
            assertThat(fragment).isNotNull();

            if (fragment == null) {
                return; // unreachable — NullAway flow narrowing
            }

            // Verify the copies are independent clones, not the originals
            assertThat(fragment.elements().get(0)).isNotSameAs(noteA);
            assertThat(fragment.elements().get(1)).isNotSameAs(noteB);
            // Verify the types match
            assertThat(fragment.elements().get(0).getType()).isEqualTo(noteA.getType());
            assertThat(fragment.elements().get(1).getType()).isEqualTo(noteB.getType());
        }

        @Test
        void testHandleCopyIsNoOpWhenNoActiveElementSelection() {
            var scoreMock = mock(ScoreView.class);
            var coordinatorMock = mock(SelectionCoordinator.class);
            when(coordinatorMock.getActiveSelection()).thenReturn(null);

            var clipboardManager = new ClipboardManager();
            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                clipboardManager
            );

            controller.handleCopy();

            assertThat(clipboardManager.isEmpty()).isTrue();
        }

        @Test
        void testHandleCopyIsNoOpWhenActiveSelectionHasNoElementSelection() {
            // state != null but hasElementSelection() == false (e.g. a lyric-only or caret
            // selection): the guard must prevent any copy. Without it, handleCopy would read
            // a selection range that does not exist.
            var stateMock = mock(LineSelectionState.class);
            when(stateMock.hasElementSelection()).thenReturn(false);
            var coordinatorMock = mock(SelectionCoordinator.class);
            when(coordinatorMock.getActiveSelection()).thenReturn(stateMock);

            var clipboardManager = new ClipboardManager();
            var controller = new ScoreViewController(
                mock(ScoreView.class),
                mock(MusicEditOperations.class),
                coordinatorMock,
                clipboardManager
            );

            controller.handleCopy();

            assertThat(clipboardManager.isEmpty()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // handleCut — row 23
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleCut {

        @Test
        void testHandleCutCopiesSelectionThenDeletesIt() {
            // Row 23: handleCut must copy the selected range into the clipboard
            // and then delete those elements from the line.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            var noteC = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(noteC);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1);  // select noteA and noteB

            var clipboardManager = new ClipboardManager();
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.isFocusOwner()).thenReturn(true);
            when(scoreMock.canDeleteLine()).thenReturn(false);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            // Drive handleCut via the public handlePasteboardOp with CUT operation.
            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.CUT));

            // Clipboard must contain clones of the two selected elements.
            assertThat(clipboardManager.getSize()).isEqualTo(2);
            var fragment = clipboardManager.getFragment();
            assertThat(fragment).isNotNull();

            if (fragment == null) {
                return; // unreachable — NullAway flow narrowing
            }

            assertThat(fragment.elements().get(0).getType()).isEqualTo(noteA.getType());
            assertThat(fragment.elements().get(1).getType()).isEqualTo(noteB.getType());

            // noteA and noteB must have been removed; only noteC (plus the terminal barline) remains.
            // The contiguous-range path in handleDelete removes [0..1], leaving noteC at index 0.
            assertThat(line.getElement(0)).isSameAs(noteC);
        }

        // Row 23 regression: a declined ending-invalidation confirm must leave both the
        // clipboard and the score untouched. handleCut runs the confirm first, before
        // copying or deleting anything.
        @Test
        void testHandleCutLeavesClipboardAndScoreUntouchedWhenEndingInvalidationIsDeclined() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addRangeElement(new Ending(noteA, noteB));
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1);  // select noteA and noteB

            var clipboardManager = new ClipboardManager();
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.isFocusOwner()).thenReturn(true);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(false);

                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.CUT));
            }

            // Nothing was copied — the decline must happen before handleCopy runs.
            assertThat(clipboardManager.isEmpty()).isTrue();
            // Nothing was deleted — both notes and the Ending remain.
            assertThat(line.getElement(0)).isSameAs(noteA);
            assertThat(line.getElement(1)).isSameAs(noteB);
            assertThat(line.getRangeElements()).hasSize(1);
        }

        // #614: a paste-replace deletes before it inserts, so it can discard an ending
        // the same way Cut can and confirms on the same terms. Declining must leave the
        // score, the selection, and the clipboard untouched.
        @Test
        void testHandlePasteLeavesScoreAndClipboardUntouchedWhenEndingInvalidationIsDeclined() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            var ending = new Ending(noteA, noteB);
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addRangeElement(ending);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            var pastedNote = ElementType.CROTCHET.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(List.of(pastedNote), Collections.singletonList(null), List.of()));

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.isFocusOwner()).thenReturn(true);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(false);

                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE));

                // Positive control: without this the assertions below would also pass
                // if handlePaste had bailed out before ever reaching the confirm.
                endingConfirmsMock.verify(() -> EndingConfirms.confirmInvalidation(any()));
            }

            assertThat(line.getElement(0)).isSameAs(noteA);
            assertThat(line.getElement(1)).isSameAs(noteB);
            assertThat(line.getRangeElements())
                .as("the declined confirm leaves the ending in place")
                .containsExactly(ending);
            assertThat(clipboardManager.getFragment())
                .as("declining must not consume the clipboard")
                .isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // Restatement prompt on delete, cut and paste-replace (#681)
    // -----------------------------------------------------------------------

    /**
     * Deleting, cutting or pasting over a note takes its explicit accidental away, so all three ask
     * whether the later notes restating it should go too. The question is put before anything is
     * mutated and before the clipboard is written, so Cancel leaves the score, the selection and
     * the clipboard exactly as they were.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RestatementPrompt {

        private static final int F_STAFF_POSITION = 3;

        private Song song = new Song();
        private Line line = song.getLine(0);

        @BeforeEach
        void setUpLineWithARestatement() {
            song = new Song();
            song.setLineWidthSs(UNCONSTRAINED_LINE_WIDTH_SS);
            line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                line.addElement(sharpNote());

                // The restatement: a second sharp at the same staff position, later in the song.
                line.addElement(sharpNote());
            });
        }

        private static StaffElement sharpNote() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(F_STAFF_POSITION);
            note.setAccidental(StaffElement.Accidental.SHARP);
            return note;
        }

        private ScoreViewController controllerSelectingTheFirstNote(ClipboardManager clipboardManager) {
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.isFocusOwner()).thenReturn(true);

            return new ScoreViewController(
                scoreMock, mock(MusicEditOperations.class), coordinator, clipboardManager);
        }

        private MockedStatic<OptionDialogs> answering(int answer) {
            var optionDialogs = mockStatic(OptionDialogs.class);

            optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                any(), any(), any(), anyInt(), anyInt())).thenReturn(answer);

            return optionDialogs;
        }

        @Test
        void testDeleteCancelledRemovesNothingAtAll() {
            var controller = controllerSelectingTheFirstNote(mock(ClipboardManager.class));

            try (var optionDialogs = answering(JOptionPane.CANCEL_OPTION)) {
                controller.handleDelete();

                // Positive control: without this the assertions below would also pass had
                // handleDelete bailed out before ever reaching the prompt.
                optionDialogs.verify(() -> OptionDialogs.showConfirmDialog(
                    any(), any(), any(), anyInt(), anyInt()));
            }

            assertThat(line.effectiveElementCount())
                .as("the deletion was abandoned, not just the restatement removal")
                .isEqualTo(2);
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        }

        @Test
        void testDeleteAcceptedRemovesTheNoteAndTheRestatement() {
            var controller = controllerSelectingTheFirstNote(mock(ClipboardManager.class));

            try (var ignored = answering(JOptionPane.YES_OPTION)) {
                controller.handleDelete();
            }

            assertThat(line.effectiveElementCount()).isEqualTo(1);
            assertThat(line.getElement(0).getAccidental())
                .as("the surviving note is the accepted restatement, now cleared")
                .isNull();
        }

        @Test
        void testDeleteDeclinedRemovesTheNoteAndLeavesTheRestatement() {
            var controller = controllerSelectingTheFirstNote(mock(ClipboardManager.class));

            try (var ignored = answering(JOptionPane.NO_OPTION)) {
                controller.handleDelete();
            }

            assertThat(line.effectiveElementCount()).isEqualTo(1);
            assertThat(line.getElement(0).getAccidental())
                .as("declining leaves every restatement alone")
                .isEqualTo(StaffElement.Accidental.SHARP);
        }

        @Test
        void testCutCancelledLeavesBothTheClipboardAndTheScoreUntouched() {
            // The prompt runs before the copy, exactly as the ending confirm does, so a cancelled
            // cut must not have written the clipboard either.
            var clipboardManager = new ClipboardManager();
            var controller = controllerSelectingTheFirstNote(clipboardManager);

            try (var ignored = answering(JOptionPane.CANCEL_OPTION)) {
                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.CUT));
            }

            assertThat(clipboardManager.isEmpty())
                .as("nothing was copied — the cancel happens before the copy")
                .isTrue();
            assertThat(line.effectiveElementCount()).isEqualTo(2);
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        }

        @Test
        void testPasteReplaceCancelledLeavesTheScoreAndTheClipboardUntouched() {
            // A paste over a selection deletes what it covers, so it asks the same question. Cancel
            // must leave the line exactly as it was, the way a too-narrow line already does.
            var clipboardManager = new ClipboardManager();
            var pastedNote = ElementType.CROTCHET.newInstance();
            clipboardManager.setFragment(
                new Fragment(List.of(pastedNote), Collections.singletonList(null), List.of()));

            var controller = controllerSelectingTheFirstNote(clipboardManager);

            try (var ignored = answering(JOptionPane.CANCEL_OPTION)) {
                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE));
            }

            assertThat(line.effectiveElementCount()).isEqualTo(2);
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(clipboardManager.getFragment())
                .as("cancelling must not consume the clipboard")
                .isNotNull();
        }

        @Test
        void testPasteReplaceAcceptedClearsTheRestatement() {
            var clipboardManager = new ClipboardManager();
            var pastedNote = ElementType.CROTCHET.newInstance();
            clipboardManager.setFragment(
                new Fragment(List.of(pastedNote), Collections.singletonList(null), List.of()));

            var controller = controllerSelectingTheFirstNote(clipboardManager);

            try (var optionDialogs = answering(JOptionPane.YES_OPTION)) {
                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE));
                optionDialogs.verify(() -> OptionDialogs.showConfirmDialog(
                    any(), any(), any(), anyInt(), anyInt()));
            }

            assertThat(line.getElement(1).getAccidental())
                .as("the accepted restatement went with the overwritten note's sharp")
                .isNull();
        }
    }

    // -----------------------------------------------------------------------
    // handlePaste — row 24
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandlePaste {

        /**
         * With a non-empty clipboard and no element selection, paste enters
         * paste mode (click-to-place) rather than inserting inline — so the
         * document is left untouched until the user picks an insertion point.
         */
        @Test
        void testHandlePasteWithNoSelectionEntersPasteMode() {
            // Set up a song with one line and one note, and pre-load the clipboard.
            var song = new Song();
            var line = song.getLine(0);
            var existingNote = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(existingNote));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(
                new Fragment(List.of(ElementType.QUAVER.newInstance()), Collections.singletonList(null), List.of())
            );

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.isFocusOwner()).thenReturn(true);

            // No element selection → the no-selection branch runs.
            var selectionCoordinator = mock(SelectionCoordinator.class);
            when(selectionCoordinator.getActiveSelection()).thenReturn(null);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                selectionCoordinator,
                clipboardManager
            );

            var pasteModeManager = mock(PasteModeManager.class);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(EditModeManager::getPasteModeManager).thenReturn(pasteModeManager);

                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE));

                // Paste mode was entered and nothing was inserted.
                verify(pasteModeManager).enter();
            }

            // The line must still contain only the one original note — placement
            // happens later, on click. effectiveElementCount() excludes the
            // auto-maintained terminal barline.
            assertThat(line.effectiveElementCount()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // handlePasteboardOp — rows 25, 26
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandlePasteboardOp {

        @Test
        void testHandlePasteboardOpIsNoOpWhenScoreDoesNotHaveFocus() {
            // Row 26: when score.isFocusOwner() is false, none of the handler
            // methods must be invoked — verified by checking the clipboard stays empty.
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(note));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var clipboardManager = new ClipboardManager();
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.isFocusOwner()).thenReturn(false);
            when(scoreMock.getSong()).thenReturn(song);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.COPY));

            // COPY must not have run — clipboard stays empty.
            assertThat(clipboardManager.isEmpty()).isTrue();
        }

        @Test
        void testHandlePasteboardOpRoutesCopyToHandleCopy() {
            // Row 25 (COPY branch): COPY operation is dispatched — clipboard receives content.
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(note));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var clipboardManager = new ClipboardManager();
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.isFocusOwner()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.COPY));

            assertThat(clipboardManager.isEmpty()).isFalse();
        }

        @Test
        void testHandlePasteboardOpRoutesDeleteToHandleDelete() {
            // Row 25 (DELETE branch): routes to handleDelete — element is removed from line.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.isFocusOwner()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.canDeleteLine()).thenReturn(false);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                mock(ClipboardManager.class)
            );

            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.DELETE));

            // noteA deleted; noteB remains (plus terminal barline = 2 total after delete of 1).
            assertThat(line.getElement(0)).isSameAs(noteB);
        }
    }

    // -----------------------------------------------------------------------
    // handleDeselect — row 27
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Deselect {

        @Test
        void testHandleDeselectCallsDeselectWhenScoreHasFocus() {
            // Row 27a: when the score has focus, handleDeselect must delegate to score.deselect().
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.isFocusOwner()).thenReturn(true);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );

            controller.handleDeselect(new DeselectCommand());

            verify(scoreMock).deselect();
        }

        @Test
        void testHandleDeselectIsNoOpWhenScoreDoesNotHaveFocus() {
            // Row 27b: when the score does not have focus, handleDeselect must not call deselect().
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.isFocusOwner()).thenReturn(false);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );

            controller.handleDeselect(new DeselectCommand());

            verify(scoreMock, never()).deselect();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DeleteNote {

        @Test
        void testDeleteNoteRemovesOneElement() {
            var line = lineWith(crotchet(), crotchet(), crotchet());
            var removed = ScoreViewController.deleteNote(1, line);

            assertThat(removed).isEqualTo(1);
            assertThat(line.elementCount()).isEqualTo(2);
        }

        @Test
        void testDeleteNoteRemovesPrecedingPairedGraceNote() {
            var line = lineWith(crotchet(), pairedGraceNote(), crotchet());
            var removed = ScoreViewController.deleteNote(2, line);

            assertThat(removed).isEqualTo(2);
            assertThat(line.elementCount()).isEqualTo(1);
        }

        @Test
        void testDeleteNoteDoesNotRemoveUnpairedGraceNote() {
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = lineWith(crotchet(), graceNote, crotchet());
            var removed = ScoreViewController.deleteNote(2, line);

            assertThat(removed).isEqualTo(1);
            assertThat(line.elementCount()).isEqualTo(2);
        }

        @Test
        void testDeleteNoteRemovesGlissandoFromPreviousNote() {
            var prev = crotchet();
            prev.setGlissando();
            var line = lineWith(prev, crotchet());
            ScoreViewController.deleteNote(1, line);

            assertThat(prev.hasGlissando()).isFalse();
        }

        @Test
        void testDeleteNoteShiftsSubsequentElementsWhenGraceNoteRemoved() {
            // [A, G(paired), B, C] — deleting B also removes G.
            // C should shift left to close the gap from G's position.
            var noteA = crotchet();
            noteA.setXOffsetPx(0);
            var grace = pairedGraceNote();
            grace.setXOffsetPx(8);
            var noteB = crotchet();
            noteB.setXOffsetPx(10);
            var noteC = crotchet();
            noteC.setXOffsetPx(20);
            var line = lineWith(noteA, grace, noteB, noteC);

            ScoreViewController.deleteNote(2, line);

            // firstDeletedIndex is 1 (grace), so shift = grace.x - noteC.x = 8 - 20 = -12
            // noteC.x + (-12) = 20 - 12 = 8
            assertThat(noteC.getXOffsetPx()).isEqualTo(8);
        }

        @Test
        void testSelectionLoopSkipsGraceNoteIndex() {
            // Simulates the handleDelete loop: [A, G(paired), B, C]
            // Selection covers indices 1..3 (G, B, C).
            // Without the fix, deleting B at index 2 also removes G at index 1,
            // then the loop tries index 1 which is now A — wrong element.
            var noteA = crotchet();
            var line = lineWith(noteA, pairedGraceNote(), crotchet(), crotchet());

            var selectionBegin = 1;
            var selectionEnd = 3;

            for (var i = selectionEnd; i >= selectionBegin; i--) {
                var removedCount = ScoreViewController.deleteNote(i, line);
                i -= (removedCount - 1);
            }

            // Only noteA should remain
            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        @Test
        void testSelectionLoopWithGraceNoteAtSelectionStart() {
            // [A, G(paired), B] — selection covers indices 1..2 (G and B)
            // Deleting B (index 2) also removes G (index 1), completing the selection.
            var noteA = crotchet();
            var line = lineWith(noteA, pairedGraceNote(), crotchet());

            var selectionBegin = 1;
            var selectionEnd = 2;

            for (var i = selectionEnd; i >= selectionBegin; i--) {
                var removedCount = ScoreViewController.deleteNote(i, line);
                i -= (removedCount - 1);
            }

            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        @Test
        void testSelectionLoopWithGraceNoteBeforeSelection() {
            // [G(paired), A, B] — selection covers indices 1..2 (A and B).
            // Deleting A (index 1) removes G (index 0) as an orphan too.
            var line = lineWith(pairedGraceNote(), crotchet(), crotchet());

            var selectionBegin = 1;
            var selectionEnd = 2;

            for (var i = selectionEnd; i >= selectionBegin; i--) {
                var removedCount = ScoreViewController.deleteNote(i, line);
                i -= (removedCount - 1);
            }

            assertThat(line.elementCount()).isEqualTo(0);
        }

        @Test
        void testDeleteNoteBreaksSyllableRelationWhenDeletedNoteIsTerminus() {
            var predecessor = crotchet();
            var terminus = crotchet();
            predecessor.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            terminus.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            var line = lineWith(predecessor, terminus);

            ScoreViewController.deleteNote(1, line);

            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testDeleteNotePreservesSyllableRelationWhenDeletedNoteIsChainMember() {
            var first = crotchet();
            var middle = crotchet();
            var last = crotchet();
            first.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            middle.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false));
            last.lyrics.add(new Lyric(1, "mi", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            var line = lineWith(first, middle, last);

            ScoreViewController.deleteNote(1, line);

            assertThat(first.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        /**
         * Decision 2 of the grace-host melisma design: deleting a paired grace note alone
         * hands its syllable back to the host, which is an ordinary note again.
         *
         * <p>Guards the call ordering inside {@code deleteNote}:
         * {@code adjustSyllablesForNeighborChange} must run BEFORE {@code transferLyrics},
         * because it decides whether to break the predecessor's word by reading the deleted
         * element's own lyric. Swap the two and the grace looks lyric-less by the time the
         * syllabic chain is evaluated, so "won-" is wrongly demoted from BEGIN to SINGLE
         * even though its word continues into the syllable now sitting on the host.
         */
        @Test
        void testDeleteGraceNoteHandsSyllableToHostAndKeepsPredecessorWordOpen() {
            // "won-der" split across [won(BEGIN), G(paired, "der" MIDDLE/START), H(STOP carrier)]
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "won", Lyric.Extend.NONE);
            var grace = pairedGraceNote();
            grace.setLyricForVerse(1, Lyric.Syllabic.MIDDLE, false, "der", Lyric.Extend.START);
            var host = crotchet();
            host.setLyricForVerse(1, null, false, "", Lyric.Extend.STOP);
            var line = lineWith(predecessor, grace, host);

            var removed = ScoreViewController.deleteNote(1, line);

            assertThat(removed).isEqualTo(1);
            assertThat(line.elementCount()).isEqualTo(2);

            var hostLyric = host.getLyricForVerse(1);

            if (hostLyric == null) {
                throw new AssertionError("the grace note's syllable was not handed back to the host");
            }

            assertThat(hostLyric.text()).isEqualTo("der");
            assertThat(hostLyric.syllabic()).isEqualTo(Lyric.Syllabic.MIDDLE);
            // The melisma dies with the pair — the syllable returns as an ordinary lyric.
            assertThat(hostLyric.extend()).isEqualTo(Lyric.Extend.NONE);

            var predecessorLyric = predecessor.getLyricForVerse(1);

            if (predecessorLyric == null) {
                throw new AssertionError("the predecessor's syllable was removed");
            }

            // The assertion that catches the ordering bug.
            assertThat(predecessorLyric.syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
        }

        // -------------------------------------------------------------------
        // Breath-mark cascade tests
        // -------------------------------------------------------------------

        /**
         * (a) A note followed immediately by a breath mark: deleting the note cascades
         * to also remove the breath mark, but deleteNote still returns 1 (the cascade
         * is excluded from the count).
         */
        @Test
        void testDeleteNoteFollowedByBreathMarkRemovesBothButReturnsOne() {
            var note = crotchet();
            var breathMark = breathMark();
            var line = lineWith(note, breathMark);

            var removed = ScoreViewController.deleteNote(0, line);

            // Both note and breath mark removed — only the terminal barline remains.
            assertThat(removed).isEqualTo(1);
            assertThat(line.effectiveElementCount()).isEqualTo(0);
        }

        /**
         * (b) A note NOT followed by a breath mark: the breath mark further along must
         * be retained after the deletion.
         */
        @Test
        void testDeleteNoteNotFollowedByBreathMarkLeavesBreathMarkIntact() {
            var noteA = crotchet();
            var noteB = crotchet();
            var breathMark = breathMark();
            var line = lineWith(noteA, noteB, breathMark);

            ScoreViewController.deleteNote(0, line);

            // noteA deleted; noteB and breathMark remain.
            assertThat(line.effectiveElementCount()).isEqualTo(2);
            assertThat(line.getElement(0)).isSameAs(noteB);
            assertThat(line.getElement(1)).isSameAs(breathMark);
        }

        /**
         * (c) Host note with a preceding paired grace note AND a trailing breath mark:
         * all three are gone; the return value is 2 (grace + host), not 3.
         */
        @Test
        void testDeleteNoteWithPrecedingGraceNoteAndTrailingBreathMarkRemovesAllThreeReturnsTwo() {
            var grace = pairedGraceNote();
            var host = crotchet();
            var breathMark = breathMark();
            var line = lineWith(grace, host, breathMark);

            var removed = ScoreViewController.deleteNote(1, line);

            // Grace note, host note, and breath mark all removed.
            assertThat(removed).isEqualTo(2);
            assertThat(line.effectiveElementCount()).isEqualTo(0);
        }

        /**
         * (d) Deleting a lone breath mark must not trigger any spurious extra removal.
         */
        @Test
        void testDeleteLoneBreathMarkRemovesOnlyTheBreathMark() {
            var breathMark = breathMark();
            var noteAfter = crotchet();
            var line = lineWith(breathMark, noteAfter);

            var removed = ScoreViewController.deleteNote(0, line);

            // Only the breath mark is removed; the note after stays.
            assertThat(removed).isEqualTo(1);
            assertThat(line.effectiveElementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteAfter);
        }

        private StaffElement crotchet() {
            return ElementType.CROTCHET.newInstance();
        }

        private StaffElement pairedGraceNote() {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            return grace;
        }

        private StaffElement breathMark() {
            return ElementType.BREATH_MARK.newInstance();
        }

        private Line lineWith(StaffElement... elements) {
            var line = detachedLine();

            for (var element : elements) {
                line.addElement(element);
            }

            return line;
        }
    }

    // -----------------------------------------------------------------------
    // handleSelectAllElements — rows 28, 29
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectAllElements {

        @Test
        void testHandleSelectAllElementsCallsSelectAllAndNotifiesScoreWhenActiveSelectionExists() {
            // Row 28: when an active selection exists, handleSelectAllElements must call
            // state.selectAll() and then notify the score via selectionChanged() + repaint().
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            // Start with a single-note selection so an active selection exists.
            ReflectionTestHelper.selectNote(coordinator, 0);

            var scoreMock = mock(ScoreView.class);
            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                mock(ClipboardManager.class)
            );

            controller.handleSelectAllElements(new SelectAllElementsCommand());

            // After selectAll, both notes must be within the selection.
            // Pre-condition: getActiveSelection() is non-null because we selected a note above.
            var state = coordinator.getActiveSelection();

            assertThat(state).isNotNull();
            if (state == null) return; // unreachable — NullAway flow narrowing
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
            verify(scoreMock).selectionChanged();
            verify(scoreMock).repaint();
        }

        @Test
        void testHandleSelectAllElementsSwapsLineSelectionForAnElementSelection() {
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            var state = coordinator.getActiveSelection();

            assertThat(state).isNotNull();
            if (state == null) return; // unreachable — NullAway flow narrowing

            state.setLineSelected(true);

            var scoreMock = mock(ScoreView.class);
            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                mock(ClipboardManager.class)
            );

            controller.handleSelectAllElements(new SelectAllElementsCommand());

            assertThat(state.isLineSelected()).isFalse();
            assertThat(state.getSelectionBegin()).isEqualTo(0);
            assertThat(state.getSelectionEnd()).isEqualTo(1);
            verify(scoreMock).selectionChanged();
            verify(scoreMock).repaint();
        }

        @Test
        void testHandleSelectAllElementsIsNoOpWhenNoActiveSelection() {
            var scoreMock = mock(ScoreView.class);
            var coordinatorMock = mock(SelectionCoordinator.class);
            when(coordinatorMock.getActiveSelection()).thenReturn(null);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );

            controller.handleSelectAllElements(new SelectAllElementsCommand());

            verify(scoreMock, never()).selectionChanged();
            verify(scoreMock, never()).repaint();
        }
    }

    // -----------------------------------------------------------------------
    // handleInsertLine — rows 30, 31, 32
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertLine {

        private ScoreView scoreMock;
        private Song songMock;
        private SelectionCoordinator coordinatorMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);
            songMock = mock(Song.class);
            coordinatorMock = mock(SelectionCoordinator.class);
            when(scoreMock.getSong()).thenReturn(songMock);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testHandleInsertLineInsertsAtSelectedLineIndexPlusShift() {
            // selectedLine == 1, shift == 1 → index = 1 + 1 = 2
            when(coordinatorMock.getSelectedLine()).thenReturn(1);

            controller.handleInsertLine(new InsertLineCommand(1));

            verify(songMock).addLine(eq(2), any(Line.class));
            verify(scoreMock).deselect();
        }

        @Test
        void testHandleInsertLineWithAddShiftInsertsAtEndRegardlessOfSelection() {
            // shift == ADD forces end-of-song insertion even without a line selected
            when(coordinatorMock.getSelectedLine()).thenReturn(-1);

            controller.handleInsertLine(new InsertLineCommand(InsertLineAction.ADD));

            verify(songMock).addLine(eq(InsertLineAction.ADD), any(Line.class));
            verify(scoreMock).deselect();
        }

        @Test
        void testHandleInsertLineShowsErrorWhenNoLineSelectedAndShiftIsNotAdd() {
            // No line selected and shift != ADD → error dialog (suppressed in tests),
            // addLine must NOT be called.
            when(coordinatorMock.getSelectedLine()).thenReturn(-1);

            controller.handleInsertLine(new InsertLineCommand(0));

            verify(songMock, never()).addLine(anyInt(), any(Line.class));
        }
    }

    // -----------------------------------------------------------------------
    // modeDidChange — rows 33, 34, 35
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModeDidChange {

        private ScoreView scoreMock;
        private SelectionCoordinator coordinatorMock;
        private ScoreViewController controller;

        // Actions.DURATION_ACTION_GROUP is a public static field. These tests swap in a
        // mock, so the original must be restored in tearDown to avoid leaking the mock
        // into other tests that share the JVM.
        private @org.jspecify.annotations.Nullable DurationActionGroup originalDurationActionGroup;

        // Entering edit mode always delegates to EditModeManager.makePreviewElement(), which
        // reads Actions constants this class does not initialize. Stub it out for the whole
        // nested class; its own type-derivation logic is covered by EditModeManagerTest.
        private MockedStatic<EditModeManager> editModeManagerMock;
        private StaffElement previewElementStub;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);
            coordinatorMock = mock(SelectionCoordinator.class);
            originalDurationActionGroup = Actions.DURATION_ACTION_GROUP;
            Actions.DURATION_ACTION_GROUP = mock(DurationActionGroup.class);

            previewElementStub = mock(StaffElement.class);
            editModeManagerMock = mockStatic(EditModeManager.class);
            editModeManagerMock.when(EditModeManager::makePreviewElement).thenReturn(previewElementStub);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );
        }

        // DURATION_ACTION_GROUP lacks a @Nullable annotation but is null until
        // Actions.initialize() runs (and after resetForTest()), so restoring the saved
        // original — null in a unit-test JVM — requires suppressing NullAway.
        @SuppressWarnings("NullAway")
        @AfterEach
        void tearDown() {
            editModeManagerMock.close();
            Actions.DURATION_ACTION_GROUP = originalDurationActionGroup;
        }

        private ModeDidChangeNotification notificationFor(Mode mode) {
            var action = mock(ModeAction.class);
            when(action.getMode()).thenReturn(mode);
            // Adjustment detection uses action.getActionCommand().startsWith("adjust-")
            var command = switch (mode) {
                case ADJUSTMENT -> "adjust-note-mode";
                case VERTICAL_ADJUSTMENT -> "adjust-vertical-mode";
                default -> mode.name().toLowerCase() + "-mode";
            };
            when(action.getActionCommand()).thenReturn(command);
            return new ModeDidChangeNotification(action);
        }

        @Test
        void testModeDidChangeClearsSelectionWhenModeIsNotSelect() {
            controller.modeDidChange(notificationFor(Mode.EDIT));

            verify(scoreMock).clearSelection();
        }

        /**
         * Edit entry must delegate unconditionally to {@link EditModeManager#makePreviewElement()},
         * which supplies a default type when no duration button is selected. A delete leaves both
         * duration groups deselected, so a controller that skipped the call in that case would
         * leave edit mode with no preview element and no way to ever recreate one.
         */
        @Test
        void testModeDidChangeSyncsPreviewElementOnEditEntry() {
            controller.modeDidChange(notificationFor(Mode.EDIT));

            verify(scoreMock).setPreviewElement(previewElementStub);
        }

        @Test
        void testModeDidChangeEnablesHorizontalAdjustmentForAdjustmentMode() {
            var ha = mock(HorizontalAdjustment.class);
            var va = mock(VerticalAdjustment.class);
            when(scoreMock.getHorizontalAdjustment()).thenReturn(ha);
            when(scoreMock.getVerticalAdjustment()).thenReturn(va);

            controller.modeDidChange(notificationFor(Mode.ADJUSTMENT));

            verify(ha).setEnabled(true);
            verify(va).setEnabled(false);
        }

        @Test
        void testModeDidChangeEnablesVerticalAdjustmentForVerticalAdjustmentMode() {
            var ha = mock(HorizontalAdjustment.class);
            var va = mock(VerticalAdjustment.class);
            when(scoreMock.getHorizontalAdjustment()).thenReturn(ha);
            when(scoreMock.getVerticalAdjustment()).thenReturn(va);

            controller.modeDidChange(notificationFor(Mode.VERTICAL_ADJUSTMENT));

            verify(ha).setEnabled(false);
            verify(va).setEnabled(true);
        }

    }

    /**
     * Regression: each span mutation type introduced by the ChangeType
     * migration must route through {@code hasLineLayoutMutation} and reach
     * {@code LineComponent.invalidateLayout()}. A mutation that does not
     * implement {@code LineScopedMutation} silently skips layout invalidation,
     * which is the main user-visible correctness risk of the migration.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LayoutInvalidation {

        private Line line;
        private LineComponent lineComponentMock;
        private ScoreViewController coordinator;

        @BeforeEach
        void setUp() {
            line = detachedLine();
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
            lineComponentMock = mock(LineComponent.class);

            var linePanelMock = mock(LinePanel.class);
            when(linePanelMock.getLine()).thenReturn(line);
            when(linePanelMock.getLineComponent()).thenReturn(lineComponentMock);

            var staffPanelMock = mock(StaffPanel.class);
            when(staffPanelMock.getLinePanels()).thenReturn(List.of(linePanelMock));

            var mainPanelMock = mock(MainPanel.class);
            when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

            coordinator = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        private void fireNotification(Mutation mutation) {
            coordinator.songDidChange(
                new SongDidChangeNotification(List.of(mutation), new Song())
            );
        }

        @Test
        void testBeamingAdditionInvalidatesLayout() {
            fireNotification(new BeamingAddition(line, new Beam(line.getElement(0), line.getElement(1))));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testBeamingRemovalInvalidatesLayout() {
            fireNotification(new BeamingRemoval(line, new Beam(line.getElement(0), line.getElement(1))));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTieAdditionInvalidatesLayout() {
            var tie = new Tie(line.getElement(0), line.getElement(1));
            fireNotification(new TieAddition(line, tie));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTieRemovalInvalidatesLayout() {
            var tie = new Tie(line.getElement(0), line.getElement(1));
            fireNotification(new TieRemoval(line, tie));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTupletAdditionInvalidatesLayout() {
            var tuplet = new Tuplet(line.getElement(0), line.getElement(1), 3);
            fireNotification(new TupletAddition(line, tuplet));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTupletRemovalInvalidatesLayout() {
            var tuplet = new Tuplet(line.getElement(0), line.getElement(1), 3);
            fireNotification(new TupletRemoval(line, tuplet));
            verify(lineComponentMock).invalidateLayout();
        }
    }

    // -----------------------------------------------------------------------
    // hasFullRelayoutMutation — row 37
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FullRelayoutDetection {

        private ScoreView scoreMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            // mainPanel must be non-null so songDidChange doesn't return early
            var mainPanelMock = mock(MainPanel.class);
            var staffPanelMock = mock(StaffPanel.class);
            when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);
            when(staffPanelMock.getLinePanels()).thenReturn(List.of());
            when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        private void fire(Mutation mutation) {
            controller.songDidChange(new SongDidChangeNotification(List.of(mutation), new Song()));
        }

        @Test
        void testFontChangeTriggerViewChanged() {
            fire(new FontChange(new DocumentFonts(), new DocumentFonts()));
            verify(scoreMock).viewChanged();
        }

        @Test
        void testMetadataChangeTriggerViewChanged() {
            fire(new MetadataChange(MetadataField.ATTRIBUTION, new Song().getMetadata(), new Song().getMetadata()));
            verify(scoreMock).viewChanged();
        }

        @Test
        void testLayoutChangeTriggerViewChanged() {
            fire(new LayoutChange(LayoutField.LINE_WIDTH_SS, 40.0, 50.0));
            verify(scoreMock).viewChanged();
        }

        @Test
        void testLineScopedMutationDoesNotTriggerViewChanged() {
            // BeamingRemoval is a LineScopedMutation — not a full-relayout trigger.
            // The setUp wires scoreMock → mainPanel → staffPanel → empty linePanels,
            // so no line component is invalidated and viewChanged() must not be called.
            var line = detachedLine();
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());

            fire(new BeamingRemoval(line, new Beam(line.getElement(0), line.getElement(1))));
            verify(scoreMock, never()).viewChanged();
        }
    }

    // -----------------------------------------------------------------------
    // songDidChange — target-line filtering and viewChanged (rows 38-40)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SongDidChangeHandling {

        private ScoreView scoreMock;
        private Line targetLine;
        private Line otherLine;
        private LineComponent targetLineComponentMock;
        private LineComponent otherLineComponentMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            targetLine = detachedLine();
            targetLine.addElement(ElementType.CROTCHET.newInstance());
            targetLine.addElement(ElementType.CROTCHET.newInstance());

            otherLine = detachedLine();
            otherLine.addElement(ElementType.CROTCHET.newInstance());
            otherLine.addElement(ElementType.CROTCHET.newInstance());

            targetLineComponentMock = mock(LineComponent.class);
            otherLineComponentMock = mock(LineComponent.class);

            var targetPanel = mock(LinePanel.class);
            when(targetPanel.getLine()).thenReturn(targetLine);
            when(targetPanel.getLineComponent()).thenReturn(targetLineComponentMock);

            var otherPanel = mock(LinePanel.class);
            when(otherPanel.getLine()).thenReturn(otherLine);
            when(otherPanel.getLineComponent()).thenReturn(otherLineComponentMock);

            var staffPanelMock = mock(StaffPanel.class);
            when(staffPanelMock.getLinePanels()).thenReturn(List.of(targetPanel, otherPanel));

            var mainPanelMock = mock(MainPanel.class);
            when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);
            when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testSongDidChangeInvalidatesOnlyTargetLineWhenLineScopedMutationHasNonNullTarget() {
            // Row 38: line-scoped mutation with a specific target line must invalidate
            // only that line's component, not neighboring lines.
            var beam = new Beam(targetLine.getElement(0), targetLine.getElement(1));
            controller.songDidChange(
                new SongDidChangeNotification(List.of(new BeamingAddition(targetLine, beam)), new Song())
            );

            verify(targetLineComponentMock).invalidateLayout();
            verify(otherLineComponentMock, never()).invalidateLayout();
        }

        @Test
        void testSongDidChangeCallsViewChangedForFullRelayoutMutation() {
            // Row 39: a full-relayout mutation (MetadataChange) must trigger viewChanged()
            controller.songDidChange(
                new SongDidChangeNotification(
                    List.of(new MetadataChange(MetadataField.ATTRIBUTION, new Song().getMetadata(), new Song().getMetadata())),
                    new Song()
                )
            );

            verify(scoreMock).viewChanged();
        }

        @Test
        void testSongDidChangeArmsRepaintDebounce() {
            // Row 40: every call to songDidChange (with a non-null mainPanel) must
            // retrigger the repaint debounce regardless of mutation type. Only the arming is
            // observable from here — that a further trigger extends the deadline, and that the
            // action eventually repaints, is Debounce's contract and is proved in DebounceTest.
            controller.songDidChange(
                new SongDidChangeNotification(
                    List.of(new MetadataChange(MetadataField.ATTRIBUTION, new Song().getMetadata(), new Song().getMetadata())),
                    new Song()
                )
            );

            assertThat(controller.repaintDebounce.isArmed()).isTrue();
        }

        @AfterEach
        void tearDown() {
            // songDidChange arms a real Swing timer bound to this test's mock ScoreView, and a
            // Swing timer outlives the test method that armed it.
            controller.repaintDebounce.cancel();
        }
    }

    // -----------------------------------------------------------------------
    // canToggleTuplet / warmTupletCache — rows 41-42
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TupletCaching {

        private MusicEditOperations operationsMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            operationsMock = mock(MusicEditOperations.class);

            controller = new ScoreViewController(
                mock(ScoreView.class),
                operationsMock,
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testCanToggleTupletDelegatesToOperationsWhenCacheIsNull() {
            // Row 41a: with no cache populated, canToggleTuplet() must call operations
            var expected = new TupletToggleInfo(true, null, false);
            when(operationsMock.canToggleTuplet()).thenReturn(expected);

            var result = controller.canToggleTuplet();

            assertThat(result).isSameAs(expected);
            verify(operationsMock).canToggleTuplet();
        }

        @Test
        void testCanToggleTupletReturnsCachedValueWithoutCallingOperations() {
            // Row 41b: once the cache is warm, canToggleTuplet() must not delegate
            var cached = new TupletToggleInfo(true, null, false);
            when(operationsMock.canToggleTuplet()).thenReturn(cached);

            // Warm the cache via musicSelectionDidChangeCacheTupletInfo
            controller.musicSelectionDidChangeCacheTupletInfo(
                mock(MusicSelectionDidChangeNotification.class)
            );

            // Now call canToggleTuplet() — operations should have been called
            // exactly once (during warmTupletCache), and not again here.
            controller.canToggleTuplet();
            verify(operationsMock).canToggleTuplet();
        }

        @Test
        void testTupletInfoCachePriorityExceedsHighPriority() {
            // Row 42: the cache handler must run before all HIGH_PRIORITY subscribers
            // so TupletAction reads a warm cache.
            assertThat(ScoreViewController.TUPLET_INFO_CACHE_PRIORITY)
                .isGreaterThan(Message.HIGH_PRIORITY);
        }
    }

    // -----------------------------------------------------------------------
    // prefsDidChange — row 43
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PrefsDidChange {

        private ScoreView scoreMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testPrefsDidChangeLoopPlaybackCallsSyncPlaybackPrefs() {
            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.LOOP_PLAYBACK));
            verify(scoreMock).syncPlaybackPrefs();
        }

        @Test
        void testPrefsDidChangeLoopPlaybackDoesNotCallUpdatePageLayout() {
            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.LOOP_PLAYBACK));
            verify(scoreMock, never()).updatePageLayout(anyInt());
        }

        @Test
        void testPrefsDidChangePageSizeCallsUpdatePageLayoutWhenInitialized() {
            // Row 44: PAGE_SIZE fires updatePageLayout (but not syncPlaybackPrefs)
            // when score.isInitialized() returns true.
            var song = new Song();
            when(scoreMock.isInitialized()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);

            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.PAGE_SIZE));

            verify(scoreMock).updatePageLayout(anyInt());
            verify(scoreMock, never()).syncPlaybackPrefs();
        }

        @Test
        void testPrefsDidChangePageSizeIsNoOpWhenNotInitialized() {
            // Row 44 guard: PAGE_SIZE must not call updatePageLayout if score is not initialized.
            when(scoreMock.isInitialized()).thenReturn(false);

            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.PAGE_SIZE));

            verify(scoreMock, never()).updatePageLayout(anyInt());
        }

        @Test
        void testPrefsDidChangeAllCallsBothEffects() {
            // Row 45: PrefsKey.ALL triggers both syncPlaybackPrefs and updatePageLayout.
            var song = new Song();
            when(scoreMock.isInitialized()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);

            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.ALL));

            verify(scoreMock).syncPlaybackPrefs();
            verify(scoreMock).updatePageLayout(anyInt());
        }
    }

    // -----------------------------------------------------------------------
    // textEditingDidChange — row 46
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TextEditingDidChange {

        private ScoreView scoreMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testTextEditingDidChangeDisablesKeyBindingsWhenEditing() {
            // Row 46a: editing=true → setKeyBindingsEnabled(false)
            controller.textEditingDidChange(new TextEditingDidChangeNotification(true));

            verify(scoreMock).setKeyBindingsEnabled(false);
        }

        @Test
        void testTextEditingDidChangeEnablesKeyBindingsWhenNotEditing() {
            // Row 46b: editing=false → setKeyBindingsEnabled(true)
            controller.textEditingDidChange(new TextEditingDidChangeNotification(false));

            verify(scoreMock).setKeyBindingsEnabled(true);
        }
    }

    // -----------------------------------------------------------------------
    // tryInsertFragment — lyric seam repair (task 1), repeated-paste
    // independence (task 4), and span-add ordering (task 5, the regression
    // test for the phase 4 task 3 ordering constraint).
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TryInsertFragment {

        private static final double WIDE_LINE_WIDTH_SS = 500;

        /** Notes in the destination line for the span-reconciliation fixtures. */
        private static final int DESTINATION_NOTE_COUNT = 6;

        /** An insertion index strictly inside a span covering the whole fixture line. */
        private static final int INTERIOR_INSERT_INDEX = 2;

        private static Song wideSong() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.setLineWidthSs(WIDE_LINE_WIDTH_SS));
            return song;
        }

        private static ScoreViewController buildController(Song song, ClipboardManager clipboardManager) {
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);

            return new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                clipboardManager
            );
        }

        // Pins the wiring from a captured fragment's source context through to the pasted note.
        // Every other paste test here hands the fragment null placeholders for its source
        // accidentals, so none exercises the case the reconciliation exists for. This one builds
        // the fragment with the real capture path instead.
        //
        // The copied note carries no accidental of its own but sounds sharp where it was copied
        // from, inheriting the sharp written on the note before it. Pasted somewhere nothing
        // alters that pitch, it has to arrive carrying an explicit sharp, or it sounds a semitone
        // lower than the note the user copied.
        @Test
        void testPastingANoteThatSoundedSharpInItsSourceContextMaterializesTheSharp() {
            final int sharedPositionSp = 4;
            final int unrelatedPositionSp = 2;

            var sourceSong = wideSong();
            var sourceLine = sourceSong.getLine(0);
            var sharpenedNote = ElementType.CROTCHET.newInstance();
            sharpenedNote.setStaffPosition(sharedPositionSp);
            sharpenedNote.setAccidental(StaffElement.Accidental.SHARP);
            var copiedNote = ElementType.CROTCHET.newInstance();
            copiedNote.setStaffPosition(sharedPositionSp);
            sourceSong.withoutMutationTracking(() -> {
                sourceLine.addElement(sharpenedNote);
                sourceLine.addElement(copiedNote);
            });

            var copiedPitch = copiedNote.getPitch();
            var fragment = Fragment.capture(sourceLine, 1, 1);

            // A destination whose only note sits elsewhere on the staff, so nothing there lends
            // the pasted note an accidental and the key signature leaves its pitch alone.
            var destinationSong = wideSong();
            var destinationLine = destinationSong.getLine(0);
            var unrelatedNote = ElementType.CROTCHET.newInstance();
            unrelatedNote.setStaffPosition(unrelatedPositionSp);
            destinationSong.withoutMutationTracking(() -> destinationLine.addElement(unrelatedNote));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(fragment);
            var controller = buildController(destinationSong, clipboardManager);

            destinationSong.withModification(() -> controller.tryInsertFragment(destinationLine, 1, null));

            var pastedNote = destinationLine.getElement(1);
            assertThat(pastedNote.getAccidental())
                .as("the pasted note sounded sharp where it was copied from and must still")
                .isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(pastedNote.getPitch())
                .as("a paste must not change the pitch of what was copied")
                .isEqualTo(copiedPitch);
        }

        @Test
        void testPasteIntoHyphenatedWordLeavesValidSyllabicChainsAtBothSeams() {
            // [begin(BEGIN), middle(MIDDLE), end(END)] — a 3-syllable word. Pasting
            // between begin and middle must leave begin as a standalone word (SINGLE)
            // and turn middle/end into a new, independently valid 2-syllable word.
            var song = wideSong();
            var line = song.getLine(0);
            var begin = ElementType.CROTCHET.newInstance();
            var middle = ElementType.CROTCHET.newInstance();
            var end = ElementType.CROTCHET.newInstance();
            begin.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "be", Lyric.Extend.NONE);
            middle.setLyricForVerse(1, Lyric.Syllabic.MIDDLE, false, "gin", Lyric.Extend.NONE);
            end.setLyricForVerse(1, Lyric.Syllabic.END, false, "ning", Lyric.Extend.NONE);

            song.withoutMutationTracking(() -> {
                line.addElement(begin);
                line.addElement(middle);
                line.addElement(end);
            });

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(List.of(ElementType.QUAVER.newInstance()), Collections.singletonList(null), List.of()));
            var controller = buildController(song, clipboardManager);

            song.withModification(() -> controller.tryInsertFragment(line, 1, null));

            var beginLyric = begin.getLyricForVerse(1);
            var middleLyric = middle.getLyricForVerse(1);
            var endLyric = end.getLyricForVerse(1);
            assertThat(beginLyric).isNotNull();
            assertThat(middleLyric).isNotNull();
            assertThat(endLyric).isNotNull();

            if (beginLyric == null || middleLyric == null || endLyric == null) {
                return; // unreachable — NullAway flow narrowing
            }

            assertThat(beginLyric.syllabic())
                .as("severed predecessor becomes a standalone word")
                .isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(middleLyric.syllabic())
                .as("severed successor starts a new word")
                .isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(endLyric.syllabic())
                .as("untouched far side of the new word is unchanged")
                .isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testPasteIntoMelismaLeavesValidExtendChainsAtBothSeams() {
            // [start(START), continueEl(CONTINUE), stop(STOP)] — a 3-note melisma.
            // Pasting between start and continueEl severs the chain at its head, so
            // nothing downstream of the severed START may keep a dangling
            // CONTINUE/STOP — the whole remaining chain collapses to plain notes.
            var song = wideSong();
            var line = song.getLine(0);
            var start = ElementType.CROTCHET.newInstance();
            var continueEl = ElementType.CROTCHET.newInstance();
            var stop = ElementType.CROTCHET.newInstance();
            start.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "ah", Lyric.Extend.START);
            continueEl.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            stop.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);

            song.withoutMutationTracking(() -> {
                line.addElement(start);
                line.addElement(continueEl);
                line.addElement(stop);
            });

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(List.of(ElementType.QUAVER.newInstance()), Collections.singletonList(null), List.of()));
            var controller = buildController(song, clipboardManager);

            song.withModification(() -> controller.tryInsertFragment(line, 1, null));

            var startLyric = start.getLyricForVerse(1);
            assertThat(startLyric).isNotNull();

            if (startLyric == null) {
                return; // unreachable — NullAway flow narrowing
            }

            assertThat(startLyric.extend()).as("severed predecessor's melisma is truncated")
                .isEqualTo(Lyric.Extend.NONE);
            assertThat(startLyric.text()).as("the severed head keeps its own syllable")
                .isEqualTo("ah");
            assertThat(continueEl.getLyricForVerse(1))
                .as("orphaned carrier is removed, not left as an empty lyric")
                .isNull();
            assertThat(stop.getLyricForVerse(1))
                .as("chain fully collapses past the severed head")
                .isNull();
        }

        @Test
        void testRepeatedPastesShareNoElementOrSpanInstancesAndAnchorToTheirOwnClones() {
            var song = wideSong();
            var line = song.getLine(0);
            var anchorSource = ElementType.CROTCHET.newInstance();
            var endSource = ElementType.CROTCHET.newInstance();

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(anchorSource, endSource),
                Arrays.asList(null, null),
                List.of(new Tie(anchorSource, endSource))
            ));
            var controller = buildController(song, clipboardManager);

            song.withModification(() -> controller.tryInsertFragment(line, 0, null));
            var firstSpans = List.copyOf(line.getRangeElements());
            var firstAnchor = line.getElement(0);
            var firstEnd = line.getElement(1);

            song.withModification(() -> controller.tryInsertFragment(line, 2, null));
            var secondAnchor = line.getElement(2);
            var secondEnd = line.getElement(3);

            assertThat(secondAnchor).isNotSameAs(firstAnchor);
            assertThat(secondEnd).isNotSameAs(firstEnd);

            assertThat(line.getRangeElements()).hasSize(2);
            var secondSpan = line.getRangeElements().stream()
                .filter(span -> !firstSpans.contains(span))
                .findFirst()
                .orElseThrow();

            assertThat(secondSpan.getAnchorElement()).isSameAs(secondAnchor);
            assertThat(secondSpan.getEndElement()).isSameAs(secondEnd);
        }

        @Test
        void testPastedSpanIndicesResolveAgainstTheDestinationLine() {
            var song = wideSong();
            var line = song.getLine(0);
            var existing = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(existing));

            var anchorSource = ElementType.CROTCHET.newInstance();
            var endSource = ElementType.CROTCHET.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(anchorSource, endSource),
                Arrays.asList(null, null),
                List.of(new Tie(anchorSource, endSource))
            ));
            var controller = buildController(song, clipboardManager);

            song.withModification(() -> controller.tryInsertFragment(line, 1, null));

            assertThat(line.getRangeElements()).hasSize(1);
            var span = line.getRangeElements().get(0);
            assertThat(span.getAnchorElementIndex()).isEqualTo(1);
            assertThat(span.getEndElementIndex()).isEqualTo(2);
        }

        /** Builds a {@code count}-note line with mutation tracking suspended. */
        private static List<StaffElement> fillLine(Song song, Line line, int count) {
            var notes = new ArrayList<StaffElement>();

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < count; i++) {
                    var note = ElementType.CROTCHET.newInstance();
                    notes.add(note);
                    line.addElement(note);
                }
            });

            return notes;
        }

        @Test
        void testPasteReplaceAfterAPairedGraceNotePositionsClonesWhereTheGraceNoteWas() {
            // deleteElementRange cascade-deletes the paired grace note sitting before
            // the selection, so the predecessor the spacing calculation anchored the
            // clones to is gone by the time they are inserted. The clones must land
            // where the grace note was, leaving no dead gap at the head of the line.
            var song = wideSong();
            var line = song.getLine(0);
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            var host = ElementType.CROTCHET.newInstance();
            var tail = ElementType.CROTCHET.newInstance();

            song.withoutMutationTracking(() -> {
                line.addElement(grace);
                line.addElement(host);
                line.addElement(tail);
            });

            var pasted = ElementType.CROTCHET.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(List.of(pasted), Collections.singletonList(null), List.of()));
            var controller = buildController(song, clipboardManager);

            // Replacing [host] alone: the cascade takes the grace note at index 0 too.
            song.withModification(() -> controller.tryInsertFragment(
                line, 1, new InsertionSpacingCalculator.DeletedRange(1, 1)));

            assertThat(line.getElementIndex(grace))
                .as("the paired grace note is cascade-deleted along with its host")
                .isEqualTo(-1);

            // Ground truth: the same paste into a line already reduced to what the
            // cascade leaves behind. Both must place the clone identically — the
            // cascade is not allowed to leak a gap into the spacing.
            var referenceSong = wideSong();
            var referenceLine = referenceSong.getLine(0);
            var referenceTail = ElementType.CROTCHET.newInstance();
            referenceSong.withoutMutationTracking(() -> referenceLine.addElement(referenceTail));

            var referencePasted = ElementType.CROTCHET.newInstance();
            var referenceClipboard = new ClipboardManager();
            referenceClipboard.setFragment(new Fragment(List.of(referencePasted), Collections.singletonList(null), List.of()));
            var referenceController = buildController(referenceSong, referenceClipboard);

            referenceSong.withModification(
                () -> referenceController.tryInsertFragment(referenceLine, 0, null));

            assertThat(line.getElement(0).getXOffsetPx())
                .as("the clone must sit where it would if the cascade-deleted grace note "
                    + "had never been on the line")
                .isEqualTo(referenceLine.getElement(0).getXOffsetPx());
        }

        @Test
        void testPastedEndingDoesNotNestInsideTheDestinationEnding() {
            // #614: an ending bracket covering a few extra notes is still valid
            // notation, but an ending nested inside another one never is — and no
            // other code path rejects one, so the paste must.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            var destinationEnding = new Ending(notes.getFirst(), notes.getLast());
            song.withoutMutationTracking(() -> line.addRangeElement(destinationEnding));

            // Plain notes, so nothing invalidates the destination ending on content
            // grounds and the straddle rule is the only thing acting on the endings.
            var pastedFirst = ElementType.CROTCHET.newInstance();
            var pastedSecond = ElementType.CROTCHET.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(pastedFirst, pastedSecond),
                Arrays.asList(null, null),
                List.of(new Ending(pastedFirst, pastedSecond))
            ));
            var controller = buildController(song, clipboardManager);
            var elementCountBeforePaste = line.elementCount();

            // Plain notes never trigger the confirm; without this mock an over-triggering
            // regression would call the real confirm against a bare mock(ScoreView.class)
            // and fail confusingly instead of cleanly.
            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                song.withModification(() -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));

                endingConfirmsMock.verifyNoInteractions();
            }

            assertThat(line.getRangeElements())
                .as("the destination ending wins; the pasted one is dropped")
                .containsExactly(destinationEnding);
            assertThat(line.elementCount())
                .as("the notes themselves were pasted — the assertion above is not vacuous")
                .isEqualTo(elementCountBeforePaste + 2);
        }

        @Test
        void testPastingAWholeEndingIntoAnEndingConfirmsOnItsBarlines() {
            // Copy an ending, paste it inside itself: the fragment necessarily carries
            // that ending's own barlines and repeat, so the destination ending is
            // invalidated on content grounds and the ordinary confirm covers it.
            var song = wideSong();
            var fixture = EndingLineFixture.primary(song);
            var line = fixture.line();

            var pastedAnchor = ElementType.SINGLE_BARLINE.newInstance();
            var pastedEnd = ElementType.SINGLE_BARLINE.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(
                    pastedAnchor,
                    ElementType.CROTCHET.newInstance(),
                    ElementType.REPEAT_RIGHT.newInstance(),
                    ElementType.CROTCHET.newInstance(),
                    pastedEnd
                ),
                Arrays.asList(null, null, null, null, null),
                List.of(new Ending(pastedAnchor, pastedEnd))
            ));
            var controller = buildController(song, clipboardManager);

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(true);

                song.withModification(() -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));

                endingConfirmsMock.verify(() -> EndingConfirms.confirmInvalidation(any()));
            }

            assertThat(line.getRangeElements())
                .as("destination invalidated by the pasted barlines, pasted ending dropped by the straddle rule")
                .isEmpty();
        }

        @Test
        void testDestinationEndingKeptByReconciliationSurvivesAPasteOfPlainNotes() {
            // The "kept" half of the ending rule: nothing in the pasted content
            // invalidates the ending, so it survives and simply widens to cover it.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            var destinationEnding = new Ending(notes.getFirst(), notes.getLast());
            song.withoutMutationTracking(() -> line.addRangeElement(destinationEnding));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance()),
                Arrays.asList(null, null),
                List.of()
            ));
            var controller = buildController(song, clipboardManager);

            // Plain notes never trigger the confirm; without this mock an over-triggering
            // regression would call the real confirm against a bare mock(ScoreView.class)
            // and fail confusingly instead of cleanly.
            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                song.withModification(() -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));

                endingConfirmsMock.verifyNoInteractions();
            }

            assertThat(line.getRangeElements()).containsExactly(destinationEnding);
            assertThat(destinationEnding.getEndElementIndex())
                .as("an ending bracket covering a few extra notes is still valid notation")
                .isEqualTo(DESTINATION_NOTE_COUNT + 1);
        }

        @Test
        void testDestinationEndingKeptByReconciliationIsStillDroppedByAPastedBarline() {
            // The reconciler keeps a straddled ending unconditionally; whether the
            // pasted *content* breaks it is Ending.isInvalidatedByInsertion's call,
            // made per clone inside line.addElement. An interior barline breaks it.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            var destinationEnding = new Ending(notes.getFirst(), notes.getLast());
            song.withoutMutationTracking(() -> line.addRangeElement(destinationEnding));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(ElementType.SINGLE_BARLINE.newInstance()),
                Collections.singletonList(null),
                List.of()
            ));
            var controller = buildController(song, clipboardManager);

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(true);

                song.withModification(() -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));

                endingConfirmsMock.verify(() -> EndingConfirms.confirmInvalidation(any()));
            }

            assertThat(line.getRangeElements())
                .as("a barline pasted into the ending's interior invalidates it")
                .isEmpty();
        }

        @Test
        void testPastingARightRepeatIntoTheFirstSpanOfASplitEndingConfirmsAndRemovesIt() {
            // Matrix 3.7 / the reported bug: a realistically-structured ending, whose
            // first and second spans are separated by a REPEAT_RIGHT split, must react
            // to a pasted repeat exactly as the plain-ending fixtures do.
            var song = wideSong();
            var fixture = EndingLineFixture.primary(song);
            var line = fixture.line();

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(ElementType.CROTCHET.newInstance(), ElementType.REPEAT_RIGHT.newInstance()),
                Arrays.asList(null, null),
                List.of()
            ));
            var controller = buildController(song, clipboardManager);

            // Index 2 is interior to the first sub-span (anchor 0, split 3, end 6).
            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(true);

                song.withModification(() -> controller.tryInsertFragment(line, 2, null));

                endingConfirmsMock.verify(() -> EndingConfirms.confirmInvalidation(any()));
            }

            assertThat(line.getRangeElements())
                .as("a repeat pasted into the first span invalidates the ending")
                .isEmpty();
        }

        @Test
        void testPastingARightRepeatAtTheSplitBoundaryOfAnEndingConfirmsAndRemovesIt() {
            // The reported bug: a fragment ending in a right repeat is naturally dropped
            // at the *end* of the first span, right before the split. That used to be
            // exempt, silently leaving the ending holding two adjacent right repeats.
            var song = wideSong();
            var fixture = EndingLineFixture.primary(song);
            var line = fixture.line();

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(ElementType.CROTCHET.newInstance(), ElementType.REPEAT_RIGHT.newInstance()),
                Arrays.asList(null, null),
                List.of()
            ));
            var controller = buildController(song, clipboardManager);

            // Index 3 is the split element's own index — the boundary.
            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(true);

                song.withModification(() -> controller.tryInsertFragment(line, 3, null));

                endingConfirmsMock.verify(() -> EndingConfirms.confirmInvalidation(any()));
            }

            assertThat(line.getRangeElements())
                .as("the ending cannot survive a second repeat in its first sub-span")
                .isEmpty();
        }

        @Test
        void testDecliningTheConfirmCancelsAPasteThatWouldInvalidateTheEnding() {
            // #614 / matrix 3.2: a pasted barline discards the destination ending, so it
            // asks first — the same confirm hand-inserting a barline there would show.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            var destinationEnding = new Ending(notes.getFirst(), notes.getLast());
            song.withoutMutationTracking(() -> line.addRangeElement(destinationEnding));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(ElementType.SINGLE_BARLINE.newInstance()),
                Collections.singletonList(null),
                List.of()
            ));
            var controller = buildController(song, clipboardManager);
            var outcome = new ScoreViewController.FragmentInsertOutcome[1];
            var elementCountBeforePaste = line.elementCount();

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(() -> EndingConfirms.confirmInvalidation(any())).thenReturn(false);

                song.withModification(() ->
                    outcome[0] = controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));
            }

            assertThat(outcome[0]).isEqualTo(ScoreViewController.FragmentInsertOutcome.CANCELLED);
            assertThat(line.getRangeElements())
                .as("the declined confirm leaves the ending in place")
                .containsExactly(destinationEnding);
            assertThat(line.elementCount())
                .as("nothing was inserted")
                .isEqualTo(elementCountBeforePaste);
            assertThat(clipboardManager.getFragment())
                .as("declining must not consume the clipboard")
                .isNotNull();
        }

        @Test
        void testAPasteOfPlainNotesIntoAnEndingShowsNoConfirm() {
            // The confirm is gated on the pasted content, not on merely landing inside
            // an ending — plain notes widen the bracket and must not interrupt.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            song.withoutMutationTracking(() ->
                line.addRangeElement(new Ending(notes.getFirst(), notes.getLast())));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(ElementType.CROTCHET.newInstance()),
                Collections.singletonList(null),
                List.of()
            ));
            var controller = buildController(song, clipboardManager);

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                song.withModification(() -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));

                endingConfirmsMock.verifyNoInteractions();
            }
        }

        @Test
        void testAPasteReplaceWhoseDeletionAlreadyInvalidatedTheEndingConfirmsOnlyOnce() {
            // handlePaste confirms the deletion before opening the bracket; the
            // insertion check must not ask a second time about the same doomed ending.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            var endingAnchor = notes.getFirst();
            song.withoutMutationTracking(() ->
                line.addRangeElement(new Ending(endingAnchor, notes.getLast())));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(ElementType.SINGLE_BARLINE.newInstance()),
                Collections.singletonList(null),
                List.of()
            ));
            var controller = buildController(song, clipboardManager);
            var elementCountBeforePaste = line.elementCount();

            // Deleting the ending's anchor invalidates it on its own.
            var deleteRange = new InsertionSpacingCalculator.DeletedRange(0, 0);
            var outcome = new ScoreViewController.FragmentInsertOutcome[1];

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                song.withModification(() ->
                    outcome[0] = controller.tryInsertFragment(line, 0, deleteRange));

                endingConfirmsMock.verifyNoInteractions();
            }

            // If deletionAlreadyConfirmed regressed to always-true, the interaction
            // count above would still be zero — only asserting on the resulting
            // document proves the ending was actually invalidated by the deletion,
            // not silently skipped past by a broken guard.
            assertThat(outcome[0])
                .as("the paste-replace still completes despite the pre-invalidated ending")
                .isEqualTo(ScoreViewController.FragmentInsertOutcome.INSERTED);
            assertThat(line.getElementIndex(endingAnchor))
                .as("the ending's anchor was deleted by the paste-replace")
                .isEqualTo(-1);
            assertThat(line.getElement(0).getType())
                .as("the pasted barline landed at the replaced index")
                .isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(line.getRangeElements())
                .as("the ending was removed as a consequence of its anchor's deletion")
                .isEmpty();
            assertThat(line.elementCount())
                .as("one note replaced by one pasted element — the count is unchanged")
                .isEqualTo(elementCountBeforePaste);
        }

        @Test
        void testPasteReplacingOnlyABeamGroupsInteriorRemovesTheBeamEndToEnd() {
            // The reconciler matrix covers this decision in isolation; this drives it
            // through the real delete-then-insert path, where the deletion's gap-fill
            // and index re-derivation run between the decision and the insert.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            var destinationBeam = new Beam(notes.get(1), notes.get(4));
            song.withoutMutationTracking(() -> line.addRangeElement(destinationBeam));

            var pastedFirst = ElementType.QUAVER.newInstance();
            var pastedSecond = ElementType.QUAVER.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(pastedFirst, pastedSecond),
                Arrays.asList(null, null),
                List.of(new Beam(pastedFirst, pastedSecond))
            ));
            var controller = buildController(song, clipboardManager);

            // Replace note 2 only — both of the beam's endpoints survive the deletion,
            // so nothing but the reconciliation can remove it.
            var deleteRange = new InsertionSpacingCalculator.DeletedRange(
                INTERIOR_INSERT_INDEX, INTERIOR_INSERT_INDEX);

            song.withModification(
                () -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, deleteRange));

            assertThat(line.getRangeElements())
                .as("a partially replaced beam group loses its beam, and the pasted beam with it")
                .isEmpty();
            assertThat(line.getElement(1))
                .as("the beam's surviving anchor note is untouched")
                .isSameAs(notes.get(1));
            assertThat(line.getElementIndex(notes.get(INTERIOR_INSERT_INDEX)))
                .as("the replaced note is gone from the line")
                .isEqualTo(-1);
            // The inserted elements are fresh clones — instantiate() never inserts the
            // stored fragment's own elements — so identity is checked negatively.
            assertThat(line.getElement(INTERIOR_INSERT_INDEX))
                .isNotSameAs(pastedFirst)
                .isNotSameAs(notes.get(INTERIOR_INSERT_INDEX));
            assertThat(line.getElement(INTERIOR_INSERT_INDEX + 1)).isNotSameAs(pastedSecond);
        }

        @Test
        void testPasteReplacingAWholeBeamGroupKeepsThePastedBeam() {
            // The complementary case: the selection covers the destination beam
            // exactly, so it dies with the deletion and the pasted beam replaces it.
            var song = wideSong();
            var line = song.getLine(0);
            var notes = fillLine(song, line, DESTINATION_NOTE_COUNT);
            song.withoutMutationTracking(() -> line.addRangeElement(new Beam(notes.get(1), notes.get(4))));

            var pastedFirst = ElementType.QUAVER.newInstance();
            var pastedSecond = ElementType.QUAVER.newInstance();
            var pastedBeam = new Beam(pastedFirst, pastedSecond);
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(pastedFirst, pastedSecond), Arrays.asList(null, null), List.of(pastedBeam)));
            var controller = buildController(song, clipboardManager);

            var deleteRange = new InsertionSpacingCalculator.DeletedRange(1, 4);

            song.withModification(() -> controller.tryInsertFragment(line, 1, deleteRange));

            assertThat(line.getRangeElements()).hasSize(1);
            var survivingBeam = line.getRangeElements().getFirst();
            assertThat(survivingBeam.getAnchorElement())
                .as("the surviving beam is the pasted one, anchored to its own clones")
                .isSameAs(line.getElement(1));
            assertThat(survivingBeam.getEndElementIndex()).isEqualTo(2);
        }

        @Test
        void testPasteInsideABeamGroupRemovesTheBeamAndDropsThePastedBeam() {
            // #614: without reconciliation the destination beam's endpoints both
            // survive, so it silently stretches over the pasted notes.
            var song = wideSong();
            var line = song.getLine(0);
            var beamStart = ElementType.QUAVER.newInstance();
            var beamMiddle = ElementType.QUAVER.newInstance();
            var beamEnd = ElementType.QUAVER.newInstance();
            var destinationBeam = new Beam(beamStart, beamEnd);

            song.withoutMutationTracking(() -> {
                line.addElement(beamStart);
                line.addElement(beamMiddle);
                line.addElement(beamEnd);
                line.addRangeElement(destinationBeam);
            });

            var pastedFirst = ElementType.QUAVER.newInstance();
            var pastedSecond = ElementType.QUAVER.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(pastedFirst, pastedSecond),
                Arrays.asList(null, null),
                List.of(new Beam(pastedFirst, pastedSecond))
            ));
            var controller = buildController(song, clipboardManager);

            song.withModification(() -> controller.tryInsertFragment(line, 1, null));

            assertThat(line.getRangeElements())
                .as("both the straddled destination beam and the pasted beam are gone")
                .isEmpty();
        }

        @Test
        void testPasteInsideAHairpinKeepsTheDestinationHairpinAndDropsThePastedOne() {
            var song = wideSong();
            var line = song.getLine(0);
            var hairpinStart = ElementType.CROTCHET.newInstance();
            var hairpinMiddle = ElementType.CROTCHET.newInstance();
            var hairpinEnd = ElementType.CROTCHET.newInstance();
            var destinationHairpin = new Crescendo(hairpinStart, hairpinEnd);

            song.withoutMutationTracking(() -> {
                line.addElement(hairpinStart);
                line.addElement(hairpinMiddle);
                line.addElement(hairpinEnd);
                line.addRangeElement(destinationHairpin);
            });

            var pastedFirst = ElementType.CROTCHET.newInstance();
            var pastedSecond = ElementType.CROTCHET.newInstance();
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                List.of(pastedFirst, pastedSecond),
                Arrays.asList(null, null),
                List.of(new Crescendo(pastedFirst, pastedSecond))
            ));
            var controller = buildController(song, clipboardManager);

            song.withModification(() -> controller.tryInsertFragment(line, 1, null));

            assertThat(line.getRangeElements()).containsExactly(destinationHairpin);
            assertThat(destinationHairpin.getEndElementIndex())
                .as("the surviving hairpin now covers the pasted notes too")
                .isEqualTo(4);
        }

        @Test
        void testCutThenPasteBackReconstructsEquivalentNotationFromFreshClones() {
            // Cut-populates-clipboard and paste-from-a-hand-built-fragment are each
            // tested in isolation elsewhere. This drives a real cut and pastes the
            // RESULTING clipboard fragment straight back, proving the two halves
            // compose into a faithful round trip — not just that each half works alone.
            var song = wideSong();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            var noteC = ElementType.CROTCHET.newInstance();
            var noteD = ElementType.CROTCHET.newInstance();
            noteA.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "la", Lyric.Extend.NONE);

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(noteC);
                line.addElement(noteD);
                line.addRangeElement(new Tie(noteA, noteB));
            });

            var elementCountBeforeCut = line.effectiveElementCount();

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1); // select noteA and noteB

            var clipboardManager = new ClipboardManager();
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.isFocusOwner()).thenReturn(true);
            when(scoreMock.canDeleteLine()).thenReturn(false);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            // The real cut: copies noteA/noteB (plus their tie and noteA's lyric)
            // into the clipboard, then deletes them from the line.
            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.CUT));

            var cutFragment = clipboardManager.getFragment();
            assertThat(cutFragment).isNotNull();

            if (cutFragment == null) {
                return; // unreachable — NullAway flow narrowing
            }

            var cutCount = cutFragment.elements().size();

            // Paste the cut fragment straight back at the same index.
            var outcome = song.withModificationResult(() -> controller.tryInsertFragment(line, 0, null));

            assertThat(outcome).isEqualTo(ScoreViewController.FragmentInsertOutcome.INSERTED);
            assertThat(line.effectiveElementCount())
                .as("a cut followed by pasting the same fragment back is size-neutral")
                .isEqualTo(elementCountBeforeCut);

            var restoredAnchor = line.getElement(0);
            var restoredEnd = line.getElement(1);

            assertThat(restoredAnchor.getType())
                .as("the pasted note has the same element type as the original")
                .isEqualTo(noteA.getType());
            assertThat(restoredEnd.getType()).isEqualTo(noteB.getType());
            assertThat(restoredAnchor)
                .as("the pasted note is a fresh clone — neither the original nor the "
                    + "intermediate clone stored on the clipboard")
                .isNotSameAs(noteA)
                .isNotSameAs(cutFragment.elements().get(0));
            assertThat(restoredEnd)
                .isNotSameAs(noteB)
                .isNotSameAs(cutFragment.elements().get(1));

            var restoredLyric = restoredAnchor.getLyricForVerse(1);
            assertThat(restoredLyric).isNotNull();

            if (restoredLyric == null) {
                return; // unreachable — NullAway flow narrowing
            }

            assertThat(restoredLyric.text()).as("the lyric text survived the round trip").isEqualTo("la");
            assertThat(restoredLyric.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);

            assertThat(line.getRangeElements()).hasSize(1);
            var restoredSpan = line.getRangeElements().getFirst();
            assertThat(restoredSpan.getAnchorElement())
                .as("the tie survived the round trip, re-anchored to the fresh clones")
                .isSameAs(restoredAnchor);
            assertThat(restoredSpan.getEndElement()).isSameAs(restoredEnd);

            assertThat(line.getElement(cutCount))
                .as("the untouched survivor is back where it started, after the pasted pair")
                .isSameAs(noteC);
            assertThat(line.getElement(cutCount + 1)).isSameAs(noteD);
        }

        @Test
        void testPasteFragmentIntoAGenuinelyEmptyLineInsertsAtTheOnlyValidIndex() {
            // insertIndex == 0 == effectiveElementCount(): there is no predecessor for
            // adjustSyllablesForNeighborChange(insertAt - 1, ...) to look up (index -1)
            // and no successor for the trailing-shift/span-reconciliation logic to
            // straddle. The spacing math for this boundary is covered at the calculator
            // unit level; this drives the full controller flow — lyric-seam repair and
            // span reconciliation included — through it.
            var song = wideSong();
            var line = song.getLine(0);
            assertThat(line.effectiveElementCount())
                .as("precondition: the destination line is genuinely empty")
                .isEqualTo(0);

            var pastedAnchor = ElementType.CROTCHET.newInstance();
            var pastedEnd = ElementType.CROTCHET.newInstance();
            pastedAnchor.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "be", Lyric.Extend.NONE);
            pastedEnd.setLyricForVerse(1, Lyric.Syllabic.END, false, "gin", Lyric.Extend.NONE);
            var pastedElements = List.of(pastedAnchor, pastedEnd);

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(
                pastedElements,
                Collections.nCopies(pastedElements.size(), null),
                List.of(new Tie(pastedAnchor, pastedEnd))
            ));
            var controller = buildController(song, clipboardManager);

            var outcome = song.withModificationResult(() -> controller.tryInsertFragment(line, 0, null));

            assertThat(outcome).isEqualTo(ScoreViewController.FragmentInsertOutcome.INSERTED);
            assertThat(line.effectiveElementCount())
                .as("both pasted notes landed on the previously empty line")
                .isEqualTo(pastedElements.size());

            var firstLyric = line.getElement(0).getLyricForVerse(1);
            var secondLyric = line.getElement(1).getLyricForVerse(1);
            assertThat(firstLyric).isNotNull();
            assertThat(secondLyric).isNotNull();

            if (firstLyric == null || secondLyric == null) {
                return; // unreachable — NullAway flow narrowing
            }

            assertThat(firstLyric.syllabic())
                .as("no predecessor to sever against — the seam repair must be a no-op here")
                .isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(secondLyric.syllabic())
                .as("no successor to sever against either")
                .isEqualTo(Lyric.Syllabic.END);

            assertThat(line.getRangeElements()).hasSize(1);
            var pastedSpan = line.getRangeElements().getFirst();
            assertThat(pastedSpan.getAnchorElement())
                .as("the pasted tie survives reconciliation with no destination span to compete with")
                .isSameAs(line.getElement(0));
            assertThat(pastedSpan.getEndElement()).isSameAs(line.getElement(1));
            assertThat(line.getElement(0))
                .as("the inserted note is a fresh clone, not the fragment's own stored element")
                .isNotSameAs(pastedAnchor);
            assertThat(line.getElement(1)).isNotSameAs(pastedEnd);
        }
    }

    // -----------------------------------------------------------------------
    // handlePaste — paste-replace atomicity (task 2) and empty/null-fragment
    // no-op (task 8)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandlePasteAtomicityAndEmptyFragment {

        private static final double WIDE_LINE_WIDTH_SS = 500;

        // Narrow enough that even a single pasted quaver cannot fit.
        private static final double NARROW_LINE_WIDTH_SS = 0;

        private static ScoreViewController buildController(
            Song song, SelectionCoordinator coordinator, ScoreView scoreMock, ClipboardManager clipboardManager) {

            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.isFocusOwner()).thenReturn(true);

            return new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );
        }

        private static int countSongDidChangeNotifications(Song song, Runnable body) {
            var count = new int[1];
            var listener = new Object() {
                @Handler
                void onSongDidChange(SongDidChangeNotification notification) {
                    count[0]++;
                }
            };

            MessageCenter.subscribe(listener);

            try {
                body.run();
            } finally {
                MessageCenter.unsubscribe(listener);
            }

            return count[0];
        }

        @Test
        void testPasteReplaceProducesExactlyOneModificationBracket() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.setLineWidthSs(WIDE_LINE_WIDTH_SS));
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0); // select noteA

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(List.of(ElementType.QUAVER.newInstance()), Collections.singletonList(null), List.of()));

            var controller = buildController(song, coordinator, mock(ScoreView.class), clipboardManager);

            var notificationCount = countSongDidChangeNotifications(song, () ->
                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE)));

            assertThat(notificationCount).isEqualTo(1);
            // noteA was replaced by the pasted quaver; noteB survives, shifted after it.
            assertThat(line.effectiveElementCount()).isEqualTo(2);
            assertThat(line.getElement(1)).isSameAs(noteB);
        }

        @Test
        void testOverflowBlockedPasteProducesNoModificationBracketAndLeavesSelectionIntact() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.setLineWidthSs(NARROW_LINE_WIDTH_SS));
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(noteA));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(List.of(ElementType.QUAVER.newInstance()), Collections.singletonList(null), List.of()));

            var controller = buildController(song, coordinator, mock(ScoreView.class), clipboardManager);

            var notificationCount = countSongDidChangeNotifications(song, () ->
                controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE)));

            assertThat(notificationCount).isEqualTo(0);
            assertThat(line.effectiveElementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteA);
            assertThat(coordinator.getActiveSelection()).isNotNull();
        }

        @Test
        void testHandlePasteWithNullFragmentIsNoOp() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(noteA));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var clipboardManager = new ClipboardManager(); // empty — no fragment ever set
            var controller = buildController(song, coordinator, mock(ScoreView.class), clipboardManager);

            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE));

            assertThat(line.effectiveElementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        @Test
        void testHandlePasteWithEmptyFragmentIsNoOp() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(noteA));

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(new Fragment(List.of(), List.of(), List.of()));
            var controller = buildController(song, coordinator, mock(ScoreView.class), clipboardManager);

            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE));

            assertThat(line.effectiveElementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }
    }

}
