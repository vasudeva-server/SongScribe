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
package songscribe.dom;

/**
 * The lines a finished change entry names, as zero-based line indices into the song.
 *
 * <p>{@link ChangeReport.Builder#build()} is the only producer: it resolves each recorded
 * {@link ChangeLines} into a range when the edit or load that produced the report is over. The
 * indices therefore describe the song as that edit or load left it, and say nothing about it
 * after any later edit.
 *
 * @param firstIndex the index of the line the change starts on
 * @param lastIndex  the index of the line the change ends on
 * @invariant {@code firstIndex <= lastIndex}. The record does not check it, because its only
 *     producer guarantees it: a {@link ChangeLines} names its lines in song order, adding or
 *     removing a line keeps the order of the lines it does not remove, and every line a
 *     recorded change names is still in the song when the report is built
 *     ({@link ChangeReport.Builder})
 */
public record LineRange(int firstIndex, int lastIndex) { }
