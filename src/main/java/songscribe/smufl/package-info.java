/**
 * The music font and what it declares about its glyphs.
 *
 * <p><b>Every spatial value in this package is in staff spaces, Y-down.</b> That is the
 * package's reason to exist: the font states its metrics in a Y-up coordinate system whose
 * em is four staff spaces, and nothing outside this package ever sees those conventions.
 * Conversion happens once, where a value is read, so no type here holds a measurement in
 * font units and no caller has to know that font units existed.
 *
 * <p><b>Every lookup is total.</b> The metadata is read in full when it is first needed,
 * and a font that cannot answer a question the application asks fails the application then
 * rather than at the first draw that needed the answer. Where a measurement genuinely does
 * not apply to every glyph, a closed type narrows the question until it does — a stem
 * attachment point is asked for by notehead, not by glyph, because most glyphs are not
 * noteheads.
 *
 * <p>The glyphs the application draws are a closed set, so the font is queried by enum
 * constant rather than by name. A glyph the application does not draw has no constant, and
 * a name the font does not carry cannot be written.
 */
@NullMarked
package songscribe.smufl;

import org.jspecify.annotations.NullMarked;
