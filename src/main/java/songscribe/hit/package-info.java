/**
 * Neutral hit-testing vocabulary shared by the layout layer, which builds hit
 * geometry, and the UI layer, which queries it.
 *
 * <p>This package depends only on {@code songscribe.dom}. Nothing here may import
 * {@code songscribe.layout} or {@code songscribe.ui}, because both of those depend
 * on this package and the resulting cycle would defeat the point of a shared
 * vocabulary.
 */
@NullMarked
package songscribe.hit;

import org.jspecify.annotations.NullMarked;
