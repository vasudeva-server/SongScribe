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

package songscribe.prefs;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.layout.PageModel;
import songscribe.ui.Appearance;
import songscribe.binding.Property;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What {@link Prefs}'s observable views of a key promise.
 *
 * <p>A view is the store's own face, so the store is what notifies it: a change made by
 * anything — a direct {@code put} here, a {@code resetAll} anywhere — reaches whoever is
 * observing that key, and reaches nobody observing a different one. That inward
 * direction is what lets a two-way binding over a preference be live rather than
 * write-only, and it is the half no test of the {@code put} API alone would catch.
 *
 * <p>These share one process-wide store, so every test writes the value it expects
 * before asserting on a change rather than relying on what the store happens to hold.
 * The store itself is a scratch directory: {@code build.gradle.kts} points
 * {@code songscribe.prefsDir} away from the real per-user file.
 */
class PrefsTest extends UnitTest {

    private static final String LETTER = PageModel.Size.LETTER.storedValue();
    private static final String A4 = PageModel.Size.A4.storedValue();

    private static final int SLOWER_TEMPO = 80;
    private static final int FASTER_TEMPO = 120;

    /**
     * A stored page size in a case no constant is spelled in, and deliberately not the
     * default: a value that falls back would answer the default and look like a match.
     */
    private static final String SHOUTED_A4 = "A4";

    /** A stored value naming no constant, as a hand-edited or older file may hold. */
    private static final String UNREADABLE = "quarto";

    /** What one view factory writes and reads back; there is a case for each of them. */
    private record RoundTripCase(
        String description,
        Supplier<Property<Object>> view,
        Object firstValue,
        Object secondValue,
        Supplier<Object> stored
    ) {}

    @Test
    void testViewIsNotifiedWhenItsKeyIsChangedByAnythingElse() {
        var pageSize = Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class);
        Prefs.put(PrefsKey.PAGE_SIZE, LETTER);

        var notifications = new AtomicInteger();
        var observation = pageSize.observe(notifications::incrementAndGet);

        try {
            Prefs.put(PrefsKey.PAGE_SIZE, A4);

            assertThat(notifications).hasValue(1);
            assertThat(pageSize.get()).isEqualTo(PageModel.Size.A4);
        } finally {
            observation.cancel();
        }
    }

    @Test
    void testViewIsNotNotifiedWhenAnotherKeyChanges() {
        var pageSize = Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class);
        Prefs.put(PrefsKey.TEMPO_CHANGE_PERCENT, SLOWER_TEMPO);

        var notifications = new AtomicInteger();
        var observation = pageSize.observe(notifications::incrementAndGet);

        try {
            Prefs.put(PrefsKey.TEMPO_CHANGE_PERCENT, FASTER_TEMPO);

            assertThat(notifications).hasValue(0);
        } finally {
            observation.cancel();
        }
    }

    @Test
    void testResetAllNotifiesEveryView() {
        var pageSize = Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class);
        var tempo = Prefs.intProperty(PrefsKey.TEMPO_CHANGE_PERCENT);

        var pageSizeNotifications = new AtomicInteger();
        var tempoNotifications = new AtomicInteger();
        var pageSizeObservation = pageSize.observe(pageSizeNotifications::incrementAndGet);
        var tempoObservation = tempo.observe(tempoNotifications::incrementAndGet);

        try {
            // ALL names no individual key, so a view that only matched its own would
            // hear nothing and its control would sit on a value the store no longer holds.
            Prefs.resetAll();

            assertThat(pageSizeNotifications).hasValue(1);
            assertThat(tempoNotifications).hasValue(1);
        } finally {
            pageSizeObservation.cancel();
            tempoObservation.cancel();
        }
    }

    @Test
    void testStoredKeyReadsBackAsItsConstantWhateverItsCase() {
        // Two of these enums were stored as name() before they had a key(), so a stored
        // value in the wrong case is what every existing prefs.json holds.
        Prefs.put(PrefsKey.PAGE_SIZE, SHOUTED_A4);

        assertThat(Prefs.getChoice(PrefsKey.PAGE_SIZE, PageModel.Size.class))
            .isEqualTo(PageModel.Size.A4);
    }

    @Test
    void testStoredValueNamingNoConstantReadsAsTheDefault() {
        Prefs.put(PrefsKey.PAGE_SIZE, UNREADABLE);

        // A preference is a file a user can edit, so this is an expected input rather
        // than a fault. One rule, in one place, replacing the three fallbacks each
        // reader used to decide for itself.
        assertThat(Prefs.getChoice(PrefsKey.PAGE_SIZE, PageModel.Size.class))
            .isEqualTo(PageModel.Size.LETTER);
    }

    @Test
    void testEveryViewOfAKeyIsNotifiedWhenItChanges() {
        // Views are handed out per call rather than cached, so a key can be viewed by two
        // callers at once — two dialogs, or a dialog and something else. Both read through
        // the one thing the store notifies, and forwarding that to only the first would
        // leave the second showing a value the store no longer holds, with nothing
        // reporting it.
        var first = Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class);
        var second = Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class);
        Prefs.put(PrefsKey.PAGE_SIZE, LETTER);

        var firstNotifications = new AtomicInteger();
        var secondNotifications = new AtomicInteger();
        var firstObservation = first.observe(firstNotifications::incrementAndGet);
        var secondObservation = second.observe(secondNotifications::incrementAndGet);

        try {
            Prefs.put(PrefsKey.PAGE_SIZE, A4);

            assertThat(firstNotifications).hasValue(1);
            assertThat(secondNotifications).hasValue(1);
            assertThat(first.get()).isEqualTo(PageModel.Size.A4);
            assertThat(second.get()).isEqualTo(PageModel.Size.A4);
        } finally {
            firstObservation.cancel();
            secondObservation.cancel();
        }
    }

    @Test
    void testWritingAConstantStoresItsStoredValueRatherThanItsName() {
        // From the other constant, so the assertion cannot pass on what the shared store
        // happened to hold when the test began.
        Prefs.put(PrefsKey.APPEARANCE, Appearance.LIGHT.storedValue());

        Prefs.choiceProperty(PrefsKey.APPEARANCE, Appearance.class).set(Appearance.DARK);

        // storedValue(), not name() — the two differ here, and storing the name would
        // write a file the running version reads back only because the match is
        // case-insensitive, so reading it back would not catch the mistake.
        assertThat(Prefs.getString(PrefsKey.APPEARANCE)).isEqualTo(Appearance.DARK.storedValue());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTripCases")
    void testWriteThroughTheViewReachesTheStore(RoundTripCase testCase) {
        var view = testCase.view().get();

        view.set(testCase.firstValue());

        assertThat(testCase.stored().get()).isEqualTo(testCase.firstValue());

        // Twice, from two different starting values, so the assertion cannot pass on
        // whatever the shared store happened to hold when the test began.
        view.set(testCase.secondValue());

        assertThat(testCase.stored().get()).isEqualTo(testCase.secondValue());
    }

    static Stream<RoundTripCase> roundTripCases() {
        return Stream.of(
            new RoundTripCase(
                "int view writes through to the store",
                view(() -> Prefs.intProperty(PrefsKey.TEMPO_CHANGE_PERCENT)),
                SLOWER_TEMPO,
                FASTER_TEMPO,
                () -> Prefs.getInt(PrefsKey.TEMPO_CHANGE_PERCENT)
            ),
            new RoundTripCase(
                "boolean view writes through to the store",
                view(() -> Prefs.booleanProperty(PrefsKey.PLAY_SELECTED_NOTE)),
                true,
                false,
                () -> Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)
            ),
            new RoundTripCase(
                "choice view writes through to the store",
                view(() -> Prefs.choiceProperty(PrefsKey.PAGE_SIZE, PageModel.Size.class)),
                PageModel.Size.LETTER,
                PageModel.Size.A4,
                () -> Prefs.getChoice(PrefsKey.PAGE_SIZE, PageModel.Size.class)
            )
        );
    }

    /**
     * Erases a view's value type, so one case table covers factories that differ only in
     * the type they view.
     *
     * @param factory the factory under test
     * @return the same factory, typed for the case table
     */
    @SuppressWarnings("unchecked")
    private static Supplier<Property<Object>> view(Supplier<? extends Property<?>> factory) {
        return () -> (Property<Object>) factory.get();
    }
}
