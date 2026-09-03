package life.catalogue.dw.managed;

import life.catalogue.common.Idle;
import life.catalogue.common.Managed;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ManagedServiceTest {

  static class Comp implements Managed, Idle {
    final String name;
    final List<String> log;
    boolean started;
    boolean idle = true;
    RuntimeException failOnStart;

    Comp(String name, List<String> log) {
      this.name = name;
      this.log = log;
    }

    @Override
    public void start() {
      if (failOnStart != null) {
        throw failOnStart;
      }
      started = true;
      log.add("start:" + name);
    }

    @Override
    public void stop() {
      started = false;
      log.add("stop:" + name);
    }

    @Override
    public boolean hasStarted() {
      return started;
    }

    @Override
    public boolean isIdle() {
      return idle;
    }
  }

  private ManagedService service(List<String> log, Component... components) {
    var svc = new ManagedService(new LifecycleEnvironment(new MetricRegistry()));
    for (Component c : components) {
      svc.manage(c, new Comp(c.name(), log));
    }
    return svc;
  }

  /**
   * The declaration order of the enum is the start order and the reverse of it is the stop order.
   * The names index has to be up before the executor runs jobs that match against it, and the cron
   * executor submits a reconcile the moment it starts, so it must come after the executor.
   */
  @Test
  public void startsAndStopsInEnumOrder() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Component.NamesIndex, Component.JobExecutor, Component.CronExecutor);

    svc.startAll();
    assertEquals(List.of("start:NamesIndex", "start:JobExecutor", "start:CronExecutor"), log);

    log.clear();
    svc.stopAll();
    assertEquals(List.of("stop:CronExecutor", "stop:JobExecutor", "stop:NamesIndex"), log);
  }

  @Test
  public void startAllTriesEveryComponentAndThenThrows() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = new ManagedService(new LifecycleEnvironment(new MetricRegistry()));
    var broken = new Comp("NamesIndex", log);
    broken.failOnStart = new IllegalStateException("nidx file is corrupt");
    svc.manage(Component.NamesIndex, broken);
    svc.manage(Component.JobExecutor, new Comp("JobExecutor", log));

    assertThrows(IllegalStateException.class, svc::startAll);
    // one component failing must not cost the others their start
    assertEquals(List.of("start:JobExecutor"), log);
    assertTrue(svc.state().get("JobExecutor"));
    assertFalse(svc.state().get("NamesIndex"));
  }

  @Test
  public void startAndStopAreIdempotent() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Component.JobExecutor);

    svc.start(Component.JobExecutor);
    svc.start(Component.JobExecutor);
    svc.stop(Component.JobExecutor);
    svc.stop(Component.JobExecutor);
    assertEquals(List.of("start:JobExecutor", "stop:JobExecutor"), log);
  }

  /**
   * An unknown name reaches here as a 400 from the param converter, but a name that parses yet has no
   * managed instance - a component only wired on some servers - must stay a logged no-op rather than throw.
   */
  @Test
  public void unmanagedComponentIsANoOp() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Component.JobExecutor);

    svc.start(Component.NamesIndex);
    svc.stop(Component.NamesIndex);
    assertTrue(log.isEmpty());
    assertFalse(svc.state().get("NamesIndex"));
  }

  /**
   * The names index has to be up before the executor runs jobs that match against it, and the cron executor
   * submits a matcher reconcile the moment it starts, so it has to come after the executor. Nothing here
   * cascades, so this order is the whole of the dependency handling.
   */
  @Test
  public void nidxIsFirstAndTheExecutorPrecedesTheCron() {
    var order = List.of(Component.values());
    assertEquals(Component.NamesIndex, order.get(0));
    assertTrue(order.indexOf(Component.JobExecutor) < order.indexOf(Component.CronExecutor));
  }

  @Test
  public void stateReportsEveryComponentPlusIdle() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Component.JobExecutor);

    var state = svc.state();
    // the deploy scripts and the UI read these CamelCase keys, and every component has one whether managed or not
    for (Component c : Component.values()) {
      assertTrue("missing key " + c, state.containsKey(c.name()));
    }
    assertFalse(state.get("JobExecutor"));
    assertTrue(state.get("idle"));

    svc.startAll();
    assertTrue(svc.state().get("JobExecutor"));
  }
}
