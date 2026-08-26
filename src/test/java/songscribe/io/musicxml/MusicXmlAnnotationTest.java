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
package songscribe.io.musicxml;

import org.audiveris.proxymusic.AboveBelow;
import org.audiveris.proxymusic.LeftCenterRight;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;

/**
 * How an annotation's two placements — where the text sits against its note, and whether it sits
 * above the staff or below it — survive a write and a read.
 *
 * <p>Each is a write switch and a read switch that must agree, and the compiler cannot check that
 * they do. Both cases come from the enum itself, so a constant added to either one reaches these
 * tests without an edit here.
 *
 * <p>Where the annotation sits is here too, and it is not on the annotation: a hand-placed
 * vertical offset belongs to the attachment, so the writer takes it from there and the reader has
 * to put it back there. Nothing in either type makes those two agree.
 *
 * <p>There is no case for a {@code <words>} that carries no {@code halign}. The writer sets the
 * attribute on every annotation it writes, and a document SongScribe did not write is refused
 * before any mapping runs, so no reader can meet one.
 */
class MusicXmlAnnotationTest extends UnitTest {

    private static final String ANNOTATION_TEXT = "dolce";

    /**
     * A hand-placed offset, in whole staff spaces. Whole because {@code relative-y} is written and
     * read in tenths of a staff space rounded to an integer, so a fractional offset would be
     * asserting a precision the format does not promise.
     */
    private static final double USER_Y_OFFSET_SS = 3;

    @ParameterizedTest
    @EnumSource(LeftCenterRight.class)
    void testAnAnnotationKeepsItsAlignmentThroughAWriteAndARead(LeftCenterRight alignment) throws Exception {
        var annotation = new Annotation(ANNOTATION_TEXT, alignment, Annotation.DEFAULT_PLACEMENT);

        var restored = annotationOf(roundTrip(songWith(annotation)));

        assertThat(restored.text()).isEqualTo(ANNOTATION_TEXT);
        assertThat(restored.alignment()).isEqualTo(alignment);
    }

    @ParameterizedTest
    @EnumSource(AboveBelow.class)
    void testAnAnnotationKeepsItsPlacementThroughAWriteAndARead(AboveBelow placement) throws Exception {
        var annotation = new Annotation(ANNOTATION_TEXT, Annotation.DEFAULT_ALIGNMENT, placement);

        assertThat(annotationOf(roundTrip(songWith(annotation))).placement()).isEqualTo(placement);
    }

    @Test
    void testAHandPlacedVerticalOffsetSurvivesAWriteAndARead() throws Exception {
        var song = songWith(new Annotation(ANNOTATION_TEXT), USER_Y_OFFSET_SS);

        assertThat(attachmentOf(roundTrip(song)).getUserYOffsetSs()).isEqualTo(USER_Y_OFFSET_SS);
    }

    /** A one-line song whose only note carries {@code annotation}, at no offset. */
    private static Song songWith(Annotation annotation) {
        return songWith(annotation, 0);
    }

    /**
     * @param annotation    the annotation the song's only note carries
     * @param userYOffsetSs where its attachment sits, in staff spaces
     * @return a one-line song whose only note carries that annotation there
     */
    private static Song songWith(Annotation annotation, double userYOffsetSs) {
        return buildSong(line -> {
            var note = crotchet();
            line.addElement(note);

            var attachment = new AnnotationAttachment(note, annotation);
            attachment.setUserYOffsetSs(userYOffsetSs);
            note.addAttachment(attachment);
        });
    }

    /**
     * @param song a song built by {@link #songWith}, or one read back from such a song
     * @return the annotation attachment on that song's only note
     */
    private static AnnotationAttachment attachmentOf(Song song) {
        var attachment = song.getLine(0).getElement(0).findAttachment(AnnotationAttachment.class);
        assertThat(attachment).isNotNull();

        return attachment;
    }

    /**
     * @param song a song built by {@link #songWith}, or one read back from such a song
     * @return the annotation on that song's only note
     */
    private static Annotation annotationOf(Song song) {
        return attachmentOf(song).getAnnotation();
    }
}
