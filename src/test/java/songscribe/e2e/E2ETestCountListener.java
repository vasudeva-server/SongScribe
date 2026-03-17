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

package songscribe.e2e;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import songscribe.SongScribe;

/**
 * Counts the total number of e2e leaf tests from JUnit's TestPlan so that the
 * status overlay in E2ETest can display an accurate "N of M" counter.
 *
 * <p>Registered via META-INF/services/org.junit.platform.launcher.TestExecutionListener.
 */
public class E2ETestCountListener implements TestExecutionListener {

    private static final String E2E_PACKAGE_PREFIX = "songscribe.e2e";

    private static int totalTests = 0;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        var count = 0;

        for (var descriptor : testPlan.getRoots()) {
            count += countLeafTests(testPlan, descriptor);
        }

        totalTests = count;

        if (count > 0) {
            SongScribe.logBanner("SongScribe (E2E Tests)");
        }
    }

    private int countLeafTests(TestPlan testPlan, org.junit.platform.launcher.TestIdentifier node) {
        if (testPlan.getChildren(node).isEmpty()) {
            // Leaf node — count it if it belongs to the e2e package.
            TestSource source = node.getSource().orElse(null);

            if (source instanceof MethodSource methodSource) {
                String className = methodSource.getClassName();

                if (className.startsWith(E2E_PACKAGE_PREFIX)) {
                    return 1;
                }
            }

            return 0;
        }

        var count = 0;

        for (var child : testPlan.getChildren(node)) {
            count += countLeafTests(testPlan, child);
        }

        return count;
    }

    public static int getTotalTests() {
        return totalTests;
    }
}
