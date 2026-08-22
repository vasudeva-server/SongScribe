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

package songscribe.binding;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The invariants {@link Bindings} promises across several calls, in two groups.
 *
 * <p>What a {@link Bindings#computed} derivation is notified by: the dependencies it
 * collects by running its body, the ones it drops when a later run stops reading them,
 * and how often the body runs for consumers sharing it.
 *
 * <p>What propagates along a binding: a binding absorbing the notification its own
 * write caused without silencing the other bindings, a fold that settles, a two-way
 * binding that round-trips, a two-way binding declining to push back a change that
 * arrived from outside it, and the release {@link Bindings#dispose} performs.
 *
 * <p>Everything is arranged through {@link ValueProperty} and {@code computed}, which
 * are real implementations, so nothing here mocks anything. The whole graph is
 * single-threaded, which is the package's EDT-only invariant read as a test.
 */
class BindingsTest extends UnitTest {

    /** The value the self-feeding binding of the re-entrancy case starts from. */
    private static final int FIRST_COUNT = 1;

    /** A later value written from outside, distinct from anything the binding produces. */
    private static final int LATER_COUNT = 5;

    private static final int HEADING_SIZE = 24;
    private static final int BODY_SIZE = 12;
    private static final int CAPTION_SIZE = 9;

    private static final String SERIF = "Serif";
    private static final String SANS = "Sans";

    /** What the transforming binding of the disposal case appends, so a write is visible. */
    private static final String BOLD_SUFFIX = " Bold";

    private static final int SPELLED_NUMBER = 7;
    private static final int TYPED_NUMBER = 11;

    /** A value carrying more than any one source determines, for the merge case. */
    private record ChosenFont(String family, int size) {}

    @Test
    void testComputedIsNotNotifiedByAValueItsBodyDidNotRead() {
        var read = new ValueProperty<>("read");
        var unread = new ValueProperty<>("unread");
        var notifications = new AtomicInteger();
        var bindings = new Bindings();
        var derived = bindings.computed(read::get);

        derived.observe(notifications::incrementAndGet);

        // Dependencies are discovered by running the body, so the derivation has to be
        // read before it has any.
        assertThat(derived.get()).isEqualTo("read");

        unread.set("changed");
        assertThat(notifications).hasValue(0);

        read.set("changed");
        assertThat(notifications).hasValue(1);
        assertThat(derived.get()).isEqualTo("changed");
    }

    @Test
    void testComputedStopsBeingNotifiedByAValueItsBodyNoLongerReads() {
        var readFirst = new ValueProperty<>(true);
        var first = new ValueProperty<>("first");
        var second = new ValueProperty<>("second");
        var notifications = new AtomicInteger();
        var bindings = new Bindings();
        var derived = bindings.computed(() -> readFirst.get() ? first.get() : second.get());

        derived.observe(notifications::incrementAndGet);

        // This run reads the selector and the first branch, and nothing else.
        assertThat(derived.get()).isEqualTo("first");

        second.set("unread");
        assertThat(notifications).hasValue(0);

        // The selector is a dependency, so this notifies; the read that follows is what
        // re-collects the set, and it now holds the other branch.
        readFirst.set(false);
        assertThat(derived.get()).isEqualTo("unread");

        var afterSwitch = notifications.get();

        first.set("no longer read");
        assertThat(notifications.get()).isEqualTo(afterSwitch);

        second.set("now read");
        assertThat(notifications.get()).isEqualTo(afterSwitch + 1);
        assertThat(derived.get()).isEqualTo("now read");
    }

    @Test
    void testComputedSharedByTwoConsumersEvaluatesOncePerDependencyChange() {
        var evaluations = new AtomicInteger();
        var source = new ValueProperty<>("source");
        var bindings = new Bindings();
        var derived = bindings.computed(() -> {
            evaluations.incrementAndGet();

            return source.get();
        });

        var firstConsumer = new ValueProperty<>("");
        var secondConsumer = new ValueProperty<>("");

        // Two eager consumers: each reads the derivation on every notification.
        bindings.bind(firstConsumer, derived);
        bindings.bind(secondConsumer, derived);

        var afterRegistration = evaluations.get();

        source.set("changed");

        assertThat(evaluations.get() - afterRegistration).isEqualTo(1);
        assertThat(firstConsumer.get()).isEqualTo("changed");
        assertThat(secondConsumer.get()).isEqualTo("changed");
    }

    @Test
    void testBindingDropsItsOwnEchoWhileOtherEdgesStillPropagate() {
        var counter = new ValueProperty<>(FIRST_COUNT);
        var mirror = new ValueProperty<>(0);
        var bindings = new Bindings();

        // A second binding off the same value, registered first so its target is settled
        // before the self-feeding binding below writes anything.
        bindings.bind(mirror, counter);

        // This binding's own write notifies the value it observes, so every application
        // arrives straight back at it. An increment makes a re-entry visible as a
        // value rather than as a hang.
        bindings.bind(counter, counter, value -> value + 1);

        assertThat(counter.get()).isEqualTo(FIRST_COUNT + 1);
        assertThat(mirror.get()).isEqualTo(FIRST_COUNT + 1);

        counter.set(LATER_COUNT);

        assertThat(counter.get()).isEqualTo(LATER_COUNT + 1);
        assertThat(mirror.get()).isEqualTo(LATER_COUNT + 1);
    }

    @Test
    void testFixedPointMergeSettlesRatherThanAccumulating() {
        var chosen = new ValueProperty<>(new ChosenFont(SERIF, BODY_SIZE));
        var bindings = new Bindings();

        // A derivation notifies on every change to what it read, whether or not its own
        // value changed, so this keeps re-applying the fold with the same family.
        var family = bindings.computed(() -> chosen.get().family());

        var applied = new ValueProperty<>(new ChosenFont(SANS, HEADING_SIZE));

        bindings.bind(applied, family, (name, current) -> new ChosenFont(name, current.size()));

        var changes = new AtomicInteger();
        applied.observe(changes::incrementAndGet);

        chosen.set(new ChosenFont(SERIF, CAPTION_SIZE));
        chosen.set(new ChosenFont(SERIF, HEADING_SIZE));

        // The fold takes the family from the source and the size from the target, so
        // folding its own result again produces that result: the target settles, where
        // a fold that accumulated would move on every notification.
        assertThat(applied.get()).isEqualTo(new ChosenFont(SERIF, HEADING_SIZE));
        assertThat(changes).hasValue(0);
    }

    @Test
    void testBidirectionalTransformRoundTripsAndSettles() {
        var spelled = new ValueProperty<>(String.valueOf(SPELLED_NUMBER));
        var number = new ValueProperty<>(0);
        var bindings = new Bindings();

        bindings.bindBidirectional(spelled, number, new Transform<>(Integer::parseInt, String::valueOf));

        // The first side wins at registration: the second is written, the first is left
        // as it was.
        assertThat(number.get()).isEqualTo(SPELLED_NUMBER);
        assertThat(spelled.get()).isEqualTo(String.valueOf(SPELLED_NUMBER));

        var spelledChanges = new AtomicInteger();
        var numberChanges = new AtomicInteger();
        spelled.observe(spelledChanges::incrementAndGet);
        number.observe(numberChanges::incrementAndGet);

        number.set(TYPED_NUMBER);

        assertThat(spelled.get()).isEqualTo(String.valueOf(TYPED_NUMBER));
        assertThat(number.get()).isEqualTo(TYPED_NUMBER);

        // One change on each side: the write travelling back through the far side stops
        // instead of driving the pair round again.
        assertThat(spelledChanges).hasValue(1);
        assertThat(numberChanges).hasValue(1);

        spelled.set(String.valueOf(SPELLED_NUMBER));

        assertThat(number.get()).isEqualTo(SPELLED_NUMBER);
        assertThat(spelledChanges).hasValue(2);
        assertThat(numberChanges).hasValue(2);
    }

    @Test
    void testBidirectionalDoesNotPushBackAChangeThatArrivedFromOutside() {
        // Storage that reports what it costs: every write through the view is counted,
        // and a change made behind the view's back is announced by the storage itself,
        // which is how a preference behaves.
        var storage = new AtomicInteger(SPELLED_NUMBER);
        var writes = new AtomicInteger();
        var stored = new ViewProperty<Integer>(
            storage::get,
            value -> {
                writes.incrementAndGet();
                storage.set(value);
            },
            ViewProperty.WriteNotification.FROM_SOURCE
        );
        var shown = new ValueProperty<>(0);
        var bindings = new Bindings();

        bindings.bindBidirectional(stored, shown);

        // The first side wins at registration, so the view is read and never written.
        assertThat(shown.get()).isEqualTo(SPELLED_NUMBER);
        assertThat(writes).hasValue(0);

        // Neither side of the pair made this change.
        storage.set(TYPED_NUMBER);
        stored.notifyObservers();

        assertThat(shown.get()).isEqualTo(TYPED_NUMBER);

        // Still none. The far side settles and stops: it does not write back into the
        // side the value came from, so the storage is not asked to store what it just
        // reported.
        assertThat(writes).hasValue(0);
    }

    @Test
    void testDisposeStopsPropagationOnEveryEdge() {
        var source = new ValueProperty<>(SERIF);
        var plain = new ValueProperty<>("");
        var transformed = new ValueProperty<>("");
        var effectRuns = new AtomicInteger();
        var bindings = new Bindings();

        bindings.bind(plain, source);
        bindings.bind(transformed, source, family -> family + BOLD_SUFFIX);
        bindings.onNotify(source, effectRuns::incrementAndGet);

        bindings.dispose();
        source.set(SANS);

        // Nothing is written by disposal either: each target keeps what its binding last
        // gave it.
        assertThat(plain.get()).isEqualTo(SERIF);
        assertThat(transformed.get()).isEqualTo(SERIF + BOLD_SUFFIX);
        assertThat(effectRuns).hasValue(0);
    }
}
