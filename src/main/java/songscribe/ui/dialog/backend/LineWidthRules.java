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
package songscribe.ui.dialog.backend;

import java.util.List;

import songscribe.Strings;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.layout.PageModel;
import songscribe.ui.dialog.LineWidthEntry;
import songscribe.ui.dialog.LocalizedMessage;
import songscribe.ui.dialog.ValidationFailure;
import songscribe.ui.dialog.ValidationResult;

/**
 * What a line width typed into Song Settings has to satisfy, and what the commit does with it.
 *
 * <p>A line width is the only free text in that dialog, so it is the only thing there that can
 * be wrong. Both rules below are about the same two questions — does the text name a number,
 * and is that number a width the page supports — asked once here rather than once for
 * validation and again for the field's own focus check.
 */
public final class LineWidthRules {

    private LineWidthRules() {}

    /**
     * Decides whether {@code entry} names a width the page supports.
     *
     * <p>Judges {@link LineWidthEntry#text()} in both cases, including
     * {@link LineWidthEntry.Edit#UNEDITED}. A width loaded from a file can itself be out of
     * range — old documents carry widths this version no longer supports — and the user is
     * told about it on OK rather than having it committed back unexamined.
     *
     * <p>The range is {@code PageModel.MIN_LINE_WIDTH_INCHES} to
     * {@code PageModel.MAX_LINE_WIDTH_INCHES}, inclusive at both ends, compared after
     * converting the text out of {@link LineWidthEntry#unit()}. The two ways to fail are
     * reported differently — text that is not a number at all, and a number outside the range
     * — because the second can quote the bounds and the first has nothing to quote them
     * against.
     *
     * @param entry the line-width field's current state
     * @return {@link ValidationResult#valid()} when the text names a supported width,
     *         otherwise a single failure whose message quotes the bounds in {@code entry}'s
     *         own unit
     */
    public static ValidationResult validate(LineWidthEntry entry) {
        var text = entry.text();
        double value;

        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return ValidationResult.invalid(failure(Strings.ERROR_LINE_WIDTH_INVALID, entry));
        }

        var inches = entry.unit().toInches(value);

        if (inches < PageModel.MIN_LINE_WIDTH_INCHES || inches > PageModel.MAX_LINE_WIDTH_INCHES) {
            return ValidationResult.invalid(failure(Strings.ERROR_LINE_WIDTH_RANGE, entry));
        }

        return ValidationResult.valid();
    }

    /**
     * The width a commit of {@code entry} applies, in staff spaces.
     *
     * <p>An {@link LineWidthEntry.Edit#UNEDITED} entry answers the width {@code song} already
     * holds, not a reading of the field's text. The field is populated with that width rounded
     * for legibility, so parsing the text back would quantize a width nobody changed — by
     * enough to push a line that only just fits past the staff margin. See
     * {@link LineWidthEntry} for the whole of that argument.
     *
     * @param song  the song the width will be stored on, and the source of the unedited answer
     * @param entry a line-width entry {@link #validate} has accepted
     * @return the width to store, in staff spaces
     * @throws RuntimeException if {@code entry} was never validated and its text does not
     *                          parse — a broken precondition rather than a user error, so it
     *                          exits rather than being reported
     */
    public static double resolveSs(Song song, LineWidthEntry entry) {
        return switch (entry.edit()) {
            case UNEDITED -> song.getLineWidthSs();
            case EDITED -> ScaleContext.inchesToSs(entry.unit().toInches(parseValidated(entry)));
        };
    }

    private static double parseValidated(LineWidthEntry entry) {
        try {
            return Double.parseDouble(entry.text());
        } catch (NumberFormatException e) {
            throw RuntimeError.exit("unvalidated line width reached the commit: " + entry.text(), e);
        }
    }

    /**
     * The failure for {@code messageKey}, with the supported range expressed in the unit the
     * user typed in.
     *
     * <p>The unit's own label is carried as a nested {@link LocalizedMessage} rather than as
     * text, because resolving it here would make this a locale decision taken on the domain
     * side of the seam.
     */
    private static ValidationFailure failure(String messageKey, LineWidthEntry entry) {
        var unit = entry.unit();

        return new ValidationFailure(
            Strings.ALERT_TITLE_LINE_WIDTH_ERROR,
            new LocalizedMessage(messageKey, List.of(
                unit.fromInches(PageModel.MIN_LINE_WIDTH_INCHES),
                unit.fromInches(PageModel.MAX_LINE_WIDTH_INCHES),
                new LocalizedMessage(unit.labelKey())
            ))
        );
    }
}
