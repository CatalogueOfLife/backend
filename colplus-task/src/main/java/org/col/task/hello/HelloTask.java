package org.col.task.hello;

import com.google.common.collect.ImmutableMultimap;
import org.col.task.common.BaseTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;

/**
 * Basic task to showcase hello world
 */
public class HelloTask extends BaseTask {
  private static final Logger LOG = LoggerFactory.getLogger(HelloTask.class);

  public HelloTask() {
    super("hello");
  }

  @Override
  public void run(ImmutableMultimap<String, String> params, PrintWriter out) {
    final String name = getFirst("name", params);
    LOG.info("Hello {}!", name);
    out.println("Hello " + name + "!");
  }
}
