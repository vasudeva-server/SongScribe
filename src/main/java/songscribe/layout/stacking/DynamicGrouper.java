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
import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Partitions a line's hairpins and text dynamics into the groups that must share one vertical
 * reference line, a port of LilyPond's {@code DynamicLineSpanner}
 * ({@code lily/dynamic-align-engraver.cc}).
 * <p>
 * This class only decides <em>which</em> marks belong together; {@link StructuralStacker}
 * computes the shared Y and places them.
 * <p>
 * The rule:
 * <ul>
 *   <li>Two hairpins whose inclusive element ranges share an element are in one group.</li>
 *   <li>A text dynamic joins a hairpin's group when it sits on one of the hairpin's own bounds or
 *       on the element immediately outside the span. The editor never produces a dynamic strictly
 *       inside a hairpin's range, but an imported file can; that case falls through to a cluster
 *       of its own, which is deliberate and must not be removed as dead code.</li>
 *   <li>A text dynamic adjacent to no hairpin is a group of its own.</li>
 * </ul>
 */
public final class DynamicGrouper {

    private DynamicGrouper() {
    }

    public sealed interface Member {

        int leftIndex();

        final class OfHairpin implements Member {

            private final Hairpin hairpin;
            private final ElementRange range;

            OfHairpin(Hairpin hairpin) {
                this.hairpin = hairpin;
                range = hairpin.range();
            }

            public Hairpin hairpin() {
                return hairpin;
            }

            public ElementRange range() {
                return range;
            }

            @Override
            public int leftIndex() {
                return range.begin();
            }
        }

        record OfDynamic(DynamicAttachment dynamic, int elementIndex) implements Member {

            @Override
            public int leftIndex() {
                return elementIndex;
            }
        }
    }

    /**
     * A set of marks sharing one vertical reference line, its {@code members} in ascending
     * {@link Member#leftIndex()} order.
     */
    public record Group(List<Member> members) {

        int leftIndex() {
            return members.getFirst().leftIndex();
        }
    }

    /**
     * The groups are in ascending X order of each group's leftmost member, and every hairpin and
     * text dynamic of {@code line} is in exactly one of them.
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

    private static List<Cluster> clusterHairpins(Line line) {
        var hairpins = new ArrayList<Member.OfHairpin>();

        var spans = line.spanRegistry();

        for (var hairpin : spans.findSpans(Hairpin.class)) {
            hairpins.add(new Member.OfHairpin(hairpin));
        }

        hairpins.sort(Comparator.comparingInt(hairpin -> hairpin.range().begin()));

        var clusters = new ArrayList<Cluster>();

        for (var hairpin : hairpins) {
            var current = clusters.isEmpty() ? null : clusters.getLast();

            if (current != null && hairpin.range().begin() <= current.hairpinEndIndex) {
                current.addHairpin(hairpin);
            } else {
                var cluster = new Cluster();
                cluster.addHairpin(hairpin);
                clusters.add(cluster);
            }
        }

        return clusters;
    }

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

    private static final class Cluster {

        private final List<Member> members = new ArrayList<>();
        private int hairpinEndIndex = Integer.MIN_VALUE;

        private void addHairpin(Member.OfHairpin hairpin) {
            members.add(hairpin);
            hairpinEndIndex = Math.max(hairpinEndIndex, hairpin.range().end());
        }

        private boolean abuts(int index) {
            for (var member : members) {
                if (member instanceof Member.OfHairpin hairpin
                    && (index == hairpin.range().begin() - 1
                        || index == hairpin.range().begin()
                        || index == hairpin.range().end()
                        || index == hairpin.range().end() + 1)) {
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
