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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;

/**
 * Tests for structural side-effects in {@link PreviewElementManager}:
 *
 * <ul>
 *   <li>{@code modifyExistingElement} grace-note cleanup (row 33): when the host of a
 *       paired grace note is replaced with a non-pitched type, the grace note is removed.</li>
 *   <li>{@code insertElement} syllable/extend adjustment (row 35): syllabic values on
 *       adjacent elements are corrected after a new element is inserted mid-line.</li>
 * </ul>
 */
class PreviewElementManagerModifyInsertTest extends PreviewElementManagerTestBase {

    // -----------------------------------------------------------------------
    // modifyExistingElement — grace-note cleanup (row 33)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModifyExistingElementGraceNoteCleanup {

        /**
         * Layout:  [grace(CONNECTED glissando)] [host note]
         *
         * Replacing the host note (index 1) with a rest removes the preceding grace note,
         * because a rest cannot be the target of a CONNECTED glissando.
         * Post-modification element count drops by 1 (grace removed).
         */
        @Test
        void testGraceNoteRemovedWhenHostReplacedWithRest() {
            song.withoutMutationTracking(() -> {
                var grace = ElementType.GRACE_QUAVER.newInstance();
                grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
                line.addElement(grace);
                line.addElement(ElementType.CROTCHET.newInstance()); // host at index 1
            });

            assertThat(line.isHostOfPairedGraceNote(1))
                .as("pre-condition: index 1 is host of paired grace note")
                .isTrue();

            var countBefore = line.elementCount();

            setPreviewElement(ElementType.CROTCHET_REST.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.handleClick(lc);

            assertThat(line.elementCount())
                .as("element count decreases by 1 after grace note removed")
                .isEqualTo(countBefore - 1);

            assertThat(line.getElement(0).getType())
                .as("remaining element is the replacement rest, not the grace note")
                .isEqualTo(ElementType.CROTCHET_REST);
        }

        /**
         * Replacing the host note with another pitched note preserves the grace note
         * (the glissando reattaches automatically).
         */
        @Test
        void testGraceNotePreservedWhenHostReplacedWithPitchedNote() {
            song.withoutMutationTracking(() -> {
                var grace = ElementType.GRACE_QUAVER.newInstance();
                grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
                line.addElement(grace);
                line.addElement(ElementType.CROTCHET.newInstance()); // host at index 1
            });

            var countBefore = line.elementCount();

            setPreviewElement(ElementType.QUAVER.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.handleClick(lc);

            assertThat(line.elementCount())
                .as("element count unchanged when host replaced with pitched note")
                .isEqualTo(countBefore);

            assertThat(line.getElement(0).getType())
                .as("grace note still present at index 0")
                .isEqualTo(ElementType.GRACE_QUAVER);

            assertThat(line.getElement(0).getGlissando())
                .as("grace note's connected glissando is preserved after pitched replacement")
                .isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // insertElement — syllable/extend adjustment (row 35)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertElementSyllableAdjustment {

        /**
         * Layout before insertion:
         *   index 0: note, lyric verse 1 "hel" syllabic=BEGIN
         *   index 1: note, lyric verse 1 "lo"  syllabic=END
         *
         * Insert a new note at index 1 (between them). After insertion:
         *   index 0: "hel" syllabic must become SINGLE (chain broken by bare insert)
         *   index 1: new bare note (no lyric)
         *   index 2: "lo" syllabic must become SINGLE (no predecessor syllable)
         */
        @Test
        void testInsertionBreaksSyllableChainOnPredecessor() {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                var note0 = ElementType.CROTCHET.newInstance();
                note0.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);
                line.addElement(note0);

                var note1 = ElementType.CROTCHET.newInstance();
                note1.setLyricForVerse(1, Lyric.Syllabic.END, false, "lo", Lyric.Extend.NONE);
                line.addElement(note1);
            });

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.handleClick(lc);

            // effectiveElementCount excludes the auto-maintained FINAL_DOUBLE_BARLINE terminal
            assertThat(line.effectiveElementCount())
                .as("effective element count increases to 3 after insertion")
                .isEqualTo(3);

            // Predecessor (index 0) syllabic: BEGIN chain broken → SINGLE
            var pred = line.getElement(0);
            var predLyric = pred.getLyricForVerse(1);
            assertThat(predLyric).as("predecessor still has a lyric").isNotNull();
            //noinspection ConstantValue -- need for NullAway
            assertThat(predLyric == null ? null : predLyric.syllabic())
                .as("predecessor 'hel' syllabic downgraded to SINGLE by insertion")
                .isEqualTo(Lyric.Syllabic.SINGLE);

            // Successor (now at index 2) syllabic: END → SINGLE (no preceding syllable)
            var succ = line.getElement(2);
            var succLyric = succ.getLyricForVerse(1);
            assertThat(succLyric).as("successor still has a lyric").isNotNull();
            //noinspection ConstantValue -- need for NullAway
            assertThat(succLyric == null ? null : succLyric.syllabic())
                .as("successor 'lo' syllabic changed to SINGLE (no preceding syllable)")
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        /**
         * Middle syllable promotion: inserting before a MIDDLE syllable promotes it to BEGIN.
         */
        @Test
        void testInsertionPromotesMiddleSyllableToBegin() {
            song.setLineWidthSs(WIDE_LINE_SS);

            song.withoutMutationTracking(() -> {
                var note0 = ElementType.CROTCHET.newInstance();
                note0.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "a", Lyric.Extend.NONE);
                line.addElement(note0);

                var note1 = ElementType.CROTCHET.newInstance();
                note1.setLyricForVerse(1, Lyric.Syllabic.MIDDLE, false, "b", Lyric.Extend.NONE);
                line.addElement(note1);
            });

            setPreviewElement(ElementType.CROTCHET.newInstance());
            PreviewElementManager.setCurrentXIndex(1);
            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.handleClick(lc);

            // Successor (was index 1, now at index 2) must be promoted from MIDDLE to BEGIN
            var succ = line.getElement(2);
            var succLyric = succ.getLyricForVerse(1);
            assertThat(succLyric).as("successor still has a lyric").isNotNull();
            //noinspection ConstantValue -- need for NullAway
            assertThat(succLyric == null ? null : succLyric.syllabic())
                .as("MIDDLE syllabic promoted to BEGIN after insertion before it")
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }
    }
}
