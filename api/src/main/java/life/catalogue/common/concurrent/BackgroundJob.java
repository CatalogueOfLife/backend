package life.catalogue.common.concurrent;

import java.util.UUID;

public abstract class BackgroundJob implements Runnable {

  private final UUID key = UUID.randomUUID();

  public UUID getKey() {
    return key;
  }

}
