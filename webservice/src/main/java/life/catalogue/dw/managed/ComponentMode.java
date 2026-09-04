package life.catalogue.dw.managed;

/**
 * How a {@link Component} behaves in this deployment, so that the same jar can run with a different set of
 * autonomous writers per environment. Configured as a map of deviations under the {@code components} config key,
 * anything not listed there being {@link #AUTO}:
 *
 * <pre>
 * components:
 *   Feedback: DISABLED
 *   ImportScheduler: MANUAL
 * </pre>
 *
 * The distinction that matters is between a component that must not exist here at all and one that merely should
 * not start by itself. Feedback opens public github issues and the DOI updater registers DataCite DOIs - neither
 * belongs on dev under any circumstance, not even switched on by hand from the admin UI. The cron executor and the
 * continuous importer are the opposite case: harmless to have around and occasionally worth exercising, they just
 * have no business polling by themselves on a machine nobody is watching.
 */
public enum ComponentMode {

  /** Managed, reported and started by start-all. The default for anything the config does not mention. */
  AUTO,

  /**
   * Managed and individually startable, but skipped by start-all. It is reported with {@code autostart: false} so
   * a UI can tell "deliberately off here" from "should be running and is not". stop-all still stops it - it is the
   * blue-green read/write switch and must not leave a hand started scheduler alive on the app being replaced.
   */
  MANUAL,

  /**
   * Not managed at all: never registered, absent from the state map, and start/stop by name is the same logged
   * no-op as any component a given server does not wire. There is nothing to switch on in the admin UI.
   *
   * Named DISABLED and not the more obvious OFF because yaml 1.1 resolves a bare OFF - like ON, YES and NO - to a
   * boolean, so "Feedback: OFF" would reach jackson as false and fail to bind to this enum at all.
   */
  DISABLED
}
