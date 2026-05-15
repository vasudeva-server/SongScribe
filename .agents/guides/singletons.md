---
paths: src/**/*.java
---

# Singletons

New singleton classes use a **private static `INSTANCE` field with static public
methods that reference `INSTANCE`**. Do not expose a public `getInstance()`
method, and do not make the public API instance methods.

Callers write `RenderResources.getTitleFont()`, not
`RenderResources.getInstance().getTitleFont()`.

See `src/main/java/songscribe/ui/render/RenderResources.java` for the canonical
example.

## Shape

```java
public final class Thing {

    private static final Thing INSTANCE = new Thing();

    private Foo foo;

    private Thing() {
        // initialization
        MessageCenter.subscribe(this);  // if it subscribes to messages
    }

    public static Foo getFoo() {
        return INSTANCE.foo;
    }

    public static void setFoo(Foo foo) {
        INSTANCE.foo = foo;
    }

    // Message handlers stay as instance methods — MBassador needs an instance.
    @Handler
    public void onSomething(SomeNotification message) {
        // mutate instance state
    }
}
```

Instance fields may be mutable. Keep them `private`; mutation goes through the
static accessors or the singleton's own `@Handler` methods.

## When not to use this pattern

This is for **new** singletons. Do not retrofit existing `getInstance()`-style
singletons unless the task explicitly calls for it.
