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

package songscribe.ui.layout;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable container for all calculated layout positions.
 * <p>
 * LayoutResult is the output of the layout engine. It contains:
 * <ul>
 *   <li>Section layouts (title, attribution, score sections)</li>
 *   <li>Line layouts with note positions, attachments, and range elements</li>
 *   <li>Total composition bounds</li>
 * </ul>
 * <p>
 * The drawing layer consumes LayoutResult without any position calculations.
 * This enables:
 * <ul>
 *   <li>Clear separation between layout and drawing</li>
 *   <li>Cacheable layout results</li>
 *   <li>Easy debugging/visualization of layout</li>
 *   <li>Multiple output formats (screen, PDF, SVG) from same layout</li>
 * </ul>
 *
 * @deprecated Replaced by new ElementRenderer system (Phase 6). Scheduled for removal in Phase 8.
 */
@Deprecated
public final class LayoutResult {

    private final @NotNull SectionLayout title;
    private final @NotNull SectionLayout attribution;
    private final @NotNull SectionLayout score;
    private final @NotNull SectionLayout lyrics;
    private final @NotNull SectionLayout banglaLyrics;
    private final @NotNull SectionLayout translation;
    private final @NotNull SectionLayout footnotes;
    private final @NotNull List<LineLayout> lines;
    private final @NotNull Rectangle2D totalBounds;

    /**
     * Creates a complete layout result.
     */
    public LayoutResult(
        @NotNull SectionLayout title,
        @NotNull SectionLayout attribution,
        @NotNull SectionLayout score,
        @NotNull SectionLayout lyrics,
        @NotNull SectionLayout banglaLyrics,
        @NotNull SectionLayout translation,
        @NotNull SectionLayout footnotes,
        @NotNull List<LineLayout> lines,
        @NotNull Rectangle2D totalBounds
    ) {
        this.title = title;
        this.attribution = attribution;
        this.score = score;
        this.lyrics = lyrics;
        this.banglaLyrics = banglaLyrics;
        this.translation = translation;
        this.footnotes = footnotes;
        this.lines = List.copyOf(lines);
        this.totalBounds = totalBounds;
    }

    /**
     * Returns the title section layout.
     */
    public @NotNull SectionLayout getTitle() {
        return title;
    }

    /**
     * Returns the attribution section layout.
     */
    public @NotNull SectionLayout getAttribution() {
        return attribution;
    }

    /**
     * Returns the score section layout.
     */
    public @NotNull SectionLayout getScore() {
        return score;
    }

    /**
     * Returns the lyrics section layout.
     */
    public @NotNull SectionLayout getLyrics() {
        return lyrics;
    }

    /**
     * Returns the Bangla lyrics section layout.
     */
    public @NotNull SectionLayout getBanglaLyrics() {
        return banglaLyrics;
    }

    /**
     * Returns the translation section layout.
     */
    public @NotNull SectionLayout getTranslation() {
        return translation;
    }

    /**
     * Returns the footnotes section layout.
     */
    public @NotNull SectionLayout getFootnotes() {
        return footnotes;
    }

    /**
     * Returns the list of line layouts.
     */
    public @NotNull List<LineLayout> getLines() {
        return lines;
    }

    /**
     * Returns the line layout at the given index, or empty if out of bounds.
     */
    public Optional<LineLayout> getLine(int lineIndex) {
        if (lineIndex >= 0 && lineIndex < lines.size()) {
            return Optional.of(lines.get(lineIndex));
        }

        return Optional.empty();
    }

    /**
     * Returns the number of lines.
     */
    public int getLineCount() {
        return lines.size();
    }

    /**
     * Returns the total bounds of all content.
     */
    public @NotNull Rectangle2D getTotalBounds() {
        return totalBounds;
    }

    /**
     * Returns the total height of all content.
     */
    public double getTotalHeight() {
        return totalBounds.getHeight();
    }

    /**
     * Returns the total width of all content.
     */
    public double getTotalWidth() {
        return totalBounds.getWidth();
    }

    /**
     * Finds the line at the given Y coordinate.
     *
     * @param y Y coordinate to test
     * @return Line layout if found, empty otherwise
     */
    public Optional<LineLayout> findLineAt(double y) {
        for (var line : lines) {
            var bounds = line.getLineBounds().getMarginBounds();

            if (y >= bounds.getMinY() && y <= bounds.getMaxY()) {
                return Optional.of(line);
            }
        }

        return Optional.empty();
    }

    /**
     * Finds the note at the given coordinate.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @return Note layout if found, empty otherwise
     */
    public Optional<NoteLayout> findNoteAt(double x, double y) {
        return findLineAt(y).flatMap(line -> line.findNoteAt(x, y));
    }

    /**
     * Returns the note layout for a specific line and note index.
     */
    public Optional<NoteLayout> getNote(int lineIndex, int noteIndex) {
        return getLine(lineIndex).flatMap(line -> line.getNote(noteIndex));
    }

    /**
     * Builder for creating LayoutResult instances incrementally.
     */
    public static class Builder {

        private SectionLayout title = SectionLayout.empty();
        private SectionLayout attribution = SectionLayout.empty();
        private SectionLayout score = SectionLayout.empty();
        private SectionLayout lyrics = SectionLayout.empty();
        private SectionLayout banglaLyrics = SectionLayout.empty();
        private SectionLayout translation = SectionLayout.empty();
        private SectionLayout footnotes = SectionLayout.empty();
        private List<LineLayout> lines = List.of();
        private Rectangle2D totalBounds = new Rectangle2D.Double();

        public Builder title(@NotNull SectionLayout title) {
            this.title = title;
            return this;
        }

        public Builder attribution(@NotNull SectionLayout attribution) {
            this.attribution = attribution;
            return this;
        }

        public Builder score(@NotNull SectionLayout score) {
            this.score = score;
            return this;
        }

        public Builder lyrics(@NotNull SectionLayout lyrics) {
            this.lyrics = lyrics;
            return this;
        }

        public Builder banglaLyrics(@NotNull SectionLayout banglaLyrics) {
            this.banglaLyrics = banglaLyrics;
            return this;
        }

        public Builder translation(@NotNull SectionLayout translation) {
            this.translation = translation;
            return this;
        }

        public Builder footnotes(@NotNull SectionLayout footnotes) {
            this.footnotes = footnotes;
            return this;
        }

        public Builder lines(@NotNull List<LineLayout> lines) {
            this.lines = lines;
            return this;
        }

        public Builder totalBounds(@NotNull Rectangle2D totalBounds) {
            this.totalBounds = totalBounds;
            return this;
        }

        public LayoutResult build() {
            return new LayoutResult(
                title,
                attribution,
                score,
                lyrics,
                banglaLyrics,
                translation,
                footnotes,
                lines,
                totalBounds
            );
        }
    }

    /**
     * Creates a new builder for LayoutResult.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "LayoutResult{" +
            "lines=" + lines.size() +
            ", totalBounds=" + rectToString(totalBounds) +
            "}";
    }

    private static String rectToString(Rectangle2D r) {
        return String.format("[%.1f,%.1f,%.1f,%.1f]",
            r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }
}
