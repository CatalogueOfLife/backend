package life.catalogue.dw.managed;

import life.catalogue.common.Idle;
import life.catalogue.common.Managed;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;

/**
 * Service for all components that need to be started & stopped by the application.
 * The managed components are registered with the Dropwizard lifecycle, but only for clean shutdowns.
 * They are not started up automatically by Dropwizard.
 *
 * During deploys we need to run two applications simultaneously which cannot both access the same file system components, often MapDB instances.
 * Starting/stopping these components needs to be controlled outside via the API of a running application.
 * See AdminResource methods.
 *
 * Which components exist and which of them start-all starts is per environment configuration, see {@link ComponentMode}.
 * A {@link ComponentMode#DISABLED} component is never registered, so it is indistinguishable from one this server does
 * not wire at all: absent from {@link #state()} and a logged no-op to start or stop.
 */
public class ManagedService {
  private static final Logger LOG = LoggerFactory.getLogger(ManagedService.class);
  /**
   * Seconds stopAll waits for the running jobs to finish before it takes the components down regardless.
   * Long enough for the ordinary job, short enough that a deploy is not held hostage by a multi hour sync.
   */
  public static final int DEFAULT_QUIESCE_SECONDS = 120;

  private final LifecycleEnvironment environment;
  private final Map<Component, ComponentMode> modes;
  private final Map<Component, Managed> components = new EnumMap<>(Component.class);
  private final List<Idle> idle = new ArrayList<>();

  public ManagedService(LifecycleEnvironment env) {
    this(env, null);
  }

  public ManagedService(LifecycleEnvironment env, Map<Component, ComponentMode> modes) {
    this.environment = env;
    // not new EnumMap<>(modes) - that throws on an empty non enum map, which a literal "components: {}" yields
    this.modes = new EnumMap<>(Component.class);
    if (modes != null) {
      this.modes.putAll(modes);
    }
  }

  public ComponentMode mode(Component component) {
    return modes.getOrDefault(component, ComponentMode.AUTO);
  }

  public void manage(Component component, Managed managed) {
    if (mode(component) == ComponentMode.DISABLED) {
      LOG.info("Component {} is disabled in this configuration and not managed", component);
      return;
    }
    environment.manage(ManagedUtils.stopOnly(managed));
    components.put(component, managed);
    if (managed instanceof Idle) {
      idle.add((Idle) managed);
    }
  }

  /**
   * @return the state of every managed component in the enum's own start order. Components that are off or simply
   *         not wired by this server are absent - there is nothing an operator could do about them.
   */
  public Map<String, ComponentState> state() {
    Map<String, ComponentState> state = new LinkedHashMap<>();
    for (Component c : Component.values()) {
      var m = components.get(c);
      if (m != null) {
        state.put(c.name(), new ComponentState(m.hasStarted(), mode(c) != ComponentMode.MANUAL));
      }
    }
    return state;
  }

  /**
   * @return true if all managed components that track idleness are idle
   */
  public boolean isIdle() {
    return idle.stream().allMatch(Idle::isIdle);
  }

  /**
   * Tries to start all managed components apart from the manual ones. If one fails to start, the other ones are
   * still tried and finally an exception thrown.
   * @throws Exception
   */
  public void startAll() throws Exception {
    Exception e = null;
    for (var c : Component.values()) {
      if (!components.containsKey(c)) continue;
      if (mode(c) == ComponentMode.MANUAL) {
        LOG.info("Component {} is manual in this configuration and not started", c);
        continue;
      }
      try {
        start(c);
      } catch (Exception ex) {
        LOG.error("Failed to start component {}", c, ex);
        e = ex;
      }
    }
    if (e!= null) {
      throw e;
    }
  }

  /**
   * Stops every managed component, the manual ones included - this is the blue green read/write switch and must not
   * leave a hand started component alive on the app being replaced.
   */
  public void stopAll() {
    stopAll(DEFAULT_QUIESCE_SECONDS);
  }

  /**
   * @param awaitSeconds how long to wait for the running jobs to finish before stopping regardless
   */
  public void stopAll(int awaitSeconds) {
    quiesceJobs(awaitSeconds);
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

  /**
   * Pauses the job executor and waits for whatever is running to finish, before any component is stopped.
   *
   * Nothing here cascades, and stopping a component never stopped running work: JobExecutor.stop() only
   * grants the running jobs ExecutorUtils.MILLIS_TO_DIE (12s) to notice their interrupt, discards the
   * boolean saying whether they did, and stopAll then pulls the names index out from under whatever is
   * still going. A sector sync mid processTree() does not unwind in 12s and dies on the dead index with
   * its old content already deleted - which cost the COL project 25,712 usages on 2026-09-01.
   *
   * Pausing first also means the queued jobs wait rather than start into a shutdown; stop() still
   * discards them as CANCELED and SyncManager.rescheduleInterrupted resubmits the syncs among them.
   *
   * Best effort by design: a deploy cannot block forever on a multi hour job, so after awaitSeconds the
   * components go down anyway and the damage is caught downstream instead - SectorRunnable pins the
   * failed attempt, SyncScheduler re-syncs the sector and ProjectCopyFactory refuses to release over it.
   */
  private void quiesceJobs(int awaitSeconds) {
    if (!(components.get(Component.JobExecutor) instanceof JobExecutor exec) || !exec.hasStarted()) {
      return;
    }
    exec.pause();
    try {
      if (exec.awaitQuiesced(awaitSeconds, TimeUnit.SECONDS)) {
        LOG.info("Job executor quiesced, stopping all components");
      } else {
        LOG.error("Job executor did not quiesce within {}s, stopping all components anyway. Still running: {}",
          awaitSeconds, exec.getQueue().stream().filter(BackgroundJob::isRunning)
                            .map(j -> j.getJobName() + " " + j.getKey()).collect(Collectors.joining(", ")));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while waiting for the job executor to quiesce, stopping all components anyway");
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
      warnUnmanaged(component, "started");
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
      warnUnmanaged(component, "stopped");
    }
  }

  private void warnUnmanaged(Component component, String verb) {
    LOG.warn("Component {} cannot be {} as it is not managed", component, verb);
  }

}
