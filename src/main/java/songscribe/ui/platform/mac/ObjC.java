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

package songscribe.ui.platform.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Reusable bridge to the Objective-C runtime via the Java Foreign Function &amp;
 * Memory (FFM) API.
 * <p>
 * <b>macOS only.</b> The static initializer loads native macOS libraries, so the
 * class must not be referenced on other platforms; callers guard with
 * {@link com.formdev.flatlaf.util.SystemInfo#isMacOS}.
 * <p>
 * Every {@code msgSend*} method wraps any failure of the native call in an
 * {@link IllegalStateException} naming the signature that failed.
 */
public final class ObjC {

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle OBJC_GET_CLASS;

    private static final MethodHandle SEL_REGISTER_NAME;

    private static final MethodHandle MSG_SEND;

    private static final MethodHandle MSG_SEND_ID;

    private static final MethodHandle MSG_SEND_LONG;

    private static final MethodHandle MSG_SEND_BOOL_ARG;

    private static final MethodHandle MSG_SEND_RET_BOOL;

    private static final MethodHandle MSG_SEND_RET_LONG;

    private static final MethodHandle MSG_SEND_RET_RECT;

    private static final MethodHandle MSG_SEND_VOID;

    private static final MethodHandle AUTORELEASE_POOL_PUSH;

    private static final MethodHandle AUTORELEASE_POOL_POP;

    private static final StructLayout NS_POINT_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("x"),
        ValueLayout.JAVA_DOUBLE.withName("y")
    );

    private static final StructLayout NS_SIZE_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("width"),
        ValueLayout.JAVA_DOUBLE.withName("height")
    );

    private static final StructLayout NS_RECT_LAYOUT = MemoryLayout.structLayout(
        NS_POINT_LAYOUT.withName("origin"),
        NS_SIZE_LAYOUT.withName("size")
    );

    private static final VarHandle NS_RECT_X = rectField("origin", "x");
    private static final VarHandle NS_RECT_Y = rectField("origin", "y");
    private static final VarHandle NS_RECT_WIDTH = rectField("size", "width");
    private static final VarHandle NS_RECT_HEIGHT = rectField("size", "height");

    static {
        var arena = Arena.global();
        var objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", arena);

        // Loading Foundation registers its Obj-C classes (NSString, NSUserDefaults,
        // …) with the runtime so objc_getClass can resolve them.
        SymbolLookup.libraryLookup("/System/Library/Frameworks/Foundation.framework/Foundation", arena);

        // AppKit does the same for the UI classes (NSApplication, NSWindow, NSView, …).
        SymbolLookup.libraryLookup("/System/Library/Frameworks/AppKit.framework/AppKit", arena);

        OBJC_GET_CLASS = downcall(objc, "objc_getClass",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        SEL_REGISTER_NAME = downcall(objc, "sel_registerName",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MSG_SEND = downcall(objc, "objc_msgSend",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MSG_SEND_ID = downcall(objc, "objc_msgSend",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MSG_SEND_LONG = downcall(objc, "objc_msgSend",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        MSG_SEND_BOOL_ARG = downcall(objc, "objc_msgSend",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));
        MSG_SEND_RET_BOOL = downcall(objc, "objc_msgSend",
            FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MSG_SEND_RET_LONG = downcall(objc, "objc_msgSend",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MSG_SEND_VOID = downcall(objc, "objc_msgSend",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        AUTORELEASE_POOL_PUSH = downcall(objc, "objc_autoreleasePoolPush",
            FunctionDescriptor.of(ValueLayout.ADDRESS));
        AUTORELEASE_POOL_POP = downcall(objc, "objc_autoreleasePoolPop",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        // objc_msgSend_stret exists on x86_64 only, and there it is the required
        // entry point for a struct this size. arm64 dropped it, and returns the
        // four doubles in registers through plain objc_msgSend.
        var structReturnEntryPoint = objc.find("objc_msgSend_stret").isPresent()
            ? "objc_msgSend_stret"
            : "objc_msgSend";

        MSG_SEND_RET_RECT = downcall(objc, structReturnEntryPoint,
            FunctionDescriptor.of(NS_RECT_LAYOUT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    private ObjC() {
    }

    /**
     * Coordinates are in points, in the space of the message that produced the rectangle; a
     * view's {@code -frame} is in its superview, whose y axis points up.
     *
     * @param y bottom edge
     */
    public record NsRect(double x, double y, double width, double height) {
        public double maxX() {
            return x + width;
        }
    }

    private static VarHandle rectField(String group, String field) {
        return NS_RECT_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement(group),
            MemoryLayout.PathElement.groupElement(field)
        );
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        var symbol = lookup.find(name)
            .orElseThrow(() -> new IllegalStateException("Obj-C runtime symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    public static MemorySegment objcClass(String name) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) OBJC_GET_CLASS.invoke(arena.allocateFrom(name));
        } catch (Throwable t) {
            throw asRuntime("objc_getClass(" + name + ')', t);
        }
    }

    public static MemorySegment selector(String name) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) SEL_REGISTER_NAME.invoke(arena.allocateFrom(name));
        } catch (Throwable t) {
            throw asRuntime("sel_registerName(" + name + ')', t);
        }
    }

    public static MemorySegment msgSend(MemorySegment receiver, MemorySegment selector) {
        try {
            return (MemorySegment) MSG_SEND.invoke(receiver, selector);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL)", t);
        }
    }

    public static MemorySegment msgSend(MemorySegment receiver, MemorySegment selector, MemorySegment arg) {
        try {
            return (MemorySegment) MSG_SEND_ID.invoke(receiver, selector, arg);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL, id)", t);
        }
    }

    public static MemorySegment msgSend(MemorySegment receiver, MemorySegment selector, long arg) {
        try {
            return (MemorySegment) MSG_SEND_LONG.invoke(receiver, selector, arg);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL, NSInteger)", t);
        }
    }

    public static void msgSendVoid(MemorySegment receiver, MemorySegment selector, boolean flag) {
        try {
            MSG_SEND_BOOL_ARG.invoke(receiver, selector, (byte) (flag ? 1 : 0));
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL, BOOL)", t);
        }
    }

    public static void msgSendVoid(MemorySegment receiver, MemorySegment selector) {
        try {
            MSG_SEND_VOID.invoke(receiver, selector);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL) -> void", t);
        }
    }

    public static boolean msgSendBoolean(MemorySegment receiver, MemorySegment selector) {
        try {
            return (byte) MSG_SEND_RET_BOOL.invoke(receiver, selector) != 0;
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL) -> BOOL", t);
        }
    }

    public static long msgSendLong(MemorySegment receiver, MemorySegment selector) {
        try {
            return (long) MSG_SEND_RET_LONG.invoke(receiver, selector);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL) -> NSInteger", t);
        }
    }

    /**
     * Has no nil-receiver behavior a caller can test for: a nil receiver yields an undefined or
     * empty rectangle with nothing to mark it wrong. Check the receiver first.
     *
     * @param receiver must not be nil
     */
    public static NsRect msgSendRect(MemorySegment receiver, MemorySegment selector) {
        try (var arena = Arena.ofConfined()) {
            var rect = (MemorySegment) MSG_SEND_RET_RECT.invoke(
                (SegmentAllocator) arena, receiver, selector
            );

            return new NsRect(
                (double) NS_RECT_X.get(rect, 0L),
                (double) NS_RECT_Y.get(rect, 0L),
                (double) NS_RECT_WIDTH.get(rect, 0L),
                (double) NS_RECT_HEIGHT.get(rect, 0L)
            );
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL) -> NSRect", t);
        }
    }

    /** Returns {@code null} when the string pointer is {@code nil}. */
    public static @Nullable String nsStringToJava(MemorySegment nsString) {
        if (nsString.address() == 0) {
            return null;
        }

        var utf8 = msgSend(nsString, selector("UTF8String"));

        if (utf8.address() == 0) {
            return null;
        }

        // A downcall pointer return is zero-length; reinterpret so the bytes up to
        // the terminating NUL are readable.
        return utf8.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /**
     * The result is valid only within the enclosing autorelease pool (see
     * {@link #withAutoreleasePool}).
     */
    public static MemorySegment javaToNsString(String value) {
        try (var arena = Arena.ofConfined()) {
            return msgSend(objcClass("NSString"), selector("stringWithUTF8String:"), arena.allocateFrom(value));
        }
    }

    /**
     * Extract any Java values you need from native objects before returning. The body may
     * return {@code null}.
     */
    public static <T extends @Nullable Object> T withAutoreleasePool(Supplier<T> body) {
        MemorySegment pool;

        try {
            pool = (MemorySegment) AUTORELEASE_POOL_PUSH.invoke();
        } catch (Throwable t) {
            throw asRuntime("objc_autoreleasePoolPush", t);
        }

        T result;

        try {
            result = body.get();
        } catch (RuntimeException bodyException) {
            popQuietly(pool, bodyException);
            throw bodyException;
        }

        try {
            AUTORELEASE_POOL_POP.invoke(pool);
        } catch (Throwable t) {
            throw asRuntime("objc_autoreleasePoolPop", t);
        }

        return result;
    }

    private static void popQuietly(MemorySegment pool, Throwable primary) {
        try {
            AUTORELEASE_POOL_POP.invoke(pool);
        } catch (Throwable popThrowable) {
            primary.addSuppressed(popThrowable);
        }
    }

    private static RuntimeException asRuntime(String what, Throwable cause) {
        return new IllegalStateException("Obj-C call failed: " + what, cause);
    }
}
