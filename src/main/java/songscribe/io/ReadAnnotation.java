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
package songscribe.io;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.StaffElement;

/**
 * An annotation a reader has finished, and where the document placed it.
 *
 * <p>Two values rather than one because where an annotation sits belongs to the
 * {@link AnnotationAttachment} and not to the {@link Annotation} — and the attachment cannot exist
 * until the element it hangs on is known, which in both file formats is later than the point the
 * annotation itself is complete.
 *
 * <p>Both readers answer this rather than the annotation alone, so neither can hand over a
 * finished annotation and leave its position behind. That is the whole reason it is a pair: a
 * second accessor beside the annotation would have to state in prose when it is meaningful, and
 * reading it at the wrong moment would give a plausible number with nothing marking it wrong.
 *
 * @param annotation    what the document's text and formatting describe
 * @param userYOffsetSs where the document placed it, in staff spaces, relative to the position
 *                      layout would otherwise compute
 */
public record ReadAnnotation(Annotation annotation, double userYOffsetSs) {

    /**
     * Builds the attachment this describes and puts it on {@code element}.
     *
     * @param element the staff element the annotation binds to
     * @effects adds one {@link AnnotationAttachment} to {@code element}
     */
    public void attachTo(StaffElement element) {
        var attachment = new AnnotationAttachment(element, annotation);

        attachment.setUserYOffsetSs(userYOffsetSs);
        element.addAttachment(attachment);
    }
}
