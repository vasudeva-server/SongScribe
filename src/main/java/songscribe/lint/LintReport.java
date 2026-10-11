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

package songscribe.lint;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import songscribe.dom.StaffElement;

/**
 * Every lint finding for one song as it was when it was linted. A state, not an event:
 * {@link Linter} holds the current one and replaces it after every change to the song.
 *
 * <p>Immutable. The per-element queries ({@link #isFlagged}, {@link #isAnnotationFlagged},
 * {@link #missingLyricVerses}) are answered from maps built once in the constructor, so the
 * score view does one lookup per element on every paint. They compare elements by identity:
 * {@link StaffElement} does not override {@code equals}, and a finding names the very object
 * that is in the song.
 *
 * <p>A finding names an element, not an index, so after an edit and before the next lint run
 * it still names the element it was found on. An element that edit removed is still named;
 * a reader that resolves a position checks that the element is still in the song.
 */
public final class LintReport {

    /** The report of a song with no finding, and the report before the first lint run. */
    public static final LintReport EMPTY = new LintReport(List.of());

    private final List<LintFinding> findings;
    private final Set<StaffElement> flagged = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<StaffElement> annotationFlagged = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<StaffElement, Set<Integer>> missingLyricVerses = new IdentityHashMap<>();

    /**
     * Builds a report over {@code findings}.
     *
     * @param findings the findings, in rule order and, within a rule, in song order; the
     *                 report keeps a copy, so later changes to the list do not reach it
     */
    public LintReport(List<LintFinding> findings) {
        this.findings = List.copyOf(findings);

        for (var finding : this.findings) {
            switch (finding.mark()) {
                case LintMark.Flagged mark -> flagged.add(mark.element());
                case LintMark.FlaggedAnnotation mark -> annotationFlagged.add(mark.element());
                case LintMark.MissingLyric mark ->
                    missingLyricVerses.computeIfAbsent(mark.element(), element -> new TreeSet<>())
                                      .add(mark.verse());
            }
        }
    }

    /**
     * Every finding in this report.
     *
     * @return an unmodifiable list, in {@link LintRule} declaration order and, within a rule,
     *         in song order
     */
    public List<LintFinding> findings() {
        return findings;
    }

    /**
     * The findings the score shows at {@code mark}.
     *
     * @return the findings whose mark equals {@code mark}, in the order of {@link #findings()};
     *         empty when none does
     */
    public List<LintFinding> findingsAt(LintMark mark) {
        return findings.stream().filter(finding -> finding.mark().equals(mark)).toList();
    }

    /**
     * Whether the song had no finding.
     *
     * @return {@code true} when {@link #findings()} is empty
     */
    public boolean isEmpty() {
        return findings.isEmpty();
    }

    /**
     * Whether some finding blocks what {@code flag} names.
     *
     * @return {@code true} when the rule of at least one finding carries {@code flag}
     */
    public boolean hasFlag(LintRule.Flag flag) {
        for (var finding : findings) {
            if (finding.rule().hasFlag(flag)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the score draws {@code element} itself in the lint color.
     *
     * @return {@code true} when some {@link LintMark.Flagged} mark names this very object
     */
    public boolean isFlagged(StaffElement element) {
        return flagged.contains(element);
    }

    /**
     * Whether the score draws a squiggle under the annotation text of {@code element}.
     *
     * @return {@code true} when some {@link LintMark.FlaggedAnnotation} mark names this very
     *         object
     */
    public boolean isAnnotationFlagged(StaffElement element) {
        return annotationFlagged.contains(element);
    }

    /**
     * The verses in which {@code element} lacks a lyric.
     *
     * @return an unmodifiable set of the 1-based verse of every {@link LintMark.MissingLyric}
     *         mark that names this very object, in ascending order; empty when none does
     */
    public Set<Integer> missingLyricVerses(StaffElement element) {
        var verses = missingLyricVerses.get(element);
        return verses == null ? Set.of() : Collections.unmodifiableSet(verses);
    }
}
