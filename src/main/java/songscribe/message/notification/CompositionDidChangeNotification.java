/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.message.notification;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.message.Message;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.Mutation;
import songscribe.music.Composition;
import songscribe.music.Line;

/**
 * Posted when one or more mutations have been applied to the composition.
 * Carries the accumulated list of mutations from the current modification bracket.
 *
 * <p>The {@link ChangeType} enum and associated methods are deprecated and will be removed
 * once all callers are migrated in Phases 4–5.
 *
 * <p><strong>EDT only.</strong> The cached {@link #getLine()} result is read and written
 * without synchronization; subscribers must call it from the event-dispatch thread.
 * This matches MBassador's synchronous dispatch and the rest of the SongScribe UI.
 */
public class CompositionDidChangeNotification extends Message {

    /**
     * Coarse-grained change category.
     *
     * @deprecated Replaced by the {@link songscribe.message.mutation.Mutation} sealed hierarchy.
     *             Use {@link #hasMutationOf} and {@link #getMutations()} instead.
     *             Will be removed after Phases 4–5 migrate all callers.
     */
    @Deprecated
    public enum ChangeType {
        CONTENT, STRUCTURE, METADATA, LYRICS, FONT, LAYOUT, FULL
    }

    private final List<Mutation> mutations;
    private final Composition composition;

    // Deprecated: tracks coarse-grained change types from pre-Phase-3b callers.
    @Nullable
    private final EnumSet<ChangeType> legacyChangeTypes;

    // Lazy cache for getLine(). null is a valid result, so we need a separate flag.
    private boolean lineIsCached;
    @Nullable
    private Line cachedLine;

    /**
     * Constructs a notification that takes ownership of an already-immutable
     * mutation list. The caller must not retain or mutate the list after
     * construction — {@code Composition.endModification} uses this to avoid
     * defensively copying the accumulated list a second time.
     */
    public CompositionDidChangeNotification(List<Mutation> mutations, Composition composition) {
        this.mutations = mutations;
        this.composition = composition;
        legacyChangeTypes = null;
    }

    /**
     * @deprecated Use {@link songscribe.music.Composition#withModification} and
     *             {@link songscribe.music.Composition#applyChange} instead.
     */
    @Deprecated
    public CompositionDidChangeNotification(ChangeType changeType, Composition composition) {
        this(changeType, composition, null);
    }

    /**
     * @deprecated Use {@link songscribe.music.Composition#withModification} and
     *             {@link songscribe.music.Composition#applyChange} instead.
     */
    @Deprecated
    public CompositionDidChangeNotification(
        ChangeType changeType,
        Composition composition,
        @Nullable Line line
    ) {
        this.mutations = List.of();
        this.composition = composition;
        legacyChangeTypes = EnumSet.of(changeType);
        cachedLine = line;
        lineIsCached = true;
    }

    /**
     * @deprecated Use {@link songscribe.music.Composition#withModification} and
     *             {@link songscribe.music.Composition#applyChange} instead.
     */
    @Deprecated
    public CompositionDidChangeNotification(
        EnumSet<ChangeType> changeTypes,
        Composition composition,
        @Nullable Line line
    ) {
        this.mutations = List.of();
        this.composition = composition;
        legacyChangeTypes = EnumSet.copyOf(changeTypes);
        cachedLine = line;
        lineIsCached = true;
    }

    public List<Mutation> getMutations() {
        return mutations;
    }

    public Composition getComposition() {
        return composition;
    }

    /**
     * Returns the single line targeted by all line-scoped mutations in the list,
     * or {@code null} if no line-scoped mutations exist or they target different lines.
     * Composition-scoped mutations are ignored. Result is lazily cached.
     */
    @Nullable
    public Line getLine() {
        if (lineIsCached) {
            return cachedLine;
        }

        Line result = null;

        for (var mutation : mutations) {
            if (mutation instanceof LineScopedMutation lineMutation) {
                var line = lineMutation.getLine();

                if (result == null) {
                    result = line;
                } else if (result != line) {
                    result = null;
                    break;
                }
            }
        }

        cachedLine = result;
        lineIsCached = true;
        return result;
    }

    /**
     * Returns {@code true} if this notification includes the given legacy change type.
     *
     * @deprecated Use {@link #hasMutationOf} with a specific {@link Mutation} subclass instead.
     */
    @Deprecated
    public boolean hasChangeType(ChangeType changeType) {
        return legacyChangeTypes != null && legacyChangeTypes.contains(changeType);
    }

    /**
     * Returns the set of legacy change types carried by this notification.
     *
     * @deprecated Use {@link #getMutations()} and {@link #hasMutationOf} instead.
     */
    @Deprecated
    public EnumSet<ChangeType> getChangeTypes() {
        return legacyChangeTypes != null
            ? EnumSet.copyOf(legacyChangeTypes)
            : EnumSet.noneOf(ChangeType.class);
    }

    /**
     * Returns {@code true} if the mutation list contains at least one instance
     * of the given mutation subclass.
     */
    public boolean hasMutationOf(Class<? extends Mutation> type) {
        for (var mutation : mutations) {
            if (type.isInstance(mutation)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return super.toString() + "(mutations=" + mutations + ")";
    }
}
