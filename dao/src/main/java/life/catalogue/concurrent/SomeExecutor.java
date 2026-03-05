package life.catalogue.concurrent;

import java.util.concurrent.ExecutorService;

public interface SomeExecutor {

  void submit(BackgroundJob job);

  static SomeExecutor from(ExecutorService executor) {
    return executor::submit;
  }
}
