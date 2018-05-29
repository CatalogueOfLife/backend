package org.col.common.concurrent;

public interface StartNotifier {

  /**
   * Notify when something has started, e.g. a runnable has just been executed by a thread.
   */
  void started();
}
