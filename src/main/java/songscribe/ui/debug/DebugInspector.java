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

package songscribe.ui.debug;

import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;

import songscribe.ui.layout.SectionLayout;
import songscribe.ui.menu.DebugState;

/**
 * Handles debug inspector hover detection and logging.
 * <p>
 * This utility class manages the inspector feature that highlights UI elements
 * on hover and logs their bounds information for debugging purposes.
 * <p>
 * The inspector functionality is currently disabled/stubbed and will need
 * to be updated when the component-based layout system is fully implemented.
 */
public final class DebugInspector {

    private static final Logger LOG = Logger.getLogger(DebugInspector.class.getName());

    private DebugInspector() {
        // Utility class - prevent instantiation
    }

    /**
     * Updates the inspector hovered element based on mouse position.
     * Checks elements in priority order (most specific first).
     * <p>
     * Currently stubbed - will be implemented when component hierarchy is available.
     *
     * @param x Mouse X coordinate
     * @param y Mouse Y coordinate
     */
    public static void updateInspectorHover(int x, int y) {
        // TODO: Implement hover detection with component hierarchy
        DebugState.setHoveredElement(null);
    }

    /**
     * Old implementation of inspector hover detection.
     * Preserved for reference when re-implementing the inspector feature.
     * <p>
     * This method is stubbed and should not be called. When the inspector
     * feature is re-enabled, this code will need to be updated to work
     * with the new component-based layout system.
     *
     * @param x Mouse X coordinate
     * @param y Mouse Y coordinate
     */
    @SuppressWarnings("unused")
    private static void updateInspectorHoverOLD(int x, int y) {
        /*
        // 1. Check notes
        int noteCount = 0;
        for (var line : layoutResult.getLines()) {
            for (var note : line.getNotes()) {
                noteCount++;
                if (note.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        note.getElementBounds(),
                        "Note",
                        DebugState.ElementType.NOTE
                    ));
                    return;
                }
            }
        }

        // 2. Check staff lyrics syllables
        int syllableCount = 0;
        for (var line : layoutResult.getLines()) {
            var lyrics = line.getLyrics();
            syllableCount += lyrics.getSyllables().size();

            for (var syllable : lyrics.getSyllables()) {
                if (syllable.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        syllable.getBounds(),
                        "Staff Lyrics: " + syllable.getText(),
                        DebugState.ElementType.STAFF_LYRICS
                    ));
                    return;
                }
            }
        }

        // 3. Check attachments
        int attachmentCount = 0;
        for (var line : layoutResult.getLines()) {
            attachmentCount += line.getAttachments().size();
            for (var attachment : line.getAttachments()) {
                if (attachment.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        attachment.getBounds(),
                        "Attachment: " + attachment.getType(),
                        DebugState.ElementType.ATTACHMENT
                    ));
                    return;
                }
            }
        }

        // 4. Check ranges
        int rangeCount = 0;
        for (var line : layoutResult.getLines()) {
            rangeCount += line.getRangeElements().size();
            for (var range : line.getRangeElements()) {
                if (range.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        range.getBounds(),
                        "Range: " + range.getType(),
                        DebugState.ElementType.RANGE
                    ));
                    return;
                }
            }
        }

        // 5. Check line bounds
        for (var line : layoutResult.getLines()) {
            if (line.getLineBounds().containsForHitTest(x, y)) {
                // Log element counts for this line
                LOG.fine(String.format(
                    "[Inspector Debug] Line hit | Total notes: %d, syllables: %d, " +
                        "attachments: %d, ranges: %d",
                    noteCount,
                    syllableCount,
                    attachmentCount,
                    rangeCount
                ));

                DebugState.setHoveredElement(new DebugState.HoveredElement(
                    line.getLineBounds(),
                    "Line",
                    DebugState.ElementType.LINE
                ));
                return;
            }
        }

        // 6. Check sections
        if (checkSection(
            layoutResult.getTitle(), x, y, "Title", DebugState.ElementType.TITLE
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getAttribution(), x, y, "Attribution", DebugState.ElementType.ATTRIBUTION
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getLyrics(), x, y, "Under Lyrics", DebugState.ElementType.UNDER_LYRICS
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getBanglaLyrics(),
            x,
            y,
            "Bangla Lyrics",
            DebugState.ElementType.BANGLA_LYRICS
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getTranslation(),
            x,
            y,
            "Translation",
            DebugState.ElementType.TRANSLATION
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getFootnotes(), x, y, "Footnotes", DebugState.ElementType.FOOTNOTES
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getScore(), x, y, "Score", DebugState.ElementType.SECTION
        )) {
            return;
        }

        // No element found
        DebugState.setHoveredElement(null);
        */
    }

    /**
     * Checks if a section contains the given point and sets the hovered element if so.
     *
     * @param section The section layout to check
     * @param x       Mouse X coordinate
     * @param y       Mouse Y coordinate
     * @param label   Label for the element
     * @param type    Type of the element
     * @return true if the section was hit and hovered element was set
     */
    public static boolean checkSection(
        SectionLayout section,
        int x,
        int y,
        String label,
        DebugState.DebugElementType type
    ) {
        var bounds = section.getBounds();

        if (bounds.containsForHitTest(x, y) && section.hasContent()) {
            DebugState.setHoveredElement(new DebugState.HoveredElement(bounds, label, type));
            return true;
        }

        return false;
    }

    /**
     * Logs inspector hover information for debugging.
     *
     * @param element The hovered element to log, or null to log hover cleared
     */
    public static void logInspectorHover(@Nullable DebugState.HoveredElement element) {
        if (element == null) {
            LOG.fine("[Inspector] Hover cleared");
            return;
        }

        var bounds = element.getBounds();
        LOG.fine(String.format(
            "[Inspector] %s (%s) | Size: %s | Padding: %s | Margin: %s",
            element.getLabel(),
            element.getType(),
            bounds.getContentSizeString(),
            bounds.getPaddingCss(),
            bounds.getMarginCss()
        ));
    }
}
