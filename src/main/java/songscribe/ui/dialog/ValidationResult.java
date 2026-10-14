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
 * What {@link DialogController#validate} answers: whether the values a dialog gathered may be
 * committed, and if not, why not.
 *
 * <p><strong>Validity is the absence of failures, not a separate flag:</strong> a rejection always
 * carries at least one failure, and an empty list never means "rejected, reason unknown".
 *
 * <p>The failures keep the order the controller reported them in, which is the order a presenter
 * shows them in. Instances are immutable: {@link #failures()} answers an unmodifiable list
 * unaffected by later changes to the list the constructor was given.
 *
 * @param failures in presentation order; empty when the input was accepted
 */
public record ValidationResult(List<ValidationFailure> failures) {

    public ValidationResult {
        failures = List.copyOf(failures);
    }

    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    public static ValidationResult invalid(ValidationFailure failure) {
        return new ValidationResult(List.of(failure));
    }

    public boolean isValid() {
        return failures.isEmpty();
    }
}
