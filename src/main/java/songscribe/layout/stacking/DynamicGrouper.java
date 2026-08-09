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

package songscribe.layout.stacking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import songscribe.dom.DynamicAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.SpanBound;

/**
 * Partitions a line's hairpins and text dynamics into the groups that must share one vertical
 * reference line, a port of LilyPond's {@code DynamicLineSpanner}
 * ({@code lily/dynamic-align-engraver.cc}).
 * <p>
 * LilyPond collects every hairpin and dynamic text of a voice into one spanner and gives the
 * spanner a single Y, so {@code p ———< f} reads as one horizontal line rather than three
 * separately stacked marks. This class only decides <em>which</em> marks belong together;
 * {@link StructuralStacker} computes the shared Y and places them.
 * <p>
 * The rule:
 * <ul>
 *   <li>Two hairpins whose inclusive element ranges share an element are in one group — that is
 *       the back-to-back {@code < >} case, where both hang on the same column.</li>
 *   <li>A text dynamic joins a hairpin's group when it sits on one of the hairpin's own bounds
 *       ({@code anchorIndex} or {@code endIndex}) or on the element immediately outside the span
 *       ({@code anchorIndex - 1} or {@code endIndex + 1}). This is an editor invariant, not a
 *       model one: {@code SpanLookup.isInsideHairpin} refuses to add a dynamic strictly inside a
 *       hairpin's range, and {@code MusicEditOperations.stripInteriorPointDynamics} removes one a
 *       new or extended hairpin swallows, so the editor never produces a dynamic strictly inside
 *       {@code (anchorIndex, endIndex)}. A file built by {@code MusicXmlNoteReader} or the legacy
 *       {@code .mssw} path consults neither, so an imported dynamic can still land strictly inside
 *       a hairpin — that case falls through to a cluster of its own, which is deliberate and must
 *       not be removed as dead code.</li>
 *   <li>A text dynamic adjacent to no hairpin is a group of its own.</li>
 * </ul>
 */
public final class DynamicGrouper {

    private DynamicGrouper() {
    }

    /**
     * One member of a group: either a hairpin or a text dynamic. The two are placed by different
     * rules on the shared line — a hairpin is centered on it, a dynamic hangs its baseline below
     * it — so they stay distinguishable rather than collapsing into a common supertype.
     */
    public sealed interface Member {

        /**
         * The index of the leftmost line element this member hangs on, which orders both the
         * groups against each other and the members within a group.
         */
        int leftIndex();

        /**
         * A hairpin, carrying the endpoint indices already resolved against the asking line so no
         * caller has to resolve them a second time.
         */
        record OfHairpin(Hairpin hairpin, int anchorIndex, int endIndex) implements Member {

            @Override
            public int leftIndex() {
                return anchorIndex;
            }
        }

        /**
         * A text dynamic, carrying the index of the element it is attached to so its column — and
         * from that its X extent — can be looked up.
         */
        record OfDynamic(DynamicAttachment dynamic, int elementIndex) implements Member {

            @Override
            public int leftIndex() {
                return elementIndex;
            }
        }
    }

    /**
     * A set of marks sharing one vertical reference line, its {@code members} in ascending
     * {@link Member#leftIndex()} order. A group of one is placed exactly as it always was; a
     * group of two or more is what the shared baseline exists for.
     */
    public record Group(List<Member> members) {

        /** The index of this group's leftmost member, which orders groups against each other. */
        int leftIndex() {
            return members.getFirst().leftIndex();
        }
    }

    /**
     * Groups every hairpin and text dynamic on {@code line}, in ascending X order of each group's
     * leftmost member.
     * <p>
     * Element index order is X order: columns are laid out in reading order, and a hairpin's
     * drawn left tip may be pulled back only as far as a dynamic at {@code anchorIndex - 1} or
     * sitting on {@code anchorIndex} itself, both of which are in the same group anyway.
     * <p>
     * A hairpin whose endpoints do not both resolve to a position in {@code line} is skipped
     * entirely — it belongs to another line and has no geometry here.
     */
    public static List<Group> group(Line line) {
        var clusters = clusterHairpins(line);
        addDynamics(line, clusters);

        var groups = new ArrayList<Group>(clusters.size());

        for (var cluster : clusters) {
            cluster.members.sort(Comparator.comparingInt(Member::leftIndex));
            groups.add(new Group(List.copyOf(cluster.members)));
        }

        groups.sort(Comparator.comparingInt(Group::leftIndex));
        return groups;
    }

    /**
     * Builds one cluster per run of hairpins whose inclusive ranges share an element. Sorting by
     * anchor index first turns this into the classic interval merge: with the anchors ascending,
     * a hairpin overlaps the run so far exactly when its anchor is at or before the furthest end
     * reached.
     */
    private static List<Cluster> clusterHairpins(Line line) {
        var hairpins = new ArrayList<Member.OfHairpin>();

        for (var hairpin : line.findSpans(Hairpin.class)) {
            if (line.anchorIndexOf(hairpin) instanceof SpanBound.At(var anchorIndex)
                && line.endIndexOf(hairpin) instanceof SpanBound.At(var endIndex)) {
                hairpins.add(new Member.OfHairpin(hairpin, anchorIndex, endIndex));
            }
        }

        hairpins.sort(Comparator.comparingInt(Member.OfHairpin::anchorIndex));

        var clusters = new ArrayList<Cluster>();

        for (var hairpin : hairpins) {
            var current = clusters.isEmpty() ? null : clusters.getLast();

            if (current != null && hairpin.anchorIndex() <= current.hairpinEndIndex) {
                current.addHairpin(hairpin);
            } else {
                var cluster = new Cluster();
                cluster.addHairpin(hairpin);
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    /**
     * Adds each text dynamic to the cluster(s) of the hairpin(s) it abuts, or to a new cluster of
     * its own when it abuts none. A dynamic between two hairpins — {@code < p >} — abuts both, and
     * merges their clusters into one: all three marks are on the same line.
     */
    private static void addDynamics(Line line, List<Cluster> clusters) {
        for (var i = 0; i < line.elementCount(); i++) {
            var dynamic = line.getElement(i).findAttachment(DynamicAttachment.class);

            if (dynamic == null) {
                continue;
            }

            var member = new Member.OfDynamic(dynamic, i);
            Cluster target = null;

            for (var iterator = clusters.iterator(); iterator.hasNext(); ) {
                var cluster = iterator.next();

                if (!cluster.abuts(i)) {
                    continue;
                }

                if (target == null) {
                    target = cluster;
                } else {
                    target.absorb(cluster);
                    iterator.remove();
                }
            }

            if (target == null) {
                target = new Cluster();
                clusters.add(target);
            }

            target.members.add(member);
        }
    }

    /**
     * A group under construction. {@code hairpinEndIndex} is the furthest end index of its
     * hairpins, which is what the interval merge in {@link #clusterHairpins} advances.
     */
    private static final class Cluster {

        private final List<Member> members = new ArrayList<>();
        private int hairpinEndIndex = Integer.MIN_VALUE;

        private void addHairpin(Member.OfHairpin hairpin) {
            members.add(hairpin);
            hairpinEndIndex = Math.max(hairpinEndIndex, hairpin.endIndex());
        }

        /**
         * Whether a text dynamic on element {@code index} sits on, or immediately outside, one of
         * this cluster's hairpin bounds.
         */
        private boolean abuts(int index) {
            for (var member : members) {
                if (member instanceof Member.OfHairpin hairpin
                    && (index == hairpin.anchorIndex() - 1
                        || index == hairpin.anchorIndex()
                        || index == hairpin.endIndex()
                        || index == hairpin.endIndex() + 1)) {
                    return true;
                }
            }

            return false;
        }

        private void absorb(Cluster other) {
            members.addAll(other.members);
            hairpinEndIndex = Math.max(hairpinEndIndex, other.hairpinEndIndex);
        }
    }
}
