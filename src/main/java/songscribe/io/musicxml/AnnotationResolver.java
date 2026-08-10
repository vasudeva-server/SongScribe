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
package songscribe.io.musicxml;

import java.awt.Component;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.StaffElement;

/**
 * Measure-level annotation {@code <direction>} state machine. Inverts the writer's
 * annotation emission: {@code MusicXmlWriter} emits a {@code <direction
 * placement="above|below">} carrying a single {@code <words>} immediately before
 * the annotated {@code <note>}, so the reader binds a finished annotation to the
 * next note after it (the same look-ahead rule the {@link MetronomeResolver} uses).
 *
 * <p><b>Discriminator.</b> A {@code <direction>} is an annotation iff it carries
 * <b>no {@code <metronome>}</b> and <b>has a {@code placement} attribute</b>. The
 * writer emits {@code placement} only on annotation directions — its tempo,
 * metric-modulation and wedge directions never carry it — so a non-null
 * {@code placement} is the unambiguous signal (and, by construction, implies no
 * {@code <metronome>}). A words-only direction with no {@code placement} is not an
 * annotation: its {@code <words>} stays with the {@link MetronomeResolver}, which
 * drops it (it carries no beat token), so it is never promoted to a phantom
 * annotation.
 */
final class AnnotationResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationResolver.class);

    // -------------------------------------------------------------------------
    // Per-direction accumulation — reset after every </direction> so a tempo or
    // wedge direction cannot inherit a previous annotation direction's state.
    // -------------------------------------------------------------------------

    // The <direction> placement; non-null marks the current direction as an
    // annotation (vs a tempo/metric-modulation/wedge direction, which never
    // carries a placement).
    private Annotation.@Nullable Placement placement = null;

    // The <words> text; null until </words> sets it.
    @Nullable
    private String words = null;

    private float xAlignment = Component.LEFT_ALIGNMENT;

    private double userYOffsetSs = 0;

    // -------------------------------------------------------------------------
    // Pending annotation — built at </direction>, awaiting its bound note.
    // -------------------------------------------------------------------------

    @Nullable
    private Annotation pendingAnnotation = null;

    // -------------------------------------------------------------------------
    // Package API — accumulation
    // -------------------------------------------------------------------------

    /**
     * Maps a MusicXML {@code placement} token to an {@link Annotation.Placement},
     * or {@code null} when the token is absent or unrecognised (marking the
     * direction as not an annotation).
     */
    static Annotation.@Nullable Placement placementFor(@Nullable String token) {
        if (MusicXmlTags.PLACEMENT_ABOVE.equals(token)) {
            return Annotation.Placement.ABOVE;
        }

        if (MusicXmlTags.PLACEMENT_BELOW.equals(token)) {
            return Annotation.Placement.BELOW;
        }

        return null;
    }

    /**
     * Maps an {@link Annotation.Placement} to its MusicXML {@code placement}
     * token — the inverse of {@link #placementFor}.
     * {@link Annotation.Placement#BELOW} maps to {@code below}; every other
     * placement to {@code above}.
     */
    static String placementToken(Annotation.Placement placement) {
        return placement == Annotation.Placement.BELOW
            ? MusicXmlTags.PLACEMENT_BELOW
            : MusicXmlTags.PLACEMENT_ABOVE;
    }

    /**
     * Begins a {@code <direction>}, capturing its {@code placement}. A non-null
     * {@code placement} marks this direction as an annotation; {@code null} leaves
     * its {@code <words>} to the {@link MetronomeResolver}.
     */
    void beginDirection(Annotation.@Nullable Placement placement) {
        resetAccumulation();
        this.placement = placement;
    }

    /** True when the current {@code <direction>} is an annotation (has a placement). */
    boolean isAnnotationDirection() {
        return placement != null;
    }

    /** Records the annotation's {@code <words>} text. */
    void setWords(String words) {
        this.words = words;
    }

    /** Records the annotation's horizontal alignment (from {@code <words halign>}). */
    void setXAlignment(float xAlignment) {
        this.xAlignment = xAlignment;
    }

    /** Records the annotation's user Y offset (from {@code <words relative-y>}). */
    void setUserYOffsetSs(double userYOffsetSs) {
        this.userYOffsetSs = userYOffsetSs;
    }

    /**
     * Finalizes the current {@code <direction>} at {@code </direction>}. Builds a
     * pending {@link Annotation} from the accumulated placement, words, alignment
     * and offset when this is an annotation direction; otherwise builds nothing.
     * Always clears the per-direction accumulation so the next direction starts
     * clean.
     */
    void endDirection() {
        if (placement == null || words == null) {
            resetAccumulation();
            return;
        }

        var annotation = new Annotation(words);
        annotation.setXAlignment(xAlignment);
        annotation.setPlacement(placement);
        annotation.setUserYOffsetSs(userYOffsetSs);
        pendingAnnotation = annotation;
        resetAccumulation();
    }

    // -------------------------------------------------------------------------
    // Package API — binding
    // -------------------------------------------------------------------------

    /**
     * Returns the pending annotation and clears the slot, transferring ownership
     * to the caller. Used by callers that must hold an annotation across a
     * deferred append (see {@code BarlineParser}'s deferred REPEAT_RIGHT) rather
     * than binding it to an element now.
     */
    @Nullable
    Annotation takePendingAnnotation() {
        var annotation = pendingAnnotation;
        pendingAnnotation = null;
        return annotation;
    }

    /**
     * Attaches {@code annotation} to {@code element} as an
     * {@link AnnotationAttachment}. No-op when {@code annotation} is null.
     */
    void attachAnnotation(StaffElement element, @Nullable Annotation annotation) {
        if (annotation == null) {
            return;
        }

        element.addAttachment(new AnnotationAttachment(element, annotation));
    }

    /**
     * Binds a finished annotation to {@code element} by attaching an
     * {@link AnnotationAttachment}. No-op when no annotation is pending.
     */
    void resolveAnnotation(StaffElement element) {
        attachAnnotation(element, takePendingAnnotation());
    }

    /**
     * Drops a pending annotation still unbound at a part boundary: an annotation
     * direction with no following note builds nothing.
     */
    void flushPendingAnnotation() {
        if (pendingAnnotation != null) {
            LOG.warn("Dropping annotation <direction> with no bound note");
            pendingAnnotation = null;
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void resetAccumulation() {
        placement = null;
        words = null;
        xAlignment = Component.LEFT_ALIGNMENT;
        userYOffsetSs = 0;
    }
}
