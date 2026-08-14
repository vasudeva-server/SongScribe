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

import songscribe.dom.Tempo;

/**
 * What the Song Settings Music tab's controls now say — the output half of
 * {@link MusicSettings}.
 *
 * <p>Only {@link #lineWidth()} can be wrong. The tempo controls are a note-value combo, a
 * bounded spinner, a description combo and a checkbox, so every state they can reach is a
 * tempo. The line width is free text and is the one thing validation has to look at.
 *
 * @param tempo     the tempo the controls describe, built fresh from them and owned by nobody
 *                  else
 * @param lineWidth the line-width field's text, unparsed
 */
public record MusicChoices(Tempo tempo, LineWidthEntry lineWidth) {}
