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

package songscribe.util;

import java.util.List;
import java.util.function.Predicate;

/**
 * One step that runs on a load, over a context that every step of the run reads and writes.
 * A stage sees the context as the stages before it left it, and runs unconditionally unless
 * made through {@link #when}.
 *
 * <p>Lives in {@code util} because both {@code io} and {@code dom} use it, and {@code dom} does
 * not depend on {@code io}.
 */
@FunctionalInterface
public interface LoadStage<C> {

    void apply(C context);

    default boolean appliesTo(C context) {
        return true;
    }

    static <C> LoadStage<C> when(Predicate<? super C> predicate, LoadStage<C> step) {
        return new LoadStage<>() {
            @Override
            public void apply(C context) {
                step.apply(context);
            }

            @Override
            public boolean appliesTo(C context) {
                return predicate.test(context) && step.appliesTo(context);
            }
        };
    }

    static <C> void runAll(List<? extends LoadStage<? super C>> stages, C context) {
        for (var stage : stages) {
            if (stage.appliesTo(context)) {
                stage.apply(context);
            }
        }
    }
}
