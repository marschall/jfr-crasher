JFR Crasher
===========

A reproducer for a crashing JFR bug.

JFR has a crashing bug when multiple threads call `#defineClass` at the same time for the same class on the same classloader. This can happen with a [parallel capable](https://docs.oracle.com/javase/7/docs/technotes/guides/lang/cl-mt.html) such as [JBoss Modules](http://jboss-modules.github.io/jboss-modules/manual/).

If you are using a class loader similar to this one an using multiple threads you may be affected.

This bug is [JDK-8232997](https://bugs.openjdk.java.net/browse/JDK-8232997) and has been fixed in Java 14.

```java
public class CustomClassLoader extends ClassLoader {

  static {
    registerAsParallelCapable();
  }

  @Override
  protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
    Class<?> loadedClass = findLoadedClass(className);
    if (loadedClass != null) {
      if (resolve) {
        resolveClass(loadedClass);
      }
      return loadedClass;
    }

    Class<?> clazz;
    try {
      clazz = defineClass(className, byteCode, 0, byteCode.length);
    } catch (LinkageError e) {
      // we lost the race, somebody else loaded the class
      clazz = findLoadedClass(className);
    }
    if (resolve) {
      resolveClass(clazz);
    }
    return clazz;
  }

}
```

