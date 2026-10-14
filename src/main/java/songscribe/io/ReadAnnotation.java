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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.StaffElement;

/**
 * An annotation a reader has finished, and where the document placed it.
 *
 * <p>A pair so that neither reader can hand over a finished annotation and leave its position
 * behind.
 */
public record ReadAnnotation(Annotation annotation, double userYOffsetSs) {

    private static final Logger LOG = LoggerFactory.getLogger(ReadAnnotation.class);

    public void attachTo(StaffElement element) throws SAXException {
        var attachment = new AnnotationAttachment(annotation);

        attachment.setUserYOffsetSs(userYOffsetSs);
        DocumentValidation.attachUnique(LOG, element, attachment);
    }
}
