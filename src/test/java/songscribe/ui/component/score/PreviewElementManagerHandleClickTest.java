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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import javax.swing.JOptionPane;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.SlideZone;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.TempoChangeDialog;
import songscribe.ui.edit.EditModeManager;

/**
 * Tests for the two principal routing branches inside
 * {@link PreviewElementManager#handleClick}:
 *
 * <ul>
 *   <li>Glissando-placeholder path (row 28): clicking with a glissando preview element
 *       either applies the glissando to the source note or is a no-op, depending on
 *       whether {@code currentGlissandoZone} is set.</li>
 *   <li>Force-insert path (row 30): {@code handleClick(lc, true)} always inserts a new
 *       element even when {@code xPosSsMatchesElement} is true, while
 *       {@code handleClick(lc, false)} modifies the existing element in-place.</li>
 * </ul>
 */
class PreviewElementManagerHandleClickTest extends PreviewElementManagerTestBase {

    private static final int VERSE = 1;
    private static final String SYLLABLE = "glo";
    private static final int GRACE_INDEX = 0;
    private static final int HOST_INDEX = 1;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void addNotes(int count, ElementType type) {
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < count; i++) {
                line.addElement(type.newInstance());
            }
        });
    }

    /**
     * Builds {@code [grace(glissando, SYLLABLE), host crotchet]} with the automatic
     * grace-host melisma established: {@link Lyric.Extend#START} on the grace and a
     * text-less {@link Lyric.Extend#STOP} carrier on the host.
     */
    private void addPairedGraceCarryingSyllable() {
        song.withoutMutationTracking(() -> {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            grace.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, SYLLABLE, Lyric.Extend.NONE);
            line.addElement(grace);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.syncGraceHostMelisma(GRACE_INDEX);
        });
    }

    /** Reads the verse lyric at {@code index}, failing the test when there is none. */
    private Lyric requireLyric(int index, int verse) {
        var lyric = line.getElement(index).getLyricForVerse(verse);

        if (lyric == null) {
            throw new AssertionError("expected a verse " + verse + " lyric at index " + index);
        }

        return lyric;
    }

    // -----------------------------------------------------------------------
    // Glissando-placeholder path (row 28)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GlissandoPlaceholderPath {

        /**
         * A valid zone (CONNECTED) applies the glissando to the source note at
         * {@code currentXIndex - 1}.
         */
        @Test
        void testValidZoneAppliesGlissandoToSourceNote() {
            addNotes(2, ElementType.CROTCHET);

            // Preview element is a SLIDE placeholder, zone is set
            setPreviewElement(ElementType.SLIDE.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setCurrentSlideZone(SlideZone.GLISSANDO);

            PreviewElementManager.handleClick(lc);

            var sourceNote = line.getElement(0);
            assertThat(sourceNote.hasGlissando())
                .as("glissando applied to source note at xIndex-1")
                .isTrue();
        }

        /**
         * A null zone (no valid glissando position) is a no-op: no glissando is set.
         */
        @Test
        void testNullZoneIsNoOp() {
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.SLIDE.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setCurrentSlideZone(null);

            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(0).hasGlissando())
                .as("no glissando set when zone is null")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Fall-placeholder path — line-full guard
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FallPlaceholderPath {

        /**
         * With room on the line, a FALL zone applies a fall to the source note at
         * {@code currentXIndex - 1} without changing the element count.
         */
        @Test
        void testFallAppliedWhenLineHasRoom() {
            song.setLineWidthSs(WIDE_LINE_SS);
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.SLIDE.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setCurrentSlideZone(SlideZone.FALL);

            var countBefore = line.elementCount();
            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(0).hasFall())
                .as("fall applied to source note when the line has room")
                .isTrue();
            assertThat(line.elementCount())
                .as("applying a fall does not change the element count")
                .isEqualTo(countBefore);
        }

        /**
         * When the fall would not fit, the click shows the line-full error and leaves the source
         * note unchanged. The alert is verified through a static mock so removing it fails the test.
         */
        @Test
        void testFallBlockedWhenLineFull() {
            song.setLineWidthSs(0);
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.SLIDE.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setCurrentSlideZone(SlideZone.FALL);

            try (MockedStatic<OptionDialogs> optionDialogsMock = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc);

                optionDialogsMock.verify(() -> OptionDialogs.showErrorMessage(
                    isNull(), eq(Strings.ALERT_TITLE_INSERT_ERROR), eq(Strings.ERROR_LINE_FULL_FALL)));
            }

            assertThat(line.getElement(0).hasFall())
                .as("fall not applied when the line is full")
                .isFalse();
        }

        /**
         * Plan trace row 2. Slides are mutually exclusive, so applying a fall to a paired
         * grace note clears its glissando and un-pairs it, which dissolves the automatic
         * grace-host melisma. Both elements survive the click, so there is no hand-back: the
         * syllable simply stays on the now-ordinary former grace note.
         *
         * <p>The host's carrier must be removed outright rather than merely have its extend
         * cleared — an empty-text lyric still counts as lyric-bearing for backward lyric
         * navigation. This test fails if {@code syncGraceHostMelisma} is dropped from the
         * slide-placeholder branch of {@code handleClick}: the grace would keep {@code START}
         * and the host would keep its {@code STOP} carrier.
         */
        @Test
        void testFallOnPairedGraceNoteTearsDownTheMelisma() {
            song.setLineWidthSs(WIDE_LINE_SS);
            addPairedGraceCarryingSyllable();

            assertThat(requireLyric(GRACE_INDEX, VERSE).extend())
                .as("pre-condition: the melisma starts on the grace note")
                .isEqualTo(Lyric.Extend.START);
            assertThat(requireLyric(HOST_INDEX, VERSE).extend())
                .as("pre-condition: the host carries the melisma's STOP")
                .isEqualTo(Lyric.Extend.STOP);

            // currentXIndex - 1 is the slide's source note, so index 1 targets the grace.
            setPreviewElement(ElementType.SLIDE.newInstance());
            PreviewElementManager.setCurrentXIndex(HOST_INDEX);
            PreviewElementManager.setCurrentSlideZone(SlideZone.FALL);

            PreviewElementManager.handleClick(lc);

            var graceNote = line.getElement(GRACE_INDEX);
            assertThat(graceNote.hasFall())
                .as("fall applied to the grace note")
                .isTrue();
            assertThat(graceNote.hasGlissando())
                .as("the fall replaced the glissando, un-pairing the grace note")
                .isFalse();

            var graceLyric = requireLyric(GRACE_INDEX, VERSE);
            assertThat(graceLyric.text())
                .as("the syllable stays on the now-ordinary former grace note")
                .isEqualTo(SYLLABLE);
            assertThat(graceLyric.extend())
                .as("the melisma START reverted to NONE")
                .isEqualTo(Lyric.Extend.NONE);

            assertThat(line.getElement(HOST_INDEX).getLyricForVerse(VERSE))
                .as("the host's carrier is removed outright, leaving no empty-lyric residue")
                .isNull();
        }
    }

    // -----------------------------------------------------------------------
    // forceInsert path (row 30)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ForceInsertPath {

        /**
         * {@code handleClick(lc, true)} with {@code xPosSsMatchesElement=true} inserts a
         * new element at the current index rather than modifying the existing one.
         * Element count must increase by 1.
         */
        @Test
        void testForceInsertInsertsEvenWhenXMatches() {
            // Set line width so the insertion check passes
            song.setLineWidthSs(WIDE_LINE_SS);
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();
            PreviewElementManager.handleClick(lc, true);

            assertThat(line.elementCount())
                .as("forceInsert=true inserts a new element (count increases by 1)")
                .isEqualTo(countBefore + 1);
        }

        /**
         * {@code handleClick(lc, false)} with {@code xPosSsMatchesElement=true} modifies the
         * element at the current index in-place. Element count must stay the same.
         */
        @Test
        void testNormalClickModifiesInPlaceWhenXMatches() {
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.QUAVER.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();
            PreviewElementManager.handleClick(lc, false);

            assertThat(line.elementCount())
                .as("forceInsert=false modifies in-place (element count unchanged)")
                .isEqualTo(countBefore);

            assertThat(line.getElement(1).getType())
                .as("element at index 1 replaced with preview type")
                .isEqualTo(ElementType.QUAVER);
        }

        /**
         * When an insertion would not fit, the note path shows the line-full error with the note
         * variant and inserts nothing. The alert is verified through a static mock so removing it
         * fails the test.
         */
        @Test
        void testNoteInsertBlockedWhenLineFull() {
            song.setLineWidthSs(0);
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();

            try (MockedStatic<OptionDialogs> optionDialogsMock = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc, true);

                optionDialogsMock.verify(() -> OptionDialogs.showErrorMessage(
                    isNull(), eq(Strings.ALERT_TITLE_INSERT_ERROR), eq(Strings.ERROR_LINE_FULL_ELEMENT),
                    eq(ElementType.CROTCHET.categoryName())));
            }

            assertThat(line.elementCount())
                .as("note not inserted when the line is full")
                .isEqualTo(countBefore);
        }
    }

    // -----------------------------------------------------------------------
    // Breath mark over an existing element — blocked (issue #456)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BreathMarkOverExistingElement {

        /**
         * Clicking a breath mark over an existing element must neither replace nor insert:
         * a breath mark never replaces an element, and being over one is a blocked position,
         * so the click leaves the line intact.
         */
        @Test
        void testBreathMarkOverExistingElementDoesNotReplaceOrInsert() {
            addNotes(2, ElementType.CROTCHET);

            setPreviewElement(ElementType.BREATH_MARK.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.effectiveElementCount();

            PreviewElementManager.handleClick(lc);

            assertThat(line.effectiveElementCount())
                .as("blocked position: no element inserted or removed")
                .isEqualTo(countBefore);
            assertThat(line.getElement(1).getType())
                .as("element under the cursor is not replaced by the breath mark")
                .isEqualTo(ElementType.CROTCHET);
        }
    }

    // -----------------------------------------------------------------------
    // Grace note over the cursor — clicks are ignored (issue #360)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GraceNoteOverExistingElement {

        /**
         * A grace note may never be replaced. Clicking over one with a different preview
         * element must leave it in place and not insert anything either.
         */
        @Test
        void testClickOverGraceNoteIsIgnored() {
            song.withoutMutationTracking(() -> line.addElement(ElementType.GRACE_QUAVER.newInstance()));

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            var countBefore = line.elementCount();
            PreviewElementManager.handleClick(lc);

            assertThat(line.elementCount())
                .as("no element inserted or removed")
                .isEqualTo(countBefore);
            assertThat(line.getElement(0).getType())
                .as("the grace note is not replaced")
                .isEqualTo(ElementType.GRACE_QUAVER);
        }
    }

    // -----------------------------------------------------------------------
    // Replacing a note removes its accidental, so the restatement prompt runs (#681)
    // -----------------------------------------------------------------------

    /**
     * Replacing a note takes its explicit accidental away, so this path asks the same restatement
     * question every other removal path does. The fixture is the #681 worked example, in D♭ major
     * — five flats, so F is unaltered by the key and every flat on an F is explicit:
     *
     * <pre>
     * line 1:  F♭  G   F♭
     * line 2:  F♭  A   F   F♮
     * </pre>
     *
     * Replacing {@code 1:0} with a plain G finds {@code 1:2} and {@code 2:0}.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ReplacementRestatements {

        // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
        private static final int A_STAFF_POSITION = 1;
        private static final int G_STAFF_POSITION = 2;
        private static final int F_STAFF_POSITION = 3;

        /** D♭ major: B E A D G are flattened, so an F needs an explicit flat to sound flat. */
        private static final int FIVE_FLATS = 5;

        private static final int THIRD_NOTE = 2;
        private static final int FOURTH_NOTE = 3;

        private Line secondLine = new Line(new Song());

        @BeforeEach
        void buildWorkedExample() {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                flatKey(line);
                line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
                line.addElement(note(G_STAFF_POSITION, null));
                line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));

                secondLine = new Line(song);
                flatKey(secondLine);
                secondLine.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
                secondLine.addElement(note(A_STAFF_POSITION, null));
                secondLine.addElement(note(F_STAFF_POSITION, null));
                secondLine.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));
                song.addLine(secondLine);
            });
        }

        private void flatKey(Line target) {
            target.setKeyType(KeyType.FLATS);
            target.setKeyAccidentalCount(FIVE_FLATS);
        }

        private StaffElement note(int staffPosition, StaffElement.@Nullable Accidental accidental) {
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(staffPosition);
            element.setAccidental(accidental);
            return element;
        }

        /** Clicks a plain G onto {@code 1:0}, replacing the F♭ that is there. */
        private void replaceTheFirstFlatWithAPlainG(int answer) {
            setPreviewElement(note(G_STAFF_POSITION, null));
            PreviewElementManager.setCurrentStaffPosition(G_STAFF_POSITION);
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            try (var optionDialogs = mockStatic(OptionDialogs.class)) {
                optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                    any(), any(), any(), anyInt(), anyInt(), anyInt())).thenReturn(answer);

                PreviewElementManager.handleClick(lc);
            }
        }

        @Test
        void testYesClearsEveryRestatementAcrossLinesAndLetsThePitchChangePropagate() {
            replaceTheFirstFlatWithAPlainG(JOptionPane.YES_OPTION);

            assertThat(line.getElement(0).getStaffPosition())
                .as("the replacement landed where the click was")
                .isEqualTo(G_STAFF_POSITION);
            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .as("the restatement on the edited line is cleared")
                .isNull();
            assertThat(secondLine.getElement(0).getAccidental())
                .as("the restatement on the next line is cleared")
                .isNull();

            // Suppression at the F position is what lets 2:2 change pitch instead of being handed
            // the flat straight back, and 2:3's natural, no longer cancelling anything, goes the
            // ordinary way: the mirror rule takes it.
            assertThat(secondLine.getElement(THIRD_NOTE).getAccidental()).isNull();
            assertThat(secondLine.getElement(FOURTH_NOTE).getAccidental()).isNull();
        }

        @Test
        void testNoReplacesTheNoteAndLeavesEveryRestatementAlone() {
            replaceTheFirstFlatWithAPlainG(JOptionPane.NO_OPTION);

            assertThat(line.getElement(0).getStaffPosition()).isEqualTo(G_STAFF_POSITION);
            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
            assertThat(secondLine.getElement(0).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
        }

        @Test
        void testCancelAbandonsTheReplacementItself() {
            replaceTheFirstFlatWithAPlainG(JOptionPane.CANCEL_OPTION);

            assertThat(line.getElement(0).getStaffPosition())
                .as("the click did nothing at all")
                .isEqualTo(F_STAFF_POSITION);
            assertThat(line.getElement(0).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
            assertThat(secondLine.getElement(0).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
        }

        @Test
        void testNothingIsAskedWhenTheReplacementKeepsTheAccidental() {
            // Same staff position, same flat: the accidental survives the replacement, so there is
            // nothing to consent to.
            setPreviewElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
            PreviewElementManager.setCurrentStaffPosition(F_STAFF_POSITION);
            PreviewElementManager.setCurrentXIndex(0);
            PreviewElementManager.setXPosSsMatchesElement(true);

            try (var optionDialogs = mockStatic(OptionDialogs.class)) {
                PreviewElementManager.handleClick(lc);
                optionDialogs.verifyNoInteractions();
            }

            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
        }
    }

    // -----------------------------------------------------------------------
    // validateAndGetPreviewElement stale-preview guard (row 37)
    // -----------------------------------------------------------------------

    /**
     * When {@code EditModeManager.elementWasModified()} returns {@code true} for the
     * current element, {@code validateAndGetPreviewElement} returns {@code null}, causing
     * {@code modifyExistingElement} to bail out without changing the element. The stale-
     * preview notification is still fired via {@code previewElementDidChange}.
     */
    @Test
    void testElementWasModifiedCausesEarlyReturnAndFiresChangeNotification() {
        addNotes(1, ElementType.CROTCHET);

        // Preview element is a different type; clicking on the existing note would
        // normally replace it — but elementWasModified will block that.
        setPreviewElement(ElementType.QUAVER.newInstance());
        PreviewElementManager.setCurrentXIndex(0);
        PreviewElementManager.setXPosSsMatchesElement(true);

        // Override the base stub: mark the element as already-modified (stale preview).
        editModeManagerMock
            .when(() -> EditModeManager.elementWasModified(any(Line.class), anyInt()))
            .thenReturn(true);

        var typeBefore = line.getElement(0).getType();

        PreviewElementManager.handleClick(lc);

        // Element must NOT have been replaced (modifyExistingElement returned early).
        assertThat(line.getElement(0).getType())
            .as("element type unchanged: stale-preview guard prevented replacement")
            .isEqualTo(typeBefore);

        // previewElementDidChange must have been called to notify that the preview
        // is stale and should be refreshed.
        editModeManagerMock.verify(
            () -> EditModeManager.previewElementDidChange(any(Line.class), eq(0))
        );
    }

    // -----------------------------------------------------------------------
    // First-note tempo prompt: anchors on the first element, deferred until a
    // host (pitched) note exists
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FirstNoteTempoPrompt {

        private void insertPreview(ElementType type, int xIndex) {
            var preview = type.newInstance();
            preview.setStaffPosition(0);
            setPreviewElement(preview);
            PreviewElementManager.setCurrentXIndex(xIndex);
            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.handleClick(lc);

            // handleClick only queues the tempo prompt; the songDidChange handler shows it
            // once the modification bracket commits. MessageCenter is mocked here, so drive
            // that step directly.
            PreviewElementManager.showPendingTempoPrompt();
        }

        /**
         * Inserting the first pitched note of the song prompts for the song tempo,
         * anchored on that note.
         */
        @Test
        void testPromptsForTempoOnFirstPitchedNote() {
            song.setLineWidthSs(WIDE_LINE_SS);

            try (var dialogMock = mockStatic(TempoChangeDialog.class);
                 var mainFrameMock = mockStatic(MainFrame.class)) {
                // showForElement is stubbed, so its MainFrame argument is never
                // dereferenced — a null instance keeps the real (heavyweight) MainFrame
                // singleton from being constructed.
                mainFrameMock.when(MainFrame::getInstance).thenReturn(null);

                insertPreview(ElementType.CROTCHET, 0);

                var firstPitchedNote = line.firstPitchedElement();
                dialogMock.verify(() -> TempoChangeDialog.showForElement(any(), eq(firstPitchedNote), eq(line)));

                // The layout must be recomputed before the dialog shows, so the note is
                // positioned at its final x rather than the stale x = 0 behind the modal.
                verify(lc).ensureLayout();
            }
        }

        /**
         * A grace note as the first note must not prompt on its own: prompting only
         * makes sense once the host note exists. The prompt fires when the host note
         * is placed next, but anchors the tempo on the grace note (the first element).
         */
        @Test
        void testDefersTempoPromptPastLeadingGraceNoteButAnchorsOnGraceNote() {
            song.setLineWidthSs(WIDE_LINE_SS);

            try (var dialogMock = mockStatic(TempoChangeDialog.class);
                 var mainFrameMock = mockStatic(MainFrame.class)) {
                // showForElement is stubbed, so its MainFrame argument is never
                // dereferenced — a null instance keeps the real (heavyweight) MainFrame
                // singleton from being constructed.
                mainFrameMock.when(MainFrame::getInstance).thenReturn(null);

                insertPreview(ElementType.GRACE_QUAVER, 0);

                dialogMock.verify(() -> TempoChangeDialog.showForElement(any(), any(), any()), never());

                // The grace note now sits at index 0 before the terminal; place the
                // host note between them.
                insertPreview(ElementType.CROTCHET, 1);

                var graceNote = line.getElement(0);
                dialogMock.verify(() -> TempoChangeDialog.showForElement(any(), eq(graceNote), eq(line)));

                // The layout must be recomputed before the dialog shows (see the sibling
                // test); the recompute happens only on the host-note insert that prompts.
                verify(lc).ensureLayout();
            }
        }

        /**
         * Once the first line already has a pitched note, inserting another pitched note
         * must not prompt again: the gate fires only on the transition to the first
         * pitched note, not on every subsequent one.
         */
        @Test
        void testDoesNotRePromptWhenPitchedNoteAlreadyExists() {
            song.setLineWidthSs(WIDE_LINE_SS);

            try (var dialogMock = mockStatic(TempoChangeDialog.class);
                 var mainFrameMock = mockStatic(MainFrame.class)) {
                mainFrameMock.when(MainFrame::getInstance).thenReturn(null);

                insertPreview(ElementType.CROTCHET, 0);
                insertPreview(ElementType.CROTCHET, 1);

                // Only the first insert (the transition to a pitched note) prompts.
                dialogMock.verify(() -> TempoChangeDialog.showForElement(any(), any(), any()), times(1));
            }
        }

        /**
         * Insertion on a line other than the first must never prompt: the song's initial
         * tempo anchors only on the first line.
         */
        @Test
        void testDoesNotPromptOnNonFirstLine() {
            song.setLineWidthSs(WIDE_LINE_SS);

            // Prepend a line so the fixture line — the one lc points at — is no longer the
            // first line, making the gate's isFirstLine guard false.
            song.withoutMutationTracking(() -> song.addLine(0, new Line(song)));

            try (var dialogMock = mockStatic(TempoChangeDialog.class);
                 var mainFrameMock = mockStatic(MainFrame.class)) {
                mainFrameMock.when(MainFrame::getInstance).thenReturn(null);

                insertPreview(ElementType.CROTCHET, 0);

                dialogMock.verify(() -> TempoChangeDialog.showForElement(any(), any(), any()), never());
            }
        }
    }
}
