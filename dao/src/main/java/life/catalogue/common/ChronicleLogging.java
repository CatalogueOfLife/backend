package life.catalogue.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.onoes.ExceptionHandler;

/**
 * Chronicle exception handlers that log to slf4j at the levels of Chronicle's own {@code Slf4jExceptionHandler},
 * but never recurse.
 *
 * Chronicle's default {@code ExceptionHandler.on(Class, String, Throwable)} catches whatever logging throws and
 * reports it through the very same method again. Once the stack is nearly exhausted every nested call overflows,
 * is caught and printed in full, so a single StackOverflowError turned into gigabytes of stack traces on stdout
 * while a server was shutting down: https://github.com/CatalogueOfLife/backend/issues/1654
 * These handlers drop a message they fail to log instead, leaving only a one line note on stderr.
 */
public enum ChronicleLogging implements ExceptionHandler {
  ERROR(Level.ERROR),
  WARN(Level.WARN),
  PERF(Level.INFO),
  DEBUG(Level.DEBUG);

  private final Level level;

  ChronicleLogging(Level level) {
    this.level = level;
  }

  /**
   * Replaces Chronicle's process wide exception handlers. Idempotent, call it before any Chronicle structure is opened.
   */
  public static void install() {
    Jvm.setExceptionHandlers(ERROR, WARN, DEBUG, PERF);
  }

  @Override
  public void on(Class<?> clazz, String message, Throwable thrown) {
    try {
      on(LoggerFactory.getLogger(clazz), message, thrown);
    } catch (Throwable t) {
      dropped(clazz.getName(), t);
    }
  }

  @Override
  public void on(Logger logger, String message, Throwable thrown) {
    try {
      logger.atLevel(level).setCause(thrown).log(message);
    } catch (Throwable t) {
      dropped(logger.getName(), t);
    }
  }

  @Override
  public boolean isEnabled(Class<?> clazz) {
    return LoggerFactory.getLogger(clazz).isEnabledForLevel(level);
  }

  private void dropped(String logger, Throwable t) {
    try {
      // no stack trace: the failure is most likely a stack overflow, printing its frames is what filled the disk
      System.err.println("Dropped chronicle " + level + " message for " + logger + ": " + t);
    } catch (Throwable ignored) {
      // nothing left to report to
    }
  }
}
