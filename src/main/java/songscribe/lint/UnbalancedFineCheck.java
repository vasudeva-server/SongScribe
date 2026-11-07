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
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * The check of {@link LintRule#UNBALANCED_FINE}: every Fine stands where the song can end, and
 * every Fine and every reference to one come in a pair, the Fine first.
 *
 * <p>An element's annotation is the one text annotation it carries. Two kinds of annotation
 * take part, and every other annotation is ignored:
 *
 * <ul>
 *   <li>A <em>Fine</em> is an annotation whose whole text, without surrounding whitespace, is
 *       {@value #FINE_TEXT} in any case: {@code Fine} and {@code FINE} are both a Fine.</li>
 *   <li>A <em>reference</em> is any other annotation whose text holds {@value #FINE_TEXT} as a
 *       whole word, in any case. {@code D.C. al Fine}, {@code D.C. al fine} and
 *       {@code al Fine.} are references; {@code Finest} is not.</li>
 * </ul>
 *
 * <p>The reference test is deliberately the bare word: any annotation with {@value #FINE_TEXT}
 * as a whole word, in any case, is taken to refer to a Fine, whatever else it says. So
 * {@code a fine tone} with no Fine before it is a finding.
 *
 * <p>Each violation carries the mark {@link LintMark.FlaggedAnnotation} on the annotated
 * element, and one element may carry several. The first three are listed under
 * {@link LintHeading#FINE_UNBALANCED}, the last under {@link LintHeading#FINE_MISSING}:
 *
 * <ul>
 *   <li>{@link Strings#LINT_REASON_FINE_NOT_ON_BAR} on a Fine on an element that is not a
 *       barline or a repeat.</li>
 *   <li>{@link Strings#LINT_REASON_FINE_ON_LAST_BAR} on a Fine on the last element of the song:
 *       the last element of the last line that has elements. The song ends there anyway.</li>
 *   <li>{@link Strings#LINT_REASON_FINE_NOT_REFERENCED} on a Fine with no reference after it in
 *       song order. One reference answers every Fine before it. A Fine on the last element of
 *       the song is never flagged with this reason, because nothing can follow it.</li>
 *   <li>{@link Strings#LINT_REASON_FINE_REFERENCE_WITHOUT_FINE} on a reference with no Fine
 *       before it in song order.</li>
 * </ul>
 */
final class UnbalancedFineCheck implements LintCheck {

    private static final String FINE_TEXT = "fine";

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\bfine\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Finds every misplaced or unmatched Fine, and every reference with no Fine, in
     * {@code song}.
     *
     * @param song the song to examine; read only
     * @return the violations
     * @invariant a song with no Fine and no reference gives no violation
     * @invariant a Fine on a bar before the last element, followed later by a reference, gives
     *     no violation, and neither does that reference
     */
    @Override
    public List<Violation> check(Song song) {
        return new Pass(lastElementOf(song)).run(song);
    }

    private enum Problem {
        NOT_ON_BAR(LintHeading.FINE_UNBALANCED, Strings.LINT_REASON_FINE_NOT_ON_BAR),
        ON_LAST_BAR(LintHeading.FINE_UNBALANCED, Strings.LINT_REASON_FINE_ON_LAST_BAR),
        NOT_REFERENCED(LintHeading.FINE_UNBALANCED, Strings.LINT_REASON_FINE_NOT_REFERENCED),
        REFERENCE_WITHOUT_FINE(LintHeading.FINE_MISSING, Strings.LINT_REASON_FINE_REFERENCE_WITHOUT_FINE);

        private final LintHeading heading;
        private final String reasonKey;

        Problem(LintHeading heading, String reasonKey) {
            this.heading = heading;
            this.reasonKey = reasonKey;
        }
    }

    private static final class Pass {

        private final @Nullable StaffElement lastElement;
        private final List<Violation> found = new ArrayList<>();
        private final List<StaffElement> unreferencedFines = new ArrayList<>();
        private boolean fineSeen = false;

        Pass(@Nullable StaffElement lastElement) {
            this.lastElement = lastElement;
        }

        List<Violation> run(Song song) {
            for (var line : song.getLines()) {
                line.getElements().forEach(this::visit);
            }

            for (var fine : unreferencedFines) {
                flag(fine, Problem.NOT_REFERENCED);
            }

            return found;
        }

        private void visit(StaffElement element) {
            var attachment = element.findAttachment(AnnotationAttachment.class);

            if (attachment == null) {
                return;
            }

            var text = attachment.getAnnotation().text();

            if (text.strip().equalsIgnoreCase(FINE_TEXT)) {
                visitFine(element);
            } else if (REFERENCE_PATTERN.matcher(text).find()) {
                visitReference(element);
            }
        }

        private void visitFine(StaffElement element) {
            fineSeen = true;

            if (!element.getType().isBar()) {
                flag(element, Problem.NOT_ON_BAR);
            }

            if (element == lastElement) {
                flag(element, Problem.ON_LAST_BAR);
            } else {
                unreferencedFines.add(element);
            }
        }

        private void visitReference(StaffElement element) {
            if (!fineSeen) {
                flag(element, Problem.REFERENCE_WITHOUT_FINE);
            }

            unreferencedFines.clear();
        }

        private void flag(StaffElement element, Problem problem) {
            found.add(new Violation(problem.heading,
                                    Strings.get(problem.reasonKey),
                                    new LintMark.FlaggedAnnotation(element)));
        }
    }

    private static @Nullable StaffElement lastElementOf(Song song) {
        var lines = song.getLines();

        for (var i = lines.size() - 1; i >= 0; i--) {
            var elements = lines.get(i).getElements();

            if (!elements.isEmpty()) {
                return elements.getLast();
            }
        }

        return null;
    }
}
