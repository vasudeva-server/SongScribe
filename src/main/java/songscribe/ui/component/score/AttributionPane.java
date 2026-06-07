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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.font.FontKey;
import songscribe.ui.renderer.GraphicsState;

/**
 * Self-painting component that renders the song attribution block.
 * <p>
 * Owns the §2.2 display algorithm ({@link #buildLines(Song)}), paints itself,
 * reports a preferred size, and exposes the text for IO/ABC export. The same
 * instance is reused by the dialog preview (Phase 4) and the score (Phase 6);
 * positioning and right-alignment are the parent's responsibility.
 * <p>
 * Two fonts must be set externally before rendering:
 * <ul>
 *   <li>{@link #setFont(Font)} — the {@link FontKey#ATTRIBUTION} font (via
 *       inherited {@code JComponent.setFont})</li>
 *   <li>{@link #setSubAttributionFont(Font)} — the {@link FontKey#SUB_ATTRIBUTION} font</li>
 * </ul>
 */
public class AttributionPane extends ScoreComponent {

    /**
     * One rendered line produced by {@link #buildLines(Song)}: the display text
     * and the font role it should be rendered in.
     */
    public record AttributionLine(String text, FontKey font) {}

    /** Month names in calendar order (1-indexed; index 0 is unused). */
    private static final String[] MONTH_KEYS = {
        "",
        Strings.MONTH_JANUARY,
        Strings.MONTH_FEBRUARY,
        Strings.MONTH_MARCH,
        Strings.MONTH_APRIL,
        Strings.MONTH_MAY,
        Strings.MONTH_JUNE,
        Strings.MONTH_JULY,
        Strings.MONTH_AUGUST,
        Strings.MONTH_SEPTEMBER,
        Strings.MONTH_OCTOBER,
        Strings.MONTH_NOVEMBER,
        Strings.MONTH_DECEMBER
    };

    private static final int MONTH_COUNT = 12;

    @Nullable
    private Font subAttributionFont;

    @Nullable
    private List<AttributionLine> cachedLines;

    /** Sets the sub-attribution font (for date/place lines). */
    public void setSubAttributionFont(Font font) {
        cachedLines = null;
        subAttributionFont = font;
    }

    /** Returns the sub-attribution font, or {@code null} if not yet set. */
    @Nullable
    public Font getSubAttributionFont() {
        return subAttributionFont;
    }

    /**
     * Directly injects precomputed lines for rendering, bypassing the Song model.
     * Used by the dialog preview to show uncommitted UI state without triggering
     * mutation notifications on the live Song.
     *
     * @param lines the lines to render; {@code null} clears the override and
     *              falls back to {@link #buildLines(Song)} from the set Song
     */
    public void setOverrideLines(@Nullable List<AttributionLine> lines) {
        cachedLines = null;
        overrideLines = lines;
        revalidate();
        repaint();
    }

    @Nullable
    private List<AttributionLine> overrideLines;

    @Override
    public void setSong(Song song) {
        cachedLines = null;
        super.setSong(song);
    }

    @Override
    public void setFont(Font font) {
        cachedLines = null;
        super.setFont(font);
    }

    /**
     * Returns the lines to render. When {@code overrideLines} is set, returns
     * them directly. Otherwise returns cached lines from {@link #buildLines(Song)},
     * computing and caching them on first call after any invalidation.
     */
    @Nullable
    private List<AttributionLine> getLines() {
        if (overrideLines != null) {
            return overrideLines;
        }

        if (song == null) {
            return null;
        }

        if (cachedLines == null) {
            cachedLines = buildLines(song);
        }

        return cachedLines;
    }

    /**
     * Builds attribution lines from explicit field values, without requiring a
     * Song instance. Used by the dialog preview to render uncommitted UI state
     * without triggering mutation notifications on the live Song.
     *
     * @param composer              composer name (already resolved, never empty)
     * @param lyricist              lyricist name (already resolved, never empty)
     * @param lyricsSource          lyrics source for the connector
     * @param arrangement           whether this is an arrangement
     * @param unofficialTranslation whether the translation is unofficial
     * @param translatedLyrics      translated lyrics text (used to determine translation credit)
     * @param month                 month index (0 = none, 1–12 = Jan–Dec)
     * @param day                   day of month (0 = none)
     * @param year                  year string (empty = none)
     * @param place                 place string (empty = none)
     * @return ordered list of attribution lines
     */
    public static List<AttributionLine> buildPreviewLines(
        String composer,
        String lyricist,
        Song.LyricsSource lyricsSource,
        boolean arrangement,
        boolean unofficialTranslation,
        String translatedLyrics,
        int month,
        int day,
        String year,
        String place
    ) {
        var credits = new ArrayList<Credit>();
        var lyricistConnector = lyricist.equals(Song.SRI_CHINMOY) ? " by " : lyricsSource.getConnector();
        var composerConnector = composer.equals(Song.SRI_CHINMOY) ? " by " : lyricsSource.getConnector();

        credits.add(new Credit("Words", lyricist, lyricistConnector));
        credits.add(new Credit("Music", composer, composerConnector));

        if (arrangement) {
            credits.add(new Credit("Arrangement", Song.SRI_CHINMOY, " by "));
        }

        var showTranslation = !unofficialTranslation && !translatedLyrics.isEmpty();

        if (showTranslation) {
            credits.add(new Credit("Translation", Song.SRI_CHINMOY, " by "));
        }

        var lines = groupCreditsToLines(credits);
        lines.addAll(buildSubAttributionLinesFromData(month, day, year, place));
        return lines;
    }

    /**
     * Builds the date and place sub-attribution lines from explicit field values.
     */
    private static List<AttributionLine> buildSubAttributionLinesFromData(
        int month, int day, String year, String place
    ) {
        var lines = new ArrayList<AttributionLine>();

        if (!year.isEmpty()) {
            var sb = new StringBuilder();

            if (month > 0) {
                sb.append(Strings.get(MONTH_KEYS[month]));

                if (day > 0) {
                    sb.append(' ');
                    sb.append(day);
                }

                sb.append(", ");
            }

            sb.append(year);
            lines.add(new AttributionLine(sb.toString(), FontKey.SUB_ATTRIBUTION));
        }

        if (!place.isEmpty()) {
            lines.add(new AttributionLine(place, FontKey.SUB_ATTRIBUTION));
        }

        return lines;
    }

    /**
     * Builds the ordered attribution lines for the given song.
     * <p>
     * Implementation of the §2.2 display algorithm:
     * <ol>
     *   <li>Assign a connector to each credit using {@code connectorFor}: {@code " by "}
     *       when the person is Sri Chinmoy, otherwise the {@code lyricsSource} connector.</li>
     *   <li>Emit credits in canonical role order: Words, Music, [Arrangement], [Translation].</li>
     *   <li>Stably group credits that share the same {@code (person, connector)} pair.</li>
     *   <li>Oxford-join the roles within each group.</li>
     *   <li>Append date and place sub-lines in {@link FontKey#SUB_ATTRIBUTION}.</li>
     * </ol>
     *
     * @param song the song to build lines for
     * @return ordered list of attribution lines (attribution lines first, then sub-attribution)
     */
    public static List<AttributionLine> buildLines(Song song) {
        var credits = buildCredits(song);
        var lines = groupCreditsToLines(credits);
        lines.addAll(buildSubAttributionLines(song));
        return lines;
    }

    /**
     * Stably groups credits by {@code (person, connector)} pair and emits one
     * {@link AttributionLine} per group, with roles Oxford-joined.
     * LinkedHashMap preserves first-appearance order so lines appear in canonical role order.
     *
     * @param credits the credit list in canonical order
     * @return attribution lines (no sub-attribution lines)
     */
    private static List<AttributionLine> groupCreditsToLines(List<Credit> credits) {
        // Stably group by (person, connector), preserving first-appearance order.
        // LinkedHashMap maintains insertion order, so groups appear in canonical order.
        var groups = new LinkedHashMap<String, CreditGroup>();

        for (var credit : credits) {
            var key = credit.person() + " " + credit.connector();
            groups.computeIfAbsent(key, k -> new CreditGroup(credit.person(), credit.connector()))
                  .addRole(credit.role());
        }

        var lines = new ArrayList<AttributionLine>();

        for (var group : groups.values()) {
            lines.add(new AttributionLine(
                oxfordJoin(group.roles()) + group.connector() + group.person(),
                FontKey.ATTRIBUTION
            ));
        }

        return lines;
    }

    /**
     * Builds the raw credit list in canonical role order (Words, Music,
     * [Arrangement], [Translation]).
     */
    private static List<Credit> buildCredits(Song song) {
        var credits = new ArrayList<Credit>();
        var lyricist = song.getLyricist();
        var composer = song.getComposer();

        credits.add(new Credit("Words", lyricist, connectorFor(lyricist, song)));
        credits.add(new Credit("Music", composer, connectorFor(composer, song)));

        if (song.isArrangement()) {
            credits.add(new Credit("Arrangement", Song.SRI_CHINMOY, " by "));
        }

        if (showTranslation(song)) {
            credits.add(new Credit("Translation", Song.SRI_CHINMOY, " by "));
        }

        return credits;
    }

    /**
     * Returns {@code " by "} when the person is Sri Chinmoy, otherwise the
     * {@code lyricsSource} connector. The connector is a function of the person,
     * not the role.
     */
    private static String connectorFor(String person, Song song) {
        if (person.equals(Song.SRI_CHINMOY)) {
            return " by ";
        }

        return song.getLyricsSource().getConnector();
    }

    /**
     * Returns {@code true} when the song has a non-empty official translation
     * (unofficial translations are not shown in the attribution).
     */
    private static boolean showTranslation(Song song) {
        return !song.isUnofficialTranslation() && !song.getTranslatedLyrics().isEmpty();
    }

    /**
     * Builds the date and place sub-attribution lines. Mirrors the logic from
     * {@code SongSettingsDialog.TextTab.getDateString()}, operating on Song fields
     * rather than UI widget state.
     */
    private static List<AttributionLine> buildSubAttributionLines(Song song) {
        var lines = new ArrayList<AttributionLine>();
        var year = song.getYear();

        if (!year.isEmpty()) {
            var sb = new StringBuilder();
            var month = song.getMonth();

            if (month > 0) {
                sb.append(Strings.get(MONTH_KEYS[month]));
                var day = song.getDay();

                if (day > 0) {
                    sb.append(' ');
                    sb.append(day);
                }

                sb.append(", ");
            }

            sb.append(year);
            lines.add(new AttributionLine(sb.toString(), FontKey.SUB_ATTRIBUTION));
        }

        var place = song.getPlace();

        if (!place.isEmpty()) {
            lines.add(new AttributionLine(place, FontKey.SUB_ATTRIBUTION));
        }

        return lines;
    }

    /**
     * Joins role names with Oxford-comma style:
     * <ul>
     *   <li>1 item → {@code "A"}</li>
     *   <li>2 items → {@code "A and B"}</li>
     *   <li>n items → {@code "A, B, ... and Z"}</li>
     * </ul>
     */
    static String oxfordJoin(List<String> items) {
        int size = items.size();

        if (size == 1) {
            return items.get(0);
        }

        if (size == 2) {
            return items.get(0) + " and " + items.get(1);
        }

        var sb = new StringBuilder();

        for (int i = 0; i < size - 2; i++) {
            sb.append(items.get(i));
            sb.append(", ");
        }

        sb.append(items.get(size - 2));
        sb.append(" and ");
        sb.append(items.get(size - 1));
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Text accessors for IO / ABC export
    // -------------------------------------------------------------------------

    /**
     * Returns the full attribution text as newline-joined lines, for use by IO.
     *
     * @param song the song
     * @return attribution text with lines separated by {@code \n}
     */
    public static String attributionText(Song song) {
        return buildLines(song).stream()
            .map(AttributionLine::text)
            .collect(Collectors.joining("\n"));
    }

    /**
     * Returns the attribution as a single space-separated line, for use by ABC export.
     *
     * @param song the song
     * @return attribution text with lines separated by {@code " "}
     */
    public static String attributionTextSingleLine(Song song) {
        return buildLines(song).stream()
            .map(AttributionLine::text)
            .collect(Collectors.joining(" "));
    }

    // -------------------------------------------------------------------------
    // ScoreComponent rendering
    // -------------------------------------------------------------------------

    @Override
    protected void render(Graphics2D g2) {
        var attributionFont = getFont();

        if (attributionFont == null || subAttributionFont == null) {
            return;
        }

        var lines = getLines();

        if (lines == null) {
            return;
        }

        if (lines.isEmpty()) {
            return;
        }

        try (var ignored = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            g2.setColor(Color.BLACK);
            var y = (float) marginTop;

            for (var line : lines) {
                var font = line.font() == FontKey.ATTRIBUTION ? attributionFont : subAttributionFont;
                g2.setFont(font);
                var metrics = g2.getFontMetrics();
                y += metrics.getAscent();
                var x = centerX(line.text(), g2);
                g2.drawString(line.text(), x, y);
                y += metrics.getDescent() + metrics.getLeading();
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        var attributionFont = getFont();

        if (attributionFont == null || subAttributionFont == null) {
            return new Dimension(0, 0);
        }

        var lines = getLines();

        if (lines == null) {
            return new Dimension(0, 0);
        }

        if (lines.isEmpty()) {
            return new Dimension(0, 0);
        }

        int maxWidth = 0;
        float totalHeight = marginTop;

        for (var line : lines) {
            var font = line.font() == FontKey.ATTRIBUTION ? attributionFont : subAttributionFont;
            var metrics = getFontMetrics(font);
            var lineWidth = metrics.stringWidth(line.text());
            maxWidth = Math.max(maxWidth, lineWidth);
            totalHeight += metrics.getHeight();
        }

        totalHeight += marginBottom;
        var lineWidthPx = song != null ? song.getLineWidthPx() : 0;
        return new Dimension(Math.max(maxWidth, lineWidthPx), (int) totalHeight);
    }

    /**
     * Computes the X coordinate to paint {@code text} centered within this
     * component's own width.
     */
    private float centerX(String text, Graphics2D g2) {
        var textWidth = g2.getFontMetrics().stringWidth(text);
        var componentWidth = getWidth();

        if (componentWidth == 0) {
            // Fall back to song line width when the component has not been laid out yet.
            componentWidth = song != null ? song.getLineWidthPx() : 0;
        }

        return (componentWidth - textWidth) / 2.0f;
    }

    // -------------------------------------------------------------------------
    // Private helper types
    // -------------------------------------------------------------------------

    /** A single credit entry before grouping. */
    private record Credit(String role, String person, String connector) {}

    /** Accumulates roles for a single (person, connector) group. */
    private static final class CreditGroup {
        private final String person;
        private final String connector;
        private final List<String> roles = new ArrayList<>();

        CreditGroup(String person, String connector) {
            this.person = person;
            this.connector = connector;
        }

        void addRole(String role) {
            roles.add(role);
        }

        String person() {
            return person;
        }

        String connector() {
            return connector;
        }

        List<String> roles() {
            return roles;
        }
    }
}
