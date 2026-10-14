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
 * <p>Immutable: a reader can tell whether its report is current by comparing it with
 * {@link Linter#getReport()} by identity.
 *
 * <p>A finding names an element, not an index, and may name one that an edit has since removed;
 * a reader that resolves a position checks that the element is still in the song.
 */
public final class LintReport {

    public static final LintReport EMPTY = new LintReport(List.of());

    private final List<LintFinding> findings;

    private final Map<StaffElement, List<LintMark>> marksByElement = new IdentityHashMap<>();

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

    public List<LintFinding> findings() {
        return findings;
    }

    public List<LintFinding> findingsAt(LintMark mark) {
        return findings.stream().filter(finding -> finding.mark().equals(mark)).toList();
    }

    public boolean isEmpty() {
        return findings.isEmpty();
    }

    public boolean hasFlag(LintRule.Flag flag) {
        for (var finding : findings) {
            if (finding.rule().hasFlag(flag)) {
                return true;
            }
        }

        return false;
    }

    public List<LintMark> marksOn(StaffElement element) {
        var marks = marksByElement.get(element);
        return marks == null ? List.of() : marks;
    }
}
