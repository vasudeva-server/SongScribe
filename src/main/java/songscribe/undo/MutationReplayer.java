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

package songscribe.undo;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.Ss;
import songscribe.dom.Tempo;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.DiminuendoRemoval;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LineLayoutField;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.ui.component.ScoreView;

/**
 * Applies a single recorded {@link Mutation} in the inverse ({@code applyUndo}) or
 * forward ({@code applyRedo}) direction while replaying a recorded batch.
 * <p>
 * Callers (the undo engine) must already have opened a modification bracket and
 * entered replay mode ({@code song.withReplay}); these methods open neither. Inside
 * replay mode the {@link Line}/{@link Song} helpers apply raw state without companion
 * side-work or guard checks, so the recorded batch is reproduced mechanically.
 * <p>
 * {@code applyUndo} restores the <em>before</em> state; {@code applyRedo} re-applies
 * the <em>forward</em> change. Both dispatch through an exhaustive pattern switch over
 * the sealed {@link Mutation} hierarchy — the compiler enforces coverage, so there is
 * no {@code default} branch.
 * <p>
 * {@link ElementModification} restores in place via
 * {@link songscribe.dom.StaffElement#copyStateFrom} (identity-preserving — never
 * {@code setElement}), so anchor references held inside other stacked records stay
 * valid across arbitrary undo/redo interleavings.
 *
 * <h2>Replaying a batch</h2>
 * A single mutation is only meaningful in the context of the batch it was recorded in.
 * Undo applies a batch <em>in reverse order</em> and redo <em>in forward order</em>;
 * either direction requires the whole batch, against the same document, with no edit
 * interleaved. Both rules follow from how the batch was recorded — a companion span
 * removal emitted before its primary element deletion can only be reversed after that
 * element is back — and are the caller's obligation, not something these methods check.
 * See {@code docs/undo.md} and {@code docs/mutations.md}.
 *
 * <p>Every change made here is itself recorded into the open bracket, so the closing
 * {@code endModification} posts a {@link songscribe.message.notification.SongDidChangeNotification}
 * describing the replay and the view repaints from it like any other edit.
 *
 * <p>{@link FontChange} is the one mutation whose state does not live on the
 * {@link Song}: fonts belong to the {@link ScoreView}, which is why these methods take
 * the view rather than the document.
 */
public final class MutationReplayer {

    private MutationReplayer() {
    }

    /**
     * Restores the before-state of {@code mutation}, undoing its forward effect: whatever
     * the mutation added is removed, whatever it removed is put back at the same index,
     * and whatever it changed is returned to the old value it recorded.
     *
     * <p>Exactly inverts {@link #applyRedo}: applying one and then the other, in either
     * order, leaves the document as it was.
     *
     * @param scoreView the view holding the document the mutation was recorded against,
     *                  with a modification bracket open and replay mode entered
     * @param mutation  a mutation recorded against that document, whose forward effect is
     *                  the document's most recent unreplayed change
     * @throws RuntimeException if the mutation cannot be applied — an index it names no
     *                          longer exists, or an element it names is gone. This means
     *                          the batch was replayed out of order, incompletely, or
     *                          against the wrong document, and the caller is expected to
     *                          treat it as an engine bug rather than a recoverable error.
     */
    public static void applyUndo(ScoreView scoreView, Mutation mutation) {
        var song = scoreView.getSong();

        switch (mutation) {
            case ElementInsertion(var line, var index, var _) -> line.removeElement(index);
            case ElementDeletion(var line, var index, var deletedElement) -> line.addElement(index, deletedElement);
            case ElementRangeDeletion(var line, var from, var _, var deletedElements) -> {
                for (var i = 0; i < deletedElements.size(); i++) {
                    line.addElement(from + i, deletedElements.get(i));
                }
            }
            case ElementReplacement(var line, var index, var oldElement, var _) -> line.setElement(index, oldElement);
            case ElementModification modification -> {
                var line = modification.line();
                // Record into the replay bracket (via applyChange) so the closing
                // endModification posts a SongDidChangeNotification and the view
                // repaints; copyStateFrom keeps element identity (never setElement).
                line.applyChange(modification, () ->
                    line.getElement(modification.index()).copyStateFrom(modification.beforeElement()));
            }
            case LineInsertion(var lineIndex, var _) -> song.removeLine(lineIndex);
            case LineDeletion(var lineIndex, var deletedLine) -> song.addLine(lineIndex, deletedLine);
            case LineKeyChange(var line, var oldKey, var _) -> line.setKey(oldKey);
            case LineLayoutChange(var line, var field, var oldValue, var _) ->
                applyLineLayoutField(line, field, oldValue);
            case SpanAddition(var line, var element) -> line.removeSpan(element);
            case SpanRemoval(var line, var element) -> line.addSpan(element);
            case BeamingAddition(var line, var beam) -> line.removeBeaming(beam);
            case BeamingRemoval(var line, var beam) -> line.addBeaming(beam);
            case TieAddition(var line, var tie) -> line.removeTie(tie);
            case TieRemoval(var line, var tie) -> line.addTie(tie);
            case TupletAddition(var line, var tuplet) -> line.removeTuplet(tuplet);
            case TupletRemoval(var line, var tuplet) -> line.addTuplet(tuplet);
            case CrescendoAddition(var line, var crescendo) -> line.removeCrescendo(crescendo);
            case CrescendoRemoval(var line, var crescendo) -> line.addCrescendo(crescendo);
            case DiminuendoAddition(var line, var diminuendo) -> line.removeDiminuendo(diminuendo);
            case DiminuendoRemoval(var line, var diminuendo) -> line.addDiminuendo(diminuendo);
            case MetadataChange(var field, var oldValue, var _) -> applyMetadataField(song, field, oldValue);
            case LayoutChange(var field, var oldValue, var _) -> applyLayoutField(song, field, oldValue);
            case LyricsChange(var field, var oldText, var _) -> applyLyricsField(song, field, oldText);
            case FontChange(var oldFonts, var _) -> scoreView.setFonts(oldFonts);
        }
    }

    /**
     * Re-applies the forward effect of {@code mutation}, reproducing the change the
     * original edit made: what it added is added again at the same index, what it removed
     * is removed, and what it changed takes the new value it recorded.
     *
     * <p>Exactly inverts {@link #applyUndo}, and takes the same preconditions — an open
     * bracket in replay mode, the same document, the batch replayed whole — except that
     * the document must be in the state that immediately <em>precedes</em> the mutation.
     *
     * @param scoreView the view holding the document the mutation was recorded against
     * @param mutation  a mutation recorded against that document, whose forward effect is
     *                  the document's next unreplayed change
     * @throws RuntimeException under the conditions given for {@link #applyUndo}
     */
    public static void applyRedo(ScoreView scoreView, Mutation mutation) {
        var song = scoreView.getSong();

        switch (mutation) {
            case ElementInsertion(var line, var index, var element) -> line.addElement(index, element);
            case ElementDeletion(var line, var index, var _) -> line.removeElement(index);
            case ElementRangeDeletion(var line, var from, var to, var _) -> line.removeRange(from, to);
            case ElementReplacement(var line, var index, var _, var newElement) -> line.setElement(index, newElement);
            case ElementModification modification -> {
                var line = modification.line();
                // See applyUndo: record so the notification fires and the view repaints.
                line.applyChange(modification, () ->
                    line.getElement(modification.index()).copyStateFrom(modification.afterElement()));
            }
            case LineInsertion(var lineIndex, var line) -> song.addLine(lineIndex, line);
            case LineDeletion(var lineIndex, var _) -> song.removeLine(lineIndex);
            case LineKeyChange(var line, var _, var newKey) -> line.setKey(newKey);
            case LineLayoutChange(var line, var field, var _, var newValue) ->
                applyLineLayoutField(line, field, newValue);
            case SpanAddition(var line, var element) -> line.addSpan(element);
            case SpanRemoval(var line, var element) -> line.removeSpan(element);
            case BeamingAddition(var line, var beam) -> line.addBeaming(beam);
            case BeamingRemoval(var line, var beam) -> line.removeBeaming(beam);
            case TieAddition(var line, var tie) -> line.addTie(tie);
            case TieRemoval(var line, var tie) -> line.removeTie(tie);
            case TupletAddition(var line, var tuplet) -> line.addTuplet(tuplet);
            case TupletRemoval(var line, var tuplet) -> line.removeTuplet(tuplet);
            case CrescendoAddition(var line, var crescendo) -> line.addCrescendo(crescendo);
            case CrescendoRemoval(var line, var crescendo) -> line.removeCrescendo(crescendo);
            case DiminuendoAddition(var line, var diminuendo) -> line.addDiminuendo(diminuendo);
            case DiminuendoRemoval(var line, var diminuendo) -> line.removeDiminuendo(diminuendo);
            case MetadataChange(var field, var _, var newValue) -> applyMetadataField(song, field, newValue);
            case LayoutChange(var field, var _, var newValue) -> applyLayoutField(song, field, newValue);
            case LyricsChange(var field, var _, var newText) -> applyLyricsField(song, field, newText);
            case FontChange(var _, var newFonts) -> scoreView.setFonts(newFonts);
        }
    }

    private static void applyLineLayoutField(Line line, LineLayoutField field, @Nullable Object value) {
        switch (field) {
            case LYRICS_Y_POS_SS -> line.setLyricsYPosSs(cast(value, Double.class));
            case ELEMENT_SPACING_RATIO -> line.setElementSpacingRatioAbsolute(cast(value, Float.class));
        }
    }

    private static void applyMetadataField(Song song, MetadataField field, @Nullable Object value) {
        switch (field) {
            case ATTRIBUTION -> song.setMetadata(cast(value, SongMetadata.class));
            case TEMPO -> song.setTempo(cast(value, Tempo.class));
            case FOOTNOTES -> song.setFootnotes(cast(value, String.class));
        }
    }

    private static void applyLayoutField(Song song, LayoutField field, @Nullable Object value) {
        switch (field) {
            case LINE_WIDTH_SS -> song.setLineWidthSs(new Ss(cast(value, Double.class)));
            case ROW_HEIGHT_ADJUSTMENT_SS -> song.setRowHeightAdjustmentSs(cast(value, Double.class));
        }
    }

    private static void applyLyricsField(Song song, LyricsField field, String text) {
        switch (field) {
            case UNDER -> song.setUnderLyrics(text);
            case BANGLA -> song.setBanglaLyrics(text);
            case TRANSLATED -> song.setTranslatedLyrics(text);
        }
    }

    /**
     * Casts a validated field value to its expected boxed type. The replayed fields
     * never carry null — their values are validated at mutation construction — so the
     * non-null return is safe; NullAway cannot see that {@code value} is non-null here.
     *
     * @return {@code value} as its expected type
     */
    @SuppressWarnings("NullAway")
    private static <T> T cast(@Nullable Object value, Class<T> type) {
        return type.cast(value);
    }
}
