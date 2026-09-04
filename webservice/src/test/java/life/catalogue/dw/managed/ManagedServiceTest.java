package life.catalogue.dw.managed;

import life.catalogue.common.Idle;
import life.catalogue.common.Managed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
    return service(log, Map.of(), components);
  }

  private ManagedService service(List<String> log, Map<Component, ComponentMode> modes, Component... components) {
    var svc = new ManagedService(new LifecycleEnvironment(new MetricRegistry()), modes);
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
    assertTrue(svc.state().get("JobExecutor").running());
    assertFalse(svc.state().get("NamesIndex").running());
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
    assertNull(svc.state().get("NamesIndex"));
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

  /**
   * The UI reads these CamelCase keys, but only for what it could actually act on: a component this server does not
   * wire, or that is off for this environment, has no switch to render and must not drag the health banner down.
   */
  @Test
  public void stateReportsOnlyManagedComponents() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Component.JobExecutor);

    var state = svc.state();
    assertEquals(List.of("JobExecutor"), List.copyOf(state.keySet()));
    assertFalse(state.get("JobExecutor").running());
    assertTrue(state.get("JobExecutor").autostart());
    assertTrue(svc.isIdle());

    svc.startAll();
    assertTrue(svc.state().get("JobExecutor").running());
  }

  /** state() keeps the enum's start order so the UI renders the switches in the order an operator flips them. */
  @Test
  public void stateIsInEnumOrder() {
    List<String> log = new ArrayList<>();
    var svc = service(log, Component.CronExecutor, Component.NamesIndex, Component.JobExecutor);

    assertEquals(List.of("NamesIndex", "JobExecutor", "CronExecutor"), List.copyOf(svc.state().keySet()));
  }

  /**
   * OFF is not a stopped component but an absent one: nothing is registered, so it cannot be switched on by hand
   * from the admin UI and it is indistinguishable from a component this server never wired.
   */
  @Test
  public void offComponentsAreNotManagedAtAll() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Map.of(Component.Feedback, ComponentMode.DISABLED),
      Component.JobExecutor, Component.Feedback);

    assertNull(svc.state().get("Feedback"));

    svc.startAll();
    svc.start(Component.Feedback);
    assertEquals(List.of("start:JobExecutor"), log);
    assertNull(svc.state().get("Feedback"));
  }

  /**
   * The exact json the ChecklistBank UI reads. It decides its health banner from autostart, so the two field
   * names and the nesting are a contract, not an implementation detail.
   */
  @Test
  public void stateSerialisesForTheUI() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Map.of(Component.ImportScheduler, ComponentMode.MANUAL,
                                  Component.Feedback, ComponentMode.DISABLED),
      Component.NamesIndex, Component.ImportScheduler, Component.Feedback);
    svc.startAll();

    assertEquals("{\"NamesIndex\":{\"running\":true,\"autostart\":true}," +
                  "\"ImportScheduler\":{\"running\":false,\"autostart\":false}}",
      new ObjectMapper().writeValueAsString(svc.state()));
  }

  /**
   * MANUAL is managed and reported, just not started by start-all. stop-all still stops it - it is the blue green
   * read/write switch and must not leave a hand started scheduler alive on the app being replaced.
   */
  @Test
  public void manualComponentsAreSkippedByStartAllButStayStartable() throws Exception {
    List<String> log = new ArrayList<>();
    var svc = service(log, Map.of(Component.ImportScheduler, ComponentMode.MANUAL),
      Component.JobExecutor, Component.ImportScheduler);

    svc.startAll();
    assertEquals(List.of("start:JobExecutor"), log);
    var state = svc.state();
    assertTrue(state.get("JobExecutor").autostart());
    assertFalse(state.get("ImportScheduler").autostart());
    assertFalse(state.get("ImportScheduler").running());

    log.clear();
    svc.start(Component.ImportScheduler);
    assertEquals(List.of("start:ImportScheduler"), log);
    assertTrue(svc.state().get("ImportScheduler").running());

    log.clear();
    svc.stopAll();
    assertEquals(List.of("stop:ImportScheduler", "stop:JobExecutor"), log);
  }
}
