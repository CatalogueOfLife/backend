package org.col.task.common;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;
import org.col.task.hello.HelloTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.PrintWriter;

/**
 * Base task setting up MDC logging properties.
 */
public abstract class BaseTask extends Task {
  private static final Logger LOG = LoggerFactory.getLogger(HelloTask.class);
  public static final String MDC_KEY_TASK = "task";

  public BaseTask(String name) {
    super(name);
  }

  @Override
  public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) throws Exception {
    MDC.put(MDC_KEY_TASK, getName());
    LOG.info("Execute {}", getName());
    run(parameters, output);
    MDC.remove(MDC_KEY_TASK);
  }

  public abstract void run(ImmutableMultimap<String, String> params, PrintWriter out) throws Exception;

  public String getFirst(String key, ImmutableMultimap<String, String> params) {
    if (params.containsKey(key)) {
      return params.get(key).iterator().next();
    }
    return null;
  }
  public boolean getFirstBoolean(String key, ImmutableMultimap<String, String> params, boolean defaultValue) {
    if (params.containsKey(key)) {
      return Boolean.valueOf(params.get(key).iterator().next());
    }
    return defaultValue;
  }
}
