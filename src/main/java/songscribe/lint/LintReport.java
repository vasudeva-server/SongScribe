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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import songscribe.dom.StaffElement;

/**
 * Every lint finding for one song as it was when it was linted. A state, not an event:
 * {@link Linter} holds the current one and replaces it after every change to the song.
 *
 * <p>Immutable: a lint run builds a new report rather than changing the current one, so a
 * reader that holds a report can tell whether it is still current by comparing it with
 * {@link Linter#getReport()} by identity.
 *
 * <p>The marks on an element ({@link #marksOn}) are answered from one map built in the
 * constructor, so the score view does one lookup per element on every paint. The map
 * compares elements by identity: {@link StaffElement} does not override {@code equals}, and a
 * finding names the very object that is in the song.
 *
 * <p>A finding names an element, not an index, so after an edit and before the next lint run
 * it still names the element it was found on. An element that edit removed is still named;
 * a reader that resolves a position checks that the element is still in the song.
 */
public final class LintReport {

    /** The report of a song with no finding, and the report before the first lint run. */
    public static final LintReport EMPTY = new LintReport(List.of());

    private final List<LintFinding> findings;

    /** The marks of the findings, by the element each names; each list unmodifiable. */
    private final Map<StaffElement, List<LintMark>> marksByElement = new IdentityHashMap<>();

    /**
     * Builds a report over {@code findings}.
     *
     * @param findings the findings, in rule order and, within a rule, in song order; the
     *                 report keeps a copy, so later changes to the list do not reach it
     */
    public LintReport(List<LintFinding> findings) {
        this.findings = List.copyOf(findings);

        for (var finding : this.findings) {
            var mark = finding.mark();
            var marks = marksByElement.computeIfAbsent(mark.element(), _ -> new ArrayList<>());

            if (!marks.contains(mark)) {
                marks.add(mark);
            }
        }

        marksByElement.replaceAll((_, marks) -> List.copyOf(marks));
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
     * @param mark the mark to look up; a mark no finding carries is allowed
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
     * @param flag what a finding may block
     * @return {@code true} when the rule of at least one finding carries {@code flag};
     *         {@code false} for a report with no finding
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
     * The marks the findings of this report put on {@code element}.
     *
     * @param element the element to look up, compared by identity; an element no finding
     *                names, or one no longer in the song, is allowed
     * @return an unmodifiable list holding each distinct mark whose {@link LintMark#element()}
     *         is this very object once, in the order of the first finding that carries it in
     *         {@link #findings()}; empty when no finding names the element
     */
    public List<LintMark> marksOn(StaffElement element) {
        var marks = marksByElement.get(element);
        return marks == null ? List.of() : marks;
    }
}
