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

import songscribe.dom.KeyType;

/**
 * A key signature as the Song Settings key combo offers it: a {@link KeyType} paired with a
 * count of accidentals.
 *
 * <p><strong>One accidental count has two spellings and only one of them is offered.</strong>
 * A key with no accidentals is neither sharp nor flat, so {@code (FLATS, 0)} and
 * {@code (SHARPS, 0)} would be the same key listed twice. {@code (FLATS, 0)} is the canonical
 * one; the combo has no {@code (SHARPS, 0)} entry, and
 * {@link songscribe.ui.dialog.backend.SongSettingsRules#canonicalKeySelectionFrom} is what
 * maps a song storing the other spelling onto the entry that exists.
 *
 * @param keyType whether {@code count} accidentals are sharps or flats; {@link KeyType#FLATS}
 *                when {@code count} is zero
 * @param count   how many accidentals the key signature carries, never negative
 */
public record KeySelection(KeyType keyType, int count) {}
