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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;

import net.engio.mbassy.listener.Handler;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Attachment;
import songscribe.dom.Beam;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.hit.HitTarget;
import songscribe.layout.NoteGeometry;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.undo.UndoTestSupport;

/**
 * Tests for {@code ScoreViewController.deleteSelectedTarget} — Backspace/Delete with a single
 * notation object directly selected (#682).
 * <p>
 * Split out of {@code ScoreViewControllerTest}, which keeps the range-delete, pasteboard,
 * restatement and message-handler tests. Splitting the production class is tracked separately
 * as issue #733.
 */
class ScoreViewControllerDeleteTargetTest extends UnitTest {

    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int NO_DOTS = 0;
    private static final String ANNOTATION_TEXT = "dolce";

    /** The staff position every accidental fixture writes its notes at. */
    private static final int F_STAFF_POSITION = 3;

    /** A line too narrow to hold anything, so the accidental fit gate has to refuse. */
    private static final double CRAMPED_LINE_WIDTH_SS = 1.0;

    /**
     * Measuring a projected line reads accidental widths out of a static table that has to be
     * built first. Without this the class passes only when some earlier test class happens to
     * have built it, and fails whenever it runs alone.
     */
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    /**
     * The accidental arm resolves its action from {@code Actions.ACCIDENTAL_ACTION_GROUP}, a
     * deferred-init field populated at application start. Initialize it here with a minimal mock
     * frame rather than depending on some earlier test class having done so in the shared JVM —
     * that hidden ordering coupling breaks the moment this class runs in a fresh JVM.
     * {@code UnitTest}'s teardown unsubscribes the actions again.
     */
    @BeforeEach
    void initializeActions() {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(ScoreView.class);
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockFrame.getRootPane()).thenReturn(mockRootPane);
        when(mockFrame.requireScoreView()).thenReturn(mockScore);
        when(mockFrame.getScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        Actions.initialize(mockFrame);
    }

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    private static ScoreViewController buildController(
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

    /** A score view reporting {@code song}, the one collaborator every fixture here needs. */
    private static ScoreView scoreViewFor(Song song) {
        var scoreMock = mock(ScoreView.class);
        when(scoreMock.getSong()).thenReturn(song);
        return scoreMock;
    }

    /** A song whose first line holds two crotchets, the smallest line any span can span. */
    private static Song twoNoteSong() {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.addElement(crotchet());
            line.addElement(crotchet());
        });

        return song;
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

        var scoreMock = scoreViewFor(song);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        var controller = buildController(coordinator, scoreMock);

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

        var scoreMock = scoreViewFor(song);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        ReflectionTestHelper.selectGlissando(coordinator, 0);
        var controller = buildController(coordinator, scoreMock);

        controller.handleDelete();

        assertThat(grace.hasGlissando()).isFalse();

        // The host's carrier must be REMOVED, not merely cleared to an empty lyric: an
        // empty-lyric host is still a valid backward target for lyric navigation.
        assertThat(host.getLyricForVerse(1)).isNull();

        var graceLyric = grace.getLyricForVerse(1);

        assertThat(graceLyric).as("the now-ordinary former grace note lost its syllable").isNotNull();

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
        song.withoutMutationTracking(() -> line.addSpan(ending));

        var scoreMock = scoreViewFor(song);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        ReflectionTestHelper.selectEnding(coordinator, ending);
        var controller = buildController(coordinator, scoreMock);

        controller.handleDelete();

        assertThat(line.findEndings()).doesNotContain(ending);
        // The removal must be recorded as a mutation so undo restores the ending.
        assertThat(song.isModified()).isTrue();
        // The deleted ending must not stay selected, or Delete would remain enabled while
        // pointing at an ending no longer in the line. ScoreView owns the actual clearing,
        // so at this seam the controller's contract is that it asks for it.
        verify(scoreMock).deselect();
    }

    /**
     * The kinds Delete must leave alone. A note is selected as an index range rather than a
     * target, so the range branch of {@code handleDelete} — not this one — deletes it; a
     * grace-note glissando is not selectable at all.
     */
    private static Stream<Named<Function<StaffElement, HitTarget>>> undeletableTargets() {
        return Stream.of(
            Named.of("element", HitTarget.Element::new),
            Named.of("grace glissando", HitTarget.GraceGlissando::new));
    }

    /**
     * Selects the target {@code targetFactory} builds over a two-note line, presses Delete with
     * the whole-line delete deliberately available, and asserts nothing was removed.
     * <p>
     * The staff line is what is really under test: when no branch of {@code handleDelete}
     * claims the keystroke it falls through to deleting the line, so a target the controller
     * cannot act on must still be claimed and quietly find nothing to remove.
     */
    private static void assertHandleDeleteRemovesNothing(
        Function<? super Line, ? extends HitTarget> targetFactory
    ) {
        var song = twoNoteSong();
        var line = song.getLine(0);
        var elementCountBefore = line.elementCount();
        var lineCountBefore = song.lineCount();

        var scoreMock = scoreViewFor(song);
        when(scoreMock.canDeleteLine()).thenReturn(true);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        ReflectionTestHelper.selectTarget(coordinator, targetFactory.apply(line));
        var controller = buildController(coordinator, scoreMock);

        controller.handleDelete();

        assertThat(song.lineCount())
            .as("the staff line must survive").isEqualTo(lineCountBefore);
        assertThat(line.elementCount())
            .as("no element may be removed").isEqualTo(elementCountBefore);
        // The span arms open their bracket unconditionally and rely on Line.removeTie and its
        // siblings noticing the span is absent. Element and line counts look identical whether
        // that inner guard fires or not — only this sees the undo step a weakened guard would
        // leave behind, naming a removal the user would find undoes nothing.
        assertThat(song.isModified())
            .as("a target with nothing to remove records no mutation").isFalse();
    }

    /**
     * Delete with one of these selected must do nothing at all — in particular it must not
     * fall through to deleting the whole staff line, which is what the next branch of
     * handleDelete does when nothing else claims the keystroke. Losing a line because the
     * user pressed Delete with a note's target selected would be the worst outcome here.
     */
    @ParameterizedTest
    @MethodSource("undeletableTargets")
    void testHandleDeleteLeavesTheLineIntactForKindsItDoesNotDelete(
        Function<? super StaffElement, ? extends HitTarget> makeTarget
    ) {
        assertHandleDeleteRemovesNothing(line -> makeTarget.apply(line.getElement(0)));
    }

    /**
     * A selection can outlive the span it names — an undo that removed the span leaves the
     * target pointing at nothing. Delete must still be claimed by the target branch and
     * quietly find nothing to remove; falling through to the whole-line delete would cost the
     * user a staff line for pressing Delete on a decoration that is merely stale.
     */
    @Test
    void testHandleDeleteOnATargetWhoseSpanIsNotOnTheLineRemovesNothing() {
        // The tie is deliberately never added to the line.
        assertHandleDeleteRemovesNothing(
            line -> new HitTarget.Tie(new Tie(line.getElement(0), line.getElement(1))));
    }

    /**
     * An articulation whose owner element has left the line — the note was deleted while its
     * articulation stayed selected — resolves to no index, so there is nothing to modify.
     */
    @Test
    void testHandleDeleteOnAnArticulationWhoseOwnerIsNotOnTheLineRemovesNothing() {
        assertHandleDeleteRemovesNothing(line -> {
            var offLineNote = crotchet();
            var articulation = new Articulation(offLineNote, ArticulationType.STACCATO);
            offLineNote.addArticulation(articulation);
            return new HitTarget.Articulation(articulation);
        });
    }

    /** An attachment that never got an owner element has no element to delete it from. */
    @Test
    void testHandleDeleteOnAnAttachmentWithNoOwnerRemovesNothing() {
        assertHandleDeleteRemovesNothing(
            _ -> new HitTarget.Attachment(new AnnotationAttachment(new Annotation(ANNOTATION_TEXT))));
    }

    /**
     * Builds a two-note line carrying the span {@code spanFactory} makes, selects that span
     * through the target {@code targetFactory} wraps it in, deletes it through the controller,
     * and asserts both halves of what every span deletion owes: the span is gone from the line,
     * and the removal went through a modification bracket so undo can bring it back.
     *
     * @return the song, so a caller can assert on the kind-specific query as well
     */
    private static <S extends Span> Song deleteSelectedSpan(
        BiFunction<? super StaffElement, ? super StaffElement, ? extends S> spanFactory,
        Function<? super S, ? extends HitTarget> targetFactory
    ) {
        var song = twoNoteSong();
        var line = song.getLine(0);
        var span = spanFactory.apply(line.getElement(0), line.getElement(1));

        song.withoutMutationTracking(() -> line.addSpan(span));

        var scoreMock = scoreViewFor(song);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        ReflectionTestHelper.selectTarget(coordinator, targetFactory.apply(span));
        var controller = buildController(coordinator, scoreMock);

        controller.handleDelete();

        assertThat(line.getSpans())
            .as("the selected span is gone from the line")
            .doesNotContain(span);
        // Without a mutation bracket the deletion would be invisible to undo: the span
        // would vanish and no undo step would bring it back.
        assertThat(song.isModified())
            .as("the removal was recorded so undo can restore it")
            .isTrue();

        return song;
    }

    /** A triplet over the whole of a two-note line. */
    private static Tuplet triplet(StaffElement anchor, StaffElement end) {
        return new Tuplet(
            anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
    }

    // A selected crescendo is removed by Delete and the removal is undoable.
    @Test
    void testHandleDeleteHairpinSelectionRemovesCrescendoFromLine() {
        var song = deleteSelectedSpan(Crescendo::new, HitTarget.Hairpin::new);

        assertThat(song.getLine(0).getCrescendos()).isEmpty();
    }

    // The diminuendo case is separate because the controller switches on the hairpin's
    // subtype: were the two branches swapped, a crescendo-only test would still pass.
    @Test
    void testHandleDeleteHairpinSelectionRemovesDiminuendoFromLine() {
        var song = deleteSelectedSpan(Diminuendo::new, HitTarget.Hairpin::new);

        assertThat(song.getLine(0).getDiminuendos()).isEmpty();
    }

    @Test
    void testHandleDeleteTieSelectionRemovesTieFromLine() {
        deleteSelectedSpan(Tie::new, HitTarget.Tie::new);
    }

    @Test
    void testHandleDeleteBeamSelectionRemovesBeamFromLine() {
        deleteSelectedSpan(Beam::new, HitTarget.Beam::new);
    }

    @Test
    void testHandleDeleteTupletSelectionRemovesTupletFromLine() {
        deleteSelectedSpan(
            ScoreViewControllerDeleteTargetTest::triplet, HitTarget.Tuplet::new);
    }

    // A trill goes out through the generic removeSpan, whose unlabeled op-name would read
    // "Ending" — the deletion itself is what this asserts; the label is asserted separately.
    @Test
    void testHandleDeleteTrillSelectionRemovesTrillFromLine() {
        deleteSelectedSpan(Trill::new, HitTarget.Trill::new);
    }

    /**
     * A cross-line tie is one {@link Tie} object in both lines' spans lists (#493). Deleting it
     * must clear both halves under a single removal: a removal that reached only the clicked
     * line would leave the far line holding an unreachable half-tie that no click could select
     * and no undo would account for.
     */
    @Test
    void testHandleDeleteRemovesACrossLineTieFromBothLinesAndOneUndoRestoresIt() {
        var song = new Song();
        var firstLine = song.getLine(0);
        var secondLine = new Line(song);
        var anchor = crotchet();
        var end = crotchet();
        var tie = new Tie(anchor, end);

        song.withoutMutationTracking(() -> {
            firstLine.addElement(anchor);
            song.addLine(secondLine);
            secondLine.addElement(end);
            firstLine.addTie(tie);
        });

        assertThat(firstLine.getSpans()).containsOnlyOnce(tie);
        assertThat(secondLine.getSpans()).containsOnlyOnce(tie);

        var scoreMock = scoreViewFor(song);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(firstLine);
        ReflectionTestHelper.selectTarget(coordinator, new HitTarget.Tie(tie));
        var controller = buildController(coordinator, scoreMock);

        var batch = UndoTestSupport.captureBatch(song, controller::handleDelete);

        assertThat(firstLine.getSpans())
            .as("the clicked line's half").doesNotContain(tie);
        assertThat(secondLine.getSpans())
            .as("the far line's half").doesNotContain(tie);

        // Two removals would need two undo presses, and the second would re-add the tie twice.
        assertThat(batch).filteredOn(TieRemoval.class::isInstance).hasSize(1);

        UndoTestSupport.replayUndo(UndoTestSupport.scoreViewFor(song), batch);

        assertThat(firstLine.getSpans())
            .as("one undo restored the clicked line's half").containsOnlyOnce(tie);
        assertThat(secondLine.getSpans())
            .as("one undo restored the far line's half").containsOnlyOnce(tie);
    }

    /** Selects {@code target} on {@code line} and presses Delete. */
    private static void handleDeleteWithTargetSelected(Song song, Line line, HitTarget target) {
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        ReflectionTestHelper.selectTarget(coordinator, target);
        buildController(coordinator, scoreViewFor(song)).handleDelete();
    }

    /**
     * A note carrying two articulations is what separates "removed the selected one" from
     * "cleared the list" — with a single articulation on the note both behaviors look alike.
     */
    @Test
    void testHandleDeleteArticulationSelectionRemovesOnlyTheSelectedArticulation() {
        var song = twoNoteSong();
        var line = song.getLine(0);
        var note = line.getElement(0);
        var staccato = new Articulation(note, ArticulationType.STACCATO);
        var accent = new Articulation(note, ArticulationType.ACCENT);

        song.withoutMutationTracking(() -> {
            note.addArticulation(staccato);
            note.addArticulation(accent);
        });

        handleDeleteWithTargetSelected(song, line, new HitTarget.Articulation(staccato));

        assertThat(note.getArticulations())
            .as("only the selected articulation is gone").containsExactly(accent);
        assertThat(song.isModified())
            .as("the removal was recorded so undo can restore it").isTrue();
    }

    /**
     * Hangs the attachment {@code attachmentFactory} makes on the first note of a two-note
     * line, deletes it through the controller, and asserts both halves of what every
     * attachment deletion owes: the attachment is gone from its owner, and the removal went
     * through a modification bracket so undo can bring it back.
     *
     * @return the owner element, so a caller can assert on the kind-specific query as well
     */
    private static StaffElement deleteSelectedAttachment(
        Function<? super StaffElement, ? extends Attachment> attachmentFactory
    ) {
        var song = twoNoteSong();
        var line = song.getLine(0);
        var owner = line.getElement(0);
        var attachment = attachmentFactory.apply(owner);

        song.withoutMutationTracking(() -> owner.addAttachment(attachment));

        handleDeleteWithTargetSelected(song, line, new HitTarget.Attachment(attachment));

        assertThat(owner.getAttachments())
            .as("the selected attachment is gone from its owner")
            .doesNotContain(attachment);
        assertThat(song.isModified())
            .as("the removal was recorded so undo can restore it")
            .isTrue();

        return owner;
    }

    // A fermata is not held in the attachment list under its own removal API — it goes out
    // through setFermata(false) — so the list assertion alone would not prove it is gone.
    @Test
    void testHandleDeleteAttachmentSelectionRemovesFermataFromElement() {
        var owner = deleteSelectedAttachment(FermataAttachment::new);

        assertThat(owner.findAttachment(FermataAttachment.class)).isNull();
    }

    @Test
    void testHandleDeleteAttachmentSelectionRemovesDynamicFromElement() {
        var owner = deleteSelectedAttachment(
            note -> new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE));

        assertThat(owner.findAttachment(DynamicAttachment.class)).isNull();
    }

    @Test
    void testHandleDeleteAttachmentSelectionRemovesAnnotationFromElement() {
        var owner = deleteSelectedAttachment(
            note -> new AnnotationAttachment(note, new Annotation(ANNOTATION_TEXT)));

        assertThat(owner.findAttachment(AnnotationAttachment.class)).isNull();
    }

    // A beat change redefines the beat from its element onward, so its removal goes through
    // the beat-defining chokepoint rather than a bare removeAttachment.
    @Test
    void testHandleDeleteAttachmentSelectionRemovesBeatChangeFromElement() {
        var owner = deleteSelectedAttachment(note ->
            new BeatChangeAttachment(note, new BeatChange(Duration.CROTCHET, Duration.CROTCHET)));

        assertThat(owner.findAttachment(BeatChangeAttachment.class)).isNull();
    }

    /**
     * Puts a tempo change on each note of a two-note line and deletes the one on the note at
     * {@code selectedIndex}. Two tempo changes is the fixture the orphan rule turns on: which
     * of the two is selected decides whether the removal is allowed.
     *
     * @return the song, so the caller can assert on both notes
     */
    private static Song deleteOneOfTwoTempoChanges(int selectedIndex) {
        var song = twoNoteSong();
        var line = song.getLine(0);
        var firstNote = line.getElement(0);
        var secondNote = line.getElement(1);

        song.withoutMutationTracking(() -> {
            firstNote.addAttachment(new TempoChangeAttachment(firstNote, new Tempo()));
            secondNote.addAttachment(new TempoChangeAttachment(secondNote, new Tempo()));
        });

        var selected = line.getElement(selectedIndex).findAttachment(TempoChangeAttachment.class);

        assertThat(selected).isNotNull();

        handleDeleteWithTargetSelected(song, line, new HitTarget.Attachment(selected));

        return song;
    }

    // A later tempo change still has the earlier one to change from, so deleting it is
    // ordinary work — the orphan rule must not refuse every tempo change it sees.
    @Test
    void testHandleDeleteAttachmentSelectionRemovesATempoChangeThatOrphansNothing() {
        var line = deleteOneOfTwoTempoChanges(1).getLine(0);

        assertThat(line.getElement(1).findAttachment(TempoChangeAttachment.class))
            .as("the selected tempo change is gone").isNull();
        assertThat(line.getElement(0).findAttachment(TempoChangeAttachment.class))
            .as("the earlier tempo change it changed from survives").isNotNull();
    }

    /**
     * The song's first tempo change is what every later change changes <em>from</em>, so
     * deleting it while a later one survives is refused. The refusal has to happen before the
     * modification bracket opens: {@code Line.modifyElement} records unconditionally, so a
     * refusal from inside would leave an undo step that undoes nothing.
     */
    @Test
    void testHandleDeleteAttachmentSelectionRefusesATempoChangeThatWouldOrphanALaterOne() {
        var song = deleteOneOfTwoTempoChanges(0);
        var line = song.getLine(0);

        assertThat(line.getElement(0).findAttachment(TempoChangeAttachment.class))
            .as("the refused tempo change stays put").isNotNull();
        assertThat(line.getElement(1).findAttachment(TempoChangeAttachment.class))
            .as("the tempo change it protects stays put").isNotNull();
        assertThat(song.isModified())
            .as("a refusal must leave no undo step behind").isFalse();
    }

    // -----------------------------------------------------------------------
    // Accidental deletion — the one arm that also changes notes the user did
    // not select, because removing an explicit accidental changes what every
    // note inheriting it sounds like.
    // -----------------------------------------------------------------------

    /** A crotchet at {@link #F_STAFF_POSITION} carrying {@code accidental}, which may be null. */
    private static StaffElement noteAt(StaffElement.@Nullable Accidental accidental) {
        var note = crotchet();
        note.setStaffPosition(F_STAFF_POSITION);
        note.setAccidental(accidental);
        return note;
    }

    /**
     * A song holding {@code notes} on a line wide enough that no fit gate refuses — the gate is
     * exercised deliberately by {@link #CRAMPED_LINE_WIDTH_SS}, never by accident.
     */
    private static Song songOf(StaffElement... notes) {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            song.setLineWidthSs(UNCONSTRAINED_LINE_WIDTH_SS);

            for (var note : notes) {
                line.addElement(note);
            }
        });

        return song;
    }

    /** Answers the restatement prompt with {@code answer} for as long as it stays open. */
    private static MockedStatic<OptionDialogs> answering(int answer) {
        var optionDialogs = mockStatic(OptionDialogs.class);

        optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
            any(), any(), any(), anyInt(), anyInt())).thenReturn(answer);

        return optionDialogs;
    }

    /**
     * Runs {@code edit} and returns the batch notification it posted, or null when it recorded
     * nothing at all — which is how a refusal is told apart from a silent mutation.
     */
    private static @Nullable SongDidChangeNotification captureSongDidChange(Runnable edit) {
        var captured = new ArrayList<SongDidChangeNotification>();

        // Highest priority so this handler runs before any other subscriber on the shared bus:
        // a co-subscriber left alive by an earlier test class could throw and, under the
        // project's publication-error handling, stop delivery to lower-priority handlers.
        var listener = new Object() {
            @Handler(priority = Integer.MAX_VALUE)
            void onSongDidChange(SongDidChangeNotification notification) {
                captured.add(notification);
            }
        };

        MessageCenter.subscribe(listener);

        try {
            edit.run();
        } finally {
            MessageCenter.unsubscribe(listener);
        }

        return captured.isEmpty() ? null : captured.getFirst();
    }

    @Test
    void testHandleDeleteAccidentalSelectionRemovesTheAccidentalFromItsNote() {
        var song = songOf(noteAt(StaffElement.Accidental.SHARP));
        var line = song.getLine(0);
        var note = line.getElement(0);

        handleDeleteWithTargetSelected(song, line, new HitTarget.Accidental(note));

        assertThat(note.getAccidental()).isNull();
        assertThat(song.isModified())
            .as("the removal was recorded so undo can restore it").isTrue();
    }

    /**
     * A selection can outlive the accidental it names — an undo that cleared the accidental leaves
     * the target pointing at a note that no longer has one. The arm refuses before reaching the
     * accidental pipeline, which would otherwise run its whole reconciliation and fit gate over a
     * note with nothing to remove: the action's own "does this apply?" test only asks whether the
     * element is a note, never whether it carries an accidental.
     */
    @Test
    void testHandleDeleteAccidentalSelectionOnANoteWithNoAccidentalRemovesNothing() {
        var song = songOf(noteAt(null));
        var line = song.getLine(0);
        var note = line.getElement(0);

        var notification = captureSongDidChange(() ->
            handleDeleteWithTargetSelected(song, line, new HitTarget.Accidental(note)));

        assertThat(note.getAccidental())
            .as("the note is left exactly as it was").isNull();
        assertThat(notification)
            .as("a refusal must leave no undo step behind").isNull();
        assertThat(song.isModified()).isFalse();
    }

    /**
     * A later note at the same staff position sounds sharp only because of the accidental being
     * deleted, so the deletion has to write that sharp out explicitly. Asserting on the selected
     * note alone would pass even if the whole reconciliation were skipped and the user's untouched
     * note silently changed pitch.
     */
    @Test
    void testHandleDeleteAccidentalSelectionMakesTheAccidentalExplicitOnANoteThatInheritedIt() {
        var song = songOf(noteAt(StaffElement.Accidental.SHARP), noteAt(null));
        var line = song.getLine(0);

        handleDeleteWithTargetSelected(song, line, new HitTarget.Accidental(line.getElement(0)));

        assertThat(line.getElement(0).getAccidental())
            .as("the selected accidental is gone").isNull();
        assertThat(line.getElement(1).getAccidental())
            .as("the note that inherited it keeps its pitch, now stated explicitly")
            .isEqualTo(StaffElement.Accidental.SHARP);
    }

    /**
     * A later note restating the accidental explicitly is offered to the user, and accepting
     * takes it away with the one being deleted.
     */
    @Test
    void testHandleDeleteAccidentalSelectionAcceptedAlsoRemovesTheRestatement() {
        var song = songOf(
            noteAt(StaffElement.Accidental.SHARP), noteAt(StaffElement.Accidental.SHARP));
        var line = song.getLine(0);

        try (var optionDialogs = answering(JOptionPane.YES_OPTION)) {
            handleDeleteWithTargetSelected(song, line, new HitTarget.Accidental(line.getElement(0)));

            // Positive control: without this the assertions below would also pass had the
            // deletion never reached the prompt.
            optionDialogs.verify(() -> OptionDialogs.showConfirmDialog(
                any(), any(), any(), anyInt(), anyInt()));
        }

        assertThat(line.getElement(0).getAccidental())
            .as("the selected accidental is gone").isNull();
        assertThat(line.getElement(1).getAccidental())
            .as("the accepted restatement went with it").isNull();
    }

    /**
     * The other half of the decision: declining leaves the restating note exactly as it was. Two
     * outcomes from one fixture is what proves the answer is really threaded into the
     * reconciliation rather than assumed.
     */
    @Test
    void testHandleDeleteAccidentalSelectionDeclinedLeavesTheRestatementAlone() {
        var song = songOf(
            noteAt(StaffElement.Accidental.SHARP), noteAt(StaffElement.Accidental.SHARP));
        var line = song.getLine(0);

        try (var ignored = answering(JOptionPane.NO_OPTION)) {
            handleDeleteWithTargetSelected(song, line, new HitTarget.Accidental(line.getElement(0)));
        }

        assertThat(line.getElement(0).getAccidental())
            .as("the selected accidental is gone").isNull();
        assertThat(line.getElement(1).getAccidental())
            .as("declining leaves every restatement alone")
            .isEqualTo(StaffElement.Accidental.SHARP);
    }

    /**
     * The accidentals the reconciliation forces take horizontal space, so a deletion can overflow
     * the line just as an insertion can. The gate runs before anything is mutated, so a refusal
     * has to leave the note as it was and record nothing at all.
     */
    @Test
    void testHandleDeleteAccidentalSelectionIsRefusedWhenTheForcedAccidentalsDoNotFit() {
        var song = songOf(noteAt(StaffElement.Accidental.SHARP), noteAt(null));
        var line = song.getLine(0);
        var note = line.getElement(0);

        song.withoutMutationTracking(() -> song.setLineWidthSs(CRAMPED_LINE_WIDTH_SS));

        SongDidChangeNotification notification;

        try (var optionDialogs = mockStatic(OptionDialogs.class)) {
            notification = captureSongDidChange(
                () -> handleDeleteWithTargetSelected(song, line, new HitTarget.Accidental(note)));

            // Positive control: the fit gate is what refused — it says so in its own
            // removal-specific wording — rather than the deletion never reaching the gate.
            optionDialogs.verify(() -> OptionDialogs.showErrorMessage(
                any(), eq(Strings.ALERT_TITLE_LINE_TOO_FULL), eq(Strings.ERROR_LINE_FULL_REMOVAL)));
        }

        assertThat(note.getAccidental())
            .as("the refused accidental stays put").isEqualTo(StaffElement.Accidental.SHARP);
        assertThat(song.isModified()).isFalse();
        assertThat(notification)
            .as("a refusal must leave no undo step behind").isNull();
    }

    // -----------------------------------------------------------------------
    // Undo labels — every deletable kind names its own undo step
    // -----------------------------------------------------------------------

    /** Runs {@code setUp} with mutation tracking off, so a fixture leaves the song unmodified. */
    private static void untracked(Line line, Runnable setUp) {
        line.getSong().withoutMutationTracking(setUp);
    }

    /** Adds {@code span} to {@code line} untracked and hands it back to be named as a target. */
    private static <S extends Span> S onLine(Line line, S span) {
        untracked(line, () -> line.addSpan(span));
        return span;
    }

    /** Hangs {@code attachment} on {@code owner} untracked and names it as a delete target. */
    private static HitTarget attachedTarget(Line line, StaffElement owner, Attachment attachment) {
        untracked(line, () -> owner.addAttachment(attachment));
        return new HitTarget.Attachment(attachment);
    }

    /** Puts an articulation of {@code type} on the line's first note and names it as a target. */
    private static HitTarget articulationTarget(Line line, ArticulationType type) {
        var owner = line.getElement(0);
        var articulation = new Articulation(owner, type);

        untracked(line, () -> owner.addArticulation(articulation));

        return new HitTarget.Articulation(articulation);
    }

    private static Arguments opNameCase(
        String kind, String expectedOpNameKey, Function<Line, HitTarget> fixture
    ) {
        return Arguments.of(Named.of(kind, fixture), expectedOpNameKey);
    }

    /**
     * Every deletable kind paired with the undo label it owes, over a two-note line. The point is
     * the pairing: a label wired to the wrong arm, a swapped staccato/accent mapping, or a trill
     * falling back to the generic span removal's "Ending" text all look like working deletions
     * until the user opens the Undo menu.
     */
    private static Stream<Arguments> deletionsAndTheirOpNames() {
        return Stream.of(
            opNameCase("tie", Strings.ACTION_EDIT_OP_REMOVE_TIE, line ->
                new HitTarget.Tie(onLine(line, new Tie(line.getElement(0), line.getElement(1))))),

            opNameCase("beam", Strings.ACTION_EDIT_OP_REMOVE_BEAM, line ->
                new HitTarget.Beam(onLine(line, new Beam(line.getElement(0), line.getElement(1))))),

            opNameCase("tuplet", Strings.ACTION_EDIT_OP_REMOVE_TUPLET, line ->
                new HitTarget.Tuplet(
                    onLine(line, triplet(line.getElement(0), line.getElement(1))))),

            opNameCase("trill", Strings.ACTION_EDIT_OP_REMOVE_TRILL, line ->
                new HitTarget.Trill(
                    onLine(line, new Trill(line.getElement(0), line.getElement(1))))),

            opNameCase("staccato", Strings.ACTION_EDIT_OP_REMOVE_STACCATO, line ->
                articulationTarget(line, ArticulationType.STACCATO)),

            opNameCase("accent", Strings.ACTION_EDIT_OP_REMOVE_ACCENT, line ->
                articulationTarget(line, ArticulationType.ACCENT)),

            opNameCase("fermata", Strings.ACTION_EDIT_OP_REMOVE_FERMATA, line -> {
                var owner = line.getElement(0);
                return attachedTarget(line, owner, new FermataAttachment(owner));
            }),

            opNameCase("dynamic", Strings.ACTION_EDIT_OP_REMOVE_DYNAMIC, line -> {
                var owner = line.getElement(0);
                return attachedTarget(line, owner,
                    new DynamicAttachment(owner, DynamicAttachment.DynamicType.FORTE));
            }),

            opNameCase("annotation", Strings.ACTION_EDIT_OP_REMOVE_ANNOTATION, line -> {
                var owner = line.getElement(0);
                return attachedTarget(line, owner,
                    new AnnotationAttachment(owner, new Annotation(ANNOTATION_TEXT)));
            }),

            opNameCase("beat change", Strings.ACTION_EDIT_OP_REMOVE_BEAT_CHANGE, line -> {
                var owner = line.getElement(0);
                return attachedTarget(line, owner, new BeatChangeAttachment(
                    owner, new BeatChange(Duration.CROTCHET, Duration.CROTCHET)));
            }),

            // The later of two tempo changes: the first one is refused, since every later change
            // is a change from it, and a refusal records nothing to name.
            opNameCase("tempo change", Strings.ACTION_EDIT_OP_REMOVE_TEMPO_CHANGE, line -> {
                var earlier = line.getElement(0);
                var owner = line.getElement(1);

                untracked(line, () ->
                    earlier.addAttachment(new TempoChangeAttachment(earlier, new Tempo())));

                return attachedTarget(line, owner, new TempoChangeAttachment(owner, new Tempo()));
            }),

            opNameCase("accidental", Strings.ACTION_EDIT_OP_REMOVE_ACCIDENTAL, line -> {
                var note = line.getElement(0);

                untracked(line, () -> note.setAccidental(StaffElement.Accidental.SHARP));

                return new HitTarget.Accidental(note);
            }));
    }

    /**
     * Every other test here asserts that <em>a</em> mutation was recorded; this one asserts it was
     * named correctly, which is invisible until the user opens the Undo menu and undoes something
     * other than what the label promised. The accidental case also carries the labeled-bracket
     * branch of {@code SelectionActionApplier} end to end.
     */
    @ParameterizedTest
    @MethodSource("deletionsAndTheirOpNames")
    void testHandleDeleteNamesTheUndoStepAfterWhatWasDeleted(
        Function<? super Line, ? extends HitTarget> makeFixture, String expectedOpNameKey
    ) {
        var song = twoNoteSong();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> song.setLineWidthSs(UNCONSTRAINED_LINE_WIDTH_SS));

        var target = makeFixture.apply(line);
        var notification = captureSongDidChange(
            () -> handleDeleteWithTargetSelected(song, line, target));

        assertThat(notification).as("the deletion recorded a mutation batch").isNotNull();
        assertThat(notification.getOpName()).isEqualTo(Strings.get(expectedOpNameKey));
    }
}
