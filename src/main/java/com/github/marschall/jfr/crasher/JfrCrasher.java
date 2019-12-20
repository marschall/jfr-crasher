package com.github.marschall.jfr.crasher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

public final class JfrCrasher {

  static final String RUNNABLE_EVENT = "com.github.marschall.jfr.crasher.JfrCrasher$RunnableEvent";

  static final String JFR_RUNNABLE = "com.github.marschall.jfr.crasher.JfrCrasher$JfrRunnable";

  private volatile ClassLoader nextLoader;

  public void crash() {
    byte[] runnableClass = loadBytecode(JFR_RUNNABLE);
    byte[] eventClass = loadBytecode(RUNNABLE_EVENT);

    int numberOfThreads = Runtime.getRuntime().availableProcessors();
    if (numberOfThreads <= 1) {
      throw new IllegalStateException("requires more than one thread");
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
          Runnable runnable = loadJfrRunnable(JfrCrasher.this.nextLoader);
          runnable.run();
        } catch (Throwable e) {
          e.printStackTrace(System.err);
          return;
        }
      }
    }

  }

  static Runnable loadJfrRunnable(ClassLoader classLoader) {
    try {
      return Class.forName(JFR_RUNNABLE, false, classLoader).asSubclass(Runnable.class).getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("could not load runnable", e);
    }
  }

  static byte[] loadBytecode(String className) {
    String resource = toResourceName(className);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (InputStream inputStream = JfrCrasher.class.getClassLoader().getResourceAsStream(resource)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        output.write(buffer, 0, read);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not get bytecode of class:" + className, e);
    }
    return output.toByteArray();
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
      Class<?> loadedClass = this.findLoadedClass(className);
      if (loadedClass != null) {
        if (resolve) {
          this.resolveClass(loadedClass);
        }
        return loadedClass;
      }

      if (className.equals(JFR_RUNNABLE)) {
        return this.loadClassFromByteArray(className, resolve, this.runnableClass);
      } else if (className.equals(RUNNABLE_EVENT)) {
        return this.loadClassFromByteArray(className, resolve, this.eventClass);
      } else {
        return super.loadClass(className, resolve);
      }
    }

    private Class<?> loadClassFromByteArray(String className, boolean resolve, byte[] byteCode) throws ClassNotFoundException {
      Class<?> clazz;
      try {
        clazz = this.defineClass(className, byteCode, 0, byteCode.length);
      } catch (LinkageError e) {
        // we lost the race, somebody else loaded the class
        clazz = this.findLoadedClass(className);
      }
      if (resolve) {
        this.resolveClass(clazz);
      }
      return clazz;
    }

  }

  public static final class JfrRunnable implements Runnable {

    @Override
    public void run() {
      RunnableEvent event = new RunnableEvent();
      event.setRunnableClassName("JfrRunnable");
      event.begin();
      event.end();
      event.commit();
    }
  }

  @Label("Runnable")
  @Description("An executed Runnable")
  @Category("Custom JFR Events")
  static class RunnableEvent extends Event {

    @Label("Class Name")
    @Description("The name of the Runnable class")
    private String runnableClassName;

    String getRunnableClassName() {
      return this.runnableClassName;
    }

    void setRunnableClassName(String operationName) {
      this.runnableClassName = operationName;
    }

  }

}
