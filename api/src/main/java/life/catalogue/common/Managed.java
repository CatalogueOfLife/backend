package life.catalogue.common;

/**
 * An interface for objects which need to be started and stopped dependent or independent whether the application is started or
 * stopped. Reports on running status too.
 *
 * Based on Dropwizard, but allows us to have the interface available without the full DW dependencies.
 */
public interface Managed {

  void start() throws Exception;

  void stop() throws Exception;

  boolean hasStarted();
}
