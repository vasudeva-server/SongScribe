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
package songscribe.ui.dialog;

import java.util.List;

/**
 * What {@link DialogBackEnd#validate} answers: whether the values a dialog gathered may be
 * applied, and if not, why not.
 *
 * <p><strong>Validity is the absence of failures, not a separate flag.</strong> There is no state
 * in which a result claims to be valid while naming reasons it is not, because {@link #isValid()}
 * is derived from {@link #failures()} rather than stored beside it. The corollary a caller may
 * rely on: a rejection always carries at least one failure, so a user who is blocked always has
 * something to read. An empty failure list means the input is acceptable — it never means
 * "rejected, reason unknown".
 *
 * <p>The failures keep the order the back end reported them in, and that is the order a presenter
 * shows them in; a back end that has a most-important failure puts it first. Instances are
 * immutable: {@link #failures()} answers an unmodifiable list unaffected by later changes to the
 * list the constructor was given.
 *
 * @param failures every reason the input was refused, in presentation order; empty when it was
 *                 accepted
 */
public record ValidationResult(List<ValidationFailure> failures) {

    public ValidationResult {
        failures = List.copyOf(failures);
    }

    /**
     * The outcome for input a back end accepts, and the answer from a back end with nothing to
     * reject.
     *
     * @return a result carrying no failures
     */
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /**
     * The outcome for input refused for a single reason. Use the canonical constructor when there
     * is more than one.
     *
     * @param failure the reason the input was refused
     * @return a result carrying exactly {@code failure}
     */
    public static ValidationResult invalid(ValidationFailure failure) {
        return new ValidationResult(List.of(failure));
    }

    /**
     * @return {@code true} when the input may be applied, which is exactly when
     *         {@link #failures()} is empty
     */
    public boolean isValid() {
        return failures.isEmpty();
    }
}
