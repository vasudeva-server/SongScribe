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

package songscribe.dom;

import java.awt.Font;

import org.jspecify.annotations.Nullable;

import songscribe.font.TextMeasurement;

/**
 * A text annotation attached to a note.
 */
public final class AnnotationAttachment extends Attachment {

    private Annotation annotation;

    public AnnotationAttachment(Annotation annotation) {
        this.annotation = annotation;
    }

    @Override
    protected Attachment createCopy() {
        return new AnnotationAttachment(annotation);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    public double computeContentWidthSs(Font font) {
        return TextMeasurement.textWidthSs(font, annotation.text()).value();
    }

    public double computeContentHeightSs(Font font) {
        return TextMeasurement.textHeightSs(font).value();
    }
}
