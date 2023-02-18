package life.catalogue.dw.managed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;

import life.catalogue.common.Idle;
import life.catalogue.common.Managed;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for all components that need to be started & stopped by the application.
 * The managed components are registered with the Dropwizard lifecycle, but only for clean shutdowns.
 * They are not started up automatically by Dropwizard.
 *
 * During deploys we need to run two applications simultaneously which cannot both access the same file system components, often MapDB instances.
 * Starting/stopping these components needs to be controlled outside via the API of a running application.
 * See AdminResource methods.
 */
public class ManagedService {
  private static final Logger LOG = LoggerFactory.getLogger(ManagedService.class);

  private final LifecycleEnvironment environment;
  private final Map<Component, Managed> components = new HashMap<>();
  private final List<Idle> idle = new ArrayList<>();

  public ManagedService(LifecycleEnvironment env) {
    environment = env;
  }

  public void manage(Component component, Managed managed) {
    environment.manage(ManagedUtils.stopOnly(managed));
    components.put(component, managed);
    if (managed instanceof Idle) {
      idle.add((Idle) managed);
    }
  }

  public Map<String, Boolean> state() {
    Map<String, Boolean> state = new HashMap<>();
    for (Component c : Component.values()) {
      var m = components.get(c);
      state.put(c.name(), m != null && m.hasStarted());
    }
    // idle summary
    state.put("idle", idle.stream().allMatch(Idle::isIdle));
    return state;
  }

  public void startAll() throws Exception {
    for (var c : Component.values()) {
      start(c);
    }
  }

  public void stopAll() {
    var comps = Component.values();
    ArrayUtils.reverse(comps);
    for (var c : comps) {
      if (components.containsKey(c)) {
        try {
          stop(c);
        } catch (Exception e) {
          LOG.error("Failed to stop component {}", c, e);
        }
      }
    }
  }

  public void start(Component component) throws Exception {
    if (components.containsKey(component)) {
      var c = components.get(component);
      if (c.hasStarted()) {
        LOG.info("Component {} is already running", component);
      } else {
        c.start();
      }
    } else {
      LOG.warn("Component {} cannot be started as it is not managed yet", component);
    }
  }

  public void stop(Component component) throws Exception {
    if (components.containsKey(component)) {
      var c = components.get(component);
      if (c.hasStarted()) {
        c.stop();
      } else {
        LOG.info("Component {} not running", component);
      }
    } else {
      LOG.warn("Component {} cannot be stopped as it is not managed yet", component);
    }
  }

}
