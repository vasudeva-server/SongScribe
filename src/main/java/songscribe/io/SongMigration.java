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
package songscribe.io;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A single migration stage in the {@link MigrationPipeline}.
 *
 * @param id        identifies the stage (used in diagnostics and test selection)
 * @param appliesTo gate deciding whether the stage runs against a given {@link MigrationContext}
 * @param apply     the transform applied to the context when {@code appliesTo} is satisfied
 */
record SongMigration(StageId id,
                     Predicate<MigrationContext> appliesTo,
                     Consumer<MigrationContext> apply) {
}
