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

/**
 * One thing wrong with the song as it is now: a {@link Violation} tied to the rule it breaks.
 * A finding has no severity of its own; what it blocks comes from its rule's flags.
 *
 * @param rule      the rule the song breaks
 * @param violation where and how the song breaks it
 */
public record LintFinding(LintRule rule, Violation violation) {

    /**
     * The heading the Messages panel shows above this finding.
     *
     * @return the violation's heading key
     */
    public String headingKey() {
        return violation.headingKey();
    }

    /**
     * What is wrong, as the user reads it.
     *
     * @return the violation's resolved text
     */
    public String description() {
        return violation.description();
    }

    /**
     * Where the finding sits in the score.
     *
     * @return the violation's mark
     */
    public LintMark mark() {
        return violation.mark();
    }
}
