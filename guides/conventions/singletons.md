# Singletons

- Give a new singleton a private static `INSTANCE` field and public static methods that reference
  it, so that callers write `SomeSingleton.getSomething()`. Never expose a public `getInstance()`,
  and never make the public API instance methods.
- Keep instance fields `private`; mutate them through the static accessors or the singleton's own
  `@Handler` methods.
- Keep a message handler an instance method, because MBassador needs an instance:

  ```java
  public static Foo getFoo() {
      return INSTANCE.foo;
  }

  @Handler
  public void onSomething(SomeNotification message) {
      // mutate instance state
  }
  ```

- Do not retrofit an existing `getInstance()`-style singleton unless the task calls for it.
