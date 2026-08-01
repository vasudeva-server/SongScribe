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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Reusable bridge to the Objective-C runtime via the Java Foreign Function &amp;
 * Memory (FFM) API. Binds the handful of {@code libobjc} entry points
 * ({@code objc_getClass}, {@code sel_registerName}, {@code objc_msgSend}, and the
 * autorelease-pool calls) that SongScribe needs to talk to AppKit / Foundation
 * without a third-party Obj-C wrapper.
 * <p>
 * <b>macOS only.</b> The static initializer loads native macOS libraries, so the
 * class must not be referenced on other platforms; callers guard with
 * {@link com.formdev.flatlaf.util.SystemInfo#isMacOS}.
 * <p>
 * Only non-struct ({@code id} / pointer) returns are supported here, so a single
 * descriptor per argument signature is sufficient — there is no {@code _stret}
 * variant to worry about on arm64.
 */
public final class ObjC {

    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code Class objc_getClass(const char *name)} */
    private static final MethodHandle OBJC_GET_CLASS;

    /** {@code SEL sel_registerName(const char *str)} */
    private static final MethodHandle SEL_REGISTER_NAME;

    /** {@code id objc_msgSend(id self, SEL op)} */
    private static final MethodHandle MSG_SEND;

    /** {@code id objc_msgSend(id self, SEL op, id arg)} */
    private static final MethodHandle MSG_SEND_ID;

    /** {@code id objc_msgSend(id self, SEL op, NSInteger arg)} */
    private static final MethodHandle MSG_SEND_LONG;

    /** {@code void objc_msgSend(id self, SEL op, BOOL arg)} */
    private static final MethodHandle MSG_SEND_BOOL_ARG;

    /** {@code BOOL objc_msgSend(id self, SEL op)} */
    private static final MethodHandle MSG_SEND_RET_BOOL;

    /** {@code NSInteger objc_msgSend(id self, SEL op)} */
    private static final MethodHandle MSG_SEND_RET_LONG;

    /** {@code void objc_msgSend(id self, SEL op)} (e.g. {@code -release}) */
    private static final MethodHandle MSG_SEND_VOID;

    /** {@code void *objc_autoreleasePoolPush(void)} */
    private static final MethodHandle AUTORELEASE_POOL_PUSH;

    /** {@code void objc_autoreleasePoolPop(void *pool)} */
    private static final MethodHandle AUTORELEASE_POOL_POP;

    static {
        var arena = Arena.global();
        var objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", arena);

        // Loading Foundation registers its Obj-C classes (NSString, NSUserDefaults,
        // …) with the runtime so objc_getClass can resolve them.
        SymbolLookup.libraryLookup("/System/Library/Frameworks/Foundation.framework/Foundation", arena);

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
    }

    private ObjC() {
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        var symbol = lookup.find(name)
            .orElseThrow(() -> new IllegalStateException("Obj-C runtime symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    /**
     * Looks up an Obj-C class by name, returning its {@code Class} pointer
     * (usable as the receiver for class-method message sends).
     */
    public static MemorySegment objcClass(String name) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) OBJC_GET_CLASS.invoke(arena.allocateFrom(name));
        } catch (Throwable t) {
            throw asRuntime("objc_getClass(" + name + ')', t);
        }
    }

    /**
     * Registers (or resolves) a selector by name. The runtime interns selectors,
     * so repeated calls for the same name are cheap.
     */
    public static MemorySegment selector(String name) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) SEL_REGISTER_NAME.invoke(arena.allocateFrom(name));
        } catch (Throwable t) {
            throw asRuntime("sel_registerName(" + name + ')', t);
        }
    }

    /**
     * Sends a no-argument message: {@code [receiver selector]}.
     */
    public static MemorySegment msgSend(MemorySegment receiver, MemorySegment selector) {
        try {
            return (MemorySegment) MSG_SEND.invoke(receiver, selector);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL)", t);
        }
    }

    /**
     * Sends a single-object-argument message: {@code [receiver selector:arg]}.
     */
    public static MemorySegment msgSend(MemorySegment receiver, MemorySegment selector, MemorySegment arg) {
        try {
            return (MemorySegment) MSG_SEND_ID.invoke(receiver, selector, arg);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL, id)", t);
        }
    }

    /**
     * Sends a single {@code NSInteger} (64-bit) argument message returning an
     * {@code id}: {@code [receiver selector:arg]} (e.g. {@code -itemAtIndex:}).
     */
    public static MemorySegment msgSend(MemorySegment receiver, MemorySegment selector, long arg) {
        try {
            return (MemorySegment) MSG_SEND_LONG.invoke(receiver, selector, arg);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL, NSInteger)", t);
        }
    }

    /**
     * Sends a single {@code BOOL} argument message returning {@code void}:
     * {@code [receiver selector:flag]} (e.g. {@code -setEnabled:}).
     */
    public static void msgSendVoid(MemorySegment receiver, MemorySegment selector, boolean flag) {
        try {
            MSG_SEND_BOOL_ARG.invoke(receiver, selector, (byte) (flag ? 1 : 0));
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL, BOOL)", t);
        }
    }

    /**
     * Sends a no-argument message returning {@code void} (e.g. {@code -release}).
     */
    public static void msgSendVoid(MemorySegment receiver, MemorySegment selector) {
        try {
            MSG_SEND_VOID.invoke(receiver, selector);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL) -> void", t);
        }
    }

    /**
     * Sends a no-argument message returning a {@code BOOL} (e.g.
     * {@code -hasSubmenu}, {@code -isEnabled}).
     */
    public static boolean msgSendBoolean(MemorySegment receiver, MemorySegment selector) {
        try {
            return (byte) MSG_SEND_RET_BOOL.invoke(receiver, selector) != 0;
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL) -> BOOL", t);
        }
    }

    /**
     * Sends a no-argument message returning an {@code NSInteger} (e.g.
     * {@code -numberOfItems}).
     */
    public static long msgSendLong(MemorySegment receiver, MemorySegment selector) {
        try {
            return (long) MSG_SEND_RET_LONG.invoke(receiver, selector);
        } catch (Throwable t) {
            throw asRuntime("objc_msgSend(id, SEL) -> NSInteger", t);
        }
    }

    /**
     * Reads an {@code NSString} into a Java string via {@code -UTF8String}.
     * Returns {@code null} when the string pointer is {@code nil}.
     */
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
     * Builds an autoreleased {@code NSString} from a Java string via
     * {@code +[NSString stringWithUTF8String:]}. The result is valid only within
     * the enclosing autorelease pool (see {@link #withAutoreleasePool}).
     */
    public static MemorySegment javaToNsString(String value) {
        try (var arena = Arena.ofConfined()) {
            return msgSend(objcClass("NSString"), selector("stringWithUTF8String:"), arena.allocateFrom(value));
        }
    }

    /**
     * Runs {@code body} inside a fresh autorelease pool, draining it afterwards so
     * temporary autoreleased objects (e.g. those returned by message sends) do not
     * leak. Extract any Java values you need from native objects before returning.
     */
    public static <T> T withAutoreleasePool(Supplier<T> body) {
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
