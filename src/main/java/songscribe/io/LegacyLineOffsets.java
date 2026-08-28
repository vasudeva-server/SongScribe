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

import songscribe.dom.Line;
import songscribe.dom.DocumentScale;

/**
 * Load-time carrier for the four line-level Y position fields that exist only in pre-Phase 11
 * (format v1) documents. Populated by {@link LineIO.LineReader} during XML parsing and consumed
 * by {@link FormatMigrator#migrateLineLevelOffsets} to redistribute the offsets onto per-instance
 * attachment and span objects. Discarded after migration completes.
 */
record LegacyLineOffsets(int tempoChangeYPosPx, int beatChangeYPosPx, int firstSecondEndingYPosPx, int trillYPosPx) {

    // Matches the field initializers that were on Line before this carrier was introduced.
    // Note: tempo's default of 0 also serves as the "unset" sentinel — legacy files that omit the
    // tempo tag are treated as having no tempo offset to migrate (see FormatMigrator).
    static final LegacyLineOffsets DEFAULTS = new LegacyLineOffsets(
        0,
        DocumentScale.ssToPx(Line.BEAT_CHANGE_DEFAULT_Y_SS).positionPx(),
        DocumentScale.ssToPx(Line.ENDING_DEFAULT_Y_SS).positionPx(),
        DocumentScale.ssToPx(Line.TRILL_DEFAULT_Y_SS).positionPx()
    );
}
