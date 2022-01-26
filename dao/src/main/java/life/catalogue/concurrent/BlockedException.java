package life.catalogue.concurrent;

import java.util.UUID;

/**
 *  job that is being blocked by another job with the given UUID.
 */
public class BlockedException extends RuntimeException {
  public final UUID blockedBy;

  public BlockedException(UUID blockedBy) {
    this.blockedBy = blockedBy;
  }

  @Override
  public String toString() {
    return "BlockedException{blockedBy=" + blockedBy + '}';
  }
}
