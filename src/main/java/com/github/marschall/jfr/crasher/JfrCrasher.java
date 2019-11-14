package com.github.marschall.jfr.crasher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JfrCrasher {

  private static final String RUNNABLE_EVENT = "com.github.marschall.jfr.crasher.JfrRunnable$RunnableEvent";

  private static final String JFR_RUNNABLE = "com.github.marschall.jfr.crasher.JfrRunnable";

  private volatile ClassLoader nextLoader;

  public void crash() {
    byte[] runnableClass = loadBytecode(JFR_RUNNABLE);
    byte[] eventClass = loadBytecode(RUNNABLE_EVENT);
    
    int numberOfThreads = Runtime.getRuntime().availableProcessors();
    if (numberOfThreads < 1) {
      throw new IllegalStateException("requies more than one thread");
    }
    ExecutorService threadPool = Executors.newFixedThreadPool(numberOfThreads);
    CyclicBarrier cyclicBarrier = new CyclicBarrier(numberOfThreads, () -> {
      this.nextLoader = new PredefinedClassLoader(runnableClass, eventClass);
    });
    for (int i = 0; i < numberOfThreads; i++) {
      threadPool.submit(new LoadingRunnable(cyclicBarrier));
    }
    threadPool.shutdown();
  }

  final class LoadingRunnable implements Runnable {

    private final CyclicBarrier barrier;

    LoadingRunnable(CyclicBarrier barrier) {
      this.barrier = barrier;
    }

    @Override
    public void run() {
      while (true) {
        try {
          this.barrier.await();
          Runnable runnable = loadJfrRunnable(nextLoader);
          runnable.run();
        } catch (InterruptedException | BrokenBarrierException e) {
          return;
        }
      }
    }

  }

  Runnable loadJfrRunnable(ClassLoader classLoader) {
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

    private Class<?> loadClassFromByteArray(String className, boolean resolve, byte[] byteCode) throws ClassNotFoundException {
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



}
