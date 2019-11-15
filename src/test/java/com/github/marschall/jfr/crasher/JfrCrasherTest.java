package com.github.marschall.jfr.crasher;

import static com.github.marschall.jfr.crasher.JfrCrasher.JFR_RUNNABLE;
import static com.github.marschall.jfr.crasher.JfrCrasher.RUNNABLE_EVENT;

import org.junit.jupiter.api.Test;

import com.github.marschall.jfr.crasher.JfrCrasher.PredefinedClassLoader;

class JfrCrasherTest {

  @Test
  void test() {
    byte[] runnableClass = JfrCrasher.loadBytecode(JFR_RUNNABLE);
    byte[] eventClass = JfrCrasher.loadBytecode(RUNNABLE_EVENT);

    ClassLoader classLoader = new PredefinedClassLoader(runnableClass, eventClass);

    Runnable runnable = JfrCrasher.loadJfrRunnable(classLoader);
    runnable.run();
  }

}
