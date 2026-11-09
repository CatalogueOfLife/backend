package life.catalogue.dw.tasks;

import life.catalogue.event.EventBroker;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import io.dropwizard.servlets.tasks.Task;

/**
 * Shows the state of the event broker and the latest events this app received.
 */
public class EventsTask extends Task {
  private final EventBroker broker;

  public EventsTask(EventBroker broker) {
    super("events");
    this.broker = broker;
  }

  @Override
  public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
    output.println(broker.status());
    output.println();
    for (String e : broker.recent()) {
      output.println(e);
    }
    output.flush();
  }
}
