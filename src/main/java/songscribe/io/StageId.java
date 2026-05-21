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

/**
 * Identifies a single stage in the {@link MigrationPipeline}. Used both at registration
 * and when a test selects a stage to exercise in isolation, so a renamed stage is a
 * compile-time break rather than a silent runtime lookup failure.
 */
enum StageId {
    LEGACY_FORMAT,
    ANNOTATION_DYNAMICS,
    FINAL_TERMINAL,
    PIXELS_TO_SS,
    LINE_WIDTH_FIX,
    TOP_PADDING_FALLBACK,
    LEGACY_LYRICS,
    SYLLABIC_BACKFILL
}
