package life.catalogue.dw.managed;

/**
 * The state of a single managed {@link Component} as served by GET /admin/component.
 *
 * @param running   whether the component has been started
 * @param autostart whether start-all starts it, i.e. its mode is not {@link ComponentMode#MANUAL}. A component
 *                  that is not autostarted and not running is healthy, which is the distinction the ChecklistBank
 *                  UI needs to decide whether to warn.
 */
public record ComponentState(boolean running, boolean autostart) {
}
