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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import songscribe.Strings;
import songscribe.font.FontKey;

/**
 * Pure, UI-free formatter that turns a {@link SongMetadata} record (plus the
 * translation flag) into attribution display lines and plain text strings.
 * <p>
 * Used by rendering, IO, ABC export, and (later) MusicXML. Contains no Swing
 * or AWT imports.
 *
 * <h3>Display pipeline</h3>
 * <pre>
 *  SongMetadata
 *      └──► buildCredits(data, showTranslation)
 *               (assign connector per person/lyricsSource; emit roles in
 *                canonical order: Words, Music, [Arrangement], [Translation])
 *      └──► groupCreditsToLines(credits)
 *               (stable group by (person, connector) via LinkedHashMap;
 *                Oxford-join roles within each group)
 *      └──► buildSubAttributionLines(data)
 *               (date line: [Month [Day, ]] year; place line)
 *      └──► List&lt;AttributionLine&gt;
 *               (ATTRIBUTION lines first, then SUB_ATTRIBUTION lines)
 * </pre>
 *
 * <p>All methods are static; no instances are needed.
 */
public final class AttributionFormatter {

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

    /**
     * Number of trailing items the Oxford join handles specially: the last item
     * is preceded by {@code " and "} and the penultimate by a comma.
     */
    private static final int OXFORD_TAIL_COUNT = 2;

    private AttributionFormatter() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds the ordered list of attribution lines for the given metadata.
     * <p>
     * This is the canonical display path used by rendering, IO, and export.
     * The {@code showTranslation} flag should be derived from
     * {@link Song#showTranslation()} when operating on a committed song, or
     * computed inline when operating on uncommitted dialog state.
     *
     * @param data            the song metadata
     * @param showTranslation {@code true} when the song has a non-empty official translation
     * @return ordered list of attribution lines (attribution first, sub-attribution last)
     */
    public static List<AttributionLine> lines(SongMetadata data, boolean showTranslation) {
        var credits = buildCredits(data, showTranslation);
        var lines = groupCreditsToLines(credits);
        lines.addAll(buildSubAttributionLines(data));
        return lines;
    }

    /**
     * Returns the full attribution text as newline-joined lines, for use by IO.
     *
     * @param data            the song metadata
     * @param showTranslation {@code true} when the song has a non-empty official translation
     * @return attribution text with lines separated by {@code \n}
     */
    public static String text(SongMetadata data, boolean showTranslation) {
        return lines(data, showTranslation).stream()
            .map(AttributionLine::text)
            .collect(Collectors.joining("\n"));
    }

    /**
     * Returns the attribution as a single space-separated line, for use by ABC export.
     *
     * @param data            the song metadata
     * @param showTranslation {@code true} when the song has a non-empty official translation
     * @return attribution text with lines separated by {@code " "}
     */
    public static String singleLineText(SongMetadata data, boolean showTranslation) {
        return lines(data, showTranslation).stream()
            .map(AttributionLine::text)
            .collect(Collectors.joining(" "));
    }

    // -------------------------------------------------------------------------
    // Private display algorithm
    // -------------------------------------------------------------------------

    /**
     * Builds the raw credit list in canonical role order:
     * Words, Music, [Arrangement], [Translation].
     */
    private static List<Credit> buildCredits(SongMetadata data, boolean showTranslation) {
        var credits = new ArrayList<Credit>();
        var lyricist = data.lyricist();
        var composer = data.composer();

        credits.add(new Credit("Words", lyricist, data.connectorFor(lyricist)));
        credits.add(new Credit("Music", composer, data.connectorFor(composer)));

        // Arrangement and Translation are always credited to Sri Chinmoy; route
        // them through connectorFor so the " by " connector has a single owner.
        if (data.arrangement()) {
            credits.add(new Credit("Arrangement", Song.SRI_CHINMOY, data.connectorFor(Song.SRI_CHINMOY)));
        }

        if (showTranslation) {
            credits.add(new Credit("Translation", Song.SRI_CHINMOY, data.connectorFor(Song.SRI_CHINMOY)));
        }

        return credits;
    }

    /**
     * Stably groups credits by {@code (person, connector)} pair and emits one
     * {@link AttributionLine} per group, with roles Oxford-joined.
     * <p>
     * {@link LinkedHashMap} preserves first-appearance order so lines appear in
     * canonical role order.
     */
    private static List<AttributionLine> groupCreditsToLines(List<Credit> credits) {
        var groups = new LinkedHashMap<String, CreditGroup>();

        for (var credit : credits) {
            var key = credit.person() + " " + credit.connector();
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
     * Builds the date and place sub-attribution lines.
     */
    private static List<AttributionLine> buildSubAttributionLines(SongMetadata data) {
        var lines = new ArrayList<AttributionLine>();
        var year = data.year();

        if (!year.isEmpty()) {
            var sb = new StringBuilder();
            var month = data.month();

            if (month > 0) {
                sb.append(Strings.get(MONTH_KEYS[month]));
                var day = data.day();

                if (day > 0) {
                    sb.append(' ');
                    sb.append(day);
                }

                sb.append(", ");
            }

            sb.append(year);
            lines.add(new AttributionLine(sb.toString(), FontKey.SUB_ATTRIBUTION));
        }

        var place = data.place();

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
     *   <li>n items → {@code "A, B, … and Z"}</li>
     * </ul>
     */
    public static String oxfordJoin(List<String> items) {
        int size = items.size();

        if (size == 0) {
            return "";
        }

        if (size == 1) {
            return items.get(0);
        }

        if (size == 2) {
            return items.get(0) + " and " + items.get(1);
        }

        var sb = new StringBuilder();

        for (int i = 0; i < size - OXFORD_TAIL_COUNT; i++) {
            sb.append(items.get(i));
            sb.append(", ");
        }

        sb.append(items.get(size - OXFORD_TAIL_COUNT));
        sb.append(" and ");
        sb.append(items.get(size - 1));
        return sb.toString();
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
