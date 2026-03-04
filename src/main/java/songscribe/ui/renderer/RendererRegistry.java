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

package songscribe.ui.renderer;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.ui.layout.Clef;
import songscribe.ui.layout.KeySignature;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.Trill;
import songscribe.ui.layout.Tuplet;

/**
 * Maps LineElement types to their corresponding renderers.
 * <p>
 * Singleton registry that provides the appropriate renderer for each element type.
 * Renderers are registered at initialization and looked up by element class.
 */
public class RendererRegistry {

    private static final RendererRegistry INSTANCE = new RendererRegistry();

    private final Map<Class<? extends LineElement>, ElementRenderer<?>> renderers = new HashMap<>();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private RendererRegistry() {
        registerDefaultRenderers();
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull RendererRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers all default renderers.
     * <p>
     * Called during construction. Additional renderers can be registered
     * later using {@link #register(Class, ElementRenderer)}.
     * <p>
     * Note: Only renderers with matching type parameters can be registered.
     * Renderers typed as {@code BaseElementRenderer<LineElement>} (like DynamicsRenderer,
     * EndingRenderer) are called directly via their convenience methods rather than
     * through the registry.
     */
    private void registerDefaultRenderers() {
        // Foundation renderers
        register(Clef.class, ClefRenderer.getInstance());
        register(KeySignature.class, KeySignatureRenderer.getInstance());

        // Range element renderers (typed to specific element classes)
        // Note: TieRenderer is no longer registered here — ties are rendered
        // directly from LayoutResult via LineRenderer.renderTies().
        register(Tuplet.class, TupletRenderer.getInstance());
        register(Trill.class, TrillRenderer.getInstance());

        // Note: EndingRenderer, DynamicsRenderer are typed as ElementRenderer<LineElement>
        // and are called directly via their convenience methods (renderEndings,
        // renderCrescendosFromLine, etc.) rather than through the registry.
    }

    /**
     * Registers a renderer for an element type.
     *
     * @param elementClass The element class
     * @param renderer     The renderer instance
     * @param <T>          The element type
     */
    public <T extends LineElement> void register(
        @NotNull Class<T> elementClass,
        @NotNull ElementRenderer<T> renderer
    ) {
        renderers.put(elementClass, renderer);
    }

    /**
     * Gets the renderer for an element instance.
     *
     * @param element The element to render
     * @param <T>     The element type
     * @return The renderer, or null if none registered
     */
    @SuppressWarnings("unchecked")
    public @Nullable <T extends LineElement> ElementRenderer<T> getRenderer(@NotNull T element) {
        return (ElementRenderer<T>) renderers.get(element.getClass());
    }

    /**
     * Gets the renderer for an element class.
     *
     * @param elementClass The element class
     * @param <T>          The element type
     * @return The renderer, or null if none registered
     */
    @SuppressWarnings("unchecked")
    public @Nullable <T extends LineElement> ElementRenderer<T> getRenderer(
        @NotNull Class<T> elementClass
    ) {
        return (ElementRenderer<T>) renderers.get(elementClass);
    }

    /**
     * Returns whether a renderer is registered for the given element type.
     *
     * @param elementClass The element class to check
     * @return true if a renderer is registered
     */
    public boolean hasRenderer(@NotNull Class<? extends LineElement> elementClass) {
        return renderers.containsKey(elementClass);
    }

    /**
     * Unregisters a renderer for an element type.
     * <p>
     * Primarily useful for testing.
     *
     * @param elementClass The element class to unregister
     */
    public void unregister(@NotNull Class<? extends LineElement> elementClass) {
        renderers.remove(elementClass);
    }

    /**
     * Clears all registered renderers.
     * <p>
     * Primarily useful for testing.
     */
    public void clear() {
        renderers.clear();
    }

    /**
     * Resets to default renderers.
     * <p>
     * Primarily useful for testing.
     */
    public void reset() {
        clear();
        registerDefaultRenderers();
    }
}
