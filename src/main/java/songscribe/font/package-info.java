/**
 * Fonts: which faces exist, how one is named to a person, and how text drawn in one measures.
 *
 * <p>Four of the concepts here are easy to confuse for one another, and each has exactly one
 * owner:
 *
 * <ul>
 *   <li><b>Resolving a stored name to an installed face</b> — {@link
 *   songscribe.font.InstalledFonts} owns the set of faces the host system offers and the
 *   lookup that turns a name a document saved into one of them.</li>
 *   <li><b>Describing a face to a person</b> — {@link songscribe.font.FontDescription} turns a
 *   face into the words that belong beside a font chooser, and produces nothing that can be
 *   resolved back into a face.</li>
 *   <li><b>Loading a face the application ships</b> — {@link songscribe.font.LocalFonts} reads
 *   the bundled font files and decides whether a face is merely handed to a caller or
 *   registered so a name can find it; {@link songscribe.font.MusescoreIconFont} and {@link
 *   songscribe.font.SourceSans3Font} each own one shipped family's identity and sizes.</li>
 *   <li><b>Measuring text</b> — {@link songscribe.font.TextMeasurement} owns every measurement
 *   the application takes, and with it the single crossing from toolkit pixels into staff
 *   spaces.</li>
 * </ul>
 *
 * <p>A face handed out from anywhere in this package is kerned, so no caller asks for kerning
 * and no two callers disagree about it.
 */
@NullMarked
package songscribe.font;

import org.jspecify.annotations.NullMarked;
