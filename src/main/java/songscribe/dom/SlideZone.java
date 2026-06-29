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

package songscribe.dom;

/**
 * The kind of slide a slide tool would attach at a hovered position: a connecting
 * {@link StaffElement.Glissando} to the following note, or a trailing {@link StaffElement.Fall} on
 * the source note. Models the <em>intended</em> tool state before any {@link StaffElement.Slide} is
 * instantiated, and owns the single mapping between that intent and the concrete slide on an element
 * so the two never drift apart.
 */
public enum SlideZone {
    GLISSANDO,
    FALL;

    /** Returns whether {@code element} already carries the slide this zone represents. */
    public boolean matches(StaffElement element) {
        return switch (this) {
            case GLISSANDO -> element.hasGlissando();
            case FALL -> element.hasFall();
        };
    }

    /** Attaches the slide this zone represents to {@code element}, replacing any existing slide. */
    public void applyTo(StaffElement element) {
        switch (this) {
            case GLISSANDO -> element.setGlissando();
            case FALL -> element.setFall();
        }
    }
}
