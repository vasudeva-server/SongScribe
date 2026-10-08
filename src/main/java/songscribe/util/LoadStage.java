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
 * <p>
 * A load is an ordered list of stages, and {@link #runAll} is what runs one: the list's order is
 * the execution order, and a stage sees the context as the stages before it left it. A stage runs
 * unconditionally unless it was made through {@link #when}, which gates it on the context.
 * <p>
 * It lives in {@code util} because both the legacy reader's migration pipeline in {@code io} and
 * the load passes in {@code dom} are lists of stages, and {@code dom} does not depend on
 * {@code io}.
 *
 * @param <C> what the stages of one run share
 */
@FunctionalInterface
public interface LoadStage<C> {

    /**
     * Applies this stage to {@code context}.
     *
     * @param context the run's context, as the stages before this one left it
     * @effects whatever this stage does to {@code context} and to what it reaches
     */
    void apply(C context);

    /**
     * Whether {@link #runAll} applies this stage to {@code context}.
     *
     * @param context the run's context, as the stages before this one left it
     * @return {@code true} when this stage runs against {@code context}; always {@code true} for a
     *     stage not made through {@link #when}
     */
    default boolean appliesTo(C context) {
        return true;
    }

    /**
     * Returns {@code step} gated on {@code predicate}.
     *
     * @param predicate decides, against the context as the earlier stages left it, whether the
     *                  stage runs
     * @param step      what the stage does when it runs
     * @param <C>       what the stages of one run share
     * @return a stage that applies {@code step}, and that applies to a context exactly when
     *     {@code predicate} accepts it and {@code step} applies to it
     */
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

    /**
     * Runs {@code stages} over {@code context}, in list order.
     *
     * @param stages  the run's stages; list order is execution order
     * @param context the run's context
     * @param <C>     what the stages share
     * @effects applies each stage whose {@link #appliesTo} accepts {@code context} at the moment
     *     the run reaches it, so a gate reads what the earlier stages wrote; a stage that throws
     *     ends the run, and the stages after it do not run
     */
    static <C> void runAll(List<? extends LoadStage<? super C>> stages, C context) {
        for (var stage : stages) {
            if (stage.appliesTo(context)) {
                stage.apply(context);
            }
        }
    }
}
