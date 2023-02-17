package life.catalogue.common;

/**
 * For services that run jobs/tasks to report on whether they are currently idle or busy.
 */
public interface Idle {

  boolean isIdle();

}
