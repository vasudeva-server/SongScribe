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

import org.junit.jupiter.api.Test;

import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;

/**
 * Regression tests for issue #426: replacing an existing note must honor the note-entry
 * decorations (accidental, dot count, articulations) carried on the preview element,
 * while preserving the decorations the existing note carries that the preview cannot set.
 *
 * <p>The user places a bare note, toggles accent/staccato/natural on the toolbar
 * (which decorate the preview element), then clicks the existing note's x position at a
 * different staff position. That click routes to {@code modifyExistingElement}, which must
 * transfer the preview's decorations to the replacement and leave the rest intact.
 */
class PreviewElementManagerReplaceDecorationsTest extends PreviewElementManagerTestBase {

    /** A staff position different from the bare note's default, mirroring the issue scenario. */
    private static final int REPLACEMENT_STAFF_POSITION = 2;

    /** A single augmentation dot set on the preview, to verify dot count transfers. */
    private static final int PREVIEW_DOT_COUNT = 1;

    /** A non-zero manual x-offset on the existing note, to verify it survives replacement. */
    private static final int EXISTING_X_OFFSET_PX = 7;

    /** The verse the preserved lyric is attached to. */
    private static final int LYRIC_VERSE = 1;

    /** The text of the preserved lyric. */
    private static final String LYRIC_TEXT = "la";

    /**
     * Builds the decorated preview element the user would be "holding": a crotchet with a
     * natural accidental, accent and staccato articulations.
     */
    private static StaffElement decoratedPreview() {
        var preview = ElementType.CROTCHET.newInstance();
        preview.setAccidental(StaffElement.Accidental.NATURAL);
        preview.addArticulation(new Articulation(preview, ArticulationType.ACCENT));
        preview.addArticulation(new Articulation(preview, ArticulationType.STACCATO));
        return preview;
    }

    /**
     * Drives the click that replaces the element at index 0 with {@code preview}, at a staff
     * position different from the original so the path is the issue's replace scenario.
     */
    private void replaceElementAt0With(StaffElement preview) {
        setPreviewElement(preview);
        PreviewElementManager.setCurrentXIndex(0);
        PreviewElementManager.setCurrentStaffPosition(REPLACEMENT_STAFF_POSITION);
        PreviewElementManager.setXPosSsMatchesElement(true);
        PreviewElementManager.handleClick(lc);
    }

    /**
     * Replacing a bare note with a fully decorated preview transfers the preview's
     * articulations, with no stray decorations left behind.
     */
    @Test
    void testReplacementInheritsAllPreviewDecorations() {
        song.setLineWidthSs(WIDE_LINE_SS);

        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));

        replaceElementAt0With(decoratedPreview());

        var replacement = line.getElement(0);

        assertThat(replacement.getArticulations())
            .extracting(Articulation::getType)
            .as("accent and staccato carried over from the preview, with no extras")
            .containsExactlyInAnyOrder(ArticulationType.ACCENT, ArticulationType.STACCATO);
    }

    /**
     * Dot count is a preview-settable attribute: a dotted preview produces a dotted replacement.
     */
    @Test
    void testReplacementInheritsPreviewDotCount() {
        song.setLineWidthSs(WIDE_LINE_SS);

        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));

        var preview = ElementType.CROTCHET.newInstance();
        preview.setDotCount(PREVIEW_DOT_COUNT);
        replaceElementAt0With(preview);

        assertThat(line.getElement(0).getDotCount())
            .as("dot count carried over from the preview")
            .isEqualTo(PREVIEW_DOT_COUNT);
    }

    /**
     * The preview is authoritative for articulations: replacing a note that already has a
     * staccato with a preview that has only an accent yields just the accent — the existing
     * staccato is replaced, not merged. This is the exact bug behind issue #426.
     */
    @Test
    void testPreviewArticulationsReplaceRatherThanMerge() {
        song.setLineWidthSs(WIDE_LINE_SS);

        song.withoutMutationTracking(() -> {
            var existing = ElementType.CROTCHET.newInstance();
            existing.addArticulation(new Articulation(existing, ArticulationType.STACCATO));
            line.addElement(existing);
        });

        var preview = ElementType.CROTCHET.newInstance();
        preview.addArticulation(new Articulation(preview, ArticulationType.ACCENT));
        replaceElementAt0With(preview);

        assertThat(line.getElement(0).getArticulations())
            .extracting(Articulation::getType)
            .as("preview's accent replaces the existing staccato; they are not merged")
            .containsExactly(ArticulationType.ACCENT);
    }

    /**
     * The preview is authoritative for these decorations: replacing a note that already has an
     * accent with an undecorated preview removes it, mirroring how an empty accidental on the
     * preview clears the existing accidental.
     */
    @Test
    void testUndecoratedPreviewClearsExistingDecorations() {
        song.setLineWidthSs(WIDE_LINE_SS);

        song.withoutMutationTracking(() -> {
            var existing = ElementType.CROTCHET.newInstance();
            existing.addArticulation(new Articulation(existing, ArticulationType.ACCENT));
            line.addElement(existing);
        });

        replaceElementAt0With(ElementType.CROTCHET.newInstance());

        var replacement = line.getElement(0);

        assertThat(replacement.getArticulations())
            .as("existing accent removed because the preview has none")
            .isEmpty();
    }

    /**
     * Decorations the preview cannot set — a dynamic attachment, lyrics, a fermata, and a manual
     * x-offset — survive the replacement untouched. This guards the "preserve" half of the
     * contract that an over-eager clear of attachments or lyrics would silently break.
     */
    @Test
    void testReplacementPreservesNonPreviewDecorations() {
        song.setLineWidthSs(WIDE_LINE_SS);

        song.withoutMutationTracking(() -> {
            var existing = ElementType.CROTCHET.newInstance();
            existing.addAttachment(
                new DynamicAttachment(existing, DynamicAttachment.DynamicType.FORTE));
            existing.setLyricForVerse(
                LYRIC_VERSE, Lyric.Syllabic.SINGLE, false, LYRIC_TEXT, Lyric.Extend.NONE);
            existing.addAttachment(new FermataAttachment(existing));
            existing.setXOffsetPx(EXISTING_X_OFFSET_PX);
            line.addElement(existing);
        });

        replaceElementAt0With(decoratedPreview());

        var replacement = line.getElement(0);

        assertThat(replacement.findAttachment(DynamicAttachment.class))
            .as("dynamic attachment is not preview-settable, so it survives replacement")
            .isNotNull();

        assertThat(replacement.getLyricForVerse(Lyric.FIRST_VERSE))
            .as("lyric survives replacement with its text intact")
            .isNotNull()
            .extracting(Lyric::text)
            .isEqualTo(LYRIC_TEXT);

        assertThat(replacement.findAttachment(FermataAttachment.class))
            .as("fermata is not preview-settable, so it survives replacement")
            .isNotNull();

        assertThat(replacement.getXOffsetPx())
            .as("manual x-offset survives replacement")
            .isEqualTo(EXISTING_X_OFFSET_PX);
    }
}
