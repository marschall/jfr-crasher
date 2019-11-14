package com.github.marschall.jfr.crasher;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

public final class JfrRunnable implements Runnable {

  @Override
  public void run() {
    RunnableEvent event = new RunnableEvent();
    event.setRunnableClassName("JfrRunnable");
    event.begin();
    event.end();
    event.commit();
  }

  @Label("Runnable")
  @Description("An executed Runnable")
  @Category("Custom JFR Events")
  static class RunnableEvent extends Event {

    @Label("Operation Name")
    @Description("The name of the JDBC operation")
    private String runnableClassName;

    String getRunnableClassName() {
      return this.runnableClassName;
    }

    void setRunnableClassName(String operationName) {
      this.runnableClassName = operationName;
    }

  }

}
