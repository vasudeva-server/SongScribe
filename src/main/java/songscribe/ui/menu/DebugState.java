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

package songscribe.ui.menu;

import module java.desktop;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import songscribe.ui.layout.ElementBounds;

/**
 * Manages debug visualization state.
 */
public class DebugState {

    private static boolean inspectorEnabled = false;
    private static HoveredElement hoveredElement = null;
    private static Point mousePosition = null;

    public static boolean isInspectorEnabled() {
        return inspectorEnabled;
    }

    public static void setInspectorEnabled(boolean enabled) {
        inspectorEnabled = enabled;
    }

    @Nullable
    public static HoveredElement getHoveredElement() {
        return hoveredElement;
    }

    public static void setHoveredElement(@Nullable HoveredElement element) {
        hoveredElement = element;
    }

    @Nullable
    public static Point getMousePosition() {
        return mousePosition;
    }

    public static void setMousePosition(@Nullable Point position) {
        mousePosition = position;
    }

    public static boolean isDebugEnabled() {
        return System.getenv("DEBUG") != null;
    }

    // Temporary stubs for backward compatibility - will be removed in Phase 6
    @Deprecated
    public static boolean isShowLayoutBoxes() {
        return false;
    }

    @Deprecated
    public static void setShowLayoutBoxes(boolean show) {
        // No-op
    }

    @Deprecated
    public static boolean isShowBoundingBoxes() {
        return false;
    }

    @Deprecated
    public static void setShowBoundingBoxes(boolean show) {
        // No-op
    }

    @Deprecated
    public static boolean isShowMargins() {
        return false;
    }

    @Deprecated
    public static void setShowMargins(boolean show) {
        // No-op
    }

    public static final class HoveredElement {

        private final ElementBounds bounds;
        private final String label;
        private final DebugElementType type;

        public HoveredElement(ElementBounds bounds, String label, DebugElementType type) {
            this.bounds = bounds;
            this.label = label;
            this.type = type;
        }

        public ElementBounds getBounds() {
            return bounds;
        }

        public String getLabel() {
            return label;
        }

        public DebugElementType getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof HoveredElement)) {
                return false;
            }

            var that = (HoveredElement) o;
            return type == that.type &&
                label.equals(that.label) &&
                boundsEqual(bounds, that.bounds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                type,
                label,
                bounds.getMarginBounds().getX(),
                bounds.getMarginBounds().getY()
            );
        }

        private boolean boundsEqual(ElementBounds b1, ElementBounds b2) {
            var m1 = b1.getMarginBounds();
            var m2 = b2.getMarginBounds();
            return m1.getX() == m2.getX() &&
                m1.getY() == m2.getY() &&
                m1.getWidth() == m2.getWidth() &&
                m1.getHeight() == m2.getHeight();
        }
    }

    public enum DebugElementType {
        NOTE,
        ATTACHMENT,
        RANGE,
        LINE,
        SECTION,
        STAFF_LYRICS,
        UNDER_LYRICS,
        TRANSLATION,
        BANGLA_LYRICS,
        FOOTNOTES,
        TITLE,
        ATTRIBUTION
    }
}
