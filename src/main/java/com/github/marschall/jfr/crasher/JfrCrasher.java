package com.github.marschall.jfr.crasher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class JfrCrasher {

  private static final String RUNNABLE_EVENT = "com.github.marschall.jfr.crasher.JfrRunnable$RunnableEvent";

  private static final String JFR_RUNNABLE = "com.github.marschall.jfr.crasher.JfrRunnable";

  public void crash() {
    byte[] runnableClass = loadBytecode(JFR_RUNNABLE);
    byte[] eventClass = loadBytecode(RUNNABLE_EVENT);
    
    ClassLoader classLoader = new PredefinedClassLoader(runnableClass, eventClass);
    Runnable runnable = loadJfrRunnable(classLoader);
    runnable.run();
  }

  private Runnable loadJfrRunnable(ClassLoader classLoader) {
    try {
      return Class.forName(JFR_RUNNABLE, true, classLoader).asSubclass(Runnable.class).getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("could not load runnable", e);
    }
  }

  private static byte[] loadBytecode(String className) {
    String resource = toResourceName(className);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (InputStream inputStream = JfrCrasher.class.getClassLoader().getResourceAsStream(resource)) {
      inputStream.transferTo(buffer);
    } catch (IOException e) {
      throw new UncheckedIOException("could not get bytecode of class:" + className, e);
    }
    return buffer.toByteArray();
  }

  private static String toResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

  public static void main(String[] args) {
    new JfrCrasher().crash();
  }

  static final class PredefinedClassLoader extends ClassLoader {

    static {
      registerAsParallelCapable();
    }

    private final byte[] runnableClass;

    private final byte[] eventClass;

    PredefinedClassLoader(byte[] runnableClass, byte[] eventClass) {
      super(null); // null parent
      this.runnableClass = runnableClass;
      this.eventClass = eventClass;
    }

//    @Override
//    protected Class<?> findClass(String className) throws ClassNotFoundException {
//      if (className.equals(JFR_RUNNABLE)) {
//        return loadClassFromByteArray(className, resolve, runnableClass);
//      } else if (className.equals(RUNNABLE_EVENT)) {
//        return loadClassFromByteArray(className, resolve, eventClass);
//      } else {
//        return super.loadClass(className, resolve);
//      }
//    }
    
    @Override
    protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
      // Check if we have already loaded it..
      Class<?> loadedClass = findLoadedClass(className);
      if (loadedClass != null) {
          if (resolve) {
              resolveClass(loadedClass);
          }
          return loadedClass;
      }
      
      if (className.equals(JFR_RUNNABLE)) {
        return loadClassFromByteArray(className, resolve, runnableClass);
      } else if (className.equals(RUNNABLE_EVENT)) {
        return loadClassFromByteArray(className, resolve, eventClass);
      } else {
        return super.loadClass(className, resolve);
      }
    }

    private Class<?> loadClassFromByteArray(String name, boolean resolve, byte[] byteCode) throws ClassNotFoundException {
      Class<?> clazz = defineClass(name, byteCode, 0, byteCode.length);
      if (resolve) {
        resolveClass(clazz);
      }
      return clazz;
    }

  }



}
