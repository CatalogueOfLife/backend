package life.catalogue.outbox;

import life.catalogue.api.model.OutboxEntry;

import java.time.Duration;

/**
 * Executes the outbox commands of one type, see {@link OutboxRunner}.
 *
 * A command is delivered at least once: a run cut short by a stop or a crash is repeated by the next runner,
 * and so is a run that a new request was merged into. A handler must therefore be safe to run again.
 *
 * Throwing {@link RetryLaterException}, UnavailableException or TooManyRequestsException retries the command later
 * without counting an attempt. Any other exception counts an attempt and retries after {@link #retryDelay(int)},
 * unless it is {@link #isPermanent(Exception) permanent} or the attempts are used up, which fails the command.
 */
public interface OutboxHandler {

  void handle(OutboxEntry entry) throws Exception;

  /**
   * @return false to leave the commands of this type waiting, e.g. while the component behind them is stopped
   */
  default boolean isOnline() {
    return true;
  }

  /**
   * @return how many commands of this type may run at the same time
   */
  default int concurrency() {
    return 1;
  }

  /**
   * @param attempts the attempts made so far, at least 1
   */
  default Duration retryDelay(int attempts) {
    return Duration.ofMinutes(Math.min((long) attempts * attempts, 6 * 60));
  }

  /**
   * @return true if retrying cannot help
   */
  default boolean isPermanent(Exception e) {
    return e instanceof IllegalArgumentException;
  }

  /**
   * Called once a command failed for good.
   */
  default void onFailed(OutboxEntry entry, Exception e) {
    // nothing by default
  }
}
