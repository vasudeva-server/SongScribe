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
 * {@link Strings#LINT_FINE_UNBALANCED}, the last under {@link Strings#LINT_FINE_MISSING}:
 *
 * <ul>
 *   <li>{@link Strings#LINT_REASON_FINE_NOT_ON_BAR} on a Fine on an element that is not a
 *       barline or a repeat ({@code ElementType.isBar()} is {@code false}).</li>
 *   <li>{@link Strings#LINT_REASON_FINE_ON_LAST_BAR} on a Fine on the last element of the song:
 *       the last element of the last line that has elements. The song ends there anyway.</li>
 *   <li>{@link Strings#LINT_REASON_FINE_NOT_REFERENCED} on a Fine with no reference after it in
 *       song order. One reference answers every Fine before it.</li>
 *   <li>{@link Strings#LINT_REASON_FINE_REFERENCE_WITHOUT_FINE} on a reference with no Fine
 *       before it in song order.</li>
 * </ul>
 */
final class UnbalancedFineCheck implements LintCheck {

    /** The text of a Fine, compared without case. */
    private static final String FINE_TEXT = "fine";

    /** What makes an annotation other than a Fine a reference to one. */
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\bfine\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Finds every misplaced or unmatched Fine, and every reference with no Fine, in
     * {@code song}.
     *
     * @param song the song to examine; read only
     * @return the violations, in the song order of the elements they flag; violations on one
     *     element come in the order of the list in the class description
     * @invariant a song with no Fine and no reference gives no violation
     * @invariant a Fine on a bar before the last element, followed later by a reference, gives
     *     no violation, and neither does that reference
     */
    @Override
    public List<Violation> check(Song song) {
        return new Pass(lastElementOf(song)).run(song);
    }

    /** A Fine still waiting for a reference, with its position from {@link LintCheck#walk}. */
    private record WaitingFine(StaffElement element, int position) {
    }

    /** Each kind of violation this check finds: the heading it is listed under, and its text. */
    private enum Problem {
        NOT_ON_BAR(Strings.LINT_FINE_UNBALANCED, Strings.LINT_REASON_FINE_NOT_ON_BAR),
        ON_LAST_BAR(Strings.LINT_FINE_UNBALANCED, Strings.LINT_REASON_FINE_ON_LAST_BAR),
        NOT_REFERENCED(Strings.LINT_FINE_UNBALANCED, Strings.LINT_REASON_FINE_NOT_REFERENCED),
        REFERENCE_WITHOUT_FINE(Strings.LINT_FINE_MISSING, Strings.LINT_REASON_FINE_REFERENCE_WITHOUT_FINE);

        private final String headingKey;
        private final String reasonKey;

        Problem(String headingKey, String reasonKey) {
            this.headingKey = headingKey;
            this.reasonKey = reasonKey;
        }
    }

    /** The state of one forward pass over a song. */
    private static final class Pass {

        private final @Nullable StaffElement lastElement;
        private final SongOrderedViolations found = new SongOrderedViolations();
        private final List<WaitingFine> unreferencedFines = new ArrayList<>();
        private boolean fineSeen = false;

        Pass(@Nullable StaffElement lastElement) {
            this.lastElement = lastElement;
        }

        List<Violation> run(Song song) {
            LintCheck.walk(song, this::visit);

            for (var fine : unreferencedFines) {
                flag(fine.element(), fine.position(), Problem.NOT_REFERENCED);
            }

            return found.inSongOrder();
        }

        private void visit(StaffElement element, int position) {
            var attachment = element.findAttachment(AnnotationAttachment.class);

            if (attachment == null) {
                return;
            }

            var text = attachment.getAnnotation().text();

            if (text.strip().equalsIgnoreCase(FINE_TEXT)) {
                visitFine(element, position);
            } else if (REFERENCE_PATTERN.matcher(text).find()) {
                visitReference(element, position);
            }
        }

        private void visitFine(StaffElement element, int position) {
            fineSeen = true;
            unreferencedFines.add(new WaitingFine(element, position));

            if (!element.getType().isBar()) {
                flag(element, position, Problem.NOT_ON_BAR);
            }

            if (element == lastElement) {
                flag(element, position, Problem.ON_LAST_BAR);
            }
        }

        private void visitReference(StaffElement element, int position) {
            if (!fineSeen) {
                flag(element, position, Problem.REFERENCE_WITHOUT_FINE);
            }

            unreferencedFines.clear();
        }

        private void flag(StaffElement element, int position, Problem problem) {
            found.add(position, new Violation(Strings.get(problem.headingKey),
                                              Strings.get(problem.reasonKey),
                                              new LintMark.FlaggedAnnotation(element)));
        }
    }

    /**
     * The last element of the song.
     *
     * @return the last element of the last line that has elements, or {@code null} when no
     *         line has any
     */
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
