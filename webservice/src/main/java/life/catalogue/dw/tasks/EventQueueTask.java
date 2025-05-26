package life.catalogue.dw.tasks;

import io.dropwizard.servlets.tasks.Task;

import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.auth.AuthBundle;
import life.catalogue.event.EventBroker;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class EventQueueTask extends Task {
  private final EventBroker broker;

  public EventQueueTask(EventBroker broker) {
    super("events");
    this.broker = broker;
  }

  @Override
  public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
    broker.dumpQueue(output);
    output.flush();
  }
}